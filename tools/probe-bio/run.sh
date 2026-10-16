#!/bin/sh
# Asks the chip in the reader whether an applet of ours can hold a fingerprint template. Card one only (serial
# 09334120); refuses card two. Loads the probe beside the card's applet, asks once, deletes the probe, and shows the
# card's applet answering as before. Nothing of the card's applet, record or money is touched.
#     sh tools/probe-bio/run.sh            (after: JAVA_HOME=/opt/homebrew/opt/openjdk@17 ant -f tools/probe-bio/build.xml)
J=/opt/homebrew/opt/openjdk@17/bin/java
GP="$J -jar $HOME/.foxycard/gp.jar --key 404142434445464748494A4B4C4D4E4F"
P="$(cd "$(dirname "$0")" && pwd)"
LOG="$P/run.log"; : > "$LOG"
say() { echo "$1"; echo "## $1" >> "$LOG"; }
card() { $J -jar $HOME/.foxycard/gp.jar -d -a 00A404000AF0464F58594341524401 -a B001000000 2>&1 | grep 'A<<' | sed 's/.*) //' | tr '\n' ' '; }
SERIAL=$($GP --info 2>/dev/null | grep ICSerialNumber | sed 's/.*=//' | tr -d ' ')
case "$SERIAL" in 09334120) ;; *) say "STOPPED: this is not card one (serial read: '$SERIAL'). Nothing was changed."; exit 1 ;; esac
BEFORE=$(card)
say "card one; its applet answers: $BEFORE"
say "loading the probe (a refusal here means the chip has no javacardx.biometry package)"
if ! $GP -d --install "$P/probe-bio.cap" >> "$LOG" 2>&1; then
  say "LOAD REFUSED: $(grep -a -o '0x6[0-9A-F]\{3\}\|SW [0-9A-F]\{4\}\|[0-9A-F]\{4\}$' "$LOG" | tail -1). The chip does not offer the fingerprint package. See run.log."
else
  ANS=$($J -jar $HOME/.foxycard/gp.jar -d -a 00A4040009F0464F585942494F01 -a B010000000 2>&1 | tee -a "$LOG" | grep 'A<<' | sed -n 2p | sed 's/.*) //')
  case "$ANS" in
    "01"*" 9000") say "TEMPLATE MADE: the chip let our applet build a fingerprint template (answer '$ANS': 01, bio type, initialised)." ;;
    *6B01) say "NO: the package is there but FINGERPRINT is not a supported type (answer '$ANS')." ;;
    *) say "REFUSED: answer '$ANS' (6Bxx = BioException reason, 6Fxx = other reason)." ;;
  esac
fi
say "deleting the probe"
$GP -d --delete F0464F585942494F01 >> "$LOG" 2>&1; $GP -d --delete F0464F585942494F >> "$LOG" 2>&1
LEFT=$($J -jar $HOME/.foxycard/gp.jar -d -a 00A4040009F0464F585942494F01 2>&1 | grep 'A<<' | head -1 | sed 's/.*) //')
say "probe after delete answers '$LEFT' (6A82 = gone); the card's applet now: $(card) (before: $BEFORE)"
