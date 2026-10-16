#!/bin/sh
# Asks the chip in the reader whether an applet of ours can hold a fingerprint template. Card one (serial 09334120)
# or the blank card three (09301320); refuses card two. Loads the probe beside the card's applet, asks once, deletes the probe, and shows the
# card's applet answering as before. Nothing of the card's applet, record or money is touched.
#     sh tools/probe-bio/run.sh            (after: JAVA_HOME=/opt/homebrew/opt/openjdk@17 ant -f tools/probe-bio/build.xml)
J=/opt/homebrew/opt/openjdk@17/bin/java
GP="$J -jar $HOME/.foxycard/gp.jar --key 404142434445464748494A4B4C4D4E4F"
P="$(cd "$(dirname "$0")" && pwd)"
LOG="$P/run.log"; : > "$LOG"
say() { echo "$1"; echo "## $1" >> "$LOG"; }
card() { $J -jar $HOME/.foxycard/gp.jar -d -a 00A404000AF0464F58594341524401 -a B001000000 2>&1 | grep 'A<<' | sed 's/.*) //' | tr '\n' ' '; }
SERIAL=$($GP --info 2>/dev/null | grep ICSerialNumber | sed 's/.*=//' | tr -d ' ')
case "$SERIAL" in 09334120) WHICH="card one" ;; 09301320) WHICH="card three (blank, the spare)" ;; *) say "STOPPED: this is neither card one nor card three (serial read: '$SERIAL'); card two is never probed. Nothing was changed."; exit 1 ;; esac
BEFORE=$(card)
say "$WHICH; the card's applet answers: ${BEFORE:-nothing (no card applet on it)}"
say "loading the probe (a refusal here means the chip has no javacardx.biometry package)"
if ! $GP -d --install "$P/probe-bio.cap" >> "$LOG" 2>&1; then
  say "LOAD REFUSED: $(grep -a -o '0x6[0-9A-F]\{3\}\|SW [0-9A-F]\{4\}\|[0-9A-F]\{4\}$' "$LOG" | tail -1). The chip does not offer the fingerprint package. See run.log."
else
  ANS=$($J -jar $HOME/.foxycard/gp.jar -d -a 00A4040009F0464F585942494F01 -a B010000000 2>&1 | tee -a "$LOG" | grep 'A<<' | sed -n 2p | sed 's/.*) //')
  case "$ANS" in
    "01"*" 9000") say "TEMPLATE MADE: the chip let our applet build a fingerprint template (answer '$ANS': 01, bio type, initialised)." ;;
    *6B01) say "NO: the package is there but FINGERPRINT is not a supported type (answer '$ANS')." ;;
    *) say "REFUSED: answer '$ANS' (6Bxx = BioException reason: 01 ILLEGAL_VALUE, 02 INVALID_DATA, 03 NO_SUCH_BIO_TEMPLATE = no engine for that type, 04 NO_TEMPLATES_ENROLLED, 05 ILLEGAL_USE; 6Fxx = other reason)." ;;
  esac
  say "the same question for every biometric type of the 3.0.5 API (01 facial, 02 voice, 03 FINGERPRINT, 04 iris, 05 retina, 06 hand geometry, 07 signature, 08 keystrokes, 09 lip, 0A thermal face, 0B thermal hand, 0C gait, 0D body odour, 0E DNA, 0F ear, 10 finger geometry, 11 palm, 12 vein, 1F password; 13, 14 and 7F are no type):"
  ARGS="-a 00A4040009F0464F585942494F01"
  for T in 01 02 03 04 05 06 07 08 09 0A 0B 0C 0D 0E 0F 10 11 12 1F 13 7F; do ARGS="$ARGS -a B011${T}0000"; done
  $J -jar $HOME/.foxycard/gp.jar -d $ARGS 2>&1 | tee -a "$LOG" | grep 'A<<' | sed -n '2,$p' | sed 's/.*) //' | paste -d' ' - - - - - - - | sed 's/^/    /'
fi
say "deleting the probe"
$GP -d --delete F0464F585942494F01 >> "$LOG" 2>&1; $GP -d --delete F0464F585942494F >> "$LOG" 2>&1
LEFT=$($J -jar $HOME/.foxycard/gp.jar -d -a 00A4040009F0464F585942494F01 2>&1 | grep 'A<<' | head -1 | sed 's/.*) //')
say "probe after delete answers '$LEFT' (6A82 = gone); the card's applet now: $(card) (before: $BEFORE)"
