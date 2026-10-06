# Flashcard: the screens and what is on each

For whoever designs them. What each screen is for, its elements, and its
states. Wording is a first draft. Kept out of commits with the spec.

Three things are not designable:
- **The card tap sheet is Apple's.** It slides up over the app whenever the
  phone is looking for a card. Only one line of text on it is ours, and it can
  change during a tap. Our own screen sits behind it.
- **A tap can take several seconds.** The card must stay on the phone while the
  mint is asked, so "keep it there" and "remove it" are states to show.
- **The PIN pad** is the lock screen's pad, with its large keys.

## A. Paying by card (the receiver's phone)

### A1. Receive invoice screen (exists)
- New: a **CARD** button, beside TAP.

### A2. Card PIN
The payer types their card's PIN on the receiver's phone.
- Title: the amount and "CARD PIN" (`PAY 1,180 SATS · CARD PIN`)
- PIN dots, 4 to 8
- Key pad
- CANCEL
- A line under the title when coming back from a wrong PIN: `Wrong PIN. 2 tries left.`

### A3. Tapping (behind Apple's sheet, and after it)
One screen, five states:
- `HOLD THE CARD TO THE TOP OF THE PHONE`
- `READING THE CARD`
- `KEEP THE CARD THERE` (the mint is being asked; a few seconds)
- `PUTTING CHANGE BACK ON THE CARD`
- `REMOVE THE CARD`
- The amount stays on screen throughout. CANCEL only in the first state.

### A4. Results
Each is a card with a title, a line or two, and one or two buttons.

| Result | Says | Buttons |
|---|---|---|
| PAID | the usual paid confirmation, marked CARD | DONE |
| WRONG PIN | `2 tries left. Nothing was taken.` | TRY AGAIN, CANCEL |
| CARD BLOCKED | `Too many wrong PINs. Its owner can take the money back with their own phone.` | OK |
| NOT ENOUGH ON THE CARD | `It holds 900 sats.` | OK |
| A DIFFERENT MINT | `This card's money is at <mint>. This phone is at <mint>.` | OK |
| CARD NEEDS RENEWING | `Its owner must renew it with their own phone before it can pay.` | OK |
| NO MONEY ON THIS CARD | for a card with no PIN or nothing loaded | OK |
| NOT A FOXY CARD | the card could not be read or is another kind | OK |
| NOT PAID | `The mint could not be reached. Nothing was taken.` | TRY AGAIN, CANCEL |
| CHECKING | `The card has signed and the mint has not answered yet.` a spinner; ends as PAID or as the next one | none |
| TAP THE CARD AGAIN | `The payment is made. 212 sats of change are waiting to go back on the card.` | TAP CARD, LATER |

TAP THE CARD AGAIN is the one that can outlive the screen: see D2.

## B. Menu › Flashcard (the holder's own phone)

### B1. Menu
- New item: **FLASHCARD** (or CARD).

### B2. Flashcard, nothing tapped yet
- A picture or mark for the card
- One line: what this is for
- **TAP CARD**
- Below, if this phone has loaded cards before: `CARDS YOU LOADED` (opens B9)

### B3. Flashcard, a card read
- **Balance**, large
- Under it, one of: `checked with the mint` · `the card says 2,048; the mint says 1,024 of it is spent` · `not checked: no connection`
- Mint name
- `12 pieces · 52 places free`
- State chip: `NO PIN YET` · `PIN SET` · `BLOCKED` · `LOCKED`
- Recovery line: `Can be taken back by this phone after 3 Oct 2027` · `Not recoverable: this card is cash` · when near: `Renew by 3 Oct 2027` in the warning colour
- Limit line: `Most one PIN entry can spend: 5,000 sats` · `No limit`
- Buttons: **ADD FUNDS**, **WITHDRAW**, then smaller: CHANGE PIN, SET LIMIT, RENEW (only when near its date), TAP AGAIN
- For a card this phone does not know: the same, with no recovery line of its own
- For a blocked card: the balance, the chip, and one line saying what can be done (take back after its date, if this phone loaded it)

### B4. A new card
Shown instead of B3's buttons when the card has no PIN.
- `THIS CARD IS NEW`
- **SET A PIN**: PIN pad, typed twice (`CHOOSE A PIN`, `TYPE IT AGAIN`)
- A choice, with a line under each:
  - `RECOVERABLE` — `If the card is lost or blocked, this phone can take the money back after a year.`
  - `LIKE CASH` — `Lose the card and the money is gone. Choose this for a gift.`
- The mint the card will be for (this phone's), shown, not chosen
- **TAP THE CARD TO FINISH**

### B5. Add funds
- Amount, with the key pad Foxy uses for amounts
- `You have 21,400 sats` · `The card can take 52 more pieces`
- NEXT → card PIN (A2's pad, titled `ADD 2,000 SATS · CARD PIN`) → tap (A3's states, worded for loading: `PUTTING 2,000 SATS ON THE CARD`)
- Results: `ON THE CARD` with the new balance · `TAP THE CARD AGAIN: 1,000 of 2,000 sats are on it` · WRONG PIN · NOT ENOUGH (this phone's balance) · NOT PAID

### B6. Withdraw
- Title: `ENTER PIN TO WITHDRAW`
- A choice: `ALL OF IT (2,048 sats)` or an amount
- PIN pad → tap
- Results: `2,048 SATS ARE IN YOUR WALLET` · WRONG PIN · CHECKING · NOT PAID

### B7. Change PIN
- Three pads in turn: `CURRENT PIN`, `NEW PIN`, `NEW PIN AGAIN` → tap
- Results: `PIN CHANGED` · WRONG PIN (tries left)

### B8. Set limit
- An amount, or `NO LIMIT`
- One line: `The most a terminal can take for one typing of the PIN.`
- PIN pad → tap → `LIMIT SET`

### B9. Cards you loaded (taking back a lost card)
- A list. Each row: a short name for the card (the last characters of its key), what was on it when last seen, and `can be taken back after 3 Oct 2027` or `CAN BE TAKEN BACK NOW`
- A row opens: the amount, the date, **TAKE IT BACK** (only after the date), and a line: `Use this only if the card is lost or blocked. The card will be empty.`
- Result: `1,024 SATS ARE BACK IN YOUR WALLET` · `Nothing was left on it`

## C. History (exists)
- Three new kinds of entry, in the existing row style: `FROM A CARD` (a payment received), `TO CARD` (funds added), `FROM CARD` (a withdrawal or a take-back).
- Their detail screens are the existing one. No new elements.

## D. Notices that live outside the Flashcard screen

### D1. On the holder's phone
- `1,000 sats are on their way to your card. Tap it to finish.` Where: under the balance on home, and on B3.

### D2. On the receiver's phone
- `212 sats of change are waiting for a card. Tap it to give them back.` Where: on the payment's own detail screen, and a small mark on HISTORY. It stays until the card is tapped.
