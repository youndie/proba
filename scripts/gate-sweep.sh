#!/usr/bin/env bash
# The gate of M6: a run across a whole group, filling in as it goes — and an idle channel that is a
# fact rather than a silence.
#
# The second half is the one worth having. A broadcaster with no subscribers succeeds at everything
# it is asked to do; the screen simply never changes, which is indistinguishable from a run that
# found nothing to say. So the run counts what it actually handed to somebody, and this script
# checks that number in both directions: zero with nobody listening, above zero with a listener.
set -euo pipefail

checker="${PROBA_SERVER:-http://127.0.0.1:8081}"
group="${1:-io.github.youndie}"
repo="${2:-https://reposilite.kotlin.website/snapshots}"
id="${group//./_}"

fail() { echo; echo "GATE FAILED: $1"; exit 1; }
state() { curl -sf --max-time 20 "$checker/sweep/$1/state"; }
field() { python3 -c 'import json,sys; print(json.load(sys.stdin)[sys.argv[1]])' "$1"; }

curl -sf --max-time 10 "$checker/health" >/dev/null || fail "the checker is not up (./gradlew :server:run)"

echo "==> with nobody listening"
sweep_id="$(curl -sf --max-time 60 "$checker/sweep/$group?repo=$repo" | python3 -c 'import json,sys; print(json.load(sys.stdin)["schema"]["formId"])')"
for _ in $(seq 1 90); do
  s="$(state "$sweep_id")"
  [ "$(field read <<<"$s")" = "$(field modules <<<"$s")" ] && break
  sleep 1
done
s="$(state "$sweep_id")"
modules="$(field modules <<<"$s")"; read_count="$(field read <<<"$s")"; delivered="$(field framesDelivered <<<"$s")"
subscribers="$(field subscribers <<<"$s")"
echo "    modules=$modules read=$read_count framesDelivered=$delivered subscribers=$subscribers"
# Said before the delivery count, because otherwise a browser tab left open on the sweep page makes
# this half fail with a message about the code.
[ "$subscribers" = "0" ] || fail "something else is subscribed to this topic ($subscribers) — close any open sweep page"
[ "$read_count" = "$modules" ] || fail "the run did not finish ($read_count of $modules)"
[ "$modules" -gt 1 ] || fail "a group of one module does not exercise a sweep"
[ "$delivered" = "0" ] || fail "frames were counted as delivered with nobody subscribed"

echo "==> with a listener"
# fresh=1 on purpose: the address returns the sweep it already has, so that a reader arriving late
# gets a screen with the finished modules on it rather than a page that says "waiting" about work
# that ended before they opened it. Asking for a run to watch is a different request.
topic="$(field topic <<<"$s")"
frames="$(mktemp)"
curl -sN --max-time 90 "$checker/updates/$topic" > "$frames" &
listener=$!
trap 'kill "$listener" 2>/dev/null || true' EXIT
# The stream opens with a frame of its own; waiting for it is what tells "subscribed" from "connected".
for _ in $(seq 1 30); do grep -q "^event: open" "$frames" && break; sleep 1; done
grep -q "^event: open" "$frames" || fail "the stream never said it was open"

sweep_id="$(curl -sf --max-time 60 "$checker/sweep/$group?repo=$repo&fresh=1" | python3 -c 'import json,sys; print(json.load(sys.stdin)["schema"]["formId"])')"
for _ in $(seq 1 90); do
  s="$(state "$sweep_id")"
  [ "$(field read <<<"$s")" = "$(field modules <<<"$s")" ] && break
  sleep 1
done
s="$(state "$sweep_id")"
delivered="$(field framesDelivered <<<"$s")"
received="$(grep -c '^data: {' "$frames" || true)"
echo "    framesDelivered=$delivered  framesReceived=$received"
[ "$delivered" -gt 0 ] || fail "nothing was delivered while a subscriber was connected"
[ "$received" -gt 1 ] || fail "the listener received $received component frames"
grep -q '"componentId":"sweep-status"' "$frames" || fail "the status line never changed"
grep -q 'of .* read' "$frames" || fail "no frame carried progress"

echo "GATE PASSED — $modules modules, $received frames received, and an idle channel reads as 0"
