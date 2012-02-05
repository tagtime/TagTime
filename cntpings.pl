#!/usr/bin/env perl
# Count the number of a pings with given tags in the given time period.
# Related scripts:
#  aux/contest.pl -- for chrock contests
#  aux/frask.pl -- a different kind of chrock contest
#  aux/tot.pl -- customized for keeping track of yahoo job pings
#  aux/showpie.pl -- this one is just wrong given how we currently use tagtime 
#  aux/showvenn.pl -- not sure about this one
#  ../kibo/timepiekib.pl -- converts tagtime log to kib file 

require "$ENV{HOME}/.tagtimerc";
require "${path}util.pl";
use Getopt::Long qw(:config bundling);

my $start = -1;
my $end = ts(time());
my $verbose = 0;
GetOptions("start|s=s"=>\$start, "end|e=s"=>\$end, "verbose|v"=>\$verbose);
$start = pd($start) unless isnum($start);
$end = pd($end) unless isnum($end);
# We might want to include shortcuts for specifying special time ranges like
# "since last saturday night at midnight" or "the last n weeks" or "last week 
# (previous saturday night to last saturday night)".  showpie.pl and frask.pl
# have options like that.

$help = <<"EOF";
USAGE: $0 logfile [boolean expression with pings]
  Available options:
    -s or --start DATE: only include pings on or after DATE
    -e or --end DATE: only include pings strictly before DATE 
    -v or --verbose: include interesting stats and tag breakdown
  where DATE is a string in YMDHMS order with any delimiters you want.
  Eg: cntpings -s2010.07.10 alice.log '(wrk | job) & !slp'
  If more than one boolean expression is given they are OR'd together, so
    cntpings alice.log foo bar
  is the same as 
    cntpings alice.log 'foo|bar'
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
open(LOG, $logfile) or die qq{Cannot open logfile "$logfile" - $!\n};
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
    $m++;
    for(@tags) { $tc{$_}++; }
  } else { $n++; }
}
$start = $first if $start == -1;

if($e>0) { 
  print "Errors in log file: $e. ", 
        "They have to be fixed before pings can be counted:\n";
  print "\n$errstr";
  exit(1);
}
print "$m (", ss2($m*$gap), ") / ", $m+$n, 
        " (", ss2(($m+$n)*$gap), " ~ ", 
              ss2($end-$start), ") = ",
  ($m+$n==0 ? "NaN" : round1(100*$m/($m+$n))), "% [rate: ",
  ss(($end-$start)/($m+1)). "]\n";
  #"NomGap = ". ss2($gap) 
if($verbose) {
  print "Start: ". ts($start). "  (pings before this: $toosoon)".
  "\n  End: ". ts($end).   "  (pings after this:  $toolate)\n";
  print lrjust("PIE:", "\n");
  for(sort {$tc{$b} <=> $tc{$a}} keys %tc) {
    print "  $_ $tc{$_} = ". round1(100*$tc{$_}/$m). "% = ~";
    if($n==0) {
      print ss2($tc{$_}/$m*($end-$start)). " = ".
            ss2($tc{$_}/$m*24*3600). "/day\n";
    } else {
      print ss($tc{$_}*$gap). " = ".
           ss2($tc{$_}*$gap/(($end-$start)/(24*3600))). "/day\n";
    }
  }
}
close(LOG);


# returns whether the boolean tag expression is true for the given line 
# from a log file (assume it's pre-stripped).
sub match {
  my($expr, $line) = @_;
  my %h;

  return 1 if $expr =~ /^\s*\(?\s*\)?\s*$/;

  #$line =~ s/^\d+\s*//;  # remove the timestamp at the beginning of the line.
  #$line = strip($line);  # strip out stuff in parens and brackets.
  for(split(/\s+/, $line)) { $h{$_} = 1; }
  $expr =~ s/([^\|])\|([^\|])/$1\|\|$2/g;
  $expr =~ s/([^\&])\&([^\&])/$1\&\&$2/g;
  $expr =~ s/(\w+)/\$h{$1}/g;
  return eval($expr);
}

