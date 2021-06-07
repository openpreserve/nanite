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
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.bind.JAXBException;

import org.apache.commons.io.FileUtils;
import org.apache.tika.detect.Detector;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;

import uk.bl.wa.util.Unpack;
import uk.gov.nationalarchives.droid.command.action.CommandExecutionException;
import uk.gov.nationalarchives.droid.container.ContainerSignatureDefinitions;
import uk.gov.nationalarchives.droid.container.ContainerSignatureSaxParser;
import uk.gov.nationalarchives.droid.core.BinarySignatureIdentifier;
import uk.gov.nationalarchives.droid.core.CustomResultPrinter;
import uk.gov.nationalarchives.droid.core.DroidSigUtils;
import uk.gov.nationalarchives.droid.core.SignatureParseException;
import uk.gov.nationalarchives.droid.core.interfaces.IdentificationRequest;
import uk.gov.nationalarchives.droid.core.interfaces.IdentificationResult;
import uk.gov.nationalarchives.droid.core.interfaces.IdentificationResultCollection;
import uk.gov.nationalarchives.droid.core.interfaces.RequestIdentifier;
import uk.gov.nationalarchives.droid.core.interfaces.resource.FileSystemIdentificationRequest;
import uk.gov.nationalarchives.droid.core.interfaces.resource.RequestMetaData;
import uk.gov.nationalarchives.droid.core.signature.droid6.FFSignatureFile;

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

    private static Logger log = Logger.getLogger(DroidDetector.class.getName());

    // static final String DROID_SIGNATURE_FILE =
    // "DROID_SignatureFile_V100069.xml";
    static final String DROID_SIGNATURE_FILE = "DROID_SignatureFile_V94.xml";
    static final String DROID_SIG_RESOURCE = "droid/" + DROID_SIGNATURE_FILE;

    static final String DROID_SIG_FILE = "" + DROID_SIG_RESOURCE;
    static final String CONTAINER_SIG_FILE = "droid/container-signature-20180920.xml";

    // Set up DROID binary handler:
    private BinarySignatureIdentifier binarySignatureIdentifier;
    private ContainerSignatureDefinitions containerSignatureDefinitions;

    private static final String FORWARD_SLASH = "/";
    private static final String BACKWARD_SLASH = "\\";
    private long maxBytesToScan = -1;
    boolean archives = false;

    private uk.gov.nationalarchives.droid.core.CustomResultPrinter resultPrinter;

    // Options:

    /** Set binarySignaturesOnly to disable container-based identification */
    private boolean binarySignaturesOnly = false;

    /**
     * Whether to allow the BinSig matcher to fall back on the file extension
     * when magic matching fails:
     */
    private boolean allowMatchByFileExtension = false;

    /**
     * When BigSig magic fails, whether to match against all known extensions or
     * just those that have no other signature (BinSig or Container):
     */
    private boolean matchAllExtensions = false;

    /**
     * Disable this flag to make it impossible for the file extension to be used
     * as a format hint
     */
    private boolean passFilenameWithInputStream = true;

    /**
     * Set up DROID resources
     */
    public DroidDetector() throws CommandExecutionException {
        // Set up the binary sig file.
        File fileSignaturesFile;
        try {
            fileSignaturesFile = Unpack.streamToTemp(DroidDetector.class,
                    DROID_SIG_FILE, false);
        } catch (IOException e1) {
            throw new CommandExecutionException(
                    "Signature file could not be extracted! " + e1);
        }

        // Set up container sig file:
        containerSignatureDefinitions = null;
        File containerSignaturesFile = null;
        if (CONTAINER_SIG_FILE != null) {
            try {
                containerSignaturesFile = Unpack.streamToTemp(
                        DroidDetector.class, CONTAINER_SIG_FILE, false);
            } catch (IOException e1) {
                throw new CommandExecutionException(
                        "Container signature file could not be extracted! "
                                + e1);
            }
        }

        // And initialise:
        init(fileSignaturesFile, containerSignaturesFile);

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
     * @throws CommandExecutionException
     */
    public DroidDetector(File fileSignaturesFile, File containerSignaturesFile)
            throws CommandExecutionException {
        init(fileSignaturesFile, containerSignaturesFile);
    }

    /**
     * Initialise the DROID engine using the supplied signature files:
     * 
     * @param fileSignaturesFile
     * @param containerSignaturesFile
     * @throws CommandExecutionException
     */
    private void init(File fileSignaturesFile, File containerSignaturesFile)
            throws CommandExecutionException {
        // Set up the binary sig file.
        binarySignatureIdentifier = new BinarySignatureIdentifier();

        if (!fileSignaturesFile.exists()) {
            throw new CommandExecutionException("Signature file not found");
        }

        binarySignatureIdentifier.setSignatureFile(fileSignaturesFile
                .getAbsolutePath());
        try {
            binarySignatureIdentifier.init();
        } catch (SignatureParseException e) {
            log.log(Level.SEVERE, "Error '" + e.getMessage()
            + "' when parsing " + DROID_SIG_FILE + " unpacked to "
            + fileSignaturesFile, e);
            throw new CommandExecutionException("Can't parse signature file! "
                    + e.getMessage());
        }
        binarySignatureIdentifier.setMaxBytesToScan(maxBytesToScan);
        String path = fileSignaturesFile.getAbsolutePath();
        String slash = path.contains(FORWARD_SLASH) ? FORWARD_SLASH
                : BACKWARD_SLASH;
        String slash1 = slash;

        // Set up container sig file:
        try {
            final InputStream in = new FileInputStream(
                    containerSignaturesFile.getAbsoluteFile());
            final ContainerSignatureSaxParser parser = new ContainerSignatureSaxParser();
            containerSignatureDefinitions = parser.parse(in);
        } catch (SignatureParseException e) {
            throw new CommandExecutionException(
                    "Can't parse container signature file");
        } catch (IOException ioe) {
            throw new CommandExecutionException(ioe);
        } catch (JAXBException jaxbe) {
            throw new CommandExecutionException(jaxbe);
        }

        resultPrinter = new CustomResultPrinter(binarySignatureIdentifier,
                containerSignatureDefinitions, "", slash, slash1, archives);
    }

    public boolean isAllowMatchByFileExtension() {
        return allowMatchByFileExtension;
    }

    public void setAllowMatchByFileExtension(
            boolean allowMatchByFileExtension) {
        this.allowMatchByFileExtension = allowMatchByFileExtension;
    }

    public boolean isMatchAllExtensions() {
        return matchAllExtensions;
    }

    public void setMatchAllExtensions(boolean matchAllExtensions) {
        this.matchAllExtensions = matchAllExtensions;
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
        return binarySignaturesOnly;
    }

    /**
     * @param binarySignaturesOnly
     *            the binarySignaturesOnly to set
     */
    public void setBinarySignaturesOnly(boolean binarySignaturesOnly) {
        this.binarySignaturesOnly = binarySignaturesOnly;
    }

    /**
     * @return the maxBytesToScan
     */
    public long getMaxBytesToScan() {
        return maxBytesToScan;
    }

    /**
     * @param maxBytesToScan
     *            the maxBytesToScan to set
     */
    public void setMaxBytesToScan(long maxBytesToScan) {
        this.maxBytesToScan = maxBytesToScan;
    }

    /**
     * 
     * @param file
     * @return
     */
    public MediaType detect(File file) {
        return getMimeTypeFromResults(detectPUIDs(file));
    }

    /**
     * 
     * @param file
     * @return
     */
    public List<IdentificationResult> detectPUIDs(File file) {
        // As this is a file, use the default number of bytes to inspect
        this.binarySignatureIdentifier.setMaxBytesToScan(this.maxBytesToScan);
        // And identify:
        try {
            String fileName;
            try {
                fileName = file.getCanonicalPath();
            } catch (IOException e) {
                throw new CommandExecutionException(e);
            }
            URI uri = file.toURI();
            RequestMetaData metaData = new RequestMetaData(file.length(),
                    file.lastModified(), fileName);
            RequestIdentifier identifier = new RequestIdentifier(uri);
            identifier.setParentId(1L);

            InputStream in = null;
            IdentificationRequest request = new FileSystemIdentificationRequest(
                    metaData, identifier);
            try {
                in = new FileInputStream(file);
                return getPUIDs(request, in);
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
                if (request != null) {
                    try {
                        request.close();
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

    /**
     * 
     */
    @Override
    public MediaType detect(InputStream input, Metadata metadata)
            throws IOException {
        return getMimeTypeFromResults(detectPUIDs(input, metadata));
    }

    /**
     * 
     * @param input
     * @param metadata
     * @return
     * @throws IOException
     */
    public List<IdentificationResult> detectPUIDs(InputStream input,
            Metadata metadata) throws IOException {

        // As this is an inputstream, restrict the number of bytes to inspect
        // TODO Make this optional:
        // this.binarySignatureIdentifier
        // .setMaxBytesToScan(InputStreamByteReader.BUFFER_SIZE);
        // And identify:
        // Optionally, get filename and identifiers from metadata:
        String fileName = "";
        if (passFilenameWithInputStream) {
            if (metadata.get(Metadata.RESOURCE_NAME_KEY) != null) {
                fileName = metadata.get(Metadata.RESOURCE_NAME_KEY);
            }
        }
        log.finer("Set up filename: " + fileName);
        RequestMetaData metaData = new RequestMetaData(
                (long) input.available(), null, fileName);

        URI nameUri = null;
        try {
            if (fileName.startsWith("file:")) {
                nameUri = new URI(fileName);
            } else {
                nameUri = new URI("file", "", "/" + fileName, null);
            }
        } catch (URISyntaxException e) {
            log.warning("Exception when building filename URI for " + fileName
                    + ": " + e);
            nameUri = URI.create("file://./name-with-no-extension");
        }
        log.finer("Set up nameUri: " + nameUri);
        RequestIdentifier identifier = new RequestIdentifier(nameUri);
        identifier.setParentId(1L);

        InputStreamIdentificationRequest request = new InputStreamIdentificationRequest(
                metaData, identifier);
        try {
            List<IdentificationResult> type = getPUIDs(request, input);
            return type;
        } catch (CommandExecutionException e) {
            log.warning("Caught exception: " + e);
            e.printStackTrace();
            log.warning("Throwing wrapped exception: " + e);
            throw new IOException(e.toString());
        } finally {
            request.removeTempDir();
        }
    }

    /**
     * 
     * @param request
     * @param input
     * @return
     * @throws IOException
     * @throws CommandExecutionException
     */
    private List<IdentificationResult> getPUIDs(
            IdentificationRequest<InputStream> request, InputStream input)
            throws IOException, CommandExecutionException {
        // Open up the stream:
        request.open(input);

        IdentificationResultCollection results = binarySignatureIdentifier
                .matchBinarySignatures(request);
        log.finer("Got " + results.getResults().size() + " matches.");

        // If there is no BinSig match, fall back on file extension:
        List<IdentificationResult> resultList = results.getResults();
        if (resultList != null && resultList.isEmpty()
                && allowMatchByFileExtension) {
            // If we call matchExtensions with "true", it will match
            // ALL files formats which have a given extension.
            // If "false", it will only match file formats for which
            // there is no other signature defined.
            IdentificationResultCollection checkExtensionResults = binarySignatureIdentifier
                    .matchExtensions(request, matchAllExtensions);
            if (checkExtensionResults != null) {
                results = checkExtensionResults;
                log.finer(
                        "Fallen back on file extension results, with # results = "
                                + results.getResults().size());
            }
        }

        // Optionally, return top results from binary signature match only:
        if (this.isBinarySignaturesOnly()) {
            if (results.getResults().size() > 0) {
                return results.getResults();
            } else {
                return null;
            }
        }

        // Also get container results:
        resultPrinter.print(results, request);

        // Return as a MediaType:
        List<IdentificationResult> lir = new ArrayList<>();
        lir.add(resultPrinter.getResult());
        return lir;
    }

    /**
     * 
     * @param result
     * @return
     */
    public static MediaType getMimeTypeFromResult(IdentificationResult result) {
        List<IdentificationResult> list = new ArrayList<>();
        if (result != null) {
            list.add(result);
        }
        return getMimeTypeFromResults(list);
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
    public static MediaType getMimeTypeFromResults(
            List<IdentificationResult> results) {
        if (results == null || results.size() == 0) {
            return MediaType.OCTET_STREAM;
        }
        // Get the first result: TODO This is getting to be a problem since .txt
        // now mismatches.
        IdentificationResult r = results.get(0);
        // It it's NULL:
        if (r == null) {
            return MediaType.OCTET_STREAM;
        }
        // Sort out the MIME type mapping:
        String mimeType = null;
        String mimeTypeString = r.getMimeType();
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
        return resultPrinter.getBinarySignatureFileVersion();
    }

    /**
     * Allow the binary signature file to be accessed directly.
     * 
     * @return FFSignatureFile
     */
    public FFSignatureFile getBinarySignatures() {
        return DroidSigUtils.getSigFile(binarySignatureIdentifier);
    }

    /**
     * Allow the container signatures to be accessed directly.
     * 
     * @return ContainerSignatureDefinitions
     */
    public ContainerSignatureDefinitions getContainerSignatures() {
        return this.containerSignatureDefinitions;
    }

    /* ----- ----- ----- ----- */

    /**
     * 
     * @param args
     * @throws CommandExecutionException
     * @throws IOException
     */
    public static void main(String[] args) throws CommandExecutionException,
    IOException {
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
            metadata.set(Metadata.RESOURCE_NAME_KEY, file.toURI().toString());
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
