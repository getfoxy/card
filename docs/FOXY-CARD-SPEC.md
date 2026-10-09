# Foxy card: a specification for forking cashu-javacard

A card that holds ecash and pays a Foxy by a tap, and the changes to make in a
fork of `lnflash/cashu-javacard` and in Foxy to get there.

The card's daily limit, its owner and its clock are specified in full in
`FOXY-CARD-DAILY-LIMIT.md`. This document keeps the part of them that the rest
of the specification leans on, and refers to that one for the whole. An earlier
draft had an allowance that only went down and an owner secret given to the
card; both are gone, and where the two documents differ, that one is what is
built.

## 0. What this is based on

- `lnflash/cashu-javacard`, applet 0.4, commit `c1f4580`, MIT. Its `spec/APDU.md`,
  `spec/NUT-XX.md` (Profile B and B+), `docs/DECISIONS.md` and the J3R180
  hardware report.
- The review made of applet 0.2 and its terminal, read again against 0.4.
- Upstream has spent a real card from an iPhone over CoreNFC and settled it at
  a mint (their hardware report). The transport is proven; nothing here
  depends on something nobody has run.

## 1. Goals

1. A person taps a card on a Foxy that is asking to be paid, types the card's
   PIN on that phone, and the payment is made or it fails, with nothing left
   in doubt.
2. The card's holder can see what is on it, put money on it and take money
   off it from their own Foxy.
3. A wrong or blocked PIN opens nothing, and on a card with an owner the PIN
   alone changes nothing but what it spends. An honest terminal that has been
   handed the PIN can take no more than the card's daily limit in a day, and
   only the owner's phone can set, change or remove the limit (5.7). A
   terminal built to cheat is not bounded until the time the card is told comes
   from a real signer (5.7). A blocked card is not lost money: its owner's
   phone unblocks it. A lost card is not lost money where the card was set up
   to be recoverable (6).
4. The receiver is never shown "paid" for money it does not hold.

Not goals of the first version: paying a receiver that has no connection,
cards from other issuers, a card without a PIN, units other than sats.

## 2. What the person sees

### 2.1 Paying by card (receiver's phone, receive invoice screen)

1. The receive screen has a third way to be paid beside the code and TAP:
   **CARD**.
2. CARD opens the PIN pad, titled with the amount: `PAY 1,180 SATS · CARD PIN`.
   The payer types their PIN. Nothing is checked yet; the phone cannot check
   a PIN without the card.
3. iOS's own sheet asks for the card: `Hold the card to the top of the phone`.
4. The card is read, asked to sign, and the phone goes to the mint. The screen
   says `KEEP THE CARD THERE` until change, if any, is back on the card, then
   `REMOVE THE CARD`.
5. One of:
   - **PAID**, the usual confirmation, with `CARD` as how it came.
   - **WRONG PIN**: `2 tries left.` Back to step 2. Nothing was taken.
   - **CARD BLOCKED**: `Too many wrong PINs. This card can no longer pay until
     its owner sets a new PIN on it.` The owner's phone unblocks it (2.2).
   - **NOT ENOUGH ON THE CARD**: `It holds 900 sats.` Nothing was taken.
   - **OVER THE CARD'S DAILY LIMIT**: the card holds enough, but what is left
     of today's limit cannot cover the payment. The phone reads the limit, what
     has been spent today and the card's day from the card before it sends the
     PIN, and says in plain words how much the card can still spend today and
     when its day turns. Nothing was taken.
   - **A DIFFERENT MINT**: `This card's money is at <mint>. This phone is at
     <mint>.` (first version; see 8.4).
   - **TAP THE CARD AGAIN**: the card left the phone before its change was
     written back. The payment is made; `212 sats of change are waiting to go
     back on the card.` Shown until it is written. The receiver cannot spend
     that change.
   - **NOT PAID**: the mint refused or could not be reached before anything
     was signed. Nothing was taken.
   - **CHECKING**: the card signed and the mint has not answered. The phone
     keeps asking; it ends as PAID or as money to go back on the card.

### 2.2 Menu › Flashcard (the holder's own phone)

As built; the screens themselves are in `FOXY-CARD-SCREENS.md`.

What the card keeps is a daily limit: the most it will sign for in one day
(5.7). The screens call it the daily limit, or the limit, and its menu row
CHANGE LIMIT.

1. **FLASHCARD** goes straight to the iOS sheet; no PIN. The screen then
   shows:
   - how fresh the mint's word on the card is (`Verified Just Now`; on a
     phone with no connection, when it last was, for the same pieces);
   - the mint it is at and its balance, and under it the card's daily limit
     (`DAILY LIMIT`, or `NO LIMIT`) and, with a limit, what is left of it today
     (`LEFT TODAY`) and when the day turns (`THE DAY TURNS AT`);
   - on the card's face, what is not ordinary: no PIN yet, locked, blocked,
     or pieces the mint says are spent.
2. **SET UP THIS CARD**, the first time: a PIN typed twice, a notice that this
   phone will be the card's owner, and a tap. It asks for no limit and suggests
   no figure: a new card has none. The one tap gives the card its PIN, its
   record (with the time key) and, last, its owner key (5.6). Nothing can be
   added to a card with no PIN, and nothing to a card with no owner. Cards are
   set up as cash for now (6).
3. **ADD FUNDS.** An amount and a tap. The owner's phone is not asked for the
   PIN: it gives the card the owner's proof, and the card lets the money in
   for that tap (5.6). Any other phone is asked for the card's PIN. The money
   leaves this phone's balance and is on the card when the screen says so. If
   the card leaves early the pieces wait for it, and the screen says so until a
   tap writes them. Putting money on a card does not touch its limit.
4. **WITHDRAW.** An amount or all of it, the PIN, a tap, and it is in this
   phone's balance. The owner's phone withdraws, renews and takes everything
   off a card whatever its limit: in the same tap it lifts the limit with the
   owner's proof, spends under the PIN, and puts the limit back with the
   owner's proof, even when the spending fails part way. If the card leaves
   before the limit can be put back, the phone has written the old limit down
   first and puts it back at the next tap. A phone that does not hold the
   card's words cannot lift the limit, and says so.
5. **CHANGE PIN.** The new PIN, typed twice, and a tap. No old PIN is asked:
   the owner's phone does not know it, and the card takes the owner's proof in
   its place (5.6), so it works only on a phone that holds the card's words; on
   any other it fails, and the screen says so in plain words. It is also how a
   blocked card is opened: a blocked card, on its owner's phone, offers
   UNBLOCK, which is CHANGE PIN, and the screen then says CARD UNBLOCKED.
6. **CHANGE LIMIT.** Three steps, then a tap. (1) A full-screen warning, titled
   `SET DAILY LIMIT`, that says what a daily limit is, that only this phone or
   a phone restored from its seed phrase can change or remove it, and that if
   the seed phrase is lost the PIN and the limit on the card can never be
   changed; CONTINUE or CANCEL. (2) The app's SET AMOUNT screen, dollars first:
   `What would you like the daily limit to be?`, with NO LIMIT under NEXT as
   the way to remove a limit. (3) A confirmation, `YOU ARE APPLYING A DAILY
   LIMIT OF:` and the amount, with what it means (the card will spend no more
   than that in one day; the limit starts again by itself each day; only this
   phone, or one restored from its seed phrase, can change or remove it), or,
   for NO LIMIT, `YOU ARE REMOVING THIS CARD'S DAILY LIMIT.` and that the card
   will be able to spend everything on it; CONFIRM or CANCEL. The tap writes it
   to the card with the owner's proof; no PIN is asked. The exact words are in
   B7 of `FOXY-CARD-SCREENS.md`. Nothing suggests a figure, and no screen says
   that the limit stops an attacker: with the interim time signer (5.7) it does
   not.
7. **The card's history**: what this phone has done with it. A card keeps no
   list of its own.
8. **TAKE BACK A LOST CARD.** For a card this phone funded as recoverable:
   after its date, the money comes back with no card (see 6). Built, and on
   no screen while cards are cash.

## 3. Who is trusted with what

| Party | Holds | Can do wrong |
|---|---|---|
| Card | its key, the pieces, the PIN, its owner's public key, the time key, the day | nothing by itself; it signs what it is asked, under its rules, up to the day's limit |
| Cardholder | the card and the PIN | pay twice from a copied card, if a receiver ever accepts without the mint |
| Owner's phone | the twelve words, and from them the card's owner key | with the card in hand: change the PIN, set or remove the limit, add funds, and so take everything. Without the card: nothing. It is a trustee, held on a leash of two things: its words and the card itself. For a holder with no phone of their own it is a friend's |
| Receiving phone | sees the PIN, talks to the card | ask the card to sign more than the amount shown, up to what is left of today's limit; keep change; write pieces the mint will refuse |
| Mint | decides what is spent | what any Cashu mint can |
| Time signer | a key | **sign a later time whenever it likes, day after day, in one tap, and so, with the PIN and the card, pass any limit**; sign one far-future time and freeze a card's day until its time key is replaced (5.7). Never move a clock backward |

Three things shape the design. The receiver always goes to the mint before
saying paid, so a copied or pretend card gains nothing. A terminal sees the
PIN and chooses what to ask the card for, so nothing the PIN alone can reach
may bound it or change it: the PIN spends and nothing else, and what bounds the
spending is a number in the card's permanent memory that only the owner's proof
sets (5.3, 5.7). And the one thing a terminal cannot manufacture, **once the
time key is held by a real signer and not by every copy of the app**, is
tomorrow: it can fetch a true "now", but not a future one, so a window that only
time can reset is a bound it cannot walk round. With the interim signer that
last sentence is not yet true. For now the time the card is told is the
receiving phone's own clock, signed by a key that is built into the app and so
is not secret. That is as weak as trusting that clock: it bounds an honest
receiver and the holder's own overspending, and it does not bound a terminal
built to cheat (5.7).

## 4. What the review found, and what the fork does

| # | Found | In upstream 0.4 | The fork |
|---|---|---|---|
| 1 | A blocked PIN took the PIN gate away | fixed (a blocked PIN counts as set) | keep; add a test that every gated command refuses in every PIN state |
| 2 | `SPEND_PROOF` signs 32 bytes the reader supplies, not the stored piece's own message | still so | the card works the message out itself from the slot; the reader supplies nothing (5.2) |
| 3 | `SIGN_ARBITRARY` signs anything and burns no slot, so SPENT enforces nothing | still there, behind the PIN | removed. Proving the card is the card gets its own command, whose signature cannot be a spend (5.2) |
| 4 | No DLEQ on the card, so a piece cannot be checked without the mint | still so | not needed while the receiver is online; a place is left for it (phase 3) |
| 5 | Factory GlobalPlatform keys left on the card | provisioning, not the applet | the provisioning steps set new keys, and say so (5.4) |
| 6 | The terminal showed paid when the swap had failed, and booked another's spend as settled | their terminal, not used here | Foxy's own rule: paid only when the mint has given this phone its pieces (8) |
| 7 | Over-payment is the merchant's income; change is not returned in the payment | by design upstream | change goes back to the card in the same tap, or waits for it (8.2) |
| 8 | A terminal with the PIN can spend every slot | by design upstream | a daily limit in the card's permanent memory, counted against a day the card keeps from a signed time: every spend adds the whole piece signed to today's total, a piece that would take the day past the limit is refused, and only the owner's proof sets or removes the limit (5.3, 5.7). It bounds an honest terminal to one day's limit per visit. It does not bound a terminal built to cheat while the time is signed by a key inside the app (5.7). Two earlier tries failed: a limit on one PIN entry was walked round by a terminal that had the PIN, and an allowance that only went down and only the owner's proof raised held against a terminal but stopped a card whose owner's phone was not at hand |
| 9 | A blocked or lost card strands its balance | acknowledged; no unblock | a blocked card is unblocked by its owner's phone (`CHANGE_PIN`, 5.6); for a lost card, a refund path the funding phone holds (6) |
| 10 | The card does not say which mint its pieces are at | open question upstream | the card records its mint (5.1) |
| 11 | The PIN crosses the air in the clear | so | phase 2: sent under a key agreed with the card. Nothing of the owner crosses the air: the card holds only the owner's public key, and a proof is a signature over a nonce the card has just given (5.6) |
| 12 | `CHANGE_PIN` needs a verified session and the old PIN, both of which a terminal handed the PIN has: it can set a PIN the holder does not know, and the holder's wrong tries then block the card for good | so | the owner's proof replaces both: `CHANGE_PIN` takes the proof and the new PIN only, no old PIN and no session, so the owner's phone can change the PIN, or unblock a blocked card, without knowing the PIN (5.6) |
| 13 | `LOCK_CARD` needs the PIN only if one is set: any reader can lock a card with no PIN for good, and a terminal that has the PIN can lock a funded card | so | a PIN set and verified, and the owner's proof (5.6); a card with no owner cannot be locked |
| 14 | A card with no PIN can be claimed by any reader: it can set the PIN, and in the fork the owner | so | a card with no owner is open while it is empty and cannot be loaded, so a claimed card holds nothing. A card with an owner is its owner's, empty or not: a terminal that holds the PIN and empties the card cannot then set its own PIN, owner, record or limit (5.3). A card still leaves the issuer's hands set up (5.4) |

## 5. The card

### 5.1 What it stores

A card record. `SET_CARD` writes the unit, the refund key, the time key, the
mint and the flag that it has run, and never the limit, the day or `now`
(except that a time key different from the one the card holds clears `now` and
the window with it, 5.3 rule 4). The limit is written by `SET_LIMIT`, `now` by
`SET_TIME`, and the window and what has been spent by `SET_LIMIT` and
`SPEND_PROOF`.

| Field | Size | |
|---|---|---|
| format | 1 | 3 for this design |
| set | 1 | 1 once `SET_CARD` has run |
| unit | 1 | sats |
| limit | 4 | the most the card signs for in one day, in sats, big-endian; **0 is no limit, and is what a new card has** |
| refund key | 33 | the key that can take the pieces back after their date, or zeros for none |
| time key | 65 | the time signer's public key, uncompressed (`04 ‖ X ‖ Y`), written by `SET_CARD` |
| now | 4 | the latest signed time the card has accepted, seconds, big-endian; 0 until it has been told one. Written only by `SET_TIME` (and cleared only as 5.3 rule 4 says) |
| window start | 4 | when the current day began; written by `SET_LIMIT` and by a `SPEND` that begins a new day |
| spent today | 4 | what the card has signed for since the window began |
| mint | 1 + up to 80 | the mint's address, as text |

The mint is at most 80 characters, not 96 as the earlier draft had it. The
reason is one command: `SET_CARD` with the owner's proof on a card that has an
owner carries a proof of up to 72 bytes, 100 bytes of record and the mint, and
a short APDU holds 255 data bytes in all (1 + 72 + 100 + 80 = 253).

Kept apart from the record: the **owner key**, 65 bytes uncompressed, and a
flag that one has been given (`GET_INFO` byte 16). See 5.6. Kept in RAM, gone
with the tap: the **nonce** the card last gave for an owner's proof and whether
it is still live, and whether `ALLOW_LOAD` has been given in this tap.

The owner key and the time key are NIST P-256 (secp256r1). Signatures are ECDSA
with SHA-256 in DER form, and public keys are uncompressed (`04 ‖ X ‖ Y`, 65
bytes). They have nothing to do with the card's own secp256k1 key or with
Cashu. The applet sets the curve's parameters on both keys itself and relies on
no default. The time key is in the record, and not a constant in the applet, so
that the move from the interim signer to a real one (5.7) is a provisioning
change and not a new applet.

A slot, 82 bytes (upstream's 78 and a date):

| Field | Size | |
|---|---|---|
| status | 1 | empty, unspent, spent; written last, as upstream's D14 |
| keyset | 8 | raw |
| amount | 4 | |
| nonce | 32 | |
| C | 33 | |
| date | 4 | the locktime of this piece, 0 for none |

128 slots (10,496 bytes, and 130 more a slot for the text a payment hashes);
64 before software 1.7. Phase 3 adds 97 bytes a slot for the DLEQ.

The text of a piece's secret, which is what the card signs the hash of, is
the text Foxy's Cashu library writes for a piece locked to one key:
`["P2PK",{"nonce":"…","data":"<card key>","tags":[]}]`, and with a date
`…"tags":[["locktime","<date>"],["refund","<refund key>"]]}]`. The card builds
what the library builds, so loading a card is an ordinary locked payment and
nothing is made specially for it. It is not upstream's text.

### 5.2 Commands

| Command | Upstream | Fork |
|---|---|---|
| SELECT | version | a new AID of our own, so the two applets cannot be mistaken for each other; answers version 1.1 |
| GET_INFO | 8 bytes | 29: adds the format, tries left, locked, record set, the daily limit, whether the card has an owner, and the day (`now`, window start, spent today) |
| GET_PUBKEY | as is | as is |
| GET_CARD | none | the card record, with the daily limit and the time key |
| GET_SLOT_STATUS, GET_PROOF | as is | the slot with its date |
| GET_BALANCE, GET_PROOF_COUNT | as is | as is |
| **SPEND** | takes a slot and 32 bytes from the reader | takes a slot only. The card builds the piece's secret from the slot and its own key, hashes it, and, when a daily limit is set, refuses (`6A8F`) a piece that would take today past the limit, with nothing signed and nothing burned (`6A92` if it has never been told the time). Otherwise it signs (in RAM), then marks the slot spent and adds the piece's whole amount to what has been signed today, in one transaction, and only then sends the signature: a card pulled away while it signs has burned and sent nothing, and no signature leaves for a piece not burned. No message comes from outside |
| **SIGN_ARBITRARY** | signs any 32 bytes | **removed** |
| **AUTH** | none | the reader sends 16 random bytes; the card answers 16 of its own and a signature over a tagged hash of both and its key. No PIN. It cannot be a spend: the tag makes the message one no secret hashes to |
| VERIFY_PIN | as is | as is. It changes nothing about spending: it can be sent as often as its sender likes |
| SET_PIN | sets a PIN once (`6985` if one exists) | sets or replaces the PIN, and unblocks, only on a card with no owner and nothing unspent. No PIN, no proof. A card with an owner refuses it (`6A91`) |
| CHANGE_PIN | the old PIN, in a verified session | the owner's proof and the new PIN only: no old PIN, no session, any PIN state, any funds. It sets the PIN, resets the tries and unblocks (5.6) |
| SET_CARD | none | writes the card record (unit, refund key, time key, mint). On a card with no owner under the PIN, on a card with an owner under the owner's proof; only with nothing unspent. Never touches the limit |
| LOAD_PROOF | 77 bytes | 81 bytes, with the date; refused unless a PIN is set and verified or the owner has allowed loading for this tap; refused on a card with no owner and on a card never told the time. Never touches the limit |
| CLEAR_SPENT | as is | as is, or under the owner's grant for this tap |
| LOCK_CARD | the PIN if one is set | a PIN set and verified, and the owner's proof (5.6) |
| **SET_LIMIT** (`33`, `34`) | none | the daily limit (5.7): `34` by the owner's proof, on any card with an owner and any funds, with no PIN; `33` by the PIN, on a card with no owner while it is empty. 0 is no limit. Four bytes are the day's limit; eight are the day's and then the limit on ONE TAP, which the card counts against ten seconds of its own clock (`FOXY-CARD-DAILY-LIMIT.md`, 6a; `6A95` when a spend is over it). (`33` was once a limit on one PIN entry, which a terminal that had the PIN walked round; it is not that) |
| **SET_TIME** (`35`) | none | tells the card the time under the time key's signature; it only moves the card's clock forward |
| **SET_OWNER** (`43`) | none | gives the card its owner's public key (5.6) |
| **GET_NONCE** (`44`) | none | a fresh 16 bytes for one owner's proof. No PIN (5.6) |
| **ALLOW_LOAD** (`45`) | none | the owner's proof lets `LOAD_PROOF` and `CLEAR_SPENT` through, with no PIN, for the rest of this tap (5.6) |

**The instructions of this fork, by byte** (class `B0`; `spec/vectors/transcript.json`
has most of them as commands and answers):

| INS | Command | Needs | Data sent | Answers |
|---|---|---|---|---|
| `01` | GET_INFO | | | 29 bytes: version (2), slots, unspent, spent, empty, capabilities, PIN state (0 none, 1 set, 2 blocked), format, tries left, locked, record set, limit (4), has owner (1); then `now` (4), window start (4), spent today (4) |
| `10` | GET_PUBKEY | | | the card's 33-byte compressed key |
| `11` `12` `13` `14` | GET_BALANCE, GET_PROOF_COUNT, GET_PROOF, GET_SLOT_STATUS | | | as upstream, except that GET_PROOF returns the 82-byte slot |
| `15` | AUTH | | 16 random bytes | 16 of the card's own and a signature |
| `16` | GET_CARD | | | format, record set, unit, limit (4), refund key (33), time key (65), mint length, mint |
| `17` | GET_PIECES | | P1 = the first slot to report | one page: the first slot the page does not cover (1), then for each slot in the range that is not empty a tag (1) and, for an unspent slot, its 81 bytes (FOXY-CARD-DAILY-LIMIT.md 8.1). `6A83` for a P1 of 64 or more |
| `20` | ~~SPEND_PROOF~~ | | | gone with format 4: `6D00`. Up to format 3 it signed for one slot, over that piece's secret alone |
| `22` `23` `24` | SPEND_ALL_BEGIN, SPEND_ALL_OUTPUTS, SPEND_ALL_SIGN | PIN, if one is set; a time, if a limit is set | the places of a payment; then the swap's outputs, 37 bytes each; then nothing | **one** 64-byte signature over every piece and every output (NUT-11 `SIG_ALL`), with every place burned in the same transaction. The limits are held to the sum. Not opened by `ALLOW_LOAD` (FOXY-CARD-DAILY-LIMIT.md 6c) |
| `25` | SPEND_ALL_AGAIN | PIN, if one is set | | the last signature given, again, for a terminal that never heard it |
| `30` | LOAD_PROOF | an owner; a PIN set, and verified or `ALLOW_LOAD` given in this tap; a card record; a time | 81 bytes | `6982` with no verified PIN and no grant (the gate comes first); then `6A90` with no owner; `6A92` with no time; `6A94` for a piece whose nonce is already in a slot, spent or not (checked last, before anything is written) |
| `31` | CLEAR_SPENT | the PIN, if one is set, or `ALLOW_LOAD` given in this tap | | |
| `32` | SET_CARD | no owner: PIN set and verified, nothing unspent. Owner: the owner's proof, nothing unspent | unit (1), refund key (33), time key (65), mint length (1), mint; with an owner, proof length (1) and the proof first | `6A8D` if anything is unspent; `6A80` for a time key not beginning `04`, or a bad refund key; `6700` for a bad length |
| `33` | SET_LIMIT, PIN form | a card with no owner: PIN set and verified; nothing unspent; a time, unless the limit is 0 | limit (4) | `6A91` on a card with an owner (use `34`); `6A8D` if anything is unspent; `6A92` with no time |
| `34` | SET_LIMIT, owner's form | the owner's proof; a time, unless the limit is 0. No PIN, any funds | proof length (1), proof (DER), limit (4) | `6A90`, `6A91`, `6A92`; `6986` on a locked card |
| `35` | SET_TIME | nothing | time (4), signature length (1), signature (DER) | the card's `now` (4); `6A93` for a signature not the time key's; `6A8C` with no record |
| `40` | VERIFY_PIN | | the PIN, 4 to 8 bytes | changes nothing about spending |
| `41` | SET_PIN | a card with no owner, nothing unspent. No PIN, no proof | the PIN | sets or replaces the PIN and unblocks; `6A91` on a card with an owner (use `42`); `6A8D` if anything is unspent; `6986` locked |
| `42` | CHANGE_PIN | the owner's proof. No PIN, no session, any PIN state, any funds | proof length (1), proof (DER), the new PIN | sets the PIN and resets the tries; ends any verified session; `6A90`, `6A91`; `6986` locked |
| `43` | SET_OWNER | no owner: nothing unspent. Owner: the owner's proof, nothing unspent | 65 bytes, the owner's public key, uncompressed; with an owner, proof length (1) and the proof first | sets or replaces the owner; `6A8D` if anything is unspent; `6A91` with an owner and no good proof; `6A80` for a key not beginning `04`; `6700` other length; `6986` locked |
| `44` | GET_NONCE | a card with an owner. No PIN | | 16 bytes, or `6A90` |
| `45` | ALLOW_LOAD | the owner's proof, over `FoxyCard/load` and no value | proof length (1), proof | for the rest of this tap: `LOAD_PROOF` and `CLEAR_SPENT` need no verified PIN. `6A90`, `6A91`; `6986` locked |
| `50` | LOCK_CARD | PIN set and verified; the owner's proof | P2 = `DE`; proof length (1), proof | `6982` with no verified PIN; `6A90` with no owner; `6A91` with no good proof |

`21` (`SIGN_ARBITRARY`) stays unassigned. The applet's version is 1.1 and
`FORMAT` is 3; a phone that knows only an earlier format refuses the card, as it
does now for anything but its own.

**Every command in every state.** "Open" means nothing is needed beyond what
the command's own row says; "refused" means `6A91` unless another word is given.

| Command | Card with **no owner** | Card **with an owner** |
|---|---|---|
| `GET_INFO`, `GET_PUBKEY`, `GET_BALANCE`, `GET_PROOF_COUNT`, `GET_PROOF`, `GET_SLOT_STATUS`, `GET_PIECES`, `AUTH`, `GET_CARD` | open | open |
| `SET_TIME` | open: the time key's signature (any state, locked or blocked) | same |
| `GET_NONCE` | `6A90` | open (it only gives a number) |
| `VERIFY_PIN` | as ever | as ever |
| `SPEND_ALL_BEGIN`, `SPEND_ALL_SIGN`, `SPEND_ALL_AGAIN` | the PIN, if one is set (such a card holds nothing) | the PIN, up to the limits |
| `LOAD_PROOF` | refused `6A90` | PIN verified, or the `ALLOW_LOAD` grant in this tap; needs a time |
| `CLEAR_SPENT` | the PIN, if one is set | the PIN, or the grant |
| `SET_PIN` (`41`) | open while nothing is unspent (sets or replaces, unblocks) | refused, empty or not: the owner uses `CHANGE_PIN` |
| `CHANGE_PIN` (`42`) | `6A90` | the owner's proof, empty or not, any PIN state |
| `SET_CARD` (`32`) | PIN set and verified, while nothing is unspent | the owner's proof, and nothing unspent (`6A8D`) |
| `SET_LIMIT` (`33`, PIN form) | PIN set and verified, while nothing is unspent | refused |
| `SET_LIMIT` (`34`) | `6A90` | the owner's proof, empty or not |
| `SET_OWNER` (`43`) | open while nothing is unspent | the owner's proof, and nothing unspent |
| `ALLOW_LOAD` (`45`) | `6A90` | the owner's proof |
| `LOCK_CARD` (`50`) | `6A90` (after the PIN gate) | PIN set and verified, and the owner's proof |

So the one thing a terminal with the PIN and an emptied card can still do is
load pieces onto it (which the mint will refuse) and spend them. It cannot set
its own PIN, owner, record, time key or limit.

Status words the fork added or moved, in a part of `6Axx` ISO 7816-4 leaves
unassigned:

| SW | Means |
|---|---|
| `6A8D` | card in use: a thing that is set only on an empty card, on a card with an unspent piece |
| `6A8F` | over the day: this piece would take today past its limit. Nothing signed, nothing burned |
| `6A90` | no owner: a command that needs the owner's proof, or `GET_NONCE`, or a load, on a card that has none |
| `6A91` | the owner's proof is missing or is not the owner's, or this command is not open to a card with an owner. Costs no PIN tries, changes nothing, does not end the PIN session |
| `6A92` | no time: the card has never been told the time, and this needs one |
| `6A93` | not the time: a `SET_TIME` whose signature is not the time key's. Nothing changes |
| `6A94` | on the card already: a `LOAD_PROOF` of a piece whose 32-byte nonce is the nonce of any non-empty slot, spent or unspent. Nothing is written, and the amount stated is not looked at. A slot that `CLEAR_SPENT` has freed is empty, so that piece may be loaded again then |

### 5.3 Rules the applet must keep

1. **No signature without a slot burned.** The only signing paths are SPEND
   and AUTH. SPEND marks the slot spent before the signature leaves, as now.
2. **The PIN gate is the first statement** of every gated command, in every
   PIN state, including blocked. One test walks every command through every
   state.
3. **A daily limit, counted against a day the card keeps.** The card keeps
   the most it will sign for in one day, in sats, in permanent memory (0 is no
   limit, and is what a new card has), and the day itself: `now`, the latest
   time it has been told under the time key's signature, which only moves
   forward; and a window, begun when the limit is set and ended only by time.
   A `SPEND` that would take today's total past the limit is refused (`6A8F`)
   before anything is signed or burned; otherwise the total and the slot's
   status change in one transaction, and the signing comes after. Only the
   owner's proof sets, changes or removes the limit (on a card with no owner,
   the PIN does, while it is empty). `VERIFY_PIN`, a new SELECT, a reset,
   `SET_CARD` with the same time key and loading pieces do not touch it, and
   nothing a terminal that has the PIN can send changes it except by spending.
   (A limit on one PIN entry, kept in RAM, was tried first: a terminal that
   had the PIN sent it again between spends, or set the limit to nothing. It
   is gone. The limit on one payment that the card has now is not that: it is
   a second number in permanent memory that refuses nothing and asks no clock;
   a payment over it waits, by the card's own work, for every limit's worth
   past the first.) The whole text is `FOXY-CARD-DAILY-LIMIT.md`, sections 3 and 6.
4. **Three tries**, then blocked, as upstream. The owner's proof unblocks it
   (`CHANGE_PIN`), and a wrong proof costs no tries. The refund path (6) is a
   second way out, for a card set up as recoverable.
5. **Nothing can be added without a PIN set.** Upstream ships cards with no
   PIN, on which any reader in range can write and spend.
6. Every buffer allocated at install, and the status byte written last, as
   upstream's D10 and D14.
7. **The PIN spends and nothing else; the owner's proof administers.** On a
   card with an owner the PIN can `SPEND` (up to the day's limit),
   `LOAD_PROOF` (change written back) and `CLEAR_SPENT`, and changes nothing
   else: not the PIN, the limit, the record, the owner or the time key.
   `CHANGE_PIN`, the owner's `SET_LIMIT` (`34`), `SET_OWNER`, `SET_CARD`,
   `ALLOW_LOAD` and `LOCK_CARD` each need an ECDSA signature by the owner key
   over a label of the command, a nonce the card has just given and the value
   (5.6). They need no PIN, because the owner's phone does not know it
   (`LOCK_CARD` keeps its PIN as well). A wrong proof costs no PIN tries,
   changes nothing and does not end the PIN session.
8. **A card with an owner is its owner's, empty or not; a card with none is
   open while it is empty, and cannot be loaded.** A terminal that has the PIN
   can make a card empty whenever its balance fits within the day's limit, so
   on a card with an owner everything that sets or replaces the PIN, the owner,
   the card record or the limit needs the owner's proof whether or not the card
   holds anything; replacing the owner or the record needs the card to be empty
   as well. `LOAD_PROOF` is refused (`6A90`) on a card with no owner, and
   (`6A92`) on a card that has never been told the time, so the earliest `now`
   a funded card can hold is its own loading.
9. **The owner adds funds with no PIN.** `ALLOW_LOAD` lets `LOAD_PROOF` and
   `CLEAR_SPENT` through for the rest of that tap, and nothing else: it never
   opens `SPEND`. It is held in transient memory, so a new SELECT or a reset
   ends it.

### 5.4 Provisioning

- Card: NXP J3R180, dual interface, supplied with known keys.
- Build from the fork's tag; compare the CAP's hash with the one CI made
  before installing.
- After installing: put the card's GlobalPlatform keys to new random ones and
  write them down, or lock the issuer domain. A card left on factory keys can
  have the applet deleted, and the money with it, by anyone with a reader.
- The card's key is made on the card at install and never leaves it.
- The card's time key is written by `SET_CARD` at set-up. For now it is the
  public half of the interim signer's key (5.7); a real signer replaces it by
  provisioning, not by a new applet.
- **A card with no owner is open while it is empty, and cannot be loaded.** Any
  reader in range can give it a PIN, a record and an owner of the reader's
  choosing (`SET_PIN`, `SET_CARD`, `SET_OWNER`), and the holder's own set-up
  then finds it already taken. That is accepted: such a card holds nothing,
  `LOAD_PROOF` answers `6A90`, and so there is nothing on it to steal; and a
  card with an owner is its owner's, empty or not. A card still leaves the
  issuer's hands set up (PIN, record, then owner: 5.6), so that nobody has to
  think about it.

### 5.5 How it is tested

- jCardSim: every command in every PIN state and, where it matters, in each
  owner state (none, one); SPEND's message against the secret Foxy builds for
  the same slot, byte for byte, for dates of zero and not; AUTH's signature
  never verifying as a spend; torn writes (the upstream slot-order tests,
  extended to the new fields).
- The daily limit, the clock, the owner key and its proofs, and the takeover of
  an emptied card, as `FOXY-CARD-DAILY-LIMIT.md` section 12 sets out. In short:
  a terminal that sends the PIN between four spends of one piece each, with a
  limit of one piece, takes one, however it is repeated, and takes a second only
  after a signed time a day later; exactly the limit goes and one sat more does
  not, with nothing burned; every command that needs the owner's proof is
  refused with the PIN alone, with a nonce and no proof, with another key's
  signature, with another command's label, for an earlier nonce, for another
  value, and for a proof that has worked once already, and is accepted with the
  right proof and no PIN; a card with an owner refuses `SET_PIN`, `SET_OWNER`,
  `SET_CARD` and the PIN form of `SET_LIMIT` without the proof even when it is
  empty; a wrong proof costs no PIN tries. A test reads the source and checks
  that what is spent today and the slot's status change between one
  `beginTransaction` and its commit, with the signing after.
- BIP-340 vectors, as upstream. In `spec/vectors/`: a signed time and its key,
  an owner key and a proof for each label, and the transcript.
- On a card, by hand: install, set up (PIN, record, owner), set a limit, load,
  spend, change written back, a day turned, wrong PIN three times and the
  owner's unblock, a tap pulled away at each step.
- Measured on the first card, because the design leans on it: how long one
  SPEND takes over NFC from an iPhone. It decides how many pieces one tap may
  sign and whether a withdrawal of a full card needs more than one tap. Also
  whether the J3R180's ECDSA verifier takes the DER form and the uncompressed
  key, and how long it takes (once for the time in every tap, once for each
  owner command), and what the third permanent write per spend (what is spent
  today, with the slot's status byte and, on a new day, the window start) does
  to the chip's write endurance, which has not been checked against its data
  sheet.

### 5.6 The owner key, and the owner's proof

The whole text is `FOXY-CARD-DAILY-LIMIT.md`, sections 7 and 8. An earlier draft
gave the card a 32-byte owner secret, once, in the clear, and compared a hash
against it. That is gone: nothing of the owner crosses the air now, and no
secret is on the card.

Three things can hurt a holder when a terminal has been handed the PIN: changing
what the card may sign for, changing the PIN, and locking the card. None of them
can be left to the PIN. So the card has an **owner**: a public key whose private
half only the holder's phone can work out.

**The owner key.** A NIST P-256 public key, 65 bytes uncompressed, given to the
card with `SET_OWNER` and kept apart from the record. The holder's phone works
the private half out for each card, in its native side only:

    k = HMAC-SHA256( key = the 64-byte BIP-39 seed,
                     message = "FoxyCard/owner" ‖ 0x00 ‖ the card's 33-byte compressed public key )
        taken mod n, n being the order of P-256

and, if the result is 0, made again with a counter byte (`0x01`, then `0x02`, and
so on) added to the end of the message. Restoring the twelve words on a new
phone makes the same key, so the new phone is the owner of every card the old
one was. The card's key is in the message, so two cards of one holder have two
keys. The private key is never sent anywhere, the page never sees it or the
seed, and the card reads out nobody's key: `GET_INFO` byte 16 says only whether
it has one (0 no, 1 yes).

**The proof** is challenge and response, checked by the card's own ECDSA
verifier.

1. `GET_NONCE` (no data, no PIN; `6A90` on a card with no owner) answers a fresh
   random 16 bytes, held in RAM. It is gone with the tap. Each nonce gets one
   try, right or wrong: a wrong proof uses it up. Asking again replaces it.
2. The proof is an ECDSA signature (P-256, SHA-256, DER) by the owner key over
   `label ‖ nonce (16) ‖ value`, with the label and value of the command:

| Command | Label (ASCII) | Value |
|---|---|---|
| `CHANGE_PIN` | `FoxyCard/change-pin` | the new PIN's bytes |
| `SET_LIMIT` (owner form, `34`) | `FoxyCard/set-limit` | the new limit, 4 bytes, big-endian |
| `SET_OWNER` (on a card that has an owner) | `FoxyCard/set-owner` | the new owner key, 65 bytes |
| `SET_CARD` (on a card that has an owner) | `FoxyCard/set-card` | the data sent: unit, refund key, time key, mint length, mint |
| `ALLOW_LOAD` | `FoxyCard/load` | nothing |
| `LOCK_CARD` | `FoxyCard/lock` | nothing |

So a proof for one command, or one value, is no proof for another, and an old or
used proof fails. The data of an owner's command is `proof length (1) ‖ proof
(DER) ‖ the value` (for `ALLOW_LOAD` and `LOCK_CARD`, the proof alone), so the
proof sits first and the value after it is exactly what the proof was made over.

**Refusal.** `6A91` when no nonce was asked for in this tap, or the signature is
not the owner's. A wrong proof costs no PIN tries, changes nothing and does not
end the PIN session. `6A90` when the card has no owner. `CHANGE_PIN`, the owner's
`SET_LIMIT`, `SET_OWNER`, `SET_CARD` and `ALLOW_LOAD` need no PIN and no old PIN:
the owner's phone does not know it, and the cards that most need an owner's
`CHANGE_PIN` are the blocked and the forgotten, where there is no old PIN to
give. `LOCK_CARD` needs a PIN that is set and verified as well; a card with no
PIN, or none verified in this tap, answers `6982`. The phone locks nothing:
`LOCK_CARD` is for a tool, and the phone's gate refuses to carry instruction
`50`.

**Who the owner is.** Whoever's words derived the key on the card: for a holder
with a phone, their own; for a holder without one, the friend whose phone set the
card up. That phone is a trustee who, with the card in hand, can do anything, and
without it, nothing. Set-up says so in as many words (`FOXY-CARD-SCREENS.md`
B3). An owner can be replaced only on a card with nothing unspent, and only with
the old owner's proof. A card whose owner has lost their words cannot be taken
over, empty or not.

**Setting a card up is one tap**, in this order: `SET_PIN`, `VERIFY_PIN`,
`SET_CARD` (the record, with the time key), `SET_OWNER` last. The owner goes in
last so that no step needs a proof, and the card is open, and can be done again,
at every point before it: a set-up cut off anywhere is finished by the next. It
asks for no limit. The PIN is typed on the owner's phone once, here, and never
again for adding funds.

If the card in hand turns out to have no usable ECDSA verifier (not yet
measured; the J3R180 is expected to have one), the earlier draft's hash of an
owner secret stands in for the owner's proof. The time signature has no such
fallback and must verify, so the first card settles the question either way.

### 5.7 The daily limit, the clock, and what they bound

The whole text is `FOXY-CARD-DAILY-LIMIT.md`, sections 5 and 6. A card is cash.
It holds what is put on it. The daily limit caps how much of it a terminal that
has been handed the PIN can take in a day. Until a real time signer exists, it
caps an honest terminal, and not a cheating one (below).

**The limit.** A number of sats in the card's permanent memory: the most the card
will sign for in one day. `GET_INFO` bytes 12 to 15 and `GET_CARD` bytes 3 to 6
carry it. 0 is no limit, and a new card has 0: set-up does not ask for one and
nothing suggests a figure. The owner sets one later from CHANGE LIMIT, to any
amount, and removes it the same way. `VERIFY_PIN`, a new SELECT, a reset,
`SET_CARD` and loading pieces do not touch it.

**The day.** The card keeps it, from a time it is told. `SET_TIME` carries a
time (four bytes, seconds) and an ECDSA signature by the time key over
`"FoxyCard/time" ‖ time`. A good signature on a later time moves the card's
`now` forward; an older or repeated time changes nothing (`9000`); a signature
that is not the time key's is `6A93`. No PIN, no owner and no state of the card
refuses it. Setting the limit begins a window at `now` with nothing spent in it.
On `SPEND`, when the limit is not 0: a card that has never been told the time
refuses (`6A92`); if `now` is a day (86,400 seconds) or more past the window's
start, the window is new and begins at `now`; a piece that would take what is
spent today past the limit is refused (`6A8F`) with nothing signed, burned or
written; otherwise the window start (if new), what is spent today and the slot's
status change in one transaction, and the signing comes after. Nothing else
begins a window: not `VERIFY_PIN`, not a new SELECT, not a reset, not a load. A
limit of 0 skips all of it and needs no time. `now` only ever moves forward,
with one exception, which is the way out of a signer's fault: `SET_CARD` that
writes a time key different from the one the card holds, on a card with nothing
unspent (and with the owner's proof, if the card has an owner), sets `now` and
the window back to 0. A far-future time signed by mistake would otherwise freeze
a card's day until real time caught up.

**It counts the pieces signed, not the price paid.** The card cannot check
change that a terminal writes back, so change loaded onto the card gives the day
nothing back. A payment of 300 sats made with one piece of 1,000 costs the day
1,000, and a till picks pieces as near the price as it can (8.1). A window is a
fixed day from its start, so a terminal that straddles one boundary can take up
to two days' limit in a short span. That is the bound, and it is written down
and not hidden.

**The time, for now.** There is no time server yet. The time the card is told is
the **receiving phone's own clock**, signed by Foxy's app with a P-256 key whose
private half is built into the app (a constant named for what it is, in the
app's source), and whose public half set-up writes to the card as its time key.
The card's code is the final design; only where the signature comes from is
interim. This is **as weak as trusting the receiver's clock**, because that is
what it is:

- the private half is in every copy of the app, so anyone can extract it and sign
  any time they like. A terminal built to cheat can tell the card it is a day
  later, again and again, in one tap, and take everything the PIN reaches. With
  the PIN and the card, the limit stops it no better than having no limit;
- a wrongly set clock on an honest phone signs a wrong time too: one set far ahead
  freezes the card's day, until a different time key is written to the card while
  it is empty;
- what it does bound: an honest receiver cannot be talked into taking more than a
  day's limit by accident, and the holder's own overspending is held to the day.
  It is a bound against mistakes and honest terminals, and not against an
  attacker. No screen says otherwise.

A **real signer replaces it by provisioning**, not by a new applet: a service that
signs only its own disciplined clock, never a time it is asked for; whose private
key is made away from the service and held well; whose public half is written to
cards as their time key. Until then, what section 3 says of a terminal being
unable to manufacture tomorrow is not true.

**What it bounds.**
- An honest terminal that has been handed the PIN can take at most one day's limit
  in a day, and up to two across a window's boundary. That is not the price of
  this payment and not what the holder agreed to: the card cannot tell a holder's
  request from a hostile terminal's.
- The PIN alone protects a lost card whose PIN nobody knows: a wrong PIN opens
  nothing, and three block the card.
- Changing the PIN, the limit, the owner or the record, adding funds with no PIN,
  and unblocking the card need the owner's phone, or the twelve words on a new
  phone.

**What it costs.**
- A piece worth more than what is left of today cannot be spent today, whatever
  the payment: with one piece of 1,000 sats and 500 left, not even 10 sats can be
  paid until the day turns or the owner's phone changes the limit.
- A payment can be refused for the limit while the card holds enough: the till
  says OVER THE CARD'S DAILY LIMIT, not NOT ENOUGH ON THE CARD.
- Every spend writes one more thing to permanent memory.
- The owner's phone taking money off the card lifts the limit and puts it back in
  the same tap. Putting it back starts a new window with nothing spent, so a
  withdrawal gives the day back its whole limit: one extra day's limit, at the
  moment the owner is holding the card.
- If the owner loses the twelve words, the PIN and the limit on the card
  can never be changed, and a blocked card can never be unblocked.

What it does not do, in particular that it does not stop a terminal built to
cheat today, is listed in section 10.

## 6. A lost or blocked card is not lost money

Upstream's position is that a card is cash: lose it and it is gone, and a
blocked PIN strands it as well. Their own notes call a refund path "the right
shape" and name its flaw. This is that path, with the flaw answered.

- Every piece loaded onto a card is locked to the card's key **and** carries a
  date and a refund key: after the date, the refund key can spend it.
- The refund key is one the funding phone derives from its words, so it is
  recoverable wherever those words are.
- The date is a year after loading. Adding funds renews every piece on the
  card to a year from then (they are swapped anyway to make change).
- **The flaw upstream names:** once the date passes, a mint may stop taking
  the card's own signature, and the card does not know (the clock it keeps for
  its limit is not used for dates). So the date is on the slot where a reader
  can see it. Foxy, receiving, refuses a piece within a week of its date,
  before asking the card to sign, and says the card needs renewing by its
  owner. The holder's own phone shows the date and offers to renew from a month
  before.
- Privacy: the refund key is in each piece's secret, which the mint sees when
  the piece is spent. The card's own key is there too and already ties a
  card's pieces together, so this adds a tie between a card and the phone that
  funded it. A different refund key for each card keeps two cards from being
  tied to each other.

- **Mints differ on what a passed date does.** NUT-11 as it now reads lets
  the first key go on spending beside the refund key, and the Cashu library
  Foxy ships reads it that way (`tests/flashcard-vectors.js` in Foxy shows
  it); an older mint takes the refund key only. The rule above leans on
  neither, so it is right at both.

- **What can be taken back is what the loading phone knows of.** A piece's
  nonce is random, so the twelve words do not rebuild it: the phone keeps the
  pieces it loads and the pieces it sees whenever the card is read on it.
  Change a receiver wrote onto the card since the phone last read it is not
  known to the phone and does not come back. And if the phone's own data is
  lost with the card, the words alone bring back nothing of the card. A later
  version can make the nonces come from the words; this one does not.

- **The daily limit does not touch this path.** The refund key spends at the
  mint and the card is not asked. It comes from the same twelve words as the
  owner key, so a holder who loses the words loses both: the refund, and any
  change to the card's PIN, limit or blocked state.

A card can be made with no refund key, and is then cash, as upstream's.

## 7. Foxy: reading the card (Swift)

- CoreNFC, `NFCTagReaderSession` for ISO 7816. The fork's AID in
  `com.apple.developer.nfc.readersession.iso7816.select-identifiers`, the
  `NFCReaderUsageDescription`, and the NFC Tag Reading capability. **That
  capability is not offered to a free signing team**: this needs the paid
  developer membership.
- `Foxy/Card/CardLink.swift`: one session, a list of steps, each a command
  and what to do with its answer. `CardAPDU.swift`: building and reading the
  commands, with no NFC in it, so it is unit tested.
- The bridge gives the page three calls: read (no PIN), spend (PIN, slots),
  write (pieces, card record; the PIN, unless the owner's phone is adding
  funds). The PIN goes to the card and nowhere else: not kept, not logged,
  wiped when the session ends.
- The native side holds the owner key (5.6). The page never sees the twelve
  words, the seed or the owner's private key; native offers it exactly three
  things:
  - `cardOwnerKey`: the owner **public** key for the card with a given
    compressed public key (130 hex characters, uncompressed), for `SET_OWNER`;
  - `cardOwnerSign`: an ECDSA signature (DER, hex) by the owner key for that
    card, over `"FoxyCard/" + label ‖ nonce ‖ value`, where the label is one of
    `change-pin`, `set-limit`, `set-owner`, `set-card`, `load`, the nonce is the
    16 bytes the card gave and the value has the shape that label takes. Native
    refuses every other label (in particular `lock` and `time`) and every other
    shape, before it reads the seed, and answers the signature and nothing
    else. A proof is made in the tap that uses it, since its nonce lives only as
    long as the tap;
  - `cardTime`: `{time, signature}`, the phone's clock and the interim signature
    of 5.7. This is the only place the interim signer is used.
- The phone's gate carries only the instructions the page uses: `01 10 11 13 14
  15 16 20 30 31 32 34 35 40 41 42 43 44 45`. `33` (the PIN form of the limit,
  for a card with no owner) is not used by the phone and is not carried; `50`
  (`LOCK_CARD`) stays out.
- Every tap tells the card the time once, before anything else is read from it
  that depends on the day: `SET_TIME` with `cardTime`'s answer, and the card's
  `now` read back. A card that answers `6A93` is at another signer: NOT A FOXY
  CARD, nothing taken. A card that answers `6A8C` is not set up yet, and is read
  as it was.
- iOS ends a session after a minute and shows its own sheet throughout; the
  sheet's line of text is set at each step (`Hold the card`, `Keep it there`,
  `Remove the card`).
- A simulator has no NFC. A stand-in card over a local port, in simulator
  builds only, as the tap link has, so the flows can be driven without
  hardware. The Java applet under jCardSim can be that stand-in, which tests
  the real applet against the real page.

## 8. Foxy: the money (wallet)

### 8.1 Being paid by a card, same mint

1. Read: `SET_TIME`, AUTH, card record, slots. Refuse here, with nothing
   signed, and before the PIN is sent, if the mint differs, the balance is
   short, a piece is near its date, a keyset is not this mint's, or what is left
   of today's limit is less than the worth of the pieces step 2 would sign. What
   is left is worked out from `GET_INFO` (the limit, `now`, the window start and
   what is spent today; a window whose day is over has the whole limit left).
2. Choose pieces: exact if the card has them, else the least over, and as close
   to the price as it can either way, because the day's limit is charged each
   piece's whole worth and not the price (5.7). Add the mint's fee for swapping
   them where it charges one; the payer's card pays it.
3. Write down, before asking the card for anything, the outputs of the swap
   to come: this phone's own for the amount, and the change locked to the
   card (new nonces, the card's key, date and refund key). A lost answer is
   then recovered from that record, as every swap here is.
4. VERIFY_PIN, then SPEND each piece. From here the card's slots are spent.
5. Swap at the mint, the card's signatures as each piece's witness.
6. Change: while the card is still there, LOAD_PROOF each piece and read it
   back. If it has gone, keep the change on file and show TAP THE CARD AGAIN.
7. PAID when step 5 has given this phone its pieces. One history entry, in,
   for the amount, marked as from a card.

As built: steps 3 and 5 are the wallet's ordinary receive of a token whose
pieces are already signed for, with its own record and recovery, and change
is a second swap, an ordinary locked send to the card's key, cut by what it
costs and settled on the payment's entry as any change here is. One swap for
both would save a sat or two at a mint that charges; it is not built.

### 8.2 What is this phone's and what is not

- The amount is this phone's when the swap answers.
- Change is never this phone's. It is locked to the card and kept in a store
  of its own (`foxy.cashu.cardchange`), outside the balance and outside the
  books, until it is written to the card. If it never is, it comes back to the
  card's funder at its date.
- Signed and not yet swapped (the mint did not answer): held, asked about
  until the mint says, exactly as a held swap is now. If the mint says the
  pieces are unspent and will not take them, they are swapped back into
  pieces locked to the card and wait for a tap like change.

### 8.3 The holder's own phone

- **Add funds:** swap from this phone's pile into pieces locked to the card,
  powers of two, as many as there are free slots; record them before the tap;
  write; read back. An out entry, `to card`, for the amount and the fee.
  Pieces made and not yet written are shown as on their way to the card and
  can be written on the next tap. On the owner's phone no PIN is asked:
  `SET_TIME`, `GET_NONCE`, a signature over `load`, `ALLOW_LOAD`, then
  `CLEAR_SPENT` and `LOAD_PROOF`. Putting money on a card does not touch its
  limit.
- **Withdraw:** SPEND the pieces, swap into this phone's own. An in entry,
  `from card`. Renewing and taking everything off a card are the same. None of
  them stops at the limit: in the same tap the owner's phone lifts the limit
  with the owner's proof (`34`, limit 0), the PIN is typed and `SPEND` runs, and
  the old limit is put back with the owner's proof, restored even when the
  spending fails part way. Where the card leaves before it can be, the phone has
  written the old limit down first and puts it back at the next tap. Putting the
  limit back starts a new window with nothing spent, so a withdrawal gives the
  day back its whole limit (5.7). A phone that does not hold the card's words
  cannot lift the limit, and says so.
- **Change PIN, unblock, change limit:** `GET_NONCE`, a signature over
  `change-pin` and the new PIN, `42`; and `SET_TIME`, `GET_NONCE`, a signature
  over `set-limit`, `34` (any amount, or none). No PIN is asked for either: the
  owner's phone does not know it.
- **Balance:** from the slots; with a connection, each piece's state asked of
  the mint. The card's daily limit, what is left of it today and when the day
  turns are shown with it.
- **Take back a lost card:** after the date, swap the pieces this phone
  recorded when it loaded them, signed with the refund key.

### 8.4 A card at another mint (second version)

The payment is made final for the payer by a swap at the card's mint, into
ecash this phone holds there; bringing it to this phone's own mint is then
this phone's business, by the road Foxy already has for moving between mints,
and its cost is shown to the receiver before the card is asked for anything.
Until then: A DIFFERENT MINT, and nothing is taken.

What is built of this is the holder's side: moving a card itself to another
mint. A card with nothing on it is told its new mint in one tap (`SET_CARD` under
the owner's proof, writing the card's current time key back so that its clock
stays; the card refuses it while it holds unspent pieces). One with money on it
takes two: the money comes off into the phone, crosses by Lightning with its
own fee paid out of it, and goes back on at the new mint. The fee shown before
the card is touched is the most it can cost; cut short anywhere, the money is
in the phone and the card is empty and still good. Run against CDK and
Nutshell with the applet as the card (`tools/live/flashcard-switch.js` in the
wallet repository, getfoxy/iOS, https://github.com/getfoxy/iOS). The app offers
the one-tap move to the owner's phone for a card with nothing on it (SWITCH TO
the phone's mint, on ADD FUNDS); it has no button for the move of a card that
holds money, and a card that holds money is told to withdraw it first.

## 9. Foxy: the screens (app)

`FOXY-CARD-SCREENS.md` lists them as built. In short:

- Receive: a CARD button beside TAP; the PIN pad with what is being paid
  under its title and on its button; the cards of 2.1.
- Menu › Flashcard: straight to the tap, then one screen in home's layout:
  the card, its balance and mint in home's own pill, and four buttons.
- Amounts are typed on the app's own amount screen. The PIN pad is the lock
  screen's, with its larger keys. A card PIN is 4 to 8 digits.
- The limit is set from CHANGE LIMIT in three steps: a warning, the amount
  screen, a confirmation (2.2; B7 of `FOXY-CARD-SCREENS.md`). Set-up asks for a
  PIN twice and shows a notice that this phone will be the owner, and asks for
  no limit.
- The card's screen shows the daily limit, what is left today and when the day
  turns.
- A card's face is drawn in its design, named by a code of three characters
  (`CARD-DESIGNS.md`; FL1 today). The card does not yet carry its code.
- Render snapshots for each state.

## 10. What it does not protect against

A card is cash. It holds what is put on it. The daily limit bounds an honest
terminal that has been handed the PIN to one day's limit per visit. It does not
bound a terminal built to cheat, today (5.7).

- **A terminal built to cheat, today.** With the interim time signer (5.7) it can
  sign its own time, day after day in one tap, and take everything the PIN
  reaches. The limit bounds honest terminals and the holder's own overspending,
  and that is all it is said to do.
- A receiver's phone that has been altered can show one amount and ask the card
  for more, up to one day's limit per visit, and up to two across a window's
  boundary, **once the time is real**. The limit does not check a payment, and
  nothing on the card can. Looking at the card's balance afterwards finds out; it
  does not undo it. It is the position of any card and any terminal.
- The same terminal can write onto the card pieces the mint will refuse, made up
  with a nonce no mint ever signed. They take nothing from anyone, since the
  receiver always asks the mint, but a later payment that picks one burns slots
  and spends a day's limit on nothing, and while such a piece is on the card it is
  not empty, so the owner cannot replace its owner or record until it is spent. A
  fake piece looks unspent to a state check, so this waits on DLEQ on the card
  (phase 3).
- It cannot write a **copy** of a real piece. The card signs a piece's secret,
  which its nonce makes, and takes the amount on the terminal's word, so a copy
  stating an amount of 1 would have been spent for 1 and signed for the real
  piece. `LOAD_PROOF` refuses a nonce that is in any slot, spent or not (`6A94`).
  The card does not remember a nonce once `CLEAR_SPENT` has freed its slot (see
  `FOXY-CARD-DAILY-LIMIT.md`, section 13).
- Any reader in range can send three wrong PINs and block the card (upstream's
  threat 14). The owner's phone unblocks it with a new PIN (5.6); a card whose
  owner has lost their words stays blocked.
- A holder whose owner has lost their words can never change the PIN or the
  limit.
- Whoever has the owner's words and the card in hand has everything on it.
- Whoever holds the time key's private half (today, anybody) can move clocks
  forward. A wrongly signed far-future time freezes a card's day until its owner
  writes it a new time key while it is empty (5.7).
- A card with no owner can be claimed by any reader in range. It holds nothing and
  cannot be loaded (5.4).
- A holder with no phone has no balance they can read that a terminal did not
  draw. That is a screen, not a clock.
- A pretend card can collect a PIN typed for it. AUTH lets a holder's own phone
  know its card; a receiver has no way to know a stranger's card is real, and
  loses nothing if it is not.
- The PIN is in the clear over a few centimetres of air until phase 2. Nothing of
  the owner crosses the air.
- Whoever holds the refund key's words can take a card's money after its date.

## 11. Order of work, and how much

| Phase | What | Rough effort |
|---|---|---|
| 0 | Paid developer membership; cards and a reader in hand; fork made | yours |
| 1a | Applet: card record, slot date, SPEND from the slot, SIGN_ARBITRARY out, AUTH, the daily limit and the clock, the owner key and its proofs (`CHANGE_PIN`, `SET_LIMIT`, `SET_OWNER`, `SET_CARD`, `ALLOW_LOAD`, `LOCK_CARD`), no load without a PIN or an owner; jCardSim tests | 3 to 4 days |
| 1b | Swift: CoreNFC session, commands, bridge, the owner key and its signatures and the signed time in the native side, stand-in for the simulator, unit tests | 2 to 3 days |
| 1c | Wallet: pay by card at the same mint, change, held and lost answers, add funds, withdraw, balance, the refund path, the till's check of what is left today, the owner's phone lifting and restoring the limit when it takes money off; suites against the stand-in | 4 to 5 days |
| 1d | Screens (the limit's three steps), wording, snapshots; documents; phone checks | 2 days |
| 1e | On real cards: install, timing, every flow, every pulled tap | 2 to 4 days, set by how many rounds the hardware needs |
| 2 | A card at another mint; the PIN under a key; unblock by a second code | 1 to 2 weeks |
| 3 | A receiver with no connection: DLEQ on the card, an issuer's certificate, a list of withdrawn cards | a design of its own; weeks |

Phase 1 is about three weeks of the way this project is worked now. The
applet and the wallet can be written and tested without a card; phases 1b and
1e cannot be finished without the membership and the hardware.

## 12. For you to decide

1. **Recovery.** A refund key and a year's date on every piece (recommended),
   or cash-like cards with nothing behind them, or the choice per card.
2. **The limit at set-up.** Decided: a new card has no limit, set-up asks for
   none, and nothing suggests a figure. A limit is set later from CHANGE LIMIT,
   in the three steps of 2.2.
3. **Same mint first.** Agree that the first version refuses a card at
   another mint.
4. **Upstream.** Tell them what was found before the fork is public, and
   whether to offer the changes back. The fork changes the slot and the
   commands listed in 5.2, so an upstream card and a fork card do not work in
   each other's terminals.
5. **The name.** "Flashcard" is close to their product's. The menu can say
   CARD.
6. **The time signer.** Until a real one exists, the time the card is told is the
   receiving phone's own clock, signed with a key inside the app (5.7). Where the
   real signer runs and who holds its key, when the interim key is retired, and
   whether the ECDSA verifier on the first card behaves (5.6) are open; see
   `FOXY-CARD-DAILY-LIMIT.md` section 14 for the rest of what is decided.
