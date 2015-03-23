package net.vhati.modmanager.core;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.Properties;


public class SlipstreamConfig {

	private Properties config;
	private File configFile;


	public SlipstreamConfig( Properties config, File configFile ) {
		this.config = config;
		this.configFile = configFile;
	}

	/**
	 * Returns a copy of an existing SlipstreamConfig object.
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
			String configComments = "";
			configComments += "\n";
			configComments += " allow_zip         - Sets whether to treat .zip files as .ftl files. Default: false.\n";
			configComments += " ftl_dats_path     - The path to FTL's resources folder. If invalid, you'll be prompted.\n";
			configComments += " run_steam_ftl     - If true, SMM will use Steam to launch FTL, if possible. Default: false.\n";
			configComments += " never_run_ftl     - If true, there will be no offer to run FTL after patching. Default: false.\n";
			configComments += " update_catalog    - If a number greater than 0, check for new mod descriptions every N days.\n";
			configComments += " update_app        - If a number greater than 0, check for newer app versions every N days.\n";
			configComments += " use_default_ui    - If true, no attempt will be made to resemble a native GUI. Default: false.\n";
			configComments += " remember_geometry - If true, window geometry will be saved on exit and restored on startup.\n";
			configComments += "\n";
			configComments += " manager_geometry  - Last saved position/size/etc of the main window.\n";

			OutputStreamWriter writer = new OutputStreamWriter( out, "UTF-8" );
			config.store( writer, configComments );
			writer.flush();
		}
		finally {
			try {if ( out != null ) out.close();}
			catch ( IOException e ) {}
		}
	}
}
