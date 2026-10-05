"use client";

// React Server Components cannot hold a context or a hook, and this file has both. Without the
// directive a framework that renders on the server refuses the whole import chain — which is every
// consumer who wanted server-rendered kompot screens in the first place.
import { createContext, useContext, type CSSProperties, type ReactNode } from "react";
import { dp } from "./modifiers";
import { KompotNode, type KompotEnvironment } from "./render";
import type {
  AnyAction,
  AnyComponent,
  KompotComponentButton,
  KompotComponentColumn,
  KompotComponentDivider,
  KompotComponentPaginatedList,
  KompotComponentRow,
  KompotComponentSpacer,
  KompotComponentTable,
  KompotComponentText,
  TableRow,
  TextSpan,
} from "./types";

type Renderer = (component: AnyComponent, environment: KompotEnvironment) => ReactNode;

function textStyle(token: string | null | undefined, environment: KompotEnvironment): CSSProperties {
  return token ? (environment.theme.typography(token) ?? {}) : {};
}

/**
 * The colour of the letters, when the server named one.
 *
 * Until kompot 0.29 nothing on the wire could say this, and the only foreground a screen could
 * influence was the one paired to a background token — which cannot put a red word on an ordinary
 * card without inventing a surface nobody asked for. An absent or unknown token still falls through
 * to that pairing: the token is open, so not knowing it costs the colour and not the text.
 */
function textColor(token: string | null | undefined, environment: KompotEnvironment): CSSProperties {
  const colour = token ? environment.theme.color(token) : undefined;
  return colour === undefined ? {} : { color: colour };
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
    <span
      data-kompot="text"
      // A heading for a screen reader to move between (SPEC.md §4.11). The protocol names no level.
      role={component.heading ? "heading" : undefined}
      aria-level={component.heading ? 2 : undefined}
      style={{ ...textStyle(component.style, environment), ...textColor(component.color, environment), ...clamp }}
    >
      {spans.length === 0 ? component.text : spans.map((span, index) => renderSpan(span, index, environment))}
    </span>
  );
};

function renderSpan(span: TextSpan, index: number, environment: KompotEnvironment): ReactNode {
  const style = { ...textStyle(span.style, environment), ...textColor(span.color, environment) };
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
      aria-label={component.accessibilityLabel ?? undefined}
      onClick={() => environment.onAction(component.action as AnyAction)}
    >
      {component.text}
    </button>
  );
};

/** The axis of the nearest stack: what a `divider` is drawn across and a `spacer` along (SPEC.md §4.10). */
const AxisContext = createContext<"row" | "column" | null>(null);

/**
 * `alignment` and `arrangement` are open words: an unfamiliar one means `start` (SPEC.md §4.7).
 *
 * Applied only when the server sent the field. Without it the stack keeps what it always drew here —
 * CSS's own default stretches a column's children across, where the protocol's `start` would wrap
 * them — so that a server not using the field sees no change from this release.
 */
const crossAxis: Record<string, string> = { start: "flex-start", center: "center", end: "flex-end" };

/**
 * `spacing` stays the smallest gap and the arrangement shares only what is left over it, and a stack
 * that does not fit is laid out from the start (SPEC.md §4.7). CSS says the same with `gap` beside
 * `justify-content` — the free space is what remains after the gaps — once `center` and `end` are
 * `safe`, so that an overflowing stack does not push its first child off the leading edge. The
 * `space-*` keywords fall back to the start by themselves.
 */
const mainAxis: Record<string, string> = {
  start: "flex-start",
  center: "safe center",
  end: "safe flex-end",
  space_between: "space-between",
  space_around: "space-around",
  space_evenly: "space-evenly",
};

/**
 * `row` and `column` are the same element with one axis different, which is also why a `weight` on a
 * child means anything at all: the parent is the flex container that hands out the shares.
 */
function stack(direction: "row" | "column"): Renderer {
  return (raw, environment) => {
    const component = raw as unknown as KompotComponentRow | KompotComponentColumn;
    const action = component.action as AnyAction | null | undefined;
    const alignment = component.alignment == null ? undefined : (crossAxis[component.alignment] ?? crossAxis.start);
    const arrangement =
      component.arrangement == null ? undefined : (mainAxis[component.arrangement] ?? mainAxis.start);
    return (
      <div
        data-kompot={direction}
        data-alignment={component.alignment ?? undefined}
        data-arrangement={component.arrangement ?? undefined}
        // A container that does something is a button to assistive technology, and reads the server's
        // words instead of its children when it was given some (SPEC.md §4.11).
        role={action ? "button" : undefined}
        tabIndex={action ? 0 : undefined}
        aria-label={action ? (component.accessibilityLabel ?? undefined) : undefined}
        style={{
          display: "flex",
          flexDirection: direction,
          gap: component.spacing ? dp(component.spacing) : undefined,
          alignItems: alignment,
          justifyContent: arrangement,
          cursor: action ? "pointer" : undefined,
        }}
        onClick={action ? () => environment.onAction(action) : undefined}
        onKeyDown={
          action
            ? (event) => {
                if (event.key === "Enter" || event.key === " ") {
                  event.preventDefault();
                  environment.onAction(action);
                }
              }
            : undefined
        }
      >
        <AxisContext.Provider value={direction}>
          {(component.children ?? []).map((child, index) => (
            <KompotNode key={(child as { id?: string }).id ?? index} component={child as AnyComponent} />
          ))}
        </AxisContext.Provider>
      </div>
    );
  };
}

/** A line across the stack's axis: horizontal in a column and outside any stack, vertical in a row. */
function Divider(props: { component: KompotComponentDivider; environment: KompotEnvironment }): ReactNode {
  const axis = useContext(AxisContext);
  const { theme } = props.environment;
  // The design system's line unless the server named a token, and an unknown token falls back to it
  // as well: the token is open, so not knowing it costs the colour and not the line. The Kotlin client
  // asks its design system's `divider` role and draws Material's outlineVariant when the role is not
  // answered (SPEC.md §4.10); here the design system is the theme, and its answer is `outline_variant`.
  const colour =
    (props.component.color ? theme.color(props.component.color) : undefined) ??
    theme.color("outline_variant") ??
    theme.color("outline");
  const vertical = axis === "row";
  return (
    <div
      data-kompot="divider"
      role="separator"
      aria-orientation={vertical ? "vertical" : "horizontal"}
      style={{
        flexShrink: 0,
        alignSelf: "stretch",
        background: colour,
        ...(vertical ? { width: "1px" } : { height: "1px" }),
      }}
    />
  );
}

/** Room along the stack's axis; a `weight` on it takes a share instead, through the modifier chain. */
function Spacer(props: { component: KompotComponentSpacer }): ReactNode {
  const axis = useContext(AxisContext);
  const size = dp(props.component.size ?? 0);
  return (
    <div
      data-kompot="spacer"
      aria-hidden
      style={{ flexShrink: 0, ...(axis === "row" ? { width: size } : { height: size }) }}
    />
  );
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
      {/* Items stack down, whatever the list itself stands in. */}
      <AxisContext.Provider value="column">
        {items.map((item, index) => (
          <KompotNode key={(item as { id?: string }).id ?? index} component={item as AnyComponent} />
        ))}
      </AxisContext.Provider>
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
  divider: (raw, environment) => <Divider component={raw as unknown as KompotComponentDivider} environment={environment} />,
  spacer: (raw) => <Spacer component={raw as unknown as KompotComponentSpacer} />,
  table,
  paginated_list: paginatedList,
};
