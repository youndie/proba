import type { CSSProperties } from "react";
import { themeWith, type Theme } from "kompot-web";
import tokens from "../../../design/tokens.json";

/**
 * proba's tokens, as a kompot theme.
 *
 * The server names tokens and never sends a colour, so this file and the server's vocabulary are one
 * contract with two ends. Renaming a token is a wire change; changing a value is not, which is the
 * whole reason a designer's answer can land here as values.
 */
export type Scheme = "light" | "dark";

export function probaTheme(scheme: Scheme): Theme {
  return themeWith(
    tokens.colors[scheme],
    tokens.typography as Record<string, CSSProperties>,
    pairs,
  );
}

const { $comment: _pairsComment, ...pairs } = tokens.pairs;

export const severities = tokens.severity;
export const shapes = tokens.shapes;
export type SeverityName = "defect" | "suspicion" | "undetermined";
