#!/usr/bin/env bash
# The gate of M7: the action fails a build on a publication with a known defect and passes the
# corrected one.
#
# The pair comes from one stand switched by one word, so the two halves cannot drift into two
# different libraries — and a green run here means the check discriminated, not that it stayed quiet.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
fail() { echo; echo "GATE FAILED: $1"; exit 1; }

# First, because everything below runs the action's body directly and would pass with action.yml
# deleted. A composite action has two halves and only one of them is exercised by running it here.
echo "==> the half only GitHub reads"
python3 "$root/scripts/check-action-manifest.py" || fail "action.yml does not hold up"


echo "==> publishing the sample: 1.0.0 with the defect, 1.0.1 without"
(cd "$root/research-stand/broken-publication" && ./gradlew -q --console=plain publishToMavenLocal -PsampleVersion=1.0.0)
(cd "$root/research-stand/broken-publication" && ./gradlew -q --console=plain publishToMavenLocal -PsampleVersion=1.0.1 -Pfixed)

run() {
  local coordinate="$1"
  local summary="$2"
  set +e
  PROBA_COORDINATES="$coordinate" \
  PROBA_REPOSITORY="file://$HOME/.m2/repository" \
  PROBA_DEEP=true \
  PROBA_FAIL_ON=defect \
  GITHUB_STEP_SUMMARY="$summary" \
  RUNNER_TEMP="${PROBA_WORKSPACE:-/tmp/proba-action}" \
    "$root/scripts/run-action.sh" > "${summary}.log" 2>&1
  local code=$?
  set -e
  echo "$code"
}

defective_summary="$(mktemp)"; corrected_summary="$(mktemp)"

echo "==> the publication with the defect"
defective_code="$(run dev.youndie.proba.sample:lib:1.0.0 "$defective_summary")"
tail -6 "${defective_summary}.log" | sed 's/^/    /'
[ "$defective_code" != "0" ] || fail "the action passed a publication with a known defect"
grep -q "api-unreachable" "$defective_summary" || fail "the summary does not name the check that fired"
grep -q "Token" "$defective_summary" || fail "the summary does not name the class a consumer cannot reach"

echo "==> the same library, corrected"
corrected_code="$(run dev.youndie.proba.sample:lib:1.0.1 "$corrected_summary")"
tail -4 "${corrected_summary}.log" | sed 's/^/    /'
[ "$corrected_code" = "0" ] || fail "the action failed a healthy publication (exit $corrected_code)"
grep -q "0 finding" "$corrected_summary" || fail "the summary does not say the corrected one is clean"

echo
echo "GATE PASSED — the action exits $defective_code on the defect and $corrected_code on the fix"
