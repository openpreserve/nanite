/**
 * 
 */
package uk.bl.wa.nanite.droid;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.bind.JAXBException;

import org.apache.commons.io.FileUtils;
import org.apache.tika.detect.Detector;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.bl.wa.util.Unpack;
import uk.gov.nationalarchives.droid.core.SignatureParseException;
import uk.gov.nationalarchives.droid.internal.api.ApiResult;
import uk.gov.nationalarchives.droid.internal.api.ApiResultExtended;
import uk.gov.nationalarchives.droid.internal.api.DroidAPIExtended;
import uk.gov.nationalarchives.droid.core.interfaces.IdentificationMethod;
import uk.gov.nationalarchives.droid.core.interfaces.IdentificationRequest;
import uk.gov.nationalarchives.droid.core.interfaces.IdentificationResult;
import uk.gov.nationalarchives.droid.core.interfaces.IdentificationResultCollection;

/**
 * 
 * Currently, this is the most complete DROID identifier code, that only uses
 * DROID code.
 * 
 * This uses a Custom Result Printer to get container results.
 * 
 * --- This needs review ---
 * 
 * Attempts to perform full Droid identification, container and binary
 * signatures.
 * 
 * Finding the actual droid-core invocation was tricky From droid command line -
 * ReportCommand which launches a profileWalker, - which fires a
 * FileEventHandler when it hits a file, - which submits an Identification
 * request to the AsyncDroid subtype SubmissionGateway, - which calls DroidCore,
 * - which calls uk.gov.nationalarchives.droid.core.BinarySignatureIdentifier -
 * Following which, SubmissionGateway does some handleContainer stuff, executes
 * the container matching engine and does some complex logic to resolve the
 * result.
 * 
 * This is all further complicated by the way a mix of Spring and Java is used
 * to initialize things, which makes partial or fast initialization rather
 * difficult.
 * 
 * For droid-command-line, the stringing together starts with:
 * /droid-command-line/src/main/resources/META-INF/ui-spring.xml this sets up
 * the ProfileContextLocator and the SpringProfileInstanceFactory. Other parts
 * of the code set up Profiles and eventually call:
 * uk.gov.nationalarchives.droid
 * .profile.ProfileContextLocator.openProfileInstanceManager(ProfileInstance)
 * which calls
 * uk.gov.nationalarchives.droid.profile.SpringProfileInstanceFactory
 * .getProfileInstanceManager(ProfileInstance, Properties) which then injects
 * more xml, including:
 * 
 * @see /droid-results/src/main/resources/META-INF/spring-results.xml which sets
 *      up most of the SubmissionGateway and identification stuff (including the
 *      BinarySignatureIdentifier and the Container identifiers).
 * 
 *      The ui-spring.xml file also includes
 *      /droid-results/src/main/resources/META-INF/spring-signature.xml which
 *      sets up the pronomClient for downloading binary and container
 *      signatures.
 * 
 *      So, the profile stuff is hooked into the DB stuff which is hooked into
 *      the identifiers. Everything is tightly coupled, so teasing it apart is
 *      hard work.
 * 
 * @see uk.gov.nationalarchives.droid.submitter.SubmissionGateway (in
 *      droid-results)
 * @see uk.gov.nationalarchives.droid.core.BinarySignatureIdentifier
 * @see uk.gov.nationalarchives.droid.submitter.FileEventHandler.onEvent(File,
 *      ResourceId, ResourceId)
 * 
 *      Also found
 * @see uk.gov.nationalarchives.droid.command.action.DownloadSignatureUpdateCommand
 *      which indicates how to download the latest sig file, but perhaps the
 *      SignatureManagerImpl does all that is needed?
 * 
 * @author Andrew Jackson <Andrew.Jackson@bl.uk>
 * @author Fabian Steeg
 * @author <a href="mailto:carl.wilson@bl.uk">Carl Wilson</a>
 *         <a href="http://sourceforge.net/users/carlwilson-bl"
 *         >carlwilson-bl@SourceForge</a>
 *         <a href="https://github.com/carlwilson-bl">carlwilson-bl@github</a>
 * 
 */
public class DroidDetector implements Detector {

    /** */
    private static final long serialVersionUID = -170173360485335112L;

    private static Logger log = LoggerFactory.getLogger(DroidDetector.class.getName());

    // static final String DROID_SIGNATURE_FILE =
    // "DROID_SignatureFile_V100069.xml";
    static final String DROID_SIGNATURE_FILE = "DROID_SignatureFile_V111.xml";
    static final String DROID_SIG_RESOURCE = "droid/" + DROID_SIGNATURE_FILE;

    static final String DROID_SIG_FILE = "" + DROID_SIG_RESOURCE;
    static final String CONTAINER_SIG_FILE = "droid/container-signature-20230307.xml";
    
    
    // Reference to the Droid API:
    private DroidAPIExtended api;

    //
    boolean archives = false;
    
    public static Property PUID = Property.internalTextBag("nanite:puid");
    public static Property MIMETYPE = Property.internalTextBag("nanite:mimetype");
    public static Property NAME = Property.internalTextBag("nanite:name");
    public static Property VERSION = Property.internalTextBag("nanite:version");
    public static Property METHOD = Property.internalTextBag("nanite:method");

    // Options:

    /**
     * Disable this flag to make it impossible for the file extension to be used
     * as a format hint
     */
    private boolean passFilenameWithInputStream = true;

    /**
     * Set up DROID resources
     * @throws IOException 
     * @throws SignatureParseException 
     */
    public DroidDetector() throws IOException, SignatureParseException {
    	
        // Set up the binary sig file.
        File fileSignaturesFile;
        try {
            fileSignaturesFile = Unpack.streamToTemp(DroidDetector.class,
                    DROID_SIG_FILE, false);
        } catch (IOException e1) {
            throw new IOException(
                    "Signature file could not be extracted! ", e1);
        }

        // Set up container sig file:
        File containerSignaturesFile = null;
        if (CONTAINER_SIG_FILE != null) {
            try {
                containerSignaturesFile = Unpack.streamToTemp(
                        DroidDetector.class, CONTAINER_SIG_FILE, false);
            } catch (IOException e1) {
                throw new IOException(
                        "Container signature file could not be extracted! ",
                                e1);
            }
        }

        // And initialise:
        init(
        		fileSignaturesFile,
        		containerSignaturesFile
        );
        
        // dont fill up tmp space with signature files
        fileSignaturesFile.delete();
        containerSignaturesFile.delete();
    }

    /**
     * Allow caller to provide signature files rather than use the embedded
     * ones.
     * 
     * @param fileSignaturesFile
     * @param containerSignaturesFile
     * @throws SignatureParseException 
     */
    public DroidDetector(File fileSignaturesFile, File containerSignaturesFile) throws SignatureParseException {
    	init(fileSignaturesFile, containerSignaturesFile);
    }

    // Set up with defaults:
    private void init(File fileSignaturesFile, File containerSignaturesFile) throws SignatureParseException {
        api = DroidAPIExtended.getInstance(
        		fileSignaturesFile.toPath(),
        		containerSignaturesFile.toPath(),
        		Long.MAX_VALUE
        );
        // Default to not using extension matches:
        api.setAllowMatchByFileExtension(false);
    }
    

    public boolean isPassFilenameWithInputStream() {
        return passFilenameWithInputStream;
    }

    public void setPassFilenameWithInputStream(
            boolean passFilenameWithInputStream) {
        this.passFilenameWithInputStream = passFilenameWithInputStream;
    }

    /**
     * @return the binarySignaturesOnly
     */
    public boolean isBinarySignaturesOnly() {
        return this.api.isBinarySignaturesOnly();
    }

    /**
     * @param binarySignaturesOnly
     *            the binarySignaturesOnly to set
     */
    public void setBinarySignaturesOnly(boolean binarySignaturesOnly) {
    	this.api.setBinarySignaturesOnly(binarySignaturesOnly);
    }

    public boolean isAllowMatchByFileExtension() {
        return this.api.isAllowMatchByFileExtension();
    }

    public void setAllowMatchByFileExtension(
            boolean allowMatchByFileExtension) {
    	this.api.setAllowMatchByFileExtension(allowMatchByFileExtension);
    }
    
    
    /**
     * @return the maxBytesToScan
     */
    public long getMaxBytesToScan() {
        return this.api.getMaxBytesToScan();
    }

    /**
     * @param maxBytesToScan
     *            the maxBytesToScan to set
     */
    public void setMaxBytesToScan(long maxBytesToScan) {
        this.api.setMaxBytesToScan(maxBytesToScan);
    }

    /**
     * 
     */
    @Override
    public MediaType detect(InputStream input, Metadata metadata)
            throws IOException {

        // As this is an inputstream, restrict the number of bytes to inspect
        // TODO Make this optional:
        // this.binarySignatureIdentifier
        // .setMaxBytesToScan(InputStreamByteReader.BUFFER_SIZE);
        // And identify:
        // Optionally, get filename and identifiers from metadata:
        String fileName = "";
        if (passFilenameWithInputStream) {
            if (metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY) != null) {
                fileName = metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY);
            }
        }
        log.debug("Got filename: " + fileName);
        List<ApiResultExtended> ids = this.api.submit(input, fileName);
		MediaType mt = getMimeTypeFromApiResults(ids);
		this.addResultsToMetadata(ids, metadata, mt);
        return mt;
    }
    
    /**
     * Pass in fields to be noted in the Metadata object.
     * 
     * @param ids
     * @param metadata
     * @param mt
     */
    private void addResultsToMetadata(List<ApiResultExtended> ids, Metadata metadata, MediaType mt) {
        // Also add format information to metadata:
		for( ApiResultExtended id : ids) {
        	metadata.add(PUID, id.getPuid());
        	metadata.add(NAME, id.getName());
        	metadata.add(VERSION, id.getVersion());
        	metadata.add(METHOD, id.getMethod().name());
        }
		metadata.add(MIMETYPE, mt.toString());
    }

    /**
     * 
     * @param file
     * @return
     */
    public MediaType detect(File file) {
    	List<ApiResultExtended> results;
		try {
			results = this.api.submit(file.toPath());
	        return getMimeTypeFromApiResults(results);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
    }
    
    // lower-level api for raw results
    public List<ApiResultExtended> identify(File file) throws IOException {
    	return this.api.submit(file.toPath());
    }

 
    /**
     * 
     * @param result
     * @return
     */
    public MediaType getMimeTypeFromResult(ApiResultExtended result) {
        List<ApiResultExtended> list = new ArrayList<>();
        if (result != null) {
            list.add(result);
        }
        return getMimeTypeFromApiResults(list);
    }

    /**
     * TODO Choose 'vnd' Vendor-style MIME types over other options when there
     * are many in each Result. TODO This does not cope ideally with
     * multiple/degenerate Results. e.g. old TIFF or current RTF that cannot
     * tell the difference so reports no versions. If there are sigs that differ
     * more than this, this will ignore the versions.
     * 
     * @param list
     * @return
     * @throws MimeTypeParseException
     */
    public MediaType getMimeTypeFromApiResults(
            List<ApiResultExtended> results) {
        if (results == null || results.size() == 0) {
            return MediaType.OCTET_STREAM;
        }
        // Get the first result: TODO This is getting to be a problem since .txt
        // now mismatches.
        ApiResultExtended r = results.get(0);
        // It it's NULL:
        if (r == null) {
            return MediaType.OCTET_STREAM;
        }
        // Sort out the MIME type mapping:
        String mimeType = null;
        String mimeTypeString = r.getMimeType();
        // Clean up
        if (mimeTypeString != null && !"".equals(mimeTypeString.trim())) {
            // This sometimes has ", " separated multiple types
            String[] mimeTypeList = mimeTypeString.split(", ");
            // Taking first (highest priority) MIME type:
            mimeType = mimeTypeList[0];
            // Fix case where no base type is supplied (e.g. "vnd.wordperfect"):
            if (mimeType.indexOf('/') == -1) {
                mimeType = "application/" + mimeType;
            }
        }
        // Build a MediaType
        MediaType mediaType = MediaType.parse(mimeType);
        Map<String, String> parameters = null;
        // Is there a MIME Type?
        if (mimeType != null && !"".equals(mimeType)) {
            parameters = new HashMap<>(mediaType.getParameters());
            // Patch on a version parameter if there isn't one there already:
            if (parameters.get("version") == null && r.getVersion() != null
                    && (!"".equals(r.getVersion())) &&
                    // But ONLY if there is ONLY one result.
                    results.size() == 1) {
                parameters.put("version", r.getVersion());
            }
        } else {
            parameters = new HashMap<>();
            // If there isn't a MIME type, make one up:
            String id = "puid-" + r.getPuid().replace("/", "-");
            String name = r.getName().replace("\"", "'");
            // Lead with the PUID:
            mediaType = MediaType.parse("application/x-" + id);
            parameters.put("name", name);
            // Add the version, if set:
            String version = r.getVersion();
            if (version != null && !"".equals(version)
                    && !"null".equals(version)) {
                parameters.put("version", version);
            }
        }

        return new MediaType(mediaType, parameters);
    }

    /**
     * Get the version of the binary signature file
     * 
     * @return String version
     */
    public String getBinarySignatureFileVersion() {
        return api.getBinarySignatureVersion();
    }

    /* ----- ----- ----- ----- */

    /**
     * 
     * @param args
     * @throws CommandExecutionException
     * @throws IOException
     * @throws SignatureParseException 
     */
    public static void main(String[] args) throws IOException, SignatureParseException {
        DroidDetector dr = new DroidDetector();
        for (String fname : args) {
            File file = new File(fname);
            System.out.println("---- Identification Starts ---");
            System.out.println("File: " + fname);
            System.out
            .println("Droid using DROID binary signature file version: "
                    + dr.getBinarySignatureFileVersion());
            System.out.println("---- VIA File ----");
            System.out.println("Result: " + dr.detect(file));
            System.out.println("---- VIA InputStream ----");
            Metadata metadata = new Metadata();
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, file.toURI().toString());
            System.out.println("Result: "
                    + dr.detect(new FileInputStream(file), metadata));
            System.out.println("---- VIA byte array ----");
            byte[] bytes = FileUtils.readFileToByteArray(file);
            System.out.println("Result: "
                    + dr.detect(new ByteArrayInputStream(bytes), metadata));
            System.out.println("----\n");
        }
        
    }

}
