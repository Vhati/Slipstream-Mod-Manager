package net.vhati.modmanager.ui;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.locks.Lock;
import javax.swing.SwingUtilities;

import net.vhati.modmanager.core.AutoUpdateInfo;
import net.vhati.modmanager.core.ModDB;
import net.vhati.modmanager.core.SlipstreamConfig;
import net.vhati.modmanager.json.JacksonAutoUpdateReader;
import net.vhati.modmanager.json.JacksonCatalogReader;
import net.vhati.modmanager.json.URLFetcher;
import net.vhati.modmanager.ui.ManagerFrame;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


/**
 * Performs I/O-related setup for ManagerFrame in the background.
 *
 * Reads cached local metadata.
 * Rescans the "mods/" folder.
 * Reads saved catalog, and redownloads if stale.
 * Reads saved info about app updates, and redownloads if stale.
 */
public class ManagerInitThread extends Thread {

	private static final Logger log = LogManager.getLogger(ManagerInitThread.class);

	private final ManagerFrame frame;
	private final SlipstreamConfig appConfig;
	private final File modorderFile;
	private final File metadataFile;
	private final File catalogFile;
	private final File catalogETagFile;
	private final File appUpdateFile;
	private final File appUpdateETagFile;


	public ManagerInitThread( ManagerFrame frame, SlipstreamConfig appConfig, File modorderFile, File metadataFile, File catalogFile, File catalogETagFile, File appUpdateFile, File appUpdateETagFile ) {
		this.frame = frame;
		this.appConfig = appConfig;
		this.modorderFile = modorderFile;
		this.metadataFile = metadataFile;
		this.catalogFile = catalogFile;
		this.catalogETagFile = catalogETagFile;
		this.appUpdateFile = appUpdateFile;
		this.appUpdateETagFile = appUpdateETagFile;
	}


	@Override
	public void run() {
		try {
			init();
		}
		catch ( Exception e ) {
			log.error( "Error during ManagerFrame init.", e );
		}
	}


	private void init() throws InterruptedException {
		if ( metadataFile.exists() ) {
			// Load cached metadata first, before scanning for new info.
			ModDB cachedDB = JacksonCatalogReader.parse( metadataFile );
			if ( cachedDB != null ) frame.setLocalModDB( cachedDB );
		}

		final List<String> preferredOrder = loadModOrder();

		Lock managerLock = frame.getLock();
		managerLock.lock();
		try {
			SwingUtilities.invokeLater(new Runnable() {
				@Override
				public void run() { frame.rescanMods( preferredOrder ); }
			});

			// Wait until notified that "mods/" has been scanned.
			while ( frame.isScanning() ) {
				frame.getScanEndedCondition().await();
			}
		}
		finally {
			managerLock.unlock();
		}

		int catalogUpdateInterval = appConfig.getPropertyAsInt( "update_catalog", 0 );
		boolean needNewCatalog = false;

		if ( catalogFile.exists() ) {
			// Load the catalog first, before updating.
			reloadCatalog();

			if ( catalogUpdateInterval > 0 ) {
				// Check if the downloaded catalog is stale.
				if ( isFileStale( catalogFile, catalogUpdateInterval ) ) {
					log.debug( String.format( "Catalog is older than %d days.", catalogUpdateInterval ) );
					needNewCatalog = true;
				} else {
					log.debug( "Catalog isn't stale yet." );
				}
			}
		}
		else {
			// Catalog file doesn't exist.
			needNewCatalog = true;
		}

		// Don't update if the user doesn't want to.
		if ( catalogUpdateInterval <= 0 ) needNewCatalog = false;

		if ( needNewCatalog ) {
			boolean fetched = URLFetcher.refetchURL( ManagerFrame.CATALOG_URL, catalogFile, catalogETagFile );
			if ( fetched && catalogFile.exists() ) {
				reloadCatalog();
			}
		}

		int appUpdateInterval = appConfig.getPropertyAsInt( "update_app", 0 );
		boolean needAppUpdate = false;

		if ( appUpdateFile.exists() ) {
			// Load the info first, before downloading.
			reloadAppUpdateInfo();

			if ( appUpdateInterval > 0 ) {
				// Check if the app update info is stale.
				if ( isFileStale( appUpdateFile, catalogUpdateInterval ) ) {
					log.debug( String.format( "App update info is older than %d days.", appUpdateInterval ) );
					needAppUpdate = true;
				} else {
					log.debug( "App update info isn't stale yet." );
				}
			}
		}
		else {
			// App update file doesn't exist.
			needAppUpdate = true;
		}

		// Don't update if the user doesn't want to.
		if ( appUpdateInterval <= 0 ) needAppUpdate = false;

		if ( needAppUpdate ) {
			boolean fetched = URLFetcher.refetchURL( ManagerFrame.APP_UPDATE_URL, appUpdateFile, appUpdateETagFile );
			if ( fetched && appUpdateFile.exists() ) {
				reloadAppUpdateInfo();
			}
		}
	}


	/**
	 * Reads modorder.txt and returns a list of mod names in preferred order.
	 */
	private List<String> loadModOrder() {
		List<String> result = new ArrayList<String>();

		FileInputStream is = null;
		try {
			is = new FileInputStream( modorderFile );
			BufferedReader br = new BufferedReader(new InputStreamReader( is, Charset.forName("UTF-8") ));
			String line;
			while ( (line = br.readLine()) != null ) {
				result.add( line );
			}
		}
		catch ( FileNotFoundException e ) {
		}
		catch ( IOException e ) {
			log.error( String.format( "Error reading \"%s\".", modorderFile.getName() ), e );
		}
		finally {
			try {if ( is != null ) is.close();}
			catch ( Exception e ) {}
		}

		return result;
	}


	private void reloadCatalog() {
		ModDB currentDB = JacksonCatalogReader.parse( catalogFile );
		if ( currentDB != null ) frame.setCatalogModDB( currentDB );
	}

	private void reloadAppUpdateInfo() {
		AutoUpdateInfo aui = JacksonAutoUpdateReader.parse( appUpdateFile );
		if ( aui != null ) frame.setAppUpdateInfo( aui );
	}


	/**
	 * Returns true if a file is older than N days.
	 */
	private boolean isFileStale( File f, int maxDays ) {
		Date modifiedDate = new Date( f.lastModified() );
		Calendar cal = Calendar.getInstance();
		cal.add( Calendar.DATE, maxDays * -1 );
		return modifiedDate.before( cal.getTime() );
	}
}
