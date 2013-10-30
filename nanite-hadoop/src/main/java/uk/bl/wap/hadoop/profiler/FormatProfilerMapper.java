package uk.bl.wap.hadoop.profiler;

/* 
 * For JobConf.get() property see:
 * http://hadoop.apache.org/common/docs/r0.18.3/mapred_tutorial.html
 */

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.MapReduceBase;
import org.apache.hadoop.mapred.Mapper;
import org.apache.hadoop.mapred.OutputCollector;
import org.apache.hadoop.mapred.Reporter;
import org.apache.log4j.Logger;
import org.apache.tika.metadata.Metadata;
import org.archive.io.ArchiveRecordHeader;

import uk.bl.wa.nanite.Nanite;
import uk.bl.wa.nanite.droid.DroidDetector;
import uk.bl.wa.tika.TikaDeepIdentifier;
import uk.bl.wa.hadoop.WritableArchiveRecord;
import uk.bl.wap.hadoop.format.Ohcount;
import uk.gov.nationalarchives.droid.command.action.CommandExecutionException;

@SuppressWarnings( { "deprecation" } )
public class FormatProfilerMapper extends MapReduceBase implements Mapper<Text, WritableArchiveRecord, Text, Text> {
	private static Logger log = Logger.getLogger(FormatProfilerMapper.class.getName());
	String workingDirectory = "";
	TikaDeepIdentifier tda = null;
	Nanite nanite = null;
	DroidDetector droidDetector = null;
	Ohcount oh = null;
	//private FileSystem hdfs;

	public FormatProfilerMapper() {
	}

	@Override
	public void configure( JobConf job ) {
		// Set up Tika:
		tda = new TikaDeepIdentifier();
		// Set up Nanite:
		try {
			nanite = new Nanite();
		} catch ( Exception e) {
			e.printStackTrace();
			log.error("Exception on Nanite instanciation: "+e);
		}
		
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
		this.workingDirectory = job.getWorkingDirectory().toString();
		
	}

	@Override
	public void map( Text key, WritableArchiveRecord value, OutputCollector<Text, Text> output, Reporter reporter ) throws IOException {
		// log the file we are processing:
		log.debug("Processing record from: "+key);
		
		final boolean INCLUDE_EXTENSION = true;
		
		// Get the wctID, if any:
		String wctID = this.getWctTi( key.toString() );
		
		// Year and type from record:
		String waybackYear = getWaybackYear(value);
		String serverType = getServerType(value);
		log.debug("Server Type: "+serverType);

		// Get filename and separate the extension of the file
		final String extURL = value.getRecord().getHeader().getUrl();
		//remove directories
		final String file = extURL.substring(extURL.lastIndexOf('/') + 1);
		String fileExt = "";
		//if we have a dot then get the extension
		if(file.contains(".")) {
			fileExt = file.substring(file.lastIndexOf('.')+1);
		}

		// Type according to Droid/Nanite:
		Metadata metadata = new Metadata();  
		metadata.set(Metadata.RESOURCE_NAME_KEY, file);

		// We need to mark the datastream so we can re-use it three times
		InputStream datastream = new BufferedInputStream(value.getPayloadAsStream()); 
		
		// NOTE: reusing the InputStream in this way will fail on files that are larger
		// than Integer.MAX_VALUE bytes
		datastream.mark(Integer.MAX_VALUE);

		// Type according to Tika:
		final String tikaType = tda.identify(datastream, metadata);

		// We must reset the InputStream so it can be re-used otherwise we get no data! 
		datastream.reset();

		// Type according to Nanite
		final String naniteType = nanite.identify(datastream, metadata).toString();
		
		// We must reset the InputStream so it can be re-used otherwise we get no data! 
		datastream.reset();
		
		// Type according to DroidDetector
		final String droidType = droidDetector.detect(datastream, metadata).toString();

		String mapOutput = "\""+serverType+"\"\t\""+tikaType+"\"\t\""+naniteType+"\"\t\""+droidType+"\"";
		
		if(INCLUDE_EXTENSION) {
			mapOutput = "\""+fileExt+"\"\t"+mapOutput;
		}
		
		// Return the output for collation:
		output.collect( new Text( mapOutput ), new Text( waybackYear ) );
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
				waybackYear = waybackDate.substring(0,4);

		} else {
			log.warn("LOG: Empty header fields!");
		}
		return waybackYear;
	}
	
	private static String getStackTrace(Throwable aThrowable) {
	    final Writer result = new StringWriter();
	    final PrintWriter printWriter = new PrintWriter(result);
	    aThrowable.printStackTrace(printWriter);
	    return result.toString();
	  }

	private String getWctTi( String warcName ) {
		Pattern pattern = Pattern.compile( "^BL-\\b([0-9]+)\\b.*\\.warc(\\.gz)?$" );
		Matcher matcher = pattern.matcher( warcName );
		if( matcher.matches() ) {
			return matcher.group( 1 );
		}
		return "";
	}

}
