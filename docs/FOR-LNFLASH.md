# Foxy card: a fork of cashu-javacard, for your review

From the Foxy wallet project (getfoxy), to the authors of `lnflash/cashu-javacard`.

We forked your applet at 0.4 (`c1f4580`, MIT) to make a card that pays a Foxy
wallet on an iPhone by a tap and a PIN. This note says what we changed and
why, what we have and have not tested, and where we would most value your
eyes. Section 3 includes things we found in your code as it stood at that
commit. They are sent to you here, privately, as your SECURITY.md asks, and
our fork and its specification stay private until you have had time with them.

**State of it, plainly:** nothing has run on a real card. The applet builds
to a CAP with the JavaCard 3.0.5 kit and passes its tests under jCardSim. The
wallet and the iPhone app have been driven end to end against the applet's
own class in jCardSim, including with a few real sats at your own mint
(forge.flashapp.me). Cards and a reader are on order.

## 1. What we would like you to look at

1. **SPEND with no message from the reader** (3.2). Is rebuilding the secret
   on the card and hashing it there sound, and affordable on a J3R180? It is a
   string of 170 to 270 bytes and one SHA-256 for each piece, before the
   signature you already make.
2. **AUTH** (4). Is a signature over a tagged hash a safe way to prove the
   card holds its key, given that the same key signs spends?
3. **The limit for one PIN entry** (3.8). It is counted in RAM and ends with
   the tap. Is there a way round it we have not seen?
4. **The date and refund key on a piece** (5). Your notes called a refund
   path "the right shape" and named its flaw. We think the date on the slot
   answers it. Do you?
5. **Anything about real hardware** that jCardSim would not show us: memory
   for 64 slots of 82 bytes, torn writes, how long a tap takes with several
   pieces to sign.
6. Whether you would want any of this back upstream. The fork is not
   compatible with your wire, by intent, and has an AID of its own so neither
   applet can be taken for the other.

## 2. What it does

- A holder sets a PIN on a new card from their own phone. The card records
  which mint its ecash is at.
- The holder puts money on it: ordinary ecash, locked to the card's key
  (NUT-11), written into slots.
- To pay, the receiver's phone asks for the card's PIN, the card is tapped,
  the card signs for enough pieces to cover the amount, and the receiver
  swaps them at the mint. Only when the mint has answered does the receiver
  say paid.
- Change is made by the receiver, locked to the card's key, and written back
  onto the card in the same tap. If the card has left, it waits for that card.
- The holder can withdraw to their phone, change the PIN, and set a limit.

## 3. What we found in 0.4, and what the fork does

We read 0.2 first and 0.4 again at `c1f4580`. If any of this has changed
since, the fault is in our reading.

| # | In 0.4 as we read it | The fork |
|---|---|---|
| 1 | A blocked PIN used to open every gated command. **Fixed in 0.4** (`requirePinIfSet` treats blocked as set). | Kept, with a test that walks every gated command through every PIN state. |
| 2 | `SPEND_PROOF` signs 32 bytes the reader supplies. The card does not check that they are the hash of the slot's own secret, so a reader chooses what is signed, and the slot marked spent need not be the piece that was spent. | `SPEND` takes a slot number and nothing else. The card builds that slot's secret from its own key and the slot's fields, hashes it, marks the slot spent, then signs. |
| 3 | `SIGN_ARBITRARY` signs any 32 bytes with the card's key and burns no slot. With the PIN, a reader can sign for every piece on the card and leave every slot reading unspent. | Removed. Proving the card is the card has its own command (section 4). |
| 4 | A card with no PIN accepts loads and spends from any reader in range. | Nothing is written to a card until a PIN is set and verified. |
| 5 | A reader with the PIN can spend every slot in one tap. By design, but the holder has typed their PIN into somebody else's terminal. | A limit, kept on the card, on what one PIN entry may spend. |
| 6 | The card does not say which mint its pieces are at (an open question in your notes). | A card record names the mint. |
| 7 | No DLEQ on the card, so a piece cannot be checked without the mint. | Unchanged. Our receiver is always online and always swaps before saying paid. |
| 8 | The deployment guide installs with the card's factory GlobalPlatform keys and does not change them afterwards. A card left on them can have the applet deleted, and the money with it, by anyone with a reader. | Not the applet's to fix. Our provisioning steps will change them. |
| 9 | In flash-pos, when we read it: an exact payment was shown as paid when the swap had failed, and a proof the mint reported as spent was booked as settled, whoever spent it. | Not our code, and you may have fixed it. Our rule is that paid means the mint has given this phone its own pieces. |

Number 2 and number 3 are the ones that matter most. Together they mean the
SPENT flag on the card protects nothing: the signature is what spends, and
the card will sign without burning the right slot, or any slot.

## 4. What changed on the wire

Class `B0` as yours. AID: package `F0 46 4F 58 59 43 41 52 44`, applet the same with `01`.

| Command | Yours | Ours |
|---|---|---|
| `01` GET_INFO | 8 bytes | 16: adds format, tries left, locked, has-record, limit |
| `13` GET_PROOF | 78-byte slot | 82: adds a 4-byte date |
| `15` AUTH | none | reader sends 16 random bytes; card answers 16 of its own and a BIP-340 signature. No PIN |
| `16` GET_CARD | none | the card record |
| `20` SPEND | slot + 32-byte message | slot only |
| `21` SIGN_ARBITRARY | signs anything | removed (`6D00`) |
| `30` LOAD_PROOF | 77 bytes | 81, with the date; needs a PIN set and verified, and a card record |
| `32` SET_CARD | none | writes the record; refused while the card holds unspent pieces |
| `33` SET_LIMIT | none | sats for one PIN entry; 0 for none |

64 slots of 82 bytes (yours: 32 of 78). The card record is unit, limit, a
33-byte refund key and the mint's address (up to 96 bytes).

**What SPEND signs.** SHA-256 of the secret, where the secret is the exact
text cashu-ts writes for a P2PK piece, built on the card:

    ["P2PK",{"nonce":"<64 hex>","data":"<card key, 66 hex>","tags":[]}]

and for a piece with a date:

    ["P2PK",{"nonce":"…","data":"…","tags":[["locktime","<decimal>"],["refund","<66 hex>"]]}]

So loading a card is an ordinary locked send from any NUT-11 wallet, and
nothing is made specially for it. This is not your secret text.

**What AUTH signs.** SHA-256 of `T ‖ T ‖ reader16 ‖ card16 ‖ cardkey33`, with
`T = SHA-256("FoxyCard/auth")`. The message is 129 bytes behind a tag; a
secret is text beginning `["P2PK"`. We believe no AUTH message can be a
secret's preimage, and would like that checked.

## 5. A lost or blocked card

The applet and the wallet can make a card recoverable: each piece carries a
locktime a year ahead and a refund key the loading phone derives from its
seed, and after that date the phone can take back what the card has not
spent. The card has no clock, which is the flaw your notes name: a mint may
stop honouring the card's own signature once the date passes. So the date is
on the slot where a reader sees it, and a receiving wallet refuses a piece
within a week of its date before asking the card to sign.

**In the app this is switched off for now.** Cards are set up as cash, as
yours are: lose the card, forget the PIN, or block it, and the money is gone.
The app says so when a card is set up.

## 6. What has been tested, and what has not

Tested:
- The applet under jCardSim: 53 tests, including every gated command in every
  PIN state, the secret the card signs against the text a wallet builds, the
  limit, and your write-order tests adapted to the new slot.
- The wallet's flows against a JavaScript model of the card, and the model
  against a recorded conversation with the applet (69 commands).
- The same flows against the applet itself over a socket: set-up, loading,
  paying with change, a card pulled away at each point of a payment in turn,
  the limit, PIN change, three wrong PINs, emptying.
- The iPhone app in the simulator against the applet in jCardSim, at a test
  mint and at forge.flashapp.me: set up, add funds, pay an invoice by card
  with change written back, withdraw.

Not tested:
- A real card. No install, no NFC, no timing, no torn write.
- CoreNFC on a phone. The reader code compiles for a device and has not run.
- Any mint's behaviour once a locktime has passed.

## 7. Three things that may be useful to you

- **Long keyset ids.** A slot has 8 bytes for the keyset. Mints we tried now
  name keysets by 33 bytes (ids beginning `01`). We store NUT-02's short form,
  the first 8 bytes, and the wallet expands it against the mint's keyset list,
  refusing if two keysets share it. Your slot has the same 8 bytes. Your own
  mint still uses the 8-byte form, so you may not have met this yet.
- **A phone should not carry whatever a page hands it.** Our iPhone code reads
  each command before sending it and carries only the SELECT of our own AID
  and our applet's instructions. The AID is also the only one in the app's
  Info.plist, so iOS never hands the app another card.
- **jCardSim gives every card the same key**, as your source notes. Two
  simulated cards are one card to a wallet that files things by card key. Our
  test server reseeds the key pair by reflection.

## 8. What it does not protect against

- An altered receiving phone can show one amount and ask the card for more,
  up to the card's limit.
- A pretend card can collect a PIN typed for it. A receiver loses nothing to
  one, because it goes to the mint before saying paid.
- The PIN crosses a few centimetres of air in the clear.
- A cash card that is lost or blocked is lost money.

## 9. Where it is

Two private repositories. Tell us which GitHub accounts to invite.

- `getfoxy/card`: the applet. Start with `FORK.md`, then
  `docs/FOXY-CARD-SPEC.md`. `spec/vectors/` has the secrets and a full
  conversation with the card. Your own `spec/`, `README` and `tools/cardctl`
  are still in the tree and still describe your wire.
- `getfoxy/iOS-card`, branch `flashcard`: the wallet. The card code is
  `build/wallet/08a-flashcard.js` and `21a-flashcard.js`, the iPhone side
  `Foxy/Flashcard/`, the tests `tests/flashcard-*.js`.

The wallet itself is public at `getfoxy/iOS`; none of the card work is in it.
