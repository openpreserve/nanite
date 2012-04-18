import sys, re
import csv
import mimeparse

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

def reduce(fmt):
    if fmt.find("/") == -1:
	return
    fmt = fmt.lower()
    fmt = fmt.strip();
    fmt = fmt.rstrip(";");
    (type, subtype, params) = mimeparse.parse_mime_type(fmt)
    return type+"/"+subtype
    	
dst = {}
for row in tsv_file:
    #print row
    fmtS = row[0].lower()
    fmtT = row[1].lower()
    fmtD = row[2].lower()
    year = row[3].lower()
    count = row[4].lower()

    # Normalise, lower case and no space after the ;
    fmtDr = reduce(fmtD)
    #fmtSr = reduce(fmtS)

    if fmtDr == fmtT:
	print fmtD,fmtT


