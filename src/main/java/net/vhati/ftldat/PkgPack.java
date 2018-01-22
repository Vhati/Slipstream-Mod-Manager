package net.vhati.ftldat;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.DeflaterInputStream;
import java.util.zip.InflaterInputStream;

import net.vhati.ftldat.AbstractPack;
import net.vhati.ftldat.AbstractPack.PathAndSize;
import net.vhati.ftldat.AbstractPack.RepackResult;
import net.vhati.ftldat.FileChannelRegionInputStream;
import net.vhati.ftldat.MeteredInputStream;


/**
 * A PKG format file.
 *
 * Structure:
 *   PKGHeader = The "PKG\n" signature and a few numbers.
 *   PKGIndexEntry = A series of entry hunks describing nested files.
 *   Paths region = A series of null-terminated entries' innerPath strings.
 *   A series of entries' data.
 *
 * See SIL: System Interface Library for games
 * http://achurch.org/SIL/
 * http://achurch.org/SIL/current/src/resource/
 *
 * Note: Although it seems the PKG format technically allows for huge
 * lists with unsigned int indeces, this implementation casts values into
 * signed ints to avoid needing exotic collections. FTL won't exceed 2 billion
 * files! Even SIL does it in "package-pkg.c".
 *
 * This was introduced in FTL 1.6.1.
 */
public class PkgPack extends AbstractPack {

	/** Bitmask flag for "deflate" compression. */
	private static final long PKGF_DEFLATED = 1 << 24;

	/** Fixed byte count of PKG headers. */
	private static final int HEADER_SIZE = 16;

	/** Fixed byte count of PKG entries. */
	private static final int ENTRY_SIZE = 20;

	/** Byte count to pre-allocate per innerPath in newly created dats. */
	private static final int TYPICAL_PATH_LENGTH = 70;

	private final int[] signature = new int[] {0x50, 0x4B, 0x47, 0x0A};  // "PKG\n"

	private CharsetEncoder asciiEncoder = Charset.forName( "US-ASCII" ).newEncoder();

	private ByteBuffer bigByteBuf = null;
	private ByteBuffer smallByteBuf = null;

	private File datFile = null;
	private RandomAccessFile raf = null;
	private List<PkgEntry> entryList = null;
	private Map<String, Integer> pathToIndexMap = null;

	private int pathsRegionSize = 0;
	private boolean compressNewAdditions = false;


	/**
	 * Opens or creates a dat in various modes.
	 * When creating, the initial index size will be 2048.
	 *
	 * @see FTLPack(File datFile, String mode, int indexSize)
	 */
	public PkgPack( File datFile, String mode ) throws IOException {
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
	 * @param entryCount size of the initial index if creating
	 */
	public PkgPack( File datFile, String mode, int entryCount ) throws IOException {
		bigByteBuf = ByteBuffer.allocate( TYPICAL_PATH_LENGTH * 3000 );  // Arbitrary default.

		// A reusable buffer large enough for the unsigned read methods.
		smallByteBuf = ByteBuffer.allocate( 4 );  // Defaults to BIG_ENDIAN.

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
			createIndex( entryCount );
		}
		else {
			throw new IllegalArgumentException( String.format( "FTLPack constructor's mode arg was not 'r', 'r+', or 'w+' (%s).", mode ) );
		}
	}

	/**
	 * Toggles whether subsequent add() calls should compress data.
	 */
	public void setCompressNewAdditions( boolean b ) {
		compressNewAdditions = b;
	}

	/**
	 * Calculates a PKG hash of a path.
	 *
	 * To print as hex: String.format( "0x%08X", hash ).
	 *
	 * Original description:
	 *
	 * "For each byte, rotate the hash value right 5 bits and XOR with the
	 * byte value (with uppercase alphabetic characters converted to
	 * lowercase). This is reasonably fast, and seems to produce good
	 * hash value distribution with real data sets."
	 *
	 * See SIL's "package-pkg.h:pkg_hash()".
	 * http://achurch.org/SIL/current/src/resource/package-pkg.h
	 *
	 * @param innerPath an ASCII string
	 */
	public long calculatePathHash( String innerPath ) {
		// Casting a character to int emulates ord(), to get the numeric ASCII value.
		int len = innerPath.length();
		long hash = 0;
		if ( innerPath != null ) {
			for ( int i=0; i < len; i++ ) {
				long n = (int)Character.toLowerCase( innerPath.charAt( i ) );
				hash = hash << 27 | hash >>> 5;
				hash ^= n;
				hash = hash & 0x00000000FFFFFFFFL;  // Mask off bits that spilled left of a 32bit uint.
			}
		}
		return hash;
	}

	/**
	 * Clears the big ByteBuffer for reuse, or allocates a new one of larger size.
	 *
	 * If reused, content will not be erased.
	 * A newly allocated buffer will have minCapacity or more.
	 * Either way, treat this as a new buffer (set limit, byte order, etc).
	 */
	private boolean recycleBigByteBuffer( int minCapacity ) {
		if ( bigByteBuf.capacity() >= minCapacity ) {
			bigByteBuf.clear();
			return true;
		}

		int newCapacity = 0;
		int incrementalCapacity = bigByteBuf.capacity() + bigByteBuf.capacity()/2;
		if ( incrementalCapacity >= minCapacity ) {
			newCapacity = incrementalCapacity;
		} else {
			newCapacity = minCapacity;
		}
		bigByteBuf = ByteBuffer.allocate( newCapacity );
		return false;
	}


	/**
	 * Reads a big-endian unsigned int.
	 *
	 * Java doesn't have an unsigned int primitive,
	 * so a long holds the value instead.
	 */
	private long readBigUInt() throws IOException {
		smallByteBuf.clear();
		raf.readFully( smallByteBuf.array(), 0, 4 );

		// Read a signed int, then discard sign
		// by casting to long and hacking off bits.
		long result = smallByteBuf.getInt( 0 );
		result &= 0x00000000FFFFFFFFL;

		return result;
	}

	private void writeBigUInt( long n ) throws IOException {
		smallByteBuf.clear();

		// Write a signed int, after discarding sign
		// by casting from long and hacking off bits.
		smallByteBuf.putInt( 0, (int)(n & 0x00000000FFFFFFFFL) );

		raf.write( smallByteBuf.array(), 0, 4 );
	}

	/**
	 * Reads a big-endian unsigned short.
	 *
	 * Java doesn't have an unsigned short primitive,
	 * so an int holds the value instead.
	 */
	private int readBigUShort() throws IOException {
		smallByteBuf.clear();
		raf.readFully( smallByteBuf.array(), 0, 2 );

		// Read a signed short, then discard sign
		// by casting to int and hacking off bits.
		int result = smallByteBuf.getShort( 0 );
		result &= 0x0000FFFF;

		return result;
	}

	private void writeBigUShort( int n ) throws IOException {
		smallByteBuf.clear();

		// Write a signed short, after discarding sign
		// by casting from int and hacking off bits.
		smallByteBuf.putShort( 0, (short)(n & 0x0000FFFF) );

		raf.write( smallByteBuf.array(), 0, 2 );
	}

	/**
	 * Returns a null terminated string of ASCII bytes.
	 *
	 * Reading begins at the buffer's current position.
	 * The buffer's limit is honored.
	 */
	private String readNullTerminatedString( ByteBuffer srcBuf ) throws IOException {
		StringBuilder result = new StringBuilder();

		while ( srcBuf.hasRemaining() ) {
			char c = (char)srcBuf.get();

			if ( c == '\0' ) break;
			if ( !asciiEncoder.reset().canEncode( c ) ) {
				throw new IOException( String.format( "Unexpected non-ASCII char in null-terminated string: %X", c ) );
			}

			result.append( c );
		}
		return result.toString();
	}

	private int writeNullTerminatedString( ByteBuffer dstBuf, CharSequence s ) throws IOException {
		if ( !asciiEncoder.reset().canEncode( s ) ) {
			throw new IllegalArgumentException( "The PKG format does not support non-ascii characters: "+ s );
		}

		int start = dstBuf.position();
		CharBuffer cBuf = CharBuffer.wrap( s );
		asciiEncoder.reset();
		CoderResult r = asciiEncoder.encode( cBuf, dstBuf, true );
		if ( r.isOverflow() ) {
			throw new IOException( "Buffer overflow while encoding string: "+ s );
		}
		asciiEncoder.flush( dstBuf );
		if ( r.isOverflow() ) {
			throw new IOException( "Buffer overflow while encoding string: "+ s );
		}
		dstBuf.put( (byte)0 );

		return dstBuf.position() - start;
	}

	private void writePkgEntry( PkgEntry entry ) throws IOException {
		if ( entry == null ) {
			writeBigUInt( 0 );  // Hash.
			writeBigUInt( 0 );  // pathOffsetAndFlags.
			writeBigUInt( 0 );  // dataOffset.
			writeBigUInt( 0 );  // dataSize.
			writeBigUInt( 0 );  // unpackedSize.
		}
		else {
			long pathOffsetAndFlags = entry.innerPathOffset;
			if ( entry.dataDeflated ) {
				pathOffsetAndFlags |= PKGF_DEFLATED;
			}

			writeBigUInt( entry.innerPathHash );  // Hash.
			writeBigUInt( pathOffsetAndFlags );   // pathOffsetAndFlags.
			writeBigUInt( entry.dataOffset );	  // dataOffset.
			writeBigUInt( entry.dataSize );       // dataSize.
			writeBigUInt( entry.unpackedSize );   // unpackedSize.
		}
	}

	/**
	 * Returns the entry with the lowest dataOffset, or null.
	 *
	 * When null is returned, newly added data should be written at the end of
	 * the file.
	 */
	private PkgEntry getEntryWithEarliestData() {
		PkgEntry result = null;
		for ( PkgEntry entry : entryList ) {
			if ( entry != null && (result == null || entry.dataOffset < result.dataOffset) ) {
				result = entry;
			}
		}
		return result;
	}

	/**
	 * Returns the offset, within the paths region, where the next innerPath
	 * would be written.
	 *
	 * This will be after the last innerPath's null-terminated string.
	 */
	private int getNextInnerPathOffset() {
		int result = 0;
		PkgEntry foundEntry = null;
		for ( PkgEntry entry : entryList ) {
			if ( entry != null && (foundEntry == null || entry.innerPathOffset > foundEntry.innerPathOffset) ) {
				foundEntry = entry;
			}
		}
		if ( foundEntry != null ) {
			result = foundEntry.innerPathOffset + foundEntry.innerPath.length() + 1; // Null termination.
		}

		return result;
	}

	private void createIndex( int entryCount ) throws IOException {
		pathsRegionSize = 0;

		entryList = new ArrayList<PkgEntry>( entryCount );

		pathToIndexMap = new HashMap<String, Integer>( entryCount );

		raf.seek( 0 );
		raf.setLength( 0 );
		for ( int x : signature ) {
			raf.writeByte( x );
		}
		writeBigUShort( HEADER_SIZE );
		writeBigUShort( ENTRY_SIZE );
		writeBigUInt( 0 );  // entryCount.
		writeBigUInt( pathsRegionSize );

		growIndex( entryCount );
	}

	private void readIndex()  throws IOException {
		raf.seek( 0 );

		// Check the file signature.
		for ( int x : signature ) {
			if ( raf.readUnsignedByte() != x ) {
				throw new IOException( "Unexpected file signature" );
			}
		}

		// Other header values.
		int headerSize = readBigUShort();
		if ( headerSize != HEADER_SIZE ) {
			throw new IOException( String.format( "Corrupt dat file (%s): header claims header size is %d bytes (expected %d)", getName(), headerSize, HEADER_SIZE ) );
		}
		int entrySize = readBigUShort();
		if ( entrySize != ENTRY_SIZE ) {
			throw new IOException( String.format( "Corrupt dat file (%s): header claims entries are %d bytes (expected %d)", getName(), entrySize, ENTRY_SIZE ) );
		}
		int entryCount = (int)readBigUInt();   // Risky casting to signed.
		if ( entryCount * entrySize > raf.length() ) {
			throw new IOException( String.format( "Corrupt dat file (%s): header claims entries combined are larger than the entire file", getName() ) );
		}
		pathsRegionSize = (int)readBigUInt();  // Risky casting to signed.
		if ( pathsRegionSize > raf.length() ) {
			throw new IOException( String.format( "Corrupt dat file (%s): header claims path strings are larger than the entire file", getName() ) );
		}

		entryList = new ArrayList<PkgEntry>( entryCount );
		for ( int i=0; i < entryCount; i++ ) {
			PkgEntry entry = new PkgEntry();
			entry.innerPathHash = readBigUInt();

			// Top 8 bits of the path offset field were set aside to store flags.
			// 0x00FFFFFF == 0000 0000:1111 1111 1111 1111 1111 1111 (8:24 bits).
			// 1 << 24    == 0000 0001:0000 0000 0000 0000 0000 0000
			long pathOffsetAndFlags = readBigUInt();
			entry.innerPathOffset = (int)(pathOffsetAndFlags & 0x00FFFFFFL);
			entry.dataDeflated = ((pathOffsetAndFlags & PKGF_DEFLATED) != 0);

			entry.dataOffset = readBigUInt();
			entry.dataSize = readBigUInt();
			entry.unpackedSize = readBigUInt();

			if ( entry.dataOffset == 0 ) {  // Null entry, dat wasn't repacked.
				entryList.add( null );
			} else {
				entryList.add( entry );
			}
		}

		pathToIndexMap = new HashMap<String, Integer>( entryCount );

		recycleBigByteBuffer( pathsRegionSize );
		bigByteBuf.limit( pathsRegionSize );
		raf.readFully( bigByteBuf.array(), 0, pathsRegionSize );

		for ( int i=0; i < entryCount; i++ ) {
			PkgEntry entry = entryList.get( i );
			if ( entry == null ) continue;

			bigByteBuf.position( entry.innerPathOffset );
			entry.innerPath = readNullTerminatedString( bigByteBuf );

			pathToIndexMap.put( entry.innerPath, i );
		}
	}

	/**
	 * Moves an entry's data to the end of the file.
	 *
	 * Its position within the entryList and it's innerPath within the
	 * paths region will remain unchanged.
	 *
	 * After returning, if this was the earliest dataOffset, there will be a
	 * gap between the paths region and the new earliest data.
	 */
	private void moveEntryDataToEOF( PkgEntry entry ) throws IOException {
		long oldOffset = entry.dataOffset;
		long newOffset = raf.length();

		long totalBytes = entry.dataSize;
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
		// Update the entry.
		entry.dataOffset = newOffset;
		raf.seek( HEADER_SIZE + entryList.indexOf( entry ) * ENTRY_SIZE + 4 + 4 );  // Skip hash and pathOffsetAndFlags.
		writeBigUInt( entry.dataOffset );
	}

	/**
	 * Ensures the index has room for at least n more entries.
	 *
	 * This is done by moving the first innerFile after the index
	 * to the end of the file. The region it used to occupy can then
	 * be filled with additional indeces.
	 */
	private void growIndex( int amount ) throws IOException {
		long neededEntriesGrowth = amount * ENTRY_SIZE;
		int neededPathsRegionGrowth = amount * TYPICAL_PATH_LENGTH;

		// Where to start writing grown entries - after existing ones.
		long firstGrowthEntryOffset = HEADER_SIZE + entryList.size() * ENTRY_SIZE;

		// Where the paths region will be - after the grown entries.
		long neededPathsRegionOffset = firstGrowthEntryOffset + neededEntriesGrowth;
		int neededPathsRegionSize = pathsRegionSize + neededPathsRegionGrowth;

		// Move data at least this far down.
		long neededMinDataOffset = neededPathsRegionOffset + neededPathsRegionSize;

		// If there's data, move it out of the way, to EOF.

		// Even if all entries are 0-sized, ensure that they move.
		if ( neededMinDataOffset > raf.length() ) raf.setLength( neededMinDataOffset );

		PkgEntry earliestDataEntry = getEntryWithEarliestData();

		while ( earliestDataEntry != null && neededMinDataOffset > earliestDataEntry.dataOffset ) {
			moveEntryDataToEOF( earliestDataEntry );

			earliestDataEntry = getEntryWithEarliestData();  // What's earliest now?
		}
		// Don't bother accepting the excess growth. Just leave a gap after the paths region.

		// Allocate a buffer with the needed size.
		// Partially fill with current bytes
		// Fill the needed remainder with 0's.
		// Write it all back, a little farther down in the file.

		recycleBigByteBuffer( neededPathsRegionSize );
		bigByteBuf.limit( neededPathsRegionSize );

		raf.readFully( bigByteBuf.array(), 0, pathsRegionSize );
		Arrays.fill( bigByteBuf.array(), pathsRegionSize+1, neededPathsRegionSize, (byte)0 );
		bigByteBuf.rewind();  // The backing array was modified directly, so this is a NOP.

		raf.seek( neededPathsRegionOffset );  // Seeking past EOF is okay; write() will grow the file.
		raf.write( bigByteBuf.array(), bigByteBuf.position(), bigByteBuf.limit() );

		pathsRegionSize = neededPathsRegionSize;

		// Add/write the grown entries.
		for ( int i=0; i < amount; i++ ) {
			entryList.add( null );
		}
		raf.seek( firstGrowthEntryOffset );
		for ( int i=0; i < amount; i++ ) {
			writePkgEntry( null );
		}

		// Update the header.
		raf.seek( signature.length + 2 + 2 );  // Skip HEADER_SIZE and ENTRY_SIZE.
		writeBigUInt( entryList.size() );
		writeBigUInt( pathsRegionSize );
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
		for ( PkgEntry entry : entryList ) {
			if ( entry == null ) continue;
			PathAndSize pas = new PathAndSize( entry.innerPath, entry.dataSize );
			result.add( pas );
		}
		return result;
	}

	/**
	 * Adds bytes read from an InputStream to the pack, as innerPath.
	 */
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

		// Make room for the innerPath null-terminated string.
		int innerPathOffset = getNextInnerPathOffset();
		while ( innerPathOffset + innerPath.length() + 1  > pathsRegionSize ) {
			growIndex( 50 );
		}

		PkgEntry entry = new PkgEntry();
		entry.innerPathOffset = 0;  // Write this later.
		entry.innerPath = innerPath;
		entry.innerPathHash = calculatePathHash( innerPath );
		entry.dataOffset = raf.length();
		entry.dataSize = 0;         // Write this later.
		entry.unpackedSize = 0;     // Write this later.
		entry.dataDeflated = compressNewAdditions;

		MeteredInputStream srcMeterStream = new MeteredInputStream( is );
		InputStream dataStream = srcMeterStream;

		if ( compressNewAdditions ) {
			dataStream = new DeflaterInputStream( dataStream );
		}

		// Write data.
		raf.seek( entry.dataOffset );
		byte[] buf = new byte[4096];
		int len;
		while ( (len = dataStream.read( buf )) >= 0 ) {
			raf.write( buf, 0, len );
		}

		// Attempting to close the wrapper streams would cause an exception if
		// the original stream was a ZipInputStream, which would need closeEntry().

		// TODO: Test if compression works without closing the wrapper.

		// Go back and fill in the dataSize.
		entry.dataSize = raf.getChannel().position() - entry.dataOffset;
		entry.unpackedSize = srcMeterStream.getCount();

		// Write the innerPath string.
		recycleBigByteBuffer( innerPath.length() + 1 );
		bigByteBuf.limit( innerPath.length() + 1 );
		writeNullTerminatedString( bigByteBuf, innerPath );
		bigByteBuf.rewind();
		raf.seek( innerPathOffset );
		raf.write( bigByteBuf.array(), bigByteBuf.position(), bigByteBuf.limit() );

		entryList.set( entryIndex, entry );
		pathToIndexMap.put( innerPath, entryIndex );

		// Write the entry itself.
		raf.seek( HEADER_SIZE + entryIndex * ENTRY_SIZE );
		writePkgEntry( entry );
	}

	@Override
	public void extractTo( String innerPath, OutputStream os ) throws FileNotFoundException, IOException {
		InputStream is = null;

		try {
			is = getInputStream( innerPath );

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
	public void remove( String innerPath ) throws FileNotFoundException, IOException {
		if ( innerPath.contains( "\\" ) ) {
			throw new IllegalArgumentException( "InnerPath contains backslashes: "+ innerPath );
		}
		if ( !pathToIndexMap.containsKey( innerPath ) ) {
			throw new FileNotFoundException( "InnerPath does not exist: "+ innerPath );
		}

		int entryIndex = pathToIndexMap.get( innerPath ).intValue();
		pathToIndexMap.remove( innerPath );
		PkgEntry removedEntry = entryList.set( entryIndex, null );

		raf.seek( HEADER_SIZE + entryIndex * ENTRY_SIZE );
		writePkgEntry( null );

		// If data was at the end, truncate.
		if ( removedEntry.dataOffset + removedEntry.dataSize == raf.length() ) {
			raf.setLength( removedEntry.dataOffset );
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
		PkgEntry entry = entryList.get( entryIndex );

		// Create a stream that can only see this region.
		// Multiple read-only streams can coexist (each has its own position).
		InputStream stream = new FileChannelRegionInputStream( raf.getChannel(), entry.dataOffset, entry.dataSize );

		if ( entry.dataDeflated ) {
			stream = new InflaterInputStream( stream );
		}

		return stream;
	}

	@Override
	public void close() throws IOException {
		raf.close();
	}

	public List<PkgEntry> listMetadata() {
		return new ArrayList<PkgEntry>( entryList );
	}

	/**
	 * Repacks the dat file. This will remove gaps, which could
	 * be created when adding, removing or replacing files.
	 *
	 * Entries will be sorted by innerPathHash (then innerPath, ignoring case).
	 *
	 * Null entries will be omitted.
	 *
	 * All innerPaths will be rewritten to the paths region, sorted by
	 * dataOffset.
	 */
	@Override
	public RepackResult repack() throws IOException {
		long bytesChanged = 0;

		int vacancyCount = Collections.frequency( entryList, null );

		// Build a list of non-null entries, sorted in the order their data appears.

		List<PkgEntry> tmpEntries = new ArrayList<PkgEntry>( entryList.size() - vacancyCount );
		for ( PkgEntry entry : entryList ) {
			if ( entry != null ) tmpEntries.add( entry );
		}
		Collections.sort( tmpEntries, new PkgEntryDataOffsetComparator() );

		for ( int i=0; i < tmpEntries.size()-1; i++ ) {
			PkgEntry a = tmpEntries.get( i );
			PkgEntry b = tmpEntries.get( i+1 );
			if ( a.dataOffset+a.dataSize > b.dataOffset ) {
				throw new IOException( String.format( "Cannot repack datfile with overlapping entries (\"%s\" and \"%s\")", a.innerPath, b.innerPath ) );
			}
		}

		// Determine the paths region size.
		// If any non-null entries somehow shared an innerPathOffset, this will
		// make them distinct.
		int neededPathsRegionSize = 0;
		for ( PkgEntry entry : tmpEntries ) {
			neededPathsRegionSize += entry.innerPath.length() + 1;
		}

		long neededPathsRegionOffset = HEADER_SIZE + tmpEntries.size() * ENTRY_SIZE;
		long neededMinDataOffset = neededPathsRegionOffset + neededPathsRegionSize;

		// If there's data, move it out of the way, to EOF.
		if ( !tmpEntries.isEmpty() ) {
			PkgEntry earliestDataEntry = tmpEntries.get( 0 );

			// Even if all entries are 0-sized, ensure that they move.
			if ( neededMinDataOffset > raf.length() ) {
				bytesChanged += neededMinDataOffset - raf.length();
				raf.setLength( neededMinDataOffset );
			}

			while ( neededMinDataOffset > earliestDataEntry.dataOffset ) {
				moveEntryDataToEOF( earliestDataEntry );
				bytesChanged += earliestDataEntry.dataSize;

				tmpEntries.remove( 0 );  // Move the entry to the end of the sorted list.
				tmpEntries.add( earliestDataEntry );

				earliestDataEntry = tmpEntries.get( 0 );  // What's earliest now?
			}
		}

		// Write innerPath strings to paths region.
		recycleBigByteBuffer( neededPathsRegionSize );
		bigByteBuf.limit( neededPathsRegionSize );
		for ( PkgEntry entry : tmpEntries ) {
			entry.innerPathOffset = bigByteBuf.position();
			writeNullTerminatedString( bigByteBuf, entry.innerPath );
		}
		bigByteBuf.rewind();
		raf.seek( neededPathsRegionOffset );
		raf.write( bigByteBuf.array(), bigByteBuf.position(), bigByteBuf.limit() );

		pathsRegionSize = neededPathsRegionSize;

		// Move data toward the top.
		long pendingDataOffset = neededMinDataOffset;

		for ( PkgEntry entry : tmpEntries ) {

			if ( pendingDataOffset != entry.dataOffset ) {
				long totalBytes = entry.dataSize;
				long bytesRemaining = totalBytes;
				byte[] buf = new byte[4096];
				int len;
				while ( bytesRemaining > 0 ) {
					raf.seek( entry.dataOffset + totalBytes - bytesRemaining );
					len = raf.read( buf, 0, (int)Math.min( buf.length, bytesRemaining ) );
					if ( len == -1 ) {
						throw new IOException( "EOF prematurely reached reading innerPath: "+ entry.innerPath );
					}

					raf.seek( pendingDataOffset + totalBytes - bytesRemaining );
					raf.write( buf, 0, len );
					bytesRemaining -= len;
				}

				entry.dataOffset = pendingDataOffset;
				bytesChanged += totalBytes;
			}

			pendingDataOffset += entry.dataSize;
		}

		// Re-sort entries, this time by hash.
		Collections.sort( tmpEntries, new PkgEntryHashComparator() );
		entryList = tmpEntries;

		pathToIndexMap.clear();
		for ( PkgEntry entry : entryList ) {
			pathToIndexMap.put( entry.innerPath, pathToIndexMap.size() );
		}

		// Update the header.
		raf.seek( signature.length + 2 + 2 );  // Skip HEADER_SIZE and ENTRY_SIZE.
		writeBigUInt( entryList.size() );
		writeBigUInt( pathsRegionSize );
		bytesChanged += 4 + 4;

		// Write the entries.
		for ( PkgEntry entry : entryList ) {
			writePkgEntry( entry );
		}

		long oldDatLength = raf.length();
		long newDatLength = pendingDataOffset;
		raf.setLength( newDatLength );  // Trim off deallocated bytes at the end.

		return new RepackResult( oldDatLength, newDatLength, bytesChanged );
	}



	/**
	 * Information about an innerFile within a dat.
	 */
	public static class PkgEntry {
		/** Offset to read a null-terminated string from the dat's paths blob. */
		public int innerPathOffset = 0;

		/** A forward slash delimited ASCII path, with no leading slash. */
		public String innerPath = null;

		/**
		 * A precalculated hash of the innerPath string.
		 * @see #calculatePathHash(String)
		 */
		public long innerPathHash = 0;

		/** Offset to read the first byte of packed data. */
		public long dataOffset = 0;

		/** Length of packed data. */
		public long dataSize = 0;

		/** Expected length of data once unpacked. */
		public long unpackedSize = 0;

		/** Whether the packed data is "deflate" ompressed. */
		public boolean dataDeflated = false;

		public PkgEntry() {
		}
	}



	/**
	 * A Comparator to sort by innerPathHash (asc), then by innerPath (asc) ignoring case.
	 */
	public static class PkgEntryHashComparator implements Comparator<PkgEntry> {
		@Override
		public int compare( PkgEntry a, PkgEntry b ) {
			if ( b == null ) return -1;
			if ( a == null ) return 1;
			if ( a.innerPathHash < b.innerPathHash ) return -1;
			if ( a.innerPathHash > b.innerPathHash ) return 1;
			return a.innerPath.compareToIgnoreCase( b.innerPath );
		}
		@Override
		public boolean equals( Object o ) {
			return ( o != null ? o == this : false );
		}
	}

	/**
	 * A Comparator to sort by dataOffset (asc).
	 */
	public static class PkgEntryDataOffsetComparator implements Comparator<PkgEntry> {
		@Override
		public int compare( PkgEntry a, PkgEntry b ) {
			if ( b == null ) return -1;
			if ( a == null ) return 1;
			if ( a.dataOffset < b.dataOffset ) return -1;
			if ( a.dataOffset > b.dataOffset ) return 1;
			return 0;
		}
		@Override
		public boolean equals( Object o ) {
			return ( o != null ? o == this : false );
		}
	}
}
