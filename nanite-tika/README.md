Nanite - Tika
=============

This is an extension to the Apache Tika toolkit, wrapping and augmenting in order to extract richer and more detailed metadata about the objects.

* It has an extended PDF Parser that can spot PDF/A and Adobe Extension Level variants.
* It uses 'extended MIME Types' to capture format definitions that more closely link back to the software.
* It has a recursive metadata handler, so metadata from child resources can be extracted more easily.

Extended MIME Types
-------------------

To bridge between common MIME type usage and the more formalised format definitions in, e.g. PRONOM, we have produced a convention for extended MIME types that is in accord with the relevant RFCs but allows these identifiers to be mapped.

At heart, we introduce three extended forms:

* For formats with MIME types, we add a version parameter that can be mapped to a PRONOM ID. e.g. instead of just 'application/pdf', we can use 'application/pdf; version=1.4', and map this to PUID fmt/XXX.
* For formats with no MIME type, but with a PRONOM ID, we can mind non-standard MIME types that bridge the gap, such as: 'application/x-puid-fmt-44'
* For formats with neither a MIME Type or a PUID, we can usually fall back on file extensions, e.g. 'application/x-ext-ini' for a '.ini' file.

To help us cope in those cases where a suitable PRONOM record does not yet exist, we need to be able to link a format back to the software that created it. To this end, we also seek to formalise extended MIME Type parameters that can be used to capture the encoding software. This is complicated by the fact that different formats currently encode this information in different and often confusing ways. For example, it is not always clear if a format is documenting the software that created an object, or the software that an object is being encoded for - i.e. when you take a 'Word 14' DOCX and export as 'Word 97-2004' Doc, which software identity is recorded where?

At the most basic, many formats have some 'software' field that we can map to a 'software=XXX' parameter. Notably, PDF documents both the 'Producer' (e.g. Adobe Distiller X) and the 'Creator' meaning the creating application of the source document (e.g. Open Office or Microsoft Word). In other cases, this kind of information is stored in comment fields, like 'Text TextEntry: keyword=Software, value=ImageMagick, encoding=ISO-8859-1, compression=none' in the case of PNG.

It may be possible to collect and normalise these formulations, but for now, we seek to simply document the conflict. Thus, for a particular PDF, we may have an extended MIME Type like this:
<code>
	application/pdf; version=1.4"; creator=Writer; producer="OpenOffice.org 3.2"
</code>
whereas for a ODF document, we have forms like this:
<code>
	application/vnd.oasis.opendocument.text; software="OpenOffice.org/3.2$Win32 OpenOffice.org_project/320m12$Build-9483"
</code>

Although this is rather clumsy, collecting this initial data will help us find a way forward. Finding new mappings, like whether 'pdf:producer' can be mapped to 'software', can be explored using this data and decided upon later.

Ideas
-----

* Should normalise/limit the extended MIME type work, and create a new job that spews out ALL the properties Tika reports in some suitable form.
* DROID still return double-forms sometimes: 'application/rtf, text/rtf'. Note that application/rtf is a superset (according to http://www.iana.org/assignments/media-types/application/rtf).
* SAME for 'audio/vnd.rn-realaudio, audio/x-pn-realaudio', well go with the unregistered 'audio/vnd.rn-realaudio'.
* 'application/lotus123, application/vnd.lotus-1-2-3;', but Tika returns 'application/x-123'!
* 'application/vnd.lotus-1-2-3, application/x-123' from DROID!
* 'application/lwp, application/vnd.lotus-wordpro'
* 'image/vnd.microsoft.icon, image/x-icon', vnd again
* 'application/x-endnote-connect, application/x-endnote-connection', use the long one.
* Gah, sometime contain NULL bytes. 'Digipath^@', 'Acrobat 4.05 Import Plug-in for Windows^@', 'Acrobat 3.0 Scan Plug-in^@', 'Document Project PDF Creator 0.2^@'. So, %s/<Ctrl-V><Ctrl-2>//g
* And some ^K, ^L, ^A, <92>
* Replace some ';' in parmeters with ',' and '=' with ''.
* TODO Check allowed characters and enforce.
* Need to create MimeTypes consistently, so values are "enquoted" consisently.

	Could not parse: text/html; charset: utf-8; charset=utf-8
	Could not parse: image/jpeg; software="adobe photoshop cs3 (10.0x20061208 [20061208.beta.1251 2006/12/08:02:00:00 cutoff; m branch])  macintosh"
	Could not parse: image/jpeg; software="adobe photoshop cs3 (10.0x20061208 [20061208.beta.1251 2006/12/08:02:00:00 cutoff; m branch])  macintosh"; hardware="canon eos 5d"
	Could not parse: application/pdf; producer="itext by lowagie.com (r1.02b;p128)"; version=1.4
	Could not parse: application/xhtml+xml; software="mozilla/4.0 (compatible; www.precedent.co.uk|webpilot v2.3.0.5; windows nt 5.0)"; encoding=iso-8859-1; charset=iso-8859-1

	ERROR: Could not parse: text/html; software="mozilla/4.0 (compatible; xhtml-cms; windows nt 5.0) mshtml->xhtml-cms/2.2.1.1"; charset=us-ascii
	ERROR: Could not parse: text/html; software="adobe photoshop(r) cs web photo gallery>  <meta http-equiv="; charset=iso-8859-1
	ERROR: Could not parse: text/html; software="mozilla/4.0 (compatible; xhtml-cms; windows nt 5.0) mshtml->xhtml-cms/2.3.0.0"; charset=us-ascii
	ERROR: Could not parse: application/pdf; producer="itext by lowagie.com (r1.02b;p128)"; version=1.4
	ERROR: Could not parse: application/xhtml+xml; software="mozilla/4.0 (compatible; www.precedent.co.uk|webpilot v2.3.0.5; windows nt 5.0)"; encoding=iso-8859-1; charset=iso-8859-1



Notes on Tika and PDFBox
------------------------

Building this tool has raised some issues that could be fixed by folding back into Apache Tika and PDFBox.

* Tika's PDFBox PDFParser needed to be copied to extend the metadata extraction. Would be nice if the relevant method was protected instead of private so it could be overridden.
* PNG, JPG, and other extractors could be extended to recover the version of the image format.
* Extend org.apache.tika.parser.image.xmp.JempboxExtractor to extract more, e.g. xmp:CreatorTool, x:xmptk
* Ensure Tika attempts to extract XMP from all supported formats. Does not appear to work for PNG.
