#!/usr/bin/perl
# Make sure no pings were missed and insert them with tag "err" if so.
# If there are duplicate lines and one is tagged "err", remove it -- 
#   this might be a simple fix to the issue of using tagtime on multiple
#   different computers.
# Also may add human-readable timestamps in brackets if there's room.
# Generalize this to also merge log files -- the ping not tagged afk should count.
