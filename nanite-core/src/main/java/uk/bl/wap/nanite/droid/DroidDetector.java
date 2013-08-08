/**
 * 
 */
package uk.bl.wap.nanite.droid;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;

import javax.xml.bind.JAXBException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.tika.detect.Detector;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;

import uk.gov.nationalarchives.droid.command.action.CommandExecutionException;
import uk.gov.nationalarchives.droid.container.ContainerSignatureDefinitions;
import uk.gov.nationalarchives.droid.container.ContainerSignatureSaxParser;
import uk.gov.nationalarchives.droid.core.BinarySignatureIdentifier;
import uk.gov.nationalarchives.droid.core.CustomResultPrinter;
import uk.gov.nationalarchives.droid.core.SignatureParseException;
import uk.gov.nationalarchives.droid.core.interfaces.IdentificationRequest;
import uk.gov.nationalarchives.droid.core.interfaces.IdentificationResultCollection;
import uk.gov.nationalarchives.droid.core.interfaces.RequestIdentifier;
import uk.gov.nationalarchives.droid.core.interfaces.resource.FileSystemIdentificationRequest;
import uk.gov.nationalarchives.droid.core.interfaces.resource.RequestMetaData;

/**
 * 
 * Attempts to perform full Droid identification, container and binary signatures.
 * 
 * WARNING! Due to hardcoded references to the underlying file, this only works for File-based identification rather than streams.
 * 
 * 
 * @author Andrew Jackson <Andrew.Jackson@bl.uk>
 *
 */
public class DroidDetector implements Detector {

	/** */
	private static final long serialVersionUID = -170173360485335112L;

	private static Logger log = Logger.getLogger(DroidDetector.class.getName());

    static final String DROID_SIGNATURE_FILE = "DROID_SignatureFile_V66.xml";
    static final String DROID_SIG_RESOURCE = "droid/"+DROID_SIGNATURE_FILE;
    
	static final String DROID_SIG_FILE = "src/main/resources/droid/DROID_SignatureFile_V66.xml";
	static final String containerSignaturesFileName = "src/main/resources/droid/container-signature-20120828.xml";

	// Set up DROID binary handler:
	private BinarySignatureIdentifier binarySignatureIdentifier;
	private ContainerSignatureDefinitions containerSignatureDefinitions;
	
    private static final String FORWARD_SLASH = "/";
    private static final String BACKWARD_SLASH = "\\";
    private int maxBytesToScan = -1;
    boolean archives = false;

	private uk.gov.nationalarchives.droid.core.CustomResultPrinter resultPrinter;
	
	private IOFileFilter extensions;

	private IOFileFilter recursive;


	public DroidDetector() throws CommandExecutionException {
		
        binarySignatureIdentifier = new BinarySignatureIdentifier();
        File fileSignaturesFile = new File(DROID_SIG_FILE);
        if (!fileSignaturesFile.exists()) {
            throw new CommandExecutionException("Signature file not found");
        }

        binarySignatureIdentifier.setSignatureFile(DROID_SIG_FILE);
        try {
            binarySignatureIdentifier.init();
        } catch (SignatureParseException e) {
            throw new CommandExecutionException("Can't parse signature file");
        }
        binarySignatureIdentifier.setMaxBytesToScan(maxBytesToScan);
        String path = fileSignaturesFile.getAbsolutePath();
        String slash = path.contains(FORWARD_SLASH) ? FORWARD_SLASH : BACKWARD_SLASH;
        String slash1 = slash;

        containerSignatureDefinitions = null;
        if (containerSignaturesFileName != null) {
            File containerSignaturesFile = new File(containerSignaturesFileName);
            if (!containerSignaturesFile.exists()) {
                throw new CommandExecutionException("Container signature file not found");
            }
            try {
                final InputStream in = new FileInputStream(containerSignaturesFileName);
                final ContainerSignatureSaxParser parser = new ContainerSignatureSaxParser();
                containerSignatureDefinitions = parser.parse(in);
            } catch (SignatureParseException e) {
                throw new CommandExecutionException("Can't parse container signature file");
            } catch (IOException ioe) {
                throw new CommandExecutionException(ioe);
            } catch (JAXBException jaxbe) {
                throw new CommandExecutionException(jaxbe);
            }
        }
        
		resultPrinter =
                new CustomResultPrinter(binarySignatureIdentifier, containerSignatureDefinitions,
                    "", slash, slash1, archives);
            

        /*
		droid = new NoProfileRunCommand();
		droid.setArchives(false);
		droid.setQuiet(false);
		droid.setRecursive(false);
		droid.setSignatureFile(DROID_SIG_FILE);
		*/
	}
	
	private String identifyFolder(File inFile) {
		String[] resources = new String[] { "" };
		File dirToSearch = new File(resources[0]);

		try {

			if (!dirToSearch.isDirectory()) {
				throw new CommandExecutionException("Resources directory not found");
			}

			Collection<File> matchedFiles =
					FileUtils.listFiles(dirToSearch, this.extensions, this.recursive);
			String fileName = null;
			for (File file : matchedFiles) {
				try {
					fileName = file.getCanonicalPath();
				} catch (IOException e) {
					throw new CommandExecutionException(e);
				}
				URI uri = file.toURI();
				RequestMetaData metaData =
						new RequestMetaData(file.length(), file.lastModified(), fileName);
				RequestIdentifier identifier = new RequestIdentifier(uri);
				identifier.setParentId(1L);

				InputStream in = null;
				IdentificationRequest request = new FileSystemIdentificationRequest(metaData, identifier);
				try {
					in = new FileInputStream(file);
					request.open(in);
					IdentificationResultCollection results =
							binarySignatureIdentifier.matchBinarySignatures(request);
					
					// Also get container results:
					// TODO Strip out the code from the resultPrinter to make a sensible result.
					resultPrinter.print(results, request);
					
					
				} catch (IOException e) {
					throw new CommandExecutionException(e);
				} finally {
					if (in != null) {
						try {
							in.close();
						} catch (IOException e) {
							throw new CommandExecutionException(e);
						}
					}
				}
			}
			return null;
		} catch (CommandExecutionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;		
	}
	
	/** 
	 * Currently, this is the most complete DROID identifier code I have.
	 * This uses a Custom Result Printer to get container results:
	 */
	private MediaType identify(File file) {
		try {
			String fileName;
			try {
				fileName = file.getCanonicalPath();
			} catch (IOException e) {
				throw new CommandExecutionException(e);
			}
			URI uri = file.toURI();
			RequestMetaData metaData =
					new RequestMetaData(file.length(), file.lastModified(), fileName);
			RequestIdentifier identifier = new RequestIdentifier(uri);
			identifier.setParentId(1L);

			InputStream in = null;
			IdentificationRequest request = new FileSystemIdentificationRequest(metaData, identifier);
			try {
				in = new FileInputStream(file);
				request.open(in);
				IdentificationResultCollection results =
						binarySignatureIdentifier.matchBinarySignatures(request);

				// Also get container results:
				resultPrinter.print(results, request);
				// Return as a MediaType:
				return DroidBinarySignatureDetector.getMimeTypeFromResults( resultPrinter.getResult() );
				

			} catch (IOException e) {
				throw new CommandExecutionException(e);
			} finally {
				if (in != null) {
					try {
						in.close();
					} catch (IOException e) {
						throw new CommandExecutionException(e);
					}
				}
			}
		} catch (CommandExecutionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	@Override
	public MediaType detect(InputStream input, Metadata metadata)
			throws IOException {
		try {
			String fileName = "";
			RequestMetaData metaData =
					new RequestMetaData((long) input.available(), null, fileName);
			RequestIdentifier identifier = new RequestIdentifier(URI.create("file:///dummy"));
			identifier.setParentId(1L);

			IdentificationRequest request = new InputStreamIdentificationRequest(metaData, identifier, input);
			try {
				request.open(input);
				IdentificationResultCollection results =
						binarySignatureIdentifier.matchBinarySignatures(request);

				// Also get container results:
				resultPrinter.print(results, request);
				// Return as a MediaType:
				return DroidBinarySignatureDetector.getMimeTypeFromResults( resultPrinter.getResult() );
				

			} catch (IOException e) {
				throw new CommandExecutionException(e);
			}
		} catch (CommandExecutionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;	}
	

	
	private List<URI> identifyOneBinary(final File tempFile) {
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

	/**
	 * @return
	 */
	private String getBinarySignatureFileVersion() {
		return resultPrinter.getBinarySignatureFileVersion();
	}


	public static void main(String[] args) throws CommandExecutionException, IOException {
		DroidDetector dr = new DroidDetector();
		for( String fname : args ) {
			File file = new File(fname);
			System.out.println("---- Identification Starts ---");
			System.out.println("File: "+fname);
			System.out.println("Droid using DROID binary signature file version: "+dr.getBinarySignatureFileVersion());
			System.out.println("---- VIA File ----");
			System.out.println("Result: " + dr.identify(file));
			System.out.println("---- VIA InputStream ----");
			Metadata metadata = new Metadata();
			System.out.println("Result: " + dr.detect(new FileInputStream(file), metadata));
			System.out.println("---- VIA byte array ----");
			byte[] bytes = FileUtils.readFileToByteArray(file);
			System.out.println("Result: " + dr.detect(new ByteArrayInputStream(bytes), metadata));
			System.out.println("----\n");
		}
	}

}
