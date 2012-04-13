Nanite Hadoop
=============

This project brings together the various Nanite identifiers as a Hadoop job, allowing them to be run 
on a set of ARC or WARC web archive files and compare and combine the results from different tools.

It depends on the wap-recordreader packages from the WAP codebase.

Running

<code>
$ mvn assembly:assembly
</code>

will build a Hadoop JAR, with the required dependencies packaged in a lib folder inside the 
main JAR file, e.g. 'target/nanite-hadoop-0.0.1-SNAPSHOT-job.jar'.


TODO
----

* Mega bonus points: Add new tika detector for plain text that uses a Weka Baysian filter to spot scripts/code/markup.
* Extreme mega bonus points: Extend tika to run http://cmusphinx.sourceforge.net/ and extract text from audio/video.


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


Notes
=====

There was an occasional error, perhaps due to two versions of POI on the classpath:

<code>
java.lang.NoSuchFieldError: SMALLER_BIG_BLOCK_SIZE_DETAILS
        at org.apache.poi.poifs.filesystem.NPOIFSFileSystem.<init>(NPOIFSFileSystem.java:93)
        at org.apache.poi.poifs.filesystem.NPOIFSFileSystem.<init>(NPOIFSFileSystem.java:190)
        at org.apache.poi.poifs.filesystem.NPOIFSFileSystem.<init>(NPOIFSFileSystem.java:184)
        at org.apache.tika.parser.microsoft.POIFSContainerDetector.getTopLevelNames(POIFSContainerDetector.java:338)
        at org.apache.tika.parser.microsoft.POIFSContainerDetector.detect(POIFSContainerDetector.java:152)
        at org.apache.tika.detect.CompositeDetector.detect(CompositeDetector.java:61)
        at org.apache.tika.Tika.detect(Tika.java:133)
        at org.apache.tika.Tika.detect(Tika.java:180)
        at org.apache.tika.Tika.detect(Tika.java:227)
        at uk.bl.wap.hadoop.profiler.FormatProfilerMapper.map(FormatProfilerMapper.java:128)
        at uk.bl.wap.hadoop.profiler.FormatProfilerMapper.map(FormatProfilerMapper.java:1)
        at org.apache.hadoop.mapred.MapRunner.run(MapRunner.java:50)
        at org.apache.hadoop.mapred.MapTask.runOldMapper(MapTask.java:391)
        at org.apache.hadoop.mapred.MapTask.run(MapTask.java:325)
        at org.apache.hadoop.mapred.LocalJobRunner$Job.run(LocalJobRunner.java:210)
</code>

I modified the assembly of the Hadoop job JAR to exclude the old version of POI that Heritrix 3.1.0 was bringing in,
which the record-readers depend on. This indicates that the Heritrix jar is probably doing too much! Just using 
the (W)ARC readers should not need bring in that kind of dependencies.
