/**
 * 
 */
package eu.scape_project.pc.cc.nanite.tika;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Map;

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.CompositeParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.ParserDecorator;
import org.apache.tika.sax.WriteOutContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import uk.bl.wap.tika.parser.pdf.itext.PDFParser;

/**
 * Base on http://wiki.apache.org/tika/RecursiveMetadata
 * 
 * @author AnJackson
 * 
 */
public class TikaNanite {

	public static void main(String[] args) throws Exception {
		
		CompositeParser autoDetectParser = new AutoDetectParser();
		// Wrap it in a recursive parser, to access the metadata.
		Parser parser = new RecursiveMetadataParser(autoDetectParser);
		// Override the built-in PDF parser (based on PDFBox) with our own (based in iText):
		MediaType pdf = MediaType.parse("application/pdf");
		Map<MediaType, Parser> parsers = autoDetectParser.getParsers();
		parsers.put( pdf, new PDFParser() );
		autoDetectParser.setParsers(parsers);
		// Set up the context:
		ParseContext context = new ParseContext();
		context.set(Parser.class, parser);

		// Basic handler (ignores/pass-through-in-silence):
		//ContentHandler handler = new DefaultHandler();
		// Abort handler, limiting the output size, to avoid OOM:
		ContentHandler handler = new WriteOutContentHandler(100*1024);
		
		Metadata metadata = new Metadata();
		InputStream stream = TikaInputStream.get(new File(args[0]));
		try {
			parser.parse(stream, handler, metadata, context);
		} finally {
			stream.close();
		}
		
		System.out.println("--EOF--");
		String[] names = metadata.names();
		Arrays.sort(names);
		for( String name : names ) {
			System.out.println("MD:"+name+": "+metadata.get(name));
		}
		System.out.println("----");		
	}

	/**
	 * 
	 * @author AnJackson
	 */
	private static class RecursiveMetadataParser extends ParserDecorator {

		/** */
		private static final long serialVersionUID = 5133646719357986442L;

		public RecursiveMetadataParser(Parser parser) {
			super(parser);
		}

		@Override
		public void parse(InputStream stream, ContentHandler ignore,
				Metadata metadata, ParseContext context) throws IOException,
				SAXException, TikaException {
			
			super.parse(stream, ignore, metadata, context);

			System.out.println("----");
			System.out.println("resourceName = "+metadata.get(Metadata.RESOURCE_NAME_KEY));
			System.out.println("----");
			String[] names = metadata.names();
			Arrays.sort(names);
			for( String name : names ) {
				System.out.println("MD:"+name+": "+metadata.get(name));
			}
			System.out.println("----");
			String text = ignore.toString();
			if( text.length() > 100 ) text = text.substring(0,200);
			System.out.println(text);
		}
	}

}
