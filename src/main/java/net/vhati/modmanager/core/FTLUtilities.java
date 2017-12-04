package net.vhati.modmanager.core;

import java.awt.Component;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileFilter;


public class FTLUtilities {

	/** Steam's application ID for FTL. */
	public static final String STEAM_APPID_FTL = "212680";


	/**
	 * Confirms the FTL resources dir exists and contains the dat files.
	 *
	 * Note: Do d.getCanonicalFile() to resolve any symlinks first!
	 */
	public static boolean isDatsDirValid( File d ) {
		if ( !d.exists() || !d.isDirectory() ) return false;
		if ( !new File( d, "data.dat" ).exists() ) return false;
		if ( !new File( d, "resource.dat" ).exists() ) return false;
		return true;
	}

	/**
	 * Returns the FTL resources dir, or null.
	 */
	public static File findDatsDir() {
		String steamPath = "Steam/steamapps/common/FTL Faster Than Light/resources";
		String gogPath = "GOG.com/Faster Than Light/resources";
		String humblePath = "FTL/resources";

		String programFiles86 = System.getenv( "ProgramFiles(x86)" );
		String programFiles = System.getenv( "ProgramFiles" );

		String home = System.getProperty( "user.home" );

		String xdgDataHome = System.getenv( "XDG_DATA_HOME" );
		if ( xdgDataHome == null && home != null )
			xdgDataHome = home +"/.local/share";

		String winePrefix = System.getProperty( "WINEPREFIX" );
		if ( winePrefix == null && home != null )
			winePrefix = home +"/.wine";

		List<File> candidates = new ArrayList<File>();
		// Windows - Steam, GOG, Humble Bundle.
		if ( programFiles86 != null ) {
			candidates.add( new File( new File( programFiles86 ), steamPath ) );
			candidates.add( new File( new File( programFiles86 ), gogPath ) );
			candidates.add( new File( new File( programFiles86 ), humblePath ) );
		}
		if ( programFiles != null ) {
			candidates.add( new File( new File( programFiles ), steamPath ) );
			candidates.add( new File( new File( programFiles ), gogPath ) );
			candidates.add( new File( new File( programFiles ), humblePath ) );
		}
		// Linux - Steam.
		if ( xdgDataHome != null ) {
			candidates.add( new File( xdgDataHome +"/Steam/steamapps/common/FTL Faster Than Light/data/resources" ) );
			candidates.add( new File( xdgDataHome +"/Steam/SteamApps/common/FTL Faster Than Light/data/resources" ) );
		}
		if ( home != null ) {
			candidates.add( new File( home +"/.steam/steam/steamapps/common/FTL Faster Than Light/data/resources" ) );
			candidates.add( new File( home +"/.steam/steam/SteamApps/common/FTL Faster Than Light/data/resources" ) );
		}
		// Linux - Wine.
		if ( winePrefix != null ) {
			candidates.add( new File( winePrefix +"/drive_c/Program Files (x86)/"+ gogPath ) );
			candidates.add( new File( winePrefix +"/drive_c/Program Files (x86)/"+ humblePath ) );
			candidates.add( new File( winePrefix +"/drive_c/Program Files/"+ gogPath ) );
			candidates.add( new File( winePrefix +"/drive_c/Program Files/"+ humblePath ) );
		}
		// OSX - Steam.
		if ( home != null ) {
			candidates.add( new File( home +"/Library/Application Support/Steam/steamapps/common/FTL Faster Than Light/FTL.app/Contents/Resources" ) );
			candidates.add( new File( home +"/Library/Application Support/Steam/SteamApps/common/FTL Faster Than Light/FTL.app/Contents/Resources" ) );
		}
		// OSX - Standalone.
		candidates.add( new File( "/Applications/FTL.app/Contents/Resources" ) );

		File result = null;

		for ( File candidate : candidates ) {
			// Resolve symlinks.
			try {candidate = candidate.getCanonicalFile();}
			catch ( IOException e ) {continue;}

			if ( isDatsDirValid( candidate ) ) {
				result = candidate;
				break;
			}
		}

		return result;
	}

	/**
	 * Modally prompts the user for the FTL resources dir.
	 *
	 * Reminder: GUI dialogs need to be in the event dispatch thread.
	 *
	 * @param parentComponent a parent for Swing dialogs, or null
	 */
	public static File promptForDatsDir( Component parentComponent ) {
		File result = null;

		String message = "";
		message += "You will now be prompted to locate FTL manually.\n";
		message += "Select '(FTL dir)/resources/data.dat'.\n";
		message += "Or 'FTL.app', if you're on OSX.";
		JOptionPane.showMessageDialog( parentComponent, message, "Find FTL", JOptionPane.INFORMATION_MESSAGE );

		final JFileChooser fc = new JFileChooser();
		fc.setDialogTitle( "Find data.dat or FTL.app" );
		fc.setFileHidingEnabled( false );
		fc.addChoosableFileFilter(new FileFilter() {
			@Override
			public String getDescription() {
				return "FTL Data File - (FTL dir)/resources/data.dat";
			}
			@Override
			public boolean accept( File f ) {
				return f.isDirectory() || f.getName().equals( "data.dat" ) || f.getName().equals( "FTL.app" );
			}
		});
		fc.setMultiSelectionEnabled(false);

		if ( fc.showOpenDialog( parentComponent ) == JFileChooser.APPROVE_OPTION ) {
			File f = fc.getSelectedFile();
			if ( f.getName().equals( "data.dat" ) ) {
				result = f.getParentFile();
			}
			else if ( f.getName().endsWith( ".app" ) && f.isDirectory() ) {
				File contentsPath = new File( f, "Contents" );
				if ( contentsPath.exists() && contentsPath.isDirectory() && new File( contentsPath, "Resources" ).exists() ) {
					result = new File( contentsPath, "Resources" );
				}
			}
		}

		if ( result != null && isDatsDirValid( result ) ) {
			return result;
		}

		return null;
	}


	/**
	 * Returns the executable that will launch FTL, or null.
	 *
	 * On Windows, FTLGame.exe is one dir above "resources/".
	 * On Linux, FTL is a script, one dir above "resources/".
	 * On OSX, FTL.app is the grandparent dir itself (a bundle).
	 */
	public static File findGameExe( File datsDir ) {
		File result = null;

		if ( System.getProperty( "os.name" ).startsWith( "Windows" ) ) {
			File ftlDir = datsDir.getParentFile();
			if ( ftlDir != null ) {
				File exeFile = new File( ftlDir, "FTLGame.exe" );
				if ( exeFile.exists() ) result = exeFile;
			}
		}
		else if ( System.getProperty( "os.name" ).equals( "Linux" ) ) {
			File ftlDir = datsDir.getParentFile();
			if ( ftlDir != null ) {
				File exeFile = new File( ftlDir, "FTL" );
				if ( exeFile.exists() ) result = exeFile;
			}
		}
		else if ( System.getProperty( "os.name" ).contains( "OS X" ) ) {
			// FTL.app/Contents/Resources/
			File contentsDir = datsDir.getParentFile();
			if ( contentsDir != null ) {
				File bundleDir = contentsDir.getParentFile();
				if ( bundleDir != null ) {
					if ( new File( bundleDir, "Contents/Info.plist" ).exists() ) {
						result = bundleDir;
					}
				}
			}
		}
		return result;
	}


	/**
	 * Returns the executable that will launch Steam, or null.
	 *
	 * On Windows, Steam.exe.
	 * On Linux, steam is a script. ( http://moritzmolch.com/815 )
	 * On OSX, Steam.app is the grandparent dir itself (a bundle).
	 *
	 * The args to launch FTL are: ["-applaunch", STEAM_APPID_FTL]
	 */
	public static File findSteamExe() {
		String programFiles86 = System.getenv( "ProgramFiles(x86)" );
		String programFiles = System.getenv( "ProgramFiles" );

		String osName = System.getProperty( "os.name" );

		List<File> candidates = new ArrayList<File>();

		if ( osName.startsWith( "Windows" ) ) {
			if ( programFiles86 != null ) {
				candidates.add( new File( new File( programFiles86 ), "Steam/Steam.exe" ) );
			}
			if ( programFiles != null ) {
				candidates.add( new File( new File( programFiles ), "Steam/Steam.exe" ) );
			}
		}
		else if ( osName.equals( "Linux" ) ) {
			candidates.add( new File( "/usr/bin/steam" ) );
		}
		else if ( osName.contains( "OS X" ) ) {
			candidates.add( new File( "/Applications/Steam.app" ) );
		}

		File result = null;

		for ( File candidate : candidates ) {
			if ( candidate.exists() ) {
				result = candidate;
				break;
			}
		}

		return result;
	}


	/**
	 * Launches an executable.
	 *
	 * On Windows, *.exe.
	 * On Linux, a binary or script.
	 * On OSX, an *.app bundle dir.
	 *
	 * @param exeFile see findGameExe() or findSteamExe()
	 * @param exeArgs arguments for the executable
	 * @return a Process object, or null
	 */
	public static Process launchExe( File exeFile, String... exeArgs ) throws IOException {
		if ( exeFile == null ) return null;
		if ( exeArgs == null ) exeArgs = new String[0];

		Process result = null;
		ProcessBuilder pb = null;
		if ( System.getProperty( "os.name" ).contains( "OS X" ) ) {
			String[] args = new String[3 + exeArgs.length];
			args[0] = "open";
			args[1] = "-a";
			args[2] = exeFile.getAbsolutePath();
			System.arraycopy( exeArgs, 0, args, 3, exeArgs.length );

			pb = new ProcessBuilder( args );
		}
		else {
			String[] args = new String[1 + exeArgs.length];

			System.arraycopy( exeArgs, 0, args, 1, exeArgs.length );

			pb = new ProcessBuilder( args );
		}
		if ( pb != null ) {
			pb.directory( exeFile.getParentFile() );
			result = pb.start();
		}
		return result;
	}


	/**
	 * Returns the directory for user profiles and saved games, or null.
	 */
	public static File findUserDataDir() {
		String home = System.getProperty( "user.home" );

		String xdgDataHome = System.getenv( "XDG_DATA_HOME" );
		if ( xdgDataHome == null && home != null )
			xdgDataHome = home +"/.local/share";

		List<File> candidates = new ArrayList<File>();

		// Windows.
		if ( home != null ) {
			// Windows XP.
			candidates.add( new File( home +"/My Documents/My Games/FasterThanLight" ) );
			// Windows Vista/7/etc.
			candidates.add( new File( home +"/Documents/My Games/FasterThanLight" ) );
		}
		// Linux.
		if ( xdgDataHome != null ) {
			candidates.add( new File( xdgDataHome +"/FasterThanLight" ) );
		}
		// OSX.
		if ( home != null ) {
			candidates.add( new File( home +"/Library/Application Support/FasterThanLight" ) );
		}

		File result = null;

		for ( File candidate : candidates ) {
			if ( candidate.isDirectory() && candidate.exists() ) {
				result = candidate;
				break;
			}
		}

		return result;
	}
}
