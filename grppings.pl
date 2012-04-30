#!/usr/bin/env perl
# COPIED FROM cntpings.pl -- should add a cmd line option to cntpings to do this
# Grep for pings matching an expression (or disjunction of expressions).

BEGIN { require "$ENV{HOME}/.tagtimerc"; }
use lib $path, "$path/lib";

require "util.pl";

use Getopt::Long qw(:config bundling);
use TagTime qw(match);

my $start = -1;
my $end = ts(time());
my $verbose = 0;
GetOptions("start|s=s"=>\$start, "end|e=s"=>\$end, "verbose|v"=>\$verbose);
$start = pd($start) unless isnum($start);
$end = pd($end) unless isnum($end);

$help = <<"EOF";
USAGE: $0 logfile [boolean expression with pings]
  Available options:
    -s or --start DATE: only include pings on or after DATE
    -e or --end DATE: only include pings strictly before DATE 
  where DATE is a string in YMDHMS order with any delimiters you want.
  Eg: grppings -s2010.07.10 alice.log '(wrk | job) & !slp'
  If more than one boolean expression is given they are OR'd together, so
    grppings alice.log foo bar
  is the same as 
    grppings alice.log 'foo|bar'
  which means: count the pings in alice.log that are tagged foo or bar.
EOF
die $help if @ARGV < 1;

#die "DEBUG: [", ts($start), "][", ts($end), "]\n";

my %tc;            # tag counts -- hashes from tag to count.
my $first = -1;    # timestamp of the first ping in time range.
my $m = 0;         # number of pings in time range that match.
my $n = 0;         # number of pings in time range that don't match.
my $e = 0;         # number of lines with parse errors.
my $toosoon = 0;   # number of pings before $start.
my $toolate = 0;   # number of pings after $end.
my $errstr = "";   # concatenation of bad lines from log file.

my $logfile = shift;
my $expr = '( ' . join(' )|( ', @ARGV) . ' )';
open(LOG, $logfile) or die;
while(<LOG>) {
  my $line = strip($_);

  my $orig = $_;
  $_ = strip($_);
  if(!(s/^\d+\s+//) || /(\(|\)|\[|\])/) {  # should be a function, 'parseable'
    $e++;
    $errstr .= $orig;
    next;
  }

  my @tags = split(/\s+/, $line);
  my $ts = shift(@tags);
  if($first == -1 || $ts < $first) { $first = $ts; }
  if($ts < $start) { $toosoon++; }
  elsif($ts > $end) { $toolate++; }
  elsif(match($expr, $line)) {
    print $orig;
    $m++;
    for(@tags) { $tc{$_}++; }
  } else { $n++; }
}
close(LOG);
$start = $first if $start == -1;

if($e>0) { 
  print "Errors in log file: $e. Here they are:\n";
  print "\n$errstr";
  exit(1);
}
