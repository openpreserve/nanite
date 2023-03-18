Nanite - a friendly swarm of format-identifying robots
======================================================

[![Build Status](https://travis-ci.org/openpreserve/nanite.png?branch=master)](https://travis-ci.org/openpreserve/nanite)

<img src="https://github.com/openplanets/nanite/raw/master/docs/nanite_logo.png" alt="Nanite logo" width="200px" />

The Nanite project builds on DROID and Apache Tika to provide a rich format identification and characterization system. It aims to make it easier to run identification and characterisation at scale, and helps compare and combine the results of different tools.

* nanite-core contains the core identification code, a wrapped version of [DROID](https://github.com/digital-preservation/droid) that can parse InputStreams.
* nanite-historical-sigs contains old versions of the DROID signature files.

Nanite has been used at scale, see this [blog post](http://www.openplanetsfoundation.org/blogs/2014-05-28-weekend-nanite)

Using the Nanite API
--------------------

Since version [1.3.1-90 of nanite-core](http://search.maven.org/#artifactdetails|eu.scape-project.nanite|nanite-core|1.3.1-90|jar), a new API has been introduced to make it possible to get the PUID-level data out, as an alternative to only being able to access the extended MIME type. This was modified slightly in version 1.5.0.

You can use the Nanite API like so:

```java
        // Create a DroidDetector using the default build-in sig file:
        DroidDetector dd = new DroidDetector();
        
		// Can use a File or an InputStream:
		File inFile = new File("src/test/resources/lorem-ipsum.doc");

		// If you use the InputStream, you need to add the resource name if you
		// want extension-based identification to work:
		Metadata metadata = new Metadata();
		metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, inFile.toURI().toString());

		// To get the identification as an extended MIME type:
		MediaType mt = dd.detect(inFile);
		// Or:
		mt = dd.detect(new FileInputStream(inFile), metadata);
		// Giving:
		// MIME Type: application/msword; version=97-2003
		System.out.println("MIME Type: " + mt);

		// Or, get the raw DROID results
		List<ApiResultExtended> lir = dd.identify(inFile);
		for (ApiResultExtended ir : lir) {

			System.out.println("PUID: " + ir.getPuid() + " '" + ir.getName()
					+ "' " + ir.getVersion() + " (" + ir.getMimeType()
					+ ") via " + ir.getMethod() + " identification.");
			// PUID: fmt/40 'Microsoft Word Document' 97-2003
			// (application/msword) via Container identification.

			// Which you can then turn into an extended MIME type if required:
			System.out.println("Extended MIME:"
					+ dd.getMimeTypeFromResult(ir));
			// Extended MIME:application/msword; version=97-2003
		}    
```

The DroidDetector is not thread-safe, and multi-threaded processes should have a separate instance of the DroidDetector for each thread. e.g. by using a ThreadLocal instance.

```java
		ThreadLocal<DroidDetector> threadLocal = new ThreadLocal<>();
		if (threadLocal.get() == null) {
			threadLocal.set(new DroidDetector());
		}
		DroidDetector dd = threadLocal.get();
```

Limitations
-----------

The Nanite system deliberately embeds a copy of the latest PRONOM signature files at the time of release, with the -XX part of the version number tracking the PRONOM release number. i.e. 1.5.0-111 includes PRONOM signature file version 111 and the corresponding container signatures.

Nanite does not support auto-updating the signature files, but if you wish, you can [download them](https://www.nationalarchives.gov.uk/aboutapps/pronom/droid-signature-files.htm) and pass them to the ```DroidDetector``` via the ```DroidDetector(File fileSignaturesFile, File containerSignaturesFile)``` constructor.

Change Log
----------

Version numbers are like `x.x.x-yy` - changes to the `yy` refer to updates to the PRONOM signature files, whereas changes to the x.x.x part refer to changes to the code that uses them. Only the latter are recorded here:

* 1.5.0
    - Major changes and simpler code due to upgrading to use DROID 6.6's droid-api module.
    - Updated signature files to v111 / 20230307.
    - Dropped nanite-hadoop module as maintenane overhead is large and it's not in use. Use webarchive-discovery instead.
* 1.4.1
    - Updates to how temporary files are handled, attempting to ensure large sets of temporary files are not left in place unnecessarily.
* 1.4.0
    - Significant update to the implementation to take advantage of improvements in DROID 6.5. DROID's improved API means less code is required to run it in Nanite.
* 1.3.1
    - Revert to *not* falling back on extension-based identification by default, as enabling this is a breaking API change.
* 1.3.0
    - As of this release, the DROID code for guessing based on file extension is also included by default, if binary signature detection fails. New parameters on the DroidDetector allow this to be controlled.

Acknowledgements
----------------

This work was partially supported by the [SCAPE project](http://scape-project.eu/). The SCAPE project is co-funded by the European Union under FP7 ICT-2009.4.1 (Grant Agreement number 270137)
