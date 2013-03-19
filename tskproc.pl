#!/usr/bin/perl
# Process (rearrange and annotate) the tasks file (taken as stdin).
# See tskedit.pl for editing your task file and what this file does.
# Would be nice to annotate the tasks with number of tagtime pings but that 
#   should probably be done post hoc, not part of this script.

my $now = time;

require "$ENV{HOME}/.tagtimerc";
require "${path}util.pl";

my $i = 0;  # counter for invalid lines.
my %h; # hash to keep track of which task numbers were seen.
my @t; # list of active task lines, including blank separator lines.
my $tc = 0; # number of actual active tasks.
my @i; # list of invalid lines.
my @di; # indices of divider lines.
my @x; # completed task lines.

my $etot = 0;  # total estimated time in seconds.
my $dtot = 0;  # total estimated time of done tasks.

while(my $a = <STDIN>) {
  chomp($a);
  if($a =~ /^\!+(.*)$/) { # toggle this line!
    $a = $1;  # ditch the '!'.
    if($a =~ /PAUSE\(\d*\)/) { $a = togPause($a); }
    elsif(valid($a)) {
      if(checked($a)) { $a = uncheck($a); }
      elsif(started($a) && $a =~ /^\d+\s+/) { $a = adde(stamp2(check($a))); }
      # !checked & !started => no toggling, start like any other task.
    }
  }

  $h{$a+0} = 1;  # remember we've seen this number.
  # if a blank line (or only the vim macro marker) in tasks section, leave it:
  if($a =~ /^\s*(TIMEPIFV[RS])?\s*$/ && $i == 0) { push(@t, "$a\n"); }
  elsif (!valid($a)) {
    if (divr($a)) { push(@di, $i); } # @di is indices of divider lines.
    push(@i, "$a\n");
    $i++;
  }
  elsif(checked($a)) {
    if(!started($a)) { push(@t, adde("$a\n"));  $tc++; }
    elsif(ended($a)) { push(@x, "$a\n"); }
    else { push(@x, adde(stamp2($a))); }
  }
  elsif(started($a)) { push(@t, adde("$a\n"));  $tc++; }
  else { push(@t, adde(stamp1("$a\n")));  $tc++; }
}

if($ARGV[0] eq "-sort") { @t = sort {$a <=> $b} @t; }

# print the active tasks, replacing bullets with task numbers.
# also make sure the start time is right now in case this was a previously
# active task with an old start time. (if a task is getting a new number now
# then its start time has to be now.)
# NOTE:
# I made this fix to ensure start times start when the task gets a new number
# on 2010.12.04 so before that there's a problem with getting the task duration
# by counting the tagtime pings with the given task number in the given time
# window.  For example, it's possible that a task was stamped as started on 
# monday, then moved to the inactive area (also monday), then reactivated again 
# on wednesday with task number 7.  The problem is that other task 7's may have 
# appeared between monday and wednesday so when you count the 7's in the tagtime
# log starting monday you'll get too many.  I guess the safe thing to do is 
# every time a task number in the tagtime log matches multiple tasks in the task
# log, scrap all those tasks.
for(@t) { 
  if(/^[o\*\-\#]{1,3}/) {
    s/(\s)\d{10}\-\d*(\s)/$1.hcts($now).'-'.$2/e;
    s/^([o\*\-\#]{1,3})\s*/ntn($1)." "/e;  
  }
  print; 
}

# move the divider lines around so they demarcate the invalid lines...
if($i>0 && $di[0]>0 || $i>1 && $di[-1] < $i-1) { # not already first and last.
  if($i==1) {  # only one dividing line, move to end.
    push(@i, $i[$di[0]]);
    delete $i[$di[0]];
  } else {
    push(@i, $i[$di[-1]]);  # stick last divider on the end.
    delete $i[$di[-1]];
    unshift(@i, $i[$di[0]]);  # stick first divider on the front.
    delete $i[$di[0]+1];
  }
}

# stick in the total estimated time of active tasks
for(@t) { $etot += estim($_); }
for(@x) { $dtot += estim($_); }
$i[0]  =~ s/^(\-{3,}.*?)\(.*?\)/"$1\($tc tasks: ".ss($etot)."\)"/eg;
$i[-1] =~ s/^(\-{3,}.*?)\(.*?\)/"$1\(".scalar(@x)." tasks: ".ss($dtot)."\)"/eg;

for(@i) { print; }  # invalid lines (see valid() function below).
for(@x) { print; }  # lines starting with x or X, ie, completed.

#########################################################################

# Return the estimated time (in seconds) for a task.
sub estim { my($a) = @_;
  my %uh = ( "s" => 1,
	     "m" => 60,
	     "h" => 3600,
	     "d" => 3600*24,
	     "w" => 3600*24*7,
	     "y" => 3600*24*365.25,
	     "c" => 60*45,  # a chrock is 45 minutes (deprecated)
             "t" => 60*45,  # a tock is 45 minutes
             "p" => 60*25,  # a pomodoro (aka a tick) is 25 minutes
	   );
  my($n, $u) = ($a =~ /\~([\d\.]*)(\w)/);
  $n = 1 if $n eq "";
  return $n * $uh{$u};
}

# Return the first available task number, and mark it unavailable.
sub ntn { my($s) = @_;
  for(my $i=0; 1; $i++) {
    if(!$h{$i}) {
      $h{$i} = 1;
      return padl($i, "0", max(length($i),length($s)));
    }
  }
}

# whether the line is formatted as a valid task.
sub valid { my($s)=@_; return $s =~ /^(x\s+)?(\d+|[o\*\-\#]{1,3})\s+\S/i; }
# whether the line counts as a dividing line.
sub divr { my($s)=@_; return $s =~ /^\-{4,}/; }
# whether marked as done (X prepended).
sub checked { my($s)=@_; return $s =~ /^x/i; }
# mark as done (prepend an X).
sub check { my($s)=@_; "X $s"; }
# unmark as done (remove prepended X).
sub uncheck { my($s)=@_; $s =~ s/^(x*\s)*//i; $s; }
# whether $s is a task line that has already been started.
sub started { my($s) = @_; return ($s =~ /\d{10,}\-/); }
# whether $s is a task line that has an end time stamp.
sub ended { my($s) = @_; return $s =~ /\d{10,}\-\d+/; }

# End time: returns string $t but without prefix that is redundant with $s.
# eg, et("abcde", "abxyz") --> "xyz"
# (actually returns "bxyz" to be a valid time, like HHMM)
sub et { my($s, $t) = @_;
  if(length($s) != length($t)) { return $t; }
  my $i;
  for($i=0; $i<length($s)-1; $i++) {
    if(substr($s,$i,1) ne substr($t,$i,1)) {
      if($i % 2 == 1) { $i--; }
      return substr($t,$i-$i%2);
    }
  }
  return substr($t,$i-$i%2);
}

# add start time stamp for right now, assuming no prev start time.
sub stamp1 { my($s)=@_; $s =~ s/(\s*)$/" ".hcts($now)."-$1"/e; $s; }

# add end time stamp for right now, replacing prev end time if any.
sub stamp2 { my($s) = @_;
  $s =~ s/(\d{10,})\-(\d*)/$1."-".et($1,hcts($now))/e;
  $s;
}

# takes paused time (a negative time), pause start, and prefix
# returns a new paused time (also negative)
sub newpt { my($x,$ps,$s) = @_;
  my $ret = ss(pss($x)-$now+phcts($ps,$s));
  if($ret =~ /^\s*\-/) { return $ret; }
  return "-$ret";  # need to add negative sign if "0s".
}

# takes task line and toggles the pause state, if contains "PAUSE(..)"
sub togPause { my($a) = @_;
  if ($a =~ /(\d{10,})\-(\d*)(?:\s+\-\S+)?\s+PAUSE\((\d*)\)/) {
    my $s = $1;  # task start in human-compressed form.
    my $e = $2;  # task end in human-compressed & abbreviated form.
    my $ps = $3;  # pause start in same form as $e.
    if($ps =~ /\d+/) {
      $a =~ s{\d{10,}\-\d*(\s+\-\S+)?\s+PAUSE\(\d+\)}
             {"$s-$e ".newpt($1,$ps,$s)}ex;
    } else {
      $a =~ s{PAUSE\(\)}{"PAUSE(".et($s,hcts($now)).")"}ex;
    }
  }
  $a;
}

# takes task line and adds elapsed time.
sub adde { my($s) = @_;
  my $u;  # units that the estimate was expressed in.
  if($s =~ /\~[\d\.\-]*(\w)/) { $u = $1; }
  if(!($s =~ s{(\d{10,})\-(\d*)
                (\s+\-\S+)?
                (\s+PAUSE\(\d*\))?
                (\s+\=\S+)?}
               {"$1-$2$3$4 =".elapsed($1,$2,pss($3),$u)}ex)) {
    $s =~ s/(NEVERSTARTED\s*)*$/ NEVERSTARTED/;
    return $s;
  }
  $s;
}

# parse human-compressed timestamp, optionally taking prefix from $p
sub phcts { my($x, $p) = @_;
  if(!defined($p)) {
    $x = padr($x,"0",12);
    return pd(join(' ', ($x =~ /(..)(..)(..)(..)(..)(..)/)));
  }
  $x = padl($x,"x",length($p)); 
  $p = padr($p,"0",12);
  $x = padr($x,"0",12);
  my @pa = ($p =~ /(..)(..)(..)(..)(..)(..)/);
  my @xa = ($x =~ /(..)(..)(..)(..)(..)(..)/);
  for(my $i=0; $i<scalar(@pa); $i++) {
    $xa[$i] = ($xa[$i] eq "xx" ? $pa[$i] : $xa[$i]);
  }
  return pd(join(' ', @xa));
}

# elapsed time in units u between a and b given in human-compressed 
#   form, adding $c seconds.
sub elapsed { my($a, $b, $c, $u) = @_;
  if($b eq "") { $b = hcts($now); }
  my $s = phcts($b,$a) - phcts($a) + $c;
  my %uh = ( "s" => 1,
	     "m" => 1.0/60,
	     "h" => 1.0/3600,
	     "d" => 1.0/3600/24,
	     "w" => 1.0/3600/24/7,
	     "y" => 1.0/3600/24/365.25,
	     "c" => 1.0/60/45,  # a chrock is 45 minutes (deprecated)
             "t" => 1.0/60/45,  # a tock is 45 minutes
             "p" => 1.0/60/25,  # a pomodoro (aka a tick) is 25 minutes
	   );
  if(!defined($uh{$u})) {
    if($s<60) { $u = "s"; }
    elsif($s<3600) { $u = "m"; }
    elsif($s<3600*24) { $u = "h"; }
    elsif($s<3600*24*365.25/2) { $u = "d"; }
    else { $u = "y"; }
  }
  return round1(10*$uh{$u}*$s)/10 . $u;
}
