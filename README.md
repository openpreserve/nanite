Nanite - a friendly swarm of format-identifying robots
======================================================

The Nanite project brings together a number of different format identification tools, and aims to make it easier 
to run them at scale, and to compare and combine the results.

* nanite-droid is a re-implementation of the core identification workflow of DROID, and depends on the openplanets version of the DROID codebase.
* nanite-tika is based on Apache Tika.
* nanite-hadoop allows the various identifiers to be run on web archives via Map-Reduce on Apache Hadoop. It depends on the (W)ARC Record Readers from the WAP codebase.

