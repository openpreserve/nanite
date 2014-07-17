package uk.bl.wap.hadoop;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.PrintWriter;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.GlobFilter;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.util.ToolRunner;

import uk.bl.wap.hadoop.gzchecker.GZChecker;
import uk.bl.wap.hadoop.profiler.FormatProfiler;

/**
 *
 */
public class NaniteHadoop {
	
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

		final boolean gzcheck = false;
		
		if(gzcheck) {
			// Run a job to check that the input warc files are ok to use
			final String[] gzargs = new String[] { args[0], args[1]+"-precheck" };
			ret = ToolRunner.run( new GZChecker(), gzargs);

			// Recover the output here and cache to a local file
			FileSystem fs = FileSystem.get(new Configuration());
			FileStatus[] i = fs.listStatus(new Path(gzargs[1]), new GlobFilter("part-*"));
			File tempFile = File.createTempFile("nanite-", ".txt");
			tempFile.deleteOnExit();
			PrintWriter pw = new PrintWriter(new FileWriter(tempFile));
			for(FileStatus f:i) {
				BufferedReader is = new BufferedReader(new InputStreamReader(fs.open(f.getPath())));
				String[] line = null;
				while(is.ready()) {
					line = is.readLine().split("\t");;
					if(line[1].equals(GZChecker.OK)) {
						pw.println(line[0]);
					}
				}
				is.close();
			}
			fs.close();
			pw.close();

			// Use the outputs from GZChecker to run the FormatProfiler
			fpargs = new String[] { tempFile.getAbsolutePath(), args[1]+"" };
		}
		
		ret |= ToolRunner.run( new FormatProfiler(), fpargs );

		System.exit( ret );
	}
	
}
