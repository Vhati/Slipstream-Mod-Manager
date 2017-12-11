package net.vhati.modmanager.cli;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.ParseException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.vhati.ftldat.AbstractPack;
import net.vhati.ftldat.FolderPack;
import net.vhati.ftldat.PkgPack;
import net.vhati.ftldat.FTLPack;
import net.vhati.modmanager.FTLModManager;
import net.vhati.modmanager.core.DelayedDeleteHook;
import net.vhati.modmanager.core.FTLUtilities;
import net.vhati.modmanager.core.ModPatchObserver;
import net.vhati.modmanager.core.ModPatchThread;
import net.vhati.modmanager.core.ModUtilities;
import net.vhati.modmanager.core.Report;
import net.vhati.modmanager.core.Report.ReportFormatter;
import net.vhati.modmanager.core.Report.ReportMessage;
import net.vhati.modmanager.core.SlipstreamConfig;


public class SlipstreamCLI {

	private static final Logger log = LogManager.getLogger(SlipstreamCLI.class);

	private static File backupDir = new File( "./backup/" );
	private static File modsDir = new File( "./mods/" );

	private static Thread.UncaughtExceptionHandler exceptionHandler = null;


	public static void main( String[] args ) {

		exceptionHandler = new Thread.UncaughtExceptionHandler() {
			@Override
			public void uncaughtException( Thread t, Throwable e ) {
				log.error( "Uncaught exception in thread: "+ t.toString(), e );
				System.exit( 1 );
			}
		};

		BasicParser parser = new BasicParser();

		Options options = new Options();
		options.addOption( OptionBuilder.withLongOpt( "extract-dats" )
		                                .withDescription( "extract FTL resources into a dir" )
		                                .hasArg()
		                                .withArgName("DIR")
		                                .create() );
		options.addOption( OptionBuilder.withLongOpt( "global-panic" )
		                                .withDescription( "patch as if advanced find tags had panic='true'" )
		                                .create() );
		options.addOption( OptionBuilder.withLongOpt( "list-mods" )
		                                .withDescription( "list available mod names" )
		                                .create() );
		options.addOption( OptionBuilder.withLongOpt( "runftl" )
		                                .withDescription( "run the game (standalone or with 'patch')" )
		                                .create() );
		options.addOption( OptionBuilder.withLongOpt( "patch" )
		                                .withDescription( "revert to vanilla and add named mods (if any)" )
		                                .create() );
		options.addOption( OptionBuilder.withLongOpt( "validate" )
		                                .withDescription( "check named mods for problems" )
		                                .create() );
		options.addOption( "h", "help", false, "display this help and exit" );
		options.addOption( OptionBuilder.withLongOpt( "version" )
		                                .withDescription( "output version information and exit" )
		                                .create() );
		CommandLine cmdline = null;
		try {
			cmdline = parser.parse( options, args, true );
		}
		catch( ParseException e ) {
			System.err.println( "Error parsing commandline: "+ e.getMessage() );
			System.exit( 1 );
		}

		if ( cmdline.hasOption( "h" ) ) {  // Exits.
			HelpFormatter formatter = new HelpFormatter();

			String helpHeader = "Perform actions against an FTL installation and/or a list of named mods."+ formatter.getNewLine();

			String helpFooter = formatter.getNewLine();
			helpFooter += "Each MODFILE is a filename in the mods/ dir."+ formatter.getNewLine();
			helpFooter += "If a named mod is a directory, a temporary zip will be created.";

			formatter.printHelp( "modman [OPTION] [MODFILE]...", helpHeader, options, helpFooter );
			System.exit( 0 );
		}
		if ( cmdline.hasOption( "version" ) ) {  // Exits.
			System.out.println( getVersionMessage() );
			System.exit( 0 );
		}

		DelayedDeleteHook deleteHook = new DelayedDeleteHook();
		Runtime.getRuntime().addShutdownHook( deleteHook );

		if ( cmdline.hasOption( "validate" ) ) {  // Exits (0/1).
			log.info( "Validating..." );

			StringBuilder resultBuf = new StringBuilder();
			ReportFormatter formatter = new ReportFormatter();
			boolean anyInvalid = false;

			for ( String modFileName : cmdline.getArgs() ) {
				File modFile = new File( modsDir, modFileName );

				if ( modFile.isDirectory() ) {
					log.info( String.format( "Zipping dir: %s/", modFile.getName() ) );
					try {
						modFile = createTempMod( modFile );
						deleteHook.addDoomedFile( modFile );
					}
					catch ( IOException e ) {
						log.error( String.format( "Error zipping dir: %s/", modFile.getName() ), e );

						List<ReportMessage> tmpMessages = new ArrayList<ReportMessage>();
						tmpMessages.add( new ReportMessage( ReportMessage.SECTION, modFileName ) );
						tmpMessages.add( new ReportMessage( ReportMessage.EXCEPTION, e.getMessage() ) );

						formatter.format( tmpMessages, resultBuf, 0 );
						resultBuf.append( "\n" );

						anyInvalid = true;
						continue;
					}
				}

				Report validateReport = ModUtilities.validateModFile( modFile );

				formatter.format( validateReport.messages, resultBuf, 0 );
				resultBuf.append( "\n" );

				if ( validateReport.outcome == false ) anyInvalid = true;
			}
			if ( resultBuf.length() == 0 ) {
				resultBuf.append( "No mods were checked." );
			}

			System.out.println();
			System.out.println( resultBuf.toString() );
			System.exit( anyInvalid ? 1 : 0 );
		}

		File configFile = new File( "modman.cfg" );
		SlipstreamConfig appConfig = getConfig( configFile );

		if ( cmdline.hasOption( "list-mods" ) ) {  // Exits.
			log.info( "Listing mods..." );

			boolean allowZip = appConfig.getProperty( SlipstreamConfig.ALLOW_ZIP, "false" ).equals( "true" );
			File[] modFiles = modsDir.listFiles( new ModAndDirFileFilter( allowZip, true ) );
			List<String> dirList = new ArrayList<String>();
			List<String> fileList = new ArrayList<String>();
			for ( File f : modFiles ) {
				if ( f.isDirectory() )
					dirList.add( f.getName() +"/" );
				else
					fileList.add( f.getName() );
			}
			Collections.sort( dirList );
			Collections.sort( fileList );
			for ( String s : dirList ) System.out.println( s );
			for ( String s : fileList ) System.out.println( s );

			System.exit( 0 );
		}

		File datsDir = null;
		if ( cmdline.hasOption( "extract-dats" ) ||
		     cmdline.hasOption( "patch" ) ||
		     cmdline.hasOption( "runftl" ) ) {
			datsDir = getDatsDir( appConfig );
		}

		if ( cmdline.hasOption( "extract-dats" ) ) {  // Exits (0/1).
			log.info( "Extracting dats..." );

			String extractPath = cmdline.getOptionValue( "extract-dats" );
			File extractDir = new File( extractPath );

			FolderPack dstPack = null;
			List<AbstractPack> srcPacks = new ArrayList<AbstractPack>( 2 );
			InputStream is = null;
			try {
				File ftlDatFile = new File( datsDir, "ftl.dat" );
				File dataDatFile = new File( datsDir, "data.dat" );
				File resourceDatFile = new File( datsDir, "resource.dat" );

				if ( ftlDatFile.exists() ) {  // FTL 1.6.1.
					AbstractPack ftlPack = new PkgPack( ftlDatFile, "r" );
					srcPacks.add( ftlPack );
				}
				else if ( dataDatFile.exists() && resourceDatFile.exists() ) {  // FTL 1.01-1.5.13.
					AbstractPack dataPack = new FTLPack( dataDatFile, "r" );
					AbstractPack resourcePack = new FTLPack( resourceDatFile, "r" );
					srcPacks.add( dataPack );
					srcPacks.add( resourcePack );
				}
				else {
					throw new FileNotFoundException( String.format( "Could not find either \"%s\" or both \"%s\" and \"%s\"", ftlDatFile.getName(), dataDatFile.getName(), resourceDatFile.getName() ) );
				}

				if ( !extractDir.exists() ) extractDir.mkdirs();

				dstPack = new FolderPack( extractDir );

				for ( AbstractPack srcPack : srcPacks ) {
					List<String> innerPaths = srcPack.list();

					for ( String innerPath : innerPaths ) {
						if ( dstPack.contains( innerPath ) ) {
							log.info( "While extracting resources, this file was overwritten: "+ innerPath );
							dstPack.remove( innerPath );
						}
						is = srcPack.getInputStream( innerPath );
						dstPack.add( innerPath, is );
					}
					srcPack.close();
				}
			}
			catch ( IOException e ) {
				log.error( "Error extracting dats", e );
				System.exit( 1 );
			}
			finally {
				try {if ( is != null ) is.close();}
				catch ( IOException ex ) {}

				try {if ( dstPack != null ) dstPack.close();}
				catch ( IOException ex ) {}

				for ( AbstractPack pack : srcPacks ) {
					try {pack.close();}
					catch ( IOException ex ) {}
				}
			}

			System.exit( 0 );
		}

		if ( cmdline.hasOption( "patch" ) ) {  // Exits sometimes (1 on failure).
			log.info( "Patching..." );

			List<File> modFiles = new ArrayList<File>();
			for ( String modFileName : cmdline.getArgs() ) {
				File modFile = new File( modsDir, modFileName );

				if ( modFile.isDirectory() ) {
					log.info( String.format( "Zipping dir: %s/", modFile.getName() ) );
					try {
						modFile = createTempMod( modFile );
						deleteHook.addDoomedFile( modFile );
					}
					catch ( IOException e ) {
						log.error( String.format( "Error zipping dir: %s/", modFile.getName() ), e );
						System.exit( 1 );
					}
				}

				modFiles.add( modFile );
			}

			boolean globalPanic = cmdline.hasOption( "global-panic" );

			SilentPatchObserver patchObserver = new SilentPatchObserver();
			ModPatchThread patchThread = new ModPatchThread( modFiles, datsDir, backupDir, globalPanic, patchObserver );
			patchThread.setDefaultUncaughtExceptionHandler( exceptionHandler );
			deleteHook.addWatchedThread( patchThread );

			patchThread.start();
			while ( patchThread.isAlive() ) {
				try {patchThread.join();}
				catch ( InterruptedException e ) {}
			}

			if ( !patchObserver.hasSucceeded() ) System.exit( 1 );
		}

		if ( cmdline.hasOption( "runftl" ) ) {  // Exits (0/1).
			log.info( "Running FTL..." );

			File exeFile = null;
			String[] exeArgs = null;

			// Try to run via Steam.
			if ( "true".equals( appConfig.getProperty( SlipstreamConfig.RUN_STEAM_FTL, "false" ) ) ) {

				String steamPath = appConfig.getProperty( SlipstreamConfig.STEAM_EXE_PATH );
				if ( steamPath.length() > 0 ) {
					exeFile = new File( steamPath );

					if ( exeFile.exists() ) {
						exeArgs = new String[] {"-applaunch", FTLUtilities.STEAM_APPID_FTL};
					}
					else {
						log.warn( String.format( "%s does not exist: %s", SlipstreamConfig.STEAM_EXE_PATH, exeFile.getAbsolutePath() ) );
						exeFile = null;
					}
				}

				if ( exeFile == null ) {
					log.warn( "Steam executable could not be found, so FTL will be launched directly" );
				}

			}
			// Try to run directly.
			if ( exeFile == null ) {
				exeFile = FTLUtilities.findGameExe( datsDir );

				if ( exeFile != null ) {
					exeArgs = new String[0];
				} else {
					log.warn( "FTL executable could not be found" );
				}
			}

			if ( exeFile != null ) {
				try {
					FTLUtilities.launchExe( exeFile, exeArgs );
				}
				catch ( Exception e ) {
					log.error( "Error launching FTL", e );
					System.exit( 1 );
				}
			}
			else {
				log.error( "No executables were found to launch FTL" );
				System.exit( 1 );
			}

			System.exit( 0 );
		}

		System.exit( 0 );
	}


	/**
	 * Loads settings from a config file.
	 *
	 * If an error occurs, it'll be logged,
	 * and default settings will be returned.
	 */
	private static SlipstreamConfig getConfig( File configFile ) {

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
				log.trace( "Loading properties from config file" );
				in = new FileInputStream( configFile );
				props.load( new InputStreamReader( in, "UTF-8" ) );
			}
		}
		catch ( IOException e ) {
			log.error( "Error loading config", e );
		}
		finally {
			try {if ( in != null ) in.close();}
			catch ( IOException e ) {}
		}

		SlipstreamConfig appConfig = new SlipstreamConfig( props, configFile );
		return appConfig;
	}


	/**
	 * Checks the validity of the config's dats path and returns it.
	 * Or exits if the path is invalid.
	 */
	private static File getDatsDir( SlipstreamConfig appConfig ) {
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
			log.error( "No FTL dats path previously set" );
		}
		if ( datsDir == null ) {
			log.error( "Run the GUI once, or edit the config file, and try again" );
			System.exit( 1 );
		}

		return datsDir;
	}


	/**
	 * Returns a temporary zip made from a directory.
	 *
	 * Empty subdirs will be omitted.
	 * The archive will be not be deleted on exit (handle that elsewhere).
	 */
	private static File createTempMod( File dir ) throws IOException {
		File tempFile = File.createTempFile( dir.getName() +"_temp-", ".zip" );

		FileOutputStream fos = null;
		try {
			fos = new FileOutputStream( tempFile );
			ZipOutputStream zos = new ZipOutputStream( new BufferedOutputStream( fos ) );
			addDirToArchive( zos, dir, null );
			zos.close();
		}
		finally {
			try {if ( fos != null ) fos.close();}
			catch ( IOException e ) {}
		}

		return tempFile;
	}

	private static void addDirToArchive( ZipOutputStream zos, File dir, String pathPrefix ) throws IOException {
		if ( pathPrefix == null ) pathPrefix = "";

		for ( File f : dir.listFiles() ) {
			if ( f.isDirectory() ) {
				addDirToArchive( zos, f, pathPrefix + f.getName() +"/" );
				continue;
			}

			FileInputStream is = null;
			try {
				is = new FileInputStream( f );
				zos.putNextEntry( new ZipEntry( pathPrefix + f.getName() ) );

				byte[] buf = new byte[4096];
				int len;
				while ( (len = is.read(buf)) >= 0 ) {
					zos.write( buf, 0, len );
				}

				zos.closeEntry();
			}
			finally {
				try {if ( is != null ) is.close();}
				catch ( IOException e ) {}
			}
		}
	}

	private static String getVersionMessage() {
		StringBuilder buf = new StringBuilder();
		buf.append( String.format( "%s %s\n", FTLModManager.APP_NAME, FTLModManager.APP_VERSION ) );
		buf.append( "Copyright (C) 2014 David Millis\n" );
		buf.append( "\n" );
		buf.append( "This program is free software; you can redistribute it and/or modify\n" );
		buf.append( "it under the terms of the GNU General Public License as published by\n" );
		buf.append( "the Free Software Foundation; version 2.\n" );
		buf.append( "\n" );
		buf.append( "This program is distributed in the hope that it will be useful,\n" );
		buf.append( "but WITHOUT ANY WARRANTY; without even the implied warranty of\n" );
		buf.append( "MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the\n" );
		buf.append( "GNU General Public License for more details.\n" );
		buf.append( "\n" );
		buf.append( "You should have received a copy of the GNU General Public License\n" );
		buf.append( "along with this program. If not, see http://www.gnu.org/licenses/.\n" );
		buf.append( "\n" );
		return buf.toString();
	}



	private static class SilentPatchObserver implements ModPatchObserver {
		private boolean done = false;
		private boolean succeeded = false;

		@Override
		public void patchingProgress( final int value, final int max ) {
		}

		@Override
		public void patchingStatus( String message ) {
		}

		@Override
		public void patchingMod( File modFile ) {
		}

		@Override
		public synchronized void patchingEnded( boolean outcome, Exception e ) {
			succeeded = outcome;
			done = true;
		}

		public synchronized boolean isDone() { return done; }
		public synchronized boolean hasSucceeded() { return succeeded; }
	}



	private static class ModAndDirFileFilter implements FileFilter {
		private boolean allowZip;
		private boolean allowDirs;

		public ModAndDirFileFilter( boolean allowZip, boolean allowDirs ) {
			this.allowZip = allowZip;
			this.allowDirs = allowDirs;
		}

		@Override
		public boolean accept( File f ) {
			if ( f.isDirectory() ) return allowDirs;

			if ( f.getName().endsWith(".ftl") ) return true;

			if ( allowZip ) {
				if ( f.getName().endsWith(".zip") ) return true;
			}
			return false;
		}
	}
}
