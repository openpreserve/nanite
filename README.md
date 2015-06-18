Nanite - a friendly swarm of format-identifying robots
======================================================

[![Build Status](https://travis-ci.org/openpreserve/nanite.png?branch=master)](https://travis-ci.org/openpreserve/nanite)

<img src="https://github.com/openplanets/nanite/raw/master/docs/nanite_logo.png" alt="Nanite logo" width="200px" />

The Nanite project builds on DROID and Apache Tika to provide a rich format identification and characterization system. It aims to make it easier to run identification and characterisation at scale, and helps compare and combine the results of different tools.

* nanite-core contains the core identification code, a wrapped version of [DROID](https://github.com/digital-preservation/droid) that can parse InputStreams.
* nanite-hadoop allows nanite-core identifiers to be run on web archives via Map-Reduce on Apache Hadoop. It depends on the (W)ARC Record Readers from the WAP codebase. It can also use [Apache Tika](http://tika.apache.org/) and [libmagic](https://github.com/openpreserve/libmagic-jna-wrapper) for identification.  Files can be characterized using Tika and output in a format suitable for importing into [C3PO](https://github.com/openpreserve/c3po).

Nanite has been used at scale, see this [blog post](http://www.openplanetsfoundation.org/blogs/2014-05-28-weekend-nanite)

Using the Nanite API
--------------------

In version 1.2.0-82, a new API has been introduced to make it possible to get the PUID-level data out, as an alternative to only being able to access the extended MIME type.

You can use the Nanite API like so:

```java
		// Can use a File or an InputStream:
		File inFile = new File("src/test/resources/lorem-ipsum.doc");

		// If you use the InputStream, you need to add the resource name if you
		// want extension-based identification to work:
		Metadata metadata = new Metadata();
		metadata.set(Metadata.RESOURCE_NAME_KEY, inFile.toURI().toString());

		// To get the identification as an extended MIME type:
		MediaType mt = dd.detect(inFile);
		// Or:
		mt = dd.detect(new FileInputStream(inFile), metadata);
		// Giving:
		// MIME Type: application/msword; version=97-2003
		System.out.println("MIME Type: " + mt);

		// Or, get the raw DROID results
		List<IdentificationResult> lir = dd.detectPUIDs(inFile);
		for (IdentificationResult ir : lir) {

			System.out.println("PUID: " + ir.getPuid() + " '" + ir.getName()
					+ "' " + ir.getVersion() + " (" + ir.getMimeType()
					+ ") via " + ir.getMethod() + " identification.");
			// PUID: fmt/40 'Microsoft Word Document' 97-2003
			// (application/msword) via Container identification.

			// Which you can then turn into an extended MIME type if required:
			System.out.println("Extended MIME:"
					+ DroidDetector.getMimeTypeFromResult(ir));
			// Extended MIME:application/msword; version=97-2003
		}
```

The DroidDetector is not threadsafe, and multithreaded processes should have a separate instance of the DroidDetector for each thread.

Acknowledgements
----------------

This work was partially supported by the [SCAPE project](http://scape-project.eu/). The SCAPE project is co-funded by the European Union under FP7 ICT-2009.4.1 (Grant Agreement number 270137)
