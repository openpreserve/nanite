package uk.bl.wap.hadoop.profiler;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
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
import org.apache.log4j.Logger;

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
	private static Logger log = Logger.getLogger(FormatProfiler.class.getName());
	private static final String CONFIG = "/hadoop_utils.config";

	public int run( String[] args ) throws IOException {
		JobConf conf = new JobConf( getConf(), FormatProfiler.class );

		log.info("Loading paths...");
		String line = null;
		List<Path> paths = new ArrayList<Path>();
		BufferedReader br = new BufferedReader( new FileReader( args[ 0 ] ) );
		while( ( line = br.readLine() ) != null ) {
			paths.add( new Path( line ) );
		}
		log.info("Setting paths...");
		FileInputFormat.setInputPaths( conf, paths.toArray(new Path[] {}) );
		log.info("Set "+paths.size()+" InputPaths");
		
		FileOutputFormat.setOutputPath( conf, new Path( args[ 1 ] ) );
		
		//this.setProperties( conf );
		conf.setJobName( args[ 0 ] + "_" + System.currentTimeMillis() );
		conf.setInputFormat( ArchiveFileInputFormat.class );
		conf.setMapperClass( FormatProfilerMapper.class );
		conf.setReducerClass( FormatProfilerReducer.class );
		conf.setOutputFormat( TextOutputFormat.class );
		conf.set( "map.output.key.field.separator", "" );

		conf.setOutputKeyClass( Text.class );
		conf.setOutputValueClass( Text.class );
		conf.setMapOutputValueClass( Text.class );
		
		// Manually set a large number of reducers:
		conf.setNumReduceTasks(50);
		
		JobClient.runJob( conf );
		return 0;
	}

	private void setProperties( JobConf conf ) throws IOException {
		Properties properties = new Properties();
		properties.load( this.getClass().getResourceAsStream( ( CONFIG ) ) );
		conf.set( "solr.default", properties.getProperty( "solr_default" ) );
		conf.set( "solr.image", properties.getProperty( "solr_image" ) );
		conf.set( "solr.media", properties.getProperty( "solr_media" ) );
		conf.set( "solr.batch.size", properties.getProperty( "solr_batch_size" ) );
		conf.set( "solr.threads", properties.getProperty( "solr_threads" ) );
		conf.set( "solr.image.regex", properties.getProperty( "solr_image_regex" ) );
		conf.set( "solr.media.regex", properties.getProperty( "solr_media_regex" ) );

		conf.set( "record.exclude.mime", properties.getProperty( "mime_exclude" ) );
		conf.set( "record.exclude.url", properties.getProperty( "url_exclude" ) );
		conf.set( "record.size.max", properties.getProperty( "max_payload_size" ) );
		conf.set( "record.include.response", properties.getProperty( "response_include" ) );
		conf.set( "record.include.protocol", properties.getProperty( "protocol_include" ) );

		conf.set( "tika.exclude.mime", properties.getProperty( "mime_exclude" ) );
		conf.set( "tika.timeout", properties.getProperty( "tika_timeout" ) );
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
