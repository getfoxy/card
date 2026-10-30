# Foxy card: a specification for forking cashu-javacard

A card that holds ecash and pays a Foxy by a tap, and the changes to make in a
fork of `lnflash/cashu-javacard` and in Foxy to get there.

The card's daily limit, its owner and its clock are specified in full in
`FOXY-CARD-DAILY-LIMIT.md`. This document keeps the part of them that the rest
of the specification leans on, and refers to that one for the whole. An earlier
draft had an allowance that only went down and an owner secret given to the
card; both are gone. Every section of the card's own (5) says what software 1.22
does (`SELECT` answers `01 16`); where a paragraph tells what an earlier version
did, it says so.

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
   alone changes nothing but what it spends. A terminal that has been handed
   the PIN, however it is built, can take no more than the card's daily limit
   in a day: the card counts its day by Bitcoin block headers, and a terminal
   cannot make one for nothing. Only the owner's phone can set, change or remove
   the limit (5.7). A blocked card is not lost money: its owner's phone unblocks
   it. A lost card is not lost money where the card was set up to be
   recoverable (6).
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
2. **SET UP THIS CARD**, the first time: a PIN typed twice, then the no-PIN
   allowance (5.7a: what the card pays without the PIN until the PIN is given
   again, two dollars unless the holder chooses another figure), a notice that
   this phone will be the card's owner, and a tap, held while the screen says
   KEEP HOLDING and counts up, as the card takes its owner (5.10). It asks for no
   daily limit and suggests no figure: a new card has none. The one tap gives the
   card its PIN, its record, its owner key (5.6) and its allowance. Nothing goes
   onto a card with no PIN (software 1.21; from 1.16 to 1.20 a card could be set
   up with NO PIN, and then paid without one, and was given one later from ADD
   PIN). A card for a holder with no phone is set up by the first phone that
   sends it money, under the holder's PIN, and has no owner (software 1.22,
   5.11). Cards are set up as cash for now (6).
3. **ADD FUNDS.** An amount and a tap. The card's PIN is given in the tap, on the
   owner's phone as on any other: since software 1.21 the card takes no piece
   without it, and the owner's proof lets none in (5.6; up to 1.20 it did). The
   money leaves this phone's balance and is on the card when the screen says so.
   If the card leaves early the pieces wait for it, and the screen says so until a
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
   more of the limit than that it is the most the card will spend in one day.
7. **The card's history**: what this phone has done with it. The card keeps a
   short account of its own as well, its last eight taps and sixteen receipts
   (`FOXY-CARD-DAILY-LIMIT.md` 6b), read under the owner's grant (or, on a card
   with no owner, under the PIN in a held tap: 5.11).
8. **TAKE BACK A LOST CARD.** For a card this phone funded as recoverable:
   after its date, the money comes back with no card (see 6). Built, and on
   no screen while cards are cash.

## 3. Who is trusted with what

| Party | Holds | Can do wrong |
|---|---|---|
| Card | its key, the pieces, the PIN, its owner's public key, its clock (the time and hash of the newest block header it has taken, the difficulty of the hardest), the day | nothing by itself; it signs what it is asked, under its rules, up to the day's limit |
| Cardholder | the card and the PIN | pay twice from a copied card, if a receiver ever accepts without the mint |
| Owner's phone | the twelve words, and from them the card's owner key | with the card in hand: change the PIN (and then load and pay under the new one), set or remove the limit, and so take everything. Without the card: nothing. It is a trustee, held on a leash of two things: its words and the card itself. A holder with no phone of their own may have no owner at all (5.11) |
| Receiving phone | sees the PIN, talks to the card | ask the card to sign more than the amount shown, up to what is left of today's limit; bind the card's change to a key that is not the mint's, so that the change waits until the holder mends it or gives it up (5.10); write pieces the mint will refuse; on a card with no owner, in a tap of its own held for a guest's seconds, read its history and rewrite it (5.11) |
| Mint | decides what is spent | what any Cashu mint can |
| Whoever shows the card a block header | a header | move the card's clock forward to the time the header carries, if it shows the work (5.7), and so end a day and begin the next, no sooner than that time. Never move a clock backward. A header dated later than the network's own time has to be forged, which costs work (`FOXY-CARD-DAILY-LIMIT.md` 5.2) |

Three things shape the design. The receiver always goes to the mint before
saying paid, so a copied or pretend card gains nothing. A terminal sees the
PIN and chooses what to ask the card for, so nothing the PIN alone can reach
may bound it or change it: the PIN spends and nothing else, and what bounds the
spending is a number in the card's permanent memory that only the owner's proof
sets (5.3, 5.7). And the one thing a terminal cannot manufacture is tomorrow:
it can fetch a true "now" from the network, but not a later one, because a
header dated later has to have its work done, and a terminal does not have a
block's worth of it. So a window that only a newer block can end is a bound it
cannot walk round (5.7). Up to software 1.14 the time was signed by a key built
into the app, which anyone could copy, and that last sentence was not true.

## 4. What the review found, and what the fork does

| # | Found | In upstream 0.4 | The fork |
|---|---|---|---|
| 1 | A blocked PIN took the PIN gate away | fixed (a blocked PIN counts as set) | keep; add a test that every gated command refuses in every PIN state |
| 2 | `SPEND_PROOF` signs 32 bytes the reader supplies, not the stored piece's own message | still so | the card works the message out itself from the slot; the reader supplies nothing (5.2) |
| 3 | `SIGN_ARBITRARY` signs anything and burns no slot, so SPENT enforces nothing | still there, behind the PIN | removed. Proving the card is the card gets its own command, whose signature cannot be a spend (5.2) |
| 4 | No DLEQ on the card, so a piece cannot be checked without the mint | still so | not needed while the receiver is online. From software 1.20 the card keeps the mint's proof for each piece, for a phone to read and check; the card checks nothing (5.9) |
| 5 | Factory GlobalPlatform keys left on the card | provisioning, not the applet | the provisioning steps set new keys, and say so (5.4) |
| 6 | The terminal showed paid when the swap had failed, and booked another's spend as settled | their terminal, not used here | Foxy's own rule: paid only when the mint has given this phone its pieces (8) |
| 7 | Over-payment is the merchant's income; change is not returned in the payment | by design upstream | change goes back to the card in the same tap, or waits for it (8.2) |
| 8 | A terminal with the PIN can spend every slot | by design upstream | a daily limit in the card's permanent memory, counted against a day the card keeps from the Bitcoin block headers it is shown: every payment adds what leaves the card (its pieces less the card's own change) to today's total, a payment that would take the day past the limit is refused, and only the owner's proof sets or removes the limit (5.3, 5.7). It bounds a terminal to one day's limit per visit, honest or not: a terminal cannot start another day without a header dated later, which has to be a real block or a forgery that costs work (5.7). Two earlier tries failed: a limit on one PIN entry was walked round by a terminal that had the PIN, and an allowance that only went down and only the owner's proof raised held against a terminal but stopped a card whose owner's phone was not at hand |
| 9 | A blocked or lost card strands its balance | acknowledged; no unblock | a blocked card is unblocked by its owner's phone (`CHANGE_PIN`, 5.6); for a lost card, a refund path the funding phone holds (6) |
| 10 | The card does not say which mint its pieces are at | open question upstream | the card records its mint (5.1) |
| 11 | The PIN crosses the air in the clear | so | since software 1.9 the PIN may be sent sealed, enciphered to a key of the card's under a nonce good once (`FOXY-CARD-DAILY-LIMIT.md` 6e); the clear form stays for a terminal that knows no other. Nothing of the owner crosses the air: the card holds only the owner's public key, and a proof is a signature over a nonce the card has just given (5.6) |
| 12 | `CHANGE_PIN` needs a verified session and the old PIN, both of which a terminal handed the PIN has: it can set a PIN the holder does not know, and the holder's wrong tries then block the card for good | so | the owner's proof replaces both: `CHANGE_PIN` takes the proof and the new PIN only, no old PIN and no session, so the owner's phone can change the PIN, or unblock a blocked card, without knowing the PIN (5.6) |
| 13 | `LOCK_CARD` needs the PIN only if one is set: any reader can lock a card with no PIN for good, and a terminal that has the PIN can lock a funded card | so | a PIN set and verified, and the owner's proof (5.6); a card with no owner cannot be locked |
| 14 | A card with no PIN can be claimed by any reader: it can set the PIN, and in the fork the owner | so | a card with no PIN takes nothing and pays nothing (software 1.21), so a card claimed by setting its PIN holds nothing; the owner is claimed only under a PIN given in the tap, by six askings in a row that take the holder seconds of holding the card, never in a tap that signed a payment, and only of an empty card (5.10). A card with a PIN and no owner (software 1.22, a holder with no phone) keeps its PIN: a new one wants the old, given in the tap (5.11). A card with an owner is its owner's, empty or not: a terminal that holds the PIN and empties the card cannot then set its own PIN, owner, record or limit (5.3). A card still leaves the issuer's hands set up (5.4) |

## 5. The card

### 5.1 What it stores

A card record. `SET_CARD` writes the unit, the refund key, the mint, the design
and the flag that it has run, and never the limit, the day, `now` or the last
header's hash; it clears the hardest bits (the ratchet), the one way out for a
card whose network has fallen under a quarter of its best, as `RESET_CARD` does
(5.6a; `FOXY-CARD-DAILY-LIMIT.md` 5.2). The daily limit is written by the owner's
`SET_LIMIT` (`34`); `now` and the block fields by `SET_HEADER`; the window and what
has been spent by the owner's `SET_LIMIT`, by a payment, and by `SET_HEADER` for the
window of a limit set before the card had taken a header. Here "the clock" is the time and the newest header's
hash, and "the ratchet" the hardest bits.

| Field | Size | |
|---|---|---|
| format | 1 | 4 since software 1.4 (one signature for a payment); 3 for the first form of this design. Not a byte of the record: `GET_CARD`'s first byte says it |
| set | 1 | 1 once `SET_CARD` has run |
| unit | 1 | `00` sats, `01` dollars; no other since 1.23 (`6A80`). Which unit is the record's: the other's keysets are in the table (1.23, below) |
| limit | 4 | the most the card signs for in one day, big-endian, in cents from 1.23 (in the record's unit before); **0 is no limit, and is what a new card has** |
| refund key | 33 | the key that can take the pieces back after their date, or zeros for none |
| hardest bits | 4 | the difficulty of the hardest block header the card has taken, as the header carries it (`bits`, little-endian); zeros until it has taken one. Record offset 120. Written by `SET_HEADER`, only upward, and cleared by `SET_CARD` and `RESET_CARD` |
| last header | 32 | the hash of the newest header taken, as Bitcoin shows a block hash. Offset 124. Written by `SET_HEADER` |
| rate | 4 | 1.23: sats to the dollar, big-endian, 0 for none. Offset 156. Written by `SET_LIMIT` with the limits; `SET_CARD` leaves it; `RESET_CARD` zeros it; `GET_CARD` answers it as zeros |
| (unused) | 25 | zeros. With the three rows above, the 65 bytes where the time key was up to software 1.14; `GET_CARD` still answers them, at offset 40 |
| now | 4 | the time of the newest block header the card has taken, seconds, big-endian; 0 until it has taken one. Written only by `SET_HEADER`, and by nothing that lowers it |
| window start | 4 | when the current day began; written by the owner's `SET_LIMIT` when the day's number changes, by a payment that begins a new day, and by the first header taken after a daily limit was set while `now` was 0; zeroed by `RESET_CARD` |
| spent today | 4 | what has left the card for good (the pieces less its own change) since the window began |
| limit on one payment | 4 | the most the card signs for in one payment straight away; 0 is no limit (5.7, `FOXY-CARD-DAILY-LIMIT.md` 6a); cents from 1.23 |
| no-PIN allowance, and what it has signed with no PIN | 4, 4 | what the card signs without the PIN between two givings of the PIN, and what it has so signed since the PIN was last given (5.7a); cents from 1.23 |
| mint | 1 + up to 77 | the mint's address, as text |
| design | 3 | the card's face: a code of three characters, capital letters and digits, naming a design in docs/CARD-DESIGNS.md; zeros for none. Written by `SET_CARD` when given, cleared by one that is not (1.10) |

The mint is at most 77 characters, not 96 as the earlier draft had it (80
before the design). The reason is one command: `SET_CARD` with the owner's
proof on a card that has an owner carries a proof of up to 72 bytes, 100 bytes
of record, the mint and the design, and a short APDU holds 255 data bytes in
all (1 + 72 + 100 + 77 + 3 = 253).

`GET_CARD` answers the record as the table has it, the mint and then the three
bytes of design; a phone that knows no design reads the mint by its length and
ignores what follows. `SET_CARD` takes the record with the design after the
mint, or without it, as a phone that knows none sends it: then the card has
none.

Kept apart from the record: the **owner key**, 65 bytes uncompressed, and a
flag that one has been given (`GET_INFO` byte 16). See 5.6. And, from software
1.12, the **openings of the card's own change**: eight places of 113 bytes (the
amount, the keyset, the date, the nonce and the blinding factor of a change
output the card made for itself at `SPEND_ALL_CHANGE`, and from 1.21 its blinded
message; 80 bytes before), each with a state (empty; drafted for a payment not
yet signed for, let go at the next `SPEND_ALL_BEGIN`; owed, signed for and
waiting for its piece), and from 1.21 the mint's key each was bound to at the
payment (33 bytes each, in a store the first `SET_CARD` reserves); listed by
`GET_CHANGE` without the nonce or the blinding factor, made into its piece by
`LOAD_CHANGE` (5.10), and let go when a piece with that nonce is written on or
(1.22) when it is given up. Kept in RAM, gone with the tap: whether the PIN has
been given, the **nonce** the card last gave for an owner's proof and whether it
is still live, whether `ALLOW_LOAD` has been given in this tap, and how many
times in a row a held tap has been asked for (an owner's claim, 1.21; the history
of a card with no owner, 1.22); and, gone with the power and not with a SELECT,
the time the terminal told, and whether a payment was signed, an owner claimed or
a card with no owner's history read in this time in the field (5.10, 5.11).

The owner key is a NIST P-256 (secp256r1) public key. Signatures are ECDSA with
SHA-256 in DER form, and the key is uncompressed (`04 ‖ X ‖ Y`, 65 bytes). It
has nothing to do with the card's own secp256k1 key or with Cashu. The applet
sets the curve's parameters on the key itself and relies on no default. Up to
software 1.14 a second key of the same kind, the time key, was in the record;
there is none now (5.7).

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
64 before software 1.7. From software 1.20 the card also keeps, for each place, 96
bytes of the mint's proof for the piece in it (e, s and r: 5.9), in a store of its
own that the first `SET_CARD` reserves, 12,288 bytes.

From software 1.23 the first `SET_CARD` reserves the **book** as well, 75 bytes
(`FOXY-CARD-DAILY-LIMIT.md` 8.5, `FOXY-CARD-KEYSETS.md`):

| Field | Size | |
|---|---|---|
| table | 54 | six entries of nine bytes: a keyset as a piece holds it (8), then a state byte (`00` free; `40` in use, `80` added for the mark, entered with the holder's say; bit 0 the unit, `00` sats, `01` dollars). Written at a load, the state byte last; let go by the reclaim |
| last payment's unit | 1 | relative to the record's: `80` the record's, `A0` the other, `00` none. Written with each payment; `00` at `SET_CARD` |
| the other unit's count | 4 | what the card has signed for, ever, in the other unit than the record's (the log's head counts the record's) |
| receipts' units | 16 | each receipt's payment's unit, relative, beside each of the sixteen receipts |

`RESET_CARD` zeros it. A card refused the room is a card of one unit.

The text of a piece's secret, which is what the card signs over, is the text
Foxy's Cashu library writes for a piece locked to one key with the `SIG_ALL` flag
(format 4, software 1.4):
`["P2PK",{"nonce":"…","data":"<card key>","tags":[["sigflag","SIG_ALL"]]}]`, and
with a date
`…"tags":[["locktime","<date>"],["refund","<refund key>"],["sigflag","SIG_ALL"]]}]`.
The card builds what the library builds, so loading a card is an ordinary locked
payment and nothing is made specially for it. It is not upstream's text. (Up to
format 3 the tags carried no flag: `"tags":[]`.)

### 5.2 Commands

Below, LIMIT is `FOXY-CARD-DAILY-LIMIT.md`. Its section 8 has the same tables.

| Command | Upstream | Fork |
|---|---|---|
| SELECT | version | a new AID of our own, so the two applets cannot be mistaken for each other; answers its version (1.1 when first written, 1.23 now: `01 17`) |
| GET_INFO | 8 bytes | 30: adds the format, tries left, locked, record set, the daily limit, whether the card has an owner, the day (`now`, window start, spent today), and whether change is owed to the card (byte 29, to anyone, 1.21). With P1 = 1, 42: the limit on one payment (to the owner's grant, or on a card with no owner to the PIN in a held tap; zeros to anyone else), the no-PIN allowance and what is counted against it (to anyone; software 1.16). With P1 = 2 (software 1.19), 43: those 42 and whether the NFC tag is on (to anyone; 5.8). 1.23: byte 8 is 4 or 5 by what the card holds, and P1 = 3 says the book (LIMIT 8.5) |
| GET_PUBKEY | as is | as is |
| **GET_DLEQ** (`1A`) | none | software 1.20: the mint's proof kept with an unspent piece, for one place or two. No PIN. The card does not check it (5.9) |
| GET_CARD | none | the card record, with the daily limit and, where the time key was, the clock's proof (the hardest `bits` and the newest header's hash), and the design. 1.23: the rate masked, and with P1 = 2 the table of keysets |
| GET_SLOT_STATUS, GET_PROOF | as is | the slot with its date; 128 slots |
| GET_BALANCE, GET_PROOF_COUNT | as is | as is (1.23: `GET_BALANCE` is the record's unit's sum, and with P1 = 1 each unit's) |
| **GET_PIECES** (`17`) | none | every slot's state and every unspent piece, a page at a time, or a short listing for choosing pieces (LIMIT 8.1, 6d). No PIN |
| **GET_LOG** (`18`) | none | the card's own account of its taps, and its receipts (LIMIT 6b): on a card with an owner to the PIN or the owner's grant (the receipts to the grant alone); on a card with a PIN and no owner to the PIN, held for (software 1.22, 5.11). 1.23: P1 = 2 and 3 say each payment's unit |
| **SPEND** | takes a slot and 32 bytes from the reader | gone (`20` answers `6D00`). A payment is one signature for all its pieces (NUT-11 `SIG_ALL`, format 4, software 1.4): `SPEND_ALL_BEGIN` names the places, `SPEND_ALL_OUTPUTS` the swap's outputs, `SPEND_ALL_CHANGE` the change the card makes for itself, and `SPEND_ALL_SIGN` signs. The card builds every piece's secret from the slot and its own key; no message comes from outside. When a daily limit is set it refuses (`6A8F`) a payment that would take today past it, with nothing signed and nothing burned. Otherwise it signs (in RAM), then notes the places spent and adds what leaves the card to what has been signed today, in one transaction, and only then sends the signature: a card pulled away while it signs has burned and sent nothing, and no signature leaves for places not burned (LIMIT 6c, 6d) |
| **SIGN_ARBITRARY** | signs any 32 bytes | **removed** (`6D00`) |
| **AUTH** | none | the reader sends 16 random bytes; the card answers 16 of its own and a signature over a tagged hash of both and its key. No PIN. It cannot be a spend: the tag makes the message one no secret hashes to |
| VERIFY_PIN | as is | as is, and the PIN may be sealed (software 1.9). It renews the no-PIN allowance (software 1.21, 5.7a), and changes nothing else about spending: it can be sent as often as its sender likes |
| SET_PIN | sets a PIN once (`6985` if one exists) | sets or replaces the PIN, and unblocks, only on a card with no owner, with nothing unspent and no change owed. It is the first thing a card is given (software 1.21). On a card not yet set up, or whose PIN is blocked: no PIN, no proof. On a card with no owner that is set up (software 1.22): the PIN it has, given in this tap, and once used the history held for (5.11). A card with an owner refuses it (`6A91`); from 1.16 to 1.20 a card with an owner and no PIN took it under the owner's grant (ADD PIN), and no such card can be made now |
| CHANGE_PIN | the old PIN, in a verified session | the owner's proof and the new PIN only: no old PIN, no session, any PIN state, any funds. It sets the PIN, resets the tries and unblocks (5.6) |
| SET_CARD | none | writes the card record (unit, refund key, mint, design; the 65 bytes where the time key was are sent and not read). On a card with no owner under the PIN given in this tap (or none, if it has no PIN yet: it holds nothing), and, once the card has been used, the history held for in the tap as well (software 1.22, 5.11); on a card with an owner under the owner's proof; only with nothing unspent and no change owed. Never touches the limit or the clock (the time and the newest header's hash); clears the ratchet's hardest bits. 1.23: a unit of sats or dollars only, and the table let go |
| LOAD_PROOF | 77 bytes | 81 bytes, with the date; refused unless a PIN is set (`6A97` with none, software 1.21) and given in this tap, on a card with an owner or none (software 1.22; up to 1.21 a card with no owner was refused, `6A90`). Nothing else opens it: not the owner's grant, not the tap after a payment (1.21; from 1.16 to 1.20 a card with no PIN took a piece from whoever held it, and the owner's grant and the tap after a payment let pieces on with no PIN). It asks nothing of the clock. Never touches the limit. With P1 = 1 (software 1.20), one piece and the mint's proof for it, 177 bytes (5.9). 1.23: with P2 = 1 the pieces' unit first, each piece held to the table of keysets (LIMIT 8.5) |
| CLEAR_SPENT | as is | the PIN given in this tap (`6A97` with none, 1.21), on a card with no owner too (1.22); not the owner's grant, nor the tap after a payment (1.21) |
| LOCK_CARD | the PIN if one is set | a PIN set and verified, and the owner's proof (5.6). A locked card still pays, under its PIN and its no-PIN allowance, still takes its own change, and its settings are frozen |
| **RESET_CARD** (`51`) | none | software 1.17: the owner's proof (and, on a locked card, the PIN given in this tap) wipes the card to what it was out of its packet, with a new key of its own, for another person to set up. Only with nothing unspent and no change owed (5.6a). With P1 = 1 (software 1.22), one opening of change given up and nothing reset (5.10) |
| **SET_TAG** (`52`) | none | software 1.19: the owner's proof switches the card's NFC tag off or on (5.8). No PIN, any funds. The tag itself is a third applet of the package, under the NFC Forum's own name |
| **SET_LIMIT** (`33`, `34`) | none | `34` by the owner's proof, on any card with an owner and any funds, with no PIN: four bytes are the daily limit (5.7); eight are the day's and then the limit on ONE PAYMENT, which asks no clock and makes a larger payment wait (LIMIT 6a); twelve (software 1.16) add the NO-PIN ALLOWANCE (5.7a). 0 is no limit. `33` by the PIN, on a card with no owner while it is empty and no change is owed to it (and, once used, with the history held for: 5.11): four bytes are the limit on one payment, and eight that and the no-PIN allowance (software 1.21; up to 1.20 four bytes were the daily limit). None needs a clock to be set. (`33` was once a limit on one PIN entry, which a terminal that had the PIN walked round; it is not that). 1.23: every figure is cents, and the rate is written with them: sixteen bytes by the owner, twelve by the PIN; the old lengths only on a dollars record (LIMIT 8.5) |
| ~~SET_TIME~~ (`35`) | none | gone in software 1.15 (`6D00`). It told the card a time under the time key's signature |
| **SET_HEADER** (`36`) | none | shows the card a Bitcoin block header (software 1.15). The card believes it for its work, and a later time in it moves the card's clock forward; it only moves forward (5.7) |
| **TELL_TIME** (`37`) | none | gives the card the terminal's own clock, as a note for its receipts and log (software 1.15). The card trusts it for nothing |
| **SET_OWNER** (`43`) | none | gives the card its owner's public key (5.6); on a card with no owner, a claim made under the PIN by six askings in a row (software 1.21, 5.10), with no change owed (1.24: pieces on the card or not; up to 1.23 only of an empty card) |
| **GET_NONCE** (`44`) | none | a fresh 16 bytes for one owner's proof. No PIN (5.6). With P1 = 1, on any card, the nonce, the PIN key and the card's signature over it, for sealing a PIN (software 1.9) |
| **ALLOW_LOAD** (`45`) | none | the owner's proof gives the owner's grant for the rest of this tap: the log and the receipts read, the limit on one payment told, a second payment not slowed, and, with the PIN, `LOAD_CHANGE` under another key (5.10). It opens no load (software 1.21; up to 1.20 it let `LOAD_PROOF` and `CLEAR_SPENT` through with no PIN) |
| **LOAD_CHANGE** (`53`) | none | software 1.21: an opening of the card's own change made a piece, by any reader, with no PIN, under the key bound at the payment. The card checks the mint's proof, takes off its own blinding factor and writes the piece (5.10) |
| **GET_CHANGE** (`19`) | none | software 1.12: the openings of the card's own change not yet loaded; since 1.21 each one's number, amount, keyset, date, blinded message and the mint's key it was bound to, never its blinding factor (5.10) |
| **SPEND_ALL_AGAIN** (`25`) | none | the last signature given, again, for a terminal that never heard it (LIMIT 6c) |

**The instructions of this fork, by byte** (class `B0`, any other `6E00`; an
instruction the card does not have is `6D00`; `spec/vectors/transcript.json` has
most of them as commands and answers). The answers are in the order the card
checks them:

| INS | Command | Needs | Data sent | Answers |
|---|---|---|---|---|
| `01` | **GET_INFO** | nothing | | 30 bytes: the draft's 17 (version 2, slots, unspent, spent, empty, capabilities, PIN state, format, tries left, locked, record set, limit 4, has owner 1), then `now` (4), window start (4), spent today (4), then byte 29: `01` while change is owed to the card (an opening of its own change waits for its piece, LIMIT 8.2) and `00` otherwise, said to anyone (1.21; up to 1.20 it said whether this tap might load with no PIN). With **P1 = 1**, 42 bytes: those thirty, then the limit on one payment (30 to 33), the no-PIN allowance (34 to 37) and what has been signed for with no PIN since the PIN was last given (38 to 41), 4 each (LIMIT 6a, LIMIT 6f). With **P1 = 2** (1.19), 43 bytes: those 42 and byte 42, `00` when the NFC tag is on, `01` when it is off, said to anyone (5.8); the answer to P1 = 1 is the same whatever the tag's state. Any other P1 is the thirty bytes. The limit on one payment is said only with the owner's grant given in this tap, or, on a card with no owner, to the PIN given in a tap whose history was held for (1.22, LIMIT 8.4), and is zeros to anyone else (LIMIT 6a); the allowance and its count are said to anyone (LIMIT 6f). Up to 1.15 bytes 34 to 41 were zeros. It refuses nothing. **1.23:** byte 8 is the card's format worked out at each asking, `04` where the table holds no keyset of the other unit and `05` where it does, in every form; with **P1 = 3**, 51 bytes: those 43, then the book's format (43: `01`, `00` with no book), the rate (44 to 47, told as the limit on one payment is and zeros to anyone else), the entries in use (48), those without the mark (49) and the last payment's unit (50: `80` the record's, `A0` the other, `00` none); every figure is cents (LIMIT 8.5) |
| `10` | GET_PUBKEY | nothing | | the card's 33-byte compressed key. `6F00` if the chip gives the key in a form the card does not know |
| `11` `12` `13` `14` | GET_BALANCE, GET_PROOF_COUNT, GET_PROOF, GET_SLOT_STATUS | nothing | `13`: P1 = the slot | as before. `GET_PROOF` gives the 82-byte slot: `6A83` for a slot past 127, `6A88` for an empty one. `GET_SLOT_STATUS` gives 128 bytes. **1.23:** `GET_BALANCE` is the sum of the unspent pieces of the record's unit alone; with **P1 = 1**, a count of units held (1), then for each, sats first, its code (1), pieces (1) and sum (4) (LIMIT 8.5) |
| `15` | AUTH | nothing | 16 random bytes | 16 of the card's own and a signature. `6700` for any length but 16 |
| `16` | **GET_CARD** | nothing | | format, record set, unit, limit (4), refund key (33), then 65 bytes at offset 40: the hardest header's `bits` (4), the newest header's hash (32) and 29 zeros (a time key up to software 1.14), mint length, mint, and the design (3, zeros for none). **1.23:** byte 0 is the card's format (4 or 5); the rate, kept in the first four of the 29 zeros, is answered as zeros; with **P1 = 2**, the table: a count (1) and each entry in use as kept (9), 55 bytes at the most, `00` alone with no book |
| `17` | **GET_PIECES** | nothing | P1 = the first slot to report | one page: the first slot the page does not cover (1), then for each slot in the range that is not empty a tag (1) and, for an unspent slot, its 81 bytes (LIMIT 8.1). `6A83` for a P1 of 128 or more. With **P2 = 2** and one to three slot numbers as data: each of those slots whole (status and its 81 bytes), in the order asked; `6700` for none or more than three, `6A83` for a slot past 127 (LIMIT 8.1). With **P2 = 3**, the short listing: for each unspent slot its number (with `0x80` where its keyset and date follow), then the power of two it is worth or `FF` and its amount (LIMIT 6d); `6A83` for a P1 of 128 or more. Any other P2 is `6A86` (P2 = 1 was the brief listing of a card of sixty-four places, 1.6) |
| `18` | **GET_LOG** | P1 = 0, the log: on a card with an owner, the PIN verified in this tap or the owner's grant. P1 = 1, the receipts: on a card with an owner, the owner's grant only. On a card with a PIN and no owner (1.22), both: the PIN given in this tap, and held for (LIMIT 8.4). On a card with no PIN, anyone (its log is empty: it has taken and paid nothing) | P2 with P1 = 1: how many receipts back from the newest to begin | the card's own log (LIMIT 6b): 16 bytes of counts, then up to eight taps of 20 bytes, newest first (16 bytes before software 1.15); bit 3 of an entry's flags (1.16) says a payment in the tap was signed with no PIN under the allowance, and bit 4 (1.22) that an opening of change was given up in it. With P1 = 1: the count of payments (4), then up to three receipts of 77 bytes, newest first (73 before 1.15). In order: `6A86` for a P1 other than 0 and 1 (it neither counts nor ends the history's held count); with an owner, `6982` without what the row needs; with no owner, `6982` with the PIN not given, `6985` in a time in the field in which a payment was signed (before any asking is counted), then `6A98`, keep holding, with no data, to each of the first nine askings in a row, a signature's work each, and the answer at the tenth; once answered, the log and the receipts are open to the PIN for the rest of that time in the field, and no payment command is taken in it. **1.23:** bit 5 of an entry's flags is the other unit; **P1 = 2** has the other unit's count (4) after the four counts; **P1 = 3** has each receipt's unit after it, 78 bytes each; `6A86` for a P1 over 3 |
| `19` | **GET_CHANGE** (1.12) | nothing: no PIN | P1 = the page, from 0 | the openings of the card's own change not yet loaded, three to a page: a count (1), then for each (1.21) its number (1, the P1 of `53`), amount (4), keyset (8), date (4), blinded message `B_` (33, compressed) and the mint's key it was bound to at the payment (33, compressed), 83 bytes. An opening whose piece is on the card by now is let go first. Never the nonce or the blinding factor: up to 1.20 a record was 80 bytes, the amount, keyset, date, nonce and blinding factor, and any reader could finish the piece (LIMIT 8.2). It refuses nothing |
| `1A` | GET_DLEQ (1.20) | nothing: no PIN, any state | P1 = a place (0 to 127); P2 = a second place, or `FF` for none | for each place asked, in order: `01` and the 96 bytes `e ‖ s ‖ r` of the mint's proof kept with the piece if the place is unspent and its record is not zero, otherwise the single byte `00`. At most 194 bytes. `6A83` for a place there is not, before anything is answered. The card does not check what it gives (5.9) |
| `20` | ~~SPEND_PROOF~~ | | | gone with format 4 (LIMIT 6c): `6D00`. Up to format 3: P1 = the slot, the 64-byte signature over that piece's secret alone |
| `22` | **SPEND_ALL_BEGIN** | a PIN set; no claim and no read of the history in this time in the field; nothing more (since 1.16 the PIN is asked at `24`; up to 1.15 it was asked here) | the places, a byte each, 1 to 128 | what they are worth (4). In order: `6A97` with no PIN (1.21) and `6985` in a time in the field in which an owner was claimed (LIMIT 8.3) or a card with no owner's history was read (LIMIT 8.4), as for every command `22` to `26`; `6700` none; `6A96` more than 128; for each place `6A83` out of range, `6A88` empty, `6985` spent, `6A80` named twice or of another date than the first; then, for a sum that wraps past four bytes only, `6A94` with a PIN set and not given (LIMIT 6f), or `6A8F` where a daily limit is set, written in the log (LIMIT 6c). Lets go any opening of change made for a payment never signed for. **1.23:** for each place, after its `6A80`, **`6A9A`** where it is of another unit than the first place, or its keyset is in no entry (LIMIT 8.5) |
| `23` | **SPEND_ALL_OUTPUTS** | a payment begun, and nothing sent since but these | 37 bytes for each output: amount (4), blinded message (33) | nothing. `6A97`, `6985` as `22`; `6985` with no payment begun; `6985` after the card's own change (`26`), the payment given up; `6700` for a length not a whole number of 37, the payment given up |
| `26` | **SPEND_ALL_CHANGE** (1.12) | a payment begun, the terminal's outputs given | the amount (4) and the mint's key for that amount in the payment's keyset (33, compressed): 37 bytes (1.21; up to 1.20 the amount alone) | one change output the card makes for itself, locked to its own key: the blinded message (33). The opening (amount, keyset, date, nonce, blinding factor and the blinded message) and the key are kept until its piece is loaded (LIMIT 8.2). `6A97`, `6985` as `22`; `6985` with no payment begun; `6700` for any length but 37; `6A80` for an amount of 0, for change that would come to more than the pieces, or for a key that does not begin `02` or `03`; `6A84` with no opening free, or on a card that has no room for the openings' keys; `6F00` if the chip's arithmetic fails. Every refusal but the first three gives the payment up |
| `24` | **SPEND_ALL_SIGN** | a payment begun; the PIN given in this tap, or the no-PIN allowance enough for the whole of the pieces (LIMIT 6f); the day's limit, on what leaves the card | | while the payment is over the limit on one payment: two bytes, the constant `00 01`, "not yet" (never how many are to come: LIMIT 6a), and nothing burned; sent again. Then the one 64-byte signature over every piece and every output; the places noted spent, the counts charged, the openings of change made owed and the log written in the same transaction. In order: `6A97`, `6985` as `22`; `6985` with no payment begun; at the first `SIGN`, `6A94` (1.16) with a PIN set and not given and the allowance not enough, the payment given up and nothing written in the log (LIMIT 6f), then `6A8F` over the day, the payment given up and the refusal written in the log (LIMIT 6); at the last, `6A96` if the transaction cannot hold it, with nothing changed. **Not** opened by `ALLOW_LOAD`. **1.23:** a payment in sats is turned into cents at the rate before it is held to the limits; **`6A99`** for a payment in sats with a limit set and no rate, before the PIN; the log, the count and the receipt in the payment's own unit (LIMIT 8.5) |
| `25` | **SPEND_ALL_AGAIN** | the PIN given in this tap, unless the last signature was given with no PIN (1.16) | | the last signature `24` gave, again. `6A97`, `6985` as `22`; `6982` without the PIN where it is wanted; `6A88` if the card has signed nothing |
| `30` | **LOAD_PROOF** | a PIN set, and given in this tap (1.21: not the owner's grant, not the tap after a payment); a card record. Not an owner (1.22: a card with no owner is loaded under its PIN; up to 1.21 it was refused, `6A90`). No clock | 81 bytes; or two or three pieces end to end, 162 or 243; or, with **P1 = 1** (1.20), one piece and the mint's proof for it, 177 bytes (5.9) | the slot of each piece stored, a byte each; with P1 = 1, two bytes, the slot and `01` where the proof was kept (`00` where the card has no room for proofs). In order: `6986` on a locked card; `6A97` with no PIN; `6982` with the PIN not given in this tap (a blocked PIN is never given); `6A8C` with no record; `6A84` with no place free; `6700` for a length that is not one to three pieces (with P1 = 1, not 177); then, for each piece, `6A84` no place left, `6A8E` for a dated piece on a card with no refund key, `6A80` for a `C` that is no compressed point, an amount of 0, or (P1 = 1) a proof of 96 zero bytes, and `6A94` for a piece whose nonce is already in a slot, spent or not (checked last, before anything is written). Several pieces are taken in order, each as one alone would be: if the first cannot be stored the command is refused with its word; if a later one cannot, the ones before it stand and the answer is their slots only, and the terminal sends the rest again to hear why. A piece that is the card's own change lets its opening go. **1.23:** with **P2 = 1** the pieces' unit first (82, 163, 244; 178 with P1 = 1); `6B00` for another P2; `6A99` once, before the first piece, for pieces in sats on a card with a limit set and no rate; the table's `6A9B`, `6A9D` and `6A9C` for each piece before its `6A94` (LIMIT 8.5) |
| `31` | **CLEAR_SPENT** | a PIN set, and given in this tap, on a card with an owner or none (1.21: not the owner's grant, which freed a blocked card's places up to 1.20, and not the tap after a payment) | | how many places were freed (1). `6986` on a locked card; `6A97` with no PIN; `6982` with the PIN not given. **1.23:** and the table reclaimed (LIMIT 8.5) |
| `32` | **SET_CARD** | no owner: the PIN given in this tap, if one is set (a card with no PIN is written by anyone: it holds nothing); nothing unspent and no change owed; and, once the card has been used, the history held for in this tap (LIMIT 8.4). Owner: the owner's proof; nothing unspent and no change owed | unit (1), refund key (33), 65 bytes the card does not read (zeros from a phone of software 1.15; a time key from an earlier one), mint length (1), mint, and the design (3) or nothing; with an owner, proof length (1) and the proof first | nothing. In order: `6986` on a locked card; with no owner `6982` with a PIN set (or blocked) and not given, with an owner `6A91` with no good proof; `6A8D` if anything is unspent or change is owed; with no owner, on a used card, `6985` in a time in the field in which a payment was signed and `6982` with no hold; `6700` for a bad length, a mint of 0 or more than 77 characters, or a design that is not three bytes; `6A80` for a design that is not capital letters and digits, or a refund key that is neither a compressed point nor 33 zeros. The 65 bytes are not checked, and `SET_CARD` does not clear the clock (the time and the newest header's hash), the window or what is spent; it clears the ratchet's hardest bits (4). The first that goes through reserves the stores of the mint's proofs and of the openings' keys (5.9, LIMIT 8.2). **1.23:** `6A80` for a unit other than `00` and `01`; the first reserves the book; the table reclaimed and the last payment's unit forgotten before its transaction (LIMIT 8.5) |
| `33` | **SET_LIMIT, PIN form** | **a card with no owner**: the PIN given in this tap; nothing unspent and no change owed; once used, the history held for in this tap. No clock | the limit on one payment (4); or eight bytes: that, then the no-PIN allowance (1.21) | nothing. In order: `6986` on a locked card (which always has an owner, and is told `6986` and not `6A91`); `6982` with no PIN given; `6A91` on a card with an owner (use `34`); `6A8D` if anything is unspent or change is owed; on a used card `6985` in a time in the field in which a payment was signed and `6982` with no hold (1.22); `6700` for any length but 4 or 8. Eight bytes set the allowance's count to 0. It does not set the daily limit (1.21; up to 1.20 four bytes were the day's limit and eight the day's and the one payment's). **1.23:** **twelve bytes** with the rate; four and eight on a dollars record alone, `6700` on a sats one (LIMIT 8.5) |
| `34` | **SET_LIMIT, owner's form** | the owner's proof. **No PIN**, any funds. No clock | proof length (1), proof (DER), limit (4) | nothing. `6986` on a locked card; `6A90`; `6A91`; `6700` for a limit that is not 4, 8 or 12 bytes. Eight bytes of limit (the day's, then the one payment's) set both (LIMIT 6a); **twelve** (1.16) add the no-PIN allowance (LIMIT 6f). A number that does not change keeps its window and its count (from 1.16 to 1.20, on a card with no PIN, a number in the allowance was `6985`; every card with an owner has a PIN since 1.21). **1.23:** **sixteen bytes**, the three figures in cents and the rate; four, eight and twelve on a dollars record alone, `6700` on a sats one (LIMIT 8.5) |
| `35` | ~~SET_TIME~~ | | | gone in 1.15: `6D00`, and the instruction stays unassigned. Up to 1.14: a time (4), a signature length (1) and a signature (DER) by the time key, answered with the card's `now` |
| `36` | **SET_HEADER** | nothing: no PIN, no owner, no record, any state | a Bitcoin block header, 80 bytes, as the network carries it (LIMIT 5.1) | the card's `now` (4, big-endian), whether the header was taken or was no later than the clock. `6700` for any length but 80; `6A80` for a `bits` no header could carry; `6A93` for too little work (its own, the floor's, or a quarter of the hardest taken). Drops a payment begun |
| `37` | **TELL_TIME** | nothing: no PIN, no owner, no record, any state | the terminal's own clock, 4 bytes, big-endian seconds | nothing. `6700` for any length but 4. Kept for this time in the field and written into the receipts and the log entries made in it (LIMIT 5.3). Drops a payment begun |
| `40` | **VERIFY_PIN** | a PIN set | the PIN, 4 to 8 bytes; or, with **P1 = 1**, the PIN sealed (LIMIT 6e) | nothing; the PIN is given for the rest of this tap, and the no-PIN allowance renews (its count goes to 0: 1.21, LIMIT 6f). In order: `6A86` for a P1 other than 0 and 1; `6984` with no PIN set; `6983` with the PIN blocked; sealed, `6700` for an envelope too short, `6985` with no nonce asked for, a try lost for an envelope that does not open (as a wrong PIN, below), `6700` for a block that is not nine bytes or a length in it not 4 to 8; `6700` for a PIN that is not 4 to 8 bytes; `63CX` for a wrong PIN, X tries left, and `6983` for the one that blocks it. A wrong PIN ends the PIN given earlier in the tap |
| `41` | **SET_PIN** | **a card with no owner**; nothing unspent and no change owed. A card not set up (no PIN, a blocked PIN, or no record): no PIN and no proof. A card set up (a PIN that is not blocked, and a record) (1.22): the PIN it has, given in this tap, and, once used, the history held for in this tap (LIMIT 8.4). It is the first thing a card is given (1.21). (From 1.16 to 1.20 also, ADD PIN, a card with an owner and no PIN under the owner's grant; gone, LIMIT 6f) | the PIN; or, with **P1 = 1**, the PIN sealed (LIMIT 6e) | nothing; sets or replaces the PIN, gives back the tries, unblocks, and ends the PIN given earlier in the tap. A blocked PIN replaced takes the log and the receipts with it; on a card that had no PIN the allowance is nothing. In order: `6A86` for a P1 other than 0 and 1; `6986` on a locked card; `6A91` on a card with an owner, grant or none (use `42`); on a set-up card `6982` with its PIN not given; `6A8D` if anything is unspent or change is owed; on a set-up card that has been used, `6985` in a time in the field in which a payment was signed and `6982` with no hold; sealed, `6700` for an envelope too short, `6985` with no nonce, `6A80` for an envelope that does not open (no try is lost), `6700` for a block that is not nine bytes; `6700` for a PIN that is not 4 to 8 bytes |
| `42` | **CHANGE_PIN** | the owner's proof. **No PIN, no session, any PIN state, any funds** | proof length (1), proof (DER), the new PIN; or, with **P1 = 1**, the same sealed, the PIN as its nine-byte block (LIMIT 6e) | nothing; sets the PIN, gives back the tries, unblocks, and ends the PIN given earlier in the tap. In order: `6A86` for a P1 other than 0 and 1; `6986` on a locked card; `6A90`; sealed, `6700` for an envelope too short, `6985` with no nonce, `6A80` for an envelope that does not open, `6700` for a bad block; `6A91` with no good proof (over the PIN itself and the same nonce the seal is under); `6700` for a new PIN that is not 4 to 8 bytes |
| `43` | **SET_OWNER** | no owner (the claim, 1.21): a PIN set and given in this tap, no payment signed in this time in the field, no change owed (1.24: its pieces stay on it; up to 1.23 nothing unspent); the same key asked for six times in a row (LIMIT 8.3). Owner: the owner's proof, nothing unspent | 65 bytes, the owner's public key, uncompressed; with an owner, proof length (1) and the proof first | the claim: `6A98`, keep holding, with no data, to each of the first five askings, a signature's work each, and the sixth sets the owner, answering nothing; a different key, or any other command between, begins the count again. With an owner: replaces it at once. In order: `6986` on a locked card; with an owner `6A91` with no good proof; with no owner `6A97` with no PIN, `6982` with the PIN not given, `6985` in a time in the field in which a payment was signed; `6A8D` with an owner if anything is unspent, with none if change is owed (1.24); `6700` for a key that is not 65 bytes; `6A80` for a key not beginning `04`; then the askings; `6A80` for a key that is not a point. After a claim, no payment is signed in that time in the field |
| `44` | GET_NONCE | a card with an owner. No PIN | | 16 bytes. `6A86` for a P1 other than 0 and 1; `6A90` with no owner. With **P1 = 1**, on any card: the 16 bytes, the PIN key (33, compressed) and the card key's signature over it (64), for sealing a PIN (LIMIT 6e) |
| `45` | **ALLOW_LOAD** | the owner's proof, over `FoxyCard/load` and no value | proof length (1), proof | nothing; for the rest of this tap the owner's grant (LIMIT 3, rule 9): the log and the receipts read, the limit on one payment told, a second payment not slowed, and with the PIN another key for `LOAD_CHANGE` (LIMIT 8.2). It opens no load (1.21; up to 1.20 `LOAD_PROOF` and `CLEAR_SPENT` needed no PIN under it). `6986` on a locked card; `6A90`; `6A91`; `6700` for bytes after the proof |
| `50` | LOCK_CARD | PIN set and given in this tap; the owner's proof | P2 = `DE` (P1 is not read); proof length (1), proof | nothing; the card locked for good. A locked card **still pays**, under its PIN and its no-PIN allowance, up to its limits, and **still takes its own change** (`LOAD_CHANGE`: receiving what it is owed changes no setting); **every other write is `6986`**, so its limit, its allowance and its PIN can no longer be changed, nor its places freed or loaded. Only `RESET_CARD`, with the PIN as well, takes it back. The phone never sends it. In order: `6982` with no PIN given (a card with no PIN, or a blocked one, cannot be locked); `6B00` for a P2 not `DE`; `6985` on a card already locked; `6A90`; `6A91`; `6700` for bytes after the proof |
| `51` | **RESET_CARD** (1.17) | P1 = `00`: the owner's proof; a locked card also the PIN given in this tap; nothing unspent and no change owed. P1 = `01` (1.22): see LIMIT 8.2 | P1 = `00`, P2 = `AD`; proof length (1), proof over `FoxyCard/reset` and nothing after it | nothing; the card as out of its packet, with a new key (5.6a). In order: `6B00` for a P1 or P2 not those; `6A90`; `6A91`; `6700` for bytes after the proof; `6982` on a locked card whose PIN has not been given; `6A8D` if anything is unspent or change is owed. With **P1 = 1** and P2 an opening's number, that opening given up and nothing reset: with an owner, the owner's proof over the number, then the number; with no owner, the number alone, under the PIN and a held tap (LIMIT 8.2, which has its words). **1.23:** the book zeroed, and the rate with the record |
| `52` | SET_TAG (1.19) | the owner's proof; no PIN, any funds | P1 = `00` tag on, `01` off; P2 = `00`; proof length (1), proof over `FoxyCard/tag` and one byte, then that byte (the same as P1) | nothing; the NFC tag switched (5.8); the state it has already is `9000` and writes nothing. In order: `6B00` for a P1 or P2 not those; `6986` on a locked card; `6A90`; `6A91`; `6700` for anything but one byte after the proof; `6B00` for a byte that is not P1 |
| `53` | **LOAD_CHANGE** (1.21) | a PIN set; then nothing more: any reader, in any tap, locked or not, blocked or not, under the key the opening was bound to at the payment. Another key: the PIN given in this tap, no payment signed in this time in the field, and the holder's say in the tap (the owner's grant, or on a card with no owner the history held for) (1.22, LIMIT 8.2) | P1 = the opening's number (`GET_CHANGE` gives it); the mint's blinded signature `C_` (65, `04 ‖ x ‖ y`), the mint's key `K` for the opening's amount (65, the same form), and the mint's NUT-12 proof `e`, `s` (32 each): 194 bytes | 83 bytes: the place the piece went into (1), `01` where the mint's proof was kept with it (`00` where the card has no room for proofs), and the piece as `GET_PROOF` gives it after its status byte (81). The card checks the proof against `K` and its own `B_`, takes its blinding factor off (`C = C_ − r·K`), writes the piece with the opening's keyset, amount, nonce and date, keeps `e`, `s` and `r` as the piece's proof (`GET_DLEQ` answers for it), writes the log, and lets the opening go (LIMIT 8.2). In order: `6A97` with no PIN; `6A83` for a P1 past 7; `6A88` for an opening not waiting; `6A94` for one whose piece is on the card already (the opening is let go); `6A84` with no place free; `6700` for any length but 194; `6A80` for a `K` not in the `04` form; for a `K` other than the bound one, `6A80` with no PIN given, `6985` in a time in the field in which a payment was signed, `6982` with no owner's grant (with an owner) or no held history (with none); `6A80` for a point that is none, or a proof that does not hold. Nothing is written before the piece itself |

`21` (`SIGN_ARBITRARY`) stays unassigned. The applet's version was 1.1 and
`FORMAT` 3 when these tables were first written; it is 1.23 now, `SELECT` answers
`01 17`, and `FORMAT` is 4 (and the card says 5 while it holds both units, LIMIT 8.5). A phone that knows only an earlier format refuses the
card. What each version changed on the wire is in LIMIT section 8, after its
table.

**Every command in every state.** "Open" means nothing is needed beyond what
the command's own row says; "refused" means `6A91` unless another word is given.
Every command that writes answers `6986` on a locked card before any other check
but that of its P1 and P2; a locked card still takes `SET_HEADER` and `TELL_TIME`,
still pays, still takes its own change (`LOAD_CHANGE`), and is reset for the
owner's proof and the PIN. A card with no PIN (software 1.21: it has no owner, and
nothing on it) refuses every load, every payment command, an owner's claim and the
let-go with `6A97`.

| Command | Card with **no owner** | Card **with an owner** |
|---|---|---|
| `GET_INFO`, `GET_PUBKEY`, `GET_BALANCE`, `GET_PROOF_COUNT`, `GET_PROOF`, `GET_SLOT_STATUS`, `GET_PIECES`, `GET_DLEQ` (1.20), `GET_CHANGE`, `AUTH`, `GET_CARD` | open (the limit on one payment in `GET_INFO`: the PIN in a held tap, 1.22) | open (the limit on one payment: the owner's grant) |
| `GET_INFO` P1 = 3, `GET_CARD` P1 = 2, `GET_BALANCE` P1 = 1 (1.23) | open; the rate in `GET_INFO` P1 = 3 to the PIN in a held tap | open; the rate to the owner's grant |
| `GET_LOG` (`18`), the log and its receipts | the PIN given in this tap (`6982`), held for: ten askings in a row, `6A98` to each but the last; `6985` in a tap that paid; open for the rest of the time in the field once answered (1.22; no PIN set: open, and empty) | the log: the PIN or the `ALLOW_LOAD` grant; the receipts: the grant alone (`6982`); at once |
| `SET_HEADER`, `TELL_TIME` | open: any state, locked or blocked (a header must show its work, LIMIT 5.1) | same |
| `GET_NONCE` | `6A90` (with P1 = 1: open) | open (it only gives a number) |
| `VERIFY_PIN` | as ever | as ever |
| `SPEND_ALL_BEGIN`, `SPEND_ALL_OUTPUTS`, `SPEND_ALL_CHANGE` | open (since 1.16); `6985` in a tap that read the history (1.22) or claimed an owner | open; `6985` in a tap that claimed an owner (LIMIT 8.3) |
| `SPEND_ALL_SIGN`, `SPEND_ALL_AGAIN` | the PIN, or the no-PIN allowance, up to the limits (since 1.22 such a card may hold pieces); `6985` in a tap that read the history | the PIN, or the no-PIN allowance, up to the limits (LIMIT 6f) |
| `LOAD_PROOF` | **the PIN given in this tap** (1.22; `6982` with it not given; up to 1.21 refused, `6A90`) | the PIN given in this tap (`6982`); not the `ALLOW_LOAD` grant, and not the tap after a payment (1.21) |
| `LOAD_PROOF` of a new keyset of the other unit than the record's, not the last payment's (1.23) | the PIN in a held tap (`6A9D` with the PIN alone) | the PIN and the owner's grant in this tap (`6A9D` with the PIN alone) |
| `LOAD_CHANGE` (`53`, 1.21) | any reader, under the key bound at the payment (such a card pays, and may be owed change); another key under the PIN in a held tap that paid nothing (1.22) | any reader, under the key bound at the payment; another key under the PIN and the `ALLOW_LOAD` grant in a tap that paid nothing (1.22) |
| `CLEAR_SPENT` | the PIN given in this tap (`6982` with it not given) | the PIN given in this tap (not the grant, and not the tap after a payment, 1.21) |
| `SET_PIN` (`41`) | **open while nothing is unspent or owed, until the card is set up, or with its PIN blocked** (sets or replaces, unblocks); once set up, under the PIN it has, and once used the history held for (1.22, LIMIT 8.4) | **refused**, empty or not (`6A91`): the owner uses `CHANGE_PIN` (from 1.16 to 1.20 a card with no PIN took one under the owner's grant, ADD PIN) |
| `CHANGE_PIN` (`42`) | `6A90` | the owner's proof, empty or not, any PIN state |
| `SET_CARD` (`32`) | the PIN given in this tap if one is set (`6982`; none: open), **while nothing is unspent and no change is owed**, and once used the history held for in this tap (1.22, LIMIT 8.4) | **the owner's proof, and nothing unspent or owed** (`6A8D`); `6986` locked |
| `SET_LIMIT` (`33`, PIN form) | the PIN given in this tap, **while nothing is unspent or owed**, and once used the history held for (1.22) | **refused**; `6986` on a locked card |
| `SET_LIMIT` (`34`) | `6A90` | the owner's proof, empty or not |
| `SET_LIMIT` with the rate (1.23) | `33`, twelve bytes, as 1.22's `33` | `34`, sixteen bytes, as 1.22's `34` |
| `SET_OWNER` (`43`) | **the claim, with no change owed** (1.24, pieces on the card or not; up to 1.23 while nothing is unspent): under the PIN given in this tap, six askings in a row, never in a tap that signed a payment (LIMIT 8.3) | **the owner's proof, and nothing unspent** |
| `ALLOW_LOAD` (`45`) | `6A90` | the owner's proof |
| `LOCK_CARD` (`50`) | `6A90` (after the PIN gate) | PIN set and given, and the owner's proof |
| `RESET_CARD` (`51`, 1.17) | `6A90`; with P1 = 1 (1.22) one opening given up under the PIN in a held tap | the owner's proof, nothing unspent or owed (`6A8D`); on a locked card the PIN as well (`6982`); with P1 = 1 one opening given up under the proof |
| `SET_TAG` (`52`, 1.19) | `6A90` | the owner's proof, empty or not, no PIN; `6986` on a locked card |

So the one thing a terminal with the PIN and an emptied card with an owner can
still do is load pieces onto it (which the mint will refuse) and spend them. It
cannot set its own PIN, owner, record or limit. A card with a PIN and no owner,
the card of a holder with no phone since software 1.22, is the PIN's: a terminal
with its PIN can also, on the empty card owed nothing, and in a held tap of its own
once the card has been used, set its record, its numbers and its PIN, and claim it
(5.11).

Status words the fork added or gave a meaning of its own, most in a part of `6Axx`
ISO 7816-4 leaves unassigned:

| SW | Means |
|---|---|
| `6A8C` | no card record: a load (`LOAD_PROOF`) before `SET_CARD` has run |
| `6A8D` | card in use: a thing that is set only on an empty card, on a card with an unspent piece; and (1.21) `SET_CARD`, `RESET_CARD`, and on a card with no owner `SET_PIN`, the PIN form of `SET_LIMIT` and (1.24) the claim, while change is owed to the card (LIMIT 8.2) |
| `6A8E` | a dated piece on a card with no refund key |
| `6A8F` | over the day: this payment would take today past its limit. Nothing signed, nothing burned, the payment given up, and the refusal written in the card's log |
| `6A95` | unused. It was "over the limit on one tap", when that was a window and refused; a payment over the limit on one payment now waits (LIMIT 6a) |
| `6A90` | no owner: a command that needs the owner's proof, or `GET_NONCE`, on a card that has none (up to 1.21 a load as well; since 1.22 a card with no owner is loaded under its PIN) |
| `6A91` | the owner's proof is missing or is not the owner's, or this command is not open to a card with an owner. Costs no PIN tries, changes nothing, does not end the PIN session |
| `6A92` | unused. It was "no time", for a card never told the time; since 1.15 a card spends its first day on trust (LIMIT 6) and nothing answers it |
| `6A93` | little work: a `SET_HEADER` whose work is not enough, whether not its own difficulty's, under the floor, or under a quarter of the hardest header taken (LIMIT 5.1). Nothing changes. (Up to 1.14: "not the time", a `SET_TIME` whose signature was not the time key's) |
| `6A94` | on the card already: a `LOAD_PROOF` or `LOAD_CHANGE` of a piece whose 32-byte nonce is the nonce of any non-empty slot, spent or unspent, and the let-go of an opening whose piece is on the card already (LIMIT 8.2). Nothing is written, and the amount stated is not looked at. A slot that `CLEAR_SPENT` has freed is empty, so that piece may be loaded again then. **Also, since 1.16, the PIN is needed** (`SPEND_ALL_SIGN`, and `SPEND_ALL_BEGIN` for a sum that wraps): a PIN is set and was not given in this tap, and the no-PIN allowance does not cover the whole payment. Nothing is signed, burned or counted, the payment is given up, and nothing is written in the log (LIMIT 6f). The commands are never confused |
| `6A96` | too many: a payment of more places than the card has, or one whose transaction the chip could not hold. Nothing burned, nothing changed |
| `6A97` | **1.21.** No PIN: a load (`LOAD_PROOF`, `CLEAR_SPENT`, `LOAD_CHANGE`), a payment command (`22` to `26`), an owner's claim (`SET_OWNER` on an open card) or (1.22) the let-go of a card with no owner, on a card that has no PIN. A card is given its PIN before anything goes on it or leaves it. Nothing is written. Not `6982`, which is a PIN that is set and not given |
| `6A98` | **1.21.** Keep holding: a held tap counted and not yet through. The first five askings of an owner's claim (LIMIT 8.3) and (1.22) the first nine askings of the history of a card with no owner (LIMIT 8.4). No data, and nothing written but the count. Ask again. Not `9000`, so that a phone that asks once never takes it for done |
| `6A99` | **1.23.** No rate: `SPEND_ALL_SIGN` of a payment in sats on a card with a limit set and no rate, given up, nothing signed, burned, counted or written; and `LOAD_PROOF` of pieces in sats on such a card, nothing written |
| `6A9A` | **1.23.** Two units in one payment: `SPEND_ALL_BEGIN` given places of both units, or a place whose keyset is in no entry. No payment begun |
| `6A9B` | **1.23.** The other unit: a load of a piece whose keyset is in the table as the other unit than the load names. Nothing written |
| `6A9C` | **1.23.** No room in the table: a new keyset with no entry free after a reclaim, or a third live entry without the mark; a load labelled the other unit on a card refused its book |
| `6A9D` | **1.23.** Not this tap's to start: a new keyset of the other unit than the record's, not the last payment's, without the holder's say in the tap |
| `6982` | the PIN not given in this tap, where a command needs it (a blocked PIN is never given); and (1.22) a command that needs a held tap, in a tap with the PIN and no hold, and another key for `LOAD_CHANGE` with no owner's grant or held history; and the owner's grant not given, for the receipts of a card with an owner (the grant alone) or its log (the PIN or the grant), and for the history of a card with no owner when the PIN has not been given |
| `6985` | no payment begun, for a step of a payment; a spent place named; a card already locked; a nonce not asked for, for a sealed PIN; and (1.21, 1.22) a payment command in a time in the field in which an owner was claimed or a card with no owner's history was read, or a claim, the history, a rewrite of a used card with no owner, a let-go with no owner, or another key for `LOAD_CHANGE`, in a time in the field in which a payment was signed; and `SPEND_ALL_OUTPUTS` sent after the card's own change (the terminal's outputs come before it) |

`SET_HEADER` also answers `6A80` (ISO's own word for wrong data) for a `bits` no
header could carry: a size of 0 or of more than 32, or a mantissa with its top
bit set.

### 5.3 Rules the applet must keep

1. **No signature without places burned.** The signatures that leave the card
   are a payment's (`SPEND_ALL_SIGN`, which notes its places spent in the
   transaction that commits it, before the signature is answered; and
   `SPEND_ALL_AGAIN`, which gives that same signature again), `AUTH`'s, and the
   card's signature over its PIN key (`GET_NONCE` with P1 = 1, made once). The
   signatures a payment's wait and a held tap make are thrown away and never
   leave the card.
2. **The PIN gate comes first** in every gated command, in every PIN state,
   including blocked, after only the lock (`6986`) and, where a command has one, a
   P1 it can read (`6A86`). One test walks every command through every
   state. (Since software 1.16 a payment's gate is at its signature, where the
   no-PIN allowance stands beside the PIN: 5.7a.)
3. **A daily limit, counted against a day the card keeps.** The card keeps
   the most it will sign for in one day, in sats, in permanent memory (0 is no
   limit, and is what a new card has), and the day itself: `now`, the time in
   the newest Bitcoin block header it has been shown, which only moves forward;
   and a window, begun when the limit is set and ended only by a newer block.
   A payment that would take today's total past the limit is refused (`6A8F`)
   before anything is signed or burned; otherwise the total and the note that
   the places are spent change in one transaction, after the signature is made
   and before it is answered. Only the owner's proof sets, changes or removes
   the limit; a card with no owner has none (the PIN sets its limit on one
   payment and its allowance, while it is empty: 5.11). `VERIFY_PIN`, a new
   SELECT, `SET_CARD` and loading pieces do not touch it (`RESET_CARD` empties the
   whole record, the limit, the window and what is spent included), and nothing a
   terminal that has the PIN can send changes it except by spending.
   (A limit on one PIN entry, kept in RAM, was tried first: a terminal that
   had the PIN sent it again between spends, or set the limit to nothing. It
   is gone. The limit on one payment that the card has now is not that: it is
   a second number in permanent memory that refuses nothing and asks no clock;
   a payment over it waits, by the card's own work, for every limit's worth
   past the first.) The whole text is `FOXY-CARD-DAILY-LIMIT.md`, sections 3 and 6.
4. **Three tries**, then blocked, as upstream. The owner's proof unblocks it
   (`CHANGE_PIN`), and a wrong proof costs no tries. On a card with no owner a
   blocked PIN is replaced by `SET_PIN` only with nothing unspent and no change
   owed (5.11). The refund path (6) is a second way out, for a card set up as
   recoverable.
5. **Nothing can be added without a PIN set.** Upstream ships cards with no
   PIN, on which any reader in range can write and spend. Since software 1.21 a
   card with no PIN takes nothing and pays nothing (`6A97`): its PIN is the first
   thing it is given. With a PIN set, a piece goes on under the PIN given in the
   tap and nothing else, on a card with an owner or none (1.22); the one
   exception is the card's own change, which any reader may write back with no
   PIN, under the key bound at the payment (`LOAD_CHANGE`, 5.10). (From 1.16 to
   1.20 a card could have no PIN, and then paid without one and was loaded and
   freed by whoever held it; and the owner's grant and the tap after a payment let
   pieces on with no PIN.)
6. Every buffer allocated at install, and the status byte written last, as
   upstream's D10 and D14. One exception, since software 1.20: the store of the
   mint's proofs, and from 1.21 the store of the openings' keys, are reserved by
   the first `SET_CARD`, once, because the chip will not install an applet that
   asks for them in the constructor (5.9).
7. **The PIN pays and loads; the owner's proof administers.** On a card with an
   owner the PIN can pay (up to the limits), `LOAD_PROOF` and `CLEAR_SPENT`, and
   changes nothing else: not the PIN, the limit, the record or the owner.
   `CHANGE_PIN`, the owner's `SET_LIMIT` (`34`), `SET_OWNER`, `SET_CARD`,
   `ALLOW_LOAD`, `LOCK_CARD`, `RESET_CARD` and `SET_TAG` each need an ECDSA
   signature by the owner key over a label of the command, a nonce the card has
   just given and the value (5.6). They need no PIN, because the owner's phone
   does not know it (`LOCK_CARD` keeps its PIN as well, and so does `RESET_CARD`
   on a locked card). A wrong proof costs no PIN tries, changes nothing and does
   not end the PIN session.
8. **A card with an owner is its owner's, empty or not; a card with no owner is
   the PIN's.** A terminal that has the PIN can make a card empty whenever its
   balance fits within the day's limit, so on a card with an owner everything
   that sets or replaces the PIN, the owner, the card record or the limit needs
   the owner's proof whether or not the card holds anything; replacing the owner
   or the record needs the card to be empty as well (the record, owed no change
   too). A card with no owner and no PIN is open: it holds nothing. A card with no
   owner and a PIN is set up, loaded and numbered under its PIN, rewritten only
   while it is empty and owed nothing, and once used only in a held tap (5.11);
   its owner is claimed under the PIN, slowly, with no change owed (5.10; from
   1.24 with its money on it, up to 1.23 only while it was empty).
   `LOAD_PROOF` asks nothing of the clock: a card that has been shown no block
   header spends its first day on trust (5.7).
9. **The owner's grant reads; it loads nothing.** `ALLOW_LOAD` gives the owner's
   grant for the rest of that tap, held in transient memory, so a new SELECT or
   a reset ends it. It never signs, and since software 1.21 it opens no load (up
   to 1.20 it let `LOAD_PROOF` and `CLEAR_SPENT` through with no PIN). It is read only where a command's own row says so: the log and its
   receipts are read under it (the receipts under it alone, on a card with an
   owner); `GET_INFO` gives the limit on one payment to it; a second payment in a
   tap that has it is not slowed; and with the PIN it lets `LOAD_CHANGE` take
   another key (5.10) (`FOXY-CARD-DAILY-LIMIT.md` 3, rule 9).
10. **A place that is unspent holds its own proof, or zeros (software 1.20).** In
    the store of the mint's proofs a place that is unspent has the proof its piece
    came with, or 96 zero bytes if it came with none, and never another piece's.
    `LOAD_PROOF` and `LOAD_CHANGE` (which keeps the mint's `e` and `s` and its own
    `r`, 1.21) write the place's record before the place's data and its status
    byte, and the status byte stays the last; nothing else writes the store but
    `RESET_CARD`, which zeros it. No payment command writes it (5.9).

### 5.4 Provisioning

- Card: NXP J3R180, dual interface, supplied with known keys.
- Build from the fork's tag; compare the CAP's hash with the one CI made
  before installing.
- After installing: put the card's GlobalPlatform keys to new random ones and
  write them down, or lock the issuer domain. A card left on factory keys can
  have the applet deleted, and the money with it, by anyone with a reader. It can
  also have the tag's applet deleted and another put in its place, carrying another
  address (5.8); rotating the keys closes both.
- The card's key is made on the card at install and never leaves it.
- The card's clock needs no provisioning: there is no key to write. A card
  begins with no header taken, and takes its first from whoever shows it one
  (5.7).
- **A card with no PIN is open, and holds nothing.** Any reader in range can
  give it a PIN and a record of the reader's choosing (`SET_PIN`, `SET_CARD`),
  and then, under that PIN, claim it (`SET_OWNER`), and the holder's own set-up
  then finds it already taken. That is accepted: such a card holds nothing
  (`LOAD_PROOF` answers `6A97` until it has a PIN), and so there is nothing on it
  to steal; and a card with an owner is its owner's, empty or not. The owner is
  claimed only under a PIN given in the tap, by six askings in a row that take
  seconds of the card held to the reader, never in a tap that signed a payment,
  and not while change is owed (5.10; up to 1.23 only of an empty card): a reader cannot claim a card in the instant
  it is near. A card with a PIN and no owner keeps its PIN once it is set up
  (software 1.22, 5.11). A card still leaves the issuer's hands set up (PIN,
  record, then owner: 5.6), so that nobody has to think about it.

### 5.5 How it is tested

- jCardSim: every command in every PIN state and, where it matters, in each
  owner state (none, one); a payment's message against the secret and the swap
  Foxy builds for the same places, byte for byte, for dates of zero and not;
  AUTH's signature never verifying as a spend; torn writes (the upstream
  slot-order tests, extended to the new fields).
- The daily limit, the clock, the owner key and its proofs, and the takeover of
  an emptied card, as `FOXY-CARD-DAILY-LIMIT.md` section 12 sets out. In short:
  a terminal that sends the PIN between four payments of one piece each, with a
  limit of one piece, takes one, however it is repeated, and takes a second only
  after a header a day later; exactly the limit goes and one sat more does
  not, with nothing burned; every command that needs the owner's proof is
  refused with the PIN alone, with a nonce and no proof, with another key's
  signature, with another command's label, for an earlier nonce, for another
  value, and for a proof that has worked once already, and is accepted with the
  right proof and no PIN; a card with an owner refuses `SET_PIN`, `SET_OWNER`,
  `SET_CARD` and the PIN form of `SET_LIMIT` without the proof even when it is
  empty; a wrong proof costs no PIN tries. A test reads the source and checks
  that what is spent today and the note that the places are spent change
  between one `beginTransaction` and its commit, with the signature made before
  it and answered after.
- The tag (software 1.19), in its own class of tests with the three applets installed in the order the chip is given them: the phone's whole
  sequence byte for byte, both files, every offset and length, an unknown file, a read with no file chosen, every instruction that writes, and
  the same bytes from two cards; the switch (on by default; only the owner's proof, and nothing else, flips it; a refusal costs no PIN try;
  off makes the tag's name unselectable and on makes it selectable again; it survives a power cycle; `RESET_CARD` puts it back on; a payment
  works with it off); the instances deleted one by one; and no static field that refers to an object an instance made.
- The mint's proof kept with a piece (software 1.20), in a class of its own: a piece loaded with its proof is given back with it, e then s then r;
  a plain load reads `00`, and three plain pieces still answer three places; a place used again serves only its own proof (a plain piece into a
  freed place reads `00`, a new proof reads the new one); `GET_DLEQ` for a spent place, an empty one, two places in one command (both, mixed, the
  same twice), a place there is not, and before any `SET_CARD`; every refusal of the load, with the word a plain load gives, and nothing written
  after any of them; the load let through by the PIN and by nothing else, in both forms (not the owner's grant, a blocked PIN or a card with no PIN, 1.21); after a payment, no load form without the PIN, and the change loaded by `LOAD_CHANGE` with no PIN, keeping the mint's `e` and `s` and its blinding factor `r` as its proof; a piece with a proof paying with the same signature bytes as one
  without, and a payment, `CLEAR_SPENT` and a refused command writing nothing to the store; `RESET_CARD` zeroing it; the store null after install
  and made by the first `SET_CARD`; `GET_INFO` as it was; and a long random history of loads, batches, payments, clears and power-ups, after
  every step of which every unspent place holds its own record. The source is read for the order (the record before the place's data and its
  status byte, torn states set by hand), for the one allocation (guarded, in `SET_CARD`, none in the constructor, the constructors' arrays
  adding up to the build that installs) and for the methods that name the store (no payment command does).
- A PIN first, the card's own change, and the owner's claim (software 1.21), and
  a holder with no phone (software 1.22): `FOXY-CARD-DAILY-LIMIT.md` section 12
  has the list. In short, a card with no PIN refuses every load, every payment
  command and a claim with `6A97`; any reader, in any tap, writes the card's own
  change under the key bound at the payment, each opening once, and nothing else;
  `LOAD_CHANGE` writes `C = k·Y` as the test works it out on its own and refuses
  every answer that is not the mint's signature on that opening under the bound
  key; a claim takes six askings and begins again on another key or command; a
  card with a PIN and no owner is set up and loaded under its PIN, its history is
  held for ten askings and never read in a paying tap, it keeps its PIN, and a
  used one is rewritten only after a hold; change owed is mended under the PIN and
  the holder's say, or given up.
- BIP-340 vectors, as upstream. In `spec/vectors/`: an owner key and a proof for
  each label, and the transcript.
- On a card, by hand: install, set up (PIN, record, owner), set a limit, load,
  spend, change written back, a day turned, wrong PIN three times and the
  owner's unblock, a tap pulled away at each step.
- Measured on the first card, because the design leans on it: how long one
  SPEND takes over NFC from an iPhone. It decides how many pieces one tap may
  sign and whether a withdrawal of a full card needs more than one tap. Also
  whether the J3R180's ECDSA verifier takes the DER form and the uncompressed
  key, and how long it takes (once for each owner command), how long
  `SET_HEADER` takes (two SHA-256 passes and a few compares), and what the third
  permanent write per spend (what is spent today, with the slot's status byte
  and, on a new day, the window start) does to the chip's write endurance, which
  has not been checked against its data sheet.

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
   try, right or wrong: a wrong proof uses it up. Asking again replaces it. (With
   P1 = 1 it is the form that seals a PIN, on any card; any other P1 is `6A86`.)
2. The proof is an ECDSA signature (P-256, SHA-256, DER) by the owner key over
   `label ‖ nonce (16) ‖ value`, with the label and value of the command:

| Command | Label (ASCII) | Value |
|---|---|---|
| `CHANGE_PIN` | `FoxyCard/change-pin` | the new PIN's bytes |
| `SET_LIMIT` (owner form, `34`) | `FoxyCard/set-limit` | the new limit, 4 bytes, big-endian; or 8, the day's and then the one payment's; or 12 (software 1.16), those and the no-PIN allowance |
| `SET_OWNER` (on a card that has an owner) | `FoxyCard/set-owner` | the new owner key, 65 bytes |
| `SET_CARD` (on a card that has an owner) | `FoxyCard/set-card` | the data sent: unit, refund key, the 65 bytes where the time key was (zeros from a phone of software 1.15; the card does not read them), mint length, mint |
| `ALLOW_LOAD` | `FoxyCard/load` | nothing |
| `LOCK_CARD` | `FoxyCard/lock` | nothing |
| `RESET_CARD` (software 1.17) | `FoxyCard/reset` | nothing; with P1 = `01` (software 1.22), the opening's number (1) |
| `SET_TAG` (software 1.19) | `FoxyCard/tag` | one byte: `00` tag on, `01` tag off |

So a proof for one command, or one value, is no proof for another, and an old or
used proof fails. The data of an owner's command is `proof length (1) ‖ proof
(DER) ‖ the value` (for `ALLOW_LOAD`, `LOCK_CARD` and `RESET_CARD`'s reset, the proof alone; for `RESET_CARD`'s let-go and `SET_TAG`, the proof and then the one byte it was made over), so the
proof sits first and the value after it is exactly what the proof was made over.

**Refusal.** `6A91` when no nonce was asked for in this tap, or the signature is
not the owner's. A wrong proof costs no PIN tries, changes nothing and does not
end the PIN session. `6A90` when the card has no owner. `CHANGE_PIN`, the owner's
`SET_LIMIT`, `SET_OWNER`, `SET_CARD`, `ALLOW_LOAD`, `SET_TAG` and, on a card that is
not locked, `RESET_CARD` need no PIN and no old PIN:
the owner's phone does not know it, and the cards that most need an owner's
`CHANGE_PIN` are the blocked and the forgotten, where there is no old PIN to
give. `LOCK_CARD` needs a PIN that is set and verified as well; a card with no
PIN, or none verified in this tap, answers `6982`. The phone locks nothing:
`LOCK_CARD` is for a tool, and the phone's gate refuses to carry instruction
`50`.

**A locked card still pays.** `LOCK_CARD` freezes the card's settings and every
write, and nothing else. Under its PIN, and up to its no-PIN allowance without
it, a locked card pays as before, held to its limits, and it still takes its own
change (`LOAD_CHANGE`: receiving what it is owed changes no setting); its limit,
its allowance and its PIN can no longer be changed (every other write is `6986`),
nor its places freed or loaded. Only `RESET_CARD`, with the
owner's proof and the PIN, takes a locked card back; its let-go form (P1 = 1,
5.10) works on a locked card too, with the proof and the PIN. A lock is not a way to make a
lost card safe: an owner who wants a card that pays nothing sets its allowance to
nothing before it is lost, and blocks its PIN or resets it once it is found.

**5.6a. RESET_CARD (software 1.17).** An owner can factory-reset a card so that
another person can set it up. `51`, P1 = `00`, P2 = `AD` (any other is `6B00`,
as `LOCK_CARD`'s `DE`), the data the owner's proof over `FoxyCard/reset` and no
value, and nothing after it (`6700`). It is refused in this order: the bytes
(`6B00`; a P1 of `01` is the let-go form, 5.10); no owner (`6A90`); the proof
(`6A91`); a LOCKED card whose PIN has not been given in this tap (`6982`: a
locked card resets for the owner's proof and the PIN, and a locked card whose PIN
is blocked cannot be reset); anything unspent on the card, or change owed to it
(`6A8D`, as `SET_CARD` refuses a record on a card that is not empty; software
1.21: an opening's blinding factor never leaves the card, so a reset would throw
the change away for good). A blocked PIN on a card that is not locked is no bar:
the owner is resetting, not unblocking.

The card then reads as a card out of its packet: no PIN (`GET_INFO` PIN state
0, three tries), no owner, not locked, no record, no limits, no allowance, no
window or spending, no places in use, no log, no receipts, no openings of change
or their keys, no kept signature, its NFC tag on (5.8), and **a new key pair**: the card's own key
(`GET_PUBKEY`) and the PIN key (`GET_NONCE` P1 = 1, whose signature by the card
key is made again at the first asking). The old key would have tied one
owner's payments to the next one's. What it keeps is its clock (the time, and
the hash of the last block header it was shown; the ratchet's difficulty goes
to nothing, as `SET_CARD` does it) and its software. The old owner's proofs
fail (`6A90`), and set-up (`SET_PIN`, `VERIFY_PIN`, `SET_CARD`, the claim) is
as on a new card, in a tap that has signed no payment (5.10). No change is owed
to a card that is reset: the reset waits until every opening's piece is written
back (`LOAD_CHANGE`) or the opening is given up (the let-go form, 5.10), since the
pieces it would have come to are locked to the key that is about to go.

The wipe is one transaction of four bytes (the lock, the PIN state, the owner,
the NFC tag's switch), after the keys are drawn, with everything else written
outside any (a chip's commit buffer holds about a dozen writes). A card taken
away before it has the owner still and is reset again; one taken away after it
is a new card whose old PIN's bytes and tries may not be cleared yet, which
nothing reads with no PIN set and `SET_PIN` replaces.

**Who the owner is.** Whoever's words derived the key on the card: for a holder
with a phone, their own; for a holder without one, whoever's phone the holder lets
claim it, or nobody (5.11). That phone is a trustee who, with the card in hand, can do anything, and
without it, nothing. Set-up says so in as many words (`FOXY-CARD-SCREENS.md`
B3). An owner can be replaced only on a card with nothing unspent, and only with
the old owner's proof. A card whose owner has lost their words cannot be taken
over, empty or not.

**Setting a card up is one tap**, in this order: `SET_PIN`, `VERIFY_PIN`,
`SET_CARD` (the record), `SET_OWNER` last, asked for six times in a row while the
card answers `6A98` and the screen says KEEP HOLDING (software 1.21, 5.10), and
then the no-PIN allowance by the owner's proof (5.7a). The owner goes in last so
that no step needs a proof, and the card is the PIN's, and can be done again, at
every point before it: a set-up cut off anywhere is finished by the next, with
`VERIFY_PIN` first once the card has its PIN. It asks for no daily limit. The PIN
is given again at every load, since every load wants it (5.3, rule 5). The card
pays from the next tap: a tap that claimed an owner signs no payment. A card for a
holder with no phone is set up with no owner (5.11).

If the card in hand turns out to have no usable ECDSA verifier (not yet
measured; the J3R180 is expected to have one), the earlier draft's hash of an
owner secret stands in for the owner's proof.

### 5.7 The daily limit, the clock, and what they bound

**Foxy's app does not set a daily limit at launch;** the card keeps the limit and
its clock of block headers, and both stay available to a later version of the app.
The whole text is `FOXY-CARD-DAILY-LIMIT.md`, sections 5 and 6. A card is cash.
It holds what is put on it. The daily limit caps how much of it a terminal that
has been handed the PIN can take in a day, honest or built to cheat: the card
counts its day by Bitcoin block headers, which it believes for their work, and a
terminal cannot make one for nothing. It caps only where the owner has set a
limit, and a new card has none.

**The limit.** A number of sats in the card's permanent memory: the most the card
will sign for in one day. `GET_INFO` bytes 12 to 15 and `GET_CARD` bytes 3 to 6
carry it. 0 is no limit, and a new card has 0: set-up does not ask for one and
nothing suggests a figure. The owner sets one later from CHANGE LIMIT, to any
amount, and removes it the same way. `VERIFY_PIN`, a new SELECT, `SET_CARD` and
loading pieces do not touch it; `RESET_CARD` empties the whole record, this
included.

**The day.** The card keeps it, from the newest Bitcoin block header it has been
shown. `SET_HEADER` carries a header: the 80 bytes as the network carries them.
The card hashes them twice with SHA-256, and the hash, read as a little-endian
number, must be at or under the target the header's own `bits` name (the proof
of work). The target must also be at or under a floor built into the applet and,
once the card has taken a header, at or under four times the target of the
hardest it has taken, which is to say the header must show at least a quarter of
that work. A header that does not is `6A93`; a `bits` no header could carry is
`6A80`; any length but 80 is `6700`. A header whose time is later than `now`
moves `now` to it; an older or repeated one changes nothing (`9000`). No key, no
PIN, no owner and no state of the card refuses a good header. Setting the limit
begins a window at `now` with nothing spent in it. At a payment's signature, when
the limit is not 0: if `now` is a day (86,400 seconds) or more past the window's
start, the window is new and begins at `now`; a payment that would take what is
spent today past the limit is refused (`6A8F`) with nothing signed or burned (the
refusal is written in the card's log); otherwise the window start (if new), what
is spent today and the note that the places are spent change in one transaction,
after the signature is made and before it is answered. Nothing else begins a
window: not `VERIFY_PIN`, not a new SELECT, not a load (`RESET_CARD` begins none, it
zeroes the window with the rest of the record). A limit of 0
skips all of it and needs no clock. `now` only ever moves forward, and nothing
sets it back.

A card that has been shown no header (`now` is 0) spends its first day on trust.
It may be loaded and given a limit; its window has no start and cannot end, so
what leaves the card counts up to the limit and is then refused (`6A8F`) until a
header arrives; and the first header taken begins the window at its own time.

**It counts what leaves the card, not the price paid.** From software 1.12 a
payment's change is the card's own (`SPEND_ALL_CHANGE`): outputs the card
builds and locks to its own key, which no terminal can take, so the day is
charged the pieces less that change. Change a terminal writes back by other
means the card cannot check, and gives the day nothing back. A payment of 300
sats made with one piece of 1,000 and 700 of the card's own change costs the
day 300; before 1.12 it cost 1,000, and a till picks pieces as near the price
as it can either way (8.1). A window is a
fixed day from its start, so a terminal that straddles one boundary can take up
to two days' limit in a short span. That is the bound, and it is written down
and not hidden.

**The time.** The card believes a header for the work it would cost to make, and
for nothing else: no key, no signer, no phone's word. Two numbers of its own say
how much work is enough. The **floor** is built into the applet: `bits`
`0x17087BC0`, four times the target of the network's blocks when this version
was written, so a quarter of a block's work then. The **quarter** is a fourth of the hardest
header the card has itself taken: it climbs with the network, and nothing
lowers it. The floor matters for the first header only. A terminal built to
cheat cannot begin another day by telling the card it is later. It must bring a
header dated a day on from the window's start, which is a real block when the
world has made one, or a forgery that costs a quarter of a block's work (of the
blocks of this version's time, on a card that has taken none). The card's time is a
block's, and not the second: a block's time can lead the true time by up to two
hours, and the clock moves a block at a time, and only when someone shows the
card a header. The numbers, and the reasons for them, are in
`FOXY-CARD-DAILY-LIMIT.md` 5.2. `TELL_TIME` gives the card the terminal's own
clock as a note for its receipts and its log, and the card trusts it for
nothing (there, 5.3).

**What it bounds.**
- A terminal that has been handed the PIN can take at most one day's limit in a
  day, and up to two across a window's boundary, honest or not. That is not the
  price of this payment and not what the holder agreed to: the card cannot tell a
  holder's request from a hostile terminal's.
- The PIN alone protects a lost card whose PIN nobody knows: a wrong PIN opens
  nothing, and three block the card.
- On a card with an owner, changing the PIN, the limit, the owner or the record,
  and unblocking the card need the owner's phone, or the twelve words on a new
  phone. Adding funds needs the PIN, on any phone (5.3, rule 5).

**What it costs.**
- A payment that would take more out of the card than what is left of today
  cannot be made today. Since software 1.12 a large piece pays a small price with
  the card's own change, which is not charged: with one piece of 1,000 sats and
  500 left, 10 sats are paid and 990 come back. (Before 1.12 the whole piece was
  charged, and not even 10 sats could be paid until the day turned or the owner's
  phone changed the limit.)
- A payment can be refused for the limit while the card holds enough: the till
  says OVER THE CARD'S DAILY LIMIT, not NOT ENOUGH ON THE CARD.
- Every payment writes one more thing to permanent memory.
- The owner's phone taking money off the card lifts the limit and puts it back in
  the same tap. Putting it back starts a new window with nothing spent, so a
  withdrawal gives the day back its whole limit: one extra day's limit, at the
  moment the owner is holding the card.
- A card that is shown no newer block does not end its day. One whose network
  falls to under a quarter of the hardest header it has taken stops taking
  headers. Both fail closed, and the owner can give the card its limit again.
- If the owner loses the twelve words, the PIN and the limit on the card
  can never be changed, and a blocked card can never be unblocked.

What it does not do is listed in section 10.

### 5.7a The no-PIN allowance (software 1.16), and a PIN first (1.21)

The whole text is `FOXY-CARD-DAILY-LIMIT.md`, section 6f.

**Every card has a PIN before anything goes on it (software 1.21).** A card with
no PIN refuses `LOAD_PROOF`, `CLEAR_SPENT`, `LOAD_CHANGE`, every payment command,
an owner's claim and the let-go with `6A97`, before anything else of the command
is looked at (the let-go looks at its number first: `6A83` for a number past 7;
the load commands look at the lock first: `6986`); `SET_PIN` stays
open on a card with no owner and nothing on it, so a card's first steps are
`SET_PIN`, `VERIFY_PIN` and the record, then the claim, or for a holder with no
phone the first load (5.11). `GET_INFO` says it as before (PIN state 0 none, 1
set, 2 blocked; no capability bit changes). From 1.16 to 1.20 a card could be set
up with a record and an owner and no `SET_PIN`, and then paid without one and was
loaded and freed by whoever held it, and the owner's phone gave it a PIN later
(ADD PIN); that card cannot be made now.

**A card has a third limit, the NO-PIN ALLOWANCE:** how many sats it will sign for
without the PIN, between two givings of the PIN. The holder chooses it at set-up,
after the PIN, in their own money (two dollars unless they choose another
figure), and the phone gives the card the sats it comes to; the card keeps sats
and nothing else. Four bytes in the record (`GET_INFO` with P1 = 1 says them, at
bytes 34 to 37, to anyone, with what has been signed for with no PIN since the PIN
was last given at 38 to 41); 0 is none, and every payment then needs the PIN; a
PIN just set leaves it at 0. Only payments signed with no PIN count against it,
each as **the whole of its pieces**, the card's own change included (1.21: a reader
with no PIN that takes almost all of a large piece as change and never swaps has
put the whole piece out of reach; up to 1.20 a payment counted as what left the
card for good); one made with the PIN counts for nothing here. The owner sets it
with `SET_LIMIT` (`34`) in its twelve-byte form; on a card with no owner the PIN
form (`33`) sets it, with eight bytes (5.11).

**It renews when the PIN is given (software 1.21).** A `VERIFY_PIN` that goes
through sets what has been signed for with no PIN to 0; no day does, and no block
header. A new number for the allowance sets it to 0 as well. So a card in a
stranger's hands gives up one allowance at most, however long they keep it. (From
1.16 to 1.20 the allowance shared the day's window with the daily limit: a day
turning took both counts to 0, a card that had seen no block header counted it
from set-up until one arrived, and a phone that showed the card no headers left
the allowance spent until the owner set it again.)

**At the signature.** `SPEND_ALL_BEGIN` asks for no PIN (but for a sum that wraps
past four bytes, `6A94`). `SPEND_ALL_SIGN` signs a payment when the PIN was given
in this tap, or when what has been signed for with no PIN since the PIN was last
given, plus the whole of this payment's pieces, is no more than the allowance.
Otherwise it answers `6A94` and gives the payment up: **the PIN is asked for the
whole payment, never for part of it**, and a till reads the answer, asks for the
PIN, and taps again. The PIN is asked before the limits and writes nothing in the
log, so that a reader with no PIN cannot fill it. A blocked card has no allowance.
The daily limit and the limit on one payment hold a payment made with no PIN as
they hold any other.

**The log** marks a tap in which a payment was signed with no PIN (bit 3 of its
flags), so that the holder's phone can say so.

A card of software 1.12 to 1.15 keeps asking for its PIN at every payment, and a
phone must go on working with them.

### 5.8 The card as an NFC tag (software 1.19)

A phone with its screen on reads NFC tags by itself and offers to open the web
address a tag carries. From software 1.19 every card also answers as an NFC Forum
Type 4 Tag (mapping version 2.0), read only, carrying one address:

    https://getfoxy.io/card

so that a phone with Foxy opens the app and a phone without it opens that page.

**The same on every card.** There is nothing per card in the address and there never
will be: an address that named the card, its key, its owner or its balance would be an
identifier that anyone with a phone could read with no PIN. Two cards answer a reader
in the same bytes, and a card with money on it answers as one with none. What the tag
tells a reader is that this is a card made for Foxy, and nothing else
(`SECURITY-MODEL.md`, threat 15).

**The applet.** A third applet of the package, `Tag` (class AID `F0 46 4F 58 59 43 41
52 44 54`), listed after the opener and the card's applet, and instantiated on the card
under the NFC Forum's own name, `D2 76 00 00 85 01 01`, given as the instance AID at
install, since phones select the tag application by that name:

    gp --create D2760000850101 --package F0464F585943415244 --applet F0464F58594341524454

It keeps no secret and no state of its own but the file chosen (one byte of transient
memory, cleared on deselect and when the application is selected), and it allocates
nothing else. It answers exactly this, with class `00` (any other class is `6E00`):

| Command | Bytes | Answer |
|---|---|---|
| SELECT by name | `00 A4 04 00 07 D2 76 00 00 85 01 01 [00]` | `9000`, no data (the runtime's); `6999` when the tag is off |
| SELECT file | `00 A4 00 0C 02 E1 03`, or `... E1 04` | `9000`; any other file `6A82`; other P1 or P2 `6B00`; Lc not 2 `6700` |
| READ BINARY | `00 B0 <offset hi> <offset lo> <Le>` | the bytes of the file chosen from that offset, as many as asked and the file holds (an Le past the end is answered with what there is); an offset at or past the end `6B00`; no file chosen `6986` |
| UPDATE BINARY (`D6`), and the other instructions that write (`0E D0 D7 DA DB DC DD E2`) | | `6982` |
| any other instruction | | `6D00` |

The capability container (file `E103`, 15 bytes):

    00 0F 20 00 3B 00 34 04 06 E1 04 00 16 00 FF

its length (15), mapping version 2.0, MLe 59, MLc 52, and one NDEF file control TLV:
file `E104`, at most 22 bytes, read access free, write access never. The NDEF file
(`E104`, 22 bytes):

    00 14  D1 01 10 55 04  67 65 74 66 6F 78 79 2E 69 6F 2F 63 61 72 64

its message's length (20), then one short, well-known record of type `U` (a URI) whose
payload is the prefix code `04` (`https://`) and the 15 ASCII bytes of `getfoxy.io/card`.
A phone reads it by selecting the name, selecting `E103` and reading 15, selecting
`E104`, reading 2 (the length) and reading 20 from offset 2.

**The switch.** The card's owner can switch the tag off, and on again: a card kept in a
phone's wallet case would otherwise raise the offer every time the screen wakes. The
switch is one persistent byte of the card's applet. It is on for a new card, for a card
with no owner and for a card just reset (`RESET_CARD` puts it back on in its last
transaction, 5.6a), and off only when its owner has said so. The command is `SET_TAG`:

| Field | Value |
|---|---|
| CLA, INS | `B0 52` |
| P1 | `00` tag on, `01` tag off |
| P2 | `00` |
| Data | proof length (1), the owner's proof (DER), and one byte, the same as P1 |
| Proof | ECDSA (P-256, SHA-256, DER) by the owner's key over `FoxyCard/tag` (12 bytes, `46 6F 78 79 43 61 72 64 2F 74 61 67`), the 16-byte nonce the card gave, and that one byte |

It is refused in this order, with the words the neighbouring commands use: `6B00` for a
P1 or P2 not those, before anything else (the nonce is left unspent); `6986` on a locked
card; `6A90` with no owner; `6A91` when no nonce was asked for in this tap or the proof is
not the owner's for this label, this nonce and this byte (it costs no PIN try, and the
nonce is spent); `6700` for anything but one byte after the proof; `6B00` for a byte that is
not P1. Asking for the state the tag has answers `9000` and writes nothing. No PIN is asked
and none is spent: the owner's phone does not know it, and a terminal that has been handed
the PIN must not be able to turn the tag off or on. It is allowed with money on the card.

**GET_INFO.** The tag's state is said by a form of its own, P1 = 2: the 42 bytes that P1 = 1
answers, and then byte 42, `00` when the tag is on and `01` when it is off. It is said to
anyone (the tag shows as much to any reader). P1 = 0 is the thirty bytes and P1 = 1 the forty-two
bytes they were in software 1.18, whatever the tag's state: a wallet written for 1.18 asks with
P1 = 1 and takes the answer as the long form only when it is exactly 42 bytes, so growing that
answer would have made it misread a 1.19 card (no limit on one payment, no allowance). Any
other P1 is answered as it was, with the thirty bytes. Cards of software 1.18 and earlier have
no tag: they answer `6D00` to `52`, and give the thirty bytes to P1 = 2.

**How the tag knows.** At each selection the tag asks the card's applet whether it is on,
through a shareable interface (`TagSwitch`, one method, `tagIsOn`) that the card's applet
hands to any caller. The card's applet is looked up by its name (`JCSystem.lookupAID`,
`F0 46 4F 58 59 43 41 52 44 01`) and its interface asked for
(`getAppletShareableInterfaceObject`) then, and nothing of either is kept. When the tag is
off the tag's `select` answers false, so the card refuses the tag application's name
(`6999`) and a phone sees no tag. The card applet's own name is selectable whatever the
switch says, and a payment is made as ever. If the card's applet is not there the tag is
on. The interface is not exported: the two applets are of one package, and so of one
context.

**Deleting.** No static field of the package refers to an object an applet instance made,
which the chip would refuse to delete (the statics are constants made when the package is
loaded; the tag holds no reference to the card's applet). The instances are deleted one by
one, the tag (`D2760000850101`), the card's applet, the opener, and then the package
(`HARDWARE_DEPLOYMENT.md`).

**What a phone needs to know to speak to a card of software 1.19.** `SELECT` answers
`01 13`. `GET_INFO` with P1 = 1 is the 42 bytes it was; with P1 = 2 it is 43, the last `00` (tag on) or `01` (off). To switch the
tag: `GET_NONCE`, then `B0 52 <p> 00 <Lc> <proof length> <proof> <p>` with `p` = `00` or `01`,
the proof being the owner's signature over `"FoxyCard/tag" ‖ nonce ‖ <p>`, in the same tap as
the nonce; a phone's gate carries instruction `52`, and its signer the label `tag`.

### 5.9 The mint's proof, kept with each piece (software 1.20)

Up to software 1.19 the card kept for each piece only what it needs to pay with: the
keyset, the amount, the nonce, `C` and the date. A phone that read the card could ask the
mint whether a piece was spent. It could not tell whether the piece was real: a made-up `C`
looks like any other until the mint is asked to take it. From software 1.20 the card also
keeps, for each piece, the mint's NUT-12 proof as a later holder needs it:

| Field | Size | |
|---|---|---|
| e | 32 | the proof's challenge |
| s | 32 | its response |
| r | 32 | the blinding factor the piece was made with |

96 bytes. **The card does not check the proof and never uses it.** It keeps what it is given
and gives it back. Phones read it and check it. Paying is untouched: the secret the card
builds and the message it signs are what they were, and no payment command writes the proof.

**The store.** 128 records of 96 bytes, 12,288 in all, the record of place k at k * 96, as
`e ‖ s ‖ r`. A record of 96 zero bytes means "no proof": the piece came with none.

**It is reserved after install, by the first `SET_CARD`.** The chip will not install an applet
whose constructor asks for this much more (a build whose constructor asked for 300 bytes
more installed, and one that asked for 12,288 more did not), and it gave the same bytes
asked for by a command after the install (`FOXY-CARD-HARDWARE.md`). So the store is not
there at install. The first `SET_CARD` that goes through reserves it, once, before the record
is written and outside any transaction. If the card refuses the room, that `SET_CARD` goes
on without it and the card works as a card with no room for proofs: the load answers `00` for
the proof, `GET_DLEQ` answers `00`, and nothing else is different. Every use of the store is
safe while it is not there, and a later `SET_CARD` asks again. No simulator has the chip's
limit, so tests pin the rule: the store is null after install, no constructor names it, the
constructors' arrays come to the figure of the build that installs, and the one allocation is
the guarded one in `SET_CARD`.

**The rule.** A place that is unspent holds in the store its own proof, or 96 zero bytes, and
never another piece's. `LOAD_PROOF` keeps it by order. It writes the place's record, the
piece's proof or zeros for a piece that came with none (written only if the record is not
zero already), before the place's data and its status byte, and the status byte stays the last write
and the commit (D14). A card taken away in between has an empty place with a record beside
it, which nothing serves, and the next piece into the place writes its own record over it.
`LOAD_CHANGE` keeps it by the same order (5.10). `CLEAR_SPENT` and the payment commands do not touch the store, so a freed place keeps the
record it had until the next piece comes. `RESET_CARD` fills the store with zeros after the
places are freed, outside any transaction: a card taken away in the middle still has its
owner, who may reset it again.

**`LOAD_PROOF` with P1 = 1.** One piece and its proof. The data is exactly 81 + 96 = 177
bytes: the piece as a plain load has it, then `e ‖ s ‖ r`. Any other length is `6700`. A
proof of 96 zero bytes is `6A80`, since zeros are what "no proof" is kept as; the card looks
at the proof no further. Everything else is exactly a load of one piece: the same checks in
the same order, the same words, the same entry in the log, the same authority (the PIN given
in this tap and nothing else: since software 1.21 not the owner's grant and not the tap after
a payment; the card's own change goes on by `LOAD_CHANGE`, 5.10), and a piece that is the
card's own change lets its opening go. The
answer is two bytes: the place, then `01` where the proof
was kept and `00` where the card has no store. A P1 other than 1 is what it was in 1.19: one
to three pieces of 81 bytes, an answer of one place for each, and each piece stored that way
has its record made zero. It is one piece to a command because a piece and its proof are 177
bytes and a short APDU carries 255; two would not fit, and extended length is not something
every reader and phone will carry.

**`GET_DLEQ` (`1A`).** P1 = a place (0 to 127), P2 = a second place, or `FF` for none. No
data, no PIN, any state of the card. For each place asked, in the order asked, the answer is
`01` and the 96 bytes if the place is unspent and its record is not zero, and otherwise the
single byte `00`: the place is empty or spent, or holds a piece that came with no proof, or
the card has no store. The same place twice is answered twice. At most 194 bytes. `6A83` for
a P1 over 127, or a P2 over 127 and not `FF`, before anything is answered. It never gives the
proof of a spent piece.

**What a reader does.** The card gives `e`, `s` and `r`; the reader has the rest, from
`GET_PIECES` (keyset, amount, nonce, `C`, date), `GET_PUBKEY` and `GET_CARD` (the card's key
and refund key) and the mint's keys. For a place `GET_DLEQ` answers:

1. Rebuild the piece's secret, the NUT-10 text the card signs the hash of
   (`FOXY-CARD-DAILY-LIMIT.md` 6c).
2. `Y = hash_to_curve(secret)` (NUT-00).
3. `K` is the mint's public key for the piece's amount in the piece's keyset (NUT-01).
4. `C' = C + r*K`, and `B' = Y + r*G`.
5. Check the proof as NUT-12 has it: `R1 = s*G - e*K`, `R2 = s*B' - e*C'`, and `e` must be the
   hash NUT-12 names of `R1`, `R2`, `K` and `C'`.

**What it shows, and what it does not.**

- A piece whose proof checks is real: the mint's key for that amount signed this secret.
  Nobody without that key can make a proof that checks, so a piece with a made-up `C` fails,
  and so does a copy of a real piece from another card, since its secret names that card's
  key and not this one's.
- It does not show that the piece is unspent. The mint decides that, and the card cannot.
- What "real" rests on is the mint's keys the reader holds and the check the reader makes.
  It does not rest on the card, which checks nothing, or on whoever loaded the card.
- A `00` is "not shown real", and not "fake". A piece put on plain, or on a card of earlier
  software, cannot be checked this way, and the reader decides what that means to it.

**Privacy.** The proof carries `r`. The mint made its signature for `B'` and answered with
`C'`; given `r`, whoever has the mint's record of that issue can recompute `B'` and `C'` from
the piece and tie the piece to the request that made it. That is so of any NUT-12 token that
carries `r`, and a reader that hands `r` to the mint lets the mint make the tie. The card
gives `r`, with no PIN, to any reader, for every unspent piece that has a proof. That
includes a piece of the card's own change once it is on the card: `LOAD_CHANGE` keeps the
mint's `e` and `s` and the card's own `r` as its proof (5.10). While the change is owed its
blinding factor never leaves the card (since 1.21; up to 1.20 `GET_CHANGE` gave it to any
reader). A reader checks the proof on its own side and sends it nowhere.

**What a phone needs to know to speak to a card of software 1.20.** `SELECT` answers `01 14` (1.21: `01 15`, 5.10).
`GET_INFO` is the 30, 42 and 43 bytes it was, byte for byte but for the version, and nothing
in it says whether the card has its store: the answer to a load with P1 = 1 does, and so does
`GET_DLEQ`. To load a piece with its proof: `B0 30 01 00 B1 <piece (81)> <e> <s> <r>`; the
answer is `<place> 01`. To read: `B0 1A <place> FF 00` for one place, or the two places in P1
and P2. A phone's gate has to carry instruction `1A`. `spec/vectors/transcript.json` has both
commands as commands and answers.

### 5.10 The card's own change, no special tap after a payment, and the owner's claim (software 1.21)

Up to software 1.20 three things were open that should not have been. The tap after a
payment let whatever reader came next put any piece on the card and free its spent places,
with no PIN, until a load used the grant up. `GET_CHANGE` gave the blinding factor of every
opening of the card's own change to any reader, so anybody who saw the mint's answer could
finish the piece. And a card with no owner was claimed by one `SET_OWNER`, by any reader, in
the instant the card was near it. 1.21 closes the three: no tap after a payment opens
anything, the card unblinds its own change and binds it to the mint's key named at the
payment, and a claim is slow and under the PIN. 1.22 adds the two ways out of change owed
that the binding needs.

**The change is bound at the payment.** `SPEND_ALL_CHANGE` carries the amount and the mint's
key for that amount in the payment's keyset (37 bytes: 4, then 33 compressed; up to 1.20 the
amount alone). The card keeps the key with the opening, from the moment the opening is made,
in a store of 264 bytes the first `SET_CARD` reserves (a card that was refused the room makes
no change: `6A84`). It builds the output itself (its own key, the payment's date and refund
key, `SIG_ALL`; a fresh nonce and blinding factor), keeps the opening with its blinded message
`B_`, and answers `B_`.

**`GET_CHANGE` (`19`) gives the blinded half.** Each record is the opening's number (1), its
amount (4), keyset (8) and date (4), its blinded message `B_` (33, compressed) and the mint's
key it was bound to (33, compressed): 83 bytes, three to a page after a count, to any reader.
Never the nonce, never the blinding factor. The phone that owes the change matches `B_` with
the one `SPEND_ALL_CHANGE` answered and the mint signed, and the holder's phone asks the mint
for change a till never handed over (NUT-09 restore, from `B_`). The key lets a phone see,
before it asks, whether the mint's answer can be taken under it. `GET_INFO`'s byte 29 says
whether any change is owed, to anyone.

**`LOAD_CHANGE` (`53`): the card unblinds its own change.** P1 is the opening's number, as
`GET_CHANGE` gives it. The data is what the mint answered for the opening's blinded message
`B_` (194 bytes):

| Field | Size | |
|---|---|---|
| `C_` | 65 | the mint's blinded signature, uncompressed (`04 ‖ x ‖ y`) |
| `K` | 65 | the mint's public key for the opening's amount, uncompressed |
| `e` | 32 | the mint's NUT-12 proof: its challenge |
| `s` | 32 | its response |

`K` must be the key the opening was bound to (its x, and its y's parity), or the answer is
refused before any arithmetic (another key: below). The card checks the proof: with its own
`B_`, `R1 = s·G − e·K` and `R2 = s·B_ − e·C_`, and `e` must be SHA-256 of `R1`, `R2`, `K` and
`C_`, each uncompressed, as lowercase hex text, end to end (NUT-12). Then it takes its own
blinding factor off, `C = C_ − r·K`, and writes the piece as `LOAD_PROOF` writes one: the
opening's keyset, amount, nonce and date and that `C`, its text, its record in the store of
the mint's proofs (`e`, `s` and the card's own `r`, so that `GET_DLEQ` answers for it as for
any piece, 5.9), its data and its status byte last. The log has the piece put on, and the
opening is let go. The answer is 83 bytes: the place (1), `01` where the proof was kept and
`00` where the card has no room for proofs (1), and the piece as `GET_PROOF` gives it after its
status byte (81), so that the phone that wrote it has the piece without another command.

Who may: any reader, with no PIN, in any tap, on a locked card or one whose PIN is blocked,
under the key the opening was bound to. Receiving what it is owed changes no setting. No tap
is the tap after a payment: the change of an earlier payment goes on as well as the last's,
in the paying tap or any later one.

In order, it refuses: `6A97` with no PIN; `6A83` for a P1 past 7; `6A88` for an opening that
is not waiting for its piece; `6A94` for one whose piece is on the card already (a load torn
after the piece and before the opening was let go), and the opening is let go; `6A84` with no
place free; `6700` for any length but 194; `6A80` for a `K` not in the `04` form; for a `K`
other than the bound one, `6A80` with no PIN given in this tap, `6985` in a time in the field
in which a payment was signed, and `6982` with no owner's grant (a card with an owner) or no
held history (a card with none); `6A80` for a point that is not on the curve, or a proof that
does not hold. Nothing is written before the piece itself.

The card does the arithmetic with the chip's own operations: the point `B_` from its kept
`x` and parity by the RSA of the change output's hash to the curve (a cube and a root mod
p), two scalar multiples by the key agreement, and three additions by the generic mapping
with its generator set to `G`, `K` or `B_`. By the figures measured on the chip for each
of those, with the hex of four points in bytecode, that is about a second through a contact
reader, to be measured. If that is too long for a phone, a later form could take `B_`'s `y`
from the phone, which saves the cube and the root.

What the proof shows: that `C_` is `B_` signed by the private key behind `K`, so a `C_` for
another opening, or a corrupted one, is refused. What it cannot show is that `K` is the
mint's. The binding is what keeps a stranger out: a reader that makes up a key after the
payment is refused (`6A80`), whatever proof it brings. A till that binds an opening to a key
of its own, or to nonsense, at the payment has made change the mint's real answer cannot fit;
up to 1.21 nothing could ever load it, and the change owed kept the card from being reset or
written again. 1.22 gives the holder two ways out:

- **Mended.** A `K` other than the bound one is taken under the PIN given in this tap, in a
  time in the field that has signed no payment, with the holder's own say in it: on a card
  with an owner the owner's grant (`ALLOW_LOAD`), on one with none the history held for
  (5.11). The proof is checked under the `K` given. The holder's phone brings the mint's
  answer under the real key, and the money is back. The PIN alone is not that say: a till
  has the PIN, and with it alone could bind an earlier payment's opening, still waiting, to
  a key of its own and be paid in the same tap. What is left: on a card with no owner a
  reader that keeps the PIN can hold a tap of its own and name a key of its own, with a proof
  that holds for it, for any opening still waiting; on a card with an owner, the owner's
  phone can. That spoils the holder's change, takes nothing, and leaves a piece the mint will
  not take, which a phone that checks the mint's proof against the mint's keys sees.
- **Given up.** `RESET_CARD` with P1 = `01` and P2 the opening's number lets that one opening
  go with its piece never loaded, and resets nothing. With an owner: the owner's proof over
  `FoxyCard/reset`, the nonce and the number, then the number (and on a locked card the PIN
  given in this tap). With no owner: the number alone, under the PIN given in this tap and the
  history held for in it. The log marks the tap (bit 4 of its flags). The money of that
  opening is gone; the card cannot tell an opening nothing can load from one whose piece is
  still to come, so it is never the card's choice. In order: `6A83` for a number past 7;
  with an owner `6A91`, then `6982` on a locked card without the PIN; with no owner `6A97`,
  `6982` (the PIN), `6985` (a tap that paid), `6982` (no hold); `6700`; `6B00` for a number
  that is not P2; `6A88` for an opening that waits for nothing; `6A94` for one whose piece is
  on the card already, which is let go with nothing written in the log. Answers `9000` and
  nothing.

**Change owed holds up a reset and a new record.** `SET_CARD` and `RESET_CARD` answer `6A8D`
while change is owed, and on a card with no owner so do `SET_PIN` and the PIN form of
`SET_LIMIT`: the refund key and the mint stay as the change's secret was built with them,
and no blinding factor is thrown away.

**The owner's claim takes time, under the PIN.** `SET_OWNER` on a card with no owner wants a
PIN set (`6A97`) and given in this tap (`6982`), no payment signed in this time in the field
(`6985`, even after a reset by the old owner's proof), and no change owed (`6A8D`; up to
1.23 nothing unspent: from 1.24 the pieces on the card stay where they are). It is
asked for six times in a row with the same key: the first five are answered `6A98`, keep
holding, with no data, each after a signature's work of the card's own, thrown away, as a
payment's wait does it (about half a second each on the chip, some three seconds in all); the
sixth sets the owner and answers `9000` and nothing. `6A98` is not `9000`, so a phone that asks
once never takes it for done. Another key, or any other command between, a `SELECT` among
them, begins the count again; a `SET_OWNER` refused for its own data neither counts nor ends
it, and the count is gone at a deselect. After a claim, no payment is signed in that time in
the field (`6985`). The phone sends `SET_OWNER` again while the card answers `6A98`, and says
KEEP HOLDING, counting up as a payment's wait does. Replacing an owner, with the old owner's
proof, is made at once, as before.

**What a phone needs to know to speak to a card of software 1.21.** `SELECT` answers `01 15`;
`GET_INFO` is 1.20's but for the version and byte 29. Set-up: `SET_PIN`, `VERIFY_PIN`,
`SET_CARD`, then `SET_OWNER` until it answers `9000`, then the allowance by `34`; the first
payment in the next tap. Change: `SPEND_ALL_CHANGE` with the amount and the mint's key, then,
in that tap or any later one, with no PIN, `B0 53 <opening> 00 C2 <C_ (65)> <K (65)> <e> <s>`,
the answer `<place> <01|00> <piece (81)>`. Loads are under the PIN given in the tap, on every
phone. A phone's gate has to carry instruction `53`. `spec/vectors/transcript.json` has the
claim, the refusals of a card with no PIN and `LOAD_CHANGE` as commands and answers.

### 5.11 A holder with no phone, and the guest's history (software 1.22)

A card with a PIN and no owner is the card of a holder with no phone of their own. The whole
text is `FOXY-CARD-DAILY-LIMIT.md` 8.4.

- **Set-up and loads with no owner.** A card with a PIN and no owner takes `SET_CARD`,
  `LOAD_PROOF` and `CLEAR_SPENT` under the PIN given in the tap (`6982` without it; `6A97` for a
  load or a freeing on a card with no PIN). The first phone that sends money to a holder with
  no phone sets the card up (the PIN twice, the currency, the no-PIN allowance and the limit on
  one payment by `33`) and loads it, and does not become its owner. The record holds no refund
  key (a cash card) or one the loading phone supplies, and the design; Foxy's phone writes
  none. The card has no daily limit: that is an owner's to set.
- **The holder's numbers.** The PIN form of `SET_LIMIT` (`33`) sets the limit on one payment
  and, with eight bytes, the no-PIN allowance, while nothing is unspent and no change is owed.
- **The card keeps its PIN.** Once its record is set and its PIN is not blocked, `SET_PIN`
  wants the PIN it has, given in the tap (`6982`), nothing unspent and no change owed
  (`6A8D`). A blocked PIN is replaced with nothing owed, and the log and the receipts go with
  it.
- **A used card is rewritten only after a hold.** Once its log has taps in it, `SET_CARD`, the
  PIN form of `SET_LIMIT` and `SET_PIN` also want the history held for in this tap (`6985` in a
  tap that paid, `6982` with the PIN and no hold), so that a till handed the PIN for a payment
  cannot, in that tap, write its own refund key, a large allowance or another mint.
- **The guest's history.** On such a card the log and the receipts (`18`, P1 = 0 and 1) are the
  PIN's, and held for: ten askings in a row, `6A98` with no data to each but the last, a
  signature's work each, some five seconds; never in a tap that paid (`6985`, before any asking
  is counted); open for the rest of that time in the field once answered; and no payment
  command in it afterwards (`6985`). Any other command, a `SELECT` and a `VERIFY_PIN` among
  them, begins the count again. With the hold done, `GET_INFO` tells the PIN the limit on one
  payment (bytes 30 to 33), which no grant can be given for on a card with no owner. The
  balance, the pieces, `GET_CHANGE` and the rest of `GET_INFO` answer any reader with no hold.
  On the phone: VIEW AS GUEST and TIE OWNERSHIP TO THIS PHONE on the FLASHCARD screen of a card
  with a PIN and no owner; KEEP HOLDING while the history is held for.
- **The claim is only of an empty card** (`6A8D`), as 5.10 has it. From 1.24 it takes the
  card with its money on it, and is refused (`6A8D`) only while change is owed.
- **Change owed** is mended under the PIN in a held tap, or given up under the PIN in a held
  tap, as 5.10 has it.
- **A card with an owner** is 1.21's in every way but the two ways out of change owed (5.10).

**What a phone needs to know to speak to a card of software 1.22.** `SELECT` answers `01 16`;
`GET_INFO` is 1.21's but for the version. A phone-less set-up: `SET_PIN`, `VERIFY_PIN`, `SET_CARD`,
`33` with eight bytes, then `LOAD_PROOF`, all in one tap. A guest's read: `VERIFY_PIN`, then
`B0 18 00 00 00` again while it answers `6A98`, then the receipts, `B0 18 01 <back> 00`, at once.
A phone's gate has to carry instruction `33` for the phone-less set-up. Mending an opening: `VERIFY_PIN`,
then `ALLOW_LOAD` (the owner's) or the held `GET_LOG` (no owner), then `B0 53 <opening> 00 C2 <C_> <K> <e> <s>` with
the mint's real `K`, in a tap that paid nothing. Giving one up:
`GET_NONCE`, then `B0 51 01 <opening> <len> <proof-len> <proof> <opening>` (the owner's), or
`B0 51 01 <opening> 01 <opening>` under the PIN in a held tap (no owner). `spec/vectors/transcript.json`
has the phone-less set-up, the held read and the refusals as commands and answers.

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
  write (pieces, card record; the PIN, which every load wants since software
  1.21, the owner's phone's included; the card's own change goes back with
  none). The PIN goes to the card and nowhere else: not kept, not logged,
  wiped when the session ends.
- The native side holds the owner key (5.6). The page never sees the twelve
  words, the seed or the owner's private key; native offers it exactly two
  things:
  - `cardOwnerKey`: the owner **public** key for the card with a given
    compressed public key (130 hex characters, uncompressed), for `SET_OWNER`;
  - `cardOwnerSign`: an ECDSA signature (DER, hex) by the owner key for that
    card, over `"FoxyCard/" + label ‖ nonce ‖ value`, where the label is one of
    `change-pin`, `set-limit`, `set-owner`, `set-card`, `load`, `reset` (software 1.17), `tag` (software 1.19: the
    card takes it; which labels the phone signs is the phone's, and LIMIT 9 lists
    them differently), the nonce is the
    16 bytes the card gave and the value has the shape that label takes. Native
    refuses every other label (in particular `lock`) and every other
    shape, before it reads the seed, and answers the signature and nothing
    else. A proof is made in the tap that uses it, since its nonce lives only as
    long as the tap.
- The phone's gate carries only the instructions the page uses: `01 10 11 13 14
  15 16 20 30 31 32 34 36 37 40 41 42 43 44 45` (and, to speak to a card of software
  1.19, `52`; and, to read the mint's proofs on a card of software 1.20, `1A`). `33` (the PIN form of the limit,
  for a card with no owner) is not used by an owner's set-up; a phone that sets up a
  card for a holder with no phone has to carry it (5.11); `50` (`LOCK_CARD`) stays out.
- Every tap that finds the card behind the network shows it the newest block
  header, before anything else is read from it that depends on the day. The
  phone fetches the header over Tor from two independent sources (mempool.space
  and Blockstream, each by its onion service), checks the work itself, that both
  name the same block and that its time is within a few hours of its own clock,
  and sends `SET_HEADER`, reading the card's `now` back from the answer. A till
  does the same before a payment. The phone's checks are its own good manners:
  the card checks the work again and depends on none of them. The phone also
  tells the card its own clock with `TELL_TIME`, a note for the receipts and the
  log.
- iOS ends a session after a minute and shows its own sheet throughout; the
  sheet's line of text is set at each step (`Hold the card`, `Keep it there`,
  `Remove the card`).
- A simulator has no NFC. A stand-in card over a local port, in simulator
  builds only, as the tap link has, so the flows can be driven without
  hardware. The Java applet under jCardSim can be that stand-in, which tests
  the real applet against the real page.

## 8. Foxy: the money (wallet)

### 8.1 Being paid by a card, same mint

1. Read: the clock (a header, if the card is behind), AUTH, card record,
   slots. Refuse here, with nothing signed, and before the PIN is sent, if the
   mint differs, the balance is short, a piece is near its date, a keyset is not
   this mint's, or what is left of today's limit is less than the worth of the
   pieces step 2 would sign. What is left is worked out from `GET_INFO` (the
   limit, `now`, the window start and what is spent today; a window whose day is
   over has the whole limit left).
2. Choose pieces: exact if the card has them, else the least over, and as close
   to the price as it can either way (the day's limit is charged what leaves the
   card, the pieces less the card's own change, since software 1.12: 5.7). Add the
   mint's fee for swapping them where it charges one; the payer's card pays it.
3. Write down, before asking the card for anything, the outputs of the swap
   to come: this phone's own for the amount, and the amount of the change with
   the mint's key it is to be taken under. The change output itself is the card's:
   it draws the nonce and the blinding factor, builds the output (its own key,
   the date and refund key) and keeps the mint's key with the opening
   (`SPEND_ALL_CHANGE`, 5.10). A lost answer is then recovered from that record,
   as every swap here is.
4. VERIFY_PIN (or the no-PIN allowance), then the payment: `SPEND_ALL_BEGIN`
   with the places, `SPEND_ALL_OUTPUTS`, `SPEND_ALL_CHANGE` for the card's own
   change, and `SPEND_ALL_SIGN`, one signature (format 4, since software 1.4; up
   to format 3 a SPEND for each piece). From here the card's places are spent.
5. Swap at the mint, the card's signature as the first piece's witness.
6. Change: the change the card made for itself goes back by `LOAD_CHANGE`,
   opening by opening, with the mint's answer, with no PIN, while the card is
   still there or at any later tap (5.10). If it has gone, keep the change on file
   and show TAP THE CARD AGAIN. Pieces of the till's own written back as change go
   on by `LOAD_PROOF`, under the PIN (since software 1.21 neither the owner's grant
   nor the tap after a payment opens that). (In the first form of this flow change
   was the receiver's, locked to the card and loaded with `LOAD_PROOF`.)
7. PAID when step 5 has given this phone its pieces. One history entry, in,
   for the amount, marked as from a card.

As built: steps 3 and 5 are the wallet's ordinary receive of a token whose
pieces are already signed for, with its own record and recovery. The change
is the card's own, made on the card (step 3) and written back by `LOAD_CHANGE`
(step 6), not a second swap of this phone's making.

### 8.2 What is this phone's and what is not

- The amount is this phone's when the swap answers.
- Change is never this phone's. It is locked to the card and kept in a store
  of its own (`foxy.cashu.cardchange`), outside the balance and outside the
  books, until it is written to the card. If it never is, it comes back to the
  card's funder at its date.
- Signed and not yet swapped (the mint did not answer): held, asked about
  until the mint says, exactly as a held swap is now. If the mint says the
  pieces are unspent and will not take them, they are swapped back into
  pieces locked to the card and written to it by `LOAD_PROOF`, under the PIN,
  at a tap.

### 8.3 The holder's own phone

- **Add funds:** swap from this phone's pile into pieces locked to the card,
  powers of two, as many as there are free slots; record them before the tap;
  write; read back. An out entry, `to card`, for the amount and the fee.
  Pieces made and not yet written are shown as on their way to the card and
  can be written on the next tap. On every phone, the owner's included, the PIN
  is given: `VERIFY_PIN`, then `CLEAR_SPENT` and `LOAD_PROOF` (since software
  1.21; up to 1.20 the owner's phone gave `ALLOW_LOAD` in its place). Putting money
  on a card does not touch its limit.
- **Withdraw:** the card signs a payment of the pieces, swapped into this phone's
  own. An in entry, `from card`. Renewing and taking everything off a card are the
  same. None of them stops at the limit: in the same tap the owner's phone lifts
  the limit with the owner's proof (`34`, limit 0), the PIN is typed and the
  payment is signed, and
  the old limit is put back with the owner's proof, restored even when the
  spending fails part way. Where the card leaves before it can be, the phone has
  written the old limit down first and puts it back at the next tap. Putting the
  limit back starts a new window with nothing spent, so a withdrawal gives the
  day back its whole limit (5.7). A phone that does not hold the card's words
  cannot lift the limit, and says so.
- **Change PIN, unblock, change limit:** `GET_NONCE`, a signature over
  `change-pin` and the new PIN, `42`; and `GET_NONCE`, a signature over
  `set-limit`, `34` (any amount, or none). No PIN is asked for either: the
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
the owner's proof, which leaves the card's clock as it is; the card refuses it
while it holds unspent pieces). One with money on it
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

A card is cash. It holds what is put on it. The daily limit bounds a terminal
that has been handed the PIN, honest or built to cheat, to one day's limit per
visit, where the owner has set one (5.7).

- **A terminal built to cheat is bound by the day.** It cannot start another by
  telling the card it is later: it must bring a header dated a day on, which is
  a real block or a forgery that costs work (5.7). Nothing is bound that the
  owner has not set: a new card has no limit, and a card with none has no day
  and no wait.
- A receiver's phone that has been altered can show one amount and ask the card
  for more, up to one day's limit per visit, and up to two across a window's
  boundary. The limit does not check a payment, and nothing on the card can.
  Looking at the card's balance afterwards finds out; it does not undo it. It is
  the position of any card and any terminal.
- The same terminal can write onto the card pieces the mint will refuse, made up
  with a nonce no mint ever signed. They take nothing from anyone, since the
  receiver always asks the mint, but a later payment that picks one burns slots
  and spends a day's limit on nothing, and while such a piece is on the card it is
  not empty, so the owner cannot replace its owner or record until it is spent. A
  fake piece looks unspent to a state check. From software 1.20 the card can keep the
  mint's proof for a piece, and a phone that reads it and checks it sees that a made-up
  piece is not real before it is paid with (5.9). The card checks nothing, so this is the
  phone's: a card loaded with no proofs, or by a terminal that gives the proofs it likes,
  is as it was to a phone that does not check.
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
- Whoever spends the work of a block header can show the card any time: a
  quarter of a block's work (of the blocks of this version's time, on a card that
  has taken no header). It buys one more day's limit, or a clock frozen years ahead, after
  which the card signs for one more limit's worth until the owner sets the limit
  again. A card that has been shown no header spends its first day on trust, and
  a block's time can lead the true time by up to two hours
  (`FOXY-CARD-DAILY-LIMIT.md` 5, 6 and 13).
- A card with no owner can be claimed by a reader that has its PIN (5.4), money
  on it or not (1.24): a reader with the PIN can spend it already, and the claim
  moves none of it. The claim wants the PIN given in the tap and six askings in a
  row, seconds of holding, is never made in a tap that signed a payment, and is
  not made while change is owed (5.10).
- A card with a PIN and no owner (software 1.22) is the PIN's: a till that keeps
  the PIN it was handed can, in a tap of its own held for a guest's seconds, read
  its history, learn its limit on one payment, and, with the card empty and owed
  nothing, rewrite its record, its numbers and its PIN (5.11). Three wrong PINs
  strand the money on it: it has no owner to unblock it.
- A locked card still pays, under its PIN and its no-PIN allowance, and its
  settings are frozen (5.6). A lock does not make a lost card safe.
- The card cannot tell that the key a till names for its change is the mint's. A
  till that names a key of its own leaves change that waits until the holder mends
  it or gives it up (5.10). A stranger cannot write a piece of the card's change
  under a key it made up (`6A80`: the key is bound at the payment, 1.21); a reader
  that keeps the PIN of a card with no owner and holds a tap of its own, or the
  owner's own phone, can, and so spoil that change. It takes nothing and puts
  nothing of its own on. (Up to 1.20 any reader that took the tap after a payment
  could do so, and more.)
- **The NFC tag (software 1.19) is read by any reader, with no PIN.** It says the same
  bytes on every card and nothing of the card, so it reveals that this is a card made
  for Foxy and nothing else (5.8). A card kept on factory keys can have the tag's
  applet replaced by one that carries another address; rotating the keys (5.4)
  closes that. The owner can switch the tag off; a terminal that has the PIN cannot
  switch it either way.
- A holder with no phone has no balance they can read that a terminal did not
  draw. That is a screen, not a clock.
- A pretend card can collect a PIN typed for it. AUTH lets a holder's own phone
  know its card; a receiver has no way to know a stranger's card is real, and
  loses nothing if it is not.
- A PIN sent in its clear form crosses a few centimetres of air as typed; the
  sealed form (software 1.9) does not, and the clear form stays for a terminal that
  knows no other. Nothing of the owner crosses the air.
- Whoever holds the refund key's words can take a card's money after its date.

## 11. Order of work, and how much

The plan as it was first made, kept as history; what is built is in section 5.

| Phase | What | Rough effort |
|---|---|---|
| 0 | Paid developer membership; cards and a reader in hand; fork made | yours |
| 1a | Applet: card record, slot date, SPEND from the slot, SIGN_ARBITRARY out, AUTH, the daily limit and the clock, the owner key and its proofs (`CHANGE_PIN`, `SET_LIMIT`, `SET_OWNER`, `SET_CARD`, `ALLOW_LOAD`, `LOCK_CARD`), no load without a PIN or an owner; jCardSim tests | 3 to 4 days |
| 1b | Swift: CoreNFC session, commands, bridge, the owner key and its signatures in the native side, stand-in for the simulator, unit tests | 2 to 3 days |
| 1c | Wallet: pay by card at the same mint, change, held and lost answers, add funds, withdraw, balance, the refund path, the till's check of what is left today, the owner's phone lifting and restoring the limit when it takes money off; suites against the stand-in | 4 to 5 days |
| 1d | Screens (the limit's three steps), wording, snapshots; documents; phone checks | 2 days |
| 1e | On real cards: install, timing, every flow, every pulled tap | 2 to 4 days, set by how many rounds the hardware needs |
| 2 | A card at another mint; the PIN under a key; unblock by a second code | 1 to 2 weeks |
| 3 | A receiver with no connection: DLEQ on the card (kept with each piece since software 1.20: 5.9), an issuer's certificate, a list of withdrawn cards | a design of its own; weeks |

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
6. **The clock.** Decided (software 1.15): the card's clock is the time in the
   newest Bitcoin block header it has been shown, believed for its work and held
   to a floor and a quarter (5.7). There is no signer and no key. Open: when the
   floor is raised, how long `SET_HEADER` takes on the chip (not yet measured),
   and whether the ECDSA verifier on the first card behaves (5.6); see
   `FOXY-CARD-DAILY-LIMIT.md` section 14 for the rest of what is decided.
