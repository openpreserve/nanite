/**
 * 
 */
package eu.scape_project.pc.cc.nanite.tika;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import javax.activation.MimeTypeParseException;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * @author Andrew Jackson <Andrew.Jackson@bl.uk>
 *
 */
public class PreservationParser extends AutoDetectParser {
	
	public static final String EXT_MIME_TYPE = "Extended-MIME-Type";

	/**
	 * 
	 */
	private static final long serialVersionUID = 6809061887891839162L;

	/**
	 * Modify the configuration as needed:
	 */
	private void modifyParserConfig() {
		// Override the built-in PDF parser (based on PDFBox) with our own (based in iText):
		MediaType pdf = MediaType.parse("application/pdf");
		Map<MediaType, Parser> parsers = getParsers();
		parsers.put( pdf, new uk.bl.wap.tika.parser.pdf.pdfbox.PDFParser() );
		setParsers(parsers);
	}

	/* (non-Javadoc)
	 * @see org.apache.tika.parser.AutoDetectParser#parse(java.io.InputStream, org.xml.sax.ContentHandler, org.apache.tika.metadata.Metadata, org.apache.tika.parser.ParseContext)
	 */
	@Override
	public void parse(InputStream stream, ContentHandler handler,
			Metadata metadata, ParseContext context) throws IOException,
			SAXException, TikaException {
		// Override with custom parsers:
		this.modifyParserConfig();
		
		// Parse:
		super.parse(stream, handler, metadata, context);
		
		// Add extended metadata of preservation interest.
		//
		// Build the extended MIME Type, incorporating version and creator software:
		ExtendedMimeType tikaType = null;
		try {
			tikaType = new ExtendedMimeType( metadata.get( Metadata.CONTENT_TYPE ) );
		} catch (MimeTypeParseException e) {
			// Stop here and return if this failed:
			e.printStackTrace();
			tikaType = ExtendedMimeType.OCTET_STREAM;
		}
		// Content encoding, if any:
		String encoding = metadata.get( Metadata.CONTENT_ENCODING );
		if( encoding != null ) {
			if ( "text".equals( tikaType.getPrimaryType() ) ) {
				tikaType.setParameter( "charset", encoding.toLowerCase() );
			} else {
				tikaType.setParameter( "encoding", encoding );
			}
		}
		// PDF Version, if any:
		if( metadata.get("pdf:version") != null ) tikaType.setVersion( metadata.get("pdf:version") );
		// For PDF, create separate tags:
		if( "application/pdf".equals(tikaType.getBaseType()) ) {
			// PDF has Creator and Producer application properties:
			String creator = metadata.get("pdf:creator");
			if( creator != null ) tikaType.setParameter("creator", creator);
			String producer = metadata.get("pdf:producer");
			if( producer != null) tikaType.setParameter("producer", producer);
		}
		// Application ID, MS Office only AFAICT, and the VERSION is only doc
		String software = null;
		if( metadata.get( Metadata.APPLICATION_NAME ) != null ) software = metadata.get( Metadata.APPLICATION_NAME );
		if( metadata.get( Metadata.APPLICATION_VERSION ) != null ) software += " "+metadata.get( Metadata.APPLICATION_VERSION);
		// Images, e.g. JPEG and TIFF, can have 'Software', 'tiff:Software',
		if( metadata.get( "Software" ) != null ) software = metadata.get( "Software" );
		if( metadata.get( Metadata.SOFTWARE ) != null ) software = metadata.get( Metadata.SOFTWARE );
		if( metadata.get( "generator" ) != null ) software = metadata.get( "generator" );
		// PNGs have a 'tEXt tEXtEntry: keyword=Software, value=GPL Ghostscript 8.71'
		String png_textentry = metadata.get("tEXt tEXtEntry");
		if( png_textentry != null && png_textentry.contains("keyword=Software, value=") )
			software = png_textentry.replace("keyword=Software, value=", "");
		/* Some JPEGs have this:
Jpeg Comment: CREATOR: gd-jpeg v1.0 (using IJG JPEG v62), default quality
comment: CREATOR: gd-jpeg v1.0 (using IJG JPEG v62), default quality
		 */
		if( software != null ) {
			tikaType.setSoftware(software);
		}
		// Also, if there is any trace of any hardware, record it here:
		if( metadata.get( Metadata.EQUIPMENT_MODEL ) != null )
			tikaType.setHardware( metadata.get( Metadata.EQUIPMENT_MODEL));
		
		// Fall back on special type for empty resources:
		if( "0".equals(metadata.get(Metadata.CONTENT_LENGTH)) ) {
			tikaType = ExtendedMimeType.EMPTY;
		}
		
		// Return extended MIME Type:
		metadata.set(EXT_MIME_TYPE, tikaType.toString());
		
		// Other sources of modification time?
		//md.get(Metadata.LAST_MODIFIED); //might be useful, as would any embedded version
		// e.g. a jpg with 'Date/Time: 2011:10:07 11:35:42'?
		// e.g. a png with
		// 'Document ImageModificationTime: year=2011, month=7, day=29, hour=15, minute=33, second=5'
		// 'tIME: year=2011, month=7, day=29, hour=15, minute=33, second=5'

	}
	
	

}
