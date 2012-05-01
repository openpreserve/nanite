import sys, re
import csv
import mimeparse
from utils import *

tsv_file = csv.reader(CommentedFile(open(sys.argv[1], "rb")), delimiter='\t')
dst = {}
for row in tsv_file:
    #print row
    try:
        (fmtS, fmtT, fmtD, year, count) = row
    except:
        print "ERROR: Could not load ",row
        continue
    fmt = bestType(fmtS,fmtT,fmtD)
    # Normalise, lower case and no space after the ;
    fmt = reduceType(fmt,False)
    
    if not fmt in dst:
        dst[fmt] = 0
    dst[fmt] += int(count)

print "MIME Type\tCount"
tot = 0
for fmt in sorted(dst):
    print fmt,"\t",dst[fmt]
    tot += dst[fmt]

print "TOTAL\t{}".format(tot)

