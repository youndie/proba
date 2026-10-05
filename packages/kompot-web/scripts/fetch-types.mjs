// Takes the renderer's TypeScript types from the published kompot-spec artefact.
//
// kompot prints them itself (`TypeScriptDeclarations`, from the wire schemas) and ships the result in
// the jar beside those schemas, as `kompot-spec/types/kompot.d.ts`. That is the open file — the one
// for the side that READS bodies, where `KompotComponent` and `KompotAction` admit a type this build
// has never seen (SPEC.md §2.1). Taking the file rather than printing it again keeps one source for
// the types; and taking it from the jar rather than from kompot's repository ties it to the version
// the server is built against, with no JVM on this side.
import { execFileSync } from "node:child_process";
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { download, kompotJar } from "./kompot-version.mjs";

const here = dirname(fileURLToPath(import.meta.url));
const root = join(here, "..");
const check = process.argv.includes("--check");
const target = join(root, "src", "generated", "kompot.ts");
const entry = "kompot-spec/types/kompot.d.ts";

const { version, url } = kompotJar(root, "kompot-spec");

const work = mkdtempSync(join(tmpdir(), "kompot-types-"));
try {
  const jar = join(work, "spec.jar");
  await download(url, jar);
  const declarations = execFileSync("unzip", ["-p", jar, entry], { encoding: "utf8" });
  if (!declarations.includes("export type KompotComponent")) {
    throw new Error(`${entry} in kompot-spec ${version} declares no KompotComponent`);
  }

  // The first line names the version, so that a file regenerated from something else is caught
  // without the network (scripts/check-kompot-pins.py); everything below it is the jar's, verbatim.
  const provenance = `// kompot-spec ${version}, ${entry} from its jar. Regenerate: pnpm --filter kompot-web schema`;
  const incoming = `${provenance}\n${declarations}`;

  if (check) {
    let existing = null;
    try {
      existing = readFileSync(target, "utf8");
    } catch {
      existing = null;
    }
    if (existing !== incoming) {
      console.error(
        existing === null
          ? "src/generated/kompot.ts is missing"
          : `src/generated/kompot.ts is not ${entry} from kompot-spec ${version}. Run pnpm --filter kompot-web schema`,
      );
      process.exit(1);
    }
    console.log(`types match kompot-spec ${version}`);
  } else {
    writeFileSync(target, incoming);
    console.log(`types <- kompot-spec ${version}`);
  }
} finally {
  rmSync(work, { recursive: true, force: true });
}
