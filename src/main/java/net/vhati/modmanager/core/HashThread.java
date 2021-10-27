package net.vhati.modmanager.core;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.vhati.ftldat.PackUtilities;

/**
 * A thread to calculate MD5 hashes of files in the background.
 *
 * As each file is hashed, a class implementing HashObserver is notified. Note:
 * The callback on that class needs to be thread-safe.
 */
public class HashThread extends Thread {

	private static final Logger log = LoggerFactory.getLogger(HashThread.class);

	private List<File> fileList = new ArrayList<File>();
	private HashObserver hashObserver = null;

	public HashThread(File[] files, HashObserver hashObserver) {
		this.fileList.addAll(Arrays.asList(files));
		this.hashObserver = hashObserver;
	}

	public void run() {
		for (File f : fileList) {
			String hash = calcFileMD5(f);
			if (hash != null) {
				hashObserver.hashCalculated(f, hash);
			}
		}

		log.info("Background hashing finished.");
		hashObserver.hashingEnded();
	}

	private String calcFileMD5(File f) {
		String result = null;
		try {
			result = PackUtilities.calcFileMD5(f);
		} catch (Exception e) {
			log.error("Error while calculating hash for file: " + f.getPath(), e);
		}
		return result;
	}
}
