# Flashcard: the screens, as they are built

What each screen is for, what is on it, and its states, in the wallet as it
stands (the wallet repository, `build/app/26f-flashcard.js` and the FLASHCARD
block of `build/markup.html`). This was the designer's brief before the
screens existed; it now describes what was made, and where the two differ the
app is right. Pictures of every screen: the render snapshots
(`tests/snapshots/render`, the views named `flashcard…`, `card: …`,
`stage: …`, `amount, … a card`, `switchMint, …`, `fcMoveConfirm, …`).

Rules the screens keep to:
- **Nothing new where the app already has it.** Amounts are typed on the
  app's own SET AMOUNT screen, dollars first and sats under them. The PIN pad
  is the lock screen's pad. Results are the app's own cards. The card's own
  screen is home's layout, element for element.
- **A way back at the top left, never CANCEL at the bottom.** The button that
  commits says what it does and how much (`PAY $0.43`, `ADD $2.00 TO CARD`),
  and is grey until it can be pressed.
- **The card tap sheet is Apple's.** It slides up whenever the phone is
  looking for a card. One line of text on it is ours and changes during a
  tap. Our own screen sits behind it and says the same.
- **Cards are cash for now.** A card that can be taken back by the phone that
  loaded it is built and switched off (`FC_RECOVERABLE`); what belongs to it
  is listed in E and is on no screen.

## A. Paying by card (the receiver's phone)

### A1. Receive invoice screen (the app's own)
- A **CARD** button beside TAP.

### A2. Card PIN
The payer types their card's PIN on the receiver's phone.
- A back button at the top left
- Title `CARD PIN`; under it `To pay $0.43 (₿500). The card's owner types its PIN here.`
- PIN dots, 4 to 8; the key pad
- After a wrong PIN, a red line: `Wrong PIN. 2 tries left.`
- The button: `PAY $0.43`, grey until four digits are in

### A3. Tapping (behind Apple's sheet, and after it)
One screen, with the amount on it throughout:
- `HOLD THE CARD TO THE TOP OF THE PHONE` (CANCEL only here)
- `READING THE CARD`
- `KEEP THE CARD THERE` (the card signs, then the mint is asked: a few seconds)
- `PUTTING CHANGE BACK ON THE CARD`
- `WRITING TO THE CARD`
- `REMOVE THE CARD`

### A4. Results
Each is one of the app's cards: a title, a line or two, one or two buttons.

| Result | Says |
|---|---|
| the paid confirmation | the usual one; the payment's entry is marked as a card's |
| WRONG PIN | the tries left, and that nothing was taken; TRY AGAIN |
| CARD BLOCKED | too many wrong PINs |
| NOT ENOUGH ON THE CARD | what it holds |
| OVER THE CARD'S LIMIT | the most one PIN entry may spend |
| A DIFFERENT MINT | the card's mint and this phone's |
| NO MONEY ON THIS CARD | a card with no PIN, or nothing loaded |
| NOT A FOXY CARD | it could not be read, or is another kind |
| NO CARD READER | this phone, or this build, cannot read cards |
| THE CARD LEFT TOO SOON | nothing was paid; what the card signed for goes back on it |
| CHECKING, then STILL CHECKING | the card has signed and the mint has not answered; ends as paid or as money to go back on the card |
| TAP THE CARD AGAIN | the payment is made and change is waiting to go back on the card; TAP CARD, LATER |

TAP THE CARD AGAIN is the one that outlives the screen: see D2.

## B. Menu › FLASHCARD (the holder's own phone)

### B1. Menu
- **FLASHCARD**. It goes straight to Apple's sheet: there is no screen to
  press TAP CARD on first.

### B2. The card's screen
Home's layout, so the two read as one design:
- **Top left:** the round history button. It opens this card's history (B8).
  Hidden for a card that is not set up.
- **Top right:** a round button with a cross. It closes the screen.
- **Between them:** `FLASHCARD`, and under it how fresh the mint's word is:
  `Verifying…`, `Verified Just Now`, on a phone with no connection
  `Verified 2 Hours Ago` (the last time this phone had the mint's word on the
  same pieces), or `Not Verified`.
- **The card**, drawn in its design (`docs/CARD-DESIGNS.md`; FL1 today). One
  line on its face, bottom left, only for what is not ordinary: `NO PIN YET`,
  `NOT FINISHED`, `LOCKED`, `BLOCKED`, or `THE MINT SAYS ₿1,024 IS ALREADY
  SPENT`.
- **CARD BALANCE**: home's panel and home's pill. The mint's tile and name on
  the left (not a button); the balance on the right, dollars over sats.
  Tapping the balance opens this card's history.
- A line in the warning colour when money is waiting to go onto a card
  (`₿500 is waiting to go onto this card. Press here, then tap it.`).
- **ADD FUNDS** and **WITHDRAW** where home has RECEIVE and SEND;
  **CHANGE PIN** and **SET LIMIT** where it has SCAN and PASTE.
- The connection's banner at the foot, as on home.

A new card has, in place of the pill and the buttons, one line (`This card is
new. Give it a PIN to put money on it.`) and **SET UP THIS CARD**. A blocked
card has the pill, a red line saying what is on it cannot be got back, and no
buttons.

The card's limit is not shown on this screen. Neither is how many pieces it
holds, nor what happens if it is lost.

### B3. Setting up a new card
- `CHOOSE A PIN` (`Four to eight digits. The card asks for it every time it
  pays.`), then `TYPE IT AGAIN`; NEXT under each
- A tap
- `THE CARD IS READY`: that it is cash, and that a lost card, a forgotten PIN
  or three wrong PINs in a row lose what is on it. ADD FUNDS, LATER

### B4. Add funds
- The app's SET AMOUNT screen, NEXT
- `CARD PIN`, the button `ADD $2.00 TO CARD`
- A tap; `GETTING IT READY`, then the states of A3
- `ON THE CARD` with the new balance; or the card left early and the money
  waits for it (the line of B2); WRONG PIN; THE CARD IS FULL

### B5. Withdraw
- SET AMOUNT, with `ALL OF IT ($1.31)` under NEXT
- `ENTER PIN TO WITHDRAW`; a tap
- `IN YOUR WALLET`; WRONG PIN; OVER THE CARD'S LIMIT (with SET LIMIT);
  CHECKING

### B6. Change PIN
- Three pads in turn: the PIN as it is now, the new one, the new one again; a tap
- `PIN CHANGED`; WRONG PIN

### B7. Set limit
- SET AMOUNT in sats, with `NO LIMIT` under NEXT
- The PIN, a tap. The limit is the most one PIN entry may spend; the card
  has no clock and cannot count a day.

### B8. A card's history
- The app's HISTORY screen, titled `CARD HISTORY`, with only the entries that
  name this card: money put on it, money taken off it, and payments taken
  from it on this phone. The wallet's running balance and its audit card are
  left off, and there is no button to clear it.
- A card keeps no list of its own. What it paid at somebody else's phone is
  not here.

### B9. Moving a card to another mint (built, with no button for now)
- The app's list of mints, titled `TO WHICH MINT?`, without the card's own
- The app's CONFIRMATION: where to, where from, about what arrives on the
  card, and the most the fee can be; `MOVE ₿2,000`
- `CARD PIN`, once; then two taps with the Lightning payment between them:
  the money comes off the card, crosses, and goes back on at the new mint
- `MOVED`; or the card was put away before its second tap and the money
  waits for it at the new mint; or `NOT MOVED`, with the money in this phone
- A card with nothing on it: its PIN and one tap, no confirmation
- It works whichever mint the phone is at, and puts the phone back after

## C. History (the app's own)
- Three kinds of entry in the existing row style: a payment taken from a
  card, money put on a card, money taken off one.
- A holder's own moves to and from their card are not announced a second
  time; a payment from a card is, like any other.

## D. Notices that live outside the card's screen

### D1. On the holder's phone
- Money made for a card and not yet on it: the line on the card's screen
  (B2). Not yet said on home.

### D2. On the receiver's phone
- Change waiting for a payer's card: on that payment's own entry, which says
  to tap the card. It stays until the card is tapped.

## E. Built, and on no screen while cards are cash

- Choosing how a lost card is treated when it is set up (`IF THE CARD IS LOST`)
- The screen before a card is tapped, with `CARDS YOU LOADED` under it
- Taking a lost card's money back after its date (`TAKE IT BACK?`, `BACK IN
  YOUR WALLET`, `NOTHING WAS LEFT ON IT`)
- Renewing a card in its last month, from a line on its screen; `CARD NEEDS
  RENEWING` and `ITS DATE HAS PASSED` at a till
