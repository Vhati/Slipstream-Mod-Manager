package net.vhati.modmanager.core;

import java.io.File;

public interface ModsScanObserver {

	/**
	 * A file's hash has been calculated.
	 */
	public void hashCalculated(File f, String hash);

	/**
	 * A new ModDB of cached metadata is ready to use.
	 */
	public void localModDBUpdated(ModDB newDB);

	/**
	 * Mods scanning ended.
	 */
	public void modsScanEnded();
}
