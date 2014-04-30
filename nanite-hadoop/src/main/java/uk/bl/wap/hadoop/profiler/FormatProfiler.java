package uk.bl.wap.hadoop.profiler;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.GlobFilter;
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

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import uk.bl.wa.hadoop.ArchiveFileInputFormat;
import uk.bl.wap.hadoop.gzchecker.GZChecker;


/**
 * Main class for FormarProfiler Hadoop job
 */
public class FormatProfiler extends Configured implements Tool {
	private static Logger log = Logger.getLogger(FormatProfiler.class.getName());
	
	/**
	 * Initialise job configuration
	 * @param conf configuration to initialise
	 * @param args command line arguments
	 * @throws IOException
	 */
	public void createJobConf(JobConf conf, String[] args) throws IOException {
		log.info("Loading paths...");
		String line = null;
		List<Path> paths = new ArrayList<Path>();
		BufferedReader br = new BufferedReader( new FileReader( args[ 0 ] ) );
		while( ( line = br.readLine() ) != null ) {
			paths.add( new Path( line ) );
		}
		br.close();
		log.info("Setting paths...");
		FileInputFormat.setInputPaths( conf, paths.toArray(new Path[] {}) );
		log.info("Set "+paths.size()+" InputPaths");
		
		FileOutputFormat.setOutputPath( conf, new Path( args[ 1 ] ) );
		
		//this.setProperties( conf );
		conf.setJobName( "NaniteFormatProfiler-"+new File(args[ 0 ]).getName() + "_" + System.currentTimeMillis() );
		conf.setInputFormat( ArchiveFileInputFormat.class );
		conf.setMapperClass( FormatProfilerMapper.class );
		conf.setReducerClass( FormatProfilerReducer.class );
		conf.setOutputFormat( TextOutputFormat.class );
		conf.set( "map.output.key.field.separator", "" );

		conf.setOutputKeyClass( Text.class );
		conf.setOutputValueClass( Text.class );
		conf.setMapOutputValueClass( Text.class );
		
		// search our classpath first- otherwise we get dependency problems
		conf.setUserClassesTakesPrecedence(true);
		
		// Override the task timeout to cope with behaviour when processing malformed archive files
		// Value set in MS.  Default value is 600,000ms (i.e. 10 mins)
		// Set this to 30 minutes
		int timeoutMins = 30;
		conf.setInt("mapred.task.timeout", timeoutMins*60*1000);
		
		// Override the maxiumum JobConf size so very large lists of files can be processed:
		// Default mapred.user.jobconf.limit=5242880 (5M), bump to 100 megabytes = 104857600 bytes.
		conf.set("mapred.user.jobconf.limit", "104857600");
		
		// Manually set a large number of reducers:
		Config config = ConfigFactory.load();
		conf.setNumReduceTasks(config.getInt("warc.hadoop.num_reducers"));
	}	

	public int run( String[] args ) throws IOException {
		JobConf conf = new JobConf( getConf(), FormatProfiler.class );
		this.createJobConf(conf, args);
		JobClient.runJob( conf );
		return 0;
	}

	/**
	 * Main method
	 * @param args arguments
	 * @throws Exception exception
	 */
	public static void main( String[] args ) throws Exception {
		
		if( !( args.length > 0 ) ) {
			System.out.println( "Need input file.list and output dir!" );
			System.exit( 0 );

		}

		String[] fpargs = new String[] { args[0], args[1]+"" };
		int ret = 0;
		
		ret |= ToolRunner.run( new FormatProfiler(), fpargs );

		System.exit( ret );
	}

	@SuppressWarnings("unused")
	private String getWctTi( String warcName ) {
		Pattern pattern = Pattern.compile( "^BL-([0-9]+)-[0-9]+\\.warc(\\.gz)?$" );
		Matcher matcher = pattern.matcher( warcName );
		if( matcher.matches() ) {
			return matcher.group( 1 );
		}
		return "";
	}
}
