#!/bin/bash
# Quals for the queued-ping title feature: when more pings ping while a
# prompt is still up, ping.pl announces them in the window title via the
# standard xterm title escape (ESC ] 0 ; title BEL); pingtitle() in util.pl
# formats the title.
# Each qual: replicata = the command run, expectata = the exit code and/or
# output asserted, resultata = printed on failure.

cd "$(dirname "$0")/.." || exit 1
fails=0
fail() { echo "FAIL: $1"; fails=$((fails+1)); }

# pingtitle formats the singular title exactly as specced.
got=$(perl -e 'require "./util.pl"; print pingtitle(1)' 2>/dev/null)
want="TagTime (1 additional ping pung after this one)"
[ "$got" = "$want" ] || fail "pingtitle(1): got '$got'"

# ...and pluralizes via splur.
got=$(perl -e 'require "./util.pl"; print pingtitle(2)' 2>/dev/null)
want="TagTime (2 additional pings pung after this one)"
[ "$got" = "$want" ] || fail "pingtitle(2): got '$got'"

# Platform canary: SIGALRM must interrupt a blocking <STDIN> promptly, not
# only after input arrives, else titles would appear only once you type.
out=$( (sleep 2; echo hi) | perl -MTime::HiRes=time -e '
  $SIG{ALRM} = sub { printf "ALRM %f\n", time; };
  alarm(1); $| = 1;
  my $x = <STDIN>; printf "GOT %f\n", time;' )
alrm=$(echo "$out" | awk '/^ALRM/{print $2}')
gott=$(echo "$out" | awk '/^GOT/{print $2}')
if [ -z "$alrm" ] || [ -z "$gott" ] || \
   ! perl -e "exit(($gott - $alrm) >= 0.5 ? 0 : 1)"; then
  fail "SIGALRM did not interrupt <STDIN> promptly (out: $out)"
fi

# End to end: run the real ping.pl in a sandbox whose ping schedule is dense
# (gap=1s), wait 5 seconds before answering, and expect a title escape
# announcing at least one additional ping. (Chance of zero pings in 5
# seconds with gap=1 is e^-5, under 1%, so a failure here is overwhelmingly
# likely to be real; rerun to be sure.)
sandbox=$(mktemp -d)
now=$(date +%s)
cat > "$sandbox/.tagtimerc" <<EOF
\$usr = "alice";
\$path = "$(pwd)/";
\$logf = "$sandbox/alice.log";
\$URPING = $((now - 5));
\$seed = 12345;
\$gap = 1;
1;
EOF
: > "$sandbox/alice.log"
out=$( (sleep 5; echo qualtag) | HOME="$sandbox" ./ping.pl "$now" 2>/dev/null )
case "$out" in
  *"]0;TagTime ("*"additional ping"*) : ;;
  *) fail "ping.pl emitted no title escape for a queued ping" ;;
esac
rm -rf "$sandbox"

if [ "$fails" -eq 0 ]; then echo "PASS: all quals green"; else exit 1; fi
