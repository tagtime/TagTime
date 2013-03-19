#!/usr/bin/env ruby
# Started a ruby port; didn't get that far yet...

#require "#{`echo $HOME`.strip}/.tagtimerc" # has to be a .rb file for require?

$IA = 16807;       # constant used for RNG (see p37 of Simulation by Ross).
$IM = 2147483647;  # constant used for RNG (2^31-1).

# $seed is a global variable that is really the state of the RNG.
$gap = 45*60
$seed = 666
$initseed = $seed

# Returns a random integer in [1,$IM-1]; changes $seed, ie, RNG state.
# (This is ran0 from Numerical Recipes and has a period of ~2 billion.)
def ran0()
  $seed = $IA*$seed % $I
end

# Returns a U(0,1) random number.
def ran01()
  ran0()/$IM
end

# Returns a random number drawn from an exponential
# distribution with mean $gap (defined in settings file).
def exprand()
  -1 * $gap * Math.log(ran01())
end

# Takes previous ping time, returns random next ping time (unix time).
# NB: this has the side effect of changing the RNG state ($seed)
#     and so should only be called once per next ping to calculate,
#     after calling prevping.
def nextping(prev)
  [prev+1, (prev+exprand()).round].max
end

# Computes the last scheduled ping time before time t.
def prevping(t)
  $seed = $initseed;
  # Starting at the beginning of time, walk forward computing next pings
  # until the next ping is >= t.
  nxtping = 1184083200  # the birth of timepie/tagtime!
  lstping = nxtping
  lstseed = $seed
  while nxtping < t
    lstping = nxtping
    lstseed = $seed
    nxtping = nextping(nxtping)
  end
  $seed = lstseed
  lstping
end


# MAIN #########################################################################
 
begin
  if ARGV.size < 1 
    puts "USAGE: ..." 
    exit(1) 
  end
  lf = ARGV.pop
  i = 0
  IO.foreach(lf) {|x| i += 1 }
  p i
end 


# Strips out stuff in parens and brackets; remaining parens/brackets means
#  they were unmatched.
#sub strip {
#  my($s)=@_;
#  while($s =~ s/\([^\(\)]*\)//g) {}
#  while($s =~ s/\[[^\[\]]*\]//g) {}
#  $s;
#}

# Strips out stuff *not* in parens and brackets.
#sub stripc {
#  my($s)=@_;
#  my $tmp = $s;
#  while($tmp =~ s/\([^\(\)]*\)/UNIQUE78DIV/g) {}
#  while($tmp =~ s/\[[^\[\]]*\]/UNIQUE78DIV/g) {}
#  my @a = split('UNIQUE78DIV', $tmp);
#  for(@a) {
#    my $i = index($s, $_);
#    substr($s, $i, length($_)) = "";
#  }
#  return $s;
#}

# Extracts tags prepended with colons and returns them space-separated.
#  Eg: "blah blah :foo blah :bar" --> "foo bar"
#sub gettags {
#  my($s)=@_;
#  my @t;
#  $s = strip($s);
#  while($s =~ s/(\s\:([\w\_]+))//) { push(@t, $2); }
#  return join(' ', @t);
#}


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

