package net.vhati.modmanager.core;

import java.io.FilterWriter;
import java.io.IOException;
import java.io.Writer;


/**
 * A filter that replaces \r, \n, or \r\n, with a standard EOL string.
 *
 * Essentially, s.replaceAll( "\r(?!\n)|(?<!\r)\n|\r\n", eol );
 *
 * Filters can be chained to share one destination buffer, while each regex
 * call creates and returns a new string.
 */
public class EOLWriter extends FilterWriter {

	private String eol;
	private boolean sawCR = false;


	/**
	 * Creates a new EOLWriter.
	 *
	 * @param out a Writer object to provide the underlying stream
	 * @param eol the string to substitute where line breaks occur
	 */
	public EOLWriter( Writer out, String eol ) {
		super( out );
		this.eol = eol;
	}


	@Override
	public void write( int c ) throws IOException {
		if ( c == '\r' ) {
			this.out.write( eol );
			sawCR = true;
		}
		else if ( c == '\n' ) {
			// If in the middle of \r\n, don't bother adding another eol.
			if ( !sawCR ) this.out.write( eol );
			sawCR = false;
		}
		else {
			this.out.write( c );
			sawCR = false;
		}
	}

	@Override
	public void write( char cbuf[], int off, int len ) throws IOException {
		int end = off + len;
		for ( int i=off; i < end; i++ ) {
			if ( cbuf[i] == '\r' ) {
				this.out.write( cbuf, off, i-off );  // Write everything up to here.
				off = i + 1;                         // Skip this char.
				this.out.write( eol );               // Write EOL instead.
				sawCR = true;
			}
			else if ( cbuf[i] == '\n' ) {
				this.out.write( cbuf, off, i-off );  // Write everything up to here.
				off = i + 1;                         // Skip this char.
				if ( sawCR ) {
					sawCR = false;
				} else {
					this.out.write( eol );             // Write EOL instead.
				}
			}
			else {
				sawCR = false;                       // Keep scanning for breaks.
			}
		}
		if ( off < end ) {                       // Write all remaining chars.
			this.out.write( cbuf, off, end-off );
		}
	}

	@Override
	public void write( String str, int off, int len ) throws IOException {
		write( str.toCharArray(), off, len );
	}
}
