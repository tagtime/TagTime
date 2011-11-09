#!/usr/bin/env perl
# Launch vim with certain macros for editing your task file.
# (looks best with black background, eg: xterm -fg SkyBlue -bg black -cr red)
# For instructions on using tskedit, see template.tsk

# The macros are defined in tasks.vim and they make it so that
# hitting a certain key marks the task on the current line
# with '!' and filters the buffer through tskproc.pl.

# tskproc.pl does the following:
# Lines starting with '!' will be toggled:
#   If it doesn't have a start time, one is added.
#   If it does have a start time, it is marked as done (X prepended)
#     and the end time is added.
#   If it is already marked done, the X is removed, as is the end time
#     (actually the end time stays there but will be overwritten when
#      you mark it done again).
# The input is also re-sorted so tasks checked as done move to the bottom.
# See template.tsk for further instructions.

require "$ENV{HOME}/.tagtimerc";
#require "${path}util.pl";

chdir($path);
$cmd = "vim -c \"source tasks.vim\" $usr.tsk";
system($cmd) == 0 or print "SYSERR: $cmd\n";
