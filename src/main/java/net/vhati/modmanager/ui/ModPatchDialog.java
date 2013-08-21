package net.vhati.modmanager.ui;

import java.awt.BorderLayout;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

import net.vhati.modmanager.core.ModPatchObserver;


public class ModPatchDialog extends JDialog implements ActionListener, ModPatchObserver {

	private JProgressBar progressBar;
	private JTextArea statusArea;
	private JButton continueBtn;

	private boolean done = false;
	private boolean patchingSucceeded = false;
	private Runnable successTask = null;


	public ModPatchDialog( Frame owner ) {
		super( owner, "Patching...", true );
		this.setDefaultCloseOperation( JDialog.DO_NOTHING_ON_CLOSE );

		progressBar = new JProgressBar();
		progressBar.setBorderPainted( true );

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
		this.setLocationRelativeTo( owner );
	}


	@Override
	public void actionPerformed( ActionEvent e ) {
		Object source = e.getSource();

		if ( source == continueBtn ) {
			this.setVisible( false );
			this.dispose();

			if ( done &&  patchingSucceeded && successTask != null ) {
				successTask.run();
			}
		}
	}


	private void setStatusText( String message ) {
		statusArea.setText( message != null ? message : "..." );
	}


	/**
	 * Updates the progress bar.
	 *
	 * If either arg is -1, the bar will become indeterminate.
	 *
	 * @param value the new value
	 * @param max the new maximum
	 */
	@Override
	public void patchingProgress( final int value, final int max ) {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				if ( value >= 0 && max >= 0 ) {
					if ( progressBar.isIndeterminate() )
						progressBar.setIndeterminate( false );

					if ( progressBar.getMaximum() != max ) {
						progressBar.setValue( 0 );
						progressBar.setMaximum( max );
					}
					progressBar.setValue( value );
				}
				else {
					if ( !progressBar.isIndeterminate() )
						progressBar.setIndeterminate( true );
					progressBar.setValue( 0 );
				}
			}
		});
	}

	/**
	 * Non-specific activity.
	 *
	 * @param message a string, or null
	 */
	@Override
	public void patchingStatus( final String message ) {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				setStatusText( message != null ? message : "..." );
			}
		});
	}

	/**
	 * A mod is about to be processed.
	 */
	@Override
	public void patchingMod( final File modFile ) {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				setStatusText( String.format( "Installing mod \"%s\"...", modFile.getName() ) );
			}
		});
	}

	/**
	 * Patching ended.
	 *
	 * If anything went wrong, e may be non-null.
	 */
	@Override
	public void patchingEnded( final boolean success, final Exception e ) {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				if ( success )
					setStatusText( "Patching completed." );
				else
					setStatusText( String.format( "Patching failed: %s", e ) );

				done = true;
				patchingSucceeded = success;

				continueBtn.setEnabled( true );
			}
		});
	}


	/**
	 * Sets a runnable to trigger after patching successfully.
	 */
	public void setSuccessTask( Runnable r ) {
		successTask = r;
	}
}
