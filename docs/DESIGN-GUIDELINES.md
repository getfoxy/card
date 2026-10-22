# Submitting a card design

Anyone may propose a design for the face of a Foxy card. This page says what a
design is, what it may and may not contain, and how one is reviewed. The rules
exist so that no design can put a holder's money or privacy at risk, and each
one is enforced by the wallet's own software as well as by review. The codes
already taken are in [CARD-DESIGNS.md](CARD-DESIGNS.md).

**Status: draft.** Today every design ships inside the wallet. Fetching a
design by its code, for a card whose design the wallet does not have, is
planned and not built. These rules are written for both.

## What a design is

A design is **one picture and a few numbers**. Nothing else.

- The picture is the whole face of the card as the wallet shows it.
- The numbers say where the wallet may draw its own line of text over the
  picture, and in what colour (below).
- A design is named by a code of three characters, capital letters and
  digits. A code is never reused: a design that changes gets a new code, so a
  card printed with the old one is still drawn as it was printed.

A design is never markup, a stylesheet, a script, a font, a vector drawing or
a link. The wallet's page holds the holder's money, and nothing from outside
is ever given the means to draw controls on it or to run in it.

## The picture

| | |
|---|---|
| Shape | The proportions of a bank card, 1200 × 756 pixels or larger. It is shipped at 1200 × 756. No transparency. |
| Still | PNG or WebP. Shipped at 400 KB at most. |
| Moving | GIF, animated WebP or APNG: a loop of six seconds at most. Its first frame must stand alone: that frame is shown to anyone who has asked their phone for less motion. Shipped as an animated WebP of 1.5 MB at most, so a GIF's 256 colours are the submitter's limit and not the card's. |
| Not accepted | SVG, PDF, HTML, video, anything that names another file or address. |

Every accepted picture is decoded to pixels and written out again by the
maintainers. What ships is that copy, so nothing a file carried beside its
pixels survives: no location, author or device data, no comments, no colour
profile with text in it, no second file hidden after the first.

## What must stay clear

The wallet draws one line of its own on the card's face, at the bottom left:
the card's limits and whether it has a PIN. People rely on that line, so:

- Leave the bottom left plain: a band 66% of the card's width and 14% of its
  height, measured from the bottom left corner.
- Say which colour that line should be drawn in. Against every part of the
  band it must reach a contrast of 4.5 to 1.
- Keep the same band free in every frame of a moving design.

If the physical card is printed with the design, leave the contact chip's
place clear on the print, and draw nothing on screen that suggests the chip
is somewhere it is not.

## What a design may not show

**Nothing that could be taken for the wallet speaking.** No balance, amount,
limit, PIN, lock, tick, warning or status. No buttons, fields, keypads or
anything shaped like one. No words that tell the holder to do something.

**Nothing that leads elsewhere.** No web addresses, no QR codes or barcodes,
no handles, phone numbers or payment addresses. A card is tapped by strangers
at tills; a code or address on its face is an invitation to follow it.

**Nothing that names one card or one person.** A design is for many cards.
Its code can be read from a card by any phone with no PIN, so a design made
for one holder would be a label on that holder. No serial numbers, no
personal names or photographs, no design issued for a single card.

**Nothing that passes for something else.** No bank or card network marks, no
imitation of a real payment card, an identity document or a ticket, and no
imitation of another maker's design.

**Nothing you have no right to.** The work is yours, or you hold the right to
offer it. No content that is unlawful, hateful or sexual. The maintainers may
decline a design without giving a reason.

## What the wallet does, whatever a design contains

These hold even if a bad design were accepted by mistake.

- A design is shown as a picture and can do nothing a picture cannot.
- The wallet takes a design only from the list the maintainers publish, and
  only if the list's signature, made with a key kept offline, checks out and
  the picture matches the list. A failed check shows the plain default face.
- The numbers are held to fixed ranges, and the colour to a plain colour.
- Nothing is ever fetched from a submitter. The wallet asks only for a design
  code, over Tor, which names a design and not a card.
- A design can be withdrawn from the list. Wallets then go back to the plain
  face for it.

## How to submit

Open a pull request on this repository that adds a folder `designs/<CODE>/`
holding:

- `face.png` (or `.webp`): the picture as you made it.
- `design.json`: the design's name, its maker, the licence, the colour for
  the wallet's line, and whether it is still or moving.
- A row for the code in [CARD-DESIGNS.md](CARD-DESIGNS.md).

By submitting you license the picture for anyone to use, copy and show with
Foxy cards and wallets, under CC BY 4.0 unless you choose CC0.

A maintainer checks the submission against this page, writes the picture out
again as described above, adds it to the signed list, and merges. A design
that fails a rule is sent back with the rule it failed.

## The review, as a list

1. One picture, in an accepted format, of the right size and weight.
2. The band at the bottom left is plain, and the line's colour reads on it.
3. No words or shapes that could be taken for the wallet's own.
4. No address, code, handle or number that leads anywhere.
5. Nothing that names one card or one person.
6. No mark or imitation of another issuer, card or document.
7. The code is free, three characters, capitals and digits.
8. The licence is stated, and the work is the submitter's to give.
9. A moving design: its first frame stands alone, and the loop is short.
