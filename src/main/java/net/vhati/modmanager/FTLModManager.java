package net.vhati.modmanager;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.Properties;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import net.vhati.modmanager.cli.SlipstreamCLI;
import net.vhati.modmanager.core.ComparableVersion;
import net.vhati.modmanager.core.FTLUtilities;
import net.vhati.modmanager.core.SlipstreamConfig;
import net.vhati.modmanager.ui.ManagerFrame;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


public class FTLModManager {

	private static final Logger log = LogManager.getLogger(FTLModManager.class);

	public static final String APP_NAME = "Slipstream Mod Manager";
	public static final ComparableVersion APP_VERSION = new ComparableVersion( "1.7" );
	public static final String APP_URL = "http://www.ftlgame.com/forum/viewtopic.php?f=12&t=17102";
	public static final String APP_AUTHOR = "Vhati";


	public static void main( String[] args ) {
		if ( args.length > 0 ) SlipstreamCLI.main( args );


		// Ensure all popups are triggered from the event dispatch thread.

		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				guiInit();
			}
		});
	}


	private static void guiInit() {

		log.debug( String.format( "%s v%s", APP_NAME, APP_VERSION ) );
		log.debug( String.format( "%s %s", System.getProperty("os.name"), System.getProperty("os.version") ) );
		log.debug( String.format( "%s, %s, %s", System.getProperty("java.vm.name"), System.getProperty("java.version"), System.getProperty("os.arch") ) );

		// Nag if the jar was double-clicked.
		if ( new File("./mods/").exists() == false ) {
			String currentPath = new File(".").getAbsoluteFile().getParentFile().getAbsolutePath();
			showErrorDialog( String.format( "Slipstream could not find its own folder.\nCurrently in: %s\n\nRun one of the following instead of the jar...\nWindows: modman.exe or modman_admin.exe\nLinux/OSX: modman.command or modman-cli.sh\n\nThe Mod Manager will now exit.", currentPath ) );
			System.err.println( String.format( "Slipstream could not find its own folder (Currently in \"%s\"), exiting.", currentPath ) );
			System.exit( 1 );
		}


		File configFile = new File( "modman.cfg" );

		boolean writeConfig = false;
		Properties config = new Properties();
		config.setProperty( "allow_zip", "false" );
		config.setProperty( "ftl_dats_path", "" );
		config.setProperty( "never_run_ftl", "false" );
		config.setProperty( "use_default_ui", "false" );
		config.setProperty( "remember_geometry", "true" );
		// "update_catalog" doesn't have a default.
		// "update_app" doesn't have a default.
		// "manager_geometry" doesn't have a default.

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
				UIManager.setLookAndFeel( UIManager.getSystemLookAndFeelClassName() );
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
				int response = JOptionPane.showConfirmDialog( null, "FTL resources were found in:\n"+ datsDir.getPath() +"\nIs this correct?", "Confirm", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE );
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
		boolean askAboutUpdates = false;
		String catalogUpdateInterval = config.getProperty( "update_catalog" );
		String appUpdateInterval = config.getProperty( "update_app" );

		if ( catalogUpdateInterval == null || !catalogUpdateInterval.matches("^\\d+$") )
			askAboutUpdates = true;
		if ( appUpdateInterval == null || !appUpdateInterval.matches("^\\d+$") )
			askAboutUpdates = true;

		if ( askAboutUpdates ) {
			String message = "";
			message += "Would you like Slipstream to periodically\n";
			message += "check for updates and download descriptions\n";
			message += "for the latest mods?\n\n";
			message += "You can change this later in modman.cfg.";

			int response = JOptionPane.showConfirmDialog(null, message, "Updates", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
			if ( response == JOptionPane.YES_OPTION ) {
				config.setProperty( "update_catalog", "7" );
				config.setProperty( "update_app", "4" );
			} else {
				config.setProperty( "update_catalog", "0" );
				config.setProperty( "update_app", "0" );
			}
		}


		SlipstreamConfig appConfig = new SlipstreamConfig( config, configFile );
		if ( writeConfig ) {
			try {
				appConfig.writeConfig();
			}
			catch ( IOException e ) {
				String errorMsg = String.format( "Error writing config to \"%s\".", configFile.getPath() );
				log.error( errorMsg, e );
				showErrorDialog( errorMsg );
			}
		}

		// Create the main window.
		try {
			ManagerFrame frame = new ManagerFrame( appConfig, APP_NAME, APP_VERSION, APP_URL, APP_AUTHOR );
			frame.init();
			frame.setVisible(true);
		} catch ( Exception e ) {
			log.error( "Exception while creating ManagerFrame.", e );
			System.exit(1);
		}
	}



	private static void showErrorDialog( String message ) {
		JOptionPane.showMessageDialog( null, message, "Error", JOptionPane.ERROR_MESSAGE );
	}
}
