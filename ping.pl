#!/usr/bin/perl
# Prompt for what you're doing RIGHT NOW.  In the future this should show
# a cool pie chart that lets you click on the appropriate pie slice,
# making that slice grow slightly.  And the slice boundaries could be fuzzy
# to indicate the confidence intervals!  Ooh, and you can drag the
# slices around to change the order so similar things are next to each
# other and it remembers that order for next time!  That's gonna rock.

my $pingTime = time();
my $autotags = "";

require "$ENV{HOME}/.tagtimerc";
require "${path}util.pl";

my $tskf = "$path$usr.tsk";

# if passed a parameter, take that to be timestamp for this ping.
# if not, then this must not have been called by launch. tag as UNSCHED.
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
  print
"Either you were busy answering a previous ping when this tried to pop up,\n";
  print "or your tagtime daemon (tagtimed.pl) stopped running.\n";
  print "Or there's a bug.  ",
    "Just in case it's a bug, please report it on github.\n";
  print divider(""), "\n\n";
}

# walk through the task file, printing the active tasks and capturing the list
# of tags for each task (capturing in a hash keyed on task number).
# TODO: have a function that takes a reference to a tasknum->tags hash and a
# tasknum->fulltaskline hash and populates those hashes, purging them first.
# that way we we're not duplicating most of this walk through code.  one 
# annoyance: we want to print them in the order they appear in the task file.
# maybe an optional parameter to the function that says whether to print the
# tasks to stdout as you encounter them.
if(-e $tskf) {  # show pending tasks
  open(F, "< $tskf") or die "ERROR-tsk: $!\n";
  while(<F>) {
    if(/^\-{4,}/ || /^x\s/i) { print; last; }
    if(/^(\d+)\s+\S/) {
      print;
      $tags{$1} = gettags($_);  # hash mapping task num to tags string.
    } else { print; }
  }
  close(F);
  print "\n";
}

my($s,$m,$h,$d) = localtime($t);
$s = dd($s); $m = dd($m); $h = dd($h); $d = dd($d);
print "It's tag time!  ",
  "What are you doing RIGHT NOW ($h:$m:$s)?\n\n";
my($resp, $tagstr, $comments, $a);
do {
  $resp = <STDIN>;

  # refetch the task numbers from task file; they may have changed.
  if(-e $tskf) {
    open(F, "< $tskf") or die "ERROR-tsk2: $!\n";
    %tags = ();  # empty the hash first.
    while(<F>) {
      if(/^\-{4,}/ || /^x\s/i) { last; }
      if(/^(\d+)\s+\S/) { $tags{$1} = gettags($_); } 
    }
    close(F);
  }

  $tagstr = trim(strip($resp));
  $comments = trim(stripc($resp));
  $tagstr =~ s/\b(\d+)\b/($tags{$1} eq "" ? "$1" : "$1 ").$tags{$1}/eg;
  $tagstr =~ s/\b(\d+)\b/tsk $1/;
  $tagstr .= $autotags;
  $tagstr =~ s/\s+/\ /g;
  $a = annotime("$t $tagstr $comments", $t)."\n";
} while($enforcenums && $tagstr ne "" && ($tagstr !~ /\b(\d+|non$d|afk)\b/));
print $a;
slog($a);

# Send your tagtime log to Beeminder if user has $private = 0.
#   (maybe should do this after retropings too but launch.pl would do that).
if(!$private && $resp !~ /^\s*$/) {
  #print divider(""), "\n";
  #print "Showing your pie while your tagtime log gets uploaded...\n";
  #print divider(""), "\n";
  #system("${path}showpie.pl $logf");
  # We could show historical stats on the tags for the current ping here.
  print divider(" sending your log file to beeminder "), "\n";
  system("scp ${path}$usr.log " . 
             "$usr\@yootles.com:/var/www/html/kibotzer/data/$usr.log");
}

# SCHDEL:  (SCHDEL = scheduled for deletion)
#if(-e $tskf) {
#  print divider(" checking your log and task file into subversion "), "\n";
#  $ret = system("$SVN ci $logf $tskf -m \"AUTO-CHECKIN of $usr's log and task file\"");
#} else {
#  print divider(" checking your log into subversion "), "\n";
#  $ret = system("$SVN ci $logf -m \"AUTO-CHECKIN of $usr's log\"");
#}
#if($ret) {
#  print
#    "ERROR: could not check in your tagtime log! (no network connection?)\n";
#  print
#    "  (This does not affect tagtime, just others' ability to see your latest pie.)\n";
#  print
#    "Please cut and paste the above and send it to dreeves\@yootles.com\n";
#  print "[This message will self-destruct in 90 seconds...]\n";
#  sleep(90);
#}

