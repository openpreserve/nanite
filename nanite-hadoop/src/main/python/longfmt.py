import sys, re
import csv
from utils import *

tsv_file = csv.reader(CommentedFile(open(sys.argv[1], "rb")), delimiter='\t')
dst = {}
byy = {}
minYear = -1
maxYear = -1
for row in tsv_file:
    #print row
    (fmtS, fmtT, fmtD, year, count) = row
    fmt = bestType(fmtS,fmtT,fmtD)
    fmt = reduceType(fmt,True)
    
    # This is a temporary override that looks at other stuff:
    # ---
    try:
        if fmtT != "null":
            (type, subtype, params) = mimeparse.parse_mime_type(fmtT)
    except:
        print "ERROR: Could not parse: "+fmtT
        exit
    key = 'producer'
    if params.has_key(key):
        fmt = params[key]
        fmt = re.sub(r"(\d+)\.\d.*",r"\1",fmt)
    else:
        fmt = None 
    #if params.has_key("version"):
    #    fmt = "{} {}".format(fmt,params["version"])
    # ---
    # 

    if not fmt in byy:
        byy[fmt] = {}
    year = int(year)
    if minYear == -1 or year < minYear:
        minYear = year
    if maxYear == -1 or year > maxYear:
        maxYear = year
    if not year in byy[fmt]:
        byy[fmt][year] = 0
    byy[fmt][year] += int(count)

out = "Format"
for year in range(minYear,maxYear+1):
    out = "{}\t{}".format(out,year)
print out

for fmt in sorted(byy):
    out = fmt
    for year in range(minYear,maxYear+1):
        
        # Calculate total:
        total = 0
        for fmt2 in byy:
            if byy[fmt2].has_key(year):
                total += byy[fmt2][year]
                
        # Output the values:
        if byy[fmt].has_key(year):
            out = "{}\t{}".format(out,100.0*float(byy[fmt][year])/float(total))
        else:
            out = "{}\t{}".format(out,0)
    print out