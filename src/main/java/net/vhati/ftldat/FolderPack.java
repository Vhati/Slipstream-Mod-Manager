package net.vhati.ftldat;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import net.vhati.ftldat.AbstractPack;
import net.vhati.ftldat.AbstractPack.PathAndSize;
import net.vhati.ftldat.PackUtilities;


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
public class FolderPack extends AbstractPack {

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
					pendingPaths.push( PackUtilities.ftlPathJoin(current, childName) );
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
		if ( innerPath.contains( "\\" ) ) {
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
