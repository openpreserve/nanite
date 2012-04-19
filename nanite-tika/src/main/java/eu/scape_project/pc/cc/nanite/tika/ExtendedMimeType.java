/**
 * 
 */
package eu.scape_project.pc.cc.nanite.tika;

import java.io.UnsupportedEncodingException;
import java.util.Enumeration;

import javax.activation.MimeType;
import javax.activation.MimeTypeParseException;

/**
 * @author Andrew Jackson <Andrew.Jackson@bl.uk>
 *
 */
public class ExtendedMimeType extends MimeType {
	
	/** */
	public static final String VERSION = "version";
	
	/** */
	public static final String SOFTWARE = "software";
	
	/** */
	public static final String HARDWARE = "hardware";

	/** */
	public static ExtendedMimeType OCTET_STREAM = null;

	/** */
	public static ExtendedMimeType EMPTY = null;

	/* Set up static format identifiers*/
	static {
		try {
			OCTET_STREAM = new ExtendedMimeType("application/octet-stream");
			EMPTY = new ExtendedMimeType("application/x-empty");
		} catch (MimeTypeParseException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * @param string
	 * @throws MimeTypeParseException
	 */
	public ExtendedMimeType(String string) throws MimeTypeParseException {
		super(string);
	}

	/**
	 * 
	 * @param m1
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public boolean match( MimeType m1 ) {
		// Deal with NULLs
		if( m1 == null || this == null ) return m1 == this;
		// Check the primary and sub type of this object is the same:
		if( ! this.getBaseType().equals( m1.getBaseType() ) ) return false;
		// Check if all parameters are shared and equal:
		Enumeration<String> names = m1.getParameters().getNames();
		while ( names.hasMoreElements() ) {
			String name = names.nextElement();
			if( ! m1.getParameter(name).equals( this.getParameter(name)) ) return false;
		}
		names = this.getParameters().getNames();
		while ( names.hasMoreElements() ) {
			String name = names.nextElement();
			if( ! this.getParameter(name).equals( m1.getParameter(name)) ) return false;
		}
		// return true if no mis-matches found:
		return true;
	}

	/**
	 * 
	 * @param m1
	 * @param m2
	 * @return
	 */
	public static boolean match( ExtendedMimeType m1, ExtendedMimeType m2 ) {
		return m1.match(m2);
	}

	/**
	 * 
	 * @return
	 */
	public String getVersion() {
		return this.getParameter(VERSION);
	}
	
	/**
	 * 
	 * @param version
	 */
	public void setVersion( String version ) {
		this.setParameter(VERSION, version);
	}
	
	/**
	 * 
	 * @return
	 */
	public String getSoftware() {
		return this.getParameter(SOFTWARE);
	}

	/**
	 * 
	 * @param software
	 */
	public void setSoftware(String software) {
		this.setParameter(SOFTWARE, software);
	}
	
	
	/**
	 * @return the hardware
	 */
	public String getHardware() {
		return this.getParameter(HARDWARE);
	}

	/**
	 * @param hardware
	 */
	public void setHardware( String hardware ) {
		this.setParameter(HARDWARE, hardware);
	}

	/* (non-Javadoc)
	 * @see javax.activation.MimeType#setParameter(java.lang.String, java.lang.String)
	 */
	@Override
	public void setParameter(String arg0, String arg1) {
		super.setParameter(cleanup(arg0), cleanup(arg1));
	}
	
	/**
	 * In some foul cases, pdf:producer can have newlines at the end.
	 * Therefore, we defensively strip whitespace.
	 * 
	 * Ideally, perhaps this would keep this data, even leading/trailing space, 
	 * in some escaped form, as a possible signature of the source process.
	 * 
	 * FIXME Escape bad chars instead of removing them?
	 * 
	 * @param arg
	 * @return
	 */
	private String cleanup( String arg ) {
		if( arg != null ) {
			arg.replaceAll(";", ",");
			arg.replaceAll("=", "");
			arg.replaceAll("\n", " ");
			arg.replaceAll("\r", " ");
			arg.replaceAll("\t", " ");
			// Also strip all non-ASCII characters:
			arg.replaceAll("[^\\x00-\\x7F]", "");
			// Strip leading/lagging whitespace:
			arg = arg.trim();
			// Enforce UTF-8:
			try {
				arg = new String( arg.getBytes("UTF-8"), "UTF-8");
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
			}
		}
		return arg;
	}
}
