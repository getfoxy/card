# Foxy card: more than one balance (the keyset table)

A card that holds dollars and sats at one mint, and money at more than one
mint, and the one change to the card's software that both need: a table, kept
on the card, of which mint and which unit each keyset it holds is.

This is a design. Nothing in it is built. It is written against software 1.20
(`SELECT` answers `01 14`), `FOXY-CARD-SPEC.md`, `FOXY-CARD-DAILY-LIMIT.md`
(LIMIT below), `spec/APDU.md`, `SECURITY-MODEL.md` and the applet as it is in
`CashuApplet.java`, and against the phone as it is at Foxy `770db86`
(`build/wallet/08a-flashcard.js`, `08c-units.js`, `21a-flashcard.js`,
`build/app/26f-flashcard.js`). The software it describes is 1.21.

## 0. Why

The owner asked two things: can a card hold two balances (dollars and
bitcoin at a mint like Forge), and what would it take to hold money at more
than one mint. Both come down to one thing the card does not know today.

What the card knows now:

- **One unit.** The record has a unit byte (offset 1, `SET_CARD`'s first data
  byte). The card stores it, answers it in `GET_CARD`, and uses it for nothing.
  The phone sends `00` and reads `00` as sats and anything else as "other",
  which it refuses (`cardUnusable`: "That card holds something other than
  sats.").
- **One mint.** The record holds one address of up to 77 characters (offset 39,
  length; 40, the text). 77 and not more because `SET_CARD` under the owner's
  proof has to fit a short APDU: 1 + 72 + 100 + 77 + 3 = 253.
- **A keyset for each piece, which it never reads.** Every slot carries the
  piece's keyset id, eight raw bytes (slot offset 1). The card hashes it into
  nothing, compares it with nothing, and takes any keyset at load. Only the
  change the card makes for itself copies it, from the payment's first piece
  into the opening (`OPEN_KEYSET`).
- **One sum.** `GET_BALANCE` adds every unspent piece.
- **One set of limits.** The daily limit, the limit on one payment and the
  no-PIN allowance are each one number, written as sats, and every payment is
  charged against them.

And what the phone does today with a keyset it reads off a card: it maps the
eight bytes to a full id among **every** keyset of its mint (`cardFullId`, by
`keysetIdsFor`, which does not look at the unit). So a phone of today shown a
card that held dollar pieces at its own mint would count cents as sats. Nothing
puts dollar pieces on a card today, so nothing has gone wrong. It is the first
thing this design has to close (10.2).

So the card can hold money of any keyset already. What it cannot do is say
which money is which, keep two kinds apart in a payment, or bound each kind by
a limit in its own unit. Each of those needs the same fact: **for each keyset,
which mint and which unit.** That fact is the table. Two balances at one mint
and money at more than one mint both ride on it; nothing else in this design
is more than what follows from it.

A card of 1.20 keeps working. A card with an empty table is a 1.20 card: its
pieces are of the record's unit at the record's mint.

## 1. Words

| Word | Means |
|---|---|
| **balance** | one mint and one unit: "dollars at Forge", "sats at Minibits". A card holds at most six (three mints, two units) |
| **unit code** | `00` sats, `01` dollars (cents). The card takes no other. A third unit is a design of its own |
| **unit A** | the record's unit: what the unit byte at record offset 1 says. Every limit in the record (offsets 2, 189 to 208) is in unit A, as it always was |
| **unit B** | the other code: `01` on a card whose record says `00`, and `00` on one whose record says `01` |
| **mint index** | 0, 1 or 2. Mint 0 is the record's mint, as it always was. Mints 1 and 2 are new (section 4) |
| **the book** | the store this design adds: the table, mints 1 and 2, unit B's limits and a few counts (3.2) |
| **an entry** | one row of the table: a keyset and its balance |
| **live** | an entry is live while an unspent slot, or a change opening that is drafted or pending, names its keyset |

## 2. Goals

1. A card holds dollars and sats at one mint, and money at up to three mints,
   and every piece on it is known to be of one balance.
2. No payment mixes two balances. A payment is charged to the limits of its own
   unit and nothing else.
3. The owner sets each unit's limits, in that unit.
4. A card of 1.20 under a new phone, and a new card under a phone of today, each
   work as far as they can and refuse plainly past that. A phone of today never
   reads cents as sats.
5. A card that holds one balance behaves as a 1.20 card, to the byte, but for
   the version.

Not goals: a third unit; a limit across units in one figure (13, decision 1); a
swap on the card (the card cannot reach a mint; a swap is the phone's, 9.5); a
payment that converts on the card.

## 3. The table

### 3.1 An entry

Ten bytes:

| Offset | Size | Field |
|---|---|---|
| 0 | 8 | the keyset id as the card holds it in a piece: the slot's bytes 1 to 8, raw (`spec/APDU.md`: a v1 id's eight bytes; a v2 id's first eight, as `cardShortId` cuts it) |
| 8 | 1 | the mint byte: `00` is a free entry; otherwise `0x40` with the mint index in bits 0 and 1, and `0x80` added where the entry was written under the owner's grant |
| 9 | 1 | the unit code: `00` or `01` |

So `40 00` is sats at mint 0 (what every piece of a 1.20 card is), `41 01` is
dollars at mint 1, and `C0 01` is dollars at mint 0 entered by the owner. A new
book is zeros, and zeros are free entries, so nothing has to be written to make
a table empty. The owner's mark (`0x80`) is there for one rule only, the cap on
entries made by anyone else (3.3).

Where a balance is said in one byte (the last payment's balance, the receipts,
the answers' "packed" fields), it is `0x80 | B << 6 | index << 4`, where `B` is
1 for unit B and 0 for unit A: `80` is `0, A`, `C0` is `0, B`, `90` and `D0` are
mint 1's, `A0` and `E0` mint 2's, and `00` is none. Bits 4 to 6 are the same
bits, in the same meaning, as a log entry's (6.5), so one reading serves both:
bits 4 and 5 the mint index, bit 6 unit B. Bit 7 says a balance is there, and
the log, which has to stay 1.20's on a card of one balance, never sets it. The
unit is relative to the record's in both, as 1.20's one count has always been:
the phone resolves A and B to a code with the record's unit byte.

The entry's mint byte and the packed balance put the index in different bits:
the entry `0x40 | index` (bits 0 and 1) with the unit as a code in its own byte,
the packed `index << 4` with the unit as A or B. One helper turns an entry into
its packed balance, and one turns a label (index, code) into it. No other code
shifts the index or compares a code with the record's unit.

### 3.2 How many, and where they live

**Sixteen entries.** A balance needs one entry for the keyset its mint issues
now, and one more for a keyset the mint has retired while pieces of it are
still on the card: two. Six balances are twelve. Four more are the room that
entries made by somebody other than the owner may take (3.3), so that a
stranger can never fill what the owner needs: sixteen. 160 bytes.

**The book.** One array of 361 bytes:

| Offset | Size | Field |
|---|---|---|
| 0 | 160 | the table, sixteen entries of ten |
| 160 | 78 | mint 1: length (1, 0 for none), address (77) |
| 238 | 78 | mint 2: the same |
| 316 | 4 | unit B: the daily limit, big-endian; 0 is no limit |
| 320 | 4 | unit B: the window start |
| 324 | 4 | unit B: spent today |
| 328 | 4 | unit B: the limit on one payment |
| 332 | 4 | unit B: the no-PIN allowance |
| 336 | 4 | unit B: signed for with no PIN in the window |
| 340 | 1 | the last payment's balance, packed (7.4) |
| 341 | 4 | unit B signed for, ever (the log's count of unit A stays where it is: 6.5) |
| 345 | 16 | the receipts' balances, packed, one beside each of the sixteen receipts (6.5) |

The five fields after unit B's daily limit (320 to 339) are in the order of the
record's 189 to 208 (window, spent, limit on one payment, allowance, no-PIN
count), so one base offset serves each unit: field k is at base + 4 × k, base
189 in the record for unit A and 320 in the book for unit B (6.3).

**The arithmetic, against what the chip has.** `FOXY-CARD-HARDWARE.md` gives
three budgets, and the book is held to each:

| Budget | What was measured | The book |
|---|---|---|
| storage | a clean card has 164,176 bytes free; 1.7 takes 45,096; 1.20's first `SET_CARD` takes 12,288 more for the mint's proofs, leaving 106,792 | 361 bytes, and a little more of package for the code (about 2.5 KB, 12): about 104,000 stay free. Storage is not what binds |
| a constructor's allocation | the constructors make 29,806 bytes of arrays; a build that asked for 300 more in the constructor installed, one that asked for 12,288 more did not | 361 is more than any figure measured to install. So the book is **not** made in the constructor. It is made where the store of the mint's proofs is made: by the first `SET_CARD`, once, outside any transaction, guarded (3.6). The constructors stay at 29,806, and the test that pins that figure stays as it is |
| memory cleared on deselect | 941 bytes installs, 1,030 does not, 942 untried | **nothing new.** The payment's balance is worked out again at `SPEND_ALL_SIGN` from its first place (7.1); the label of a load is read from the APDU buffer; the marks of the reclaim (3.4) are a short local, sixteen bits; the unit's array and offsets are parameters (6.3) |

**The install-size window.** Whether the card's applet installs turns on the
load file's size, by a rule nobody has found (`FOXY-CARD-HARDWARE.md`): 1.20
installed at 19,634 bytes (178 past a multiple of 256). This design adds code,
about 575 lines (12), which at the six bytes a line `sizeTheFile` assumes is
some 2,500 to 3,400 bytes of file: a size between 22,100 and 23,100 bytes,
where nothing has been tried. `tools/refcheck.py` will say "not tried". The
build is then moved a line of `sizeTheFile` at a time (six bytes each, up to 43
lines to cover a window of 256) and tried on a chip with `install-try.sh` until
one installs, and that size goes into `SIZES`. One sitting at the chip is
planned for it (12).

### 3.3 When an entry is written

**At `LOAD_PROOF`, and nowhere else**, for a piece whose keyset is in no entry.
A piece whose keyset is in an entry is checked against it and writes nothing to
the table.

`LOAD_PROOF` gains a labelled form. Why a label at all: a 1.20 phone loads with
no label and must go on being able to, and a label is the only way the card can
be told a balance it cannot work out. The card cannot work it out because a
keyset id says nothing about its mint or its unit until a mint is asked, and a
card cannot ask a mint.

| Form | P1 | P2 | Data | Lengths |
|---|---|---|---|---|
| plain, as 1.20 | `00` | `00` | one to three pieces of 81 | 81, 162, 243 |
| with the mint's proof, as 1.20 | `01` | `00` | one piece and its proof | 177 |
| **labelled** (1.21) | `00` | `01` | mint index (1), unit (1), then one to three pieces of 81 | 83, 164, 245 |
| **labelled, with the proof** (1.21) | `01` | `01` | mint index (1), unit (1), then one piece and its proof | 179 |

With P2 = 1 the label is the first two data bytes, and everything after it is
read as 1.20 reads its data, two bytes on: piece k at `OFFSET_CDATA + 2 + 81 ×
k`, the proof at `OFFSET_CDATA + 2 + 81`, and the log's "put on" summed from
each piece's amount at the same shift (`OFFSET_CDATA + 2 + 81 × k + 8`). The
answer is as 1.20's: the places, or the place and the proof flag. P2 = 0 is
unlabelled. Any other P2 is `6B00`, checked with the length, before anything is
written. The label's checks (1 and 2 below) are made once for the command,
before its first piece.

Every piece of a labelled command is of the label's balance. An unlabelled piece
is labelled `0, A`: sats at mint 0 on a sats card, which is what a 1.20 card
assumes of every piece. A card of 1.20 does not look at P2, and answers every
labelled length `6700`: a new phone that sent a label to an old card by mistake
is refused, and nothing is written (10.1).

**Who may enter a keyset, and as which balance.** The load itself is let
through as it is today (`requireLoadAuthority`: the PIN verified in this tap,
the owner's grant, the tap after a payment, or anyone on a card with no PIN).
Entering a keyset is held tighter, because a wrong entry would put money in the
wrong balance for as long as a piece of that keyset is on the card:

| In this tap | as `0, A` | as the last payment's balance (7.4) | as any other balance |
|---|---|---|---|
| the owner's grant (`ALLOW_LOAD`) | yes, marked the owner's | yes, marked | yes, marked |
| the PIN verified | yes | yes | `6A9A` |
| the tap after a payment (the change grant) | yes | yes | `6A9A` |
| a card with no PIN, anyone who holds it | yes | yes | `6A9A` |

Under the owner's grant every entry is marked, a plain load's `0, A` too: it
enters as `C0 00` on a sats card. Under the PIN, the change grant or no PIN it
enters as `40 00`.

`6A9A` and not `6A91`: the phone reads `6A91` as `not-owner` ("This phone does
not hold the seed phrase this card was set up with"), and `cardGrant` as "this
phone is not the owner". Neither is what happened here, which is a load the tap
may make but a balance it may not start.

Why those two columns are open to a till. `0, A` is what any loader of a 1.20
card does today, so closing it would break every till of today. The last
payment's balance is where a till's change and refunds go: a payment of dollars
at mint 1 makes change in dollars at mint 1, and a mint that has moved to a new
keyset since the card's pieces were made gives that change in the new keyset.
Anything else is the owner's: a balance the card has not paid from is a balance
only the owner has any business starting.

**The cap.** At most four live entries may lack the owner's mark. A fifth is
`6A99`, as a full table is. A stranger with a no-PIN card in hand, or a till
with the PIN, can load pieces of four keysets of its own making and no more,
and the twelve the owner needs stay free.

**What is checked, in order.** The checks of a load are those of 1.20 in their
order (`6982` the gate; `6A90` no owner; `6A8C` no record; `6A84` a full card;
`6700` the length; then for each piece `6A8E` a date with no refund key, `6A80`
not a piece), and then, new, for each piece before its nonce is looked at:

1. A label whose mint index is over 2 or whose unit is over `01`: `6A80`.
   Then, on a card with no book, a label other than `0, A`: `6A99`, before
   check 2. So a card refused its book answers every such load one word.
2. A label that names mint 1 or 2 with no address on the card: `6A97`.
3. The piece's keyset in an entry of another balance than the label's (the
   label of a plain load being `0, A`): `6A97`. Nothing is written.
4. The keyset in no entry, and the tap may not enter it as this balance (the
   table above): `6A9A`.
5. The keyset in no entry, and either no entry is free, or the new entry would
   lack the owner's mark and four non-free entries already lack it: the reclaim
   runs (3.4), and the counts are taken again. Still no free entry, or still
   four unmarked: `6A99`. So the cap counts live entries, and the 128-place
   pass is paid only where the cheap count says the table may be full, never at
   a load of a keyset already entered.

and then `6A94` for a nonce already on the card. The reclaim in check 5 writes
only entries that nothing names. It is not a write of this piece, and `6A94` is
still the last check before this piece writes anything, as LIMIT section 8 has
it. Several pieces in one
command are taken in order, as now: a refusal of the first is the command's
answer, and a later one ends the command with the places of those before it.

### 3.4 When an entry is let go

- **`RESET_CARD`** zeros the whole book, with the store of the mint's proofs,
  after the places are freed and outside the transaction (as 1.20 does that
  store).
- **`SET_CARD`** zeros the table, mints 1 and 2, the last payment's balance and
  the receipts' balances with `Util.arrayFillNonAtomic`, after every check has
  passed and before the record's transaction, not in it. 333 bytes would
  overrun a commit buffer that holds about a dozen writes
  (`FOXY-CARD-HARDWARE.md`), and the transaction already logs about 115 (unit
  1, refund key 33, mint length 1, mint up to 77, design 3, set 1). jCardSim has
  no such limit, so a build that zeroed the book atomically would pass every
  test and throw `TransactionException` on the chip. The order is: the sixteen
  mint bytes first (each entry is then free), then the rest of the table, then
  each mint's length byte, then its address, then the last balance and the
  receipts' balances. A tear leaves the old record over a book that is empty or
  part empty. Nothing unspent and nothing owed names an entry (the two `6A8D`
  below are checked before it), so nothing is lost, and a part-zeroed entry
  whose mint byte went first is free. A mint zeroed under an old record is set
  again by `SET_MINT`.
- `SET_CARD` is let only on a card with nothing unspent, as it always was. On a
  1.21 card it also answers `6A8D` while any opening is PENDING (change owed to
  the card), because `requireNothingUnspent` looks only at the 128 status bytes:
  after a payment of every piece with the card's own change, the card holds
  nothing unspent and still waits for a piece whose entry the opening keeps
  live. A DRAFT opening is let go first (set EMPTY), as `BEGIN` lets it go. So
  no entry an opening names is ever zeroed. A phone of today already refuses to
  set up a card that is owed change (`change-owed`), and reads `6A8D` as
  `in-use` where it does not.
- Unit B's limits are a limit, and `SET_CARD` never touches a limit: they stay
  (but see 6.6 for a change of unit).
- **A reclaim** lets go every entry that is not live. It runs at `CLEAR_SPENT`,
  after the spent places are freed; at a load of a new keyset where the table
  may be full or the cap reached (3.3, check 5); and at `SET_MINT`, before its
  `6A8D` is looked at (4.2). It is one pass over the 128 places (each unspent
  place marks its entry, its own keyset compared with the one before it first,
  so that a drawer of one keyset costs one comparison a place) and one over the
  eight openings that are DRAFT or PENDING, then every entry left unmarked is
  set free by writing `00` to its mint byte. The marks are sixteen bits in a
  short local, bit i for entry i: no scratch and no RAM array, so the memory
  cleared on deselect stays at 941, and nothing in `LOAD_PROOF`'s scratch
  (`X_HEX`, `X_NUM`, `X_SUM`) is touched between its pieces. A reclaim torn
  halfway has freed some dead entries and not others, and the next one
  finishes. A live entry is never freed, because the marks are made from what
  is on the card at the moment.

An entry is never changed in place. A keyset that is entered stays as that
balance until it is dead and reclaimed, so no command can move a piece from
one balance to another.

### 3.5 A piece whose keyset is in no entry

There is none. Every load enters the piece's keyset or is refused (3.3), the
card's own change is written back in the keyset of its opening, whose entry the
opening keeps live (3.4), and nothing frees an entry a live piece names. A test
runs a long random history and checks after every step that every unspent
place's keyset is in an entry of the balance it was loaded as (10.5, J28).

Should a lookup on a card with a book find no entry all the same (a bug), the
place is of **no balance**, the same answer in every command that asks:
`SPEND_ALL_BEGIN` and `SPEND_ALL_SIGN` refuse it `6A98` and never fall back to
`0, A` (a fallback would charge cents to the sats' day, the one thing the
design exists to stop); `GET_BALANCE` counts it in no sum; the reclaim marks
nothing for it. The phone counts it nowhere. J28 fails on it, so it never
ships.

The one card that has no table is a card that was refused the room for the book
(3.6). It is a 1.20 card in every way: every piece is `0, A`, every load is
taken as a plain one, a labelled load of any other balance is `6A99` (no room,
before any other check of the label: 3.3), and every new command answers as a
1.20 card's would.

### 3.6 Order of writes, and the book's reservation

`loadOne` writes, for a new keyset, the entry's keyset and unit and then its
mint byte, last, as one byte: the mint byte is the entry's commit, as the
status byte is a slot's (D14). This is before the slot's hex text, its proof
record, its data and its status byte. A card taken away in between holds an
entry that nothing names, which the next reclaim frees.

The book is reserved by the first `SET_CARD` that goes through, after the store
of the mint's proofs, the same way: once, outside any transaction, in a
`try` that leaves it null where the card refuses the room. Every use of the book
is safe while it is null. A later `SET_CARD` asks again. The tests that pin the
one allocation of the proofs' store (that it is the only array the install
leaves out, that it is made in `SET_CARD` and nowhere else) gain the book as the
second, and nothing beside it.

## 4. The mint list

### 4.1 Three mints, and why three

Mint 0 is the record's, at offset 39 and 40, as it is. Mints 1 and 2 are in the
book, 78 bytes each. Three, because:

- the table holds two keysets for each of six balances, and a fourth mint would
  be eight balances and sixteen entries with no room for the cap (3.3);
- `GET_CARD` with P1 = 1 says the list in one answer: 1 + 2 × 79 = 159 bytes for
  two mints past mint 0, and three would be 238, which fits, and four would be
  317, which does not;
- the screens show one mint at a time and list the others (9.4); three is a
  list a person reads at a glance.

Each address is up to 77 characters, as mint 0's, and for the same reason (the
command that writes it carries the owner's proof: 4.2).

### 4.2 SET_MINT (`46`)

Why a command of its own: a mint's address is the owner's to give, as the
record is, and it has to be given before the first piece of that mint goes on.
Putting it in the load would make every load carry 78 bytes and an owner's
proof.

| Field | Value |
|---|---|
| CLA, INS | `B0 46` |
| P1, P2 | `00 00` (anything else `6B00`, before anything else) |
| Data | proof length (1), the owner's proof (DER), then the value: the mint index (1, `01` or `02`), the address's length (1, 0 to 77), the address |
| Proof | ECDSA (P-256, SHA-256, DER) by the owner's key over `FoxyCard/set-mint` (17 bytes, `46 6F 78 79 43 61 72 64 2F 73 65 74 2D 6D 69 6E 74`), the 16-byte nonce the card gave, and the value as sent |

No name in the owner's list is the start of another (`set-mint` beside
`set-limit`, `set-card`, `set-owner`), so a proof for one is none for another.

Refused in this order: `6B00` P1 or P2; `6986` a locked card; `6A90` no owner;
`6A91` the proof (no nonce in this tap, or not the owner's for this label,
nonce and value; no PIN try is spent); `6700` a value of fewer than two bytes,
or whose length byte is over 77 or is not what follows; `6A8C` a card with no
record or no book; `6A80` an index that is not 1 or 2, an address with a byte
outside `20` to `7E`, or an address the same, byte for byte, as another mint's
on the card (one mint under two indexes would split one mint's money into two
balances, and a keyset can be in only one); `6A8D` an entry of that index that
is live (money of that mint, or change owed to it, is on the card).

Before `6A8D` is looked at, `SET_MINT` runs the reclaim (3.4). An entry of that
index still there after it is live, and is `6A8D`. Otherwise no entry of that
index is left, dead or alive. Why: a dead entry of the old mint's keyset K,
not yet reclaimed, still says mint 1. Were it kept, a later load of K labelled
`1, unit` would pass check 3 with no new entry and go in as the new mint's
money, misfiled by the card. Then, in one transaction, the address and its
length; a length of 0 lets the index go.

No PIN is asked, and none is spent. Any funds are allowed, at other mints:
money at mint 1 is untouched by mint 2's address, and the address of a mint the
card holds money at cannot be changed or taken away while it does.

### 4.3 How a mint is added

With its first keyset, in the owner's ADD FUNDS tap, no PIN:

1. `GET_NONCE`, the phone signs `set-mint` over `01 ‖ length ‖ address`,
   `SET_MINT`.
2. `GET_NONCE`, the phone signs `load`, `ALLOW_LOAD`.
3. `CLEAR_SPENT`, then `LOAD_PROOF` labelled `01, unit`: the first piece enters
   the mint's keyset, marked the owner's, and the rest go in after it.

Two owner's proofs, each 41 ms of the chip's verifier (`FOXY-CARD-HARDWARE.md`),
and a command each. A tap cut short after step 1 leaves an address with no
money, which is harmless and which the next ADD FUNDS finds there. Mint 0 is
changed as now, by `SET_CARD`, only with nothing unspent anywhere on the card.

### 4.4 GET_CARD (`16`)

Why three forms: a phone of today reads the answer to P1 = 0 by its exact
length (`cardRecordOf` refuses any length but `106 + mint` or `109 + mint`), so
anything added to that answer would make it refuse every new card. The list and
the table are asked for by forms of their own, as `GET_INFO` P1 = 1 and P1 = 2
were added beside P1 = 0.

| P1 | Answer |
|---|---|
| `00`, and any but `01` and `02` | 1.20's, to the byte: the card's format (1; 4 or 5: 4.5), record set (1), unit (1), limit (4), refund key (33), the clock's 65 bytes, mint 0's length (1) and address, design (3). At most 186 bytes |
| `01` (1.21) | the mints past mint 0: a count (1), then for each index that has an address, the index (1), the length (1), the address. At most 159 bytes. `00` alone on a card with no book |
| `02` (1.21) | the table: a count (1), then each entry that is not free, its ten bytes as kept (3.1), in the table's order. At most 161 bytes. `00` alone on a card with no book |

No PIN, any state, as P1 = 0. A card of 1.20 does not look at P1 and answers
every form with the record; a phone asks P1 = 1 and 2 only of a card that says
1.21 (10.1).

### 4.5 GET_INFO (`01`), and the card's format

`GET_INFO` with P1 = 0, 1 and 2 answers the 30, 42 and 43 bytes it does, to the
byte but for the version and byte 8. With **P1 = 3** (1.21) it answers 73:

| Bytes | Field |
|---|---|
| 0 to 42 | the 43 bytes of P1 = 2 |
| 43 | the book's format: `01`; `00` on a card that was refused the room for it |
| 44 | unit B's code |
| 45 to 48 | unit B's daily limit |
| 49 to 52 | unit B's window start |
| 53 to 56 | unit B's spent today |
| 57 to 60 | unit B's limit on one payment: with the owner's grant in this tap, and zeros to anyone else, as bytes 30 to 33 are for unit A (LIMIT 6a) |
| 61 to 64 | unit B's no-PIN allowance, to anyone |
| 65 to 68 | unit B's signed with no PIN in its window, to anyone |
| 69 | entries in use |
| 70 | of them, entries without the owner's mark |
| 71 | mints with an address, mint 0 counted |
| 72 | the last payment's balance, packed |

Bytes 45 to 68 are the book's 316 to 339 in one copy, then 57 to 60 zeroed
without the grant: the book keeps unit B's fields in this order for that reason
(3.2).

Any other P1 is answered with the thirty bytes, as it is now. A card of 1.20
answers P1 = 3 with the thirty.

**Byte 8, the card's format.** It was 4 for every card since 1.4, and it says
how a piece's secret is made (`SIG_ALL`). It still means that, and gains one
value:

| Byte 8 | Means | A phone of today |
|---|---|---|
| `04` | every entry in the table is `0, A`, or there is no table: **a card of one balance**. Every piece is of the record's unit at the record's mint, and the secret is format 4's | reads it, pays with it and loads it as a 1.20 card (or, for a record in dollars, refuses it: "That card holds something other than sats.") |
| `05` | some entry is of another balance. The secret is format 4's, and the table has to be read before a piece can be counted | refuses it before anything else is read: "That card is a kind this Foxy does not know." (`cardLook`, which takes 3 and 4 and nothing else) |

`GET_CARD`'s byte 0 says the same. The card works it out at each asking, from
the sixteen mint and unit bytes: 5 if any entry has a mint byte other than `00`
and either its index (mint byte `& 0x03`) is not 0 or its unit byte is not the
record's; else 4. The owner's bit (`0x80`) is not looked at, so `C0 00` is
`0, A`; a free entry (`00`) is skipped; a card with no book is 4. So a card goes
to 5 at its first entry of any balance other than `0, A`, a card that holds
only `0, B` among them (one balance, but not the record's, so a phone of today
would read it in the record's unit), and back to 4 when the last such entry is
reclaimed: an owner who takes the dollars off a card and taps CLEAR has a card a
phone of today can use again. Why a format, and not only a version: a phone of today checks the
format and not the version before it reads a piece as sats, and it is the one
switch that stops it reading cents as sats (10.2). New phones take 4 and 5 as
the same secret (`cardSecret`, `cardIsAll` compare with `>= 4`).

## 5. Sums

The card's pieces are listed by `GET_PIECES` as they are, each with its keyset.
A phone reads the table once (`GET_CARD` P1 = 2) and sorts the pieces into
balances itself; that is how a phone reads one balance per unit per mint, and it
needs no sum from the card. The card's sums are for a reader that does not list
pieces (an upstream terminal, a quick look):

| Command | Answers |
|---|---|
| `GET_BALANCE` (`11`), P1 = `00` | the sum of the unspent pieces of **the record's balance** (`0, A`), 4 bytes. On a card of one balance that is every unspent piece, so the answer is 1.20's. On a card of format 5 it is never a sum across units: a reader that knows only this command sees the money it would understand, and no cents added to sats |
| `GET_BALANCE`, P1 = `01` (1.21) | a count n (1), then for each balance that holds a piece, in the order mint 0 sats, mint 0 dollars, mint 1 sats, and so on: mint index (1), unit code (1), pieces (1), sum (4); then a count u (1; 1 or 2, 0 on a card with nothing unspent), then for each unit held: unit code (1), its sum over every mint (4). At most 1 + 6 × 7 + 1 + 2 × 5 = 54 bytes |

A sum that passes four bytes wraps, as P1 = 0's always has (`testGetBalanceWrapsPast2Pow32`). Sats are added to sats and cents to cents,
across mints, because a sat at Forge and a sat at Minibits are the same sat to
the holder; they are never added to each other. The card does not know a price
and never says a total in one unit.

## 6. Limits

### 6.1 One set for each unit

Why: a daily limit of 20,000 counted against cents is a limit of $200, and
against sats about $20. A number with no unit is no limit. So each unit has its
own: the daily limit, the limit on one payment and the no-PIN allowance, each
with what has been counted against it and a window of its own.

Unit A's are the record's fields, where they are (limit at 2, window 189, spent
193, limit on one payment 197, allowance 201, its count 205). Unit B's are in
the book (316 to 339), in the same meanings. A window of its own for each unit,
and not one shared, because setting a limit begins its window: a shared window
would let the owner's withdrawal of dollars, which lifts and puts back the
dollar limit, also give the sats their whole day back.

### 6.2 SET_LIMIT gains a unit

The owner's form (`34`) takes the value it takes now, or the same with the unit
in front:

| Value | Sets |
|---|---|
| 4, 8 or 12 bytes | unit A's, exactly as now |
| **5, 9 or 13 bytes** (1.21) | unit code (1), then 4, 8 or 12 bytes as above, for that unit: unit A's when the code is the record's, unit B's when it is the other. Any other code is `6A80` |

The proof is over the value as sent, unit byte and all, under the label it has
(`FoxyCard/set-limit`), so a proof for the sats' limit is none for the dollars'.
A card of 1.20 answers the new lengths `6700`, so a figure in cents can never be
taken by an old card as sats (10.1). Every rule of the twelve-byte form holds
for each unit (LIMIT 6f): a figure that does not change keeps its window and its
count; a new daily limit begins that unit's window at `now` and takes that
unit's two counts to 0; an allowance on a card with no PIN is `6985`. The PIN
form (`33`, a card with no owner) is unit A's only.

Unit B on a card with no book, including a card before its first `SET_CARD`
(whose book is not made yet), is `6A8C`, after the proof and the length and
before anything is written. Before the first `SET_CARD` the record's unit byte
is `00`, so unit A is sats there, and a five-byte value with code `00` sets
unit A's as the four-byte one does.

`writeLimit`'s transaction writes the same number of fields for unit B as for
unit A, into the book in place of the record: nothing more is logged.

### 6.3 A payment is charged to its own unit, and only to it

The payment's unit is its balance's (7.1). Each reader of a limit takes three
parameters: the unit's array (`cardRecord` or the book), the offset of its daily
limit (2 or 316) and the base of its other five (189 or 320), worked out once
at the top of the command from the payment's balance. Field k is at base + 4 ×
k (3.2). No instance field holds them: a field is persistent memory, and would
be written at every payment. Java has no pair return, and small methods that
answer an array and an offset would be two calls a field, each a place the
chip relocates at install, of which it takes only so many (`waitsFor`'s
comment). `requireUnderLimits`, `waitsFor`, `requireAllowanceOrPin`, `dayIsOver` (as
`windowIsOver` on the unit's window) and the `SIGN` commit read their fields
this way, so the code that counts is the code of 1.20 with three parameters in
it:

- **The day** (`requireUnderLimits`): spent today in the payment's unit, plus
  what leaves the card, over that unit's daily limit: `6A8F`, written in the log
  and refused, as now. A day is over when `now` is a day past **that unit's**
  window start. At `SPEND_ALL_BEGIN` a sum that wraps is refused here too, as
  now (the carry path), against the payment's unit: the unit is the first
  place's, and the one-balance check (`6A98`, 7.1), made in the loop over the
  places, comes before the wrapped-sum refusal, so a wrap never meets a place
  of another balance. The refusal is logged with the payment's balance (6.5),
  and the other unit's counts are untouched.
- **The wait** (`waitsFor`): with `L` that unit's limit on one payment and `S`
  what leaves the card (the pieces less the card's own change, both in the
  payment's unit), the formula of LIMIT 6a, unchanged:

      S ≤ L + L/32         waits = 0
      otherwise            waits = 10 + 4 × (ceil(S / L) − 2), at most 255 limits' worth,
                           less two for every three change outputs, never below 0

  "One payment a tap at full speed" stays one rule for the card and not one a
  unit: a second payment in the same time in the field is slowed whichever
  balance it is of, because what it guards is a terminal taking a payment a
  second, and two units do not make that slower.
- **The no-PIN allowance** (`requireAllowanceOrPin`): what has been signed with
  no PIN in that unit's window, plus what leaves the card, over that unit's
  allowance: `6A94`, the PIN for the whole payment, nothing in the log. A card
  with a PIN and no allowance in one unit asks for the PIN for every payment in
  that unit.
- **The commit**: in `SPEND_ALL_SIGN`'s one transaction, the payment's unit's
  window start (on a new day), spent today and no-PIN count: the same number of
  writes as now, in another place.
- **`SET_HEADER`**: the first header after a limit set while `now` was 0 anchors
  the window of each unit that has one: one `arrayCopy` of 4 more in its
  transaction, for unit B's window.

### 6.4 A card with one unit

A card whose book holds no entry of unit B has unit B's limits and counts at 0,
which is no limit, and never reads them: no payment is of unit B. It is the 1.20
card. A card whose record is in dollars and that holds only dollars is the same,
in cents: its limits in the record are cents, and every rule above reads them
as they are (9.7).

### 6.5 The log and the receipts say the balance

Why: the log is the one account of a card's use that needs nobody's honesty, and
a line that says "1,000 signed for" without its unit says nothing.

- **A log entry is of one balance.** Its flags byte gains bits 4 and 5 (the mint
  index) and bit 6 (`1` unit B, `0` unit A), the same bits as the packed
  balance's (3.1) with its bit 7 left off. A tap whose payment or load is of
  another balance than the entry it has begins a new entry: the count of taps
  then counts entries, which a phone that remembers the counts it saw reads the
  same. Bit 2 stays unused. An entry of a 1.20 card, and of a 1.21 card that
  holds only `0, A` (in sats or in dollars), has the three bits at 0, so its log
  is 1.20's to the byte (goal 5).
- **How `logEntry` knows.** It takes the packed balance. Where the tap has an
  entry (`tapOpen[0]`), it reads that entry's bits 4 to 6. If they differ, it
  begins a new entry as at a tap's start, and the new entry's bits are written
  when it is begun, in the transaction that begins it (`LOAD_PROOF`'s log,
  `SIGN`'s, `refuseOverLimit`'s). Nothing new in RAM: the entry's own flags
  byte is what is compared.
- **Units are the record's.** Bit 6 and the head's count are relative to the
  record's unit at the moment of reading, as 1.20's one count has always been.
  A change of the record's unit (6.6) changes what old entries mean, as it
  does on 1.20; it is the owner's, on an empty card.
- **The counts.** The head's "sats, ever" (offset 4) counts unit A. Unit B's is
  in the book (341). `GET_LOG` with **P1 = 2** (1.21) answers 20 bytes of counts
  (taps, unit A ever, refused, runs, unit B ever) and the eight entries: 180
  bytes. P1 = 0 is the 176 bytes it is.
- **The receipts.** Each receipt's balance is kept beside it in the book (345,
  one byte for each of the sixteen), written in the receipt's own transaction.
  `GET_LOG` with **P1 = 3** (1.21) answers as P1 = 1 does with each receipt one
  byte longer, its 77 and its balance packed: 4 + 3 × 78 = 238 bytes. Same gate
  as P1 = 1 (the owner's grant; with no PIN, anyone). P1 = 1 is the 77-byte form
  it is. A receipt from before the book has `00` there.

A card of 1.20 answers `GET_LOG` P1 = 2 and 3 with `6A86`.

### 6.6 A change of the record's unit

`SET_CARD` may change the unit byte only on a card with nothing unspent, as
now. But its limits stay, and a daily limit of 2,000 set in sats would read, in
a record now in dollars, as $20.00. So a 1.21 card refuses a `SET_CARD` that
changes the unit (`6985`) while any limit of either unit is set. The owner's
phone, in the one tap that turns a card from sats to dollars, takes the limits
to 0 with its proof, writes the record, and sets them again in the unit they
belong to. On a 1.20 card, which does not refuse, the phone does the same in the
same order (9.7).

## 7. Payments

### 7.1 One balance a payment

`SPEND_ALL_BEGIN` (`22`) refuses places of more than one balance: **`6A98`**,
nothing hashed, the payment not begun. It is checked with the date, after the
places are found unspent and named once: the first place's keyset is compared
with each place's own (one native comparison a place), and only a place of
another keyset is looked up in the table, its balance compared with the first
place's. Two keysets of one balance (a mint's old keyset and its new) are one
balance, and a mint takes them in one swap. The first place is looked up too:
a place of no entry (3.5) is `6A98`, never `0, A`. This check is in the loop
over the places, so it comes before the refusal of a sum that wraps, which is
then charged to the payment's unit (6.3).

Why the card refuses and does not leave it to the mint: a mint refuses a swap
whose inputs are of two units, so no money would move. But the card charges the
day before the mint is asked, and a payment of sats and cents together has no
unit to charge. And two mints in one payment is a swap no mint can make.

At `SPEND_ALL_SIGN`, the payment's balance is the first place's entry, looked up
once. Nothing new is kept in RAM between the two commands.

### 7.2 SIG_ALL stays per mint and per date

One signature is over every piece and every output of one swap at one mint, and
the pieces' secrets have to agree in everything but the nonce (LIMIT 6c). Both
rules stand: one balance a payment (so one mint and one unit), one date a
payment. A card of two balances pays a dollar price from its dollars and a sats
price from its sats; never both at once.

### 7.3 The card's own change

`SPEND_ALL_CHANGE` copies the payment's first keyset into the opening, as it
does now. So the change is in the payment's balance by construction, and what
leaves the card (the pieces less that change) is in the payment's unit. The
opening carries its mint and unit through its keyset, whose entry it keeps live
until the piece is back (3.4). `GET_CHANGE` answers the openings as it does,
80 bytes each; a phone finds an opening's balance in the table it has read. No
form of `GET_CHANGE` changes. The eight openings are shared by every balance.

### 7.4 The last payment's balance

Kept in the book (340), packed, written in `SPEND_ALL_SIGN`'s transaction (one
byte more in a transaction that is the same size for every payment; the test
that counts its writes is moved by one, and the chip's commit buffer is
measured again with it, 12). It is the balance a till may enter a new keyset as
without the owner (3.3), and `GET_INFO` P1 = 3 says it (byte 72). It stays until
the next payment, and is let go by `SET_CARD` and `RESET_CARD`.

**Every transaction this design touches, and what it gains.** The chip's
commit buffer holds about a dozen writes and jCardSim has no limit, so each is
listed and each is measured at the chip in the one sitting of 12:

| Transaction | Gains |
|---|---|
| `SPEND_ALL_SIGN` | one byte (the last balance); at a tap's first entry of a balance, the new entry's flags byte (6.5). The unit's counts replace unit A's, they do not add to them |
| `LOAD_PROOF`'s log | the flags byte, at a new entry |
| `refuseOverLimit` | the flags byte, at a new entry |
| `SET_HEADER` | one `arrayCopy` of 4 (unit B's window) |
| `writeLimit` | none: unit B's writes replace unit A's |
| `SET_MINT` | the address and its length: two writes, nothing else in it |
| `SET_CARD` | none: the book's zeroing is outside it (3.4) |
| the entry at a load | none: its bytes are written before the slot's, outside any transaction, its mint byte last (3.6) |

## 8. Commands and words, all together

New or changed in 1.21. `SELECT` answers `01 15`. No capability bit changes;
all eight were taken by 1.9, and the version says it.

| INS | Command | Needs | Data sent | Answers |
|---|---|---|---|---|
| `01` | GET_INFO | | P1 = 3 | 73 bytes (4.5). Byte 8 is 4 or 5 in every form |
| `11` | GET_BALANCE | | P1 = 0 or 1 | P1 = 0: the record's balance; P1 = 1: each balance and each unit's total (5) |
| `16` | GET_CARD | | P1 = 0, 1 or 2 | the record as 1.20's; the mints; the table (4.4) |
| `18` | GET_LOG | as P1 = 0 and 1 | P1 = 2 or 3 | the counts with unit B's; the receipts with their balances (6.5) |
| `22` | SPEND_ALL_BEGIN | | | `6A98` for places of two balances (7.1) |
| `24` | SPEND_ALL_SIGN | | | charged to the payment's unit only; the last payment's balance written (6.3, 7.4) |
| `30` | LOAD_PROOF | as 1.20 for the load; the table's gate for a new keyset (3.3) | P2 = 1: mint index (1), unit (1), then the pieces; any other P2 but 0 is `6B00` | `6A97`, `6A9A`, `6A99` as 3.3; a new keyset entered |
| `31` | CLEAR_SPENT | as 1.20 | | and a reclaim of the table (3.4) |
| `32` | SET_CARD | as 1.20 | as 1.20 | `6A8D` also while change is owed (an opening PENDING; a DRAFT let go first); the table, mints 1 and 2, the last balance and the receipts' balances zeroed outside its transaction, before it (3.4); `6985` for a change of unit while a limit is set (6.6); the book reserved the first time (3.6) |
| `34` | SET_LIMIT, owner's | as 1.20 | 5, 9 or 13 bytes: the unit, then the limits | that unit's limits (6.2) |
| `46` | **SET_MINT** | the owner's proof, no PIN, any funds but not at that mint | proof length, proof, index, length, address | the address of mint 1 or 2 set, replaced or let go (4.2) |
| `51` | RESET_CARD | as 1.20 | | and the book zeroed |

Every command in every state, the rows that change:

| Command | Card with no owner | Card with an owner |
|---|---|---|
| `GET_INFO` P1 = 3, `GET_CARD` P1 = 1 and 2, `GET_BALANCE` P1 = 1 | open | open |
| `SET_MINT` (`46`) | `6A90` | the owner's proof, any funds, none of them at that mint (`6A8D`, after a reclaim); `6986` locked |
| `SET_CARD` (`32`) | as 1.20 | as 1.20, and `6A8D` while change is owed |
| `LOAD_PROOF` with a label other than `0, A` and the last payment's balance | `6A90` (no load at all) | the owner's grant in this tap (`6A9A` otherwise) |

Status words, in the part of `6Axx` that ISO 7816-4 leaves unassigned (`6A92`
and `6A95` stay unused, as LIMIT section 8 says):

| SW | Means |
|---|---|
| `6A97` | another balance: a load whose piece's keyset is in the table as another balance than the load names, or whose label names a mint the card has no address for. Nothing is written |
| `6A98` | two balances in one payment: `SPEND_ALL_BEGIN` was given places of more than one mint or unit. Nothing is hashed, counted or written, and no payment is begun |
| `6A99` | no room in the table: a load of a new keyset with no free entry after a reclaim, or a fifth live entry without the owner's mark; also every labelled load of another balance than `0, A` on a card refused the room for its book, before the label's mint is looked at. Nothing of the piece is written |
| `6A9A` | not this tap's to start: a load of a new keyset as a balance other than `0, A` and the last payment's, without the owner's grant (3.3). Kept apart from `6A91`, which a phone reads as "not the owner". Nothing is written |

`CardGate.swift` carries `46` beside the rest.

## 9. The phone

Each file, and what it gains. Nothing here is built.

### 9.1 `build/wallet/08a-flashcard.js`

- **Reading.** `cardInfoOf`: `balancesKnown` for 1.21 and on (the version, as
  for `proofs`); the 73-byte answer to P1 = 3 read into `info.b` (unit B's
  limits and counts) and `info.book` (format, entries, mints, last balance);
  format 5 taken as 4 for the secret. `cardRecordOf`: unit `00` is `'sat'` and
  `01` is `'usd'`, anything else `'other'`. A new `cardBookOf` reads `GET_CARD`
  P1 = 1 and 2 into `card.mints` (index to address) and `card.table` (short
  keyset to balance). `cardLook` asks P1 = 3 in place of P1 = 1 and the two
  `GET_CARD` forms of a 1.21 card (two commands, about 0.06 s), and skips the
  two where byte 8 is 4 and one mint is named.
- **Sorting the pieces.** Each piece's balance is the table's, and then it has
  to agree with the mint: the keyset has to be one that mint lists **in that
  unit** (`keysetUnit` of `08c-units.js`, against the mint the table names).
  `cardFullId` takes a unit and maps only among that unit's keysets. A piece
  whose keyset the mint does not list in that unit is foreign, counted nowhere
  and never paid with. `card.balances` is the pieces by balance, with each one's
  sum; `card.pieces` stays the whole list for what reads every piece (the mint's
  state check, the proofs' check).
- **Usable.** `cardUnusable` stops refusing a record in dollars, and stops
  refusing a card at another mint outright: it asks whether the card holds the
  balance asked for (this phone's mint and the unit of the request), and says
  so in plain words when not (9.6). `cardUsable(card, w, own, balance)` gives the
  pieces of one balance only.
- **Limits.** `cardDayOf`, `cardTapOf` and `cardNoPinOf` take a unit and read A's
  or B's. `cardLimitTo` sends the five-, nine- or thirteen-byte value to a card
  of 1.21, and the old lengths for unit A on a card before it. `cardLiftNote`
  (the limit written down before the owner's withdrawal lifts it) keys by card
  and unit. `cardPaceNote` (a limit kept in dollars and re-pegged to sats as the
  price moves) applies to sats only; a dollar limit is cents and is never
  re-pegged.
- **Choosing pieces.** `cardPick`, `cardPickAll`, `cardExactPick` and the rest
  are handed one balance's pieces and nothing else, and the cap of that
  balance's unit. Unchanged inside.
- **Signing.** `cardSign` and `cardSignGroup` begin with one balance's places.
  `6A98` read as "the card was asked to pay from two balances at once", which a
  phone that picked from one balance never sees.
- **Loading.** `cardPrepare`, `cardLoadCut` and `cardChangeCut` make pieces in
  the unit's wallet (`walletIn(unit)`), on that unit's active keyset
  (`unitKeysetId`), cut in that unit; `cardMintMisfit` asks of that keyset. The
  128 places are the card's, not a balance's: two balances that each want a
  deep drawer (eight of every size from 1 to 1,024, 88 pieces) would want 176. A
  card of two balances is cut four deep in each (44 and 44), with the rest of
  the room for larger pieces and change (13, decision 7). `cardWriteOwed` sends
  every piece labelled with its balance on a card of 1.21, and plain on one
  before it (which holds only `0, A`). `6A97`, `6A9A` and `6A99` are read as
  words (9.6).
- **Records.** Every row this file keeps for a card gains the balance it is of:
  `CARD_OWED` (pieces owed to a card: it has `mint` and gains `unit`),
  `CARD_SWAPS` (a payment's swap), the pieces a phone files to take a lost card
  back (`cardRemember`), the receipts (`CARD_RECEIPTS`, from `GET_LOG` P1 = 3).
  A row with no unit is sats at the card's mint, as every row written before is.
- **Its mint.** `cardRepointFor` (a card moved to another mint, empty) is
  mint 0's, and does what it does. A new `cardMintAdd` puts `SET_MINT` before
  the first load of a mint the card does not name (4.3).
- **The secret, and every other comparison of the format with 4.** Each
  becomes `>= 4` (with `<= 5`, so a format 6 is still refused): `cardSecret`
  (`Number(format) === CARD_FORMAT_ALL`), `cardIsAll` (`card.info.format ===
  CARD_FORMAT_ALL`), every capability `cardInfoOf` works out as `b(8) ===
  CARD_FORMAT_ALL && version` (`ownChange`, `shaped`, `costed`, `headers`,
  `noPinKnown`, `resetKnown`, `moreKnown`, `proofsKnown`: left as they are, a
  card of format 5 would read as knowing none of them), `cardLook`'s refusal
  (`format !== CARD_FORMAT && format !== CARD_FORMAT_ALL`, which takes 5 on a
  new phone), `21a-flashcard.js`'s take-back (`Number(row.format) ===
  CARD_FORMAT_ALL`), and `26f-flashcard.js`'s `fcShaped` (`c.info.format ===
  4`). A row's format
  (`row.format`, filed where the pieces are filed for take-back) is filed as 4
  for a card of 4 or 5, since it says how the secret is made and not the
  balances. A test greps both files for `=== CARD_FORMAT_ALL`, `=== 4` and
  `== 4` beside `format` and finds none left.

### 9.2 `build/wallet/08c-units.js`

Little. `unitCode` and `unitOfCode` map `'sat'` and `'usd'` to `00` and `01`.
The card's code uses `keysetsOf`, `keysetUnit`, `unitKeysetId` and `walletIn`
as they are, and every write into a pile still passes `unitGuard`, so a dollar
piece taken off a card can only land in the dollar pile.

### 9.3 `build/wallet/21a-flashcard.js`

The page's calls gain a balance: `cardPrepare(card, amount, { unit, mint })`,
`cardAdd`, `cardWithdraw(link, { unit, mint, amount })`, `cardSetLimit(link, {
unit, ... })`, `cardPay(link, { unit })` (the till's), and `cardBalances(card)`
for the screen. A new `cardSwap` runs the two taps of 9.5.

### 9.4 `build/app/26f-flashcard.js`, the Flashcard screen

- **The pills.** The home's two pills, as the two-balances home has them: the
  dollar pill in frost blue with its worth in sats under it, the bitcoin pill in
  fox fur with its worth in dollars under it, at the market price. A card that
  holds one balance shows one pill, as now. A card at a mint of one unit never
  shows the dollar pill.
- **Mints.** The mint on show is named over its pills, as the home names its
  mint (`FORGE ›`). The card's other mints are rows under the pills, each with
  its balances; a tap on one puts it on show. The daily limit, what is left of
  it today and when the day turns are said under each pill, for its unit.
- **ADD FUNDS and WITHDRAW** ask which balance first where there are two, on the
  app's own currency question (`What do you want to add?`, the two cards with
  their balances), and then the SET AMOUNT screen in that unit. A mint the card
  does not name is added from ADD FUNDS (`ADD ANOTHER MINT`, the app's mint
  list), at the cost of nothing but the load.
- **SWAP** on the card's row, on a card that holds a balance at a mint of two
  units (9.5).
- **CHANGE LIMIT** on a card of two units sets both from one dollar figure
  (13, decision 1): dollars to cents exactly, sats at the price and re-pegged.
  The confirmation says both: `YOU ARE APPLYING A DAILY LIMIT OF: $20.00` and,
  under it, `on its dollars, and on its bitcoin at today's price`.
- **Words** for the refusals of 9.6.

### 9.5 SWAP, a phone's swap with a tap at each end

The card cannot reach a mint, and a swap between dollars and sats is the mint's
(a quote paid by a melt), which takes from a few seconds to a minute: longer
than a card is held. So a swap is the phone's, with the card at each end:

1. **SWAP**, the amount on SET AMOUNT in the unit that leaves, the app's own
   swap confirmation (YOU ARE GETTING, YOU ARE SPENDING, FEE, the mint's rate).
2. **Tap 1, the PIN.** A withdrawal of that amount from that balance, as
   WITHDRAW is: the unit's limits lifted with the owner's proof, the pieces
   signed under the PIN, the limits put back. The money is in this phone.
3. **No card.** The phone swaps at the mint, as its own SWAP does.
4. **Tap 2, no PIN.** ADD FUNDS of what arrived, into the other balance, under
   the owner's grant.

Two taps and one PIN. Cut short anywhere, the money is in the phone, in
whichever unit it was at that moment, and the screen offers to put it on the
card or leave it. Only the owner's phone swaps (step 4 is a load under the
grant, and step 2 lifts the limit).

### 9.6 The till

A till asks to be paid in its request's unit at its own mint. It reads the card
(`GET_INFO` P1 = 3, the table and the mints on a 1.21 card) and finds the
balance at its mint in that unit. Then everything is as now, on that balance's
pieces and that unit's limits: the pieces it picks, the PIN or the allowance,
OVER THE CARD'S DAILY LIMIT in that unit, the change, the second tap. A card
with balances at several mints pays any till at any of them, which is the one
thing two mints on a card buy at a counter.

| What the card holds | The till says |
|---|---|
| no balance at this phone's mint | `This card's money is at Minibits and Forge. This phone is at <mint>.` |
| sats here, and a dollar request | as now (`770db86`): the card pays the dollar invoice from its sats |
| dollars here, and a dollar request | paid from the dollars. A card that holds both pays a dollar request from its dollars first, and from its sats only where its dollars are short (13, decision 5) |
| dollars here, and a sats request | `This card holds dollars here, not bitcoin.` (a dollar payment of a sats invoice is a melt, and is left for later) |

Offline, a till takes only an exact set of one balance in its request's unit,
as now in sats, and the DLEQ of each piece is checked against the mint's keys of
that unit.

**Change and refunds** go back in the balance they came from: written labelled
under the change grant, which the card lets in as the last payment's balance
(3.3). A refund to the payer of a payment the mint refused is the same.

**Take-back of a lost card** (`cardTakeBack`): the pieces this phone filed when
it loaded them, each with its balance, swapped with the refund key at its own
mint, each into the pile of its own unit.

### 9.7 What a dollars card needs

A card whose record says dollars and that holds only dollars at mint 0 is a
card of one balance (format 4), in cents. Its limits are the record's, in cents,
typed in dollars and never re-pegged. It needs nothing of 1.21: see 12.2.

### 9.8 Native

- `Foxy/Flashcard/CardGate.swift`: instruction `46`.
- `Foxy/Keychain/NUT13.swift`, `CardOwner.Label`: `setMint = "set-mint"`,
  whose value is an index of `01` or `02`, a length of 0 to 77 and that many
  bytes of `20` to `7E`; and `setLimit` accepting 5, 9 and 13 bytes whose first
  is `00` or `01`, beside 4, 8 and 12. Native refuses every other label and
  shape before it reads the seed, as now. Its tests gain both.

## 10. Migration and compatibility

### 10.1 A card of 1.20 under a new phone

It stays a card of one balance for good. A Java Card applet is not upgraded in
place: a new version is a new install, which makes a new key and takes the
money with the old one, so a card in hand becomes a 1.21 card only by being
emptied, deleted and installed again (`HARDWARE_DEPLOYMENT.md`), after which
the phone sets it up as a new card.

The new phone knows it by its version (`01 14`), and sends it nothing new:

| New command or form | What a 1.20 card does | Why it is safe |
|---|---|---|
| `GET_INFO` P1 = 3 | the thirty bytes | the phone checks the version, and the length (73) |
| `GET_CARD` P1 = 1, 2 | the record | the phone asks only a 1.21 card |
| `GET_BALANCE` P1 = 1 | the one sum | the same |
| `GET_LOG` P1 = 2, 3 | `6A86` | the same |
| `LOAD_PROOF` labelled | `6700` (83, 164, 245, 179 are not its lengths) | a label sent by mistake writes nothing |
| `SET_LIMIT` of 5, 9, 13 bytes | `6700` | a figure in cents can never be taken as sats |
| `SET_MINT` (`46`) | `6D00` | |

A 1.20 card can be a dollars card (12.2), with a record in dollars and pieces
in dollars, and nothing else.

### 10.2 A card of 1.21 under a phone of today

| The card | What the phone of today does |
|---|---|
| one balance, sats at mint 0 (format 4) | everything, as with a 1.20 card. It loads plain pieces, which go in as `0, A`. It sets limits of 4, 8 and 12 bytes, which are unit A's. Its `SET_CARD` on an empty card clears the table, which is empty of money |
| one balance, dollars (format 4, record unit `01`) | refuses it at the read: "That card holds something other than sats." Refuses to load it (`cardPrepare` calls `cardUnusable`) |
| more than one balance (format 5) | refuses it before it reads a piece: "That card is a kind this Foxy does not know." |

So no phone ever reads a dollar piece as sats. That needs the format byte: a
phone of today maps a keyset to any keyset of its mint whatever its unit
(`cardFullId`), and the version alone does not stop it, since it takes any
version past 1.20 as having what 1.20 has.

### 10.3 Version and formats

| | 1.20 | 1.21 |
|---|---|---|
| `SELECT` | `01 14` | `01 15` |
| the card's format, `GET_INFO` byte 8 and `GET_CARD` byte 0 | 4 | 4 for a card of one balance, 5 for one of more |
| the piece's format (the secret) | 4 (`SIG_ALL`) | 4, whatever byte 8 says |
| the book's format, `GET_INFO` P1 = 3 byte 43 | none | `01`, or `00` for a card refused the room |
| `FORMAT` in the applet | 4 | 4, and a constant `FORMAT_BALANCES` = 5 |

### 10.4 The model of the card (the card with no card)

`tests/flashcard-card.js` in Foxy answers as the applet does, and is held to it
by `tests/flashcard-model.js` replaying a recording of the applet.

- `makeCard({ software: 21 })` the card of this design; `software: 20` and
  before are the cards before it, as now; `makeCard({ book: false })` a 1.21
  card refused the room for its book, as `proofRoom: false` is for the proofs.
- The book, the table's gate (3.3), the reclaim, `SET_MINT`, the labelled load,
  unit B's limits in the day, the wait and the allowance, `6A97`, `6A98`,
  `6A99`, the format byte at each asking, `GET_INFO` P1 = 3, `GET_CARD` P1 = 1
  and 2, `GET_BALANCE` P1 = 1, `GET_LOG` P1 = 2 and 3, and the log's bits.
  `MINT_MAX` (80 in the model) comes down to 77 to match the applet, which it
  should already.
- `tests/flashcard-model.js` replays a 1.21 recording and keeps every recording
  it has, the 1.20 one as `-120`.
- New suites: `tests/flashcard-balances.js` (dollars and sats at one mint: load,
  read, pay a dollar till and a sats till, change, withdraw, swap, limits in each
  unit, a till of today refused cleanly), `tests/flashcard-mints.js` (two mints:
  add a mint, pay a till at each, a mint's address under money refused, a mint
  let go), and the money suites run over a two-unit stand-in mint, as the
  two-balances work runs. A live suite against a two-unit Nutshell (the one
  `tools/live/two-balances.js` uses) with the applet under jCardSim as the card.

### 10.5 The JUnit tests to add

A new class, `BalancesTest`, beside `ProofStoreTest`, with the applet under
jCardSim. Each test, and what it pins:

| # | Test | Pins |
|---|---|---|
| J1 | `theBookIsNotMadeAtInstallAndIsMadeByTheFirstSetCard` | null after install, after a PIN and after a refused `SET_CARD`; 361 zero bytes after the first that goes through; the same array after every later one |
| J2 | `aCardRefusedTheBookIsATwentyCard` | with the allocation made to fail: plain loads go on as `0, A`, a labelled load of another balance is `6A99`, every new form answers as 3.5 says, byte 8 is 4 |
| J3 | `aPlainLoadEntersANewKeysetAsTheRecordsBalance` | under the PIN or the change grant: one entry `40 00` after a plain load on a sats card, `40 01` on a dollars card; under the owner's grant, `C0 00` and `C0 01`; a second piece of the keyset writes no entry |
| J4 | `aLabelledLoadUnderTheOwnersGrantEntersItsBalanceMarked` | `C1 01` (dollars at mint 1, the owner's), `C2 00` (sats at mint 2, the owner's), `C0 01` (dollars at mint 0, the owner's); the mint byte's owner bit; the packed balance of each by the one helper (`D0`, `A0`, `C0` on a sats card) |
| J5 | `withoutTheOwnerOnlyTheRecordsBalanceAndTheLastPaymentsMayBeEntered` | under the PIN, the change grant and on a card with no PIN: `0, A` yes, the last payment's balance yes, any other `6A9A`, nothing written |
| J6 | `aKeysetIsNeverEnteredTwice` | a piece of an entered keyset labelled another balance is `6A97`, plain or labelled, with the proof or without; nothing written, the count the same |
| J7 | `aLabelNamingAMintWithNoAddressIs6A97AndABadLabelIs6A80` | index 1 with no address; index 3; unit `02`; P2 = 2 is `6B00`, nothing written |
| J7b | `aLabelledLoadKeepsItsOwnProofAndCountsItsAmount` | P1 = 1, P2 = 1: the proof read back by `GET_DLEQ` is the one sent after the label, byte for byte; three labelled pieces: each slot holds its own 81 bytes; the log's "put on" is the pieces' sum, not a sum read two bytes off |
| J8 | `theTableHoldsSixteenAndReclaimsTheDeadFirst` | a seventeenth keyset with every entry live is `6A99`; with one dead it goes in, in the dead one's place |
| J9 | `noMoreThanFourLiveEntriesWithoutTheOwner` | the fifth `6A99` under the PIN; the owner's still go in; four unmarked entries, two of them dead and not yet reclaimed: a fifth unmarked keyset goes in after the reclaim, and the count after is three; a load of a keyset already entered runs no reclaim (the pass is counted) |
| J10 | `clearSpentFreesWhatNoPieceAndNoOpeningNames` | after a payment of every piece of a keyset and `CLEAR_SPENT`, its entry is free; an entry named by a pending opening is not |
| J11 | `aTornEntryIsFreedAndNeverServes` | the entry's mint byte last; torn states set by hand: an entry with no piece, a piece whose status byte is not written |
| J12 | `setMintUnderTheOwnersProof` | set, replace, let go; every refusal of 4.2 in its order; the label, the nonce and the value each bound; no PIN try spent |
| J13 | `setMintRefusesAMintThatHoldsMoney` | `6A8D` with a live entry of that index, money at mint 0 and mint 2 no bar; a dead entry of index 1 (all its pieces spent, no `CLEAR_SPENT`), then `SET_MINT` index 1 to a new address: the entry is free after it, and a load of the old keyset labelled `1, unit` enters a new entry and does not reuse it |
| J14 | `setMintRefusesAMintTwice` | the same address at index 1 and 2: `6A80` |
| J15 | `setCardZerosTheTableAndTheMintsOnlyWhenEmpty` | `6A8D` with anything unspent anywhere; after it the table, mints 1 and 2, the last balance and the receipts' balances are zeros, unit B's limits are not |
| J15b | `setCardRefusesWhileChangeIsOwed` | a payment of every piece with the card's own change, then `SET_CARD` under the owner's proof is `6A8D` and the book is untouched; after the change is written back and `CLEAR_SPENT`, it goes through; a DRAFT opening left by a payment never signed is let go and does not bar it |
| J16 | `setCardRefusesAChangeOfUnitWhileALimitIsSet` | `6985` while any of the six is set; goes through when all are 0 |
| J17 | `resetCardZerosTheBook` | the book zeros, the same array, not made twice |
| J18 | `aPaymentOfTwoBalancesIs6A98` | two units at one mint; two mints in one unit; two keysets of one balance go through and sign once; a place whose entry is zeroed by hand (3.5) is `6A98`, first or not, and is not charged to unit A |
| J19 | `aPaymentIsChargedToItsOwnUnitsDayAndNoOther` | the day to the sat and to the cent; the other unit's count untouched; `6A8F` in each; each unit's window turning by itself |
| J20 | `theWaitIsWorkedOutFromItsOwnUnitsLimitOnOnePayment` | the 6a formula in each unit; a second payment in a tap slowed across units |
| J21 | `theNoPinAllowanceIsPerUnit` | to the unit, `6A94` past it, a unit with none asks the PIN, nothing in the log |
| J22 | `setLimitWithAUnitSetsThatUnitOnly` | 5, 9 and 13 bytes; a proof for one unit is none for the other; 4, 8 and 12 are unit A's; `6A80` for code `02`; every other length `6700` |
| J23 | `theFirstHeaderAnchorsEachUnitsWindow` | a limit in each unit set before any header; the first header anchors both |
| J24 | `getInfoIsTwentyByteForByteAndThreeSaysUnitB` | P1 = 0, 1, 2 as 1.20 but the version and byte 8; P1 = 3 every byte; unit B's limit on one payment zeros without the grant |
| J25 | `theFormatIsFourForOneBalanceAndFiveForMore` | 4 on a new card and after loads of `0, A`; an entry `C0 00` (`0, A`, the owner's) leaves it at 4; 5 at the first other entry; 5 on a card that holds `0, B` only; 4 again after the reclaim; `GET_CARD` byte 0 the same |
| J26 | `getBalanceZeroIsTheRecordsBalanceAndOneIsEachBalance` | on one balance P1 = 0 is 1.20's; on two, the record's only; P1 = 1's layout and totals; the wrap |
| J27 | `getCardZeroIsAsItWasAndOneAndTwoAreTheMintsAndTheTable` | byte for byte; 159 and 161 at the most |
| J28 | `aLongHistoryKeepsEveryPieceInItsBalance` | random labelled and plain loads under every authority, payments, change, clears, `SET_MINT`, power-ups: after every step every unspent place's keyset is in an entry of the balance it was loaded as, and each unit's counts add up to what was signed |
| J29 | `theLogSaysEachEntrysBalance` | the flags' bits, the same as the packed balance's bits 4 to 6; a tap of two balances makes two entries; a dollars card holding only `0, A` has bit 6 at 0; `GET_LOG` P1 = 2's counts; P1 = 3's receipts and their balances |
| J30 | `onlyTheseMethodsNameTheBook` | a source scan, as the proofs' store has: `processSetCard` (the reservation and the zeroing), `processResetCard`, `loadOne`, `processClearSpent` and the reclaim, `processSetMint` (and its reclaim), `processSpendAllSign`'s transaction and its receipt, `processSetLimitOwner`, `processSetHeader`, the readers (`GET_INFO`, `GET_CARD`, `GET_BALANCE`, `GET_LOG`, `SPEND_ALL_BEGIN`); no other |
| J31 | `theSignTransactionIsOneByteLonger` | the source reading that counts what the transaction writes, moved by the last balance's byte and no more |
| J32 | `aOneBalanceCardAnswersTheTwentyTranscript` | `spec/vectors/transcript.json`'s 1.20 commands replayed on a 1.21 card set up in sats, loaded plain and under the grant: every answer and status word equal, but `SELECT`'s second byte and `GET_INFO` byte 1 (the version). Then the same on a card set up in dollars. Goal 5, pinned whole |
| J33 | `setCardZerosTheBookOutsideItsTransaction` | a source scan: in `processSetCard` the book is named only before `beginTransaction`, and only by `arrayFillNonAtomic`; the mint bytes zeroed first |
| J34 | `aWrappedSumIsRefusedAgainstItsOwnUnit` | a payment of dollar places whose sum wraps is `6A8F` against unit B, written in the log with unit B's bits; unit A's counts are untouched; with places of two balances and a wrapping sum, the answer is `6A98` |
| J35 | `theCapHoldsOnACardWithNoPin` | anyone who holds a card with no PIN: `0, A` and the last payment's balance go in, any other `6A9A`, the fifth unmarked `6A99` |
| J36 | `aRefusalIsLoggedAsItsBalance` | `6A8F` in each unit: the refusal is counted in an entry with that balance's bits, and a refusal in one balance after a payment in the other begins a new entry |
| J37 | `setLimitOfUnitBWithNoBookIs6A8C` | 5, 9 and 13 bytes with unit B's code before the first `SET_CARD` and on a card refused its book: `6A8C`, nothing written; with unit A's code, as the 4, 8 and 12 |

Changed: the test that walks every command through every state gains `46` and
the labelled load; `theConstructorsAskForNoMorePersistentMemory` stays at
29,806, and the once-only allocations it lists gain the book; the RAM budget
test stays at 941.

Changed too: the source scans in `CashuAppletTest` that name the limit fields or
slice the source by method order. Each is rewritten to the per-unit form,
keeping what it pins:

- `testSpendIsOneTransaction` (about line 7767): the day's window, the spent
  count and the no-PIN count are written in the one transaction, now through
  the unit's array and base; the receipt's transaction counts nothing.
- the `waitsFor` scan (about 7863): it reads the unit's limit on one payment
  and no window, no clock and no spent count.
- the `GET_INFO` scan (about 7906): the limit on one payment copied once for
  each unit, each under `loadGrant`.
- the `SET_HEADER` scan (about 5632): each unit's window anchored after the
  clock.
- the `SET_CARD` scan (about 7960): still no clock, window or spent count in
  it, now with the book's zeroing before its transaction (J33).
- every `body(code, a, b)` whose end marker moves, the slice from
  `processGetReceipts(` to `dayIsOver(` (about 7911) among them. New methods go
  after `dayIsOver`, or the slices are re-anchored on the next method by name.

### 10.6 The transcript vectors

`spec/vectors/transcript.json` gains, as commands and answers: `SELECT` `01 15`;
`GET_INFO` P1 = 3 on a new card and on a card of two balances; `GET_CARD` P1 =
1 and 2; a plain load and a labelled one; a labelled load refused `6A9A`,
`6A97` and `6A99`; `SET_MINT` set and refused `6A8D`; `SPEND_ALL_BEGIN` refused
`6A98`; a payment in dollars, with the day's count read after it in P1 = 3;
`SET_LIMIT` with a unit; `GET_BALANCE` P1 = 1; `GET_LOG` P1 = 2 and 3;
`SET_CARD` refused `6A8D` while change is owed; a labelled load with P2 = 2
refused `6B00`. `spec/vectors/owner.json` gains a proof over `set-mint` and one
over a thirteen-byte `set-limit`.

### 10.7 Order of the work

1. **Applet 1.21 and `BalancesTest`**, the transcript and the vectors, with the
   install-size sitting at the chip (3.2) and the transactions of 7.4 measured
   in it. A 1.21 card in a phone of today is safe from the first build: format
   4 is used as 1.20, format 5 is refused (10.2).
2. **The model of the card** (10.4), from a 1.21 recording. It cannot come
   first: `tests/flashcard-model.js` holds it to a recording of the applet.
3. **The phone** (9.1 to 9.8). It asks a card for nothing new unless the card
   says `01 15`, so it can ship before any 1.21 card exists. The format
   comparisons of 9.1 go in first, in the same change as the reading of format
   5, or a new phone would treat a 1.21 card of two balances as knowing nothing.
4. **The dollars card on 1.20** (12.2) is phone only and can go before step 1.

## 11. Threats

What the table and the second unit change in `SECURITY-MODEL.md` and LIMIT 13,
each with the rule that closes it. Numbered to follow the threat table's 16.

| # | Threat | Closed by |
|---|---|---|
| 17 | **A dollar limit walked round with sats.** The owner sets $20 a day on the dollars; the card's sats have no limit; a terminal that has the PIN takes the sats | Each unit has its own limits, and nothing is bound that the owner has not set (LIMIT 13). The card cannot hold one ceiling across units without a price. So the phone never sets one unit's limit alone on a card of two: CHANGE LIMIT sets both from one dollar figure (9.4). The worst a terminal takes in a day is then the two limits together, which the screen says. Open: 13, decision 1 |
| 18 | **A payment charged to the wrong unit**, by mixing pieces or by a piece put in the wrong balance | `SPEND_ALL_BEGIN` refuses two balances (`6A98`). A keyset is in one entry and is never moved (3.4). A terminal can put a piece of its own in a wrong balance (its own money, which the mint refuses there), and never a piece of the holder's |
| 19 | **A mint index swapped**: mint 1's address pointed at another mint, so a phone takes the card's pieces to the wrong mint, or a keyset entered under the wrong mint | `SET_MINT` is the owner's alone and is refused while any money or change of that mint is on the card (`6A8D`). Mint 0 changes only with nothing unspent. An entry is never changed. The phone keeps a piece only where the mint the table names lists its keyset in that unit, and checks its DLEQ with that mint's keys (9.1) |
| 20 | **A keyset entered by a stranger** on a card with no PIN (anyone may load it), or by a till that has the PIN | A stranger enters only the record's balance or the last payment's (3.3), at most four entries live at once. What it can do is load pieces of its own, as LIMIT 13 already says: a nuisance, and a keyset it squats on answers the owner's later load `6A97` until those pieces are gone. The owner's phone frees it as WITHDRAW does: that unit's limits lifted with the owner's proof, a payment of nothing but those places under the PIN (the mint refuses it, and the card has burned them; a squatter's junk piece can carry any amount, which is why the limits are lifted first), the limits put back, then `CLEAR_SPENT`, and the entry is free. One PIN and two owner's proofs, not free. Open: 13, decision 3 |
| 21 | **Two units under one SIG_ALL** | `6A98` before anything is hashed. The card's own change is made in the payment's keyset, so it is the payment's balance |
| 22 | **A phone of today reading cents as sats** | the card's format is 5 for a card of more than one balance, and a phone of today refuses it at the read. A card in dollars alone it refuses by its unit byte (10.2) |
| 23 | **A figure in cents taken as sats by a card that does not know units** | the new forms of `SET_LIMIT` and `LOAD_PROOF` are lengths a card of 1.20 refuses (`6700`) |
| 24 | **A reader that adds a card's balance**, an upstream terminal | `GET_BALANCE` P1 = 0 is the record's balance alone; no command of the card adds two units |

What it does not change: the card checks no piece and knows no price; a
terminal that has the PIN takes up to each unit's limits in a day; the receiver
always asks the mint. And one thing it costs: a card of two balances is two
limits' worth to a thief in a day, where a card of one is one (17).

**A mismatch between the documents and the applet, found writing this.**
`FOXY-CARD-SPEC.md` 5.3 rule 5 and LIMIT 6f say a card with no PIN is loaded
only by the owner's grant or in the tap after a payment. The applet
(`requireLoadAuthority` returns at once on a card with no PIN), the `LOAD_PROOF`
rows of SPEC 5.2 and LIMIT 8, and `ProofStoreTest` ("a card with no PIN at all")
say anyone who holds it. This design follows the applet. Threat 20 is smaller if
the rule of 5.3 is the one built (13, decision 3).

## 12. Size, and what can ship first

### 12.1 The whole

| Area | Lines of applet code | |
|---|---|---|
| the book: constants, the reservation, `SET_CARD` (the zeroing outside its transaction, change owed) and `RESET_CARD` | 55 | |
| the table: lookup, the labelled load, the gate, the cap, the order of writes | 130 | |
| the reclaim at `CLEAR_SPENT`, at a full table or the cap, and at `SET_MINT` | 40 | |
| `SET_MINT` | 55 | |
| `SPEND_ALL_BEGIN`'s one balance, `SIGN`'s unit and last balance | 35 | |
| limits per unit: the array and the two offsets as parameters, `requireUnderLimits`, `waitsFor`, `requireAllowanceOrPin`, `writeLimit`, `SET_LIMIT`'s unit, `SET_HEADER` | 110 | |
| `GET_INFO` P1 = 3, the format byte, `GET_CARD` P1 = 1 and 2, `GET_BALANCE` P1 = 1, `GET_LOG` P1 = 2 and 3 | 115 | |
| the log's bits and the receipts' balances | 35 | |
| **in all** | **about 575**, and about as many lines of comment in this file's way | about 2.5 to 3.4 KB of load file |

| Work | Days |
|---|---|
| applet | 4 |
| JUnit (J1 to J37 with J7b and J15b, about 3,300 lines), the source scans rewritten (10.5), the transcript and the vectors | 3.5 |
| at the chip: the install-size window (3.2), every transaction of 7.4's table, the reclaim's and `BEGIN`'s times with a table, `GET_BALANCE` P1 = 0 on a card of format 5 and P1 = 1 (each looks up 128 places in the table), `SET_CARD`'s zeroing of 333 bytes outside its transaction, `ProofStoreCheck`'s like for the book | 1 to 2 |
| the model of the card and its replay | 1 |
| the phone's wallet (`08a`, `21a`, `08c`, native): about 900 lines | 4 |
| the screens (`26f`): pills, mints, SWAP, the limit for two units, the words: about 600 lines, and their snapshots | 3 |
| the phone's suites, and a live one at a two-unit mint with the applet as the card | 2 |
| **in all** | **18.5 to 19.5 working days**, about four weeks of the way this project is worked now |

### 12.2 What can ship first: a dollars card, on the cards there are

**A card of one balance in dollars needs no new card software.** A 1.20 card
already keeps a unit byte it never reads, takes pieces of any keyset, counts
what leaves it and waits by its own work in whatever unit its numbers are, and
its limits are numbers with no unit. Set up with a record that says `01`, loaded
with Forge's dollar pieces, and given limits in cents, it is a dollars card. The
two 1.20 cards in hand can be dollars cards the day the phone is.

What that cut is, exactly, all of it in the phone:

1. `cardRecordOf`: `01` is `'usd'`. `cardUnusable`: a dollars card at this mint
   pays a dollar request; a sats request is refused in words (9.6).
2. Set-up and an empty card's record: `DOLLARS` or `BITCOIN` at a mint of two
   units (the app's own currency question), written as the unit byte;
   from bitcoin to dollars, the limits taken off and put back in cents in the
   same tap (6.6).
3. ADD FUNDS from the dollar pile: pieces on the dollar keyset, cut in cents.
   WITHDRAW into the dollar pile, through `unitGuard`.
4. The limits in cents, typed in dollars, never re-pegged.
5. The till: a dollar request paid from a dollars card, its change and refunds
   in dollars; the rule of `211ed9e` that refuses a held card payment in a unit
   that is not sats lifted for a dollars card. Offline: an exact set in cents.
6. The screen: one frost-blue pill with its worth in sats, amounts in dollars.
7. The rows (`CARD_OWED`, `CARD_SWAPS`, take-back) with `unit: 'usd'`.
8. Suites: the model card set up in dollars; the money suites at a two-unit
   stand-in mint; the live two-unit Nutshell with the applet as the card.

Why it is smaller: no applet, no chip, no install-size window, no new command,
no table, no new card to buy or install; and nothing on the card can mix two
units, because one card holds one. Why it is safe with the phones there are: a
phone of today refuses a card whose unit is not sats, to read it and to load it
(10.2). What it does not give: dollars and sats on one card, and more than one
mint. Those are 1.21.

About 4 to 5 days, phone only.

## 13. For the owner to decide

1. **One ceiling or two.** Each unit has its own limits (designed), and the
   phone sets both from one dollar figure, so a thief's day is the two limits
   together. The other way is one ceiling in dollars on the card, with a rate the
   owner's phone writes with the limit and the card converting sats to cents at
   each payment: about 80 lines more in bytecode, a rate that goes stale as the
   re-pegged limit does, and one more number a terminal is not told. Recommended:
   two.
2. **Three mints and sixteen entries**, four of them open to entries made without
   the owner (3.2, 3.3). Fewer mints is fewer entries and a smaller book; more
   than four mints does not fit one answer.
3. **Who loads a card with no PIN.** The applet lets anyone who holds it; the
   SPEC says only the owner's grant or the tap after a payment (11). Building the
   SPEC's rule would close junk pieces and squatted keysets on cards with no PIN.
   The documents and the applet should agree either way.
4. **The format byte, 4 or 5 by what the card holds** (designed), so a phone of
   today keeps using a 1.21 card of one balance. The other way is 5 on every
   1.21 card: simpler, and every phone of today refuses every new card.
5. **A dollar request from a card of both**: from the dollars first, and from
   the sats (by the `770db86` route) only where the dollars are short
   (designed). Or always ask.
6. **A change of the record's unit** refused while a limit is set, the phone
   taking them off and putting them back in one tap (designed), or the card
   swapping the two units' limits itself.
7. **The 128 places between two balances**: four of every size in each
   (designed), or as the owner uses them (the phone could cut deep in the unit
   paid with most).
8. **The screen of a card at several mints**: one mint on show with its pills
   and the others as rows (designed), or every balance in one list.
9. **The cards in hand**: stay 1.20 (sats, or dollars with 12.2), or be emptied
   and installed again as 1.21 cards with new keys.
10. **What ships first**: the dollars card on 1.20 (12.2), then 1.21.
