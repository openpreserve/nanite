/**
 * 
 */
package uk.bl.wa.nanite.droid;

import java.io.IOException;
import java.io.InputStream;

import net.domesdaybook.reader.ByteReader;
import uk.gov.nationalarchives.droid.core.interfaces.RequestIdentifier;
import uk.gov.nationalarchives.droid.core.interfaces.resource.RequestMetaData;

/**
 * @author Andrew Jackson <Andrew.Jackson@bl.uk>
 *
 */
public class InputStreamIdentificationRequest extends ByteArrayIdentificationRequest {

	private InputStreamByteReader isReader;
	
	public InputStreamIdentificationRequest(RequestMetaData metaData,
			RequestIdentifier identifier, InputStream in) {
		this.metaData = metaData;
		this.identifier = identifier;
		try {
			this.size = in.available();
		} catch (IOException e) {
			e.printStackTrace();
		}
		// Init the reader:
		this.isReader = new InputStreamByteReader(in);
	}

	/* (non-Javadoc)
	 * @see uk.gov.nationalarchives.droid.core.interfaces.IdentificationRequest#getByte(long)
	 */
	@Override
	public byte getByte(long position) {
		return this.isReader.readByte(position);
	}

	/* (non-Javadoc)
	 * @see uk.gov.nationalarchives.droid.core.interfaces.IdentificationRequest#getReader()
	 */
	@Override
	public ByteReader getReader() {
		return this.isReader;
	}

	/* (non-Javadoc)
	 * @see uk.gov.nationalarchives.droid.core.interfaces.IdentificationRequest#close()
	 */
	@Override
	public void close() throws IOException {
		InputStream in = this.isReader.getInputStream();
		in.close();
	}

	/* (non-Javadoc)
	 * @see uk.gov.nationalarchives.droid.core.interfaces.IdentificationRequest#getSourceInputStream()
	 */
	@Override
	public InputStream getSourceInputStream() throws IOException {
		InputStream in = this.isReader.getInputStream();
	    in.reset();
		return in;
	}

	/**
	 * 
	 */
	public void disposeBuffer() {
		if( this.isReader != null ) {
			try {
				this.isReader.finalize();
			} catch (Throwable e) {
				//e.printStackTrace();
				// TODO Log this...
			}
		}
		this.isReader = null;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#finalize()
	 */
	@Override
	protected void finalize() throws Throwable {
		super.finalize();
		// Shut down any buffering:
		this.disposeBuffer();
	}
	
	
}
