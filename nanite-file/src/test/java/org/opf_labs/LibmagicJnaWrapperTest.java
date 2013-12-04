package org.opf_labs;

import static org.junit.Assert.assertEquals;

import java.io.FileInputStream;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.junit.Before;
import org.junit.Test;
/**
 * Test to see if the LibmagicJnaWrapper gives the same results as the command line utility.
 * 
 * @author irsital
 */
public class LibmagicJnaWrapperTest {
	/**
	 * Copied the test resources from the DroidDetectorTest class.
	 */
	private static final String WPD_RESOURCE = "src/test/resources/wpd/TOPOPREC.WPD";
	private static final String PDF_RESOURCE = "src/test/resources/simple.pdf";
	
	private LibmagicJnaWrapper magicWrapper;
	private Metadata metadata;

	@Before
	public void setUp() throws Exception {
		// Set the --mime-type option of the file command
		magicWrapper = new LibmagicJnaWrapper(LibmagicJnaWrapper.MAGIC_MIME_TYPE);
		magicWrapper.loadCompiledMagic();
		
		metadata = new Metadata();
	}
	
	@Test
	public void detectPDF() throws Exception	{
		metadata.set(Metadata.RESOURCE_NAME_KEY, PDF_RESOURCE);
		MediaType pdfMime = magicWrapper.detect(new FileInputStream(PDF_RESOURCE), metadata);
		// Running "file --mime-type simple.pdf" gives the same result
		assertEquals(pdfMime.toString(),"application/pdf");
	}
	
	@Test 
	public void detectWPD() throws Exception	{
		metadata.set(Metadata.RESOURCE_NAME_KEY, WPD_RESOURCE);
		MediaType wpdMime = magicWrapper.detect(new FileInputStream(WPD_RESOURCE), metadata);
		// Running "file --mime-type TOPOPREC.WPD" gives the same result
		assertEquals(wpdMime.toString(),"application/octet-stream");
	}
}