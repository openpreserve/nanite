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
    #
    fmtS = row[0]
    fmtT = row[1]
    fmtD = row[2]
    year = row[3]
    count = row[4]
    fmt = fmtT
    if( fmt == "null" ):
        fmt = "application/octet-stream"
    if( fmt == "application/octet-stream" ):# or fmt.startswith("application/x-puid-fmt-111") ):
	    fmt = fmtD
    # Fall back on DROID if it has a version and tika does not:
    if( fmtD.find('version=') != -1 and fmt.find("version=") == -1):
        fmt = fmtD
    # For unrecognised formats, or
    if( fmt.startswith("text/plain") or fmt == "application/octet-stream" ):
        fmt = fmtS
    # Normalise, lower case and no space after the ;
    #fmt = fmt.lower()
    fmt = fmt.strip()
    fmt = fmt.rstrip(";")
    if( fmt == "null" ):
        fmt = "null/null"
    if( fmt == "text" ):
        fmt = "text/plain"
    try:
        (type, subtype, params) = mimeparse.parse_mime_type(fmt)
    except:
        print "ERROR: Could not parse: "+fmt
        exit   
    fmt = type+"/"+subtype
    # Add version, if required:
    if False and params.has_key('version'):
        v = params['version']
        if not v.startswith('"'):
            v = '"'+v+'"'
        fmt = fmt+'; version='+v
    if not fmt in dst:
        dst[fmt] = 0
    dst[fmt] += int(count)

print "MIME Type\tCount"
for fmt in sorted(dst):
    print fmt,"\t",dst[fmt]

