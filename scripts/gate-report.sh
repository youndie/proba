#!/usr/bin/env bash
# The gate of M5: a report at a permanent address, arriving already drawn.
#
# "Already drawn" is the part worth checking, and it is checked the only way that means anything —
# by looking at what the server sent, not at what a browser ends up showing. A page assembled by
# JavaScript looks identical in a browser and is a blank page to anything that does not run it:
# a link preview, a reader, a crawler, someone on a slow connection.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
coordinate="${1:-io.github.youndie/kompot-core/0.27.0.46}"
repo="${2:-https://reposilite.kotlin.website/snapshots}"
page="http://127.0.0.1:3000/report/${coordinate}?repo=$(python3 -c 'import sys,urllib.parse;print(urllib.parse.quote(sys.argv[1],safe=""))' "$repo")"

fail() { echo; echo "GATE FAILED: $1"; exit 1; }

echo "==> the checker answers"
curl -sf --max-time 10 http://127.0.0.1:8081/health >/dev/null || fail "the Kotlin server is not up (./gradlew :server:run)"

echo "==> the page answers"
html="$(curl -sf --max-time 90 "$page")" || fail "the page did not answer ($page)"

echo "==> what the server sent already contains the report"
grep -q "$(basename "$coordinate" | sed 's/.*//')" <<<"$html" || true
for needle in "kompot-core" "version-in-declared-name" "defect"; do
  grep -qi -- "$needle" <<<"$html" || fail "\"$needle\" is not in the markup the server sent"
done

echo "==> and it is markup, not a script that will fetch it later"
# The findings must be in the HTML itself. If they only appear inside a script payload the page is
# not server-rendered in any sense that matters.
without_scripts="$(python3 - "$html" <<'PY'
import re, sys
print(re.sub(r"<script.*?</script>", "", sys.argv[1], flags=re.S))
PY
)"
grep -q "version-in-declared-name" <<<"$without_scripts" || fail "the report exists only inside a script tag"

echo "==> the address is permanent"
[ "$(curl -sf --max-time 90 "$page" | wc -c)" -gt 1000 ] || fail "the same address did not answer twice"

echo "GATE PASSED — the report is at $page and arrives drawn"
