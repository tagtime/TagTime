#!/usr/bin/env perl
# Given a tagtime log file, and a Beeminder graph to update, call the Beeminder
# API to update the graph.
#
# As a side effect, generate a .bee file from the tagtime log, used as a cache
# to avoid calling the Beeminder API if the tagtime log changed but it did not 
# entail any changes relevant to the given Beeminder graph.

require "$ENV{HOME}/.tagtimerc";
require "${path}util.pl";
require "${path}beemapi.pl";
use Data::Dumper; $Data::Dumper::Terse = 1;
$| = 1; # autoflush
$ping = ($gap+0.0)/3600;  # number of hours per ping

if(@ARGV != 2) { print "Usage: ./beeminder.pl tagtimelog user/slug\n"; exit 1; }

$ttlf    = shift;  # tagtime log filename
$usrslug = shift;  # like alice/weight
$usrslug =~ /^(?:.*?(?:\.\/)?data\/)?([^\+\/\.]*)[\+\/]([^\.]*)/;
($usr, $slug) = ($1, $2);
$beef = "$usr+$slug.bee"; # beef = bee file (cache of data on bmndr)

if(defined(@beeminder)) { # for backward compatibility
  print "Deprecation warning: Get your settings file in line!\n";
  print "Specifically, 'beeminder' should be a hash, not an arry.\n";
  for(@beeminder) {
    @stuff = split(/\s+/, $_); # usrslug and tags
    $us = shift(@stuff);
    $beeminder{$us} = [@stuff];
  }
}
$crit = $beeminder{$usrslug} or die "Can't determine which tags match $usrslug";

# ph (ping hash) maps "y-m-d" to number of pings on that day.
# sh (string hash) maps "y-m-d" to the beeminder comment string for that day.
# bh (beeminder hash) maps "y-m-d" to the bmndr ID of the datapoint on that day.
# ph1 and sh1 are based on the current tagtime log and
# ph0 and sh0 are based on the cached .bee file or beeminder-fetched data.

my $start = time;  # start and end are the earliest and latest times we will
my $end   = 0;     # need to care about when updating beeminder.
# bflag is true if we need to regenerate the beeminder cache file. reasons we'd
# need to: 1. it doesn't exist; 2. any beeminder IDs are missing from the 
# cache file; 3. there are multiple datapoints for the same day.
$bflag = (!-e $beef);
my $bf1 = 0; my $bf2 = 0; my $bf3 = 0; my $bf4 = 0; # why bflag?
$bf1 = 1 if $bflag;
undef %remember; # remember which dates we've already seen in the cache file
if(open(B, "<$beef")) {
  while(my $l = <B>) {
    my($y,$m,$d,$v,$p,$c,$b) = ($l =~ /
      (\d+)\s+          # year
      (\d+)\s+          # month
      (\d+)\s+          # day
      (\S+)\s+          # value
      \"(\d+)           # number of pings
        (?:[^\n\"\(]*)  # currently the string " ping(s)"
        \:\             # the ": " after " pings"
        ([^\[]*)        # the comment string (no brackets)
        (?:\[           # if present,
          bID\:([^\]]*) # the beeminder ID, in brackets
        \])?            # end bracket for "[bID:abc123]"
      \s*\"/x);
    my $ts = "$y-$m-$d";
    $ph0{$ts} = $p;
    $c =~ s/\s+$//;
    $sh0{$ts} = $c;
    $bh{$ts} = $b;
    my $t = pd("$y $m $d");
    $start = $t if $t < $start;
    $end   = $t if $t > $end;
    if(!defined($b) || $b eq "") {
      $bflag = 1;
      $bf2++;
      if($bf2 == 1) {
        print "Problem with this line in cache file:\n$l";
      } elsif($bf2 == 2) {
        print "Additional problems with cache file, which is expected if this ",
              "is your first\ntime updating TagTime with the new Bmndr API.\n";
      }
    }
    ($bflag = $bf3 = 1) if defined($remember{$ts});
    $remember{$ts} = 1;
  }
  close(B);
} else { $bflag = 1; $bf4 = 1; }

if($bflag) { # re-slurp all the datapoints from beeminder
  undef %ph0; undef %sh0; undef %bh;
  $start = time;  # reset these since who knows what happened to them when we
  $end   = 0;     # calculated them from the cache file we decided to toss.

  my $tmp = $beef;  $tmp =~ s/(?:[^\/]*\/)*//; # strip path from filename
  if($bf1) {
    print "Cache file missing ($tmp); recreating... ";
  } elsif($bf2) {
    print "Cache file doesn't have all the Bmndr IDs; recreating... ";
  } elsif($bf3) {
    print "Cache file has duplicate Bmndr IDs; recreating... ";
  } elsif($bf4) {
    print "Couldn't read cache file; recreating... ";
  } else { # this case is impossible
    print "Recreating Beeminder cache ($tmp)[$bf1$bf2$bf3$bf4]... ";
  }
  $data = beemfetch($usr, $slug);
  print "[Bmndr data fetched]\n";
  
  # take one pass to delete any duplicates on bmndr; must be one datapt per day
  my $i = 0;
  undef %remember;
  my @todelete;
  for my $x (@$data) {
    my($y,$m,$d) = dt($x->{"timestamp"});
    my $ts = "$y-$m-$d";
    my $b = $x->{"id"};
    if(defined($remember{$ts})) {
      print "Beeminder has multiple datapoints for the same day. " ,
            "The other id is $remember{$ts}. Deleting this one:\n";
      print Dumper $x;
      beemdelete($usr, $slug, $b);
      push(@todelete,$i);
    }
    $remember{$ts} = $b;
    $i++;
  }

  for my $x (reverse(@todelete)) {
    splice(@$data,$x,1);
  }

  for my $x (@$data) { # parse the bmndr data into %ph0, %sh0, %bh
    my($y,$m,$d) = dt($x->{"timestamp"});
    my $ts = "$y-$m-$d";
    my $t = pd($ts);
    $start = $t if $t < $start;
    $end   = $t if $t > $end;
    my $v = $x->{"value"};
    my $c = $x->{"comment"};
    my $b = $x->{"id"};
    $ph0{$ts} = 0+$c; # ping count is first thing in the comment
    $sh0{$ts} = $c;
    $sh0{$ts} =~ s/[^\:]*\:\s+//; # drop the "n pings:" comment prefix
    # This really shouldn't happen.
    if(defined($bh{$ts})) { die "Duplicate cached/fetched id datapoints for $y-$m-$d: $bh{$ts}, $b.\n", Dumper $x, "\n"; }
    $bh{$ts} = $b;
  }
}

open(T, $ttlf) or die "Can't open TagTime log file: $ttlf\n";
$np = 0; # number of lines (pings) in the tagtime log that match
while(<T>) { # parse the tagtime log file
  if(!/^(\d+)\s*(.*)$/) { die "Bad line in TagTime log: $_"; }
  my $t = $1;     # timestamp as parsed from the tagtime log
  my $stuff = $2; # tags and comments for this line of the log
  my $tags = strip($stuff);
  if(tagmatch($tags, $crit)) {
    my($y,$m,$d) = dt($t);
    $ph1{"$y-$m-$d"} += 1;
    $sh1{"$y-$m-$d"} .= stripb($stuff) . ", ";
    $np++;
    $start = $t if $t < $start;
    $end   = $t if $t > $end;
  }
}
close(T);
# clean up $sh1: trim trailing commas, pipes, and whitespace
for(sort(keys(%sh1))) { $sh1{$_} =~ s/\s*(\||\,)\s*$//; }

#print "Processing datapoints in: ", ts($start), " - ", ts($end), "\n";

my $nquo = 0;  # number of datapoints on beeminder with no changes (status quo)
my $ndel = 0;  # number of deleted datapoints on beeminder
my $nadd = 0;  # number of created datapoints on beeminder
my $nchg = 0;  # number of updated datapoints on beeminder
my $minus = 0; # total number of pings decreased from what's on beeminder
my $plus = 0;  # total number of pings increased from what's on beeminder
my $ii = 0;
for(my $t = daysnap($start)-86400; $t <= daysnap($end)+86400; $t += 86400) {
  my($y,$m,$d) = dt($t);
  my $ts = "$y-$m-$d";
  my $b =  $bh{$ts} || "";
  my $p0 = $ph0{$ts} || 0;
  my $p1 = $ph1{$ts} || 0;
  my $s0 = $sh0{$ts} || "";
  my $s1 = $sh1{$ts} || "";
  if($p0 eq $p1 && $s0 eq $s1) { # no change to the datapoint on this day
    $nquo++ if $b;
    next;
  } 
  if($b eq "" && $p1 > 0) { # no such datapoint on beeminder: CREATE
    $nadd++;
    $plus += $p1;
    $bh{$ts} = beemcreate($usr,$slug,$t, $p1*$ping, splur($p1,"ping").": ".$s1);
  } elsif($p0 > 0 && $p1 <= 0) { # on beeminder but not in tagtime log: DELETE
    $ndel++;
    $minus += $p0;
    beemdelete($usr, $slug, $b);
  } elsif($p0 != $p1 || $s0 ne $s1) { # bmndr & tagtime log differ: UPDATE
    $nchg++;
    if   ($p1 > $p0) { $plus  += ($p1-$p0); } 
    elsif($p1 < $p0) { $minus += ($p0-$p1); }
    beemupdate($usr, $slug, $b, $t, ($p1*$ping), splur($p1,"ping").": ".$s1);
    # If this fails, it may well be because the point being updated was deleted/
    # replaced on another machine (possibly as the result of a merge) and is no
    # longer on the server. In which case we should probably fail gracefully
    # rather than failing with an ERROR (see beemupdate()) and not fixing
    # the problem, which requires manual cache-deleting intervention.
    # Restarting the script after deleting the offending cache is one option,
    # though simply deleting the cache file and waiting for next time is less
    # Intrusive. Deleting the cache files when merging two TT logs would reduce
    # the scope for this somewhat.
  } else {
    print "ERROR: can't tell what to do with this datapoint (old/new):\n";
    print "$y $m $d  ",$p0*$ping," \"$p0 pings: $s0 [bID:$b]\"\n";
    print "$y $m $d  ",$p1*$ping," \"$p1 pings: $s1\"\n";
  }
}

open(F, ">$beef") or die;  # generate the new cache file
for my $ts (sort(keys(%ph1))) {
  my($y,$m,$d) = split(/\-/, $ts);
  my $p = $ph1{$ts};
  my $v = $p*$ping;
  my $c = $sh1{$ts};
  my $b = $bh{$ts};
  print F "$y $m $d  $v \"",splur($p,"ping"),": $c [bID:$b]\"\n";
}
close(F);

my $nd = scalar(keys(%ph1)); # number of datapoints
if($nd != $nquo+$nchg+$nadd) { # sanity check
  print "\nERROR: total != nquo+nchg+nadd ($nd != $nquo+$nchg+$nadd)\n";
}
print "Datapts: $nd (~$nquo *$nchg +$nadd -$ndel), ",
      "Pings: $np (+$plus -$minus) ";
my $r = ref($crit);
if   ($r eq "")       { print "w/ tag $crit";                        }
elsif($r eq "ARRAY")  { print "w/ tags in {", join(",",@$crit), "}"; }
elsif($r eq "Regexp") { print "matching $crit";                      }
elsif($r eq "CODE")   { print "satisfying lambda";                   }
else                  { print "(unknown-criterion: $crit)";          }
print "\n";


# Whether the given string of space-separated tags matches the given criterion.
sub tagmatch { my($tags, $crit) = @_;
  my $r = ref($crit);
  if   ($r eq "")       { return $tags =~ /\b$crit\b/;                         }
  elsif($r eq "ARRAY")  { for my $c (@$crit) { return 1 if $tags =~ /\b$c\b/; }}
  elsif($r eq "CODE")   { return &$crit($tags);                                }
  elsif($r eq "Regexp") { return $tags =~ $crit;                               }
  else { die "Criterion $crit is neither string, array, regex, nor lambda!"; }
  return 0;
}


# Convert a timestamp to noon on the same day. 
# This matters because if you start with some timestamp and try to step 
# forward 24 hours at a time then daylight savings time can screw you up.
# You might add 24 hours and still be on the same day. If you start from 
# noon that you shouldn't have that problem.
sub daysnap { my($t) = @_;
  my($sec,$min,$hr, $d,$m,$y) = localtime($t);
  return timelocal(0,0,12, $d,$m,$y);
}

# $string = do {local (@ARGV,$/) = $file; <>}; # slurp file into string
