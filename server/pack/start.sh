#!/bin/sh
# Firmament Ages server: installs NeoForge on the first start, then runs the server.
# /restart (console or in game): the server stops and this script starts it again.
# /stop, then /stop confirm: the server stops and this script ends.
# A crash: the script starts the server again after RESTART_DELAY seconds; the third crash within five minutes ends it.
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
grep -qi '^eula=true' eula.txt 2>/dev/null || echo "WARNING: the Minecraft EULA is not accepted. Read https://aka.ms/MinecraftEULA and set eula=true in eula.txt."

if [ ! -f "$UNIXARGS" ]; then
  echo "Installing NeoForge $NEOFORGE ..."
  "$JAVA" -jar "$INSTALLER" --installServer
  if [ ! -f "$UNIXARGS" ]; then
    echo "NeoForge installer failed, see $INSTALLER.log"
    exit 1
  fi
fi

crash_count() {
  set -- crash-reports/*.txt
  if [ -e "$1" ]; then echo "$#"; else echo 0; fi
}

CRASH1=""
CRASH2=""
while :; do
  BEFORE=$(crash_count)
  "$JAVA" @user_jvm_args.txt @"$UNIXARGS" nogui
  CODE=$?
  if [ -f RESTART ]; then
    rm -f RESTART
    echo "RESTART file found, starting the server again."
    continue
  fi
  if [ "$CODE" -eq 0 ] && [ "$(crash_count)" -le "$BEFORE" ]; then
    echo "The server stopped. Run start.sh to start it again."
    exit 0
  fi
  NOW=$(date +%s)
  if [ -n "$CRASH1" ] && [ $((NOW - CRASH1)) -le 300 ]; then
    echo "The server crashed three times within five minutes (exit code $CODE), not restarting. See crash-reports and logs/latest.log."
    exit 1
  fi
  CRASH1=$CRASH2
  CRASH2=$NOW
  echo "The server crashed (exit code $CODE). Restarting in $RESTART_DELAY s."
  sleep "$RESTART_DELAY"
done
