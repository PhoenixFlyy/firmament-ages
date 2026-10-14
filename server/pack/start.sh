#!/bin/sh
# Firmament Ages server: installs NeoForge on the first start, then runs the server in a restart loop.
# End the loop for good: create a file named STOP next to this script, then type "stop" in the console.
cd "$(dirname "$0")" || exit 1

NEOFORGE={{NEOFORGE}}
RESTART_DELAY=10
INSTALLER="neoforge-$NEOFORGE-installer.jar"
UNIXARGS="libraries/net/neoforged/neoforge/$NEOFORGE/unix_args.txt"

# Java 21: FA_JAVA_HOME, then JAVA_HOME, then java on PATH.
if [ -n "$FA_JAVA_HOME" ]; then JAVA="$FA_JAVA_HOME/bin/java"
elif [ -n "$JAVA_HOME" ]; then JAVA="$JAVA_HOME/bin/java"
else JAVA=java
fi
if ! "$JAVA" -version 2>&1 | grep -q 'version "21[."]'; then
  echo "Java 21 not found. Install Java 21 and put it on PATH, or set FA_JAVA_HOME to its folder."
  exit 1
fi
[ -f eula.txt ] || echo "WARNING: eula.txt is missing. Read https://aka.ms/MinecraftEULA and create eula.txt with eula=true."

if [ ! -f "$UNIXARGS" ]; then
  echo "Installing NeoForge $NEOFORGE ..."
  "$JAVA" -jar "$INSTALLER" --installServer
  if [ ! -f "$UNIXARGS" ]; then
    echo "NeoForge installer failed, see $INSTALLER.log"
    exit 1
  fi
fi

while :; do
  "$JAVA" @user_jvm_args.txt @"$UNIXARGS" nogui
  if [ -f STOP ]; then
    rm -f STOP
    echo "STOP file found, leaving the restart loop."
    break
  fi
  echo "Server stopped. Restarting in $RESTART_DELAY s (create a file named STOP to end the loop)."
  sleep "$RESTART_DELAY"
done
