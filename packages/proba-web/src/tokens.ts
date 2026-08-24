import type { CSSProperties } from "react";
import tokens from "../../../design/tokens.json";

export type Scheme = "light" | "dark";

export const colors = tokens.colors;
export const typography = tokens.typography as Record<string, CSSProperties>;
export const severities = tokens.severity;
export const shapes = tokens.shapes;
export type SeverityName = "defect" | "suspicion" | "undetermined";
const { $comment: _comment, ...pairsOnly } = tokens.pairs;
export const pairs = pairsOnly as Record<string, string>;

/**
 * The tokens as CSS custom properties.
 *
 * A page rendered on the server cannot know which scheme the reader prefers, and guessing means
 * either a flash of the wrong one or shipping JavaScript to decide it. Naming a variable instead
 * moves the decision to CSS, where `prefers-color-scheme` already answers it, and the server-rendered
 * markup is then correct in both.
 */
export function tokensCss(): string {
  const declare = (scheme: Scheme) =>
    Object.entries(colors[scheme])
      .map(([token, value]) => `  --kompot-${token}: ${value};`)
      .join("\n");
  return [
    `:root {\n${declare("light")}\n  color-scheme: light dark;\n}`,
    `@media (prefers-color-scheme: dark) {\n  :root {\n${declare("dark")}\n  }\n}`,
    `[data-scheme="light"] {\n${declare("light")}\n}`,
    `[data-scheme="dark"] {\n${declare("dark")}\n}`,
    `body { margin: 0; font-family: ui-sans-serif, system-ui, -apple-system, "Segoe UI", sans-serif;` +
      ` background: var(--kompot-surface); color: var(--kompot-on_surface); }`,
  ].join("\n\n");
}
