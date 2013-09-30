#!/usr/bin/env perl
# Prompt for what you're doing RIGHT NOW.
# In the future this should show a cool pie chart that lets you click on the 
# appropriate pie slice, making that slice grow slightly. And the slice 
# boundaries could be fuzzy to indicate the confidence intervals! Ooh, and you 
# can drag the slices around to change the order so similar things are next to 
# each other and it remembers that order for next time! That's gonna rock.

eval {
  # Load Term::ANSIColor if available.
  require Term::ANSIColor;
  Term::ANSIColor->import(':constants');
};

my $pingTime = time();
my $autotags = "";

require "$ENV{HOME}/.tagtimerc";
require "${path}util.pl";

my $tskf = "$path$usr.tsk";

my $eflag = 0; # if any problems then prompt before exiting

# if passed a parameter, take that to be the timestamp for this ping.
# if not, then this must not have been called by launch.pl so tag as UNSCHED.
$t = shift;
if(!defined($t)) {
  $autotags .= " UNSCHED";
  $t = time();
}

# Can't lock the same lockfile here since launch.pl will have the lock!
# This script may want to lock a separate lock file, just in case multiple
# instances are invoked, but launch.pl will only launch one at a time.
#lockb();  # wait till we can get the lock.

if($pingTime-$t > 9) {
  print divider(""), "\n";
  print divider(" WARNING "x8), "\n";
  print divider(""), "\n";
  print "This popup is ", ($pingTime-$t), " seconds late.\n";
  print <<EOS;
Either you were answering a previous ping when this tried to pop up, or you just
started the tagtime daemon (tagtimed.pl), or your computer's extremely sluggish.
EOS
  print divider(""), "\n\n";
}

# walk through the task file, printing the active tasks and capturing the list
# of tags for each task (capturing in a hash keyed on task number).
# TODO: have a function that takes a reference to a tasknum->tags hash and a
# tasknum->fulltaskline hash and populates those hashes, purging them first.
# that way we we're not duplicating most of this walk through code. one 
# annoyance: we want to print them in the order they appear in the task file.
# maybe an optional parameter to the function that says whether to print the
# tasks to stdout as you encounter them.
if(-e $tskf) {  # show pending tasks
  if(open(F, "<$tskf")) {
    while(<F>) {
      if(/^\-{4,}/ || /^x\s/i) { print; last; }
      if(/^(\d+)\s+\S/) {
        print;
        $tags{$1} = gettags($_);  # hash mapping task num to tags string
      } else { print; }
    }
    close(F);
  } else {
    print "ERROR: Can't read task file ($tskf)\n";
    $eflag++;
  }
  print "\n";
}

my($s,$m,$h,$d) = localtime($t);
$s = dd($s); $m = dd($m); $h = dd($h); $d = dd($d);


print "It's tag time!  What are you doing RIGHT NOW ($h:$m:$s)?\n";

# Get what the user was last doing. In the case this fails, set $eflag and
# print the reason why.

my $last_doing = eval { get_last_doing() };
$last_doing = trim($last_doing);
if($@) { $eflag++; warn "ERROR: $@" }

my $ansi_last_doing = $last_doing;

if($INC{'Term/ANSIColor.pm'}) {
  # Yay! We can do fancy formatting
  $ansi_last_doing = CYAN() . BOLD() . $last_doing . RESET();
}

print qq{Ditto (") to repeat prev tags: $ansi_last_doing\n\n};

my($resp, $tagstr, $comments, $a);
do {
  use strict;
  use warnings;

  our (%tags, $t);

  $resp = <STDIN>;

  if ($resp =~ /^"\s*$/) {
    # Responses for lazy people. A response string consisting of only
    # a pair of double-quotes means "ditto", and acts as if we entered
    # the last thing that was in our TagTime log file.

    $resp = $last_doing;
  }

  # refetch the task numbers from task file; they may have changed.
  if(-e $tskf) {
    if(open(F, "<$tskf")) {
      %tags = ();  # empty the hash first.
      while(<F>) {
        if(/^\-{4,}/ || /^x\s/i) { last; }
        if(/^(\d+)\s+\S/) { $tags{$1} = gettags($_); } 
      }
      close(F);
    } else {
      print "ERROR: Can't read task file ($tskf) again\n";
      $eflag++;
    }
  }

  $tagstr = trim(strip($resp));
  $comments = trim(stripc($resp));
  $tagstr =~ s/\b(\d+)\b/($tags{$1} eq "" ? "$1" : "$1 ").$tags{$1}/eg;
  $tagstr =~ s/\b(\d+)\b/tsk $1/;
  $tagstr .= $autotags;
  $tagstr =~ s/\s+/\ /g;
  $a = annotime("$t $tagstr $comments", $t)."\n";
} while($tagstr ne "" &&
        ($enforcenums  && ($tagstr !~ /\b(\d+|non|afk)\b/) ||
         $enforcenonon && ($tagstr =~ /\bnon\b/)));
print $a;
slog($a);

# Send your TagTime log to Beeminder if user has %beeminder hash non-empty.
#   (maybe should do this after retropings too but launch.pl would do that).
if((%beeminder || @beeminder) && $resp !~ /^\s*$/) {
  # We could show historical stats on the tags for the current ping here.
  print divider(" sending your tagtime data to beeminder "), "\n";
  if(@beeminder) {  # for backward compatibility
    for(@beeminder) { print "$_: "; @tmp = split(/\s+/, $_); bm($tmp[0]); }
  } else {
    for(keys(%beeminder)) { print "$_: "; bm($_); }
  }
  if($eflag) {
    print splur($eflag,"error"), ", press enter to dismiss...";
    my $tmp = <STDIN>;
  }
}

# Send pings to the given beeminder goal, e.g. passing "alice/foo" sends
# appropriate (as defined in .tagtimerc) pings to bmndr.com/alice/foo
sub bm { my($s) = @_;
  $cmd = "${path}beeminder.pl ${path}$usr.log $s";
  if(system($cmd) != 0) {
    print "ERROR running command: beeminder.pl $usr.log $s\n";
    $eflag++;
  }
}

# Return what the user was last doing by extracting it from their logfile.
# Timestamps and comments are removed.
# On error, throws an exception. (You can catch this with Try::Tiny or eval)
sub get_last_doing {
  use strict;
  use warnings;
  use Tie::File;  # For people too lazy to find the last line of a file. :)

  our $logf;

  tie(my @loglines, 'Tie::File', $logf) or die "Can't open $logf for ditto function: $!";

  my $last = $loglines[-1];

  my ($resp) = $last =~ m{
    ^
    \d+        # Timestamp
    \s+        # Spaces after timestamp
    (.*)       # Om nom nom
  }x;

  if(not $resp) {
    die "ERROR: Failed to find any tags for ditto function. " .
        "Last line in TagTime log:\n" . $last;
  }

  return strip($resp);   # remove comments and timestamps
}
