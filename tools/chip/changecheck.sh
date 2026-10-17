#!/bin/sh
# Software 1.21 checked on the card itself (see ChangeCheck.java): a PIN first, the owner's claim (timed), a payment that makes
# change for the card, and that change loaded in the next tap with no PIN by LOAD_CHANGE (timed), the card unblinding it itself.
# It refuses a card that is not blank, and ends with the card blank.
#
#   sh tools/chip/changecheck.sh                    the card in the reader (PC/SC)
#   sh tools/chip/changecheck.sh 127.0.0.1:47470    a card behind tools/chip/run.sh, or the simulator's server (tools/cardsim/run.sh)
#
# Needs a JDK (11 or later) and GlobalPlatformPro's jar for its PC/SC binding: GP_JAR, or gp.jar beside this script.
set -e
cd "$(dirname "$0")"
JAR="${GP_JAR:-$PWD/gp.jar}"
[ -f "$JAR" ] || { echo "gp.jar not found: set GP_JAR to GlobalPlatformPro's jar" >&2; exit 1; }
mkdir -p classes
javac -cp "$JAR" -d classes ChangeCheck.java
exec java -cp "$JAR:classes" ChangeCheck "$@"
