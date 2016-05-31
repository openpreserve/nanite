/**
 * 
 */
package uk.bl.wa.nanite.droid;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

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

import uk.gov.nationalarchives.droid.command.action.CommandExecutionException;
import uk.gov.nationalarchives.droid.core.interfaces.IdentificationMethod;
import uk.gov.nationalarchives.droid.core.interfaces.IdentificationResult;

/**
 * @author Andrew Jackson <Andrew.Jackson@bl.uk>
 *
 */
public class DroidDetectorTest {

	private DroidDetector ddc;
	private DroidDetector ddb;

	/**
	 * @throws java.lang.Exception
	 */
	@Before
	public void setUp() throws Exception {
		ddc = new DroidDetector();
		// Another, with only binary sigs:
		ddb = new DroidDetector();
		ddb.setBinarySignaturesOnly(true);
	}

	/**
	 * @throws java.lang.Exception
	 */
	@After
	public void tearDown() throws Exception {
	}

    @Test
    public void testDetectByExtension() throws FileNotFoundException,
            IOException, CommandExecutionException {

        DroidDetector dde = new DroidDetector();

        // Test a TXT (no file extension matching - should be default):
        innerTestDetectInputStreamMetadata(dde,
                "src/test/resources/plain-text.txt",
                "application/octet-stream");

        // Test a TXT (binary sigs only):
        dde.setAllowMatchByFileExtension(true);
        innerTestDetectInputStreamMetadata(dde,
                "src/test/resources/plain-text.txt", "text/plain");
    }

	/**
	 * Test method for {@link uk.bl.wa.nanite.droid.DroidDetector#detect(java.io.InputStream, org.apache.tika.metadata.Metadata)}.
	 * @throws IOException 
	 * @throws FileNotFoundException 
	 */
	@Test
	public void testDetectInputStreamMetadata() throws FileNotFoundException, IOException {
		// Test a WPD
		innerTestDetectInputStreamMetadata(ddc,
				"src/test/resources/wpd/TOPOPREC.WPD",
				"application/vnd.wordperfect");
		// Test a WPD
		innerTestDetectInputStreamMetadata(ddc, "src/test/resources/cc0.mp3",
				"audio/mpeg");
		// Test a WPD
		innerTestDetectInputStreamMetadata(ddc,
				"src/test/resources/simple.pdf", "application/pdf");
		// Test a DOC
		innerTestDetectInputStreamMetadata(ddc,
				"src/test/resources/lorem-ipsum.doc", "application/msword");

        // Test an ICO
        innerTestDetectInputStreamMetadata(ddc,
                "src/test/resources/favicon.ico", "image/vnd.microsoft.icon");

        // Test a PNG ICO
        innerTestDetectInputStreamMetadata(ddc,
                "src/test/resources/favicon-png.ico", "image/png");

		// --- Binary sigs only:

		// Test a WPD
		innerTestDetectInputStreamMetadata(ddb,
				"src/test/resources/wpd/TOPOPREC.WPD",
				"application/vnd.wordperfect");
		// Test a WPD
		innerTestDetectInputStreamMetadata(ddb, "src/test/resources/cc0.mp3",
				"audio/mpeg");
		// Test a WPD
		innerTestDetectInputStreamMetadata(ddb,
				"src/test/resources/simple.pdf",
				"application/pdf");
		// Test a DOC
		innerTestDetectInputStreamMetadata(ddb,
				"src/test/resources/lorem-ipsum.doc",
				"application/x-puid-fmt-111");

	}

	private void innerTestDetectInputStreamMetadata(DroidDetector dd,
			String filepath,
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
		metadata.set(Metadata.RESOURCE_NAME_KEY, f.getName());
		type = dd.detect(new FileInputStream(f), metadata);
		System.out.println("Got via InputStream: "+type);
		assertEquals(expectedMime, type.getBaseType().toString());
	}


	/**
	 * Test that we have access to the PUIDs
	 */
	@Test
	public void testPUIDs() {
		innerTestPUIDs(ddc, "src/test/resources/wpd/TOPOPREC.WPD", "x-fmt/44",
				IdentificationMethod.BINARY_SIGNATURE);
		innerTestPUIDs(ddc, "src/test/resources/cc0.mp3", "fmt/134",
				IdentificationMethod.BINARY_SIGNATURE);
		innerTestPUIDs(ddc, "src/test/resources/simple.pdf", "fmt/18",
				IdentificationMethod.BINARY_SIGNATURE);
		innerTestPUIDs(ddc, "src/test/resources/lorem-ipsum.doc", "fmt/40",
				IdentificationMethod.CONTAINER);

		// --- Binary sigs only:

		innerTestPUIDs(ddb, "src/test/resources/wpd/TOPOPREC.WPD", "x-fmt/44",
				IdentificationMethod.BINARY_SIGNATURE);
		innerTestPUIDs(ddb, "src/test/resources/cc0.mp3", "fmt/134",
				IdentificationMethod.BINARY_SIGNATURE);
		innerTestPUIDs(ddb, "src/test/resources/simple.pdf", "fmt/18",
				IdentificationMethod.BINARY_SIGNATURE);
		innerTestPUIDs(ddb, "src/test/resources/lorem-ipsum.doc", "fmt/111",
				IdentificationMethod.BINARY_SIGNATURE);
	}

	private void innerTestPUIDs(DroidDetector dd, String testFile,
			String expectedPUID,
			IdentificationMethod method) {
		// Get the PUID results:
		List<IdentificationResult> lir = dd.detectPUIDs(new File(testFile));
		IdentificationResult found = null;
		for (IdentificationResult ir : lir) {
			System.out.println(testFile + ": " + ir.getPuid() + " '"
					+ ir.getName() + "' (" + ir.getMimeType() + ") by "
					+ ir.getMethod());
			if (expectedPUID.equals(ir.getPuid()))
				found = ir;
		}
		assertNotNull("None of the PUIDs matched " + expectedPUID, found);
		assertEquals(method, found.getMethod());
	}

}
