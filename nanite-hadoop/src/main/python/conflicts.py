import sys, re
import csv
import mimeparse
from utils import *


tsv_file = csv.reader(CommentedFile(open(sys.argv[1], "rb")), delimiter='\t')
    	
dst = {}
for row in tsv_file:
    #print row
    (fmtS, fmtT, fmtD, year, count) = row
    
    # Include the version info when comparing
    version = False    

    # Normalise, lower case and no space after the ;
    fmtDr = reduceType(fmtD,version)
    fmtSr = reduceType(fmtS,version)
    fmtTr = reduceType(fmtT,version)

    # Only report when the base type disagrees, ignore parameters:
    #if fmtDr != fmtTr:
    #if fmtTr == "application/octet-stream":
    if True:
        #print fmtS,fmtT,fmtD
        #combo = "{}\t{}".format(fmtSr,fmtTr)
        combo = "{}\t{}".format(fmtSr,fmtTr)
        if not combo in dst:
            dst[combo] = 0
        dst[combo] += int(count)

print "Server Type\tTika Type\tDROID Type\tCount"
for fmt in sorted(dst):
    print "{}\t{}".format(fmt,dst[fmt])

