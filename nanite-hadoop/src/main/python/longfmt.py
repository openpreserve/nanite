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
    
    #if params.has_key("version"):
    #    fmt = "{}\t{}".format(subtype,params["version"])
    #else:
    #    fmt = "{}\t{}".format(subtype,"-")
    fmt = "{}".format(subtype)
        
    key = 'software'
    #key = 'hardware'
    if params.has_key(key):
        val = params[key]
        # Include the major version, aggregate the rest:
        val = re.sub(r"([^\d])(\d+)\.\d[^\"]*",r"\1\2.x",val)
        # Just aggregate on the main name, ignoring what looks like version info:
        #val = re.sub(r"[^\d](\d+)\.\d.*",r"",val)
        fmt = "{}\t{}".format(fmt,val)
    else:
        fmt = "{}\t{}".format(fmt,None)
        
    
    # ---
    # 
    
    #fmt = bestType(fmtS,fmtT,fmtD)
    #fmt = reduceType(fmt,True)
    
    #fmt = reduceType(fmt,False)
    #fmt = "X"
    
    # Aggregate stats:
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
    # And a total summing over format:
    if not year in tot:
        tot[year] = 0
    tot[year] += int(count)

out = "Format"
for year in range(minYear,maxYear+1):
    out = "{}\t{}".format(out,year)
print out

ftot = {}
for fmt in sorted(byy):
    out = fmt
    total = 0
    for year in range(minYear,maxYear+1):
        # Set up formats counter
        if not year in ftot:
            ftot[year] = 0
        
        # Output the values:
        if byy[fmt].has_key(year):
            #out = "{}\t{}".format(out, 100.0*byy[fmt][year]/tot[year])
            out = "{}\t{}".format(out, byy[fmt][year])
            total += byy[fmt][year]
            # Count total formats:
            if byy[fmt][year] > 100:
                ftot[year] += 1
        else:
            out = "{}\t{}".format(out,0)
            
    print "{}\t{}".format(out,total)

out = "Totals"
for year in range(minYear,maxYear+1):
    out = "{}\t{}".format(out,tot[year])
print out

out = "Total Formats"
for year in range(minYear,maxYear+1):
    out = "{}\t{}".format(out,ftot[year])
print out
