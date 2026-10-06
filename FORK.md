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
| `docs/FOXY-CARD-SCREENS.md` | the wallet's card screens, as built |
| `docs/CARD-DESIGNS.md` | the codes of the designs a card's face can have, and how one is added |
| `docs/FOR-LNFLASH.md` | the note to upstream: what changed, what was found, what has and has not been tested |
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

What it holds lasts as long as the process, and no longer: a card server
that stops takes its card's contents with it. Stop one by its own process
number, never by a pattern that could match another, and read it first
(`ctl.sh <port> show`). Ecash put on a simulated card is not lost with it, as
long as the card had the simulator's fixed key (`run.sh <port> plain`): a new
server started the same way is the same card, and the wallet that loaded it
still holds the token it wrote. It prints each command's instruction and
status and never its data, because one command is the PIN.

## When a card arrives

Not run yet. `docs/HARDWARE_DEPLOYMENT.md` is upstream's guide and its steps
hold, with this fork's names in place of upstream's:

    ant -q -f applet/build.xml cap -Djc.sdk=$HOME/.javacard/sdks/jc305u4_kit
    gp --list                                   # the card answers, with its default keys
    gp --install applet/target/<the cap file>
    gp --apdu 00A404000AF0464F58594341524401    # SELECT by the applet's name: 0100 9000
    gp --apdu B0010000                          # GET_INFO: 16 bytes, 9000

    gp --delete F0464F58594341524401            # to take it off again: the applet,
    gp --delete F0464F585943415244              # then its package

`gp` is GlobalPlatformPro, which is not installed on this Mac yet. A card
locks itself for good after a run of failed key attempts, so `gp --list` with
no key given (its default test keys) is tried once, and not again if it
fails. Upstream's report for the same chip is
`docs/HARDWARE_TEST_REPORT_2026-09-22.j3r180.md`: its 6F00 on install came from
a probe in the constructor, which is the first thing to suspect here too.

After that, the phone: Foxy with the NFC entitlement (the app repository's
`tools/flashcard.entitlements`), MENU, FLASHCARD, TAP CARD.

## Not public

The spec's section 4 lists weaknesses in upstream 0.4. Upstream asks for those
privately. This repository stays private until they have been told.
