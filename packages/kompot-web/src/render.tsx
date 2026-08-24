"use client";

// React Server Components cannot hold a context or a hook, and this file has both. Without the
// directive a framework that renders on the server refuses the whole import chain — which is every
// consumer who wanted server-rendered kompot screens in the first place.
import { createContext, useContext, type ReactNode } from "react";
import type { ActionHandler } from "./actions";
import { applyModifiers } from "./modifiers";
import { materialTheme, type Theme } from "./theme";
import { modifiersOf, type AnyComponent } from "./types";
import { renderers } from "./components";

export interface KompotEnvironment {
  theme: Theme;
  onAction: ActionHandler;
  /** Drawn in place of a component whose type this client does not know. */
  renderUnknown: (component: AnyComponent) => ReactNode;
}

const defaultUnknown = (component: AnyComponent): ReactNode => (
  <div data-kompot-unknown={String(component.type)} style={{ display: "none" }} />
);

const EnvironmentContext = createContext<KompotEnvironment>({
  theme: materialTheme,
  onAction: () => {},
  renderUnknown: defaultUnknown,
});

export function useKompot(): KompotEnvironment {
  return useContext(EnvironmentContext);
}

export function KompotProvider(props: {
  theme?: Theme;
  onAction?: ActionHandler;
  renderUnknown?: (component: AnyComponent) => ReactNode;
  children: ReactNode;
}): ReactNode {
  const value: KompotEnvironment = {
    theme: props.theme ?? materialTheme,
    onAction: props.onAction ?? (() => {}),
    renderUnknown: props.renderUnknown ?? defaultUnknown,
  };
  return <EnvironmentContext.Provider value={value}>{props.children}</EnvironmentContext.Provider>;
}

/**
 * Draws one node of the tree.
 *
 * An unrecognised type reaches [KompotEnvironment.renderUnknown] instead of throwing. That is the
 * protocol's promise and not a nicety: the hierarchy is open precisely so a server can ship a
 * component before the clients know it, which is worth nothing if the screen dies on arrival.
 */
export function KompotNode(props: { component: AnyComponent }): ReactNode {
  const environment = useKompot();
  const renderer = renderers[String(props.component.type)];
  const leaf = renderer ? renderer(props.component, environment) : environment.renderUnknown(props.component);
  return applyModifiers(modifiersOf(props.component), leaf, environment.theme);
}

/** Draws a whole screen. */
export function KompotScreen(props: {
  component: AnyComponent;
  theme?: Theme;
  onAction?: ActionHandler;
  renderUnknown?: (component: AnyComponent) => ReactNode;
}): ReactNode {
  return (
    <KompotProvider theme={props.theme} onAction={props.onAction} renderUnknown={props.renderUnknown}>
      <KompotNode component={props.component} />
    </KompotProvider>
  );
}
