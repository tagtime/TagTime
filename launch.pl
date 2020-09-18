#!/usr/bin/env perl
# Check if it's time (or past time) to ping. If so, catch up on missed pings
# and/or launch ping.pl for the current ping.
# This should be called by the daemon (tagtimed.pl) every time a ping is due.

require "$ENV{HOME}/.tagtimerc";
require "${path}util.pl";
require "${path}merge.pl";

# Generate derived settings used only in this file
$remote_server = "$remote_user\@$remote_host";
$remote_log = "$remote_server:$remote_path";
$remote_sshid = $remote_key eq "" ? "" : "-i $remote_key";
$scp_cmd = "scp $remote_sshid";
$ssh_cmd = "ssh $remote_sshid";

$launchTime = mytime();

my $args = join(' ', @ARGV); # supported arguments: test, quiet
my $test =   ($args =~ /\btest\b/);
my $quiet =  ($args =~ /\bquiet\b/);

if($test) {  # just pop up the editor and exit; mainly for testing.
  editor($logf, "TagTime Log Editor (invoked explicitly with \"test\" arg)");
  exit(0);
}

if(!lockn()) { 
  debug("Can't get lock. Exiting.");
  exit(1);
} # Don't wait if we can't get the lock.

# figure out the next ping after the last one that's in the log file
$nxtping = parseping($logf);

if($remote_id ne "" && $nxtping < $launchTime) {
  # If we have a gap, first try to fill in with stuff from the most recent remote log
  fill_remote();
}

$nxtping = parseping($logf);

my $editorFlag = 0;

debug("Filling in RETRO pings ($launchTime <=> $nxtping)");
# First, if we missed any pings by more than $retrothresh seconds for no
# apparent reason, then assume the computer was off and auto-log them.
while($nxtping < $launchTime-$retrothresh) {
  slog(annotime("$nxtping afk off RETRO", $nxtping)."\n");
  $nxtping = nextping($nxtping);
  $editorFlag = 1;
}

# Next, ping for any pings in the last retrothresh seconds.
do {
  while($nxtping <= mytime()) {
    if($nxtping < mytime()-$retrothresh) {
      slog(annotime("$nxtping afk RETRO", $nxtping)."\n");
      $editorFlag = 1;
    } else {
      launch($nxtping);  # this shouldn't complete till you answer.
    }
    my($ts,$ln) = lastln();

    debug("Processing ping response ($ts <=> $nxtping, ef=$editorFlag)");

    # First, check to see if we have remote pings to fill in, if this computer
    # was just sitting with a ping window up while they were being answered elsewhere
    if($ts != $nxtping) {
      my ($rts,$rln) = remoteln();
      if ($rts > $ts) {
        debug("$rts > $ts, filling from remote");

        $verify = nextping(prevping($ts)); # NB: must call prevping before nextping
        if($ts == $verify) {
          fill_remote();
        } else {
          print "Local file has a bad last line:\n$ln";
          $nxtping = prevping($launchTime);
        }
        # re-read
        ($ts,$ln) = lastln();
        
        $verify = nextping(prevping($ts));
        if($ts == $verify) {
          debug("New last timestamp: $ts");
        } else {
          print "Remote file has a bad last line:\n$ln";
          $nxtping = prevping($launchTime);
        }
      } else {
        debug("$rts <= $ts, nothing to fill from remote");
      }
    }

    debug("Checked from remote ($ts <=> $nxtping, ef=$editorFlag)");

    if($ts != $nxtping) { # in case, eg, we closed the window w/o answering.
      # suppose there's a ping window waiting (call it ping 1), and while it's 
      # sitting there unanswered another ping (ping 2) pings.  then you kill 
      # the ping 1 window.  the editor will then pop up for you to fix the err 
      # ping but there will be nothing in the log yet for ping 2.  perhaps 
      # that's ok, just thinking out loud here...
      slog(annotime(
             "$nxtping err [missed ping from ".ss(mytime()-$nxtping)." ago]",
             $nxtping)."\n");
      editor($logf,"TagTime Log Editor (unanswered pings logged as \"err\")");
      $editorFlag = 0;
    } elsif(trim(strip($ln)) eq "") {  # no tags in last line of log.
      #editor($logf, "TagTime Log Editor (add tags for last ping)");
      #$editorFlag = 0;
      $editorFlag = 1;
    }

    debug("Generated err pings ($ts <=> $nxtping, ef=$editorFlag)");

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
} while($nxtping <= mytime());

if($remote_id ne "") {
    debug("Backing up log to remote server...");
    system("$scp_cmd -C $logf $remote_log$usr.$remote_id.log");
    debug("Making commit if remote is a git repo...");
    system("$ssh_cmd $remote_server 'cd $remote_path; [ -d .git ] && git commit --author=\"Tagtime <tagtime\@$remote_id>\" -am \"Backup from $remote_id\"'");
}
unlock();


# Parses a log line as a 2-element array
#   consisting of timestamp and rest of the line.
sub parseln {
  my ($x) = @_;
  $x =~ /^\s*(\d+)\s*(.*)$/;
  return ($1,$2);
}
# Returns the last line in the log as a 2-elm array
sub lastln { 
  my $x;
  open(L, $logf) or die "ERROR-lastln: Can't open log: $!";
  $x = $_ while(<L>);
  close(L);
  return parseln($x);
}

# Returns the last line in the remote log as a 2-elm array
sub remoteln {
  # If we have a gap, first try to fill in with stuff from the most recent remote log
  $remote_line = `$ssh_cmd $remote_server 'cd $remote_path && tail -n1 -q cincodenada.*.log | sort | tail -n1'`;
  return parseln($remote_line);
}

sub fill_remote {
  debug("Downloading remote files...");
  system("$scp_cmd $remote_log$usr.*.log .");
  # Remove our log, we are source of truth for it
  # Otherwise we overwrite our own edits, bleh
  unlink "$usr.$remote_id.log";

  @mergefiles = glob("$path$usr.*.log");

  debug("Merging pings from remote files...");
  if(-e $logf) {
    push(@mergefiles, $logf);
    system("cp $logf $logf.backup");
  }
  open NEWLOG, ">", "$logf.merge";
  print(@mergefiles);
  if(merge(NEWLOG, 0, @mergefiles) == 0) {
    system("mv $logf.merge $logf");
    debug("Merge successful");
  } else {
    editor("$logf.merge", "Merge errors! Please resolve errors manually")
  }
}

# Launch the tagtime pinger for the given time (in unix time).
sub launch {
  my($t) = @_;
  my($sec,$min,$hour) = localtime($t);
  $sec = dd($sec); $min = dd($min); $hour = dd($hour);
  $ENV{DISPLAY} ||= ":0.0";  # have to set this explicitly if invoked by cron.
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

sub parseping {
  local $nxtping, $lastping;
  local ($logf) = @_;
  print("Launch time: $launchTime\n");
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

  return $nxtping;
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
    #my $now = mytime();
    #my $eaten = $now - $prompt;  # subtract amount of time eaten up
    #                               #   answering last ping
    #if ($nxtping<$now && $nxtping>=$now-$retrothresh) {
    #  sleep(max(0, $nxtping - max($lstping,$now-$retrothresh) - $eaten));
    #}
