#!/usr/bin/env perl
# TagTime daemon: this figures out from scratch when to beep and does so, 
#   continuously, even when the previous ping is still unanswered.
# After each ping it also runs launch.pl (with the 'quiet' arg since
#   this is already doing the beeping) which launches popups or an editor
#   for any overdue pings.
# Might be nice to watch ~/.tagtimerc (aka settings.pl) for changes a la 
#   watching.pl so that we don't have to restart this daemon when settings 
#   change. (does it suffice to just re-require it?)

=head1 NAME

tagtimed - Stochastic tagging of your time!

=head1 SYNPOSIS

    $ tagtimed.pl     # Start tagtime

    $ tagtimed.pl -l  # Start tagtime if not already running.

    $ tagtimed.pl -s  # Start tagtime if not already running, be silent otherwise.

=head1 DESCRIPTION

See L<http://messymatters.com/tagtime/>

You can add the following to your C<.bashrc> or equivalent to make sure TagTime starts
when you open a shell:

    tagtimed.pl -l -s &

=head1 BUGS

The C<-l> switch must be used on all processes for locks to work correctly.

The C<-l> switch will exit Tagtime if it's already running with another user using the C<-l>
switch.

=cut

{
    use strict;
    use warnings;
    use Fcntl qw(:flock);
    use Getopt::Std qw(getopts);

    # Lock our process if required.  For more details, see:
    # http://perltraining.com.au/tips/2005-08-08.html

    # Note: without the '-l' switch, we won't even try to create a lock.

    my %opts = ( l => 0, s => 0);   # [l]ock and [s]ilent

    getopts('ls',\%opts);

    # Lock process if called with -l switch
    if ($opts{l} and not flock(DATA,LOCK_EX|LOCK_NB)) {

        # Oops, we're already locked...
        exit(0) if $opts{s};        # Silent exit on already locked.
        die "Already running\n";    # Noisy exit otherwise.
    }
}

$launchTime = time();

eval {
    require "$ENV{HOME}/.tagtimerc";
};

if ($@) {
    die "$0: $ENV{HOME}/.tagtimerc can't be loaded ($!). Do you need to run install.py?\n"
}


require "${path}util.pl";

my $lstping = prevping($launchTime);
my $nxtping = nextping($lstping);

if($cygwin) { unlock(); }  # on cygwin may have stray lock files around.

$cmd = "${path}launch.pl";        # Catch up on any old pings.
system($cmd) == 0 or print "SYSERR: $cmd\n";

print STDERR "TagTime is watching you! Last ping would've been ",
  ss(time()-$lstping), " ago.\n";

my $start = time();
my $i = 1;
while(1) {
  # sleep till next ping but check again in at most a few seconds in
  # case computer was off (should be event-based and check upon wake).
  sleep(clip($nxtping-time(), 0, 2));
  $now = time();
  if($nxtping <= $now) {
    if($catchup || $nxtping > $now-$retrothresh) {
      if(!defined($playsound)) { print STDERR "\a"; }
      else { system("$playsound") == 0 or print "SYSERR: $playsound\n"; }
    }
    # invokes popup for this ping plus additional popups if there were more
    #   pings while answering this one:
    $cmd = "${path}launch.pl quiet &";
    system($cmd) == 0 or print "SYSERR: $cmd\n";
    print STDERR annotime(padl($i," ",4).": PING! gap ".
			  ss($nxtping-$lstping)."  avg ".
                          ss((0.0+time()-$start)/$i). " tot ".
                          ss(0.0+time()-$start), $nxtping, 72), "\n";
    $lstping = $nxtping;
    $nxtping = nextping($nxtping);
    $i++;
  }
}

# Invoke popup for this ping plus additional popups if there were more pings 
# while answering this one.
sub pingery { 
  # TODO: move everything from launch.pl to here
  return 0;
}

#SCHDEL (scheduled for deletion):
#check if X11 is already running, and if not, start it
#$X11= '/Applications/Utilities/X11.app/Contents/MacOS/X11 &';
#if (-e $X11) {
#	$filename='/tmp/.X0-lock';
#	my $xorg=`ps -A|grep -c 'X11.app'`;
#	print $xorg;
#	#unless (-e $filename || $xorg>1) {
#	unless ($xorg>2) {
#		`$X11`
#	}
#}

__DATA__
This section exists to make it trivial to implement the -l (lock)
feature of tagtimed.
