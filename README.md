To determine how you spend your time, TagTime literally randomly samples you.
At random times it pops up and asks what you're doing *right at that moment*.
You answer with tags.

See 
[messymatters.com/tagtime](http://messymatters.com/tagtime )
for the whole story.

We're currently auto-tweeting git commits: [@tagtm](http://twitter.com/tagtm ).

# Code 

The core Perl implementation of TagTime itself is in the following files:

* tagtimed.pl -- the TagTime daemon
* launch.pl -- launches the pinger by popping up an xterm
* ping.pl -- prompts for the tags
* util.pl -- utility functions
* settings.pl.template -- user-specific settings

In addition are the following files:

* install.py -- install script
* grppings.pl -- grep your TagTime log file
* cntpings.pl -- tally pings in your log file matching given criteria

* tskedit.pl -- task editor / to-do list that integrates with TagTime
* tskproc.pl -- helper script used by tskedit.pl
* tasks.vim.template -- vim macros needed for the task editor

* merge.pl -- just a stub, for fixing/merging TagTime logs

* beeminder.pl -- sends your TagTime data to your Beeminder graph
* beemapi.pl -- partial Perl implementation of the Beeminder API

The script directory contains various scripts we've used, like for various games
and contests and commitment contracts and whatnot. 
Basically, incentive schemes for getting ourselves to procrastinate less.
We view TagTime as the foundation for all such lifehacks, since it's a way to 
guarantee you always have data on where your time is going.
It's hard to flake out on reporting to TagTime since it actively pings you.
You can be perfectly passive -- just responding when prompted.
That's why we call it "time-tracking for space cadets".

The src directory currently contains Python code contributed by Jonathan Chang 
for a new back-end for TagTime. It hasn't yet been integrated. Same with pyqt
which was contributed by Arthur Breitman.
The src directory also contains the source for an Android app by Bethany Soule 
(bsoule) with contributions by Michael Janssen (jamuraa).

Thanks also to Paul Fenwick, Jesse Aldridge, Kevin Lochner, and Rob Felty for 
contributions to the code.

# Installation and Quick Start

0. Clone the repository on Github
1. cd into your local tagtime directory
2. Run: python install.py USERNAME
3. Verify in settings.pl (wherever it says CHANGEME) that the install
   script filled in everything correctly
4. Make sure you have X11 (on Mac) or Cygwin (on Windows) running (not an issue
   on Linux)
5. Run: ./tagtimed.pl &
6. Answer the pings!
   (Always answer with what it caught you at right at that moment)

# Perl Newbies

1. Run: sudo cpan
2. At the cpan prompt run: upgrade (this may not actually be necessary)
3. For each thing that TagTime complains about, like 
   'can't find LWP::UserAgent', run: install LWP::UserAgent

# Advanced Usage

TagTime's Task Manager is documented in the file template.tsk  
It's for vim users only. You don't need it to use TagTime.

Basic ping-tallying: 

    ./cntpings.pl username.log  (run w/o args for options)

    (Special tags: 
     off = tagtime (launch.pl) didn't run;
     afk = away from keyboard;
     err = you closed the window without answering the ping)

How to make the tagtime daemon automatically start on bootup in OSX:

    sudo ln -s /path/to/tagtimed.pl /Library/StartupItems/tagtimed.pl

Pick a distinctive sound for your pings by setting $playsound in 
settings.pl.
Sample sounds are in the sound directory. 
Non-mac users, see README file in sound directory.

A handy vim macro for duplicating the previous line's tags in the tagtime log:

    "replace tags on this tagtime line with those from the prev line.
    "(warning: must have timestamp in square brackets on both lines)
    map <f4> mzk0el"vy/\([\\|$\)<cr>jd/\([\\|$\)<cr>h"vp`zj

# Extra Features

Editor: If you hit enter instead of answering the ping it will open up the 
editor.

Ditto: If you enter just a double-quote character (") it will enter whatever 
pings you entered last time. (Thanks to Paul Fenwick for implementing that.)

# The Math

If your tagtime gap is g minutes then the probability of at least one ping
in any x minute window is 1-exp(-x/g).
The window corresponding to probability p is -g*ln(1-p).
For example, with g=45, there's a 10% chance of getting pinged in any window
of duration 4 minutes 44 seconds.
There's a 50% chance of getting pinged within 31 minutes.
There's a 99% chance of a ping within 3.5 hours.
The probability of waiting over 10 hours for a ping is one in a million.

# Beeminder Integration

To set up TagTime to automatically send reports to 
[Beeminder](http://www.beeminder.com/), 
first set up a goal there (either a "Do More" or "Do Less" goal). 
Copy the url and plug it into your 
`settings.pl` file under the Beeminder section. 

Each goal on Beeminder will track a collection of one or more tags on TagTime. 
Regular expressions are encouraged! 
See `settings.pl` for more details. 

# Android App

There is an Android app available [on Google
Play](https://play.google.com/store/apps/details?id=bsoule.tagtime).
The source and build instructions are in `src/and`.

# Google Group

For discussion and questions: 
[TagTime Google Group](https://groups.google.com/forum/?fromgroups#!forum/tagtime ).
