# Foxy card: one PIN, a daily limit, an owner key

The second design of the card's limit, replacing the allowance of
`FOXY-CARD-SPEC.md` 5.3 (rule 3), 5.6 and 5.7 as they stood in the working
tree, and the `SET_ALLOWANCE` and owner-secret rows of its 5.2. Everything else
in that specification stands: the slot, the secret text, `SPEND` from the slot,
`AUTH`, the gate in every PIN state, D10 and D14, provisioning, the phone's
side. Where this document is silent, that one speaks; where the two differ,
this one is what is built.

What is kept from the earlier draft, what is changed and what is dropped is
listed at the end (section 11), because the applet before this design
implemented the draft and the changes are easier to make from that list than
from the prose.

## 0. Why

The first limit was on one PIN entry, kept in RAM and reset by `VERIFY_PIN`.
A terminal that had been handed the PIN sent `VERIFY_PIN` again between
spends, or `SET_LIMIT 0`, and took the whole card in one tap; the limit bounded
nothing (the audit, with a jCardSim proof). The draft that followed put an
allowance in permanent memory that every spend lowered and only the holder's
phone could raise. That holds against a terminal, but a card whose allowance is
used up has stopped until its holder's phone has been to it, and some holders
will not have a phone. Their card is set up on a friend's, and should then work
on its own.

So the limit is **per day**, and the card keeps the day itself, from a time it
is told under a signature it can check. A terminal with the PIN can take one
day's limit per visit and nothing more, and the holder never has to bring the
card back to anything.

**Read section 5.2 before relying on any of this.** Until a real time signer
exists, the time the card is told comes from the receiving phone's own clock,
signed with a key that is inside the app and so is not secret. That bounds a
receiver that is honest and the holder's own overspending. It does not bound a
terminal that is built to cheat.

## 1. Goals

1. A holder with no phone of their own carries a card that bounds, by itself,
   what any honest terminal can take from it: one day's limit per visit.
2. Nothing about a funded card (its PIN, its limit, its mint, its owner, its
   time key) can be changed by anyone who merely holds the PIN.
3. The phone that set the card up (the **owner**: the holder's own, or a
   friend's for a holder who has none) can change the PIN and the limit at any
   time, without knowing the PIN, can add funds without the PIN, and can
   unblock a blocked card.
4. A card with **no owner** is nobody's and is open while it is empty, because
   there is nothing on it to protect; it cannot be loaded. A card **with** an
   owner is its owner's, empty or not.
5. The receiver's and holder's sides of `FOXY-CARD-SPEC.md` are unchanged in
   what they promise: paid only when the mint has given the phone its pieces;
   nothing signed when a refusal can be known beforehand.

Not goals: a limit that follows the price rather than the pieces signed
(section 6); a check of pieces a terminal writes onto the card (DLEQ, phase
3); a balance the holder can read without a phone (a screen; a separate
question); recovery of a lost card (no refund key for now); a time that cannot
be forged (section 5.2).

## 2. Who is trusted with what

| Party | Holds | Can do wrong |
|---|---|---|
| Card | its key, the pieces, the PIN, its owner's public key, the time key, the day | nothing by itself; it signs what it is asked, under its rules, up to the day's limit |
| Cardholder | the card and the PIN | pay twice from a copied card, if a receiver ever accepts without the mint |
| Owner's phone | the twelve words, and from them the card's owner key | with the card in hand: change the PIN, set or remove the limit, add funds, and so take everything. Without the card: nothing. It is a trustee, held on a leash of two things: its words and the card itself |
| Receiving phone | sees the PIN, talks to the card | ask the card to sign more than the amount shown, up to what is left of today's limit; keep change; write pieces the mint will refuse |
| Mint | decides what is spent | what any Cashu mint can |
| Holder of the time key's private half | a key | **sign a later time whenever it likes, day after day, in one tap, and so, with the PIN and the card, pass any limit**; sign one far-future time and freeze a card's day until its time key is replaced (section 3, rule 4). Never move a clock backward |

Two consequences shape the design, as before, and one is new. The receiver
always goes to the mint before saying paid, so a copied or pretend card gains
nothing. A terminal sees the PIN and chooses what to ask the card for, so
nothing the PIN alone can reach may bound it or change it. And the one thing a
terminal cannot manufacture, **once the time key is held by a real signer and
not by every copy of the app**, is tomorrow: it can fetch a true "now", but not
a future one, so a window that only time can reset is a bound it cannot walk
round. With the interim signer of 5.2 that last sentence is not yet true.

## 3. The rules

1. **The PIN spends and nothing else.** With the PIN a terminal can `SPEND`
   (up to the day's limit), `LOAD_PROOF` (change written back) and
   `CLEAR_SPENT`. On a card with an owner the PIN changes nothing else: not the
   PIN, not the limit, not the record, not the owner, not the time key.
2. **The owner's proof administers.** `CHANGE_PIN`, the owner's `SET_LIMIT`
   (`34`), `SET_OWNER`, `SET_CARD`, `ALLOW_LOAD` and `LOCK_CARD` need an ECDSA
   signature by the owner key over a label of the command, a nonce the card has
   just given, and the value. They need no PIN, because the owner's phone does
   not know it (`LOCK_CARD` keeps its PIN as well). The signature is checked by
   the card's native ECDSA verifier; the owner's private key never leaves the
   phone's native side.
3. **A card with an owner is not open, empty or not; a card with none is open
   while it is empty.** Everything that sets or replaces the PIN, the owner, the
   card record (mint, unit, refund key, time key) or the limit needs, on a card
   with an owner, that owner's proof, whether or not the card holds anything.
   Replacing the owner, or the record, needs the card to be empty as well. A
   card with **no** owner is the factory-fresh card, and while it has no
   unspent piece those same things are set with no proof (the PIN where they
   need one). Why not "an empty card is open", as the first version of this
   design said: a terminal holding the PIN can make a card empty whenever its
   balance fits within the day's limit, and could then set its own PIN and owner
   and load a junk piece, taking the card over for good. The table in section 8
   is the rule worked through every command.
4. **The day is the card's.** The card keeps `now`, the latest time it has been
   told under the time key's signature. `now` only ever moves forward, with
   **one exception**: `SET_CARD` that writes a time key **different** from the
   one the card holds, on a card with nothing unspent (and with the owner's
   proof, if the card has an owner), sets `now` back to 0, and with it the
   window (its start and what was spent). That is the way out of a signer's
   fault: one wrongly signed far-future time would otherwise freeze a card's day
   until real time caught up. In no other case does anything clear it: not
   `SET_CARD` with the same key, not `SET_OWNER`, not a new PIN, not a reset. A
   stale or replayed time is simply ignored; an absent one leaves the card in
   its old window. The design fails closed.
5. **The window is anchored by the limit, and reset only by time.** Setting
   the limit starts a window at `now`. A `SPEND` that finds `now` a day or more
   past the window's start begins a new one at `now`. Nothing else begins one:
   not `VERIFY_PIN`, not a new SELECT, not a reset, not a load.
6. **Refuse before burning; count and burn together.** A piece that would take
   the day past its limit is refused with nothing signed, nothing burned and
   nothing written. Otherwise the day's total and the slot's status change in
   one transaction, and the signing comes after.
7. **No money without a time, and none without an owner.** `LOAD_PROOF` is
   refused on a card that has never been told the time (`6A92`), so the earliest
   `now` a funded card can hold is its own loading; with rule 4, no older time
   can ever get in after it. `LOAD_PROOF` is refused on a card that has no owner
   (`6A90`): there is nobody who could change its PIN or its limit, and no
   "plain cash card with a PIN and no owner" exists.
8. **Three tries, then blocked** and the owner unblocks it. A `CHANGE_PIN` with
   the owner's proof resets the tries and sets a new PIN, on any card with an
   owner, blocked or not.
9. **The owner adds funds with no PIN.** `ALLOW_LOAD` (owner's proof, label
   `FoxyCard/load`) lets `LOAD_PROOF` and `CLEAR_SPENT` through for the rest of
   that tap, without a verified PIN. It is held in transient memory, so a new
   SELECT or a reset ends it. It lets nothing else through: not `SPEND`, not
   anything the PIN opens. A till still writes change back under the verified
   PIN as before.
10. The rest of `FOXY-CARD-SPEC.md` 5.3: no signature without a slot burned;
    the PIN gate first in every state; nothing loaded without a PIN set; every
    buffer at install and the status byte last.

## 4. What the card stores

The card record, extended. `SET_CARD` writes the unit, the refund key, the
time key, the mint and the set flag, and never the limit, the day or `now`
(except as rule 4 says):

| Field | Size | |
|---|---|---|
| format | 1 | **3** for this design (2 was the allowance draft and the first limit) |
| set | 1 | 1 once `SET_CARD` has run |
| unit | 1 | sats |
| limit | 4 | the most the card signs for in one day, in sats, big-endian; **0 is no limit, and is what a new card has** |
| refund key | 33 | zeros for now (no refund key) |
| time key | 65 | the time signer's public key, uncompressed (`04 ‖ X ‖ Y`), written by `SET_CARD` |
| now | 4 | the latest signed time the card has accepted, seconds, big-endian; 0 until it has been told one. Written only by `SET_TIME` (and cleared only as rule 4 says) |
| window start | 4 | when the current day began; written by `SET_LIMIT` and by a `SPEND` that begins a new day |
| spent today | 4 | what the card has signed for since the window began |
| tap limit | 4 | the most the card signs for in one tap, in sats, big-endian; **0 is no limit** (section 6a). Not in `GET_CARD`; `GET_INFO` with P1 = 1 says it |
| tap start | 4 | when the current tap began; written by an eight-byte `SET_LIMIT` that changes the tap's limit and by a `SPEND` that begins a new tap |
| spent this tap | 4 | what has been signed for since the tap began |
| mint | 1 + up to 80 | the mint's address, as text |

The mint is at most 80 characters, not 96 as it was. The reason is one
command: `SET_CARD` with the owner's proof on a card that has an owner carries a
proof of up to 72 bytes, 100 bytes of record and the mint, and a short APDU
holds 255 data bytes in all (1 + 72 + 100 + 80 = 253).

Kept apart from the record, as the owner secret was: the **owner key**, 65
bytes uncompressed, and a flag that one has been given (`GET_INFO` byte 16).

Kept in RAM, gone with the tap: the **nonce** the card last gave for an
owner's proof and whether it is still live, and whether `ALLOW_LOAD` has been
given in this tap. There is no per-entry count any more; `VERIFY_PIN` changes
nothing about spending and can be sent as often as its sender likes.

**The curve.** The owner key and the time key are NIST P-256 (secp256r1),
signatures are ECDSA with SHA-256 in DER form, public keys are uncompressed
(`04 ‖ X ‖ Y`, 65 bytes). They have nothing to do with the card's own
secp256k1 key or with Cashu. The applet sets the curve's parameters on both
keys itself (`p`, `a`, `b`, `G`, `n` and a cofactor of 1) and never relies on a
default.

Why the time key is in the record and not a constant in the applet: so that
the move from the interim signer to a real one (section 5.2) is a provisioning
change, not a new applet. The cost is one more thing `SET_CARD` writes, and it
is written only on a card with nothing unspent.

Why the keys are uncompressed: the card's ECDSA verifier takes a public key
through `setW`, which wants `04 ‖ X ‖ Y`, and decompressing a point on the card
is a modular square root nobody should write. Sixty-five bytes each is the
whole cost.

## 5. Time

### 5.1 What the card accepts

`SET_TIME` carries a time `t`, four bytes big-endian seconds, and an ECDSA
signature. The card verifies, with `Signature.ALG_ECDSA_SHA_256` on the time
key, a signature over the bytes

    "FoxyCard/time" ‖ t

(the verifier hashes once, itself, so this is a prefix and the bytes, not a
BIP-340 tagged hash). The signature is in the DER form the verifier takes, and
is sent with its length in front of it. Then:

- a card with no record (so no time key): `6A8C`, nothing changes;
- not the time key's signature: `6A93`, nothing changes;
- `t ≤ now`: `9000`, nothing changes (an old or repeated time is harmless);
- otherwise `now = t`.

Either way the answer is the card's `now`, four bytes, so the phone sees what
the card believes. No PIN, no owner, no state the card can be in refuses it: a
blocked or locked card still takes the time. A time can only move the clock
forward, and moving it forward is what ends a window early, which is why who may
sign one matters (5.2).

There is no nonce and no freshness check, on purpose. A stale time cannot help
anybody: it only keeps a window from resetting. A fresh one is the truth. So a
signed time is a **public broadcast**: a terminal may fetch one and reuse it for
a minute, and nothing about a tap waits on the signer.

### 5.2 The signer: what it is for now, and what it must become

**For now, and only for now, there is no server.** The time the card is told is
the **receiving phone's own clock**, signed by Foxy's app with a P-256 key whose
private half is **built into the app** as a constant named for what it is
(`InterimCardTime`, in `Foxy/Flashcard/CardTime.swift`). Its public half is what
set-up writes to the card as the time key. The card's code is exactly the final
design: it verifies against the time key in its record. Only where the
signature comes from is interim.

This is **as weak as trusting the receiver's clock**, because that is what it
is:

- the private half is in every copy of the app, so anyone can extract it and
  sign any time they like;
- so a terminal built to cheat can tell the card it is a day later, again and
  again, in one tap, and take the whole balance within the PIN's reach. With
  the PIN and the card, the limit stops it no better than having no limit;
- a wrongly set clock on an honest phone signs a wrong time too: a clock set
  far ahead freezes the card's day (the card never takes an earlier time), until
  a different time key is written to the card while it is empty (rule 4), which
  is the way out;
- what it **does** bound: an honest receiver cannot be talked into taking more
  than a day's limit by accident, and the holder's own overspending is held to
  the day. It is a bound against mistakes and honest terminals, not against an
  attacker;
- so no screen says that the limit stops an attacker, and the threat notes say
  the same.

A **real signer replaces it by provisioning**, not by a new applet: a service
that signs only its own disciplined clock, never a time it is asked for; whose
private key is made away from the service and held well; whose public half is
written to cards as their time key. Then the paragraph above is false and the
last sentence of section 2 is true. Rules for that signer, for when it exists:

- it signs only its **own** clock, never a time it is asked for; the request
  carries nothing;
- its clock is disciplined (NTP): a clock ahead of true resets windows early,
  by exactly that much, for every card, and a far-future time freezes every card
  that took it;
- the **holder of its private key can sign day after day in one tap**, so with
  the PIN and the card they can pass any limit; and one far-future signature
  freezes a card's day until the card's time key is replaced (rule 4). So the key
  is made away from the service, held well, and never reused for anything else;
- the key is long-lived. Changing it is a provisioning change for every card (an
  empty card can be given the new key by its owner; a funded card cannot, until
  it is emptied; a card with a key nobody signs for any more is frozen in its
  last window, which is safe and inconvenient).

What the phone does with it is in section 9.

## 6. The day

**The window.** `SET_LIMIT`, by either path, writes the limit, sets the window
start to `now` and spent-today to 0, and needs `now ≠ 0` unless the limit is 0
(`6A92`). On `SPEND`, after the PIN gate and the slot checks, when the limit is
not 0:

1. `now = 0` → `6A92`: a card that has never been told the time does not spend
   under a limit.
2. If `now ≥ window start + 86 400`, the window is new: it will start at `now`
   with nothing spent.
3. spent today (0 in a new window) + the piece's amount, with its carry, over
   the limit → `6A8F`, nothing signed, nothing burned, nothing written.
4. Otherwise, in one transaction: the window start (if new) `= now`; spent today
   `+=` the amount; the slot's status `=` spent. Then the signature.

A limit of 0 is no limit: steps 1 to 3 are skipped, nothing is counted, and a
card with no limit needs no time to spend. **A new card's limit is 0, and
set-up does not ask for one**; a limit is set later from CHANGE LIMIT, to any
amount, and removed the same way (section 10).

**It counts the pieces signed, not the price paid**, as the draft's allowance
did and for the same reason: the card cannot check what a terminal writes back,
so change does not give the day anything back. A 300-sat payment made with one
piece of 1,000 costs the day 1,000. A till picks pieces as near the price as it
can (`FOXY-CARD-SPEC.md` 8.1), and the holder's phone loads a card in pieces
small enough for the limit to be useful.

**The edge of a window.** A window is a fixed day from its start, so a terminal
that straddles one boundary can take up to two days' limit in a short span. That
is the bound, and it is written down rather than hidden. (Rolling windows need a
log of spends; a fixed one needs eight bytes.)

**What a day is worth to a bad terminal.** With a real time signer: one visit,
one day's limit, in one tap if it likes, and nothing it can send changes the
day's length, the limit, or the clock, except forward to the truth. With the
interim signer: whatever the PIN reaches (5.2).

**What the owner's own withdrawal does to it.** The owner's phone taking money
off the card lifts the limit with its proof, spends, and puts the old limit back
in the same tap (section 9). Putting the limit back is a `SET_LIMIT`, which
starts a new window with nothing spent, so a withdrawal gives the day back its
whole limit. That is one extra day's limit, at the moment the owner is holding
the card, and no more.

## 6a. One tap

A second limit, kept the same way: the most the card signs for in **one tap**.
It is there for the same terminal the day is, and for the hold of a card to a
phone that the day does not bound: with no limit on a tap, whatever the day
allows goes in one tap, and with no limit on the day either, the whole card.

**A tap is ten seconds of the card's clock** (`TAP_SECONDS`), from the first
piece signed in it. It is not a PIN entry, a SELECT, or a time in the field. The
first limit this card had was on one PIN entry (section 0) and bounded nothing,
because everything that began it again was something a terminal sends. What a
terminal cannot send is a later time, once the signer is real (5.2); until then
this limit is exactly as strong as the day's, and no stronger.

On `SPEND`, after the day's check and before anything is signed, when the tap
limit is not 0:

1. `now = 0` → `6A92`, as for the day.
2. If `now ≥ tap start + 10`, the tap is new: it will start at `now` with nothing
   spent.
3. spent this tap (0 in a new one) + the piece's amount, with its carry, over the
   tap limit → **`6A95`**, nothing signed, nothing burned, nothing written.
4. Otherwise, in the same transaction as the day's count and the burn: the tap
   start (if new) `= now`; spent this tap `+=` the amount.

**Ten seconds** is longer than a card is held to a phone to pay (a read and two
signatures are under three), so one hold is one tap; and short enough that the
next charge, which a person has to type an amount and a PIN for, begins a tap of
its own. A payment larger than the limit is made as more than one charge. A
terminal that keeps a card for a minute can take six taps' worth: the bound is
per ten seconds of holding, and it is written down rather than hidden.

**It counts the pieces signed, not the price**, as the day does.

**Setting it.** `SET_LIMIT` takes four bytes or eight, by either form. Four are
the day's limit, as before, and leave the tap's as it is. Eight are both: the
day's, then the tap's. In the eight-byte form a limit whose number does not
change keeps its window and its count, so that setting one does not begin the
other again; a number that changes begins its window at `now` with nothing
spent. The owner's proof covers all eight bytes under `FoxyCard/set-limit`. A
limit on a tap needs a time, as a limit on the day does (`6A92`). The owner's
own withdrawal lifts both with one command (`0, 0`) and puts both back with one.

**Reading it.** `GET_INFO` with P1 = 1 answers 42 bytes: the thirty it always
gave, then the tap limit, the tap start and spent this tap, four bytes each.
With P1 = 0 it answers the thirty, so a reader that checks for thirty still
gets them. An applet without this section ignores P1 and answers thirty: that
is how a phone tells that a card has no limit on a tap to set.

**A different time key** (rule 4) clears the tap start and its count with the
clock and the day's window. The limit itself stays.

## 6b. The log

The card keeps its own account of what it has signed for and what it has
refused. It is the only record of a card's use that no terminal's honesty is
needed for, and it is what a holder reads to find out what a tap really took.

**Only the card writes it.** A `SPEND` writes it in the transaction that burns
the piece: no piece is burned that the log does not have. A `SPEND` refused for
being over a limit (`6A8F`, `6A95`) writes it and then refuses. No command sets
it, moves it back or clears it, for the PIN or for the owner, and `CLEAR_SPENT`,
`SET_LIMIT`, `CHANGE_PIN`, `SET_CARD` and `LOCK_CARD` leave it as it is. The
counts only go up, and stop at the top of four bytes.

**What it holds** (117 bytes, permanent):

| Field | Size | |
|---|---|---|
| taps | 4 | taps in which anything was signed for or refused, ever |
| sats | 4 | sats signed for, ever |
| refused | 4 | spends refused for being over a limit, ever |
| runs | 4 | times a third spend was refused inside ten seconds of the clock (below) |
| run start, run length | 4, 1 | the run of refusals in hand: the clock at its first, and how many |
| the last eight taps | 8 × 12 | a ring; the tap numbered n is at (n − 1) mod 8. Each: the clock when it began (4), sats signed for in it (4), pieces signed (1), spends refused in it (1), flags (1; bit 0 is the mark), and a byte of nothing |

**A tap, here, is one time in a reader's field**: from the card being powered
to its being taken away (a byte of RAM that is gone with the power, and not
with a SELECT, says whether this one has an entry yet). The limit on one tap
counts by the clock, because that is what bounds a terminal; the log counts by
the field, because that is what a person did. A terminal that cuts the field to
begin again shows as more taps, and the counts count every one, so the ring
being pushed round hides nothing from a phone that remembers the counts it saw.

**It records what was signed for, not the price.** The card does not know the
price. It cannot say that a payment was too much; it says what left, and when.

**The mark.** A terminal that keeps to the limits is never refused: `GET_INFO`
says what the day and the tap have left, before anything is asked for. A
refusal is therefore a terminal that asked for more than it was allowed. A run
of **three inside ten seconds of the card's clock** (from the run's first
refusal) is one trying the limit again and again: the count of runs goes up
once, at the third, and the tap in which the third or any later refusal of the
run falls is marked. Ten seconds of the clock, and not three in a row at any
distance: a terminal that does not know of a limit and is refused once at each
of three visits is not that. A clock that is behind the run's start (rule 4
sets it back to nothing) begins a new run. A card with no limit refuses
nothing, and so marks nothing: the mark needs a limit to be set.

**What a mark is not.** It is not proof of theft, and its absence is not proof
of honesty. A terminal that takes what the limits allow, or that walks round
them by signing itself a later time (5.2, with the interim signer), is never
refused and leaves no mark; what it took is in the taps and the counts all the
same.

**Reading it: `GET_LOG` (`18`).** Sixteen bytes of counts (taps, sats, refused,
runs), then the taps the ring holds, newest first, twelve bytes each: 112 bytes
at the most. For whoever the card is open to: the PIN verified in this tap, or
the owner's grant (`ALLOW_LOAD`), so the owner's phone reads it with no PIN. It
says when a card was used and for how much, which a stranger's reader is not
told (`6982`). A card with no PIN yet has an empty log, and answers anyone.

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
   try, right or wrong: a wrong proof uses it up. Asking again replaces it.
2. The proof is an ECDSA (P-256, SHA-256, DER) signature by the owner key over

       label ‖ nonce (16) ‖ value

   verified on the card with `ALG_ECDSA_SHA_256` on the owner key, with the
   label and value of the command:

| Command | Label (ASCII) | Value |
|---|---|---|
| `CHANGE_PIN` | `FoxyCard/change-pin` | the new PIN's bytes |
| `SET_LIMIT` (owner form, `34`) | `FoxyCard/set-limit` | the new limit, 4 bytes, big-endian; or 8, the day's and then the tap's (6a) |
| `SET_OWNER` (on a card that has an owner) | `FoxyCard/set-owner` | the new owner key, 65 bytes |
| `SET_CARD` (on a card that has an owner) | `FoxyCard/set-card` | the data sent: unit, refund key, time key, mint length, mint |
| `ALLOW_LOAD` | `FoxyCard/load` | nothing |
| `LOCK_CARD` | `FoxyCard/lock` | nothing |

So a proof for one command, or one value, is no proof for another, and an old
or used proof fails.

The data of an owner's command is `proof length (1) ‖ proof (DER) ‖ the value`
(for `ALLOW_LOAD` and `LOCK_CARD`, the proof alone), so the proof sits first and
the value after it is exactly what the proof was made over.

**Refusal.** `6A91` when no nonce was asked for in this tap, or the signature is
not the owner's. A wrong proof costs no PIN tries, changes nothing and does not
end the PIN session. `6A90` when the card has no owner.

**No PIN.** `CHANGE_PIN`, the owner's `SET_LIMIT`, `SET_OWNER`, `SET_CARD` and
`ALLOW_LOAD` need no verified session and no old PIN: the owner's phone does not
know the PIN, and the cards that most need an owner's `CHANGE_PIN` are the
blocked and the forgotten, where there is no old PIN to give. `LOCK_CARD` keeps
the draft's rule (a PIN set and verified, and the proof), and the phone still
never carries instruction `50`.

**Fallback, documented and not built.** If the card in hand turns out to have no
usable ECDSA verifier (the J3R180 is expected to; it is not yet measured), the
draft's proof (a 32-byte owner secret given once at set-up, and
`SHA-256(label ‖ secret ‖ nonce ‖ value)` compared in constant time) stands in,
labels, nonce and all. The time signature has no such fallback and must verify;
so the question is settled by the first card either way.

### 7.3 Who is the owner

Whoever's words derived the key on the card. For a holder with a phone, their
own. For a holder without one, the friend whose phone set the card up: a trustee
who, with the card in hand, can do anything, and without it, nothing. Set-up says
so in as many words (section 10).

An owner can be replaced only on a card with nothing unspent, and only with the
old owner's proof (`SET_OWNER` refuses a funded one with `6A8D`, and refuses with
no proof `6A91`), because the only thing that could authorise a new owner is the
old one. A card whose owner has lost their words **cannot be taken over**, empty
or not: that is what the owner's words are for, and what the screen at set-up
says.

There is no card with a PIN and no owner that holds money. A card with no owner
cannot be loaded (rule 7), so such a card is only an unfinished set-up, and the
phone finishes it.

## 8. Commands

Class `B0`. The instructions of this design, by byte; a row in **bold** is new
or changed from `FOXY-CARD-SPEC.md` 5.2 and the allowance draft.

| INS | Command | Needs | Data sent | Answers |
|---|---|---|---|---|
| `01` | **GET_INFO** | | | 30 bytes: the draft's 17 (version 2, slots, unspent, spent, empty, capabilities, PIN state, format, tries left, locked, record set, limit 4, has owner 1), then `now` (4), window start (4), spent today (4), then whether this tap may load with no PIN (1; 8.2) With **P1 = 1**, 42 bytes: those thirty, then the tap limit, the tap start and spent this tap, 4 each (6a). |
| `10` | GET_PUBKEY | | | the card's 33-byte compressed key |
| `11` `12` `13` `14` | GET_BALANCE, GET_PROOF_COUNT, GET_PROOF, GET_SLOT_STATUS | | | as before |
| `15` | AUTH | | 16 random bytes | 16 of the card's own and a signature |
| `16` | **GET_CARD** | | | format, record set, unit, limit (4), refund key (33), time key (65), mint length, mint |
| `17` | **GET_PIECES** | | P1 = the first slot to report | one page: the first slot the page does not cover (1), then for each slot in the range that is not empty a tag (1) and, for an unspent slot, its 81 bytes (8.1). `6A83` for a P1 of 64 or more |
| `18` | **GET_LOG** | the PIN verified in this tap, or the owner's grant; nothing on a card with no PIN yet | | the card's own log (6b): 16 bytes of counts, then up to eight taps of 12 bytes, newest first. `6982` to anyone else |
| `20` | **SPEND_PROOF** | PIN, if one is set; a time, if a limit is set | P1 = the slot | the 64-byte signature; `6A8F` over the day; `6A92` with no time. **Not** opened by `ALLOW_LOAD`, nor by the tap after a payment. Notes, in permanent memory, that the card has paid: the next tap may load with no PIN (8.2) `6A95` over the limit on one tap (6a). |
| `30` | **LOAD_PROOF** | an owner; a PIN set, and verified or `ALLOW_LOAD` given in this tap or this tap being the one after a payment (8.2); a card record; a time | 81 bytes | `6982` with no verified PIN and no grant (the gate comes first); then `6A90` with no owner; `6A92` with no time; `6A94` for a piece whose nonce is already in a slot, spent or not (checked last, after the length and the piece itself, and before anything is written) |
| `31` | **CLEAR_SPENT** | the PIN, if one is set, or `ALLOW_LOAD` given in this tap, or the tap after a payment | | |
| `32` | **SET_CARD** | no owner: PIN set and verified, nothing unspent. Owner: the owner's proof, nothing unspent | unit (1), refund key (33), time key (65), mint length (1), mint; with an owner, proof length (1) and the proof first | `6A8D` if anything is unspent; `6A80` for a time key not beginning `04`, or a bad refund key; `6700` for a bad length |
| `33` | **SET_LIMIT, PIN form** | **a card with no owner**: PIN set and verified; nothing unspent; a time, unless the limit is 0 | limit (4) | `6A91` on a card with an owner (use `34`); `6A8D` if anything is unspent; `6A92` with no time Eight bytes set the limit on one tap as well (6a). |
| `34` | **SET_LIMIT, owner's form** | the owner's proof; a time, unless the limit is 0. **No PIN**, any funds | proof length (1), proof (DER), limit (4) | `6A90`, `6A91`, `6A92`; `6986` on a locked card Eight bytes of limit (the day's, then the tap's) set both (6a). |
| `35` | **SET_TIME** | nothing | time (4), signature length (1), signature (DER) | the card's `now` (4); `6A93` for a signature not the time key's; `6A8C` with no record |
| `40` | VERIFY_PIN | | the PIN, 4 to 8 bytes | changes nothing about spending |
| `41` | **SET_PIN** | **a card with no owner**, nothing unspent. No PIN, no proof | the PIN | sets or replaces the PIN and unblocks; `6A91` on a card with an owner (use `42`); `6A8D` if anything is unspent; `6986` locked |
| `42` | **CHANGE_PIN** | the owner's proof. **No PIN, no session, any PIN state, any funds** | proof length (1), proof (DER), the new PIN | sets the PIN and resets the tries; ends any verified session; `6A90`, `6A91`; `6986` locked |
| `43` | **SET_OWNER** | no owner: nothing unspent. Owner: the owner's proof, nothing unspent | 65 bytes, the owner's public key, uncompressed; with an owner, proof length (1) and the proof first | sets or replaces the owner; `6A8D` if anything is unspent; `6A91` with an owner and no good proof; `6A80` for a key not beginning `04`; `6700` other length; `6986` locked |
| `44` | GET_NONCE | a card with an owner. No PIN | | 16 bytes, or `6A90` |
| `45` | **ALLOW_LOAD** | the owner's proof, over `FoxyCard/load` and no value | proof length (1), proof | for the rest of this tap: `LOAD_PROOF` and `CLEAR_SPENT` need no verified PIN. `6A90`, `6A91`; `6986` locked |
| `50` | LOCK_CARD | PIN set and verified; the owner's proof | P2 = `DE`; proof length (1), proof | as the draft |

`21` (`SIGN_ARBITRARY`) stays unassigned. The applet's version becomes 1.1 and
`FORMAT` 3; a phone that knows only format 2 refuses the card, as it does now
for anything but its own.

**Every command in every state: the rule of section 3 worked through.** "Open"
means nothing is needed beyond what the command's own row says; "refused" means
`6A91` unless another word is given.

| Command | Card with **no owner** | Card **with an owner** |
|---|---|---|
| `GET_INFO`, `GET_PUBKEY`, `GET_BALANCE`, `GET_PROOF_COUNT`, `GET_PROOF`, `GET_SLOT_STATUS`, `GET_PIECES`, `AUTH`, `GET_CARD` | open | open |
| `SET_TIME` | open: the time key's signature (any state, locked or blocked) | same |
| `GET_NONCE` | `6A90` | open (it only gives a number) |
| `VERIFY_PIN` | as ever | as ever |
| `SPEND_PROOF` | the PIN, if one is set (such a card holds nothing) | the PIN, up to the day's limit |
| `LOAD_PROOF` | **refused `6A90`** | PIN verified, or the `ALLOW_LOAD` grant in this tap, or this tap being the one after a payment (8.2); needs a time |
| `CLEAR_SPENT` | the PIN, if one is set | the PIN, or either grant |
| `SET_PIN` (`41`) | **open while nothing is unspent** (sets or replaces, unblocks) | **refused**, empty or not: the owner uses `CHANGE_PIN` |
| `CHANGE_PIN` (`42`) | `6A90` | the owner's proof, empty or not, any PIN state |
| `SET_CARD` (`32`) | PIN set and verified, **while nothing is unspent** | **the owner's proof, and nothing unspent** (`6A8D`) |
| `SET_LIMIT` (`33`, PIN form) | PIN set and verified, **while nothing is unspent** | **refused** |
| `SET_LIMIT` (`34`) | `6A90` | the owner's proof, empty or not |
| `SET_OWNER` (`43`) | **open while nothing is unspent** | **the owner's proof, and nothing unspent** |
| `ALLOW_LOAD` (`45`) | `6A90` | the owner's proof |
| `LOCK_CARD` (`50`) | `6A90` (after the PIN gate) | PIN set and verified, and the owner's proof |

So the one thing an attacker with the PIN and an emptied card can still do is
load pieces onto it (which the mint will refuse) and spend them. It cannot set
its own PIN, owner, record, time key or limit.

Status words, in the part of `6Axx` ISO 7816-4 leaves unassigned:

| SW | Means |
|---|---|
| `6A8D` | card in use: a thing that is set only on an empty card, on a card with an unspent piece |
| `6A8F` | over the day: this piece would take today past its limit. Nothing signed, nothing burned |
| `6A95` | over the tap: this piece would take this tap past the limit on one tap (6a). Nothing signed, nothing burned |
| `6A90` | no owner: a command that needs the owner's proof, or `GET_NONCE`, or a load, on a card that has none |
| `6A91` | the owner's proof is missing or is not the owner's, or this command is not open to a card with an owner. Costs no PIN tries, changes nothing, does not end the PIN session |
| `6A92` | no time: the card has never been told the time, and this needs one |
| `6A93` | not the time: a `SET_TIME` whose signature is not the time key's. Nothing changes |
| `6A94` | on the card already: a `LOAD_PROOF` of a piece whose 32-byte nonce is the nonce of any non-empty slot, spent or unspent. Nothing is written, and the amount stated is not looked at. A slot that `CLEAR_SPENT` has freed is empty, so that piece may be loaded again then |

What `CardGate` carries: the instructions the page uses, `01 10 11 13 14 15 16
17 20 30 31 32 34 35 40 41 42 43 44 45`. `33` (the PIN form of the limit, for an
open card) is not used by the phone and is not carried; `50` stays out.

### 8.1 GET_PIECES: the whole card in a few answers

A tap used to read a card with one command for each of its states and one
for each piece on it, at a tenth of a second or so a command. `GET_PIECES`
(`17`) says what both of those say, in pages.

**The answer.** P1 is the first slot to report (0 to 63; `6A83` above that);
P2 and the data are not used. No PIN: it says what `GET_SLOT_STATUS` and
`GET_PROOF` say, which need none. The answer is

    next (1)     the first slot this page does not cover; 64 means the card has
                 no more to say, and anything else is the P1 to ask for next
    then, for each slot from P1 up to next that is not empty, in order:
      tag (1)    (state << 6) | slot, state 1 for unspent and 2 for spent
      piece (81) only for an unspent slot: keyset (8), amount (4), nonce (32),
                 C (33), date (4), which is what `GET_PROOF` gives after its
                 status byte

A slot in the range with no entry is empty. A spent slot is listed by its tag
alone: its bytes stay on the card until `CLEAR_SPENT` frees the place, and
nothing that reads a card wants them. A page is at most 255 bytes, so three
unspent pieces (1 + 3 x 82 = 247) make a page, and spent slots, which cost a
byte, fill what is left of it. At least one entry always fits, so every answer
moves on. A card of sixty-four unspent pieces is twenty-two pages; a card with
six is two.

**Why pages, and not extended length.** 64 slots of 82 bytes are 5,248 bytes,
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

**On the phone.** `cardLook` asks for pages from slot 0 until `next` is 64, and
not at all when `GET_INFO` counts no unspent and no spent slot. A card that
answers the first page with anything but a page (an older applet answers
`6D00`) is read the old way: `GET_SLOT_STATUS`, then `GET_PROOF` for each slot
that is not empty. The old commands stay on the card and in the gate for that.
A read of a card holding six pieces is eight commands (select, time, info,
key, proof of the key, record, two pages) where it was twelve.

### 8.2 The tap after a payment: change with no PIN

A till lets the card go as soon as it has signed, and asks the mint with the
card gone (9). Change, when there is any, is made after that and written at a
second tap. That tap used to need the PIN again, for one payment, which is
cumbersome at a counter.

**The rule.** The card keeps one bit in permanent memory, *change due*, set
by every `SPEND_PROOF` in the same transaction that burns the slot. At the
next `SELECT`, whichever reader's it is, the bit is taken into the tap (a
transient *change grant*) and cleared; a card pulled between the two still has
the bit, and the tap after gets the grant instead. For that one tap,
`LOAD_PROOF` and `CLEAR_SPENT` need no verified PIN, exactly as under
`ALLOW_LOAD`. Nothing else opens: not a spend, not the PIN, not the record,
not the limit, not the lock. A locked card still takes no load; a blocked PIN
still waives nothing (`requireLoadAuthority` asks for a PIN that is set, and
a blocked one is not). A payment in the change tap, which needs the PIN as any
payment does, sets the bit again for the tap after it.

`GET_INFO`'s last byte says whether this tap has the grant, so the phone can
tell before it writes.

**What it opens, and to whom.** One load, by the next reader to tap the card.
The reader that is meant is the till that took the payment a moment before, and
had the PIN typed on it for that. A different reader that taps first (the
holder's own phone, say, looking at the balance) uses the grant up by
selecting the applet, and the till asks for the PIN at its change tap after
all: nothing is lost, the change stays owed to the card on the till. A reader
that is a stranger's gets, at most, to put pieces on the card without the PIN,
once: the junk-piece nuisance of 13, which the PIN-holding terminal already
has. It takes nothing, and the day's limit is not touched by any load.

**On the phone.** A till that owes a card change, a refund of what it signed
and left with, or a payment put back after the mint refused it, taps first
with no PIN (`cardWrite` with `change`) and reads `GET_INFO`; a card whose
last byte is 0 is refused with 'pin-needed' before anything is sent, and the
PIN pad comes up for the next tap. The screen says `No PIN is needed.` on the
TAP THE CARD AGAIN card. Pieces owed for a top-up cut short are written as
before, with the PIN or the owner's proof.

## 9. What the phone does

**Time.** Every tap tells the card the time, once, before anything else is read
from it that depends on the day. The page asks its native side for
`{time, signature}` (`cardTime`): the phone's clock, signed with the interim key
of 5.2. The page sends `SET_TIME` with them, and reads the card's `now` back. A
card that answers `6A93` is at another signer: NOT A FOXY CARD, nothing taken. A
card that answers `6A8C` is not set up yet, and is read as it was. This is the
only place the interim signer is used: replacing it is a native change.

**A receiver (`cardTake`).** In the tap, after `SET_TIME` and the reads, work out
what is left of today from `GET_INFO` (limit, `now`, window start, spent today;
a window whose day is over has the whole limit left) and refuse **before the PIN
is sent** a payment whose pieces would pass it (`worth > left today`).
OVER THE CARD'S DAILY LIMIT says what is left today and when the day turns.

**A loader.** `SET_TIME` before `LOAD_PROOF`, always: a card is told the time
before it is given money (rule 7). A till writing change under the PIN does the
same.

**The owner's phone.** The page never holds the owner key. Native offers the page:

- `cardOwnerKey {key}`: the owner **public** key for the card with that
  compressed public key: 130 hex characters, uncompressed. Used for `SET_OWNER`.
- `cardOwnerSign {key, label, nonce, value}`: an ECDSA signature (DER, hex) by the
  owner key for that card, over `"FoxyCard/" + label ‖ nonce ‖ value`, where `label`
  is one of `change-pin`, `set-limit`, `set-owner`, `set-card`, `load`, the nonce is
  the 16 bytes the card gave and the value has the shape that label takes. Native
  refuses every other label (in particular `lock` and `time`) and every other
  shape, before it reads the seed. It answers the signature and nothing else.
- `cardTime {}`: `{time, signature}`, the phone's clock and the interim signature
  of 5.2.

The owner's private key and the seed never leave native. The draft's action that
handed the page a secret is gone.

Set-up on a card with no owner, one tap, in this order: `SET_PIN`, `VERIFY_PIN`,
`SET_CARD` (the record, with the time key), `SET_OWNER` last. The owner goes in
last so that no step needs a proof, and the card is open, and can be done again,
at every point before it: a set-up cut off anywhere is finished by the next. It
asks for no limit: the card starts with none. The PIN is typed on the owner's
phone once, here, and never again for adding funds.

After set-up, on the owner's phone:

- **Add funds:** `SET_TIME`, `GET_NONCE`, sign `load`, `ALLOW_LOAD`, then
  `CLEAR_SPENT` and `LOAD_PROOF`. No PIN is asked.
- **CHANGE PIN:** `GET_NONCE`, sign `change-pin` over the new PIN, `42`. No old
  PIN is asked: the owner's phone does not know it. A blocked card on its
  owner's phone offers UNBLOCK, which is CHANGE PIN.
- **CHANGE LIMIT** (any amount, or none): `SET_TIME`, `GET_NONCE`, sign
  `set-limit`, `34`. No PIN is asked.
- **Taking money off** (withdraw, take back, renew, switching mint), in one tap:
  the limit is **lifted** with the owner's proof (`34`, limit 0), the PIN is
  typed and `SPEND` runs, and the old limit is **put back** with the owner's
  proof, **restored even when the spending fails part way**. Where the card
  leaves before it can be, the phone has written the old limit down first, and
  puts it back at the next tap. `SPEND` itself still needs the PIN.
- **Moving the card to another mint** (empty card): `SET_CARD` with the owner's
  proof, writing the card's current time key back (so its clock stays).
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
  change, made after the swap and written back at a second tap, with no PIN
  (`changeDue`). Every card payment is said as two taps, SEND and RECEIVE; one
  paid exactly is complete at the first. A till with no route takes only an
  exact set (below), found by search, fewest pieces first.
- What goes onto a card is cut like the float in a cash drawer: every power of
  two from 1 up to the largest that fits, once, then the rest of the amount in
  powers of two (every price up to the whole is exact then), and then, with the
  pieces left under the cap, the smallest rungs two or three deep, so that a
  run of offline payments finds the rungs the first took still there; never the
  small change the rest of the wallet keeps, no more than thirty-two pieces (half
  the card), and no more than the card has places for less a dozen kept for an
  online payment's change (an amount that needs
  more is rounded up, smallest pieces first, and the screen says by how much; a
  mint whose largest piece is small makes more of that piece). Change written
  back is cut the same way, filling the card's gaps. The card signs a piece in
  most of a second, so the pieces are what a withdrawal is made of: one cut
  short keeps what the card signed, in the phone, and the next tap takes the
  rest.
- A till with no route may take a card on trust, as plain ecash is taken: the
  HIGH RISK question is put to the person first, in front of the amount, and the
  owner's answer decides. Only an exact set of pieces is taken (a till with no route
  cannot give change), refused before the PIN is sent when the card has none; the
  day's limit is looked at as ever, and the time is signed on the phone, which
  works with no route. Every signature the card gives is checked on the phone
  before anything is kept. The pieces are kept as a trusted row, with a pending
  entry, and swapped in when there is a route; if the mint says they were spent
  meanwhile, the entry is marked taken back and the person is told. "Paid" is
  never said. A piece on a card carries no DLEQ proof (that is phase 3, section
  13), so offline the phone cannot check the mint's signature on it: that is the
  risk the question names. A switch of its own turns this off; plain ecash between
  two offline phones has its own, as before.

**Tests on the phone.** The model in `tests/flashcard-card.js` grows the time,
the window, the owner key and the proofs, with Node's own P-256; the page's tests
run against a fake native side that holds the same derivation;
`tests/flashcard-model.js` holds the model to the applet's transcript as before;
the money suites gain a day that turns and a terminal that re-sends the PIN and
gets one day; a test checks that the page never receives an owner secret.

## 10. Set-up, and what is said

- A card is set up on a card with no owner, by the phone that will be its owner.
  The set-up asks for a PIN twice. **It does not ask for a limit and does not
  suggest one.** A new card has no limit.
- For a holder with no phone, the owner is the friend's phone. Set-up says, once,
  on the screen that makes this phone the owner: *This phone can reset this
  card's PIN and limit. Whoever holds the card and this phone's seed phrase holds
  its money.*
- `SET_PIN` once; the holder never types the PIN anywhere but a till and, once, at
  set-up. A changed PIN comes from the owner's phone.
- No refund key for now: a lost card is cash gone. A blocked card is not, because
  the owner unblocks it.
- Any card with no owner can be claimed by any reader in range, and that is fine:
  it holds nothing and cannot be loaded. A card leaves the issuer's hands set up,
  as the draft's 5.4 says, so that nobody has to think about it.

**The words on the screens.** The screens do **not** say the limit stops an
attacker: with the interim signer it does not (5.2).

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
while it is empty**; `SET_CARD` carries the time key; the mint is at most 80
characters; `GET_INFO` grows by twelve bytes and `GET_CARD` by sixty-five;
version 1.1, format 3.

**Dropped:** the allowance that only goes down and only the owner raises; the
rule that a card with no owner can never be given an allowance, and the card
with a PIN and no owner that holds money (it cannot be loaded); `6985` on
`SET_PIN` when a PIN exists; the owner secret's 32 bytes on the card and in the
air; any page-side secret.

**New:** `SET_TIME`, the time key, `now`, the window, `6A92`, `6A93`, rule 7 (no
money before a time and none without an owner), `ALLOW_LOAD` (`45`), the owner's
labels for `SET_OWNER`, `SET_CARD` and the load grant, the clock's one way back
(rule 4), and the interim signer (5.2).

## 12. How it is tested

- jCardSim, the day: a limit of one piece and four pieces on the card, and a
  terminal that sends the PIN between every spend, takes one, however it is
  repeated, and takes a second only after `SET_TIME` with a time a day later; a
  new SELECT, a reset, `CLEAR_SPENT` and a load leave today as it is; spent today
  carries across taps; exactly the limit goes and one sat more does not, with
  nothing burned; a sum that wraps is over any limit; limit 0 spends with no time;
  a limit with no time refuses `6A92` and burns nothing; the window turns at
  86 400 seconds and not at 86 399; no load without a time. A funded card always
  holds a time (rule 7, and rule 4 lets only an emptied card lose it), so the two
  guards in `SPEND_PROOF` for a card with no time are not reachable by any command;
  they are tested by setting the card's record directly.
- jCardSim, the time: a signature by another key is `6A93` and changes nothing;
  an older time is `9000` and changes nothing; a newer one is taken; `now`
  survives `SET_CARD` with the same key, `SET_OWNER`, `CHANGE_PIN`, a reset, and an
  emptying and re-set-up of the card, so a last-week's time offered to a card that
  was just set up again is ignored; `SET_CARD` with a **different** time key on an
  empty card clears it, and the window with it, and a far-future time is then
  recoverable; `LOAD_PROOF` and `SET_LIMIT` (with a limit) refuse `6A92` on a card
  never told the time; the two-days-at-a-boundary bound is pinned as what it is.
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
- jCardSim, A2 and A3: a card with no owner refuses `LOAD_PROOF` `6A90`; after
  `ALLOW_LOAD`, `LOAD_PROOF` and `CLEAR_SPENT` go with no PIN verified, `SPEND` does
  not, and a new SELECT ends the grant.
- jCardSim, the empty card: after `CLEAR_SPENT` of a fully spent card the open
  commands are allowed on a card with no owner.
- jCardSim, a piece is on the card once: a copy of an unspent piece that states
  an amount of 1 (and the piece itself, and the same nonce with a date or another
  keyset) is refused `6A94` with nothing written, nothing counted against the day
  and the real piece as it was; the same for a piece on a spent slot, which loads
  again after `CLEAR_SPENT` and is signed for and charged at its real worth; a nonce
  that differs in its first byte or its last is another piece; the owner's
  `ALLOW_LOAD` gets a copy no further than the PIN does.
- jCardSim, GET_PIECES: an empty card is one byte; up to three unspent pieces are one
  page, each the bytes `GET_PROOF` gives after its status byte; a spent slot is a tag
  alone and an empty one is left out; a page from the middle, and from an empty slot; a
  full card is twenty-two pages, each within 255 bytes and each moving on; a page fills to
  255 bytes and not a byte over (three pieces and eight spent tags, then the next piece
  starts a page); it asks for no PIN and changes nothing; a slot there is not is `6A83`.
  The transcript has it, and the phone's model of the card is held to it.
- The source-reading test: spent today and the slot's status change between one
  `beginTransaction` and its commit, with the signing after.
- Vectors in `spec/vectors/`: a signed time and its key; an owner key and a proof
  for each label (`owner.json`; ECDSA signatures are randomised, so each is one the
  card verified, and another implementation verifies it again); the transcript,
  extended (`transcript.json`), which the phone's JavaScript model of the card is
  held to command for command.
- On a card, by hand: the ECDSA verify on the J3R180 (that it takes the DER form and
  the uncompressed key, and how long it takes, once for the time in every tap and
  once for each owner command); set-up, a day's spending, a day turned, a wrong PIN
  three times and an owner's unblock, a tap pulled away at each step; and the third
  permanent write per spend (spent today, with the status byte and, on a new day,
  the window start) against the chip's write endurance.

## 13. What it does not protect against

- **A terminal built to cheat, today.** With the interim signer (5.2) it can sign
  its own time, day after day in one tap, and take everything the PIN reaches. The
  limit bounds honest terminals and the holder's own overspending, and that is all
  it is said to do.
- A terminal that has been handed the PIN takes one day's limit per visit, and up to
  two across a window's boundary, **once the time is real**. It does not check a
  payment, and nothing on the card can.
- The same terminal can write onto the card pieces the mint will refuse, made up
  with a nonce no mint ever signed. They take nothing from anyone, since the
  receiver always asks the mint, but a later payment that picks one burns slots and
  spends a day's limit on nothing, and while such a piece is on the card it is not
  empty, so the owner cannot replace its owner or record until it is spent. A fake
  piece looks unspent to a state check, so this waits on DLEQ on the card (phase 3).
  Since 8.2, the reader that taps the card next after any payment can do the same
  once without the PIN: the nuisance is the same, and the reader is almost always
  the till that just had the PIN.
- It cannot write a **copy** of a real piece. The card signs a piece's secret, which
  its nonce makes, and takes the amount on the terminal's word, so a copy stating an
  amount of 1 would have been spent for 1 and signed for the real piece. `LOAD_PROOF`
  refuses a nonce that is in any slot, spent or not (`6A94`). What the card does not
  do is remember a nonce once `CLEAR_SPENT` has freed its slot: a piece that was
  burned and whose signature never reached the mint (a tap cut off at the wrong
  moment) can be read from its spent slot, cleared, loaded again with a small amount
  and signed for again, by a terminal that has the PIN. A signature that was handed
  over has already been paid for in full.
- A holder with no phone has no balance they can read that a terminal did not draw.
  That is a screen, not a clock.
- Whoever has the owner's words and the card in hand has everything on it.
- A holder whose owner has lost their words can never change the PIN or the limit.
- Whoever holds the time key's private half (today, anybody) can move clocks forward.
- A wrongly signed far-future time freezes a card's day until its owner writes it a
  new time key while it is empty (rule 4).
- The PIN is in the clear over a few centimetres of air until phase 2.
- A lost card is cash gone; there is no refund key for now.

## 14. Decided, and left

Decided: no limit by default and none asked at set-up (A8); no loading of a card
with no owner (A2); the owner's phone adds funds without the PIN (A3); the clock
may be cleared in one case (A4); the keys are P-256 and ECDSA (A6), and the owner
key stays in native code (A7); taking money off lifts and restores the limit in one
tap (A9); the owner's proof is ECDSA, with the hash as a documented fallback only
(A11); the time is the receiving phone's own clock, signed with a key that is in
the app, until a real signer exists (A10).

Left: where the real time signer runs and who holds its key; whether the ECDSA
verifier on the first card behaves (7.2); and the time at which the interim key is
retired.
