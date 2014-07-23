Nanite-Core
=============

This code re-implements the [DROID](https://github.com/digital-preservation/droid) core workflow so that is better suited to running from the CLI, or embedded, or within a Hadoop MapReduce job. Critically, this means being able to identify byte arrays or input streams without assuming they are file-backed (as DROID does).

Example usage
-------------

    DroidDetector droidDetector = new DroidDetector();
    droidDetector.setBinarySignaturesOnly( droidUseBinarySignaturesOnly );
    droidDetector.setMaxBytesToScan(BUF_SIZE);
    Metadata metadata = new Metadata();
    metadata.set(Metadata.RESOURCE_NAME_KEY, "filename");
    MediaType droidType = droidDetector.detect(datastream, metadata);

