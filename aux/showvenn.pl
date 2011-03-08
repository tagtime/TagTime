#!/usr/bin/perl
# This doesn't work anymore. Use cntpings.pl instead.
# Show the pie!
# Usage: ./showpie.pl logfile
# This currently assumes exactly one tag per line in the log file.
# Anything after that first tag will be ignored.
# Shows your pie for the week we're currently in, or a number of weeks
#   ago specified by $ago, or shows your pie for all time if $ago == -1.
# OPTIONS
# --ago
#   Specify $ago on the command line with -ago:
#   0 = show pie for the current week starting last saturday night.
#   1 = show pie for the previous week, up till last saturday night.
#   i = show pie for the week i weeks ago.
#  -1 = show pie for all time.
#  TODO: specify number of weeks to include, like frask.pl does.
# --pie
#   pie=1 will make a visual representation of your timepie
# --exclude
#   excludes pings matching given regexp.
#   examples: --exclude='slp|job'  (excludes all slp and all job pings)
#             --exclude='mt[yp]|sl.'
# --keep
#   includes only pings matching given regexp.
#   example: --keep='job'  (excludes all pings unless they include job tag)

require "$ENV{HOME}/.tagtimerc";
require "${path}util.pl";
use Getopt::Long; # command line options module.
use Algorithm::Combinatorics qw(combinations);

(@ARGV >= 1) or die "Usage: $0 logFile\n";
my $ago = 0;   # default value if not given on command line.
my $pie = 0;   # default value if not given on command line.
my $venn = 0;
my $exclude='^NEVERLLHAPPEN$'; # default value for regexp of things to exclude.
my $keep='.*';  # default value for regexp of things to keep.
GetOptions("ago=i"=>\$ago, "pie=i"=>\$pie, "venn=i"=>\$venn, 
           "exclude=s"=>\$exclude, "keep=s"=>\$keep);
my $excludeQ = ($exclude ne '^NEVERLLHAPPEN$');
my $keepQ = ($keep ne '.*');
my($ts, $tag);  # timestamp and tag.
my %tc;        # tag counts -- hashes from tag to count.
my %tc_all;
my %tc_ol;		# tag counts with overlap (tag1||tag2 = 1 means tag1 and tag2 overlapped once)
my @times;     # list of timestamps.
my $n;         # total number of pings.
my $toosoon = 0;   # number of pings before $start that aren't counted.
my $toolate = 0;   # number of pings after $end that aren't counted.
my $filtered = 0;  # number of pings filtered out.

# Compute start and end to be last saturday night to right now (if $ago==0).
my $start = -1;     # initial value -- the dawn of time.
my $end = time();   # right now.
my $u = 273600+3600;  # the very first saturday night in the history of unix.
my @tmp = localtime(time);
$u -= 3600 if $tmp[-1]; # subtract an hour from that if we're in daylight time.
my $w = 7*24*3600;  # a week.
my $k = int(($end-$u)/$w);  # number of sat nights since the dawn of unix.
if ($ago >= 0) {
  $start = ($k-$ago)*$w+$u; # last sat night, or $ago sat nights before.
  $end = min($end, $start+$w); # now or start + 1 week, whichever comes first.
}
my $logFile=$ARGV[0];
my @logFilePart=split(/\./,$logFile);
my $username=$logFilePart[0];
open(LOG,$logFile) || die("could not open $logFile");
while(<LOG>) {
  my $line = strip($_);
  if($line =~ /[\d+]\s([\d\w\s]*)/) {
    my @tag_line = split(/\s+/, $1);
	#print @tag_line;
	@tags_only = grep(!/(RETRO|[\d*]|$exclude)/, @tag_line);
	#print @tags_only;
  }
  ($ts, $tag) = split(/\s+/, $_);  # ignoring all but first tag!
  if($ts<$start) { $toosoon++; }
  elsif($ts>$end) { $toolate++; }
  elsif(eval("(\$line =~ /$exclude/) || (\$line !~ /$keep/)")) { $filtered++; }
  else {
    push(@times, $ts);
    $num_tags = @tags_only;
    my @tags_sorted = sort @tags_only; #lexical sort
    for($i=1; $i <= $num_tags; $i++) {
      my $iter = combinations(\@tags_sorted, $i);
      while(my $c = $iter->next) {
#       print @$c;
#       print join('||', @$c);
        $tc_ol{join('||', @$c)}++;
      }
    }
    foreach (@tags_sorted) { $tc_all{$_}++; }
#	print %tc_ol;
    $tc{$tag}++;
  }
}
if ($start == -1) { $start = min(@times); }

$n = @times;
my $globalStats = "Pings: $n (". ss($n*$gap). "). Duration ". ss($end-$start).
  ". NomGap ". ss($gap). ". AvgGap ". ss(($end-$start)/($n+1)).
  "\nStart: ". ts($start). "  (pings before this: $toosoon)".
  "\n  End: ". ts($end).   "  (pings after this:  $toolate)\n";
#colors to use
my @colors=qw(000000 ff0000 00ff00 0000ff ffffff ffff00 ff00ff 00ffff 333333 990000 009900 000099 ff6666 66ff66 6666ff 888888 ffff88 ff88ff 88ffff 999900 009999 990099);
#build up url to use in Google charts
my ($url,$labels,$data,$color, $toprint);
if($filtered==0) {
  $toprint .= "PIE:\n";
} else {
  $toprint .= lrjust("PIE:", "(" . round1(100*$n/($n+$filtered)) .
    "% remains after " . ($excludeQ ? "excluding /$exclude/" : "") .
                         ($excludeQ && $keepQ ? " and " : "") .
                         ($keepQ ? "keeping /$keep/" : "") . ")") . "\n";
}
for (sort {$tc{$b} <=> $tc{$a}} keys %tc) {
  $data.=round1(100*$tc{$_}/$n). ",";
  $labels.="$_|";
  $color.=shift(@colors) . ",";
  $toprint.= "  $_ $tc{$_} = ". round1(100*$tc{$_}/$n). "% = ~";
  if($filtered==0) {
    $toprint .= ss($tc{$_}/$n*($end-$start)). " = ".
      ss($tc{$_}/$n*24*3600). "/day\n";
  } else {
    $toprint .= ss($tc{$_}*$gap). " = ".
      ss($tc{$_}*$gap/(($end-$start)/(24*3600))). "/day\n";
  }
}
close(LOG);
#get rid of trailing , and | in data
$data=substr($data,0,-1);
$labels=substr($labels,0,-1);
$color=~s/,+$//g;
$url="http://chart.apis.google.com/chart?cht=p&chd=t:$data&chs=500x350&chl=$labels&chco=$color";
#print "url = $url\n";
print $globalStats, $toprint;
if ($pie==1) {
  my $return1 = system("curl '$url' > pie-$username.png 2> /dev/null");
  my $html="pie-$username.html";
  open(HTML, ">$html");
  print HTML "<html>
  <header>
  <title>${username}'s time pie</title>
  <style type='text/css'>
    body {font-family: 'Times New Roman', Times, Palatino, serif;
          background:#229; text-align:center;}
    #container {text-align:left;background:#FFF;border:1px solid black;padding:1em;width:800px;margin:1em auto;}
    h1 {text-align:center}
  </style>
  </header>
  <body>
    <div id='container'>
    <h1>${username}'s time pie</h1>
    <pre>$globalStats</pre>
    <img src='pie-$username.png';
    <pre>$toprint</pre>
    </div>
  </body>
  </html>";
    my $return2 = system("open $html");
  if ($return2) { #try firefox
    $return2 = system("firefox $html &");
    if ($return2!=0) { #try htmlview instead
      $return2 = system("htmlview $html");
    }
  }
}

# generate venn diagram with multiple tags
$data=substr($data,0,-1);
$labels=substr($labels,0,-1);
$color=~s/,+$//g;


@all_tags = sort(keys(%tc_all));
splice(@all_tags, 8);
$num_tags = @all_tags;
$labels = join('|', @all_tags);
$num_all_tags = 0;
foreach(values(%tc_all)) {
	$num_all_tags += $_;
}
print join(' ', @all_tags);
print "number of tags: " . $num_all_tags;

$data = "";
for($i = 1; $i <= $num_tags; $i++) {
  my $iter = combinations(\@all_tags, $i);
  while(my $c = $iter->next) {
#   print @$c;
#   print join('||', @$c);
    $data .= int($tc_ol{join('||', @$c)} / $num_all_tags * 100) . ",";
  }
}
#print $data;
$data=substr($data,0,-1);
$url="http://chart.apis.google.com/chart?cht=v&chd=t:$data&chs=500x350&chdl=$labels";  #&chco=$color";
print "url = $url\n";
print $globalStats, $toprint;
if ($venn==1) {
  my $return1 = system("curl '$url' > pie-$username.png 2> /dev/null");
  my $html="venn-$username.html";
  open(HTML, ">$html");
  print HTML "<html>
  <header>
  <title>${username}'s time pie</title>
  <style type='text/css'>
    body {font-family: 'Times New Roman', Times, Palatino, serif;
          background:#229; text-align:center;}
    #container {text-align:left;background:#FFF;border:1px solid black;padding:1em;width:800px;margin:1em auto;}
    h1 {text-align:center}
  </style>
  </header>
  <body>
    <div id='container'>
    <h1>${username}'s time pie</h1>
    <pre>$globalStats</pre>
    <img src='pie-$username.png';
    <pre>$toprint</pre>
    </div>
  </body>
  </html>";
    my $return2 = system("open $html");
  if ($return2) { #try firefox
    $return2 = system("firefox $html &");
    if ($return2!=0) { #try htmlview instead
      $return2 = system("htmlview $html");
    }
  }
}

