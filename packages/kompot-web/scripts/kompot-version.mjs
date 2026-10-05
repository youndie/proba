import { readFileSync, writeFileSync } from "node:fs";
import { join } from "node:path";

/**
 * The kompot version, read from the one place that pins it.
 *
 * It used to be pinned here as well, which meant three copies of one number and a bot that could
 * move some of them: the server built against one version while these types came from another, and
 * each check compared its own copy against its own pin and stayed green. One pin makes that
 * impossible rather than detectable.
 */
export function kompotVersion(root) {
  const catalogue = readFileSync(join(root, "..", "..", "gradle", "libs.versions.toml"), "utf8");
  const found = catalogue.match(/^kompot\s*=\s*"([^"]+)"/m);
  if (!found) throw new Error("gradle/libs.versions.toml declares no kompot version");
  return found[1];
}

/**
 * Where the jar of one kompot artefact at the pinned version lives.
 *
 * A release (X.Y.Z) is on Central and never in the snapshot repository; a CI-numbered snapshot
 * (X.Y.Z.N) is only in the snapshot repository. The pin says which one it is, so there is no second
 * setting to keep in step with it.
 */
export function kompotJar(root, artifact) {
  const { kompot } = JSON.parse(readFileSync(join(root, "package.json"), "utf8"));
  const version = kompotVersion(root);
  const repository = /^\d+\.\d+\.\d+$/.test(version) ? kompot.releases : kompot.snapshots;
  const url = `${repository}/io/github/youndie/kompot/${artifact}/${version}/${artifact}-${version}.jar`;
  return { version, url };
}

/** Downloads a jar to `path`, failing loudly rather than leaving an HTML error page behind. */
export async function download(url, path) {
  const response = await fetch(url);
  if (!response.ok) throw new Error(`${url} answered ${response.status}`);
  writeFileSync(path, Buffer.from(await response.arrayBuffer()));
}
