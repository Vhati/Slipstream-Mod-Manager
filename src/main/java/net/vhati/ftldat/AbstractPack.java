package net.vhati.ftldat;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;


public abstract class AbstractPack {

	/**
	 * Returns a descriptive name for this dat.
	 */
	public String getName() {
		throw new UnsupportedOperationException();
	}

	/**
	 * Returns an list of all innerPaths.
	 */
	public List<String> list() {
		throw new UnsupportedOperationException();
	}

	/**
	 * Returns a list of pairs of (innerPath, filesize).
	 */
	public List<PathAndSize> listSizes() {
		throw new UnsupportedOperationException();
	}

	/**
	 * Adds bytes read from an InputStream to the pack, as innerPath.
	 */
	public void add( String innerPath, InputStream is ) throws IOException {
		throw new UnsupportedOperationException();
	}

	/**
	 * Writes the contents of the file with innerPath to dstFile.
	 */
	public void extractTo( String innerPath, OutputStream os ) throws FileNotFoundException, IOException {
		throw new UnsupportedOperationException();
	}

	/**
	 * Removes the file with innerPath from the pack.
	 */
	public void remove( String innerPath ) throws FileNotFoundException, IOException {
		throw new UnsupportedOperationException();
	}

	/**
	 * Returns whether innerPath is in the pack.
	 */
	public boolean contains( String innerPath ) {
		throw new UnsupportedOperationException();
	}

	/**
	 * Returns an InputStream get bytes from an innerFile.
	 *
	 * Close all input streams before calling methods to
	 * modify this dat. Do not pass an input stream from
	 * this dat instance into another of its own methods.
	 */
	public InputStream getInputStream( String innerPath ) throws FileNotFoundException, IOException {
		throw new UnsupportedOperationException();
	}

	/**
	 * Closes this dat and releases any system resources associated with the stream.
	 */
	public void close() throws IOException {
	}

	/**
	 * Tidies up the dat before closing (possibly mandatory).
	 *
	 * @returns a result, or null if nothing happened
	 */
	public RepackResult repack() throws IOException {
		return null;
	}



	/**
	 * A holder for (innerPath + size) results from listSizes().
	 */
	public static class PathAndSize {
		public String path = null;
		public long size = 0;

		public PathAndSize( String path, long size ) {
			this.path = path;
			this.size = size;
		}
	}



	/**
	 * A holder for results after repacking a dat.
	 */
	public static class RepackResult {
		public long oldDatLength = 0;
		public long newDatLength = 0;
		public long bytesChanged = 0;

		public RepackResult( long oldDatLength, long newDatLength, long bytesChanged ) {
			this.oldDatLength = oldDatLength;
			this.newDatLength = newDatLength;
			this.bytesChanged = bytesChanged;
		}
	}
}
