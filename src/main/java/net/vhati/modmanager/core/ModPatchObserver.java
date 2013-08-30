package net.vhati.modmanager.core;

import java.io.File;


public interface ModPatchObserver {

	/**
	 * Updates a progress bar.
	 *
	 * If either arg is -1, the bar will become indeterminate.
	 *
	 * @param value the new value
	 * @param max the new maximum
	 */
	public void patchingProgress( final int value, final int max );

	/**
	 * Non-specific activity.
	 *
	 * @param message a string, or null
	 */
	public void patchingStatus( String message );

	/**
	 * A mod is about to be processed.
	 */
	public void patchingMod( File modFile );

	/**
	 * Patching ended.
	 * If anything went wrong, e may be non-null.
	 */
	public void patchingEnded( boolean outcome, Exception e );
}
