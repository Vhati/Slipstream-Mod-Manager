package net.vhati.modmanager.ui;

import java.awt.Frame;
import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.util.List;
import javax.swing.JDialog;
import javax.swing.SwingUtilities;

import net.vhati.ftldat.FTLDat;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


public class DatExtractDialog extends ProgressDialog {

	private static final Logger log = LogManager.getLogger(DatExtractDialog.class);

	private boolean started = false;

	private File extractDir;
	private File[] datFiles;


	public DatExtractDialog( Frame owner, File extractDir, File[] datFiles ) {
		super( owner, false );
		this.setTitle( "Extracting..." );

		this.extractDir = extractDir;
		this.datFiles = datFiles;

		this.setSize( 400, 160 );
		this.setMinimumSize( this.getPreferredSize() );
		this.setLocationRelativeTo( owner );
	}

	/**
	 * Starts the background extraction thread.
	 * Call this immediately before setVisible().
	 */
	public void extract() {
		if ( started ) return;

		DatExtractThread t = new DatExtractThread( extractDir, datFiles );
		t.start();
		started = true;
	}

	@Override
	protected void setTaskOutcome( boolean outcome, Exception e ) {
		super.setTaskOutcome( outcome, e );
		if ( !this.isShowing() ) return;

		if ( succeeded )
			setStatusText( "All resources extracted successfully." );
		else
			setStatusText( String.format( "Error extracting dats: %s", e ) );
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

			FTLDat.AbstractPack srcP = null;
			FTLDat.AbstractPack dstP = null;
			InputStream is = null;
			int progress = 0;
			try {
				if ( !extractDir.exists() ) extractDir.mkdirs();

				dstP = new FTLDat.FolderPack( extractDir );

				for ( File datFile : datFiles ) {
					srcP = new FTLDat.FTLPack( datFile, false );
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
			catch ( Exception ex ) {
				log.error( "Error extracting dats.", ex );
				setTaskOutcomeLater( false, ex );
			}
			finally {
				try {if ( is != null ) is.close();}
				catch ( IOException ex ) {}

				try {if ( srcP != null ) srcP.close();}
				catch ( IOException ex ) {}

				try {if ( dstP != null ) dstP.close();}
				catch ( IOException ex ) {}
			}
		}
	}
}
