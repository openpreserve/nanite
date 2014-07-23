Nanite Hadoop
=============

This project brings together nanite-core and other identification/characterisation libraries as a Hadoop job, allowing them to be run on a set of ARC or WARC web archive files.

It depends on the wap-recordreader packages from the WAP codebase.

There are currently two main MapReduce programs included in Nanite Hadoop:

 * GZChecker: this MapReduce program will ensure that all input files can be opened (if they are GZ compressed) and can automatically remove any problematic files for input into the FormatProfiler. This can quickly help identify some problem files instead of allowing them to cause a problem in the FormatProfiler after it has been running for several hours.
 * FormatProfiler: this is the main Nanite Hadoop program and it contains several options for different outputs.  These options can be configured via a properties file within the nanite-hadoop jar, no recompilation required.

There is a MapReduce program that chains the above two programs => NaniteHadoop.java

FormatProfiler configuration options
------------------------------------

The following options can be turned off and on as required

 * Include the file extension in the output (if possible)
 * Include the mimetype reported by the web server when harvested
 * Use nanite-core/DROID for identification (mimetype only)
 * Use Apache Tika for identification (mimetype only)
 * Use Apache Tika for characterisation (detailed information i.e. exif data, page count etc)
 * Use libmagic for identification (mimetype only)
 * Include the year of harvest (if not, will set a default year e.g. 2013 for all records)
 * Generate a c3po compatible zip per input (W)ARC (Tika characterisation required)
 * Generate a SequenceFile per input (W)ARC containing serialized Tika parser Metadata objects (Tika characterisation required)
 * Generate a ZIP file containing serialized Metadata objects; one per input (W)ARC (Tika characterisation required)
 * Include the (W)ARC record headers in the Metadata output

Building an executable JAR
--------------------------

    $ mvn assembly:assembly

The above command will build an executable Hadoop JAR, that contains all the required dependencies inside the main JAR file, e.g. 'target/nanite-hadoop-1.1.7-77-SNAPSHOT-job.jar'.

TODO
----

* Add a MapReduce program for validating the input (W)ARC files, whether they are compressed or not
* Check if the first entry passed to the Map is for the (W)ARC files themselves
