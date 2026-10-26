#!/bin/sh
# Who the chip in the reader is, before anything is put on it. Read only: no key is used and nothing is written, unless
# --list is given, which logs into the card manager once with the factory key to list what is installed.
#
#   sh tools/j3r452/identify.sh [--dry-run] [--reader NAME] [--list]
#
#   1. the readers, and the ATR of each card present (no command)
#   2. the card manager's data with no key: CPLC (fabricator, IC type, serial), IIN, CIN, card data (GlobalPlatform and
#      secure channel versions), key information, free memory (FF21), platform ID (DF20); then a SELECT of Foxy's applet,
#      the opener, the NDEF tag AID, the upstream applet, the two probes and a seller's preloaded applets. If Foxy's applet
#      answers, GET_INFO and GET_BALANCE are read and nothing else
#   3. GlobalPlatformPro's own reading (gp --info), with no key
#   4. with --list only: gp --list, which authenticates with the factory key ONCE. A key that is not the factory key is a
#      failed login, which the card counts; this script never tries again, and neither should you without the right key.
#
# Everything is kept in tools/j3r452/out/<time>/identify.log. Take every other card out of every reader first, card two above all.
. "$(dirname "$0")/common.sh"
LIST=""
while [ $# -gt 0 ]; do
  case "$1" in
    --dry-run) DRY=1 ;;
    --reader) READER="$2"; shift ;;
    --list) LIST=1 ;;
    *) echo "unknown option $1"; exit 64 ;;
  esac
  shift
done
need
if [ -z "$DRY" ]; then mkdir -p "$OUT"; LOG="$OUT/identify.log"; : > "$LOG"; else LOG=/dev/null; echo "DRY RUN: nothing is sent to any card. gp is: $JAVA -jar $GP_JAR ${READER:+-r \"$READER\"}"; fi

say "1. the readers"
if [ -n "$DRY" ]; then tool readers; else tool readers 2>&1 | tee -a "$LOG"; fi

say "2. the chip, read with no key"
if [ -n "$DRY" ]; then tool identify; else tool identify 2>&1 | tee -a "$LOG"; fi

say "3. gp --info (GlobalPlatformPro's reading, no key)"
if [ -n "$DRY" ]; then echo "  would run: gp --info"; else gp --info 2>&1 | tee -a "$LOG"; fi

if [ -n "$LIST" ]; then
  say "4. gp --list, logging in ONCE with the factory key"
  if [ -n "$DRY" ]; then echo "  would run: gp --key <GP_KEY> --list   (one login; if it fails, stop: do not try again)"
  else
    if ! gp --key "$GP_KEY" --list 2>&1 | tee -a "$LOG" | grep -q "^ISD:"; then
      say "STOP: the login with the factory key did not go through (see the log). Do not try again: ask the seller for the key."
      exit 3
    fi
  fi
fi
[ -n "$DRY" ] || say "kept in $LOG"
