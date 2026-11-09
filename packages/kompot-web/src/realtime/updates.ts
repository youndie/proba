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

/** Where an `update` leaves the address, for the application to keep (SPEC §16.4). */
export interface UpdateAddress {
  deeplink: string;
  history: "push" | "replace";
}

/** The `update` action of kompot-commands: frames of §10 plus the address the screen ends at. */
export interface UpdateAction {
  type: "update";
  updates: UpdateComponentMessage[];
  deeplink?: string | null;
  history?: string | null;
}

/**
 * Applies the frames of one `update` in order, and says where the address goes.
 *
 * In order, one after the other, because the order is the meaning: of two frames for one id the later
 * wins, and a frame for a node inside one replaced earlier lands in the replacement. A frame for an id
 * the tree does not have is ignored and the rest still apply, as for a live frame.
 *
 * The address is returned, never followed: the application keeps the history. `history` is an open
 * string, so anything but `replace` — absent or a word this client does not know — means `push`.
 * Without a deeplink there is no address, and the one the application has stays.
 */
export function applyUpdateAction(
  tree: AnyComponent,
  action: UpdateAction,
): { tree: AnyComponent; address: UpdateAddress | null } {
  const next = (action.updates ?? []).reduce(applyUpdate, tree);
  const address = action.deeplink
    ? { deeplink: action.deeplink, history: action.history === "replace" ? ("replace" as const) : ("push" as const) }
    : null;
  return { tree: next, address };
}

/** The node under an id, or undefined when the tree does not have it. */
export function findNode(tree: AnyComponent, componentId: string): AnyComponent | undefined {
  if (tree.id === componentId) return tree;
  for (const child of childrenOf(tree)) {
    const found = findNode(child, componentId);
    if (found) return found;
  }
  return undefined;
}
