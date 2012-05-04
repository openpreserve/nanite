/**
 * 
 */
package uk.bl.wap.tika.parser.warc;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;

import net.didion.loopy.iso9660.ISO9660FileEntry;
import net.didion.loopy.iso9660.ISO9660FileSystem;
import net.didion.loopy.iso9660.ISO9660VolumeDescriptorSet;

import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.io.IOUtils;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.archive.io.ArchiveReader;
import org.archive.io.ArchiveReaderFactory;
import org.archive.io.ArchiveRecord;
import org.archive.io.ArchiveRecordHeader;
import org.archive.io.arc.ARCReader;
import org.archive.io.arc.UncompressedARCReader;


/**
 * @author Andrew Jackson <Andrew.Jackson@bl.uk>
 *
 */
public class WebARCExtractor {

	private final ContentHandler handler;

	private final Metadata metadata;

	private final EmbeddedDocumentExtractor extractor;

	public WebARCExtractor(
			ContentHandler handler, Metadata metadata, ParseContext context) {
		this.handler = handler;
		this.metadata = metadata;

		EmbeddedDocumentExtractor ex = context.get(EmbeddedDocumentExtractor.class);

		if (ex==null) {
			this.extractor = new ParsingEmbeddedDocumentExtractor(context);
		} else {
			this.extractor = ex;
		}

	}

	/* (non-Javadoc)
	 * @see org.apache.tika.parser.AbstractParser#parse(java.io.InputStream, org.xml.sax.ContentHandler, org.apache.tika.metadata.Metadata)
	 */
	//@Override
	public void parse(InputStream stream) throws IOException, SAXException, TikaException {
		XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
		xhtml.startDocument();
		

		ArchiveReader ar = UncompressedARCReader.get("name.arc", stream, true);;
		// This did not work as assumes compressed:
		// ArchiveReaderFactory.get("name.arc", stream, true);
		
		metadata.set("version",ar.getVersion());
		
		if (ar != null) {
				Iterator<ArchiveRecord> it = ar.iterator();

				while (it.hasNext()) {
					ArchiveRecord entry = it.next();
					String name = entry.getHeader().getUrl();
					System.out.println("WebARC - Pre-scan found directory named: "+name);
					// Now parse it...
					// Setup
					Metadata entrydata = new Metadata();
					entrydata.set(Metadata.RESOURCE_NAME_KEY, entry.getHeader().getUrl());
					// Use the delegate parser to parse the compressed document
					if (extractor.shouldParseEmbedded(entrydata)) {
						extractor.parseEmbedded(entry, xhtml, entrydata, true);
					}
				}

		}
		xhtml.endDocument();
	}

	/**
	 * Helper to patch a consistent path from the ISO9660 Entry:
	 * @param entry
	 * @return
	 */
	private String getFullPath(ISO9660FileEntry entry) {
		String fullPath = entry.getPath();
		if( fullPath == null || fullPath.length() == 0 ) fullPath = entry.getName();
		if( fullPath.charAt(0) != '.' ) fullPath = "./"+fullPath;
		return fullPath;
	}

}
