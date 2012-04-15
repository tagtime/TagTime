#!/usr/bin/env perl
# Check if it's time (or past time) to ping. If so, catch up on missed pings
# and/or launch ping.pl for the current ping.
# This should be called by the daemon (tagtimed.pl) every time a ping is due.

$launchTime = time();

require "$ENV{HOME}/.tagtimerc";
require "${path}util.pl";

my $args = join(' ', @ARGV); # supported arguments: test, quiet
my $test =   ($args =~ /\btest\b/);
my $quiet =  ($args =~ /\bquiet\b/);

if($test) {  # just pop up the editor and exit; mainly for testing.
  editor($logf, "TagTime Log Editor (invoked explicitly with \"test\" arg)");
  exit(0);
}

# figure out the next ping after the last one that's in the log file
if(-e $logf) {
  $lll = `tail -1 $logf`;  # last log line
  $lll =~ /^\s*(\d+)/; # parse out the timestamp for the last line, which better
  $lstping = $1;       # be equal to nextping@prevping of itself.
  $tmp = nextping(prevping($lstping)); # NB: must call prevping before nextping
  if($lstping == $tmp) {
    $nxtping = nextping($lstping);
  } else {
    print "TagTime log file ($logf) has bad last line:\n$lll";
    $nxtping = prevping($launchTime);
  }
} else {
  $nxtping = prevping($launchTime);
}

if(!lockn()) { 
  print "Can't get lock. Exiting.\n" unless $quiet; 
  exit(1); 
} # Don't wait if we can't get the lock.

my $editorFlag = 0;

# First, if we missed any pings by more than $retrothresh seconds for no
# apparent reason, then assume the computer was off and auto-log them.
while($nxtping < $launchTime-$retrothresh) {
  slog(annotime("$nxtping afk off RETRO", $nxtping)."\n");
  $nxtping = nextping($nxtping);
  $editorFlag = 1;
}

# Next, ping for any pings in the last retrothresh seconds.
do {
  while($nxtping <= time()) {
    if($nxtping < time()-$retrothresh) {
      slog(annotime("$nxtping afk RETRO", $nxtping)."\n");
      $editorFlag = 1;
    } else {
      launch($nxtping);  # this shouldn't complete till you answer.
    }
    my($ts,$ln) = lastln();
    if($ts != $nxtping) { # in case, eg, we closed the window w/o answering.
      # suppose there's a ping window waiting (call it ping 1), and while it's 
      # sitting there unanswered another ping (ping 2) pings.  then you kill 
      # the ping 1 window.  the editor will then pop up for you to fix the err 
      # ping but there will be nothing in the log yet for ping 2.  perhaps 
      # that's ok, just thinking out loud here...
      slog(annotime(
             "$nxtping err [missed ping from ".ss(time()-$nxtping)." ago]",
             $nxtping)."\n");
      editor($logf,"TagTime Log Editor (unanswered pings logged as \"err\")");
      $editorFlag = 0;
    } elsif(trim(strip($ln)) eq "") {  # no tags in last line of log.
      #editor($logf, "TagTime Log Editor (add tags for last ping)");
      #$editorFlag = 0;
      $editorFlag = 1;
    }

    $lstping = $nxtping; $nxtping = nextping($nxtping);
    # Here's where we would add an artificial gap of $nxtping-$lstping.
  }
  if($editorFlag) {
    editor($logf, "TagTime Log Editor (fill in your RETRO pings)");
    $editorFlag = 0;
    # when editor finishes there may be new pings missed!
    # that's why we have the outer do-while loop here, to start over if
    #   there are new pings in the past after we finish editing.
  }
} while($nxtping <= time());

unlock();


# Returns the last line in the log but as a 2-element array
#   consisting of timestamp and rest of the line.
sub lastln { 
  my $x;
  open(L, $logf) or die "ERROR-lastln: Can't open log: $!";
  $x = $_ while(<L>);
  close(L);
  $x =~ /^\s*(\d+)\s*(.*)$/;
  return ($1,$2);
}

# Launch the tagtime pinger for the given time (in unix time).
sub launch {
  my($t) = @_;
  my($sec,$min,$hour) = localtime($t);
  $sec = dd($sec); $min = dd($min); $hour = dd($hour);
  #$ENV{DISPLAY} = ":0.0";  # have to set this explicitly if invoked by cron.
  if(!$quiet) {
    if(!defined($playsound)) { print STDERR "\a"; }
    else { system("$playsound") == 0 or print "SYSERR: $playsound\n"; }
  }
  $cmd = "$XT -T 'TagTime ${hour}:${min}:${sec}' " .
    "-fg white -bg red -cr MidnightBlue -bc -rw -e ${path}ping.pl $t";
  system($cmd) == 0 or print "SYSERR: $cmd\n";
  #system("${path}term.sh ${path}ping.pl $t");
}

# Launch an editor to edit file f, labeling the window with title t.
sub editor {
  my($f, $t) = @_;
  $ENV{DISPLAY} ||= ":0.0";  # have to set this explicitly if invoked by cron.
  if(!defined($EDIT_COMMAND)) {
    $cmd = "$XT -T '$t' -fg white -bg red -cr MidnightBlue -bc -rw -e $ED $f";
    system($cmd) == 0 or print "SYSERR: $cmd\n";
    #system("${path}term.sh $ED $f");
  } else {
    $cmd = "$EDIT_COMMAND $f";
    system($cmd) == 0 or print "SYSERR: $cmd\n";
  }
}


# SCHDEL (SCHEDULED FOR DELETION): (discussion and code for artificial gaps)
# It can happen that 2 pings can both occur since we last checked (a minute
# ago if using cron) which means that this script would notice them both *now*
# and ping you twice in a row with zero gap.  That's bad.
# This fixes that by checking if the next ping is overdue but not a
# retro-ping (ie, a *now* ping), in which case pause for the number of
# seconds between lstping and nxtping (but not more than retrothresh seconds).
# UPDATE: There were too many subtle corner cases with the above (like what
# if the computer was turned off (hibernating) while executing sleep()) so
# I got rid of it.  We're now counting on this script being run within a
# couple seconds of each ping else all pings within retrothresh will come up
# with zero gap.
    # if another ping is overdue, mind the gap! (ie delay the 2nd ping so as to
    #   maintain the original gap betw them (but not more than retrothresh)):
    #my $now = time();
    #my $eaten = $now - $prompt;  # subtract amount of time eaten up
    #                               #   answering last ping
    #if ($nxtping<$now && $nxtping>=$now-$retrothresh) {
    #  sleep(max(0, $nxtping - max($lstping,$now-$retrothresh) - $eaten));
    #}
