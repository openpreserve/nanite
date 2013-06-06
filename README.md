Nanite - a friendly swarm of format-identifying robots
======================================================

The Nanite project builds on DROID and Apache Tika to provide a rich format identification and characterization system. It aims to make it easier to run identification and characterisation at scale, and helps compare and combine the results of different tools.

* nanite-core contains the core identification code, including a wrapped version of DROID and other extensions to Apache Tika.
* nanite-ext contains experimental extensions to nanite-core.
* nanite-cli provides a command-line application for using nanite-core.
* nanite-hadoop allows nanite-core identifiers to be run on web archives via Map-Reduce on Apache Hadoop. It depends on the (W)ARC Record Readers from the WAP codebase.
* nanite-web is an experimental web service for format identification, based on nanite-core.
* nanite-fidget is a set of tools specifically for working on file identifiction.

