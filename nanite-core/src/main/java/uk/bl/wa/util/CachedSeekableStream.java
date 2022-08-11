/*
 *    Copyright 2010-2012 University of Toronto
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package uk.bl.wa.util;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Seekable stream which uses block-based caching.
 *
 * @author AndrewBrook
 */
public class CachedSeekableStream extends SeekableStream {
    //public static final int DEFAULT_BLOCK_SIZE = 65536;
    private static final Logger LOG = LoggerFactory.getLogger(CachedSeekableStream.class);

    private final SeekableStream wrappedStream;
    private final int bufferSize;
    private final URI uri;

    private File cacheFile = null;
    private int numBlocks = 0;
    private RandomAccessFile cache = null;
    private BufferedInputStream bufferedStream;
    private int positionInBuf = 0;
    private long positionInFile = 0;


    public CachedSeekableStream(SeekableStream seekable, int bufSize, URI uri) {
        wrappedStream = seekable;
        bufferSize = bufSize;
        this.uri = uri;
        bufferedStream = new BufferedInputStream(wrappedStream, bufferSize);
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {

        int posInByteArray = offset;
        int bytesRead = 0;
        while (length > 0) {
            // How many we can read from this buffer (ie. how far allow stream is it).
            int canRead = bufferSize - positionInBuf;

            // If we are at the end of the bufferedStream, get a new one.
            if (canRead == 0) {
                //seek
                seek(positionInFile);
                //update canRead
                canRead = bufferSize - positionInBuf;
            }

            //Create temporary buffer
            int toRead = Math.min(canRead, length);
            byte[] buff1 = new byte[toRead];

            //read canRead bytes
            int bytesReadThisTime = bufferedStream.read(buff1, 0, toRead);
            System.arraycopy(buff1, 0, buffer, posInByteArray, toRead);

            //prepare for next iteration
            positionInFile += bytesReadThisTime;
            posInByteArray += toRead;
            length -= toRead;
            bytesRead += bytesReadThisTime;
            positionInBuf +=bytesReadThisTime;
        }
        return bytesRead;
    }

    private void cachedSeek(int blockOffset, int positionOffset) throws IOException{
        //from offset, calculate actual position in file
        long actualOffset = (numBlocks * 4L) + (blockOffset - 1L) * bufferSize;

        //retrieve from stream
        FileInputStream cacheStream = new FileInputStream(cacheFile);
        try {
            cacheStream.skip(actualOffset);
            bufferedStream.close();
            bufferedStream = new BufferedInputStream(cacheStream, bufferSize);
            bufferedStream.skip(positionOffset);
            positionInBuf = positionOffset;
        } catch (IOException x) {
            throw x;
        }
    }

    @Override
    public synchronized void seek(long pos) throws IOException {

        positionInFile = pos;

        //determine which block needs to be accessed
        int block = (int)(pos / bufferSize);

        // Check offset for block
        openCache();
        cache.seek(block * 4);
        int offset = cache.readInt();

        if (offset != 0) {
            //block is cached
            int positionOffset = (int)(pos % bufferSize);
            cachedSeek(offset, positionOffset);
            closeCache();
        } else {
            // Not cached, seek to start of block
            positionInFile = pos - (pos % bufferSize);
            wrappedStream.seek(positionInFile);
            bufferedStream.close();
            bufferedStream = new BufferedInputStream(wrappedStream, bufferSize);

            // Cache block
            byte[] b = new byte[bufferSize];
            int numRead = bufferedStream.read(b, 0, bufferSize); //read buffer into byte[] b
            int storeOffset = (int)((cache.length() - (numBlocks * 4))/this.bufferSize)+1; //offset to data in cache
            long actualOffset = cache.length(); //actual pointer to data in cache            
            cache.seek(block * 4); //seek to write offset
            cache.writeInt(storeOffset); //write the offset
            cache.seek(actualOffset); //seek to where data will be written
            cache.write(b, 0, bufferSize); //write data

            //skip to position % buffersize
            positionInBuf = 0;
            closeCache();

            //TODO: is this necessary? extra work...
            seek(pos);
        }
        
    }

    @Override
    public long length() {
        return wrappedStream.length();
    }

    @Override
    public String getSource() {
        return wrappedStream.getSource();
    }


    @Override
    public void close() throws IOException {
        wrappedStream.close();
    }

    @Override
    public boolean eof() throws IOException {
        return positionInFile >= wrappedStream.length();
    }

    /**
     * Savant never directly calls this method.  Implemented only because the interface requires this.
     * @deprecated Do not use; only <code>read(byte[] buffer, int offset, int length)</code> works correctly.
     */
    @Override
    public int read() throws IOException {
        byte[] sillyBuf = new byte[1];
        read(sillyBuf, 0, 1);
        return sillyBuf[0];
    }

    private void openCache() throws IOException {
        if (cacheFile == null || !cacheFile.exists()) {
            initCache();
        }
        cache = new RandomAccessFile(cacheFile, "rw");
    }

    private void closeCache() throws IOException {
        cache.close();
        cache = null;
    }

    private void initCache() throws IOException {

        cacheFile = File.createTempFile("temp", ".tmp");

        // Calculate number of blocks in file
        numBlocks = (int)Math.ceil(length() / (double)bufferSize);

        // Create the cacheFile with an empty index section.
        RandomAccessFile raf = new RandomAccessFile(cacheFile, "rw");
        for (int i = 0; i < numBlocks; i++) {
            //write 0x0000
            raf.write(0);
            raf.write(0);
            raf.write(0);
            raf.write(0);
        }
        raf.close();
    }

	@Override
	public long position() throws IOException {
		return this.positionInFile;
	}
}
