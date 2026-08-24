#!/usr/bin/env bash
# The body of the composite action.
#
# proba runs on the runner rather than behind an API, so a private publication is never handed to
# somebody else's service to be looked at. The distribution is built once and then invoked directly:
# through `gradle run` the exit code belongs to Gradle, and the one that matters here is the check's.
set -uo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
repository="${PROBA_REPOSITORY:-https://repo1.maven.org/maven2}"
fail_on="${PROBA_FAIL_ON:-defect}"
deep_flag=""
[ "${PROBA_DEEP:-true}" = "true" ] && deep_flag="--deep"

summary_file="${GITHUB_STEP_SUMMARY:-/dev/null}"
workspace="${RUNNER_TEMP:-/tmp}/proba-consumer"

echo "==> building proba"
"$root/gradlew" --quiet --console=plain -p "$root" :resolver:installDist || exit 70
proba="$root/resolver/build/install/resolver/bin/resolver"
[ -x "$proba" ] || { echo "no distribution at $proba"; exit 70; }

status=0
checked=0
while IFS= read -r line; do
  coordinate="$(printf '%s' "$line" | tr -d '[:space:]')"
  [ -z "$coordinate" ] && continue
  checked=$((checked + 1))

  part="$(mktemp)"
  echo "==> $coordinate"
  "$proba" "$coordinate" --repo "$repository" $deep_flag \
    --fail-on "$fail_on" --summary "$part" --workspace "$workspace" --wrapper "$root"
  code=$?
  cat "$part" >> "$summary_file" 2>/dev/null || true
  rm -f "$part"
  [ "$code" -ne 0 ] && status="$code"
done <<< "${PROBA_COORDINATES:-}"

if [ "$checked" = "0" ]; then
  echo "no coordinate was given"
  exit 64
fi
exit "$status"
