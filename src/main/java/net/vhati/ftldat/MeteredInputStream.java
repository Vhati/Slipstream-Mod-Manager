package net.vhati.ftldat;

import java.io.FilterInputStream;
import java.io.InputStream;
import java.io.IOException;


/**
 * An InputStream that counts byts that flow through it.
 */
public class MeteredInputStream extends FilterInputStream {

	long count = 0;
	long mark = -1;


	public MeteredInputStream( InputStream in ) {
		super( in );
	}

	/**
	 * Returns the number of bytes seen so far.
	 *
	 * If mark() is supported, a previous count will be restored by reset().
	 */
	public long getCount() {
		return count;
	}

	@Override
	public synchronized void mark( int readlimit ) {
		in.mark( readlimit );
		mark = count;
	}

	@Override
	public int read() throws IOException {
		int result = in.read();
		if (result != -1) {
			count++;
		}
		return result;
	}

	@Override
	public int read( byte[] b, int off, int len ) throws IOException {
		int result = in.read( b, off, len );

		if ( result != -1 ) count += result;

		return result;
	}

	@Override
	public synchronized void reset() throws IOException {
		if ( !in.markSupported() ) throw new IOException( "Mark not supported" );
		if ( mark == -1 ) throw new IOException( "Mark not set" );

		in.reset();
		count = mark;
	}

	@Override
	public long skip( long n ) throws IOException {
		long result = in.skip( n );
		count += result;
		return result;
	}
}
