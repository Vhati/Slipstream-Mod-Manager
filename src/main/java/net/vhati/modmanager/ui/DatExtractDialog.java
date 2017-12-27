package net.vhati.modmanager.ui;

import java.awt.Frame;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JDialog;
import javax.swing.SwingUtilities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.vhati.ftldat.AbstractPack;
import net.vhati.ftldat.FolderPack;
import net.vhati.ftldat.FTLPack;
import net.vhati.ftldat.PkgPack;


public class DatExtractDialog extends ProgressDialog {

	private static final Logger log = LoggerFactory.getLogger( DatExtractDialog.class );

	private boolean started = false;

	private File extractDir = null;
	private File datsDir = null;

	private DatExtractThread workerThread = null;


	public DatExtractDialog( Frame owner, File extractDir, File datsDir ) {
		super( owner, false );
		this.setTitle( "Extracting..." );

		this.extractDir = extractDir;
		this.datsDir = datsDir;

		this.setSize( 400, 160 );
		this.setMinimumSize( this.getPreferredSize() );
		this.setLocationRelativeTo( owner );

		workerThread = new DatExtractThread( extractDir, datsDir );
	}

	/**
	 * Returns the worker thread that does the extracting.
	 *
	 * This method is provided so other classes can customize the thread
	 * before calling extract().
	 */
	public Thread getWorkerThread() {
		return workerThread;
	}

	/**
	 * Starts the background extraction thread.
	 * Call this immediately before setVisible().
	 */
	public void extract() {
		if ( started ) return;

		workerThread.start();
		started = true;
	}

	@Override
	protected void setTaskOutcome( boolean outcome, Exception e ) {
		super.setTaskOutcome( outcome, e );
		if ( !this.isShowing() ) return;

		if ( succeeded ) {
			setStatusText( "All resources extracted successfully." );
		} else {
			setStatusText( String.format( "Error extracting dats: %s", e ) );
		}
	}



	private class DatExtractThread extends Thread {

		private File extractDir = null;
		private File datsDir = null;


		public DatExtractThread( File extractDir, File datsDir ) {
			this.extractDir = extractDir;
			this.datsDir = datsDir;
		}

		@Override
		public void run() {
			AbstractPack dstPack = null;
			List<AbstractPack> srcPacks = new ArrayList<AbstractPack>( 2 );
			InputStream is = null;
			int progress = 0;

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
					progress = 0;
					List<String> innerPaths = srcPack.list();
					setProgressLater( progress, innerPaths.size() );

					for ( String innerPath : innerPaths ) {
						setStatusTextLater( innerPath );
						if ( dstPack.contains( innerPath ) ) {
							log.info( "While extracting resources, this file was overwritten: "+ innerPath );
							dstPack.remove( innerPath );
						}
						is = srcPack.getInputStream( innerPath );
						dstPack.add( innerPath, is );
						setProgressLater( progress++ );
					}
					srcPack.close();
				}
				setTaskOutcomeLater( true, null );
			}
			catch ( Exception e ) {
				log.error( "Error extracting dats", e );
				setTaskOutcomeLater( false, e );
			}
			finally {
				try {if ( is != null ) is.close();}
				catch ( IOException e ) {}

				try {if ( dstPack != null ) dstPack.close();}
				catch ( IOException e ) {}

				for ( AbstractPack pack : srcPacks ) {
					try {pack.close();}
					catch ( IOException ex ) {}
				}
			}
		}
	}
}
