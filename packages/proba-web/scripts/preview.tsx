import { writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { renderToStaticMarkup } from "react-dom/server";
import { KompotScreen } from "kompot-web";
import type { AnyComponent } from "kompot-web";
import { probaTheme, type Scheme } from "../src/theme";
import { sampleScreen, type SampleFinding } from "../src/sample";

/**
 * Renders the report screen to a page that can be looked at.
 *
 * A design argued in prose is a design nobody has seen. This also does the one thing the DOM tests
 * cannot: it puts the renderer through react-dom/server, which is how the pages of M5 will be
 * produced, so a component that only works in a browser fails here rather than later.
 */
const here = dirname(fileURLToPath(import.meta.url));
const root = join(here, "..", "..", "..");

// Taken from an actual run: `proba dev.youndie.proba.sample:lib:1.0.0 --deep`, plus the finding the
// first probe of this project ever produced. Invented findings would let the design be tuned to
// text that never occurs.
const findings: SampleFinding[] = [
  {
    checkId: "api-unreachable",
    severity: "defect",
    subject: "jvm: lib-1.0.0.jar",
    message:
      "the public API hands out 1 type a consumer cannot name — dev.youndie.proba.sample.support.Token — " +
      "so code calling it fails with “Cannot access class” while this build, its tests and its publish stay green",
    evidence: [
      "not on the compile classpath: dev.youndie.proba.sample.support.Token",
      "compile classpath: 3 artefacts",
    ],
  },
  {
    checkId: "version-in-declared-name",
    severity: "defect",
    subject: "jvm: kompot-core-jvm-0.27.0.jar",
    message:
      "arrives as “kompot-core-jvm-0.27.0.jar”, which carries no version 0.27.0.46 — anything that tells " +
      "artefacts apart by file name records a version that was never published",
    evidence: [
      "declared name kompot-core-jvm-0.27.0.jar",
      "fetched from kompot-core-jvm-0.27.0.46.jar",
    ],
  },
  {
    checkId: "api-omits-sibling",
    severity: "suspicion",
    subject: "jvm",
    message:
      "a consumer compiling against this target never receives io.github.youndie:kompot-registry-annotations, " +
      "which the run time does receive — correct only if no public signature mentions them",
    evidence: [
      "api jvmApiElements-published: kompot-core, kotlinx-serialization-json, kotlin-stdlib",
      "runtime jvmRuntimeElements-published: … , kompot-registry-annotations",
    ],
  },
  {
    checkId: "api-unreachable",
    severity: "undetermined",
    subject: "iosArm64",
    message: "no consumer build was run for this target, so what a compile classpath receives is not known here",
    evidence: ["the confirming tier resolves jvm only; reading a klib's public surface is still an open question"],
  },
];

const screen = sampleScreen("io.github.youndie:kompot-core:0.27.0.46", findings) as unknown as AnyComponent;
writeFileSync(join(root, "design", "sample-screen.json"), `${JSON.stringify(screen, null, 2)}\n`);

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

const out = join(root, "design", "preview.html");
writeFileSync(out, page);
console.log(`design/preview.html <- ${findings.length} findings, both schemes`);
