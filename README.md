# Slipstream Mod Manager

Slipstream is a mod manager for the game FasterThanLight
([FTL](https://subsetgames.com/ftl.html)), making it easy to install multiple
mods at the same time and, later, revert to vanilla gameplay.

It is the successor to Grognak's Mod Manager
([GMM](https://subsetgames.com/forum/viewtopic.php?p=9994)).

![Screenshot of the mod manager in use](assets/screenshot01.png)

## Requirements

-   Java (1.6 or higher).
    -   <http://www.java.com/en/download/>
-   FTL (1.01-1.6.3, Windows/OSX/Linux, Steam/GOG/Standalone).
    -   <https://subsetgames.com/>
-   WinXP SP1 can't run Java 1.7.
    -   (1.7 was built with VisualStudio 2010, causing a DecodePointer error.)
    -   To get 1.6, you may have to google "jdk-6u45-windows-i586.exe".

## Building

1.  Open the repository in the provided Visual Studio Code devcontainer.
2.  Run `mvn package` from the repository root to compile the source and
    generate a JAR file.

## Downloads, comments and donations

[Download compiled binaries](https://sourceforge.net/projects/slipstreammodmanager/).

Comments can be made in [this forum thread](https://subsetgames.com/forum/viewtopic.php?f=12&t=17102).

I can accept [PayPal donations](https://vhati.github.io/donate.html). That
would be fantastic.
