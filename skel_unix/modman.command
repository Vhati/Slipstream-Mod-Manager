#!/bin/bash

# Get the script's name.
me=$(basename "$0");

# Get the absolute path to this script's folder.
if echo "$0" | awk '{exit(!/^\//);}'; then
  maindir=$(dirname "$0");
else
  maindir=$PWD/$(dirname "$0");
fi


# Spawn a terminal if this is headless (or piped) and not on an ssh socket.
# The terminal will run another instance of this script with ${ingui}=1.
if [ ! \( -t 0 -o -S /dev/stdin \) ]; then
  if [ "${ingui}" = "1" ]; then
    echo "This is a nested instance. The terminal must have spawned.";
    echo "Yet the 'Am I  headless' test couldn't detect the terminal!?";
    exit
  fi

  echo "Double-clicked? You need a terminal!";
  export ingui=1;

  if command -v x-terminal-emulator >/dev/null; then
    # Linux.
    x-terminal-emulator -e "$0";

  elif command -v osascript >/dev/null; then
    # OSX.
    # *.command files normally get their own terminal there,
    # but better safe than sorry.
    # - - -
    osascript <<EOF
tell application "Terminal"
  activate
  do script "export ingui=1;cd \"${maindir}\";\"./${me}\";exit"
end tell
EOF
    # - - -
  fi
  exit;
fi

# Finally, the payload.
cd "${maindir}";
# - - -

# Search in $PATH among other places.
java_cmd=$(command -v java);

# OSX uses a command to decide java's location (or prompt the user to install it).
if [ -x "/usr/libexec/java_home" ]; then
  export JAVA_HOME=$(/usr/libexec/java_home --request)

  if [ -n "${JAVA_HOME}" ]; then
    java_cmd=${JAVA_HOME}/bin/java
  fi
fi

if [ -n "${java_cmd}" ]; then

  echo "";
  echo "Found java at: ${java_cmd}";
  echo "";

  "${java_cmd}" -jar modman.jar;

else

  echo "";
  echo "This script was unable to find java."
  echo "";

fi

# - - -

if [ "${ingui}" = "1" ]; then
  read -p "Press enter to continue" dummyvar;
fi
exit;
