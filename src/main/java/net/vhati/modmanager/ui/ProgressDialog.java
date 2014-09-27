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
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;


public class ProgressDialog extends JDialog implements ActionListener {

	protected JScrollPane statusScroll;
	protected JProgressBar progressBar;
	protected JTextArea statusArea;
	protected JButton continueBtn;

	protected boolean continueOnSuccess = false;
	protected boolean done = false;
	protected boolean succeeded = false;
	protected Runnable successTask = null;


	public ProgressDialog( Frame owner, boolean continueOnSuccess ) {
		super( owner, true );
		this.setDefaultCloseOperation( JDialog.DO_NOTHING_ON_CLOSE );

		this.continueOnSuccess = continueOnSuccess;

		progressBar = new JProgressBar();
		progressBar.setBorderPainted( true );

		JPanel progressHolder = new JPanel( new BorderLayout() );
		progressHolder.setBorder( BorderFactory.createEmptyBorder( 10, 15, 0, 15 ) );
		progressHolder.add( progressBar );
		getContentPane().add( progressHolder, BorderLayout.NORTH );

		statusArea = new JTextArea();
		statusArea.setLineWrap( true );
		statusArea.setWrapStyleWord( true );
		statusArea.setFont( statusArea.getFont().deriveFont( 13f ) );
		statusArea.setEditable( false );
		statusScroll = new JScrollPane( statusArea );

		JPanel statusHolder = new JPanel( new BorderLayout() );
		statusHolder.setBorder( BorderFactory.createEmptyBorder( 15, 15, 15, 15 ) );
		statusHolder.add( statusScroll );
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


	@Override
	public void actionPerformed( ActionEvent e ) {
		Object source = e.getSource();

		if ( source == continueBtn ) {
			this.setVisible( false );
			this.dispose();

			if ( done &&  succeeded && successTask != null ) {
				successTask.run();
			}
		}
	}


	/**
	 * Updates the text area's content. (Thread-safe)
	 *
	 * @param message a string, or null
	 */
	public void setStatusTextLater( final String message ) {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				setStatusText( message != null ? message : "..." );
			}
		});
	}

	protected void setStatusText( String message ) {
		statusArea.setText( message != null ? message : "..." );
		statusArea.setCaretPosition( 0 );
	}


	/**
	 * Updates the progress bar. (Thread-safe)
	 *
	 * If the arg is -1, the bar will become indeterminate.
	 *
	 * @param value the new value
	 */
	public void setProgressLater( final int value ) {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				if ( value >= 0 ) {
					if ( progressBar.isIndeterminate() )
						progressBar.setIndeterminate( false );

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
	 * Updates the progress bar. (Thread-safe)
	 *
	 * If either arg is -1, the bar will become indeterminate.
	 *
	 * @param value the new value
	 * @param max the new maximum
	 */
	public void setProgressLater( final int value, final int max ) {
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
	 * Triggers a response to the immediate task ending. (Thread-safe)
	 *
	 * If anything went wrong, e may be non-null.
	 */
	public void setTaskOutcomeLater( final boolean success, final Exception e ) {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				setTaskOutcome( success, e );
			}
		});
	}

	protected void setTaskOutcome( final boolean outcome, final Exception e ) {
		done = true;
		succeeded = outcome;

		if ( !ProgressDialog.this.isShowing() ) {
			// The window's not visible, no continueBtn to click.
			ProgressDialog.this.dispose();

			if ( succeeded && successTask != null ) {
				successTask.run();
			}
		}
		if ( continueOnSuccess && succeeded && successTask != null ) {
			ProgressDialog.this.setVisible( false );
			ProgressDialog.this.dispose();
			successTask.run();
		}
		else {
			continueBtn.setEnabled( true );
			continueBtn.requestFocusInWindow();
		}
	}


	/**
	 * Sets a runnable to trigger after the immediate task ends successfully.
	 */
	public void setSuccessTask( Runnable r ) {
		successTask = r;
	}

	/**
	 * Shows or hides this component depending on the value of parameter b.
	 *
	 * If the immediate task has already completed,
	 * this method will do nothing.
	 */
	public void setVisible( boolean b ) {
		if ( !done ) super.setVisible( b );
	}
}
