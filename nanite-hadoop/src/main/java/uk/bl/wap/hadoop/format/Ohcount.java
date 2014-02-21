/**
 * 
 */
package uk.bl.wap.hadoop.format;

import java.io.File;

import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;

/**
 * @author Andrew Jackson <Andrew.Jackson@bl.uk>
 *
 */
@SuppressWarnings("javadoc")
public class Ohcount {
	private static Logger log = Logger.getLogger(Ohcount.class.getName());
	
	public static final String OH_300_STATIC_BIN = "ohcount-3.0.0-static";
	
	private static String OH_BIN = "ohcount";
	
	public Ohcount() {
	}
	
	public Ohcount( String ohcount_binary ) {
		OH_BIN = ohcount_binary;
	}
	
	public Ohcount( File ohcount_binary ) {
		OH_BIN = ohcount_binary.getAbsolutePath();
	}
	
	public String identify( File input ) {
		try {
			String[] command = { OH_BIN, "-d", input.getAbsolutePath() };
			ProcessBuilder pb = new ProcessBuilder(command);
			System.out.println("command = "+ pb.command());
			Process p = pb.start();
			p.waitFor();
			String result = IOUtils.toString(p.getInputStream());
			p.destroy();
			System.out.println("result = "+result);
			int index = result.indexOf("\t");
			if ( index > 0 ) {
				result = result.substring(0, index);
				if( ! "(null)".equals(result) )
					return "text/x-"+result;
			}
		} catch( Exception e ) {
			log.error("ohcount identify Caught exception: "+e);
			e.printStackTrace();
		}
		return "application/octet-stream";
	}
	
	public static void main( String[] args ) {
		Ohcount oh = new Ohcount("/usr/local/bin/ohcount");
		File test = new File("src/main/java/uk/bl/wap/hadoop/profiler/Ohcount.java");
		System.out.println(test.getName()+" = "+ oh.identify(test));
	}

}
