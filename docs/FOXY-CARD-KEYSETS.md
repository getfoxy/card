# Foxy card: dollars and sats on one card (the keyset table, software 1.23)

A card that holds dollars and sats at its one mint, held to one ceiling in
cents whichever it pays with, and the change to the card's software that this
needs: a table, kept on the card, of which unit each keyset it holds is, and a
rate, written by the owner's phone, that turns sats into cents at a payment.

This is the design of software 1.23 (`SELECT` answers `01 17`). Nothing in it is
built. It is written against software 1.22 (`01 16`) as it is in
`CashuApplet.java` and as `FOXY-CARD-DAILY-LIMIT.md` (LIMIT below) describes it,
against `FOXY-CARD-SPEC.md` (SPEC), `spec/APDU.md` and `FOXY-CARD-HARDWARE.md`,
and against the phone's reading of a card in `build/wallet/08a-flashcard.js`,
`08c-units.js`, `21a-flashcard.js` and `build/app/26f-flashcard.js`. Where this
document and LIMIT differ, LIMIT says what 1.22 does and this says what 1.23
changes. When 1.23 is built, LIMIT section 8, SPEC 5.2 and `spec/APDU.md` take
the rows of section 8 below.

Every choice here follows the owner's answers to the ten questions of an earlier
draft (section 13). The draft held money at up to three mints and gave each unit
limits of its own; the owner chose one mint and one ceiling, and this document is
that draft cut down to them.

## 0. Why

The owner asked for two balances on a card: dollars and bitcoin, at a mint that
issues both. Today a card knows neither unit apart from the other.

What a card of 1.22 knows:

- **One unit.** The record has a unit byte (offset 1, `SET_CARD`'s first data
  byte), `00` sats or `01` dollars. The card stores it, answers it in
  `GET_CARD`, and uses it for nothing. The phone reads `00` as sats and `01` as
  dollars (`cardRecordOf`), and a dollars card has shipped on the cards there are:
  a card set up in dollars, holding only dollar pieces, its limits in cents.
- **One mint.** The record holds one address of up to 77 characters (offset 39,
  length; 40, the text).
- **A keyset for each piece, which it never reads.** Every slot carries the
  piece's keyset id, eight raw bytes (slot offset 1). The card compares it with
  nothing and takes any keyset at load. Only the change the card makes for itself
  copies it, from the payment's first piece into the opening (`OPEN_KEYSET`).
- **One sum.** `GET_BALANCE` adds every unspent piece.
- **Limits with no unit.** The daily limit, the limit on one payment and the
  no-PIN allowance are each one number, in whatever unit the phone wrote them,
  and every payment is charged against them as it is. On a sats card they are
  sats and the phone re-pegs them to the price; on a dollars card they are cents.

And the phone maps a keyset it reads off a card to a full id among every keyset
of its mint (`cardFullId`, by `keysetIdsFor`, which does not look at the unit): a
phone shown a card that held both units would count cents as sats.

So a card can hold money of any keyset already. What it cannot do is say which
piece is which unit, keep the two apart in a payment, or hold a payment of sats
and a payment of dollars to one ceiling. The first two need one fact, **for each
keyset, which unit**: that is the table (3). The third needs a price on the card:
that is the rate (6.2).

A card of one unit (format 4, 4.3) and no ceiling answers as a 1.22 card does,
to the byte, but for the version.

## 1. Words

| Word | Means |
|---|---|
| **unit code** | `00` sats, `01` dollars (cents). The card takes no other. A third unit is a design of its own |
| **the record's unit** | what the unit byte at record offset 1 says. Called **unit A** where a byte has to say it in one bit |
| **the other unit** | `01` on a card whose record says `00`, and `00` on one whose record says `01`. Called **unit B** in the same places |
| **the ceiling** | the card's limits, every one of them in cents on a card of 1.23 whatever the record's unit: the daily limit (kept, and not set by Foxy's app: LIMIT 6), the limit on one payment (LIMIT 6a) and the no-PIN allowance (LIMIT 6f). A payment of either unit is held to them |
| **the rate** | sats per dollar, four bytes, written by the owner's phone with the ceiling. The card turns a payment's sats into cents with it (6.2) |
| **the book** | the store this design adds: the table and three small things beside it (3.2) |
| **an entry** | one row of the table: a keyset and its unit |
| **live** | an entry is live while an unspent slot, or a change opening that is drafted or owed, names its keyset |
| **the holder's say** | on a card with an owner, the owner's grant (`ALLOW_LOAD`) given in this tap; on a card with no owner, the history held for in this tap (LIMIT 8.4). The same pair 1.22 asks for before `LOAD_CHANGE` takes the mint's answer under another key (LIMIT 8.2) |
| **the mark** | the bit in an entry that says it was written with the holder's say in the tap (3.1) |

## 2. Goals

1. A card holds dollars and sats at its one mint, and every piece on it is known
   to be of one unit.
2. No payment mixes two units.
3. Every payment, in either unit, is held to the same ceiling, in cents, which the
   owner sets once in dollars.
4. A card of 1.22 under a new phone, and a card of 1.23 under a phone of today,
   each work as far as they can and refuse plainly past that. No phone ever reads
   a dollar piece as sats.
5. A card that holds one unit and has no ceiling set answers as a 1.22 card, to
   the byte, but for the version. (With a ceiling set its figures are cents: 6.1.)

Not goals: a third unit; more than one mint (4.4); a swap on the card (the card
cannot reach a mint; a swap is the phone's, 9.6); a price on the card that
follows the market by itself (the card has no clock that runs; the rate is the
owner's phone's, 6.5).

## 3. The table

### 3.1 An entry

Nine bytes:

| Offset | Size | Field |
|---|---|---|
| 0 | 8 | the keyset id as the card holds it in a piece: the slot's bytes 1 to 8, raw (`spec/APDU.md`: a v1 id's eight bytes; a v2 id's first eight, as `cardShortId` cuts it) |
| 8 | 1 | the state: `00` a free entry. Otherwise bit 6 (`0x40`) set, the entry in use; bit 7 (`0x80`) the mark; bit 0 the unit code. Bits 1 to 5 are zero |

So `40` is sats, `41` dollars, `C0` sats entered with the holder's say and `C1`
dollars entered so. An entry is in use when bit 6 is set and is free otherwise;
the card writes `00` to free one. A new book is zeros, and zeros are free entries,
so nothing has to be written to make a table empty.

**Why nine bytes and not the ten of the earlier draft.** The draft's entry had a
mint byte (`0x40` with the mint index in bits 0 and 1, and the mark) and a unit
byte. With one mint the index is always 0, so the mint byte would carry only "in
use" and the mark, six bytes in seven of it dead, and the unit would sit in a byte
of its own. One state byte holds all three, and is still self-describing: the
format rule (4.3) reads each entry's unit from bit 0 of the same byte that says
it is in use, with no other byte to look at. One byte also makes the entry's
commit one write (3.6). Bits 4 and 5 are left zero where a mint index would go
(4.4).

Where a unit is said in one byte outside the table (the last payment's unit, the
receipts' units, `GET_INFO` byte 50), it is relative to the record's: `80` the
record's unit, `A0` the other, `00` none. Bit 5 is the same bit, in the same
meaning, as a log entry's (6.6), so one reading serves both, and bit 7 says a unit
is there, which the log never sets. Relative, as the log's one count has always
been: the phone resolves it to a code with the record's unit byte. The table alone
holds codes, because a keyset's unit is a fact about the mint and not about the
record.

### 3.2 How many, and where they live

**Six entries.** A unit needs one entry for the keyset its mint issues now, and
one more for a keyset the mint has retired while pieces of it are still on the
card: two. Two units are four. Two more are the room that entries made without
the holder's say may take (3.3), so that a reader that is not the holder's can
never fill what the holder needs: six. 54 bytes.

**The book.** One array of 75 bytes:

| Offset | Size | Field |
|---|---|---|
| 0 | 54 | the table, six entries of nine |
| 54 | 1 | the last payment's unit, relative (7.4) |
| 55 | 4 | the other unit signed for, ever (the log's count of the record's unit stays where it is: 6.6) |
| 59 | 16 | the receipts' units, relative, one beside each of the sixteen receipts (6.6) |

The rate is not in the book. It is a limit, written by `SET_LIMIT` with the
others, and it lives in the record beside them (6.2), where it exists before the
first `SET_CARD` and on a card refused its book.

**The arithmetic, against what the chip has.** `FOXY-CARD-HARDWARE.md` gives
three budgets, and the book is held to each:

| Budget | What was measured | The book |
|---|---|---|
| storage | 164,176 bytes free on a clean card; the applet about 45 KB; the first `SET_CARD` 12,288 more for the mint's proofs and 264 for the openings' keys | 75 bytes, and about 2 KB of code (12.1). Not what binds |
| a constructor's allocation | 300 bytes more than 1.19's 29,806 installed, 12,288 more did not; since 1.21 the constructors make 30,069, 37 under that line | 75 is past the 37, so the book is **not** made in the constructor but by the first `SET_CARD`, once, outside any transaction, guarded, as the mint's proofs' store and the openings' keys are (3.6). The constructors stay at 30,069 |
| memory cleared on deselect | 941 bytes installs, 1,030 does not, 942 untried | **nothing new**: the payment's unit is looked up again at `SPEND_ALL_SIGN` (7.1), a load's label read from the APDU buffer, the reclaim's marks six bits of a short local (3.4), a payment's cents worked out in `scratch` where `X_HEX` is, unused at `SPEND_ALL_SIGN` (6.4) |

The install-size window is in 12.2.

### 3.3 When an entry is written

**At `LOAD_PROOF`, and nowhere else**, for a piece whose keyset is in no entry.
A piece whose keyset is in an entry is checked against it and writes nothing to
the table. `LOAD_CHANGE` writes none: the card's own change is in the keyset of
its opening, which is the payment's first piece's, whose entry the opening keeps
live (3.4).

`LOAD_PROOF` gains a labelled form. Why a label at all: a phone of today loads
with no label and must go on being able to, and a label is the only way the card
can be told a unit it cannot work out. A keyset id says nothing about its unit
until a mint is asked, and a card cannot ask a mint.

| Form | P1 | P2 | Data | Lengths |
|---|---|---|---|---|
| plain, as 1.22 | `00` | `00` | one to three pieces of 81 | 81, 162, 243 |
| with the mint's proof, as 1.22 | `01` | `00` | one piece and its proof | 177 |
| **labelled** (1.23) | `00` | `01` | the unit code (1), then one to three pieces of 81 | 82, 163, 244 |
| **labelled, with the proof** (1.23) | `01` | `01` | the unit code (1), then one piece and its proof | 178 |

With P2 = 1 the label is the first data byte, and everything after it is read as
1.22 reads its data, one byte on: piece k at `OFFSET_CDATA + 1 + 81 × k`, the
proof at `OFFSET_CDATA + 1 + 81`, and the log's "put on" summed from each piece's
amount at the same shift (`logLoaded`). The answer is as 1.22's. Any P2 other
than 0 and 1 is `6B00`, before anything is written.

Every piece of a labelled command is of the label's unit. An unlabelled piece is
of the record's unit, which is what a 1.22 card assumes of every piece. A card of
1.22 does not look at P2, and answers every labelled length `6700`: a new phone
that sent a label to an old card by mistake is refused, and nothing is written.

**Who may enter a keyset, and as which unit.** Every load is the PIN's, given in
this tap, as 1.21 made it (`requireLoadAuthority`, LIMIT 3 rule 7): nothing in
1.23 opens a load to anyone else. Entering a new keyset is held tighter, because
a wrong entry puts money in the wrong unit for as long as a piece of that keyset
is on the card:

| In this tap | a new keyset as the record's unit | as the last payment's unit (7.4) | as the other unit |
|---|---|---|---|
| the PIN and the holder's say | yes, marked | yes, marked | yes, marked |
| the PIN alone | yes | yes | `6A9D` |

Why those two columns are open to a till that holds the PIN. The record's unit is
what any loader of a 1.22 card does today, so closing it would break every till
of today. The last payment's unit is where a till's refund to the payer goes: a
payment the mint refused is paid back in its own unit, and a mint that has moved
to a new keyset since the card's pieces were made gives the refund in the new
keyset. The other unit, on a card that has not just paid in it, is the holder's
to start.

**The mark, and the cap.** An entry written with the holder's say in the tap is
marked. At most **two** live entries may lack the mark; a third is refused as a
full table is (`6A9C`). A till with the PIN can load pieces of two keysets of its
own making and no more, and the four the holder needs stay free.

The owner's phone reads its card as owner, which gives the grant in the tap
(`cardGrant`, from `cardLook` with `mine`), before every ADD FUNDS of a 1.23 card,
so the owner's entries are marked. On a card with no
owner a load under the PIN alone enters its keyset unmarked, and the phone holds
the tap (LIMIT 8.4) only for a new keyset of the other unit, or after a `6A9C`.

**What is checked, in order.** The checks of a load are 1.22's in their order
(`6986` locked; `6A97` no PIN; `6982` the PIN not given; `6A8C` no record; `6A84`
a full card), then `6B00` for a P2 that is not 0 or 1, then `6700` the length,
then, for a labelled command, once before its first piece:

1. A unit code over `01`: `6A80`. Then, on a card with no book (3.6), a label
   of the other unit: `6A9C`.

then for each piece 1.22's `6A84` and `6A8E` and `6A80`, and then, new, before
its nonce is looked at:

2. The piece's keyset in an entry of the other unit than the label's (the label
   of a plain load being the record's unit): `6A9B`. Nothing is written.
3. The keyset in no entry, and the tap may not enter it as this unit (the table
   above): `6A9D`.
4. The keyset in no entry, and either no entry is free, or the new entry would
   lack the mark and two in use already lack it: the reclaim runs (3.4), and the
   counts are taken again. Still no free entry, or still two unmarked: `6A9C`.
   So the cap counts live entries, and the pass over the 128 places is paid only
   where the cheap count says the table may be full, never at a load of a keyset
   already entered.

and then `6A94` for a nonce already on the card. The reclaim in check 4 writes
only entries that nothing names. It is not a write of this piece, and `6A94` is
still the last check before this piece writes anything, as LIMIT 8 has it.
Several pieces in one command are taken in order, as now: a refusal of the first
is the command's answer, and a later one ends the command with the places of
those before it.

### 3.4 When an entry is let go

- **A reclaim** lets go every entry that is not live. One pass over the 128
  places (each unspent place marks its entry, its keyset compared with the one
  before it first, so that a drawer of one keyset costs one comparison a place)
  and one over the eight openings that are drafted or owed; then every entry left
  unmarked is set free by writing `00` to its state byte. The marks are six bits
  of a short local, bit i for entry i: no scratch and no RAM array, and nothing in
  `LOAD_PROOF`'s scratch (`X_HEX`, `X_NUM`, `X_SUM`) is touched between its
  pieces. A reclaim torn halfway has freed some dead entries and not others, and
  the next one finishes. A live entry is never freed, because the marks are made
  from what is on the card at that moment. It runs at `CLEAR_SPENT`, after the
  spent places are freed; at a load of a new keyset where the table may be full or
  the cap reached (3.3, check 4); and at `SET_CARD`.
- **`SET_CARD`** is let only on a card with nothing unspent and no change owed
  (1.21, `requireNothingOwed`). In 1.23 it then lets go any opening drafted for a
  payment never signed (as `SPEND_ALL_BEGIN` would), runs the reclaim, which with
  nothing unspent and nothing owed frees every entry, and writes `00` to the last
  payment's unit: all of it after every check has passed and before the record's
  transaction, outside it. The receipts' units and the other unit's count stay,
  with the log and the receipts they describe, which `SET_CARD` does not clear.
- **`RESET_CARD`** zeros the whole book with `Util.arrayFillNonAtomic`, beside the
  store of the mint's proofs, outside any transaction, as 1.22 does that store.

An entry is never changed in place. A keyset that is entered stays as that unit
until it is dead and reclaimed, so no command can move a piece from one unit to
the other.

### 3.5 A piece whose keyset is in no entry

There is none on a card with a book: every load enters its keyset or is refused
(3.3), the card's own change is in its opening's keyset, which the opening keeps
live, and nothing frees an entry a live piece names (U28 checks it after every step
of a long random history). Should a lookup find no entry all the same (a bug), the
place is of **no unit** in every command: `SPEND_ALL_BEGIN` refuses it `6A9A` and
never falls back to the record's unit; `GET_BALANCE` and the phone count it in no
sum; the reclaim marks nothing for it.

The one card with no table is one refused the room for its book (3.6): a card of
one unit in every way, every piece the record's unit, every load taken as plain, a
load labelled the other unit `6A9C`.

### 3.6 Order of writes, and the book's reservation

`loadOne` writes, for a new keyset, the entry's eight keyset bytes and then its
state byte, last, as one byte: the state byte is the entry's commit, as the
status byte is a slot's (D14). This is before the slot's hex text, its proof
record, its data and its status byte. A card taken away in between holds an entry
that nothing names, which the next reclaim frees.

The book is reserved by the first `SET_CARD` that goes through, after the store of
the mint's proofs and the openings' keys, the same way: once, outside any
transaction, in a `try` that leaves it null where the card refuses the room. Every
use of the book is safe while it is null. A later `SET_CARD` asks again. The tests
that pin the once-only allocations (`ONCE_ONLY_ALLOCATIONS` in
`SchnorrHWMathTest`: that each is in `processSetCard` and nowhere else, and the
null its catch leaves) gain the book as the third, and nothing beside it.

## 4. The mint

### 4.1 One mint

What stays, all of it 1.22's:

- The mint is the record's, at offsets 39 (length) and 40 (up to 77 characters).
  Every piece on the card is at that mint. An entry carries no mint.
- The mint is changed only by `SET_CARD`, with nothing unspent and no change owed,
  by the owner's proof on a card with an owner, and under the PIN (and a held tap,
  once used) on a card with none (SPEC 5.2, LIMIT 8.4). `cardRepointFor` in the
  phone does it for an empty card, as now.
- The phone keeps a piece only where the record's mint lists its keyset **in the
  unit the table names** (`keysetUnit` of `08c-units.js`), and checks its DLEQ
  with that mint's keys of that unit (9.1).

No `SET_MINT`: instruction `46` stays unused. No mint list in the book. No mint
form of `GET_CARD`.

### 4.2 GET_CARD (`16`)

| P1 | Answer |
|---|---|
| `00`, and any but `02` | 1.22's, to the byte but for byte 0, the card's format (4 or 5: 4.3): record set (1), unit (1), daily limit (4, cents on 1.23), refund key (33), the clock's 65 bytes, mint length (1) and address, design (3). The 29 bytes after the newest header's hash stay zeros in the answer, though the rate is kept in the first four of them (6.2): the answer masks it. At most 186 bytes |
| `02` (1.23) | the table: a count (1), then each entry in use, its nine bytes as kept (3.1), in the table's order. At most 55 bytes. `00` alone on a card with no book |

No PIN, any state: a till needs the table to sort the pieces into units before it
picks. A form of its own, because a phone of today reads P1 = 0's answer by its
exact length (`cardRecordOf`), and would refuse a longer one. P1 = 1 is left as
P1 = 0, the place a mint list would take (4.4). A card of 1.22 does not look at P1;
a phone asks P1 = 2 only of a card that says `01 17`.

### 4.3 GET_INFO (`01`), and the card's format

`GET_INFO` with P1 = 0, 1 and 2 answers the 30, 42 and 43 bytes it does, laid out
to the byte as 1.22's but for the version and byte 8; every figure in them is
cents (the daily limit at 12, spent today at 25, the limit on one payment at 30,
the allowance and its count at 34 and 38). With **P1 = 3** (1.23) it answers 51:

| Bytes | Field |
|---|---|
| 0 to 42 | the 43 bytes of P1 = 2 |
| 43 | the book's format: `01`; `00` on a card that was refused the room for it |
| 44 to 47 | the rate (6.2): with the owner's grant in this tap, or on a card with no owner to the PIN in a held tap (1.22, LIMIT 8.4); zeros to anyone else, as bytes 30 to 33 are |
| 48 | entries in use |
| 49 | of them, entries without the mark |
| 50 | the last payment's unit, relative (`80`, `A0` or `00`) |

Any other P1 is answered with the thirty bytes, as now. A card of 1.22 answers
P1 = 3 with the thirty.

**Byte 8, the card's format.** It has been 4 since 1.4, and it says how a piece's
secret is made (`SIG_ALL`). It still means that, and gains one value:

| Byte 8 | Means | A phone of today |
|---|---|---|
| `04` | every entry in use is of the record's unit, or there is no table: **a card of one unit**. The secret is format 4's | reads it, pays with it and loads it as a 1.22 card of that unit (a dollars card as the dollars card that has shipped) |
| `05` | some entry in use is of the other unit. The secret is format 4's, and the table has to be read before a piece can be counted | refuses it before anything else is read: "That card is a kind this Foxy does not know." (`cardLook`, which takes 3 and 4 and nothing else) |

`GET_CARD`'s byte 0 says the same. The card works it out at each asking from the
six state bytes: 5 if an entry in use has a bit 0 that is not the record's unit
byte, else 4 (the mark is not looked at; no book is 4). So a card goes to 5 at its
first entry of the other unit, and back to 4 when the last is reclaimed. Why a
format and not only a version: a phone of today checks the format, not the version,
before it reads a piece in the record's unit, and it is the one switch that stops
it reading cents as sats (10.2). New phones take 4 and 5 as the same secret (9.1).

### 4.4 What three mints would have added (history)

The earlier draft held money at up to three mints. It would have added: two more
addresses in the book (78 bytes each); `SET_MINT` (`46`), under the owner's proof
over `FoxyCard/set-mint`, refused while money of that mint was on the card; a mint
index in every entry and sixteen entries in place of six (two for each of six
balances, and four for the cap); a reclaim at `SET_MINT` so that a dead entry of
an old mint could not misfile a new mint's money; the mint's bits in the log and
the receipts; `GET_CARD` P1 = 1 for the list; and a till that could be paid at any
of the three. The door is marked: instruction `46` and `GET_CARD` P1 = 1 are left
unused, an entry's state byte keeps bits 4 and 5 zero where an index would go, and
a log entry's flags keep bits 6 and 7 free (bit 4 is 1.22's let-go and bit 5 is
the unit, 6.6). A mint index would need two bits there, and the book would grow by
the addresses; nothing else in 1.23 would have to move.

## 5. Sums

The card's pieces are listed by `GET_PIECES` as they are, each with its keyset.
A phone reads the table once (`GET_CARD` P1 = 2) and sorts the pieces into units
itself; it needs no sum from the card. The card's sums are for a reader that does
not list pieces (an upstream terminal, a quick look):

| Command | Answers |
|---|---|
| `GET_BALANCE` (`11`), P1 = `00` | the sum of the unspent pieces of **the record's unit**, 4 bytes. On a card of one unit that is every unspent piece, so the answer is 1.22's. On a card of format 5 it is never a sum across units: a reader that knows only this command sees the money it would understand, and no cents added to sats |
| `GET_BALANCE`, P1 = `01` (1.23) | a count u (1; 0, 1 or 2), then for each unit held, sats first: unit code (1), pieces (1), sum (4). At most 13 bytes |

A sum that passes four bytes wraps, as P1 = 0's always has
(`testGetBalanceWrapsPast2Pow32`). A card of 1.22 does not look at P1 and answers
the one sum. The card never says a total in one unit across both: the rate is for
holding a payment to the ceiling, not for counting money.

## 6. The ceiling

### 6.1 One ceiling, in cents, across units

The owner's answer to the first question: one ceiling across units. On a card of
1.23 **every limit in the record is in cents**, whatever the record's unit: the
daily limit (offset 2), what it counts (window 189, spent today 193), the limit on
one payment (197), the no-PIN allowance (201) and what it counts (205). A dollar
payment is compared as it is. A sats payment is turned into cents with the rate
at `SPEND_ALL_SIGN`, and then held to the same figures by the same code.

Foxy's app sets no daily limit at launch (LIMIT 6), and does not on 1.23 either.
The field stays, and the card keeps it as 1.22 does: a later app may set it, in
cents, and it is then charged with the same converted figure. So the ceiling that
works at launch is two numbers: **the limit on one payment** (a larger payment
waits, LIMIT 6a) and **the no-PIN allowance** (LIMIT 6f). Each bounds payments of
either unit, and each is counted across both: an allowance of $2.00 is $2.00 of
sats and dollars together, not $2.00 of each.

Why not a set of limits for each unit, as the earlier draft had it: a terminal
with the PIN would take the two together, and a thief two allowances where the
owner set one.

### 6.2 The rate

**What it is.** Sats per dollar, four bytes, big-endian, the price the owner's
phone shows turned round and rounded **down**: `R = floor(100,000,000 / P)`, with
`P` that phone's price in dollars a bitcoin. At $100,000 a bitcoin R is 1,000. 0
is no rate.

**Where it is kept.** In the record at offset 156, the first four of the 29 bytes
after the newest header's hash, zeros since 1.15 (they were the end of a time key
up to 1.14). `SET_CARD` does not write those bytes and so leaves the rate as it
is, as it leaves every limit; `RESET_CARD` zeros them with the rest of the record
but the clock. `SET_LIMIT` writes it, in the transaction that writes the limits
(6.3). `GET_CARD` answers those bytes as zeros (4.2); `GET_INFO` P1 = 3 says the
rate to the holder (4.3). Why the record and not the book: it is a limit, written
with the limits; it is there before the first `SET_CARD` and on a card refused its
book; and it costs no new storage.

**How a payment is turned into cents.** Java Card makes `int` optional and this
applet uses none (`toDecimal`, `SchnorrHW`), so the arithmetic is by hand on
bytes, in `scratch`:

    cents(S) = ceil(S × 100 / R)       S the payment's sats, four bytes; R the rate, R ≥ 1

    1. A = S × 100, in five bytes: from the low byte up, v = A[i] × 100 + carry, A[i] = v & 0xFF,
       carry = v >> 8. v is at most 255 × 100 + 99 = 25,599, inside a short.
    2. Q = A ÷ R and M = A mod R, by forty steps of shift and subtract on five-byte numbers.
    3. Q = Q + 1 where M is not 0.
    4. Q past four bytes (its top byte not 0): Q = FF FF FF FF, over every ceiling.

**The bound.** Every amount the card is asked about is four bytes: a piece, a
payment's sum (a sum that wraps is refused at `SPEND_ALL_BEGIN`, as now) and every
count. So S < 2^32 (42.9 bitcoin) and A = S × 100 < 2^39, which five bytes hold:
nothing overflows, for any S the card can hold and any R. Q fits four bytes
whenever R ≥ 100 (a price of up to a million dollars a bitcoin), since Q ≤ S then;
under that, a Q past four bytes is taken as over every ceiling. The whole supply,
21 million bitcoin, is 2.1 × 10^15 sats, 51 bits: past any figure a card holds, so
the card is never asked to convert it. R stays at least 1 for prices up to $100
million a bitcoin; past that a sat is worth more than a dollar and the rate would
need a scale, which is a later version's question.

**The rounding never under-counts.** R is rounded down, so each sat is counted as
at least its worth in cents at the phone's price, and Q is rounded up, so a part
of a cent counts as a cent. Both err toward the stricter: a payment in sats is
charged a little more than its worth, never less. The error from R's rounding is
under 1/R: 0.1% at $100,000 a bitcoin, 1% at a million.

### 6.3 SET_LIMIT, both forms

The owner's form (`34`): proof length (1), the proof (DER) over
`FoxyCard/set-limit`, the nonce the card gave and the value as sent, then the
value:

| Value | Sets | On 1.23 |
|---|---|---|
| 4 bytes: daily limit | as 1.22 | taken where the record's unit is dollars (the figures are cents already); `6700` where it is sats |
| 8 bytes: daily limit, limit on one payment | as 1.22 | the same |
| 12 bytes: those, then the allowance | as 1.22 | the same |
| **16 bytes** (1.23): daily limit (4), limit on one payment (4), allowance (4), rate (4) | every figure in cents, and the rate in sats per dollar (0 none) | taken |

The PIN form (`33`, a card with no owner, under the PIN given in this tap, with
nothing unspent and no change owed, and once used in a held tap: LIMIT 8.4):

| Value | Sets | On 1.23 |
|---|---|---|
| 4 bytes: limit on one payment | as 1.22 | taken where the record's unit is dollars; `6700` where it is sats |
| 8 bytes: that, then the allowance | as 1.22 | the same |
| **12 bytes** (1.23): limit on one payment (4), allowance (4), rate (4) | in cents, and the rate | taken |

**Why the old lengths only on a dollars record.** They carry no rate, and are
figures in the record's unit, as they always were. On a dollars record that is
cents: they are taken, the rate left as it is, and the dollars card that has
shipped works on a 1.23 card with no change to its phone. On a sats record they
would be sats where the card keeps cents, ten times too loose or more, so they are
refused before anything is written (before the first `SET_CARD` too, whose unit
byte is `00`). One comparison of the unit byte, with the length.

**The version gate, both ways.** A card of 1.22 answers the sixteen-byte owner's
form and the twelve-byte PIN form `6700`, so a figure in cents is never taken by a
card that does not know it is cents. A new phone sends the new forms only to a
card that says `01 17`, and the old ones, in the old way, to a card before it
(10.3).

**What each form does, beyond 1.22.** The rules of LIMIT 6f hold for every
figure: a figure that does not change keeps its window and its count; a new daily
limit begins its window at `now` with nothing spent; a new allowance counts from
nothing; the PIN form's allowance count goes to 0 whether or not its number
changed. A new rate changes no count and begins no window: the counts are cents
already. The proof is over the value as sent, rate and all, so a proof for one
rate is none for another, and none for a shorter value.

`writeLimit` writes the rate, where it changed, in the transaction that writes the
limits: one `arrayCopy` of four more (7.4).

### 6.4 A payment held to the ceiling

The payment's unit is its first place's entry's (7.1). At the first
`SPEND_ALL_SIGN`, after 1.22's `6A97` and `6985`:

1. **Its cents.** A dollar payment's figures are its own. A sats payment's two
   figures are turned into cents (6.2): **the whole of its pieces** (`allSum`),
   which the allowance counts (LIMIT 6f), and **what leaves the card** (`allNet`,
   the pieces less the card's own change), which the day and the wait count (LIMIT
   6, 6a). Each is converted and rounded up by itself, so neither under-counts.
   They go in `scratch` at offsets 0 and 4, the division's room at 8 to 17, all
   inside `X_HEX`, unused at `SPEND_ALL_SIGN`, and are worked out again at the last
   `SPEND_ALL_SIGN`, where the checks are asked again: nothing is kept in RAM
   between the two, and the rate cannot change between them (any other command
   gives the payment up).
2. **No rate.** A sats payment, no rate (R = 0), and any of the three limits set:
   **`6A99`**, the payment given up, nothing signed, burned, counted or logged.
   Before the PIN gate, so a reader with no PIN writes nothing. With no limit set
   none is asked, and a dollar payment never asks. The card refuses rather than
   taking a sats payment as unbounded or as zero cents, which would leave a ceiling
   the owner set with none for sats. Foxy's phone writes the rate with every
   ceiling (9.1), so `6A99` is met only by a card set by something else.
3. **The allowance** (`requireAllowanceOrPin`), held to the whole in cents, as 1.22
   holds it to the whole in its unit: `6A94` over it, the PIN for the whole payment.
4. **The day** (`requireUnderLimits`), held to what leaves the card in cents, where
   a daily limit is set: `6A8F`, written in the log, as now.
5. **The wait** (`waitsFor`), with L the limit on one payment and S what leaves the
   card, both in cents, the formula of LIMIT 6a unchanged:

        S ≤ L + L/32         waits = 0
        otherwise            waits = 10 + 4 × (units − 2), units = ceil(S / L), at most 255,
                             less two for every three change outputs, never below 0

   S is rounded up (6.2) and `units` counts a part of a limit as a whole, as the
   code always has, so the wait never under-counts: about seven seconds over the
   limit and three more a further limit's worth, at least what the payment's true
   worth in cents would ask. A second payment in a time in the field is slowed as
   now, whichever unit.

`requireUnderLimits` and `waitsFor` already take the sum they are held to, and
`requireAllowanceOrPin` is given its figure the same way, so the code that counts
is 1.22's, handed cents. The commit is 1.22's, in cents. `SET_HEADER` is untouched:
one window, as there was.

### 6.5 How stale a rate may be

The card has no clock that runs, and it does not judge its rate: the rate is the
owner's phone's word, as the ceiling is. What keeps it near the market is the
phone's rule, which exists today for the figures themselves: at each read of the
card as its owner (`cardLook` with `mine`), where the price the phone shows has
moved more than `CARD_PACE_DRIFT` (2%) from what the card holds, the phone writes
it again with its proof, in the tap. On a sats card of 1.22 it re-pegs the
figures (`cardPaceSats`, `cardPaceNoPinSats`); on a card of 1.23 the figures are
cents and never move, and it re-pegs the rate: the sixteen-byte form with the
three figures as the card has them (so no window or count begins again) and the
new R. The phone compares its own R with the one the card says (`GET_INFO` P1 = 3,
bytes 44 to 47, which the owner's grant opens), not with a note of its own, so it
sees the rate it last wrote, and a write that was lost is written again at the
next read.

A card with no owner has no owner's read. Its rate, like its limits, is set under
the PIN only while it is empty (`33`, LIMIT 8.4), and drifts with the price while
it holds money. A 1.22 card with no owner drifts the same: its sats limits are
fixed and their dollar worth moves. A sats payment is held to the same number of
sats either way.

A till is told nothing new. It is not told the rate (4.3) and does not need it: it
reads the allowance in cents, guesses with its own price, and the card's answer
(`6A94`, the wait) is the truth, as it is today (`cardNeedsPin`).

### 6.6 The log and the receipts say the unit

Why: the log is the one account of a card's use that needs nobody's honesty, and
"1,000 signed for" without its unit says nothing. Its amounts stay in each
payment's own unit; the cents a payment was weighed at are the ceiling's business.

- **A log entry is of one unit.** Its flags byte gains bit 5 (`0x20`): set, the
  other unit; clear, the record's. Bits 0 to 4 are 1.22's (bit 4 the let-go, 1.22).
  A tap whose payment or load is of another unit than the entry it has begins a
  new entry: the count of taps then counts entries, which a phone that remembers
  the counts it saw reads the same. An entry of a card that holds one unit has bit
  5 at 0, so its log is 1.22's to the byte (goal 5).
- **How `logEntry` knows.** It takes the unit, and where the tap has an entry
  (`tapOpen[0]`) whose bit 5 differs, begins a new one, its bit written in the
  transaction that begins it (`logLoaded`'s, `SIGN`'s, `refuseOverLimit`'s,
  `LOAD_CHANGE`'s, whose unit is its opening's keyset's). Nothing new in RAM.
- **Relative to the record.** Bit 5 and the head's count are relative to the
  record's unit when read, as 1.22's one count always was; a change of the record's
  unit (6.7), the owner's on an empty card, changes what old entries mean.
- **The counts.** The head's count at offset 4 counts the record's unit. The other
  unit's is in the book (55). `GET_LOG` with **P1 = 2** (1.23) answers 20 bytes of
  counts (taps, the record's unit ever, refused, runs, the other unit ever) and the
  eight entries: up to 180 bytes. P1 = 0 is the up to 176 it is.
- **The receipts.** Each receipt's amount is in its payment's unit, and the unit
  is kept beside it in the book (59, one byte for each of the sixteen), written in
  the receipt's own transaction. `GET_LOG` with **P1 = 3** (1.23) answers as P1 = 1
  does with each receipt one byte longer, its 77 and its unit: 4 + 3 × 78 = 238
  bytes. P1 = 1 is the 77-byte form it is. A receipt from before the book has `00`.
- **Who reads them.** P1 = 2 is P1 = 0's (the PIN or the grant with an owner; the
  PIN in a held tap with none) and P1 = 3 is P1 = 1's (the grant with an owner; the
  PIN in a held tap with none), and on a card with no owner each is counted toward
  the held tap as the form it extends is. Any P1 past 3 is `6A86`, which neither
  counts nor ends the held count. A card of 1.22 answers P1 = 2 and 3 with `6A86`.

### 6.7 A change of the record's unit

Moot. The earlier draft kept each unit's limits in that unit, so a record turned
from sats to dollars would have read a daily limit of 2,000 sats as $20.00, and it
refused the change while a limit was set. On 1.23 every limit is in cents whatever
the record's unit, so `SET_CARD` may change the unit byte (on an empty card owed
nothing, as always) and every limit means what it meant. Nothing is refused for
it. What the unit byte still decides is which unit is the record's: the format
(4.3), `GET_BALANCE` P1 = 0, the log's bit and its head count, and whether the old
lengths of `SET_LIMIT` are taken (6.3). New in 1.23, `SET_CARD` takes only `00`
and `01` there (`6A80` for any other, with the design's check).

## 7. Payments

### 7.1 One unit a payment, and who chooses it

`SPEND_ALL_BEGIN` (`22`) refuses places of both units: **`6A9A`**, nothing hashed,
the payment not begun. It is checked in the loop over the places, after a place is
found unspent and named once and with the date: the first place's keyset is
compared with each place's own (one native comparison a place), and only a place
of another keyset is looked up in the table, its unit compared with the first
place's. Two keysets of one unit (a mint's old keyset and its new) are one unit,
and a mint takes them in one swap. The first place is looked up too: a place of no
entry (3.5) is `6A9A`, never the record's unit. Being in the loop, it comes before
the refusal of a sum that wraps.

Why the card refuses and does not leave it to the mint: a mint refuses a swap whose
inputs are of two units, so no money would move. But the card charges its counts
before the mint is asked, and a payment of sats and cents together has no one
figure to charge.

**The card never chooses a unit.** The payment's unit is the unit of the pieces the
phone names. The phone chooses them after asking the holder: where a card holds
both units and either could pay, the phone asks every time, on the app's own
currency question (SELECT CURRENCY), the two with their balances, and a unit that
cannot cover the price shown as short and not offered (the owner's answer to the
fifth question: always ask). Where only one unit can pay, the phone pays from it
and asks nothing. The unit is named to the card by the pieces and by nothing else:
`SPEND_ALL_BEGIN` gains no byte, so a 1.22 phone's payment is unchanged, and a
phone that named the wrong pieces would be refused (`6A9A`) before a mixed payment
could be signed.

At `SPEND_ALL_SIGN`, the payment's unit is the first place's entry, looked up once
at each `SIGN` that needs it. Nothing new is kept in RAM between the commands.

### 7.2 SIG_ALL stays per unit and per date

One signature is over every piece and every output of one swap at one mint, and
the pieces' secrets have to agree in everything but the nonce (LIMIT 6c). Both
rules stand: one unit a payment, one date a payment.

### 7.3 The card's own change

`SPEND_ALL_CHANGE` copies the payment's first keyset into the opening, as it does
now. So the change is in the payment's unit by construction, and what leaves the
card (the pieces less that change) is in the payment's unit before it is turned
into cents. The opening carries its unit through its keyset, whose entry it keeps
live until the piece is back by `LOAD_CHANGE` (3.4). `GET_CHANGE` answers the
openings as it does; a phone finds an opening's unit in the table it has read. The
eight openings are shared by both units.

### 7.4 The last payment's unit, and every transaction that changes

Kept in the book (54), relative, written in `SPEND_ALL_SIGN`'s transaction: the
unit a till with the PIN may enter a new keyset as (3.3), said in `GET_INFO` P1 = 3
byte 50, let go by `SET_CARD` and `RESET_CARD`.

The chip's commit buffer holds about a dozen writes and jCardSim's has no limit,
so each transaction this design touches is listed, and measured at the chip (10.7):

| Transaction | Gains |
|---|---|
| `SPEND_ALL_SIGN` | one byte (the last unit); at a tap's first entry of a unit, the new entry's flags byte (6.6). The other unit's count, where the payment is of it, replaces the head's, and does not add to it. Spent today and the no-PIN count are the same writes, in cents |
| the receipt's | one byte (the receipt's unit) |
| `logLoaded`, `LOAD_CHANGE`'s log, `refuseOverLimit` | the flags byte, at a new entry |
| `writeLimit` | one `arrayCopy` of four (the rate), where it changed: at most seven writes in all |
| `SET_CARD` | none: the reclaim and the last unit's byte are outside it, before it (3.4) |
| the entry at a load | none: its bytes are written before the slot's, outside any transaction, its state byte last (3.6) |

## 8. Commands and words, all together

New or changed in 1.23. `SELECT` answers `01 17`. No capability bit changes (all
eight were taken by 1.9), and no instruction is new: the version says what a card
knows.

| INS | Command | Needs | Data sent | Answers |
|---|---|---|---|---|
| `01` | GET_INFO | as 1.22 | P1 = 3 | 51 bytes (4.3). Byte 8 is 4 or 5 in every form; every figure is cents |
| `11` | GET_BALANCE | | P1 = 0 or 1 | P1 = 0: the record's unit's sum; P1 = 1: each unit's (5) |
| `16` | GET_CARD | | P1 = 0 or 2 | the record as 1.22's, byte 0 the format, the rate masked; the table (4.2) |
| `18` | GET_LOG | as P1 = 0 and 1 | P1 = 2 or 3 | the counts with the other unit's; the receipts with their units (6.6) |
| `22` | SPEND_ALL_BEGIN | as 1.22 | | `6A9A` for places of both units, or of no entry (7.1) |
| `24` | SPEND_ALL_SIGN | as 1.22 | | a sats payment in cents at the rate; `6A99` with a limit and no rate; the last unit written (6.4, 7.4) |
| `30` | LOAD_PROOF | as 1.22 for the load (the PIN given in this tap); the holder's say for a new keyset of the other unit (3.3) | P2 = 1: the unit code (1), then the pieces; any P2 but 0 and 1 is `6B00` | `6A9B`, `6A9C`, `6A9D` as 3.3; a new keyset entered |
| `31` | CLEAR_SPENT | as 1.22 | | and a reclaim of the table (3.4) |
| `32` | SET_CARD | as 1.22 | as 1.22 | `6A80` for a unit over `01`; drafted openings let go and the table reclaimed before its transaction; the book reserved the first time (3.4, 3.6) |
| `33` | SET_LIMIT, PIN form | as 1.22 | 12 bytes: limit on one payment, allowance, rate | the 4 and 8 bytes of 1.22 only on a dollars record, `6700` on a sats one (6.3) |
| `34` | SET_LIMIT, owner's | as 1.22 | 16 bytes: daily limit, limit on one payment, allowance, rate | the 4, 8 and 12 bytes of 1.22 only on a dollars record, `6700` on a sats one (6.3) |
| `51` | RESET_CARD | as 1.22 | | and the book zeroed; the rate zeroed with the record |

Every command in every state, the rows that change:

| Command | Card with no owner | Card with an owner |
|---|---|---|
| `GET_INFO` P1 = 3, `GET_CARD` P1 = 2, `GET_BALANCE` P1 = 1 | open; the rate in P1 = 3 to the PIN in a held tap | open; the rate in P1 = 3 to the owner's grant |
| `LOAD_PROOF` of a new keyset of the other unit, not the last payment's | the PIN in a held tap (`6A9D` with the PIN alone) | the PIN and the owner's grant in this tap (`6A9D` with the PIN alone) |
| `SET_LIMIT` with a rate | `33`, 12 bytes, as 1.22's `33` | `34`, 16 bytes, as 1.22's `34` |

**Status words.** 1.23 adds five, in the part of `6Axx` that ISO 7816-4 leaves
unassigned. Against LIMIT 8's table, none collides: 1.22 answers `6A8C` (no record),
`6A8D` (in use, or change owed), `6A8E` (a dated piece, no refund key), `6A8F` (over
the day), `6A90` (no owner), `6A91` (the owner's proof), `6A93` (little work), `6A94`
(a piece on the card already; the PIN needed), `6A96` (too many), `6A97` (no PIN,
1.21) and `6A98` (keep holding, 1.21), and keeps `6A92` and `6A95` unused, as it
says; 1.23 keeps every one of those meanings, the figures behind `6A8F` and `6A94`
now in cents. The earlier draft's `6A97` ("another balance") and `6A98` ("two
balances") collide with 1.21's words and are not used for them:

| SW | Means (1.23) |
|---|---|
| `6A99` | **no rate**: `SPEND_ALL_SIGN` of a sats payment on a card with a limit set and no rate. The payment given up; nothing signed, burned, counted or written in the log (6.4) |
| `6A9A` | **two units in one payment**: `SPEND_ALL_BEGIN` given places of both units, or a place of no entry. Nothing hashed, counted or written; no payment begun (7.1) |
| `6A9B` | **the other unit**: a load whose piece's keyset is in the table as the other unit than the load names. Nothing written (3.3) |
| `6A9C` | **no room in the table**: a new keyset with no free entry after a reclaim, or a third live entry without the mark; and every load labelled the other unit on a card refused its book. Nothing of the piece written (3.3) |
| `6A9D` | **not this tap's to start**: a new keyset as the other unit, not the last payment's, without the holder's say. Kept apart from `6A91`, which a phone reads as "not the owner", and `6982`, "the PIN". Nothing written (3.3) |

`CardGate.swift` gains nothing: no instruction is new.

## 9. The phone

Each file, and what it gains. Nothing here is built. The dollars card on 1.20 and
1.22 (a record of `01`, dollar pieces, limits in cents typed in dollars and never
re-pegged, the till's dollar payments, the rows with `unit: 'usd'`) has shipped,
and is not on this list. Everything below asks a card for nothing new unless it
says `01 17`.

### 9.1 `build/wallet/08a-flashcard.js`

- **The format, everywhere it is compared with 4**, becomes `>= CARD_FORMAT_ALL
  && <= 5` (a format 6 still refused): `cardSecret`, `cardIsAll`, every capability
  `cardInfoOf` works out as `b(8) === CARD_FORMAT_ALL && version` (`headers`,
  `noPinKnown`, `resetKnown`, `moreKnown`, `proofsKnown`, `ownChange`, `shaped`,
  `costed`: left alone, a card of format 5 would read as knowing none of them), and
  `cardLook`'s refusal. A row's `format`, filed for take-back, is filed as 4 for 4
  or 5. A test greps the three files for `=== CARD_FORMAT_ALL`, `=== 4` and `== 4`
  beside `format` and finds none left. These go in first, with the reading of 5.
- **Reading.** `cardIs123` beside `cardIs121`. `cardInfoOf` reads the 51 bytes of
  P1 = 3 into `info.book` (format, entries, unmarked, last unit) and `info.rate`,
  and marks every figure of a 1.23 card as cents (`info.cents`). A new
  `cardTableOf` reads `GET_CARD` P1 = 2 into `card.table` (short keyset to unit
  code). `cardLook` asks `GET_INFO` P1 = 3 in place of P1 = 1 on a 1.23 card (the
  owner's second asking after the grant too), and `GET_CARD` P1 = 2 where byte 8 is
  5; on format 4 every piece is the record's unit and the table is not asked.
  `cardRecordOf` is unchanged: the rate is masked in its answer.
- **Sorting the pieces.** On format 5 each piece's unit is the table's, and the
  mint must list its keyset in that unit (`keysetUnit`); `cardFullId` takes a unit
  and maps only among its keysets. A piece that fails is foreign, counted nowhere
  and never paid with. `card.units` is the pieces by unit with their sums;
  `card.pieces` stays the whole list (the mint's state check, `cardRealCheck`).
- **Usable.** `cardUnusable` takes the unit asked for and says whether the card
  holds it, in the words it has (`cardWrongUnit`); on a card of both units neither
  is wrong, and the page asks (9.5).
- **The ceiling.** On a 1.23 card `cardDayOf`, `cardTapOf` and `cardNoPinOf` read
  cents; `cardNeedsPin` turns a sats `net` into cents before it compares, with the
  card's rate where this phone is the owner and its own price where not, rounded as
  the card rounds (6.2); `cardWaitSigns` is handed the cents of what leaves the card.
- **Writing it.** `cardLimitTo` sends the sixteen-byte form (the figures in cents
  and R) to a card that says `01 17`, the old forms, as now, to one before it; the
  PIN form sends twelve bytes on 1.23. `R = floor(100,000,000 / P)`, `P` the price
  SET AMOUNT uses; with no price, no write. The phone asks the owner before writing
  a rate more than twice, or under half, the card's (11, row 26).
- **The re-peg.** On a 1.23 card `cardLook` (`mine`) re-pegs the rate, not the
  figures (6.5): its R against `info.rate`, by `CARD_PACE_DRIFT`, the figures sent
  as the card has them. No `cardPaceNote` or `cardPaceNoPinNote` is written for a
  1.23 card: its figures are on the card, in cents.
- **A limit lifted for a withdrawal** (`cardUnderPin`, `cardLiftNote`): the lift and
  the putting back are the sixteen-byte form on 1.23, the rate as the card has it.
- **Choosing pieces.** `cardPick`, `cardPickAll` and `cardExactPick` are handed
  one unit's pieces. Unchanged inside.
- **Signing.** `cardSignAll` and `cardSignGroup` begin with one unit's places.
  `6A9A` is read as "the card was asked to pay from both currencies at once";
  `6A99` as "this card cannot pay in bitcoin until its owner's phone sets it again".
- **Loading.** `cardPrepare`, `cardLoadCut` and `cardChangeCut` make pieces in the
  unit's wallet (`walletIn`), on its active keyset (`unitKeysetId`), cut in it (9.2);
  `cardMintMisfit` asks of that keyset. `cardWriteOwed` labels every piece on a 1.23
  card, the record's unit too (so the card checks it), and sends plain to one before
  it; on a card with no owner a new keyset of the other unit goes in a held tap.
  Words: `6A9B` "That money is the other currency on this card. Nothing was put on
  it."; `6A9C` "This card has no room for another kind of money until its owner's
  phone clears it."; `6A9D` "Only the card's owner can add a new currency to it."
- **Records.** `CARD_OWED`, `CARD_SWAPS`, `cardRemember`'s rows and the receipts
  (from `GET_LOG` P1 = 3) carry each unit, as the dollars card's rows already do.

### 9.2 The 128 places between two units

The owner's answer to the seventh question: what this design recommends. A card of
128 places is cut deep today, eight of every size from 1 to 1,024 (`CARD_DEEP`,
`CARD_DEEP_TOP`, `cardDeepLadder`): 88 pieces, and eight prices in a row paid
exactly. Two units each eight deep would want 176. So:

| Card | Unit | Sizes | Of each | Pieces | Worth |
|---|---|---|---|---|---|
| one unit | sats | 1, 2, 4 … 1,024 sats | 8 | 88 | 16,376 sats |
| one unit | dollars | 1, 2, 4 … 1,024 cents ($0.01 to $10.24) | 8 | 88 | $163.76 |
| both units | sats | 1, 2, 4 … 1,024 sats | 4 | 44 | 8,188 sats |
| both units | dollars | 1, 2, 4 … 1,024 cents | 4 | 44 | $81.88 |

Both units deep are 88 of the 128, the same as one unit today, and leave 40 for
the rest of each amount in powers of two and for the card's own change (at most
`CARD_WIDE_CHANGE`, 32, a payment). Four of each pays four prices in a row exactly
in each unit, up to 2,047 of that unit each.

What the refill does per unit: `cardDeepLadder` gains the depth (8 for a card that
holds one unit, 4 for one that holds both or is about to) and is handed only that
unit's pieces as `have`, so a top-up of dollars fills the dollar rungs and never
counts or cuts a sats piece, and the reverse. The sizes are the unit's keyset's
(`unitKeysetId`): a dollar keyset that does not list a size is cut without it. When
the second unit first goes on, the first unit's eight-deep drawer is not cut down;
it drains to four as it is paid from, and its refills go to four. Where the places
do not allow four, it is three of each, down to none, as `cardDeepLadder` already
steps down within `CARD_WIDE_LOAD`.

### 9.3 `build/wallet/08c-units.js`

Nothing new. The card's code uses `keysetsOf`, `keysetUnit`, `unitKeysetId`,
`walletIn` and `cardUnitByte` (in `08a`) as they are, and every write into a pile
still passes `unitGuard`, so a dollar piece taken off a card can only land in the
dollar pile.

### 9.4 `build/wallet/21a-flashcard.js`

The page's calls gain a unit where a card holds both: `cardPrepare(card, amount, {
unit })`, `cardWithdraw(link, { unit, amount })`, `cardPay` (the till's) with the
unit the holder chose, and `cardUnits(card)` for the screen. `cardSetLimit` takes
dollars and, on a 1.23 card, writes cents and the rate; on a card before it, what
it writes today. A new `cardSwap` runs the two taps of 9.6. The take-back
(`cardTakeBack`, `Number(row.format) === CARD_FORMAT_ALL`) takes the row's format as
9.1 says, and swaps each piece into the pile of its own unit.

### 9.5 `build/app/26f-flashcard.js`, the Flashcard screen

- **The pills.** A card that holds both units shows two, as the two-balances home
  has them: the dollar pill with its worth in sats under it, the bitcoin pill with
  its worth in dollars under it, at the market price. A card of one unit shows one,
  as now. One mint, named over the pills as now; no rows of other mints.
- **The ceiling.** Said once, under the pills, in dollars: the limit on one payment
  and the no-PIN allowance, each for both units together. No daily limit is shown
  where none is set.
- **CHANGE LIMIT** takes one figure for both units, and the rate is written with it.
  The owner's rule for the unit it is typed in: a card that holds dollars only takes
  the figure in dollars; a card that holds bitcoin, alone or with dollars, offers
  dollars or sats (the SET AMOUNT screen's swap). A figure typed in sats is turned
  into cents by the phone at the rate it writes in that same tap, so the card still
  holds cents. The figure is never re-pegged; the rate is, without a word (6.5).
  The same rule holds for the set-up screens' wheels (the no-PIN amount and the
  limit on one payment).
- **ADD FUNDS and WITHDRAW** ask which currency first where both are possible, on
  the app's own currency question with the two balances, then SET AMOUNT in that
  unit.
- **Paying from the card** (SEND, and the till's sheet when this phone is the
  holder's): SELECT CURRENCY every time both units could pay (7.1).
- **SWAP** on a card that holds both (9.6).
- `fcShaped` (`c.info.format === 4`) becomes `>= 4`, as 9.1.
- **Words** for `6A99` to `6A9D`, as 9.1.

### 9.6 SWAP, a phone's swap with a tap at each end

The card cannot reach a mint, and a swap between dollars and sats is the mint's (a
quote paid by a melt), which takes from a few seconds to a minute: longer than a
card is held. So a swap is the phone's, with the card at each end:

1. **SWAP**, the amount on SET AMOUNT in the unit that leaves, the app's own swap
   confirmation (YOU ARE GETTING, YOU ARE SPENDING, FEE, the mint's rate).
2. **Tap 1, the PIN.** A withdrawal of that amount from that unit, as WITHDRAW is:
   the ceiling lifted with the owner's proof, the pieces signed under the PIN, the
   ceiling put back. The money is in this phone.
3. **No card.** The phone swaps at the mint, as its own SWAP does.
4. **Tap 2, the PIN and the grant.** ADD FUNDS of what arrived, into the other
   unit, labelled, the keyset entered with the mark where it is new.

Cut short anywhere, the money is in the phone, in whichever unit it was then, and
the screen offers to put it on the card or leave it. Only the owner's phone swaps.

### 9.7 The till

A till asks to be paid in its request's unit at its own mint. It reads the card
(`GET_INFO` P1 = 3 and, on format 5, the table) and finds the pieces at its mint in
the unit the holder chose. Then everything is as now, on that unit's pieces: the
pieces it picks, the PIN or the allowance (in cents: 6.5), the wait, the change, the
second tap.

| What the card holds | The till |
|---|---|
| no money at this phone's mint | as now: "This card's money is at <its mint>. This phone is at <this mint>." |
| one unit, the request's | paid from it, as now |
| one unit, not the request's | as now (`cardWrongUnit`: a dollars card asked for sats is offered a figure in dollars for the holder to agree to; a bitcoin card asked for dollars is refused in words) |
| both units | SELECT CURRENCY, every time both could pay, the holder answering at the till as they give the PIN; one alone where only one can |

Offline, a till takes only an exact set of one unit in its request's unit, as now,
and the DLEQ of each piece is checked against the mint's keys of that unit
(`unitKeysOffline` for dollars).

**Change** comes back by `LOAD_CHANGE` in the payment's keyset, with no label
(7.3). **A refund to the payer** of a payment the mint refused is a labelled load in
the payment's unit, under the PIN, which the card lets in as the last payment's unit
(3.3).

### 9.8 Native

- `Foxy/Flashcard/CardGate.swift`: nothing. No instruction is new, and the gate
  checks the class and the instruction only.
- `Foxy/Keychain/NUT13.swift`, `CardOwner.Label.setLimit`: `accepts` takes 16 bytes
  beside 4, 8 and 12. The PIN form carries no owner's proof and never reaches it.
  Native refuses every other label and shape before it reads the seed, as now. Its
  tests gain the sixteen-byte value.

## 10. Migration and compatibility

### 10.1 The cards in hand are installed again as 1.23

The owner's answer to the ninth question: installing again is fine. There is no
migration on the card. A Java Card applet is not upgraded in place: a new version is
a new install, which makes a new key, and pieces locked to the old key cannot be
moved to the new one by the card. So, for each card in hand:

1. The owner's phone takes every piece off (`cardWithdraw` of everything, as RESET
   does in the app), and the change owed is written back or given up first, as
   RESET already asks (`6A8D`).
2. The card is deleted and installed again with the 1.23 load file, in the order of
   `HARDWARE_DEPLOYMENT.md` (the tag, the card's applet, the opener, then the
   package; installed opener first), at a size known to install (12.2).
3. The phone sets it up as a new card: PIN, record, the owner's claim, then the
   ceiling with the rate (the sixteen-byte form).
4. ADD FUNDS puts the money back, labelled, in whichever units the owner chooses.

The old log and receipts go with the old install; the phone keeps its own rows of
the card. A card not installed again stays a 1.22 card, and a new phone keeps
working with it as now (10.3).

### 10.2 A card of 1.23 under a phone of today

| The card | What the phone of today does |
|---|---|
| one unit, sats, no limit set (format 4) | everything, as with a 1.22 card. Its plain loads go in as the record's unit |
| one unit, sats, a limit set (format 4) | reads it, pays from it, is paid by it, loads it. It reads the cents as sats: its screen misstates them, and its guess at the PIN errs toward asking (under a million dollars a bitcoin); the card decides, as always. It cannot change them: its old `SET_LIMIT` lengths are `6700` on a sats record, so its set-up stops at the allowance, CHANGE LIMIT is refused, its re-peg is let go, and its withdrawal stops at the lift, before anything is signed |
| one unit, dollars (format 4) | everything, as the dollars card that has shipped: its figures are cents, as it writes them, and its old forms are taken |
| both units (format 5) | refuses it before it reads a piece: "That card is a kind this Foxy does not know." |

So no phone ever reads a dollar piece as sats. That needs the format byte: a phone
of today maps a keyset to any keyset of its mint whatever its unit (`cardFullId`),
and the version alone does not stop it, since it takes any version past 1.22 as
having what 1.22 has.

### 10.3 A card of 1.22 under a new phone

The new phone knows it by its version (`01 16`) and sends it nothing new: it reads
it, sets its limits in its unit and re-pegs a sats card's figures, as today.

| New form | What a 1.22 card does | Why it is safe |
|---|---|---|
| `GET_INFO` P1 = 3 | the thirty bytes | the phone checks the version, and the length (51) |
| `GET_CARD` P1 = 2 | the record | the phone asks only a 1.23 card |
| `GET_BALANCE` P1 = 1 | the one sum | the same |
| `GET_LOG` P1 = 2, 3 | `6A86` | the same |
| `LOAD_PROOF` labelled | `6700` (82, 163, 244 and 178 are not its lengths) | a label sent by mistake writes nothing |
| `SET_LIMIT` of 16 bytes (`34`), of 12 (`33`) | `6700` | a figure in cents is never taken as sats |

### 10.4 Version and formats

| | 1.22 | 1.23 |
|---|---|---|
| `SELECT` | `01 16` | `01 17` |
| the card's format, `GET_INFO` byte 8 and `GET_CARD` byte 0 | 4 | 4 for a card of one unit, 5 for one of both |
| the piece's format (the secret) | 4 (`SIG_ALL`) | 4, whatever byte 8 says |
| the book's format, `GET_INFO` P1 = 3 byte 43 | none | `01`, or `00` for a card refused the room |
| the limits | in whatever unit the phone wrote | cents |
| `FORMAT` in the applet | 4 | 4, and a constant `FORMAT_UNITS` = 5 |

### 10.5 The model of the card (the card with no card)

`tests/flashcard-card.js` in Foxy answers as the applet does, and is held to it by
`tests/flashcard-model.js` replaying a recording of the applet.

- `makeCard({ software: 23 })` is this design's card, `software: 22` and before the
  cards before it, and `makeCard({ book: false })` a 1.23 card refused its book, as
  `proofRoom: false` is for the proofs. It has every rule of sections 3 to 8.
- `tests/flashcard-model.js` replays a 1.23 recording and keeps every recording it
  has, the 1.22 one as `-122`.
- A new suite, `tests/flashcard-units.js`: dollars and sats on one card at one mint
  (load both, read, pay a dollar till and a sats till, SELECT CURRENCY, change in
  each unit, withdraw, swap, one ceiling across both with the rate re-pegged as the
  price moves, a phone of today refused cleanly on format 5, a refund in a new keyset
  of the last payment's unit). The money suites run over a two-unit stand-in mint, as
  the dollars card's do (`flashcard-usd.js`). A live suite at a local mint of two
  units with the applet under jCardSim as the card.

### 10.6 The JUnit tests to add

A new class, `UnitsTest`, beside `ProofStoreTest`, with the applet under jCardSim.
Thirty-four tests, each and what it pins:

| # | Test | Pins |
|---|---|---|
| U1 | `theBookIsNotMadeAtInstallAndIsMadeByTheFirstSetCard` | null after install, after a PIN and after a refused `SET_CARD`; 75 zero bytes after the first that goes through; the same array after every later one |
| U2 | `aCardRefusedItsBookIsAOneUnitCard` | the allocation made to fail: plain loads as the record's unit; a load labelled the other unit `6A9C`; byte 8 is 4; P1 = 3 byte 43 and `GET_CARD` P1 = 2 are `00`; a rate is still taken and read back |
| U3 | `aPlainLoadEntersItsKeysetAsTheRecordsUnit` | under the PIN alone, `40` on a sats card and `41` on a dollars card; with the grant too, `C0` and `C1`; a second piece of the keyset writes no entry |
| U4 | `theOtherUnitNeedsTheHoldersSay` | a new keyset labelled the other unit: `6A9D` under the PIN alone, nothing written; entered marked under the PIN and the grant; on a card with no owner, `6A9D` under the PIN alone and entered marked in a held tap |
| U5 | `aTillMayEnterANewKeysetOfTheLastPaymentsUnit` | after a dollar payment on a sats card, the PIN alone enters a new dollar keyset, unmarked; after a sats payment it is `6A9D`; after `SET_CARD` the last unit is gone |
| U6 | `aKeysetIsNeverEnteredAsBothUnits` | a piece of an entered keyset labelled the other unit is `6A9B`, plain or labelled, with the proof or without; nothing written, the count the same |
| U7 | `aBadLabelIsRefusedBeforeAnythingIsWritten` | unit `02` is `6A80`; P2 = 2 is `6B00`; 82, 163, 244 and (P1 = 1) 178 taken, every other labelled length `6700` |
| U8 | `aLabelledLoadReadsItsPiecesOneByteOn` | P1 = 1, P2 = 1: the proof read back by `GET_DLEQ` is the one sent after the label, byte for byte; three labelled pieces: each slot holds its own 81 bytes; the log's "put on" is the pieces' sum, not a sum read a byte off |
| U9 | `theTableHoldsSixAndReclaimsTheDeadFirst` | a seventh keyset with every entry live is `6A9C`; with one dead it goes in, in the dead one's place |
| U10 | `noMoreThanTwoLiveEntriesWithoutTheMark` | a third unmarked `6A9C`; marked ones still go in; two unmarked entries, one dead and not yet reclaimed: a new unmarked keyset goes in after the reclaim; a load of a keyset already entered runs no reclaim (the pass is counted) |
| U11 | `clearSpentFreesWhatNoPieceAndNoOpeningNames` | after a payment of every piece of a keyset and `CLEAR_SPENT` its entry is free; one named by an owed opening is not; a drafted opening keeps it until the next `BEGIN` |
| U12 | `aTornEntryIsFreedAndNeverServes` | the state byte last; torn states set by hand: an entry with no piece, a piece whose status byte is not written; the format reads 4 again after the reclaim |
| U13 | `setCardLetsTheTableGoAndTakesOnlyUnitsItKnows` | after it every entry is free and the last unit `00`; a drafted opening let go first; the receipts' units and the other unit's count stay; unit `02` is `6A80`, nothing written; the rate and every limit stay |
| U14 | `resetCardZerosTheBookAndTheRate` | the book zeros, the same array, not made twice; the rate zeros with the record, the clock kept |
| U15 | `aPaymentOfBothUnitsIs6A9A` | both units in one `BEGIN`; two keysets of one unit go through and sign once; a place whose entry is zeroed by hand is `6A9A`, first or not, and is never charged as the record's unit |
| U16 | `theCentsOfASatsPaymentAreRoundedUp` | `cents` of 1 sat at R = 1,000 is 1; of 1,000 is 100; of 1,001 is 101; at R = 1 and at R = 2^32 − 1; of 2^32 − 1 sats at R = 100 is 2^32 − 1; at R = 99 it is past four bytes and taken as `FF FF FF FF` |
| U17 | `theConversionAgreesWithBigIntegerEverywhere` | thousands of random S and R, and every edge, against `ceil(S × 100 / R)` worked in `BigInteger`, saturated at four bytes: never one cent under |
| U18 | `aSatsPaymentWithALimitAndNoRateIs6A99` | at the first `SIGN`, before the PIN gate; nothing signed, burned, counted or logged; the payment given up (`6985` after); with no limit set it signs; a dollar payment never asks for a rate |
| U19 | `theWaitIsWorkedOutInCents` | LIMIT 6a's formula with S and L in cents: a sats payment worth the limit waits nothing, one worth a cent over the tolerance waits ten, each further limit's worth four; the L/32 tolerance in cents; a second payment in a tap slowed across units |
| U20 | `theAllowanceCountsBothUnitsInCents` | a sats payment and a dollar payment with no PIN add up in cents, as the whole of their pieces; `6A94` past it; `VERIFY_PIN` renews it |
| U21 | `theDayCountsBothUnitsInCents` | a daily limit set: sats and dollar payments counted in one spent-today, `6A8F` over it, written in the log with the payment's unit; the window as 1.22's |
| U22 | `setLimitTakesTheRateWithTheFigures` | sixteen bytes (`34`) and twelve (`33`); the proof binds the rate (a proof for one rate is none for another, and none for twelve bytes); a rate alone changed begins no window and keeps every count; seven writes at most |
| U23 | `theOldLengthsAreTakenOnlyOnADollarsRecord` | 4, 8 and 12 bytes (`34`), 4 and 8 (`33`): taken on a dollars record as 1.22 takes them, the rate untouched; `6700` on a sats record and before the first `SET_CARD`, nothing written; every other length `6700` |
| U24 | `theRateIsTheHoldersToRead` | `GET_INFO` P1 = 3 bytes 44 to 47 with the grant, and on a card with no owner to the PIN in a held tap; zeros otherwise; `GET_CARD` P1 = 0's 29 bytes stay zeros with a rate set |
| U25 | `getInfoThreeSaysTheBook` | every byte of the 51; P1 = 0, 1 and 2 as 1.22 but the version and byte 8 |
| U26 | `theFormatIsFourForOneUnitAndFiveForBoth` | 4 on a new card and after loads of the record's unit, marked or not; 5 at the first entry of the other unit; 5 on a dollars card that holds sats; 4 again after the reclaim; `GET_CARD` byte 0 the same |
| U27 | `getBalanceZeroIsTheRecordsUnitAndOneIsEachUnit` | on one unit P1 = 0 is 1.22's; on both, the record's only; P1 = 1's layout; the wrap; a place of no entry in no sum |
| U28 | `aLongHistoryKeepsEveryPieceInItsUnit` | random loads under every authority, payments in both units with change, refunds, clears, `SET_CARD`, new rates, power-ups: after every step every unspent place's keyset is in an entry of its unit, and the counts add up, in cents and in each unit |
| U29 | `theLogSaysEachEntrysUnit` | bit 5; a tap of both units makes two entries; a card of one unit has bit 5 at 0; P1 = 2's counts; P1 = 3's receipts and units; a refusal in an entry of its unit; with no owner, P1 = 2 and 3 counted toward the held tap as 0 and 1, and `6A86` for P1 = 4 counting nothing |
| U30 | `getCardTwoIsTheTable` | the count and every entry in use, in order; 55 bytes at the most; P1 = 1 answers as P1 = 0 |
| U31 | `onlyTheseMethodsNameTheBook` | a source scan, as the proofs' store has: `processSetCard`, `processResetCard`, `processLoadProof`, `loadOne`, `processLoadChange`, the reclaim, `processSpendAllBegin`, `processSpendAllSign`, and the readers of `GET_INFO`, `GET_CARD`, `GET_BALANCE` and `GET_LOG`; no other |
| U32 | `theTransactionsGrowByWhatTheDesignSays` | the source readings that count what each transaction writes: `SIGN`'s and the receipt's one byte more, `writeLimit`'s one copy more, nothing in `SET_CARD`'s |
| U33 | `aOneUnitCardAnswersTheTwentyTwoTranscript` | `spec/vectors/transcript.json`'s 1.22 commands replayed on a 1.23 card in sats: every answer equal but the version, and the old-length `SET_LIMIT`s, `6700` (U23); on a card in dollars, those too. Goal 5, pinned whole |
| U34 | `theCentsAreWorkedInScratchAndAskNoRam` | a source scan: the conversion names `scratch` at offsets inside `X_HEX` only, and nothing else is written there between the conversion and its use; the RAM budget test stays at 941 and 7 |

Changed, keeping what each pins:

- `SchnorrHWMathTest`: the constructors stay at 30,069; `ONCE_ONLY_ALLOCATIONS`
  gains the book, and `theOnceOnlyAllocationIsNarrow` finds three; RAM stays at 941.
- `CashuAppletTest`'s helpers (`setLimitCommand`, `setLimitsCommand`,
  `setLimitByPinCommand`, `setLimit`, `setLimits`) send the new forms with a rate
  of **100** sats per dollar. At R = 100, `ceil(S × 100 / 100) = S`: every figure a
  1.22 test wrote in sats is the same number of cents, and every limit test keeps its
  figures and its assertions. The tests that pin the old lengths themselves
  (`testSetLimitTakesTheAllowance` and its like) are rewritten to U23's rule.
- The source scans that slice by method (`testSpendIsOneTransaction`, from
  `requireUnderLimits(` to `waitsFor(`; the `waitsFor` and `GET_INFO` scans; from
  `processSetCard(` to `processSetLimit(`; from `processSetLimitOwner(` to
  `writeLimit(`): new methods go after `dayIsOver`, or the slices are anchored on the
  next method by name, each keeping what it pins.
- The test that walks every command through every state gains the labelled load and
  the new `SET_LIMIT` lengths.

### 10.7 At the chip

One sitting, with `tools/chip`. `ProofStoreCheck` gains the book (made by the first
`SET_CARD`, P1 = 3 byte 43 `01`; zeros after `RESET_CARD`). `ChangeCheck` gains a run
of 1.23, blank card to blank card as now, with a keyset id made up for each unit as it
makes up its mint's key:

1. `SELECT` answers `01 17`; `GET_INFO` P1 = 3 is 51 bytes, format 4.
2. `SET_PIN`, `VERIFY_PIN`, `SET_CARD` in sats, the owner's claim; `SET_LIMIT` (`34`)
   of sixteen bytes, a limit on one payment of 100 cents, an allowance of 200, R =
   1,000; P1 = 3 with the grant reads the rate back; `GET_CARD` answers it as zeros.
3. A new tap, the PIN: a plain load of sats (`40`); a load labelled dollars, `6A9D`;
   with the grant as well, taken (`C1`); format 5; `GET_CARD` P1 = 2 lists both.
4. A payment of a sats piece and a dollar piece together: `6A9A`.
5. A new tap, no PIN: a sats payment of pieces of 2,000 sats with change, 200 cents,
   the whole allowance, signed; a second of 10 sats in a new tap: `6A94`. Timed: the first and
   the last `SIGN`, against 1.22's, for the conversion's cost.
6. A sats payment of 1,500 sats under the PIN: 150 cents, a limit and a half, ten
   waits counted to the asking.
7. A dollar payment with change: the change by `LOAD_CHANGE`; the entry stays live
   while the opening is owed.
8. `SET_LIMIT` with R = 0, a sats payment: `6A99`; a dollar payment signs.
9. The dollars paid off, `CLEAR_SPENT`: the reclaim frees the dollar entry, format 4.
10. `SET_CARD` again on the empty card; `RESET_CARD`; blank.

Each transaction of 7.4's table is measured in it, with the reclaim's time over a
full card and `GET_BALANCE` P1 = 1's; and the install-size work of 12.2.

### 10.8 The transcript vectors

`spec/vectors/transcript.json` gains, as commands and answers: `SELECT` `01 17`;
`GET_INFO` P1 = 3 on a new card, on a card of both units, without and with the grant;
`GET_CARD` P1 = 0 with a rate set, and P1 = 2; a plain load and a labelled one; a
labelled load refused `6A9B`, `6A9C` and `6A9D`, and one with P2 = 2 refused `6B00`;
`SPEND_ALL_BEGIN` refused `6A9A`; a sats payment held to a ceiling in cents, with the
counts read after it in P1 = 3; `SPEND_ALL_SIGN` refused `6A99`; `SET_LIMIT` of
sixteen bytes (`34`) and of twelve (`33`), and the owner's old twelve on a sats
record refused `6700`;
`GET_BALANCE` P1 = 1; `GET_LOG` P1 = 2 and 3. `spec/vectors/owner.json` gains a proof
over a sixteen-byte `set-limit`.

### 10.9 Order of the work

1. **Applet 1.23 and `UnitsTest`**, the transcript and the vectors, with the sitting
   at the chip (10.7, 12.2). A 1.23 card under a phone of today is safe from the first
   build: format 4 is used as 1.22, format 5 is refused, and a sats ceiling cannot be
   written in sats (10.2).
2. **The model of the card** (10.5), from a 1.23 recording. It cannot come first:
   `tests/flashcard-model.js` holds it to a recording of the applet.
3. **The phone** (9.1 to 9.8). It asks a card for nothing new unless the card says
   `01 17`, so it can ship before any 1.23 card exists. The format comparisons of 9.1
   go in first, in the same change as the reading of format 5.
4. **The cards in hand** installed again (10.1), once the phone that knows 1.23 is
   the owner's.

## 11. Threats

What the table, the second unit and the rate change in `SECURITY-MODEL.md` and
LIMIT 13. Numbered to follow the threat table's 16.

| # | Threat | Closed by |
|---|---|---|
| 17 | **A ceiling walked round with the other unit.** The owner sets $20 on one payment; a terminal that has the PIN takes the sats | One ceiling, in cents, for both units (6.1): a sats payment is weighed in cents at the rate before it is held to it. The worst a terminal takes is what one ceiling allows, whichever unit it takes, and a thief with the card has one allowance, not one a unit |
| 18 | **A payment charged as the wrong unit**, by mixing pieces or by a piece put in the wrong unit | `SPEND_ALL_BEGIN` refuses both units (`6A9A`). A keyset is in one entry and is never moved (3.4). A terminal can label a piece of its own wrongly (its own money, which the mint refuses there, and which the phone counts nowhere), and never a piece of the holder's |
| 19 | **A piece taken to the wrong mint** | One mint, the record's, changed only on an empty card owed nothing (4.1). The phone keeps a piece only where that mint lists its keyset in the unit the table names, and checks its DLEQ with that mint's keys of that unit |
| 20 | **A keyset entered by a reader that is not the holder's**: a till that has the PIN (nobody loads a card with no PIN, since 1.21) | It enters only the record's unit or the last payment's (3.3), at most two entries live at once. It can load pieces of its own, as LIMIT 13 says: a nuisance, and a keyset it squats on answers the owner's later load `6A9B` until they are gone. The owner's phone frees it as WITHDRAW does: the ceiling lifted with the owner's proof (a squatter's piece can say any amount), a payment of those places under the PIN (the mint refuses it; the card burns them), the ceiling put back, `CLEAR_SPENT`. It cannot start the other unit on a card that has not just paid in it, so it cannot turn a card of one unit to format 5 |
| 21 | **Two units under one SIG_ALL** | `6A9A` before anything is hashed. The card's own change is made in the payment's keyset, so it is the payment's unit |
| 22 | **A phone of today reading cents as sats** | in a piece: the format is 5 for a card of both units, and a phone of today refuses it at the read (10.2). In a figure: on a sats card of 1.23 it reads the ceiling's cents as sats on its screen and in its guess at the PIN, which the card overrules; it cannot write a figure in sats there (`6700`) |
| 23 | **A figure in cents taken as sats by a card that does not know units** | the sixteen- and twelve-byte `SET_LIMIT` and the labelled `LOAD_PROOF` are lengths a card of 1.22 refuses (`6700`) |
| 24 | **A reader that adds a card's balance**, an upstream terminal | `GET_BALANCE` P1 = 0 is the record's unit alone; no command of the card adds two units |
| 25 | **A stale rate.** The price moves after the owner's phone wrote R, and a sats payment is weighed at the old price | The owner's phone re-pegs R at every read as owner where its price has moved more than 2% (6.5). Between reads, the ceiling for sats moves with the price as a 1.22 sats limit's worth in dollars always has. A card with no owner keeps its rate while it holds money, as it keeps its limits; it holds a sats payment to the same number of sats a 1.22 card with no owner would. A card with a ceiling and no rate fails closed: `6A99` for every sats payment, never a payment unbounded |
| 26 | **A wrong rate written by the owner's own phone**: a bad price, or a bug | It costs only sats payments, and in proportion: a rate ten times too many sats per dollar weighs a sats payment at a tenth of its worth, so the limit on one payment and the allowance let ten times their figure of sats go before the next correct re-peg; dollar payments are untouched. A rate ten times too few is ten times stricter: sats payments wait or ask the PIN, and nothing is lost. The phone writes R only from the price SET AMOUNT uses, rounds it toward the stricter, and asks the owner before writing a rate more than twice or less than half the one on the card (9.1). The card cannot judge a price, and does not try |
| 27 | **A rate written by a till** | It cannot: the rate is written only with the owner's proof (`34`), or, on a card with no owner, under the PIN while the card is empty and owed nothing and, once used, in a held tap (`33`, LIMIT 8.4). A till with the PIN cannot change the rate of a card that holds money, as it cannot change its limits |
| 28 | **The conversion's arithmetic** | Five bytes for every product a four-byte amount can make, rounding up, and a quotient past four bytes taken as over every ceiling (6.2). U16 and U17 pin it against `BigInteger` |

What it does not change: the card checks no piece and knows no market; a terminal
that has the PIN takes up to the ceiling; the receiver always asks the mint.

## 12. Size, and what shipped first

### 12.1 The applet

| Area | Lines of applet code |
|---|---|
| the book: constants, the reservation, `SET_CARD`'s reclaim and unit check, `RESET_CARD`'s zeroing | 30 |
| the table: lookup, the labelled load (the label, and the one-byte shift in `loadOne` and `logLoaded`), the gate, the cap, the order of writes | 90 |
| the reclaim, at `CLEAR_SPENT`, at a full table or the cap, and at `SET_CARD` | 35 |
| `SPEND_ALL_BEGIN`'s one unit; `SIGN`'s unit and the last unit | 25 |
| the ceiling: the conversion, `6A99`, the cents handed to the allowance, the day and the wait | 55 |
| `SET_LIMIT`: the sixteen- and twelve-byte forms, the old lengths' rule, the rate in `writeLimit` | 25 |
| `GET_INFO` P1 = 3, the format byte, `GET_CARD` P1 = 2 and the rate masked, `GET_BALANCE` P1 = 1, `GET_LOG` P1 = 2 and 3 | 80 |
| the log's bit, the other unit's count, the receipts' units | 30 |
| **in all** | **about 370**, and as many lines of comment in this file's way |

At the 4.3 to 5.9 bytes of load file a line of code that the earlier draft's estimate
came to, that is about 1,600 to 2,200 bytes: a load file of about 23,400 to 24,100
bytes.

### 12.2 The install-size window

Whether the card's applet installs turns on the load file's size, by a rule nobody
has found (`FOXY-CARD-HARDWARE.md`). The record so far: 1.19 installed at 19,269
bytes (69 past a multiple of 256), 1.20 at 19,634 (178 past), 1.21 at 21,109 (117
past); **1.22 was refused at 21,740 bytes (236 past) and installed at 21,830 (70
past)**, with fifteen lines of `sizeTheFile` and nothing else changed. No size past
21,830 has been tried, so `tools/refcheck.py` will say "not tried" of 1.23's.

The work: build, read the size, and try it on the chip with
`~/.foxycard/install-try.sh`. Where it fails, move the build a line of `sizeTheFile`
at a time (six bytes a line; up to 43 lines cover a window of 256; lines may be taken
away as well as added) and try again until one installs. Every answer, the failures
too, goes into `SIZES` in `tools/refcheck.py`, and `sizeTheFile`'s comment says which
size failed and which installed, as 1.22's does. 1.23 ships at a size that installed.
The sitting is planned for it, with the measures of 10.7.

### 12.3 Days

| Work | Days |
|---|---|
| applet | 3 |
| JUnit (U1 to U34, about 2,600 lines), the helpers and source scans rewritten (10.6), the transcript and the vectors | 3 |
| at the chip: the install-size window, 10.7's run and measures, `ProofStoreCheck`'s book | 1 to 2 |
| the model of the card and its replay | 1 |
| the phone's wallet (`08a`, `21a`, native): about 700 lines | 3 |
| the screens (`26f`): two pills, SELECT CURRENCY, SWAP, the ceiling for both, the words: about 400 lines, and their snapshots | 2 |
| the phone's suites, and the live one at a two-unit mint with the applet as the card | 2 |
| **in all** | **15 to 16 working days**, about three weeks |

### 12.4 What shipped first

The dollars card on the cards there are (a record of `01`, dollar pieces at the mint,
limits in cents typed in dollars and never re-pegged, paid by a dollar till) needed no
new card software, and has shipped in the phone. It is a card of one unit, format 4,
and a 1.23 card set up in dollars is the same card to that phone (10.2). 1.23 is next.

## 13. Decided

The owner's answers to the ten questions of the earlier draft, in the owner's words,
and what each meant for this design:

1. **One ceiling or two.** "ok on limit across units". One ceiling, in cents, for
   both units, with a rate the owner's phone writes and the card converting sats at
   each payment (6).
2. **Three mints and sixteen entries.** "yes both units at one mint, card supports
   one mint for now". One mint, the record's; no mint list, no `SET_MINT`; six
   entries; the three mints kept as history with the door marked (4).
3. **Who loads a card with no PIN.** "confirmed". Nobody, since 1.21 (`6A97`); every
   load is the PIN's, and a new keyset of the other unit wants the holder's say as
   well (3.3).
4. **The format byte by what the card holds.** "A sounds fine". 4 for a card of one
   unit, 5 for one of both, worked out at each asking, so a phone of today keeps
   using a card of one unit (4.3).
5. **A dollar request from a card of both.** "always ask". The card never chooses;
   the phone asks the holder every time both units could pay (7.1, 9.7).
6. **A change of the record's unit.** "got answer?". Moot once the first was
   decided: every limit is in cents whatever the record's unit, so a change of unit
   changes no limit's meaning, and nothing is refused for it (6.7).
7. **The 128 places between two units.** "what you recommend". Four of every size,
   1 to 1,024, in each unit on a card of both; eight on a card of one (9.2).
8. **The screen of a card at several mints.** "yes both units at one mint, card
   supports one mint for now". One mint on the screen, named over its pills, and no
   rows of other mints (9.5).
9. **The cards in hand.** "reintall to 1.23 is fine". Each is emptied by the phone,
   installed again as 1.23 with a new key, set up and loaded; no migration on the
   card (10.1).
10. **What ships first.** "ok cool". The dollars card on 1.20 has shipped in the
    phone; 1.23 is next (12.4).

**Still open.** Nothing the owner has to answer before 1.23 is built. Two things are
the chip's to answer, in the one sitting: the size at which 1.23 installs (12.2), and
the time the conversion adds to a sats payment's first and last `SIGN` (10.7, step 5).
