package uk.bl.wap.nanite;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

/**
 * 
 * @author Andrew Jackson <Andrew.Jackson@bl.uk>
 *
 */
public abstract class Identification {

	public abstract ExtendedMimeType identify( InputStream in );
	
	public ExtendedMimeType identify( URL url ) throws IOException {
		return this.identify(url.openStream());
	}
	
	public ExtendedMimeType identify( File file ) throws FileNotFoundException {
		return this.identify( new FileInputStream(file) );
	}
	
}
