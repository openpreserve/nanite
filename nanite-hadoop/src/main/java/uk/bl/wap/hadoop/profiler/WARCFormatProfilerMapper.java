package uk.bl.wap.hadoop.profiler;

/* 
 * For JobConf.get() property see:
 * http://hadoop.apache.org/common/docs/r0.18.3/mapred_tutorial.html
 */

import java.io.File;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.MapReduceBase;
import org.apache.hadoop.mapred.Mapper;
import org.apache.hadoop.mapred.OutputCollector;
import org.apache.hadoop.mapred.Reporter;
import org.apache.tika.Tika;
import org.archive.io.ArchiveRecordHeader;

import eu.scape_project.pc.cc.nanite.Nanite;

import uk.bl.wap.hadoop.WritableArchiveRecord;
import uk.gov.nationalarchives.droid.core.interfaces.IdentificationResult;
import uk.gov.nationalarchives.droid.core.interfaces.IdentificationResultCollection;
import uk.gov.nationalarchives.droid.core.interfaces.signature.SignatureFileException;

import static org.archive.io.warc.WARCConstants.HEADER_KEY_DATE;

@SuppressWarnings( { "deprecation" } )
public class WARCFormatProfilerMapper extends MapReduceBase implements Mapper<Text, WritableArchiveRecord, Text, Text> {
	String workingDirectory = "";
	Tika tika = new Tika();
	Nanite nanite = null;
	File tmpFile = null;

	public WARCFormatProfilerMapper() {
	 try {
		nanite = new Nanite();
		tmpFile = File.createTempFile("Nanite", "tmp");
		tmpFile.deleteOnExit();
	} catch (IOException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
		System.out.println("Exception on Nanite instanciation: "+e);
	} catch (SignatureFileException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
		System.out.println("Exception on Nanite instanciation: "+e);
	}
	}

	@Override
	public void configure( JobConf job ) {
		this.workingDirectory = job.get( "mapred.work.output.dir" );
	}

	@Override
	public void map( Text key, WritableArchiveRecord value, OutputCollector<Text, Text> output, Reporter reporter ) throws IOException {
		ArchiveRecordHeader header = value.getRecord().getHeader();
		String serverType = null;
		String tikaType = null;
		String droidType = null;

		if( !header.getHeaderFields().isEmpty() ) {
			try {
				// The crawl year:
				String wctID = this.getWctTi( key.toString() );
				String waybackDate = ( ( String ) header.getHeaderFields().get( HEADER_KEY_DATE ) ).replaceAll( "[^0-9]", "" );
				String waybackYear = waybackDate.substring(0,4);

				// Type according to server:
				serverType = value.getHttpHeader("Content-Type");
				if( serverType == null ) {
					output.collect( new Text("LOG: Server Content-Type is null."), new Text(wctID));
				}

				// Type according to Tiki:
				tikaType = tika.detect( value.getPayload() );
				
				// Type according to Droid/Nanite:
				droidType = "application/octet-stream";
				try {
				IdentificationResultCollection irc = nanite.identify(
						Nanite.createByteArrayIdentificationRequest(tmpFile.toURI(), value.getPayload()));
				if( irc.getResults().size() > 0 ) {
					IdentificationResult res = irc.getResults().get(0);
					droidType = Nanite.getMimeTypeFromResult(res);
				} else {
					output.collect( new Text("LOG: Droid found no match."), new Text(wctID));
				}
				} catch( Exception e ) {
					e.printStackTrace();
					System.out.println("Exception on Nanite invocation: "+e);
					output.collect( new Text("LOG: Droid threw exception: "+e), new Text(wctID));
				}

				output.collect( new Text( serverType+"\t"+tikaType+"\t"+droidType ), new Text( waybackYear ) );
			} catch( Exception e ) {
				System.err.println( e.getMessage() );
			}
		}
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
