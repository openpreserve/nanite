/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */
package uk.bl.wap.tika.parser.pdf;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.File;
import java.io.OutputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Set;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.parser.PdfTextExtractor;

/**
 * PDF parser.
 * <p>
 * This parser can process also encrypted PDF documents if the required password is given as a part of the input metadata associated with a document. If no password is given, then this parser will try decrypting the document using the empty
 * password that's often used with PDFs.
 * 
 * @author Roger Coram, Andrew Jackson <Andrew.Jackson@bl.uk>
 */
public class PDFParser extends AbstractParser {

	/** Serial version UID */
	private static final long serialVersionUID = -752276948656079347L;

	/**
	 * Metadata key for giving the document password to the parser.
	 * 
	 * @since Apache Tika 0.5
	 */
	public static final String PASSWORD = "org.apache.tika.parser.pdf.password";

	private static final Set<MediaType> SUPPORTED_TYPES = Collections.singleton( MediaType.application( "pdf" ) );

	public Set<MediaType> getSupportedTypes( ParseContext context ) {
		return SUPPORTED_TYPES;
	}

	public static void main( String[] args ) {
		try {
			FileInputStream input = new FileInputStream( new File( "src/test/resources/test.pdf" ) );
			OutputStream output = System.out; //new FileOutputStream( new File( "Z:/part-00001.xml" ) );
			PdfReader reader = new PdfReader( input );
			StringBuilder builder = new StringBuilder();

			Metadata metadata = new Metadata();
			PDFParser.extractMetadata( reader, metadata );
			builder.append( "<?xml version=\"1.0\" encoding=\"UTF-8\"?><wctdocs><![CDATA[" );
			builder.append( PDFParser.extractText( reader ) );
			builder.append( "]]></wctdocs>\n" );
			input.close();
			
			output.write( builder.toString().getBytes( "UTF-8" ) );			
			
			output.write( metadata.toString().getBytes( "UTF-8" ) );

			output.close();
		} catch( Exception e ) {
			e.printStackTrace();
		}
	}

	public PDFParser() {}

	public void parse( InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context ) throws IOException, SAXException, TikaException {
		PdfReader reader = new PdfReader( stream );
		PDFParser.extractMetadata( reader, metadata );

		XHTMLContentHandler xhtml = new XHTMLContentHandler( handler, metadata );
		xhtml.startDocument();
		xhtml.startElement( "p" );
		xhtml.characters( new String( PDFParser.extractText( reader ).getBytes( "UTF-8" ), "UTF-8" ) );
		xhtml.endElement( "p" );
		xhtml.endDocument();
	}

	private static String extractText( PdfReader reader ) {
		StringBuilder output = new StringBuilder();
		try {
			int numPages = reader.getNumberOfPages();
			int page = 1;
			while( page <= numPages ) {
				output.append( PdfTextExtractor.getTextFromPage( reader, page ) );
				page++;
			}
		} catch( Exception e ) {
			System.err.println( "PDFParser.extractText(): " + e.getMessage() );
		}
		return output.toString();
	}

	private static void extractMetadata( PdfReader reader, Metadata metadata ) {
		try {
			HashMap<String, String> map = reader.getInfo();
			// Clone the PDF info:
			for( String key : map.keySet() ) {
				metadata.set( key.toLowerCase(), map.get( key ) );
			}
			// Add other data of interest:
			metadata.set("pdf:version", "1."+reader.getPdfVersion());
			metadata.set("pdf:numPages", ""+reader.getNumberOfPages());
			metadata.set("pdf:cryptoMode", ""+reader.getCryptoMode());
			metadata.set("pdf:openedWithFullPermissions", ""+reader.isOpenedWithFullPermissions());
			metadata.set("pdf:encrypted", ""+reader.isEncrypted());
			metadata.set("pdf:metadataEncrypted", ""+reader.isMetadataEncrypted());
			metadata.set("pdf:128key", ""+reader.is128Key());
			metadata.set("pdf:tampered", ""+reader.isTampered());
			// Ensure the normalised metadata are mapped in:
			metadata.set( Metadata.TITLE, map.get( "Title" ) );
			metadata.set( Metadata.AUTHOR, map.get( "Author" ) );
		} catch( Exception e ) {
			System.err.println( "PDFParser.extractMetadata(): " + e.getMessage() );
		}
	}

	/**
	 * @deprecated This method will be removed in Apache Tika 1.0.
	 */
	public void parse( InputStream stream, ContentHandler handler, Metadata metadata ) throws IOException, SAXException, TikaException {
		parse( stream, handler, metadata, new ParseContext() );
	}
}