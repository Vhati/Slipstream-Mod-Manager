package net.vhati.modmanager.ui;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.locks.Lock;
import javax.swing.SwingUtilities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.vhati.modmanager.core.AutoUpdateInfo;
import net.vhati.modmanager.core.ModDB;
import net.vhati.modmanager.core.ModFileInfo;
import net.vhati.modmanager.core.SlipstreamConfig;
import net.vhati.modmanager.json.JacksonAutoUpdateReader;
import net.vhati.modmanager.json.JacksonCatalogReader;
import net.vhati.modmanager.json.URLFetcher;
import net.vhati.modmanager.ui.table.ListState;

/**
 * Performs I/O-related setup for ManagerFrame in the background.
 *
 * Reads cached local metadata. Rescans the "mods/" folder. Reads saved catalog,
 * and redownloads if stale. Reads saved info about app updates, and redownloads
 * if stale.
 */
public class ManagerInitThread extends Thread {

	private static final Logger log = LoggerFactory.getLogger(ManagerInitThread.class);

	private final ManagerFrame frame;
	private final SlipstreamConfig appConfig;
	private final File modsDir;
	private final File modsTableStateFile;
	private final File metadataFile;
	private final File catalogFile;
	private final File catalogETagFile;
	private final File appUpdateFile;
	private final File appUpdateETagFile;

	public ManagerInitThread(ManagerFrame frame, SlipstreamConfig appConfig, File modsDir, File modsTableStateFile,
			File metadataFile, File catalogFile, File catalogETagFile, File appUpdateFile, File appUpdateETagFile) {
		super("init");
		this.frame = frame;
		this.appConfig = appConfig;
		this.modsDir = modsDir;
		this.modsTableStateFile = modsTableStateFile;
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
		} catch (Exception e) {
			log.error("Error during ManagerFrame init.", e);
		}
	}

	private void init() throws InterruptedException {

		if (metadataFile.exists()) {
			// Load cached metadata first, before scanning for new info.
			ModDB cachedDB = JacksonCatalogReader.parse(metadataFile);
			if (cachedDB != null)
				frame.setLocalModDB(cachedDB);
		}

		final ListState<ModFileInfo> tableState = loadModsTableState();

		Lock managerLock = frame.getLock();
		managerLock.lock();
		try {
			SwingUtilities.invokeLater(new Runnable() {
				@Override
				public void run() {
					frame.rescanMods(tableState);
				}
			});

			// Wait until notified that "mods/" has been scanned.
			while (frame.isScanning()) {
				frame.getScanEndedCondition().await();
			}
		} finally {
			managerLock.unlock();
		}

		int catalogUpdateInterval = appConfig.getPropertyAsInt("update_catalog", 0);
		boolean needNewCatalog = false;

		// Load the catalog first, before downloading.
		if (catalogFile.exists())
			reloadCatalog();

		if (catalogUpdateInterval > 0) {
			if (catalogFile.exists()) {
				// Check if the downloaded catalog is stale.
				if (isFileStale(catalogFile, catalogUpdateInterval)) {
					log.debug(String.format("Catalog is older than %d days", catalogUpdateInterval));
					needNewCatalog = true;
				} else {
					log.debug("Catalog isn't stale yet");
				}
			} else {
				// Catalog file doesn't exist.
				needNewCatalog = true;
			}
		}

		if (needNewCatalog) {
			boolean fetched = URLFetcher.refetchURL(ManagerFrame.CATALOG_URL, catalogFile, catalogETagFile);
			if (fetched && catalogFile.exists()) {
				reloadCatalog();
			}
		}

		// Load the cached info first, before downloading.
		if (appUpdateFile.exists())
			reloadAppUpdateInfo();

		int appUpdateInterval = appConfig.getPropertyAsInt(SlipstreamConfig.UPDATE_APP, 0);
		boolean needAppUpdate = false;

		if (appUpdateInterval > 0) {
			if (appUpdateFile.exists()) {
				// Check if the app update info is stale.
				if (isFileStale(appUpdateFile, appUpdateInterval)) {
					log.debug(String.format("App update info is older than %d days", appUpdateInterval));
					needAppUpdate = true;
				} else {
					log.debug("App update info isn't stale yet");
				}
			} else {
				// App update file doesn't exist.
				needAppUpdate = true;
			}
		}

		if (needAppUpdate) {
			boolean fetched = URLFetcher.refetchURL(ManagerFrame.APP_UPDATE_URL, appUpdateFile, appUpdateETagFile);
			if (fetched && appUpdateFile.exists()) {
				reloadAppUpdateInfo();
			}
		}
	}

	/**
	 * Reads modorder.txt and returns a list of mod names in preferred order.
	 */
	private ListState<ModFileInfo> loadModsTableState() {
		List<String> fileNames = new ArrayList<String>();

		BufferedReader br = null;
		try {
			FileInputStream is = new FileInputStream(modsTableStateFile);
			br = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")));
			String line;
			while ((line = br.readLine()) != null) {
				fileNames.add(line);
			}
		} catch (FileNotFoundException e) {
		} catch (IOException e) {
			log.error(String.format("Error reading \"%s\"", modsTableStateFile.getName()), e);
			fileNames.clear();
		} finally {
			try {
				if (br != null)
					br.close();
			} catch (Exception e) {
			}
		}

		ListState<ModFileInfo> result = new ListState<ModFileInfo>();

		for (String fileName : fileNames) {
			File modFile = new File(modsDir, fileName);
			ModFileInfo modFileInfo = new ModFileInfo(modFile);
			result.addItem(modFileInfo);
		}

		return result;
	}

	private void reloadCatalog() {
		final ModDB currentDB = JacksonCatalogReader.parse(catalogFile);
		if (currentDB != null) {
			SwingUtilities.invokeLater(new Runnable() {
				@Override
				public void run() {
					frame.setCatalogModDB(currentDB);
				}
			});
		}
	}

	private void reloadAppUpdateInfo() {
		final AutoUpdateInfo aui = JacksonAutoUpdateReader.parse(appUpdateFile);
		if (aui != null) {
			SwingUtilities.invokeLater(new Runnable() {
				@Override
				public void run() {
					frame.setAppUpdateInfo(aui);
				}
			});
		}
	}

	/**
	 * Returns true if a file is older than N days.
	 */
	private boolean isFileStale(File f, int maxDays) {
		Calendar fileCal = Calendar.getInstance();
		fileCal.setTimeInMillis(f.lastModified());
		fileCal.getTimeInMillis(); // Re-calculate calendar fields.

		Calendar freshCal = Calendar.getInstance();
		freshCal.add(Calendar.DATE, maxDays * -1);
		freshCal.getTimeInMillis(); // Re-calculate calendar fields.

		return (fileCal.compareTo(freshCal) < 0);
	}
}
