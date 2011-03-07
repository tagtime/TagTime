#!/usr/bin/perl
# Take multiple log files on the command line and merge them if it can be done
# unambiguously. That means that for each ping (use the user's settings to 
# determine what all the pings are, starting with the earliest ping in any of
# the log files) there should be at most one log file that contains tags other
# than "afk" or "off" or "err". Modify the first log file specified so it 
# contains the ping entry from that file.  If no logs contain an entry with 
# non-afk, non-off tags then use the afk/off entry. If no logs contain an entry
# for the ping at all then create one in the target log file with tag "err".
# Note that if only one log file is given this just fills in any missing pings
# with "err" tags.
# If you use tagtime on multiple computers this should be a simple way to merge
# the disparate log files.

# TODO: write this script
