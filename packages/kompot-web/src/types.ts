import type {
  KompotAction,
  KompotComponent,
  KompotModifierNodeBackground,
  KompotModifierNodeGradient,
  KompotModifierNodePadding,
  KompotModifierNodeSize,
  KompotModifierNodeWeight,
} from "./generated/kompot";

export * from "./generated/kompot";

/**
 * The modifier chain is CLOSED — the five nodes below are all there are — while the component and
 * action hierarchies are OPEN. That asymmetry is the protocol's, not this renderer's: a server may
 * send a component type no client knows, and the client owes it a placeholder rather than a crash.
 */
export type ModifierNode =
  | KompotModifierNodeBackground
  | KompotModifierNodeGradient
  | KompotModifierNodePadding
  | KompotModifierNodeSize
  | KompotModifierNodeWeight;

/** Anything that arrived in a component position, known type or not. */
export type AnyComponent = KompotComponent & { modifiers?: ModifierNode[] };

export type AnyAction = KompotAction;

export function modifiersOf(component: AnyComponent): ModifierNode[] {
  const modifiers = (component as { modifiers?: unknown }).modifiers;
  return Array.isArray(modifiers) ? (modifiers as ModifierNode[]) : [];
}
