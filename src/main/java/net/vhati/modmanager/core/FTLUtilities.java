package net.vhati.modmanager.core;

import java.awt.Component;
import java.io.File;
import java.io.IOException;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileFilter;


public class FTLUtilities {

	/**
	 * Confirms the FTL resources dir exists and contains the dat files.
	 */
	public static boolean isDatsDirValid( File d ) {
		if ( !d.exists() || !d.isDirectory() ) return false;
		if ( !new File(d, "data.dat").exists() ) return false;
		if ( !new File(d, "resource.dat").exists() ) return false;
		return true;
	}

	/**
	 * Returns the FTL resources dir, or null.
	 */
	public static File findDatsDir() {
		String steamPath = "Steam/steamapps/common/FTL Faster Than Light/resources";
		String gogPath = "GOG.com/Faster Than Light/resources";
		String humblePath = "FTL/resources";

		String xdgDataHome = System.getenv("XDG_DATA_HOME");
		if ( xdgDataHome == null )
			xdgDataHome = System.getProperty("user.home") +"/.local/share";

		File[] candidates = new File[] {
			// Windows - Steam
			new File( new File(""+System.getenv("ProgramFiles(x86)")), steamPath ),
			new File( new File(""+System.getenv("ProgramFiles")), steamPath ),
			// Windows - GOG
			new File( new File(""+System.getenv("ProgramFiles(x86)")), gogPath ),
			new File( new File(""+System.getenv("ProgramFiles")), gogPath ),
			// Windows - Humble Bundle
			new File( new File(""+System.getenv("ProgramFiles(x86)")), humblePath ),
			new File( new File(""+System.getenv("ProgramFiles")), humblePath ),
			// Linux - Steam
			new File( xdgDataHome +"/Steam/SteamApps/common/FTL Faster Than Light/data/resources" ),
			// OSX - Steam
			new File( System.getProperty("user.home") +"/Library/Application Support/Steam/SteamApps/common/FTL Faster Than Light/FTL.app/Contents/Resources" ),
			// OSX
			new File( "/Applications/FTL.app/Contents/Resources" )
		};

		File result = null;

		for ( File candidate : candidates ) {
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
		JOptionPane.showMessageDialog( parentComponent,  message, "Find FTL", JOptionPane.INFORMATION_MESSAGE );

		final JFileChooser fc = new JFileChooser();
		fc.setDialogTitle( "Find data.dat or FTL.app" );
		fc.addChoosableFileFilter(new FileFilter() {
			@Override
			public String getDescription() {
				return "FTL Data File - (FTL dir)/resources/data.dat";
			}
			@Override
			public boolean accept(File f) {
				return f.isDirectory() || f.getName().equals("data.dat") || f.getName().equals("FTL.app");
			}
		});
		fc.setMultiSelectionEnabled(false);

		if ( fc.showOpenDialog( parentComponent ) == JFileChooser.APPROVE_OPTION ) {
			File f = fc.getSelectedFile();
			if ( f.getName().equals("data.dat") ) {
				result = f.getParentFile();
			}
			else if ( f.getName().endsWith(".app") && f.isDirectory() ) {
				File contentsPath = new File(f, "Contents");
				if( contentsPath.exists() && contentsPath.isDirectory() && new File(contentsPath, "Resources").exists() )
					result = new File(contentsPath, "Resources");
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

		if ( System.getProperty("os.name").startsWith("Windows") ) {
			File ftlDir = datsDir.getParentFile();
			if ( ftlDir != null ) {
				File exeFile = new File( ftlDir, "FTLGame.exe" );
				if ( exeFile.exists() ) result = exeFile;
			}
		}
		else if ( System.getProperty("os.name").equals("Linux") ) {
			File ftlDir = datsDir.getParentFile();
			if ( ftlDir != null ) {
				File exeFile = new File( ftlDir, "FTL" );
				if ( exeFile.exists() ) result = exeFile;
			}
		}
		else if ( System.getProperty("os.name").contains("OS X") ) {
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
	 * Spawns the game (FTLGame.exe or FTL.app).
	 *
	 * @param exeFile see findGameExe()
	 * @return a Process object, or null
	 */
	public static Process launchGame( File exeFile ) throws IOException {
		if ( exeFile == null ) return null;

		Process result = null;
		ProcessBuilder pb = null;
		if ( System.getProperty("os.name").contains("OS X") ) {
			pb = new ProcessBuilder( "open", "-a", exeFile.getAbsolutePath() );
		} else {
			pb = new ProcessBuilder( exeFile.getAbsolutePath() );
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

		String xdgDataHome = System.getenv("XDG_DATA_HOME");
		if ( xdgDataHome == null )
			xdgDataHome = System.getProperty("user.home") +"/.local/share";

		File[] candidates = new File[] {
			// Windows XP
			new File( System.getProperty("user.home") +"/My Documents/My Games/FasterThanLight" ),
			// Windows Vista/7
			new File( System.getProperty("user.home") +"/Documents/My Games/FasterThanLight" ),
			// Linux
			new File( xdgDataHome +"/FasterThanLight" ),
			// OSX
			new File( System.getProperty("user.home") +"/Library/Application Support/FasterThanLight" )
		};

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
