import type { AnyComponent } from "../types";

/** A frame, not a channel: it names a component already on screen and gives its replacement (SPEC §10.1). */
export interface UpdateComponentMessage {
  componentId: string;
  component: AnyComponent;
}

/**
 * Replaces the node the frame names, and returns the tree unchanged when it names nothing.
 *
 * A frame for an unknown id is ignored rather than appended or thrown on (SPEC §10.2). The tree the
 * server is updating is one the client already has; a frame about anything else is about a screen
 * this one is not showing, which happens whenever a person navigates while a frame is in flight.
 */
export function applyUpdate(tree: AnyComponent, message: UpdateComponentMessage): AnyComponent {
  if (tree.id === message.componentId) return message.component;
  return mapChildren(tree, (child) => applyUpdate(child, message));
}

/** True when the frame found a home. Useful to a caller that wants to know it was not dropped. */
export function updatesAnything(tree: AnyComponent, componentId: string): boolean {
  if (tree.id === componentId) return true;
  return childrenOf(tree).some((child) => updatesAnything(child, componentId));
}

const childArrays = ["children", "initialItems"] as const;

function childrenOf(node: AnyComponent): AnyComponent[] {
  const found: AnyComponent[] = [];
  for (const key of childArrays) {
    const value = (node as Record<string, unknown>)[key];
    if (Array.isArray(value)) found.push(...(value as AnyComponent[]));
  }
  const single = (node as Record<string, unknown>).emptyState;
  if (single && typeof single === "object") found.push(single as AnyComponent);
  return found;
}

function mapChildren(node: AnyComponent, map: (child: AnyComponent) => AnyComponent): AnyComponent {
  let changed = false;
  const next: Record<string, unknown> = { ...node };
  for (const key of childArrays) {
    const value = (node as Record<string, unknown>)[key];
    if (!Array.isArray(value)) continue;
    const mapped = (value as AnyComponent[]).map(map);
    if (mapped.some((child, at) => child !== (value as AnyComponent[])[at])) {
      next[key] = mapped;
      changed = true;
    }
  }
  const single = (node as Record<string, unknown>).emptyState;
  if (single && typeof single === "object") {
    const mapped = map(single as AnyComponent);
    if (mapped !== single) {
      next.emptyState = mapped;
      changed = true;
    }
  }
  return changed ? (next as AnyComponent) : node;
}
