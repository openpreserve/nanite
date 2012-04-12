package uk.bl.wap.hadoop.profiler;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.filecache.DistributedCache;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapred.FileInputFormat;
import org.apache.hadoop.mapred.FileOutputFormat;
import org.apache.hadoop.mapred.JobClient;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.TextOutputFormat;
import org.apache.hadoop.util.ToolRunner;
import org.apache.hadoop.util.Tool;

import uk.bl.wap.hadoop.ArchiveFileInputFormat;
import uk.bl.wap.hadoop.format.Ohcount;
import uk.bl.wap.hadoop.util.Unpack;

/**
 * WARCTikExtractor
 * Extracts text using Tika from a series of WARC files.
 * 
 * @author rcoram
 */

@SuppressWarnings( { "deprecation" } )
public class FormatProfiler extends Configured implements Tool {

	public int run( String[] args ) throws IOException {
		JobConf conf = new JobConf( getConf(), FormatProfiler.class );

		String line = null;
		BufferedReader br = new BufferedReader( new FileReader( args[ 0 ] ) );
		while( ( line = br.readLine() ) != null ) {
			FileInputFormat.addInputPath( conf, new Path( line ) );
		}
		
		FileOutputFormat.setOutputPath( conf, new Path( args[ 1 ] ) );
		
		// Add the ohcount binary to the distributed cache.
		File ohcount = Unpack.streamToTemp(FormatProfiler.class, "native/linux_x64/"+Ohcount.OH_300_STATIC_BIN, true);
		//DistributedCache.addCacheFile( ohcount.toURI(), conf );
		
		// Run in local/debug mode: This requires that mapred.local.dir is a valid writeable folder.
		//conf.set("mapred.job.tracker", "local");

		conf.setJobName( args[ 0 ] + "_" + System.currentTimeMillis() );
		conf.setInputFormat( ArchiveFileInputFormat.class );
		conf.setMapperClass( FormatProfilerMapper.class );
		conf.setReducerClass( FormatProfilerReducer.class );
		conf.setOutputFormat( TextOutputFormat.class );
		conf.set( "map.output.key.field.separator", "" );

		conf.setOutputKeyClass( Text.class );
		conf.setOutputValueClass( Text.class );
		conf.setMapOutputValueClass( Text.class );
		
		JobClient.runJob( conf );
		return 0;

	}

	public static void main( String[] args ) throws Exception {
		if( !( args.length > 0 ) ) {
			System.out.println( "Need input file.list and output dir!" );
			System.exit( 0 );

		}
		int ret = ToolRunner.run( new FormatProfiler(), args );

		System.exit( ret );
	}

	private String getWctTi( String warcName ) {
		Pattern pattern = Pattern.compile( "^BL-([0-9]+)-[0-9]+\\.warc(\\.gz)?$" );
		Matcher matcher = pattern.matcher( warcName );
		if( matcher.matches() ) {
			return matcher.group( 1 );
		}
		return "";
	}
}
