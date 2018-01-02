package net.vhati.ftldat;

import java.io.InputStream;
import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;


public class FileChannelRegionInputStream extends InputStream {

	private FileChannel channel;
	private long regionOffset;
	private long regionLength;

	// A buffer holds an even narrower region of the file.
	// When possible read() calls will reuse this,
	// rather than pester the channel.
	private ByteBuffer buf = null;
	private long bufOffset = 0;  // Relative to regionOffset.
	private int bufLength = 0;

	private long intraPos = 0;


	public FileChannelRegionInputStream( FileChannel channel, long offset, long length ) {
		this( channel, offset, length, 4096 );
	}

	public FileChannelRegionInputStream( FileChannel channel, long offset, long length, int bufferSize ) {
		this.channel = channel;
		this.regionOffset = offset;
		this.regionLength = length;
		buf = ByteBuffer.allocate( bufferSize );
	}

	@Override
	public int available() throws IOException {
		if ( !channel.isOpen() ) throw new ClosedChannelException();
		return bufLength;
	}

	@Override
	public int read() throws IOException {
		if ( !channel.isOpen() ) throw new ClosedChannelException();
		if ( intraPos >= regionLength ) return -1;

		if ( intraPos < bufOffset || intraPos >= bufOffset+bufLength ) {
			// The requested byte isn't currently buffered.
			bufOffset = intraPos;
			int len = 0;          // Get *something*.
			buf.position( 0 );
			while ( len == 0 ) {
				len = channel.read( buf, regionOffset + bufOffset );
			}
			if ( len == -1 ) {
				bufLength = 0;
				return -1;
			} else {
				bufLength = len;
			}
		}

		// Do an absolute get() from the buffer,
		//   and interpret the byte as if it were unsigned.
		int result = buf.get( (int)(intraPos - bufOffset) ) & 0xff;
		intraPos++;
		return result;
	}

	@Override
	public int read( byte[] b, int bOff, int bLen ) throws IOException {
		if ( bLen == 0 ) return 0;
		if ( bOff < 0 ) throw new IndexOutOfBoundsException( String.format( "Index: %d, Size: %d", bOff, bLen ) );
		if ( bOff + bLen > b.length ) throw new IndexOutOfBoundsException( String.format( "Index: %d, Size: %d", (bOff+bLen), bLen ) );
		if ( !channel.isOpen() ) throw new ClosedChannelException();
		if ( intraPos >= regionLength ) return -1;

		int bytesTotal = Math.min( bLen, (int)(regionLength - intraPos) );
		int bytesRemaining = bytesTotal;
		int bytesRead = 0;

		if ( intraPos >= bufOffset && intraPos < bufOffset+bufLength ) {
			// Read part of the current buffer, possibly until the end.

			buf.position( (int)(intraPos - bufOffset) );
			int bufTodo = Math.min( bytesRemaining, bufLength - (int)(intraPos - bufOffset) );
			buf.get( b, bOff, bufTodo );
			bytesRemaining -= bufTodo;
			bytesRead += bufTodo;
			intraPos += bufTodo;
		}

		if ( bytesRemaining > 0 ) {
			// Refill the buffer at the current intraPos.

			bufOffset = intraPos;
			int len = 0;
			buf.position( 0 );
			len = channel.read( buf, regionOffset + bufOffset );
			if ( len == -1 ) {
				bufLength = 0;
				throw new BufferUnderflowException();
			} else {
				bufLength = len;
			}

			buf.position( 0 );
			int bufTodo = Math.min( bytesRemaining, bufLength );
			buf.get( b, bOff + bytesRead, bufTodo );
			bytesRemaining -= bufTodo;
			bytesRead += bufTodo;
			intraPos += bufTodo;
		}

		return bytesRead;
	}


	@Override
	public long skip( long n ) throws IOException {
		if ( !channel.isOpen() ) throw new ClosedChannelException();
		if ( intraPos >= regionLength ) return -1;

		if ( intraPos + n <= regionLength ) {
			n = regionLength - intraPos;
		}
		intraPos += n;
		return n;
	}
}
