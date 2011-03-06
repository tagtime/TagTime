#!python

# This class defines the interface for storing pings.
# The current implementation relies uses sqlite3 as a backing store.

import sqlite3
import time

# All the pings for a given user.
class UserPingDatabase:
  def __init__(self, username):
    self.username = username
    self.conn = sqlite3.connect(self.get_database_filename(username))
    self.cursor = self.conn.cursor()
    self.cursor.execute('''create table if not exists pings
      (time bigint, creation_time bigint, response text, response_time bigint)''');
    self.conn.commit()

  @staticmethod
  def get_database_filename(username):
    return 'tagtime.{0}.db'.format(username)

  def add_ping(self, t):
    creation_time = time.time()
    self.cursor.execute('insert into pings values (?, ?, null, null)',
                        (t, creation_time))
    self.conn.commit()

  def add_pings(self, ping_times):
    for t in ping_times:
      self.add_ping(t)

  def is_ping_answered(self, t):
    self.cursor.execute('''select response_time from pings
                           where time = ? limit 1''', (t,))    
    rows = [row for row in self.cursor]
    if len(rows) == 0:
      raise ValueError('No pings found for time ' + str(t))
    return rows[0][0] != "null"

  def get_last_ping_before(self, t):
    ## Needs implementation.

  def get_last_ping(self):
    self.cursor.execute("select time from pings order by time desc limit 1")
    rows = [row for row in self.cursor]
    if len(rows) == 0:
      # We have a new database. Insert a row for the current time and return it.
      t = time.time()
      self.add_ping(t)
      return t
    elif len(rows) == 1:
      return rows[0][0]
    else:
      raise ValueError('Expected at most 1 ping, got " + str(len(rows))


