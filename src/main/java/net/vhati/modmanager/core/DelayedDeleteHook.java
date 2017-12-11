package net.vhati.modmanager.core;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Iterator;
import java.util.List;


/**
 * A shutdown hook that waits on threads before deleting files.
 *
 * This hook's waiting will keep the VM alive until the threads complete.
 *
 * Usage:
 *   DelayedDeleteHook deleteHook = new DelayedDeleteHook();
 *   Runtime.getRuntime().addShutdownHook( deleteHook );
 */
public class DelayedDeleteHook extends Thread {

	private LinkedHashSet<Thread> watchedThreads = new LinkedHashSet<Thread>();
	private LinkedHashSet<File> doomedFiles = new LinkedHashSet<File>();


	public synchronized void addWatchedThread( Thread t ) {
		if ( watchedThreads == null )
			throw new IllegalStateException( "Shutdown in progress" );
		watchedThreads.add( t );
	}

	public synchronized void addDoomedFile( File f ) {
		if ( doomedFiles == null )
			throw new IllegalStateException( "Shutdown in progress" );
		doomedFiles.add( f );
	}

	@Override
	public void run() {
		ArrayList<Thread> pendingThreads;
		ArrayList<File> pendingFiles;
		boolean interrupted = false;

		synchronized ( this ) {
			pendingThreads = new ArrayList<Thread>( watchedThreads );
			pendingFiles = new ArrayList<File>( doomedFiles );
			watchedThreads = null;
			doomedFiles = null;
		}

		try {
			// Wait on each thread.
			Iterator<Thread> it = pendingThreads.iterator();
			while ( it.hasNext() ) {
				Thread t = it.next();
				while ( t.isAlive() ) {
					try {
						t.join();
					}
					catch ( InterruptedException e ) {
						interrupted = true;
					}
				}
				it.remove();
			}

			Collections.reverse( pendingFiles );
			for ( File f : pendingFiles ) {
				f.delete();
			}
		}
		finally {
			if ( interrupted ) Thread.currentThread().interrupt();
		}
	}
}
