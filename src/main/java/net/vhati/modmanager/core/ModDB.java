package net.vhati.modmanager.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import net.vhati.modmanager.core.ModInfo;


public class ModDB {

	// Accociates Forum thread urls with hashes of their forst post's content.
	private HashMap<String,String> threadHashMap = new HashMap<String,String>();

	private List<ModInfo> catalog = new ArrayList<ModInfo>();


	/**
	 * Returns mod info for a given file hash.
	 */
	public ModInfo getModInfo( String hash ) {
		if ( hash == null ) return null;

		for ( ModInfo modInfo : catalog ) {
			if ( modInfo.getFileHash().equals(hash) ) {
				return modInfo;
			}
		}
		return null;
	}

	public void addMod( ModInfo modInfo ) {
		catalog.add( modInfo );
	}

	public void removeMod( ModInfo modInfo ) {
		catalog.remove( modInfo );
	}

	/**
	 * Stores the first-post content hash of a forum thread.
	 */
	public void putThreadHash( String url, String threadHash ) {
		threadHashMap.put( url, threadHash );
	}

	public String getThreadHash( String url ) {
		return threadHashMap.get( url );
	}

	public void clear() {
		threadHashMap.clear();
		catalog.clear();
	}

	/**
	 * Returns the internal ArrayList of mod info.
	 */
	public List<ModInfo> getCatalog() {
		return catalog;
	}
}
