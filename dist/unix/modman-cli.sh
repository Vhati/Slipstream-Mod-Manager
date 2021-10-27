#!/bin/bash

# Get the absolute path to this script's folder.
if echo "$0" | awk '{exit(!/^\//);}'; then
  maindir=$(dirname "$0");
else
  maindir=$PWD/$(dirname "$0");
fi

cd "${maindir}";

# Search in $PATH among other places.
java_cmd=$(command -v java);

# OSX uses a command to decide java's location.
if [ -x "/usr/libexec/java_home" ]; then
  export JAVA_HOME=$(/usr/libexec/java_home)

  if [ -n "${JAVA_HOME}" ]; then
    java_cmd=${JAVA_HOME}/bin/java
  fi
fi

if [ -n "${java_cmd}" ]; then

  "${java_cmd}" -jar modman.jar "$@";

else

  echo "";
  echo "This script was unable to find java."
  echo "";

fi
