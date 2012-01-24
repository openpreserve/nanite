/**
 * 
 */
package eu.scape_project.pc.cc.nanite.tika;

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

	       public RecursiveMetadataParser(Parser parser) {
	           super(parser);
	       }

	       @Override
	       public void parse(
	               InputStream stream, ContentHandler ignore,
	               Metadata metadata, ParseContext context)
	               throws IOException, SAXException, TikaException {
	           ContentHandler content = new BodyContentHandler();
	           super.parse(stream, content, metadata, context);

	           System.out.println("----");
	           System.out.println(metadata);
	           System.out.println("----");
	           System.out.println(content.toString());
	       }
	   }
	   
}
