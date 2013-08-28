package net.vhati.modmanager.ui;

import java.awt.BorderLayout;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

import net.vhati.ftldat.FTLDat;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


public class DatExtractDialog extends JDialog implements ActionListener {

	private static final Logger log = LogManager.getLogger(DatExtractDialog.class);

	private JProgressBar progressBar;
	private JTextArea statusArea;
	private JButton continueBtn;

	private boolean extracting = false;
	private boolean done = false;

	private File extractDir;
	private File[] datFiles;


	DatExtractDialog( Frame owner, File extractDir, File[] datFiles ) {
		super( owner, "Extracting...", true );
		this.setDefaultCloseOperation( JDialog.DO_NOTHING_ON_CLOSE );

		this.extractDir = extractDir;
		this.datFiles = datFiles;

		progressBar = new JProgressBar();
		progressBar.setBorderPainted( true );
		progressBar.setStringPainted( false );

		JPanel progressHolder = new JPanel( new BorderLayout() );
		progressHolder.setBorder( BorderFactory.createEmptyBorder( 10, 15, 0, 15 ) );
		progressHolder.add( progressBar );
		getContentPane().add( progressHolder, BorderLayout.NORTH );

		statusArea = new JTextArea();
		statusArea.setBorder( BorderFactory.createEtchedBorder() );
		statusArea.setLineWrap( true );
		statusArea.setWrapStyleWord( true );
		statusArea.setEditable( false );

		JPanel statusHolder = new JPanel( new BorderLayout() );
		statusHolder.setBorder( BorderFactory.createEmptyBorder( 15, 15, 15, 15 ) );
		statusHolder.add( statusArea );
		getContentPane().add( statusHolder, BorderLayout.CENTER );

		continueBtn = new JButton( "Continue" );
		continueBtn.setEnabled( false );
		continueBtn.addActionListener( this );

		JPanel continueHolder = new JPanel();
		continueHolder.setLayout( new BoxLayout( continueHolder, BoxLayout.X_AXIS ) );
		continueHolder.setBorder( BorderFactory.createEmptyBorder( 0, 0, 10, 0 ) );
		continueHolder.add( Box.createHorizontalGlue() );
		continueHolder.add( continueBtn );
		continueHolder.add( Box.createHorizontalGlue() );
		getContentPane().add( continueHolder, BorderLayout.SOUTH );

		this.setSize( 400, 160 );
		this.setMinimumSize( this.getPreferredSize() );
		this.setLocationRelativeTo( owner );
	}

	/**
	 * Starts the background extraction thread.
	 */
	public void extract() {
		if ( extracting ) return;

		DatExtractThread t = new DatExtractThread( extractDir, datFiles );
		t.start();
	}


	@Override
	public void actionPerformed( ActionEvent e ) {
		Object source = e.getSource();

		if ( source == continueBtn ) {
			this.setVisible( false );
			this.dispose();
		}
	}


	private void setStatusText( String message ) {
		statusArea.setText( message != null ? message : "..." );
	}

	private void setProgress( final int n ) {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				progressBar.setValue( n );
			}
		});
	}

	private void setMaximum( final int n ) {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				progressBar.setMaximum( n );
			}
		});
	}

	public void extractingInnerPath( final String innerPath ) {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				setStatusText( innerPath );
			}
		});
	}

	public void extractingEnded( final boolean success, final Exception e ) {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				if ( success )
					setStatusText( "All resources extracted successfully." );
				else
					setStatusText( String.format( "Error extracting dats: %s", e ) );

				done = true;
				continueBtn.setEnabled( true );

				if ( !DatExtractDialog.this.isShowing() ) {
					// The window's not visible, no continueBtn to click.
					DatExtractDialog.this.dispose();
				}
			}
		});
	}


	/**
	 * Shows or hides this component depending on the value of parameter b.
	 *
	 * If extracting has already completed, this method will do nothing.
	 */
	public void setVisible( boolean b ) {
		if ( !done ) super.setVisible( b );
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
					setProgress( progress );
					List<String> innerPaths = srcP.list();
					setMaximum( innerPaths.size() );

					for ( String innerPath : innerPaths ) {
						extractingInnerPath( innerPath );
						if ( dstP.contains( innerPath ) ) {
							log.info( "While extracting resources, this file was overwritten: "+ innerPath );
							dstP.remove( innerPath );
						}
						is = srcP.getInputStream( innerPath );
						dstP.add( innerPath, is );
						setProgress( progress++ );
					}
					srcP.close();
				}
				extractingEnded( true, null );
			}
			catch ( Exception ex ) {
				log.error( "Error extracting dats.", ex );
				extractingEnded( false, ex );
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
