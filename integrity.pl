#!/usr/bin/env perl
# This is modeled on merge.pl and just reports on any missing or unscheduled
# pings in a log file given on the command line.

# Notes: Collect every timestamp in the given log file, plus all the scheduled
# ping timestamps from the earliest one in the log up to the max of now and the
# latest. Sort that whole collection and walk through it. For each t, if there's
# no ping at time t in the log, missing++, and if t is not a scheduled ping time
# then unsched++.

BEGIN { require "$ENV{HOME}/.tagtimerc"; }
require "$ENV{HOME}/lab/tagtime/util.pl";

die "USAGE: $0 logfile\n" if @ARGV != 1;

# Generate a dummy tagtime log line for timestamp t
sub genmiss { my ($t) = @_; "$t " . annotime('MISSING', $t, 33); }

my $earliest = -1; # earliest timestamp in the log file
my $latest = 0;    # latest timestamp in the log file, maxed with now
my $loglat = 0;    # actual latest timestamp in the log file
my %tash;          # tag hash: maps ping timestamp to tags from the logfile
my %alltimes;      # maps all timestamps to 1
my $logfile = $ARGV[0];
open(LOG, $logfile) or die;
$prevts = 0; # remember the previous timestamp
while($line = <LOG>) {
  if(!parsable($line)) { die "Unparsable line in $logfile:\n$line"; }
  my @tags = split(/\s+/, $line);
  my $ts = shift(@tags);
  if($ts <= $prevts) { die "Non-monotone timestamps in $logfile:\n$line"; }
  $prevts = $ts;
  if($ts < $earliest || $earliest == -1) { $earliest = $ts; }
  if($ts > $latest)                      { $latest   = $ts; }
  $line =~ s/^\d+\s+//;
  chomp($line);
  $tash{$ts} = $line;
  $alltimes{$ts} = 1;
}  
close(LOG);

$loglat = $latest;
my $now = time();
if($now > $latest) { $latest = $now; }
my %sch; # maps timestamps to whether they are a scheduled pings
my $i = prevping($earliest);
$i = nextping($i);
while($i <= $latest) {
  $sch{$i} = 1;
  $alltimes{$i} = 1;
  $i = nextping($i);
}

my $missed = 0;
my $stale = 0;  # missed pings after the log file ends
my $unsched = 0;
my $bafflement = 0; # number of timestamps neither scheduled nor in the logfile
my $m; # remember the last missed ping
my $u; # remember the last unsched ping
for my $t (sort(keys(%alltimes))) {
  if    ( $sch{$t} &&  defined($tash{$t})) { next; } # all is right w/ world
  elsif ( $sch{$t} && !defined($tash{$t}) && $t > $loglat) { $stale++; }
  elsif ( $sch{$t} && !defined($tash{$t})) { $missed++;  $m = genmiss($t); }
  elsif (!$sch{$t} &&  defined($tash{$t})) { $unsched++; $u = "$t ".$tash{$t}; }
  elsif (!$sch{$t} && !defined($tash{$t})) { $bafflement++; }
}

if ($bafflement > 0) { die "Bafflement! This can't be nonzero: $bafflement\n"; }

print "* 0 parse errors\n";
print "* 0 nonmonotonicities\n";

if ($unsched == 0) { print "* 0 unscheduled pings\n"; }
elsif ($unsched == 1) { print "* 1 unscheduled ping:\n  $u\n"; }
else { print "* $unsched unscheduled pings, most recently:\n  $u\n"; }

if ($missed == 0) { print "* 0 missing pings\n"; }
elsif ($missed == 1) { print "* 1 missing ping:\n  $m\n"; }
else { print "* $missed missing pings, most recently:\n  $m\n"; }

print "* " . splur($stale, "ping has", "pings have") . 
      " pung since this log file ended\n"; 
