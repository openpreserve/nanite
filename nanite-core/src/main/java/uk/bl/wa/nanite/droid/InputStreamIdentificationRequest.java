/**
 * 
 */
package uk.bl.wa.nanite.droid;

import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.io.input.CloseShieldInputStream;

import net.domesdaybook.reader.ByteReader;
import uk.gov.nationalarchives.droid.core.interfaces.RequestIdentifier;
import uk.gov.nationalarchives.droid.core.interfaces.resource.RequestMetaData;
import uk.gov.nationalarchives.droid.core.interfaces.resource.ResourceUtils;

/**
 * @author Andrew Jackson <Andrew.Jackson@bl.uk>
 *
 */
public class InputStreamIdentificationRequest extends ByteArrayIdentificationRequest {

	private InputStreamByteReader isReader;
    private String fileName;
    private String extension;
	
	public InputStreamIdentificationRequest(RequestMetaData metaData,
			RequestIdentifier identifier, InputStream in) {
		this.metaData = metaData;
        this.fileName = metaData.getName();
        this.extension = ResourceUtils.getExtension(fileName);
		this.identifier = identifier;
		try {
			this.size = in.available();
		} catch (IOException e) {
			e.printStackTrace();
		}
		// Init the reader:
		this.isReader = new InputStreamByteReader(new CloseShieldInputStream(in));
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

    @Override
    public String getFileName() {
        return this.fileName;
    }

    @Override
    public long size() {
        return this.size;
    }

    @Override
    public String getExtension() {
        return this.extension;
    }

    @Override
    public RequestMetaData getRequestMetaData() {
        return this.metaData;
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
