#!python

import schedule
import storage
import time


# Base class to read from pings from storage, compute ping times,
# and see if another ping is in order, perform the ping and record the result.
# Subclasses should implement the actual prompt functionality.
class Prompter:
  def __init__(self, database, l):
    self.database = database
    self.l = l
  
  # Ensures that there is at least one unanswered ping at the end of the queue.
  def ensure_unanswered_ping(self):
    current = time.time()
    t = self.database.get_last_ping()
    ping_times = schedule.get_next_ping_times_through(t, current, l)
    self.database.add_pings(ping_times)

  def needs_ping(self):
    self.ensure_unsanswered_ping()
    current = time.time()
    t = self.database.get_last_ping_before(current)
    if self.database.is_ping_answered(t):
      pass
    else:
      self.prompt()
