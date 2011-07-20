#!/usr/bin/perl
# This shows your frask (fraction of pings that (1) have a task number 
# and (2) do not contain the smk tag) for our frask contests.

use Getopt::Long; # command line options module.
require "$ENV{HOME}/.tagtimerc";
require "${path}util.pl";

my $target = 1/2;  # target frask.
my $ago = 0;   # default value if not given on command line.
my $weeks = 1;  # default number of weeks to include.
GetOptions("ago=i" => \$ago, "weeks=i" => \$weeks);

# WARNING: this code to compute $start and $end copied from showpie.pl:
# TODO: make a function in util.pl:  drange(ago, weeks)
# that gives start and end for a date range where 'ago' is how many tagtime
# weeks ago to start (always starts on saturday night midnight) and weeks is 
# how many weeks to include.
# Compute start and end to be last saturday night to right now (if $ago==0).
my $start = -1;     # initial value -- the dawn of time.
my $end = time();   # right now.
my $u = 273600+3600;  # the very first saturday night in the history of unix.
                      # TODO: subtract 1 hr from that if we're in daylight time.
my $w = 7*24*3600;  # seconds in a week.
my $k = int(($end-$u)/$w);  # number of sat nights since the dawn of unix.
if ($ago >= 0) {
  $start = ($k-$ago)*$w+$u; # last sat night, or $ago sat nights before.
  $end = min($end, $start+$w*$weeks); # now or start + n weeks, whichever comes first.
}

# hacked this in for post-hooliday frasking:
# $start = 1199336400;  # returned from vacation: 2008-01-03 00:00:00 THU 
$start = 1201306300;  # 12-hour contest by Bee ($5 per dip (under 50%) penalty)

print "DAN: ", frask("dreeves.log");
print "BEE: ", frask("bsoule.log");
print "ROB: ", frask("robfelty.log");


# takes tagtime log and returns frask info.
sub frask {
  my($f)=@_;

  open(F, "<$f") or die "ERROR $f: $!";
  my $e = 0; # unparsable ping lines.
  my $t = 0; # total number of pings.
  my $n = 0; # number of non-afk/off pings.
  my $k = 0; # number of non-afk/off pings with a task tag.

  while(<F>) {
    my $orig = $_;
    my($ts, $rest) = split(/\s+/, $_);
    if($ts < $start || $ts > $end) { next; }
    $t++;
    $_ = strip($_); # strip out stuff in parens and brackets.
    if(!(s/^\d+\s+//) || /(\(|\)|\[|\])/) {
      $e++;
      print "PARSE ERROR: $orig";
    }
    if(!/\b(afk|off)\b/) {
      $n++;
      if(/\b\d+\b/ && $_ !~ /\bsmk\b/) { $k++; }
    }
  }
  close(F);
  return "No pings yet!\n" if $t==0;
  return "No non-afk pings yet!\n" if $n==0;
  return "pings: $t, non-afk: $n, frask: $k/$n = ", round1(1000*$k/$n)/10, "% (need ", ($target*$n-$k)/(1-$target), " taskpings for ", round1(100*$target), "%)\n";
}
