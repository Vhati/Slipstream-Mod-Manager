package net.vhati.modmanager.core;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;


public class SlipstreamConfig {

	public static final String ALLOW_ZIP = "allow_zip";
	public static final String FTL_DATS_PATH = "ftl_dats_path";
	public static final String STEAM_DISTRO = "steam_distro";
	public static final String STEAM_EXE_PATH = "steam_exe_path";
	public static final String RUN_STEAM_FTL = "run_steam_ftl";
	public static final String NEVER_RUN_FTL = "never_run_ftl";
	public static final String UPDATE_CATALOG = "update_catalog";
	public static final String UPDATE_APP = "update_app";
	public static final String USE_DEFAULT_UI = "use_default_ui";
	public static final String REMEMBER_GEOMETRY = "remember_geometry";
	public static final String MANAGER_GEOMETRY = "manager_geometry";

	private Properties config;
	private File configFile;


	public SlipstreamConfig( Properties config, File configFile ) {
		this.config = config;
		this.configFile = configFile;
	}

	/**
	 * Copy constructor.
	 */
	public SlipstreamConfig( SlipstreamConfig srcConfig ) {
		this.configFile = srcConfig.getConfigFile();
		this.config = new Properties();
		this.config.putAll( srcConfig.getConfig() );
	}


	public Properties getConfig() { return config; }

	public File getConfigFile() { return configFile; }


	public Object setProperty( String key, String value ) {
		return config.setProperty( key, value );
	}

	public int getPropertyAsInt( String key, int defaultValue ) {
		String s = config.getProperty( key );
		if ( s != null && s.matches("^\\d+$") )
			return Integer.parseInt( s );
		else
			return defaultValue;
	}

	public String getProperty( String key, String defaultValue ) {
		return config.getProperty( key, defaultValue );
	}

	public String getProperty( String key ) {
		return config.getProperty( key );
	}


	public void writeConfig() throws IOException {

		OutputStream out = null;
		try {
			out = new FileOutputStream( configFile );

			Map<String, String> userFieldsMap = new LinkedHashMap<String, String>();
			Map<String, String> appFieldsMap = new LinkedHashMap<String, String>();

			userFieldsMap.put( ALLOW_ZIP,         "Sets whether to treat .zip files as .ftl files. Default: false." );
			userFieldsMap.put( FTL_DATS_PATH,     "The path to FTL's resources folder. If invalid, you'll be prompted." );
			userFieldsMap.put( STEAM_DISTRO,      "If true, FTL was installed via Steam. Stops the GUI asking for a path." );
			userFieldsMap.put( STEAM_EXE_PATH,    "The path to Steam's executable, if FTL was installed via Steam." );
			userFieldsMap.put( RUN_STEAM_FTL,     "If true, SMM will use Steam to launch FTL, if possible." );
			userFieldsMap.put( NEVER_RUN_FTL,     "If true, there will be no offer to run FTL after patching. Default: false." );
			userFieldsMap.put( UPDATE_CATALOG,    "If a number greater than 0, check for new mod descriptions every N days." );
			userFieldsMap.put( UPDATE_APP,        "If a number greater than 0, check for newer app versions every N days." );
			userFieldsMap.put( USE_DEFAULT_UI,    "If true, no attempt will be made to resemble a native GUI. Default: false." );
			userFieldsMap.put( REMEMBER_GEOMETRY, "If true, window geometry will be saved on exit and restored on startup." );

			appFieldsMap.put( MANAGER_GEOMETRY,   "Last saved position/size/etc of the main window." );

			List<String> allFieldsList = new ArrayList<String>( userFieldsMap.size() + appFieldsMap.size() );
			allFieldsList.addAll( userFieldsMap.keySet() );
			allFieldsList.addAll( appFieldsMap.keySet() );
			int fieldWidth = 0;
			for ( String fieldName : allFieldsList ) {
				fieldWidth = Math.max( fieldName.length(), fieldWidth );
			}

			StringBuilder commentsBuf = new StringBuilder( "\n" );
			for ( Map.Entry<String, String> entry : userFieldsMap.entrySet() ) {
				commentsBuf.append( String.format( " %-"+ fieldWidth +"s - %s\n", entry.getKey(), entry.getValue() ) );
			}
			commentsBuf.append( "\n" );
			for ( Map.Entry<String, String> entry : appFieldsMap.entrySet() ) {
				commentsBuf.append( String.format( " %-"+ fieldWidth +"s - %s\n", entry.getKey(), entry.getValue() ) );
			}

			OutputStreamWriter writer = new OutputStreamWriter( out, "UTF-8" );
			config.store( writer, commentsBuf.toString() );
			writer.flush();
		}
		finally {
			try {if ( out != null ) out.close();}
			catch ( IOException e ) {}
		}
	}
}
