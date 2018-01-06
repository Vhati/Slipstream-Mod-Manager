package net.vhati.modmanager.core;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import net.vhati.modmanager.core.ComparableVersion;


/**
 * Holds info about available updates.
 */
public class AutoUpdateInfo {
	private ComparableVersion latestVersion = null;
	private Map<String, String> latestURLs = new TreeMap<String, String>();
	private String notice = null;
	private Map<ComparableVersion, List<String>> changelog = new TreeMap<ComparableVersion, List<String>>( Collections.reverseOrder() );


	public void setLatestVersion( ComparableVersion version ) {
		latestVersion = version;
	}

	public ComparableVersion getLatestVersion() {
		return latestVersion;
	}

	public void setNotice( String s ) {
		notice = s;
	}

	public String getNotice() {
		return notice;
	}

	public void putLatestURL( String os, String url ) {
		latestURLs.put( os, url );
	}

	public void putChanges( ComparableVersion version, List<String> changeList ) {
		changelog.put( version, changeList );
	}

	public Map<String, String> getLatestURLs() {
		return latestURLs;
	}

	public Map<ComparableVersion, List<String>> getChangelog() {
		return changelog;
	}
}
