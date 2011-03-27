#!/usr/bin/perl
# Convert time strings to unix time and vice versa.

require "$ENV{HOME}/.tagtimerc";
require "${path}util.pl";

print "Enter date/times in YMDHMS order, or unix timestamps, one per line.\n";
print "Ctrl-D when done.\n";
while(<>) {
  chomp;
  if (isnum($_)) { print "$_ unixtm == ", ts($_), "\n"; }
  else { print "$_ == ", ts(pd($_)), " == ", pd($_), " unixtm\n"; }
}
