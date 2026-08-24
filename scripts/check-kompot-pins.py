#!/usr/bin/env python3
"""One kompot version, pinned in three places, which have to stay one version.

The server compiles against it, kompot-web generates its types from its schemas, and the conformance
corpus is fetched from it. Renovate reads only the Gradle catalogue on its own, so a bump would move
that and leave the other two behind — a repository built against one version and typed from another,
which nothing else here would notice: `schema:check` and `corpus:check` compare the committed copies
against *their own* pin, and stay green while it drifts away from the server's.

Two things are checked, and the second is the one that rots quietly: that the pins agree, and that
Renovate's custom managers still find them. A field renamed in package.json leaves a manager matching
nothing, and a manager that matches nothing behaves exactly like one with no update to offer.
"""
import json
import re
import sys
from pathlib import Path

root = Path(__file__).resolve().parent.parent
problems = []

catalogue = (root / "gradle" / "libs.versions.toml").read_text(encoding="utf-8")
found = re.search(r'^kompot\s*=\s*"([^"]+)"', catalogue, re.MULTILINE)
if not found:
    sys.exit("gradle/libs.versions.toml declares no kompot version")
pins = {"gradle/libs.versions.toml": found.group(1)}

package = json.loads((root / "packages" / "kompot-web" / "package.json").read_text(encoding="utf-8"))
for field in ("specVersion", "tckVersion"):
    if field not in package.get("kompot", {}):
        problems.append(f"packages/kompot-web/package.json has no kompot.{field}")
    else:
        pins[f"package.json kompot.{field}"] = package["kompot"][field]

if len(set(pins.values())) > 1:
    problems.append("the pins disagree: " + ", ".join(f"{where}={version}" for where, version in pins.items()))

# Renovate's regexes are JavaScript's; python spells a named group differently.
config = json.loads((root / "renovate.json").read_text(encoding="utf-8"))
target = "packages/kompot-web/package.json"
text = (root / target).read_text(encoding="utf-8")
for manager in config.get("customManagers", []):
    if not any(re.search(pattern, target) for pattern in manager["fileMatch"]):
        continue
    for expression in manager["matchStrings"]:
        if not re.search(expression.replace("(?<", "(?P<"), text):
            problems.append(
                f"renovate.json: the manager for {manager['depNameTemplate']} matches nothing in {target}",
            )

if problems:
    print("the kompot pins do not hold together:")
    for problem in problems:
        print(f"  {problem}")
    sys.exit(1)

print(f"kompot is {next(iter(pins.values()))} in all {len(pins)} places, and Renovate can find each of them")
