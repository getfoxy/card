#!/bin/sh
# The chip checks of software 1.23 on the new chip, through a bridge that serves this chip and no other, and the times.
#
#   sh tools/j3r452/suite.sh --serial XXXXXXXX [--ictype XXXX] [--reader NAME] [--port 47470] [--dry-run] [--sigall]
#
#   1. the guard: the chip is --serial, its applet answers 01 17 (1.23), and it is blank and holds nothing
#   2. the bridge (J3r452 serve): the chip's serial checked again, then 127.0.0.1:PORT; each command's time logged, never its data
#   3. tools/chip/proofstore.sh through it: the store of the mint's proofs, the book, a piece loaded with its DLEQ proof and read
#      back, a payment, RESET_CARD (36 checks on the J3R180 at 1.23)
#   4. tools/chip/changecheck.sh through it: the PIN, a card with no owner, the guest's held history, the claim, the card's own
#      change (SPEND_ALL_CHANGE, LOAD_CHANGE), a till's bound opening, and 1.23's dollars and sats on one card with its keyset
#      table, rate, waits and a 120-piece drawer (175 checks on the J3R180 at 1.23)
#   5. with --sigall only: the phone repository's live suite (flashcard-sigall.js) against the chip, at the local Docker mints
#      (fake money). It was last run on a chip at software 1.19 and has not been run against 1.23; it leaves the card set up,
#      so run install.sh --clean-own after it
#   6. the times: every "took" line, and the bridge log's milliseconds for the commands of a tap
#
# Both checks refuse a card that is not blank and end with it blank. Fake keys and a made-up mint only: no money is involved.
# Everything is kept in tools/j3r452/out/<time>/.
. "$(dirname "$0")/common.sh"
PORT=47470; SIGALL=""
while [ $# -gt 0 ]; do
  case "$1" in
    --dry-run) DRY=1 ;;
    --reader) READER="$2"; shift ;;
    --serial) SERIAL="$2"; shift ;;
    --ictype) ICTYPE="$2"; shift ;;
    --port) PORT="$2"; shift ;;
    --sigall) SIGALL=1 ;;
    *) echo "unknown option $1"; exit 64 ;;
  esac
  shift
done
need
[ -n "$SERIAL" ] || { echo "STOPPED: say which chip with --serial."; exit 14; }
case "$SERIAL" in 09334120|09334220|09301320) echo "STOPPED: $SERIAL is card one, two or three, never this script's."; exit 11 ;; esac
if [ -z "$DRY" ]; then mkdir -p "$OUT"; LOG="$OUT/suite.log"; : > "$LOG"; else LOG=/dev/null; echo "DRY RUN: nothing is sent to any card."; fi

say "1. the guard: the new chip, on 1.23, blank"
if [ -n "$DRY" ]; then tool --installed guard
else
  tool --installed guard > "$OUT/guard.txt" 2>&1; CODE=$?; cat "$OUT/guard.txt" >> "$LOG"; tail -1 "$OUT/guard.txt"
  [ "$CODE" = "0" ] || { say "STOPPED by the guard (exit $CODE). Nothing was changed."; exit "$CODE"; }
fi
if lsof -nP -iTCP:"$PORT" -sTCP:LISTEN >/dev/null 2>&1; then say "STOPPED: something already listens on port $PORT (--port to choose another)."; exit 6; fi

say "2. the bridge on 127.0.0.1:$PORT"
if [ -n "$DRY" ]; then tool serve "$PORT"; BRIDGE=""
else
  compile_tool
  ( tool_exec --log "$OUT/bridge.log" serve "$PORT" ) > "$OUT/bridge.out" 2>&1 &
  BRIDGE=$!
  trap 'kill "$BRIDGE" 2>/dev/null' EXIT INT TERM
  for i in 1 2 3 4 5 6 7 8 9 10; do grep -q "is on 127.0.0.1" "$OUT/bridge.out" 2>/dev/null && break; sleep 1; done
  if ! grep -q "is on 127.0.0.1" "$OUT/bridge.out"; then cat "$OUT/bridge.out"; kill "$BRIDGE" 2>/dev/null; say "STOPPED: the bridge did not start."; exit 7; fi
  cat "$OUT/bridge.out"
fi
FAILED=0
run_check() {  # name script
  say "$1"
  if [ -n "$DRY" ]; then echo "  would run: GP_JAR=$GP_JAR PATH=$JDK:\$PATH sh $REPO/tools/chip/$2 127.0.0.1:$PORT"; return; fi
  GP_JAR="$GP_JAR" PATH="$JDK:$PATH" sh "$REPO/tools/chip/$2" "127.0.0.1:$PORT" > "$OUT/${2%.sh}.log" 2>&1
  C=$?
  grep -a "FAIL\|took\|checks,\|THE " "$OUT/${2%.sh}.log"
  say "   $2 ended with exit $C ($(grep -a 'checks,' "$OUT/${2%.sh}.log" | tail -1))"
  [ "$C" = "0" ] || FAILED=1
}
run_check "3. the store of the mint's proofs (ProofStoreCheck)" proofstore.sh
if [ "$FAILED" = "0" ] || [ -n "$DRY" ]; then run_check "4. a PIN first, the claim, the card's own change, and 1.23's two units (ChangeCheck)" changecheck.sh
else say "4. skipped: step 3 failed, and the card may not be blank (install.sh --clean-own puts it right)"; fi

if [ -n "$SIGALL" ]; then
  say "5. the phone's live suite against the chip (fake money, local Docker mints)"
  FOXY="${FOXY_REPO:-$HOME/Desktop/Foxy}"
  NODE="${NODE:-$(command -v node)}"
  if [ -n "$DRY" ]; then
    echo "  would run: (cd $FOXY && sh tools/live/local-mint.sh up)"
    echo "  would run: (cd $FOXY && NODE_EXTRA_CA_CERTS=build/live-tls/cert.pem FOXY_LIVE_LOCAL=1 FOXY_CARD_CHIP=1 node tools/live/flashcard-sigall.js $PORT)"
    echo "  would run: (cd $FOXY && sh tools/live/local-mint.sh down)"
  else
    ( cd "$FOXY" && sh tools/live/local-mint.sh up ) >> "$LOG" 2>&1 || say "   the local mints did not start (is Docker running?)"
    ( cd "$FOXY" && NODE_EXTRA_CA_CERTS=build/live-tls/cert.pem FOXY_LIVE_LOCAL=1 FOXY_CARD_CHIP=1 "$NODE" tools/live/flashcard-sigall.js "$PORT" ) > "$OUT/sigall.log" 2>&1
    grep -a "FAIL\|passed, .* failed\|WATCHDOG\|THREW" "$OUT/sigall.log" | tail -15
    ( cd "$FOXY" && sh tools/live/local-mint.sh down ) >> "$LOG" 2>&1
    say "   the card is not blank after this suite: sh tools/j3r452/install.sh --serial $SERIAL --clean-own --skip-probes"
  fi
fi

[ -z "$BRIDGE" ] || kill "$BRIDGE" 2>/dev/null
say "6. the times"
if [ -n "$DRY" ]; then echo "  would collect every 'took' line, and the median milliseconds by instruction from the bridge log, into $OUT/times.txt"; exit 0; fi
{
  echo "From the checks (compare with the J3R180 column of the test plan, step h):"
  grep -a -h "took\|THE GUEST\|THE CLAIM" "$OUT"/proofstore.log "$OUT"/changecheck.log 2>/dev/null | sed 's/^ */  /'
  echo
  echo "From the bridge, the round trip of the commands of a tap and a set-up through the reader (class, instruction, P1: count, median, slowest, in ms):"
  awk '$1 ~ /^[0-9a-f][0-9a-f]$/ && $NF=="ms" {k=$1" "$2" p1 "$4; n[k]++; v[k]=v[k]" "$(NF-1)} END {for (k in n) print k, n[k], v[k]}' "$OUT/bridge.log" |
  while read cla ins p p1 count rest; do
    MED=$(echo $rest | tr ' ' '\n' | sort -n | awk '{a[NR]=$1} END {print a[int((NR+1)/2)]}')
    MAX=$(echo $rest | tr ' ' '\n' | sort -n | tail -1)
    case "$ins" in 15) N=AUTH ;; 22) N=SPEND_ALL_BEGIN ;; 23) N=SPEND_ALL_OUTPUTS ;; 24) N=SPEND_ALL_SIGN ;; 26) N=SPEND_ALL_CHANGE ;; 30) N=LOAD_PROOF ;; 31) N=CLEAR_SPENT ;;
      32) N=SET_CARD ;; 33|34) N=SET_LIMIT ;; 40) N=VERIFY_PIN ;; 41) N=SET_PIN ;; 43) N=SET_OWNER ;; 51) N=RESET_CARD ;; 53) N=LOAD_CHANGE ;; 18) N=GET_LOG ;; 1a) N=GET_DLEQ ;;
      19) N=GET_CHANGE ;; 11) N=GET_BALANCE ;; 01) N=GET_INFO ;; a4) N=SELECT ;; *) continue ;; esac
    echo "  $cla $ins p1 $p1  $N: $count, median $MED, slowest $MAX"
  done | sort
} > "$OUT/times.txt"
cat "$OUT/times.txt"
say "kept in $OUT"
[ "$FAILED" = "0" ] || { say "A CHECK FAILED: see the logs above before anything else."; exit 1; }
say "every chip check held."
