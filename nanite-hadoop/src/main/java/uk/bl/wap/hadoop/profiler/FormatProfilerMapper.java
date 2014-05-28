package uk.bl.wap.hadoop.profiler;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.httpclient.Header;
import org.apache.hadoop.fs.FileSystem;
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
	
	final static String propertiesFile = 			"FormatProfiler.properties";
	final static String INCLUDE_EXTENSION = 		"INCLUDE_EXTENSION";
	final static String INCLUDE_SERVERTYPE = 		"INCLUDE_SERVERTYPE";
	final static String USE_DROID = 				"USE_DROID";
	final static String USE_TIKADETECT = 			"USE_TIKADETECT";
	final static String USE_TIKAPARSER = 			"USE_TIKAPARSER";
	final static String USE_LIBMAGIC = 				"USE_LIBMAGIC";
	final static String INCLUDE_WAYBACKYEAR = 		"INCLUDE_WAYBACKYEAR";
	final static String GENERATE_SEQUENCEFILE = 	"GENERATE_SEQUENCEFILE";
	final static String GENERATE_METADATA_ZIP = 	"GENERATE_METADATA_ZIP";
	final static String GENERATE_C3PO_ZIP = 		"GENERATE_C3PO_ZIP";
	final static String INCLUDE_ARC_HEADERS = 		"INCLUDE_ARC_HEADERS";
	final static String DUMP_FILES_IN_HDFS =		"DUMP_FILES_IN_HDFS";
	final static String DUMP_TIKA_PARSER_TIMEOUT_FILES_IN_C3PO_ZIP = "DUMP_TIKA_PARSER_TIMEOUT_FILES_IN_C3PO_ZIP";
	
	// NOTE: these default settings may be overridden if FormatProfiler.properties exists as a resource
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
		// Should we use Tika (parser)?
		put(USE_TIKAPARSER, Boolean.TRUE);
		// Should we use Tika (detect)?
		put(USE_TIKADETECT, Boolean.TRUE);
		// Should we use libmagic?
		put(USE_LIBMAGIC, Boolean.FALSE);
		// Whether to ignore the year of harvest (if so, will set a default year)
		put(INCLUDE_WAYBACKYEAR, Boolean.FALSE);
		// Whether to generate a c3po compatible zip per input arc (Tika parser required)
		put(GENERATE_C3PO_ZIP, Boolean.TRUE);
		// Whether to generate a zip containing serialized metadata objects; one per input arc (Tika parser required)
		put(GENERATE_METADATA_ZIP, Boolean.TRUE);
		// Whether or not to generate a sequencefile per input arc containing serialized Tika parser Metadata objects
		put(GENERATE_SEQUENCEFILE, Boolean.TRUE);
	    // whether to include the ARC header information in the output
	    put(INCLUDE_ARC_HEADERS, Boolean.TRUE);
		// dump the payload contents into a zip file in hdfs
	    put(DUMP_FILES_IN_HDFS, Boolean.FALSE);
	    // dump any files that cause a parser timeout into the c3po zip file
	    put(DUMP_TIKA_PARSER_TIMEOUT_FILES_IN_C3PO_ZIP, Boolean.FALSE);
	}};
    
	//////////////////////////////////////////////////
	// Global variables
	//////////////////////////////////////////////////	

	private DroidDetector droidDetector = null;
    @SuppressWarnings("unused")
	private Parser tikaParser = null;
	private ProcessIsolatedTika isolatedTikaParser = null;
    private LibmagicJnaWrapper libMagicWrapper = null;
	private Tika tikaDetect = null;
	
	private JobConf gConf = null;

    private Writer tikaParserSeqFile = null;
    private ZipOutputStream tikaParserMetadataZip = null;
    private ZipOutputStream tikaC3poZip = null;
    private ZipOutputStream zipOutputFiles = null;
    private int zipEntryCount = 0;
    
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
    	InputStream p = FormatProfilerMapper.class.getClassLoader().getResourceAsStream(propertiesFile);
    	if(p!=null) {
    		Properties props = new Properties();
    		try {
    			props.load(p);
    		} catch (IOException e) {
    			// TODO Auto-generated catch block
    			e.printStackTrace();
    		}

    		log.info("Loaded properties from "+propertiesFile);

    		// Iterate on the options and load the default from the class properties on errors
    		for(Object key:props.keySet()) {
    			if(key instanceof String) {
    				if(gProps.containsKey(key)) {
    					String k = (String)key;
    					gProps.put(k, Boolean.valueOf(props.getProperty(k, gProps.get(k).toString())));
    				}
    			}
    		}
    		
    		if(gProps.containsKey(USE_TIKAPARSER)) {
    			// We need the parser
    			if(new Boolean(gProps.get(USE_TIKAPARSER)).booleanValue()) {
    				gProps.put(USE_TIKADETECT, Boolean.TRUE);
    			}
    		}

    	}
    	
		for(String key:gProps.keySet()) {
			log.info(key+": "+gProps.get(key));
		}

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
	private void initOutputFiles(Text pWarc) {

	    try {
	    	
	    	String filePrefix = gConf.get("mapred.output.dir")+"/"+pWarc; 

	    	if(gProps.get(GENERATE_SEQUENCEFILE)) {
	    		// Set the output sequence file's name
	    		Path seqFile = new Path(filePrefix+".tika.seqfile");
	    		tikaParserSeqFile = SequenceFile.createWriter(gConf, Writer.compression(CompressionType.BLOCK),
	    				Writer.file(seqFile),
	    				Writer.keyClass(Text.class),
	    				Writer.valueClass(Text.class));
	    	}

    		FileSystem fs = null;
    		
    		if(gProps.get(GENERATE_METADATA_ZIP)||
    		   gProps.get(GENERATE_C3PO_ZIP)||
    		   gProps.get(DUMP_FILES_IN_HDFS)) {
    			fs = FileSystem.get(gConf);
    		}

	    	if(gProps.get(GENERATE_METADATA_ZIP)) {
	    		// Zip file output
	    		Path zip = new Path(filePrefix+".tika-obj.zip");
	    		tikaParserMetadataZip = new ZipOutputStream(fs.create(zip));	    		
	    	}
	    	
	    	if(gProps.get(GENERATE_C3PO_ZIP)) {
	    		// Zip file output
	    		Path zip = new Path(filePrefix+".tika.zip");
	    		tikaC3poZip = new ZipOutputStream(fs.create(zip));
	    	}
	    	
	    	if(gProps.get(DUMP_FILES_IN_HDFS)) {
	    		// Zip file output
	    		Path zip = new Path(filePrefix+".dump.zip");
	    		zipOutputFiles = new ZipOutputStream(fs.create(zip));
	    	}
	    	
	    } catch(IOException e) {
	    	log.error("Can't create output file");
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
	

	/**
	 * Add metadata to the zip file, in a format c3po can use
	 * @param metadata
	 */
	private void addMetadataToZip(ZipOutputStream zipFile, Metadata metadata, int zipEntryCount) {
		
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		PrintWriter pw = new PrintWriter(baos);
		
		// This output format mimics "java -jar tika-app.jar -m file" as that is what c3po expects
		String[] names = metadata.names();
		for(String name : names) {
			for(String value : metadata.getValues(name)) {
				pw.println(name+": "+value.replaceAll("[\n\r]"," "));
			}
		}
		
		pw.close();
		
		byte[] data = baos.toByteArray();
		
		addFileToZip(zipFile, data, data.length, zipEntryCount, "txt");
		
	}
	
	/**
	 * Add metadata to the zip file, in a format c3po can use
	 * @param metadata
	 */
	private void addFileToZip(ZipOutputStream zipFile, byte[] data, int len, int zipEntryCount, String ext) {
		
		ZipEntry entry = new ZipEntry(String.format("%08d", zipEntryCount)+"."+ext);
		
		try {
			zipFile.putNextEntry(entry);
			zipFile.write(data, 0, len);
			zipFile.closeEntry();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	
	private String serialize(Object obj) {
		try {
			// Store serialized metadata object in the sequence file
			// c3po can use this object and reconstruct an xml file
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			ObjectOutputStream oos = new ObjectOutputStream(baos);
			oos.writeObject(obj);
			oos.close();
			// encode object in base64
			return new String(Base64.encodeBase64(baos.toByteArray()));
		} catch(IOException e) {
			//
		}
		return null;
	}
	
	/**
	 * This is here just for completeness, in case anyone wishes to deserialise a metadata object
	 */
	@SuppressWarnings("unused")
	private Object deserialize(byte[] data) {
		Object o = null;
		try {
			ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(Base64.decodeBase64(data)));
			try {
				o = ois.readObject();
			} catch (ClassNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			ois.close();
		} catch(IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
//		Metadata metadata = null;
//		if(o instanceof Metadata) {
//			metadata = (Metadata)o;
//		}
		return o;
	}

	//////////////////////////////////////////////////
	// Mapper methods
	//////////////////////////////////////////////////	

	@Override
	public void configure( JobConf job ) {

		loadConfig();
		
		// Set up Droid
		if(gProps.get(USE_DROID)) {
			try {
				droidDetector = new DroidDetector();
				droidDetector.setBinarySignaturesOnly( droidUseBinarySignaturesOnly );
			} catch (CommandExecutionException e) {
				log.error("droidDetector CommandExecutionException "+ e);
			}
		}
		
		// Set up Tika (detect)
		if(gProps.get(USE_TIKADETECT)) {
			tikaDetect = new Tika();
		}

		// Set up Tika (parser)
		if(gProps.get(USE_TIKAPARSER)) {
			
		    // store conf so it can be used to create a sequence file on HDFS
		    gConf = job;
			
			isolatedTikaParser = new ProcessIsolatedTika();
			
		    // Do this in a method so we can call it again if we need to re-initialise
		    //initTikaParser();
		}

		// Set up libMagic
		if(gProps.get(USE_LIBMAGIC)) {
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
		if(gProps.get(USE_TIKAPARSER)) {
			if(null!=isolatedTikaParser) {
				isolatedTikaParser.stop();
			}
		}
		if(gProps.get(GENERATE_SEQUENCEFILE)) {
			if(null!=tikaParserSeqFile) {
				tikaParserSeqFile.close();
			}
		}
		if(gProps.get(GENERATE_C3PO_ZIP)) {	
			if(null!=tikaC3poZip) {
				tikaC3poZip.finish();
				tikaC3poZip.close();
			}
		}
		if(gProps.get(GENERATE_METADATA_ZIP)) {	
			if(null!=tikaParserMetadataZip) {
				tikaParserMetadataZip.finish();
				tikaParserMetadataZip.close();
			}
		}
		if(gProps.get(DUMP_FILES_IN_HDFS)) {	
			if(null!=zipOutputFiles) {
				zipOutputFiles.finish();
				zipOutputFiles.close();
			}
		}
	}

	@Override
	public void map( Text key, WritableArchiveRecord value, OutputCollector<Text, Text> output, Reporter reporter ) throws IOException {

		// log the file we are processing:
		//log.info("Processing record from: "+key);
		//log.info("                   URL: "+value.getRecord().getHeader().getUrl());
		
		// These is here instead of configure() as we want to use "key"
		if((gProps.get(GENERATE_SEQUENCEFILE)&(null==tikaParserSeqFile))||
		   (gProps.get(GENERATE_C3PO_ZIP)    &(null==tikaC3poZip))||
		   (gProps.get(DUMP_FILES_IN_HDFS)   &(null==zipOutputFiles))||
		   (gProps.get(GENERATE_METADATA_ZIP)&(null==tikaParserMetadataZip))) {
				initOutputFiles(key);
		}

		// Year and type from record:
		String waybackYear = "";
		if(gProps.get(INCLUDE_WAYBACKYEAR)) {
			waybackYear = getWaybackYear(value);
		} else {
			waybackYear = "na";
		}
		final String serverType = getServerType(value);
		log.debug("Server Type: "+serverType);

		// Get filename and separate the extension of the file
		// Use URLEncoder as some URLs cause URISyntaxException in DroidDetector
		String extURL = value.getRecord().getHeader().getUrl();

		InputStream datastream = null;
		try {
			
			String mapOutput = "";

			if (gProps.get(INCLUDE_EXTENSION)) {
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
				mapOutput = "\"" + fileExt + "\"";
			}
			
			
			if (gProps.get(INCLUDE_SERVERTYPE)) {
				mapOutput += "\t\"" + serverType + "\"";
			}
			
			// Sanitize the URL for use by the detectors
			if (extURL != null && extURL.length() > 0) {
				extURL = URLEncoder.encode(extURL, "UTF-8");
			} 
			
//			log.debug("file: "+file+", ext: "+fileExt);
			
			// Need to consume the headers.
			ArchiveRecord record = value.getRecord();
            Map<String, String> arcHttpHeaders = new HashMap<String,String>();
			if (record instanceof ARCRecord) {
				ARCRecord arc = (ARCRecord) record;
                if (gProps.get(INCLUDE_ARC_HEADERS)) {
                    for (Header h: arc.getHttpHeaders()) {
                        arcHttpHeaders.put(h.getName(),h.getValue());
                    }
                }
				arc.skipHttpHeader(); // TODO: Is this still necessary after the above loop?
			} 
			
			// record.getHeader().getLength() contains the length of the headers, too so
			// use available() instead (although this is also not the length of the payload)
			//final long dataLength = record.available();
			//final long maxBytesToRead = dataLength<BUF_SIZE?dataLength:BUF_SIZE;
			
			// Initialise a buffered input stream - the size parameter must be here, otherwise mark() fails on 
			// streams longer than 64kb (may be JVM specific)
			// NOTE: we don't use value.getPayloadAsStream() as data may already be buffered in the record
			//datastream = new BoundedInputStream(new BufferedInputStream(new CloseShieldInputStream(record), (int)maxBytesToRead+1), maxBytesToRead);
			datastream = new BufferedInputStream(new CloseShieldInputStream(record), BUF_SIZE);//(int)maxBytesToRead+1);
			
			// Mark the datastream so we can re-use it
			// NOTE: this code will fail if >BUF_SIZE bytes are read
			datastream.mark((int)BUF_SIZE);

			if(gProps.get(DUMP_FILES_IN_HDFS)) {

				byte[] buf = new byte[(int)BUF_SIZE];
				int len = datastream.read(buf);
				addFileToZip(tikaParserMetadataZip, buf, len, zipEntryCount, "bin");
				
				datastream.reset();
				
			}
			
			if (gProps.get(USE_DROID)) {
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
			
			String tdaTikaType = "";
            if (gProps.get(USE_TIKADETECT)) {
            	// Type according to Tika detect
            	Metadata metadata = new Metadata();
            	metadata.set(Metadata.RESOURCE_NAME_KEY, extURL);
            	
            	log.trace("Using Tika detect...");
            	tdaTikaType = tikaDetect.detect(datastream, metadata);

            	mapOutput += "\t\"" + tdaTikaType + "\"";
            	
           		// Reset the datastream for reuse
           		datastream.reset();

            }
			
            if (gProps.get(USE_TIKAPARSER)) {
            	
            	// Type according to Tika parser
            	Metadata metadata = new Metadata();
            	metadata.set(Metadata.RESOURCE_NAME_KEY, extURL);
            	// Set the mimetype to the one from Tika detect() so we can inform the Tika parser() 
            	// what data to expect - we do this as we cannot pass a metadata object or the filename 
            	// to the isolated Tika parser
            	metadata.set(Metadata.CONTENT_TYPE, tdaTikaType);
            	
            	log.trace("Using Tika parser...");
            	
    			final boolean success = isolatedTikaParser.parse(datastream, metadata);

    			String parserTikaType = metadata.get(Metadata.CONTENT_TYPE);

    			log.debug("ProcessIsolatedTikaType: "+parserTikaType+", success: "+success);
    			
    			if(!success) {
    				parserTikaType = "tikaParserTimeout";
    			}

    			if(gProps.get(INCLUDE_ARC_HEADERS)) {
    				for (Map.Entry<String, String> t: arcHttpHeaders.entrySet()) {
    					metadata.set("ARC-"+t.getKey(),t.getValue());
    				}
    			}

    			if(metadata.get(TimeoutParser.TIMEOUTKEY)!=null) {
    				// indicate the parser timed out in the reduce output
    				parserTikaType = "tikaParserTimeout";
    				if(gProps.get(DUMP_TIKA_PARSER_TIMEOUT_FILES_IN_C3PO_ZIP)&gProps.get(GENERATE_C3PO_ZIP)) {
    					byte[] buf = new byte[(int)BUF_SIZE];
    					int len = datastream.read(buf);
    					// FIXME: different zip file??
    					addFileToZip(tikaC3poZip, buf, len, zipEntryCount, "err");
    				}
    			}

    			String mdString = null;
    			
    			if(gProps.get(GENERATE_SEQUENCEFILE)) {
    				mdString = serialize(metadata);
    				tikaParserSeqFile.append(new Text(extURL), new Text(mdString));
    			}
    			
    			if(gProps.get(GENERATE_METADATA_ZIP)) {
    				if(null==mdString) {
    					mdString = serialize(metadata);
    				}
    				addFileToZip(tikaParserMetadataZip, mdString.getBytes(), mdString.getBytes().length, zipEntryCount, "txt");
    			}

    			if(gProps.get(GENERATE_C3PO_ZIP)) {
    				// Store in the zip in c3po format
    				addMetadataToZip(tikaC3poZip, metadata, zipEntryCount);
    			}
    			
    			zipEntryCount++;
    			
    			mapOutput += "\t\"" + parserTikaType + "\"";
    			
           		// Reset the datastream for reuse
           		datastream.reset();
           		
            }

            if (gProps.get(USE_LIBMAGIC)) {

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
			log.trace("OUTPUT " + mapOutput + " " + waybackYear);
			
		} catch (IOException e) {
			log.error("Failed to identify due to IOException:" + e);
			e.printStackTrace();
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
	
	public static void main(String[] args) {
		new FormatProfilerMapper().loadConfig();
	}

}
