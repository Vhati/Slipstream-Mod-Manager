package net.vhati.modmanager.ui;

import java.awt.Frame;
import java.io.File;
import javax.swing.JDialog;
import javax.swing.SwingUtilities;

import net.vhati.modmanager.core.ModPatchObserver;


public class ModPatchDialog extends ProgressDialog implements ModPatchObserver {


	public ModPatchDialog( Frame owner, boolean continueOnSuccess ) {
		super( owner, true );
		this.setTitle( "Patching..." );

		this.setSize( 400, 160 );
		this.setMinimumSize( this.getPreferredSize() );
		this.setLocationRelativeTo( owner );
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
		this.setProgressLater( value, max );
	}

	/**
	 * Non-specific activity.
	 *
	 * @param message a string, or null
	 */
	@Override
	public void patchingStatus( final String message ) {
		setStatusTextLater( message != null ? message : "..." );
	}

	/**
	 * A mod is about to be processed.
	 */
	@Override
	public void patchingMod( final File modFile ) {
		setStatusTextLater( String.format( "Installing mod \"%s\"...", modFile.getName() ) );
	}

	/**
	 * Patching ended.
	 *
	 * If anything went wrong, e may be non-null.
	 */
	@Override
	public void patchingEnded( boolean outcome, Throwable e ) {
		setTaskOutcomeLater( outcome, e );
	}


	@Override
	protected void setTaskOutcome( boolean outcome, Throwable e ) {
		super.setTaskOutcome( outcome, e );
		if ( !this.isShowing() ) return;

		if ( succeeded == true )
			setStatusText( "Patching completed." );
		else
			setStatusText( String.format( "Patching failed: %s", e ) );
	}
}
