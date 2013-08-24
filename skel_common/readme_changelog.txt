Changelog

???:
- Added LF to CR-LF conversion for *.xml.append, *.xml, and *.txt
- Added a Validate warning for text files with LF line endings
- Fixed data loss when decoding Windows-1252 text with accented characters
- Changed catalog auto-update url from GMM's repository to SMM's
- Added tolerance for non-standard zips containing backslash paths
- Added a Validate warning for non-standard zips containing backslash paths

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
