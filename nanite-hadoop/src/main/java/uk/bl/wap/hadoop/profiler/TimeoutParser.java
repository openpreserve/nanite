package uk.bl.wap.hadoop.profiler;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Timeout parsing after a specified amount of time
 * @author wpalmer
 */
public class TimeoutParser extends AbstractParser {

	/**
	 * 
	 */
	private static final long serialVersionUID = 5704727074413786775L;

	/**
	 * Internal class for running the parser in a background thread
	 * @author wpalmer
	 */
	private class TikaRunnable implements Runnable {
		private InputStream gIS = null; 
		private ContentHandler gCH = null;
		private Metadata gMD = null;
		private ParseContext gPC = null;
		private Exception gException = null;
		public TikaRunnable(InputStream pIS, ContentHandler pCH, Metadata pMD, ParseContext pPC) {
			gIS = pIS;
			gCH = pCH;
			gMD = pMD;
			gPC = pPC;
		}
		@Override
		public void run() {
			try {
				gParser.parse(gIS, gCH, gMD, gPC);
				// exceptions are cached and rethrown
			} catch (IOException e) {
				gException = e;
			} catch (SAXException e) {
				gException = e;
			} catch (TikaException e) {
				gException = e;
			}
		}
		public Exception getException() {
			return gException;
		}
	}
	
	/**
	 * Key to use in metadata object, if parser was terminated after timing out
	 */
	final public static String TIMEOUTKEY = "tika:parserTimeout";
	
	/**
	 * Default timeout value (10 seconds)
	 */
	final private static long DEFAULT_TIMEOUT_MS = 10000;
	
	private Parser gParser = null;
	private long gTimeoutLength = 0;

	/**
	 * Create a TimeoutParser
	 * Timeout is specified internally as DEFAULT_TIMEOUT_MS
	 * @param pParser parser to use for parsing
	 */
	public TimeoutParser(Parser pParser) {
		gParser = pParser;
		gTimeoutLength = DEFAULT_TIMEOUT_MS;
	}

	/**
	 * Create a TimeoutParser
	 * @param pParser parser to use for parsing
	 * @param pTimeoutLengthMS length of timeout in milliseconds
	 */
	public TimeoutParser(Parser pParser, long pTimeoutLengthMS) {
		this(pParser);
		gTimeoutLength = pTimeoutLengthMS;
	}
	
	/**
	 * Create a TimeoutParser
	 * @param pParser parser to use for parsing
	 * @param pTimeoutLength length of timeout
	 * @param pTimeoutUnits unit for length of timeout
	 */
	public TimeoutParser(Parser pParser, long pTimeoutLength, TimeUnit pTimeoutUnits) {
		this(pParser, TimeUnit.MILLISECONDS.convert(pTimeoutLength, pTimeoutUnits));
	}	

	@SuppressWarnings("deprecation")
	@Override
	public void parse(InputStream pInputStream, ContentHandler pContentHandler, Metadata pMetadata,
			ParseContext pParseContext) throws IOException, SAXException, TikaException {
		
		TikaRunnable tikaRunnable = new TikaRunnable(pInputStream, pContentHandler, pMetadata, pParseContext);
		Thread parserThread = new Thread(tikaRunnable);
		
		parserThread.setName("TimeoutParser:"+gParser.getClass().getSimpleName()+":"+System.currentTimeMillis());
		parserThread.start();
		
		long msToAllow = gTimeoutLength;

		// check for finished execution every so often so we don't hold things up unnecessarily 
		final int step = 20;
		while(msToAllow>0) {
			if(parserThread.isAlive()) {
				msToAllow-=step;
			} else {
				break;
			}
			// sleep for step
			try {
				Thread.sleep(step);
			} catch (InterruptedException e) {
				//e.printStackTrace();
			}
		}
		
		boolean addedTimeoutMetadata = false;
		
		// Kill the thread if it's still running
		while(parserThread.isAlive()) {

			// Add a metadata key that indicates we terminated the parsing
			if(!addedTimeoutMetadata) {
				pMetadata.add(TIMEOUTKEY, "true");
				addedTimeoutMetadata = true;
			}

			parserThread.interrupt();
			// FIXME: deprecated API call
			parserThread.stop();

			try {
				Thread.sleep(step);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		if(tikaRunnable.getException()!=null) {
			Exception e = tikaRunnable.getException(); 
			if(e instanceof IOException) {
				throw (IOException)e;
			}
			if(e instanceof SAXException) {
				throw (SAXException)e;
			}
			if(e instanceof TikaException) {
				throw (TikaException)e;
			}
			// Should not get here
			e.printStackTrace();
		}
		
		tikaRunnable = null;
		parserThread = null;
		
	}
	
	@Override
	public Set<MediaType> getSupportedTypes(ParseContext pParseContext) {
		return gParser.getSupportedTypes(pParseContext);
	}
	
	private static void parse(File pFile) {
		Parser parser = new TimeoutParser(new AutoDetectParser());//, 10, TimeUnit.SECONDS);
		Metadata metadata = new Metadata();
		metadata.set(Metadata.RESOURCE_NAME_KEY, pFile.getAbsolutePath());
		
		try {
			parser.parse(new FileInputStream(pFile), new BodyContentHandler(), metadata, new ParseContext());
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (SAXException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (TikaException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		System.out.println("Metadata keys: "+metadata.names().length);
		for(String k:metadata.names()) {
			System.out.println(k+": "+metadata.get(k));
		}
		
	}
	
	/**
	 * Test main method
	 * @param args
	 */
	public static void main(String[] args) {
	
		//parse(new File("odt.odt"));
		parse(new File("corrupt.mp3"));
		
	}


}
