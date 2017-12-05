package net.vhati.ftldat;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;


public class PackUtilities {

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
			result = PackUtilities.calcStreamMD5( is );
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
}
