package uk.bl.wap.hadoop.gzchecker;

import java.io.IOException;
import java.util.Iterator;

import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapred.MapReduceBase;
import org.apache.hadoop.mapred.OutputCollector;
import org.apache.hadoop.mapred.Reducer;
import org.apache.hadoop.mapred.Reporter;

/**
 * @author wpalmer
 */
public class GZCheckerReducer extends MapReduceBase implements Reducer<Text, Text, Text, Text> {
	
	public GZCheckerReducer() {
	}

	@Override
	public void reduce(Text success, Iterator<Text> files,
			OutputCollector<Text, Text> collector, Reporter reporter) throws IOException {
		
		while(files.hasNext()) {
			collector.collect(files.next(), success);
		}
		
	}

}
