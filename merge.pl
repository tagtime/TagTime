#!/usr/bin/env perl
# Merge the tagtime logs given on the command line; output to stdout.
# If only one log file is given this just fills in any missing pings autotagged
# with MISSING and autotags pings that shouldn't be there with UNSCHED.
# NB: The rest of this is not fully implemented yet! 
#     Currently just concatenates the tags from each log file. 
#     Eventual spec follows, and in the meantime you can use it with a single 
#     log file to just sanity check it...
# If multiple log files are given this will properly merge them, like if you
# use tagtime on multiple computers.
# Any ping that, according to the ping schedule, is missing from all the given 
# logs will be added with the autotag MISSING and any pings present in any of 
# the logs that shouldn't be there (again, according to the ping schedule) will
# have the autotag UNSCHED appended.
# For each outputted ping with timestamp t, include the union of the tags with 
# timestamp t in all the given log files, ignoring log files that are tagged
# only with autotags at time t. Unless *all* the log files are tagged only with
# autotags at time t, in wich case go ahead and do the union like normal. 
# Autotags are {MISSING, UNSCHED, RETRO, afk, off, err}.
# The earliest timestamp outputted is the earliest timestamp in all the logs.
# The latest timestamp outputted is the max of now and the latest in the logs.

# Notes: Collect every timestamp in every given log file, plus all the scheduled
# ping timestamps from the earliest one in the logs up to the max of now and the
# latest. Sort that whole collection and walk through it. For each t:
#   missflag = true
#   let @p = {}, a list of ping responses for each log file
#   for each log file l:
#     if tags{l+t} not empty: missflag = false 
#     push(@p, tags{l+t})
#   if sch{t} and missflag: push(@p, "MISSING")
#   if not sch{t}: push(@p, "UNSCHED")
#   print t, join('+', @p)

BEGIN { require "$ENV{HOME}/.tagtimerc"; }
require "util.pl";

die "USAGE: $0 logfile+\n" if @ARGV < 1;

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
    }
  }
  if($sch{$t} && $missflag) { push(@p, annotime('MISSING', $t, 33)); }
  if(!$sch{$t})             { push(@p, 'UNSCHED'); }
  print $t, " ", join(' + ', @p), "\n";
}
