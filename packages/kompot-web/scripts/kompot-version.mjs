import { readFileSync } from "node:fs";
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
