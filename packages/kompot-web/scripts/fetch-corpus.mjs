// Takes the client conformance corpus from the published kompot-client-tck artefact.
//
// The corpus is written by the other side. Reading it here rather than restating it is the point:
// cases restated in this repository would drift towards whatever this implementation happens to do,
// and the one thing they exist to do is disagree with it.
import { execFileSync } from "node:child_process";
import { mkdirSync, mkdtempSync, readFileSync, readdirSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const root = join(here, "..");
const check = process.argv.includes("--check");

const { kompot } = JSON.parse(readFileSync(join(root, "package.json"), "utf8"));
const { tckVersion: version, repository } = kompot;
const jarUrl = `${repository}/io/github/youndie/kompot-client-tck/${version}/kompot-client-tck-${version}.jar`;

const work = mkdtempSync(join(tmpdir(), "kompot-corpus-"));
try {
  const jar = join(work, "tck.jar");
  const response = await fetch(jarUrl);
  if (!response.ok) throw new Error(`${jarUrl} answered ${response.status}`);
  writeFileSync(jar, Buffer.from(await response.arrayBuffer()));
  execFileSync("unzip", ["-q", "-o", jar, "*.json", "-d", work]);

  const index = JSON.parse(readFileSync(join(work, "index.json"), "utf8"));
  const out = join(root, "corpus");
  mkdirSync(out, { recursive: true });

  const wanted = ["index.json", ...index.cases];
  let differences = 0;
  for (const name of wanted) {
    const incoming = readFileSync(join(work, name), "utf8");
    const target = join(out, name);
    if (check) {
      let existing = null;
      try {
        existing = readFileSync(target, "utf8");
      } catch {
        existing = null;
      }
      if (existing !== incoming) {
        console.error(existing === null ? `${name} is missing` : `${name} differs from kompot-client-tck ${version}`);
        differences += 1;
      }
    } else {
      writeFileSync(target, incoming);
    }
  }

  // A case that vanished from this copy is the interesting direction: the corpus grows upstream, and
  // a runner that only reads what it already has cannot notice a clause it was never held to.
  const local = readdirSync(out).filter((name) => name.endsWith(".json"));
  const extra = local.filter((name) => !wanted.includes(name));
  if (extra.length > 0) {
    console.error(`not in kompot-client-tck ${version}: ${extra.join(", ")}`);
    differences += 1;
  }

  if (check && differences > 0) process.exit(1);
  console.log(
    check
      ? `corpus matches kompot-client-tck ${version} — ${index.cases.length} cases`
      : `corpus <- kompot-client-tck ${version} — ${index.cases.length} cases`,
  );
} finally {
  rmSync(work, { recursive: true, force: true });
}
