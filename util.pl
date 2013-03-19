# Utility functions for tagtime.
# This uses settings from ~/.tagtimerc so that must have been loaded first.

use Fcntl qw(:DEFAULT :flock);
use Time::Local;  # more sophisticated packages are Date::Calc and Date::Manip

$lockf = "${path}tagtime.lock";

$| = 1;  # autoflush STDOUT

my $IA = 16807;       # constant used for RNG (see p37 of Simulation by Ross)
my $IM = 2147483647;  # constant used for RNG (2^31-1)

# $seed is a global variable that is really the state of the RNG.
# Should be set in .tagtimerc but set to a default value here if not.
if(!defined($seed)) { $seed = 666; }
my $initseed = $seed;

if(!defined($linelen)) { $linelen = 80; }  # default line length.

# Returns a random integer in [1,$IM-1]; changes $seed, ie, RNG state.
# (This is ran0 from Numerical Recipes and has a period of ~2 billion.)
sub ran0 {
  #if ($seed == 666) { print "WARNING: seed uninitialized!\n"; }
  $seed = $IA*$seed % $IM;
  return $seed;
}

# Returns a U(0,1) random number.
sub ran01 { return ran0()/$IM; }

# Returns a random number drawn from an exponential distribution with mean 
# $gap (defined in settings file).
sub exprand { return -1 * $gap * log(ran01()); }


sub max { my $max = $_[0]; for(@_) { $max = $_ if ($_ > $max); } $max; }
sub min { my $min = $_[0]; for(@_) { $min = $_ if ($_ < $min); } $min; }

sub clip { my($x, $a, $b) = @_;  return max($a, min($b, $x)); }

# Takes previous ping time, returns random next ping time (unixtime).
# NB: this has the side effect of changing the RNG state ($seed)
#     and so should only be called once per next ping to calculate,
#     after calling prevping.
sub nextping { my($prev)=@_; return max($prev+1,round1($prev+exprand())); }

# Computes the last scheduled ping time before time t.
sub prevping {
  my($t) = @_;
  $seed = $initseed;
  # Starting at the beginning of time, walk forward computing next pings
  # until the next ping is >= t.
  my $nxtping = 1184083200;  # the birth of timepie/tagtime!
  my $lstping = $nxtping;
  my $lstseed = $seed;
  while($nxtping < $t) {
    $lstping = $nxtping;
    $lstseed = $seed;
    $nxtping = nextping($nxtping);
  }
  $seed = $lstseed;
  return $lstping;
}

# Strips out stuff in parens and brackets; remaining parens/brackets means
#  they were unmatched.
sub strip {
  my($s) = @_;
  while($s =~ s/\([^\(\)]*\)//g) {}
  while($s =~ s/\[[^\[\]]*\]//g) {}

  # Also remove trailing whitespace? (this breaks cntpings.pl)
  #$s =~ s/\s*$//;

  return $s;
}

# Strips out stuff in brackets only; remaining brackets means they were 
#  unmatched.
sub stripb {
  my($s) = @_;
  while($s =~ s/\s*\[[^\[\]]*\]//g) {}
  $s;
}

# Strips out stuff *not* in parens and brackets.
sub stripc {
  my($s) = @_;
  my $tmp = $s;
  while($tmp =~ s/\([^\(\)]*\)/UNIQUE78DIV/g) {}
  while($tmp =~ s/\[[^\[\]]*\]/UNIQUE78DIV/g) {}
  my @a = split('UNIQUE78DIV', $tmp);
  for(@a) {
    my $i = index($s, $_);
    substr($s, $i, length($_)) = "";
  }
  return $s;
}

# Whether the given string is valid line in a tagtime log file
sub parsable { my($s) = @_;
  $s = strip($s);
  return !(!($s =~ s/^\d+\s+//) || ($s =~ /(\(|\)|\[|\])/));
}

# Fetches stuff in parens. Not currently used.
sub fetchp {
  my($s) = @_;
  my $tmp = $s;
  while($tmp =~ s/\([^\(\)]*\)/UNIQUE78DIV/g) {}
  my @a = split('UNIQUE78DIV', $tmp);
  for(@a) {
    my $i = index($s, $_);
    substr($s, $i, length($_)) = "";
  }
  $s =~ s/^\(//;
  $s =~ s/\)$//;
  return $s;
}

# Extracts tags prepended with colons and returns them space-separated.
#  Eg: "blah blah :foo blah :bar" --> "foo bar"
sub gettags {
  my($s) = @_;
  my @t;
  $s = strip($s);
  while($s =~ s/(\s\:([\w\_]+))//) { push(@t, $2); }
  return join(' ', @t);
}

# Blocking lock -- try to get the lock and wait if we can't.
sub lockb {
  # okFlag is currently just "whether we had to wait at all for the lock"
  my $okFlag = 1;  # false if we had to override the lock or something.
  if($cygwin) {  # stupid windows
    while(-e $lockf) {
      print "TagTime is locked.  Waiting 30 seconds...\n";
      sleep(30);
      $okFlag = 0;
    }
    $cmd = "/usr/bin/touch $lockf";
    system($cmd) == 0 or print "SYSERR: $cmd\n";
  } else {  # nice unix (including mac)
    sysopen(LF, $lockf, O_RDONLY | O_CREAT) or die "Can't open lock file: $!";
    if(!flock(LF, LOCK_EX | LOCK_NB)) {  # exclusive, nonblocking lock.
      print "TagTime is locked.  Waiting...";
      flock(LF, LOCK_EX) or die "Can't lock $lockf: $!";
      print " ready!\n\n";
      $okFlag = 0;
    }
  }
  return $okFlag;
}

# Nonblocking lock -- try to get the lock and return 0 if we can't.
sub lockn {
  if($cygwin) {  # stupid windows
    if(-e $lockf) { return 0; }
    $cmd = "/usr/bin/touch $lockf";
    system($cmd) == 0 or print "SYSERR: $cmd\n";
  } else {  # nice unix (including mac)
    sysopen(LF, $lockf, O_RDONLY | O_CREAT) or die "Can't open lock file: $!";
    # Don't wait if we can't get the lock, the next cron'd version'll get it
    if(!flock(LF, LOCK_EX | LOCK_NB)) { return 0; }
    flock(LF, LOCK_EX) or die "Can't lock $lockf: $!";
  }
  return 1;
}

# Release the lock.
sub unlock {
  if($cygwin) {  # stupid windows
    $cmd = "/bin/rm -f $lockf";
    system($cmd) == 0 or print "SYSERR: $cmd\n";
  } else {  # nice unix
    close(LF);  # release the lock.
  }
}

# Singular or Plural:  Pluralize the given noun properly, if n is not 1. 
#   Eg: splur(3, "boy") -> "3 boys"
sub splur { my($n, $noun) = @_;  return "$n $noun".($n==1 ? "" : "s"); }

# Trim whitespace from front and back of string s.
sub trim { my($s) = @_;  $s =~ s/^\s+//;  $s =~ s/\s+$//;  return $s; }

# Takes a string "foo" and returns "-----foo-----" of length $linelen.
sub divider { my($label) = @_;
  #if(!defined($linelen)) { $linelen = 79; }
  my $n = length($label);
  my $left = int(($linelen - $n)/2);
  my $rt = $linelen - $left - $n;
  return ("-"x$left).$label.("-"x$rt);
}

# Takes 2 strings and returns them concatenated with enough space in the middle
# so the whole string is $x long (default: $linelen).
sub lrjust { my($a, $b, $x) = @_;
  $x = $linelen unless defined($x);
  "$a " . " "x(max(0,$x-length("$a $b"))) . $b;
}

# Annotates a line of text with the given timestamp.
sub annotime {                 # NB: this does not include a newline.
  my($a, $t, $ll) = @_;
  $ll = $linelen unless defined($ll);
  my($yea,$o,$d,$h,$m,$s,$wd) = dt($t);
  my @candidates = (
    #"[$yea.$o.$d $h:$m:$s $wd; r=".round1(time()-$t)."]",
    "[$yea.$o.$d $h:$m:$s $wd]",    # 24 chars
    "[$o.$d $h:$m:$s $wd]",         # 18 chars
    "[$d $h:$m:$s $wd]",            # 15 chars
    "[$o.$d $h:$m:$s]",             # 14 chars
    "[$h:$m:$s $wd]",               # 12 chars
    "[$o.$d $h:$m]",                # 11 chars
    "[$d $h:$m:$s]",                # also 11 so this will never get chosen
    "[$h:$m $wd]",                  #  9 chars
    "[$h:$m:$s]",                   #  8 chars
    "[$d $h:$m]",                   # also 8 so this will never get chosen
    "[$h:$m]",                      #  5 chars
    "[$m]"                          #  2 chars
  );
  for(@candidates) {
    if(length("$a $_") <= $ll) {
      return lrjust($a, $_, $ll-0*24);
    }
  }
  return $a;
}

# append a string to the log file ($logf defined in settings file)
sub slog {
  my($s) = @_;
  open(F, ">>$logf") or die "Can't open log file for writing: $!\n";
  print F $s;
  close(F);
}

# double-digit: takes number from 0-99, returns 2-char string eg "03" or "42".
sub dd { my($n) = @_;  return padl($n, "0", 2); }
  # simpler but less general version: return ($n<=9 && $n>=0 ? "0".$n : $n)

# pad left: returns string x but with p's prepended so it has width w
sub padl {
  my($x,$p,$w) = @_;
  if(length($x) >= $w) { return substr($x,0,$w); }
  return $p x ($w-length($x)) . $x;
}

# pad right: returns string x but with p's appended so it has width w
sub padr {
  my($x,$p,$w) = @_;
  if(length($x) >= $w) { return substr($x,0,$w); }
  return $x . $p x ($w-length($x));
}

# Whether the argument is a valid real number.
sub isnum { my($x)=@_; return ($x=~ /^\s*(\+|\-)?(\d+\.?\d*|\d*\.?\d+)\s*$/); }

# round to nearest integer.
sub round1 { my($x) = @_; return int($x + .5 * ($x <=> 0)); }



# DATE/TIME FUNCTIONS FOLLOW

# Date/time: Takes unixtime in seconds and returns list of
#   year, mon, day, hr, min, sec, day-of-week, day-of-year, is-daylight-time
sub dt { my($t) = @_;
  $t = time unless defined($t);
  my($sec,$min,$hour,$mday,$mon,$year,$wday,$yday,$isdst) = localtime($t);
  $year += 1900;  $mon = dd($mon+1);  $mday = dd($mday);
  $hour = dd($hour);  $min = dd($min); $sec = dd($sec);
  my %wh = ( 0=>"SUN",1=>"MON",2=>"TUE",3=>"WED",4=>"THU",5=>"FRI",6=>"SAT" );
  return ($year,$mon,$mday,$hour,$min,$sec,$wh{$wday},$yday,$isdst);
}

# Time string: takes unixtime and returns a formated YMD HMS string.
sub ts { my($t) = @_;
  my($year,$mon,$mday,$hour,$min,$sec,$wday,$yday,$isdst) = dt($t);
  return "$year-$mon-$mday $hour:$min:$sec $wday";
}

# Human-Compressed Time String: like 0711281947 for 2007-11-28 19:47
sub hcts { my($t) = @_;
  if($t % 60 >= 30) { $t += 60; } # round to the nearest minute.
  my($year,$mon,$mday,$hour,$min,$sec,$wday,$yday,$isdst) = dt($t);
  return substr($year,-2)."${mon}${mday}${hour}${min}";
}

# Seconds to str: takes number of seconds, returns a string like 1d02h03:04:05
sub ss { my($s) = @_;
  my($d,$h,$m);
  my $incl = "s";

  if($s < 0) { return "-".ss(-$s); }

  $m = int($s/60);
  if($m > 0) { $incl = "ms"; }
  $s %= 60;
  $h = int($m/60);
  if($h > 0) { $incl = "hms"; }
  $m %= 60;
  $d = int($h/24);
  if($d > 0) { $incl = "dhms"; }
  $h %= 24;

  return ($incl=~"d" ? "$d"."d" : "").
         ($incl=~"h" ? dd($h)."h" : "").
         ($incl=~"m" ? dd($m).":" : "").
         ($incl!~"m" ? $s : dd($s))."s";
}

# just like above but with the biggest possible unit being hours instead of days
sub ss2 { my($s) = @_;
  my($d,$h,$m);
  my $incl = "s";

  if($s < 0) { return "-".ss2(-$s); }

  $m = int($s/60);
  if($m > 0) { $incl = "ms"; }
  $s %= 60;
  $h = int($m/60);
  if($h > 0) { $incl = "hms"; }
  $m %= 60;

  return ($incl=~"h" ? $h."h" : "").
         ($incl=~"m" ? dd($m).":" : "").
         ($incl!~"m" ? $s : dd($s))."s";
}
       

# Parse ss: takes a string like the one returned from ss() and parses it,
# returning a number of seconds.
sub pss { my($s) = @_;
  $s =~ /^\s*(\-?)(\d*?)d?(\d*?)h?(\d*?)(?:\:|m)?(\d*?)s?\s*$/;
  return ($1 eq '-' ? -1 : 1) * ($2*24*3600+$3*3600+$4*60+$5);
}

# Parse Date: must be in year, month, day, hour, min, sec order, returns
#   unixtime.
sub pd { my($s) = @_;
  my($year, $month, $day, $hour, $minute, $second);

  if($s =~ m{^\s*(\d{1,4})\W*0*(\d{1,2})\W*0*(\d{1,2})\W*0*
                 (\d{0,2})\W*0*(\d{0,2})\W*0*(\d{0,2})\s*.*$}x) {
    $year = $1;  $month = $2;   $day = $3;
    $hour = $4;  $minute = $5;  $second = $6;
    $hour |= 0;  $minute |= 0;  $second |= 0;  # defaults.
    $year = ($year<100 ? ($year<70 ? 2000+$year : 1900+$year) : $year);
  }
  else {
    ($year,$month,$day,$hour,$minute,$second) =
      (1969,12,31,23,59,59); # indicates couldn't parse it.
  }

  return timelocal($second,$minute,$hour,$day,$month-1,$year);
}

1;  # perl wants this for libraries imported with 'require'.


# SCRATCH AREA:

# Implementation of ran0 in C, from numerical recipes:

# #define IA 16807
# #define IM 2147483647
# #define AM (1.0/IM)
# #define IQ 127773
# #define IR 2836
# static long seed = 1;
# long ran0() {
#   long k = (seed)/IQ;
#   seed = IA*((seed) - k*IQ) - IR*k;
#   if (seed < 0) { seed += IM; }
#   return (seed);
# }

# Implementation of ran0 in Mathematica:

# IA = 7^5;  IM = 2^31-1;
# RAN = Rationalize[AbsoluteTime[]*1000,1];
# setSeed[i_] := (RAN = i)
# ran0[] := (RAN = Mod[IA * RAN, IM])
