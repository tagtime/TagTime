To determine how you spend your time, TagTime literally randomly samples you.
At random times it pops up and asks what you're doing *right at that moment*.
You answer with tags.

For more on the idea behind this project, see our blog post at
[messymatters.com/tagtime](http://messymatters.com/tagtime ).

We're currently auto-tweeting git commits: [@tagtm](http://twitter.com/tagtm ).

# Code 

The core Perl implementation of TagTime itself is in the following files:

* tagtimed.pl -- the TagTime daemon
* launch.pl -- launches the pinger by popping up an xterm
* ping.pl -- prompts for the tags
* util.pl -- utility functions
* settings.pl.template -- user-specific settings

In addtion are the following files:

* install.py -- install script
* grppings.pl -- grep your tagtime log file
* cntpings.pl -- tally pings in your log file matching given criteria

* tskedit.pl -- task editor / to-do list that integrates with TagTime
* tskproc.pl -- helper script used by tskedit.pl
* tasks.vim.template -- vim macros needed for the task editor

* merge.pl -- just a stub, for fixing/merging tagtime logs

The script directory contains various scripts we've used, like for various games and contests and commitment contracts and whatnot. 
Basically, incentive schemes for getting ourselves to procrastinate less.

The src directory currently contains Python code contributed by Jonathan Chang for a new back-end for TagTime. It hasn't yet been integrated.
It also contains the source for an Android app by Bethany Soule (bsoule) with contributions by Michael Janssen (jamuraa).

Thanks also to Jesse Aldridge, Kevin Lochner, and Rob Felty for contributions to the code.

# Installation and Quick Start

0. Clone the repository on Github.
1. cd into your local tagtime directory.
2. Run: python install.py <username>
3. Verify in settings.pl (wherever it says CHANGEME) that the install
   script filled in everything correctly.
4. Make sure you have X11 (on Mac) or Cygwin (on Windows) running (not an issue on Linux).
5. Run: ./tagtimed.pl &
6. Answer the pings!
   (Always answer with what it caught you at right at that moment.)

# Advanced Usage

TagTime's Task Manager is documented in the file template.tsk
It's for vim users only.  You don't need it to use TagTime.

Basic ping-tallying: 

    ./cntpings.pl username.log  (run w/o args for options)

    (Special tags: 
     off = tagtime (launch.pl) didn't run;
     afk = away from keyboard;
     err = you closed the window without answering the ping)

How to make the tagtime daemon automatically start on bootup in OSX:

    sudo ln -s /path/to/tagtimed.pl /Library/StartupItems/tagtimed.pl

Pick a distinctive sound for your pings by setting $playsound in 
settings.pl. Sample sounds are in the sound directory. Non-mac users, 
see README file in sound directory.

A handy vim macro for duplicating the previous line's tags in the tagtime log:

    map <f4> mzk0el"vy/\([\\|$\)<cr>jd/\([\\|$\)<cr>h"vp`zj

# The Math

If your tagtime gap is g minutes then the probability of at least one ping
in any x minute window is 1-exp(-x/g).
The window corresponding to probability p is -g*ln(1-p).
For example, with g=45, there's a 10% chance of getting pinged in any window
of duration 4 minutes 44 seconds.  There's a 50% chance of getting pinged within 31 minutes.
There's a 99% chance of a ping within 3.5 hours.
The probability of waiting over 10 hours for a ping is one in a million.

