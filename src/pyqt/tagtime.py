import ctypes
import sqlite3
from datetime import datetime, timedelta
import math
import sys
from PyQt4 import QtCore, QtGui, uic

#todo: a gui to save the tags in the popup
#todo: a main window that goes in the status bar with configuration for account / sync

def xor_shift(x):
    x ^= (ctypes.c_uint64(x).value << 21)
    x ^= (ctypes.c_uint64(x).value >> 35)
    x ^= (ctypes.c_uint64(x).value << 4)
    return ctypes.c_uint64(x).value

def clean_tag(tag):
    return tag.lower().strip()

class Storage:
    create_pings_query = """Create TABLE if not exists pings
            (seed INTEGER PRIMARY KEY, time NUMERIC, answered NUMERIC)"""
    create_tags_query = """Create TABLE if not exists tags
            (id INTEGER PRIMARY KEY, tag TEXT UNIQUE)"""
    create_tagged_query = """Create TABLE if not exists tagged
            (id INTEGER PRIMARY KEY, tag_id INTEGER, seed INTEGER)"""

    insert_ping_query = """INSERT INTO pings VALUES(?,?,?)"""
    insert_tag_query = """INSERT OR IGNORE INTO tags (tag) VALUES(?)"""
    insert_tagged_query = """INSERT INTO tagged (tag_id, seed) SELECT id, ? FROM tags WHERE tag = ?"""

    def __init__(self):
        self.conn = sqlite3.connect('storage.db')
        self.cursor = self.conn.cursor()
        self.cursor.execute( Storage.create_pings_query )
        self.cursor.execute( Storage.create_tags_query )
        self.cursor.execute( Storage.create_tagged_query)
        self.conn.commit()

    def save_ping(self, ping):
        #sqllite needs to store the seed as a signed 64 bit integer
        seed = ctypes.c_int64(ping.seed).value
        print "saving ping #%(seed)d for time #%(time)s" % {'seed':seed, 'time':datetime.now()}

        self.cursor.execute( Storage.insert_ping_query,
            (seed, ping.time, ping.answered) )
        self.cursor.executemany( Storage.insert_tag_query, [[t] for t in ping.tags if len(t) > 0] )
        self.cursor.executemany( Storage.insert_tagged_query, [[seed, tag] for tag in ping.tags if len(tag) > 0] )
        self.conn.commit()

class Ping:
    def __init__(self, seed, time):
        self.seed = seed
        self.time = time
        self.answered = False

    def reply(self, tags):
        self.tags = map( clean_tag, tags )
        self.answered = True

    def next_ping(self):
        dt =  timedelta( minutes=-45*math.log( 1 - self.seed / float(1<<64) ) )
        if timedelta < 0:
            print timedelta, self.seed
        return Ping( xor_shift(self.seed), self.time + dt )


class PingDialog(QtGui.QWidget):

    def onTagButtonClicked(self):
        self.hide()
        tags = self.tagEdit.text().__str__().split(',')
        self.ping.reply(tags)
        app.storage.save_ping(self.ping)

    def __init__(self, ping):
        super(PingDialog, self).__init__()
        self.ping = ping
        uic.loadUi('pingdialog.ui', self)
        self.label.setText( datetime.now().strftime("%c") + ": what are you doing <b>right now</b>?" )
        self.tagButton.clicked.connect(self.onTagButtonClicked)
        self.show()
        self.activateWindow()
        self.raise_()

class Control(QtGui.QApplication):

    def wake(self):
        print "time activated"
        p = self.current_ping
        self.current_ping = p.next_ping()
        dt = self.current_ping.time - datetime.now()
        self.timer.singleShot(dt.total_seconds() * 1000, self.wake)
        print "Setting timer in %f seconds" % dt.total_seconds()
        self.ping_dialog = PingDialog(p)


    def __init__(self, *args):
        QtGui.QApplication.__init__(self, *args)

        #ping storage
        self.storage = Storage()

        # find current ping by iterating until finding the next ping
        #TODO: use latest ping from web database or local storage!
        p = Ping( 1234, datetime.fromtimestamp(1.335e9))
        while p.time < datetime.now():
            p = p.next_ping()
        self.current_ping = p

        #set alarm for ping
        #TODO: check potential race condition
        self.timer = QtCore.QTimer()
        dt = self.current_ping.time - datetime.now()
        print "Setting timer in %f seconds" % dt.total_seconds()

        self.timer.singleShot(dt.total_seconds() * 1000, self.wake)


def main():
    global app
    app = Control(sys.argv)
    app.setQuitOnLastWindowClosed(False)
    sys.exit(app.exec_())

if __name__ == '__main__':
    main()


