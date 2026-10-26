#!/bin/sh
# Puts the card software on a new chip, after proving the chip is the new one and that nothing of Foxy's is on it.
#
#   sh tools/j3r452/install.sh --serial XXXXXXXX [--ictype XXXX] [--cap FILE] [--reader NAME] [--dry-run]
#                              [--probes-only] [--skip-probes] [--no-tag] [--clean-own] [--size-try]
#
#   --serial     the chip's serial as identify.sh printed it (CPLC IC serial number); required
#   --ictype     the CPLC IC type identify.sh printed, when the chip's platform ID could not be read (see the test plan, step b)
#   --cap        the load file; ~/.foxycard/foxycard-1.23.cap unless said. Any other file needs --size-try (a size not yet tried)
#   --probes-only  run the two probes and stop: the card's software is not loaded
#   --skip-probes  load the card's software without the probes first (not advised on a chip that has not had them)
#   --no-tag     do not make the NFC tag (D2760000850101)
#   --clean-own  delete Foxy's own AIDs first (its tag, applet, opener, package, and the probes) if an earlier try left them;
#                nothing that is not Foxy's is ever deleted by this script
#
# It refuses, and changes nothing, when: the chip is card one, two or three; Foxy's applet answers (unless --clean-own, on the
# chip --serial names, which is then reinstalled); the CPLC is not NXP or is
# a JCOP 4 P71 (J3R180); the serial is not --serial; more than one reader holds a card; the factory key does not log in; or the
# load file is not the 1.23 build (without --size-try). After a refused INSTALL it resets the card and stops: it never retries.
#
# The log is kept in tools/j3r452/out/<time>/install.log.
. "$(dirname "$0")/common.sh"
CAP="$HOME/.foxycard/foxycard-1.23.cap"; PROBES_ONLY=""; SKIP_PROBES=""; NO_TAG=""; CLEAN=""; SIZE_TRY=""
while [ $# -gt 0 ]; do
  case "$1" in
    --dry-run) DRY=1 ;;
    --reader) READER="$2"; shift ;;
    --serial) SERIAL="$2"; shift ;;
    --ictype) ICTYPE="$2"; shift ;;
    --cap) CAP="$2"; shift ;;
    --probes-only) PROBES_ONLY=1 ;;
    --skip-probes) SKIP_PROBES=1 ;;
    --no-tag) NO_TAG=1 ;;
    --clean-own) CLEAN=1 ;;
    --size-try) SIZE_TRY=1 ;;
    *) echo "unknown option $1"; exit 64 ;;
  esac
  shift
done
need
[ -n "$SERIAL" ] || { echo "STOPPED: say which chip with --serial (run identify.sh first and copy its IC serial)."; exit 14; }
case "$SERIAL" in 09334120|09334220|09301320) echo "STOPPED: $SERIAL is card one, two or three, never this script's."; exit 11 ;; esac
if [ -z "$DRY" ]; then mkdir -p "$OUT"; LOG="$OUT/install.log"; : > "$LOG"; else LOG=/dev/null; echo "DRY RUN: nothing is sent to any card. gp is: $JAVA -jar $GP_JAR ${READER:+-r \"$READER\"}"; fi

# the load file
[ -f "$CAP" ] || { echo "STOPPED: no load file at $CAP"; exit 2; }
SHA=$(shasum -a 256 "$CAP" | cut -d' ' -f1)
SIZE=$(python3 -I "$REPO/tools/refcheck.py" "$CAP" | head -1)
say "load file: $CAP"
say "  sha256 $SHA; $SIZE"
if [ "$SHA" != "$CAP_123_SHA" ] && [ -z "$SIZE_TRY" ]; then
  say "STOPPED: this is not the 1.23 build that installed on the J3R180 (sha256 $(echo "$CAP_123_SHA" | cut -c1-16)...). For a size experiment say --size-try."
  exit 2
fi

# a. the chip: the new one, and nothing of Foxy's answering on it
say "a. the guard (read only)"
if [ -n "$DRY" ]; then tool ${CLEAN:+--own-reinstall} guard
else
  tool ${CLEAN:+--own-reinstall} guard > "$OUT/guard-before.txt" 2>&1; CODE=$?
  cat "$OUT/guard-before.txt" >> "$LOG"; grep -A40 "^what it says" "$OUT/guard-before.txt"
  if [ "$CODE" != "0" ]; then say "STOPPED by the guard (exit $CODE). Nothing was changed."; exit "$CODE"; fi
fi

# b. one login with the factory key: what is installed
say "b. gp --list: one login with the factory key"
if [ -n "$DRY" ]; then echo "  would run: gp --key <GP_KEY> --list   (one login; on a failed login the script stops and never tries again)"
else
  gp --key "$GP_KEY" --list > "$OUT/list-before.txt" 2>&1
  cat "$OUT/list-before.txt" >> "$LOG"
  if ! grep -q "^ISD:" "$OUT/list-before.txt"; then say "STOPPED: the factory key did not log in (see $OUT/list-before.txt). Do not try again: ask the seller for the key."; exit 3; fi
  grep -E "^(ISD|APP|PKG|SSD):" "$OUT/list-before.txt"
  if grep -q "$PKG\|$CAPS_PKG\|$BIO_PKG" "$OUT/list-before.txt"; then
    if [ -z "$CLEAN" ]; then say "STOPPED: Foxy's package or a probe is already on the chip (an earlier try). Run again with --clean-own to delete Foxy's own AIDs first."; exit 4; fi
  fi
  NDEF_TAKEN=""
  if grep -q "APP: $TAG" "$OUT/list-before.txt" && ! awk -v t="APP: $TAG" -v p="$PKG" 'index($0,t){f=1;next} f&&/^APP:|^PKG:/{f=0} f&&index($0,p){print}' "$OUT/list-before.txt" | grep -q .; then
    NDEF_TAKEN=1; say "NOTE: $TAG (the NFC tag's AID) belongs to something else on this chip: Foxy's tag will not be made. The applet and the chip checks do not need it."
  fi
fi
if [ -n "$CLEAN" ]; then
  say "   deleting Foxy's own AIDs only (tag only if it is Foxy's)"
  [ -n "$NDEF_TAKEN" ] || step gp --key "$GP_KEY" --delete "$TAG"
  for A in "$BIG" "$OPENER" "$PKG" "$CAPS_APP" "$CAPS_PKG" "$BIO_APP" "$BIO_PKG"; do step gp --key "$GP_KEY" --delete "$A"; done
fi

say "c. free memory before"
if [ -n "$DRY" ]; then echo "  would run: gp -d -a 00A4040000 -a 80CAFF2100"; else gp -d -a 00A4040000 -a 80CAFF2100 2>&1 | grep "A<<" | tail -1 | tee -a "$LOG"; fi

# d. the probes: every primitive the card's applet asks for at install, and the fingerprint engine
if [ -z "$SKIP_PROBES" ]; then
  say "d. the primitives probe"
  [ -f "$HERE/probe-caps/probe-caps.cap" ] || step env JAVA_HOME="$(dirname "$JDK")" ant -q -f "$HERE/probe-caps/build.xml"
  step gp --key "$GP_KEY" --install "$HERE/probe-caps/probe-caps.cap"
  if [ -n "$DRY" ]; then tool caps; CAPS=0; else tool caps > "$OUT/caps.txt" 2>&1; CAPS=$?; cat "$OUT/caps.txt" >> "$LOG"; sed -n '/^$/,$p' "$OUT/caps.txt"; fi
  step gp --key "$GP_KEY" --delete "$CAPS_APP"; step gp --key "$GP_KEY" --delete "$CAPS_PKG"
  say "   the fingerprint probe (tools/probe-bio)"
  BIOCAP="$REPO/tools/probe-bio/probe-bio.cap"
  [ -f "$BIOCAP" ] || step env JAVA_HOME="$(dirname "$JDK")" ant -q -f "$REPO/tools/probe-bio/build.xml"
  if [ -n "$DRY" ]; then echo "  would run: gp --key <GP_KEY> --install $BIOCAP   (a refused load = no javacardx.biometry on the chip)"; tool bio
  else
    if gp --key "$GP_KEY" --install "$BIOCAP" >> "$LOG" 2>&1; then tool bio 2>&1 | tee -a "$LOG" | tail -3
    else say "   the fingerprint probe's LOAD was refused: the chip has no javacardx.biometry package (see the log)."; fi
  fi
  step gp --key "$GP_KEY" --delete "$BIO_APP"; step gp --key "$GP_KEY" --delete "$BIO_PKG"
  if [ "$CAPS" = "20" ] || [ "$CAPS" = "21" ]; then say "STOPPED: the primitives probe says the card's software cannot install as built (exit $CAPS). The probes are deleted; the chip is as it was."; exit "$CAPS"; fi
  if [ "$CAPS" != "0" ]; then say "STOPPED: the primitives probe did not finish (exit $CAPS); see the log. The chip is as it was, less the probes."; exit "$CAPS"; fi
fi
[ -z "$PROBES_ONLY" ] || { say "probes only: stopping here. The chip is as it was."; exit 0; }

# e. the card's software: the opener with the load, then the card's applet, then the tag
say "e. loading $(basename "$CAP") and instantiating the opener"
T0=$(date +%s)
step gp --key "$GP_KEY" --install "$CAP" --applet "$OPENER"
say "   the load and the opener took about $(( $(date +%s) - T0 )) s"
say "   the card's applet (its constructor makes the key and about 45 KB of storage)"
T0=$(date +%s)
step gp --key "$GP_KEY" --create "$BIG" --package "$PKG" --applet "$BIG"
say "   the INSTALL took about $(( $(date +%s) - T0 )) s (about 1 to 2 s on the J3R180; 7 to 9 s and no answer was its refusal)"
if [ -z "$DRY" ]; then
  SAID=$(gp -d -a 00A404000A$BIG 2>&1 | grep "A<<" | head -1 | sed 's/.*) //')
  if [ -z "$SAID" ]; then tool reset >> "$LOG" 2>&1; SAID=$(gp -d -a 00A404000A$BIG 2>&1 | grep "A<<" | head -1 | sed 's/.*) //'); fi
  say "   the card's applet answers: '$SAID' (0117 9000 is software 1.23)"
  if [ "$SAID" != "0117 9000" ]; then
    say "NOT INSTALLED. Write down: the status gp printed for the INSTALL (in the log), the seconds above, and the free memory. Do not retry and"
    say "do not pad yet: follow the test plan, step e (\"when the INSTALL is refused\")."
    gp --key "$GP_KEY" --list > "$OUT/list-after-refusal.txt" 2>&1
    exit 5
  fi
fi
if [ -z "$NO_TAG" ] && [ -z "$NDEF_TAKEN" ]; then
  say "   the NFC tag, under the NFC Forum's AID"
  step gp --key "$GP_KEY" --create "$TAG" --package "$PKG" --applet "$TAGCLASS"
  if [ -z "$DRY" ]; then
    CC=$(gp -d -a 00A4040007$TAG -a 00A4000C02E103 -a 00B000000F 2>&1 | grep "A<<" | sed -n 3p | sed 's/.*) //')
    say "   the tag's capability container: '$CC' (000F20003B00340406E104001600FF 9000 is right)"
  fi
fi
say "f. free memory after"
if [ -n "$DRY" ]; then echo "  would run: gp -d -a 00A4040000 -a 80CAFF2100"; else gp -d -a 00A4040000 -a 80CAFF2100 2>&1 | grep "A<<" | tail -1 | tee -a "$LOG"; fi
if [ -n "$DRY" ]; then tool --installed guard; else tool --installed guard > "$OUT/guard-after.txt" 2>&1; CODE=$?; tail -1 "$OUT/guard-after.txt" | tee -a "$LOG"; [ "$CODE" = "0" ] || exit "$CODE"; fi
say "INSTALLED. Next: sh tools/j3r452/suite.sh --serial $SERIAL${ICTYPE:+ --ictype $ICTYPE}${READER:+ --reader \"$READER\"}"
