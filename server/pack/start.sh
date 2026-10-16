#!/bin/sh
# Firmament Ages server: installs NeoForge on the first start, then runs the server in a restart loop.
# End the loop for good: create a file named STOP next to this script, then type "stop" in the console.
cd "$(dirname "$0")" || exit 1

NEOFORGE={{NEOFORGE}}
RESTART_DELAY=10
INSTALLER="neoforge-$NEOFORGE-installer.jar"
UNIXARGS="libraries/net/neoforged/neoforge/$NEOFORGE/unix_args.txt"

# Java 21: the first of FA_JAVA_HOME, JAVA_HOME and java on PATH that is Java 21 (another version is skipped).
JAVA=""
for CANDIDATE in "${FA_JAVA_HOME:+$FA_JAVA_HOME/bin/java}" "${JAVA_HOME:+$JAVA_HOME/bin/java}" java; do
  [ -n "$CANDIDATE" ] || continue
  if { "$CANDIDATE" -version; } 2>&1 | grep -q 'version "21[."]'; then JAVA="$CANDIDATE"; break; fi
done
if [ -z "$JAVA" ]; then
  echo "Java 21 not found. Install Java 21, or set FA_JAVA_HOME to its folder. FA_JAVA_HOME, JAVA_HOME and PATH were checked."
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
