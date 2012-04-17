/**
 * 
 */
package eu.scape_project.pc.cc.nanite.tika;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

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
		String tikaType = metadata.get( Metadata.CONTENT_TYPE );
		// PDF Version, if any:
		if( metadata.get("pdf:version") != null ) tikaType += "; version=\""+metadata.get("pdf:version")+"\"";
		// For PDF, create separate tags:
		if( tikaType.startsWith("application/pdf") ) {
			// PDF has Creator and Producer application properties:
			String creator = metadata.get("creator").replace("\"","'");
			if( creator != null ) tikaType += "; creator=\""+creator+"\"";
			String producer = metadata.get("producer").replace("\"","'");
			if( producer != null) tikaType += "; producer=\""+producer+"\"";
		}
		// Application ID, MS Office only AFAICT
		String tikaAppId = "";
		if( metadata.get( Metadata.APPLICATION_NAME ) != null ) tikaAppId += metadata.get( Metadata.APPLICATION_NAME );
		if( metadata.get( Metadata.APPLICATION_VERSION ) != null ) tikaAppId += " "+metadata.get( Metadata.APPLICATION_VERSION);
		// Append the appid
		if( ! "".equals(tikaAppId) ) {
			tikaType = tikaType+"; appid=\""+tikaAppId+"\"";
		}
		// Images, e.g. JPEG and TIFF, can have 'Software', 'tiff:Software',
		String software = null;
		if( metadata.get( "Software" ) != null ) software = metadata.get( "Software" );
		if( metadata.get( Metadata.SOFTWARE ) != null ) software = metadata.get( Metadata.SOFTWARE );
		// PNGs have a 'tEXt tEXtEntry: keyword=Software, value=GPL Ghostscript 8.71'
		String png_textentry = metadata.get("tEXt tEXtEntry");
		if( png_textentry != null && png_textentry.contains("keyword=Software, value=") )
			software = png_textentry.replace("keyword=Software, value=", "");
		/* Some JPEGs have this:
Jpeg Comment: CREATOR: gd-jpeg v1.0 (using IJG JPEG v62), default quality
comment: CREATOR: gd-jpeg v1.0 (using IJG JPEG v62), default quality
		 */
		if( software != null ) {
			tikaType = tikaType+"; software=\""+software+"\"";
		}
		// Return extended MIME Type:
		metadata.set(EXT_MIME_TYPE, tikaType);
		
		// Other sources of modification time?
		//md.get(Metadata.LAST_MODIFIED); //might be useful, as would any embedded version
		// e.g. a jpg with 'Date/Time: 2011:10:07 11:35:42'?
		// e.g. a png with
		// 'Document ImageModificationTime: year=2011, month=7, day=29, hour=15, minute=33, second=5'
		// 'tIME: year=2011, month=7, day=29, hour=15, minute=33, second=5'

	}
	
	

}
