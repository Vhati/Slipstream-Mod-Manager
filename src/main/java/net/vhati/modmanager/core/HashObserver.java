package net.vhati.modmanager.core;

import java.io.File;


public interface HashObserver {
	public void hashCalculated( File f, String hash );
}
