#!/usr/bin/env perl
# Take a tagtime log on the command line and spit out a csv version.

BEGIN { require "$ENV{HOME}/.tagtimerc"; }
require "util.pl";

die "USAGE: $0 logfile\n" if @ARGV != 1;

my $e = 0;         # number of lines with parse errors
my $errstr = "";   # concatenation of bad lines from log files
my $earliest = -1; # earliest timestamp in all the log files
my $latest = 0;    # latest timestamp in all the log files
my %th;            # maps logfile+timestamp to tags for that log for that ping
my %alltimes;      # maps all timestamps to 1
for my $logfile (@ARGV) {
  open(LOG, $logfile) or die;
  $prevts = 0; # remember the previous timestamp
  while($line = <LOG>) {
    if(!parsable($line)) {
      $e++;
      $errstr .= $line;
      next;
    }
    my @tags = split(/\s+/, $line);
    my $ts = shift(@tags);
    if($ts <= $prevts) {
      $e++;
      $errstr .= "NON-MONOTONE:\n$line";
      next;
    }
    $prevts = $ts;
    if($ts < $earliest || $earliest == -1) { $earliest = $ts; }
    if($ts > $latest)                      { $latest   = $ts; }
    $line =~ s/^\d+\s+//;
    chomp($line);
    $th{$logfile.$ts} = $line;
    $alltimes{$ts} = 1;
  }  
  close(LOG);
}

if($e>0) { 
  print "Errors in log file(s): $e. ", 
        "They have to be fixed before this script can run:\n";
  print "\n$errstr";
  exit(1);
}

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

for my $t (sort(keys(%alltimes))) {
  my $missflag = 1;
  my @p = ();
  for my $l (@ARGV) {
    if(defined($th{$l.$t})) { 
      $missflag = 0; 
      push(@p, $th{$l.$t});
      my @q = split(' ', strip($th{$l.$t}));
      for my $x (@q) {
        print "$t, $x\n";
      }
    }
  }
  #if($sch{$t} && $missflag) { print annotime('MISSING', $t, 33), "\n";; }
  #if(!$sch{$t})             { print "$t UNSCHED ", join(' ', @p), "\n"; }
  #print $t, " ", join(' + ', @p), "\n";
}
