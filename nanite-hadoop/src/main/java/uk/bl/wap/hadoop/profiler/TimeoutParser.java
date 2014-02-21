package uk.bl.wap.hadoop.profiler;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLClassLoader;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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
	private class TikaCallable implements Callable<Integer> {
		private InputStream gIS = null; 
		private ContentHandler gCH = null;
		private Metadata gMD = null;
		private ParseContext gPC = null;
		private Exception gException = null;
		public TikaCallable(InputStream pIS, ContentHandler pCH, Metadata pMD, ParseContext pPC) {
			gIS = pIS;
			gCH = pCH;
			gMD = pMD;
			gPC = pPC;
		}
		@Override
		public Integer call() {
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
			return null;
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
		
		TikaCallable tikaCallable = new TikaCallable(pInputStream, pContentHandler, pMetadata, pParseContext);
		FutureTask<Integer> tikaTask = new FutureTask<Integer>(tikaCallable);
		Thread parserThread = new Thread(tikaTask);
		
		parserThread.setName("TimeoutParser:"+gParser.getClass().getSimpleName()+":"+System.currentTimeMillis());
		parserThread.start();
		
		boolean terminated = false;
		
		try {
			tikaTask.get(gTimeoutLength, TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			// FIXME: deprecated API call
			parserThread.stop();
			terminated = true;
		} catch (TimeoutException e) {
			// FIXME: deprecated API call
			parserThread.stop();
			terminated = true;
		} catch (ExecutionException e) {
			e.printStackTrace();
		} 

		if(terminated) {
			// Add a metadata key that indicates the parsing was terminated
			pMetadata.add(TIMEOUTKEY, "true");
			
			// FIXME: Should we force the parser to null here?
			
		}
		
		if(tikaCallable.getException()!=null) {
			Exception e = tikaCallable.getException(); 
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
		
		tikaCallable = null;
		tikaTask = null;
		parserThread = null;
		
	}
	
	@Override
	public Set<MediaType> getSupportedTypes(ParseContext pParseContext) {
		return gParser.getSupportedTypes(pParseContext);
	}

	/**
	 * Example method for creating a new ClassLoader to load the Parser from, for use when
	 * we have had to use Thread.stop()
	 * @param pFile
	 */
	// FIXME: this won't work as classes loaded from different classloaders are not compatible and we 
	// would rely on this re-loading a compatible set of classes for Tika and its dependencies
//	private static void parseFromNewLoader(File pFile) {
//		long time = System.currentTimeMillis();
//		URLClassLoader classLoader = new URLClassLoader(((URLClassLoader)TimeoutParser.class.getClassLoader()).getURLs());
//		Parser parser = null;
//		try {
//			parser = (AutoDetectParser)Class.forName("org.apache.tika.parser.AutoDetectParser", true, classLoader).newInstance();
//		} catch (InstantiationException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		} catch (IllegalAccessException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		} catch (ClassNotFoundException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
//		parser = new TimeoutParser(parser);
//		System.out.println("Parser init ms: "+(System.currentTimeMillis()-time));
//		Metadata metadata = new Metadata();
//		metadata.set(Metadata.RESOURCE_NAME_KEY, pFile.getAbsolutePath());
//		
//		try {
//			parser.parse(new FileInputStream(pFile), new BodyContentHandler(), metadata, new ParseContext());
//		} catch (FileNotFoundException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		} catch (IOException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		} catch (SAXException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		} catch (TikaException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
//		
//		System.out.println("Metadata keys: "+metadata.names().length);
//		for(String k:metadata.names()) {
//			System.out.println(k+": "+metadata.get(k));
//		}
//		
//	}

	private static void parse(File pFile) {
		long time = System.currentTimeMillis();
		Parser parser = new TimeoutParser(new AutoDetectParser());//, 10, TimeUnit.SECONDS);
		System.out.println("Parser init ms: "+(System.currentTimeMillis()-time));
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
		//parse(new File("corrupt.mp3"));
		parse(new File("v1.pdf"));
		parse(new File("pdfa.pdf"));
		
	}


}
