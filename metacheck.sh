#!/bin/bash
# Run any sanity checks on your TagTime logfile.
# Currently I'm running integrity.pl on all but the first 457 lines
# (that was back in 2007 before the universal ping schedule was finalized)
# and also checking that all kib pings since 2024 October are tagged bux.

tail -n +457 dreeves.log | ./integrity.pl 
./grppings.pl dreeves.log -s2024.10.01 'kib & !bux'

