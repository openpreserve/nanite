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
dst = {}
for row in tsv_file:
    #print row
    fmtS = row[0]
    fmtT = row[1]
    fmtD = row[2]
    year = row[3]
    count = row[4]
    fmt = fmtD
    if( fmt == "application/octet-stream" or fmt.startswith("application/x-ole2-compound-document-format") ):
	fmt = fmtT
    if( fmt.startswith("text/")):
        fmt = fmtS
    # Normalise, lower case and no space after the ;
    fmt = fmt.lower()
    fmt = fmt.strip();
    fmt = fmt.rstrip(";");
    (type, subtype, params) = mimeparse.parse_mime_type(fmt)
    fmt = type+"/"+subtype
    if not fmt in dst:
        dst[fmt] = 0
    dst[fmt] += int(count)

print "MIME Type\tCount"
for fmt in sorted(dst):
    print fmt,"\t",dst[fmt]

