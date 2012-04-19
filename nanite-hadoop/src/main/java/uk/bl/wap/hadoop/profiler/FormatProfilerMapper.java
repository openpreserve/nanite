package uk.bl.wap.hadoop.profiler;

/* 
 * For JobConf.get() property see:
 * http://hadoop.apache.org/common/docs/r0.18.3/mapred_tutorial.html
 */

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.io.IOUtils;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.MapReduceBase;
import org.apache.hadoop.mapred.Mapper;
import org.apache.hadoop.mapred.OutputCollector;
import org.apache.hadoop.mapred.Reporter;
import org.apache.log4j.Logger;
import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.CompositeParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.WriteOutContentHandler;
import org.archive.io.ArchiveRecordHeader;
import org.xml.sax.ContentHandler;
import eu.scape_project.pc.cc.nanite.Nanite;
import eu.scape_project.pc.cc.nanite.tika.PreservationParser;

import uk.bl.wap.hadoop.WritableArchiveRecord;
import uk.bl.wap.hadoop.format.Ohcount;
import uk.bl.wap.hadoop.util.Unpack;
import uk.gov.nationalarchives.droid.core.interfaces.IdentificationResult;
import uk.gov.nationalarchives.droid.core.interfaces.IdentificationResultCollection;

@SuppressWarnings( { "deprecation" } )
public class FormatProfilerMapper extends MapReduceBase implements Mapper<Text, WritableArchiveRecord, Text, Text> {
	private static Logger log = Logger.getLogger(FormatProfilerMapper.class.getName());
	String workingDirectory = "";
	Tika tika = null;
	Nanite nanite = null;
	Ohcount oh = null;
	File tmpFile = null;
	//private FileSystem hdfs;

	public FormatProfilerMapper() {
	}

	@Override
	public void configure( JobConf job ) {
		// Set up Tika:
		tika = new Tika();
		// Set up Nanite:
		try {
			nanite = new Nanite();
			tmpFile = File.createTempFile("Nanite", "tmp");
			tmpFile.deleteOnExit();
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
		log.debug("Processing wcID: "+wctID);
		
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
		log.debug("Server Type: "+serverType);


		try {
			// Type according to Tiki:
			tikaType = tika.detect( value.getPayload() );
		} catch( Throwable e ) {
			log.error( e.getMessage() );
			System.err.println("Failed: "+e.getMessage());
			e.printStackTrace();
			//output.collect( new Text("LOG:ERROR Tika.detect threw exception: "+e+"\n"+getStackTrace(e)), new Text(key+" "+tmpFile+" "+value));
		}

		// Now perform full parse...
		// Set up metadata object:
		Metadata md = new Metadata();
		// Now perform the parsing:
		try {
			// Abort handler, limiting the output size, to avoid OOM:
			WriteOutContentHandler ch = new WriteOutContentHandler(MAX_BUF);
			// Silent handler:
			//ContentHandler ch = new DefaultHandler();
			// Set up a parse context:
			ParseContext ctx = new ParseContext();
			// Set up the parser:
			CompositeParser parser = new PreservationParser();
			// Parse
			parser.parse( new ByteArrayInputStream( value.getPayload() ), ch, md, ctx );
			// One could forcibly limit the size if OOM is still causing problems, like this:
			//parser.parse( new ByteArrayInputStream( value.getPayload(), 0, BUF_8KB ), ch, md, ctx );
		} catch( Throwable e ) {
			log.debug( "Tika Exception: " + e.getMessage() );
			//e.printStackTrace();
		}

		// Use the extended MIME type generated by the PreservationParser:
		tikaType = md.get(PreservationParser.EXT_MIME_TYPE);
		
		// Type according to Droid/Nanite:
		droidType = "application/octet-stream";
		try {
			IdentificationResultCollection irc = nanite.identify(
					Nanite.createByteArrayIdentificationRequest(tmpFile.toURI(), value.getPayload()) );
			/*
			IdentificationResultCollection ircf = nanite.identify(
					Nanite.createFileIdentificationRequest(tmpFile) );
			*/
			droidType = Nanite.getMimeTypeFromResults(irc.getResults()).toString();
		} catch( Exception e ) {
			log.error("Exception on DroidNanite invocation: "+e);
			e.printStackTrace();
		}

		// Return the output for collation:
		output.collect( new Text( serverType+"\t"+tikaType+"\t"+droidType ), new Text( waybackYear ) );
	}
	
	private File copyToTempFile( String name, byte[] content, int max_bytes ) throws Exception {
		File tmp = File.createTempFile("FmtTmp-", name);
		tmp.deleteOnExit();
		FileOutputStream fos = new FileOutputStream(tmp);
		IOUtils.copy(new ByteArrayInputStream(content, 0, max_bytes), fos);
		fos.flush();
		fos.close();
		return tmp;
	}
	
	private static int MAX_BUF = 16*1024;

	private File copyToTempFile( String name, byte[] content ) throws Exception {
		//if( content.length < BUF_8KB )
		return copyToTempFile(name, content, MAX_BUF);
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

}
