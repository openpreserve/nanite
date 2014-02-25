package uk.bl.wap.hadoop.gzchecker;

import java.io.File;
import java.io.IOException;

import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapred.FileInputFormat;
import org.apache.hadoop.mapred.FileOutputFormat;
import org.apache.hadoop.mapred.JobClient;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.TextOutputFormat;
import org.apache.hadoop.mapred.lib.NLineInputFormat;
import org.apache.hadoop.util.ToolRunner;
import org.apache.hadoop.util.Tool;
import org.apache.log4j.Logger;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;


/**
 * This Hadoop program will farily quickly check that all gz files can be opened, listing all files
 * along with read/open status in the reducer output.
 * 
 * To run this program do the following:
 *  hadoop jar nanite-hadoop-xxx-job.jar uk.bl.wap.hadoop.gzchecker.GZChecker inputFiles.txt jobName
 *  NOTE: inputFiles.txt is a text file containing a list of (w)arc files in HDFS
 */
public class GZChecker extends Configured implements Tool {
	private static Logger log = Logger.getLogger(GZChecker.class.getName());
	
	/**
	 * String for use in reducer outputs
	 */
	public static final String OK = "ok";
	/**
	 * String for use in reducer outputs
	 */
	public static final String FAIL = "fail";

	/**
	 * Set up the configuration for this job
	 * @param conf
	 * @param args
	 * @throws IOException
	 */
	public void createJobConf(JobConf conf, String[] args) throws IOException {
		log.info("Loading paths...");
		// copy input file to hdfs
		FileSystem fs = FileSystem.get(conf);
		File localInput = new File(args[0]);
		if(!localInput.exists()) {
			log.error("Input file does not exist");
		}
		Path input = new Path(localInput.getName()+"."+System.currentTimeMillis());
		if(fs.exists(input)) {
			log.error("Input file already exists in HDFS");
		} else {
			fs.copyFromLocalFile(new Path(localInput.getAbsolutePath()), input);
			//fs.deleteOnExit(input);
		}
		fs.close();
		
		log.info("Setting paths...");
		FileInputFormat.setInputPaths( conf, input );
		
		FileOutputFormat.setOutputPath( conf, new Path( args[ 1 ] ) );
		
		//this.setProperties( conf );
		conf.setJobName( "NaniteGZChecker-"+new File(args[ 0 ]).getName() + "_" + System.currentTimeMillis() );
		conf.setInputFormat( NLineInputFormat.class );
		conf.setInt("mapred.line.input.format.linespermap", 200);
		
		conf.setMapperClass( GZCheckerMapper.class );
		conf.setReducerClass( GZCheckerReducer.class );
		conf.setOutputFormat( TextOutputFormat.class );
		conf.set( "map.output.key.field.separator", "" );

		conf.setOutputKeyClass( Text.class );
		conf.setOutputValueClass( Text.class );
		conf.setMapOutputValueClass( Text.class );
		
		// Override the task timeout to cope with behaviour when processing malformed archive files:
		// Actually, error indicates 20,000 seconds is the default here, which is 5.5 hrs!
		//conf.set("mapred.task.timeout", "1800000");
		
		// Override the maxiumum JobConf size so very large lists of files can be processed:
		// Default mapred.user.jobconf.limit=5242880 (5M), bump to 100 megabytes = 104857600 bytes.
		conf.set("mapred.user.jobconf.limit", "104857600");
		//Hadoop has default time out for 600S secinds which doesn't seems to be enough, so changed it to 30 minutes.
		conf.set("mapred.task.timeout", Integer.toString(360*60*1000));
		// Manually set a large number of reducers:
		@SuppressWarnings("unused")
		Config config = ConfigFactory.load();
//		conf.setNumReduceTasks(config.getInt("warc.hadoop.num_reducers"));
		conf.setNumReduceTasks(1);

    }

	public int run( String[] args ) throws IOException {
		JobConf conf = new JobConf( getConf(), GZChecker.class );
		this.createJobConf(conf, args);
		JobClient.runJob( conf );
		return 0;
	}

	/**
	 * Main method
	 * @param args arguments
	 * @throws Exception in case of an exception
	 */
	public static void main( String[] args ) throws Exception {
		if( !( args.length > 0 ) ) {
			System.out.println( "Need input file.list and output dir!" );
			System.exit( 0 );

		}
		int ret = ToolRunner.run( new GZChecker(), args );

		System.exit( ret );
	}

}
