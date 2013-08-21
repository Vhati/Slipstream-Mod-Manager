The build process for this project is automated by Maven.
  http://maven.apache.org/
  http://docs.codehaus.org/display/MAVENUSER/Getting+Started+with+Maven


To build, run "mvn clean" and "mvn package" in this folder.



"skel_common/" contains files to include in distribution archives.

"skel_win/" and "skel_unix/" are for system specific dist files.



"skel_exe/" contains materials to create modman.exe (not part of Maven).

- Get Launch4j: http://launch4j.sourceforge.net/index.html
- Drag "launch4j.xml" onto "launch4jc.exe".
- "modman.exe" will appear alongside the xml.
- Drag modman.exe into "skel_win/".
- "mvn clear" and "mvn package".



This project depends on the following libraries.
- Jackson JSON Processor 2.x
    http://jackson.codehaus.org/Home
    (JavaDoc links are on the right.)
- PNGJ
    https://code.google.com/p/pngj/
- JDOM 2.x
    http://www.jdom.org/
- log4j
    http://logging.apache.org/log4j/2.x/



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
