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


Setup

  Extract the files from this archive anywhere.

  On the first run, you may be prompted to locate your
  FTL resources. Specifically "data.dat" in the "resources\"
  directory under your FTL install.

  On OSX, you can select "FTL.app", because the resources are inside it.

  In most cases, this should be located automatically.


Usage

  To add a mod (an *.ftl file):
    Put it in the Slipstream/mods/ folder.

  To start the mod manager:
    Double-click modman.command.

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
    Delete the outdated files in Slipstream/backup/
    If you don't, the game's resources will get corrupted.

  Commandline:
    Run "./modman-cli.sh -h" for commandline usage.


Troubleshooting

* If you get "java.lang.UnsupportedClassVersionError" on startup...
    You need a newer version of Java.

* If you get permission errors...
    Make sure resource.dat and data.dat aren't read-only.

* If the game shows exclamation marks for everything...
    See the suggestion below for replacing corrupt resources.

* If text in-game is shrunken and moved to the top of the screen...
    FTL was upgraded, but Slipstream modded with backups from the old FTL.
    When upgrading FTL in the future, delete what's in SMM/backup/ first.
    See the suggestion below for replacing corrupt resources.

* If FTL's resources are corrupt...
    Delete the files in SMM/backup/
    Steam users:
      Delete FTL's resource directory:
        Linux: "[~/.local/share or $XDG_DATA_HOME]/Steam/SteamApps/common/FTL Faster Than Light/data/resources"
        Mac: "~/Library/Application Support/Steam/SteamApps/common/FTL Faster Than Light/FTL.app"
      Start Steam and "verify game cache".
        https://support.steampowered.com/kb_article.php?ref=2037-QEUH-3335
      Run FTL, which will cause steam to copy fresh resources from its cache.
    Standalone users:
      Reinstall FTL.
