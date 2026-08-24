#!/usr/bin/env python3
"""kompot is pinned once, and this is what keeps it once.

It used to be pinned three times — the Gradle catalogue the server builds against, and two fields in
packages/kompot-web/package.json that the schema and corpus were fetched by. Renovate reads the
catalogue natively and each of the three resolved on its own, so a single bump produced a repository
pinned at 0.30.0.68, 0.30.0.67 and 0.30.0.68 at once. Nothing else would have noticed: schema:check
and corpus:check each compare a committed copy against *their own* pin and stay green while it drifts
away from the server's.

Now the scripts read the catalogue, so the drift is impossible rather than detected — and this guards
the thing that made it possible, which is a second pin appearing again.
"""
import json
import re
import sys
from pathlib import Path

root = Path(__file__).resolve().parent.parent
problems = []

catalogue = (root / "gradle" / "libs.versions.toml").read_text(encoding="utf-8")
version = re.search(r'^kompot\s*=\s*"([^"]+)"', catalogue, re.MULTILINE)
if not version:
    sys.exit("gradle/libs.versions.toml declares no kompot version")

package = json.loads((root / "packages" / "kompot-web" / "package.json").read_text(encoding="utf-8"))
for field, value in (package.get("kompot") or {}).items():
    # A repository is a place; a version is the thing there must be only one of.
    if field != "repository" and re.search(r"\d+\.\d+", str(value)):
        problems.append(f"packages/kompot-web/package.json pins kompot again as kompot.{field} = {value}")

for name in ("fetch-schema.mjs", "fetch-corpus.mjs"):
    script = (root / "packages" / "kompot-web" / "scripts" / name).read_text(encoding="utf-8")
    if "kompotVersion(" not in script:
        problems.append(f"{name} no longer takes the version from the catalogue")

if problems:
    print("kompot is pinned in more than one place:")
    for problem in problems:
        print(f"  {problem}")
    sys.exit(1)

print(f"kompot is {version.group(1)}, pinned once in gradle/libs.versions.toml and read from there")
