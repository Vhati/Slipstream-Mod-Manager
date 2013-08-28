package net.vhati.modmanager.core;

import java.io.File;


public interface HashObserver {

	/**
	 * A file's hash has been calculated.
	 */
	public void hashCalculated( File f, String hash );

	/**
	 * Hashing ended.
	 */
	public void hashingEnded();
}
