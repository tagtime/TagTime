#!/usr/bin/perl -w
# For pomodoro contests...
# smack frac = fraction of in-front-of-the-computer time (ie, not "afk") that
# was spent checking email, chatting, surfing, etc.  (Even if some of that is
# legitimate work.)  Tag "smk" means "*smack* get back to real work".
# In other words, of the time spent in front of the computer, what fraction
# was not real work?
# NB: this only works if "smk" is the first tag for every ping where you
# "get smacked" and if all not-in-front-of-the-computer pings include the
# tag "afk".  (Does smk still have to be first?)
# ALSO:
# This shows your frask (fraction of non-afk pings that (1) have a task number
# and (2) do not contain the smk tag) for our frask contests.

use Getopt::Long; # command line options module.
require "$ENV{HOME}/.tagtimerc";
require "${path}util.pl";
my $tmp = $path;  # quelling warning about only using $path once.

@peops = qw(dreeves bsoule); #dgyang robfelty);

my $target = .75;  # target non-smk frac.
my $target2 = .666666666666666666666666; # target job/kibo/etc frac.
my $ago = 0;   # default value if not given on command line.
my $weeks = 1; # default number of weeks to include. (not supported by showpie)
GetOptions("ago=i" => \$ago, "weeks=i" => \$weeks);

for my $i (@peops) {
  print divider(" ${i}'s pie "), "\n";
  my $showit = (0 && $i eq $usr ? 1 : 0);
  system("./showpie.pl --exclude=\"afk|off\" --pie $showit --ago $ago $i.log");
  if (0 && $i eq $usr) {
    system("rsync pie-$i.png pie-$i.html yootles.com:/var/www/html/yootles/outbox");
  }
}

#print divider(" FRASK "), "\n";
print divider(" SMACK FRAC "), "\n";

# WARNING: this code to compute $start and $end copied from showpie.pl:
# TODO: make a function in util.pl:  drange(ago, weeks)
# that gives start and end for a date range where 'ago' is how many tagtime
# weeks ago to start (always starts on saturday night midnight) and weeks is
# how many weeks to include.
# Compute start and end to be last saturday night to right now (if $ago==0).
my $start = -1;     # initial value -- the dawn of time.
my $end = time();   # right now.
my($sec,$min,$hour,$mday,$mon,$year,$wday,$yday,$isdst) = localtime($end);
my $u = 273600+(1-$isdst)*3600; # the very 1st sat night in the history of unix.
my $w = 7*24*3600;  # seconds in a week.
my $k = int(($end-$u)/$w);  # number of sat nights since the dawn of unix.
if ($ago >= 0) {
  $start = ($k-$ago)*$w+$u; # last sat night, or $ago sat nights before.
  $end = min($end, $start+$w*$weeks); # now or start + n weeks, whichever comes first.
}

# hacking in ad hoc start times:
# $start = 1199336400; # returned from vacation: 2008-01-03 00:00:00 THU
# $start = 1201306300; # 12-hour contest by Bee ($5 per dip (under 50%) penalty)
# $start = 1213159500;  # contest with b in pullman
# $start = 1219312800; # 75% work commitment today.
# $start = 1219935600; # 75% work commitment 8/28.
# $start = 1220028458; # D: 2/3 job commitment 8/29 ($20); B: ___.
$start = 1229356800;   # B: back on road, D match B.

print "DAN: ", frask("dreeves.log","job");
print "BEE: ", frask("bsoule.log","kibo");
#print "DAV: ", frask("dgyang.log");
#print "ROB: ", frask("robfelty.log");


# takes tagtime log and returns frask info.
sub frask {
  my($f,$tag)=@_;

  open(F, "<$f") or die "ERROR $f: $!";
  my $e = 0; # unparsable ping lines.
  my $t = 0; # total number of pings.
  my $n = 0; # number of non-afk/off pings.
  my $k = 0; # number of non-afk/off pings with a task tag.
  my $tt = 0; # number of $tag pings.

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
    if(!/\b(afk|off|bab)\b/ || /\b$tag\b/i) {
      $n++;
      #if(/\b\d+\b/ && $_ !~ /\bsmk\b/) { $k++; }
      if($_ !~ /\bsmk\b/) { $k++; }
      if($_ =~ /\b$tag\b/i) { $tt++; }
    }
  }
  close(F);

  my $needtsk;
  my $needtag;
  if($n > 0 && $k/$n < $target) {
    $needtsk = round1(10*($target*$n-$k)/(1-$target))/10;
  } else {
    $needtsk = -round1(10*($k-$target*$n)/$target)/10;
  }
  if($n > 0 && $tt/$n < $target2) {
    $needtag = round1(10*($target2*$n-$tt)/(1-$target2))/10;
  } else {
    $needtag = -round1(10*($tt-$target2*$n)/$target2)/10;
  }

  return "No pings yet! (need 0 pings for ", round1(100*$target), "%)\n" if $t==0;
  return "No non-afk pings yet! (need 0 taskpings for ", round1(100*$target), "%)\n" if $n==0;
  return "non-smk: $k/$n = ", round1(1000*$k/$n)/10, "%, $tag: $tt/$n = ", round1(1000*$tt/$n)/10, "%, (need $needtsk nonsmkpings, $needtag $tag)\n";
}

# this seems to be an old/wrong version of frask()
# takes tagtime log and returns $tag info.
sub tagcount {
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
      #if(/\b\d+\b/ && $_ !~ /\bsmk\b/) { $k++; }
      if($_ !~ /\bsmk\b/) { $k++; }
    }
  }
  close(F);
  return "No pings yet! (need 0 taskpings for ", round1(100*$target), "%)\n" if $t==0;
  return "No non-afk pings yet! (need 0 taskpings for ", round1(100*$target), "%)\\n" if $n==0;
  return "pings: $t, non-afk: $n, non-smk: $k/$n = ", round1(1000*$k/$n)/10, "% (need ", ($target*$n-$k)/(1-$target), " nonsmkpings for ", round1(100*$target), "%)\n";
}

