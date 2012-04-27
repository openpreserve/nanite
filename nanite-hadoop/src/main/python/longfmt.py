import sys, re
import csv
from utils import *

tsv_file = csv.reader(CommentedFile(open(sys.argv[1], "rb")), delimiter='\t')
dst = {}
byy = {}
tot = {}
minYear = -1
maxYear = -1
for row in tsv_file:
    #print row
    try:
        (fmtS, fmtT, fmtD, year, count) = row
    except:
        print "ERROR: Could not load ",row
        continue
    
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
        fmt = params[key].lower()
        fmt = re.sub(r"([^\d])(\d+)\.\d.*",r"\1\2.x",fmt)
        #fmt = re.sub(r"[^\d](\d+)\.\d.*",r"",fmt)
    else:
        fmt = None 
    if params.has_key("version"):
        fmt = "{}\t{}".format(fmt,params["version"])
    # ---
    # 
    
    fmt = bestType(fmtS,fmtT,fmtD)
    fmt = reduceType(fmt,True)

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
    # And a total:
    if not year in tot:
        tot[year] = 0
    tot[year] += int(count)

out = "Format"
for year in range(minYear,maxYear+1):
    out = "{}\t{}".format(out,year)
print out

for fmt in sorted(byy):
    out = fmt
    total = 0
    for year in range(minYear,maxYear+1):
                
        # Output the values:
        if byy[fmt].has_key(year):
            #out = "{}\t{}".format(out, 100.0*byy[fmt][year]/tot[year])
            out = "{}\t{}".format(out, byy[fmt][year])
            total += byy[fmt][year]
        else:
            out = "{}\t{}".format(out,0)
            
    print "{}\t{}".format(out,total)
