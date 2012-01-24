/**
 * 
 */
package eu.scape_project.pc.cc.nanite.tika;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.ParserDecorator;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * @author AnJackson
 * 
 */
public class TikaNanite {

	public static void main(String[] args) throws Exception {
		Parser parser = new RecursiveMetadataParser(new AutoDetectParser());
		ParseContext context = new ParseContext();
		context.set(Parser.class, parser);

		ContentHandler handler = new DefaultHandler();
		Metadata metadata = new Metadata();

		InputStream stream = TikaInputStream.get(new File(args[0]));
		try {
			parser.parse(stream, handler, metadata, context);
		} finally {
			stream.close();
		}
	}

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
			ContentHandler content = new BodyContentHandler();
			super.parse(stream, content, metadata, context);

			System.out.println("----");
			System.out.println(metadata);
			System.out.println("----");
			System.out.println(content.toString());
		}
	}

}
