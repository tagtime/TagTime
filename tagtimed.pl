#!/usr/bin/perl
# This is the tagtime daemon.
# It figures out from scratch (ignoring ~/.nextping) when to beep and does
#   so, continuously, even when the previous ping is still unanswered.
# After each ping it also runs launch.pl (with the 'quiet' arg since
#   this is already doing the beeping) which launches popups or an editor
#   for any overdue pings.
# Might be nice to watch ~/.tagtimerc (aka settings.pl) for changes a la 
#   watching.pl so that we don't have to restart this daemon when settings 
#   change. (does it suffice to just re-require it?)

$launchTime = time();

require "$ENV{HOME}/.tagtimerc";
require "${path}util.pl";

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

my $lstping = prevping($launchTime);
my $nxtping = nextping($lstping);

if($cygwin) { unlock(); }  # on cygwin may have stray lock files around.

system("${path}launch.pl");        # Catch up on any old pings.
system("${path}launch.pl recalc"); # Recalc .nextping in case settings changed.

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
      else { system("$playsound"); }
    }
    # invokes popup for this ping plus additional popups if there were more
    #   pings while answering this one:
    system("${path}launch.pl quiet &");
    print STDERR annotime(padl($i," ",4).": PING! gap ".
			  ss($nxtping-$lstping)."  avg ".
                          ss((0.0+$now-$start)/$i), $nxtping, 55), "\n";
    $lstping = $nxtping;
    $nxtping = nextping($nxtping);
    $i++;
  }
}
