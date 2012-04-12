package uk.bl.wap.hadoop.profiler;

/* 
 * For JobConf.get() property see:
 * http://hadoop.apache.org/common/docs/r0.18.3/mapred_tutorial.html
 */

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.io.IOUtils;
import org.apache.hadoop.filecache.DistributedCache;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.MapReduceBase;
import org.apache.hadoop.mapred.Mapper;
import org.apache.hadoop.mapred.OutputCollector;
import org.apache.hadoop.mapred.Reporter;
import org.apache.log4j.Logger;
import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.archive.io.ArchiveRecordHeader;

import eu.scape_project.pc.cc.nanite.Nanite;

import uk.bl.wap.hadoop.WritableArchiveRecord;
import uk.bl.wap.hadoop.format.Ohcount;
import uk.bl.wap.hadoop.util.Unpack;
import uk.gov.nationalarchives.droid.core.interfaces.IdentificationResult;
import uk.gov.nationalarchives.droid.core.interfaces.IdentificationResultCollection;

@SuppressWarnings( { "deprecation" } )
public class FormatProfilerMapper extends MapReduceBase implements Mapper<Text, WritableArchiveRecord, Text, Text> {
	private static Logger log = Logger.getLogger(FormatProfilerMapper.class.getName());
	String workingDirectory = "";
	Tika tika = new Tika();
	Nanite nanite = null;
	Ohcount oh = null;
	File tmpFile = null;
	private FileSystem hdfs;

	public FormatProfilerMapper() {
	}

	@Override
	public void configure( JobConf job ) {
		try {
			nanite = new Nanite();
			tmpFile = File.createTempFile("Nanite", "tmp");
		} catch ( Exception e) {
			e.printStackTrace();
			log.error("Exception on Nanite instanciation: "+e);
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
		
		
		// Instanciate Ohcount using the location of the binary:	
		try {
			//File ohcount = new File( DistributedCache.getLocalCacheFiles(job)[0].toString() );
			File ohcount = Unpack.streamToTemp(FormatProfiler.class, "native/linux_x64/"+Ohcount.OH_300_STATIC_BIN, true);
			oh = new Ohcount( ohcount );
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
		
		// This returns a hdfs URI:
		//this.workingDirectory = job.get( "mapred.work.output.dir" );
		this.workingDirectory = job.getWorkingDirectory().toString();
		
	}

	@Override
	public void map( Text key, WritableArchiveRecord value, OutputCollector<Text, Text> output, Reporter reporter ) throws IOException {
		ArchiveRecordHeader header = value.getRecord().getHeader();
		String serverType = "unknown";
		String tikaType = "unknown";
		String droidType = "unknown";
		String waybackYear = "unknown";

		// Get the ID:
		String wctID = this.getWctTi( key.toString() );
		
		// Get the server header data:
		if( !header.getHeaderFields().isEmpty() ) {
			// The crawl year:
			String waybackDate = ( ( String ) value.getRecord().getHeader().getDate() ).replaceAll( "[^0-9]", "" );
			if( waybackDate != null ) 
				waybackYear = waybackDate.substring(0,4);

			// Type according to server:
			serverType = value.getHttpHeader("Content-Type");
			if( serverType == null ) {
				output.collect( new Text("LOG: Server Content-Type is null."), new Text(wctID));
			}
		} else {
			output.collect( new Text("LOG: Empty header fields. "), new Text(key));
		}


		// Type according to Tiki:
		try {
			tikaType = tika.detect( value.getPayload() );
			// Now perform full parse:
			Metadata md = new Metadata();
			tika.parse( new ByteArrayInputStream( value.getPayload() ), md );
			//for( String name : md.names() ) {
			//}
			String tikaAppId = "";
			if( md.get( Metadata.APPLICATION_NAME ) != null ) tikaAppId += md.get( Metadata.APPLICATION_NAME );
			if( md.get( Metadata.APPLICATION_VERSION ) != null ) tikaAppId += "_"+md.get( Metadata.APPLICATION_VERSION);
			// For PDF, dig in:
			if( "application/pdf".equals(tikaType) ) {
				// PDF has Creator and Producer application properties:
				String creator = md.get("creator");
				String producer = md.get("producer");
				tikaAppId = creator+" + "+producer;
			}
			// Append the appid
			if( ! "".equals(tikaAppId) ) {
				tikaType = tikaType+"; appid=\""+tikaAppId+"\"";
			}

		} catch( Throwable e ) {
			log.error( e.getMessage() );
			e.printStackTrace();
			output.collect( new Text("LOG:ERROR Analysis threw exception: "+e+"\n"+getStackTrace(e)), new Text(key+" "+tmpFile+" "+value));
		}
		
		// Ohcount
		/*
		String ohType = "application/octet-stream";
		if( tikaType.startsWith("text") ) {
			try {
				// This could maybe be made to work, but the real name is often hidden, e.g. javascript downloads as resource.asp?identifier.
				//String name = value.getRecord().getHeader().getUrl();
				//name = name.substring( name.lastIndexOf("/") + 1);
				String name = wctID;
				File contentTmp = this.copyToTempFile(name, value.getPayload());
				ohType = oh.identify(contentTmp);
				contentTmp.delete();
			} catch (Exception e) {
				log.error( e.getMessage() );
				e.printStackTrace();
				output.collect( new Text("LOG:ERROR Analysis threw exception: "+e+"\n"+getStackTrace(e)), new Text(key+" "+tmpFile+" "+value));
			}
		}
		*/

		// Type according to Droid/Nanite:
		droidType = "application/octet-stream";
		try {
			IdentificationResultCollection irc = nanite.identify(
					Nanite.createByteArrayIdentificationRequest(tmpFile.toURI(), value.getPayload()) );
			/*
			IdentificationResultCollection ircf = nanite.identify(
					Nanite.createFileIdentificationRequest(tmpFile) );
			*/
			if( irc.getResults().size() > 0 ) {
				IdentificationResult res = irc.getResults().get(0);
				droidType = Nanite.getMimeTypeFromResult(res);
			}
		} catch( Exception e ) {
			log.error("Exception on Nanite invocation: "+e);
			e.printStackTrace();
			output.collect( new Text("LOG:ERROR Droid threw exception: "+e+"\n"+getStackTrace(e)), new Text(wctID) );
		}

		// Return the output for collation:
		output.collect( new Text( serverType+"\t"+tikaType+"\t"+droidType ), new Text( waybackYear ) );
	}
	
	private File copyToTempFile( String name, byte[] content, int max_bytes ) throws Exception {
		File tmp = File.createTempFile("FmtTmp-", name);
		FileOutputStream fos = new FileOutputStream(tmp);
		IOUtils.copy(new ByteArrayInputStream(content, 0, max_bytes), fos);
		fos.flush();
		fos.close();
		return tmp;
	}
	
	private static int BUF_8KB = 8*1024;

	private File copyToTempFile( String name, byte[] content ) throws Exception {
		//if( content.length < BUF_8KB )
		return copyToTempFile(name, content, BUF_8KB);
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

	/* (non-Javadoc)
	 * @see java.lang.Object#finalize()
	 */
	@Override
	protected void finalize() throws Throwable {
		super.finalize();
		if( tmpFile.exists() ) {
			tmpFile.delete();
		}
	}
	

	/**
	 * A simple test class to check the classpath is set up ok.
	 * 
	 * @param args
	 * @throws Exception 
	 * @throws IOException 
	 * @throws ConfigurationException 
	 */
	public static void  main( String[] args ) throws Exception {
		File file = new File(args[0]);
		Nanite nan = new Nanite();
		System.out.println("Nanite using binary sig. file version "+nan.getBinarySigFileVersion());
		nan.getMimeType(file);	
	}
}
