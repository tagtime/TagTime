#!/usr/bin/python

"""

Reads a TagTime logfile, as for example produced by the TagTime app, and
displays it in various ways.

Currently supported are:

    - A pie chart of the total percentage time spent on a task
    - Bar charts showing which tags were active
      - on a specific day of the week
      - on a specific hour of the day
    - A line plot showing which were active on a given date

The plots can be configured from command line, especially:

    - which tags are to be shown (specific)
    - automatically select the top N tags
    - whether to show the 'other' category in the plots as well
    - the hour resolution in the hour of the day plot

REQUIREMENTS

This script relies heavily on the pandas module, which does most of the work.

"""

import matplotlib.pyplot as plt
import pandas as pd
import numpy as np
from collections import defaultdict
import datetime
import re

class TagTimeLog:
    def __init__(self, filename, interval=.75, startend=(None, None), double_count=False):
        self.interval = interval
        self.double_count = double_count
        if isinstance(filename, str):
            with open(filename, "r") as log:
                self._parse_file(log)
        else:
            self._parse_file(filename)
        self.D = self.D.ix[startend[0]:startend[1]]
        self.D = self.D.fillna(0)

    def _parse_file(self, handle):
        D = defaultdict(list)
        V = defaultdict(list)
        for line in handle:
            line = re.sub(r'\s*\[.*?\]\s*$', '', line)
            fields = re.split(r'\s+', line)
            dt = datetime.datetime.fromtimestamp(int(fields[0]))
            fields = fields[1:]
            for f in fields:
                D[f].append(dt)
                if self.double_count:
                    V[f].append(self.interval)
                else:
                    V[f].append(self.interval / len(fields))

        for f in D.keys():
             D[f] = pd.Series(V[f], index=D[f])  

        self.D = pd.DataFrame(D)

    def day_sums(self, tags, top_n=None, other=False):
        """ show the supplied tags summed up per day """
        if top_n is not None:
            tags = self.top_n_tags(top_n)
        D = self.D[tags] if tags is not None else self.D
        if other:
            D['other'] = self.D[[t for t in self.D.keys() if t not in tags]].sum(axis=1)
        D = D.resample('D', how='sum')
        cmap = plt.cm.Paired
        colors = cmap(np.linspace(0., 1., len(D.keys())))
        D.plot(color=colors)
        plt.legend(loc='best')

    def hour_sums(self, tags, top_n, resolution=2, other=False):
        """ show the supplied tags summed up per hour """
        if top_n is not None:
            tags = self.top_n_tags(top_n)
        D = self.D[tags] if tags is not None else self.D
        if other:
            D['other'] = self.D[[t for t in self.D.keys() if t not in tags]].sum(axis=1)
        D = D.groupby(resolution * (D.index.hour / resolution)).sum()
        cmap = plt.cm.Paired
        colors = cmap(np.linspace(0., 1., len(D.keys())))
        D.plot(kind='bar', stacked=True, color=colors)
        plt.legend(loc='best')

    def day_of_the_week_sums(self, tags, top_n=None, other=False):
        if top_n is not None:
            tags = self.top_n_tags(top_n)
        D = self.D[tags] if tags is not None else self.D
        if other:
            D['other'] = self.D[[t for t in self.D.keys() if t not in tags]].sum(axis=1)
        D = D.resample('D', how='sum')
        D = D.groupby(D.index.dayofweek).mean()
        cmap = plt.cm.Paired
        colors = cmap(np.linspace(0., 1., len(D.keys())))
        D.plot(kind='bar', stacked=True, color=colors)
        plt.title('Time Spent over Day of the Week')
        plt.xticks(np.arange(7) + 0.5, list("MTWTFSS"))
        plt.legend(loc='best')

    def top_n_tags(self, n):
        # sum up tags within a day, determine the mean over the days
        D = self.D.resample('D', how='sum').mean()
        keys = sorted(D.keys(), key=lambda x: D[x], reverse=True)
        return keys[:n]

    def pie(self, tags, top_n=None, other=False):
        """ 
        Show a pie-chart of how time is spent.
        """

        if top_n is not None:
            tags = self.top_n_tags(top_n)
        D = self.D[tags] if tags is not None else self.D
        if other:
            D['other'] = self.D[[t for t in self.D.keys() if t not in tags]].sum(axis=1)

        # sum up tags within a day, determine the mean over the days
        D = D.resample('D', how='sum').mean()

        # sort by time spent
        keys = sorted(D.keys(), key=lambda x: D[x], reverse=True)
        values = [D[x] for x in keys]

        # reformat labels to include absolute hours
        keys = ["%s (%1.1f h)" % (x, D[x]) for x in keys]

        fig = plt.figure()
        ax = fig.add_subplot(111)
        cmap = plt.cm.Paired
        colors = cmap(np.linspace(0., 1., len(values)))
        pie_wedge_collection = ax.pie(values, labels=keys, autopct='%1.1f%%', colors=colors, labeldistance=1.05)
        for pie_wedge in pie_wedge_collection[0]:
                pie_wedge.set_edgecolor('white')

def main():
    import argparse
    import os
    import sys

    parser = argparse.ArgumentParser(description='Process some integers.')
    parser.add_argument('logfile', type=argparse.FileType('r'), help='the logfile to analyze')
    parser.add_argument('--pie', action='store_true', help='display a pie chart for total time spent')
    parser.add_argument('--day-of-the-week', action='store_true', help='display a bar for each day of the week')
    parser.add_argument('--day', action='store_true', help='display a bar for each day')
    parser.add_argument('--hour-of-the-day', action='store_true', help='display a bar for each hour of the day')
    parser.add_argument('--resolution', type=int, default=2, help='the number of consecutive hours summed over in hour-of-the-day chart')
    parser.add_argument('--top-n', type=int, help='limit the tags acted upon to the N most popular')
    parser.add_argument('--other', action='store_true', help='show the category "other"')
    parser.add_argument('--tags', nargs='*', help='limit the tags acted upon')
    parser.add_argument('--interval', type=float, default=.75, help='the expected time between two pings, in fractions of hours')
    parser.add_argument('--double-count', action='store_true', help='one ping with multiple tags is treated as one ping separate for every tag (default off=time is split equally between tags)')
    parser.add_argument('--start', type=lambda x: datetime.datetime.strptime(x, '%Y-%m-%d'), help='start date of interval, inclusive (YYYY-MM-DD)')
    parser.add_argument('--end',   type=lambda x: datetime.datetime.strptime(x, '%Y-%m-%d'), help='end date of interval, exclusive (YYYY-MM-DD)')
    args = parser.parse_args()
    
    ttl = TagTimeLog(args.logfile, interval=args.interval, startend=(args.start, args.end), double_count=args.double_count)
    if(args.pie):
        ttl.pie(args.tags, args.top_n, args.other)
    if(args.day_of_the_week):
        ttl.day_of_the_week_sums(args.tags, args.top_n, args.other)
    if(args.hour_of_the_day):
        ttl.hour_sums(args.tags, args.top_n, resolution=args.resolution, other=args.other)
    if(args.day):
        ttl.day_sums(args.tags, args.top_n, args.other)

    plt.show()


if __name__ == '__main__':
    main()
