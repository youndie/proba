"use client";

// React Server Components cannot hold a context or a hook, and this file has both. Without the
// directive a framework that renders on the server refuses the whole import chain — which is every
// consumer who wanted server-rendered kompot screens in the first place.
import { createContext, useContext, useEffect, type ReactNode } from "react";
import type { ActionHandler } from "./actions";
import { applyModifiers } from "./modifiers";
import { materialTheme, type Theme } from "./theme";
import { modifiersOf, type AnyComponent } from "./types";
import { renderers } from "./components";

export interface KompotEnvironment {
  theme: Theme;
  onAction: ActionHandler;
  /**
   * Drawn in place of a component whose type this client does not know and for which the server
   * named no equivalent. The default draws nothing (SPEC.md §2.1): a visible mark would greet every
   * reader of an older client for the whole rollout, which is exactly when nothing is broken. A
   * debug build or an editing tool may want one, which is what this is for.
   */
  renderUnknown: (component: AnyComponent) => ReactNode;
  /**
   * Where a degradation is reported. Invisible is not uncounted: the protocol requires a client to
   * report it (SPEC.md §2.1), because that count is what says how many readers do not see a component.
   */
  onDegradation: (degradation: Degradation) => void;
}

/**
 * One node this client could not draw as sent. `outcome` names what it did instead — the same three
 * outcomes the Kotlin client reports: nothing at all, the host's placeholder, or the equivalent the
 * server named in `fallback`. Only the last is somebody's choice, so it is the one a staged rollout is
 * decided on.
 */
export interface Degradation {
  kind: "unknown_component";
  type: string;
  id?: string;
  outcome: "nothing" | "placeholder" | "server_fallback";
}

const defaultUnknown = (component: AnyComponent): ReactNode => (
  <div data-kompot-unknown={String(component.type)} style={{ display: "none" }} />
);

const EnvironmentContext = createContext<KompotEnvironment>({
  theme: materialTheme,
  onAction: () => {},
  renderUnknown: defaultUnknown,
  onDegradation: () => {},
});

export function useKompot(): KompotEnvironment {
  return useContext(EnvironmentContext);
}

export function KompotProvider(props: {
  theme?: Theme;
  onAction?: ActionHandler;
  renderUnknown?: (component: AnyComponent) => ReactNode;
  onDegradation?: (degradation: Degradation) => void;
  children: ReactNode;
}): ReactNode {
  const value: KompotEnvironment = {
    theme: props.theme ?? materialTheme,
    onAction: props.onAction ?? (() => {}),
    renderUnknown: props.renderUnknown ?? defaultUnknown,
    onDegradation: props.onDegradation ?? (() => {}),
  };
  return <EnvironmentContext.Provider value={value}>{props.children}</EnvironmentContext.Provider>;
}

/**
 * Draws one node of the tree.
 *
 * An unrecognised type never throws. That is the protocol's promise and not a nicety: the hierarchy
 * is open precisely so a server can ship a component before the clients know it, which is worth
 * nothing if the screen dies on arrival. In order (SPEC.md §2.1): the equivalent the server named in
 * `fallback`, drawn as a node of its own — and degrading again one level down if it is unknown too —
 * or else [KompotEnvironment.renderUnknown]. Either way it is reported.
 */
export function KompotNode(props: { component: AnyComponent }): ReactNode {
  const environment = useKompot();
  const type = String(props.component.type);
  const renderer = renderers[type];
  const fallback = renderer ? undefined : equivalentOf(props.component);
  const outcome: Degradation["outcome"] | undefined = renderer
    ? undefined
    : fallback
      ? "server_fallback"
      : environment.renderUnknown === defaultUnknown
        ? "nothing"
        : "placeholder";
  const id = typeof props.component.id === "string" ? props.component.id : undefined;

  // Once per node that appears, not once per render: a count that grows with every keystroke elsewhere
  // on the screen answers no question.
  useEffect(() => {
    if (outcome) environment.onDegradation({ kind: "unknown_component", type, id, outcome });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [type, id, outcome]);

  if (fallback) return <KompotNode component={fallback} />;
  const leaf = renderer ? renderer(props.component, environment) : environment.renderUnknown(props.component);
  return applyModifiers(modifiersOf(props.component), leaf, environment.theme);
}

function equivalentOf(component: AnyComponent): AnyComponent | undefined {
  const fallback = (component as { fallback?: unknown }).fallback;
  return fallback !== null && typeof fallback === "object" && typeof (fallback as { type?: unknown }).type === "string"
    ? (fallback as AnyComponent)
    : undefined;
}

/** Draws a whole screen. */
export function KompotScreen(props: {
  component: AnyComponent;
  theme?: Theme;
  onAction?: ActionHandler;
  renderUnknown?: (component: AnyComponent) => ReactNode;
  onDegradation?: (degradation: Degradation) => void;
}): ReactNode {
  return (
    <KompotProvider
      theme={props.theme}
      onAction={props.onAction}
      renderUnknown={props.renderUnknown}
      onDegradation={props.onDegradation}
    >
      <KompotNode component={props.component} />
    </KompotProvider>
  );
}
