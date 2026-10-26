# Sourced by identify.sh, install.sh and suite.sh: the Java, GlobalPlatformPro, the host tool, and the dry-run switch.
# Nothing here talks to a card.

HERE="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$HERE/../.." && pwd)"
if [ -x /opt/homebrew/opt/openjdk@17/bin/java ]; then JDK=/opt/homebrew/opt/openjdk@17/bin; else JDK="$(dirname "$(command -v java)")"; fi
JAVA="$JDK/java"
GP_JAR="${GP_JAR:-$HOME/.foxycard/gp.jar}"
# the factory key that blank NXP cards ship with; a seller may have changed it (GP_KEY=... to use another)
GP_KEY="${GP_KEY:-404142434445464748494A4B4C4D4E4F}"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
OUT="${OUT:-$HERE/out/$STAMP}"

# Foxy's AIDs (docs/HARDWARE_DEPLOYMENT.md) and the probes'
PKG=F0464F585943415244; BIG=F0464F58594341524401; OPENER=F0464F5859434152444D; TAGCLASS=F0464F58594341524454; TAG=D2760000850101
CAPS_PKG=F0464F585943415053; CAPS_APP=F0464F58594341505301; BIO_PKG=F0464F585942494F; BIO_APP=F0464F585942494F01
# the card software this test is for: 1.23, 24,390 bytes, the build that installed on the J3R180 on 2026-10-22
CAP_123_SHA=afa3fc7afee23ab5e55733d734fc8171355c00a84c88142fcd3b114157570d61

DRY=""; READER=""; SERIAL=""; ICTYPE=""

need() { [ -f "$GP_JAR" ] || { echo "STOPPED: GlobalPlatformPro's jar is not at $GP_JAR (set GP_JAR)."; exit 2; }; [ -x "$JAVA" ] || { echo "STOPPED: no Java at $JAVA."; exit 2; }; }

# the host tool, compiled once into tools/j3r452/classes (ignored by git: *.class)
compile_tool() {
  if [ ! -f "$HERE/classes/J3r452.class" ] || [ "$HERE/J3r452.java" -nt "$HERE/classes/J3r452.class" ]; then
    mkdir -p "$HERE/classes" && "$JDK/javac" -Xlint:none -cp "$GP_JAR" -d "$HERE/classes" "$HERE/J3r452.java" || { echo "STOPPED: J3r452.java did not compile."; exit 2; }
  fi
}
# J3R452_BRIDGE=host:port puts the simulator (applet/src/test/.../CardServer behind a stand-in card manager) where the reader
# would be, to try these scripts with no chip; the guard runs against it all the same
tool() {
  compile_tool
  "$JAVA" -cp "$GP_JAR:$HERE/classes" J3r452 ${J3R452_BRIDGE:+--bridge "$J3R452_BRIDGE"} ${DRY:+--dry-run} ${READER:+--reader "$READER"} ${SERIAL:+--serial "$SERIAL"} ${ICTYPE:+--ictype "$ICTYPE"} "$@"
}
# the same, as the process itself (for a background bridge whose process id is then Java's, so that kill stops it)
tool_exec() {
  exec "$JAVA" -cp "$GP_JAR:$HERE/classes" J3r452 ${J3R452_BRIDGE:+--bridge "$J3R452_BRIDGE"} ${DRY:+--dry-run} ${READER:+--reader "$READER"} ${SERIAL:+--serial "$SERIAL"} ${ICTYPE:+--ictype "$ICTYPE"} "$@"
}

# GlobalPlatformPro: with the key when a command needs the card manager's authority
gp() { "$JAVA" -jar "$GP_JAR" ${READER:+-r "$READER"} "$@"; }

# one step: printed always; run unless this is a dry run
step() {
  if [ -n "$DRY" ]; then echo "  would run: $*"; return 0; fi
  echo "## $*" >> "$LOG"
  "$@" 2>&1 | grep -v "Invalid CPLC" | tee -a "$LOG"
}

say() { echo "$1"; [ -n "$DRY" ] || echo "## $1" >> "$LOG"; }
