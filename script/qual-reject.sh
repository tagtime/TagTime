#!/bin/bash
# Qual for ping.pl's response-rejection behavior.
# Replicata: answer a ping with a response containing the tag "non" (with
#   enforcenonon set), then with a clean response, via piped stdin and a
#   fixture HOME so the real log and Beeminder are untouched.
# Expectata: ping.pl prints a rejection notice for the bad response and logs
#   only the clean one.
# Resultata on failure printed below.

cd "$(dirname "$0")/.." || exit 1
repo=$(pwd)
tmp=$(mktemp -d) || exit 1
trap 'rm -rf "$tmp"' EXIT

cat > "$tmp/.tagtimerc" <<EOF
\$usr = "qualuser";
\$path = "$repo/";
\$logf = "$tmp/qual.log";
\$retrothresh = 60;
\$gap = 45*60;
\$seed = 666;
\$enforcenums = 0;
\$enforcenonon = 1;
%beeminder = ();
1;
EOF
echo "1784000000 seed tags" > "$tmp/qual.log"

out=$(printf 'non autopilot\ngood tags\n' | HOME="$tmp" perl ping.pl 1784000001 2>&1)
fails=0

if ! echo "$out" | grep -qi "enforce"; then
  echo "FAIL: no rejection notice printed for a response with tag 'non'"
  echo "--- ping.pl output was:"; echo "$out"
  fails=$((fails+1))
fi
if grep -q "non autopilot" "$tmp/qual.log"; then
  echo "FAIL: rejected response got logged"
  fails=$((fails+1))
fi
if ! grep -q "good tags" "$tmp/qual.log"; then
  echo "FAIL: clean response did not get logged"
  fails=$((fails+1))
fi

if [ "$fails" -eq 0 ]; then echo "PASS: all quals green"; else echo "$fails qual(s) red"; exit 1; fi
