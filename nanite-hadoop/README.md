Nanite Hadoop
=============

This project brings together the various Nanite identifiers as a Hadoop job, allowing them to be run 
on a set of ARC or WARC web archive files and compare and combine the results from different tools.

It depends on the wap-recordreader packages from the WAP codebase.


TODO

* Change nanite-droid to use file extensions or PUID instead of long names for the x- MIME types.
* Add a nanite-ohcount that uses ohcount-3.0.0-static and at least extracts the main type.


[anjackson@explorer ~]$ ohcount-3.0.0-static -i apps/ohcount-3.0.0/test/src_licenses/gpl_t1.c
Examining 1 file(s)
                              Ohloh Line Count
Language               Code    Comment  Comment %      Blank      Total  File
----------------  ---------  ---------  ---------  ---------  ---------  -----------------------------------------------
c                         0          1     100.0%          0          1  gpl_t1.c

[anjackson@explorer ~]$ ohcount-3.0.0-static -l apps/ohcount-3.0.0/test/src_licenses/gpl_t1.c
gpl gpl_t1.c

[anjackson@explorer ~]$ ohcount-3.0.0-static -d apps/ohcount-3.0.0/test/src_licenses/gpl_t1.c
c       apps/ohcount-3.0.0/test/src_licenses/gpl_t1.c
