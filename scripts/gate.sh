#!/usr/bin/env bash
# The gate of M2, as something that can be run rather than described.
#
# Publishes the sample library twice from one stand — once with the defect, once without — and
# requires proba to tell them apart. A run that cannot publish, or cannot resolve, fails here rather
# than reporting a clean result: "found nothing" and "never looked" must not leave by the same door.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
workspace="${PROBA_WORKSPACE:-/tmp/proba-gate}"
repository="file://$HOME/.m2/repository"

trap 'echo; echo "GATE FAILED"; exit 1' ERR

echo "==> publishing the sample: 1.0.0 with the defect, 1.0.1 without"
(cd "$root/research-stand/broken-publication" && ./gradlew -q --console=plain publishToMavenLocal -PsampleVersion=1.0.0)
(cd "$root/research-stand/broken-publication" && ./gradlew -q --console=plain publishToMavenLocal -PsampleVersion=1.0.1 -Pfixed)

run() {
  "$root/gradlew" -q --console=plain :resolver:run \
    --args="$1 --repo $repository --deep --workspace $workspace --wrapper $root" 2>&1 | grep -v SLF4J
}

echo "==> the publication with the defect"
defective="$(run dev.youndie.proba.sample:lib:1.0.0)"
echo "$defective" | sed 's/^/    /'

echo "==> the same library, corrected"
corrected="$(run dev.youndie.proba.sample:lib:1.0.1)"
echo "$corrected" | sed 's/^/    /'

echo
grep -q 'api-unreachable' <<<"$defective" \
  || { echo "the defective publication was not caught"; exit 1; }
grep -q 'dev.youndie.proba.sample.support.Token' <<<"$defective" \
  || { echo "the finding does not name the class a consumer cannot reach"; exit 1; }
grep -q 'nothing to report' <<<"$corrected" \
  || { echo "the corrected publication was reported on anyway"; exit 1; }

echo "GATE PASSED — the defect is named, and one word of difference clears it"
