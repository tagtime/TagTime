#!python

# Implements the logic to determine ping times.  Ping times
# are separated by an exponential distribution, with
# expected interval time = 1 / lambda.

from random import expovariate
from datetime import timedelta

# Returns a stocastically determined next ping time after time t.
def get_next_ping_time(last_time, interval, **kwargs):
  delta = max(kwargs.get('min_delta', 0), expovariate(1.0/interval))
  if kwargs['debug']:
    print 'delta: ', timedelta(seconds=round(delta))
  return last_time + delta

# Returns next pings starting from 'start' so that the last ping
# exceeds 'end'.
def get_next_ping_times_through(start, end, interval, **kwargs):
  assert start < end
  ping_times = []
  current_ping_time = start
  while current_ping_time < end:
    current_ping_time = get_next_ping_time(current_ping_time, interval, **kwargs)
    ping_times.append(current_ping_time)
  return ping_times

if __name__ == '__main__':
  print get_next_ping_times_through(100, 500, 20, min_delta=5, debug=True)
