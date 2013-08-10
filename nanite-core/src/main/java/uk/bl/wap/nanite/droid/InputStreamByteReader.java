/**
 * 
 */
package uk.bl.wap.nanite.droid;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

import net.domesdaybook.reader.ByteReader;

/**
 * A ByteReader wrapped around an InputStream, using a large buffer to simulate random access.
 * 
 * @author Andrew Jackson <Andrew.Jackson@bl.uk>
 *
 */
public class InputStreamByteReader implements ByteReader {
	
	private BufferedInputStream in;
	private long nextpos = 0;
	private static int BUFFER_SIZE = 10*1024*1024; //Integer.MAX_VALUE;

	public InputStreamByteReader( InputStream in ) {
		// Set up a large buffer for the input stream, allowing random access:
		this.in = new BufferedInputStream(in, BUFFER_SIZE);
		this.nextpos = 0;
		// The 'reset' logic will fail if the buffer is not big enough.
		this.in.mark(BUFFER_SIZE);
	}

	@Override
	public byte readByte(long position) {
		//System.out.println("Reading "+position);
		try {
			// If skipping back, skip back.
			if( position < this.nextpos ) {
				in.reset();
				in.skip(position);
			} else if( position > this.nextpos ) {
				in.skip( position - this.nextpos );
			}
			this.nextpos = position+1;
			return (byte)in.read();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return 0;
	}

}
