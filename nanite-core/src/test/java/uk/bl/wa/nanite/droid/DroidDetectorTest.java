/**
 * 
 */
package uk.bl.wa.nanite.droid;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import uk.gov.nationalarchives.droid.core.interfaces.IdentificationResult;

/**
 * @author Andrew Jackson <Andrew.Jackson@bl.uk>
 *
 */
public class DroidDetectorTest {

	private DroidDetector dd;

	/**
	 * @throws java.lang.Exception
	 */
	@Before
	public void setUp() throws Exception {
		dd = new DroidDetector();
	}

	/**
	 * @throws java.lang.Exception
	 */
	@After
	public void tearDown() throws Exception {
	}

	/**
	 * Test method for {@link uk.bl.wa.nanite.droid.DroidDetector#detect(java.io.InputStream, org.apache.tika.metadata.Metadata)}.
	 * @throws IOException 
	 * @throws FileNotFoundException 
	 */
	@Test
	public void testDetectInputStreamMetadata() throws FileNotFoundException, IOException {
		// Test a WPD
		innerTestDetectInputStreamMetadata(
				"src/test/resources/wpd/TOPOPREC.WPD",
				"application/vnd.wordperfect");
		// Test a WPD
		innerTestDetectInputStreamMetadata("src/test/resources/cc0.mp3",
				"audio/mpeg");
		// Test a WPD
		innerTestDetectInputStreamMetadata("src/test/resources/simple.pdf",
				"application/pdf");
		// Test a DOC
		innerTestDetectInputStreamMetadata(
				"src/test/resources/lorem-ipsum.doc", "application/msword");
	}

	private void innerTestDetectInputStreamMetadata(String filepath,
			String expectedMime) throws FileNotFoundException, IOException {
		File f = new File(filepath);
		//File f = new File("src/test/resources/simple.pdf");
		Metadata metadata = new Metadata();
		metadata.set(Metadata.RESOURCE_NAME_KEY, f.toURI().toString());
		// Via File:
		MediaType type = dd.detect(f);
		System.out.println("Got via File: "+type);
		assertEquals(expectedMime, type.getBaseType().toString());
		// Via InputStream:
		metadata = new Metadata();
		metadata.set(Metadata.RESOURCE_NAME_KEY, f.toURI().toString());
		type = dd.detect(new FileInputStream(f), metadata);
		System.out.println("Got via InputStream: "+type);
		assertEquals(expectedMime, type.getBaseType().toString());
	}


	/**
	 * Test that we have access to the PUIDs
	 */
	@Test
	public void testPUIDs() {
		innerTestPUIDs("src/test/resources/wpd/TOPOPREC.WPD", "x-fmt/44");
		innerTestPUIDs("src/test/resources/cc0.mp3", "fmt/134");
		innerTestPUIDs("src/test/resources/simple.pdf", "fmt/18");
		innerTestPUIDs("src/test/resources/lorem-ipsum.doc", "fmt/40");
	}

	private void innerTestPUIDs(String testFile, String expectedPUID) {
		// Get the PUID results:
		List<IdentificationResult> lir = dd.detectPUIDs(new File(testFile));
		boolean found = false;
		for (IdentificationResult ir : lir) {
			System.out.println(testFile + ": " + ir.getPuid() + " '"
					+ ir.getName() + "' (" + ir.getMimeType() + ") by "
					+ ir.getMethod().name());
			if (expectedPUID.equals(ir.getPuid()))
				found = true;
		}
		assertEquals("None of the PUIDs matched " + expectedPUID, true, found);
	}
}
