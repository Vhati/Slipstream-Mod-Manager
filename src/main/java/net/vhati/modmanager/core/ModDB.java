package net.vhati.modmanager.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.vhati.modmanager.core.ModInfo;
import net.vhati.modmanager.core.ModsInfo;


public class ModDB {

	public static final String EXACT = "exact";
	public static final String FUZZY = "fuzzy";


	private HashMap<String,String> threadHashMap = new HashMap<String,String>();

	private List<ModInfo> catalog = new ArrayList<ModInfo>();


	public ModDB() {
	}

	/**
	 * Constructs a shallow copy of an existing ModDB.
	 *
	 * Different catalog list, same ModInfos.
	 */
	public ModDB( ModDB srcDB ) {
		threadHashMap.putAll( srcDB.getThreadHashMap() );
		catalog.addAll( srcDB.getCatalog() );
	}


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
	 * Returns the internal Map of forum thread urls and hashes of their first posts' content.
	 */
	public Map<String,String> getThreadHashMap() {
		return threadHashMap;
	}

	/**
	 * Returns the internal List of mod info.
	 */
	public List<ModInfo> getCatalog() {
		return catalog;
	}


	/**
	 * Returns ModInfos that are likely revisions in the same series.
	 *
	 * The searched item will appear in the results as well.
	 *
	 * The returned map contains two lists, keyed to constants:
	 *   EXACT - All attributes match (excluding fileHash/fileVersion).
	 *   FUZZY - Title and URL match, but not everything.
	 */
	public HashMap<String,List<ModInfo>> getSimilarMods( ModInfo modInfo ) {
		HashMap<String,List<ModInfo>> resultsMap = new HashMap<String,List<ModInfo>>();
		resultsMap.put( EXACT, new ArrayList<ModInfo>() );
		resultsMap.put( FUZZY, new ArrayList<ModInfo>() );

		for ( ModInfo altInfo : catalog ) {
			if ( altInfo.getTitle().equals( modInfo.getTitle() ) ) {
				if ( altInfo.getURL().equals( modInfo.getURL() ) ) {
					boolean exact = true;

					if ( !altInfo.getDescription().equals( modInfo.getDescription() ) )
						exact = false;
					else if ( !altInfo.getAuthor().equals( modInfo.getAuthor() ) )
						exact = false;

					resultsMap.get( exact ? EXACT : FUZZY ).add( altInfo );
				}
			}
		}

		return resultsMap;
	}


	/**
	 * Collects ModInfo objects that differ only in version, and creates ModsInfo objects.
	 */
	public List<ModsInfo> getCollatedModInfo() {
		List<ModsInfo> results = new ArrayList<ModsInfo>();
		List<ModInfo> seenList = new ArrayList<ModInfo>();

		for ( ModInfo modInfo : catalog ) {
			if ( seenList.contains( modInfo ) ) continue;
			seenList.add( modInfo );

			ModsInfo modsInfo = new ModsInfo();
			modsInfo.setTitle( modInfo.getTitle() );
			modsInfo.setAuthor( modInfo.getAuthor() );
			modsInfo.setThreadURL( modInfo.getURL() );
			modsInfo.setDescription( modInfo.getDescription() );

			String threadHash = getThreadHash( modInfo.getURL() );
			modsInfo.setThreadHash( ( threadHash != null ? threadHash : "???" ) );

			modsInfo.putVersion( modInfo.getFileHash(), modInfo.getVersion() );

			Map<String,List<ModInfo>> similarMods = getSimilarMods( modInfo );
			for ( ModInfo altInfo : similarMods.get( ModDB.EXACT ) ) {
				if ( seenList.contains( altInfo ) ) continue;
				seenList.add( altInfo );

				modsInfo.putVersion( altInfo.getFileHash(), altInfo.getVersion() );
			}

			results.add( modsInfo );
		}

		return results;
	}
}
