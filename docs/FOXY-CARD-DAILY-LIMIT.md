# Foxy card: one PIN, a daily limit, an owner key

The second design of the card's limit. It replaced the allowance of
`FOXY-CARD-SPEC.md`'s first draft (its 5.3 rule 3, 5.6 and 5.7, and the
`SET_ALLOWANCE` and owner-secret rows of its 5.2). The rest of that specification
is the card's as it stands: the slot, the secret text, `AUTH`, the gate in every
PIN state, D10 and D14, provisioning, the phone's side. This document has the
limits, the owner, the clock and every command in full (section 8). The two say
the same; where this one is silent, that one speaks.

What is kept from the earlier draft, what is changed and what is dropped is
listed at the end (section 11), because the applet before this design
implemented the draft and the changes are easier to make from that list than
from the prose.

From software 1.15 the card's clock is the time written in the newest Bitcoin
block header the card has been shown (section 5). Up to 1.14 it was a time told
under a signature by a key in the card's record. This document is written for
the clock of 1.15; section 11 keeps the account of the first design and says
where 1.15 replaced it.

From software 1.16 a card that has a PIN has a third limit, the no-PIN
allowance: what it will sign for without the PIN (section 6f). A payment needs
the PIN given in its tap unless it fits in the allowance.

From software 1.19 the card also answers as an NFC tag carrying one web address,
which its owner switches off and on with one more owner's command, `SET_TAG`
(`FOXY-CARD-SPEC.md` 5.8). It touches nothing of the limits, the day or the clock.

From software 1.20 the card also keeps the mint's NUT-12 proof with each piece that
comes with one, for a phone to read (`GET_DLEQ`) and check (`FOXY-CARD-SPEC.md` 5.9).
The card checks nothing and never uses it; a payment is what it was, and the limits,
the day and the clock are untouched.

From software 1.21 a card is given its PIN before anything goes on it or leaves it
(6f), and every load wants the PIN given in its tap (section 3, rule 7). The card
makes its own change and unblinds it itself, bound to the mint's key named at the
payment, and any reader may write that change back, in any tap, with no PIN (8.2).
An owner is claimed slowly, under the PIN (8.3). The no-PIN allowance is renewed
when the PIN is given, and not when the day turns (6f).

From software 1.22 a card with a PIN and no owner is the card of a holder with no
phone: it is set up and loaded under its PIN, by a phone that does not become its
owner, and its history is the PIN's, read on a friend's phone after a held tap
(8.4). Change owed to any card can be mended under the PIN and the holder's own
say, or given up (8.2). A card with an owner is 1.21's but for those two ways out.

Every section below says what software 1.22 does (`SELECT` answers `01 16`).
Where a paragraph tells what an earlier version did, it says so. What changed in
1.21 and 1.22, rule by rule, is in section 15, at the end.

## 0. Why

The first limit was on one PIN entry, kept in RAM and reset by `VERIFY_PIN`.
A terminal that had been handed the PIN sent `VERIFY_PIN` again between
spends, or `SET_LIMIT 0`, and took the whole card in one tap; the limit bounded
nothing (the audit, with a jCardSim proof). The draft that followed put an
allowance in permanent memory that every spend lowered and only the holder's
phone could raise. That holds against a terminal, but a card whose allowance is
used up has stopped until its holder's phone has been to it, and some holders
will not have a phone. Their card is set up on another phone (since 1.22 by the
first phone that sends it money, which does not become its owner: 8.4), and
should then work on its own.

So the limit is **per day**, and the card keeps the day itself, from the time
written in the newest Bitcoin block header it has been shown. A terminal with
the PIN can take one day's limit per visit and nothing more, and the holder
never has to bring the card back to anything.

**Read section 5.2, and the first day in section 6, before relying on any of
this.** The card believes a header for the work it would have cost to make and
for nothing else: no key, no signer, no phone's word. A terminal built to cheat
cannot start another day by telling the card it is later. It has to bring a
header dated later, and a header costs work to make: the world's miners make
one every ten minutes, and anyone else would have to spend a good part of a
block's work on one. Up to software 1.14 the time was signed by a key inside the
app, which anyone could copy, and those versions bound an honest terminal and
not a cheating one.

## 1. Goals

1. A holder with no phone of their own carries a card that bounds, by itself,
   what any terminal can take from it, honest or built to cheat: one day's limit
   per visit, where a limit is set.
2. Nothing about a funded card (its PIN, its limit, its mint, its owner) can be
   changed by anyone who merely holds the PIN.
3. The phone that is the card's **owner** (the holder's own) can change the PIN
   and the limit at any time, without knowing the PIN, and can unblock a blocked
   card. It puts money on the card as anyone does, under the PIN (since 1.21; up
   to 1.20 its proof let money on with no PIN).
4. A card with **no** owner and no PIN is nobody's, and is open, because there
   is nothing on it to protect. A card with a PIN and no owner is the card of a
   holder with no phone: it is set up, loaded and read under its PIN (8.4). A card
   **with** an owner is its owner's, empty or not.
5. The receiver's and holder's sides of `FOXY-CARD-SPEC.md` are unchanged in
   what they promise: paid only when the mint has given the phone its pieces;
   nothing signed when a refusal can be known beforehand.

Not goals: a limit that follows the price rather than the pieces signed
(section 6); a check, by the card, of pieces a terminal writes onto it (DLEQ: since
1.20 the card keeps the mint's proof for a phone to check, `FOXY-CARD-SPEC.md` 5.9); a balance the holder can read without a phone (a screen; a separate
question); recovery of a lost card (Foxy's phone writes no refund key); a clock that is
exact (a block's time can lead the true time by up to two hours, 5.1).

## 2. Who is trusted with what

| Party | Holds | Can do wrong |
|---|---|---|
| Card | its key, the pieces, the PIN, its owner's public key, its clock (the time and hash of the newest block header it has taken, the difficulty of the hardest), the day | nothing by itself; it signs what it is asked, under its rules, up to the day's limit |
| Cardholder | the card and the PIN | pay twice from a copied card, if a receiver ever accepts without the mint |
| Owner's phone | the twelve words, and from them the card's owner key | with the card in hand: change the PIN (and then load and pay under the new one), set or remove the limit, and so take everything. Without the card: nothing. It is a trustee, held on a leash of two things: its words and the card itself |
| Receiving phone | sees the PIN, talks to the card | ask the card to sign more than the amount shown, up to what is left of today's limit; bind the card's change to a key that is not the mint's, so that the change waits until the holder mends it or gives it up (8.2); write pieces the mint will refuse; on a card with no owner, in a tap of its own held for the seconds of a guest's read, read its history and rewrite its record, its numbers and its PIN (8.4) |
| Mint | decides what is spent | what any Cashu mint can |
| Whoever shows the card a block header | a header | move the card's clock forward to the time the header carries, if it shows the work (5.1), and so end a day and begin the next, no sooner than that time. Never move a clock backward. A header dated later than the network's own time has to be forged, which costs work (5.2) |

Two consequences shape the design, as before, and one is new. The receiver
always goes to the mint before saying paid, so a copied or pretend card gains
nothing. A terminal sees the PIN and chooses what to ask the card for, so
nothing the PIN alone can reach may bound it or change it. And the one thing a
terminal cannot manufacture is tomorrow: it can fetch a true "now" from the
network, but not a later one, because a header dated later has to have its work
done, and a terminal does not have a block's worth of it. So a window that only
a newer block can end is a bound it cannot walk round (5).

## 3. The rules

1. **The PIN pays and loads, and on a card with an owner nothing else.** With the
   PIN given in the tap a terminal can pay (up to the limits), `LOAD_PROOF` and
   `CLEAR_SPENT`. On a card with an owner the PIN changes nothing else: not the
   PIN, not the limit, not the record, not the owner. On a card with no owner the
   PIN also sets the card up: its record, its limit on one payment, its no-PIN
   allowance and a new PIN, only while nothing is unspent and no change is owed,
   and, once the card has been used, only in a tap whose history was held for
   (8.4). The card's own change goes back with no PIN at all (`LOAD_CHANGE`, 8.2).
   A card pays up to its no-PIN allowance without the PIN (6f), and every card has
   a PIN before anything goes on it (6f).
2. **The owner's proof administers.** `CHANGE_PIN`, the owner's `SET_LIMIT`
   (`34`), `SET_OWNER` and `SET_CARD` on a card with an owner, `ALLOW_LOAD`,
   `LOCK_CARD`, `RESET_CARD` (1.17; both its forms on a card with an owner) and
   `SET_TAG` (1.19) need an ECDSA
   signature by the owner key over a label of the command, a nonce the card has
   just given, and the value. They need no PIN, because the owner's phone does
   not know it (`LOCK_CARD` keeps its PIN as well, and so does `RESET_CARD` on a
   locked card). The signature is checked by
   the card's native ECDSA verifier; the owner's private key never leaves the
   phone's native side.
3. **A card with an owner is not open, empty or not.** Everything that sets or
   replaces the PIN, the owner, the card record (mint, unit, refund key) or the
   limits needs, on a card with an owner, that owner's proof, whether or not the
   card holds anything. Replacing the owner needs the card empty as well, and
   replacing the record needs it empty and owed no change. A card with **no**
   owner and no PIN is the card out of its packet: anyone may give it its PIN and
   its record, since it holds nothing and takes nothing before its PIN (6f). A card
   with no owner and a PIN is the PIN's: its record, its numbers and a new PIN are
   set under the PIN given in the tap (a new PIN too, once the card is set up; a
   card not set up yet, or with a blocked PIN, takes a new PIN with none given),
   only while nothing is unspent and no change is owed, and, once it has been used, only after a held tap (8.4); its owner is
   claimed under the PIN, by six askings in a row, never in a tap that paid, and
   only while it is empty (8.3). Why not "an empty card is open", as the first
   version of this design said: a terminal holding the PIN can make a card empty
   whenever its balance fits within the day's limit, and could then set its own
   PIN and owner and load a junk piece, taking the card over for good. The table
   in section 8 is the rule worked through every command.
4. **The day is the card's.** The card keeps `now`, the time written in the
   newest Bitcoin block header it has taken (`SET_HEADER`, 5.1). `now` only
   ever moves forward, and **nothing sets it back**: not `SET_CARD`, not
   `SET_OWNER`, not a new PIN, not a reset. Up to software 1.14 `SET_CARD` with
   a different time key cleared it, as the way out of a wrongly signed time.
   There is no signed time now, and so no such way out: a forged header dated
   far ahead would freeze the clock, and what the owner can do then is set the
   limit again, which gives the day its whole limit (5.2). A header that is not
   later than `now` is simply ignored, and one that shows too little work is
   refused; no header at all leaves the card in its old window. The design
   fails closed.
5. **The window is anchored by the limit, and reset only by a newer block.**
   Setting the daily limit to a new number starts a window at `now`. A payment
   that finds `now` a day or more past the window's start begins a new one at
   `now`. Nothing else begins one: not `VERIFY_PIN`, not a new SELECT, not a
   load (`RESET_CARD` begins none: it zeroes the window with the rest of the
   record). A limit set while `now` is still 0 starts a window with no
   start, which the first header taken then anchors (6).
6. **Refuse before burning; count and burn together.** A payment that would take
   the day past its limit is refused with nothing signed and nothing burned (the
   refusal is written in the card's log, 6b). Otherwise the day's total and the
   note that the places are spent change in one transaction, made after the
   signature and before it is answered (6c, 6d).
7. **No money without a PIN.** `LOAD_PROOF` is refused on a card that has no PIN
   (`6A97`, 1.21, before anything but the lock is looked at: a card is given its
   PIN first) and without the PIN given in this tap (`6982`). Nothing waives it:
   not the owner's grant and not the tap after a payment (1.21). It asks nothing
   of the owner: a card with no owner is loaded under its PIN as any other is
   (1.22, 8.4; up to 1.21 such a card was refused, `6A90`, and this rule said "no
   money without an owner"). It asks nothing of the clock: a card that has been
   shown no block yet may be loaded, and spends its first day on trust (6). Up to
   software 1.14 it could not be loaded before a time, and this rule said so
   (`6A92`).
8. **Three tries, then blocked** and the owner unblocks it. A `CHANGE_PIN` with
   the owner's proof resets the tries and sets a new PIN, on any card with an
   owner, blocked or not. A card with no owner has no owner to unblock it: its
   blocked PIN is replaced by `SET_PIN` only with nothing unspent and no change
   owed, and its history goes with the old PIN (8.4).
9. **The owner's grant reads; it loads nothing.** `ALLOW_LOAD` (owner's proof,
   label `FoxyCard/load`) is held in transient memory for the rest of that tap,
   so a new SELECT or a reset ends it. It signs nothing and loads nothing (1.21;
   up to 1.20 it let `LOAD_PROOF` and `CLEAR_SPENT` through with no PIN). It is
   read in four places, each by its own row: the log and its receipts are read
   under it (6b, and on a card with an owner the receipts under it alone);
   `GET_INFO` says the limit on one payment to it (6a); a second payment in a
   tap that has it is not slowed (6a); and, with the PIN given in the same tap,
   `LOAD_CHANGE` takes the mint's answer under a key other than the one bound at
   the payment (8.2).
10. The rest of `FOXY-CARD-SPEC.md` 5.3: no signature without places burned;
    the PIN gate first in every state (since 1.16 at the signature, with the
    no-PIN allowance beside it: 6f); nothing loaded without a PIN set and given
    in the tap, but the card's own change, which goes back with none (8.2); a
    card with no PIN takes nothing and pays nothing (`6A97`, 1.21; from 1.16 to
    1.20 it was loaded by whoever held it); every buffer at install (but the two
    stores the first `SET_CARD` reserves) and the status byte last.

## 4. What the card stores

The card record, extended. `SET_CARD` writes the unit, the refund key, the
mint, the design and the set flag, and never the limit, the day, `now` or the
last header's hash; it clears the hardest bits, the ratchet, which is the one
way out for a card whose network has fallen under a quarter of its best (5.2),
and so does `RESET_CARD`. `SET_HEADER` writes `now` and the block fields (5.1).
Below, "the clock" is the time and the newest header's hash; "the ratchet" is
the hardest bits:

| Field | Size | |
|---|---|---|
| format | 1 | **4** since software 1.4, one signature for a payment (6c); 3 was the first form of this design and 2 the allowance draft. Not a byte of the record: `GET_CARD`'s first byte and `GET_INFO`'s byte 8 say it |
| set | 1 | 1 once `SET_CARD` has run |
| unit | 1 | sats |
| limit | 4 | the most the card signs for in one day, in sats, big-endian; **0 is no limit, and is what a new card has** |
| refund key | 33 | the key that may spend a dated piece once its date has passed, or zeros for none. Foxy's phone writes none (`FOXY-CARD-SPEC.md` 6) |
| hardest bits | 4 | the difficulty of the hardest block header the card has taken, as the header carries it (`bits`, little-endian); zeros until it has taken one. A later header must show at least a quarter of that work (5.2). Record offset 120. Written by `SET_HEADER`, only upward, and cleared by `SET_CARD` and `RESET_CARD` (5.2) |
| last header | 32 | the hash of the newest header taken, as Bitcoin shows a block hash, for the owner's screen. Offset 124. Written by `SET_HEADER` |
| (unused) | 29 | zeros. With the two rows above, the 65 bytes where the time key was up to software 1.14; `GET_CARD` still answers those 65 bytes at offset 40 |
| now | 4 | the time of the newest block header the card has taken, seconds, big-endian; 0 until it has taken one. Written only by `SET_HEADER`, and by nothing that lowers it |
| window start | 4 | when the current day began; written by the owner's `SET_LIMIT` when the day's number changes, by a payment that begins a new day, and by the first header taken after a daily limit was set while `now` was 0 (6); zeroed by `RESET_CARD`. It is the daily limit's alone (from 1.16 to 1.20 the no-PIN allowance counted in it too) |
| spent today | 4 | what the card has signed for since the window began |
| limit on one payment | 4 | the most the card signs for in one payment straight away, in sats, big-endian; **0 is no limit**. A larger payment waits (section 6a). Not in `GET_CARD`; `GET_INFO` with P1 = 1 says it |
| no-PIN allowance | 4 | **1.16.** The most the card signs for without the PIN between two givings of the PIN (1.21; a day's worth from 1.16 to 1.20), in sats, big-endian, on a card that has a PIN; **0 is none**, and then every payment needs the PIN. Record offset 201. `GET_INFO` with P1 = 1 says it, to anyone (6f). These four bytes held when the tap began, when the limit above was counted against ten seconds of the clock, and were zeros from 1.5 to 1.15 |
| no-PIN spent | 4 | **1.16.** What the card has signed for with no PIN since the PIN was last given, each payment counted as the whole of its pieces (1.21). A `VERIFY_PIN` that goes through sets it to 0, and so does a new number for the allowance; no day does (up to 1.20 it was counted in the day's window and went to 0 when the day turned). Offset 205. These four bytes held what had been signed for in that tap, and were zeros from 1.5 to 1.15 |
| mint | 1 + up to 77 | the mint's address, as text |
| design | 3 | **1.10.** The card's face: three capital letters or digits, or zeros for none (`FOXY-CARD-SPEC.md` 5.1) |

The mint is at most 77 characters, not 96 as it was (80 before software 1.10,
which put the design after it). The reason is one command: `SET_CARD` with the
owner's proof on a card that has an owner carries a proof of up to 72 bytes,
100 bytes of record, the mint and the design, and a short APDU holds 255 data
bytes in all (1 + 72 + 100 + 77 + 3 = 253).

Kept apart from the record, as the owner secret was: the **owner key**, 65
bytes uncompressed, and a flag that one has been given (`GET_INFO` byte 16). And
the **openings of the card's own change**: eight, each its amount, keyset, date,
nonce, blinding factor and blinded message, a state (none, a draft for a payment
not yet signed, or owed), and the mint's key it was bound to at the payment
(8.2).

Kept in RAM, gone with the tap (cleared when the card is deselected): whether the
PIN has been given; the **nonce** the card last gave for an owner's proof and
whether it is still live; whether `ALLOW_LOAD` has been given in this tap; and how
many times in a row a held tap has been asked for, an owner's claim (8.3) or the
history of a card with no owner (8.4). There is no per-entry count any more;
`VERIFY_PIN` can be sent as often as its sender likes, and what it changes of the
limits is only that the no-PIN allowance renews (6f). Kept in RAM, gone with the
power and not with a SELECT: the **time the terminal told** (5.3); whether this
time in the field has an entry in the log yet (6b); whether a payment has been
signed in it (so that a second is slowed, 6a); and whether a payment was signed,
an owner was claimed, or the history of a card with no owner was read in it (8.3,
8.4), which a `RESET_CARD` in the same tap does not clear.

**The curve.** The owner key is a NIST P-256 (secp256r1) public key,
signatures are ECDSA with SHA-256 in DER form, and the key is uncompressed
(`04 ‖ X ‖ Y`, 65 bytes). It has nothing to do with the card's own secp256k1
key or with Cashu. The applet sets the curve's parameters on the key itself
(`p`, `a`, `b`, `G`, `n` and a cofactor of 1) and never relies on a default.
Up to software 1.14 a second key of the same kind, the time key, was kept in
the record; there is none now, and nobody signs the clock (section 5).

Why the key is uncompressed: the card's ECDSA verifier takes a public key
through `setW`, which wants `04 ‖ X ‖ Y`, and decompressing a point on the card
is a modular square root nobody should write. Sixty-five bytes is the whole
cost.

## 5. Time

### 5.1 What the card accepts

The card's clock is the time written in the newest Bitcoin block header it has
taken. `SET_HEADER` (`36`) carries one: the 80 bytes as the network carries
them, every number little-endian. They are the version (4), the hash of the
previous block (32), the merkle root (32), the time (4, at byte 68), the
difficulty as `bits` (4, at byte 72) and the nonce (4). The card does not ask
who brings it. It believes a header for what it would cost to make, and looks at
nothing else in it: not the previous block, not the merkle root, not the
version.

Three checks, in this order. A header that fails one is refused and nothing
changes:

1. **Its own work.** The card hashes the 80 bytes twice with SHA-256 and reads
   the hash as a little-endian number, as Bitcoin does. That number must be at
   or under the target the header's own `bits` name. This is the proof of work:
   `6A93` if it is not shown.
2. **The floor.** The target must also be at or under the floor built into the
   applet, `bits` `0x17087BC0` (5.2): `6A93` if it is over.
3. **The quarter.** Once the card has taken any header, the target must be at or
   under four times the target of the hardest header it has taken: the header
   must show at least a quarter of that work (5.2). `6A93` if it does not.

`bits` is Bitcoin's short form of a target: its last byte is a size and the
three bytes before it a mantissa, and the target is the mantissa times 256 to
the power of the size less three. A `bits` no header could carry (a size of 0 or
of more than 32, or a mantissa with its top bit set, which would be a negative
target) is `6A80`. Any length of data but 80 is `6700`.

A header that passes is **taken** if its time is later than `now`. The card then
writes, in one transaction: `now`, set to the header's time; the start of the
day's window, where a daily limit is set and the window has no start yet (6); the
header's `bits`, where they are the hardest yet (record offset 120, 4 bytes, as
the header carries them); and its hash, as Bitcoin shows a block hash (offset
124, 32 bytes), for the owner's screen. A header that is not later than `now`
passes the same checks, changes nothing and is answered `9000` as a taken one
is, so a terminal that brings the header it has is never in the wrong. Either
way the answer is the card's `now`, four bytes, big-endian, so that the phone
sees what the card believes.

No key, no PIN, no owner, no record and no state the card can be in refuses a
good header: a blocked or locked card takes one, and a till may send one. There
is no nonce and no freshness check, on purpose. An old header cannot help
anybody, since it moves nothing, and a newer one that shows the work is as good
as any. So a header is a **public broadcast**: anyone may fetch one and show it,
and nothing about a tap waits on a signer. Like any command that is not the next
step of a payment, it drops a payment begun and not yet signed (6c), so a till
shows its header before it begins one.

The card's time is a block's, and a block's time is not the second. The network
lets a block be dated up to two hours ahead of its own clock, so the card's
clock can lead the true time by that much. And the clock moves a block at a
time, about ten minutes, and only when someone shows the card a header: a card
that is shown none keeps its clock where it is, and its day does not end.

On the chip a header is two SHA-256 passes, one over 80 bytes and one over 32,
and a few compares of 32 bytes: expected to be well under 20 ms. It has not been
measured (`FOXY-CARD-HARDWARE.md`).

### 5.2 The floor and the quarter, and why those numbers

The card cannot see the network, so it cannot know what a block's work is today.
It holds a header to two numbers of its own, and they do different jobs.

**The quarter.** Once the card has taken a header, every later one must show at
least a quarter of the work of the hardest it has taken (check 3). The card
keeps the hardest `bits` in its record and raises them whenever a harder header
is taken, so the bar climbs with the network and nothing lowers it. A card used
for years is held to the work of those years, and a forged header has to show a
quarter of that: about two and a half minutes of all the mining in the world, at
the work the network did when this version was written.

Why a quarter, and not all of it. The network's work is not steady. Its
difficulty fell by about a fifth, peak to trough, in the year before this
version, and by about half in 2021. A bar at the whole of the hardest header
would refuse honest blocks after any fall at all. A quarter lets the network
lose three parts in four of the work the card last saw before an honest block is
refused. If it ever did, the card would refuse every honest header: its clock
would stop and its day would not end, and nothing on the card sets the bar back
(`SET_HEADER` writes it, and only upward). That fails closed: the card signs for
no more than what is left of its day, and the owner can give it the limit again
(6).

**The floor.** The quarter needs something to be a quarter of, and the first
header a card takes has nothing before it. So the applet has a floor: `bits`
`0x17087BC0`, four times the target of the network's blocks when this version
was made (`0x17021EF0`), that is, a quarter of their work: a difficulty of about
3.3e13, about 2^77 hashes to find one header, two and a half minutes of all the
mining in the world. A header with less work than that is `6A93`, however little
the card has seen.

Why a quarter, and not less. Every card in use is already held to a quarter of
the hardest header it has itself taken, so for a card set up in this version's
time the floor and the ratchet are one bound: a new card's first header costs a
forger what every later one does. A lower floor would buy nothing but a cheaper
first header. A floor is a number fixed in the applet, and a card outlives the
year it was made in, so it could in principle be outgrown downwards: the
network's work would have to fall by three quarters from this version's time
before an honest first header was refused, which it never has (its fall in 2021
was about a half, and over the year before 1.15 about a fifth), and a fall that
deep would stall every used card's ratchet alike, which a new record remedies.
The floor does not climb with the network, so as the network grows it is a
smaller part of it: each version of the software sets it to a quarter of its own
time's, and a card made with that version starts from there.

**The floor matters for the first header only.** Once the card has taken a
header from the network, its quarter is far above the floor, and the floor has
nothing more to say. (It is the stricter of the two only until a header with more
than four times the floor's work has been taken.)

**What a forgery costs, and buys.** A header is believed for its work, whatever
else it says, so a forger pays the work and writes any time that fits in four
bytes. On a card that has taken a header from the network the work is a quarter
of a block's. On a card that has taken none it is the floor's. What it buys is
the clock moved forward to the time the forger chose. If that is a day past the
window's start, it is another day's limit, once for each header forged. If it is
years ahead, it freezes the clock: every real block is earlier than the clock
now, so none moves it, and the day does not end. The card fails closed. From then
on it signs for one more limit's worth in all, and no more, until the owner sets
the limit again, which begins a window at the clock's new time with nothing
spent (6).

The price is the same whatever the card holds. A limit worth little is far
beyond a forger's reach; one worth a great deal is only as strong as that price.
A card is better for having been shown a header at the start, because the floor's
work is the smaller of the two (the phone shows its card a block at each tap
where the card is behind it, section 9).

### 5.3 The time a terminal tells

`TELL_TIME` (`37`) carries four bytes: the terminal's own clock, seconds,
big-endian. The card keeps it in RAM for this time in the field (gone with the
power, not with a SELECT) and writes it into every receipt (6b) and into the log
entry for the time in the field (6b) as they are made, zeros where none was
told. A log entry takes the told time as it stands when the entry is begun, by
the first thing in the time in the field that is written down; a receipt, when
its payment is signed. So a terminal tells the time first.

It is trusted for nothing. It is not the day and it is not any limit, nothing on
the card is judged by it, and no key, PIN or state of the card refuses it. It is
a note, so that the owner's screen can say to the second when a payment was,
beside the block time that is the card's own: a terminal that lies here lies in
its own entries and nowhere else. It answers nothing, and is `6700` for any
length but four.

What the phone does with the clock is in section 9.

## 6. The day

**Foxy's app does not set a daily limit at launch.** The card keeps the limit and
the clock of block headers (5) all the same, and both stay available to a later
version of the app; a card whose limit is 0 has no day to bound (below), and
everything in this section is what the card does where an owner has set one.

**The window.** The owner's `SET_LIMIT` (`34`) writes the daily limit, sets the
window start to `now` and spent-today to 0 (in its eight- and twelve-byte forms
only when the day's number changes, 6a). The PIN form (`33`) does not touch the
daily limit since 1.21: it sets the limit on one payment and the no-PIN allowance
of a card with no owner (8.4), and a card with no owner has no daily limit. The
no-PIN allowance (6f) does not share this window since 1.21: it renews when the PIN
is given, and not when the day turns (from 1.16 to 1.20 it shared the window, and
both counts went to 0 when the day turned). Setting the limit needs no clock: a
card that has been shown no block yet (`now` = 0) is given its limit like any
other, and its window has no start until a header gives it one (below). At a
payment's first `SPEND_ALL_SIGN` (6c), after the PIN gate, when the limit is not 0:

1. If `now ≥ window start + 86 400`, the window is new: it will start at `now`
   with nothing spent.
2. spent today (0 in a new window) + what leaves the card for good (the pieces
   less the card's own change, below), with its carry, over the limit → `6A8F`:
   nothing signed, nothing burned, the payment given up, and the refusal written
   in the card's log (6b).
3. Otherwise, at the last `SPEND_ALL_SIGN`, the same is asked again, the
   signature is made, and in one transaction: the window start (if new) `= now`;
   spent today `+=` what leaves the card; the note that the payment's places are
   spent, with their list. The signature is made first, in memory that leaves the
   card only with the answer, and since 1.8 the places' status bytes are written
   after the transaction: see "Version 1.8" below. (In software 1.1 the slot's
   status was in the transaction, and the signature came after.)

A limit of 0 is no limit: steps 1 to 3 are skipped, nothing is counted, and the
clock is not looked at. **A new card's limit is 0, and set-up does not ask for
one**; a limit is set later from CHANGE LIMIT, to any amount, and removed the
same way (section 10).

**The clock moves a block at a time.** `now` is the time in the newest header the
card has taken, so it moves only when someone shows the card a header, and then
to that header's time. The day ends when the card is shown a header a day or more
past the window's start, and not before; a card that is shown none does not end
its day. A terminal that has the PIN and wants a second day has to bring such a
header: a real block, when the world has made one, or a forgery (5.2). It cannot
end a day without a header dated a day on, and the next payment after that
header begins the new window at the header's time.

**The first day, on trust.** A card that has been shown no header yet may be
loaded, may be given a day's limit, and may pay under it. Its window has no start
and cannot end, so what leaves the card counts up to the limit, and then the
card refuses (`6A8F`) until a header arrives. The first header taken anchors the
window at its own time (5.1), and what was spent on trust counts in that window.
That is a day on trust and no more than the limit: the card does not need a
header to be of use.

What that day is worth to a bad terminal is one limit, and one more. The header
that anchors the window is the first one the terminal shows, and a terminal can
choose an old one: any real block with the work will do, and they are all public.
The window is then anchored at that old time, and the first current block ends
it; the card has spent one limit on trust and may spend another. That is once in
a card's life, and only for a card that was spent from before it had been shown a
block. A phone with a route shows its card a block at the first tap where the
card is behind (section 9), which closes it.

**It counts what leaves the card, not the price paid.** From software 1.12 a
payment's change is the card's own (`SPEND_ALL_CHANGE`, 6d): outputs the card
builds and locks to its own key, which no terminal can take, so the day is
charged the pieces less that change, and a 300-sat payment made with one
piece of 1,000 and 700 of the card's own change costs the day 300. Change a
terminal writes back by other means the card cannot check, as the draft's
allowance could not, and it gives the day nothing back: before 1.12 that
payment cost the day 1,000. A till picks pieces as near the price as it can
(`FOXY-CARD-SPEC.md` 8.1), and the holder's phone loads a card in pieces
small enough for the limit to be useful.

**The edge of a window.** A window is a fixed day from its start, so a terminal
that straddles one boundary can take up to two days' limit in a short span. That
is the bound, and it is written down rather than hidden. (Rolling windows need a
log of spends; a fixed one needs eight bytes.)

**What a day is worth to a bad terminal.** One visit, one day's limit, in one
tap if it likes, and nothing it can send changes the day's length, the limit, or
the clock, except forward, by a header that has the work. To begin a second day
inside the first it must bring a header dated a day on from the window's start: a
real block, once the world has made one, or a forgery that costs about a quarter
of a block's work (5.2). Holding the PIN and the card does not buy that.

**What the limits need.** The day, and the limit on one payment (6a; the per-tap
limit), each bound a terminal only where the owner has set them. A new card has
neither: set-up asks for no limit, and a card with none is bound by nothing but
its balance.

**What the owner's own withdrawal does to it.** The owner's phone taking money
off the card lifts the limit with its proof, spends (under the PIN or the no-PIN
allowance, as any payment is: the owner's grant signs nothing), and puts the old
limit back in the same tap (section 9). Putting the limit back is a `SET_LIMIT`, which
starts a new window with nothing spent, so a withdrawal gives the day back its
whole limit. That is one extra day's limit, at the moment the owner is holding
the card, and no more.

## 6a. One payment

A second limit, and unlike the day's it asks no clock: **the most the card
signs for in one payment straight away.** The card refuses nothing over it. It
makes a larger payment *wait*.

Why not a window, which is what this was (ten seconds of the card's clock, and
`6A95` over it). The card has no clock that runs. Its "now" is the time of the
newest block header a terminal has shown it, which moves a block at a time,
about ten minutes, and only when someone shows it one: a window of ten seconds
on a clock like that does not end until the next block is shown, and a terminal
chooses when to show it. A limit that asks no clock has nothing to walk
forward.

**The rule.** With a limit of `L` sats and a payment of which `S` sats leave
the card for good — the pieces named at `SPEND_ALL_BEGIN`, less the change the
card makes for itself (`SPEND_ALL_CHANGE`, software 1.12; section 6d) — worked
out at the first `SPEND_ALL_SIGN`, when the change is known, and shaped
(software 1.13) so that whoever holds the card can feel that a payment was over
the limit:

    S ≤ L                waits = 0                                                (change or no change)
    S > L                waits = WAIT_OVER_SIGNS + WAIT_MORE_SIGNS × (ceil(S / L) − 2)   (10 + 4 for each further limit's worth: about seven seconds, then three each; 1.13 to 1.17 had 3 for each further one, 1.13 to 1.16 had 7 for the first, about five seconds)

less the time the change outputs the card made in this payment took, two
waits for every three outputs (software 1.14; an output is about two thirds of
a signature's work: 0.4 s against 0.6 s on the chip), never below 0. So a
payment over the limit takes the same time in the hand with change as without,
and what is felt says how far over the limit it was, which a terminal cannot
shorten by asking for change outputs: each costs the holder's hand what it
takes off. (1.13 took one wait off for every output, more than an output costs,
and a terminal could buy the wait down with outputs of a sat.) That change is
coming is the phone's to say, not the card's: the till's phone buzzes three
times as the first tap ends with change owed. "Within `L`" is `S ≤ L +
L/32`: a limit set in another money is so many sats at one moment and a price
in that money so many at another, so a payment of exactly the limit lands a
few sats over, and that is not "over the limit". So a payment within the
limit that makes no change goes at once (about two seconds in the hand, all of
it reading and signing, plus about half a second for each change output the
card makes); one over the limit about seven seconds longer, and three more for
every further limit's worth. `L` of 0 is no limit. Past 255 limits a payment waits
as 255 do (1022 waits), which is about twelve minutes and so never. (Before 1.13, 1.12
counted four signatures for every limit's worth, a part counting as one;
before 1.12 the first `L` was free and the pieces were counted whole.)

**One payment a tap at full speed.** A second payment signed in the same time
in the field waits as one over the limit does — about seven seconds, and three
more for every limit's worth of it — whether or not there is a limit, unless
the owner's grant (`ALLOW_LOAD`) is in the tap. A terminal that holds the PIN
could otherwise take a limit's worth a second for as long as the card is held,
each payment signed at once; now each costs it seven seconds, or a fresh tap,
which asks the PIN again. The owner's phone, which takes a whole card off in
more than one signature where its pieces have more than one date, gives its
grant first and is not slowed. Writing back the card's own change
(`LOAD_CHANGE`, 8.2) and a refund are loads, not payments, and are not touched by
this.

**What a wait is.** The card cannot sleep and has no timer. The one thing on it
whose duration is the chip's own, and that no terminal can shorten, is a
signature: about three quarters of a second, measured, and very steady. So the
card waits by signing, over 32 bytes of its own choosing, and throwing the
signature away. One to a command: while waits remain, `SPEND_ALL_SIGN` does
one, and answers `9000` with **two bytes**, `00 01`, "not yet", in place of
the signature. The terminal sends `SPEND_ALL_SIGN` again. When none remain,
the next one burns the pieces and answers the signature (6c). The card does
not say how many are to come: that would say how many limits the payment is
over, and so what the limit is. No
command is longer than one signature, which matters to a phone that gives up
on a card that is silent for long.

**Nothing is burned, counted or written until the wait is done.** A card
lifted in the wait has lost nothing and the terminal holds nothing. Any other
command in the wait drops the payment, as it does at any point between
`BEGIN` and `SIGN` (6c), and so does a `SELECT` or a reset; `SPEND_ALL_SIGN`
then answers `6985`, and a new `BEGIN` waits in full again. The count of waits
is in RAM and cannot be set from outside.

**Nothing is remembered from one tap to the next.** Two payments of `L` in two
taps wait nothing; two in one tap do: the second is slowed as one over the limit,
about seven seconds, by the rule of one payment a tap above (unless the owner's
grant is in the tap). Within a tap the card remembers only that it has paid, and
from one tap to the next nothing at all, on purpose: that is the whole of what
this limit is. A terminal that has the PIN can take `L` for each tap, or for each
seven seconds of one, for as long as the card is held, by many small payments or
by one large one that waits. It cannot go faster, whatever it sends and whatever
header it shows. The day's limit (6) is
the ceiling; this is the rate, and what it buys the holder is that money
leaves the card about as fast as an honest payment of that size would, so
that a card lifted when the payment ought to be over has lost about what it
ought to have paid.

The wait is charged on what leaves the card, not on the price: the pieces,
less the change the card makes for itself (6d; before 1.12 the pieces whole,
since change a terminal writes back is not something the card can check). A wallet
that wants the card held no longer than it must chooses the pieces that
overpay least.

**Setting it.** The owner's `SET_LIMIT` (`34`) with eight bytes of limit (the
day's, then this one) sets both, and twelve (1.16) add the no-PIN allowance (6f);
four bytes set the day's and leave this one. On a card with no owner the PIN form
(`33`) sets this one with four bytes, and the allowance after it with eight (8.4).
It needs no clock,
and neither does the day's (up to software 1.14 the day's did, unless it was 0:
`6A92`). Bit 3 of `GET_INFO`'s capabilities byte says the card waits and does
not refuse. The owner's phone lifts it to 0 with its proof to take money off its
own card, and puts it back, as it does the day's.

**Who is told it.** The holder, and nobody else. `GET_INFO` with P1 = 1 (or 2)
answers it at bytes 30 to 33 on a card with an owner only when the owner's grant
(`ALLOW_LOAD`) has been given in this tap, and on a card with no owner, which has
no grant to give, only to the PIN given in a tap whose history was held for (1.22,
8.4). To anyone else, the PIN verified or not, those bytes are zeros, which is
also what a card with no such limit says. (The eight bytes after them, 34 to 41,
were the window and its count and were zeros to everyone; since 1.16 they are the
no-PIN allowance and what is counted against it, and are said to anyone: 6f.) A
terminal that was told the limit would ask for just under it, again and again,
and never be made to wait. On a card with no owner a till that keeps the PIN can
hold a tap of its own and learn it there, in a tap that signs nothing (8.4).

What that buys is time, once. A terminal that has the PIN can still find the
limit by trying: a payment that is answered "not yet" is over it, one that is
signed is not, and each try is a second of the card being held. A dozen tries
close in on it. Nothing a card can do stops that short of charging for a
payment given up in the wait, which would charge a holder for lifting the
card, and lifting the card is the one thing a holder can do.

## 6b. The log

The card keeps its own account of what it has signed for and what it has
refused. It is the only record of a card's use that no terminal's honesty is
needed for, and it is what a holder reads to find out what a tap really took.

**Only the card writes it.** A payment writes it in the transaction that commits
it, which is the one that notes its pieces spent (since 1.8 their status bytes
follow it): no piece is burned that the log does not have. A payment refused for
being over the day's limit (`6A8F`) writes it and then refuses. A load
(`LOAD_PROOF`, `LOAD_CHANGE`) writes what was put on, and the giving up of an
opening of change writes its flag (8.2). No command sets it or moves it back, for
the PIN or for the owner, and `CLEAR_SPENT`, `SET_LIMIT`, `CHANGE_PIN`, `SET_CARD`
and `LOCK_CARD` leave it as it is. Two things clear it, with the receipts:
`RESET_CARD` (1.17), which makes the card a new one, and `SET_PIN` replacing a
blocked PIN on a card with no owner, so that the new PIN reads none of the old
one's history (1.22, 8.4). The counts only go up, and stop at the top of four
bytes.

**What it holds** (181 bytes, permanent; 149 before software 1.15):

| Field | Size | |
|---|---|---|
| taps | 4 | taps in which anything was signed for, refused, put on or given up, ever |
| sats | 4 | sats signed for, ever |
| refused | 4 | spends refused for being over a limit, ever |
| runs | 4 | times a third spend was refused inside ten seconds of the clock (below) |
| run start, run length | 4, 1 | the run of refusals in hand: the clock at its first, and how many |
| the last eight taps | 8 × 20 | a ring; the tap numbered n is at (n − 1) mod 8. Each: the clock when it began (4), sats signed for in it (4), pieces signed (1), spends refused in it (1), flags (1; bit 0 is the mark, bit 1 a payment that waited, bit 3 a payment with no PIN, bit 4 an opening of change given up, 1.22), pieces put on in it (1), sats put on in it (4), and the time the terminal told (4; 5.3) |

**A tap, here, is one time in a reader's field**: from the card being powered
to its being taken away (a byte of RAM that is gone with the power, and not
with a SELECT, says whether this one has an entry yet). The log counts by the
field, because that is what a person did. A tap in which a payment was over
the limit on one payment, and was waited for (6a), says so: bit 1 of its flags. A terminal that cuts the field to
begin again shows as more taps, and the counts count every one, so the ring
being pushed round hides nothing from a phone that remembers the counts it saw.

**It records what was signed for, not the price.** The card does not know the
price. It cannot say that a payment was too much; it says what left, and when.

**The mark.** A terminal that keeps to the limits is never refused: `GET_INFO`
says what the day has left (and, to the owner's grant, the limit on one payment), before anything is asked for. A
refusal is therefore a terminal that asked for more than it was allowed. A run
of **three inside ten seconds of the card's clock** (from the run's first
refusal) is one trying the limit again and again: the count of runs goes up
once, at the third, and the tap in which the third or any later refusal of the
run falls is marked. Ten seconds of the clock, and not three in a row at any
distance: a terminal that does not know of a limit and is refused once at each
of three visits is not that. The clock is block time (5), which moves a block at
a time, so in practice a run is three refusals with no newer block shown between
them. Before the card has been shown any header the clock cannot tell visits
apart at all, so there the card makes no run: every refusal begins one, and none
is marked, though each is counted and written in its tap's entry. Three visits a
week apart would read as one run of three with no clock to part them, and a false
mark is worse than none. The first header gives runs a clock to be counted by. A
card with no limit refuses nothing, and so marks nothing: the mark needs a limit
to be set.

**What a mark is not.** It is not proof of theft, and its absence is not proof
of honesty. A terminal that takes what the limits allow is never refused and
leaves no mark; what it took is in the taps and the counts all the same.

**Reading it: `GET_LOG` (`18`).** Sixteen bytes of counts (taps, sats, refused,
runs), then the taps the ring holds, newest first, twenty bytes each: 176 bytes
at the most. For whoever the card is open to. On a card with an owner: the PIN
verified in this tap, or the owner's grant (`ALLOW_LOAD`), so the owner's phone
reads it with no PIN, at once. On a card with a PIN and no owner (1.22): the PIN
given in this tap, and a held tap of ten askings, never in a tap that paid (8.4).
It says when a card was used and for how much, which a stranger's reader is not
told (`6982`). A card with no PIN has an empty log, and answers anyone. A P1 other
than 0 (the log) and 1 (the receipts) is `6A86`.


**What was put on, too.** A tap's entry is twenty bytes since 1.15 (sixteen
from 1.6): after what was signed for in it (clock 4, sats 4, pieces 1, refusals
1, flags 1) come the pieces put on in it (1, stops at 255), what they were worth
(4), and the time the terminal told (4, zeros where it told none: 5.3).
`LOAD_PROOF` writes it once for the command, after the pieces are stored, and
`LOAD_CHANGE` once for its piece. So "taps" counts every time in the field in
which anything was signed for, refused, put on or (1.22) given up.

**The clock moved twice in a tap** (flag bit 2) is gone. Up to software 1.14 the
card marked a time in the field in which a signed time was told twice, more than
two minutes apart, because a terminal walking the clock forward to turn the day
did exactly that. A terminal cannot move the clock past the present now: it moves
only by headers that have the work (5). It can still walk a clock that is behind,
one real header at a time, and turn the day with each: a card whose clock lags by
some days gives up that many more days' limit in one visit. That is open, and the
guard for it is not built. Bit 2 is never set, and the fourth count counts runs of
refusals and nothing else.

**Receipts** (`GET_LOG` with P1 = 1). For each payment the card signs, written
after the burn: the clock (4; the time of the newest block header the card has
taken), the time the terminal told (4; zeros where it told none, 5.3), what the
pieces were worth less the card's own change (4), SHA-256 of the message it signed (32), and the first
output's blinded message as the terminal gave it (33; zeros for a payment with
no outputs). 77 bytes each (73 before software 1.15). The last sixteen, in a
ring, after a count of every payment ever. Answered as the count (4) and up to
three receipts newest first, P2 back from the newest: 235 bytes at the most.
The first time is the card's own and a terminal cannot choose it; the second is
the terminal's and the card vouches for nothing in it. They are kept apart so
that the owner's screen can give the second beside the first.

The log says what left the card and when. A receipt says where it went, as far
as a card can know. Under SIG_ALL the message names every output of the one
swap the mint took the signature for, and an output is made from its
receiver's seed. Nobody can tell whose an output is by looking at it; anybody
who is later shown a wallet's seed can make that wallet's outputs again and
find this one among them, and the hash then pins the whole swap. It is what a
holder has to show for a payment they did not mean to make: it does not say
who took it, and it lets a wallet be shown to be the one, or not to be.

On a card with an owner, by the owner's grant given in this tap and nothing
else: not the PIN, which a till has (`6982`). On a card with a PIN and no owner,
which has no grant to give, by the PIN and the held tap, as the log (1.22, 8.4).
On a card with no PIN, by anyone: it has signed nothing. Nothing clears them but
what clears the log (above). The card's ring is short, and a phone that wants
more than sixteen keeps what it reads.

## 6c. One signature for a payment

Up to format 3 the card signed for each piece it paid with, most of a second
apiece on the chip, and each signature was over that piece's secret and nothing
else (NUT-11's `SIG_INPUTS`). A payment of six pieces was six signatures and
about five seconds of holding the card still; and a signature for a piece was
good for any swap at all, so whoever held the signed pieces decided where the
money went.

Format 4 signs **once**, over the whole swap (NUT-11's `SIG_ALL`):

    secret_0 C_0 ... secret_n C_n  amount_0 B_0 ... amount_m B_m

as text with nothing between the parts: each piece's secret as the card builds
it, its `C` in lowercase hex, and for each output of the swap its amount in
decimal and its blinded message `B_` in lowercase hex. The card signs SHA-256
of that (BIP-340), and the signature is the witness of the first piece; the
others carry none. That is the message as NUT-11 now reads and as CDK and
Nutshell both check it: the same swaps were put to both, and they agree on
every one (one signature for the whole is taken; a signature for each piece, a
signature over other outputs, a piece that was not signed for, and pieces
whose tags differ are all refused).

**The secret.** A slot is the same 81 bytes. The text the card builds for it
ends with the flag as its last tag, which is where a wallet's library puts it:

    ["P2PK",{"nonce":"<64 hex>","data":"<card key>","tags":[["sigflag","SIG_ALL"]]}]
    ["P2PK",{"nonce":"<64 hex>","data":"<card key>","tags":[["locktime","<date>"],["refund","<refund key>"],["sigflag","SIG_ALL"]]}]

So a card of format 4 can sign for no piece locked without the flag, and a
piece locked with it is not one a format 3 card can sign for. A card is one or
the other for all it holds; the format byte says which.

**The commands.** A payment is three or four commands, in this order and with
nothing between them. Each of them, and `SPEND_ALL_AGAIN`, is refused before
anything else is looked at with `6A97` on a card with no PIN (6f), and with `6985`
in a time in the field in which an owner was claimed (8.3) or the history of a
card with no owner was read (8.4).

1. `SPEND_ALL_BEGIN` (`22`): the places it is made of, a byte each, in the order
   the mint will be shown them. No PIN is asked here (since 1.16 it is asked at
   `SIGN`, 6f). One place or more, as many as the card has (`6700` for none,
   `6A96` for more; before 1.7 it was thirty-two at the most); each a place the
   card has (`6A83`), unspent (`6A88` for an empty place, `6985` for a spent
   one), named once (`6A80`), and of the same date as the first (`6A80`): a mint
   takes one signature only for pieces whose secrets agree in everything but the
   nonce. Nothing is held to a limit here: the day's limit and the wait are held
   to what leaves the card, which is known only at `SIGN` (since 1.12). The one
   exception is a sum of the places that wraps past four bytes: `6A94` where a
   PIN is set and was not given in this tap (the PIN's to ask for, 6f), and
   otherwise `6A8F` where a daily limit is set, written in the log (6b). Any
   opening of change made for a payment that was never signed for is let go
   (8.2). The card hashes each place's secret and `C` itself, from the slot: the
   terminal names places and never supplies a secret. Answers what they are
   worth, 4 bytes.
2. `SPEND_ALL_OUTPUTS` (`23`): the swap's outputs, 37 bytes each (amount 4, `B_`
   33), as many to a command as fit and as many commands as it takes. Each is
   hashed as its amount in decimal and its `B_` in hex. `6985` with no payment
   begun; `6985` after the card's own change (step 3), which comes after the
   terminal's outputs, and the payment is given up; `6700` for a length that is
   not a whole number of 37, which also gives the payment up.
3. `SPEND_ALL_CHANGE` (`26`, since 1.12), once for each output of change the card
   makes for itself, or not at all: the amount (4) and the mint's key for that
   amount in the payment's keyset (33, compressed; 1.21, up to 1.20 the amount
   alone). The card builds the output itself and keeps the key with it (8.2), and
   answers its blinded message (33), which goes into the message as an output.
   `6985` with no payment begun; `6700` for any length but 37; `6A80` for an
   amount of 0, for change that would come to more than the pieces, or for a key
   that does not begin `02` or `03`; `6A84` with none of the eight openings free
   (or on a card that has no room for the openings' keys); `6F00` if the chip's
   arithmetic fails. Every refusal gives the payment up.
4. `SPEND_ALL_SIGN` (`24`): `6985` with no payment begun. At the first `SIGN`: the
   PIN given in this tap, or the no-PIN allowance enough for the whole of the
   pieces (`6A94` otherwise, the payment given up and nothing written: 6f); then
   the day's limit on what leaves the card (`6A8F`, the payment given up and the
   refusal written in the log: 6, 6b); then what the payment will wait (6a).
   While it has waits to do, one of them and two bytes in answer, the constant
   `00 01` ("not yet": never how many are to come, which would say the limit),
   with nothing burned; sent again until none are left. Then the PIN or the
   allowance and the day's limit are asked again, the signature is made, and in
   **one transaction** the payment is committed (since 1.8 the places are marked
   spent after it, 6d): the day's count is charged, the no-PIN count where the
   payment was signed with no PIN, the openings of the change the payment made go
   from drafts to owed (8.2), the log gains the payment, and the signature is
   kept. `6A96` if the transaction cannot hold it, with nothing changed. Then the
   receipt (6b), and only then is the signature answered, 64 bytes.

Anything else sent between `BEGIN` and `SIGN` drops the payment, and `SIGN`
then answers `6985`: another command, another `BEGIN`, a `SELECT`, the card
leaving the field. Nothing can be read or changed under a payment that is
being hashed.

`SPEND_ALL_AGAIN` (`25`) answers the last signature the card gave: under the PIN
given in this tap (`6982`), or with no PIN where that signature was given with no
PIN (6f); then `6A88` if it has given none. A card that is taken away as it
answers `SIGN` has burned the pieces, and the terminal never heard the signature:
asked again at its next tap, it gives it. The signature is over a swap only the
terminal that set it out can make, so giving it twice gives nobody anything they
did not have. It is kept until the next `SIGN` replaces it.

`SPEND_PROOF` (`20`) is gone: `6D00`. There is no command by which this card
signs for a piece alone.

**What it buys.** One signature whatever the number of pieces, so a payment
made of many small pieces costs the card no more than one made of one large
one, and a terminal can ask for pieces that come to exactly the price instead
of over-paying and writing change back at a second tap. And the signature
names where the money goes: signed pieces taken from a terminal, or seen on
the air, are worth nothing to anyone who cannot make those very outputs.

**What it does not.** The terminal chooses the outputs, and the card cannot
tell whose they are. A terminal built to cheat still takes what it asks the
card to sign for, up to the limits, exactly as before; the limits and the log
are what bound and show that, and they are held to the sum. And a signature
that binds its outputs is good only while the mint will still make that swap:
if the mint retires the keyset the outputs are on, or raises its fee, between
the signature and the swap, the pieces cannot be swapped by the card's
signature at all (a card with a refund key: its owner's phone takes them back
after their date, as section 6 of `FOXY-CARD-SPEC.md` has it).

Measured on the chip, with 1.7: its transaction held the burn of eleven
pieces and not of thirty-two (`SIGN` answered `6A96`, nothing burned, no
signature given). 1.8 takes the burn out of the transaction (6d). Not yet
measured: how long `BEGIN` takes for a hundred places (it is some kilobytes of
SHA-256) beside the one signature.

## 6d. Time on the card

How long a card has to be held is most of what a person knows of it, and
almost all of it is the card's own work: every command is a round trip of a
few hundredths of a second, and anything the applet does byte by byte in
bytecode is slow. Measured on the chip, through a phone: a signature is three
quarters of a second and cannot be made less; taking in a payment's pieces
(`SPEND_ALL_BEGIN`) was 79 ms a piece, two seconds for twenty-five; an output
was 34 ms; a load was 60 ms a piece; and reading every piece, three to a
command, was up to 0.7 s. Version 1.6 changes none of what the card does for
a piece and most of how long it takes.

- **The text of a piece is written once, at loading.** A payment's message is
  each piece's secret and its C as text, and the two parts of that which
  differ from piece to piece are its nonce and its C in hex. `LOAD_PROOF`
  writes those beside the slot (`slotHex`, 130 bytes a place), before the
  slot's data and its status byte, so a tear leaves the place empty as it
  always did. `SPEND_ALL_BEGIN` then turns nothing to hex for a piece: it
  builds the rest of the secret once (everything after the nonce is the same
  for every piece of one payment: the card's key, and the one date and refund
  key), and hands the hash four spans a piece.
- **An output is one span to the hash**, its amount in decimal ending where
  its `B_` in hex begins; an amount under 32,768 is divided as a short.
- **A brief listing** (`GET_PIECES`, P2 = 1): what a till needs to choose
  pieces is what each is worth, its date and its keyset, sixteen bytes, and
  not its nonce and its C. Fourteen places to a command where the whole form
  has three. The pieces it chooses it then asks for whole, by slot, three to
  a command (P2 = 2), and checks each against what the listing said.
- **Three pieces to one `LOAD_PROOF`.** Each is taken as one alone would be,
  in order (the rule is in section 8).

Loading is a little slower for a piece than it was (it now writes the hex)
and quicker in all (a third of the commands). Loading is done by a card's
holder at home; paying is done at a till.

**Version 1.7: 128 places, and a shorter listing.** A price typed in dollars
is an odd number of sats, so every payment needs small pieces of its own, and
a card that is to pay several prices in a row exactly has to hold several of
every small size: eight each of the eleven sizes from 1 to 1,024 are
eighty-eight pieces. Sixty-four places did not hold that and the rest of a
card's money, so there are 128. Three things follow, and nothing the card
signs or stores for a piece is different:

- **A place's number is seven bits.** The tag of `GET_PIECES` was
  `(state << 6) | place`; it is now the place, with `0x80` where the place is
  spent. `next` ends at 128.
- **The short listing** (`GET_PIECES`, P2 = 3) takes the place of the brief
  one (P2 = 1, which this card refuses with `6A86`: its tags could not name
  these places). A till reads what a card holds at every payment, and a deep
  drawer is a hundred pieces. For each unspent place: the place (1), with
  `0x80` where its keyset (8) and date (4) follow, which they do in the first
  entry of every answer and wherever they are not those of the entry before;
  then the power of two it is worth (1), or `FF` and its amount (4) where it
  is worth some other amount. Spent and empty places are not listed
  (`GET_INFO` counts them). A card whose pieces are of one keyset and one date
  and are powers of two is two bytes a piece after the first: all 128 places
  in two answers. The answer is gathered and sent at once, where the brief
  listing sent each entry by itself, which was most of what it took.
- **A payment may name every place the card has**, where it was thirty-two: a
  deep drawer holds its money in small pieces, and a payment of most of a
  small card is many of them. The burn is still one transaction.

Bit 5 of the capabilities byte says a card has more than sixty-four places,
seven-bit tags and the short listing; `GET_INFO`'s count of places is 128.

**Version 1.8: the burn is not in the transaction.** A payment burned its
pieces inside the transaction that committed it, a status byte for each. A
chip's transaction holds only so much, and how much is not something a
simulator has: jCardSim's has no size at all. On the card this runs on, a
payment of eleven pieces went through and one of thirty-two was refused
(`6A96`, by the `TransactionException` the code was already catching, with
nothing burned). A deep drawer is a hundred pieces, and taking a card's money
off is all of them.

So the transaction is now the same size whatever the payment:

1. The signature is made, into the answer's buffer. Nothing is written yet.
2. The places are written to `burnList` (how many, then a byte each), outside
   any transaction. Nothing reads that list unless `burnPending` is set.
3. One transaction: the day's charge, `burnPending = 1`, the openings of the
   card's own change made for this payment taken from drafts to pending (one
   write of eight bytes; up to 1.20 the transaction also set a note that let
   the next tap load with no PIN, which 1.21 took away: 8.2), the log, and
   the signature kept for `SPEND_ALL_AGAIN`. **From the moment it commits, the
   pieces are spent.**
4. `finishBurn`: each place in the list has its status byte set to spent, one
   plain write each, and `burnPending` is cleared (from 1.9 in a transaction
   of its own, one byte: a commit is the one write a chip must have made
   lasting before it goes on, so the status bytes are in place before the
   note that they are owed is gone).
5. The receipt, in a transaction of its own, and then the answer.

A card that leaves the field before step 3 commits has a list nobody reads,
and has burned nothing. One that leaves after it, with some status bytes not
yet written, finishes step 4 at the start of its very next command, before
that command (or `SELECT`) looks at anything: `process` begins with it. A
byte written twice is the same byte, so step 4 may be cut short and begun
again any number of times. There is no state in which the payment is
committed and one of its pieces can still be read as unspent by a command,
and none in which a piece is marked spent for a payment that did not commit.

Bit 6 of the capabilities byte says a card burns so, and a terminal may then
name as many places in one payment as the card has. A terminal that reads a
card without it should name few: eight, by what was seen on the chip.

## 6e. The PIN, sealed

A PIN typed at a terminal crossed the air to the card as it was typed, and
whoever was listening to the tap had it; the PIN, and later the card, is all a
thief needs. A bank card's PIN is enciphered to a key of the card's. From 1.9
so is this one, and bit 7 of the capabilities byte says so.

**The PIN key.** A second secp256k1 key pair, made on the card at install and
used for this and nothing else. It is not the key the card signs payments
with: a terminal puts points of its own choosing to this key, and whatever
that could ever tell it, it tells it nothing of the key the money is locked
to. `GET_NONCE` with P1 = 1 answers, on any card and with no PIN,

    nonce (16)    fresh at every asking, and good once
    PIN key (33)  compressed
    signature (64) BIP-340, by the card's own key, over
                  SHA-256("FoxyCard/pinkey" || the PIN key)

A terminal that knows the card's key (it reads it at every tap, and the
owner's phone has it on file) holds the PIN key to it by the signature, which
is made once, at the first asking, and kept.

**A sealed command.** `VERIFY_PIN`, `SET_PIN` and `CHANGE_PIN` with P1 = 1
carry, in place of their clear data,

    E (65)        the sender's public key for this one message, 04 || X || Y
    ct (n)        the data under a keystream
    tag (16)

    secret      = the x of the point E and the PIN key share (32 bytes)
    block(i, m) = SHA-256("FoxyCard/seal" || i (1) || secret || E || nonce
                          || the instruction (1) || m)
    tag         = the first 16 bytes of block(0, ct)
    keystream   = block(1) || block(2) || ...

The card checks the tag before it opens anything. The sender makes a new key
pair for every message, so no two keystreams are the same; the nonce is the
card's, so what was recorded at one tap cannot be played to the card at
another; and the instruction is in the sum, so an envelope made for one
command is none for another.

**What is sealed** is the clear form's data with the PIN as a block of nine
bytes: its length (4 to 8), the PIN, and zeros to eight. An envelope is the
same length for a PIN of four digits as for one of eight. For `VERIFY_PIN`
and `SET_PIN` the block is all of it. For `CHANGE_PIN` it follows the owner's
proof, which is over the PIN itself and the same nonce, exactly as in the
clear form: one asking for a nonce serves the proof and the seal, the seal
leaves the nonce for the proof, and the proof uses it up.

**What a failure costs.** With no nonce asked for in this tap, `6985`, and
nothing else. An envelope that does not open (a point that is no point, a tag
that is not this sum's) has used the nonce up, and on `VERIFY_PIN` it **costs
a try of the PIN**, as a wrong PIN does: without that a terminal could put
points to the PIN key without end. On `SET_PIN` and `CHANGE_PIN` it is
`6A80` and changes nothing. Those two have no try to cost, which leaves a
terminal free to put points to the PIN key through them: were the chip's key
agreement not to check that a point is on the curve, enough askings could
find the PIN key, and with it the PIN of any tap that was also recorded. The
key the card signs with is another key, and nothing but a PIN is sealed to
this one. A block that is not a block is `6700`.

**Its working room is not its own.** A sealed command needs ninety-nine bytes
of working memory, and uses the first ninety-nine of the scratch the applet
already had; its key agreement is the signer's own. As first built it had an
array and a key agreement of its own, the applet then asked for 1,030 bytes of
memory that is cleared when another applet is chosen where it had asked for
931, and the chip would not install it (`6F00`). No simulator has a limit
there. A test now adds up every transient array and every crypto object the
two classes make, and fails on any more than the build that is known to
install.

**What it is for, and what it is not.** It keeps a PIN from somebody
listening to a tap, now or with a recording later. It does nothing about the
terminal the PIN was typed on, which has it; and a false card can still ask
for a PIN, signing a PIN key of its own with a key of its own. The clear forms
remain, for a terminal that knows no other: a card cannot unsend a PIN that
was sent to it in the clear, and refusing one would only add a failure to the
exposure. A phone that knows the sealed form uses no other with a card that
has it.

## 6f. The no-PIN allowance (software 1.16), and a PIN first (1.21)

**Every card has a PIN before anything goes on it (1.21).** A card with no PIN is a
card not set up: it refuses `LOAD_PROOF`, `CLEAR_SPENT`, `LOAD_CHANGE`, every
payment command (`22` to `26`), an owner's claim (`SET_OWNER` on an open card)
and (1.22) the let-go form of `RESET_CARD` with `6A97`, a word nothing else
answers, before anything else of the command is looked at (the let-go looks at its
number first, `6A83` for a number past 7, and the load commands at the lock,
`6986`). `SET_PIN` stays open
on a card with no owner and nothing on it, so a card's first steps are `SET_PIN`,
`VERIFY_PIN` and the record, then the claim (8.3) or, for a holder with no phone,
the first load (8.4). `GET_INFO` says it as it always did: byte 7, the PIN state,
is 0 for none, 1 for set and 2 for blocked, and a 0 is a card that holds nothing
and pays nothing. No capability bit changes (all eight are taken); the version
says which rule a card keeps. From 1.16 to 1.20 a card could be set up with a
record and an owner and no `SET_PIN`, and then paid without one, and was loaded
by whoever held it; that card cannot be made now, and the paragraphs below that
speak of it are marked as of those versions.

**A card that has a PIN has a third limit, the no-PIN allowance:** how many sats
it will sign for *without* the PIN between two givings of the PIN. Only payments
signed with no PIN count against it, and each counts as **the whole of its
pieces**, the card's own change included (1.21; up to 1.20, what left the card
for good). Change is only the card's once a till has taken the swap to the mint
and the piece is written back, and a reader with no PIN that names a large piece
and takes almost all of it as change, then never swaps, has put the whole piece
out of the card's reach for the price of a few sats of allowance; counted whole,
what a reader with no PIN can put out of the card's reach, by paying or by
stranding, is the allowance and no more. With the PIN given in this tap a
payment is as it was, bounded by the daily limit and counted by nothing here. A
PIN set on a card that had none leaves the allowance at 0, so that every payment
asks for the PIN until a figure is set. The figure is the holder's: the phone
asks for it at set-up, after the PIN, in the holder's own money (two dollars
unless the holder says otherwise), and gives the card the sats it comes to: on a
card with an owner by the owner's proof (`34`, twelve bytes), on a card with no
owner under the PIN (`33`, eight bytes: 8.4). The card stores sats and knows
nothing of dollars.

**The record.** Eight bytes that were a window and a count of the limit on one
payment until 1.5, and zeros from then to 1.15, are the allowance (offset 201,
four bytes, big-endian, sats) and what has been signed for with no PIN since the
PIN was last given (offset 205, four bytes). `GET_INFO` with P1 = 1 answers them
at bytes 34 to 37 and 38 to 41, **to anyone**: a till needs them to know whether
to ask for the PIN, and nothing is learned from them that trying would not tell.
The limit on one payment (bytes 30 to 33) is still the holder's alone (6a).

**It renews when the PIN is given (1.21).** A `VERIFY_PIN` that goes through sets
what has been signed for with no PIN to 0; no day does, and no block header. So a
card in a stranger's hands gives up one allowance at most, however long they keep
it, and the holder fills it again by giving the PIN at any till. The count also
goes to 0 when the allowance is given a new number ("Setting it", below). The applet's
constant `ALLOWANCE_RENEWS_ON_PIN` is true for this rule; false would put back the
rule of 1.16 to 1.20, under which the allowance shared the day's window with the
daily limit, both counts went to 0 when the day turned, and a card that had seen
no header counted from set-up until one anchored the window.

**The gate.** `SPEND_ALL_BEGIN` asks nothing of the PIN, but for a sum that wraps
(6c): a payment begun is of no use to anyone until it is signed. `SPEND_ALL_SIGN`
asks at its first answer, before the limits:

| PIN state | Signs when |
|---|---|
| none set | never (1.21: `6A97` at every payment command; from 1.16 to 1.20, always, up to the daily limit and the wait) |
| set, and given in this tap | always, up to the daily limit and the wait |
| set, not given, not blocked | what has been signed for with no PIN since the PIN was last given, plus the whole of this payment's pieces, is no more than the allowance. A sum past what four bytes hold is over |
| set, not given, and the allowance is 0 | never, even for a payment that takes nothing |
| blocked | never: a blocked card has no allowance until its PIN is replaced (the owner's `CHANGE_PIN`, or on a card with no owner `SET_PIN`: 8.4) |

Otherwise the card answers **`6A94`**, gives the payment up (a `SPEND_ALL_SIGN`
after it is `6985`, as after a refusal for the day) and leaves everything as it
was. **Over the allowance the PIN is asked for the WHOLE payment**: a payment is
never split into what the allowance covers and what it does not. With the PIN the
payment is as it always was. A refusal for want of the PIN writes **nothing** in
the card's log: it is neither a refusal over a limit nor part of a run of them,
and three of them in ten seconds mark no tamper. The PIN is asked before the
limits, so that a reader with no PIN cannot fill the log with refusals; a payment
within the allowance and over the day's limit is refused `6A8F` and written down
as any other. The word `6A94` is also `LOAD_PROOF`'s and `LOAD_CHANGE`'s (a piece
already on the card) and the let-go's (8.2); the commands are never confused. The
daily limit and the limit on one payment apply to every payment as they did: a
payment signed with no PIN within the limit on one payment waits for nothing, and
one over it waits as ever; a second payment in the same time in the field is
slowed as ever.

The owner's grant (`ALLOW_LOAD`) signs nothing, as it never did: a payment is the
PIN's, or the allowance's. A payment with no PIN whose sum wraps is `6A94` at
`SPEND_ALL_BEGIN`, which is the one refusal that command makes for the PIN.

**The last signature again.** `SPEND_ALL_AGAIN` gives the last signature to a
reader with no PIN if it was given with no PIN, so that a till whose answer was
lost does not need a PIN it never needed; one that was given with the PIN is still
the PIN's (`6982`). (From 1.16 to 1.20, on a card with no PIN, it was open; a card
with none has signed nothing now, and answers `6A97`.)

**The log.** Bit 3 of a tap's flags (`0x08`) says a payment in the tap was signed
with no PIN under the allowance, so that the holder's phone can say "no PIN" beside
it. (From 1.16 to 1.20 a payment on a card with no PIN was not marked: there was
no PIN to be without.) Entries are twenty bytes still, and receipts are as they
were.

**Setting it.** On a card with an owner, `SET_LIMIT`, the owner's form (`34`),
takes twelve bytes: the day's limit, the limit on one payment and the allowance,
then the proof as ever. A number that does not change keeps its window and its
count, as eight bytes already promised: a new allowance counts from nothing; a
new day's limit begins the day's window again at `now` with nothing spent in it,
and leaves the allowance's count as it is (1.21; from 1.16 to 1.20 it took both
counts to 0). The four- and eight-byte forms leave the allowance as it is. A
twelve-byte form with a zero in the allowance is the eight-byte form, and the
allowance off. On a card with no owner the PIN form (`33`) sets it: eight bytes,
the limit on one payment and then the allowance, and the count goes to 0 whether
or not the number changed (8.4). (From 1.16 to 1.20, on a card with an owner and
no PIN, a twelve-byte form with a number in the allowance was `6985`; no such card
can be made now, every card with an owner having a PIN, and the refusal is gone
with it.)

**Adding a PIN** to a card with an owner is gone (1.21). From 1.16 to 1.20,
`SET_PIN` on a card that had an owner and no PIN, with the owner's grant in the
tap, set one (ADD PIN). An owner is now claimed only under a PIN (8.3), and
nothing takes a PIN away but `RESET_CARD`, which takes the owner with it, so no
card has an owner and no PIN: `SET_PIN` on a card with an owner is `6A91`, grant
or none, clear or sealed, and the owner uses `CHANGE_PIN`.

**A card with no PIN takes nothing (1.21).** It refuses `LOAD_PROOF`,
`CLEAR_SPENT` and `LOAD_CHANGE` with `6A97`, to everyone, grant or none, and so
holds nothing; its log is empty and is shown to whoever holds it (`RESET_CARD`,
the one way back to no PIN, clears the log). `LOCK_CARD` wants a PIN, and a card
with none cannot be locked. A card with no PIN and no owner is open, as the card
out of its packet is, and `SET_CARD` is written to it by anyone. (From 1.16 to
1.20 a card with no PIN and an owner was loaded and freed by whoever held it, and
paid for whoever held it.)

**What it bounds.** A terminal that can tap the card with no PIN gets the
allowance once, and no more until the PIN is given again; the day's limit stays
the ceiling for everything. It buys a till one tap that pays, and a PIN pad only
when a payment is over what the card will sign for unasked. A thief with the card
has one allowance, however long they keep it, until the holder sets it to nothing
or has the card blocked; the holder chooses the figure. (From 1.16 to 1.20 it was
an allowance a day, renewed only when the card was shown a block header a day past
its window's start.) A card of 1.12 to 1.15 keeps asking for its PIN, and a phone
must go on working with them: the software number says which is which.

## 7. The owner

### 7.1 The key

The owner's phone works out a private key from its words, per card, **in its
native side only**:

    k = HMAC-SHA256( key = the 64-byte BIP-39 seed,
                     message = "FoxyCard/owner" ‖ 0x00 ‖ the card's 33-byte compressed public key )
        taken mod n, n being the order of P-256

and if the result is 0, made again with a counter byte (`0x01`, then `0x02`, and
so on) added to the end of the message. It gives the card the public half,
uncompressed, with `SET_OWNER`. Restoring the twelve words on another phone makes
the same key, so that phone is the owner of every card this one was. The card's
key is in the message, so two cards of one owner have two keys. The private key
is never sent anywhere; the card holds only the public one and reads it out to
nobody (`GET_INFO` byte 16 says only whether it has one).

**The page never holds the key or any secret derived from the seed.** The
phone's native side offers the page exactly two things for an owner: the owner
**public** key for a card, and a signature for one of a fixed list of labels
over `label ‖ nonce ‖ value`. It signs nothing else (section 9). This is the
draft's derivation, kept, used as a key and kept native, where the draft handed
the page a secret.

### 7.2 The proof

Challenge and response, as the draft had it, with a signature where the draft
had a hash:

1. `GET_NONCE` (no data, no PIN; `6A90` on a card with no owner) answers a fresh
   random 16 bytes, held in RAM. It is gone with the tap. Each nonce gets one
   try, right or wrong: a wrong proof uses it up. Asking again replaces it. (With
   P1 = 1 it is the form that seals a PIN, on any card, 6e; any other P1 is
   `6A86`.)
2. The proof is an ECDSA (P-256, SHA-256, DER) signature by the owner key over

       label ‖ nonce (16) ‖ value

   verified on the card with `ALG_ECDSA_SHA_256` on the owner key, with the
   label and value of the command:

| Command | Label (ASCII) | Value |
|---|---|---|
| `CHANGE_PIN` | `FoxyCard/change-pin` | the new PIN's bytes |
| `SET_LIMIT` (owner form, `34`) | `FoxyCard/set-limit` | the new limit, 4 bytes, big-endian; or 8, the day's and then the tap's (6a); or 12, those and the no-PIN allowance (6f) |
| `SET_OWNER` (on a card that has an owner) | `FoxyCard/set-owner` | the new owner key, 65 bytes |
| `SET_CARD` (on a card that has an owner) | `FoxyCard/set-card` | the data sent: unit, refund key, the 65 bytes where the time key was, mint length, mint. A phone of software 1.15 sends those 65 bytes as zeros and the card ignores them; a phone of an earlier software sends a key, and the card ignores that too. The proof is over the bytes as sent, either way |
| `ALLOW_LOAD` | `FoxyCard/load` | nothing |
| `LOCK_CARD` | `FoxyCard/lock` | nothing |
| `RESET_CARD` (1.17) | `FoxyCard/reset` | nothing; with P1 = 1 (1.22), the opening's number (1) |
| `SET_TAG` (1.19) | `FoxyCard/tag` | one byte: `00` tag on, `01` tag off |

So a proof for one command, or one value, is no proof for another, and an old
or used proof fails.

The data of an owner's command is `proof length (1) ‖ proof (DER) ‖ the value`
(for `ALLOW_LOAD`, `LOCK_CARD` and `RESET_CARD`'s reset, the proof alone; for
`RESET_CARD`'s let-go and `SET_TAG`, the proof and then the one byte it was made
over), so the proof sits first and the value after it is exactly what the proof
was made over.

**Refusal.** `6A91` when no nonce was asked for in this tap, or the signature is
not the owner's. A wrong proof costs no PIN tries, changes nothing and does not
end the PIN session. `6A90` when the card has no owner.

**No PIN.** `CHANGE_PIN`, the owner's `SET_LIMIT`, `SET_OWNER`, `SET_CARD`,
`ALLOW_LOAD`, `SET_TAG` and, on a card that is not locked, `RESET_CARD` need no
verified session and no old PIN: the owner's phone does not
know the PIN, and the cards that most need an owner's `CHANGE_PIN` are the
blocked and the forgotten, where there is no old PIN to give. `LOCK_CARD` keeps
the draft's rule (a PIN set and verified, and the proof), and the phone still
never carries instruction `50`.

**Fallback, documented and not built.** If the card in hand turns out to have no
usable ECDSA verifier (the J3R180 is expected to; it is not yet measured), the
draft's proof (a 32-byte owner secret given once at set-up, and
`SHA-256(label ‖ secret ‖ nonce ‖ value)` compared in constant time) stands in,
labels, nonce and all. (Up to software 1.14 the time signature had no such
fallback and had to verify. There is none now, and the owner's proof is the one
thing the verifier is for.)

### 7.3 Who is the owner

Whoever's words derived the key on the card. For a holder with a phone, their
own. For a holder without one, whoever's phone the holder lets claim it: a trustee
who, with the card in hand, can do anything, and without it, nothing. The claim
says so in as many words (section 10). Since 1.22 a holder with no phone need
have no owner at all (8.4): the phone that sets the card up and loads it does not
become its owner.

An owner can be replaced only on a card with nothing unspent, and only with the
old owner's proof (`SET_OWNER` refuses a funded one with `6A8D`, and refuses with
no proof `6A91`), because the only thing that could authorise a new owner is the
old one. A card whose owner has lost their words **cannot be taken over**, empty
or not: that is what the owner's words are for, and what the screen at set-up
says.

A card with a PIN and no owner may hold money (1.22): the card of a holder with
no phone, loaded under its PIN by whichever phone sends it money (8.4). It has no
owner to change its PIN or unblock it, and its numbers, its record and its PIN are
the PIN's, while it holds nothing and is owed nothing. It is tied to an owner, if
the holder wants one, by the claim, which wants it empty (8.3). Up to 1.21 such a
card could not be loaded (rule 7 said "no money without an owner"), and was only
an unfinished set-up.

## 8. Commands

Class `B0` (any other class is `6E00`, and an instruction the card does not have
is `6D00`). The instructions of software 1.23, by byte; a row in **bold** is new
or changed from `FOXY-CARD-SPEC.md` 5.2's first form and the allowance draft. The
answers are listed in the order the card checks them. A word the row does not
explain is in the table of status words below.

| INS | Command | Needs | Data sent | Answers |
|---|---|---|---|---|
| `01` | **GET_INFO** | nothing | | 30 bytes: the draft's 17 (version 2, slots, unspent, spent, empty, capabilities, PIN state, format, tries left, locked, record set, limit 4, has owner 1), then `now` (4), window start (4), spent today (4), then byte 29: `01` while change is owed to the card (an opening of its own change waits for its piece, 8.2) and `00` otherwise, said to anyone (1.21; up to 1.20 it said whether this tap might load with no PIN). With **P1 = 1**, 42 bytes: those thirty, then the limit on one payment (30 to 33), the no-PIN allowance (34 to 37) and what has been signed for with no PIN since the PIN was last given (38 to 41), 4 each (6a, 6f). With **P1 = 2** (1.19), 43 bytes: those 42 and byte 42, `00` when the NFC tag is on, `01` when it is off, said to anyone (SPEC 5.8); the answer to P1 = 1 is the same whatever the tag's state. Any other P1 is the thirty bytes. The limit on one payment is said only with the owner's grant given in this tap, or, on a card with no owner, to the PIN given in a tap whose history was held for (1.22, 8.4), and is zeros to anyone else (6a); the allowance and its count are said to anyone (6f). Up to 1.15 bytes 34 to 41 were zeros. It refuses nothing. **1.23:** byte 8 is the card's format worked out at each asking, `04` where the table holds no keyset of the other unit and `05` where it does (8.5), in every form; with **P1 = 3**, 51 bytes: those 43, then the book's format (43: `01`, `00` with no book), the rate (44 to 47, told as the limit on one payment is and zeros to anyone else), the entries in use (48), those without the mark (49) and the last payment's unit (50: `80` the record's, `A0` the other, `00` none); every figure is cents |
| `10` | GET_PUBKEY | nothing | | the card's 33-byte compressed key. `6F00` if the chip gives the key in a form the card does not know |
| `11` `12` `13` `14` | GET_BALANCE, GET_PROOF_COUNT, GET_PROOF, GET_SLOT_STATUS | nothing | `13`: P1 = the slot | as before. `GET_PROOF` gives the 82-byte slot: `6A83` for a slot past 127, `6A88` for an empty one. `GET_SLOT_STATUS` gives 128 bytes. **1.23:** `GET_BALANCE` is the sum of the unspent pieces of the record's unit alone (every piece on a card of one unit); with **P1 = 1**, a count of units held (1), then for each, sats first, its code (1), pieces (1) and sum (4), 13 bytes at the most; a piece whose keyset is in no entry is in no sum |
| `15` | AUTH | nothing | 16 random bytes | 16 of the card's own and a signature. `6700` for any length but 16 |
| `16` | **GET_CARD** | nothing | | format, record set, unit, limit (4), refund key (33), then 65 bytes at offset 40: the hardest header's `bits` (4), the newest header's hash (32) and 29 zeros (a time key up to software 1.14), mint length, mint, and the design (3, zeros for none). **1.23:** byte 0 is the card's format (4 or 5, as `GET_INFO` byte 8), and the first four of the 29 zeros hold the rate, which this answer masks as zeros; with **P1 = 2**, the table: a count (1) and each entry in use as kept (9), 55 bytes at the most, `00` alone with no book; any other P1 is P1 = 0 |
| `17` | **GET_PIECES** | nothing | P1 = the first slot to report | one page: the first slot the page does not cover (1), then for each slot in the range that is not empty a tag (1) and, for an unspent slot, its 81 bytes (8.1). `6A83` for a P1 of 128 or more. With **P2 = 2** and one to three slot numbers as data: each of those slots whole (status and its 81 bytes), in the order asked; `6700` for none or more than three, `6A83` for a slot past 127 (8.1). With **P2 = 3**, the short listing: for each unspent slot its number (with `0x80` where its keyset and date follow), then the power of two it is worth or `FF` and its amount (6d); `6A83` for a P1 of 128 or more. Any other P2 is `6A86` (P2 = 1 was the brief listing of a card of sixty-four places, 1.6) |
| `18` | **GET_LOG** | P1 = 0, the log: on a card with an owner, the PIN verified in this tap or the owner's grant. P1 = 1, the receipts: on a card with an owner, the owner's grant only. On a card with a PIN and no owner (1.22), both: the PIN given in this tap, and held for (8.4). On a card with no PIN, anyone (its log is empty: it has taken and paid nothing) | P2 with P1 = 1: how many receipts back from the newest to begin | the card's own log (6b): 16 bytes of counts, then up to eight taps of 20 bytes, newest first (16 bytes before software 1.15); bit 3 of an entry's flags (1.16) says a payment in the tap was signed with no PIN under the allowance, and bit 4 (1.22) that an opening of change was given up in it. With P1 = 1: the count of payments (4), then up to three receipts of 77 bytes, newest first (73 before 1.15). In order: `6A86` for a P1 other than 0 and 1 (it neither counts nor ends the history's held count); with an owner, `6982` without what the row needs; with no owner, `6982` with the PIN not given, `6985` in a time in the field in which a payment was signed (before any asking is counted), then `6A98`, keep holding, with no data, to each of the first nine askings in a row, a signature's work each, and the answer at the tenth; once answered, the log and the receipts are open to the PIN for the rest of that time in the field, and no payment command is taken in it. **1.23:** bit 5 of an entry's flags is the other unit; with **P1 = 2**, the four counts and the other unit's count (4) before the entries, 180 bytes at the most; with **P1 = 3**, the receipts each followed by its unit (`80`, `A0`, or `00` before the book), 78 bytes each; P1 = 2 is P1 = 0's to read and P1 = 3 is P1 = 1's, and `6A86` is for a P1 over 3 |
| `19` | **GET_CHANGE** (1.12) | nothing: no PIN | P1 = the page, from 0 | the openings of the card's own change not yet loaded, three to a page: a count (1), then for each (1.21) its number (1, the P1 of `53`), amount (4), keyset (8), date (4), blinded message `B_` (33, compressed) and the mint's key it was bound to at the payment (33, compressed), 83 bytes. An opening whose piece is on the card by now is let go first. Never the nonce or the blinding factor: up to 1.20 a record was 80 bytes, the amount, keyset, date, nonce and blinding factor, and any reader could finish the piece (8.2). It refuses nothing |
| `1A` | GET_DLEQ (1.20) | nothing: no PIN, any state | P1 = a place (0 to 127); P2 = a second place, or `FF` for none | for each place asked, in order: `01` and the 96 bytes `e ‖ s ‖ r` of the mint's proof kept with the piece if the place is unspent and its record is not zero, otherwise the single byte `00`. At most 194 bytes. `6A83` for a place there is not, before anything is answered. The card does not check what it gives (SPEC 5.9) |
| `20` | ~~SPEND_PROOF~~ | | | gone with format 4 (6c): `6D00`. Up to format 3: P1 = the slot, the 64-byte signature over that piece's secret alone |
| `22` | **SPEND_ALL_BEGIN** | a PIN set; no claim and no read of the history in this time in the field; nothing more (since 1.16 the PIN is asked at `24`; up to 1.15 it was asked here) | the places, a byte each, 1 to 128 | what they are worth (4). In order: `6A97` with no PIN (1.21) and `6985` in a time in the field in which an owner was claimed (8.3) or a card with no owner's history was read (8.4), as for every command `22` to `26`; `6700` none; `6A96` more than 128; for each place `6A83` out of range, `6A88` empty, `6985` spent, `6A80` named twice or of another date than the first; then, for a sum that wraps past four bytes only, `6A94` with a PIN set and not given (6f), or `6A8F` where a daily limit is set, written in the log (6c). Lets go any opening of change made for a payment never signed for. **1.23:** for each place, after its `6A80`, **`6A9A`** where it is of another unit than the first place, or its keyset is in no entry (the first place too): nothing hashed, no payment begun (8.5) |
| `23` | **SPEND_ALL_OUTPUTS** | a payment begun, and nothing sent since but these | 37 bytes for each output: amount (4), blinded message (33) | nothing. `6A97`, `6985` as `22`; `6985` with no payment begun; `6985` after the card's own change (`26`), the payment given up; `6700` for a length not a whole number of 37, the payment given up |
| `26` | **SPEND_ALL_CHANGE** (1.12) | a payment begun, the terminal's outputs given | the amount (4) and the mint's key for that amount in the payment's keyset (33, compressed): 37 bytes (1.21; up to 1.20 the amount alone) | one change output the card makes for itself, locked to its own key: the blinded message (33). The opening (amount, keyset, date, nonce, blinding factor and the blinded message) and the key are kept until its piece is loaded (8.2). `6A97`, `6985` as `22`; `6985` with no payment begun; `6700` for any length but 37; `6A80` for an amount of 0, for change that would come to more than the pieces, or for a key that does not begin `02` or `03`; `6A84` with no opening free, or on a card that has no room for the openings' keys; `6F00` if the chip's arithmetic fails. Every refusal but the first three gives the payment up |
| `24` | **SPEND_ALL_SIGN** | a payment begun; the PIN given in this tap, or the no-PIN allowance enough for the whole of the pieces (6f); the day's limit, on what leaves the card | | while the payment is over the limit on one payment: two bytes, the constant `00 01`, "not yet" (never how many are to come: 6a), and nothing burned; sent again. Then the one 64-byte signature over every piece and every output; the places noted spent, the counts charged, the openings of change made owed and the log written in the same transaction. In order: `6A97`, `6985` as `22`; `6985` with no payment begun; at the first `SIGN`, `6A94` (1.16) with a PIN set and not given and the allowance not enough, the payment given up and nothing written in the log (6f), then `6A8F` over the day, the payment given up and the refusal written in the log (6); at the last, `6A96` if the transaction cannot hold it, with nothing changed. **Not** opened by `ALLOW_LOAD`. **1.23:** the limits are in cents: a payment in sats is turned into cents at the rate, its whole for the allowance and what leaves the card for the day and the wait, each rounded up, at the first `SIGN` and at the last; before the PIN is asked, **`6A99`** for a payment in sats with any limit set and no rate, the payment given up and nothing written. The log entry, the count and the receipt are in the payment's own unit and say it, and the last payment's unit is written in the transaction (8.5) |
| `25` | **SPEND_ALL_AGAIN** | the PIN given in this tap, unless the last signature was given with no PIN (1.16) | | the last signature `24` gave, again. `6A97`, `6985` as `22`; `6982` without the PIN where it is wanted; `6A88` if the card has signed nothing |
| `30` | **LOAD_PROOF** | a PIN set, and given in this tap (1.21: not the owner's grant, not the tap after a payment); a card record. Not an owner (1.22: a card with no owner is loaded under its PIN; up to 1.21 it was refused, `6A90`). No clock | 81 bytes; or two or three pieces end to end, 162 or 243; or, with **P1 = 1** (1.20), one piece and the mint's proof for it, 177 bytes (SPEC 5.9) | the slot of each piece stored, a byte each; with P1 = 1, two bytes, the slot and `01` where the proof was kept (`00` where the card has no room for proofs). In order: `6986` on a locked card; `6A97` with no PIN; `6982` with the PIN not given in this tap (a blocked PIN is never given); `6A8C` with no record; `6A84` with no place free; `6700` for a length that is not one to three pieces (with P1 = 1, not 177); then, for each piece, `6A84` no place left, `6A8E` for a dated piece on a card with no refund key, `6A80` for a `C` that is no compressed point, an amount of 0, or (P1 = 1) a proof of 96 zero bytes, and `6A94` for a piece whose nonce is already in a slot, spent or not (checked last, before anything is written). Several pieces are taken in order, each as one alone would be: if the first cannot be stored the command is refused with its word; if a later one cannot, the ones before it stand and the answer is their slots only, and the terminal sends the rest again to hear why. A piece that is the card's own change lets its opening go. **1.23:** with **P2 = 1** the first data byte is the pieces' unit (`00` sats, `01` dollars) and the rest is read one byte on: 82, 163 or 244 bytes, 178 with P1 = 1; a piece of P2 = 0 is of the record's unit. After `6A84`: `6B00` for a P2 other than 0 and 1; `6700`; `6A80` for a unit over `01` and `6A9C` for a label of the other unit with no book; then for each piece, after its `6A80`, `6A9B` for a keyset in the table as the other unit, `6A9D` for a new keyset that is neither the record's unit nor the last payment's without the holder's say, and `6A9C` with no entry free after a reclaim or a third live entry without the mark; then `6A94`; and, once, after the unit's `6A80` and `6A9C` and before the first piece, `6A99` for pieces in sats on a card with a limit set and no rate (8.5) |
| `31` | **CLEAR_SPENT** | a PIN set, and given in this tap, on a card with an owner or none (1.21: not the owner's grant, which freed a blocked card's places up to 1.20, and not the tap after a payment) | | how many places were freed (1). `6986` on a locked card; `6A97` with no PIN; `6982` with the PIN not given. **1.23:** and the table reclaimed after the places are freed (8.5) |
| `32` | **SET_CARD** | no owner: the PIN given in this tap, if one is set (a card with no PIN is written by anyone: it holds nothing); nothing unspent and no change owed; and, once the card has been used, the history held for in this tap (8.4). Owner: the owner's proof; nothing unspent and no change owed | unit (1), refund key (33), 65 bytes the card does not read (zeros from a phone of software 1.15; a time key from an earlier one), mint length (1), mint, and the design (3) or nothing; with an owner, proof length (1) and the proof first | nothing. In order: `6986` on a locked card; with no owner `6982` with a PIN set (or blocked) and not given, with an owner `6A91` with no good proof; `6A8D` if anything is unspent or change is owed; with no owner, on a used card, `6985` in a time in the field in which a payment was signed and `6982` with no hold; `6700` for a bad length, a mint of 0 or more than 77 characters, or a design that is not three bytes; `6A80` for a design that is not capital letters and digits, or a refund key that is neither a compressed point nor 33 zeros. The 65 bytes are not checked, and `SET_CARD` does not clear the clock (the time and the newest header's hash), the window or what is spent; it clears the ratchet's hardest bits (4). The first that goes through reserves the stores of the mint's proofs and of the openings' keys (SPEC 5.9, 8.2). **1.23:** `6A80` for a unit other than `00` and `01`; the first that goes through reserves the book as well; after every check, outside the record's transaction, any opening drafted for a payment never signed is let go, the table reclaimed and the last payment's unit forgotten (8.5) |
| `33` | **SET_LIMIT, PIN form** | **a card with no owner**: the PIN given in this tap; nothing unspent and no change owed; once used, the history held for in this tap. No clock | the limit on one payment (4); or eight bytes: that, then the no-PIN allowance (1.21) | nothing. In order: `6986` on a locked card (which always has an owner, and is told `6986` and not `6A91`); `6982` with no PIN given; `6A91` on a card with an owner (use `34`); `6A8D` if anything is unspent or change is owed; on a used card `6985` in a time in the field in which a payment was signed and `6982` with no hold (1.22); `6700` for any length but 4 or 8. Eight bytes set the allowance's count to 0. It does not set the daily limit (1.21; up to 1.20 four bytes were the day's limit and eight the day's and the one payment's). **1.23:** **twelve bytes**, the limit on one payment, the allowance (cents) and the rate; four and eight are taken on a dollars record alone, the rate left as it is, and are `6700` on a sats record and before the first `SET_CARD` (8.5) |
| `34` | **SET_LIMIT, owner's form** | the owner's proof. **No PIN**, any funds. No clock | proof length (1), proof (DER), limit (4) | nothing. `6986` on a locked card; `6A90`; `6A91`; `6700` for a limit that is not 4, 8 or 12 bytes. Eight bytes of limit (the day's, then the one payment's) set both (6a); **twelve** (1.16) add the no-PIN allowance (6f). A number that does not change keeps its window and its count (from 1.16 to 1.20, on a card with no PIN, a number in the allowance was `6985`; every card with an owner has a PIN since 1.21). **1.23:** **sixteen bytes**, the daily limit, the limit on one payment, the allowance (cents) and the rate, the proof over all of them; four, eight and twelve are taken on a dollars record alone, the rate left as it is, and are `6700` on a sats record; a rate alone changed begins no window and no count (8.5) |
| `35` | ~~SET_TIME~~ | | | gone in 1.15: `6D00`, and the instruction stays unassigned. Up to 1.14: a time (4), a signature length (1) and a signature (DER) by the time key, answered with the card's `now` |
| `36` | **SET_HEADER** | nothing: no PIN, no owner, no record, any state | a Bitcoin block header, 80 bytes, as the network carries it (5.1) | the card's `now` (4, big-endian), whether the header was taken or was no later than the clock. `6700` for any length but 80; `6A80` for a `bits` no header could carry; `6A93` for too little work (its own, the floor's, or a quarter of the hardest taken). Drops a payment begun |
| `37` | **TELL_TIME** | nothing: no PIN, no owner, no record, any state | the terminal's own clock, 4 bytes, big-endian seconds | nothing. `6700` for any length but 4. Kept for this time in the field and written into the receipts and the log entries made in it (5.3). Drops a payment begun |
| `40` | **VERIFY_PIN** | a PIN set | the PIN, 4 to 8 bytes; or, with **P1 = 1**, the PIN sealed (6e) | nothing; the PIN is given for the rest of this tap, and the no-PIN allowance renews (its count goes to 0: 1.21, 6f). In order: `6A86` for a P1 other than 0 and 1; `6984` with no PIN set; `6983` with the PIN blocked; sealed, `6700` for an envelope too short, `6985` with no nonce asked for, a try lost for an envelope that does not open (as a wrong PIN, below), `6700` for a block that is not nine bytes or a length in it not 4 to 8; `6700` for a PIN that is not 4 to 8 bytes; `63CX` for a wrong PIN, X tries left, and `6983` for the one that blocks it. A wrong PIN ends the PIN given earlier in the tap |
| `41` | **SET_PIN** | **a card with no owner**; nothing unspent and no change owed. A card not set up (no PIN, a blocked PIN, or no record): no PIN and no proof. A card set up (a PIN that is not blocked, and a record) (1.22): the PIN it has, given in this tap, and, once used, the history held for in this tap (8.4). It is the first thing a card is given (1.21). (From 1.16 to 1.20 also, ADD PIN, a card with an owner and no PIN under the owner's grant; gone, 6f) | the PIN; or, with **P1 = 1**, the PIN sealed (6e) | nothing; sets or replaces the PIN, gives back the tries, unblocks, and ends the PIN given earlier in the tap. A blocked PIN replaced takes the log and the receipts with it; on a card that had no PIN the allowance is nothing. In order: `6A86` for a P1 other than 0 and 1; `6986` on a locked card; `6A91` on a card with an owner, grant or none (use `42`); on a set-up card `6982` with its PIN not given; `6A8D` if anything is unspent or change is owed; on a set-up card that has been used, `6985` in a time in the field in which a payment was signed and `6982` with no hold; sealed, `6700` for an envelope too short, `6985` with no nonce, `6A80` for an envelope that does not open (no try is lost), `6700` for a block that is not nine bytes; `6700` for a PIN that is not 4 to 8 bytes |
| `42` | **CHANGE_PIN** | the owner's proof. **No PIN, no session, any PIN state, any funds** | proof length (1), proof (DER), the new PIN; or, with **P1 = 1**, the same sealed, the PIN as its nine-byte block (6e) | nothing; sets the PIN, gives back the tries, unblocks, and ends the PIN given earlier in the tap. In order: `6A86` for a P1 other than 0 and 1; `6986` on a locked card; `6A90`; sealed, `6700` for an envelope too short, `6985` with no nonce, `6A80` for an envelope that does not open, `6700` for a bad block; `6A91` with no good proof (over the PIN itself and the same nonce the seal is under); `6700` for a new PIN that is not 4 to 8 bytes |
| `43` | **SET_OWNER** | no owner (the claim, 1.21): a PIN set and given in this tap, no payment signed in this time in the field, nothing unspent; the same key asked for six times in a row (8.3). Owner: the owner's proof, nothing unspent | 65 bytes, the owner's public key, uncompressed; with an owner, proof length (1) and the proof first | the claim: `6A98`, keep holding, with no data, to each of the first five askings, a signature's work each, and the sixth sets the owner, answering nothing; a different key, or any other command between, begins the count again. With an owner: replaces it at once. In order: `6986` on a locked card; with an owner `6A91` with no good proof; with no owner `6A97` with no PIN, `6982` with the PIN not given, `6985` in a time in the field in which a payment was signed; `6A8D` if anything is unspent; `6700` for a key that is not 65 bytes; `6A80` for a key not beginning `04`; then the askings; `6A80` for a key that is not a point. After a claim, no payment is signed in that time in the field |
| `44` | GET_NONCE | a card with an owner. No PIN | | 16 bytes. `6A86` for a P1 other than 0 and 1; `6A90` with no owner. With **P1 = 1**, on any card: the 16 bytes, the PIN key (33, compressed) and the card key's signature over it (64), for sealing a PIN (6e) |
| `45` | **ALLOW_LOAD** | the owner's proof, over `FoxyCard/load` and no value | proof length (1), proof | nothing; for the rest of this tap the owner's grant (rule 9): the log and the receipts read, the limit on one payment told, a second payment not slowed, and with the PIN another key for `LOAD_CHANGE` (8.2). It opens no load (1.21; up to 1.20 `LOAD_PROOF` and `CLEAR_SPENT` needed no PIN under it). `6986` on a locked card; `6A90`; `6A91`; `6700` for bytes after the proof |
| `50` | LOCK_CARD | PIN set and given in this tap; the owner's proof | P2 = `DE` (P1 is not read); proof length (1), proof | nothing; the card locked for good. A locked card **still pays**, under its PIN and its no-PIN allowance, up to its limits, and **still takes its own change** (`LOAD_CHANGE`: receiving what it is owed changes no setting); **every other write is `6986`**, so its limit, its allowance and its PIN can no longer be changed, nor its places freed or loaded. Only `RESET_CARD`, with the PIN as well, takes it back (its let-go form, P1 = 1, also works on a locked card, with the proof and the PIN). The phone never sends it. In order: `6982` with no PIN given (a card with no PIN, or a blocked one, cannot be locked); `6B00` for a P2 not `DE`; `6985` on a card already locked; `6A90`; `6A91`; `6700` for bytes after the proof |
| `51` | **RESET_CARD** (1.17) | P1 = `00`: the owner's proof; a locked card also the PIN given in this tap; nothing unspent and no change owed. P1 = `01` (1.22): see 8.2 | P1 = `00`, P2 = `AD`; proof length (1), proof over `FoxyCard/reset` and nothing after it | nothing; the card as out of its packet, with a new key (SPEC 5.6a). In order: `6B00` for a P1 or P2 not those; `6A90`; `6A91`; `6700` for bytes after the proof; `6982` on a locked card whose PIN has not been given; `6A8D` if anything is unspent or change is owed. With **P1 = 1** and P2 an opening's number, that opening given up and nothing reset: with an owner, the owner's proof over the number, then the number; with no owner, the number alone, under the PIN and a held tap (8.2, which has its words). **1.23:** the book is zeroed with the store of the mint's proofs, and the rate with the record |
| `52` | SET_TAG (1.19) | the owner's proof; no PIN, any funds | P1 = `00` tag on, `01` off; P2 = `00`; proof length (1), proof over `FoxyCard/tag` and one byte, then that byte (the same as P1) | nothing; the NFC tag switched (SPEC 5.8); the state it has already is `9000` and writes nothing. In order: `6B00` for a P1 or P2 not those; `6986` on a locked card; `6A90`; `6A91`; `6700` for anything but one byte after the proof; `6B00` for a byte that is not P1 |
| `53` | **LOAD_CHANGE** (1.21) | a PIN set; then nothing more: any reader, in any tap, locked or not, blocked or not, under the key the opening was bound to at the payment. Another key: the PIN given in this tap, no payment signed in this time in the field, and the holder's say in the tap (the owner's grant, or on a card with no owner the history held for) (1.22, 8.2) | P1 = the opening's number (`GET_CHANGE` gives it); the mint's blinded signature `C_` (65, `04 ‖ x ‖ y`), the mint's key `K` for the opening's amount (65, the same form), and the mint's NUT-12 proof `e`, `s` (32 each): 194 bytes | 83 bytes: the place the piece went into (1), `01` where the mint's proof was kept with it (`00` where the card has no room for proofs), and the piece as `GET_PROOF` gives it after its status byte (81). The card checks the proof against `K` and its own `B_`, takes its blinding factor off (`C = C_ − r·K`), writes the piece with the opening's keyset, amount, nonce and date, keeps `e`, `s` and `r` as the piece's proof (`GET_DLEQ` answers for it), writes the log, and lets the opening go (8.2). In order: `6A97` with no PIN; `6A83` for a P1 past 7; `6A88` for an opening not waiting; `6A94` for one whose piece is on the card already (the opening is let go); `6A84` with no place free; `6700` for any length but 194; `6A80` for a `K` not in the `04` form; for a `K` other than the bound one, `6A80` with no PIN given, `6985` in a time in the field in which a payment was signed, `6982` with no owner's grant (with an owner) or no held history (with none); `6A80` for a point that is none, or a proof that does not hold. Nothing is written before the piece itself |

`21` (`SIGN_ARBITRARY`) stays unassigned. The applet's version became 1.1 and
`FORMAT` 3 with this design; a phone that knows only format 2 refuses the card,
as it does for anything but its own. With one signature for a payment (6c) the
version is 1.4 and `FORMAT` 4: a phone that knows only format 3 refuses such a
card, and a phone that knows both pays with either. With the limit on one
payment a wait and not a window (6a) the version is 1.5, the format is still
4, and bit 3 of the capabilities byte says so. 1.6 is the same card made
quicker to hold (6d), and bit 4 says so. 1.7 is the same card with 128 places
(6d, 8.1), and bit 5 says so. 1.8 burns a payment's pieces outside its
transaction (6d), so that a payment may be of any number, and bit 6 says so.
1.9 takes its PIN sealed (6e), and bit 7 says so. 1.15 takes its clock from
Bitcoin block headers (5): `SET_TIME` and the time key are gone, `SET_HEADER` and
`TELL_TIME` are new, and a log entry and a receipt are four bytes longer for the
time a terminal tells. No capability bit says so, since all eight are taken; the
version does, and `SELECT` answers `01 0F`. 1.16 makes the PIN optional and
gives a card that has one the no-PIN allowance (6f): `SELECT` answers `01 10`,
no capability bit changes, and the wire is otherwise 1.15's. 1.17 adds
`RESET_CARD` (SPEC 5.6a), and `SELECT` answers `01 11`. 1.18 makes the wait for
each further limit's worth over the limit four signatures where it was three
(6a): `SELECT` answers `01 12`, no capability bit changes, and the wire is
otherwise 1.17's. 1.19 makes the card an NFC tag as well, which its owner can
switch off (SPEC 5.8): `SET_TAG` is new, `GET_INFO` has a form of its own, P1 = 2, one byte longer than P1 = 1's,
`SELECT` answers `01 13`, no capability bit changes, and the wire is otherwise 1.18's.
1.20 keeps the mint's proof with each piece (SPEC 5.9): `GET_DLEQ` (`1A`) is new,
`LOAD_PROOF` has a form of its own, P1 = 1, `SELECT` answers `01 14`, no capability bit
changes, `GET_INFO` is 1.19's to the byte but for the version, and the wire is otherwise
1.19's. 1.21 makes a PIN the first thing a card has, and the card's own change its own
(6f, 8.2, 8.3, SPEC 5.10): `SELECT` answers `01 15`; `LOAD_CHANGE` (`53`) is new;
`SPEND_ALL_CHANGE` takes the mint's key with the amount (37 bytes); `GET_CHANGE` answers
83-byte records (the number, the blinded message and the bound key where the nonce and the
blinding factor were); `SET_OWNER` on an open card is a claim of six askings under the PIN;
`LOAD_PROOF`, `CLEAR_SPENT`, `LOAD_CHANGE`, the payment commands and a claim answer `6A97` on
a card with no PIN, and no load is opened by the owner's grant or the tap after a payment;
`GET_INFO` byte 29 says whether change is owed; the PIN form of `SET_LIMIT` sets the limit
on one payment and the allowance; the allowance counts the whole of a payment's pieces and
renews when the PIN is given; `SET_CARD` and `RESET_CARD` wait while change is owed; no
capability bit changes. 1.22 sets up, loads and frees the places of a card with a PIN and no
owner under its PIN, and holds its history for its PIN (8.4); it takes change owed under
another key, or gives an opening up (8.2): `SELECT` answers `01 16`; `RESET_CARD` has a
let-go form (P1 = 1); a log entry's flags have bit 4; `LOAD_PROOF` no longer answers
`6A90`; no capability bit changes, and a card with an owner is 1.21's in every other way.
1.23 holds dollars and sats at its one mint (8.5): `SELECT` answers `01 17`; `GET_INFO` has a
form of its own, P1 = 3, and its byte 8 is 4 or 5 by what the card holds; `GET_CARD` P1 = 2,
`GET_BALANCE` P1 = 1 and `GET_LOG` P1 = 2 and 3 are new forms; `LOAD_PROOF` takes a unit with
P2 = 1; `SET_LIMIT` takes sixteen bytes (`34`) and twelve (`33`) with the rate, and the old
lengths only on a dollars record; `6A99` to `6A9D` are new words; no capability bit changes.

**Every command in every state: the rule of section 3 worked through.** "Open"
means nothing is needed beyond what the command's own row says; "refused" means
`6A91` unless another word is given. Every command that writes answers `6986` on a
locked card before any other check but that of its P1 and P2 (`LOAD_PROOF`,
`CLEAR_SPENT`, `SET_CARD`, both `SET_LIMIT`s, `SET_PIN`, `CHANGE_PIN`, `SET_OWNER`,
`ALLOW_LOAD`, `SET_TAG`); `SET_HEADER` and `TELL_TIME` are taken by a locked card,
the payment commands still pay on one, `LOAD_CHANGE` still writes its own change on
one, and `RESET_CARD` resets one for the owner's proof and the PIN. A card with
**no PIN** (it has no owner, and nothing on it, since 1.21) refuses every load,
every payment command, an owner's claim and the let-go with `6A97`, whichever
column it is in (the let-go answers `6A83` for a number past 7 first).

| Command | Card with **no owner** | Card **with an owner** |
|---|---|---|
| `GET_INFO`, `GET_PUBKEY`, `GET_BALANCE`, `GET_PROOF_COUNT`, `GET_PROOF`, `GET_SLOT_STATUS`, `GET_PIECES`, `GET_DLEQ` (1.20), `GET_CHANGE`, `AUTH`, `GET_CARD` | open (the limit on one payment in `GET_INFO`: the PIN in a held tap, 1.22) | open (the limit on one payment: the owner's grant) |
| `GET_INFO` P1 = 3, `GET_CARD` P1 = 2, `GET_BALANCE` P1 = 1 (1.23) | open; the rate in `GET_INFO` P1 = 3 to the PIN in a held tap | open; the rate to the owner's grant |
| `GET_LOG` (`18`), the log and its receipts | the PIN given in this tap (`6982`), held for: ten askings in a row, `6A98` to each but the last; `6985` in a tap that paid; open for the rest of the time in the field once answered (1.22; no PIN set: open, and empty) | the log: the PIN or the `ALLOW_LOAD` grant; the receipts: the grant alone (`6982`); at once |
| `SET_HEADER`, `TELL_TIME` | open: any state, locked or blocked (a header must show its work, 5.1) | same |
| `GET_NONCE` | `6A90` (with P1 = 1: open) | open (it only gives a number) |
| `VERIFY_PIN` | as ever | as ever |
| `SPEND_ALL_BEGIN`, `SPEND_ALL_OUTPUTS`, `SPEND_ALL_CHANGE` | open (since 1.16); `6985` in a tap that read the history (1.22) or claimed an owner | open; `6985` in a tap that claimed an owner (8.3) |
| `SPEND_ALL_SIGN`, `SPEND_ALL_AGAIN` | the PIN, or the no-PIN allowance, up to the limits (since 1.22 such a card may hold pieces); `6985` in a tap that read the history | the PIN, or the no-PIN allowance, up to the limits (6f) |
| `LOAD_PROOF` | **the PIN given in this tap** (1.22; `6982` with it not given; up to 1.21 refused, `6A90`) | the PIN given in this tap (`6982`); not the `ALLOW_LOAD` grant, and not the tap after a payment (1.21) |
| `LOAD_PROOF` of a new keyset of the other unit than the record's, not the last payment's (1.23, 8.5) | the PIN in a held tap (`6A9D` with the PIN alone) | the PIN and the owner's grant in this tap (`6A9D` with the PIN alone) |
| `LOAD_CHANGE` (`53`, 1.21) | any reader, under the key bound at the payment (such a card pays, and may be owed change); another key under the PIN in a held tap that paid nothing (1.22) | any reader, under the key bound at the payment; another key under the PIN and the `ALLOW_LOAD` grant in a tap that paid nothing (1.22) |
| `CLEAR_SPENT` | the PIN given in this tap (`6982` with it not given) | the PIN given in this tap (not the grant, and not the tap after a payment, 1.21) |
| `SET_PIN` (`41`) | **open while nothing is unspent or owed, until the card is set up, or with its PIN blocked** (sets or replaces, unblocks); once set up, under the PIN it has, and once used the history held for (1.22, 8.4) | **refused**, empty or not (`6A91`): the owner uses `CHANGE_PIN` (from 1.16 to 1.20 a card with no PIN took one under the owner's grant, ADD PIN) |
| `CHANGE_PIN` (`42`) | `6A90` | the owner's proof, empty or not, any PIN state |
| `SET_CARD` (`32`) | the PIN given in this tap if one is set (`6982`; none: open), **while nothing is unspent and no change is owed**, and once used the history held for in this tap (1.22, 8.4) | **the owner's proof, and nothing unspent or owed** (`6A8D`); `6986` locked |
| `SET_LIMIT` (`33`, PIN form) | the PIN given in this tap, **while nothing is unspent or owed**, and once used the history held for (1.22) | **refused**; `6986` on a locked card |
| `SET_LIMIT` (`34`) | `6A90` | the owner's proof, empty or not |
| `SET_LIMIT` with the rate (1.23) | `33`, twelve bytes, as 1.22's `33` | `34`, sixteen bytes, as 1.22's `34` |
| `SET_OWNER` (`43`) | **the claim, while nothing is unspent**: under the PIN given in this tap, six askings in a row, never in a tap that signed a payment (8.3) | **the owner's proof, and nothing unspent** |
| `ALLOW_LOAD` (`45`) | `6A90` | the owner's proof |
| `LOCK_CARD` (`50`) | `6A90` (after the PIN gate) | PIN set and given, and the owner's proof |
| `RESET_CARD` (`51`, 1.17) | `6A90`; with P1 = 1 (1.22) one opening given up under the PIN in a held tap | the owner's proof, nothing unspent or owed (`6A8D`); on a locked card the PIN as well (`6982`); with P1 = 1 one opening given up under the proof |
| `SET_TAG` (`52`, 1.19) | `6A90` | the owner's proof, empty or not, no PIN; `6986` on a locked card |

So the one thing a terminal with the PIN and an emptied card with an owner can
still do is load pieces onto it (which the mint will refuse) and spend them. It
cannot set its own PIN, owner, record or limit. On a card with no owner (1.22) the
PIN is the card's say: a terminal with the PIN can also, on an empty card owed
nothing and in a held tap of its own once the card has been used, set its record,
its numbers and its PIN, and claim it (8.4).

Status words, in the part of `6Axx` ISO 7816-4 leaves unassigned, and the ISO
words the card gives a meaning of its own:

| SW | Means |
|---|---|
| `6A8C` | no card record: a load (`LOAD_PROOF`) before `SET_CARD` has run |
| `6A8D` | card in use: a thing that is set only on an empty card, on a card with an unspent piece; and (1.21) `SET_CARD`, `RESET_CARD`, and on a card with no owner `SET_PIN` and the PIN form of `SET_LIMIT`, while change is owed to the card (8.2) |
| `6A8E` | a dated piece on a card with no refund key |
| `6A8F` | over the day: this payment would take today past its limit. Nothing signed, nothing burned, the payment given up, and the refusal written in the card's log |
| `6A95` | unused. It was "over the limit on one tap", when that was a window and refused; a payment over the limit on one payment now waits (6a) |
| `6A90` | no owner: a command that needs the owner's proof, or `GET_NONCE`, on a card that has none (up to 1.21 a load as well; since 1.22 a card with no owner is loaded under its PIN) |
| `6A91` | the owner's proof is missing or is not the owner's, or this command is not open to a card with an owner. Costs no PIN tries, changes nothing, does not end the PIN session |
| `6A92` | unused. It was "no time", for a card never told the time; since 1.15 a card spends its first day on trust (6) and nothing answers it |
| `6A93` | little work: a `SET_HEADER` whose work is not enough, whether not its own difficulty's, under the floor, or under a quarter of the hardest header taken (5.1). Nothing changes. (Up to 1.14: "not the time", a `SET_TIME` whose signature was not the time key's) |
| `6A94` | on the card already: a `LOAD_PROOF` or `LOAD_CHANGE` of a piece whose 32-byte nonce is the nonce of any non-empty slot, spent or unspent, and the let-go of an opening whose piece is on the card already (8.2). Nothing is written, and the amount stated is not looked at. A slot that `CLEAR_SPENT` has freed is empty, so that piece may be loaded again then. **Also, since 1.16, the PIN is needed** (`SPEND_ALL_SIGN`, and `SPEND_ALL_BEGIN` for a sum that wraps): a PIN is set and was not given in this tap, and the no-PIN allowance does not cover the whole payment. Nothing is signed, burned or counted, the payment is given up, and nothing is written in the log (6f). The commands are never confused |
| `6A96` | too many: a payment of more places than the card has, or one whose transaction the chip could not hold. Nothing burned, nothing changed |
| `6A97` | **1.21.** No PIN: a load (`LOAD_PROOF`, `CLEAR_SPENT`, `LOAD_CHANGE`), a payment command (`22` to `26`), an owner's claim (`SET_OWNER` on an open card) or (1.22) the let-go of a card with no owner, on a card that has no PIN. A card is given its PIN before anything goes on it or leaves it. Nothing is written. Not `6982`, which is a PIN that is set and not given |
| `6A99` | **1.23.** No rate: `SPEND_ALL_SIGN` of a payment in sats on a card with a limit set and no rate, the payment given up, nothing signed, burned, counted or written in the log; and `LOAD_PROOF` of pieces in sats on such a card, nothing written |
| `6A9A` | **1.23.** Two units in one payment: `SPEND_ALL_BEGIN` given places of both units, or a place whose keyset is in no entry. Nothing hashed, counted or written; no payment begun |
| `6A9B` | **1.23.** The other unit: a load of a piece whose keyset is in the table as the other unit than the load names. Nothing written |
| `6A9C` | **1.23.** No room in the table: a new keyset with no entry free after a reclaim, or a third live entry without the mark; and a load labelled the other unit on a card refused its book. Nothing of the piece written |
| `6A9D` | **1.23.** Not this tap's to start: a new keyset of the other unit than the record's, not the last payment's, without the holder's say in the tap. Nothing written. Kept apart from `6A91`, which a phone reads as "not the owner", and `6982`, "the PIN" |
| `6A98` | **1.21.** Keep holding: a held tap counted and not yet through. The first five askings of an owner's claim (8.3) and (1.22) the first nine askings of the history of a card with no owner (8.4). No data, and nothing written but the count. Ask again. Not `9000`, so that a phone that asks once never takes it for done |
| `6982` | the PIN not given in this tap, where a command needs it (a blocked PIN is never given); and (1.22) a command that needs a held tap, in a tap with the PIN and no hold, and another key for `LOAD_CHANGE` with no owner's grant or held history; and the owner's grant not given, for the receipts of a card with an owner (the grant alone) or its log (the PIN or the grant), and for the history of a card with no owner when the PIN has not been given |
| `6985` | no payment begun, for a step of a payment; a spent place named; a card already locked; a nonce not asked for, for a sealed PIN; and (1.21, 1.22) a payment command in a time in the field in which an owner was claimed or a card with no owner's history was read, or a claim, the history, a rewrite of a used card with no owner, a let-go with no owner, or another key for `LOAD_CHANGE`, in a time in the field in which a payment was signed; and `SPEND_ALL_OUTPUTS` sent after the card's own change (the terminal's outputs come before it) |

`SET_HEADER` also answers `6A80` (ISO's own word for wrong data) for a `bits` no
header could carry: a size of 0 or of more than 32, or a mantissa with its top
bit set.

What `CardGate` carries: the instructions the page uses, `01 10 11 13 14 15 16
17 20 30 31 32 34 36 37 40 41 42 43 44 45`, and those of the later versions as each
came (`18`, `19`, `1A`, `22` to `26`, `51`, `52`), and from 1.21 `53`. `33` (the PIN
form of the limit, for an open card) is not used by an owner's set-up, and `50`
stays out; a phone that sets up a card for a holder with no phone sends `33` (8.4).

### 8.1 GET_PIECES: the whole card in a few answers

A tap used to read a card with one command for each of its states and one
for each piece on it, at a tenth of a second or so a command. `GET_PIECES`
(`17`) says what both of those say, in pages.

**The answer.** P1 is the first slot to report (0 to 127; `6A83` above that);
P2 is 0 and the data is not used (P2 = 2 and P2 = 3 are its other forms, 6d).
No PIN: it says what `GET_SLOT_STATUS` and `GET_PROOF` say, which need none.
The answer is

    next (1)     the first slot this page does not cover; 128 means the card has
                 no more to say, and anything else is the P1 to ask for next
    then, for each slot from P1 up to next that is not empty, in order:
      tag (1)    the slot, and 0x80 where it is spent (a card of sixty-four
                 places, before 1.7: (state << 6) | slot, state 1 for unspent
                 and 2 for spent, and next ends at 64)
      piece (81) only for an unspent slot: keyset (8), amount (4), nonce (32),
                 C (33), date (4), which is what `GET_PROOF` gives after its
                 status byte

A slot in the range with no entry is empty. A spent slot is listed by its tag
alone: its bytes stay on the card until `CLEAR_SPENT` frees the place, and
nothing that reads a card wants them. A page is at most 255 bytes, so three
unspent pieces (1 + 3 x 82 = 247) make a page, and spent slots, which cost a
byte, fill what is left of it. At least one entry always fits, so every answer
moves on. A card of 128 unspent pieces is forty-three pages; a card with
six is two. (A till that only wants to choose pieces reads the short listing,
6d, which is one answer for a hundred pieces.)

**Why pages, and not extended length.** 128 slots of 82 bytes are 10,496 bytes,
which a short APDU (256) does not hold. An extended-length answer would hold it,
and was not chosen: the card's support for extended APDUs was not measured, the
simulator's is not the card's, and every extra thing the reader and the phone
must carry is one more thing that can fail in the one tap that matters. A page
needs nothing but a short APDU, which every command here already is. What it
costs is a command for each three pieces, where extended length would be one:
for the cards this is for, with a dozen pieces at most, that is four commands
and not twelve, and more is saved by the commands not sent than by the one
that carries more. The pages are sent from where the slots sit
(`sendBytesLong` from the slot store), so the APDU buffer's size is not relied
on.

**On the phone.** `cardLook` asks for pages from slot 0 until `next` is 128 (64 on a card of 1.6 and before), and
not at all when `GET_INFO` counts no unspent and no spent slot. A card that
answers the first page with anything but a page (an older applet answers
`6D00`) is read the old way: `GET_SLOT_STATUS`, then `GET_PROOF` for each slot
that is not empty. The old commands stay on the card and in the gate for that.
A read of a card holding six pieces is about eight commands (select, the clock,
info, key, proof of the key, record, two pages) where it was twelve.

### 8.2 The card's own change, and why no tap is special

A till lets the card go as soon as it has signed, and asks the mint with the
card gone (9). The change the card made for itself (`SPEND_ALL_CHANGE`, 6c) is
then **owed** to the card: each output of it is an *opening*, kept on the card
until its piece is written back. A card keeps eight openings at most. Any reader
may write that change back, in any tap, with no PIN, and nothing else. No tap is
special: there is no grant for the tap after a payment (1.21).

**Made on the card, bound at the payment (1.21).** `SPEND_ALL_CHANGE` carries the
amount and the mint's key for that amount in the payment's keyset (33 bytes,
compressed). The card keeps the key with the opening. It draws a nonce, builds
the secret as its pieces' are (its own key, the payment's date and refund key,
`SIG_ALL`), hashes it to the curve as the mint will (NUT-00), draws a blinding
factor and blinds the point. It keeps the opening (the amount, keyset, date,
nonce, blinding factor and blinded message `B_`), answers `B_`, and hashes it into
the payment's message as an output. The opening is a draft until the payment is
signed, owed from the transaction that commits the payment, and let go when its
piece is on the card. A draft of a payment never signed is let go at the next
`SPEND_ALL_BEGIN`.

**Listed to anyone.** `GET_CHANGE` (`19`) gives, three to a page after a count,
each owed opening's number, amount, keyset, date, `B_` and the key it was bound
to: 83 bytes. Never the nonce or the blinding factor. An opening whose piece is
on the card already is let go first. `GET_INFO`'s byte 29 says whether any change
is owed, to anyone, as `GET_CHANGE` lists it to anyone.

**Written back by `LOAD_CHANGE` (`53`).** P1 is the opening's number. The data is
what the mint answered for the opening's `B_`: the blinded signature `C_` and the
mint's key `K` (65 bytes each, uncompressed), and the mint's NUT-12 proof `e` and
`s` (32 each), 194 bytes. `K` must be the key the opening was bound to (its x and
its y's parity), or the answer is refused before any arithmetic. The card checks
the proof (`R1 = s·G − e·K`, `R2 = s·B_ − e·C_`, and `e` must be SHA-256 of `R1`,
`R2`, `K` and `C_`, each uncompressed, as lowercase hex text), takes its own
blinding factor off (`C = C_ − r·K`), and writes the piece as `LOAD_PROOF` writes
one: the opening's keyset, amount, nonce and date and that `C`, with `e`, `s` and
`r` kept as the piece's proof (`GET_DLEQ` answers for it as for any piece), the
status byte last. The log has the piece put on (6b), and the opening is let go.
The answer is 83 bytes: the place, `01` where the proof was kept (`00` on a card
with no room for proofs), and the piece as `GET_PROOF` gives it after its status
byte. The blinding factor is given with the proof and never before: while the
change is owed it never leaves the card, so no reader can finish a piece of the
card's change but the card. No PIN is asked, on a locked card or a blocked one
too: receiving what it is owed changes no setting. Its refusals, in order, are in
section 8.

**What the binding buys.** The proof shows that `C_` was made with the private key
of `K`, and `K` is the key the till named at the payment, under the PIN or the
allowance: so the one piece a reader without the PIN can write is the one the mint
signed for this opening, and a reader that writes it gives the card what it was
owed. A key made up after the payment is refused (`6A80`), whoever brings it
without the PIN: a proof holds for any key whose private key its maker knows, and
without the binding a stranger could have the card write a piece the mint never
made and let the opening go.

**What it cannot see, and the two ways out (1.22).** The card cannot tell that the
key the till names is the mint's. A till that names its own key, or nonsense,
makes change the mint's real answer cannot fit: up to 1.21 nothing could load it,
and the change owed kept `SET_CARD`, `RESET_CARD`, and on a card with no owner
`SET_PIN` and the PIN form of `SET_LIMIT`, at `6A8D` for good. Neither way out
weakens the binding for a reader without the PIN:

- **Mended by the holder.** `LOAD_CHANGE` takes a key other than the bound one
  when the PIN has been given in this tap (`6A80` without it, as in 1.21), no
  payment has been signed in this time in the field (`6985`), and the holder's own
  say is in the tap as well (`6982` without it): on a card with an owner the
  owner's grant (`ALLOW_LOAD`), on one with none the history held for (8.4). The
  PIN alone is not enough, because a till has the PIN: with it alone a till could
  bind the change of an earlier payment, still waiting, to a key of its own and
  then be paid in the same tap. The proof is checked under the key given, as
  under the bound one (`6A80` where it does not hold), so what is written is
  still a signature under the key the reader names. The holder's phone asks the
  mint for its answer to the opening's `B_` (NUT-09 restore, from `GET_CHANGE`)
  and brings it under the mint's real key: the money is back. A stranger's reader
  has no PIN; a till handed the PIN has no owner's grant, and a held tap keeps
  every payment out of the rest of that time in the field. What is left: on a
  card with no owner, a reader that keeps the PIN can hold a tap of its own and
  name a key of its own, with a proof that holds for it, for any opening still
  waiting, and so spoil that change, as it can give the opening up; on a card
  with an owner, the owner's own phone can do the same. Neither takes anything;
  the piece is one the mint will not take, and a phone that checks the mint's
  proof (`GET_DLEQ`) against the mint's keys sees it.
- **Given up.** `RESET_CARD` with P1 = `01` and P2 the opening's number (0 to 7)
  lets that one opening go with its piece never loaded, and resets nothing. On a
  card with an owner the data is the owner's proof over `FoxyCard/reset`, the
  nonce just given and the number, then the number (the reset's own label, with a
  value the reset never has); on a locked card the PIN given in this tap as well.
  On a card with no owner the data is the number alone, under the PIN given in
  this tap and the history held for in it (8.4). The tap is written in the log:
  its entry, with bit 4 of its flags, in the transaction that lets the opening go.
  Its answer is `9000` and nothing. The money of that opening is gone: the card
  cannot tell an opening nothing can load from one whose piece is still to come,
  so giving it up is the owner's choice, or the holder's under the PIN, and never
  the card's. Then nothing owed stops a reset, a new record, a new PIN or the PIN
  form of the limit. Refusals, in order: `6A83` for a number past 7, before
  anything else is looked at; with an owner, `6A91` with no good proof and `6982`
  on a locked card whose PIN has not been given; with no owner, `6A97` with no
  PIN, `6982` with the PIN not given, `6985` in a time in the field in which a
  payment was signed, and `6982` with no hold; `6700` for anything but the number
  after the proof (with an owner) or but the number (with none); `6B00` for a
  number that is not P2; `6A88` for an opening that waits for nothing; `6A94` for
  one whose piece is on the card already, which is let go, with nothing written
  in the log.

A card with no owner whose PIN is blocked can do neither: a blocked PIN is
replaced only with nothing owed (8.4), so no PIN can be given to it while change
is owed. Change bound to a key the mint's answer is not under stays owed for good
there; only `LOAD_CHANGE` under the bound key clears it. A locked card with an
owner whose PIN is blocked is the same: the let-go wants the PIN on a locked
card, and `CHANGE_PIN` answers `6986` on one.

**What change owed holds up.** `SET_CARD` and `RESET_CARD` answer `6A8D` while any
opening is owed, and on a card with no owner so do `SET_PIN` and the PIN form of
`SET_LIMIT`: the refund key and the mint stay as the change's secret was built
with them, and no blinding factor is thrown away. An opening whose piece is on
the card already, its letting go torn off, owes nothing and is let go there.

**On the phone.** A till that owes a card change taps it again, or sends at once
in the same tap: `GET_CHANGE` for the openings' numbers (it matches each opening's
blinded message with the one `SPEND_ALL_CHANGE` answered and the mint signed),
then one `LOAD_CHANGE` for each, with the mint's `C_`, `K`, `e` and `s`. The
screen says `No PIN is needed.` on the TAP THE CARD AGAIN card. What does not go
on that way is a load, and wants the PIN (`LOAD_PROOF`): pieces of the till's own
that it would write back without an opening (a refund of what it signed and left
with, a payment put back after the mint refused it, a remainder). Change a till
never brought back, the holder's phone brings: it asks the mint for its answer to
the opening's `B_` (NUT-09 restore) and sends `LOAD_CHANGE` under the bound key,
which needs nothing, or mends it under another key as above.

(Up to 1.20 a payment set one bit that let the next reader to tap the card put
any piece on it and free its spent places with no PIN, until a load used it, and
`GET_CHANGE` gave every reader each opening's nonce and blinding factor, so that
any reader that saw the mint's answer could finish the piece.)

### 8.3 An owner claimed slowly (1.21)

A card with no owner is claimed by whoever sets its owner first. A till that had
just been handed a card's PIN, or any reader that found a card nobody had
claimed, could once do that in the instant the card was near it. So a claim
(`SET_OWNER` on a card with no owner) takes time the holder can see, and is made
under the PIN:

- **The PIN, set and given in this tap.** `6A97` with none set, `6982` with one not
  given. A card's first steps are `SET_PIN`, `VERIFY_PIN` and then the claim.
- **Only of an empty card.** `6A8D` with anything unspent on it. A card with no
  owner may hold money since 1.22 (8.4); it is claimed only once it is empty
  again.
- **Six askings in a row** (`CLAIM_ASKINGS`). The first five are each answered
  `6A98`, keep holding, with no data, after a signature's work of the card's own,
  thrown away, as a payment's wait does it (6a): about half a second each on the
  chip, measured, some three seconds in all, and some four by a phone. The sixth
  sets the owner and answers `9000` and nothing. `6A98` is not `9000`, so a phone
  that asks once and takes `9000` for done is never told the claim was made. A
  different key in an asking begins the count again with that key, and so does
  any other command sent between, a `SELECT` among them, as anything else gives up
  a payment's wait. A `SET_OWNER` refused for its own data (a key of the wrong
  length, say) neither counts nor ends the count. The count is in memory that is
  cleared on deselect. The key asked for is kept, while the count runs, where a
  payment's places are kept: no payment is in hand, since the asking has given it
  up. The checks in section 8's row come before the askings; a key that is not a
  point is refused (`6A80`) at the sixth.
- **Never with a payment in the same time in the field.** A claim is refused
  (`6985`) in a tap in which a payment was signed, even one whose card was reset to
  an open card after it by its owner's proof; and after a claim, every payment
  command is refused (`6985`) for the rest of that tap. The next tap is free of
  both.

A card with an owner is not claimed: `SET_OWNER` there needs the old owner's proof,
as before, and is made at once.

**On the phone.** It sends `SET_OWNER` again for as long as the card answers
`6A98`, and says on the screen **KEEP HOLDING**, counting up as a payment's wait
does, until the claim is made. A card taken away in the count is not claimed, and
the next tap begins it again. (An earlier text of this section had ten askings,
each but the last answered `9000` and `00 01`; the card as built asks six, and
answers `6A98`.)

### 8.4 A holder with no phone (1.22)

A card with a PIN and no owner is the card of a holder who has no phone of their
own. The first phone that sends such a card money sets it up and loads it under
the holder's PIN, and does not become its owner. The holder reads its history on
a friend's phone as a guest. A card with an owner is 1.21's but for the version
and the two ways out of change owed (8.2).

1. **Set up and loaded under the PIN.** `SET_CARD`, `LOAD_PROOF` (both its forms)
   and `CLEAR_SPENT` go through on a card with a PIN and no owner with the PIN
   given in this tap, and answer `6982` without it. With no PIN at all,
   `LOAD_PROOF` and `CLEAR_SPENT` answer `6A97`, and `SET_CARD` is open on the
   empty card. `LOAD_PROOF` no longer answers `6A90`. The phone that sends the
   card money sets it up (the PIN, entered twice, the currency, the no-PIN
   allowance, the limit on one payment) and loads it, in one tap: `SET_PIN`,
   `VERIFY_PIN`, `SET_CARD`, `33` with eight bytes, then `LOAD_PROOF`.
2. **The record.** Whoever writes the record writes all of it: the unit, the mint,
   the design (three characters, or none), and either no refund key (33 zeros: a
   cash card, whose dated pieces nobody can take back if it is lost) or a refund
   key the loading phone supplies (then that phone, and not the holder, can take
   back the dated pieces of a lost card once their date has passed). Foxy's phone
   writes no refund key (10). A record is rewritten only on an empty card with no
   change owed (`6A8D`).
3. **The holder's numbers, with the PIN.** The PIN form of `SET_LIMIT` (`33`) sets
   the limit on one payment and, with eight bytes, the no-PIN allowance (its count
   then goes to 0), under the PIN given in this tap (`6982`), while nothing is
   unspent and no change is owed (`6A8D`); `6A91` once the card has an owner. A
   card with no owner has no daily limit: that is the owner's to set (`34`).
4. **The history is the PIN's, and held for.** `GET_LOG`, the log (P1 = 0) and the
   receipts (P1 = 1), needs the PIN given in this tap (`6982`). In a time in the
   field in which a payment was signed it is refused (`6985`) before any asking is
   counted. Otherwise it is held for: each of the first nine askings in a row is
   answered `6A98`, keep holding, with no data, after a signature's work of the
   card's own, thrown away, as the claim and a payment's wait do it, and the tenth
   answers (`HISTORY_ASKINGS`: some five seconds on the chip at the measured half
   second an asking). The log and the receipts count as one held tap. Any other
   command, a `SELECT`, a `VERIFY_PIN` and an owner's claim among them, begins the
   count again, and an asking of the history begins the claim's count afresh; a
   `GET_LOG` with a P1 that is neither 0 nor 1, or one refused for want of the
   PIN, neither counts nor ends it. Once answered, the history is open to the PIN
   for the rest of that time in the field, through a `SELECT`, so that the log and
   every page of receipts are read with one hold.
5. **No payment in a tap that read the history.** Once the history of a card with
   no owner has been answered in a time in the field, every payment command (`22`
   to `26`) answers `6985` for the rest of it: a bit beside the ones that mark a
   payment and a claim (`TAP_READ`, beside `TAP_PAID` and `TAP_CLAIMED`), kept
   through a `SELECT` and a `RESET_CARD`, gone with the power. A hold begun and not
   finished is no read, and stops no payment; once a payment is signed, rule 4
   refuses the history in that tap.
6. **What stays open.** `GET_INFO`, `GET_BALANCE`, `GET_PIECES`, `GET_PROOF`,
   `GET_SLOT_STATUS`, `GET_CARD`, `GET_DLEQ`, `GET_CHANGE` and `AUTH` answer any
   reader with no hold: a till needs the balance and the pieces to take a payment,
   and is not slowed. Only the history, which says when the card was used and for
   how much, is held for.
7. **A card with an owner is 1.21's** (but for change owed, 8.2). The log under the PIN or the owner's grant,
   at once, in a paying tap as well; the receipts by the grant alone; the record by
   the owner's proof; loads and `CLEAR_SPENT` under the PIN. Nothing is held for
   and nothing is marked read.
8. **The claim, only of an empty card.** `SET_OWNER` on a card with no owner is
   made under the PIN given in the tap, by six askings in a row, never in a tap
   that paid, and only while nothing is unspent (`6A8D`, 8.3). A holder with no
   phone who ties the card to a phone of their own does it with the card empty.
9. **The card keeps its PIN.** `SET_PIN` on a card with no owner whose record is
   set and whose PIN is not blocked (the card is set up) wants the PIN it has,
   given in this tap (`6982`), nothing unspent and no change owed (`6A8D`), and,
   once the card has been used, the history held for in this tap (rule 10).
   Without this, anyone could put a PIN on such a card once it was spent to
   nothing, and then read its history, write its record, take the change it was
   owed and claim it. A PIN that is blocked has no one to give it: it is replaced
   with nothing owed (`6A8D`), and the log and the receipts go with it, so that the
   new PIN reads none of the old one's. A card whose record is not set yet has
   nothing to keep, and its set-up is taken up again by whoever comes. A card with
   an owner is `6A91`.
10. **A used card is rewritten only after a hold.** On a card with no owner whose
    log has taps in it (a piece has been put on it or taken), `SET_CARD`, the PIN
    form of `SET_LIMIT` and `SET_PIN` need the history held for in this tap, as a
    guest holds for it, and answer `6985` in a tap that paid and `6982` in a tap
    with the PIN and no hold. A till that is handed the PIN for a payment cannot,
    in the tap that took the money, put its own refund key on the card (and so take
    back future loads once their date has passed), a large no-PIN allowance (and so
    spend future loads with no PIN) or another mint; it can only do what a guest
    can, and no payment follows a hold. A card that has taken nothing is set up as
    before. Held for in a tap of its own, a till that keeps the PIN can still
    rewrite the card there (the same cost as reading its history,
    `SECURITY-MODEL.md` 20); what it wrote is in the card's record (`GET_CARD`) for
    the holder's phone to see.
11. **The guest sees the limit on one payment.** `GET_INFO`'s four bytes of it (30
    to 33, asked for with P1 = 1 or 2) are told to the PIN given in a held tap on a
    card with no owner, since no owner's grant can be given. Everyone else is told
    zeros, and so is a card with an owner but by its owner's grant. The price: on a
    card with no owner, a till that keeps the PIN and holds a tap of its own learns
    the limit in a tap that signs nothing, and so can ask for just under it and
    never be made to wait.
12. **Change owed, mended or given up.** On a card with no owner, `LOAD_CHANGE`
    under another key and the let-go form of `RESET_CARD` are the PIN's in a held
    tap (8.2). A card with no owner whose PIN is blocked can do neither.

**What the phone shows.** A card with a PIN and no owner, read on the FLASHCARD
screen of a Foxy app, shows two buttons: **VIEW AS GUEST** and **TIE OWNERSHIP TO
THIS PHONE**. VIEW AS GUEST asks for the card's PIN, then sends `GET_LOG` again
for as long as the card answers `6A98`, saying **KEEP HOLDING** on the screen and
counting up, as a payment's wait and the claim do; then it reads the receipts and
shows the balance and the history, and makes no payment in that tap. A phone that
sees `6A98` once and stops has read nothing: `6A98` is not `9000`. TIE OWNERSHIP
TO THIS PHONE is the claim (8.3), and wants the card empty (rule 8). The phone that
set the card up and loaded it shows neither as its own: it is not the card's owner.
A set-up cut off after the PIN is taken up again with `VERIFY_PIN` first (rule 9).
With the guest's hold done, the same screen can show the limit on one payment
(rule 11).

**Memory.** None added, of either kind. The history's count is the claim's byte
(cleared on deselect), marked by its top bit while it counts the history's
askings, so that an asking of one never adds to a count of the other; `TAP_READ`
is one more bit of the byte that already held `TAP_PAID` and `TAP_CLAIMED` (gone
with the power). The memory asked for at install is 1.21's: 941 bytes cleared on
deselect. The let-go adds none either: its two constants (its P1, the log's bit)
are compile-time numbers, and it is signed under the reset's own label.

### 8.5 Dollars and sats on one card (1.23)

A card of 1.23 holds dollars and sats at its one mint. The design, with its reasons, is
`FOXY-CARD-KEYSETS.md`; these are the rules as the card keeps them. A card that holds one
unit and has no limit set answers as 1.22 to the byte but for the version.

1. **The book.** The first `SET_CARD` that goes through reserves 75 bytes beside the store
   of the mint's proofs, once, outside any transaction, and a card the chip refuses them
   carries on as a card of one unit (every piece the record's, a load labelled the other
   unit `6A9C`). The book is the **table**, six entries of nine bytes (a keyset's eight
   bytes as a piece holds them, then a state byte: `00` free; `40` in use, `80` added for
   the mark, the unit code in bit 0), the last payment's unit (1), the other unit's count of
   what was signed for, ever (4), and each receipt's unit (16). A unit said in one byte
   outside the table is relative to the record's: `80` the record's, `A0` the other, `00`
   none. Every use of the book is safe while it is not there.
2. **Every piece is of one unit.** A load names its pieces' unit: with `LOAD_PROOF` P2 = 1
   the first data byte, `00` sats or `01` dollars, and the pieces after it read one byte
   on; with P2 = 0 the record's unit. A keyset in an entry of the other unit is refused
   (`6A9B`). A keyset in no entry is entered, its eight bytes and then its state byte, the
   entry's commit, before anything of the place: under the PIN alone as the record's unit
   or the last payment's (where a till's refund in a mint's new keyset goes), and as either
   unit with the holder's say in the tap (the owner's grant; on a card with no owner the
   history held for), which marks it (`6A9D` otherwise). `LOAD_CHANGE` enters nothing: the
   card's own change is in its opening's keyset, the payment's.
3. **Room in the table.** At most two live entries lack the mark, so a reader with the PIN
   and not the holder's say can take two and no more. A new keyset that finds no entry free,
   or would be a third without the mark, runs the reclaim and looks again (`6A9C` if it
   still finds none). The **reclaim** lets go every entry that no unspent place and no
   opening of change drafted or owed names: one pass over the places, a comparison a place
   where the keyset is the place before's, and one over the openings, then a byte written
   for each dead entry, outside any transaction. It runs at `CLEAR_SPENT`, at a load where
   the table may be full, and at `SET_CARD`. A live entry is never freed, and an entry is
   never changed in place, so no command moves a piece from one unit to the other.
4. **One unit a payment.** `SPEND_ALL_BEGIN` refuses places of both units, or a place whose
   keyset is in no entry, with `6A9A`, before anything is hashed. Two keysets of one unit
   pay together. The card never chooses a unit: the payment's is that of the places named.
5. **Every limit in cents.** The daily limit, the limit on one payment, the no-PIN allowance
   and what each counts are cents, whatever the record's unit. A payment in dollars is held
   to them as it is. A payment in sats is turned into cents at the **rate**, sats to the
   dollar, as `ceil(sats × 100 / rate)` worked on bytes (no `int`), a quotient past four
   bytes taken as over every limit: its whole for the allowance and what leaves the card for
   the day and the wait (6a's rule unchanged), each rounded up by itself, at the first
   `SIGN` and again at the last. The phone rounds the rate down; the card rounds the cents
   up; a payment in sats is never counted for less than its worth. A payment in sats on a
   card with any limit set and no rate is refused `6A99`, before the PIN is asked, and given
   up with nothing written; and pieces in sats are refused `6A99` at `LOAD_PROOF` on such a
   card, nothing written, so that a card with no owner, whose rate is written only while it
   is empty, never holds sats it cannot pay. Change (`LOAD_CHANGE`) is never refused this
   way: it is of a payment already signed.
6. **The rate is written with the limits.** It lives in the record at offset 156, where the
   newest header's hash is followed by zeros. `SET_LIMIT` writes it: the owner's form in
   sixteen bytes (the three figures and the rate, the proof over all of them), the PIN's in
   twelve (the limit on one payment, the allowance and the rate). 1.22's lengths carry no
   rate and are figures in the record's unit: they are taken on a dollars record, the rate
   left as it is, and are `6700` on a sats record and before the first `SET_CARD`. A rate
   alone changed begins no window and keeps every count. `SET_CARD` leaves the rate;
   `RESET_CARD` zeros it; `GET_CARD` answers its bytes as zeros; `GET_INFO` P1 = 3 tells it
   to whom the limit on one payment is told.
7. **What the card says.** Byte 8 of `GET_INFO` and byte 0 of `GET_CARD`, the card's format,
   are worked out at each asking: `04` where no entry in use is of the other unit, `05` where
   one is, so that a phone that knows only format 4 refuses a card of both units and never
   reads cents as sats. `GET_INFO` P1 = 3 says the book; `GET_CARD` P1 = 2 the table;
   `GET_BALANCE` P1 = 0 the record's unit's sum and P1 = 1 each unit's; nothing on the card
   adds the two.
8. **The log and the receipts say the unit.** A log entry is of one unit, bit 5 of its flags
   set for the other; a tap whose payment or load is of the other unit than its entry begins
   a new one. The head counts the record's unit and the book the other's (`GET_LOG` P1 = 2);
   each receipt's unit is kept in the book in the receipt's own transaction (`GET_LOG`
   P1 = 3). The last payment's unit is written in the payment's transaction.
9. **Starting over.** `SET_CARD`, on an empty card owed nothing, refuses a unit other than
   `00` and `01` (`6A80`), and after every check, outside its transaction, lets go any
   opening drafted for a payment never signed, reclaims the table (every entry free) and
   forgets the last payment's unit; the receipts' units and the other unit's count stay with
   the log. A change of the record's unit changes no limit's meaning. `RESET_CARD` zeros the
   book.

**What the phone does.** It asks a card for nothing new unless the card says `01 17`
(`FOXY-CARD-KEYSETS.md` 9). It writes the rate with every limit, labels every load of a 1.23
card, and asks the holder which currency every time both could pay.

**Memory.** None added at install: the constructors make the same 30,069 bytes of permanent
arrays, and the card asks for the same 941 bytes cleared on deselect. The book's 75 bytes
are asked for after the install, by `SET_CARD`. A payment's cents are worked out in the first
eighteen bytes of the working room, which nothing else uses at `SPEND_ALL_SIGN`; the reclaim's
marks are six bits of a local. The transactions that grow: the payment's by the last unit's
byte (and, at a new entry, its flags byte), the receipt's by its unit's byte, `SET_LIMIT`'s
by one copy of four, the rate (seven writes at the most). `SET_CARD`'s and a load's do not.

## 9. What the phone does

**The clock.** The card cannot look at the network, so the phone does. It
fetches the newest block header over Tor from two independent sources
(mempool.space and Blockstream, each by its onion service) and checks it before
it shows it to anyone: the work itself, that both sources name the same block,
and that the block's time is within a few hours of the phone's own clock. Then,
in the tap, where the card's `now` (read in `GET_INFO`) is behind that block's
time, it shows the card the header with `SET_HEADER` and reads the card's `now`
back from the answer. A till does the same before it begins a payment (a payment
begun is dropped by any command that is not its next step, 6c). A phone with no
route has no newer header to show, and the card's day does not move. It also
tells the card its own clock with `TELL_TIME`, for the receipts and the log
(5.3).

The phone's checks are its own good manners. The card checks the work again and
depends on none of them: whatever is shown to it, it takes only a header that
has the work (5.1). Up to software 1.14 the page asked its native side for the
phone's clock signed with an interim key (`cardTime`) and sent it with
`SET_TIME`; there is nothing to sign now.

**A receiver (`cardTake`).** In the tap, after the clock and the reads, work out
what is left of today from `GET_INFO` (limit, `now`, window start, spent today;
a window whose day is over has the whole limit left) and refuse **before the PIN
is sent** a payment whose pieces would pass it (`worth > left today`).
OVER THE CARD'S DAILY LIMIT says what is left today and when the day turns.

**A loader.** Asks nothing of the clock: `LOAD_PROOF` is not held to it (rule 7),
and a card loaded before it has been shown a block spends its first day on trust
(6). It gives the PIN in the tap, whoever it is: every load wants it (rule 7). A
till writing the card's own change back (`LOAD_CHANGE`, 8.2) asks nothing of the
clock either, and needs no PIN.

**The owner's phone.** The page never holds the owner key. Native offers the page:

- `cardOwnerKey {key}`: the owner **public** key for the card with that
  compressed public key: 130 hex characters, uncompressed. Used for `SET_OWNER`.
- `cardOwnerSign {key, label, nonce, value}`: an ECDSA signature (DER, hex) by the
  owner key for that card, over `"FoxyCard/" + label ‖ nonce ‖ value`, where `label`
  is one of `change-pin`, `set-limit`, `set-owner`, `set-card`, `load`, `reset`
  (software 1.17), the nonce is the 16 bytes the card gave and the value has the
  shape that label takes. Native refuses every other label and every other shape,
  before it reads the seed. It answers the signature and nothing else. The card
  knows two labels more, `lock` (`LOCK_CARD` needs the owner's proof under it) and
  `tag` (`SET_TAG`, software 1.19). The phone signs neither yet, so it can neither
  lock a card nor switch its tag.

The owner's private key and the seed never leave native. The draft's action that
handed the page a secret is gone.

Set-up on a card with no owner, by the phone that will be its owner, one tap, in
this order: `SET_PIN`, `VERIFY_PIN`, `SET_CARD` (the record; the 65 bytes where
the time key was go as zeros), `SET_OWNER` last, asked for again while the card
answers `6A98`, keep holding (six askings in a row, some three seconds on the chip,
with KEEP HOLDING on the screen, counting up; 8.3), and then the no-PIN allowance
the holder chose after the PIN, by the owner's proof (`34`, twelve bytes; 6f). The
owner goes in last so that no step needs a proof, and the card is the PIN's, and
can be done again, at every point before it: a set-up cut off anywhere is
finished by the next, with `VERIFY_PIN` first once the card has its PIN. It asks
for no limit: the card starts with none. The PIN is given again at every later
load, since every load wants it (rule 7). A card set up so pays in the next tap,
not in this one (8.3). Set-up for a holder with no phone, by a phone that does not
become the owner, is in 8.4.

After set-up, on the owner's phone:

- **Add funds:** the PIN (`VERIFY_PIN`), then `CLEAR_SPENT` and `LOAD_PROOF`. No
  clock. (Up to 1.20 the owner's grant, `ALLOW_LOAD`, stood in for the PIN here;
  since 1.21 it opens no load.)
- **CHANGE PIN:** `GET_NONCE`, sign `change-pin` over the new PIN, `42`. No old
  PIN is asked: the owner's phone does not know it. A blocked card on its
  owner's phone offers UNBLOCK, which is CHANGE PIN.
- **CHANGE LIMIT** (any amount, or none): `GET_NONCE`, sign `set-limit`, `34`.
  No PIN is asked, and no clock.
- **Taking money off** (withdraw, take back, renew, switching mint), in one tap:
  the limit is **lifted** with the owner's proof (`34`, limit 0), the PIN is
  typed and the payment is signed (6c), and the old limit is **put back** with
  the owner's proof, **restored even when the spending fails part way**. Where
  the card leaves before it can be, the phone has written the old limit down
  first, and puts it back at the next tap. The payment itself still needs the PIN,
  or the no-PIN allowance (6f).
- **Moving the card to another mint** (empty card): `SET_CARD` with the owner's
  proof. The card's clock stays as it is, since `SET_CARD` does not touch it.
- Whether this phone is a card's owner is found out by trying: after reading, the
  phone asks for a nonce and an `ALLOW_LOAD` it has signed. `9000` means the
  phone holds the words the card was set up with; `6A91` means it does not. It
  costs no PIN tries and changes nothing.

A card whose owner is somebody else's, empty or not, is not set up afresh: the
phone says it belongs to another phone's words.

**Screens.** Section 10, in words.

**What the phone leaves out, and what it does with change.**

- A till does not ask the card for `AUTH` (the slowest answer the card gives:
  its own Schnorr signature, most of a second). It is about to be paid in
  pieces the card signs, and a signature that is not good for the key in a
  piece's secret is refused before anything is kept; the mint's swap is what
  says paid. A pretend card gets nothing from that: one with a key of its own
  has pieces the mint did not issue for that key, and one that names the real
  card's key cannot sign for it. `AUTH` stays wherever the holder's phone reads a
  card to show it, and in a withdrawal, where the owner's key signs.
- A tap should take under three seconds, and the card signs a piece in most of
  a second, so an online payment signs as few pieces as it can: the one or two
  that cover the price (and the receiver's fee on them) with the least over,
  and where no two do, the fewest that do. What they come to over the price is
  change, made after the swap and written back at a second tap, or any later one,
  with no PIN, by `LOAD_CHANGE` for each opening of the card's own change (8.2;
  `changeDue`). Every card payment is said as two taps, SEND and RECEIVE; one
  paid exactly is complete at the first. A till with no route takes only an
  exact set (below), found by search, fewest pieces first.
- What goes onto a card is cut like the float in a cash drawer: every power of
  two from 1 up to the largest that fits, once, then the rest of the amount in
  powers of two (every price up to the whole is exact then), and then, with the
  pieces left under the cap, the smallest rungs two or three deep, so that a
  run of offline payments finds the rungs the first took still there; never the
  small change the rest of the wallet keeps, no more than thirty-two pieces (a
  quarter of the card's 128 places), and no more than the card has places for less a dozen kept for an
  online payment's change (an amount that needs
  more is rounded up, smallest pieces first, and the screen says by how much; a
  mint whose largest piece is small makes more of that piece). Change written
  back is cut the same way, filling the card's gaps. A load is one command
  to the card for each piece, so the pieces are what a withdrawal is made of: one
  cut short keeps what the card took, in the phone, and the next tap takes the
  rest.
- A card of 128 places (1.7) is cut deep instead: eight of each size from 1 to
  1,024, smallest first as far as the money goes, and the rest of the amount
  in powers of two. Eight prices in a row are then paid exactly, whatever they
  are, up to 2,047 sats each: one tap, one signature and no change. What the
  card holds counts towards each size, so a top-up and the change of a payment
  fill what has been spent from (change in thirty-two pieces at the most, so
  that the tap that writes it stays short). Where no set of pieces makes a
  price exactly, the drawer is short of something small, and the set that
  overpays least would bring back a sat or two; so a till takes the cheapest
  set that brings back 256 sats or more, whose change fills the small sizes
  again. That set may be over a limit on one payment that the till is not
  told: the card then answers its first `SIGN` with "not yet", having signed
  nothing, and the till pays with the cheapest set after all. Nobody is made
  to wait for change. A payment is thirty-two pieces or fewer wherever such a
  set pays (with a larger piece and change, if need be), and more only where
  nothing that few does.
- A till with no route may take a card on trust, as plain ecash is taken: the
  HIGH RISK question is put to the person first, in front of the amount, and the
  owner's answer decides. Only an exact set of pieces is taken (a till with no route
  cannot give change), refused before the PIN is sent when the card has none; the
  day's limit is looked at as ever, by the card's own clock, which a phone with no
  route has no new block to move on. Every signature the card gives is checked on the phone
  before anything is kept. The pieces are kept as a trusted row, with a pending
  entry, and swapped in when there is a route; if the mint says they were spent
  meanwhile, the entry is marked taken back and the person is told. "Paid" is
  never said. A piece on a card of software before 1.20 carries no DLEQ proof, so
  offline the phone cannot check the mint's signature on it: that is the risk the
  question names. From 1.20 a card can keep the proof (`FOXY-CARD-SPEC.md` 5.9), and a
  phone that reads it and checks it can. A switch of its own turns this off; plain ecash between
  two offline phones has its own, as before.

**Tests on the phone.** The model in `tests/flashcard-card.js` grows the clock,
the window, the owner key and the proofs, with Node's own P-256; the page's tests
run against a fake native side that holds the same derivation;
`tests/flashcard-model.js` holds the model to the applet's transcript as before;
the money suites gain a day that turns and a terminal that re-sends the PIN and
gets one day; a test checks that the page never receives an owner secret.

## 10. Set-up, and what is said

- A card is set up on a card with no owner. The set-up asks for a PIN twice, and
  then for the no-PIN allowance (6f): two dollars unless the holder chooses
  another figure. There is no set-up without a PIN (1.21; from 1.16 to 1.20 a card
  could be set up with none, and paid without one). **It does not ask for a daily
  limit and does not suggest one.** A new card has no limit.
- For a holder with a phone, that phone is made the owner at set-up (8.3). For a
  holder with no phone (1.22), the first phone that sends the card money sets it
  up and loads it under the holder's PIN, and does not become its owner: the card
  has none, the holder's numbers are set with the PIN, and the holder reads the
  history on a friend's phone as a guest (8.4). Up to 1.21 the owner was the
  friend's phone. A phone that is made the owner (the held claim) says, once, on
  the screen that does it: *This phone can reset this card's PIN and limit.
  Whoever holds the card and this phone's seed phrase holds its money.*
- `SET_PIN` once at set-up; the holder types the PIN at a till, at set-up, and at
  every load. On a card with an owner a changed PIN comes from the owner's phone;
  on a card with no owner, from `SET_PIN` under the PIN it has, with the card
  empty (8.4).
- No refund key: a lost card is cash gone, as Foxy's phone sets cards up. A blocked
  card with an owner is not, because the owner unblocks it; a blocked card with no
  owner and money on it is (8.4).
- A card with no owner and nothing on it can be claimed by a reader that has its
  PIN, and that is accepted: it holds nothing. The claim wants the PIN given in
  the tap and six askings in a row, seconds of the card held to the reader, which
  the holder sees, and never comes in a tap that signed a payment (8.3). A card
  leaves the issuer's hands set up, as the draft's 5.4 says, so that nobody has to
  think about it.

**The words on the screens.** The screens say what the limit is, the most the
card will spend in one day, and promise nothing beyond it: a terminal that has
the PIN still takes a day's limit per visit, and a card with no limit has no day
to bound (13).

1. *Menu row on the card's screen:* CHANGE LIMIT.
2. *Step 1, the warning* (the app's warning card). Title: SET DAILY LIMIT. Body:
   "A daily limit is the most this card will spend in one day. It starts again by
   itself each day." / "Only this phone, or a phone restored from its seed
   phrase, can change or remove the limit." / "If you lose the seed phrase for
   this Foxy app, the PIN and the limit on this card can never be changed." /
   "Do you wish to continue?" Buttons: CONTINUE, CANCEL.
3. *Step 2, the amount screen* (the app's SET AMOUNT, dollars first), asking:
   "What would you like the daily limit to be?" It also offers NO LIMIT as the
   way to remove one.
4. *Step 3, the confirmation.* "You are applying a daily limit of: $20.00" (the
   amount chosen) / "This card will spend no more than this in one day. The limit
   starts again by itself each day." / "Only this phone, or a phone restored from
   its seed phrase, can change or remove it." Buttons: CONFIRM, CANCEL. For
   NO LIMIT the confirmation says: "You are removing this card's daily limit. It
   will be able to spend everything on it."
5. *Card set-up, once, on the screen that makes this phone the owner:* "This phone
   can reset this card's PIN and limit. Whoever holds the card and this phone's
   seed phrase holds its money."
6. *The card's screen* shows the daily limit (or NO LIMIT), what is left today, and
   when the day turns.
7. *A till refusing:* title OVER THE CARD'S DAILY LIMIT, saying how much the card
   can still spend today and when its day turns.
8. *A blocked card on its owner's phone* offers UNBLOCK, which is CHANGE PIN.

The allowance draft's screens and wording that no longer apply ("you will need to
connect to your foxy app to restart the limit", SET LIMIT, RAISE THE LIMIT) are
gone.

## 11. From the allowance draft: kept, changed, dropped

This is history: the account of the first design, as it was made for version
1.1, and what it says of the card is what 1.1 did. The clock's part of it was
replaced in 1.15, and the last paragraphs say how, and what later versions
changed of the rest; the sections above say what 1.22 does.

**Kept as drafted:** the instruction bytes `34`, `42`, `43`, `44`, `50` and their
categories; `GET_NONCE` and the nonce's life (RAM, one try, gone with the tap,
replaced by asking again); the `label ‖ nonce ‖ value` binding and the
per-command labels; `6A8F`, `6A90`, `6A91` and the rule that a wrong proof costs
no PIN tries and ends no session; the owner derivation from the seed and the
card's key; `GET_INFO`'s 17 bytes with byte 16 "has owner"; `LOCK_CARD` needing a
verified PIN and the proof, and the phone never carrying it; "the count is of
pieces signed, not the price paid"; the transaction that joins the count and the
status byte, with the signing after, and the test that reads the source to check
it; "`VERIFY_PIN` changes nothing about spending"; `SET_CARD` never touching the
limit.

**Changed:** the allowance becomes a **daily limit** with a window (section 6);
`0` means **no limit** again, not nothing, and is the default; the owner secret
becomes an owner **key** (65 bytes, uncompressed, P-256; the proof is a DER ECDSA
signature the card verifies, not a hash it compares; nothing of the owner crosses
the air, and the key never leaves native code); `CHANGE_PIN` takes the proof and
the new PIN only (**no old PIN, no session**) and resets the tries; `SET_OWNER`
works on a card with no owner while it is empty, and on a card with an owner only
with that owner's proof, and only while it is empty, replacing the owner;
`SET_PIN` works on a card with no owner while it is empty, replacing a PIN, and
not at all on a card with an owner; `SET_CARD` needs the owner's proof on a card
with an owner; `33` returns as `SET_LIMIT` by PIN, **for a card with no owner,
while it is empty** (the daily limit then; since 1.21 the limit on one payment
and the allowance); `SET_CARD` carries the time key; the mint is at most 80
characters (77 since 1.10, section 4); `GET_INFO` grows by twelve bytes and
`GET_CARD` by sixty-five; version 1.1, format 3.

**Dropped:** the allowance that only goes down and only the owner raises; the
rule that a card with no owner can never be given an allowance, and the card
with a PIN and no owner that holds money (it could not be loaded; back in 1.22,
8.4); `6985` on `SET_PIN` when a PIN exists; the owner secret's 32 bytes on the
card and in the air; any page-side secret.

**New:** `SET_TIME`, the time key, `now`, the window, `6A92`, `6A93`, rule 7 (no
money before a time and none without an owner: the time went in 1.15 and the
owner in 1.22, and the rule now asks for the PIN), `ALLOW_LOAD` (`45`; a grant
that let loads through with no PIN up to 1.20, and opens none since 1.21), the
owner's labels for `SET_OWNER`, `SET_CARD` and the load grant, the clock's one way
back (rule 4), and the interim signer (5.2).

**Replaced in 1.15:** `SET_TIME`, the time key, the clock's one way back, the
interim signer, `6A92` and rule 7's "no money without a time". The clock is now
the time in the newest Bitcoin block header the card has been shown (section 5),
so: `SET_HEADER` (`36`) and `TELL_TIME` (`37`) are new; `35` answers `6D00` and
stays unassigned; `SET_CARD` does not read the 65 bytes where the time key was,
and no longer clears the clock; a card that has been shown no block may be
loaded, may be given a limit and spends its first day on trust (6); `6A93` means
little work, and `6A92` is unused; a log entry is twenty bytes and a receipt
seventy-seven; the flag for a clock moved twice in a tap is never set.

**Changed in 1.21 and 1.22:** see section 15.

## 12. How it is tested

- jCardSim, the day: a limit of one piece and four pieces on the card, and a
  terminal that sends the PIN between every spend, takes one, however it is
  repeated, and takes a second only after a header a day later; a new SELECT, a
  reset, `CLEAR_SPENT` and a load leave today as it is; spent today carries
  across taps; exactly the limit goes and one sat more does not, with nothing
  burned; a sum that wraps is over any limit; limit 0 spends with no clock; the
  window turns at 86 400 seconds and not at 86 399; the two-days-at-a-boundary
  bound is pinned as what it is. The first day is on trust: a card that has been
  shown no header loads, is given a limit whose window has no start, and spends
  up to the limit, where a payment within it goes and counts and one past it is
  `6A8F` (never `6A92`) with nothing burned; the first header anchors the window
  at its time with what was spent kept.
- jCardSim, the clock: a header whose hash is over the target of its own `bits`
  is `6A93` and changes nothing, whatever its time and whoever brings it; one at
  exactly the floor is taken, and one a hair over it is `6A93` though its own
  work is done; once a header is taken, one whose target is over four times the
  hardest taken is `6A93`, and exactly four times is taken; `bits` no header
  could carry (a size of 0 or past 32, a mantissa with its top bit set) are
  `6A80`, and any length but 80 is `6700`; an older or repeated header is `9000`
  and changes nothing, a newer one is taken, and the answer is the card's clock
  in four bytes; no key, PIN, owner, record or state refuses a good header, and
  the hardest `bits` are raised only by a header that moves the clock; the
  clock (the time and the newest header's hash) survives `SET_CARD` (with any
  bytes where the time key was), `SET_OWNER`, `CHANGE_PIN`, a reset and an
  emptying and set-up again, so an old block offered to a card set up again is
  ignored, while `SET_CARD` and `RESET_CARD` clear the ratchet's hardest bits,
  the way out of a stalled ratchet (5.2); `35` answers `6D00`; and real
  blocks of the network, at the real floor, are taken or refused as above: the
  genesis block, which has its own work and a target far over the floor, is
  `6A93`, as is the tip with a nonce byte changed.
- jCardSim, the told time: `TELL_TIME` is written into every receipt (offset 4)
  and every log entry (offset 16) made after it, zeros where none was told, and
  a later telling replaces the earlier for what is written after; it needs no
  PIN, owner, record or state, and a length but four is `6700`; it moves nothing
  that lasts; it goes with the power and not with a SELECT; and no clock moved
  on is marked any more (`LOG_FLAG_CLOCK` is a word and is never set).
- jCardSim, the owner: every owner-proved command (`CHANGE_PIN`, `34`,
  `SET_OWNER`, `SET_CARD`, `ALLOW_LOAD`, `LOCK_CARD`) refused with the PIN alone,
  with a nonce and no proof, with another key's signature, with another command's
  label, with a proof for an earlier nonce, for another value, and with one that
  has worked once already, and accepted with the right proof and **no PIN**; a
  wrong proof costs no PIN tries, even five times; `CHANGE_PIN` unblocks a blocked
  card; a card with no owner refuses them with `6A90`.
- jCardSim, A1: a card with an owner refuses `SET_PIN`, `SET_OWNER`, `SET_CARD` and
  the PIN form of `SET_LIMIT` without the owner's proof **even when it is empty**;
  a card with no owner accepts them while empty (and refuses them `6A8D` with one
  unspent piece); the takeover (a terminal with the PIN empties the card, then
  tries to set its own PIN, owner and record) fails for the attacker at every step.
- jCardSim, A2, as 1.22 has it: a card with no owner is loaded under its PIN
  given in the tap and never without it (up to 1.21 it was refused, `6A90`). A3,
  as 1.21 has it: the owner's grant opens no load (`LOAD_PROOF` in every form and
  `CLEAR_SPENT` want the PIN with the grant as without it); what it opens is the
  log, the receipts and the limit on one payment, for the rest of that tap; it
  needs the owner's proof, and is refused on a card with no owner and on a locked
  card; a new SELECT ends it. (Up to 1.20 these tests held the opposite: the grant
  let both through with no PIN, and a card with no owner took no load.)
- jCardSim, the empty card: after `CLEAR_SPENT` of a fully spent card the open
  commands are allowed on a card with no owner.
- jCardSim, a piece is on the card once: a copy of an unspent piece that states
  an amount of 1 (and the piece itself, and the same nonce with a date or another
  keyset) is refused `6A94` with nothing written, nothing counted against the day
  and the real piece as it was; the same for a piece on a spent slot, which loads
  again after `CLEAR_SPENT` and is signed for and charged at its real worth; a nonce
  that differs in its first byte or its last is another piece; under the owner's
  grant alone nothing loads, a copy or another piece.
- jCardSim, GET_PIECES: an empty card is one byte; up to three unspent pieces are one
  page, each the bytes `GET_PROOF` gives after its status byte; a spent slot is a tag
  alone and an empty one is left out; a page from the middle, and from an empty slot; a
  full card is forty-three pages, each within 255 bytes and each moving on; a page fills to
  255 bytes and not a byte over (three pieces and eight spent tags, then the next piece
  starts a page); it asks for no PIN and changes nothing; a slot there is not is `6A83`.
  The transcript has it, and the phone's model of the card is held to it.
- jCardSim, 128 places (1.7): the short listing is two bytes a piece after the first
  where keyset and date are shared, names them again where either differs and at the
  start of every answer, gives `FF` and four bytes for an amount that is no power of
  two and the size for every power from 1 to 2^31, skips spent and empty places, and
  takes two answers for a full card; the whole listing names places above 63 and marks
  a spent one by its high bit; every command that names a place takes 64 to 127 and
  refuses 128; a payment of 33, of 64 and of all 128 places signs once, burns exactly
  those, waits as its sum says, and is charged to the day whole; 129 is `6A96`; a burn
  that fails is `6A96` with nothing burned. Each was broken on purpose in a copy of the
  applet, one change at a time (74 changes), and every change was caught by a test.
- jCardSim, the allowance (1.16, and 1.21's rules): the twelve-byte limit (every
  other length refused, numbers that do not change keeping their count); the
  allowance to the sat (to its limit signs, one sat more is `6A94`; a payment is
  not split; a payment with the PIN counts for nothing; the whole of the pieces,
  change and all, is what is counted, so a reader with no PIN that names a large
  piece and asks almost all of it back as change is refused; an allowance of 0
  asks for the PIN even for a payment that takes nothing; a blocked card has none,
  and its count stands through it); the PIN given renews the count, and no day
  does, however many pass; with only the allowance set no window is begun; a card
  that has seen no header counts it from set-up and a header changes nothing of
  the count (where `ALLOWANCE_RENEWS_ON_PIN` is false, 1.16's day rule is pinned as
  well); the PIN asked before the limits,
  so that a refusal for want of it writes nothing in the log and a run of them marks
  no tamper, and a payment within the allowance and over the day refused as ever;
  the wait and the second payment in a tap applying to a payment with no PIN; a sum
  that wraps; the log's flag; the last signature again. The transcript has the
  allowance, and the phone's model of the card is held to it. (The tests of 1.16's
  card with no PIN, and of ADD PIN, went with them in 1.21.)
- jCardSim, a PIN first, the card's own change and the claim (1.21): a card with
  no PIN refuses `LOAD_PROOF` (both forms), `CLEAR_SPENT`, `LOAD_CHANGE`, every
  payment command and an owner's claim with `6A97` and nothing else, and takes
  `SET_PIN`, after which the first steps are the PIN, the record and the claim or
  the load; a reset card is a card with no PIN again; no command leaves a card
  with an owner and no PIN. No tap is the tap after a payment: any reader, in any
  tap, with no PIN, writes any opening's change under the key it was bound to (an
  earlier payment's as well as the last, each once, in the paying tap too, a
  second `SELECT` changing nothing), and nothing else: no `LOAD_PROOF` in any form
  and no `CLEAR_SPENT`, with the grant or without; a locked card and a blocked PIN
  still take their own change. The key is bound at `SPEND_ALL_CHANGE` (37 bytes;
  another length `6700`, a key not `02` or `03` `6A80`, the payment given up
  either way), kept from the moment the opening is made, and owed openings go from
  draft to owed in the payment's own transaction, torn or failed. A key made up
  after the payment, with a proof that holds for it, is refused `6A80` with
  nothing written, and so is the bound key's negation. `LOAD_CHANGE` writes
  `C = k·Y`, worked out by the test on its own from the opening's secret, with the
  opening's keyset, amount, nonce and date, keeps the mint's `e`, `s` and the
  factor `r` as the piece's proof, which a wallet's NUT-12 check passes, answers
  the place, `01` and the piece, and the piece pays; `GET_CHANGE` gives the
  number, amount, keyset, date, blinded message and bound key and never the nonce
  or the blinding factor; every refusal (a proof whose `e` or `s` is off, another
  opening's answer, a point off the curve or not in the `04` form, a wrong length,
  a number past 7, an opening not waiting, no place free, a piece on the card
  already) writes nothing. The claim: six askings, `6A98` to each but the last
  and never `9000` before the sixth; a different key, another command or a new
  `SELECT` begins the count again, a refused asking neither counts nor ends it, and
  the count is gone at a deselect; refused with no PIN given, and in a tap that
  signed a payment (even after a reset); a payment refused after a claim in the
  same tap. The 1.21 rules were broken on purpose in a copy of the applet, one at a
  time, and a test failed for each.
- jCardSim, a holder with no phone (1.22): on a card with a PIN and no owner
  `SET_CARD`, `LOAD_PROOF` and `CLEAR_SPENT` go through under the PIN given in the
  tap and are refused without it (`6982`); the record holds no refund key or the
  loading phone's; the PIN form of `SET_LIMIT` sets the limit on one payment and
  the allowance, and is refused with a piece on the card or change owed (`6A8D`).
  The guest's history: nine `6A98` and the answer at the tenth asking, for the log
  and the receipts as one hold, then open for the rest of the time in the field;
  the count begins again on any other command, a `SELECT`, a `VERIFY_PIN` and a
  claim among them, and neither count adds to the other; no history in a paying
  tap and no payment in a reading tap (`6985`). A set-up card keeps its PIN, a
  blocked PIN owed change is not replaced, a used card is rewritten only after a
  hold (`6985` in the paying tap, `6982` with no hold), and the limit on one
  payment is told to the PIN in a held tap and to no other. A card with an owner
  is 1.21's. Change owed: the mint's real answer under another key is taken under
  the PIN and the owner's grant, or the PIN and the hold with no owner, in a tap
  that paid nothing, and the PIN alone mends nothing (`6982`, and `6985` after a
  payment); the let-go by the owner's proof, with the PIN on a locked card, and
  with no owner under the PIN in a held tap, written in the log by bit 4; `6A94`
  for an opening whose piece is on the card already, with nothing logged; `6A97`
  for the let-go of a card with no PIN.
- The source-reading test: spent today and the slot's status change between one
  `beginTransaction` and its commit, with the signing after.
- Vectors in `spec/vectors/`: an owner key and a proof for each label, and one for
  the limit's label over all twelve bytes
  (`owner.json`; ECDSA signatures are randomised, so each is one the card
  verified, and another implementation verifies it again); the transcript,
  extended (`transcript.json`), which the phone's JavaScript model of the card is
  held to command for command.
- On a card, by hand: the ECDSA verify on the J3R180 (that it takes the DER form and
  the uncompressed key, and how long it takes, once for each owner command);
  `SET_HEADER` (two SHA-256 passes and a few compares: expected well under 20 ms,
  not yet measured); set-up, a day's spending, a day turned by a block, a wrong PIN
  three times and an owner's unblock, a tap pulled away at each step; and the third
  permanent write per spend (spent today, with the status byte and, on a new day,
  the window start) against the chip's write endurance.

## 13. What it does not protect against

- **A terminal built to cheat is bound by the day.** It cannot start another by
  telling the card it is later: the card's clock is the newest block header it
  has been shown, believed for its work and for nothing else. To end a day the
  terminal has to bring a header dated a day on from the window's start, which is
  a real block when the world has made one, or a forgery at about a quarter of a
  block's work (5.2). It cannot shorten the day any other way.
- A terminal that has been handed the PIN takes one day's limit per visit, and up to
  two across a window's boundary. It does not check a payment, and nothing on the
  card can.
- **Nothing is bound that the owner has not set.** The day, and the limit on one
  payment (6a; the per-tap limit), each need the owner to have set a number, and
  a new card has neither: set-up asks for none (section 10).
- **The no-PIN allowance is cash.** A card signs its no-PIN allowance to whoever
  taps it, and no more without the PIN until the PIN is given again; a thief with
  the card has that much once (6f). (From 1.16 to 1.20 it was that much a day, and
  a card could have no PIN at all, and was then cash to whoever held it, up to its
  limits; since 1.21 every card has a PIN.)
- **A forged header.** Whoever spends the work of a header can show the card any
  time. On a card that has taken a header from the network the work is a quarter
  of a block's; on a card that has taken none it is the floor's, a quarter of a
  block's work in this version's time (5.2). It buys one more day's limit, or a clock frozen
  years ahead, after which the card signs for one more limit's worth in all until
  the owner sets the limit again. The price is the same whatever the card holds.
- **A locked card still pays.** `LOCK_CARD` freezes the card's settings and every
  write; it does not stop a payment. Under its PIN and up to its no-PIN allowance a
  locked card pays as before, and its limit, its allowance and its PIN can no
  longer be changed. A locked card is not a lost card made safe (8, `50`).
- **A card that has been shown no header** spends its first day on trust, up to
  its limit, and a terminal that chooses the first header it shows can make that
  two limits' worth, once (6).
- **A clock that is not the second.** A block's time can lead the true time by up
  to two hours (5.1), and the card's day moves only when it is shown a newer
  block: a card that is shown none does not end its day.
- **A network whose work falls** to under a quarter of the hardest header a card
  has taken stops that card taking headers. Its clock stops, and nothing resets
  the bar (5.2). It fails closed: the card signs for no more than what is left of
  its day, and the owner can give it the limit again.
- A terminal that has been handed the PIN can also write onto the card pieces the
  mint will refuse, made up with a nonce no mint ever signed. They take nothing from anyone, since the
  receiver always asks the mint, but a later payment that picks one burns slots and
  spends a day's limit on nothing, and while such a piece is on the card it is not
  empty, so the owner cannot replace its owner or record until it is spent. A fake
  piece looks unspent to a state check. From 1.20 the card can keep the mint's proof
  for a piece, and a phone that reads and checks it sees that a made-up piece is not
  real (`FOXY-CARD-SPEC.md` 5.9); the card checks nothing, so a card loaded with no
  proofs is as it was.
  Up to 1.20 the reader that tapped the card next after any payment could do the
  same once without the PIN, and free the burned places. Since 1.21 it can do
  neither: no tap loads without the PIN but the card's own change, and that only
  under the key bound at the payment (8.2). What is left is the holder's own: on a
  card with no owner, a reader that keeps the PIN and holds a tap of its own, and on
  a card with an owner, the owner's phone, can write under one of the card's own
  openings a piece signed by a key of its own making, with a proof that holds for
  that key; the mint will not take it, and that opening's change is spoiled (8.2).
  It takes nothing, and puts no piece of its own on.
- It cannot write a **copy** of a real piece. The card signs a piece's secret, which
  its nonce makes, and takes the amount on the terminal's word, so a copy stating an
  amount of 1 would have been spent for 1 and signed for the real piece. `LOAD_PROOF`
  refuses a nonce that is in any slot, spent or not (`6A94`). What the card does not
  do is remember a nonce once `CLEAR_SPENT` has freed its slot: a piece that was
  burned and whose signature never reached the mint (a tap cut off at the wrong
  moment) can be read from its spent slot, cleared, loaded again with a small amount
  and signed for again, by a terminal that has the PIN. A signature that was handed
  over has already been paid for in full.
- A holder with no phone has no balance they can read that a terminal or a
  friend's phone did not draw. That is a screen, not a clock.
- **A card with no owner is the PIN's (1.22).** A till that keeps the PIN it was
  handed can, in a tap of its own held for the seconds a guest's read takes, read
  the card's history, learn its limit on one payment, and, once the card is empty
  and owed nothing, rewrite its record, its numbers and its PIN, or claim it; and it
  can spoil or give up change still owed (8.2, 8.4). The hold keeps all of that out
  of the tap the till was paid in. Three wrong PINs strand the money on such a card:
  it has no owner to unblock it, and a blocked PIN is replaced only on an empty
  card.
- Whoever has the owner's words and the card in hand has everything on it.
- A holder whose owner has lost their words can never change the PIN or the limit.
- The PIN sent in its clear form crosses a few centimetres of air as it was typed;
  the sealed form (6e) does not, and the clear forms stay for a terminal that knows
  no other.
- A lost card is cash gone where its record has no refund key, which is every card
  Foxy's phone sets up.

## 14. Decided, and left

Decided: no limit by default and none asked at set-up (A8); no loading of a card
with no owner (A2; decided again otherwise in 1.22: a card with no owner is loaded
under its PIN, 8.4); the owner's phone adds funds without the PIN (A3; decided again
otherwise in 1.21: every load wants the PIN, rule 7); the owner
key is P-256 and ECDSA (A6), and stays in native code (A7); taking money off lifts
and restores the limit in one tap (A9); the owner's proof is ECDSA, with the hash
as a documented fallback only (A11); the clock is the newest Bitcoin block header
the card has been shown, believed for its work, held to a floor and to a quarter
of the hardest it has taken (1.15). The clock's one way back (A4) and the interim
signer (A10) went with the signed time. Decided since: a PIN before anything, the
card's own change made, bound and unblinded on the card, the owner claimed slowly,
and the allowance renewed by the PIN (1.21, `DECISIONS.md` D16); a holder with no
phone, the guest's history held for, and change owed mended or given up (1.22,
D17).

Left: the time `SET_HEADER` takes on the chip (not yet measured, 5.1); when the
floor is raised (5.2); and whether the ECDSA verifier on the first card behaves
(7.2).

## 15. What changed in 1.21, 1.22 and 1.23 (once "1.21 in one place" and "1.22 in one place")

History. Until the sections above were rewritten for 1.22, this document opened
with two sections of rules, one for each version, named in this section's title,
which won over anything later that still said otherwise. Their rules are kept here, with
their numbers, one line each, and the section that now states each one. Nothing
here overrides anything above.

**Software 1.21** (`SELECT` answers `01 15`):

1. A PIN before anything: a card with no PIN answers `6A97` to every load, every
   payment command and a claim (6f).
2. Every load needs the PIN given in this tap; the owner's grant and the tap after a
   payment open none (section 3, rules 7 and 9).
3. The card's own change is bound to the mint's key at the payment, unblinded on the
   card by `LOAD_CHANGE`, and loaded by any reader, with no PIN (8.2).
4. Change owed holds up `SET_CARD` and `RESET_CARD` (`6A8D`) (8.2).
5. The owner's claim is a tap of its own: under the PIN, six askings, `6A98` to each
   but the last, never with a payment in the same tap (8.3).
6. The no-PIN allowance counts the whole of a payment's pieces and is renewed by the
   PIN, not by the day (6f).
7. The holder sets the limit on one payment and the allowance with the PIN on a card
   with no owner (`33`); the owner's form rules once there is an owner (8, 8.4).
8. Foxy's phone sets no daily limit at launch (6).

**Software 1.22** (`SELECT` answers `01 16`):

1. A card with a PIN and no owner is set up and loaded under the PIN (8.4, rule 1).
2. Its record: no refund key or the loading phone's, and the design (8.4, rule 2).
3. The holder's numbers, with the PIN, while nothing is unspent or owed (8.4,
   rule 3).
4. Its history is the PIN's, held for by ten askings (8.4, rule 4; 6b).
5. No payment in a tap that read the history (8.4, rule 5).
6. The balance, the pieces and `GET_INFO` stay open (8.4, rule 6).
7. A card with an owner is 1.21's (8.4, rule 7).
8. The claim is only of an empty card (8.3; 8.4, rule 8).
9. A set-up card keeps its PIN; a blocked one is replaced only with nothing owed,
   and the history goes with it (8.4, rule 9).
10. A used card is rewritten only after a hold (8.4, rule 10).
11. The guest sees the limit on one payment (8.4, rule 11; 6a).
12. Change owed is mended under the PIN and the holder's say, or given up by
    `RESET_CARD` with P1 = 1 (8.2). Both need a PIN that can be given: a card
    whose PIN is blocked with change owed cannot do either (8.2).

**Software 1.23** (`SELECT` answers `01 17`): dollars and sats on one card at its one mint,
the table of keysets in the book the first `SET_CARD` makes, every limit in cents with the
rate written beside them, one unit a payment, and the log and the receipts saying each
payment's unit (8.5, and `FOXY-CARD-KEYSETS.md`). Sats are not put on a card with a limit
and no rate (`6A99` at the load), where no payment could take them off again.
