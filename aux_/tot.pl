#!/usr/bin/env perl
# Compute amount of time spent on the tag passed as an argument.
# Second argument must be the name of a log file.

require "$ENV{HOME}/.tagtimerc";
require "${path}util.pl";

# Hours per day, as computed for vacation time.
# (8 is conservative since breaks and lunches typically count as work,
#  and in fact sometimes I count such pings, like for group lunches, and breaks
#  at conferences where I don't know which pings happened when)
$hpd = 7.70;

$daypay = 125000/(50*5);

@ARGV >= 2 or die "Usage: $0 logfile tags...\n";
$logf = shift;
@targtags = @ARGV;
$ago = 0;
#if (scalar @ARGV == 3) { $ago = shift; }

if ($logf eq "dreeves.log") {
  $vac =          # number of vacation days danny has taken.
    0             # add new entries after this line.
    + 199.95/$daypay # boomerang data recovery expense (2007-06-26).
    + 1           # labor day company holiday.
    + 2           # 2 flex days taken for tanglewood, 2007-09.
    + 2.5/$hpd    # 2.5 hours ryan fugger.
    + 2           # 2 company holidays for thanksgiving, 2007-11.
    + 3           # 3 company holidays for xmas, ny eve, ny day, 2007-08.
    + 5.75/$hpd   # 5.75 hours roland.
    + 1           # MLK holiday, 2008-01-21
    + 1           # 2008.02.18 president's day company holiday
    + 0           # mty pings from Rob.
    + 0           # mockup/taskbot pings from Bethany.
    + 1           # memorial day company holiday.
    + 98*2/$daypay  # travel to ISAT peer production workshop.
    + 4           # skate workshop + peoria, 2008-06-20 - 30.
    + 1           # 2008.07.04 fourth of july company holiday.
    + 4/$hpd      # 4 hours roland 2008 June 30, sigecom exchanges
    + (15.75+2.48)/$daypay # bought Ariely's predictably irrational.
    + 4.38/$daypay # bought Brams's win-win solution book.
    + 1           # labor day company holiday 2008-09-01.
    + 66*.75/$hpd # mty pings from Rob.
    + .5/$hpd     # rob centmail work on 2008-10-02
    + 812.55/$daypay # cashing in yootles on yahootles ledger.
    + 2           # 2 company holidays for thanksgiving, 2008-11.
    + 150/$daypay # roland 2008 Nov, sigecom exchanges.
    + 2           # sanibel vacation, 2008-12-08/09.
    + 3           # 2008/2009 company holidays: xmas, post-xmas, new years day
    + 4           # 2008/2009 year end floating and vacation days taken.
    + 1           # mlk company holiday 2009.
    + 7           # vacation days for cantor's birth.
    + 1           # presidents' day company holiday 2009 feb 16.
    + 3/$hpd      # messymatters.com setup by rob.
    + 1/$hpd      # messymatters.com stuff by rob on 2009-03-30.
    + 85.12/$daypay # power cord; payroll lost the receipt 2009-03-16.
    + 33.70/$daypay # wits&wagers from openlygeek.com 2009-05-01.
    + 1           # memorial day 2009-05-25.
    + 1           # fourth of july 2009.
    + .75*2/$hpd  # rob messymatters work. 
    + 1           # labor day 2009.
    + 11/$daypay  # mturk costs for messymatters post.
    + 2           # thanksgiving 2009
    + 6           # xmas, new years, and vacation days 2009/2010.
    + 120/$daypay # roland sigecom work.
    + 31.57/$daypay # video cable dongle for new laptop.
    + 899/$daypay # transferring from yootles.com/mt on 2010-01-14.
    + 1           # MLK day 2010-01-18.
    + 1           # presidents' day 2010-01-15.
    + 345.17/$daypay # transferring from yootles.com/mt on 2010-02-16.
    + 149.71/$daypay # ben lubin visitor dinner 2010-03-02.
    + 6           # vacation days for back-country ski trip 2010-03-12.
    + 2/$daypay   # mturk discrimination hits 2010-05-03.
    + 20/$daypay   # mturk prediction hits 2010-05-07.
    + 65.31/$daypay # dinner with giro 2010-05-04.
    + 103.67/$daypay # dinner with shaili 2010-05-12.
    + 120/$daypay    # roland sigecom exchanges 2010-06-09.
    + 1            # fourth of july, 2010.07.05
    + 5           # official vacation days taken.
    + (166.0+8)/$hpd  # official vacation accrued as of 2010-07-26.
    + 0;          # that's all; add new stuff above this line.
                  # Roland total hours: 
                  #   5.75 + 4 + 150/40 + 120/40 + (15+157.50)/40 + 2
  $start = 1184090291;  # when danny started tracking.
  $sat = 1184472000;  # the first saturday night since danny started tracking.
} elsif ($logf eq "robfelty.log") {
  $vac = 0; # number of vacation days for rob.
  $start = 1189708422;  # when rob started tracking.
  $sat = 1189829662;  # the first saturday night since rob started tracking.
} elsif ($logf eq "bsoule.log") {
  $vac = 0;  # number of vacation days for bee.
  $start = 1184090291;  # when danny started tracking.
  $sat = 1184472000;  # the first saturday night since danny started tracking.
} else {
  die "This only works for Danny, Rob, & Bethany right now.\nHack yourself in if you want.\n";
}

# Count the number of weekends since the start of tagtime log.
my $end = time();   # right now.
my $w = 7*24*3600;  # a week.
my $k = 1+int(($end-$sat)/$w);  # number of sat nights since dawn of the log.

my $pings = 0;
open(F, $logf) or die;
while(<F>) {
  die unless /^(\d+)\s*(.*)$/;
  my $ts = $1;
  my $stuff = $2;
  my $tags = strip($stuff);
  foreach $tag (@targtags) {
    if($tags =~ /\b$tag\b/) { $pings++; last; }
  }
}
close(F);

$secs = time()-$start;
$days = $secs/3600/24;
$weeks = $secs/3600/24/7;
$workdays2 = $weeks*5 - $vac;
$workdays = $days - $k*2 - $vac;

#system("./showpie.pl $logf --keep ".join('|', @targtags)." --ago=$ago");
print "-"x72, "\n";
print "Total pings: $pings\n";
print "Total hours: ", $pings*3/4, "\n";
print "Credited days (vacation, etc): ", $vac, "\n";
print "Hours per day: ", $pings*3/4/$days, "\n";
print "Hours per week: ", $pings*3/4/$weeks, "\n";
print "Hours per workday: ", $pings*3/4/$workdays, "\n";
print "Hours per workday (no-wknds): ", $pings*3/4/$workdays2, "\n";
print "Hours per workweek: ", $pings*3/4/$workdays*5, "\n";
print "Hours per workweek (no-wknds): ", $pings*3/4/$workdays2*5, "\n";
print "Vacation days accrued: ", $pings*3/4/$hpd, " - ", $workdays, " = ", $pings*3/4/$hpd-$workdays, "\n";
print "Vac days accrued (no-wknds): ", $pings*3/4/$hpd, " - ", $workdays2, " = ", $pings*3/4/$hpd-$workdays2, "\n";

# Explanation of no-wknds:
# When it doesn't explicitly account for weekends (no-wknds) then on Monday it 
# will say you have, say, 3 vacation days as opposed to close to 4.  That's 
# because not accounting for weekends it expects you to work 40/7 = 5.7 hours 
# per day and you did bupkes on Sunday so it thinks you wasted close to a day.  
# Accounting for weekends it expects you to work 0 on sunday (and saturday) and 
# 8 per day on workdays.  Mid-week the distinction doesn't matter and the 2 
# numbers will be the same, starting to diverge in the other direction towards 
# the end of the week.

