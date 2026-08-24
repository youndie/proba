#!/usr/bin/env python3
"""The half of a composite action that only GitHub reads.

`scripts/gate-action.sh` runs the action's body directly, with the environment set by hand. That is
a real check of what the body does, and it would pass with `action.yml` deleted — which is close to
what happened: a replacement character in the manifest made every workflow using the action fail at
*Set up job*, the gate stayed green, and the breakage was found by somebody else's pipeline.

So this reads the manifest the way GitHub does, and then checks the two files against each other
rather than against a list written here — a list of expected inputs is a third place to drift.
"""
import re
import sys
from pathlib import Path

try:
    import yaml
except ImportError:
    sys.exit("PyYAML is needed to read action.yml the way GitHub does")

root = Path(__file__).resolve().parent.parent
problems = []

# 1. It has to parse at all. This is the failure that shipped.
try:
    manifest = yaml.safe_load((root / "action.yml").read_text(encoding="utf-8"))
except Exception as failure:
    sys.exit(f"action.yml is not YAML: {failure}")

declared = set(manifest.get("inputs") or {})
steps = (manifest.get("runs") or {}).get("steps") or []

# What the manifest hands to the body, and which inputs it names doing so.
supplied = {}
for step in steps:
    for variable, expression in (step.get("env") or {}).items():
        supplied[variable] = set(re.findall(r"inputs\.([A-Za-z0-9_-]+)", str(expression)))

# 2. Nothing may be handed from an input that does not exist.
for variable, inputs in supplied.items():
    for name in inputs - declared:
        problems.append(f"{variable} is set from inputs.{name}, which action.yml does not declare")

body = (root / "scripts" / "run-action.sh").read_text(encoding="utf-8")
read = set(re.findall(r"\$\{(PROBA_[A-Z_]+)", body))

# 3. Everything the body reads must be supplied — otherwise it silently takes its default forever.
for variable in sorted(read - set(supplied)):
    problems.append(f"{variable} is read by run-action.sh and set by no step in action.yml")

# 4. And everything declared must reach the body, or it is a promise nothing keeps.
promised = {name for names in supplied.values() for name in names}
for name in sorted(declared - promised):
    problems.append(f"inputs.{name} is declared and never handed to anything")

if problems:
    print("action.yml and its body disagree:")
    for problem in problems:
        print(f"  {problem}")
    sys.exit(1)

print(f"action.yml parses; {len(declared)} input(s), all declared, supplied and read")
