package net.vhati.modmanager.ui;

import java.awt.Frame;
import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.util.List;
import javax.swing.JDialog;
import javax.swing.SwingUtilities;

import net.vhati.ftldat.AbstractPack;
import net.vhati.ftldat.FolderPack;
import net.vhati.ftldat.FTLPack;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


public class DatExtractDialog extends ProgressDialog {

	private static final Logger log = LogManager.getLogger( DatExtractDialog.class );

	private boolean started = false;

	private File extractDir;
	private File[] datFiles;

	private DatExtractThread workerThread = null;


	public DatExtractDialog( Frame owner, File extractDir, File[] datFiles ) {
		super( owner, false );
		this.setTitle( "Extracting..." );

		this.extractDir = extractDir;
		this.datFiles = datFiles;

		this.setSize( 400, 160 );
		this.setMinimumSize( this.getPreferredSize() );
		this.setLocationRelativeTo( owner );

		workerThread = new DatExtractThread( extractDir, datFiles );
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

		private File extractDir;
		private File[] datFiles;

		public DatExtractThread( File extractDir, File[] datFiles ) {
			this.extractDir = extractDir;
			this.datFiles = datFiles;
		}

		@Override
		public void run() {
			AbstractPack srcP = null;
			AbstractPack dstP = null;
			InputStream is = null;
			int progress = 0;

			try {
				if ( !extractDir.exists() ) extractDir.mkdirs();

				dstP = new FolderPack( extractDir );

				for ( File datFile : datFiles ) {
					srcP = new FTLPack( datFile, "r" );
					progress = 0;
					List<String> innerPaths = srcP.list();
					setProgressLater( progress, innerPaths.size() );

					for ( String innerPath : innerPaths ) {
						setStatusTextLater( innerPath );
						if ( dstP.contains( innerPath ) ) {
							log.info( "While extracting resources, this file was overwritten: "+ innerPath );
							dstP.remove( innerPath );
						}
						is = srcP.getInputStream( innerPath );
						dstP.add( innerPath, is );
						setProgressLater( progress++ );
					}
					srcP.close();
				}
				setTaskOutcomeLater( true, null );
			}
			catch ( Exception e ) {
				log.error( "Error extracting dats.", e );
				setTaskOutcomeLater( false, e );
			}
			finally {
				try {if ( is != null ) is.close();}
				catch ( IOException e ) {}

				try {if ( srcP != null ) srcP.close();}
				catch ( IOException e ) {}

				try {if ( dstP != null ) dstP.close();}
				catch ( IOException e ) {}
			}
		}
	}
}
