/**
 * 
 */
package uk.bl.wa.nanite.droid;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

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
	
	private long nextpos = 0;
	private BufferedInputStream in = null;
	static int BUFFER_SIZE = 10*1024*1024; //Integer.MAX_VALUE;

	public InputStreamByteReader( InputStream in ) {
		// Set up a large buffer for the input stream, allowing random access:
		this.in = new BufferedInputStream(in, BUFFER_SIZE);
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
			e.printStackTrace();
		}
		return 0;
	}

	InputStream getInputStream() {
		return this.in;
	}

}
