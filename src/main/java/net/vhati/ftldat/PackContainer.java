package net.vhati.ftldat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.vhati.ftldat.AbstractPack;


/**
 * A container coordinating access to one or more of FTL's dat files.
 *
 * FTL 1.01-1.5.13 had two files "data.dat" and "resource.dat", in a
 * subdirectory "resources/" relative to FTLGame.exe.
 *   data.dat:data/
 *   resource.dat:audio/
 *   resource.dat:fonts/
 *   resource.dat:img/
 *
 * FTL 1.6.1 switched to a single "ftl.dat", alongside "FTLGame.exe".
 *   ftl.dat:audio/
 *   ftl.dat:data/
 *   ftl.dat:fonts/
 *   ftl.dat:img/
 *
 * SlipStream mods may have "mod-appendix/", which should be ignored, not added
 * to packs.
 *
 * @see net.vhati.ftldat.FTLPack
 * @see net.vhati.ftldat.PkgPack
 */
public class PackContainer {

	/**
	 * Regex for innerPaths.
	 *
	 * Group1: parentPath/ (may be null).
	 * Group2: root/ (may be null).
	 * Group3: fileName (never null, if innerPath is valid).
	 */
	protected Pattern pathPtn = Pattern.compile( "^(?:(([^/]+/)(?:.*/)?))?([^/]+)$" );

	protected Map<String, AbstractPack> rootMap = new HashMap<String, AbstractPack>();
	protected AbstractPack defaultPack = null;


	public PackContainer() {
	}

	/**
	 * Sets a pack to use when not overridden for specific root dirs.
	 *
	 * If null, only specific root dir paths will be supported.
	 */
	public void setDefaultPack( AbstractPack pack ) {
		defaultPack = pack;
	}

	public AbstractPack getDefaultPack() {
		return defaultPack;
	}

	/**
	 * Sets a pack to use for innerPaths within a given root dir, instead of
	 * the default pack.
	 *
	 * The root must include a trailing forward slash.
	 *
	 * A null pack should be interpreted as the result when paths should be
	 * ignored. A null root would apply to top-level files.
	 */
	public void setPackFor( String root, AbstractPack pack ) throws IllegalArgumentException {
		if ( root != null && !root.endsWith( "/" ) ) {
			throw new IllegalArgumentException( "Root dir lacks a trailing forward slash:"+ root );
		}

		rootMap.put( root, pack );
	}

	/**
	 * Returns the pack relevant to innerPath, or null.
	 *
	 * Returns null if no pack is relevant or if innerPath is malformed.
	 */
	public AbstractPack getPackFor( String innerPath ) {
		Matcher m = pathPtn.matcher( innerPath );
		if ( m.matches() ) {
			String root = m.group( 2 );
			AbstractPack rootPack = rootMap.get( root );

			if ( rootPack != null ) return rootPack;
			if ( !rootMap.containsKey( root ) ) return null;
		}
		else {
			return null;  // Malformed.
		}

		if ( defaultPack != null ) return defaultPack;

		return null;
	}

	/**
	 * Returns a list of known root dirs.
	 *
	 * The list may contain null, if that root was set.
	 */
	public List<String> getRoots() {
		return new ArrayList<String>( rootMap.keySet() );
	}

	/**
	 * Returns a list of all contained packs.
	 */
	public List<AbstractPack> getPacks() {
		List<AbstractPack> result = new ArrayList<AbstractPack>( rootMap.values().size() + 1 );

		for ( AbstractPack pack : rootMap.values() ) {
			if ( pack != null && !result.contains( pack ) ) result.add( pack );
		}
		if ( defaultPack != null ) result.add( defaultPack );

		return result;
	}
}
