package net.vhati.modmanager.core;

import java.util.LinkedHashMap;
import java.util.Map;


/**
 * Combined information from several similar ModInfo objects of varying versions.
 */
public class ModsInfo {
	public String title = null;
	public String author = null;
	public String threadURL = null;
	public String threadHash = null;
	public String description = null;
	private Map<String,String> versionsMap = new LinkedHashMap<String,String>();


	public ModsInfo() {
	}


	public void setTitle( String s ) { this.title = s; }
	public void setAuthor( String s ) { this.author = s; }
	public void setThreadURL( String s ) { this.threadURL = s; }
	public void setThreadHash( String s ) { this.threadHash = s; }
	public void setDescription( String s ) { this.description = s; }

	public String getTitle() { return this.title; }
	public String getAuthor() { return this.author; }
	public String getThreadURL() { return this.threadURL; }
	public String getThreadHash() { return this.threadHash; }
	public String getDescription() { return this.description; }


	public void putVersion( String fileHash, String fileVersion ) {
		versionsMap.put( fileHash, fileVersion );
	}

	/**
	 * Returns the internal Map of mod file hashes and version strings.
	 */
	public Map<String,String> getVersionsMap() {
		return versionsMap;
	}
}
