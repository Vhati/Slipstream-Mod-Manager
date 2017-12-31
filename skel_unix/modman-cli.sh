#!/bin/bash

# Get the absolute path to this script's folder.
if echo "$0" | awk '{exit(!/^\//);}'; then
  maindir=$(dirname "$0");
else
  maindir=$PWD/$(dirname "$0");
fi

cd "${maindir}";

java_cmd=$(command -v java);

# OSX uses a command to decide java's location.
if [ -x "/usr/libexec/java_home" ]; then
  export JAVA_HOME=`/usr/libexec/java_home`
  java_cmd=${JAVA_HOME}/bin/java
fi

${java_cmd} -jar modman.jar "$@";
