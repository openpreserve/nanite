package uk.bl.wap.hadoop.profiler;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapred.MapReduceBase;
import org.apache.hadoop.mapred.OutputCollector;
import org.apache.hadoop.mapred.Reducer;
import org.apache.hadoop.mapred.Reporter;
import org.apache.log4j.Logger;

@SuppressWarnings( { "deprecation" } )
public class FormatProfilerReducer extends MapReduceBase implements Reducer<Text, Text, Text, Text> {
	private static Logger log = Logger.getLogger(FormatProfilerReducer.class);
	
	public FormatProfilerReducer() {}

	class MutableInt {
		  int value = 0;
		  public void inc () { ++value; }
		  public int get () { return value; }
	}	
	
	@Override
	public void reduce( Text key, Iterator<Text> values, OutputCollector<Text, Text> output, Reporter reporter ) throws IOException {
		Map<String,MutableInt> freq = new HashMap<String,MutableInt>();
		
		log.info("Reducing for key: "+key);
		
		Text fs = new Text();
		
		while( values.hasNext() ) {
			fs = values.next();
			String type = fs.toString();
			// Increment: 
			//int count = freq.containsKey(type) ? freq.get(type) : 0;
			//freq.put(type, count + 1);
			// Increment a counter:
			MutableInt value = freq.get(type);
			if( value == null ) {
				value = new MutableInt();
				value.inc();
				freq.put(type, value);
			} else {
				value.inc();
			}
		}
		
		for( String fss : freq.keySet() ) {
			output.collect( key, new Text( fss+"\t"+freq.get(fss).get() ) );
		}
		
	}
}
