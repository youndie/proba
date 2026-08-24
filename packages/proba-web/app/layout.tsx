import type { ReactNode } from "react";
import { tokensCss } from "../src/tokens";

export const metadata = {
  title: "proba",
  description: "What a consumer actually gets when they add your library",
};

export default function RootLayout(props: { children: ReactNode }) {
  return (
    <html lang="en">
      <head>
        {/* Generated from design/tokens.json, so the palette has one definition and two readers. */}
        <style dangerouslySetInnerHTML={{ __html: tokensCss() }} />
      </head>
      <body>{props.children}</body>
    </html>
  );
}
