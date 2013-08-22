The build process for this project is automated by Maven.
  http://maven.apache.org/
  http://docs.codehaus.org/display/MAVENUSER/Getting+Started+with+Maven


To build, run "mvn clean" and "mvn package" in this folder.


"img/"
  Screenshots.

"skel_common/"
  Files to include in distribution archives.

"skel_win/" and "skel_unix/"
  System-specific files to include in distribution archives.

"skel_exe/"
  Materials to create modman.exe (not part of Maven).
    - Get Launch4j: http://launch4j.sourceforge.net/index.html
    - Drag "launch4j.xml" onto "launch4jc.exe".
    - "modman.exe" will appear alongside the xml.
    - Drag modman.exe into "skel_win/".
    - Run "mvn clear" and "mvn package".



This project depends on the following libraries.
- Jackson JSON Processor 2.x
    http://jackson.codehaus.org/Home
    (For JavaDocs, look right.)
- PNGJ
    https://code.google.com/p/pngj/
    (For JavaDocs, scroll down.)
- JDOM 2.x
    http://www.jdom.org/
    (For JavaDocs, look left.)
- log4j2
    http://logging.apache.org/log4j/2.x/
    (JavaDocs are not available.)



Here's a batch file that builds when double-clicked (edit the vars).
- - - -
@ECHO OFF
SETLOCAL

SET JAVA_HOME=D:\Apps\j2sdk1.6.0_45
SET M2_HOME=D:\Apps\Maven

SET M2=%M2_HOME%\bin
SET PATH=%M2%;%PATH%

CD /D "%~dp0"
CALL mvn clean && CALL mvn package

PAUSE
ENDLOCAL & EXIT /B
- - - -
