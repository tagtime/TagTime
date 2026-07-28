#!/bin/bash
# Quals for iterm2-tagtime, covering the two failure modes found 2026-07-27:
# 1. Ghost-window stall: the wrapper must outlive its command by seconds,
#    never minutes, even when iTerm2's close-on-end fails to close an ended
#    session's window (invisible ghost, `exists` true indefinitely, held
#    launch.pl's lock 18-84 min in the field).
# 2. Command-not-run: the wrapper must actually execute the command it was
#    given (a $@-inside-a-function trap once had iTerm2 silently spawning
#    default login shells instead -- and the timing-based quals stayed
#    green, hence the marker files below).
# Replicata = 3 runs with an instant marker-touching command, then 3 runs
#   with a 3-second command.
# Expectata = every marker file appears; every wrapper run exits within
#   15s; the count of VISIBLE TagTime windows does not grow. (Invisible
#   ghosts are macOS deferral debris the wrapper can't reap -- AE close is
#   deferred too -- logged as xt-ghost-survived-close and flushed at the
#   next user activation of iTerm2.)
# NB: each run flashes (or invisibly defers) a red iTerm2 window; needs
#   iTerm2 plus an automation grant for the invoking context.

cd "$(dirname "$0")/.." || exit 1
fails=0

count_tagtime_windows() {
  osascript -e 'tell application "iTerm2" to count (windows whose name contains "TagTime" and visible is true)' 2>/dev/null || echo 0
}

run_within() {  # <seconds> <description> <cmd...>
  budget=$1; desc=$2; shift 2
  ./iterm2-tagtime "$@" >/dev/null 2>&1 &
  wpid=$!
  alive=1
  for t in $(seq 1 $((budget * 2))); do
    kill -0 "$wpid" 2>/dev/null || { alive=0; break; }
    sleep 0.5
  done
  if [ "$alive" = 1 ]; then
    echo "FAIL: $desc: wrapper still alive after ${budget}s"
    kill "$wpid" 2>/dev/null
    wait "$wpid" 2>/dev/null
    fails=$((fails+1))
  else
    wait "$wpid" 2>/dev/null
  fi
}

baseline=$(count_tagtime_windows)
rm -f tmp/qual-iterm2-ran.*

for i in 1 2 3; do
  run_within 15 "marker run $i" /usr/bin/touch "$PWD/tmp/qual-iterm2-ran.$i"
  if [ ! -e "tmp/qual-iterm2-ran.$i" ]; then
    echo "FAIL: marker run $i: command never executed (no marker file)"
    fails=$((fails+1))
  fi
done

for i in 4 5 6; do
  run_within 15 "sleep run $i" /bin/sleep 3
done

after=$(count_tagtime_windows)
if [ "${after:-0}" -gt "${baseline:-0}" ]; then
  echo "FAIL: visible TagTime windows grew from $baseline to $after"
  fails=$((fails+1))
fi
rm -f tmp/qual-iterm2-ran.*

if [ "$fails" = 0 ]; then echo "PASS: all quals green"; else exit 1; fi
