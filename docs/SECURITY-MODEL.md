# Security model

What this card protects, what it does not, and which weaknesses are accepted
versus unresolved.

Written to be usable by an auditor. Where something is untested, it says so
rather than asserting a property nobody has measured.

## The one guarantee

**A proof cannot be redeemed without a signature from the card that holds it.**

Proofs are P2PK-locked ([NUT-11](https://github.com/cashubtc/nuts/blob/main/11.md))
to a secp256k1 key generated inside the secure element and never exported. Every
other property below is weaker than this one, and most attacks are attempts to
get around it rather than through it.

## Threat table

| # | Threat | Protected? | Notes |
|---|---|---|---|
| 1 | Passive read of card memory | **Partially** | An attacker gets keyset id, amount, nonce and `C` — the secret string is never stored on the card, but it is reconstructible from the nonce plus the card pubkey, so treat it as leaked too. What does not leak is the private key, so the proofs stay unspendable. Balance and history leak. From software 1.20 a reader also gets the mint's proof for each unspent piece that has one, and so its blinding factor (#16). From 1.21 it no longer gets the blinding factor of the change the card made for itself and has not yet loaded (#17). |
| 2 | Hostile reader spends the card | **Once a PIN is set** | `SPEND_PROOF` and `SIGN_ARBITRARY` are gated on `VERIFY_PIN` in the same session (D13), and a failed PIN check ends that session. A card with no PIN set — the factory state — can be drained by anyone in NFC range, so the holder must set one before carrying value. Three wrong tries block the card; the gate keeps refusing in the blocked state (ENG-615 closed a bug where it stopped), and there is no unblock path (D13). That dead end is its own threat: see #14. |
| 3 | Card lost or destroyed | ❌ **By design** | No seed, no backup, no recovery. See [D5](DECISIONS.md#d5). |
| 4 | Cloning the chip | **Yes** | Cloning EEPROM copies the proofs but not the key; a clone cannot sign. Cards should be CC EAL 5+ to resist invasive extraction. |
| 5 | Offline double-spend from copied data | ❌ **No** | Fundamental. An offline merchant cannot know a proof was already melted. See below. |
| 6 | Replaying a signature on another proof | **Yes** | The signed message is `sha256(secret)`, unique per proof. A signature does not transfer. |
| 7 | Malicious mint tagging users | **Yes** | NUT-12 DLEQ, verified client-side. See [D11](DECISIONS.md#d11). |
| 8 | Mint insolvency | **Monitored, not prevented** | The mint is trusted for solvency. See below. |
| 9 | Malicious terminal | **Partially** | Cannot forge proofs or spend them elsewhere. Can lie about the amount, or take the tap and never settle — ordinary merchant risk. |
| 10 | Fault injection to recover the key | **Mitigated** | Aux randomness in the nonce prevents the two-signatures-same-`k` recovery. See [D9](DECISIONS.md#d9). |
| 11 | Tear-off (card pulled mid-write) | **Yes, by write order** (applet 0.4 on); not yet on silicon | Every slot write changes the data first and commits the status byte last, as a single byte the JCRE writes atomically ([D14](DECISIONS.md#d14)). A card whose `SELECT` answers below `00 04` may write the status byte first and needs the CAP reinstalled. A torn `LOAD_PROOF` leaves the slot empty and a torn `CLEAR_SPENT` leaves it spent; neither state is counted in the balance or signed for. The empty slot's bytes are never read. The half-cleared spent slot still answers `GET_PROOF`, as status `02` with some fields zeroed, and its data is not a proof: a reader that uses a spent slot's data must check it ([`spec/APDU.md`](../spec/APDU.md#clear_spent-0x31)), and `cardctl dump` skips one that fails. `SPEND_PROOF` writes only the status byte, before it signs ([D7](DECISIONS.md#d7)): a tear after the burn loses that signature, which flash-pos re-derives with `SIGN_ARBITRARY` (`resignWitness`). The order is enforced by a source scan and the torn states are tested in jCardSim; no card has been pulled mid-write. |
| 12 | Counterfeit physical cards | **Not a concern** | Value is bound to the chip's key. A look-alike with no valid chip holds nothing. |
| 13 | Supply-chain / pre-personalised cards | ⚠️ **Unaddressed** | Nothing currently attests that a card's key was generated on-card by an untampered applet. |
| 14 | Hostile reader blocks the PIN (3 unauthenticated APDUs) | **A nuisance on a card with an owner**; ❌ **No** on a card with a PIN and no owner | `VERIFY_PIN` needs no authentication, so three wrong PINs from any reader in NFC range block the card. A card with an owner is opened again by its owner's phone: `CHANGE_PIN` takes the owner's proof in place of the old PIN, sets a new one and resets the tries ([spec](FOXY-CARD-SPEC.md) 5.6), so nothing is lost and the card cannot pay until its owner taps it. A card that was given a PIN and money but no owner has no such path: `SET_PIN` unblocks only an empty card, so its balance is stranded. See below. |
| 15 | Reading the NFC tag (software 1.19) | **Nothing of the card to read** | Any reader, with no PIN, gets the same bytes from every card: one web address. It names no card, key, owner or balance. A card on factory keys can have the tag's applet replaced by one carrying another address; rotating the card manager keys closes that. See below. |
| 16 | A piece on the card that the mint never made (software 1.20) | **Checked by the reader, not by the card** | The card keeps the mint's NUT-12 proof for a piece and gives it back; it checks nothing. A reader that checks it, with the mint's keys, sees that a made-up `C` is not real, and a piece copied from another card is not real here. It does not show that a real piece is unspent. Reading the proof gives away `r`, which ties a piece to the request that made it if the mint is given it. See below. |
| 17 | A stranger's tap after a payment (software 1.21) | **Bounded to the card's own change** | Up to 1.20 the next tap after any payment, by any reader, could load any piece and free the spent places with no PIN, until a load used the grant up. From 1.21 that tap may only turn the card's own openings of change into pieces (`LOAD_CHANGE`), each once, and only in that tap; the card unblinds them itself, with a blinding factor no reader is given. A stranger who takes that tap can still spoil the change of the payment before it (a key it made up, with a proof that holds for it: the card cannot tell a key is the mint's), and takes nothing. See below. |
| 18 | A reader claims a card nobody owns (software 1.21) | **Slowed, under the PIN** | A card with no owner holds nothing, and whoever sets its owner first has it. From 1.21 a claim wants the card's PIN given in the tap and ten askings in a row, a signature's work each, some seven seconds of the card held to the reader, and is never made in a tap that signed a payment. A reader that has been given the PIN can still claim an open card in that time; what is closed is the claim in the instant of a tap. |
| 19 | A locked card | **Still pays** | `LOCK_CARD` freezes every write and every setting; a locked card pays under its PIN and up to its no-PIN allowance as before, and its limit, its allowance and its PIN can no longer be changed. A lock is not a way to make a lost card safe. |

## The offline double-spend problem (#5)

The card marks a proof `SPENT` before releasing a signature, and no APDU can
unmark it. **That stops the card from spending twice. It does not stop anything
else.**

If an attacker copies a proof's data and separately obtains a signature over it,
they can redeem at the mint while the card still shows the proof unspent — or,
more simply, spend at an offline merchant who cannot check, then redeem the same
proof online before that merchant settles.

**This is inherent to every offline bearer instrument.** Physical cash addresses
it with anti-counterfeiting rather than double-spend detection, and so must this.

Practical mitigations, none of them cryptographic:

- The **mint is the final authority** — the second redemption fails.
- **Terminals should settle promptly** on regaining connectivity. Settlement lag
  is exactly the exposure window.
- **Online terminals must call `checkProofStates`** before accepting. There is
  no excuse when connectivity exists, and `cashu-client` treats `PENDING` as
  not-spendable for the same reason.
- **Keep card balances small.** The loss ceiling is the card balance.

Any claim that this design has "zero double-spend risk" is false. One external
documentation contribution asserted exactly that, which is part of why this
document exists.

## Mint solvency (#8)

A bearer proof is a claim on the mint. If the mint cannot honour it, the proof
is worthless regardless of how sound the cryptography is.

This is not hypothetical here. Flash Forge was found **insolvent** — roughly
$831 of outstanding bearer promises against $0.13 of backing — because the
Lightning backends had been drained while the ecash stayed in circulation.

Current controls:

- **[Public reserves attestation](https://forge.flashapp.me/reserves)**, refreshed
  every 15 minutes, comparing outstanding liability against real backend balances.
- **Automated solvency monitoring** with alerting, per unit.
- The mint's sat reserve is a **self-hosted phoenixd node** rather than a
  third-party custodial account.

Honest limits: this is an **operator attestation**, not a trustless proof of
reserves. A holder cannot cryptographically verify it. Signed attestations from
the reserve wallets would be the next step.

## PIN-gated spending, and the blocked PIN (#2, #14)

Spending is PIN-gated once a PIN is set ([D13](DECISIONS.md#d13), which
superseded the no-PIN [D12](DECISIONS.md#d12) and resolved the decision D12
had left open under ENG-209). `SPEND_PROOF`, `SIGN_ARBITRARY`, `LOAD_PROOF`,
`CLEAR_SPENT` and `LOCK_CARD` answer `6982` until `VERIFY_PIN` succeeds in the
same NFC session.
The gate is the first statement of the spend handler, so a wrong, missing or
blocked PIN never consumes a proof. The unverified-session case is proven on
silicon (applet 0.2 on the J3R180, in the
[hardware report](HARDWARE_TEST_REPORT_2026-09-22.j3r180.md)).

The gate does not cover a card with **no PIN**. Cards ship without one, and
until one is set a reader in range can drain the card. Setting a PIN is the
holder's first job. (In the Foxy fork from software 1.21 a card with no PIN
holds nothing and pays nothing: every load and payment command answers `6A97`
until it has one.)

**Software 1.16 moved the gate, and made the PIN optional.** In the Foxy fork a
spend is asked its PIN at the signature (`SPEND_ALL_SIGN`), not when it is begun,
because what leaves the card is only known there. A card with a PIN signs without
it only up to the no-PIN allowance the owner has set (a day's worth, counted in
the daily limit's window), and answers `6A94` over it, for the whole payment; a
blocked card has no allowance, and the answer is the same. A card with no PIN
signs, under its daily limit and its limit on one payment, and is cash to whoever
holds it. Neither writes anything but a signature: the PIN is asked before the
limits, so a reader with no PIN cannot fill the card's log.
`FOXY-CARD-DAILY-LIMIT.md` 6f has the whole.

**Software 1.21 makes the PIN the first thing.** The card of 1.16 to 1.20 with no
PIN, loaded, freed and spent by whoever held it, is gone: a card with no PIN
refuses every load, every payment command and an owner's claim (`6A97`), and an
owner is claimed only under a PIN, so no card has an owner and no PIN. The no-PIN
allowance stays, on a card that has a PIN.

**The blocked state had its own bug (ENG-615).** Every build before applet 0.3
gated on `pinState == 1`, and a blocked card has `pinState == 2`, so three
wrong PINs *removed* the gate: the card reported itself blocked and spent
without asking (0.1 builds from before D13 gate no spend at all, and their
write gate opened the same way). A failed PIN check also left a verified
session verified, so the session that blocked the card could keep spending.
Applet 0.3 gates whenever a PIN exists in any state, and a failed check ends
the session's verification, the way `OwnerPIN.check` resets its own validated
flag. The 0.3 behaviour is verified in jCardSim only; it has not yet run on
silicon. A card whose `SELECT` answers anything below `00 03` (`00 01` or
`00 02`) runs a vulnerable build and needs the CAP reinstalled, and since
[D14](DECISIONS.md#d14) so does one answering `00 03` (#11). Sweep it first: a
reinstall regenerates the key the proofs are locked to.

**A blocked card (#14).** `VERIFY_PIN` is unauthenticated, so any reader in NFC
range can send the three wrong PINs. What that costs depends on whether the
card has an owner.

*With an owner*, which is every card the Foxy app sets up: the owner's phone
opens it again. `CHANGE_PIN` takes the owner's proof in place of the old PIN,
on a card in any PIN state and holding any funds; it sets the new PIN, resets
the tries and unblocks ([spec](FOXY-CARD-SPEC.md) 5.6). The thief gets nothing,
the holder loses nothing, and the card cannot pay until its owner has tapped
it. Wrong-PIN spam is therefore a nuisance, and one coil at a venue can still
put every such card out of use for the day.

*With a PIN and no owner*: there is no way back. `SET_PIN` replaces a PIN only
on a card with no owner and nothing unspent, and nothing but the card key can
sign for the card's proofs (the P2PK secret carries only a `sigflag` tag, with
no refund key and no locktime, and D5's recovery proposal is not merged). Three
wrong PINs then destroy the balance. Do not put money on a card that has a PIN
and no owner.

This paragraph used to say that no card could be unblocked. That was true
before the owner's proof existed and is not true now.

## The NFC tag (#15, software 1.19)

From software 1.19 the card also answers as an NFC Forum Type 4 Tag carrying one web
address, so that a phone with its screen on offers to open it (`FOXY-CARD-SPEC.md` 5.8).

**What it reveals: nothing of the card.** The two files a reader can read are constants of
the applet, the same on every card (the capability container, and a message of 22 bytes
whose address is `https://getfoxy.io/card`). Nothing of the card's key, its owner, its PIN
state, its limits or its balance is in them or can be reached through them: the tag applet
holds no reference to the card's applet, keeps no state but the file chosen, and writes
nothing (every instruction that writes is `6982`). It asks the card's applet one question,
whether the tag is on, through a shareable interface of one method that answers a boolean
and is not exported to any other package. The address could not be per card without
becoming an identifier that anyone with a phone could read with no PIN; that is why it is
not, and a change that made it so would be a regression of this property.

What a reader does learn is that the card has a tag application and that its address is
Foxy's page: it is a card made for Foxy. Its other name, the card applet's, can be selected
by anyone as well and answers its version, so this adds little.

**Who holds the switch.** The owner, by the owner's proof under its own label
(`FoxyCard/tag`), with no PIN. A terminal that has been handed the PIN cannot turn the tag
off or on, and a wrong proof costs no PIN try. A locked card refuses the command, so a
locked card's tag stays as it was. `RESET_CARD` puts the tag back on. The switch is not a
security control for the money: it exists so that a card kept in a phone's wallet case does
not raise the offer every time the screen wakes. Off, the tag application refuses its
name, and nothing else about the card changes.

**A card left on factory keys.** Anyone with a reader and a card on factory keys can delete
the card's applet and the money with it (`FOXY-CARD-SPEC.md` 5.4). They can equally delete
the tag's applet and install another in its place, under the same name, carrying another
address, so that a phone that reads the card is sent somewhere else. Rotating the card
manager keys (or locking the issuer domain) closes both, and it is part of set-up for the
cards that are given to people.

## The mint's proof kept with each piece (#16, software 1.20)

From software 1.20 the card keeps, for a piece that is loaded with one, the mint's NUT-12
proof: `e`, `s` and the blinding factor `r`, 96 bytes. `GET_DLEQ` gives it back, with no
PIN, for an unspent piece (`FOXY-CARD-SPEC.md` 5.9, [D15](DECISIONS.md#d15)).

**What "real" rests on.** A phone that reads a piece and its proof can rebuild the piece's
secret, work out `C'` and `B'`, and check the proof against the mint's public key for the
amount. A piece that passes was signed by that key, over that secret. So "this piece is
real" rests on two things the reader has: the mint's keys, and the check it makes. It does
not rest on the card, which checks nothing and hands back what it was given, and it does not
rest on whoever loaded the card. A terminal that writes a piece with a made-up `C`, and any
proof it likes, makes a piece that fails the check: nobody without the mint's key can make a
proof that passes. A real piece copied from another card fails too, since its secret names
that card's key.

**What it does not show.**

- That the piece is unspent. Only the mint knows, and a copy of a real piece passes the
  check as the real piece does (#5).
- That the mint will honour it (#8).
- That a piece with no proof is fake. A piece put on plain, or a card of earlier software,
  is "not shown real", and the reader decides what that means to it.
- Anything, to a reader that does not check. The card does not refuse a piece whose proof
  fails or is missing, and a terminal with the PIN can still write a piece the mint will
  refuse (`FOXY-CARD-SPEC.md` 10). What changed is that a reader can now tell.

**What it costs: `r`.** The proof carries the blinding factor. The mint made its signature for
`B'`, and given `r`, whoever has the mint's record of that issue can recompute `B'` and `C'`
from the piece and tie the piece to the request that made it. That is so of any NUT-12 token
that carries `r`; here, any reader can read `r` for every unspent piece that has a proof,
with no PIN. (Up to 1.20 `GET_CHANGE` also gave it for the change the card made for itself;
from 1.21 those factors never leave the card, and the card's own change keeps no proof in
the store: #17.) A reader that hands `r` to the mint lets the mint make the tie; one that checks on its own
side and sends the proof nowhere does not. The money is as safe as it was: a piece is
spent only with the card's signature, and nothing here gives that.

**The store.** One persistent array of 12,288 bytes, reserved by the first `SET_CARD` and
not at install, because the chip will not install an applet that asks for that much more in
its constructor (`FOXY-CARD-HARDWARE.md`). A card that is refused it works without. A place
that is unspent holds its own proof or zeros and never another piece's: `LOAD_PROOF` writes
the record before the place's data and the status byte last, `CLEAR_SPENT` and the payment
commands write nothing to the store, and `RESET_CARD` zeros it. A card taken away in a load
leaves an empty place with a record beside it, which nothing serves.

## The tap after a payment, and the card's own change (#17, software 1.21)

A till lets the card go once it has signed, and writes the change at a second tap. Up to
1.20 that tap opened `LOAD_PROOF` and `CLEAR_SPENT` with no PIN, for any piece, to whatever
reader came next, until a load used it up: a stranger's reader between the payment and the
till's change tap could fill the card's empty places with junk and wipe the spent places,
which are the card's own account of what it paid. And `GET_CHANGE` gave every reader the
blinding factor of each opening of the card's change, so any reader that saw the mint's
answer could finish the piece.

From 1.21:

- A payment notes the openings of change it made, a bit each, in its own transaction. The
  first `SELECT` of the next time in the field takes the note into that tap and clears it.
  In that tap `LOAD_CHANGE` of a noted opening needs no PIN, once. Nothing else opens: no
  `LOAD_PROOF`, no `CLEAR_SPENT`, no older opening. The tap ends, and the grant with it.
- `GET_CHANGE` gives each opening's number, amount, keyset, date and blinded message, and
  never its nonce or blinding factor. `LOAD_CHANGE` takes the mint's blinded signature, the
  mint's key and its NUT-12 proof, checks the proof against the card's own blinded message,
  and takes the blinding factor off on the card. So the only piece the grant can put on is a
  piece of the card's own opening, with the opening's own nonce and amount.
- `GET_INFO`'s byte 29 says change may be loaded only to the PIN or the owner's grant.

What is left: the card cannot tell that the key it is given is the mint's. A stranger whose
reader is the first after a payment can make up a key, sign the opening's blinded message
with it, prove that with a proof that holds, and so write a piece under the card's own
opening that the mint will not take. That spends the holder's change of that payment; it
takes nothing and adds nothing of the stranger's. Binding the key at the payment, when the
till that has the PIN asks for the change, would close it.

## What has and has not been tested

**Verified in simulation (jCardSim) and by construction:**
- BIP-340 signatures verify against an independent verifier and the spec's own vectors
- Modular arithmetic matches `BigInteger` across random and edge inputs
- Slot lifecycle, PIN gating, APDU encodings
- The tag (1.19), byte for byte, the switch and its refusals, and that nothing the tag
  says differs between two cards
- The mint's proof kept with a piece (1.20): given back as it was loaded, never served for
  a spent, empty or plain place, and never another piece's through any long history of
  loads, payments and clears; every refusal of the load with nothing written; no payment
  command writing the store; the store null after install and made by the first
  `SET_CARD`; the order of the writes and the torn states. A reader's check of the proof is
  not the card's and is tested where the reader is
- The blocked PIN (applet 0.3): every gated command refuses, before and after
  further PIN attempts, and a failed PIN check ends the session
- A PIN first, the tap after a payment, the card's own change and the claim (1.21):
  every load and payment command and a claim refused on a card with no PIN; the
  tap after a payment loading that payment's change once for each opening and
  nothing else, and gone at the next tap; `LOAD_CHANGE` writing `C = k·Y` as an
  independent computation has it, and refusing every answer that is not the mint's
  signature on that opening; `GET_CHANGE` never giving a nonce or a blinding factor;
  the claim's ten askings, its restart and its exclusion of a payment
- DLEQ verification, including the tagging attack it exists to catch
- NUT-11 witness checks: wrong key, wrong proof, corrupted signature, `n_sigs`

**Run on silicon** (NXP JCOP4 J3R180, 2026-09-22/23, recorded in the
[hardware report](HARDWARE_TEST_REPORT_2026-09-22.j3r180.md); applet 0.1, then
0.2 for the D13 probes):
- `cardctl selftest`: card signatures verify under BIP-340, and two signatures
  over the same message use different nonces
- Load, spend, swap and NUT-05 melt against the project mint, and a spend from
  the merchant terminal
- `SET_PIN`, `VERIFY_PIN` (wrong, then right) and `CHANGE_PIN` with the right
  current PIN
- The unverified-session gate: `LOAD_PROOF` and `CLEAR_SPENT` refuse on 0.1,
  `SPEND_PROOF` refuses on 0.2
- The tag (1.19): installed under the NFC Forum's name beside the card's applet,
  read from outside as a phone reads it, and read by a phone, which offered its
  address
- The mint's proof (1.20): the build installs, the first `SET_CARD` is given the
  store after the install, a piece loaded with a proof gives the same 96 bytes
  back, a plain piece and a place used again read `00`, a payment and a reset go
  through, and `GET_INFO` is the length it was (`tools/chip/proofstore.sh`, 27
  checks). Not run there: a card pulled away during the first `SET_CARD` or a
  load with a proof. From a phone, a load with proofs takes about 0.09 s a piece

An earlier card, a J3R452 on applet 0.1, ran the read and sign path on
2026-09-01 ([its report](HARDWARE_TEST_REPORT_2026-09-01.j3r452.md)).

**Not yet run on silicon:**
- Software 1.21: its install at its own size, `LOAD_CHANGE` and how long it takes
  (estimated about a second through a contact reader from the figures measured
  for each operation), the claim's seconds, and the grant across two selections
  in one tap from a phone
- The tag's switch (1.19): `SET_TAG` on a card, and with it the shareable lookup
  between two applets of one package
- A blocked PIN on any build: neither ENG-615 nor its 0.3 fix (the
  blocked-state gate and the failed-check session rule) has run on a card
- `LOCK_CARD`, deliberately not run on a card holding value
- EEPROM wear and lifetime
- A card pulled mid-write: the slot write order ([D14](DECISIONS.md#d14)) is
  scanned in the source and its torn states tested in jCardSim, but no card
  has been torn on purpose; power glitches short of a clean tear are
  unanalysed
- Timing/side-channel characteristics of the hand-rolled modular arithmetic
- RF range, and whether a drain attack is practical at distance

**Two bugs found in review were invisible to the simulator and would each have
been fatal in the field:** a dropped carry in the modular reduction that made
every signature invalid, and an EEPROM leak that would have bricked cards after
a few hundred taps. Treat simulator results accordingly — see
[D10](DECISIONS.md#d10).

## Open items

1. **A blocked card with no owner** (#14) — a card with an owner is unblocked
   by its owner's phone, which closes what ENG-617 (`UNBLOCK_PIN` + PUK) was
   opened for. What is left is the card with a PIN, money and no owner, which
   three unauthenticated APDUs still strand; and that anyone in range can put
   an owned card out of use until its owner taps it
   ([D13](DECISIONS.md#d13)).
2. **Tear-off on silicon** (#11) — the write order is analysed and enforced
   ([D14](DECISIONS.md#d14)); pulling a card mid-`LOAD_PROOF` and
   mid-`CLEAR_SPENT` on hardware is still owed.
3. **Recovery** (#3) — [PR #4](https://github.com/lnflash/cashu-javacard/pull/4),
   closed unmerged, is the starting point; see [D5](DECISIONS.md#d5) for the
   flaw to fix first. A refund path would also rescue a blocked card's balance
   (#14).
4. **Card attestation** (#13) — no proof a key was generated on-card by genuine
   firmware.
5. **Side-channel review** of `SchnorrHW` — the modular arithmetic was written
   for correctness, with no constant-time analysis.
6. **Trustless proof of reserves** — upgrade the attestation to signed
   statements from the reserve wallets.

## Reporting a vulnerability

See [`SECURITY.md`](../SECURITY.md). Please do not open a public issue for
anything affecting funds.
