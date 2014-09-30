Nanite-Core
=============

This code re-implements the [DROID](https://github.com/digital-preservation/droid) core workflow so that is better suited to running from the CLI, or embedded, or within a Hadoop MapReduce job. Critically, this means being able to identify byte arrays or input streams without assuming they are file-backed (as DROID does).

Example usage
-------------

First add the ```nanite-core``` [dependency](http://search.maven.org/#artifactdetails|eu.scape-project.nanite|nanite-core|1.1.6-77|jar) to your Java project, e.g. for Maven:

    <dependency>
        <groupId>eu.scape-project.nanite</groupId>
        <artifactId>nanite-core</artifactId>
        <version>1.1.6-77</version>
    </dependency>

Then from your code, you can use this:

    DroidDetector droidDetector = new DroidDetector();
    Metadata metadata = new Metadata();
    metadata.set(Metadata.RESOURCE_NAME_KEY, "filename");
    MediaType droidType = droidDetector.detect(datastream, metadata);

You can also tweak the DROID configuration if you wish. e.g. this config only uses binary signatures, but allows DROID to scan all the bytes in the bytestream:

    droidDetector.setBinarySignaturesOnly( true ); 
    droidDetector.setMaxBytesToScan( -1 );
