import type { CSSProperties, ReactNode } from "react";
import type { ModifierNode } from "./types";
import type { Theme } from "./theme";

/**
 * The chain is ordered, and the order is the whole difficulty (SPEC §5.1): `padding` then
 * `background` is not `background` then `padding`. One element per node, **first node outermost**,
 * reproduces that exactly — the outer element's padding shrinks what the inner background covers,
 * and swapping them swaps the result. Collapsing the chain into one element's style cannot express
 * the difference at all: CSS has one padding and one background per box.
 */
export function applyModifiers(nodes: ModifierNode[], child: ReactNode, theme: Theme): ReactNode {
  const weight = nodes.find((node): node is Extract<ModifierNode, { type: "weight" }> => node.type === "weight");
  const visual = nodes.filter((node) => node.type !== "weight");

  if (visual.length === 0 && weight === undefined) return child;

  // A weighted node takes its whole share rather than drawing itself to its content (SPEC §5.2), so
  // every element between the share and a background has to fill. Without this the same tree looks
  // right with long text — which stretches on its own — and wrong with short, which sends whoever
  // sees it looking for the cause in the data.
  const share: CSSProperties =
    weight === undefined ? {} : { flexGrow: weight.value, flexBasis: 0, minWidth: 0, minHeight: 0 };

  if (visual.length === 0) {
    return <div style={{ boxSizing: "border-box", ...share }}>{child}</div>;
  }

  // A constraint set by one node reaches the nodes after it. In Compose `size(12.dp).background(c)`
  // is a 12dp square, because background fills the constraints it is handed; nested elements do not
  // do that by themselves, and the background box collapses to nothing around empty content. Carried
  // forward per axis rather than for the whole element: constraining the width says nothing about
  // the height, and filling both would stretch a padded box across its row.
  const fills = fillsFrom(visual, weight !== undefined);

  let rendered: ReactNode = child;
  for (let index = visual.length - 1; index >= 0; index -= 1) {
    const node = visual[index]!;
    const outermost = index === 0;
    rendered = (
      <div
        data-kompot-modifier={node.type}
        style={{
          boxSizing: "border-box",
          ...(outermost ? {} : fills[index]),
          ...styleOf(node, theme),
          ...(outermost ? share : {}),
        }}
      >
        {rendered}
      </div>
    );
  }
  return rendered;
}

/** For each node, what it must fill because an earlier node in the chain — or a weight — constrained it. */
function fillsFrom(visual: ModifierNode[], weighted: boolean): CSSProperties[] {
  let width = weighted;
  let height = weighted;
  return visual.map((node) => {
    const fill: CSSProperties = {};
    if (width) fill.width = "100%";
    if (height) fill.height = "100%";
    if (node.type === "size") {
      width ||= node.widthDp != null || node.width === "Fill";
      height ||= node.heightDp != null || node.height === "Fill";
    }
    return fill;
  });
}

function styleOf(node: ModifierNode, theme: Theme): CSSProperties {
  switch (node.type) {
    case "background":
      // The foreground travels with the background: nothing on the wire can set the colour of text,
      // so this is the only point at which a server's choice reaches it.
      return { background: theme.color(node.color), color: theme.onColor(node.color) };

    case "gradient": {
      const colors = node.colors.map((token) => theme.color(token)).filter((it): it is string => it !== undefined);
      // A gradient of one usable colour is not a gradient; leaving it out costs styling, not the screen.
      return colors.length > 1
        ? { backgroundImage: `linear-gradient(${colors.join(", ")})`, color: theme.onColor(node.colors[0]!) }
        : {};
    }

    case "padding":
      return padding(node);

    case "size":
      return size(node);

    case "weight":
      return {};
  }
}

function padding(node: Extract<ModifierNode, { type: "padding" }>): CSSProperties {
  const style: CSSProperties = {};
  if (node.all != null) style.padding = dp(node.all);
  if (node.top != null) style.paddingTop = dp(node.top);
  if (node.bottom != null) style.paddingBottom = dp(node.bottom);
  // start/end are the writing direction's, not the screen's, so the logical properties are the
  // faithful ones: a right-to-left locale has to move them without the server saying anything.
  if (node.start != null) style.paddingInlineStart = dp(node.start);
  if (node.end != null) style.paddingInlineEnd = dp(node.end);
  return style;
}

function size(node: Extract<ModifierNode, { type: "size" }>): CSSProperties {
  const style: CSSProperties = {};
  // A number wins over the symbolic value on the same axis (SPEC §5.4), and Wrap is the same as no
  // node at all — it is what a box does without being asked.
  if (node.widthDp != null) style.width = dp(node.widthDp);
  else if (node.width === "Fill") style.width = "100%";
  if (node.heightDp != null) style.height = dp(node.heightDp);
  else if (node.height === "Fill") style.height = "100%";
  return style;
}

/**
 * The protocol's one unit is the density-independent pixel (SPEC §5.3), and the CSS pixel is already
 * density-independent, so the conversion is one to one rather than a scale factor to get wrong.
 */
export function dp(value: number): string {
  return `${value}px`;
}
