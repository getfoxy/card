# Card designs

Each design a card's face can have is named by a code of three characters:
capital letters and digits, nothing else. Foxy draws a card in the design its
code names.

A card maker who wants a design of their own picks a code that is not in the
table below, adds a row for it, and adds its drawing to the wallet. A code is
taken once it is in this table, and is never used for a second design: a
design that changes gets a new code (FL1, then FL2), so a card printed with
the old one is still drawn as it was printed.

| code | design | whose | drawn in Foxy |
|---|---|---|---|
| FL1 | Flash, first design: black card, the Flash bolt in a circle at its centre, chip at the top left, BEARER at the bottom right | Flash | yes |

Where the wallet keeps them: `FC_DESIGNS` in `build/app/26f-flashcard.js` of
the wallet repository, and the drawing itself in `build/markup.html` (the card
on the FLASHCARD screen).

## Not decided yet

A card does not yet say which design it is. Every card is drawn as FL1. For a
card to name its design, the code has to be written on the card when it is
made: three bytes in the card record (`SET_CARD`, read back by `GET_CARD`),
which changes the record's layout and so the applet, its specification and
the wallet's reader together.
