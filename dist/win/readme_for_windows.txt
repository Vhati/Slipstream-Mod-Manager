Slipstream Mod Manager
https://github.com/Vhati/Slipstream-Mod-Manager


About

  Slipstream is a mod manager for the game FasterThanLight, making it
  easy to install multiple mods at the same time and, later, revert
  to vanilla gameplay.

  It is the successor to Grognak's Mod Manager (GMM).


Requirements

  Java (1.6 or higher).
    http://www.java.com/en/download/

  FTL (1.01-1.6.2, Windows/OSX/Linux, Steam/GOG/Standalone).
    https://subsetgames.com/

  * WinXP SP1 can't run Java 1.7.
    (1.7 was built with VisualStudio 2010, causing a DecodePointer error.)
    To get 1.6, you may have to google "jdk-6u45-windows-i586.exe".


Setup

  Extract the files from this archive anywhere.

  On the first run, you may be prompted to locate your FTL resources.
  Specifically "data.dat" in a directory where FTL was installed.

  In most cases, this should be located automatically.


Usage

  To add a mod (an *.ftl file):
    Put it in the Slipstream\mods\ folder.

  To start the mod manager:
    On XP, double-click modman.exe.

    Since Windows Vista, Slipstream usually needs to be run as an admin.
      Right-click modman.exe and "Run as Administrator".
      Or double-click modman_admin.exe.

  To install mods:
    Tick checkboxes to select the mods you want to install.
    Mods at the top get clobbered by ones below. Drag names to reorder.
    Click the "Patch" button.
    (Any unselected mods will be omitted/uninstalled.)

  To uninstall all mods:
    Click "Patch" with none selected.

  Before upgrading Slipstream:
    Uninstall all mods, so the next version will see a clean game.

  If you upgrade FTL:
    Delete the outdated files in Slipstream\backup\
	This can be done from the menu: Help-"Delete Backups".
    If you don't, the game's resources will get corrupted.

  Commandline:
    Run "modman_admin.exe -h" for commandline usage.

    Since Windows Vista, the Command Prompt itself may need to be started as an
    administrator (right-click it in the start menu).


Troubleshooting

* General advice...
    Double-click modman_debug.bat.
    That will show you the logs and offer to fix interface glitches.

* If you get "java.lang.UnsupportedClassVersionError" on startup...
    You need a newer version of Java.

* If some other program starts when you double-click modman.jar...
    Running an exe is recommended, but if you insist, try JarFix.
      https://johann.loefflmann.net/en/software/jarfix/index.html

* If you get permission errors...
    Option 1 (Windows Vista and later):
      Start Menu -> Programs -> Accessories.
      Right-click "Command Prompt" to run as an admin.
      Type this, then hit enter: cd "c:\location\of\Slipstream"
      Type this, then hit enter: java -jar modman.jar

    Option 2:
      Make sure resource.dat and data.dat aren't read-only.

* If patching apparently succeeds, but no mods appear in-game...
    Option 1:
      Confirm you're modding the resources from the right FTL installation.
      In the console, look for "Using FTL dats path from config: ...".
      Or open modman-log.txt.

      Or in SMM, under File-Preferences, check "ftl_dats_path".
      Or open modman.cfg with a text editor.

    Option 2 (Windoes Vista and later):
      Look for a resources directory like this and delete it.
      "C:\Users\[Username]\AppData\Local\VirtualStore\Program Files\FTL\resources"

      This may be created when a non-admin app tries to write to a
      protected location, like Program Files. The OS writes in VirtualStore
      instead and lies to the app.

      Weirdness happens when an admin-app modifies the real protected file.
      A user-run app then continues to see the VirtualStore copy instead.

      By deleting the copy and exclusively running SMM as admin, this can be
      avoided.

* If the game shows exclamation marks for everything...
    See the suggestion below for replacing corrupt resources.

* If text in-game is shrunken and moved to the top of the screen...
    FTL was upgraded, but Slipstream modded with backups from the old FTL.
    See the suggestion below for replacing corrupt resources.

* If FTL's resources are corrupt...
    Option 1:
      In SMM, Help-DeleteBackups
      Steam users:
        In SMM, Help-Steam:VerifyGameCache.
      Standalone users:
        Reinstall FTL.

    Option 2:
      Delete the files in SMM\backup\
      Steam users:
        Delete FTL's resource directory:
          "C:\Program Files [or (x86)]\Steam\steamapps\common\FTL Faster Than Light\resources"
        Start Steam and "verify integrity of game files".
          https://support.steampowered.com/kb_article.php?ref=2037-QEUH-3335
      Standalone users:
        Reinstall FTL.

* UnmappableCharacterException during patching...
    For FTL 1.01-1.5.13, Slipstream re-encodes text to windows-1252, which
    restricts the set of characters that can be used in mods. Click the
    Validate button to find any problematic characters. FTL 1.6.1 switched to
    unicode and should not have this problem.
