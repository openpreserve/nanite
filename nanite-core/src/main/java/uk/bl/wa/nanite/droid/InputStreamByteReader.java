/**
 * 
 */
package uk.bl.wa.nanite.droid;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.io.input.CloseShieldInputStream;
import org.apache.log4j.Logger;

import net.domesdaybook.reader.ByteReader;


/**
 * A ByteReader wrapped around an InputStream, using a large buffer to simulate random access.
 * 
 * TODO Shift to some kind of file-backed stream buffer, {@see CachedSeekableStream} or {@see FileCacheSeekableStream}.
 * 
 * @author Andrew Jackson <Andrew.Jackson@bl.uk>
 *
 */
public class InputStreamByteReader implements ByteReader {
	private static Logger log = Logger.getLogger(InputStreamByteReader.class);
	
	private long nextpos = 0;
	private BufferedInputStream in = null;
	static int BUFFER_SIZE = 10*1024*1024; // Items larger than this likely to fail identification;

	/**
	 * @param in Force use of a CloseShieldInputStream so we can safely dispose of any buffers we create
	 */
	public InputStreamByteReader( CloseShieldInputStream in ) {
		// Set up a large buffer for the input stream, wrapping for random access if mark/reset are not supported:
		if( ! this.in.markSupported() ) {
			this.in = new BufferedInputStream(in, BUFFER_SIZE);
		}
		// The 'reset' logic will fail if the buffer is not big enough.
		this.in.mark(BUFFER_SIZE);
		this.nextpos = 0;
	}

	@Override
	public byte readByte(long position) {
		//System.out.println("@"+nextpos+" Reading "+position);
		try {
			// If skipping back, reset then skip forward:
			if( position < this.nextpos ) {
				//System.out.println("@"+nextpos+"Reset and skip to "+position);
				in.reset();
				in.skip(position);
			} else if( position > this.nextpos ) {
				//System.out.println("@"+nextpos+"Skipping to "+position);
				in.skip( position - this.nextpos );
			}
			int b = in.read();
			//System.out.println("Got byte: "+ Integer.toHexString(0xFF & b) );
			// Increment the internal position, unless EOF?:
			this.nextpos = position+1;
			if( b == -1 ) this.nextpos = position;
			//System.out.println("NOW @"+nextpos+"\n");
			// Return the byte:
			return (byte)b;
		} catch (IOException e) {
			log.error("IOException in readByte: "+e);
			e.printStackTrace();
			log.error("Throwing as RuntimeException...");
			throw new RuntimeException(e);
		}
	}

	InputStream getInputStream() {
		return this.in;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#finalize()
	 */
	@Override
	protected void finalize() throws Throwable {
		super.finalize();
		// Close buffers
		if(this.in!=null) {
			this.in.close();
		}
		// Discard the buffer:
		this.in = null;
	}
}
