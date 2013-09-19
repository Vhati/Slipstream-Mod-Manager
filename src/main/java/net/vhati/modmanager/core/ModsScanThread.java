package net.vhati.modmanager.core;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.vhati.ftldat.FTLDat;
import net.vhati.modmanager.core.ModDB;
import net.vhati.modmanager.core.ModInfo;
import net.vhati.modmanager.core.ModsScanObserver;
import net.vhati.modmanager.xml.JDOMModMetadataReader;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


/**
 * A thread to calculate MD5 hashes of files in the background.
 *
 * As each file is hashed, a class implementing HashObserver is notified.
 * Note: The callback on that class needs to be thread-safe.
 */
public class ModsScanThread extends Thread {

	private static final Logger log = LogManager.getLogger(ModsScanThread.class);

	private List<File> fileList = new ArrayList<File>();
	private ModDB newDB;
	private ModsScanObserver scanObserver;


	public ModsScanThread( File[] files, ModDB knownDB, ModsScanObserver scanObserver ) {
		this.fileList.addAll( Arrays.asList(files) );
		this.newDB = new ModDB( knownDB );
		this.scanObserver = scanObserver;
	}


	public void run() {
		Map<File,String> hashMap = new HashMap<File,String>();

		for ( File f : fileList ) {
			String hash = calcFileMD5( f );
			if ( hash != null ) {
				hashMap.put( f, hash );
				scanObserver.hashCalculated( f, hash );
			}
		}
		log.info( "Background hashing finished." );

		// Cache info about new files.
		for ( File f : fileList ) {
			String fileHash = hashMap.get( f );

			if ( fileHash != null && newDB.getModInfo( fileHash ) == null ) {
				ModInfo modInfo = JDOMModMetadataReader.parseModFile( f );
				if ( modInfo != null ) {
					modInfo.setFileHash( fileHash );
					newDB.addMod( modInfo );
				}
			}
		}
		// Prune info about absent files.
		for ( Iterator<ModInfo> it = newDB.getCatalog().iterator(); it.hasNext(); ) {
			ModInfo modInfo = it.next();
			if ( !hashMap.containsValue( modInfo.getFileHash() ) )
				it.remove();
		}
		scanObserver.localModDBUpdated( new ModDB( newDB ) );
		log.info( "Background metadata caching finished." );

		scanObserver.modsScanEnded();
	}


	private String calcFileMD5( File f ) {
		String result = null;
		try {
			result = FTLDat.calcFileMD5( f );
		}
		catch ( Exception e ) {
			log.error( "Error while calculating hash for file: "+ f.getPath(), e );
		}
		return result;
	}
}
