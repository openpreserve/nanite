Nanite - a tiny droid
=====================

This small amount of code re-implements the DROID core workflow so that is better suited 
to running from the CLI, or embedded, or as an Hadoop map-reduce JAR. Critically, this means being able to 
identify byte arrays or input streams without assuming they are file-backed (as Droid does).

As well as making it easier to re-use DROID, the hope was that signature creation could be made easier too.

Unfortunately, it was not possible to make all DROID features work properly. The core identification code (DroidCore)
mixed a complex, multi-thread process workflow with file and persistance management assumptions. It it also configured 
using a complex two-stage Spring dependency injection setup procedure, which makes stripping out unnecessary functionality 
rather difficult.

These complications mean that it was only possible to extract the core binary internal signature engine
into a re-usable form. The Container-aware detection (i.e. parsing ZIP and OLE2 files to determine if they
are office documents) is not working at all in this version. However, this limitation only affects a handful of formats.

Compilation
-----------

This code required that you have compiled the Droid code and installed it in your local Maven 
repository. Contact the us via http://www.openplanetsfoundation.org/contact if you have 
problems.

Build it using:

```
$  mvn package
```

Run it using:

```
$  java -jar target/pc-cc-nanite-0.0.1-SNAPSHOT-jar-with-dependencies.jar {input file} 
```

Example output:

```
opf:pc-cc-nanite andy$ java -jar target/pc-cc-nanite-0.0.1-SNAPSHOT-jar-with-dependencies.jar ~/Downloads/168-777-1-PB.pdf
2011-10-13 11:11:14,209  WARN Signature [id:293] will always scan up to maximum bytes.  Matches formats:  [Name:Internet Message Format] [PUID:fmt/278]
2011-10-13 11:11:14,257  WARN Signature [id:305] will always scan up to maximum bytes.  Matches formats:  [Name:WARC] [PUID:fmt/289]
MATCHING: fmt/18, Acrobat PDF 1.4 - Portable Document Format 1.4
Content-Type: application/pdf; version=1.4
```

Note use of extended MIME types to act as interoperable identifiers.

I've also found that the -with-dependencies jar can be deployed as part of an Hadoop job and it works fine.

TODO
----
* NOTE that occasional bad matches are difficult to control due to pure Priority model. x-pict should be 'lower priority than' pretty much everything, because a couple of bytes at a fixed offset can collide with other formats, by chance.
* Add an InputstreamIdentificationRequest class.
* Make ByteArray and Inputstream Identification Request classes spool out to tmp files 
so the contained-id can use getSourceFile and open up the data.
* Add in the Container-level identification engine (only does bytestream ID at present).
* Make the slow, auto-updating, Spring-based SignatureManager start-up optional.
* Consider modifying the Droid source code so Zip and other container-opening algorithms 
can operate directly on streams instead of spooling to tmp. Note that this all overlaps
somewhat with JHOVE2.
* Consider stripping log4j config out of /droid-core-interfaces/src/main/resources/log4j.properties because this overrides local config when assembling one jar.
* Consider cleaning up Droid and Planets dependencies, e.g. multiple Spring versions. Perhaps no longer an issue as dependency on planets-suite:core-utils has been dropped.
* Consider cleaning up the SubmissionGateway from droid-results so that the logic that calls the container identification engine and combines the results is put together while keeping it separate from all the threading and other UI-oriented stuff.

