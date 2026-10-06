# This is a fork

`getfoxy/card` is a private fork of `lnflash/cashu-javacard` (applet 0.4,
`c1f4580`, MIT). The applet here is **not compatible on the wire** with
upstream's, and has an AID of its own (`F0 46 4F 58 59 43 41 52 44`) so that
neither can be mistaken for the other.

What changed and why is in [`docs/FOXY-CARD-SPEC.md`](docs/FOXY-CARD-SPEC.md),
section 4. In short:

- `SPEND_PROOF` takes no message. The card rebuilds the slot's own secret and
  signs its hash; nothing a reader sends reaches the signer.
- `SIGN_ARBITRARY` is removed. `AUTH` proves the card is the card, over a
  tagged hash no piece's secret can equal.
- Nothing is loaded onto a card with no PIN.
- One PIN entry spends no more than a limit kept on the card.
- A slot carries a date and the card a refund key, so the pieces of a lost or
  blocked card can be taken back by whoever loaded them.
- The card records the mint its pieces are at.
- 64 slots of 82 bytes.

## What is and is not up to date

| Part | State |
|---|---|
| `applet/` and its tests | the fork. `mvn -f applet/pom.xml test`, and `ant -f applet/build.xml cap -Djc.sdk=…/jc305u4_kit` |
| `docs/FOXY-CARD-SPEC.md` | the fork |
| `spec/`, `README.md`, the other `docs/` | upstream's, describing upstream's wire. Not yet rewritten |
| `tools/cardctl`, `tools/e2e-*` | upstream's host tools, for upstream's wire. They do not drive this applet |
| CI | switched off here until the host tools are ported |
| `tools/cardsim/` | new: the applet on a loopback port, for Foxy in the iOS Simulator (below) |

Nothing here has run on a card yet. The applet has been converted to a CAP by
the JavaCard 3.0.5 tools and tested under jCardSim, which cannot reproduce
EEPROM limits, torn writes, or how long a signature takes.

## A card with no card

`sh tools/cardsim/run.sh` runs the applet in jCardSim and listens on
127.0.0.1:47431. Foxy's simulator build has no NFC and reaches a card there
instead, so the app's card screens, its bridge and a mint can be driven against
the applet's own class before a card exists. `sh tools/cardsim/ctl.sh off`
takes the card off the reader; `on`, `pull N` (let N more commands through,
then take it away), `new` and `show` are the others. The app's repository has
`tests/flashcard-applet.js`, which drives the wallet's flows over the same
socket from Node.

What it holds lasts as long as the process. It prints each command's
instruction and status and never its data, because one command is the PIN.

## Not public

The spec's section 4 lists weaknesses in upstream 0.4. Upstream asks for those
privately. This repository stays private until they have been told.
