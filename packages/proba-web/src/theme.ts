import type { CSSProperties } from "react";
import { themeWith, type Theme } from "kompot-web";
import { colors, pairs, typography, type Scheme } from "./tokens";

/**
 * proba's tokens, as a kompot theme.
 *
 * The server names tokens and never sends a colour, so this file and the server's vocabulary are one
 * contract with two ends. Renaming a token is a wire change; changing a value is not, which is the
 * whole reason a designer's answer can land here as values.
 */
export function probaTheme(scheme: Scheme): Theme {
  return themeWith(colors[scheme], typography, pairs);
}

/** The same palette, named rather than resolved, so CSS decides the scheme. */
export const cssVariableTheme: Theme = {
  color: (token) => (token in colors.light ? `var(--kompot-${token})` : undefined),
  typography: (token) => typography[token],
  onColor: (token) => {
    const paired = pairs[token] ?? (`on_${token}` in colors.light ? `on_${token}` : undefined);
    return paired ? `var(--kompot-${paired})` : undefined;
  },
};

export type { Scheme };
export type SeverityName = "defect" | "suspicion" | "undetermined";
