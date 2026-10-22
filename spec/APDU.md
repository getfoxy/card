# APDU Command Reference

JavaCard Cashu Applet — AID: `D2 76 00 00 85 01 02`

All commands use `CLA = B0` unless noted. All multi-byte integers are big-endian.

This is upstream's command reference, with Foxy's software noted where it differs.
Foxy's applet has an AID of its own (`F0 46 4F 58 59 43 41 52 44`) and a wire of its
own; for every command of it, `docs/FOXY-CARD-DAILY-LIMIT.md` section 8 is the
reference, and where this page and that one differ, that one is what is built. Foxy's
software 1.21 (`SELECT` answers `01 15`) gives a card its PIN before anything goes on
it or leaves it, lets the tap after a payment load that payment's own change and
nothing else, unblinds the card's own change on the card ([LOAD_CHANGE](#load_change-0x53--software-121)),
and claims an owner slowly ([SET_OWNER](#set_owner-0x43--software-121)). Software 1.22
(`SELECT` answers `01 16`) sets up, loads and frees the places of a card with a PIN and no
owner under its PIN, the card of a holder with no phone, and holds that card's history for
its PIN ([GET_LOG](#get_log-0x18--software-122-on-a-card-with-no-owner)).

---


> **1.22:** the rules in FOXY-CARD-DAILY-LIMIT.md, section "1.22 in one place", win over anything below that still says a card with no owner takes no load, that the receipts are the owner's grant's alone, that a card with no owner holds nothing, or that SET_PIN, SET_CARD or the PIN form of SET_LIMIT is open to any reader on an empty card with no owner.

> **1.21:** the rules in FOXY-CARD-DAILY-LIMIT.md, section "1.21 in one place", win over anything below that still describes the tap after a payment as a grant, a claim answered `00 01`, loads under the owner's grant, or a no-PIN allowance renewed by the day.

## SELECT APPLICATION

Issued by reader before any other command. Standard ISO 7816-4 SELECT.

| Field | Value |
|-------|-------|
| CLA | 00 |
| INS | A4 |
| P1 | 04 |
| P2 | 00 |
| Lc | 07 |
| Data | `D2 76 00 00 85 01 02` |
| Response | 2-byte applet version (`MM mm`) + `90 00` |

The Data field above is the 7-byte **package** AID. ISO 7816-4 SELECT does
prefix matching, so it also selects the applet instance. Readers that decline
partial matches must retry with the full 8-byte **applet** AID
`D2 76 00 00 85 01 02 01` (Lc `08`) — that is exactly what `cardctl` does: the
7-byte form first, the 8-byte form as a fallback on any error. A reader that
sends neither selects nothing, and every subsequent command fails in a way that
looks like an uninstalled applet.

---

## Category 0x1x — Read (no authentication required)

### GET_INFO (0x01)

Returns applet version, capabilities, and slot statistics. Always available without authentication.

| Field | Value |
|-------|-------|
| CLA | B0 |
| INS | 01 |
| P1 | 00 |
| P2 | 00 |
| Le | 00 |

**Response (8 bytes):**

| Offset | Length | Description |
|--------|--------|-------------|
| 0 | 1 | Major version |
| 1 | 1 | Minor version |
| 2 | 1 | Max proof slots |
| 3 | 1 | Unspent proof count |
| 4 | 1 | Spent proof count |
| 5 | 1 | Empty slot count |
| 6 | 1 | Capabilities flags (see below) |
| 7 | 1 | PIN state (0=unset, 1=set, 2=locked — the PIN is blocked, tries exhausted) |

**Capabilities flags (byte 6):**

| Bit | Meaning |
|-----|---------|
| 0 | secp256k1 native (1) or software (0) |
| 1 | Schnorr signing supported |
| 2 | PIN protection available |
| 3–7 | Reserved (0) |

**In Foxy's software** the answer is longer than these eight bytes (see `docs/FOXY-CARD-SPEC.md` 5.2). Asked with P1 = 1 the answer is 42 bytes. From 1.19, asked with P1 = 2 it is those 42 bytes and one more, the 43rd, which says whether the NFC tag is on: `00` on, `01` off. From 1.20 `SELECT` answers `01 14` and those three answers are otherwise byte for byte what they were; nothing in them says whether the card has room for the mint's proofs (see [GET_DLEQ](#get_dleq-0x1a--software-120)). From 1.21 `SELECT` answers `01 15`, and byte 29 (whether this tap may still load change with no PIN) is said only to a reader that has given the PIN or the owner's grant in the tap, and is `00` to any other. Byte 7 is as above; on a 1.21 card a PIN state of 0 is a card that holds nothing and pays nothing. From 1.22 the limit on one payment (bytes 30 to 33 of the 42) is also told, on a card with no owner, to the PIN given in a tap in which the history was held for (`TAP_READ`); to any other reader it is zeros.

**PIN state 2 is not "no PIN".** A blocked card still has a PIN; it can never
be verified again (`VERIFY_PIN` answers `6983`), so every PIN-gated command
answers `6982` permanently. There is no unblock path in this profile. A reader
must treat 2 as "a PIN exists", never as 0. (In Foxy's software the owner's phone
unblocks it, `CHANGE_PIN` with the owner's proof, and the owner's grant still frees a
blocked card's spent slots with `CLEAR_SPENT`.)

---

### GET_PUBKEY (0x10)

Returns the card's secp256k1 public key (compressed, 33 bytes). This key is generated once at install and never exported in private form. Used by the mint to set NUT-11 P2PK spending conditions.

| Field | Value |
|-------|-------|
| CLA | B0 |
| INS | 10 |
| P1 | 00 |
| P2 | 00 |
| Le | 21 |
| Response | 33-byte compressed public key |

**Errors:**

| SW | Meaning |
|----|---------|
| 9000 | OK |
| 6F00 | Key encoding not recognised (hardware error) |

---

### GET_BALANCE (0x11)

Returns the sum of all unspent proof amounts as a 4-byte big-endian uint32. Unit matches the keyset unit (sats or cents).

| Field | Value |
|-------|-------|
| CLA | B0 |
| INS | 11 |
| P1 | 00 |
| P2 | 00 |
| Le | 04 |
| Response | 4-byte uint32 (big-endian) |

---

### GET_PROOF_COUNT (0x12)

Returns a 1-byte count of total proof slots that are non-empty (unspent + spent).

| Field | Value |
|-------|-------|
| CLA | B0 |
| INS | 12 |
| P1 | 00 |
| P2 | 00 |
| Le | 01 |
| Response | 1-byte count |

---

### GET_PROOF (0x13)

Returns full proof data at a given slot index. Slot must be non-empty.

A spent slot is returned as the card holds it, and that is not always a proof:
a card pulled mid-`CLEAR_SPENT` leaves a slot that still reads `02` with some
of its data zeroed (see [CLEAR_SPENT](#clear_spent-0x31)).

| Field | Value |
|-------|-------|
| CLA | B0 |
| INS | 13 |
| P1 | Slot index (0-based) |
| P2 | 00 |
| Le | 4E (78 bytes) |

**Response (78 bytes):**

| Offset | Length | Description |
|--------|--------|-------------|
| 0 | 1 | Status: `01`=unspent, `02`=spent |
| 1 | 8 | Keyset ID — the NUT-02 id as 8 **raw** bytes, e.g. `00 59 53 4c e0 bf a1 9a` for `0059534ce0bfa19a` |
| 9 | 4 | Amount (big-endian uint32) |
| 13 | 32 | Nonce — the 32-byte nonce from the NUT-10 P2PK secret, **not** the secret string |
| 45 | 33 | C point (compressed secp256k1) |

**Errors:**

| SW | Meaning |
|----|---------|
| 6A83 | Slot index out of range |
| 6A88 | Slot is empty |

---

### GET_SLOT_STATUS (0x14)

Lightweight bulk status read. Returns a 1-byte status for every slot (0=empty, 1=unspent, 2=spent), allowing the reader to enumerate without reading full proof data.

| Field | Value |
|-------|-------|
| CLA | B0 |
| INS | 14 |
| P1 | 00 |
| P2 | 00 |
| Le | 20 (32 bytes, one per slot) |
| Response | 32 bytes: one status byte per slot |

In Foxy's software the card has 128 slots, and the answer is **128 bytes**, one status byte per slot (ask with Le `00`).

---

### GET_CHANGE (0x19) — software 1.12, record changed in 1.21

The openings of the change the card made for itself (`SPEND_ALL_CHANGE`) and has not yet been handed, three to a page. No PIN. It writes nothing but this: an opening whose piece is on the card by now is let go first.

| Field | Value |
|-------|-------|
| CLA | B0 |
| INS | 19 |
| P1 | The page, from 0 |
| P2 | 00 |
| Le | 00 (256) |

**Response.** A count (1), then for each opening, 50 bytes: its number (1, the P1 of [LOAD_CHANGE](#load_change-0x53--software-121)), the amount (4), the keyset (8), the date (4) and the blinded message `B_` (33, compressed). Never the nonce or the blinding factor: the card unblinds its change itself. Up to 1.20 a record was 80 bytes, the amount, keyset, date, nonce and blinding factor.

---

### GET_DLEQ (0x1A) — software 1.20

Gives back the mint's proof (NUT-12) that the card keeps with an unspent piece: `e` (32), `s` (32) and the blinding factor `r` (32), 96 bytes. The card does not check it and never uses it; a reader rebuilds the piece's secret, `Y = hash_to_curve(secret)`, `C' = C + r*K` and `B' = Y + r*G`, and checks the proof (`NUT-XX.md`, `docs/FOXY-CARD-SPEC.md` 5.9). No PIN, any state of the card, locked or not. It writes nothing.

| Field | Value |
|-------|-------|
| CLA | B0 |
| INS | 1A |
| P1 | A slot (0 to 127) |
| P2 | A second slot (0 to 127), or FF for none |
| Le | 00 (256) |

**Response.** For each slot asked, in the order asked: `01` and the 96 bytes (`e ‖ s ‖ r`) if the slot is unspent and its record is not zero; otherwise the single byte `00` (the slot is empty or spent, or holds a piece that came with no proof, or the card has no room for proofs). The same slot twice is answered twice. One slot is 1 or 97 bytes; two are 2 to 194.

**Errors:**

| SW | Meaning |
|----|---------|
| 6A83 | P1 is over 127, or P2 is over 127 and not FF; nothing is answered |

---

### GET_LOG (0x18) — software 1.22 on a card with no owner

The card's own account of its taps (P1 = 0) and its receipts (P1 = 1, P2 = how many back from the newest to begin); the layouts are in `docs/FOXY-CARD-SPEC.md` 5.2. Who reads them depends on the card.

| Field | Value |
|-------|-------|
| CLA | B0 |
| INS | 18 |
| P1 | 00 the log; 01 the receipts |
| P2 | 00 for the log; for the receipts, how many back from the newest |
| Le | 00 (256) |

**A card with an owner** (as 1.21): the log to the PIN given in this tap or the owner's grant (`ALLOW_LOAD`), the receipts to the grant alone, at once.

**A card with a PIN and no owner** (1.22, the card of a holder with no phone, read on a friend's phone as a guest): both to the PIN given in this tap, and held for. Each of the first nine askings in a row is answered `6A98` with no data, after a signature's work of the card's own, thrown away; the tenth answers. The log and the receipts count as one held tap; any other command, a `SELECT` among them, begins the count again. Once answered, the history is open to the PIN for the rest of that time in the field, and no payment command is taken in it (`6985`). In a time in the field in which a payment was signed it is refused, `6985`, before any asking is counted. The balance and the pieces are not held for.

**A card with no PIN** has signed nothing and loaded nothing, and answers anyone with an empty log.

**Errors:**

| SW | Meaning |
|----|---------|
| 6982 | The PIN not given in this tap (and, with an owner, no grant; the receipts of a card with an owner want the grant) |
| 6A98 | Keep holding: an asking of a card with no owner counted, and the history not yet given; ask again |
| 6985 | A card with no owner, in a time in the field in which a payment was signed |
| 6A86 | A P1 other than 00 and 01 (it neither counts nor ends a count) |

---

## Category 0x2x — Spend (PIN required if PIN is set — Profile B+)

If a PIN is set, the reader must call `VERIFY_PIN (0x40)` within the same NFC session before any spend or sign command (D13). "A PIN is set" includes the blocked state (GET_INFO byte 7 = 2): `VERIFY_PIN` can never succeed there, so every spend and sign command answers `6982` permanently. Cards provisioned without a PIN keep tap-and-go behaviour. (In Foxy's software a payment is `SPEND_ALL_BEGIN`, `SPEND_ALL_OUTPUTS`, `SPEND_ALL_CHANGE` and `SPEND_ALL_SIGN`, `22` to `26`, and the PIN, or the no-PIN allowance, is asked at `SPEND_ALL_SIGN`; from 1.21 a card with no PIN refuses all five with `6A97`, and a card claimed in this time in the field refuses them with `6985`; from 1.22 so does a card with no owner whose history was read in this time in the field.) The PIN session flag is transient: cleared on card deselect / tap end, and by any failed PIN check (see [Session State](#session-state-transient)).

### SPEND_PROOF (0x20)

Atomically marks a proof as spent (irreversible) and returns a NUT-11 P2PK Schnorr signature. The signature proves the card authorised this spend. The reader submits the proof + signature to the Cashu mint for redemption.

This is the **core payment operation**.

**PIN:** if a PIN is set, `VERIFY_PIN` must precede this command in the same
session (D13). A blocked PIN counts as set, and refuses for good. The gate runs
*before* the slot burn, so a wrong, missing or blocked PIN never consumes a
proof.

| Field | Value |
|-------|-------|
| CLA | B0 |
| INS | 20 |
| P1 | Slot index |
| P2 | 00 |
| Lc | 20 |
| Data | 32-byte message = SHA-256(UTF8(Proof.secret)) — see the note below |
| Le | 40 |
| Response | 64-byte Schnorr signature (R \|\| s, 32 bytes each) |

**Errors:**

| SW | Meaning |
|----|---------|
| 6985 | Proof already spent — double-spend blocked |
| 6A88 | Slot is empty |
| 6A83 | Slot index out of range |
| 6982 | PIN set (or blocked) and not verified in this session; permanent once the PIN is blocked |
| 6F00 | Signing failed (hardware error) |

**Note on message construction:** The reader computes

```
msg = SHA-256(UTF8(Proof.secret))
```

where `Proof.secret` is the reconstructed NUT-10 P2PK secret string — see
[NUT-XX.md — SPEND_PROOF Message Construction](NUT-XX.md#spend_proof--message-construction)
for the canonical definition and the reconstruction rules. That secret embeds
the proof's 32-byte nonce, which is what makes the message unique per proof and
so makes a captured signature useless against any other proof.

---

### SIGN_ARBITRARY (0x21)

Signs any 32-byte message with the card private key **without** consuming a proof. Used for:
- NUT-11 proof-of-ownership challenges (wallet queries card capability)
- Card authentication during provisioning
- Future NUT extensions requiring card identity proofs

**PIN:** gated like `SPEND_PROOF` (D13) — the signature is a spend
authorisation under the card's key, so an unverified session must not be able
to mint one. `6982` when a PIN is set (or blocked) and unverified in this
session.

| Field | Value |
|-------|-------|
| CLA | B0 |
| INS | 21 |
| P1 | 00 |
| P2 | 00 |
| Lc | 20 |
| Data | 32-byte message |
| Le | 40 |
| Response | 64-byte Schnorr signature (R \|\| s) |

**Errors:**

| SW | Meaning |
|----|---------|
| 6982 | PIN set (or blocked) and not verified in this session; permanent once the PIN is blocked |
| 6F00 | Signing failed |

---

## Category 0x3x — Write (PIN required if PIN is set)

If PIN is set, the reader must call `VERIFY_PIN (0x40)` within the same NFC session before calling write commands. "PIN is set" includes the blocked state (GET_INFO byte 7 = 2), in which every write command answers `6982` permanently. (In Foxy's software the owner's grant, `ALLOW_LOAD`, stands in for the PIN for `LOAD_PROOF`, `LOAD_CHANGE` and `CLEAR_SPENT`; on a blocked card it still opens `CLEAR_SPENT` and not the loads. From 1.21 a card with no PIN refuses all three with `6A97`.) The PIN session flag is transient: cleared on card deselect / tap end, and by any failed PIN check (see [Session State](#session-state-transient)).

### LOAD_PROOF (0x30)

Stores a new proof in the next available empty slot. Used during card top-up (funding at flash-pos or via flash-mobile).

| Field | Value |
|-------|-------|
| CLA | B0 |
| INS | 30 |
| P1 | 00 |
| P2 | 00 |
| Lc | 4D (77 bytes) |
| Data | 8-byte keyset_id (raw) + 4-byte amount + 32-byte nonce + 33-byte C point |
| Le | 01 |
| Response | 1-byte slot index assigned |

**In Foxy's software** a piece is **81 bytes**: those 77 and its date (4, big-endian, 0 for none), and one command takes one, two or three pieces end to end (Lc `51`, `A2` or `F3`), answered with the slot of each piece stored. It needs a PIN set and given in this tap and a card record; the owner's grant does not open it (1.21), and from 1.22 a card with no owner is loaded as one with an owner is, under the PIN (up to 1.21 it was refused, `6A90`); the tap after a payment does not open it (1.21: that tap loads the payment's own change, by [LOAD_CHANGE](#load_change-0x53--software-121)). Several pieces are taken in order, each as one alone would be: if the first cannot be stored the command is refused with its word; if a later one cannot, the answer is the slots of those before it.

**Errors:**

| SW | Meaning |
|----|---------|
| 6982 | Security condition not satisfied (PIN set or blocked, and not verified in this session) |
| 6986 | Card locked (`LOCK_CARD`) — writes disabled |
| 6A84 | No space — all slots occupied |
| 6A97 | Foxy, 1.21: the card has no PIN; a card is given its PIN first (checked before anything but the lock) |
| 6A8C | Foxy: the card has no record (`SET_CARD` has not run) |
| 6700 | Foxy: a length that is not one, two or three pieces of 81 bytes (with P1 = 1: not 177) |
| 6A8E | Foxy: a dated piece on a card with no refund key |
| 6A80 | Foxy: a `C` that is not a compressed point, an amount of 0, or (P1 = 1) a proof of 96 zero bytes |
| 6A94 | Foxy: a piece whose nonce is already in a slot, spent or not; checked last, before anything is written |

In Foxy's software the words come in this order: `6986`, `6A97`, `6982`, `6A8C`, `6A84`, `6700`, and then, for each piece, `6A84`, `6A8E`, `6A80`, `6A94`. (Up to 1.21, `6A90` for a card with no owner came after `6982`.)

**Write order.** The card stores the 77 bytes, then marks the slot unspent
(D14, applet 0.4 and later; a card whose `SELECT` answers below `00 04` may
mark it first). A card that leaves the field mid-command leaves the slot empty
or holding the whole proof, never part of one marked unspent. Upstream's card
does not check for duplicates, so after a `LOAD_PROOF` whose answer was lost the
proof may or may not be on the card: read the slots (`GET_SLOT_STATUS`,
`GET_PROOF`) before sending it again. Foxy's card refuses a piece whose nonce is
in any slot (`6A94`), so sending it again is safe, and the answer says it is
there already. In Foxy's software the slot's text for hashing and its record in
the store of the mint's proofs are written before its data, and the status byte
is the last write.

**With P1 = 1 (Foxy's software 1.20): one piece and the mint's proof for it.** The data is exactly 177 bytes: the 81 bytes of a piece (`docs/FOXY-CARD-SPEC.md` 5.1; keyset, amount, nonce, `C`, date) and then `e ‖ s ‖ r`, 96 bytes. The answer is two bytes: the slot, then `01` if the proof was kept and `00` if the card has no room for proofs and went on without it. Any other length is `6700`; a proof of 96 zero bytes is `6A80` (zeros are what "no proof" is kept as); every other check, word and effect is that of a load of one piece, in the same order. A P1 other than 1 is as it was: one to three pieces of 81 bytes, the slot of each as the answer, and each piece stored that way is kept with no proof. The proof is written into the slot's record before the slot's data, and the status byte is still the last write.

---

### CLEAR_SPENT (0x31)

Garbage-collects all spent proof slots, freeing them for new proofs. Called after a top-up cycle to reclaim slot space. Requires PIN. (In Foxy's software: the PIN given in this tap, on a card with an owner or, from 1.22, one with none; `6A97` on a card with no PIN; neither the owner's grant nor the tap after a payment opens it, 1.21. From 1.16 to 1.20 a card with no PIN was freed by whoever held it, and the tap after a payment freed any card's.)

| Field | Value |
|-------|-------|
| CLA | B0 |
| INS | 31 |
| P1 | 00 |
| P2 | 00 |
| Le | 01 |
| Response | 1-byte count of slots freed |

**Errors:**

| SW | Meaning |
|----|---------|
| 6982 | Security condition not satisfied (PIN set or blocked, and not verified in this session) |
| 6986 | Card locked (`LOCK_CARD`) — writes disabled |
| 6A97 | Foxy, 1.21: the card has no PIN |

**Write order.** Each spent slot's data is zeroed, then the slot is marked
empty (D14, applet 0.4 and later). A tear leaves a slot still spent, which the
next `CLEAR_SPENT` frees. Until then `GET_PROOF` returns the slot with status
`02` and some fields zeroed; the fill is not atomic, so which of them are
zeroed is not defined. Once `CLEAR_SPENT` has touched a spent slot, its data is
not a proof. Only the zeroed bytes set such a slot apart, so a reader that uses
a spent slot's data checks it first. `cardctl dump` skips a spent slot that
fails the card file's slot checks ([`CARD-FILE.md`](CARD-FILE.md#slot)). The
slot checks catch a zeroed amount and a `C` that is no longer a point. A tear
that zeroed only keyset or nonce bytes, or part of `C` whose x still lands on
the curve, passes them, and `dump` writes that slot as spent.

---

### LOAD_CHANGE (0x53) — software 1.21

One opening of the card's own change (`SPEND_ALL_CHANGE`) made into the piece it was for. The data is what the mint answered for the opening's blinded message `B_`. The card checks the mint's NUT-12 proof against `K` and its own `B_` (`R1 = s·G − e·K`, `R2 = s·B_ − e·C_`, and `e` must be SHA-256 of `R1`, `R2`, `K` and `C_`, each uncompressed, as lowercase hex text), takes off its own blinding factor (`C = C_ − r·K`), and writes the piece as `LOAD_PROOF` writes one, with the opening's keyset, amount, nonce and date and that `C`, a record of zeros in the store of the mint's proofs, and the status byte last. The opening is let go. The blinding factor never leaves the card.

| Field | Value |
|-------|-------|
| CLA | B0 |
| INS | 53 |
| P1 | The opening's number (0 to 7), as [GET_CHANGE](#get_change-0x19--software-112-record-changed-in-121) gives it |
| P2 | 00 |
| Lc | C2 (194 bytes) |
| Data | `C_` (65, `04 ‖ x ‖ y`) + `K`, the mint's key for the opening's amount (65, the same form) + `e` (32) + `s` (32) |
| Le | 01 |
| Response | 1-byte slot index the piece went into |

**Who may:** the PIN given in this tap, or the owner's grant (`ALLOW_LOAD`), or this tap being the one after the payment that made the opening, once for that opening (`docs/FOXY-CARD-DAILY-LIMIT.md` 8.2).

**Errors** (in the order they are checked):

| SW | Meaning |
|----|---------|
| 6986 | Card locked (`LOCK_CARD`) |
| 6A97 | The card has no PIN |
| 6A83 | P1 is past the last opening (7) |
| 6982 | Neither the PIN, nor the owner's grant, nor the grant of the tap after the payment for this opening |
| 6A88 | The opening is not waiting for its piece |
| 6A94 | The opening's piece is on the card already; the opening is let go |
| 6A84 | No empty slot |
| 6700 | Any length but 194 |
| 6A80 | A point that is not on the curve or not in the `04` form, or a proof that does not hold |

The card cannot tell that `K` is the mint's key: a key made up with a proof that holds for it is taken, and the piece is one the mint will not take (`docs/FOXY-CARD-SPEC.md` 5.10).

---

## Category 0x4x — Authentication

### VERIFY_PIN (0x40)

Verifies the provisioning PIN. On success, sets a transient session flag that permits the PIN-gated commands (spend, sign, write) for the remainder of this NFC tap, and resets the retry counter. On failure, decrements the retry counter and clears the session flag: a session that had verified is no longer authenticated, as with `OwnerPIN.check`.

| Field | Value |
|-------|-------|
| CLA | B0 |
| INS | 40 |
| P1 | 00 |
| P2 | 00 |
| Lc | 04–08 |
| Data | PIN bytes (4–8 bytes) |

**Errors:**

| SW | Meaning |
|----|---------|
| 63 CX | Wrong PIN — X retries remaining (e.g. `63 C2` = 2 retries left); the session's verification ends |
| 6983 | PIN blocked — max retries exhausted, GET_INFO byte 7 now 2. Answered by the try that exhausts the counter (not `63 C0`) and by every `VERIFY_PIN` after it; there is no unblock path |
| 6984 | PIN not set (use SET_PIN first) |

---

### SET_PIN (0x41)

Sets the provisioning PIN. May only be called **once** (during card personalization). Subsequent PIN changes use `CHANGE_PIN`. If called when PIN is already set, returns `6985` — and a blocked PIN counts as set, so `SET_PIN` can never re-key a blocked card.

| Field | Value |
|-------|-------|
| CLA | B0 |
| INS | 41 |
| P1 | 00 |
| P2 | 00 |
| Lc | 04–08 |
| Data | New PIN bytes |

**Errors:**

| SW | Meaning |
|----|---------|
| 6985 | PIN already set (or blocked) — use CHANGE_PIN |
| 6986 | Card locked (`LOCK_CARD`) |
| 6700 | Wrong data length (PIN must be 4–8 bytes) |

In Foxy's software `SET_PIN` sets or replaces the PIN, and unblocks, on a card with no owner and nothing unspent, and is the first thing a card is given (1.21); on a card with an owner it is `6A91`, and the owner uses `CHANGE_PIN` with the owner's proof. From 1.22, once a card with no owner has its record (it is set up), it keeps its PIN: a new one wants the PIN it has given in this tap (`6982`), nothing unspent and no change owed (`6A8D`), and, once the card has been used, the history held for in this tap (`6985` in a tap that paid, `6982` with no hold; see `GET_LOG`). A blocked PIN has no one to give it: it is replaced with nothing owed, and the log and the receipts go with it. A card not yet set up takes a PIN from anyone, as before (`docs/FOXY-CARD-DAILY-LIMIT.md`, "1.22 in one place", rules 9 and 10). From 1.16 to 1.20 a card with an owner and no PIN took one under the owner's grant; no such card can be made now.

### SET_OWNER (0x43) — software 1.21

Gives a card its owner's key, a P-256 public key, 65 bytes uncompressed (`docs/FOXY-CARD-SPEC.md` 5.6). On a card with an owner the old owner's proof comes first and it is made at once. On a card with no owner it is a **claim**, and from 1.21 a claim is slow and under the PIN.

| Field | Value |
|-------|-------|
| CLA | B0 |
| INS | 43 |
| P1 | 00 |
| P2 | 00 |
| Data | The owner key, `04 ‖ X ‖ Y` (65); with an owner, proof length (1) and the old owner's proof first |
| Response | On a claim, `00 01` ("not yet") to each of the first nine askings in a row; nothing to the tenth, which sets the owner |

**The claim.** The PIN must be set and given in this tap, and no payment signed in this time in the field. The same key is asked for ten times in a row; each of the first nine is answered `9000` and `00 01` after a signature's work of the card's own, thrown away, as a payment's wait does it. A different key, or any other command between (a `SELECT` among them), begins the count again. After a claim, no payment is signed in that time in the field.

**Errors:**

| SW | Meaning |
|----|---------|
| 6986 | Card locked |
| 6A97 | A claim on a card with no PIN |
| 6982 | A claim with the PIN not given in this tap |
| 6985 | A claim in a time in the field in which a payment was signed |
| 6A91 | A card with an owner, with no good proof |
| 6A8D | Anything unspent is on the card |
| 6700 | A key that is not 65 bytes |
| 6A80 | A key that does not begin `04`, or is not a point |

---

### CHANGE_PIN (0x42)

Changes the PIN. Requires the current PIN to be verified first in this session.

The card checks the current PIN in the data field again. A wrong one costs a
try and answers `63CX`, exactly as `VERIFY_PIN` does, and it ends the
session's verification: the next `CHANGE_PIN` answers `6982` until
`VERIFY_PIN` succeeds again, and that success resets the counter. So
`CHANGE_PIN` cannot run the counter down on its own; `6983` is listed below
because it shares `VERIFY_PIN`'s failure handling, and a reader should handle
it the same way.

| Field | Value |
|-------|-------|
| CLA | B0 |
| INS | 42 |
| P1 | 00 |
| P2 | 00 |
| Lc | Variable |
| Data | 1-byte old PIN length + old PIN + new PIN (4–8 bytes each) |

**Errors:**

| SW | Meaning |
|----|---------|
| 6982 | Current PIN not verified in this session (always the case once the PIN is blocked, or after a failed check) |
| 63 CX | Wrong current PIN — X retries remaining; the session's verification ends |
| 6983 | PIN blocked — tries exhausted, GET_INFO byte 7 now 2 |
| 6986 | Card locked (`LOCK_CARD`) |
| 6700 | Wrong data length |

---

## Category 0x5x — Admin

### LOCK_CARD (0x50)

Permanently disables all write operations. **Irreversible.** Requires PIN when one is set; a blocked PIN counts as set, so a blocked card cannot be locked. P1 is not read.

**A locked card still pays.** Locking freezes the card's settings and every write (`6986`); it does not stop a payment. In Foxy's software a locked card pays under its PIN and up to its no-PIN allowance, and its limit, its allowance and its PIN can no longer be changed; locking also needs the owner's proof, and only `RESET_CARD`, with the owner's proof and the PIN, takes a locked card back. It is not a way to make a lost card safe.

| Field | Value |
|-------|-------|
| CLA | B0 |
| INS | 50 |
| P1 | 00 |
| P2 | DE (deadbeef confirmation byte) |

**Errors:**

| SW | Meaning |
|----|---------|
| 6982 | PIN set (or blocked) and not verified in this session |
| 6985 | Card already locked |

### RESET_CARD (0x51) — software 1.17

Returns the card to what it was out of its packet, so that another person can set it up: no PIN (three tries), no owner, not locked, no record, no limits, no log or receipts, no places in use, and a new key pair of its own (the card's key and the PIN key). It keeps its clock (the time and the hash of the last block header) and its software.

| Field | Value |
|-------|-------|
| CLA | B0 |
| INS | 51 |
| P1 | 00 |
| P2 | AD (confirmation byte) |
| Data | proof length (1), the owner's proof (ECDSA over `FoxyCard/reset`, the nonce just given, and no value); nothing after it |

**Errors** (in the order they are checked):

| SW | Meaning |
|----|---------|
| 6B00 | P1 is not 00 or P2 is not AD |
| 6A90 | The card has no owner |
| 6A91 | No nonce was asked for in this tap, or the proof is not the owner's |
| 6700 | Bytes after the proof |
| 6982 | The card is locked and the PIN has not been verified in this tap |
| 6A8D | Anything unspent is on the card |

From 1.20 it also fills the store of the mint's proofs with zeros, after the places are freed and outside the transaction. A blocked PIN is no bar on a card that is not locked. The wipe is one transaction of five bytes (the lock, the PIN state, the change note, the owner and the NFC tag's switch), after the keys are drawn; see `docs/FOXY-CARD-SPEC.md` 5.6a. It puts the NFC tag back on.

### SET_TAG (0x52) — software 1.19

Switches the card's NFC tag off or on (`docs/FOXY-CARD-SPEC.md` 5.8). The tag is on for a new card, a card with no owner and a card just reset, and off only when the owner has said so. The owner's proof, no PIN, any funds.

| Field | Value |
|-------|-------|
| CLA | B0 |
| INS | 52 |
| P1 | 00 tag on, 01 tag off |
| P2 | 00 |
| Data | proof length (1), the owner's proof (ECDSA, P-256, SHA-256, DER, over `FoxyCard/tag`, the nonce just given and the one byte after the proof), then that one byte, the same as P1 |

**Errors** (in the order they are checked):

| SW | Meaning |
|----|---------|
| 6B00 | P1 is not 00 or 01, or P2 is not 00 (the nonce is left unspent) |
| 6986 | The card is locked |
| 6A90 | The card has no owner |
| 6A91 | No nonce was asked for in this tap, or the proof is not the owner's for this label, nonce and byte; no PIN try is spent |
| 6700 | Anything but one byte after the proof |
| 6B00 | The byte after the proof is not P1 |

Asking for the state the tag already has answers `9000` and writes nothing. `GET_INFO` with P1 = 2 says the state in its last byte (the 43rd): `00` on, `01` off. With P1 = 1 it answers the 42 bytes it always did, whatever the state.

---

## The NFC tag application — software 1.19

A third applet of the package, `Tag`, answers as an NFC Forum Type 4 Tag (mapping version 2.0), read only, under the NFC Forum's own name, **not** the card's: `D2 76 00 00 85 01 01` (the instance AID given at install; the class AID in the package is `F0 46 4F 58 59 43 41 52 44 54`). It carries one web address, `https://getfoxy.io/card`, the same on every card. It takes class `00` and answers exactly:

| Command | Answer |
|---------|--------|
| `00 A4 04 00 07 D2 76 00 00 85 01 01 [00]` (SELECT by name) | `9000`, no data; `6999` when the owner has switched the tag off |
| `00 A4 00 0C 02 E1 03`, `00 A4 00 0C 02 E1 04` (SELECT file) | `9000`; any other file `6A82`; other P1 or P2 `6B00`; Lc not 2 `6700` |
| `00 B0 <offset hi> <offset lo> <Le>` (READ BINARY) | the bytes of the file chosen from that offset, as many as asked and the file holds; an offset at or past the end `6B00`; no file chosen `6986` |
| UPDATE BINARY (`D6`) and the other instructions that write (`0E D0 D7 DA DB DC DD E2`) | `6982` |
| any other instruction | `6D00` (a class other than `00`: `6E00`) |

The capability container (`E103`, 15 bytes) is `00 0F 20 00 3B 00 34 04 06 E1 04 00 16 00 FF`; the NDEF file (`E104`, 22 bytes) is `00 14 D1 01 10 55 04` followed by the 15 ASCII bytes of `getfoxy.io/card`.

---

## Error Code Summary

| SW | Meaning |
|----|---------|
| 90 00 | Success |
| 63 CX | Wrong PIN, X retries remaining (the session's verification ends) |
| 67 00 | Wrong length (Lc/Le) |
| 69 82 | Security condition not satisfied (PIN set or blocked, and not verified in this session) |
| 69 83 | Authentication method blocked (PIN blocked, GET_INFO byte 7 = 2) |
| 69 84 | Referenced data not usable (PIN not set) |
| 69 85 | Conditions not satisfied (already spent / already set / card already locked) |
| 69 86 | Command not allowed (card locked by `LOCK_CARD` — writes disabled) |
| 6A 83 | Record not found (slot out of range) |
| 6A 84 | Not enough memory (no empty slots) |
| 6A 88 | Referenced data not found (slot empty) |
| 6A 97 | Foxy, 1.21: the card has no PIN; a load, a payment or an owner's claim is refused until it is given one |
| 6A 98 | Foxy, 1.21: keep holding; a held tap counted and not yet through (an owner's claim; from 1.22 the history of a card with no owner): ask again |
| 6D 00 | Instruction not supported |
| 6E 00 | Class not supported |
| 6F 00 | No precise diagnosis (hardware / crypto error) |

---

## Proof Slot Layout

Each proof occupies exactly **78 bytes** of persistent EEPROM:

```
Offset  Len  Field
------  ---  -----
0       1    Status (00=empty, 01=unspent, 02=spent)
1       8    Keyset ID (NUT-02 id as 8 RAW bytes, e.g. 00 59 53 4c e0 bf a1 9a)
9       4    Amount (big-endian uint32)
13      32   Nonce (32-byte nonce from the NUT-10 P2PK secret)
45      33   C point (compressed secp256k1, 02/03 prefix)
```

The keyset id is stored **raw**, not as ASCII text. A NUT-02 id is 16 hex
characters, which is 8 bytes raw but 16 bytes as ASCII — storing it as text
would fit only half the id and match no keyset at the mint.

The 32-byte field is the **nonce**, not the secret. A NUT-10 P2PK secret is a
JSON string of ~150 bytes; the card stores the nonce and a reader rebuilds the
secret from it plus `GET_PUBKEY`. See [`NUT-XX.md`](NUT-XX.md).

Total: 32 slots × 78 bytes = **2,496 bytes** EEPROM for proof storage.

In Foxy's software the slot is 82 bytes (a 4-byte date after `C`), there are 128 of them, and `GET_PROOF` gives the slot and nothing more. From 1.20 the card also keeps, beside the slots and not part of them, 96 bytes for each slot: the mint's proof for the piece in it, `e ‖ s ‖ r`, in a store of 12,288 bytes that the first `SET_CARD` reserves. `GET_PROOF` does not give it; [`GET_DLEQ`](#get_dleq-0x1a--software-120) does, for an unspent slot.

---

## Session State (Transient)

The following flags are held in transient RAM and cleared on card deselect:

| Flag | Set by | Cleared by |
|------|--------|-----------|
| `pin_verified` | VERIFY_PIN (success) | Deselect / tap end / any failed PIN check (VERIFY_PIN or CHANGE_PIN) |
| Foxy: the held count | each asking of a held tap: `SET_OWNER` on a card with no owner (1.21), `GET_LOG` on a card with a PIN and no owner (1.22) | Deselect, any other command, or the asking that goes through |

Foxy's card also keeps, for the time in the field (through a `SELECT`, gone with the power), whether a payment was signed in it, whether an owner was claimed in it and (1.22) whether the history of a card with no owner was read in it: no claim and no history after a payment, no payment after a claim or a read.

---

## Provisioning Flow (Top-Up)

```
Reader                          Card
  |                              |
  |--- SELECT APPLICATION -----> |
  |<-- 90 00 -------------------|
  |--- VERIFY_PIN (0x40) ------> |  (PIN set at personalization)
  |<-- 90 00 -------------------|
  |--- CLEAR_SPENT (0x31) -----> |  (reclaim spent slots)
  |<-- count + 90 00 -----------|
  |--- LOAD_PROOF (0x30) ------> |  (repeat for each proof)
  |<-- slot_idx + 90 00 --------|
  |--- GET_BALANCE (0x11) -----> |  (verify new balance)
  |<-- balance + 90 00 ---------|
```

## Payment Flow (Offline Spend)

```
POS Terminal                    Card
  |                              |
  |--- SELECT APPLICATION -----> |
  |<-- 90 00 -------------------|
  |--- GET_BALANCE (0x11) -----> |
  |<-- balance + 90 00 ---------|
  |--- GET_SLOT_STATUS (0x14) -> |
  |<-- slot statuses -----------|
  |--- GET_PROOF (0x13, idx) --> |  (read proof to pay with)
  |<-- proof data + 90 00 ------|
  |--- SPEND_PROOF (0x20, idx) > |  (sign + mark spent atomically)
  |<-- 64-byte signature --------|
  |                              |
  [Terminal submits proof + sig to Cashu mint via Lightning/HTTP]
```
