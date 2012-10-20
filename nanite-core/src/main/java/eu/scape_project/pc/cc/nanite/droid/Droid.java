package eu.scape_project.pc.cc.nanite.droid;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import uk.gov.nationalarchives.droid.command.action.CommandExecutionException;
import uk.gov.nationalarchives.droid.command.action.DroidCommand;
import uk.gov.nationalarchives.droid.command.action.NoProfileRunCommand;
import uk.gov.nationalarchives.droid.core.BinarySignatureIdentifier;
import uk.gov.nationalarchives.droid.core.interfaces.IdentificationMethod;
import uk.gov.nationalarchives.droid.core.interfaces.IdentificationRequest;
import uk.gov.nationalarchives.droid.core.interfaces.IdentificationResult;
import uk.gov.nationalarchives.droid.core.interfaces.IdentificationResultCollection;
import uk.gov.nationalarchives.droid.core.interfaces.RequestIdentifier;
import uk.gov.nationalarchives.droid.core.interfaces.resource.FileSystemIdentificationRequest;
import uk.gov.nationalarchives.droid.core.interfaces.resource.RequestMetaData;

/**
 * Droid identification service.
 * 
 * @author Fabian Steeg
 * @author <a href="mailto:carl.wilson@bl.uk">Carl Wilson</a> <a
 *         href="http://sourceforge.net/users/carlwilson-bl"
 *         >carlwilson-bl@SourceForge</a> <a
 *         href="https://github.com/carlwilson-bl">carlwilson-bl@github</a>
 */
public final class Droid implements Serializable {
	@SuppressWarnings("unused")
	private static Logger LOG = Logger.getLogger(Droid.class.getName());

	/** The ID for serialization */
	private static final long serialVersionUID = -7116493742376868770L;
	/** The name of the service */
	static final String NAME = "Droid";
	/** The version of the service */
	static final String VERSION = "2.0";
	
	static final String DROID_SIG_FILE = "src/main/resources/DROID_SignatureFile_V45.xml";
	
	// Set up DROID binary handler:
	private final NoProfileRunCommand droid;

	public Droid() {
		droid = new NoProfileRunCommand();
		droid.setArchives(false);
		droid.setQuiet(false);
		droid.setRecursive(false);
		droid.setSignatureFile(DROID_SIG_FILE);
	}

	private IdentificationMethod method;

	/**
	 * Identify a file represented as a byte array using Droid.
	 * 
	 * @param tempFile
	 *            The file to identify using Droid
	 * @return Returns the Pronom IDs found for the file as URIs in a Types
	 *         object
	 */
	public List<URI> identifyOneBinary(final File tempFile) {
		// Set up the identification request
		RequestMetaData metadata = new RequestMetaData(tempFile.length(),
				tempFile.lastModified(), tempFile.getName());
		RequestIdentifier identifier = new RequestIdentifier(tempFile.toURI());
		identifier.setParentId(1L);
		IdentificationRequest request = new FileSystemIdentificationRequest(
				metadata, identifier);
		try {
			request.open(new FileInputStream(tempFile));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		// Get the results collection
		try {
			droid.setResources( new String[] { tempFile.getCanonicalPath() });
			droid.execute();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (CommandExecutionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		/*
		IdentificationResultCollection resultSet = DROID
				.matchBinarySignatures(request);
		List<IdentificationResult> results = resultSet.getResults();

		List<URI> formatHits = new ArrayList<URI>(results.size());
		// Now iterate through the collection and create the format URIs
		for (IdentificationResult result : results) {
			formatHits.add(URI.create("info:pronom/" + result.getPuid()));
			this.method = result.getMethod();
		}
		return formatHits;
		*/
		return null;
	}

}
