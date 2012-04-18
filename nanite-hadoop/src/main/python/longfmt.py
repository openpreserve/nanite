import sys, re
import csv

class CommentedFile:
    def __init__(self, f, commentstring="LOG:"):
        self.f = f
        self.commentstring = commentstring
    def next(self):
        line = self.f.next()
        while line.startswith(self.commentstring) or not line.strip():
            line = self.f.next()
        return line
    def __iter__(self):
        return self

tsv_file = csv.reader(CommentedFile(open("results-nanite-hadoop-0.0.1-warc-big-manually-cleaned.tsv", "rb")),
                      delimiter='\t')
byy = {}
for row in tsv_file:
    #print row
    fmtS = row[0]
    fmtT = row[1]
    fmtD = row[2]
    year = row[3]
    count = row[4]
    if not fmtD in byy:
        byy[fmtD] = {}
    if not year in byy[fmtD]:
        byy[fmtD]["2004"] = 0
        byy[fmtD]["2005"] = 0
        byy[fmtD]["2006"] = 0
        byy[fmtD]["2007"] = 0
        byy[fmtD]["2008"] = 0
    byy[fmtD][year] += int(count)

print "format,2004,2005,2006,2007,2008"
for fmtD in sorted(byy):
    print fmtD,",",byy[fmtD]["2004"],",",byy[fmtD]["2005"],",",byy[fmtD]["2006"],",",byy[fmtD]["2007"],",",byy[fmtD]["2008"]