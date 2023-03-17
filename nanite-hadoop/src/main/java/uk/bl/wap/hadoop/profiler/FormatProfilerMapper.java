package uk.bl.wap.hadoop.profiler;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpParser;
import org.apache.commons.io.input.CloseShieldInputStream;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.MapReduceBase;
import org.apache.hadoop.mapred.Mapper;
import org.apache.hadoop.mapred.OutputCollector;
import org.apache.hadoop.mapred.Reporter;
import org.apache.log4j.Logger;
import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.archive.io.ArchiveRecord;
import org.archive.io.ArchiveRecordHeader;
import org.archive.io.arc.ARCRecord;
import org.archive.io.warc.WARCRecord;

import uk.bl.wa.hadoop.WritableArchiveRecord;
import uk.bl.wa.nanite.droid.DroidDetector;
import uk.gov.nationalarchives.droid.core.SignatureParseException;

/**
 * 
 * @author Andrew Jackson <Andrew.Jackson@bl.uk>
 * @author William Palmer <William.Palmer@bl.uk>
 * 
 */
public class FormatProfilerMapper extends MapReduceBase
        implements Mapper<Text, WritableArchiveRecord, Text, Text> {

    private static Logger log = Logger
            .getLogger(FormatProfilerMapper.class.getName());

    //////////////////////////////////////////////////
    // Global constants
    //////////////////////////////////////////////////

    final private static boolean droidUseBinarySignaturesOnly = false;

    // Maximum buffer size
    final private static int BUF_SIZE = 20 * 1024 * 1024;

    //////////////////////////////////////////////////
    // Global properties
    //////////////////////////////////////////////////

    final static String propertiesFile = "FormatProfiler.properties";
    final static String INCLUDE_EXTENSION = "INCLUDE_EXTENSION";
    final static String INCLUDE_SERVERTYPE = "INCLUDE_SERVERTYPE";
    final static String USE_DROID = "USE_DROID";
    final static String USE_TIKADETECT = "USE_TIKADETECT";
    final static String INCLUDE_WAYBACKYEAR = "INCLUDE_WAYBACKYEAR";

    // NOTE: these default settings may be overridden if
    // FormatProfiler.properties exists as a resource
    private Map<String, Boolean> gProps = new HashMap<String, Boolean>() {
        /**
         * 
         */
        private static final long serialVersionUID = -2539902167731664733L;
        {
            // Whether or not to include the extension in the output
            put(INCLUDE_EXTENSION, Boolean.TRUE);
            // Whether or not to report the server type
            put(INCLUDE_SERVERTYPE, Boolean.TRUE);
            // Should we use Droid?
            put(USE_DROID, Boolean.TRUE);
            // Should we use Tika (detect)?
            put(USE_TIKADETECT, Boolean.TRUE);
            // Whether to ignore the year of harvest (if so, will set a default
            // year)
            put(INCLUDE_WAYBACKYEAR, Boolean.FALSE);
        }
    };

    //////////////////////////////////////////////////
    // Global variables
    //////////////////////////////////////////////////

    private DroidDetector droidDetector = null;
    private Tika tikaDetect = null;

    //////////////////////////////////////////////////
    // Constructors
    //////////////////////////////////////////////////

    /**
     * Default constructor
     */
    public FormatProfilerMapper() {

    }

    //////////////////////////////////////////////////
    // Private methods
    //////////////////////////////////////////////////

    /**
     * Load configuration from the properties file
     */
    private void loadConfig() {

        // load properties
        InputStream p = FormatProfilerMapper.class.getClassLoader()
                .getResourceAsStream(propertiesFile);
        if (p != null) {
            Properties props = new Properties();
            try {
                props.load(p);
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

            log.info("Loaded properties from " + propertiesFile);

            // Iterate on the options and load the default from the class
            // properties on errors
            for (Object key : props.keySet()) {
                if (key instanceof String) {
                    if (gProps.containsKey(key)) {
                        String k = (String) key;
                        gProps.put(k, Boolean.valueOf(props.getProperty(k,
                                gProps.get(k).toString())));
                    }
                }
            }

        }

        for (String key : gProps.keySet()) {
            log.info(key + ": " + gProps.get(key));
        }

    }

    /**
     * Convenience method for getting the file extension from a URI
     * 
     * @param s
     *            path
     * @return file extension
     */
    @SuppressWarnings("unused")
    private String getFileExt(String s) {
        String shortenedToExt = s.toLowerCase();
        if (s.contains(".")) {
            try {
                // try and remove as much additional as possible after the path
                shortenedToExt = new URI(s).getPath().toLowerCase();
            } catch (URISyntaxException e) {
                //
            }
            // We assume that the last . is now before the file extension
            if (shortenedToExt.contains(";")) {
                // Removing remaining string after ";" assuming that this
                // interferes with actual extension in some cases
                shortenedToExt = shortenedToExt.substring(0,
                        shortenedToExt.indexOf(';') + 1);
            }
            shortenedToExt = shortenedToExt
                    .substring(shortenedToExt.lastIndexOf('.') + 1);
        }
        Pattern p = Pattern.compile("^([a-zA-Z0-9]*).*$");
        Matcher m = p.matcher(shortenedToExt);
        boolean found = m.find();
        String ext = "";
        if (found) {
            // m.group(0) is full pattern match, then (1)(2)(3)... for the above
            // pattern
            ext = m.group(1);
        }
        // System.out.println(s+" found: "+found+" ext: "+ext);
        return ext;
    }

    /**
     * Copied from WARCIndexer.java -
     * https://github.com/ukwa/warc-discovery/blob/master/warc-indexer/src/main/java/uk/bl/wa/indexer/WARCIndexer.java#L657
     * 
     * @param fullUrl
     * @return extension
     */
    private static String parseExtension(String fullUrl) {
        if (fullUrl.lastIndexOf("/") != -1) {
            String path = fullUrl.substring(fullUrl.lastIndexOf("/"));
            if (path.indexOf("?") != -1) {
                path = path.substring(0, path.indexOf("?"));
            }
            if (path.indexOf("&") != -1) {
                path = path.substring(0, path.indexOf("&"));
            }
            if (path.indexOf(".") != -1) {
                String ext = path.substring(path.lastIndexOf("."));
                ext = ext.toLowerCase();
                // Avoid odd/malformed extensions:
                // if( ext.contains("%") )
                // ext = ext.substring(0, path.indexOf("%"));
                ext = ext.replaceAll("[^0-9a-z]", "");

                // try and sanitize some extensions
                String e;// = "";
                e = "html";
                if (ext.startsWith(e)) {
                    ext = e;
                }
                e = "jpg";
                if (ext.startsWith(e)) {
                    ext = e;
                }
                e = "jpeg";
                if (ext.startsWith(e)) {
                    ext = e;
                }
                e = "png";
                if (ext.startsWith(e)) {
                    ext = e;
                }

                return ext;
            }
        }
        return null;
    }

    /**
     * 
     * @param value
     * @return
     */
    private String getServerType(WritableArchiveRecord value) {
        String serverType = "application/x-unknown";

        ArchiveRecord record = value.getRecord();
        if (record instanceof WARCRecord) {
            // There are not always headers! The code should check first.
            try {
                String statusLine = HttpParser.readLine(record, "UTF-8");
                Header[] headers = HttpParser.parseHeaders(record, "UTF-8");
                for (Header header : headers) {
                    if ("content-type".equalsIgnoreCase(header.getName())) {
                        serverType = header.getValue().toLowerCase();
                    }
                }
            } catch (HttpException e) {
                log.warn("HttpException while processing server type: " + e);
            } catch (IOException e) {
                log.warn("IOException while processing server type: " + e);
            }
        } else if (record instanceof ARCRecord) {
            ARCRecord arc = (ARCRecord) record;
            ArchiveRecordHeader header = arc.getHeader();
            // Get the server header data:
            if (!header.getHeaderFields().isEmpty()) {
                // Type according to server:
                // force lower case
                serverType = header.getMimetype().toLowerCase();
                if (serverType == null) {
                    log.warn("LOG: Server Content-Type is null.");
                }
            } else {
                log.warn("LOG: Empty header fields.");
            }
            try {
                arc.skipHttpHeader();
            } catch (IOException e) {
                log.warn("IOException while processing server type: " + e);
            }
        } else {
            throw new RuntimeException("Unsupported record type!");
        }

        return serverType;
    }

    /**
     * 
     * @param value
     * @return
     */
    private String getWaybackYear(WritableArchiveRecord value) {
        String waybackYear = "unknown";
        ArchiveRecordHeader header = value.getRecord().getHeader();
        // Get the server header data:
        if (!header.getHeaderFields().isEmpty()) {
            // The crawl year:
            String waybackDate = (header.getDate()).replaceAll("[^0-9]", "");
            if (waybackDate != null)
                waybackYear = waybackDate.substring(0,
                        waybackDate.length() < 4 ? waybackDate.length() : 4);

        } else {
            log.warn("LOG: Empty header fields!");
        }
        return waybackYear;
    }

    /**
     * This is here just for completeness, in case anyone wishes to deserialise
     * a metadata object
     */
    @SuppressWarnings("unused")
    private Object deserialize(byte[] data) {
        Object o = null;
        try {
            ObjectInputStream ois = new ObjectInputStream(
                    new ByteArrayInputStream(Base64.decodeBase64(data)));
            try {
                o = ois.readObject();
            } catch (ClassNotFoundException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            ois.close();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        // Metadata metadata = null;
        // if(o instanceof Metadata) {
        // metadata = (Metadata)o;
        // }
        return o;
    }

    //////////////////////////////////////////////////
    // Mapper methods
    //////////////////////////////////////////////////

    @Override
    public void configure(JobConf job) {

        loadConfig();

        // Set up Droid
        if (gProps.get(USE_DROID)) {
            try {
                droidDetector = new DroidDetector();
                droidDetector
                        .setBinarySignaturesOnly(droidUseBinarySignaturesOnly);
            } catch (IOException e) {
                log.error("droidDetector IOException " + e);
            } catch (SignatureParseException e) {
                log.error("droidDetector SignatureParseException " + e);
			}
        }

        // Set up Tika (detect)
        if (gProps.get(USE_TIKADETECT)) {
            tikaDetect = new Tika();
        }

    }

    /*
     * (non-Javadoc)
     * 
     * @see org.apache.hadoop.mapred.MapReduceBase#close()
     */
    @Override
    public void close() throws IOException {
        // TODO Auto-generated method stub
        super.close();
    }

    @Override
    public void map(Text key, WritableArchiveRecord value,
            OutputCollector<Text, Text> output, Reporter reporter)
            throws IOException {

        // log the file we are processing:
        // log.info("Processing record from: "+key);
        // log.info(" URL: "+value.getRecord().getHeader().getUrl());
        ArchiveRecord record = value.getRecord();
        if (record instanceof WARCRecord) {
            WARCRecord warc = (WARCRecord) record;
            String type = (String) warc.getHeader()
                    .getHeaderValue(WARCRecord.HEADER_KEY_TYPE);
            if (!"response".equals(type)) {
                return;
            }
        }

        // Year and type from record:
        String waybackYear;// = "";
        if (gProps.get(INCLUDE_WAYBACKYEAR)) {
            waybackYear = getWaybackYear(value);
        } else {
            waybackYear = null;
        }

        // Consume the HTTP headers and get the server type:
        final String serverType = getServerType(value);
        log.debug("Server Type: " + serverType);

        // Get filename and separate the extension of the file
        // Use URLEncoder as some URLs cause URISyntaxException in DroidDetector
        String extURL = value.getRecord().getHeader().getUrl();

        InputStream datastream = null;
        try {

            String mapOutput = "";

            if (gProps.get(INCLUDE_EXTENSION)) {
                // Make sure we have something to turn in to a URL!
                // if (extURL != null && extURL.length() > 0) {
                // extURL = URLEncoder.encode(extURL, "UTF-8");
                // } else {
                // extURL = "";
                // }

                // Remove directories
                // String file =
                // extURL;//value.getRecord().getHeader().getUrl();
                // if (file != null) {
                // final int lastIndexSlash = file.lastIndexOf('/');
                // if (lastIndexSlash > 0 & (lastIndexSlash < file.length())) {
                // file = file.substring(lastIndexSlash + 1);
                // }
                // } else {
                // file = "";
                // }

                // Get file extension
                // String fileExt = "";
                // if (file.contains(".")) {
                // fileExt = parseExtension(file);
                // }

                String fileExt = "";
                if (extURL != null && extURL.length() > 0) {
                    // Don't normalise the URL using URLEncoder for this method
                    fileExt = parseExtension(extURL);
                }
                mapOutput = "\"" + fileExt + "\"";
            }

            if (gProps.get(INCLUDE_SERVERTYPE)) {
                mapOutput += "\tSERVER:\"" + serverType + "\"";
            }

            // Sanitize the URL for use by the detectors
            if (extURL != null && extURL.length() > 0) {
                extURL = URLEncoder.encode(extURL, "UTF-8");
            }

            // log.debug("file: "+file+", ext: "+fileExt);

            // Initialise a buffered input stream - the size parameter must be
            // here, otherwise mark() fails on
            // streams longer than 64kb (may be JVM specific)
            // NOTE: we don't use value.getPayloadAsStream() as data may already
            // be buffered in the record
            // datastream = new BoundedInputStream(new BufferedInputStream(new
            // CloseShieldInputStream(record), (int)maxBytesToRead+1),
            // maxBytesToRead);
            datastream = new BufferedInputStream(
                    new CloseShieldInputStream(record), BUF_SIZE);// (int)maxBytesToRead+1);

            // Mark the datastream so we can re-use it
            // NOTE: this code will fail if >BUF_SIZE bytes are read
            datastream.mark(BUF_SIZE);

            if (gProps.get(USE_DROID)) {
                // Type according to DroidDetector
                Metadata metadata = new Metadata();
                metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, extURL);

                log.trace("Using DroidDetector...");
                droidDetector.setMaxBytesToScan(BUF_SIZE);
                final MediaType droidType = droidDetector.detect(datastream,
                        metadata);

                mapOutput += "\tDROID:\"" + droidType + "\"";

                // Reset the datastream for reuse
                datastream.reset();

            }

            String tdaTikaType = "";
            if (gProps.get(USE_TIKADETECT)) {
                // Type according to Tika detect
                Metadata metadata = new Metadata();
                metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, extURL);

                log.trace("Using Tika detect...");
                tdaTikaType = tikaDetect.detect(datastream, metadata);

                mapOutput += "\tTIKA:\"" + tdaTikaType + "\"";

                // Reset the datastream for reuse
                datastream.reset();

            }

            // Try and lose the buffered data
            // datastream = null;

            // Return the output for collation
            output.collect(new Text(mapOutput), new Text(waybackYear));
            log.trace("OUTPUT " + mapOutput + " " + waybackYear);

        } catch (IOException e) {
            log.error("Failed to identify due to IOException:" + e);
            e.printStackTrace();
            try {
                // Output a result so we can see how many records fail to
                // process
                output.collect(new Text("IOException\t\"" + key + "\""),
                        new Text(waybackYear));
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        } catch (NumberFormatException e) {
            // This happens when the (W)ARC is potentially malformed.
            log.error("Potentially malformed (W)ARC file, skipping URL: ("
                    + value.getRecord().getHeader().getUrl() + ")");
            try {
                // Output a result so we can see how many records fail to
                // process
                output.collect(
                        new Text("\"Malformed Record\"\t\"" + key + "\""),
                        new Text(waybackYear));
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        } catch (Exception e) {
            // If we reach this point there has been some serious error we did
            // not anticipate
            log.error("Exception: " + e.getMessage() + " for record ("
                    + value.getRecord().getHeader().getUrl() + ")");
            e.printStackTrace();
            try {
                // Output a result so we can see some basic details
                output.collect(new Text("Exception\t\"" + key + "\""),
                        new Text(waybackYear));
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        } finally {
            if (datastream != null) {
                // The source InputStream is now wrapped in a
                // CloseShieldInputStream, as
                // without it closing the datastream causes a
                // NumberFormatException in
                // ArchiveRecord/ARCRecordMetaData
                datastream.close();
                datastream = null;
            }
        }
    }

}
