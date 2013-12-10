package uk.bl.wap.hadoop.gzchecker;

import java.io.IOException;
import java.util.zip.GZIPInputStream;

import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.MapReduceBase;
import org.apache.hadoop.mapred.Mapper;
import org.apache.hadoop.mapred.OutputCollector;
import org.apache.hadoop.mapred.Reporter;

/**
 * @author wpalmer
 */
public class GZCheckerMapper extends MapReduceBase implements Mapper<LongWritable, Text, Text, Text> {

    private FileSystem gFS = null;
	
    public GZCheckerMapper() {
    	
	}

	@Override
	public void configure(JobConf job) {
		// TODO Auto-generated method stub
		super.configure(job);
		try {
			gFS = FileSystem.get(job);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}

	@Override
	public void map(LongWritable key, Text filename, OutputCollector<Text, Text> collector,
			Reporter reporter) throws IOException {
		
		Path p = new Path(filename.toString());
		if(gFS.exists(p)) {
			//try and open the gz file, if it won't open then fail it
			FSDataInputStream gzfile = gFS.open(p);
			
			boolean success = false;
			
			GZIPInputStream gz = null; 
			try {
				// Try a simple open
				gz = new GZIPInputStream(gzfile);
				success = true;

			} catch (IOException e) {
				
				success = false;
				
			} finally {
				if(gz!=null) {
					gz.close();
					gz = null;
				}
			}

			if(success) {
				collector.collect(new Text("ok"), filename);
			} else {
				collector.collect(new Text("fail"), filename);
			}
			
		} else {
			collector.collect(new Text("badpath"), filename);
		}
		
	}

}