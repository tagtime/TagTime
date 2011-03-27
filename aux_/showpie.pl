#!/usr/bin/perl
# Show the pie!  (by dreeves and robfelty)
# NOTE: This doesn't work for the way we currently use tagtime, with 
# multiple tags in arbitrary order.  Use cntpings.pl instead.
#
# This currently assumes exactly one tag per line in the log file.
# Anything after that first tag will be ignored.
# (Although --keep and --exclude consider all tags.)
# TODO: with --keep foo it should strip out foo so you can see subtags
# TODO: for --ago, specify number of weeks to include, like frask.pl does.

require "$ENV{HOME}/.tagtimerc";
require "${path}util.pl";
use Getopt::Long; # command line options module.

#(@ARGV >= 1) or die "Usage: $0 logFile [--ago X --pie 1 --keep FOO --exclude BAR]\n";
$help = <<"EOF";
USAGE: $0 logfile
  Available options:
  --ago N:  show pie for the week N weeks ago. (default is 0)
            N=0 => show pie for the current week starting last saturday night.
            N=1 => show pie for the previous week, up till last saturday night.
            N=-1 = show pie for all time.
  --pie 1:  Show a graphical representation (launched in browser).
  --exclude 'REGEXP': Exclude pings with tags matching the regular expression.
        EG: --exclude='slp|job'  (excludes all slp and all job pings)
            --exclude='mt[yp]|sl.'
  --keep 'REGEXP': Keep only pings with tags matching the regular expression.
        EG: --keep='job'  (excludes all pings unless they include job tag)
EOF
die $help if !@ARGV >= 1;

my $ago = 0;   # default value if not given on command line.
my $pie = 0;   # default value if not given on command line.
my $exclude='^NEVERLLHAPPEN$'; # default value for regexp of things to exclude.
my $keep='.*';  # default value for regexp of things to keep.
GetOptions("ago=i"=>\$ago, "pie=i"=>\$pie, 
           "exclude=s"=>\$exclude, "keep=s"=>\$keep);
my $excludeQ = ($exclude ne '^NEVERLLHAPPEN$');
my $keepQ = ($keep ne '.*');
my($ts, $tag);  # timestamp and tag.
my %tc;        # tag counts -- hashes from tag to count.
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
  ($ts, $tag) = split(/\s+/, $_);  # ignoring all but first tag!
  if($ts<$start) { $toosoon++; }
  elsif($ts>$end) { $toolate++; }
  elsif(eval("(\$line =~ /$exclude/) || (\$line !~ /$keep/)")) { $filtered++; }
  else {
    push(@times, $ts);
    if ($ts == 1) { print "DEBUG: $line\n"; }
    $tc{$tag}++;
  }
}
if ($start == -1) { $start = min(@times); print "DEBUG: $start\n"; }

$n = @times;
my $globalStats = "Pings: $n (". ss2($n*$gap). "). Duration ". ss($end-$start).
  ". NomGap ". ss2($gap). ". AvgGap ". ss2(($end-$start)/($n+1)).
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
  my $value= round1(100*$tc{$_}/$n). ",";
  if ($value != 0) {
    $data.=$value;
    $labels.="$_|";
    $color.=shift(@colors) . ",";
  }
  $toprint.= "  $_ $tc{$_} = ". round1(100*$tc{$_}/$n). "% = ~";
  if($filtered==0) {
    $toprint .= ss2($tc{$_}/$n*($end-$start)). " = ".
      ss2($tc{$_}/$n*24*3600). "/day\n";
  } else {
    $toprint .= ss($tc{$_}*$gap). " = ".
      ss2($tc{$_}*$gap/(($end-$start)/(24*3600))). "/day\n";
  }
}
close(LOG);
#get rid of trailing , and | in data
$data=substr($data,0,-1);
$labels=substr($labels,0,-1);
$color=~s/,+$//g;
$url="http://chart.apis.google.com/chart?cht=p&chd=t:$data&chs=500x350&chl=$labels&chco=$color";
print $globalStats, $toprint;
if ($pie==1) {
  my $return1 = system("curl '$url' > pie-$username.png 2> /dev/null");
  if ($return1) { #try wget
    $return1 = system("wget '$url' -O pie-$username.png 2> /dev/null");
  }
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
    if ($return2) { #try htmlview instead
      $return2 = system("htmlview $html");
    }
  }
}


sub ss2 { my($s) = @_;
  my($d,$h,$m);
  my $incl = "s";

  if ($s < 0) { return "-".ss2(-$s); }

  $m = int($s/60);
  if ($m > 0) { $incl = "ms"; }
  $s %= 60;
  $h = int($m/60);
  if ($h > 0) { $incl = "hms"; }
  $m %= 60;
  #$d = int($h/24);
  #if ($d > 0) { $incl = "dhms"; }
  #$h %= 24;

  return ($incl=~"d" ? "$d"."d" : "").
         ($incl=~"h" ? $h."h" : "").
         ($incl=~"m" ? dd($m).":" : "").
         ($incl!~"m" ? $s : dd($s))."s";
}

