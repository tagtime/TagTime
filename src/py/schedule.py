#!python

# Implements the logic to determine ping times.  Ping times
# are separated by an exponential distribution, with 
# expected interval time = 1 / lambda.

import math
import random

# Returns a stocastically determined next ping time after time t.
def get_next_ping_time(t, l):
  delta = -math.log(random.rand()) / l
  return t + math.round(l)

# Returns next pings starting from 'start' so that the last ping
# exceeds 'end'.
def get_next_ping_times_through(start, end, l):
  assert start < end
  ping_times = []
  current_ping_time = start
  while current_ping_time < end:
    current_ping_time = get_next_ping_time(current_ping_time, l)
    ping_times.append(current_ping_time)
  return current_ping_times
