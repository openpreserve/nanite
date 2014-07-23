Nanite-Core
=============

This is an API wrapper for [DROID](https://github.com/digital-preservation/droid) that enables easy reuse of the DROID identification code.  

It extends the default DROID API by enabling identification of data via InputStreams, instead of just by Files.

Example usage
-------------

    DroidDetector droidDetector = new DroidDetector();
    droidDetector.setBinarySignaturesOnly( droidUseBinarySignaturesOnly );
    droidDetector.setMaxBytesToScan(BUF_SIZE);
    Metadata metadata = new Metadata();
    metadata.set(Metadata.RESOURCE_NAME_KEY, "filename");
    MediaType droidType = droidDetector.detect(datastream, metadata);
