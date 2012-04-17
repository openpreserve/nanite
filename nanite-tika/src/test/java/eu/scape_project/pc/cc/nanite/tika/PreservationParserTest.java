/**
 * 
 */
package eu.scape_project.pc.cc.nanite.tika;

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.CompositeParser;
import org.apache.tika.parser.ParseContext;
import org.junit.Before;
import org.junit.Test;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import uk.bl.wap.tika.parser.pdf.pdfbox.PDFParser;

/**
 * @author Andrew Jackson <Andrew.Jackson@bl.uk>
 *
 */
public class PreservationParserTest {
	
	CompositeParser parser = null;

	/**
	 * @throws java.lang.Exception
	 */
	@Before
	public void setUp() throws Exception {
		parser = new PreservationParser();
	}

	/**
	 * Test method for {@link eu.scape_project.pc.cc.nanite.tika.PreservationParser#parse(java.io.InputStream, org.xml.sax.ContentHandler, org.apache.tika.metadata.Metadata, org.apache.tika.parser.ParseContext)}.
	 * @throws TikaException 
	 * @throws SAXException 
	 * @throws IOException 
	 */
	@Test
	public void testParseInputStreamContentHandlerMetadataParseContext() throws IOException, SAXException, TikaException {
		FileInputStream input = new FileInputStream( new File( "src/test/resources/simple-PDFA-1a.pdf" ) );
	
		Metadata metadata = new Metadata();
		parser.parse(input, new DefaultHandler() , metadata, new ParseContext() );
		input.close();
		
		for( String key : metadata.names() ) {
			System.out.write( (key+" : "+metadata.get(key)+"\n").getBytes( "UTF-8" ) );
		}
		
		//for( String name : md.names() ) {
		//}
		String tikaType = metadata.get(PreservationParser.EXT_MIME_TYPE);
		assertEquals(tikaType, "application/pdf; version=\"A-1a\"; creator=\"Writer\"; producer=\"OpenOffice.org 3.2\"");
	}
	
	

}
