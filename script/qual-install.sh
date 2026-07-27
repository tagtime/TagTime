#!/bin/bash
# Quals for install.py, run in a throwaway sandbox with a fake $HOME so the
# real settings.pl and ~/.tagtimerc are never touched.
# Each qual: replicata = the command run, expectata = the exit code and/or
# file state asserted, resultata = printed on failure.

cd "$(dirname "$0")/.." || exit 1
fails=0
fail() { echo "FAIL: $1"; fails=$((fails+1)); }

sandbox=$(mktemp -d)
fakehome="$sandbox/home"
mkdir -p "$fakehome"
cp settings.pl.template install.py "$sandbox/"

# Fresh install: exits 0, generates settings.pl, symlinks ~/.tagtimerc to it.
(cd "$sandbox" && HOME="$fakehome" python3 install.py alice >/dev/null 2>&1) \
  || fail "install.py exited nonzero on fresh install"
[ -f "$sandbox/settings.pl" ] || fail "no settings.pl generated"
[ -L "$fakehome/.tagtimerc" ] || fail "no ~/.tagtimerc symlink created"

# The generated settings have the tokens filled in and none left over.
grep -q '"alice"' "$sandbox/settings.pl" 2>/dev/null || \
  fail "username not substituted into settings.pl"
grep -q '__' "$sandbox/settings.pl" 2>/dev/null && \
  fail "unsubstituted __TOKENS__ remain in settings.pl"

# The generated settings load as perl and are on the POP/EDPOP scheme.
(cd "$sandbox" && perl -e 'require "./settings.pl";
  defined($POP) && $POP =~ /%t/ && defined($EDPOP) or exit 1' 2>/dev/null) \
  || fail "generated settings.pl not loadable with POP and EDPOP"

# Rerunning refuses to clobber an existing settings.pl.
if (cd "$sandbox" && HOME="$fakehome" python3 install.py alice >/dev/null 2>&1)
then fail "install.py clobbered an existing settings.pl"; fi

rm -rf "$sandbox"
if [ "$fails" -eq 0 ]; then echo "PASS: all quals green"; else exit 1; fi
