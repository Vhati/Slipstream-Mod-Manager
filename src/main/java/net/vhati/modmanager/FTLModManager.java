package net.vhati.modmanager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.Properties;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import net.vhati.modmanager.core.ComparableVersion;
import net.vhati.modmanager.core.FTLUtilities;
import net.vhati.modmanager.ui.ManagerFrame;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


public class FTLModManager {

	private static final Logger log = LogManager.getLogger(FTLModManager.class);

	private static final String APP_NAME = "Slipstream Mod Manager";
	private static final ComparableVersion APP_VERSION = new ComparableVersion( "1.0" );


	public static void main( String[] args ) {

		log.debug( String.format( "%s v%s", APP_NAME, APP_VERSION ) );
		log.debug( System.getProperty("os.name") +" "+ System.getProperty("os.version") +" "+ System.getProperty("os.arch") );
		log.debug( System.getProperty("java.vm.name") +", "+ System.getProperty("java.version") );


		File configFile = new File( "modman.cfg" );

		boolean writeConfig = false;
		Properties config = new Properties();
		config.setProperty( "allow_zip", "false" );
		config.setProperty( "ftl_dats_path", "" );
		config.setProperty( "never_run_ftl", "false" );
		config.setProperty( "use_default_ui", "false" );
		// "update_catalog" doesn't have a default.

		// Read the config file.
		InputStream in = null;
		try {
			if ( configFile.exists() ) {
				log.trace( "Loading properties from config file." );
				in = new FileInputStream( configFile );
				config.load( new InputStreamReader( in, "UTF-8" ) );
			} else {
				writeConfig = true; // Create a new cfg, but only if necessary.
			}
		}
		catch (IOException e) {
			log.error( "Error loading config.", e );
			showErrorDialog( "Error loading config from "+ configFile.getPath() );
		}
		finally {
			try {if ( in != null ) in.close();}
			catch ( IOException e ) {}
		}

		// Look-and-Feel.
		String useDefaultUI = config.getProperty( "use_default_ui", "false" );

		if ( !useDefaultUI.equals("true") ) {
			try {
				log.trace( "Using system Look and Feel" );
				UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
			}
			catch (Exception e) {
				log.error( "Error setting system Look and Feel.", e );
				log.info( "Setting 'useDefaultUI=true' in the config file will prevent this error." );
			}
		} else {
			log.debug( "Using default Look and Feel." );
		}

		// FTL Resources Path.
		File datsDir = null;
		String datsPath = config.getProperty( "ftl_dats_path", "" );

		if ( datsPath.length() > 0 ) {
			log.info( "Using FTL dats path from config: "+ datsPath );
			datsDir = new File( datsPath );
			if ( FTLUtilities.isDatsDirValid( datsDir ) == false ) {
				log.error( "The config's ftl_dats_path does not exist, or it lacks data.dat." );
				datsDir = null;
			}
		} else {
			log.trace( "No FTL dats path previously set." );
		}

		// Find/prompt for the path to set in the config.
		if ( datsDir == null ) {
			datsDir = FTLUtilities.findDatsDir();
			if ( datsDir != null ) {
				int response = JOptionPane.showConfirmDialog(null, "FTL resources were found in:\n"+ datsDir.getPath() +"\nIs this correct?", "Confirm", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
				if ( response == JOptionPane.NO_OPTION ) datsDir = null;
			}

			if ( datsDir == null ) {
				log.debug( "FTL dats path was not located automatically. Prompting user for location." );
				datsDir = FTLUtilities.promptForDatsDir( null );
			}

			if ( datsDir != null ) {
				config.setProperty( "ftl_dats_path", datsDir.getAbsolutePath() );
				writeConfig = true;
				log.info( "FTL dats located at: "+ datsDir.getAbsolutePath() );
			}
		}

		if ( datsDir == null ) {
			showErrorDialog( "FTL resources were not found.\nThe Mod Manager will now exit." );
			log.debug( "No FTL dats path found, exiting." );
			System.exit( 1 );
		}

		// Prompt if update_catalog is invalid or hasn't been set.

		String updateCatalog = config.getProperty( "update_catalog" );
		if ( updateCatalog == null || !updateCatalog.matches("^true|false$") ) {
			String message = "";
			message += "Would you like Slipstream to periodically\n";
			message += "download descriptions for the latest mods?\n\n";
			message += "You can change this later in modman.cfg.";

			int response = JOptionPane.showConfirmDialog(null, message, "Catalog Updates", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
			if ( response == JOptionPane.YES_OPTION )
				config.setProperty( "update_catalog", "true" );
			else
				config.setProperty( "update_catalog", "false" );
		}


		if ( writeConfig ) {
			OutputStream out = null;
			try {
				out = new FileOutputStream( configFile );
				String configComments = "";
				configComments += "\n";
				configComments += " allow_zip - Sets whether to treat .zip files as .ftl files. Default: false.\n";
				configComments += " ftl_dats_path - The path to FTL's resources folder. If invalid, you'll be prompted.\n";
				configComments += " never_run_ftl - If true, there will be no offer to run FTL after patching. Default: false.\n";
				configComments += " update_catalog - If true, periodically download descriptions for the latest mods. If invalid, you'll be prompted.\n";
				configComments += " use_default_ui - If true, no attempt will be made to resemble a native GUI. Default: false.\n";

				config.store( new OutputStreamWriter( out, "UTF-8" ), configComments );
			}
			catch ( IOException e ) {
				log.error( "Error saving config to "+ configFile.getPath(), e );
				showErrorDialog( "Error saving config to "+ configFile.getPath() );
			}
			finally {
				try {if ( out != null ) out.close();}
				catch ( IOException e ) {}
			}
		}

		// Create the GUI.
		try {
			final ManagerFrame frame = new ManagerFrame( config, APP_NAME, APP_VERSION );
			frame.setDefaultCloseOperation( frame.EXIT_ON_CLOSE );
			frame.setVisible(true);

			SwingUtilities.invokeLater(new Runnable() {
				@Override
				public void run() {
					frame.init();
				}
			});
		}
		catch ( Exception e ) {
			log.error( "Exception while creating ManagerFrame.", e );
			System.exit(1);
		}
	}


	private static void showErrorDialog( String message ) {
		JOptionPane.showMessageDialog(null, message, "Error", JOptionPane.ERROR_MESSAGE);
	}
}
