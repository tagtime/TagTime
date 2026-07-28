#!/bin/bash
# Quals for tagtime-panel, covering what's testable headlessly: exit-status
# propagation and argument errors. The
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

expect_exit 0 "clean child exit propagates 0"       ./TagTime /usr/bin/true
expect_exit 3 "nonzero child exit propagates"       ./TagTime /bin/sh -c "exit 3"
expect_exit 2 "no arguments is a usage error"       ./TagTime
expect_exit 2 "nonexistent command errors"          ./TagTime /nonexistent/cmd
expect_exit 0 "title escape in child output is harmless" \
  ./TagTime /bin/sh -c 'printf "\033]0;QualTitle\007ok\n"'

# Cmd-tab eligibility and label: the app switcher lists apps whose activation
# policy is .regular, which lsappinfo reports as ApplicationType=Foreground
# (.accessory would be UIElement), labeled with the LaunchServices display
# name -- for an unbundled binary that's the executable name, which is the
# whole reason the binary is named TagTime. (LS ignores an embedded
# __info_plist for this, so CFBundleName can't do it; tested 2026-07-27.)
# Needs a live panel to ask about, hence the sleep child, looked up by pid
# since a name lookup misfires when a real ping's panel is also up. (Whether
# cmd-tabbing lands the caret in the field is keyboard behavior -- human-qual
# territory per the header.)
./TagTime /bin/sh -c "sleep 2" >/dev/null 2>&1 &
panelpid=$!
sleep 1
asn=$(lsappinfo find pid=$panelpid)
apptype=$(lsappinfo info -only ApplicationType "$asn")
dispname=$(lsappinfo info -only LSDisplayName "$asn")
wait "$panelpid"
case "$apptype" in
  *Foreground*) ;;
  *) echo "FAIL: expected ApplicationType=Foreground (cmd-tab eligible), got: $apptype"
     fails=$((fails+1)) ;;
esac
case "$dispname" in
  *'="TagTime"'*) ;;
  *) echo "FAIL: expected LSDisplayName=TagTime (cmd-tab label), got: $dispname"
     fails=$((fails+1)) ;;
esac

if [ "$fails" -eq 0 ]; then echo "PASS: all quals green"; else echo "$fails qual(s) red"; exit 1; fi
