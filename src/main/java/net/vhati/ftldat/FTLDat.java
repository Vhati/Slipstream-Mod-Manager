package net.vhati.ftldat;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
//import java.nio.MappedByteBuffer;  // For Memory-mapped streams
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import net.vhati.ftldat.FileChannelRegionInputStream;


public class FTLDat {

	/**
	 * Returns segments of a path string, split by each "/" occurrence.
	 */
	public static String[] ftlPathSplit( String path ) {
		return path.split( "/" );
	}

	/**
	 * Concatenates an array of strings with "/" between them.
	 *
	 * No slashes will be added for empty strings.
	 * Any incidental backslashes will become forward slashes.
	 */
	public static String ftlPathJoin( String[] chunks ) {
		StringBuilder buf = new StringBuilder();
		boolean first = true;
		for ( String chunk : chunks ) {
			if ( chunk.length() == 0 ) continue;
			if ( first ) {
				buf.append( "/" );
				first = false;
			}
			buf.append( chunk );
		}
		return buf.toString().replace( "\\", "/" );
	}

	/**
	 * Concatenates two strings with a "/" between them.
	 *
	 * No slash will be added if either string is empty.
	 * Any incidental backslashes will become forward slashes.
	 */
	public static String ftlPathJoin( String a, String b ) {
		StringBuilder buf = new StringBuilder();
		if ( a.length() > 0 ) buf.append( a );
		if ( a.length() * b.length() > 0 ) buf.append( "/" );
		if ( b.length() > 0 ) buf.append( b );
		return buf.toString().replace( "\\", "/" );
	}

	/**
	 * Copies all bytes from one file to another.
	 */
	public static void copyFile( File srcFile, File dstFile ) throws IOException {
		FileInputStream is = null;
		FileOutputStream os = null;
		try {
			is = new FileInputStream( srcFile );
			os = new FileOutputStream( dstFile );

			byte[] buf = new byte[4096];
			int len;
			while ( (len = is.read( buf )) >= 0 ) {
				os.write( buf, 0, len );
			}
		}
		finally {
			try {if ( is != null ) is.close();}
			catch ( IOException e ) {}

			try {if ( os != null ) os.close();}
			catch ( IOException e ) {}
		}
	}

	/**
	 * Calculates an MD5 hash of data from an InputStream.
	 *
	 * The returned string will be lowercase hexadecimal.
	 */
	public static String calcStreamMD5( InputStream is ) throws NoSuchAlgorithmException, IOException {
		MessageDigest md = MessageDigest.getInstance( "MD5" );
		byte[] buf = new byte[4096];
		int len;
		while ( (len = is.read( buf )) >= 0 ) {
			md.update( buf, 0, len );
		}

		byte[] hashBytes = md.digest();
		StringBuilder hashStringBuf = new StringBuilder();
		for ( byte b : hashBytes ) {
			hashStringBuf.append( Integer.toString( (b & 0xff) + 0x100, 16 ).substring( 1 ) );
		}
		return hashStringBuf.toString();
	}

	public static String calcFileMD5( File f ) throws NoSuchAlgorithmException, IOException {
		String result = null;
		FileInputStream is = null;
		try {
			is = new FileInputStream( f );
			result = FTLDat.calcStreamMD5( is );
		}
		finally {
			try {if (is != null) is.close();}
			catch ( Exception e ) {}
		}
		return result;
	}


	/**
	 * Returns an approximate byte count for humans.
	 */
	public static String humanReadableByteCount( long bytes, boolean si ) {
		int unit = si ? 1000 : 1024;
		if ( bytes < unit ) return bytes +" B";
		int exp = (int)( Math.log( bytes ) / Math.log( unit ) );
		String pre = (si ? "kMGTPE" : "KMGTPE").charAt( exp-1 ) + (si ? "" : "i");
		return String.format( "%.1f %sB", (bytes / Math.pow( unit, exp )), pre );
	}


	/**
	 * Information about an innerFile within a dat.
	 *
	 * entryOffset - Offset (written in header) to
	 *               the dataSize + innerPath + data.
	 * innerPath   - A virtual location ("dir/dir/filename").
	 * dataOffset  - Offset to the innerFile.
	 * dataSize    - Size of the innerFile.
	 */
	public static class DatEntry {
		public long entryOffset = 0;
		public String innerPath = null;
		public long dataOffset = 0;
		public long dataSize = 0;

		public DatEntry() {
		}
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



	/*
	 * An InputStream wrapping a ByteBuffer.
	 *
	 * A memory-mapped region of a file is a ByteBuffer.
	 */
/*
	public static class ByteBufferBackedInputStream extends InputStream {
		ByteBuffer buf;

		public ByteBufferBackedInputStream( ByteBuffer buf ) {
			this.buf = buf;
		}

		@Override
		public synchronized int available() throws IOException {
			if ( !buf.hasRemaining() ) return 0;
			return buf.remaining();
		}

		@Override
		public synchronized int read() throws IOException {
			if ( !buf.hasRemaining() ) return -1;
			return buf.get() & 0xFF;
		}

		@Override
		public synchronized int read( byte[] bytes, int off, int len ) throws IOException {
			if ( !buf.hasRemaining() ) return -1;
			len = Math.min( len, buf.remaining() );
			buf.get(bytes, off, len);
			return len;
		}
	}
*/



	public abstract static class AbstractPack {

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
		 * Adds bytes read from srcFile to the pack, as innerPath.
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
	}



	/**
	 * A pseudo-dat backed by a real filesystem.
	 *
	 * Files can be independently added/removed/altered
	 * directly, so long as this class is not busy
	 * at the time.
	 *
	 * The contains() method returns true for directories,
	 * but remove() will not delete them. The list()
	 * method will not include directories themselves,
	 * only files within.
	 */
	public static class FolderPack extends AbstractPack {
		private File rootDir;


		public FolderPack( File rootDir ) {
			this.rootDir = rootDir;
		}


		@Override
		public String getName() {
			return rootDir.getName();
		}

		@Override
		public List<String> list() {
			List<String> result = new ArrayList<String>();

			Stack<String> pendingPaths = new Stack<String>();
			pendingPaths.push( "" );

			while ( !pendingPaths.isEmpty() ) {
				String current = pendingPaths.pop();
				File tmpFile = new File( rootDir, current );
				if ( tmpFile.isFile() ) {
					result.add( current );
				}
				else if ( tmpFile.isDirectory() ) {
					for ( String childName : tmpFile.list() ) {
						pendingPaths.push( FTLDat.ftlPathJoin(current, childName) );
					}
				}
			}
			return result;
		}

		@Override
		public List<PathAndSize> listSizes() {
			List<PathAndSize> result = new ArrayList<PathAndSize>();
			List<String> innerPaths = list();
			for ( String innerPath : innerPaths ) {
				File tmpFile = getFile( innerPath );
				result.add( new PathAndSize( innerPath, tmpFile.length() ) );
			}
			return result;
		}

		@Override
		public void add( String innerPath, InputStream is ) throws IOException {
			File dstFile = getFile( innerPath );
			if ( dstFile.exists() ) throw new IOException( "InnerPath already exists: "+ innerPath );

			dstFile.getParentFile().mkdirs();

			FileOutputStream os = null;
			try {
				os = new FileOutputStream( dstFile );

				byte[] buf = new byte[4096];
				int len;
				while ( (len = is.read( buf )) >= 0 ) {
					os.write( buf, 0, len );
				}
			}
			finally {
				try {if ( os != null ) os.close();}
				catch ( IOException e ) {}
			}
		}

		@Override
		public void extractTo( String innerPath, OutputStream os ) throws IOException {
			File srcFile = getFile( innerPath );

			FileInputStream is = null;
			try {
				is = new FileInputStream( srcFile );

				byte[] buf = new byte[4096];
				int len;
				while ( (len = is.read( buf )) >= 0 ) {
					os.write( buf, 0, len );
				}
			}
			finally {
				try {if ( is != null ) is.close();}
				catch ( IOException e ) {}
			}
		}

		@Override
		public void remove( String innerPath ) {
			File tmpFile = getFile( innerPath );
			if ( tmpFile.exists() && tmpFile.isFile() ) {
				tmpFile.delete();
			}
		}

		@Override
		public boolean contains( String innerPath ) {
			File tmpFile = getFile( innerPath );
			return tmpFile.exists();
		}

		@Override
		public InputStream getInputStream( String innerPath ) throws FileNotFoundException, IOException  {
			return new FileInputStream( getFile( innerPath ) );
		}

		/**
		 * Returns a File object for an innerPath.
		 * The location it represents is not guaranteed to exist.
		 */
		public File getFile( String innerPath ) {
			if ( innerPath.indexOf( "\\" ) != -1 ) {
				throw new IllegalArgumentException( "InnerPath contains backslashes: "+ innerPath );
			}
			File tmpFile = new File( rootDir, innerPath );

			// Check if the file is inside rootDir.
			File parentDir = tmpFile.getParentFile();
			while( parentDir != null ) {
				if ( parentDir.equals( rootDir ) ) return tmpFile;
				parentDir = parentDir.getParentFile();
			}

			throw new IllegalArgumentException( String.format( "InnerPath \"%s\" is outside the FolderPack at \"%s\"", innerPath, rootDir ) );
		}
	}



	/**
	 * The original format FTL used to store resources.
	 *
	 * Structure:
	 *   Index = A count, followed by a series of offsets to entries.
	 *   Entries = A series of {dataSize, innerPath, data} hunks.
	 */
	public static class FTLPack extends AbstractPack {

		private CharsetEncoder asciiEncoder = Charset.forName( "US-ASCII" ).newEncoder();

		private ByteBuffer byteBuffer = null;
		private File datFile = null;
		private RandomAccessFile raf = null;
		private List<DatEntry> entryList = null;
		private Map<String, Integer> pathToIndexMap = null;


		/**
		 * Opens or creates a dat in various modes.
		 * When creating, the initial index size will be 2048.
		 *
		 * @see FTLPack(File datFile, String mode, int indexSize)
		 */
		public FTLPack( File datFile, String mode ) throws IOException {
			this( datFile, mode, 2048 );
		}

		/**
		 * Opens or creates a dat in various modes.
		 *
		 * The mode must be one of the following:
		 *   r  - opens an existing dat, read-only.
		 *   r+ - opens an existing dat, read/write.
		 *   w+ - creates a new empty dat, read/write.
		 *
		 * @param datFile a file to open/create
		 * @param mode see above
		 * @param indexSize size of the initial index if creating
		 */
		public FTLPack( File datFile, String mode, int indexSize ) throws IOException {
			// A reusable buffer large enough for the unsigned read methods.
			byteBuffer = ByteBuffer.allocate( 4 );
			byteBuffer.order( ByteOrder.LITTLE_ENDIAN );

			if ( mode.equals( "r" ) ) {
				if ( !datFile.exists() )
					throw new FileNotFoundException( String.format( "The datFile was not found: %s", datFile.getPath() ) );

				this.datFile = datFile;
				raf = new RandomAccessFile( datFile, "r" );
				readIndex();
			}
			else if ( mode.equals( "r+" ) ) {
				if ( !datFile.exists() )
					throw new FileNotFoundException( String.format( "The datFile was not found: %s", datFile.getPath() ) );

				this.datFile = datFile;
				raf = new RandomAccessFile( datFile, "rw" );
				readIndex();
			}
			else if ( mode.equals( "w+" ) ) {
				this.datFile = datFile;
				raf = new RandomAccessFile( datFile, "rw" );
				createIndex( indexSize );
			}
			else {
				throw new IllegalArgumentException( String.format( "FTLPack constructor's mode arg was not 'r', 'r+', or 'w+' (%s)", mode ) );
			}
		}


		/**
		 * Reads a little-endian unsigned int.
		 * Java doesn't have an unsigned int primitive,
		 * so a long holds the value instead.
		 */
		private long readLittleUInt() throws IOException {
			byteBuffer.clear();
			raf.readFully( byteBuffer.array(), 0, 4 );

			// Read a signed int, then discard sign
			// by casting to long and hacking off bits.
			long result = byteBuffer.getInt( 0 );
			result &= 0x00000000FFFFFFFFL;

			return result;
		}

		private void writeLittleUInt( long n ) throws IOException {
			byteBuffer.clear();

			// Write a signed int, after discarding sign
			// by casting from long and hacking off bits.
			byteBuffer.putInt( 0, (int)(n & 0x00000000FFFFFFFFL) );

			raf.write( byteBuffer.array(), 0, 4 );
		}

		private String readLittleUString() throws IOException {
			long strLen = readLittleUInt();
			byte[] strBytes = new byte[ (int)strLen ];
			raf.readFully( strBytes );

			return new String( strBytes, asciiEncoder.charset().name() );
		}

		private void writeLittleUString( String s ) throws IOException {
			writeLittleUInt( s.length() );
			byte[] strBytes = s.getBytes( asciiEncoder.charset().name() );
			raf.write( strBytes );
		}

		/**
		 * Returns the offset to seek within the header,
		 * in order to read the data offset of an innerFile entry.
		 *
		 * @param n the nth index.
		 */
		private long getHeaderIndexPosition( int n ) {
			return ( 4 + n*4 );  // 4-byte indexSize + 4-byte indeces.
		}

		/**
		 * Creates a new index.
		 * WARNING: This will erase the file.
		 */
		private void createIndex( int indexSize ) throws IOException {
			entryList = new ArrayList<DatEntry>( indexSize );
			for ( int i=0; i < indexSize; i++ ) {
				entryList.add( null );
			}

			pathToIndexMap = new HashMap<String, Integer>( indexSize );

			raf.seek( 0 );
			raf.setLength( 0 );
			writeLittleUInt( indexSize );
			for ( int i=0; i < indexSize; i++ ) {
				writeLittleUInt( 0 );
			}
		}

		/**
		 * Reads (or re-reads) the index from the file.
		 */
		private void readIndex() throws IOException {
			raf.seek( 0 );
			int indexSize = (int)readLittleUInt();
			if ( indexSize * 4 > raf.length() ) {
				throw new IOException( String.format( "Corrupt dat file (%s): header claims to be larger than the entire file", getName() ) );
			}

			entryList = new ArrayList<DatEntry>( indexSize );
			for ( int i=0; i < indexSize; i++ ) {
				entryList.add( null );
			}

			pathToIndexMap = new HashMap<String, Integer>( indexSize );

			// Store partial DatEntry objects in entryList (leaving nulls where absent).
			for ( int i=0; i < indexSize; i++ ) {
				long entryOffset = readLittleUInt();

				if ( entryOffset != 0 ) {
					DatEntry entry = new DatEntry();
					entry.entryOffset = entryOffset;
					entryList.set( i, entry );
				}
			}

			for ( int i=0; i < indexSize; i++ ) {
				DatEntry entry = entryList.get( i );
				if ( entry == null ) continue;

				raf.seek( entry.entryOffset );
				entry.dataSize = readLittleUInt();
				entry.innerPath = readLittleUString();
				entry.dataOffset = raf.getChannel().position();

				if ( pathToIndexMap.containsKey( entry.innerPath ) ) {
					throw new IOException( "InnerPath occurs more than once: "+ entry.innerPath );
				}
				pathToIndexMap.put( entry.innerPath, new Integer( i ) );
			}
		}

		/**
		 * Moves the nth index's entry to the end of the file.
		 * It will still be nth in the header, however.
		 * Used by growIndex().
		 */
		private void moveEntryToEOF( int n ) throws IOException {
			DatEntry entry = entryList.get( n );
			long oldOffset = entry.entryOffset;
			long newOffset = raf.length();

			long totalBytes = (entry.dataOffset-entry.entryOffset) + entry.dataSize;
			long bytesRemaining = totalBytes;
			byte[] buf = new byte[4096];
			int len;
			while ( bytesRemaining > 0 ) {
				raf.seek( oldOffset + totalBytes - bytesRemaining );
				len = raf.read( buf, 0, (int)Math.min( buf.length, bytesRemaining ) );
				if ( len == -1 ) {
					throw new IOException( "EOF prematurely reached reading innerPath: "+  entry.innerPath );
				}

				raf.seek( newOffset + totalBytes - bytesRemaining );
				raf.write( buf, 0, len );
				bytesRemaining -= len;
			}
			// Update the index.
			raf.seek( getHeaderIndexPosition( n ) );
			writeLittleUInt( newOffset );
			entry.dataOffset = ( newOffset + (entry.dataOffset-entry.entryOffset) );
			entry.entryOffset = newOffset;
		}

		/**
		 * Ensures the index has room for at least n entries.
		 *
		 * This is done by moving the first innerFile after the index
		 * to the end of the file. The region it used to occupy can then
		 * be filled with additional indeces.
		 */
		private void growIndex( int amount ) throws IOException {
			int freeRoom = -1;

			while ( true ) {
				int vacancyCount = Collections.frequency( entryList, null );

				if ( entryList.size() - vacancyCount == 0 ) {
					// There is no innerFile after the index. We can grow
					// as much as we like. Limit ourselves to amount.
					freeRoom = amount;
					break;
				}
				else {
					// Find the used index with the lowest entryOffset.
					int earliestUsedIndex = -1;
					long minEntryOffset = Long.MAX_VALUE;
					for ( int i=0; i < entryList.size(); i++ ) {
						DatEntry entry = entryList.get( i );
						if ( entry.entryOffset < minEntryOffset ) {
							earliestUsedIndex = i;
							minEntryOffset = entry.entryOffset;
						}
					}
					// (region between header and first innerFile entry) / (possible 4-byte ints).
					freeRoom = (int)( ( minEntryOffset - getHeaderIndexPosition( entryList.size() ) ) / 4 );

					if ( freeRoom >= amount ) {
						freeRoom = amount;  // We don't need hundreds of thousands more.
						break;
					}

					// If it's not enough, move the first file and check again.
					moveEntryToEOF( earliestUsedIndex );
				}
			}
			// Expand the header to claim the vacated region.
			for ( int i=0; i < freeRoom; i++ ) {
				entryList.add( null );
			}
			raf.seek( 0 );
			writeLittleUInt( entryList.size() );
			raf.seek( getHeaderIndexPosition(entryList.size() - freeRoom) );
			for ( int i=0; i < freeRoom; i++ ) {
				writeLittleUInt( 0 );
			}
		}

		@Override
		public String getName() {
			return datFile.getName();
		}

		@Override
		public List<String> list() {
			List<String> result = new ArrayList<String>();
			result.addAll( pathToIndexMap.keySet() );
			return result;
		}

		@Override
		public List<PathAndSize> listSizes() {
			List<PathAndSize> result = new ArrayList<PathAndSize>();
			for ( DatEntry entry : entryList ) {
				if ( entry == null ) continue;
				PathAndSize pas = new PathAndSize( entry.innerPath, entry.dataSize );
				result.add( pas );
			}
			return result;
		}

		@Override
		public void add( String innerPath, InputStream is ) throws IOException {
			if ( innerPath.indexOf( "\\" ) != -1 ) {
				throw new IllegalArgumentException( "InnerPath contains backslashes: "+ innerPath );
			}
			if ( pathToIndexMap.containsKey( innerPath ) ) {
				throw new IOException( "InnerPath already exists: "+ innerPath );
			}
			if ( !asciiEncoder.canEncode( innerPath ) ) {
				throw new IllegalArgumentException( "InnerPath contains non-ascii characters: "+ innerPath );
			}

			// Find a vacancy in the header, or create one.
			int entryIndex = entryList.indexOf( null );
			if ( entryIndex == -1 ) {
				growIndex( 50 );  // Save effort for 49 future adds.
				entryIndex = entryList.indexOf( null );
			}

			DatEntry entry = new DatEntry();
			entry.entryOffset = raf.length();
			entry.innerPath = innerPath;
			entry.dataSize = 0;  // Write this later.

			raf.seek( getHeaderIndexPosition( entryIndex ) );
			writeLittleUInt( entry.entryOffset );

			raf.seek( entry.entryOffset );
			writeLittleUInt( entry.dataSize );
			writeLittleUString( entry.innerPath );
			entry.dataOffset = raf.getChannel().position();

			byte[] buf = new byte[4096];
			int len;
			while ( (len = is.read( buf )) >= 0 ) {
				raf.write( buf, 0, len );
			}

			// Go back and fill in the dataSize.
			entry.dataSize = raf.getChannel().position() - entry.dataOffset;
			raf.seek( entry.entryOffset );
			writeLittleUInt( entry.dataSize );

			entryList.set( entryIndex, entry );
			pathToIndexMap.put( innerPath, entryIndex );
		}

		@Override
		public void extractTo( String innerPath, OutputStream os ) throws FileNotFoundException, IOException {
			if ( innerPath.indexOf( "\\" ) != -1 ) {
				throw new IllegalArgumentException( "InnerPath contains backslashes: "+ innerPath );
			}
			if ( !pathToIndexMap.containsKey( innerPath ) ) {
				throw new FileNotFoundException( "InnerPath does not exist: "+ innerPath );
			}

			int entryIndex = pathToIndexMap.get( innerPath ).intValue();
			DatEntry entry = entryList.get( entryIndex );

			raf.seek( entry.dataOffset );

			long bytesRemaining = entry.dataSize;
			byte[] buf = new byte[4096];
			int len;
			while ( bytesRemaining > 0 ) {
				raf.seek( entry.dataOffset + entry.dataSize - bytesRemaining );
				len = raf.read( buf, 0, (int)Math.min( buf.length, bytesRemaining ) );
				if ( len == -1 ) {
					throw new IOException( "EOF prematurely reached reading innerPath: "+  entry.innerPath );
				}

				os.write( buf, 0, len );
			}
		}

		@Override
		public void remove( String innerPath ) throws FileNotFoundException, IOException {
			if ( innerPath.indexOf( "\\" ) != -1 ) {
				throw new IllegalArgumentException( "InnerPath contains backslashes: "+ innerPath );
			}
			if ( !pathToIndexMap.containsKey( innerPath ) ) {
				throw new FileNotFoundException( "InnerPath does not exist: "+ innerPath );
			}

			int entryIndex = pathToIndexMap.get( innerPath ).intValue();
			pathToIndexMap.remove( innerPath );
			DatEntry removedEntry = entryList.set( entryIndex, null );

			raf.seek( getHeaderIndexPosition( entryIndex ) );
			writeLittleUInt( 0 );

			// If data was at the end, truncate.
			if ( removedEntry.dataOffset + removedEntry.dataSize == raf.length() ) {
				raf.setLength( removedEntry.entryOffset );
			}
		}

		@Override
		public boolean contains( String innerPath ) {
			if ( innerPath.indexOf( "\\" ) != -1 ) {
				throw new IllegalArgumentException( "InnerPath contains backslashes: "+ innerPath );
			}
			return pathToIndexMap.containsKey( innerPath );
		}

		@Override
		public InputStream getInputStream( String innerPath ) throws FileNotFoundException, IOException {
			if ( innerPath.indexOf( "\\" ) != -1 ) {
				throw new IllegalArgumentException( "InnerPath contains backslashes: "+ innerPath );
			}
			if ( !pathToIndexMap.containsKey( innerPath ) ) {
				throw new FileNotFoundException( "InnerPath does not exist: "+ innerPath );
			}

			int entryIndex = pathToIndexMap.get( innerPath ).intValue();
			DatEntry entry = entryList.get( entryIndex );

			// Create a stream that can only see this region.
			// Multiple read-only streams can coexist (each has its own position).
			InputStream stream = new FileChannelRegionInputStream( raf.getChannel(), entry.dataOffset, entry.dataSize );

			// Mapped regions may not garbage collect promptly.
			// That would keep the file in use: bad.
			// Closing raf doesn't affect them. :/
			// This method has best I/O performance though.
			//MappedByteBuffer buf = raf.getChannel().map( FileChannel.MapMode.READ_ONLY, entry.dataOffset, entry.dataSize );
			//buf.load();
			//InputStream stream = new ByteBufferBackedInputStream( buf );

			return stream;
		}

		@Override
		public void close() throws IOException {
			raf.close();
		}

		public List<DatEntry> listMetadata() {
			return new ArrayList<DatEntry>( entryList );
		}

		/**
		 * Repacks the dat file. This will remove gaps, which could
		 * be created when adding, removing or replacing files.
		 */
		public RepackResult repack() throws IOException {
			long bytesChanged = 0;

			int vacancyCount = Collections.frequency( entryList, null );

			// Build a list of non-null entries, sorted in the order their data appears.

			List<DatEntry> tmpEntries = new ArrayList<DatEntry>( entryList.size() - vacancyCount );
			for ( DatEntry entry : entryList ) {
				if ( entry != null ) tmpEntries.add( entry );
			}
			Collections.sort( tmpEntries, new DatEntryDataOffsetComparator() );

			for ( int i=0; i < tmpEntries.size()-1; i++ ) {
				DatEntry a = tmpEntries.get( i );
				DatEntry b = tmpEntries.get( i+1 );
				if ( a.dataOffset+a.dataSize > b.entryOffset ) {
					throw new IOException( String.format( "Cannot repack datfile with overlapping entries (\"%s\" and \"%s\")", a.innerPath, b.innerPath ) );
				}
			}

			pathToIndexMap.clear();  // entryList gets replaced later.

			// Write the header size.
			if ( tmpEntries.size() != entryList.size() ) {
				raf.seek( 0 );
				writeLittleUInt( tmpEntries.size() );
				bytesChanged += 4;
			}

			long pendingEntryOffset = getHeaderIndexPosition( tmpEntries.size() );

			for ( int i=0; i < tmpEntries.size(); i++ ) {
				DatEntry entry = tmpEntries.get ( i );
				pathToIndexMap.put( entry.innerPath, new Integer( i ) );

				// Write the header index.
				raf.seek( getHeaderIndexPosition( i ) );
				writeLittleUInt( pendingEntryOffset );
				bytesChanged += 4;

				// Shift the entry toward the start of the dat.
				if ( pendingEntryOffset != entry.entryOffset ) {
					long totalBytes = (entry.dataOffset-entry.entryOffset) + entry.dataSize;
					long bytesRemaining = totalBytes;
					byte[] buf = new byte[4096];
					int len;
					while ( bytesRemaining > 0 ) {
						raf.seek( entry.entryOffset + totalBytes - bytesRemaining );
						len = raf.read( buf, 0, (int)Math.min( buf.length, bytesRemaining ) );
						if ( len == -1 ) {
							throw new IOException( "EOF prematurely reached reading innerPath: "+ entry.innerPath );
						}

						raf.seek( pendingEntryOffset + totalBytes - bytesRemaining );
						raf.write( buf, 0, len );
						bytesRemaining -= len;
					}

					entry.dataOffset = pendingEntryOffset + (entry.dataOffset-entry.entryOffset);
					entry.entryOffset = pendingEntryOffset;
					bytesChanged += totalBytes;
				}

				pendingEntryOffset += (entry.dataOffset-entry.entryOffset) + entry.dataSize;
			}

			entryList = tmpEntries;

			long oldDatLength = raf.length();
			long newDatLength = pendingEntryOffset;
			raf.setLength( newDatLength );  // Trim off deallocated bytes at the end.

			return new RepackResult( oldDatLength, newDatLength, bytesChanged );
		}
	}



	/**
	 * A Comparator to sort by dataOffset (asc).
	 */
	public static class DatEntryDataOffsetComparator implements Comparator<DatEntry> {
		@Override
		public int compare( DatEntry a, DatEntry b ) {
			if ( b == null ) return -1;
			if ( a == null ) return 1;
			if ( a.entryOffset < b.entryOffset ) return -1;
			if ( a.entryOffset > b.entryOffset ) return 1;
			return 0;
		}
		@Override
		public boolean equals( Object o ) {
			return ( o != null ? o == this : false );
		}
	}
}
