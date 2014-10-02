#!/bin/sh

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
java -jar modman.jar;
# - - -

if [ "${ingui}" = "1" ]; then
  read -p "Press enter to continue" dummyvar;
fi
exit;
