#!/usr/bin/env bash
# The gate of M1.2, against live coordinates and nothing assembled.
#
# The rule discriminates or it is worthless: a check that fires on every library nobody can use, and
# one that fires on none, are equally useless. So the pair is a real publication that excludes
# consumers and two that do not — and all three omit the same metadata, which is what makes the
# absence of that metadata the wrong thing to key on by itself.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
proba="$root/resolver/build/install/resolver/bin/resolver"
[ -x "$proba" ] || "$root/gradlew" -q -p "$root" :resolver:installDist

fail() { echo; echo "GATE FAILED: $1"; exit 1; }

check() {
  "$proba" "$1" ${2:+--repo "$2"} --fail-on none 2>&1 | grep -v '^SLF4J'
}

echo "==> a publication whose classes exclude consumers"
suspect="$(check io.github.youndie:kompot-core:0.27.1.50 https://reposilite.kotlin.website/snapshots)"
grep -q "bytecode-java-version" <<<"$suspect" || fail "the check did not fire on classes that require Java 25"
grep -q "require Java 25" <<<"$suspect" || fail "the finding does not name the version required"
grep -q "not declared" <<<"$suspect" || fail "the finding does not say the metadata is silent"
sed -n '/bytecode-java-version/,+2p' <<<"$suspect" | sed 's/^/    /'

echo "==> two that exclude nobody, and are just as silent in their metadata"
for coordinate in org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0 io.ktor:ktor-client-core:3.4.0; do
  control="$(check "$coordinate")"
  if grep -q "bytecode-java-version" <<<"$control"; then
    sed -n '/bytecode-java-version/,+2p' <<<"$control" | sed 's/^/    /'
    fail "$coordinate was reported, and it excludes nobody"
  fi
  echo "    $coordinate — silent"
done

echo "GATE PASSED — the rule is about the bytecode, not about the missing attribute"
