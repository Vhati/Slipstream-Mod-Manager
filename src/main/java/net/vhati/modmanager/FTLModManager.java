package net.vhati.modmanager;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.Properties;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.vhati.modmanager.cli.SlipstreamCLI;
import net.vhati.modmanager.core.ComparableVersion;
import net.vhati.modmanager.core.FTLUtilities;
import net.vhati.modmanager.core.SlipstreamConfig;
import net.vhati.modmanager.ui.ManagerFrame;


public class FTLModManager {

	private static final Logger log = LogManager.getLogger( FTLModManager.class );

	public static final String APP_NAME = "Slipstream Mod Manager";
	public static final ComparableVersion APP_VERSION = new ComparableVersion( "1.7" );
	public static final String APP_URL = "https://subsetgames.com/forum/viewtopic.php?f=12&t=17102";
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
		try {
			log.debug( String.format( "%s v%s", APP_NAME, APP_VERSION ) );
			log.debug( String.format( "%s %s", System.getProperty( "os.name" ), System.getProperty( "os.version" ) ) );
			log.debug( String.format( "%s, %s, %s", System.getProperty( "java.vm.name" ), System.getProperty( "java.version" ), System.getProperty( "os.arch" ) ) );

			// Nag if the jar was double-clicked.
			if ( new File( "./mods/" ).exists() == false ) {
				String currentPath = new File( "." ).getAbsoluteFile().getParentFile().getAbsolutePath();
				showErrorDialog( String.format( "Slipstream could not find its own folder.\nCurrently in: %s\n\nRun one of the following instead of the jar...\nWindows: modman.exe or modman_admin.exe\nLinux/OSX: modman.command or modman-cli.sh\n\nThe Mod Manager will now exit.", currentPath ) );
				System.err.println( String.format( "Slipstream could not find its own folder (Currently in \"%s\"), exiting.", currentPath ) );

				throw new ExitException();
			}


			File configFile = new File( "modman.cfg" );

			boolean writeConfig = false;
			Properties props = new Properties();
			props.setProperty( SlipstreamConfig.ALLOW_ZIP, "false" );
			props.setProperty( SlipstreamConfig.FTL_DATS_PATH, "" );
			props.setProperty( SlipstreamConfig.STEAM_EXE_PATH, "" );
			props.setProperty( SlipstreamConfig.RUN_STEAM_FTL, "false" );
			props.setProperty( SlipstreamConfig.NEVER_RUN_FTL, "false" );
			props.setProperty( SlipstreamConfig.USE_DEFAULT_UI, "false" );
			props.setProperty( SlipstreamConfig.REMEMBER_GEOMETRY, "true" );
			// "update_catalog" doesn't have a default.
			// "update_app" doesn't have a default.
			// "manager_geometry" doesn't have a default.

			// Read the config file.
			InputStream in = null;
			try {
				if ( configFile.exists() ) {
					log.debug( "Loading config file" );
					in = new FileInputStream( configFile );
					props.load( new InputStreamReader( in, "UTF-8" ) );
				} else {
					writeConfig = true; // Create a new cfg, but only if necessary.
				}
			}
			catch ( IOException e ) {
				log.error( "Error loading config", e );
				showErrorDialog( "Error loading config from "+ configFile.getPath() );
			}
			finally {
				try {if ( in != null ) in.close();}
				catch ( IOException e ) {}
			}

			SlipstreamConfig appConfig = new SlipstreamConfig( props, configFile );

			// Look-and-Feel.
			boolean useDefaultUI = "true".equals( appConfig.getProperty( SlipstreamConfig.USE_DEFAULT_UI, "false" ) );

			if ( !useDefaultUI ) {
				LookAndFeel defaultLaf = UIManager.getLookAndFeel();
				log.debug( "Default look and feel is: "+ defaultLaf.getName() );

				try {
					log.debug( "Setting system look and feel: "+ UIManager.getSystemLookAndFeelClassName() );

					// SystemLaf is risky. It may throw an exception, or lead to graphical bugs.
					// Problems are geneally caused by custom Windows themes.
					UIManager.setLookAndFeel( UIManager.getSystemLookAndFeelClassName() );
				}
				catch ( Exception e ) {
					log.error( "Failed to set system look and feel", e );
					log.info( "Setting "+ SlipstreamConfig.USE_DEFAULT_UI +"=true in the config file to prevent this error..." );

					appConfig.setProperty( SlipstreamConfig.USE_DEFAULT_UI, "true" );
					writeConfig = true;

					try {
						UIManager.setLookAndFeel( defaultLaf );
					}
					catch ( Exception f ) {
						log.error( "Error returning to the default look and feel after failing to set system look and feel", f );

						// Write an emergency config and exit.
						try {
							appConfig.writeConfig();
						}
						catch ( IOException g ) {
							log.error( String.format( "Error writing config to \"%s\"", configFile.getPath(), g ) );
						}

						throw new ExitException();
					}
				}
			}
			else {
				log.debug( "Using default Look and Feel" );
			}

			// FTL Resources Path.
			File datsDir = null;
			String datsPath = appConfig.getProperty( SlipstreamConfig.FTL_DATS_PATH, "" );

			if ( datsPath.length() > 0 ) {
				log.info( "Using FTL dats path from config: "+ datsPath );
				datsDir = new File( datsPath );
				if ( FTLUtilities.isDatsDirValid( datsDir ) == false ) {
					log.error( "The config's "+ SlipstreamConfig.FTL_DATS_PATH +" does not exist, or it is invalid" );
					datsDir = null;
				}
			}
			else {
				log.debug( "No "+ SlipstreamConfig.FTL_DATS_PATH +" previously set" );
			}

			// Find/prompt for the path to set in the config.
			if ( datsDir == null ) {
				datsDir = FTLUtilities.findDatsDir();
				if ( datsDir != null ) {
					int response = JOptionPane.showConfirmDialog( null, "FTL resources were found in:\n"+ datsDir.getPath() +"\nIs this correct?", "Confirm", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE );
					if ( response == JOptionPane.NO_OPTION ) datsDir = null;
				}

				if ( datsDir == null ) {
					log.debug( "FTL dats path was not located automatically. Prompting user for location" );
					datsDir = FTLUtilities.promptForDatsDir( null );
				}

				if ( datsDir != null ) {
					appConfig.setProperty( SlipstreamConfig.FTL_DATS_PATH, datsDir.getAbsolutePath() );
					writeConfig = true;
					log.info( "FTL dats located at: "+ datsDir.getAbsolutePath() );
				}
			}

			if ( datsDir == null ) {
				showErrorDialog( "FTL resources were not found.\nThe Mod Manager will now exit." );
				log.debug( "No FTL dats path found, exiting" );

				throw new ExitException();
			}

			// Ask about Steam.
			if ( appConfig.getProperty( SlipstreamConfig.STEAM_EXE_PATH, "" ).length() == 0 ) {

				int steamBasedResponse = JOptionPane.showConfirmDialog( null, "Was FTL installed via Steam?", "Confirm", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE );
				if ( steamBasedResponse == JOptionPane.YES_OPTION ) {
					File steamExeFile = FTLUtilities.findSteamExe();

					if ( steamExeFile == null && System.getProperty( "os.name" ).startsWith( "Windows" ) ) {
						try {
							String registryExePath = FTLUtilities.queryRegistryKey( "HKCU\\Software\\Valve\\Steam", "SteamExe", "REG_SZ" );
							if ( registryExePath != null && !(steamExeFile=new File( registryExePath )).exists() ) {
								steamExeFile = null;
							}
						}
						catch( IOException e ) {
							log.error( "Error while querying registry for Steam's path", e );
						}
					}

					if ( steamExeFile != null ) {
						int response = JOptionPane.showConfirmDialog( null, "Steam was found at:\n"+ steamExeFile.getPath() +"\nIs this correct?", "Confirm", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE );
						if ( response == JOptionPane.NO_OPTION ) steamExeFile = null;
					}

					if ( steamExeFile == null ) {
						log.debug( "Steam was not located automatically. Prompting user for location" );

						StringBuilder steamBuf = new StringBuilder();
						steamBuf.append( "You will be prompted to locate Steam's executable.\n" );
						steamBuf.append( "- Windows: Steam.exe\n" );
						steamBuf.append( "- Linux: steam\n" );
						steamBuf.append( "- OSX: Steam.app\n" );
						steamBuf.append( "\n" );
						steamBuf.append( "If you can't find it, you can cancel and set it later." );
						JOptionPane.showMessageDialog( null, steamBuf.toString(), "Find Steam", JOptionPane.INFORMATION_MESSAGE );

						JFileChooser steamExeChooser = new JFileChooser();
						steamExeChooser.setDialogTitle( "Find Steam.exe or steam or Steam.app" );
						steamExeChooser.setFileHidingEnabled( false );
						steamExeChooser.setMultiSelectionEnabled( false );

						if ( steamExeChooser.showOpenDialog( null ) == JFileChooser.APPROVE_OPTION ) {
							steamExeFile = steamExeChooser.getSelectedFile();
							if ( !steamExeFile.exists() ) steamExeFile = null;
						}
					}

					if ( steamExeFile != null ) {
						appConfig.setProperty( SlipstreamConfig.STEAM_EXE_PATH, steamExeFile.getAbsolutePath() );
						writeConfig = true;
						log.info( "Steam located at: "+ steamExeFile.getAbsolutePath() );
					}

					if ( appConfig.getProperty( SlipstreamConfig.STEAM_EXE_PATH, "" ).length() > 0 ) {

						String[] launchOptions = new String[] {"Directly", "Steam"};
						int launchResponse = JOptionPane.showOptionDialog( null, "Would you prefer to launch FTL directly, or via Steam?", "How to Launch?", JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, launchOptions, launchOptions[1] );
						if ( launchResponse == 0 ) {
							appConfig.setProperty( SlipstreamConfig.RUN_STEAM_FTL, "false" );
							writeConfig = true;
						}
						else if ( launchResponse == 1 ) {
							appConfig.setProperty( SlipstreamConfig.RUN_STEAM_FTL, "true" );
							writeConfig = true;
						}
					}
				}
				else {
					appConfig.setProperty( SlipstreamConfig.RUN_STEAM_FTL, "false" );
					writeConfig = true;
				}
			}

			// Prompt if update_catalog is invalid or hasn't been set.
			boolean askAboutUpdates = false;
			if ( !appConfig.getProperty( SlipstreamConfig.UPDATE_CATALOG, "" ).matches( "^\\d+$" ) )
				askAboutUpdates = true;
			if ( !appConfig.getProperty( SlipstreamConfig.UPDATE_APP, "" ).matches( "^\\d+$" ) )
				askAboutUpdates = true;

			if ( askAboutUpdates ) {
				String message = "";
				message += "Would you like Slipstream to periodically check for updates?\n";
				message += "\n";
				message += "You can change this later in modman.cfg.";

				int response = JOptionPane.showConfirmDialog( null, message, "Updates", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE );
				if ( response == JOptionPane.YES_OPTION ) {
					appConfig.setProperty( SlipstreamConfig.UPDATE_CATALOG, "7" );
					appConfig.setProperty( SlipstreamConfig.UPDATE_APP, "4" );
				}
				else {
					appConfig.setProperty( SlipstreamConfig.UPDATE_CATALOG, "0" );
					appConfig.setProperty( SlipstreamConfig.UPDATE_APP, "0" );
				}
				writeConfig = true;
			}

			if ( writeConfig ) {
				try {
					appConfig.writeConfig();
				}
				catch ( IOException e ) {
					String errorMsg = String.format( "Error writing config to \"%s\"", configFile.getPath() );
					log.error( errorMsg, e );
					showErrorDialog( errorMsg );
				}
			}

			// Create the main window.
			try {
				ManagerFrame frame = new ManagerFrame( appConfig, APP_NAME, APP_VERSION, APP_URL, APP_AUTHOR );
				frame.init();
				frame.setVisible( true );
			}
			catch ( Exception e ) {
				log.error( "Exception while creating ManagerFrame", e );

				throw new ExitException();
			}
		}
		catch ( ExitException e ) {
			System.gc();
			// System.exit( 1 );  // Don't do this (InterruptedException). Let EDT end gracefully.
			return;
		}
	}

	private static void showErrorDialog( String message ) {
		JOptionPane.showMessageDialog( null, message, "Error", JOptionPane.ERROR_MESSAGE );
	}



	private static class ExitException extends RuntimeException {
		public ExitException() {
		}

		public ExitException( String message ) {
			super( message );
		}

		public ExitException( Throwable cause ) {
			super( cause );
		}

		public ExitException( String message, Throwable cause ) {
			super( message, cause );
		}
	}
}
