# Foxy card: a specification for forking cashu-javacard

A card that holds ecash and pays a Foxy by a tap, and the changes to make in a
fork of `lnflash/cashu-javacard` and in Foxy to get there.

**Not for the public repository yet.** Section 4 lists weaknesses in the
upstream applet as it stands. Upstream asks for those to be reported privately,
so this file is kept out of commits (`.git/info/exclude`) until they have been
told and have had time to answer.

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
3. A terminal cannot take more than the holder agreed to, a wrong or blocked
   PIN opens nothing, and a lost or blocked card is not lost money.
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
   - **CARD BLOCKED**: `Too many wrong PINs. Its owner can take the money back
     with their own phone.`
   - **NOT ENOUGH ON THE CARD**: `It holds 900 sats.` Nothing was taken.
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

1. **TAP CARD.** The iOS sheet; no PIN. The screen then shows:
   - the balance, as the card says and, with a connection, as the mint says
     (`2,048 sats · checked with the mint`, or `the card says 2,048; the mint
     says 1,024 of it is spent`);
   - the mint it is at, how many pieces, how many places are free;
   - its state: no PIN yet, PIN set, blocked, locked;
   - if it can be recovered, until when (see 6).
2. **SET A PIN**, the first time: typed twice. Nothing can be added to a card
   with no PIN.
3. **ADD FUNDS.** An amount, the PIN, a tap. The money leaves this phone's
   balance and is on the card when the screen says so. If the card leaves
   early: `TAP THE CARD AGAIN: 1,000 of 2,000 sats are on it.`
4. **WITHDRAW.** `Enter PIN to withdraw`, a tap, and everything on the card is
   in this phone's balance. A part can be asked for instead of all.
5. **CHANGE PIN.** Old, new, new, a tap.
6. **TAKE BACK A LOST CARD.** For a card this phone funded that is lost or
   blocked: after its date, the money comes back with no card (see 6).

## 3. Who is trusted with what

| Party | Holds | Can do wrong |
|---|---|---|
| Card | its key, the pieces, the PIN | nothing by itself; it signs what it is asked, under its rules |
| Cardholder | the card and the PIN | pay twice from a copied card, if a receiver ever accepts without the mint |
| Receiving phone | sees the PIN, talks to the card | ask the card to sign more than the amount shown; keep change |
| Mint | decides what is spent | what any Cashu mint can |

Two consequences shape the design. The receiver always goes to the mint
before saying paid, so a copied or pretend card gains nothing. And because a
terminal sees the PIN and chooses what to ask the card for, the card itself
must limit what one PIN entry can spend (5.3).

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
| 8 | A terminal with the PIN can spend every slot | by design upstream | a limit per PIN entry, kept on the card (5.3) |
| 9 | A blocked or lost card strands its balance | acknowledged; no unblock | a refund path the funding phone holds (6) |
| 10 | The card does not say which mint its pieces are at | open question upstream | the card records its mint (5.1) |
| 11 | The PIN crosses the air in the clear | so | phase 2: sent under a key agreed with the card |

## 5. The card

### 5.1 What it stores

A card record, written under the PIN:

| Field | Size | |
|---|---|---|
| format | 1 | 2 for this fork |
| mint | 1 + up to 96 | the mint's address, as text |
| unit | 1 | sats |
| refund key | 33 | the key that can take the pieces back after their date, or zeros for none |
| limit | 4 | the most one PIN entry may spend, in sats; 0 for no limit |

A slot, 82 bytes (upstream's 78 and a date):

| Field | Size | |
|---|---|---|
| status | 1 | empty, unspent, spent; written last, as upstream's D14 |
| keyset | 8 | raw |
| amount | 4 | |
| nonce | 32 | |
| C | 33 | |
| date | 4 | the locktime of this piece, 0 for none |

64 slots to begin with (5,248 bytes), more if the J3R180's memory allows once
measured. Phase 3 adds 97 bytes a slot for the DLEQ.

The text of a piece's secret, which is what the card signs the hash of, is
the text Foxy's Cashu library writes for a piece locked to one key:
`["P2PK",{"nonce":"…","data":"<card key>","tags":[]}]`, and with a date
`…"tags":[["locktime","<date>"],["refund","<refund key>"]]}]`. The card builds
what the library builds, so loading a card is an ordinary locked payment and
nothing is made specially for it. It is not upstream's text.

### 5.2 Commands

| Command | Upstream | Fork |
|---|---|---|
| SELECT | version | a new AID of our own, so the two applets cannot be mistaken for each other; version as upstream |
| GET_INFO | 8 bytes | adds tries left, the limit, the format |
| GET_PUBKEY | as is | as is |
| GET_CARD | none | the card record |
| GET_SLOT_STATUS, GET_PROOF | as is | the slot with its date |
| GET_BALANCE, GET_PROOF_COUNT | as is | as is |
| **SPEND** | takes a slot and 32 bytes from the reader | takes a slot only. The card builds the piece's secret from the slot and its own key, hashes it, marks the slot spent, and signs. No message comes from outside |
| **SIGN_ARBITRARY** | signs any 32 bytes | **removed** |
| **AUTH** | none | the reader sends 16 random bytes; the card answers 16 of its own and a signature over a tagged hash of both and its key. No PIN. It cannot be a spend: the tag makes the message one no secret hashes to |
| VERIFY_PIN, SET_PIN, CHANGE_PIN | as is | as is |
| SET_CARD | none | writes the card record; PIN |
| LOAD_PROOF | 77 bytes | 81 bytes, with the date; refused unless a PIN is set |
| CLEAR_SPENT, LOCK_CARD | as is | as is |

### 5.3 Rules the applet must keep

1. **No signature without a slot burned.** The only signing paths are SPEND
   and AUTH. SPEND marks the slot spent before the signature leaves, as now.
2. **The PIN gate is the first statement** of every gated command, in every
   PIN state, including blocked. One test walks every command through every
   state.
3. **A limit per PIN entry.** After VERIFY_PIN the card adds up the amounts
   it has signed in this session and refuses a SPEND that would pass the
   limit. A terminal that wants more must have the PIN typed again, and the
   holder sets the limit from their own phone. The count is in RAM and ends
   with the tap.
4. **Three tries**, then blocked for good, as upstream. The refund path (6)
   is what makes that survivable.
5. **Nothing can be added without a PIN set.** Upstream ships cards with no
   PIN, on which any reader in range can write and spend.
6. Every buffer allocated at install, and the status byte written last, as
   upstream's D10 and D14.

### 5.4 Provisioning

- Card: NXP J3R180, dual interface, supplied with known keys.
- Build from the fork's tag; compare the CAP's hash with the one CI made
  before installing.
- After installing: put the card's GlobalPlatform keys to new random ones and
  write them down, or lock the issuer domain. A card left on factory keys can
  have the applet deleted, and the money with it, by anyone with a reader.
- The card's key is made on the card at install and never leaves it.

### 5.5 How it is tested

- jCardSim: every command in every PIN state; SPEND's message against the
  secret Foxy builds for the same slot, byte for byte, for dates of zero and
  not; the limit; AUTH's signature never verifying as a spend; torn writes
  (the upstream slot-order tests, extended to the new fields).
- BIP-340 vectors, as upstream.
- On a card, by hand: install, set PIN, load, spend, change written back,
  wrong PIN three times, a tap pulled away at each step.
- Measured on the first card, because the design leans on it: how long one
  SPEND takes over NFC from an iPhone. It decides how many pieces one tap may
  sign and whether a withdrawal of a full card needs more than one tap.

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
  the card's own signature, and the card has no clock to know. So the date is
  on the slot where a reader can see it. Foxy, receiving, refuses a piece
  within a week of its date, before asking the card to sign, and says the
  card needs renewing by its owner. The holder's own phone shows the date and
  offers to renew from a month before.
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
  write (PIN, pieces, card record). The PIN goes to the card and nowhere
  else: not kept, not logged, wiped when the session ends.
- iOS ends a session after a minute and shows its own sheet throughout; the
  sheet's line of text is set at each step (`Hold the card`, `Keep it there`,
  `Remove the card`).
- A simulator has no NFC. A stand-in card over a local port, in simulator
  builds only, as the tap link has, so the flows can be driven without
  hardware. The Java applet under jCardSim can be that stand-in, which tests
  the real applet against the real page.

## 8. Foxy: the money (wallet)

### 8.1 Being paid by a card, same mint

1. Read: AUTH, card record, slots. Refuse here, with nothing signed, if the
   mint differs, the balance is short, a piece is near its date, or a keyset
   is not this mint's.
2. Choose pieces: exact if the card has them, else the least over. Add the
   mint's fee for swapping them where it charges one; the payer's card pays
   it.
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
  can be written on the next tap.
- **Withdraw:** SPEND the pieces, swap into this phone's own. An in entry,
  `from card`.
- **Balance:** from the slots; with a connection, each piece's state asked of
  the mint.
- **Take back a lost card:** after the date, swap the pieces this phone
  recorded when it loaded them, signed with the refund key.

### 8.4 A card at another mint (second version)

The payment is made final for the payer by a swap at the card's mint, into
ecash this phone holds there; bringing it to this phone's own mint is then
this phone's business, by the road Foxy already has for moving between mints,
and its cost is shown to the receiver before the card is asked for anything.
Until then: A DIFFERENT MINT, and nothing is taken.

## 9. Foxy: the screens (app)

- Receive: a CARD button beside TAP; the PIN pad with the amount in its
  title; the cards of 2.1.
- Menu › Flashcard: one screen, empty until a card is tapped, then the card's
  state and the buttons of 2.2.
- The PIN pad is the lock screen's, with its larger keys. A card PIN is 4 to
  8 digits.
- Render snapshots for each state; the wording above is the wording.

## 10. What it does not protect against

- A receiver's phone that has been altered can show one amount and ask the
  card for another, up to the card's limit. The limit, and looking at the
  card's balance afterwards, are the defences; it is the position of any card
  and any terminal.
- A pretend card can collect a PIN typed for it. AUTH lets a holder's own
  phone know its card; a receiver has no way to know a stranger's card is
  real, and loses nothing if it is not.
- The PIN is in the clear over a few centimetres of air until phase 2.
- Whoever holds the refund key's words can take a card's money after its
  date.

## 11. Order of work, and how much

| Phase | What | Rough effort |
|---|---|---|
| 0 | Paid developer membership; cards and a reader in hand; fork made private | yours |
| 1a | Applet: card record, slot date, SPEND from the slot, SIGN_ARBITRARY out, AUTH, limit, no load without a PIN; jCardSim tests | 3 to 4 days |
| 1b | Swift: CoreNFC session, commands, bridge, stand-in for the simulator, unit tests | 2 to 3 days |
| 1c | Wallet: pay by card at the same mint, change, held and lost answers, add funds, withdraw, balance, the refund path; suites against the stand-in | 4 to 5 days |
| 1d | Screens, wording, snapshots; documents; phone checks | 2 days |
| 1e | On real cards: install, timing, every flow, every pulled tap | 2 to 4 days, set by how many rounds the hardware needs |
| 2 | A card at another mint; the PIN under a key; unblock by a second code | 1 to 2 weeks |
| 3 | A receiver with no connection: DLEQ on the card, an issuer's certificate, a list of withdrawn cards | a design of its own; weeks |

Phase 1 is about three weeks of the way this project is worked now. The
applet and the wallet can be written and tested without a card; phases 1b and
1e cannot be finished without the membership and the hardware.

## 12. For you to decide

1. **Recovery.** A refund key and a year's date on every piece (recommended),
   or cash-like cards with nothing behind them, or the choice per card.
2. **The limit.** A default for a new card: none, or a figure.
3. **Same mint first.** Agree that the first version refuses a card at
   another mint.
4. **Upstream.** Tell them what was found before the fork is public, and
   whether to offer the changes back. The fork changes the slot and two
   commands, so an upstream card and a fork card do not work in each other's
   terminals.
5. **The name.** "Flashcard" is close to their product's. The menu can say
   CARD.
