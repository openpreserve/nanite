Nanite - Tika
=============

This is an extension to the Apache Tika toolkit, wrapping and augmenting in order to extract richer and more detailed metadata about the objects.

* It has an extended PDF Parser that can spot PDF/A and Adobe Extension Level variants.
* It uses 'extended MIME Types' to capture format definitions that more closely link back to the software.
* It has a recursive metadata handler, so metadata from child resources can be extracted more easily.

Ideas
-----
* BUG? Fails to identify lots of PDF/A, suspect some error is uncaught, in these cases, as Tika is just returning application/pdf with no version at all while DROID reports 1a.
** A-1b is better, with Tika returning lost of 1b, even when DROID misses them!
** Likely the Adobe Extension Level code is falling over.
** PDF/A-1a, which only allows 1.4? http://nationalarchives.gov.uk/PRONOM/Format/proFormatSearch.aspx?status=detailReport&id=770&strPageToDisplay=signatures
** PDF/A-1b, which allows PDF v1.3,4,5,6,7 http://nationalarchives.gov.uk/PRONOM/Format/proFormatSearch.aspx?status=detailReport&id=1100&strPageToDisplay=signatures

* IMPORTANT I think that, as used now, the FormatProfiler examines all embedded bitstreams and indeed text but only reports the top-level/outer MIME type.
* For this to work reliably, we will need to modify PackageExtractor so that the parent-child relationship is maintained. Otherwise, the identity of files gets confused when there are ZIPs in ZIPs etc.
* Check allowed parameter/values characters and enforce.
 
* Should normalise/limit the extended MIME type work, and create a new job that spews out ALL the properties Tika reports in some suitable form.

  * DROID still return double-forms sometimes: 'application/rtf, text/rtf'. Note that application/rtf is a superset (according to http://www.iana.org/assignments/media-types/application/rtf).
* SAME for 'audio/vnd.rn-realaudio, audio/x-pn-realaudio', well go with the unregistered 'audio/vnd.rn-realaudio'.
* 'application/lotus123, application/vnd.lotus-1-2-3;', but Tika returns 'application/x-123'!
* 'application/vnd.lotus-1-2-3, application/x-123' from DROID!
* 'application/lwp, application/vnd.lotus-wordpro'
* 'image/vnd.microsoft.icon, image/x-icon', vnd again
* 'application/x-endnote-connect, application/x-endnote-connection', use the long one.
* DONE, but not an ideal fix.

* Gah, sometime contain NULL bytes. 'Digipath^@', 'Acrobat 4.05 Import Plug-in for Windows^@', 'Acrobat 3.0 Scan Plug-in^@', 'Document Project PDF Creator 0.2^@'. So, %s/<Ctrl-V><Ctrl-2>//g
* And some ^K, ^L, ^A, <92>

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
