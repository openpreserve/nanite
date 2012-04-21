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

def appendParameter(fmt, params, param):
    if params.has_key(param):
        v = params[param]
        if not v.startswith('"'):
            v = '"'+v+'"'
        fmt = "{}; {}={}".format(fmt,param,v)
    return fmt

def reduceType(fmt,version=False):
    if( fmt == "null" ):
        fmt = "application/x-unknown"
    if( fmt == "text" ):
        fmt = "text/plain"
    if fmt.find("/") == -1:
	       return fmt
    fmt = fmt.rstrip(";");
    #fmt = fmt.strip();
    try:
        (type, subtype, params) = mimeparse.parse_mime_type(fmt)
    except:
        print "ERROR: Could not parse: "+fmt
        exit   
    fmt = type+"/"+subtype
    # Add name, to keep PUIDs understandable
    fmt = appendParameter(fmt,params,"name")
    # Add version, if required:
    if version:
        fmt = appendParameter(fmt,params,"version")
    return fmt

def bestType(fmtS,fmtT,fmtD):
    fmt = fmtT
    if( fmt == "null" ):
        fmt = "application/octet-stream"
    if( fmt == "application/octet-stream" ):
        fmt = fmtD
    # Fall back on DROID if it has a version and tika does not:
    if( fmt.find('version=') == -1 and fmtD.find("version=") != -1):
        fmt = fmtD
    # For unrecognised formats, report the server identity
    if( fmt.startswith("text/plain") or fmt == "application/octet-stream" ):
        fmt = fmtS
    return fmt
    	
