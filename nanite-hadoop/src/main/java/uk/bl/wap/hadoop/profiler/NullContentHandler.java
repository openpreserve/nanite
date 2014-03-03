package uk.bl.wap.hadoop.profiler;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

@SuppressWarnings("javadoc")
public class NullContentHandler implements ContentHandler {
		@Override
		public void characters(char[] arg0, int arg1, int arg2)
				throws SAXException { }

		@Override
		public void endDocument() throws SAXException { }

		@Override
		public void endElement(String arg0, String arg1, String arg2)
				throws SAXException { }

		@Override
		public void endPrefixMapping(String arg0)
				throws SAXException { }

		@Override
		public void ignorableWhitespace(char[] arg0, int arg1,
				int arg2) throws SAXException { }

		@Override
		public void processingInstruction(String arg0, String arg1)
				throws SAXException { }

		@Override
		public void setDocumentLocator(Locator arg0) { }

		@Override
		public void skippedEntity(String arg0) throws SAXException { }

		@Override
		public void startDocument() throws SAXException { }

		@Override
		public void startElement(String arg0, String arg1,
				String arg2, Attributes arg3) throws SAXException { }

		@Override
		public void startPrefixMapping(String arg0, String arg1)
				throws SAXException { }
		
}
