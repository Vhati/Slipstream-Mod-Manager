Changelog

1.9.1:
- Edited a comment in boilerplate mod metadata to include AE ships
- Made the comments in boilerplace mod metadata optional
- Fixed omitted Validate warnings for PNG files
- Disabled XML escaping when reencoding to ensure invalid chars cause an error
- Added Validate warnings about FTL 1.6.1+ for TTF, MP3, and PNG files
- Changed logging framework to SLF4J/Logback
- Changed command line parser to picocli

1.9:
- Fixed corrupted civilian sector music in FTL 1.6.1+

1.8:
- Fixed endless prompting about Steam on startup for standalone distros
- Fixed mods not being added to FTL's resources
- Fixed 'stream closed' error during patching

1.7:
- Added support for FTL 1.6.1
- Added a config option to launch FTL via Steam, if possible
- Added alternate locations to locate FTL resources on Linux
- Added 'modman_debug.bat' for troubleshooting on Windows
- Added help menu items for deleting backups and Steam's 'Verify Game Cache'
- Added a menu item to generate boilerplate for new mods
- Added a 'copy link location' popup to forum links in mod descriptions
- Added advanced removeAttributes tag
- Fixed 'access denied' error when dats are set to be read-only
- Fixed Windows exe launchers not finding Java 9
- Fixed unresolved symlinks when locating FTL resources
- Fixed unpreserved leading/trailing space in 'misc.xml' values (JDOM 2.0.5 bug)

1.6:
- Fixed modorder not being saved properly

1.5:
- Added 'no info... yet' message when mods/ scan is still in-progress
- Fixed modman.command double-clicking on Linux?
- Fixed advanced find tags not honoring start= when greater than match count
- Fixed advanced find tags not honoring limit=
- Fixed hidden files and directories not appearing when finding resources
- Updated log4j2 to 2.0-beta9, fixing a hang when run with Java 1.7.0_25
- Changed FTLDat to allow opening dats in read-only mode
- Changed modman.exe to fail rather than use VirtualStore
- Added modman_admin.exe, which always runs as administrator
- Added a Validate warning for junk files whose names start/end with tilde
- Added a Validate warning for junk files whose names start+end with hash
- Added a Validate warning for junk files named '.dropbox'
- Added a check during patching to skip junk files
- Added an error popup when the jar is double-clicked
- Added rawappend/rawclobber suffixes to preserve whitespace in 'misc.xml'
- Minor optimizations to reduce memory usage
- Added uncaught exception handling for background threads

1.4:
- Cleaned up some dodgy code when initially prompting for FTL's location
- Added another common game location: "C:\Program Files (x86)\FTL"

1.3:
- Added "Return FTL to an unmodded state before switching to a new version"
- Fixed advanced find tags' panic arg (was "true" all the time)
- Fixed advanced selector tag not narrowing results when it had a value
- Fixed perpetually green "Update" button
- Added --global-panic commandline arg to show mod devs typoed find tags
- Added commandline tips in readme_modders.txt
- Fixed sloppy parser Validate error about things not allowed at root
- Added a Preferences dialog as an alternative to editing modman.cfg
- Added a troubleshooting note about Java 1.7.0_25 to readmes
- Added support for embedded descriptions in *.ftl files

1.2:
- Added a commandline interface
- Incorporated strict-then-sloppy XML parsing into the patch process
- Added special XML tags for advanced appending
- Added XML Sandbox for tinkering with append tags
- Added 'Engi Scrap Advantage' example mod
- Added scrollbars to progress popups to show long error messages
- The main window's geometry is saved on exit
- Added urls in the statusbar when hovering over links
- Added periodic checking for SMM updates
- Added FTL launching on Linux
- Fixed critical bugs in InputStreams returned by FTLDat

1.1:
- Added a button to open the mods/ folder
- Added FTL resource extraction
- Made the mods list resizable (drag the divider)
- Changed modorder saving to occur on exit, instead of after patching
- Added a mod's estimated release date to the "No info" notice
- Added an explanation of encodings to the modder readme
- Added LF to CR-LF conversion for *.xml.append, *.xml, and *.txt
- Added a Validate warning for text files with LF line endings
- Fixed data loss when decoding Windows-1252 text with accented characters
- Changed catalog auto-update url from GMM's repository to SMM's
- Added tolerance for non-standard zips containing backslash paths
- Added a Validate warning for non-standard zips containing backslash paths
- Fixed Validation checking that scanned selected mods in reverse order
- Restricted double-click selection to repeated clicks on one title cell

1.0:
- Changed mod list to a table with checkboxes
- Instead of extracting to temp, mod data is transferred directly into dats
- Added a GUI progress bar during patching
- Added a Validate warning for paths with non-ASCII chars
- Added support for windows-1252 ANSI and UTF-16 text in mods

Changes shared with Grognaks Mod Manager 1.8:
- Added periodic updates to the catalog of mod metadata
- Added ini setting: update_catalog
- Added a log warning during patching if a mod gets clobbered
- Added a log warning during patching if a modded file's case doesn't match
- Made the main window resizable
- Changed listbox selection to use double-click

Grognaks Mod Manager 1.7:
- Last official release
