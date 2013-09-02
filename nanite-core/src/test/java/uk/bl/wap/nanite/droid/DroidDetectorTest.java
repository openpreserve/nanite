/**
 * 
 */
package uk.bl.wap.nanite.droid;

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import uk.bl.wa.nanite.droid.DroidDetector;

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
		File f = new File("src/test/resources/wpd/TOPOPREC.WPD");
		//File f = new File("src/test/resources/simple.pdf");
		Metadata metadata = new Metadata();
		metadata.set(Metadata.RESOURCE_NAME_KEY, f.toURI().toString());
		// Via File:
		MediaType type = dd.detect(f);
		System.out.println("Got via File: "+type);
		assertEquals("application/vnd.wordperfect",type.getBaseType().toString());
		// Via InputStream:
		type = dd.detect(new FileInputStream(f), metadata);
		System.out.println("Got via InputStream: "+type);
		assertEquals("application/vnd.wordperfect",type.getBaseType().toString());
	}

}
