# Foxy card: a fork of cashu-javacard, for your review

From the Foxy wallet project (getfoxy), to the authors of `lnflash/cashu-javacard`.

We forked your applet at 0.4 (`c1f4580`, MIT) to make a card that pays a Foxy
wallet on an iPhone by a tap and a PIN. This note says what we changed and
why, what we have and have not tested, and where we would most value your
eyes. Section 3 includes things we found in your code as it stood at that
commit.

**State of it, plainly:** the applet has run on one real card (a J3R180) with
the Foxy app on one iPhone: installed, every command sent through a reader, and
a card set up, loaded, paid from and given its change back over NFC. It builds
to a CAP with the JavaCard 3.0.5 kit and passes its tests under jCardSim. The
wallet and the iPhone app have also been driven end to end against the applet's
own class in jCardSim, including with a few real sats at your own mint
(forge.flashapp.me). One card is not a batch; section 6 says what has not been
tested.

**Which version this describes.** The note was first written when the applet was
at version 1.1; it now describes software 1.22 (`SELECT` answers `01 16`), and says
so where it tells what an earlier version did. Software 1.21 makes a PIN the first
thing a card has, makes every load the PIN's, has the card make and unblind its
own change, bound to the mint's key at the payment, and claims an owner slowly.
Software 1.22 makes a card with a PIN and no owner the card of a holder with no
phone, set up and loaded under its PIN, with its history held for its PIN, and
lets change owed be mended or given up. `docs/FOXY-CARD-SPEC.md` and
`docs/FOXY-CARD-DAILY-LIMIT.md` have the whole; section 9 says what we think a 2.0
should carry from 1.21 and 1.22.

## 1. What we would like you to look at

1. **A payment with no message from the reader** (row 2 of section 3). Is
   rebuilding the secrets on the card and hashing them there sound, and affordable
   on a J3R180? Each is a string of 170 to 270 bytes, hashed with the swap's
   outputs into one SHA-256 for the whole payment (since 1.4; up to 1.3 one for
   each piece), before the one signature.
2. **AUTH** (4). Is a signature over a tagged hash a safe way to prove the
   card holds its key, given that the same key signs spends?
3. **The daily limit, the clock and the owner's proof** (row 5 of section 3,
   and section 4). Our first answer to row 5 was a limit on what one PIN entry
   could spend, counted in RAM. A terminal that has the PIN walked round it, by
   sending the PIN again between spends or by setting the limit to nothing, and
   we removed it. The second was an allowance in permanent memory that every
   spend lowered and only a proof of a secret raised; it held against a
   terminal, but stopped a card whose owner's phone was not at hand, and we
   replaced it. The card now keeps a limit per day: the sats it will sign for in
   one day, counted against a day the card keeps itself. Its clock is the time
   written in the newest Bitcoin block header it has been shown (software 1.15),
   believed for the work it would cost to make and for nothing else, and it only
   moves forward. The owner is a P-256 public key on the card, and a proof
   is an ECDSA signature over a label, a 16-byte nonce and the value being set;
   no secret is on the card or in the air. Each nonce is good for one try, so we
   believe an observed proof cannot be replayed against it. Is there a way round
   this that we have not seen? Is an ECDSA verify cheap enough on a J3R180 to do
   once for each owner command, and does the J3R180's verifier take the DER
   form and an uncompressed key? On the one card we have, it takes both, and a
   verify took about 45 ms. Note what the clock now asks of a terminal built to
   cheat with it: a header dated later, which takes the work of a block, or a
   quarter of one at the least, to make. Up to 1.14 the time was signed by a key
   built into our app, which anyone could copy, and we replaced that. We would
   value your eyes on the three checks a header must pass (its own work, a floor
   built into the applet, and a quarter of the hardest the card has taken), and
   on what a card does that has seen no header (it spends its first day on
   trust, up to its limit). Our specification says what each costs.
4. **The date and refund key on a piece** (5). Your notes called a refund
   path "the right shape" and named its flaw. We think the date on the slot
   answers it. Do you?
5. **Anything about real hardware** that jCardSim would not show us: memory
   for 128 slots of 82 bytes, torn writes, how long a tap takes with several
   pieces to sign, how long the ECDSA verifies take (one for each owner
   command), and how long a block header takes (two SHA-256 passes and a few
   compares; we expect well under 20 ms and have not measured it).
6. Whether you would want any of this back upstream. The fork is not
   compatible with your wire, by intent, and has an AID of its own so neither
   applet can be taken for the other.

## 2. What it does

- A holder sets up a new card from their own phone, in one tap: the phone
  gives the card a PIN, then a record (which names the mint its ecash is at),
  then an owner: a public key worked out from the holder's
  seed. No limit is set: a new card has none.
- The holder puts money on it: ordinary ecash, locked to the card's key
  (NUT-11), written into slots, under the card's PIN given in the tap, on any
  phone (since 1.21; up to 1.20 the owner's phone did it with no PIN).
- To pay, the receiver's phone asks for the card's PIN, the card is tapped,
  the card signs for enough pieces to cover the amount, and the receiver
  swaps them at the mint. Only when the mint has answered does the receiver
  say paid.
- Change is made by the card itself, as outputs locked to its own key, bound to
  the mint's key the receiver names for it (1.21), and written back by any reader
  with the mint's answer, which the card checks and unblinds itself, in the same
  tap or a later one. If the card has left, it waits for that card.
- The holder can withdraw to their phone, change the PIN, unblock a blocked
  card, and set, change or remove the daily limit. A change of PIN, an
  unblock and a change of limit need a proof by the owner key, which only the
  holder's phone can make; they need no old PIN. A till can do none of them.
- A holder with no phone (1.22) has a card with a PIN and no owner: the first
  phone that sends it money sets it up and loads it under the holder's PIN, and
  the holder reads its history on a friend's phone, under the PIN, after the card
  is held still for some seconds.
- A card is cash. It holds what is put on it. A terminal that has been handed
  the PIN takes at most one day's limit per visit, honest or not: to start
  another day it must bring a block header dated later, which takes the work of a
  block to make (see item 3 above).

## 3. What we found in 0.4, and what the fork does

We read 0.2 first and 0.4 again at `c1f4580`. If any of this has changed
since, the fault is in our reading.

| # | In 0.4 as we read it | The fork |
|---|---|---|
| 1 | A blocked PIN used to open every gated command. **Fixed in 0.4** (`requirePinIfSet` treats blocked as set). | Kept, with a test that walks every gated command through every PIN state. |
| 2 | `SPEND_PROOF` signs 32 bytes the reader supplies. The card does not check that they are the hash of the slot's own secret, so a reader chooses what is signed, and the slot marked spent need not be the piece that was spent. | `SPEND` took a slot number and nothing else. The card built that slot's secret from its own key and the slot's fields, hashed it, signed in RAM, marked the slot spent, and only then sent the signature (signing after the burn lost the piece whenever the card was pulled away mid-signature). Since format 4 (1.4) a payment is one signature for all its pieces and the swap's outputs: the reader names places and outputs, and the card builds every secret and hashes the message itself (section 4). |
| 3 | `SIGN_ARBITRARY` signs any 32 bytes with the card's key and burns no slot. With the PIN, a reader can sign for every piece on the card and leave every slot reading unspent. | Removed. Proving the card is the card has its own command (section 4). |
| 4 | A card with no PIN accepts loads and spends from any reader in range. | Nothing is loaded onto a card until a PIN is set and given in the tap. A card with no owner is open while it has no PIN and nothing on it: any reader can give it a PIN, a record and an owner. From 1.22 a card with a PIN and no owner (a holder with no phone) is loaded under that PIN like any other, and keeps its PIN: a new one is set only under the old once the card is set up (a card not set up yet takes one from anyone), with the history held for once the card has been used, and a blocked one only with nothing owed. A card with an owner is its owner's, empty or not, so a terminal that has the PIN and empties a card cannot then claim it. Ours are set up before they leave our hands. |
| 5 | A reader with the PIN can spend every slot in one tap. By design, but the holder has typed their PIN into somebody else's terminal. | Our first answer was a limit on what one PIN entry may spend. **It did not hold:** a terminal that has the PIN presents it again between spends, which reset the count, or sets the limit to nothing, because the PIN alone could. It is removed. Our second, an allowance that only went down and only an owner's proof raised, held against a terminal but stopped a card whose owner's phone was not at hand. The card now keeps a limit per day, with a day of its own: a clock (`now`) that only moves forward and is the time in the newest Bitcoin block header the card has been shown. Every payment adds what leaves the card for good (its pieces less the card's own change) to what has been signed today, in the same transaction that notes its places spent; a payment that would take today past the limit is refused before anything is signed; only the owner's proof sets or removes the limit. A terminal that has the PIN cannot start a new day by telling the card a later time: it has to bring a header of that time, and the card believes a header only for its work. (Up to 1.14 the time was signed by a key built into our app, so it was not secret, and the limit bound only honest terminals.) A card is cash. |
| 6 | The card does not say which mint its pieces are at (an open question in your notes). | A card record names the mint. |
| 7 | No DLEQ on the card, so a piece cannot be checked without the mint. | Our receiver is always online and always swaps before saying paid. And from software 1.20 the card keeps the mint's NUT-12 proof (`e`, `s` and the blinding factor `r`, 96 bytes) with each piece that comes with one, and gives it back, so that a phone can check a piece offline. The card checks nothing and never uses it. |
| 8 | The deployment guide installs with the card's factory GlobalPlatform keys and does not change them afterwards. A card left on them can have the applet deleted, and the money with it, by anyone with a reader. | Not the applet's to fix. Our provisioning steps will change them. |
| 9 | In flash-pos, when we read it: an exact payment was shown as paid when the swap had failed, and a proof the mint reported as spent was booked as settled, whoever spent it. | Not our code, and you may have fixed it. Our rule is that paid means the mint has given this phone its own pieces. |
| 10 | `CHANGE_PIN` needs a verified session and the old PIN, both of which a reader handed the PIN has. It can set a PIN the holder does not know, and the holder's wrong tries then block the card for good (your threat 14, by another road). | The owner's proof replaces both: the proof and the new PIN only, no old PIN and no session. A wrong proof costs no tries. The same command unblocks a blocked card. |
| 11 | `LOCK_CARD` needs the PIN only if one is set: any reader can lock a card with no PIN for good, and a reader that has the PIN can lock a funded card (it keeps paying, and can never be loaded, cleared or changed). | A PIN set and verified, and the owner's proof. A card with no owner cannot be locked. |

Number 2 and number 3 are the ones that matter most. Together they mean the
SPENT flag on the card protects nothing: the signature is what spends, and
the card will sign without burning the right slot, or any slot.

## 4. What changed on the wire

Class `B0` as yours. AID: package `F0 46 4F 58 59 43 41 52 44`, applet the same with `01`.

| Command | Yours | Ours |
|---|---|---|
| `01` GET_INFO | 8 bytes | 30: adds format, tries left, locked, has-record, the daily limit, has-owner, then the day (`now`, window start, spent today), then whether change is owed to the card (byte 29, to anyone; 1.21). With P1 = 1, 42: the limit on one payment (to the owner's grant, or on a card with no owner to the PIN in a held tap; zeros to anyone else), the no-PIN allowance and what has been paid with no PIN since the PIN was last given (anyone; 1.16). With P1 = 2, 43 (1.19): those 42 and whether the NFC tag is on (anyone) |
| `13` GET_PROOF | 78-byte slot | 82: adds a 4-byte date |
| `15` AUTH | none | reader sends 16 random bytes; card answers 16 of its own and a BIP-340 signature. No PIN |
| `16` GET_CARD | none | the card record, with the daily limit and, where a time key was up to 1.14, the hardest difficulty and the hash of the newest block header the card has taken; the design after the mint |
| `17` GET_PIECES | none | every slot's state and every unspent piece, a page at a time; or a short listing for choosing pieces; or up to three slots whole. No PIN |
| `18` GET_LOG | none | the card's own account of its last eight taps (P1 = 0) and its last sixteen receipts (P1 = 1). With an owner: the log to the PIN or the owner's grant, the receipts to the grant. With a PIN and no owner (1.22): both to the PIN, after ten askings in a row, `6A98` (keep holding) to each but the last; never in a tap that paid (`6985`), and no payment in a tap that read them |
| `19` GET_CHANGE | none | the openings of the card's own change not yet written back: number, amount, keyset, date, blinded message and the mint's key each was bound to, 83 bytes each (1.21). Never the blinding factor. No PIN |
| `1A` GET_DLEQ (1.20) | none | no PIN. `P1` a slot, `P2` a second slot or `FF`. For each slot asked: `01` and the 96 bytes (`e ‖ s ‖ r`) of the mint's proof kept with an unspent piece, or the single byte `00`; at most 194 bytes; `6A83` for a slot there is not. The card does not check what it gives |
| `20` SPEND | slot + 32-byte message | gone since format 4 (1.4): `6D00`. Up to 1.3 it took a slot only and, from 1.1, refused (`6A8F`) a piece that would take today past the limit, and otherwise added the piece's whole amount to what was spent today in the transaction that marked the slot spent |
| `22` `23` `26` `24` SPEND_ALL_BEGIN, OUTPUTS, CHANGE, SIGN | none | one payment, one signature (NUT-11 `SIG_ALL`): the places; the swap's outputs; the card's own change outputs (the amount and the mint's key for it; the card builds and blinds each and keeps it); the signature, every place burned in the transaction that commits it. The PIN is asked at `24`: a card with a PIN signs without it up to its no-PIN allowance, counted on the whole of the pieces and renewed when the PIN is given (1.21); over it, `6A94` asks for the PIN. Over the limit on one payment `24` answers `00 01`, "not yet", a signature's work at a time, before it signs. A card with no PIN refuses all of them, `6A97` (1.21), and a tap that claimed an owner or read a card with no owner's history refuses them, `6985` |
| `25` SPEND_ALL_AGAIN | none | the last signature, again, for an answer lost on the air: under the PIN, or with none where it was given with none |
| `30` LOAD_PROOF | 77 bytes | 81, with the date; needs a PIN set (`6A97` with none) and given in the tap (`6982`), and a card record; nothing else opens it, the owner's grant included (1.21), and a card with no owner is loaded so as well (1.22; up to 1.21 it needed an owner). It asks nothing of the clock. Never touches the limit. With `P1 = 1` (1.20), 177 bytes: one piece and the mint's proof for it; the answer is the slot and `01` where the proof was kept |
| `31` CLEAR_SPENT | the PIN if one is set | the PIN given in the tap (`6A97` with none set); not the owner's grant (1.21) |
| `32` SET_CARD | none | writes the record (unit, refund key, mint, design; sixty-five bytes where a time key was are sent and not read); on a card with no owner the PIN if one is set (and, once the card has been used, the history held for in the tap: 1.22), the owner's proof on a card with one; refused while the card holds unspent pieces or is owed change. Never touches the limit or the clock |
| `33` SET_LIMIT, PIN form | none | on a card with no owner, by the PIN, while nothing is unspent or owed (and once used, in a held tap): the limit on one payment (4 bytes), and with 8 the no-PIN allowance (1.21; up to 1.20 the daily limit). (`33` was once a limit on one PIN entry, removed; this is not that) |
| `34` SET_LIMIT, owner's form | none | proof length, proof, then the limit (4 bytes; 8 add the limit on one payment; 12, from 1.16, add the no-PIN allowance). The owner's proof, no PIN, any funds |
| `35` SET_TIME | none | gone in 1.15 (`6D00`). It took a time under an ECDSA signature by a time key in the record |
| `36` SET_HEADER | none | a Bitcoin block header, 80 bytes. No key, no PIN, no owner. The card hashes it twice with SHA-256 and the hash must be at or under the target its own `bits` name, at or under a floor built into the applet, and, once the card has taken a header, at or under four times the target of the hardest taken (`6A93` otherwise; `6A80` for `bits` no header could carry). A header with a later time moves the card's clock forward; answers its `now` |
| `37` TELL_TIME | none | the terminal's own clock, 4 bytes. A note for the receipts and the log, trusted for nothing |
| `40` VERIFY_PIN | the PIN | the PIN, or sealed (P1 = 1, 1.9); it also renews the no-PIN allowance (1.21) |
| `41` SET_PIN | the PIN, once (`6985` if one exists) | sets or replaces the PIN, and unblocks, on a card with no owner, with nothing unspent and no change owed. A card with an owner refuses it (`6A91`; ADD PIN, 1.16 to 1.20, is gone). A card with no owner that is set up keeps its PIN (1.22): a new one wants the old given in the tap, and once the card has been used the history held for; a blocked one is replaced with nothing owed, and the history goes with it |
| `42` CHANGE_PIN | old PIN length, old PIN, new PIN | proof length, proof, new PIN. The owner's proof; no old PIN, no session, any PIN state, any funds. Resets the tries |
| `43` SET_OWNER | none | the owner's public key, 65 bytes uncompressed. On a card with no owner, a claim (1.21): under the PIN given in the tap, only while it is empty, never in a tap that paid, asked six times in a row, `6A98` (keep holding) to each but the last; on a card with an owner, only with the old owner's proof and only while it is empty (`6A8D` otherwise, `6A91` without a good proof) |
| `44` GET_NONCE | none | no data; answers a fresh 16 bytes held in RAM. No PIN; `6A90` on a card with no owner. With P1 = 1, on any card, the nonce, the key a PIN is sealed to, and the card's signature over it |
| `45` ALLOW_LOAD | none | the owner's proof (label `FoxyCard/load`). For the rest of that tap: the log and the receipts read, the limit on one payment told, a second payment not slowed, and, with the PIN, `LOAD_CHANGE` under another key (1.22). It opens no load (1.21; up to 1.20 `LOAD_PROOF` and `CLEAR_SPENT` needed no PIN under it). Not a payment |
| `50` LOCK_CARD | the PIN if one is set; `P2 = DE` | a PIN set and verified, `P2 = DE`, and the owner's proof as data. A locked card still pays and still takes its own change |
| `51` RESET_CARD (1.17) | none | `P2 = AD`, and the owner's proof as data (label `FoxyCard/reset`); a locked card also wants the PIN in this tap (`6982`); nothing unspent and no change owed (`6A8D`). The card is then as out of its packet, with a new key, and keeps only its clock; see SPEC 5.6a. With `P1 = 1` and P2 an opening's number (1.22), that one opening of change is given up and nothing is reset: the owner's proof over the number, or on a card with no owner the PIN in a held tap |
| `52` SET_TAG (1.19) | none | `P1 = 00` tag on, `01` off, `P2 = 00`, and the owner's proof as data (label `FoxyCard/tag`) followed by the one byte the proof is over, the same as P1; no PIN, any funds; `6986` locked. The card also answers as an NFC Forum Type 4 Tag, under its own applet and the NFC Forum's name, carrying one web address, the same on every card; see SPEC 5.8 |
| `53` LOAD_CHANGE (1.21) | none | one opening of the card's own change made a piece: the mint's blinded signature, its key and its NUT-12 proof. The key must be the one bound at the payment; the card checks the proof, takes its own blinding factor off and writes the piece, with the mint's proof kept. Any reader, no PIN, any tap. Another key only under the PIN and the owner's grant, or on a card with no owner the PIN in a held tap, in a tap that paid nothing (1.22) |

New status words: `6A8C` no card record, `6A8D` something set only on an empty
card, with a piece unspent or change owed, `6A8E` a dated piece on a card with no
refund key, `6A8F` over the day's limit (was "over the limit"), `6A90` no owner,
`6A91` the owner's proof is missing or wrong, or the command is not open to a card
with an owner, `6A93` a `SET_HEADER` whose work is not enough, `6A94` a piece whose
nonce is already in a slot, spent or not, and (1.16) a signature refused for want
of the PIN, `6A96` more places than one payment can burn, `6A97` (1.21) a card with
no PIN, and `6A98` (1.21) keep holding: a held tap counted and not yet through, ask
again. (`6A92` was "never told the time", and is unused since 1.15.)

128 slots of 82 bytes since software 1.7 (yours: 32 of 78), with the text a
payment hashes (130 bytes) and, from 1.20, the mint's proof (96 bytes) kept
beside each. The card record is unit, the daily
limit, a 33-byte refund key, 65 bytes that hold the hardest difficulty and the
newest hash of a block header taken (a time key up to 1.14), the clock and the
day, the limit on one payment, the no-PIN allowance and its count, the mint's
address (up to 77 bytes; 80 when this was first written) and the design. Apart
from it the card keeps a 65-byte owner public key, which nothing reads out, and
the eight openings of its own change. The applet's version was 1.1 (`SELECT`
answers `01 01`) and the format byte 3 when this was first written; they are 1.22
(`01 16`) and 4 now.

**The owner and its proof.** The holder's phone works the owner's private key out
for each card as HMAC-SHA256 with the 64-byte BIP-39 seed as the key, over
`"FoxyCard/owner" ‖ 0x00 ‖` the card's 33-byte compressed key, taken mod the
order of P-256, in its native side only; the page never holds it. It gives the
card the public half with `SET_OWNER`, last in the set-up tap. A proof is an
ECDSA signature (P-256, SHA-256, DER) by that key over `label ‖ nonce (16) ‖
value`, with the nonce from `GET_NONCE`, verified on the card. It is one try for
each nonce, gone with the tap, and a wrong proof costs no PIN tries and does not
end the PIN session.

| Command | Label (ASCII) | Value |
|---|---|---|
| `CHANGE_PIN` | `FoxyCard/change-pin` | the new PIN's bytes |
| `SET_LIMIT` (`34`) | `FoxyCard/set-limit` | the new limit, 4 bytes, big-endian (8 or 12 from 1.5 and 1.16: the limit on one payment, then the no-PIN allowance) |
| `SET_OWNER` (on a card with an owner) | `FoxyCard/set-owner` | the new owner key, 65 bytes |
| `SET_CARD` (on a card with an owner) | `FoxyCard/set-card` | the data sent |
| `ALLOW_LOAD` | `FoxyCard/load` | nothing |
| `LOCK_CARD` | `FoxyCard/lock` | nothing |
| `RESET_CARD` (1.17) | `FoxyCard/reset` | nothing; with P1 = 1 (1.22), the opening's number (1) |
| `SET_TAG` (1.19) | `FoxyCard/tag` | one byte: `00` tag on, `01` tag off |

A proof for one command or value is no proof for another, and an old or used
one fails (`6A91`). Nothing of the owner crosses the air: no secret is on the
card or sent to it, and the page never holds one. A card is set up in one tap:
`SET_PIN`, `VERIFY_PIN`, `SET_CARD`, then `SET_OWNER` last, so that no step
needs a proof and a set-up cut off anywhere is finished by the next. A card
with no owner is open while it has no PIN. From 1.22 one with a PIN may be loaded, under the PIN, and is the card of a holder with no phone.

**The clock.** `SET_HEADER` shows the card a Bitcoin block header. The card
keeps `now`, the time in the newest header it has taken, which only moves
forward. A header is believed for its work and for nothing else: the card
hashes the 80 bytes twice and the hash, read as a little-endian number, must be
at or under the target the header's `bits` name. That target must be at or under
a floor built into the applet (`bits` `0x17087BC0`, four times the target of the
network's blocks when 1.15 was written, so a quarter of a block's work then, about
2^77 hashes to find one) and, once the card has taken a header, at or under four times
the target of the hardest it has taken: a quarter of that work, which climbs with
the network and is never lowered. Setting the limit begins a window at `now`, and
a payment that finds `now` a day (86,400 seconds) or more past the window's start
begins a new one. Nothing else begins a window, and nothing sets `now` back.
`LOAD_PROOF` asks nothing of the clock: a card that has been shown no header
spends its first day on trust, up to its limit, and the first header taken begins
the window. A block's time can lead the true time by up to two hours, and the
clock moves a block at a time, only when someone shows the card a header.

If the J3R180 turns out to have no usable ECDSA verifier, we have a documented
fallback for the owner's proof, built on a hash of a secret.

**What a payment signs.** Since software 1.4 there is no `SPEND` of one piece:
`0x20` answers `6D00`. Every secret ends with the SIG_ALL flag, and a payment is
one signature over a running hash of every piece's secret and `C`, then the
outputs (`FOXY-CARD-SPEC.md` 5.2). The secret is the exact text cashu-ts writes
for a P2PK piece, built on the card:

    ["P2PK",{"nonce":"<64 hex>","data":"<card key, 66 hex>","tags":[["sigflag","SIG_ALL"]]}]

and for a piece with a date:

    ["P2PK",{"nonce":"…","data":"…","tags":[["locktime","<decimal>"],["refund","<66 hex>"],["sigflag","SIG_ALL"]]}]

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
spent. The card checks no date against a clock (the `now` it keeps for the
daily limit is only the time of the newest block header it has been shown, and
is not used for dates), which is the flaw your notes name: a mint may stop
honouring the card's own signature once the date passes. So the date is on the slot where a reader
sees it, and a receiving wallet refuses a piece within a week of its date
before asking the card to sign.

**In the app this is switched off for now.** Cards are set up as cash, as
yours are: lose the card and the money is gone. A forgotten PIN or a blocked
card is put right from the owner's phone, which sets a new PIN with no old one.
The app says a card is cash when it is set up.

## 6. What has been tested, and what has not

Tested:
- The applet under jCardSim, including every gated command in every PIN state,
  the secret the card signs against the text a wallet builds, your write-order
  tests adapted to the new slot, the daily limit and the clock (a reader that
  has the PIN and sends it between spends takes one piece of four, and takes a
  second only after a header a day later; exactly the limit goes and one sat
  more does not, with nothing burned; a window turns at 86,400 seconds and not
  at 86,399; a header whose hash is over the target of its own `bits`, or whose
  target is over the floor or over four times the hardest taken, is `6A93`; an
  older header changes nothing; real blocks of the network are taken or refused
  as they should be; a card that has been shown no header spends its first day
  on trust), and the owner (each command refused with the PIN alone, with another
  command's label, another value, another nonce, another key's signature, or a
  used proof, and accepted with the right proof and no PIN; a wrong proof costs
  no PIN tries; a card with an owner refuses the PIN's `SET_PIN`, `SET_OWNER`,
  `SET_CARD` and `SET_LIMIT` even when empty; a card with no owner is loaded under
  its PIN and never without it, 1.22), the card's own change (any reader writes it
  under the key bound at the payment, and a key made up after it is refused), the
  claim's six askings, and the guest's ten. A test reads the source to check that
  the note that the places are spent and what is spent today change in one
  transaction, with the signature made before it and answered after.
- The wallet's flows against a JavaScript model of the card, and the model
  against a recorded conversation with the applet.
- The same flows against the applet itself over a socket: set-up, loading,
  paying with change, a card pulled away at each point of a payment in turn,
  PIN change, three wrong PINs, emptying.
- The iPhone app in the simulator against the applet in jCardSim, at a test
  mint and at forge.flashapp.me: set up, add funds, pay an invoice by card
  with change written back, withdraw.

Run on one real card, and not beyond it: install, every command, NFC with an
iPhone, and timing (an ECDSA verify about 45 ms, a payment's one signature
about 0.6 s on the chip, by a code comment, the card's own Schnorr in software, and a read tap about a
second and a quarter over contact).

Not tested:
- More than one card, and a batch. No torn write on silicon, and no write
  endurance for the extra write on every spend.
- The wallet and the iPhone app against the daily limit, the clock and the owner
  key on a real card, beyond a set-up and a payment: the applet's side of them
  was walked command by command through a reader. `SET_HEADER` has since been
  run on real cards from the phone, where the step that shows a header that
  moves the clock takes 0.07 to 0.09 s over NFC. It has not been timed through
  a reader.
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

A card is cash. It holds what is put on it. The daily limit bounds a terminal
that has been handed the PIN, honest or built to cheat, to one day's limit per
visit, where the owner has set one.

- **A terminal built to cheat is bound by the day.** It cannot start another by
  telling the card it is later. The card's clock is the newest block header it
  has been shown, believed for its work, so the terminal must bring a header
  dated a day on: a real block when the world has made one, or a forgery that
  costs a quarter of a block's work (of the blocks of 1.15's time, on a card that
  has taken no header). A forgery buys one more day's limit, or a clock frozen years
  ahead. A card that has been shown no header spends its first day on trust. A
  block's time can lead the true time by up to two hours.
- Nothing is bound that the owner has not set: a new card has no limit, and a
  card with none has no day and no wait on one payment.
- A terminal that has been handed the PIN takes one day's limit per visit, and up
  to two across a window's boundary. The limit does not check a payment, and
  change a till writes back as pieces of its own does not give the day anything
  back (the card's own change is not charged, since 1.12), so a till should pick
  pieces as close to the price as it can.
- The same terminal can write onto the card pieces the mint will refuse, made up
  with a nonce no mint ever signed. They take nothing from anyone, since our
  receiver asks the mint, but a later payment that picks one burns slots and
  spends a day's limit on nothing. A fake piece looks unspent to a state check. From
  1.20 the card can keep the mint's proof for a piece, and a phone that reads and
  checks it sees that a made-up piece is not real; the card checks nothing.
- It cannot write a copy of a real piece: the card signs a piece's secret, which
  its nonce makes, and takes the amount on the terminal's word, so a copy stating
  an amount of 1 would have been spent for 1 and signed for the real piece.
  `LOAD_PROOF` refuses a nonce that is in any slot, spent or not (`6A94`). The card
  does not remember a nonce once `CLEAR_SPENT` has freed its slot.
- A holder whose owner has lost their words can never change the PIN or the
  limit, or unblock a blocked card. Whoever has the owner's words and the card in
  hand has everything on it.
- Any reader in range can send three wrong PINs and block the card. Its owner's
  phone unblocks it; a card with no owner has none to (below).
- A pretend card can collect a PIN typed for it. A receiver loses nothing to
  one, because it goes to the mint before saying paid.
- A PIN sent in its clear form crosses a few centimetres of air as typed; the
  sealed form (1.9) does not. Nothing of the owner does.
- A card with no owner can be claimed by a reader once it is empty (from 1.22 it
  may hold money, and is loaded under its PIN). From 1.21 a claim wants the
  card's PIN given in the tap and six askings in a row, seconds of holding, and
  is never made in a tap that paid.
- A locked card still pays, under its PIN and its no-PIN allowance; locking
  freezes its settings and every write but its own change coming back.
- The card cannot tell that the key a till names for its change is the mint's. A
  till that names its own leaves change that waits until the holder mends it or
  gives it up. A stranger's key is refused, since the key is bound at the payment
  (1.21); a reader that keeps the PIN of a card with no owner and holds a tap of
  its own, or the owner's own phone, can spoil an opening's change with a key of
  its own. Nothing is taken (section 9, rule 3).
- On a card with no owner (1.22) a till that keeps the PIN can, in a tap of its
  own held for some seconds, read its history and, once it is empty and owed
  nothing, rewrite its record, its numbers and its PIN. Three wrong PINs strand the
  money on such a card: it has no owner to unblock it.
- A cash card that is lost is lost money.

## 9. What a 2.0 should carry from 1.21 and 1.22

The rules as built are in `docs/FOXY-CARD-DAILY-LIMIT.md`: 6f (a PIN first, the
allowance), 8.2 (the card's own change), 8.3 (the claim) and 8.4 (a holder with no
phone); section 15 there lists them version by version. What a 2.0 card should
carry from them:

1. **A PIN before any money.** A card with no PIN takes no piece and makes no
   payment (`6A97`). Every load wants the PIN given in the tap, and nothing waives
   it, the owner's grant included. The one write that wants no PIN is the card's
   own change (rule 2).
2. **Change the card makes and unblinds itself, bound to the mint's key at the
   payment.** The paying reader names the change amount and the mint's key for it;
   the card builds the output (its own key, the payment's date and refund key, a
   fresh nonce and blinding factor), keeps the key with the opening, and lists the
   opening to anyone without its blinding factor. Any reader may later bring the
   mint's answer, in any tap, with no PIN; the card refuses any key but the bound
   one, checks the mint's proof, takes the blinding factor off itself and keeps the
   mint's proof with the piece. So a reader can load the real change and nothing
   else, and no grant or special tap is needed for it.
3. **Change owed is mended or given up where a PIN can be given.** A reset or a
   new record waits until no change is owed. A till can bind an opening to a key
   that is not the mint's, and then the mint's real answer can never be loaded
   under the bound key. Two ways out, both needing a PIN that can be given (with
   the PIN blocked and change owed, a card with no owner takes no new PIN and a
   locked card's let-go wants the PIN, so such change stays owed for good): the mint's answer is taken under another key when the PIN was given in the
   tap, the tap paid nothing, and the holder's own say is in it (the owner's grant;
   on a card with no owner, the held history), with the proof still checked under
   the key given (the PIN alone is a till's too); and one opening is given up by
   number (the owner's proof; on a card with no owner, the PIN in a held tap), its
   money gone, written in the log.
4. **An owner claimed in a tap of its own**, under the PIN, only of an empty card,
   never in a tap that paid, and no payment after it in that tap, after a held
   count of askings (six on this card), each but the last answered with a status
   word that is not `9000` (`6A98`) after a signature's work of the card's own.
5. **A no-PIN allowance counted on the whole of what a payment spends and refilled
   by the PIN**, not by a clock: a stranger with the card gets one allowance, and a
   reader with no PIN cannot strand more than it by asking for change it never
   swaps.
6. **The holder sets the limit on one payment and the allowance with the PIN** on
   a card with no owner, while it is empty and owed nothing; once there is an
   owner, only the owner's proof sets limits.
7. **A card for a holder with no phone** (1.22). A card with a PIN and no owner is
   set up and loaded under the PIN given in the tap: the record, the pieces and
   the freeing of spent places, refused without it. The first phone that sends
   such a card money sets it up (the PIN twice, the currency, the no-PIN allowance,
   the per-payment limit) and loads it, and does not become its owner. Its record
   holds no refund key (a cash card) or one that phone supplies, and the design.
   It keeps its PIN: once set up, a new one wants the old (a card not set up yet
   takes one from anyone), and a blocked one is replaced only with nothing owed,
   the history going with it. Once it has been used it is
   rewritten (record, numbers, PIN) only in a held tap, so that a till handed the
   PIN for a payment cannot, in that tap, write its own refund key, a large
   allowance or another mint. Tying the card to a phone of the holder's own is the
   held claim of rule 4, on an empty card.
8. **The guest's history, held for** (1.22). On a card with a PIN and no owner the
   log and the receipts are the PIN's, read on a friend's phone as a guest, and
   held for: a count of askings (ten on this card), each but the last answered
   with the same status word as the claim's and after the same throwaway work, so
   that it takes seconds the holder sees. Never in a tap that paid, and no payment
   in a tap that read it, so that a till handed the PIN for a payment does not read
   the holder's history in the same tap. With the hold done the PIN is told the
   limit on one payment, which no owner's grant can show on such a card. The
   balance and the pieces are not held for: a till needs them to be paid.

What 1.22 leaves to a 2.0: a reader that keeps a card's PIN, rather than one handed
it for a payment, can still hold a tap of its own on a card with no owner and read,
rewrite or spoil there (`SECURITY-MODEL.md` 20 to 22); and a card with no owner
whose PIN is blocked strands its money (14).

## 10. Where it is

Two public repositories.

- `getfoxy/card` (https://github.com/getfoxy/card): the applet. Start with
  `FORK.md`, then `docs/FOXY-CARD-SPEC.md`, and `docs/FOXY-CARD-DAILY-LIMIT.md`
  for the daily limit, the clock and the owner key in full. `spec/vectors/` has
  the secrets and a full conversation with the card. Your own `spec/`, `README`
  and `tools/cardctl` are still in the tree and still describe your wire.
- `getfoxy/iOS` (https://github.com/getfoxy/iOS): the wallet. The card code is
  `build/wallet/08a-flashcard.js` and `21a-flashcard.js`, the iPhone side
  `Foxy/Flashcard/`, the tests `tests/flashcard-*.js`, and what the app does
  with a card is `docs/CARD.md`.
