#!/bin/bash
# Quals for tagtime-panel, covering what's testable headlessly: exit-status
# propagation, argument errors, and the timing-log instrumentation. The
# visual/keyboard behavior (panel above everything, typing feeds the child)
# needs a human and real pings; it can't be qualed here.
# Each qual: replicata = the command run, expectata = the exit code asserted,
# resultata = printed on failure.
# NB: each invocation flashes a small red panel on screen for an instant.

cd "$(dirname "$0")/.." || exit 1
fails=0

expect_exit() {  # <expected> <description> <cmd...>
  want=$1; desc=$2; shift 2
  "$@" >/dev/null 2>&1
  got=$?
  if [ "$got" -ne "$want" ]; then
    echo "FAIL: $desc (expected exit $want, got $got)"
    fails=$((fails+1))
  fi
}

expect_exit 0 "clean child exit propagates 0"       ./tagtime-panel /usr/bin/true
expect_exit 3 "nonzero child exit propagates"       ./tagtime-panel /bin/sh -c "exit 3"
expect_exit 2 "no arguments is a usage error"       ./tagtime-panel
expect_exit 2 "nonexistent command errors"          ./tagtime-panel /nonexistent/cmd
expect_exit 0 "title escape in child output is harmless" \
  ./tagtime-panel /bin/sh -c 'printf "\033]0;QualTitle\007ok\n"'

before=$(wc -l < tmp/popup-timing.log)
./tagtime-panel /usr/bin/true >/dev/null 2>&1
after=$(wc -l < tmp/popup-timing.log)
if [ $((after - before)) -ne 2 ]; then
  echo "FAIL: expected 2 timing-log lines (xt-start, xt-exit) per run, got $((after - before))"
  fails=$((fails+1))
fi

if [ "$fails" -eq 0 ]; then echo "PASS: all quals green"; else echo "$fails qual(s) red"; exit 1; fi
