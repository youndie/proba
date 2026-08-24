#!/usr/bin/env bash
# The gate of M1.1: what the tree actually draws, measured in a browser.
#
# The DOM tests hold the encoding — which element carries which style — and jsdom computes no layout,
# so none of them can say whether that encoding produces the right boxes. This one measures them, and
# measures the wrong encoding beside it so that a passing run means the measurement discriminated.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root"

pnpm --filter kompot-web measure
