Nanite-Core
=============

This code re-implements the [DROID](https://github.com/digital-preservation/droid) core workflow so that is better suited to running from the CLI, or embedded, or within a Hadoop MapReduce job. Critically, this means being able to identify byte arrays or input streams without assuming they are file-backed (as DROID does).

Unfortunately, it was not possible to make all DROID features work properly. The core identification code (DroidCore) mixed a complex, multi-thread process workflow with file and persistance management assumptions. It it also configured using a complex two-stage Spring dependency injection setup procedure, which makes stripping out unnecessary functionality rather difficult.

These complications mean that it was only possible to extract the core binary internal signature engine into a re-usable form. The Container-aware detection (i.e. parsing ZIP and OLE2 files to determine if they are office documents) is not working at all in this version. However, this limitation only affects a handful of formats.

Example usage
-------------

    DroidDetector droidDetector = new DroidDetector();
    droidDetector.setBinarySignaturesOnly( droidUseBinarySignaturesOnly );
    droidDetector.setMaxBytesToScan(BUF_SIZE);
    Metadata metadata = new Metadata();
    metadata.set(Metadata.RESOURCE_NAME_KEY, "filename");
    MediaType droidType = droidDetector.detect(datastream, metadata);

