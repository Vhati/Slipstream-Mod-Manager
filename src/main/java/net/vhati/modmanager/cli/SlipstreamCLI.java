package net.vhati.modmanager.cli;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.IVersionProvider;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Parameters;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.vhati.ftldat.AbstractPack;
import net.vhati.ftldat.FolderPack;
import net.vhati.ftldat.PkgPack;
import net.vhati.ftldat.FTLPack;
import net.vhati.modmanager.FTLModManager;
import net.vhati.modmanager.core.DelayedDeleteHook;
import net.vhati.modmanager.core.FTLUtilities;
import net.vhati.modmanager.core.ModPatchObserver;
import net.vhati.modmanager.core.ModPatchThread;
import net.vhati.modmanager.core.ModUtilities;
import net.vhati.modmanager.core.Report;
import net.vhati.modmanager.core.Report.ReportFormatter;
import net.vhati.modmanager.core.Report.ReportMessage;
import net.vhati.modmanager.core.SlipstreamConfig;

public class SlipstreamCLI {

	private static final Logger log = LoggerFactory.getLogger(SlipstreamCLI.class);

	private static File backupDir = new File("./backup/");
	private static File modsDir = new File("./mods/");

	private static Thread.UncaughtExceptionHandler exceptionHandler = null;

	public static void main(String[] args) {

		exceptionHandler = new Thread.UncaughtExceptionHandler() {
			@Override
			public void uncaughtException(Thread t, Throwable e) {
				log.error("Uncaught exception in thread: " + t.toString(), e);
				System.exit(1);
			}
		};

		SlipstreamCommand slipstreamCmd = new SlipstreamCommand();
		CommandLine commandLine = new CommandLine(slipstreamCmd);
		try {
			commandLine.parse(args);
		} catch (ParameterException e) {
			// For multiple subcommands, e.getCommandLine() returns the one that failed.

			System.err.println("Error parsing commandline: " + e.getMessage());
			System.exit(1);
		}

		if (commandLine.isUsageHelpRequested()) {
			commandLine.usage(System.out);
			System.exit(0);
		}
		if (commandLine.isVersionHelpRequested()) {
			commandLine.printVersionHelp(System.out);
			System.exit(0);
		}

		DelayedDeleteHook deleteHook = new DelayedDeleteHook();
		Runtime.getRuntime().addShutdownHook(deleteHook);

		if (slipstreamCmd.validate) { // Exits (0/1).
			log.info("Validating...");

			StringBuilder resultBuf = new StringBuilder();
			ReportFormatter formatter = new ReportFormatter();
			boolean anyInvalid = false;

			for (String modFileName : slipstreamCmd.modFileNames) {
				File modFile = new File(modsDir, modFileName);

				if (modFile.isDirectory()) {
					log.info(String.format("Zipping dir: %s/", modFile.getName()));
					try {
						modFile = createTempMod(modFile);
						deleteHook.addDoomedFile(modFile);
					} catch (IOException e) {
						log.error(String.format("Error zipping dir: %s/", modFile.getName()), e);

						List<ReportMessage> tmpMessages = new ArrayList<ReportMessage>();
						tmpMessages.add(new ReportMessage(ReportMessage.SECTION, modFileName));
						tmpMessages.add(new ReportMessage(ReportMessage.EXCEPTION, e.getMessage()));

						formatter.format(tmpMessages, resultBuf, 0);
						resultBuf.append("\n");

						anyInvalid = true;
						continue;
					}
				}

				Report validateReport = ModUtilities.validateModFile(modFile);

				formatter.format(validateReport.messages, resultBuf, 0);
				resultBuf.append("\n");

				if (validateReport.outcome == false)
					anyInvalid = true;
			}
			if (resultBuf.length() == 0) {
				resultBuf.append("No mods were checked.");
			}

			System.out.println();
			System.out.println(resultBuf.toString());
			System.exit(anyInvalid ? 1 : 0);
		}

		File configFile = new File("modman.cfg");
		SlipstreamConfig appConfig = getConfig(configFile);

		if (slipstreamCmd.listMods) { // Exits.
			log.info("Listing mods...");

			boolean allowZip = appConfig.getProperty(SlipstreamConfig.ALLOW_ZIP, "false").equals("true");
			File[] modFiles = modsDir.listFiles(new ModAndDirFileFilter(allowZip, true));
			List<String> dirList = new ArrayList<String>();
			List<String> fileList = new ArrayList<String>();
			for (File f : modFiles) {
				if (f.isDirectory())
					dirList.add(f.getName() + "/");
				else
					fileList.add(f.getName());
			}
			Collections.sort(dirList);
			Collections.sort(fileList);
			for (String s : dirList)
				System.out.println(s);
			for (String s : fileList)
				System.out.println(s);

			System.exit(0);
		}

		File datsDir = null;
		if (slipstreamCmd.extractDatsDir != null || slipstreamCmd.patch || slipstreamCmd.runftl) {
			datsDir = getDatsDir(appConfig);
		}

		if (slipstreamCmd.extractDatsDir != null) { // Exits (0/1).
			log.info("Extracting dats...");

			File extractDir = slipstreamCmd.extractDatsDir;

			FolderPack dstPack = null;
			List<AbstractPack> srcPacks = new ArrayList<AbstractPack>(2);
			InputStream is = null;
			try {
				File ftlDatFile = new File(datsDir, "ftl.dat");
				File dataDatFile = new File(datsDir, "data.dat");
				File resourceDatFile = new File(datsDir, "resource.dat");

				if (ftlDatFile.exists()) { // FTL 1.6.1.
					AbstractPack ftlPack = new PkgPack(ftlDatFile, "r");
					srcPacks.add(ftlPack);
				} else if (dataDatFile.exists() && resourceDatFile.exists()) { // FTL 1.01-1.5.13.
					AbstractPack dataPack = new FTLPack(dataDatFile, "r");
					AbstractPack resourcePack = new FTLPack(resourceDatFile, "r");
					srcPacks.add(dataPack);
					srcPacks.add(resourcePack);
				} else {
					throw new FileNotFoundException(String.format("Could not find either \"%s\" or both \"%s\" and \"%s\"",
							ftlDatFile.getName(), dataDatFile.getName(), resourceDatFile.getName()));
				}

				if (!extractDir.exists())
					extractDir.mkdirs();

				dstPack = new FolderPack(extractDir);

				for (AbstractPack srcPack : srcPacks) {
					List<String> innerPaths = srcPack.list();

					for (String innerPath : innerPaths) {
						if (dstPack.contains(innerPath)) {
							log.info("While extracting resources, this file was overwritten: " + innerPath);
							dstPack.remove(innerPath);
						}
						is = srcPack.getInputStream(innerPath);
						dstPack.add(innerPath, is);
					}
					srcPack.close();
				}
			} catch (IOException e) {
				log.error("Error extracting dats", e);
				System.exit(1);
			} finally {
				try {
					if (is != null)
						is.close();
				} catch (IOException ex) {
				}

				try {
					if (dstPack != null)
						dstPack.close();
				} catch (IOException ex) {
				}

				for (AbstractPack pack : srcPacks) {
					try {
						pack.close();
					} catch (IOException ex) {
					}
				}
			}

			System.exit(0);
		}

		if (slipstreamCmd.patch) { // Exits sometimes (1 on failure).
			log.info("Patching...");

			List<File> modFiles = new ArrayList<File>();
			if (slipstreamCmd.modFileNames != null) {
				for (String modFileName : slipstreamCmd.modFileNames) {
					File modFile = new File(modsDir, modFileName);

					if (modFile.isDirectory()) {
						log.info(String.format("Zipping dir: %s/", modFile.getName()));
						try {
							modFile = createTempMod(modFile);
							deleteHook.addDoomedFile(modFile);
						} catch (IOException e) {
							log.error(String.format("Error zipping dir: %s/", modFile.getName()), e);
							System.exit(1);
						}
					}

					modFiles.add(modFile);
				}
			}

			boolean globalPanic = slipstreamCmd.globalPanic;

			SilentPatchObserver patchObserver = new SilentPatchObserver();
			ModPatchThread patchThread = new ModPatchThread(modFiles, datsDir, backupDir, globalPanic, patchObserver);
			Thread.setDefaultUncaughtExceptionHandler(exceptionHandler);
			deleteHook.addWatchedThread(patchThread);

			patchThread.start();
			while (patchThread.isAlive()) {
				try {
					patchThread.join();
				} catch (InterruptedException e) {
				}
			}

			if (!patchObserver.hasSucceeded())
				System.exit(1);
		}

		if (slipstreamCmd.runftl) { // Exits (0/1).
			log.info("Running FTL...");

			File exeFile = null;
			String[] exeArgs = null;

			// Try to run via Steam.
			if ("true".equals(appConfig.getProperty(SlipstreamConfig.RUN_STEAM_FTL, "false"))) {

				String steamPath = appConfig.getProperty(SlipstreamConfig.STEAM_EXE_PATH);
				if (steamPath.length() > 0) {
					exeFile = new File(steamPath);

					if (exeFile.exists()) {
						exeArgs = new String[] { "-applaunch", FTLUtilities.STEAM_APPID_FTL };
					} else {
						log.warn(
								String.format("%s does not exist: %s", SlipstreamConfig.STEAM_EXE_PATH, exeFile.getAbsolutePath()));
						exeFile = null;
					}
				}

				if (exeFile == null) {
					log.warn("Steam executable could not be found, so FTL will be launched directly");
				}

			}
			// Try to run directly.
			if (exeFile == null) {
				exeFile = FTLUtilities.findGameExe(datsDir);

				if (exeFile != null) {
					exeArgs = new String[0];
				} else {
					log.warn("FTL executable could not be found");
				}
			}

			if (exeFile != null) {
				try {
					FTLUtilities.launchExe(exeFile, exeArgs);
				} catch (Exception e) {
					log.error("Error launching FTL", e);
					System.exit(1);
				}
			} else {
				log.error("No executables were found to launch FTL");
				System.exit(1);
			}

			System.exit(0);
		}

		System.exit(0);
	}

	/**
	 * Loads settings from a config file.
	 *
	 * If an error occurs, it'll be logged, and default settings will be returned.
	 */
	private static SlipstreamConfig getConfig(File configFile) {

		Properties props = new Properties();
		props.setProperty(SlipstreamConfig.ALLOW_ZIP, "false");
		props.setProperty(SlipstreamConfig.FTL_DATS_PATH, "");
		props.setProperty(SlipstreamConfig.STEAM_EXE_PATH, "");
		props.setProperty(SlipstreamConfig.RUN_STEAM_FTL, "false");
		props.setProperty(SlipstreamConfig.NEVER_RUN_FTL, "false");
		props.setProperty(SlipstreamConfig.USE_DEFAULT_UI, "false");
		props.setProperty(SlipstreamConfig.REMEMBER_GEOMETRY, "true");
		// "update_catalog" doesn't have a default.
		// "update_app" doesn't have a default.
		// "manager_geometry" doesn't have a default.

		// Read the config file.
		InputStream in = null;
		try {
			if (configFile.exists()) {
				log.trace("Loading properties from config file");
				in = new FileInputStream(configFile);
				props.load(new InputStreamReader(in, "UTF-8"));
			}
		} catch (IOException e) {
			log.error("Error loading config", e);
		} finally {
			try {
				if (in != null)
					in.close();
			} catch (IOException e) {
			}
		}

		SlipstreamConfig appConfig = new SlipstreamConfig(props, configFile);
		return appConfig;
	}

	/**
	 * Checks the validity of the config's dats path and returns it. Or exits if the
	 * path is invalid.
	 */
	private static File getDatsDir(SlipstreamConfig appConfig) {
		File datsDir = null;
		String datsPath = appConfig.getProperty(SlipstreamConfig.FTL_DATS_PATH, "");

		if (datsPath.length() > 0) {
			log.info("Using FTL dats path from config: " + datsPath);
			datsDir = new File(datsPath);
			if (FTLUtilities.isDatsDirValid(datsDir) == false) {
				log.error("The config's " + SlipstreamConfig.FTL_DATS_PATH + " does not exist, or it is invalid");
				datsDir = null;
			}
		} else {
			log.error("No FTL dats path previously set");
		}
		if (datsDir == null) {
			log.error("Run the GUI once, or edit the config file, and try again");
			System.exit(1);
		}

		return datsDir;
	}

	/**
	 * Returns a temporary zip made from a directory.
	 *
	 * Empty subdirs will be omitted. The archive will be not be deleted on exit
	 * (handle that elsewhere).
	 */
	private static File createTempMod(File dir) throws IOException {
		File tempFile = File.createTempFile(dir.getName() + "_temp-", ".zip");

		FileOutputStream fos = null;
		try {
			fos = new FileOutputStream(tempFile);
			ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(fos));
			addDirToArchive(zos, dir, null);
			zos.close();
		} finally {
			try {
				if (fos != null)
					fos.close();
			} catch (IOException e) {
			}
		}

		return tempFile;
	}

	private static void addDirToArchive(ZipOutputStream zos, File dir, String pathPrefix) throws IOException {
		if (pathPrefix == null)
			pathPrefix = "";

		for (File f : dir.listFiles()) {
			if (f.isDirectory()) {
				addDirToArchive(zos, f, pathPrefix + f.getName() + "/");
				continue;
			}

			FileInputStream is = null;
			try {
				is = new FileInputStream(f);
				zos.putNextEntry(new ZipEntry(pathPrefix + f.getName()));

				byte[] buf = new byte[4096];
				int len;
				while ((len = is.read(buf)) >= 0) {
					zos.write(buf, 0, len);
				}

				zos.closeEntry();
			} finally {
				try {
					if (is != null)
						is.close();
				} catch (IOException e) {
				}
			}
		}
	}

	@Command(name = "modman", abbreviateSynopsis = true, sortOptions = false, description = "Perform actions against an FTL installation and/or a list of named mods.", footer = "%nIf a named mod is a directory, a temporary zip will be created.", versionProvider = SlipstreamVersionProvider.class)
	public static class SlipstreamCommand {
		@Option(names = "--extract-dats", paramLabel = "DIR", description = "extract FTL resources into a dir")
		File extractDatsDir;

		@Option(names = "--global-panic", description = "patch as if advanced find tags had panic='true'")
		boolean globalPanic;

		@Option(names = "--list-mods", description = "list available mod names")
		boolean listMods;

		@Option(names = "--runftl", description = "run the game (standalone or with 'patch')")
		boolean runftl;

		@Option(names = "--patch", description = "revert to vanilla and add named mods (if any)")
		boolean patch;

		@Option(names = "--validate", description = "check named mods for problems")
		boolean validate;

		@Option(names = { "-h", "--help" }, usageHelp = true, description = "display this help and exit")
		boolean helpRequested;

		@Option(names = "--version", versionHelp = true, description = "output version information and exit")
		boolean versionRequested;

		@Parameters(paramLabel = "MODFILE", description = "names of files or directories in the mods/ dir")
		String[] modFileNames;
	}

	public static class SlipstreamVersionProvider implements IVersionProvider {
		@Override
		public String[] getVersion() {
			return new String[] { String.format("%s %s", FTLModManager.APP_NAME, FTLModManager.APP_VERSION),
					"Copyright (C) 2013,2014,2017,2018 David Millis", "",
					"This program is free software; you can redistribute it and/or modify",
					"it under the terms of the GNU General Public License as published by",
					"the Free Software Foundation; version 2.", "",
					"This program is distributed in the hope that it will be useful,",
					"but WITHOUT ANY WARRANTY; without even the implied warranty of",
					"MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the",
					"GNU General Public License for more details.", "",
					"You should have received a copy of the GNU General Public License",
					"along with this program. If not, see http://www.gnu.org/licenses/.", "", };
		}
	}

	private static class SilentPatchObserver implements ModPatchObserver {
		private boolean succeeded = false;

		@Override
		public void patchingProgress(final int value, final int max) {
		}

		@Override
		public void patchingStatus(String message) {
		}

		@Override
		public void patchingMod(File modFile) {
		}

		@Override
		public synchronized void patchingEnded(boolean outcome, Exception e) {
			succeeded = outcome;
		}

		public synchronized boolean hasSucceeded() {
			return succeeded;
		}
	}

	private static class ModAndDirFileFilter implements FileFilter {
		private boolean allowZip;
		private boolean allowDirs;

		public ModAndDirFileFilter(boolean allowZip, boolean allowDirs) {
			this.allowZip = allowZip;
			this.allowDirs = allowDirs;
		}

		@Override
		public boolean accept(File f) {
			if (f.isDirectory())
				return allowDirs;

			if (f.getName().endsWith(".ftl"))
				return true;

			if (allowZip) {
				if (f.getName().endsWith(".zip"))
					return true;
			}
			return false;
		}
	}
}
