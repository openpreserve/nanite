/**
 * 
 */
package uk.bl.wap.hadoop.profiler;

import java.io.File;

import org.apache.commons.io.IOUtils;

/**
 * @author Andrew Jackson <Andrew.Jackson@bl.uk>
 *
 */
public class Ohcount {
	
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
			Process p = pb.start();
			p.waitFor();
			String result = IOUtils.toString(p.getInputStream());
			p.destroy();
			int index = result.indexOf("\t");
			if ( index > 0 ) 
				return "text/x-"+result.substring(0, index);
		} catch( Exception e ) {
			System.err.println("ohcount identify Caught exception: "+e);
			e.printStackTrace();
		}
		return null;
	}
	
	public static void main( String[] args ) {
		Ohcount oh = new Ohcount("/usr/local/bin/ohcount");
		File test = new File("src/main/java/uk/bl/wap/hadoop/profiler/Ohcount.java");
		System.out.println(test.getName()+" = "+ oh.identify(test));
	}

}
