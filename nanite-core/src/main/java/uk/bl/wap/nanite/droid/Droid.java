package uk.bl.wap.nanite.droid;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;

import javax.activation.MimeTypeParseException;
import javax.xml.bind.JAXBException;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.IOFileFilter;

import uk.bl.wap.nanite.ExtendedMimeType;
import uk.bl.wap.nanite.Identification;
import uk.gov.nationalarchives.droid.command.ResultPrinter;
import uk.gov.nationalarchives.droid.command.action.CommandExecutionException;
import uk.gov.nationalarchives.droid.command.action.DroidCommand;
import uk.gov.nationalarchives.droid.command.action.NoProfileRunCommand;
import uk.gov.nationalarchives.droid.container.ContainerSignatureDefinitions;
import uk.gov.nationalarchives.droid.container.ContainerSignatureSaxParser;
import uk.gov.nationalarchives.droid.core.BinarySignatureIdentifier;
import uk.gov.nationalarchives.droid.core.SignatureParseException;
import uk.gov.nationalarchives.droid.core.interfaces.IdentificationMethod;
import uk.gov.nationalarchives.droid.core.interfaces.IdentificationRequest;
import uk.gov.nationalarchives.droid.core.interfaces.IdentificationResult;
import uk.gov.nationalarchives.droid.core.interfaces.IdentificationResultCollection;
import uk.gov.nationalarchives.droid.core.interfaces.RequestIdentifier;
import uk.gov.nationalarchives.droid.core.interfaces.resource.FileSystemIdentificationRequest;
import uk.gov.nationalarchives.droid.core.interfaces.resource.RequestMetaData;
import uk.gov.nationalarchives.droid.core.interfaces.signature.SignatureFileException;
import uk.gov.nationalarchives.droid.core.interfaces.signature.SignatureManagerException;

/**
 * Droid identification service.
 * 
 * @author Fabian Steeg
 * @author <a href="mailto:carl.wilson@bl.uk">Carl Wilson</a> <a
 *         href="http://sourceforge.net/users/carlwilson-bl"
 *         >carlwilson-bl@SourceForge</a> <a
 *         href="https://github.com/carlwilson-bl">carlwilson-bl@github</a>
 */
public final class Droid extends Identification {
	@SuppressWarnings("unused")
	private static Logger LOG = Logger.getLogger(Droid.class.getName());

	static final String DROID_SIG_FILE = "src/main/resources/droid/DROID_SignatureFile_V63.xml";
	
	// Set up DROID binary handler:
	private BinarySignatureIdentifier binarySignatureIdentifier;
	private ContainerSignatureDefinitions containerSignatureDefinitions;
	
    private static final String FORWARD_SLASH = "/";
    private static final String BACKWARD_SLASH = "\\";
    private int maxBytesToScan = -1;
    boolean archives = false;

	private ResultPrinter resultPrinter;

	public Droid() throws CommandExecutionException {
		String fileSignaturesFileName = DROID_SIG_FILE;
		String containerSignaturesFileName = "src/main/resources/droid/container-signature-20120828.xml";
		
        binarySignatureIdentifier = new BinarySignatureIdentifier();
        File fileSignaturesFile = new File(fileSignaturesFileName);
        if (!fileSignaturesFile.exists()) {
            throw new CommandExecutionException("Signature file not found");
        }

        binarySignatureIdentifier.setSignatureFile(fileSignaturesFileName);
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
                new ResultPrinter(binarySignatureIdentifier, containerSignatureDefinitions,
                    path, slash, slash1, archives);
            

        /*
		droid = new NoProfileRunCommand();
		droid.setArchives(false);
		droid.setQuiet(false);
		droid.setRecursive(false);
		droid.setSignatureFile(DROID_SIG_FILE);
		*/
	}

	private IdentificationMethod method;

	private IOFileFilter extensions;

	private IOFileFilter recursive;

	@Override
	public ExtendedMimeType identify(InputStream in) {
		// TODO Auto-generated method stub
		return null;
	}
	
	public String identifyFolder(File inFile) {
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
	
	@Override
	public ExtendedMimeType identify(File file) {
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
			return null;
		} catch (CommandExecutionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	
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


	public static void main(String[] args) throws CommandExecutionException {
		Droid dr = new Droid();
		for( String fname : args ) {
			File file = new File(fname);
			System.out.println("File: "+fname);
			System.out.println("Droid using DROID binary signature file version: ?");
			System.out.println("Result: " + dr.identify(file));
			System.out.println("----");
		}
	}
}
