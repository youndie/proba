import { readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { renderToStaticMarkup } from "react-dom/server";
import { KompotScreen } from "kompot-web";
import type { AnyComponent } from "kompot-web";
import { probaTheme, type Scheme } from "../src/theme";

/**
 * Renders the report screen to a page that can be looked at, in both schemes at once.
 *
 * The screen is a document the Kotlin server produced — `design/sample-screen.json`, captured from a
 * real run — and not something built here. There was a producer on this side while the server did not
 * exist; it is gone, and this page did not change when it went, which was the point of it emitting a
 * plain document in the first place.
 *
 *   curl "$PROBA/report/io.github.youndie/kompot-standard/0.27.0.46?repo=…" \
 *     | python3 -m json.tool > design/sample-screen.json
 */
const here = dirname(fileURLToPath(import.meta.url));
const root = join(here, "..", "..", "..");

const screen = JSON.parse(readFileSync(join(root, "design", "sample-screen.json"), "utf8")) as AnyComponent;

const panel = (scheme: Scheme) =>
  renderToStaticMarkup(<KompotScreen component={screen} theme={probaTheme(scheme)} />);

const page = `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>proba — the report screen</title>
<style>
  :root { color-scheme: light dark; }
  body { margin: 0; font-family: ui-sans-serif, system-ui, -apple-system, "Segoe UI", sans-serif; }
  .schemes { display: grid; grid-template-columns: 1fr; }
  @media (min-width: 980px) { .schemes { grid-template-columns: 1fr 1fr; } }
  .scheme { min-width: 0; }
  .scheme > h2 {
    margin: 0; padding: 10px 32px; font: 600 12px/16px ui-monospace, Menlo, monospace;
    letter-spacing: .08em; text-transform: uppercase;
  }
  .light > h2 { background: #f2f1ee; color: #6b6865; }
  .dark  > h2 { background: #0e0e0d; color: #9a9691; }
</style>
</head>
<body>
<div class="schemes">
  <section class="scheme light"><h2>light</h2>${panel("light")}</section>
  <section class="scheme dark"><h2>dark</h2>${panel("dark")}</section>
</div>
</body>
</html>
`;

writeFileSync(join(root, "design", "preview.html"), page);
console.log("design/preview.html <- design/sample-screen.json, both schemes");
