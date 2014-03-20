package uk.bl.wap.hadoop.profiler;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.SequenceFile;
import org.apache.hadoop.io.SequenceFile.CompressionType;
import org.apache.hadoop.io.SequenceFile.Writer;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.MapReduceBase;
import org.apache.hadoop.mapred.Mapper;
import org.apache.hadoop.mapred.OutputCollector;
import org.apache.hadoop.mapred.Reporter;
import org.apache.log4j.Logger;
import org.apache.tika.Tika;
import org.apache.tika.io.CloseShieldInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.EmptyParser;
import org.apache.tika.parser.Parser;
import org.archive.io.ArchiveRecord;
import org.archive.io.ArchiveRecordHeader;
import org.archive.io.arc.ARCRecord;
import org.opf_labs.LibmagicJnaWrapper;

import uk.bl.dpt.qa.ProcessIsolatedTika;
import uk.bl.wa.hadoop.WritableArchiveRecord;
import uk.bl.wa.nanite.droid.DroidDetector;
import uk.gov.nationalarchives.droid.command.action.CommandExecutionException;

/**
 * 
 * @author Andrew Jackson <Andrew.Jackson@bl.uk>
 * @author William Palmer <William.Palmer@bl.uk>
 * 
 */
public class FormatProfilerMapper extends MapReduceBase implements Mapper<Text, WritableArchiveRecord, Text, Text> {

	private static Logger log = Logger.getLogger(FormatProfilerMapper.class.getName());

	//////////////////////////////////////////////////
	// Global constants
	//////////////////////////////////////////////////	

	final private static boolean droidUseBinarySignaturesOnly = false;
	
	// Maximum buffer size
	final private static int BUF_SIZE = 20*1024*1024;
	
	//////////////////////////////////////////////////
	// Global properties
	//////////////////////////////////////////////////	
	
	// NOTE: these settings may be overridden if FormatProfiler.properties exists as a resource
	
	private Properties props = null;
	// Whether or not to include the extension in the output
	private boolean INCLUDE_EXTENSION = true;
	// Should we use Droid?
	private boolean USE_DROID = true;
	// Should we use Tika (parser)?
	private boolean USE_TIKAPARSER = true;
	// Should we use Tika (detect)?
	private boolean USE_TIKADETECT = false;
	// Should we use libmagic?
	private boolean USE_LIBMAGIC = false;
	// Whether to ignore the year of harvest (if so, will set a default year)
	private boolean INCLUDE_WAYBACKYEAR = false;
	
	//////////////////////////////////////////////////
	// Global variables
	//////////////////////////////////////////////////	

	private DroidDetector droidDetector = null;
    @SuppressWarnings("unused")
	private Parser tikaParser = null;
	private ProcessIsolatedTika isolatedTikaParser = null;
    private Writer tikaParserSeqFile = null;
    private LibmagicJnaWrapper libMagicWrapper = null;
	private Tika tikaDetect = null;
//	private boolean gTikaAlreadyInitialised = false;
	
	private JobConf gConf = null;

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
    	
    	final String propertiesFile = "FormatProfiler.properties";
    	
    	// load properties
    	InputStream p = FormatProfilerMapper.class.getResourceAsStream(propertiesFile);
    	if(p!=null) {
    		props = new Properties();
    		try {
    			props.load(p);
    		} catch (IOException e) {
    			// TODO Auto-generated catch block
    			e.printStackTrace();
    		}

    		log.info("Loaded properties from "+propertiesFile);

    		if(props.contains("INCLUDE_EXTENSION")) {
    			INCLUDE_EXTENSION = new Boolean(props.getProperty("INCLUDE_EXTENSION"));
    		}

    		if(props.contains("USE_DROID")) {
    			USE_DROID = new Boolean(props.getProperty("USE_DROID"));
    		}

    		if(props.contains("USE_TIKAPARSER")) {
    			USE_TIKAPARSER = new Boolean(props.getProperty("USE_TIKAPARSER"));
    		}

    		if(props.contains("USE_TIKADETECT")) {
    			USE_TIKADETECT = new Boolean(props.getProperty("USE_TIKADETECT"));
    		}

    		if(props.contains("USE_LIBMAGIC")) {
    			USE_LIBMAGIC = new Boolean(props.getProperty("USE_LIBMAGIC"));
    		}

    		if(props.contains("INCLUDE_WAYBACKYEAR")) {
    			INCLUDE_WAYBACKYEAR = new Boolean(props.getProperty("INCLUDE_WAYBACKYEAR"));
    		}

    	}
    	
		log.info("INCLUDE_EXTENSION: "+INCLUDE_EXTENSION);
		log.info("USE_DROID: "+USE_DROID);
		log.info("USE_TIKAPARSER: "+USE_TIKAPARSER);
		log.info("USE_TIKADETECT: "+USE_TIKADETECT);
		log.info("USE_LIBMAGIC: "+USE_LIBMAGIC);
		log.info("INCLUDE_WAYBACKYEAR: "+INCLUDE_WAYBACKYEAR);

    }

    /**
     * Initialise the Tika Parser
     */
    @SuppressWarnings("unused")
	private void initTikaParser() {
		AutoDetectParser parser = null;
   		parser = new AutoDetectParser();
    	
    	// NOTE: Tika 1.4 & 1.5-SNAPSHOT parsers (and their dependencies) have problems with certain files
		Map<MediaType, Parser> parsers = parser.getParsers();
		
	    // Hangs
	    //log.info("Disabling parsing of audio/mpeg files");
		//parsers.put(MediaType.audio("mpeg"), new EmptyParser());
		
	    // java.lang.OutOfMemoryError: Java heap space @ com.sun.imageio.plugins.png.PNGImageReader (JDK6)
	    log.info("Disabling parsing of image/png files");
		parsers.put(MediaType.image("png"), new EmptyParser());
		
	    // java.lang.OutOfMemoryError: Java heap space @ com.coremedia.iso.ChannelHelper.readFully (JDK6)
	    log.info("Disabling parsing of video/mp4 files");
		parsers.put(MediaType.video("mp4"), new EmptyParser());
		
		parser.setParsers(parsers);
		
		// wrap the parser in a TimeoutParser and use the default timeout value
		tikaParser = new TimeoutParser(parser);
		
    }
    
	/**
	 * Initialise a sequence file that will contain the Tika outputs
	 * @param pWarc
	 */
	private void initSequenceFile(Text pWarc) {

	    try {
		// Set the output sequence file's name
		Path seqFile = new Path(gConf.get("mapred.output.dir")+"/"+pWarc+".tika.seqfile");
	    tikaParserSeqFile = SequenceFile.createWriter(gConf, Writer.compression(CompressionType.BLOCK),
	    												   Writer.file(seqFile),
	    												   Writer.keyClass(Text.class),
	    												   Writer.valueClass(Text.class));
	    } catch(IOException e) {
	    	log.error("Can't create output sequence file");
	    }
	    
	}
	
	/**
	 * Convenience method for getting the file extension from a URI
	 * @param s path
	 * @return file extension
	 */
	@SuppressWarnings("unused")
	private String getFileExt(String s) {
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
	 * Copied from WARCIndexer.java - https://github.com/ukwa/warc-discovery/blob/master/warc-indexer/src/main/java/uk/bl/wa/indexer/WARCIndexer.java#L657
	 * @param fullUrl
	 * @return extension
	 */
    private static String parseExtension( String fullUrl ) {
        if( fullUrl.lastIndexOf( "/" ) != -1 ) {
                String path = fullUrl.substring( fullUrl.lastIndexOf( "/" ) );
                if( path.indexOf( "?" ) != -1 ) {
                        path = path.substring( 0, path.indexOf( "?" ) );
                }
                if( path.indexOf( "&" ) != -1 ) {
                        path = path.substring( 0, path.indexOf( "&" ) );
                }
                if( path.indexOf( "." ) != -1 ) {
                        String ext = path.substring( path.lastIndexOf( "." ) );
                        ext = ext.toLowerCase();
                        // Avoid odd/malformed extensions:
                        // if( ext.contains("%") )
                        // ext = ext.substring(0, path.indexOf("%"));
                        ext = ext.replaceAll( "[^0-9a-z]", "" );
                        
                        // try and sanitize some extensions
                        String e = "";
                        e = "html"; if(ext.startsWith(e)) { ext = e; }
                        e = "jpg"; if(ext.startsWith(e)) { ext = e; }
                        e = "jpeg"; if(ext.startsWith(e)) { ext = e; }
                        e = "png"; if(ext.startsWith(e)) { ext = e; }
                        
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

	//////////////////////////////////////////////////
	// Mapper methods
	//////////////////////////////////////////////////	

	@Override
	public void configure( JobConf job ) {

		loadConfig();
		
		// Set up Droid
		if(USE_DROID) {
			try {
				droidDetector = new DroidDetector();
				droidDetector.setBinarySignaturesOnly( droidUseBinarySignaturesOnly );
			} catch (CommandExecutionException e) {
				log.error("droidDetector CommandExecutionException "+ e);
			}
		}
		
		// Set up Tika (detect)
		if(USE_TIKADETECT) {
			tikaDetect = new Tika();
		}

		// Set up Tika (parser)
		if(USE_TIKAPARSER) {
			
		    // store conf so it can be used to create a sequence file on HDFS
		    gConf = job;
			
			isolatedTikaParser = new ProcessIsolatedTika();
			
		    // Do this in a method so we can call it again if we need to re-initialise
		    //initTikaParser();
		}

		// Set up libMagic
		if(USE_LIBMAGIC) {
			// Set up libMagicWrapper
			libMagicWrapper = new LibmagicJnaWrapper();
			// Load default magic file
			libMagicWrapper.loadCompiledMagic();
		}
		
	}

	/* (non-Javadoc)
	 * @see org.apache.hadoop.mapred.MapReduceBase#close()
	 */
	@Override
	public void close() throws IOException {
		// TODO Auto-generated method stub
		super.close();
		// tidy up
		if(USE_TIKAPARSER) {
			if(null!=tikaParserSeqFile) {
				tikaParserSeqFile.close();
			}
			if(null!=isolatedTikaParser) {
				isolatedTikaParser.stop();
			}
		}
	}

	@Override
	public void map( Text key, WritableArchiveRecord value, OutputCollector<Text, Text> output, Reporter reporter ) throws IOException {

		// log the file we are processing:
		//log.info("Processing record from: "+key);
		//log.info("                   URL: "+value.getRecord().getHeader().getUrl());
		
		if(USE_TIKAPARSER) {
			if(null==tikaParserSeqFile) {
				initSequenceFile(key);
			}
		}

		// Year and type from record:
		String waybackYear = "";
		if(INCLUDE_WAYBACKYEAR) {
			waybackYear = getWaybackYear(value);
		} else {
			waybackYear = "na";
		}
		String serverType = getServerType(value);
		log.debug("Server Type: "+serverType);

		// Get filename and separate the extension of the file
		// Use URLEncoder as some URLs cause URISyntaxException in DroidDetector
		String extURL = value.getRecord().getHeader().getUrl();
		
		InputStream datastream = null;
		try {
			
			String mapOutput = "\"" + serverType + "\"";
			
			if (INCLUDE_EXTENSION) {
				// Make sure we have something to turn in to a URL!
//				if (extURL != null && extURL.length() > 0) {
//					extURL = URLEncoder.encode(extURL, "UTF-8");
//				} else {
//					extURL = "";
//				}

				// Remove directories
//				String file = extURL;//value.getRecord().getHeader().getUrl();
//				if (file != null) {
//					final int lastIndexSlash = file.lastIndexOf('/');
//					if (lastIndexSlash > 0 & (lastIndexSlash < file.length())) {
//						file = file.substring(lastIndexSlash + 1);
//					}
//				} else {
//					file = "";
//				}


				// Get file extension
//				String fileExt = "";
//				if (file.contains(".")) {
//					fileExt = parseExtension(file);
//				}
				
				String fileExt = "";
				if (extURL != null && extURL.length() > 0) {
					// Don't normalise the URL using URLEncoder for this method
					fileExt = parseExtension(extURL);
				} 
				mapOutput = "\"" + fileExt + "\"\t" + mapOutput;
			}
			
			// Sanitize the URL for use by the detectors
			if (extURL != null && extURL.length() > 0) {
				extURL = URLEncoder.encode(extURL, "UTF-8");
			} 
			
//			log.debug("file: "+file+", ext: "+fileExt);
			
			// Need to consume the headers.
			ArchiveRecord record = value.getRecord();
			if (record instanceof ARCRecord) {
				ARCRecord arc = (ARCRecord) record;
				arc.skipHttpHeader();
			}

			// Initialise a buffered input stream
			// - don't pass BUF_SIZE as a parameter here, testing indicates it dramatically slows down the processing
			datastream = new BufferedInputStream(new CloseShieldInputStream(value.getPayloadAsStream()));
			// Mark the datastream so we can re-use it
			// NOTE: this code will fail if >BUF_SIZE bytes are read
			datastream.mark(BUF_SIZE);

			if (USE_DROID) {
				// Type according to DroidDetector
				Metadata metadata = new Metadata();
				metadata.set(Metadata.RESOURCE_NAME_KEY, extURL);
				
				log.trace("Using DroidDetector...");
				droidDetector.setMaxBytesToScan(BUF_SIZE);
				final MediaType droidType = droidDetector.detect(datastream, metadata);

				mapOutput += "\t\"" + droidType + "\"";
				
				// Reset the datastream for reuse
				datastream.reset();

			}
            
            if (USE_TIKAPARSER) {
            	
            	// Type according to Tika parser
            	Metadata metadata = new Metadata();
            	metadata.set(Metadata.RESOURCE_NAME_KEY, extURL);
            	
            	log.trace("Using Tika parser...");

            	// A do-absolutely-nothing ContentHandler
    			//ContentHandler nullHandler = new NullContentHandler();
    			
    			//tikaParser.parse(datastream, nullHandler, metadata, new ParseContext());
    			final boolean success = isolatedTikaParser.parse(datastream, metadata);

    			String parserTikaType = metadata.get(Metadata.CONTENT_TYPE);

    			log.debug("ProcessIsolatedTikaType: "+parserTikaType+", success: "+success);
    			
    			if(!success) {
    				parserTikaType = "tikaParserTimeout";
    			}
    			
    			if(metadata.get(TimeoutParser.TIMEOUTKEY)!=null) {
    				// indicate the parser timed out in the reduce output
    				parserTikaType = "tikaParserTimeout";
    				
    				// Re-initialise the parser due to a forced Thread stop, just in case
    				// NOTE: even reinitialising might still leave problems
    				
    				// FIXME: classes loaded from different classloaders cannot be mixed
    				
//    				tikaParser = null;
//    				initTikaParser();
    			}

    			// Store parser output in sequence file
    			// FIXME: better formatting? Make this compatible with c3po
    			String md = "";
    			for(String k:metadata.names()) {
    				md+=k+": "+metadata.get(k)+"\n";
    			}
    			tikaParserSeqFile.append(new Text(extURL), new Text(md));

    			mapOutput += "\t\"" + parserTikaType + "\"";
    			
           		// Reset the datastream for reuse
           		datastream.reset();
           		
            }
			
            if (USE_TIKADETECT) {
            	// Type according to Tika detect
            	Metadata metadata = new Metadata();
            	metadata.set(Metadata.RESOURCE_NAME_KEY, extURL);
            	
            	log.trace("Using Tika detect...");
            	final String tdaTikaType = tikaDetect.detect(datastream, metadata);

            	mapOutput += "\t\"" + tdaTikaType + "\"";
            	
           		// Reset the datastream for reuse
           		datastream.reset();

            }
			
            if (USE_LIBMAGIC) {

            	// Use libmagic-jna-wrapper to identify the file
            	// You need to manually install this to your local maven repo - see pom for download url
            	// Also - libmagic.so needs to be installed on your cluster, for Ubuntu this is
            	// contained in the libmagic-dev package
            	// 
            	// Note: libmagic does not currently consume a metadata object
            	log.trace("Using libMagicWrapper...");

            	// We don't have fileSize
            	final String libMagicType = libMagicWrapper.getMimeType(datastream);

            	mapOutput += "\t\"" + libMagicType + "\"";
            	
    			// Reset the datastream for reuse
               	datastream.reset();

            }
            
            // Try and lose the buffered data
			// datastream = null;
			
			// Return the output for collation
			output.collect(new Text(mapOutput), new Text(waybackYear));
			log.info("OUTPUT "+mapOutput+" "+waybackYear);
			
		} catch (IOException e) {
			log.error("Failed to identify due to IOException:" + e);
			try {
				// Output a result so we can see how many records fail to process
				output.collect(new Text("IOException\t\""+key+"\""), new Text(waybackYear));
			} catch (IOException e1) {
				e1.printStackTrace();
			}
		} catch (NumberFormatException e) {
			// This happens when the (W)ARC is potentially malformed.
			log.error("Potentially malformed (W)ARC file, skipping URL: ("+value.getRecord().getHeader().getUrl()+")");
			try {
				// Output a result so we can see how many records fail to process
				output.collect(new Text("\"Malformed Record\"\t\""+key+"\""), new Text(waybackYear));
			} catch (IOException e1) {
				e1.printStackTrace();
			}
		} catch (Exception e) {
			// If we reach this point there has been some serious error we did not anticipate
			log.error("Exception: "+e.getMessage()+" for record ("+value.getRecord().getHeader().getUrl()+")");
			e.printStackTrace();
			try {
				// Output a result so we can see some basic details
				output.collect(new Text("Exception\t\""+key+"\""), new Text(waybackYear));
			} catch (IOException e1) {
				e1.printStackTrace();
			}			
		} finally {
			if (datastream != null) {
				// Closing the datastream causes a NumberFormatException in 
				// ArchiveRecord/ARCRecordMetaData, so don't directly close the input stream.
				// The source InputStream is now wrapped in a CloseShieldInputStream, will see 
				// if it makes a difference
				datastream.close();
				datastream = null;
			}
		}
	}
	
}
