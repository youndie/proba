import type { CSSProperties } from "react";

/**
 * Design-system tokens are open strings: a server may send `ColorToken("promo_gold")` with no client
 * release. An unknown token therefore costs styling and nothing else — returning undefined here is
 * the whole degradation story, and it is why neither lookup throws.
 */
export interface Theme {
  color(token: string): string | undefined;
  typography(token: string): CSSProperties | undefined;
}

const materialColors: Record<string, string> = {
  primary: "#6750a4",
  on_primary: "#ffffff",
  secondary: "#625b71",
  on_secondary: "#ffffff",
  surface: "#fef7ff",
  on_surface: "#1d1b20",
  surface_variant: "#e7e0ec",
  on_surface_variant: "#49454f",
  background: "#fef7ff",
  on_background: "#1d1b20",
  error: "#b3261e",
  on_error: "#ffffff",
  outline: "#79747e",
};

const materialTypography: Record<string, CSSProperties> = {
  display_large: { fontSize: "57px", lineHeight: "64px", fontWeight: 400 },
  headline_large: { fontSize: "32px", lineHeight: "40px", fontWeight: 400 },
  headline_small: { fontSize: "24px", lineHeight: "32px", fontWeight: 400 },
  title_large: { fontSize: "22px", lineHeight: "28px", fontWeight: 400 },
  title_medium: { fontSize: "16px", lineHeight: "24px", fontWeight: 500 },
  body_large: { fontSize: "16px", lineHeight: "24px", fontWeight: 400 },
  body_medium: { fontSize: "14px", lineHeight: "20px", fontWeight: 400 },
  label_large: { fontSize: "14px", lineHeight: "20px", fontWeight: 500 },
  label_small: { fontSize: "11px", lineHeight: "16px", fontWeight: 500 },
};

/** The reference token set, matching the constants a kompot server and client already share. */
export const materialTheme: Theme = {
  color: (token) => materialColors[token],
  typography: (token) => materialTypography[token],
};

export function themeWith(colors: Record<string, string>, typography: Record<string, CSSProperties> = {}): Theme {
  return {
    color: (token) => colors[token] ?? materialTheme.color(token),
    typography: (token) => typography[token] ?? materialTheme.typography(token),
  };
}
