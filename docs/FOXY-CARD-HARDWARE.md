# What the chip has room for

Foxy's card runs on an NXP J3R180 (JCOP 4, Java Card 3.0.5, 180 KB of
storage). The simulator the tests run in (jCardSim) has none of the limits
below, so each was found on the chip, usually the hard way. Each is given
with what was measured and what the card's software does about it.

## The first applet instantiated from a package must be small

The card goes mute, or answers `6F00`, at the INSTALL that both instantiates
its package for the first time and runs `CashuApplet`'s constructor, which
makes the card's key, sets the signer up and allocates about 45 KB of
storage. LOAD is accepted block by block and the last block (where the card
links the package) answers `9000` in the usual 730 ms; INSTALL then either
gets no answer at all (the reader gives up after five block waiting times,
about 7 s, and the card must be reset before it speaks again) or `6F00`
after about 1.3 s. Nothing is installed either way.

The same constructor installs in about a second once a small applet of the
same package has been instantiated before it (0.2 s). The order is what
matters: the same file with the big applet instantiated first fails again.

What was measured, on the way to that:

- Without an opener the failure is a sharp function of the load file's size
  and nothing else in it: 13,362 bytes installs, 13,377 does not, whatever
  the extra bytes are (code never run and data never read alike). Sending
  the file in more LOAD blocks, loading another package first, moving the
  code within the file, more arrays or larger ones, 544 bytes less working
  memory, a second of extra key making: none of these moved the line. A
  file that imports a second package and never calls it installs; one that
  calls into it at install does not.
- With an opener instantiated first, a file of 14,760 bytes installs, and
  its constructor leaves every one of its marks: it ran to the end.
- The reader is not the limit: a command of 14 s is answered through it.

The mechanism is not known for certain. What fits every measurement is a
budget the chip keeps for one INSTALL command (a journal for the atomic
install, most likely), charged with the package's own pages at its first
instantiation as well as with everything the constructor allocates; the
small applet pays the package's share in an INSTALL of its own.

What the software does: the package holds two applets, `Opener` first (AID
`F0464F5859434152444D`), which does nothing, and `CashuApplet`. The opener
is instantiated with the load, and the card's applet is created from the
package after it. `tools/refcheck.py` runs after every `ant cap` and refuses
a file whose first applet is not the opener. Deleting goes the other way:
the card's applet, the opener, then the package.

## One transaction holds about a dozen status bytes

A payment of 6 to 11 pieces was burned inside one transaction; 32 pieces
were refused with `6A96` (`TransactionException`, nothing burned). The
transaction's commit buffer on this chip is small, and jCardSim's is not.

What the software does: since 1.8 the burn is outside the transaction.
The places are written to `burnList` first, one transaction commits one
byte (`burnPending`) with the day, the log and the signature,
`finishBurn()` marks the places after, and `process()` finishes an
interrupted burn before anything else. Every transaction is the same size
whatever the number of pieces. A phone that meets a card without
capability bit 6 asks it for 8 pieces a signature at most.

## Working memory

The ISD reports about 2,486 bytes of free volatile memory on a clean card.
The applet asks for 931 bytes cleared on deselect and 7 cleared on reset,
and a unit test keeps it there. The first build of 1.9 asked for 1,030
and was refused at INSTALL with `6F00`; it was also the package's first
applet (above), so the two were never separated.

## Storage

A clean card has 164,176 bytes free. Software 1.7 takes 45,096 of them for
128 places and everything else. The figures come from the ISD's memory
tag, which needs no key:

    00A4040000          (choose the card manager)
    80CAFF2100          -> FF21 10 8102 nnnn 8204 <free storage> 8304 <free volatile>

## Times, through a contact reader

Measured with the card in an ACS ACR39U, one command at a time:

| command | about |
|---|---|
| key generation at install (the INSTALL step) | 1.0 s |
| SIGN (one signature, or one "not yet") | 0.7 s |
| AUTH | 0.7 s |
| LOAD_PROOF, three pieces in one command | 0.19 s |
| GET_PIECES, the whole list | 0.1 s |
| SET_TIME | 0.07 s |
| SELECT, GET_INFO, GET_BALANCE | 0.02 to 0.03 s |

So a drawer of about 100 pieces takes about 7 s to write through this
reader, and the listing reads around it cost as much again. A phone over
NFC is a different path and is measured there.

## The reader, when the card goes mute

The ACR39U's block waiting time is about 1.4 s. A card that stops
answering shows as `SCARD_E_NOT_TRANSACTED` after about 7.2 s (five
waits), and the reader will not speak to it again until the card is
reset. GlobalPlatformPro disconnects without a reset, so after such a
failure either pull the card out and push it back, or connect and
disconnect with a reset (`Card.disconnect(true)`), which
`tools/chip/reset.sh` does. The reader itself waits as long as the card
asks: a command of 14 s was answered through it.
