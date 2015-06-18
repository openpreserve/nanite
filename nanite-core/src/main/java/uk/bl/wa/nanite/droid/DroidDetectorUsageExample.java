/**
 * 
 */
package uk.bl.wa.nanite.droid;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;

import uk.gov.nationalarchives.droid.command.action.CommandExecutionException;
import uk.gov.nationalarchives.droid.core.interfaces.IdentificationResult;

/**
 * 
 * Example of using the DroidDetector directly.
 * 
 * @author Andrew Jackson <Andrew.Jackson@bl.uk>
 *
 */
public class DroidDetectorUsageExample {

	public static void main(String[] args) throws CommandExecutionException,
			FileNotFoundException, IOException {
		DroidDetector dd = new DroidDetector();

		// Can use a File or an InputStream:
		File inFile = new File("src/test/resources/lorem-ipsum.doc");

		// If you use the InputStream, you need to add the resource name if you
		// want extension-based identification to work:
		Metadata metadata = new Metadata();
		metadata.set(Metadata.RESOURCE_NAME_KEY, inFile.toURI().toString());

		// To get the identification as an extended MIME type:
		MediaType mt = dd.detect(inFile);
		// Or:
		mt = dd.detect(new FileInputStream(inFile), metadata);
		// Giving:
		// MIME Type: application/msword; version=97-2003
		System.out.println("MIME Type: " + mt);

		// Or, get the raw DROID results
		List<IdentificationResult> lir = dd.detectPUIDs(inFile);
		for (IdentificationResult ir : lir) {

			System.out.println("PUID: " + ir.getPuid() + " '" + ir.getName()
					+ "' " + ir.getVersion() + " (" + ir.getMimeType()
					+ ") via " + ir.getMethod() + " identification.");
			// PUID: fmt/40 'Microsoft Word Document' 97-2003
			// (application/msword) via Container identification.

			// Which you can then turn into an extended MIME type if required:
			System.out.println("Extended MIME:"
					+ DroidDetector.getMimeTypeFromResult(ir));
			// Extended MIME:application/msword; version=97-2003
		}
	}

}
