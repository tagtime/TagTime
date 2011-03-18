#!/usr/bin/perl
# Take a log file as the first command line argument and optionally an
# additional log file. Modify the first log file to fill in any missing pings.
# We start with the timestamp of the first ping in the first log file and walk
# forward in time, rewriting the target log file.
# If a ping is missing from the target log, insert it. Use the tags from the 
# second log file, if present, otherwise autotag it "MIA".
# If a ping is present in the target log that shouldn't be there, add the 
# autotag "UNSCHED".
# If a ping in the target log has only autotags (afk, off, err, MIA) and the 
# same ping in the second log file has any non-autotags then replace the ping
# in the target log with the entry from the second log.
# If a ping is present in the second log file that shouldn't be there, add it
# to the target file with the "UNSCHED" autotag added.
# (Maybe abort if too many UNSCHED pings since that probably means the log is
# on a different ping schedule and it doesn't make sense to do a merge.)
#
# Note that if only one log file is given this just fills in any missing pings
# with MIA tags (and marks pings that shouldn't be there as UNSCHED).
# If you use tagtime on multiple computers this should be a simple way to merge
# the disparate log files.

# TODO: write this script
