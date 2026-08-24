"use client";

// React Server Components cannot hold a context or a hook, and this file has both. Without the
// directive a framework that renders on the server refuses the whole import chain — which is every
// consumer who wanted server-rendered kompot screens in the first place.
import type { CSSProperties, ReactNode } from "react";
import { dp } from "./modifiers";
import { KompotNode, type KompotEnvironment } from "./render";
import type {
  AnyAction,
  AnyComponent,
  KompotComponentButton,
  KompotComponentColumn,
  KompotComponentPaginatedList,
  KompotComponentRow,
  KompotComponentTable,
  KompotComponentText,
  TableRow,
  TextSpan,
} from "./types";

type Renderer = (component: AnyComponent, environment: KompotEnvironment) => ReactNode;

function textStyle(token: string | null | undefined, environment: KompotEnvironment): CSSProperties {
  return token ? (environment.theme.typography(token) ?? {}) : {};
}

const text: Renderer = (raw, environment) => {
  const component = raw as unknown as KompotComponentText;
  const clamp: CSSProperties =
    component.maxLines != null
      ? {
          display: "-webkit-box",
          WebkitBoxOrient: "vertical",
          WebkitLineClamp: component.maxLines,
          overflow: "hidden",
          textOverflow: component.ellipsis ? "ellipsis" : "clip",
        }
      : {};

  const spans = component.spans ?? [];
  return (
    <span data-kompot="text" style={{ ...textStyle(component.style, environment), ...clamp }}>
      {spans.length === 0 ? component.text : spans.map((span, index) => renderSpan(span, index, environment))}
    </span>
  );
};

function renderSpan(span: TextSpan, index: number, environment: KompotEnvironment): ReactNode {
  const style = textStyle(span.style, environment);
  const action = span.action as AnyAction | null | undefined;
  if (!action) {
    return (
      <span key={index} style={style}>
        {span.text}
      </span>
    );
  }
  return (
    <a
      key={index}
      href="#"
      data-kompot="span-link"
      style={{ ...style, cursor: "pointer" }}
      onClick={(event) => {
        event.preventDefault();
        environment.onAction(action);
      }}
    >
      {span.text}
    </a>
  );
}

const button: Renderer = (raw, environment) => {
  const component = raw as unknown as KompotComponentButton;
  return (
    <button
      type="button"
      data-kompot="button"
      data-variant={component.variant ?? undefined}
      onClick={() => environment.onAction(component.action as AnyAction)}
    >
      {component.text}
    </button>
  );
};

/**
 * `row` and `column` are the same element with one axis different, which is also why a `weight` on a
 * child means anything at all: the parent is the flex container that hands out the shares.
 */
function stack(direction: "row" | "column"): Renderer {
  return (raw, environment) => {
    const component = raw as unknown as KompotComponentRow | KompotComponentColumn;
    const action = component.action as AnyAction | null | undefined;
    return (
      <div
        data-kompot={direction}
        style={{
          display: "flex",
          flexDirection: direction,
          gap: component.spacing ? dp(component.spacing) : undefined,
          cursor: action ? "pointer" : undefined,
        }}
        onClick={action ? () => environment.onAction(action) : undefined}
      >
        {(component.children ?? []).map((child, index) => (
          <KompotNode key={(child as { id?: string }).id ?? index} component={child as AnyComponent} />
        ))}
      </div>
    );
  };
}

const table: Renderer = (raw) => {
  const component = raw as unknown as KompotComponentTable;
  return (
    <table data-kompot="table">
      <tbody>
        {(component.rows ?? []).map((row: TableRow, index) => (
          <tr key={index}>
            {(row.cells ?? []).map((cell, cellIndex) =>
              row.header ? <th key={cellIndex}>{cell}</th> : <td key={cellIndex}>{cell}</td>,
            )}
          </tr>
        ))}
      </tbody>
    </table>
  );
};

/**
 * The first page and the empty state only.
 *
 * Loading further pages needs a request to the server and the page response merged into the list,
 * and that is not in this milestone. Saying so here rather than drawing a button that does nothing:
 * a control that silently fails is worse than one that is not offered.
 */
const paginatedList: Renderer = (raw) => {
  const component = raw as unknown as KompotComponentPaginatedList;
  const items = component.initialItems ?? [];
  if (items.length === 0 && component.emptyState) {
    return <KompotNode component={component.emptyState as AnyComponent} />;
  }
  return (
    <div data-kompot="paginated-list" data-has-more={component.loadMoreAction ? "true" : "false"}>
      {items.map((item, index) => (
        <KompotNode key={(item as { id?: string }).id ?? index} component={item as AnyComponent} />
      ))}
    </div>
  );
};

import { formRenderers } from "./forms/components";

export const renderers: Record<string, Renderer> = {
  ...Object.fromEntries(Object.entries(formRenderers).map(([type, draw]) => [type, (component: AnyComponent) => draw(component)])),
  text,
  button,
  row: stack("row"),
  column: stack("column"),
  table,
  paginated_list: paginatedList,
};
