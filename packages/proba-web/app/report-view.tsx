"use client";

import { KompotScreen, webActionHandler } from "kompot-web";
import type { AnyComponent } from "kompot-web";
import { cssVariableTheme } from "../src/theme";

/**
 * The screen the server described, drawn.
 *
 * A client component because the renderer carries a React context, and server components cannot.
 * It is still rendered to HTML on the server for the first response — which is the point: the report
 * is a page somebody shares, and a page that needs JavaScript to say anything is a blank page to
 * whoever opens the link.
 */
export function ReportView(props: { screen: AnyComponent }) {
  return (
    <KompotScreen
      component={props.screen}
      theme={cssVariableTheme}
      onAction={webActionHandler({
        onHostAction: (action) => {
          if (action.type === "load_page") location.assign(String((action as { url?: unknown }).url ?? "/"));
        },
      })}
    />
  );
}
