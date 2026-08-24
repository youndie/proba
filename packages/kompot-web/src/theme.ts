import type { CSSProperties } from "react";

/**
 * Design-system tokens are open strings: a server may send `ColorToken("promo_gold")` with no client
 * release. An unknown token therefore costs styling and nothing else — returning undefined here is
 * the whole degradation story, and it is why neither lookup throws.
 */
export interface Theme {
  color(token: string): string | undefined;
  typography(token: string): CSSProperties | undefined;

  /**
   * The foreground that goes with a background token, if the theme names one.
   *
   * The protocol has nowhere to put the colour of text: `KompotTextStyle` carries size, line height,
   * weight and tracking, the text component carries a typography token and no colour, and of the
   * five modifier nodes only `background` names a colour at all. So the foreground is the client's
   * decision, and every client makes it differently unless it is written down somewhere.
   *
   * Pairing it to the background is the version of that decision a server can influence: the server
   * says `background: surface_raised`, and the text on it comes out in whatever this theme says goes
   * with `surface_raised`. An unpaired token changes nothing and the text inherits, which is the
   * same degradation an unknown token gets.
   */
  onColor(backgroundToken: string): string | undefined;
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

/** Material names its foregrounds after its backgrounds already, so the pairing is a prefix. */
const materialPairs = (token: string): string | undefined => {
  const paired = `on_${token}`;
  return paired in materialColors ? materialColors[paired] : undefined;
};

/** The reference token set, matching the constants a kompot server and client already share. */
export const materialTheme: Theme = {
  color: (token) => materialColors[token],
  typography: (token) => materialTypography[token],
  onColor: materialPairs,
};

export function themeWith(
  colors: Record<string, string>,
  typography: Record<string, CSSProperties> = {},
  pairs: Record<string, string> = {},
): Theme {
  const theme: Theme = {
    color: (token) => colors[token] ?? materialTheme.color(token),
    typography: (token) => typography[token] ?? materialTheme.typography(token),
    onColor: (token) => {
      const paired = pairs[token];
      if (paired) return theme.color(paired);
      return colors[`on_${token}`] ?? materialTheme.onColor(token);
    },
  };
  return theme;
}
