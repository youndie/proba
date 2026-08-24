// Takes the wire schemas from the published kompot-spec artefact and generates TypeScript from them.
//
// Not a copy kept in this repository: a second copy of a contract is a second source of truth, and
// the one nobody regenerates is the one that quietly stops being true. The version is a coordinate in
// package.json, so what these types describe is a thing that was actually published.
import { execFileSync } from "node:child_process";
import { mkdtempSync, mkdirSync, readdirSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { compileFromFile } from "json-schema-to-typescript";
import { kompotVersion } from "./kompot-version.mjs";

const here = dirname(fileURLToPath(import.meta.url));
const root = join(here, "..");
const check = process.argv.includes("--check");

const { kompot } = JSON.parse(readFileSync(join(root, "package.json"), "utf8"));
const { repository } = kompot;
const version = kompotVersion(root);
const jarUrl = `${repository}/io/github/youndie/kompot-spec/${version}/kompot-spec-${version}.jar`;

// The tree and the forms. Wizards and server-driven themes are left out until something draws them:
// generating every module would produce types nothing refers to, and a reader could not tell which
// of them the code is actually held to.
//
// Both are compiled into one file. Generating a file per module makes json-schema-to-typescript
// inline every type kompot-standard references out of kompot-core into both, and the duplicates
// collide the moment anything imports both.
const modules = ["kompot-core", "kompot-standard", "form-core", "form-standard", "kompot-forms"];
const bundle = "kompot.ts";
const wrapperTitle = "KompotWireBundle";

const work = mkdtempSync(join(tmpdir(), "kompot-schema-"));
try {
  const jar = join(work, "kompot-spec.jar");
  const response = await fetch(jarUrl);
  if (!response.ok) throw new Error(`${jarUrl} answered ${response.status}`);
  writeFileSync(jar, Buffer.from(await response.arrayBuffer()));

  execFileSync("unzip", ["-q", jar, "kompot-spec/schema/*", "-d", work]);
  const schemaDir = join(work, "kompot-spec", "schema");
  const present = readdirSync(schemaDir);

  const out = join(root, "src", "generated");
  mkdirSync(out, { recursive: true });

  // Everything lives under $defs, and a schema whose root declares nothing compiles to an interface
  // that declares nothing. The wrapper names every definition so each one is emitted, and is dropped
  // from the result afterwards.
  const properties = {};
  for (const module of modules) {
    const file = `${module}.schema.json`;
    if (!present.includes(file)) throw new Error(`${file} is not in ${jarUrl}`);
    const schema = JSON.parse(readFileSync(join(schemaDir, file), "utf8"));
    for (const name of Object.keys(schema.$defs ?? {})) {
      properties[`${module}__${name}`] = { $ref: `${file}#/$defs/${name}` };
    }
  }

  const wrapperFile = join(schemaDir, "__bundle.schema.json");
  writeFileSync(
    wrapperFile,
    JSON.stringify({
      $schema: "https://json-schema.org/draft/2020-12/schema",
      title: wrapperTitle,
      type: "object",
      properties,
    }),
  );

  const compiled = await compileFromFile(wrapperFile, {
    cwd: schemaDir,
    bannerComment:
      `/* eslint-disable */\n` +
      `// Generated from ${modules.map((m) => m + ".schema.json").join(", ")} in kompot-spec ${version}.\n` +
      `// Do not edit. Regenerate with: pnpm schema`,
    declareExternallyReferenced: true,
    enableConstEnums: false,
    style: { singleQuote: false },
  });

  // Drop the wrapper itself: it exists to make the definitions reachable, not to be used.
  const generated = compiled.replace(
    new RegExp(`export interface ${wrapperTitle} \\{[\\s\\S]*?\\n\\}\\n`, "m"),
    "",
  );
  if (generated.includes(wrapperTitle)) throw new Error("the wrapper interface survived into the output");

  const target = join(out, bundle);
  if (check) {
    const existing = readFileSync(target, "utf8");
    if (existing !== generated) {
      console.error(`${bundle} is not what kompot-spec ${version} generates`);
      process.exit(1);
    }
    console.log(`${bundle} matches kompot-spec ${version}`);
  } else {
    writeFileSync(target, generated);
    console.log(`${bundle} <- ${modules.join(", ")} (kompot-spec ${version})`);
  }
} finally {
  rmSync(work, { recursive: true, force: true });
}
