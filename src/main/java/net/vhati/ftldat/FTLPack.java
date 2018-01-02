package net.vhati.ftldat;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
//import java.nio.MappedByteBuffer;      // For Memory-mapped streams
//import java.nio.channels.FileChannel;  // For Memory-mapped streams
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.vhati.ftldat.AbstractPack;
import net.vhati.ftldat.AbstractPack.PathAndSize;
import net.vhati.ftldat.AbstractPack.RepackResult;
import net.vhati.ftldat.FileChannelRegionInputStream;


/**
 * The original format FTL used to store resources.
 *
 * Structure:
 *   Index = A count, followed by a series of offsets to entries.
 *   Entries = A series of {dataSize, innerPath, data} hunks.
 */
public class FTLPack extends AbstractPack {

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
			pathToIndexMap.put( entry.innerPath, i );
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
		if ( innerPath.contains( "\\" ) ) {
			throw new IllegalArgumentException( "InnerPath contains backslashes: "+ innerPath );
		}
		if ( pathToIndexMap.containsKey( innerPath ) ) {
			throw new IOException( "InnerPath already exists: "+ innerPath );
		}
		if ( !asciiEncoder.reset().canEncode( innerPath ) ) {
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
		if ( innerPath.contains( "\\" ) ) {
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
		if ( innerPath.contains( "\\" ) ) {
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
		if ( innerPath.contains( "\\" ) ) {
			throw new IllegalArgumentException( "InnerPath contains backslashes: "+ innerPath );
		}
		return pathToIndexMap.containsKey( innerPath );
	}

	@Override
	public InputStream getInputStream( String innerPath ) throws FileNotFoundException, IOException {
		if ( innerPath.contains( "\\" ) ) {
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
	@Override
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
			pathToIndexMap.put( entry.innerPath, i );

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



	/**
	 * Information about an innerFile within a dat.
	 *
	 * entryOffset = Offset (written in header) to
	 *               the dataSize + innerPath + data.
	 * innerPath   = A virtual location ("dir/dir/filename").
	 * dataOffset  = Offset to the innerFile.
	 * dataSize    = Size of the innerFile.
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
