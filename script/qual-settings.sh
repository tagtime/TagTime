#!/bin/bash
# Quals for the popup-command settings scheme: $POP and $EDPOP in the
# settings file, and popcmd() in util.pl which turns $POP into a runnable
# command by substituting the ping's unixtime for the %t placeholder.
# Each qual: replicata = the command run, expectata = the exit code and/or
# output asserted, resultata = printed on failure.

cd "$(dirname "$0")/.." || exit 1
fails=0

fail() { echo "FAIL: $1"; fails=$((fails+1)); }

# popcmd substitutes the ping time for every occurrence of %t.
got=$(perl -e '$POP="pop %t up %t"; require "./util.pl"; print popcmd(1234)' \
      2>/dev/null)
[ "$got" = "pop 1234 up 1234" ] || \
  fail "popcmd substitution (expected 'pop 1234 up 1234', got '$got')"

# popcmd dies loudly if $POP lacks the %t placeholder.
err=$(perl -e '$POP="no placeholder"; require "./util.pl"; popcmd(1)' 2>&1)
if [ $? -eq 0 ] || ! echo "$err" | grep -q "ERROR2213"; then
  fail "popcmd should die re missing %t (got exit 0 or wrong msg: '$err')"
fi

# popcmd dies loudly if $POP is undefined.
err=$(perl -e 'require "./util.pl"; popcmd(1)' 2>&1)
if [ $? -eq 0 ] || ! echo "$err" | grep -q "ERROR2212"; then
  fail "popcmd should die re undefined POP (got exit 0 or wrong msg: '$err')"
fi

# The live settings define $POP (with %t) and $EDPOP, and no longer define
# the retired $XT / $ED / $EDIT_COMMAND.
perl -e 'require "./settings.pl";
  defined($POP) && $POP =~ /%t/ && defined($EDPOP) or exit 1;
  defined($XT) || defined($ED) || defined($EDIT_COMMAND) and exit 1;
  exit 0' || fail "settings.pl not on the POP/EDPOP scheme"

# Ditto for the template (textual check; the template is not runnable as-is).
if ! grep -q '^\$POP = .*%t' settings.pl.template || \
   ! grep -q '^\$EDPOP = ' settings.pl.template || \
   grep -q '__XT__\|__ED__' settings.pl.template; then
  fail "settings.pl.template not on the POP/EDPOP scheme"
fi

# Everything still compiles.
for f in util.pl launch.pl; do
  perl -c "$f" >/dev/null 2>&1 || fail "$f does not compile"
done

if [ "$fails" -eq 0 ]; then echo "PASS: all quals green"; else exit 1; fi
