package uk.bl.wap.hadoop.profiler;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.MapReduceBase;
import org.apache.hadoop.mapred.Mapper;
import org.apache.hadoop.mapred.OutputCollector;
import org.apache.hadoop.mapred.Reporter;
import org.apache.log4j.Logger;
import org.apache.pdfbox.io.RandomAccess;
import org.apache.pdfbox.io.RandomAccessFile;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.CloseShieldInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.archive.io.ArchiveRecord;
import org.archive.io.ArchiveRecordHeader;
import org.archive.io.arc.ARCRecord;
import org.opf_labs.LibmagicJnaWrapper;
import org.xml.sax.SAXException;

import uk.bl.wa.hadoop.WritableArchiveRecord;
import uk.bl.wa.nanite.droid.DroidDetector;
import uk.gov.nationalarchives.droid.command.action.CommandExecutionException;

/**
 * 
 * @author Andrew Jackson <Andrew.Jackson@bl.uk>
 *
 */
public class FormatProfilerMapper extends MapReduceBase implements Mapper<Text, WritableArchiveRecord, Text, Text> {

	private static Logger log = Logger.getLogger(FormatProfilerMapper.class.getName());

	//////////////////////////////////////////////////
	// Global constants
	//////////////////////////////////////////////////	
	
	// Whether or not to include the extension in the output
	final boolean INCLUDE_EXTENSION = true;
	
	// Whether or not to buffer the data locally before re-using it (seems to be twice as 
	// fast as not doing this)
	// Testing indicates that this is faster and causes fewer exceptions.  Droid sometimes
	// fails with "resetting to invalid mark" when LOCAL_BUFFER is off.  Creating a new
	// InputStream that re-uses a local byte[] does not cause that failure.
	final boolean LOCAL_BUFFER = true;

	// Should we use libmagic?
	final boolean USE_LIBMAGIC = true;
	
	// Whether to ignore the year of harvest (if so, will set a default year)
	final boolean IGNORE_WAYBACKYEAR = true;
	
	// Maximum buffer size
	private static final int BUF_SIZE = 20*1024*1024;
	
	//////////////////////////////////////////////////
	// Global variables
	//////////////////////////////////////////////////	
	
	// Global buffer for LOCAL_BUFFER use
	private byte[] payload = null;

	private DroidDetector droidDetector = null;
    private Parser tikaParser = new AutoDetectParser();
    private LibmagicJnaWrapper libMagicWrapper = null;
    
	//private DefaultDetector tikaDetector = new DefaultDetector();
	//private TikaDeepIdentifier tda = null;
	//private Ohcount oh = null;

    
    public FormatProfilerMapper() {
	}

	@Override
	public void configure( JobConf job ) {
		// Set up Tika
		//tda = new TikaDeepIdentifier();

		if(USE_LIBMAGIC) {
			// Set up libMagicWrapper
			libMagicWrapper = new LibmagicJnaWrapper();
			// Load default magic file
			libMagicWrapper.loadCompiledMagic();
		}
		
		// Set up Droid
		try {
			droidDetector = new DroidDetector();
		} catch (CommandExecutionException e) {
			log.error("droidDetector CommandExecutionException "+ e);
		}
		
		// Initialise local payload buffer
		if(LOCAL_BUFFER) {
			payload = new byte[BUF_SIZE];
		}
		
	}

	@Override
	public void map( Text key, WritableArchiveRecord value, OutputCollector<Text, Text> output, Reporter reporter ) throws IOException {
		
		// log the file we are processing:
		log.debug("Processing record from: " + key);

		// Year and type from record:
		String waybackYear = "";
		if(IGNORE_WAYBACKYEAR) {
			waybackYear = "2013";
		} else {
			waybackYear = getWaybackYear(value);
		}
		String serverType = getServerType(value);
		log.debug("Server Type: "+serverType);

		// Get filename and separate the extension of the file
		// Use URLEncoder as some URLs cause URISyntaxException in DroidDetector
		String extURL = value.getRecord().getHeader().getUrl();
		
		InputStream datastream = null;
		try {
			
			// Make sure we have something to turn in to a URL!
			if (extURL != null && extURL.length() > 0) {
				extURL = URLEncoder.encode(extURL, "UTF-8");
			} else {
				extURL = "";
			}
			
			// Remove directories
			String file = value.getRecord().getHeader().getUrl();
			if (file != null) {
				final int lastIndexSlash = file.lastIndexOf('/');
				if (lastIndexSlash > 0 & (lastIndexSlash < file.length())) {
					file = file.substring(lastIndexSlash + 1);
				}
			} else {
				file = "";
			}
			
			// Get file extension
			String fileExt = "";
			if (file.contains(".")) {
				fileExt = getFileExt(file);
			}
			
			log.debug("file: "+file+", ext: "+fileExt);
			
			// Need to consume the headers.
			ArchiveRecord record = value.getRecord();
			if (record instanceof ARCRecord) {
				ARCRecord arc = (ARCRecord) record;
				arc.skipHttpHeader();
			}

			// We use this variable when creating a new ByteArrayInputStream so it does not
			// attempt to read past the file size
			int fileSize = 0;
			if(LOCAL_BUFFER) {
				// Fill the buffer, reading at most payload.length bytes
				fileSize = value.getPayloadAsStream().read(payload, 0, payload.length);
				datastream = new ByteArrayInputStream(payload, 0, fileSize);
			} else {
				datastream = new BufferedInputStream(value.getPayloadAsStream(), BUF_SIZE);
				// Mark the datastream so we can re-use it
				// NOTE: this code will fail if the payload is > BUF_SIZE
				datastream.mark(BUF_SIZE);
			}

            // Type according to DroidDetector
            Metadata metadata = new Metadata();
            metadata.set(Metadata.RESOURCE_NAME_KEY, extURL);
            log.trace("Using DroidDetector...");
			final MediaType droidType = droidDetector.detect(datastream, metadata);

			// Reset the datastream
            if(LOCAL_BUFFER) {
            	datastream.close();
            	datastream = new ByteArrayInputStream(payload, 0, fileSize);
            } else {
            	datastream.reset();
            }
            
            String libMagicType = null;
            if(USE_LIBMAGIC) {

            	// Use libmagic-jna-wrapper to identify the file
            	// You need to manually install this to your local maven repo - see pom for download url
            	// Also - libmagic.so needs to be installed on your cluster, for Ubuntu this is
            	// contained in the libmagic-dev package
            	// 
            	// Note: libmagic does not currently consume a metadata object
            	log.trace("Using libMagicWrapper...");
            	// libMagic needs the fileSize to work correctly with our buffering setup
            	libMagicType = libMagicWrapper.getMimeType(datastream, fileSize);

            	// Reset the datastream
            	if(LOCAL_BUFFER) {
            		datastream.close();
            		datastream = new ByteArrayInputStream(payload, 0, fileSize);
            	} else {
            		datastream.reset();
            	}

            }
			
			// Type according to vanilla-Tika
            metadata = new Metadata();
            metadata.set(Metadata.RESOURCE_NAME_KEY, extURL);
            log.trace("Using Tika...");
            String defaultTikaType = tikaDetect(datastream, metadata);
            
            // Try and lose the buffered data
			if(LOCAL_BUFFER) {
				// We should think about emptying/zero-ing payload
				datastream.close();
			} 				
			datastream = null;
            
			String mapOutput = "\"" + serverType + "\"\t\"" + defaultTikaType
					+ "\"\t\"" + droidType + "\"";

			if(USE_LIBMAGIC) {
				mapOutput += "\t\"" + libMagicType + "\"";
			}
			
			if (INCLUDE_EXTENSION) {
				mapOutput = "\"" + fileExt + "\"\t" + mapOutput;
			}

			// Return the output for collation
			output.collect(new Text(mapOutput), new Text(waybackYear));

		} catch (IOException e) {
			log.error("Failed to identify due to IOException:" + e);
			try {
				// Output a result so we can see how many records fail to process
				output.collect(new Text("IOException: "+e.getMessage()), new Text(waybackYear));
			} catch (IOException e1) {
				e1.printStackTrace();
			}
		} catch (NumberFormatException e) {
			// This happens when the (W)ARC is potentially malformed.
			log.error("Potentially malformed (W)ARC file, skipping URL: ("+value.getRecord().getHeader().getUrl()+")");
			try {
				// Output a result so we can see how many records fail to process
				output.collect(new Text("\"Malformed Record\""), new Text(waybackYear));
			} catch (IOException e1) {
				e1.printStackTrace();
			}
		} catch (Exception e) {
			// If we reach this point there has been some serious error we did not anticipate
			log.error("Exception: "+e.getMessage()+" for record ("+value.getRecord().getHeader().getUrl()+")");
			e.printStackTrace();
			try {
				// Output a result so we can see some basic details
				output.collect(new Text("Exception: "+e.getMessage()), new Text(waybackYear));
			} catch (IOException e1) {
				e1.printStackTrace();
			}			
		} finally {
			//payload = null;
			if (datastream != null) {
				// Closing the datastream causes a NumberFormatException in 
				// ArchiveRecord/ARCRecordMetaData, so don't do datastream.close()!
				datastream = null;
			}
		}
	}
	
	/**
	 * Convenience method for getting the file extension from a URI
	 */
	public String getFileExt(String s) {
		String shortenedToExt = s.toLowerCase();
		if(s.contains(".")) {
			try {
				//try and remove as much additional as possible after the path
				shortenedToExt = new URI(s).getPath().toLowerCase();
			} catch (URISyntaxException e) {
			}
			// We assume that the last . is now before the file extension
			if (shortenedToExt.contains(";")) {
				//Removing remaining string after ";" assuming that this interferes with actual extension in some cases 
				shortenedToExt = shortenedToExt.substring(0, shortenedToExt.indexOf(';') + 1);
			}
			shortenedToExt = shortenedToExt.substring(shortenedToExt.lastIndexOf('.') + 1);
		}
		Pattern p = Pattern.compile("^([a-zA-Z0-9]*).*$");
		Matcher m = p.matcher(shortenedToExt);
		boolean found = m.find();
		String ext = "";
		if(found) {
			//m.group(0) is full pattern match, then (1)(2)(3)... for the above pattern
			ext = m.group(1);
		}
		//System.out.println(s+" found: "+found+" ext: "+ext);
		return ext;
	}
	
	/**
	 * This method uses vanilla Tika to get properties of the file
	 */
	private String tikaDetect(InputStream datastream, Metadata metadata) {
		String defaultTikaType = "";
		try {
			BodyContentHandler handler = new BodyContentHandler();
 			// This will parse all files to get meta data information
			tikaParser.parse(datastream, handler, metadata, new ParseContext());
            final String mimeType = metadata.get(Metadata.CONTENT_TYPE);
            defaultTikaType = mimeType;
            // TODO: remove charset info?  We don't really need it here
		} catch (IOException e) {
			log.error("Failed due to IO exception:" + e);
		} catch (TikaException e) {
			log.error("Failed due to TikaException exception:" + e);
		} catch (SAXException e) {
			log.error("Failed due to SAXException exception:" + e);
		}
		return defaultTikaType;
	}
	
	/**
	 * 
	 * @param value
	 * @return
	 */
	private String getServerType(WritableArchiveRecord value) {
		String serverType = "application/x-unknown";
		ArchiveRecordHeader header = value.getRecord().getHeader();
		// Get the server header data:
		if( !header.getHeaderFields().isEmpty() ) {
			// Type according to server:
			serverType = header.getMimetype();
			if( serverType == null ) {
				log.warn("LOG: Server Content-Type is null.");
			}
		} else {
			log.warn("LOG: Empty header fields.");
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
		if( !header.getHeaderFields().isEmpty() ) {
			// The crawl year:
			String waybackDate = ( ( String ) header.getDate() ).replaceAll( "[^0-9]", "" );
			if( waybackDate != null ) 
				waybackYear = waybackDate.substring(0,waybackDate.length()<4?waybackDate.length():4);

		} else {
			log.warn("LOG: Empty header fields!");
		}
		return waybackYear;
	}
	
}
