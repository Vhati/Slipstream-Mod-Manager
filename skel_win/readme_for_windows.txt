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

  FTL (1.01-1.03.3, Windows/OSX/Linux, Steam/GOG/Standalone).
    http://www.ftlgame.com/

  * WinXP SP1 can't run Java 1.7.
    (1.7 was built with VisualStudio 2010, causing a DecodePointer error.)
    To get 1.6, you may have to google "jdk-6u45-windows-i586.exe".


Setup

  Extract the files from this archive anywhere.

  On the first run, you may be prompted to locate your
  FTL resources. Specifically "data.dat" in the "resources\"
  directory under your FTL install.

  In most cases, this should be located automatically.


Usage

  To add a mod (an *.ftl file):
    Put it in the Slipstream\mods\ folder.

  To start the mod manager:
    Double-click modman.exe or modman.jar.

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
    If you don't, the game's resources will get corrupted.


Troubleshooting

* If you get "java.lang.UnsupportedClassVersionError" on startup...
    You need a newer version of Java.

* If some other program starts when you double-click modman.jar...
    Try JarFix.
      http://johann.loefflmann.net/en/software/jarfix/index.html

* If you get permission errors...
    Option 1:
      Right-click modman.exe to run as an admin.

    Option 2:
      Start Menu -> Programs -> Accessories.
      Right-click "Command Prompt" to run as an admin.
      Type this, then hit enter: cd "c:\location\of\Slipstream"
      Type this, then hit enter: java -jar modman.jar

    Option 3:
      Make sure resource.dat and data.dat aren't read-only.

* If the game shows exclamation marks for everything...
    See the suggestion below for replacing corrupt resources.

* If text in-game is shrunken and moved to the top of the screen...
    FTL was upgraded, but Slipstream modded with backups from the old FTL.
    When upgrading FTL in the future, delete what's in SMM\backup\ first.
    See the suggestion below for replacing corrupt resources.

* If FTL's resources are corrupt...
    Delete the files in SMM\backup\
    Steam users:
      Delete FTL's resource directory:
        "C:\Program Files [or (x86)]\Steam\steamapps\common\FTL Faster Than Light\resources"
      Start Steam and "verify game cache".
        https://support.steampowered.com/kb_article.php?ref=2037-QEUH-3335
      Run FTL, which will cause steam to copy fresh resources from its cache.
    Standalone users:
      Reinstall FTL.
