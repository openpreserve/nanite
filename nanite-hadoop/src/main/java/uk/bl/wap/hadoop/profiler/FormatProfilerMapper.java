package uk.bl.wap.hadoop.profiler;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;

import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.MapReduceBase;
import org.apache.hadoop.mapred.Mapper;
import org.apache.hadoop.mapred.OutputCollector;
import org.apache.hadoop.mapred.Reporter;
import org.apache.log4j.Logger;
import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.archive.io.ArchiveRecord;
import org.archive.io.ArchiveRecordHeader;
import org.archive.io.arc.ARCRecord;

import uk.bl.wa.nanite.droid.DroidDetector;
import uk.bl.wa.tika.TikaDeepIdentifier;
import uk.bl.wa.hadoop.WritableArchiveRecord;
import uk.bl.wap.hadoop.format.Ohcount;
import uk.gov.nationalarchives.droid.command.action.CommandExecutionException;

/**
 * 
 * @author Andrew Jackson <Andrew.Jackson@bl.uk>
 *
 */
public class FormatProfilerMapper extends MapReduceBase implements Mapper<Text, WritableArchiveRecord, Text, Text> {
	private static Logger log = Logger.getLogger(FormatProfilerMapper.class.getName());
	String workingDirectory = "";
	Tika tda = null;
	DroidDetector droidDetector = null;
	Ohcount oh = null;
	private static int BUF_SIZE = 10*1024*1024;
	//private FileSystem hdfs;

	public FormatProfilerMapper() {
	}

	@Override
	public void configure( JobConf job ) {
		log.info("Instanciating FPMapper...");
		// Set up Tika:
		//tda = new TikaDeepIdentifier();
		tda = new Tika();
		
		try {
			droidDetector = new DroidDetector();
		} catch (CommandExecutionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		/*
		try {
		this.hdfs = FileSystem.get( job );
		URI[] uris = DistributedCache.getCacheFiles(job);
		if( uris != null ) {
			for( URI uri : uris ) {
				FSDataInputStream input = hdfs.open( new Path( uri.getPath()) );
			}
		}
	} catch( IOException e ) {
		e.printStackTrace();
	}*/
		
		
		// This returns a hdfs URI:
		//this.workingDirectory = job.get( "mapred.work.output.dir" );
		//this.workingDirectory = job.getWorkingDirectory().toString();
		
	}

	@Override
	public void map( Text key, WritableArchiveRecord value, OutputCollector<Text, Text> output, Reporter reporter ) throws IOException {
		// log the file we are processing:
		log.info("Processing record from: "+key);
		
		final boolean INCLUDE_EXTENSION = true;
		
		// Year and type from record:
		String waybackYear = getWaybackYear(value);
		String serverType = getServerType(value);
		log.debug("Server Type: "+serverType);

		// Get filename and separate the extension of the file
		// Use URLEncoder as some URLs cause URISyntaxException in DroidDetector
		String extURL = value.getRecord().getHeader().getUrl();
		// Make sure we have something to turn in to a URL!
		if(extURL!=null&&extURL.length()>0) {
			extURL = URLEncoder.encode(extURL, "UTF-8");
		} else {
			extURL = "";
		}
		// Remove directories
		String file = value.getRecord().getHeader().getUrl();
		if(file!=null) {
			final int lastIndexSlash = file.lastIndexOf('/');
			if(lastIndexSlash>0&(lastIndexSlash<file.length())) {
				file = file.substring(lastIndexSlash + 1);
			}
		} else {
			file = "";
		}
		String fileExt = "";
		// If we have a dot then get the extension
		if(file.contains(".")) {
			if(file.lastIndexOf('.')+1<file.length()) {
				fileExt = file.substring(file.lastIndexOf('.')+1);
			}
		}

		// Type according to Droid/Nanite:
		Metadata metadata = new Metadata();  
		metadata.set(Metadata.RESOURCE_NAME_KEY, extURL);
		
		// Need to consume the headers.
		ArchiveRecord record = value.getRecord();
		if( record instanceof ARCRecord ) {
			ARCRecord arc = (ARCRecord) record;
			arc.skipHttpHeader();
		}

		// We need to mark the datastream so we can re-use it three times
		InputStream datastream = new BufferedInputStream(value.getPayloadAsStream(), BUF_SIZE); 

      // Big try-catch to deal with buffer-size error exceptions:
	  try {
		
		// NOTE: reusing the InputStream in this way will fail on files that are larger
		// than BUF_SIZE bytes
		datastream.mark(BUF_SIZE);

		// Type according to DroidDetector
		MediaType droidType = droidDetector.detect(datastream, metadata);

		// We must reset the InputStream so it can be re-used otherwise we get no data! 
		datastream.reset();

		// NOTE: Tika is last here as the mark() on datastream gets lost resulting in
		// "java.io.IOException: Resetting to invalid mark" on later reset() calls
		
		// Type according to Tika:
		final String tikaType = tda.detect(datastream, metadata);
		
		String mapOutput = "\""+serverType+"\"\t\""+tikaType+"\"\t\""+droidType+"\"";
		
		if(INCLUDE_EXTENSION) {
			mapOutput = "\""+fileExt+"\"\t"+mapOutput;
		}
		
		// try and lose the buffered data
		datastream.close();
		datastream = null;
		
		// Return the output for collation:
		if( output != null ) {
			output.collect( new Text( mapOutput ), new Text( waybackYear ) );
		} else {
			log.info("OUTPUT "+mapOutput+" "+waybackYear);
		}
      } catch( Exception e ) {
			log.error("Failed to identify due to exception:" +e);
      }
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
