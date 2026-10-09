package me.flashapp.cashu;

import javacard.framework.*;
import javacard.security.*;
import javacardx.crypto.*;

/**
 * Cashu JavaCard Applet
 *
 * Implements NUT-XX Profile B (Bearer/Offline) for offline NFC payments.
 * Stores Cashu proofs in hardware-persistent EEPROM with non-resettable
 * spend counters. Provides secp256k1 signing for NUT-11 P2PK spending
 * conditions.
 *
 * This is the Foxy fork (docs/FOXY-CARD-SPEC.md). It is not compatible on
 * the wire with upstream cashu-javacard, and has an AID of its own so the two
 * cannot be mistaken for each other:
 *
 * AID: F0 46 4F 58 59 43 41 52 44   ("FOXYCARD" after a proprietary F0)
 *
 * Command set:
 *   0x01  GET_INFO         — version, format, slot stats, PIN state, tries, limit, owner, the day; with P1 = 1, the tap as well
 *   0x10  GET_PUBKEY       — 33-byte compressed secp256k1 card pubkey
 *   0x11  GET_BALANCE      — sum of unspent proof amounts (uint32)
 *   0x12  GET_PROOF_COUNT  — count of non-empty slots
 *   0x13  GET_PROOF        — full proof data at slot index, with its date
 *   0x14  GET_SLOT_STATUS  — bulk 1-byte status for all slots
 *   0x15  AUTH             — prove this is the card: sign the reader's nonce and its own
 *   0x16  GET_CARD         — the card record: mint, unit, refund key, limit, time key
 *   0x17  GET_PIECES       — every slot's state and every unspent piece, a page at a time (P1 = the first slot)
 *   0x18  GET_LOG          — the card's own account of its taps: what it signed for, what it refused
 *   0x22  SPEND_ALL_BEGIN  — the places a payment is made of, in order (one signature for all: NUT-11 SIG_ALL)
 *   0x23  SPEND_ALL_OUTPUTS — the swap's outputs, 37 bytes each, hashed into the message as they come
 *   0x24  SPEND_ALL_SIGN   — the one signature; every piece named is burned as it is given
 *   0x25  SPEND_ALL_AGAIN  — the last signature given, again, for an answer lost on the air
 *   0x30  LOAD_PROOF       — store new proof (an owner, a time, and a PIN or the owner's grant)
 *   0x31  CLEAR_SPENT      — free spent slots (PIN, or the owner's grant)
 *   0x32  SET_CARD         — write the card record (open card: PIN; owned card: the owner's proof; only with nothing unspent)
 *   0x33  SET_LIMIT        — the daily limit, by PIN: an open card only (no owner, nothing unspent)
 *   0x34  SET_LIMIT        — the daily limit, by the owner's proof; with eight bytes, the limit on one payment as well
 *   0x35  SET_TIME         — tell the card the time, under the time key's signature
 *   0x40  VERIFY_PIN       — verify the PIN
 *   0x41  SET_PIN          — set or replace the PIN: an open card only (no owner, nothing unspent)
 *   0x42  CHANGE_PIN       — the owner's proof and the new PIN: no old PIN, any state
 *   0x43  SET_OWNER        — give the card its owner key (an open card, or the old owner's proof; nothing unspent)
 *   0x44  GET_NONCE        — a fresh nonce for the owner's proof
 *   0x45  ALLOW_LOAD       — the owner's proof lets LOAD_PROOF and CLEAR_SPENT through, with no PIN, for this tap
 *   0x50  LOCK_CARD        — permanently disable write operations (PIN, and the owner's proof)
 *
 * What the fork changes, and why (the spec's section 4, and docs/FOXY-CARD-DAILY-LIMIT.md):
 *   - SPEND_PROOF takes no message. Upstream signed 32 bytes the reader
 *     supplied, so a reader could have slot A burned for slot B's signature,
 *     and the SPENT flag bound nothing. The card now rebuilds the slot's own
 *     NUT-10 secret and signs its hash.
 *   - SIGN_ARBITRARY is gone. It signed anything and burned nothing. AUTH is
 *     what is left of "prove you are the card", over a tagged hash that no
 *     secret can hash to.
 *   - Nothing is loaded onto a card with no PIN, and nothing onto a card with
 *     no owner.
 *   - A piece is loaded once. A nonce that is in any slot, spent or not, is
 *     refused (6A94): the card signs the secret, which the nonce makes, and
 *     takes the amount on the terminal's word, so a copy with a smaller
 *     amount would have been signed for at the smaller price.
 *   - The card keeps a DAILY LIMIT in permanent memory, and a clock of its own
 *     (`now`) that only moves forward, told to it under a signature by a time
 *     key kept in its record. A spend is counted against the day it falls in.
 *     A terminal that was handed the PIN can sign for one day's limit and no
 *     more. The PIN, a new SELECT or a reset change nothing about it. The time
 *     key is P-256, and who holds its private half decides how far that
 *     holds: see the spec's section 5.2.
 *   - A card with an OWNER (a P-256 public key) is its owner's, empty or not:
 *     changing the PIN, the limit, the owner or the card record, adding funds
 *     without the PIN, and locking the card each need the owner's proof, an
 *     ECDSA signature over a label, a nonce the card has just given and the
 *     value being set. A card with no owner is open while it is empty, and
 *     cannot be loaded.
 *   - A slot carries a date, and the card a refund key, so a lost or blocked
 *     card's pieces can be taken back by whoever loaded them.
 *   - The card says which mint its pieces are at.
 *
 * @see <a href="https://github.com/lnflash/cashu-javacard">cashu-javacard</a>
 * @see spec/APDU.md for full command reference
 * @see spec/NUT-XX.md for protocol specification
 */
public class CashuApplet extends Applet {

    // -------------------------------------------------------------------------
    // Applet version
    //
    // Moves with every wire-visible behaviour change, and with every fix that
    // a card in the field has to be told apart by, because it is the only
    // non-destructive way to tell two builds apart. 0.3 is the ENG-615 fix:
    // on 0.2 a blocked PIN stopped gating; on 0.3 it gates for good, and a
    // failed PIN check (VERIFY_PIN or CHANGE_PIN) ends the session's
    // authentication. 0.4 is the ENG-620 fix (D14): every slot write commits
    // the status byte last. Nothing on the wire changes outside a torn write,
    // but main tracked a 0.3 CAP with the old order, and SELECT's version is
    // all an installed card reports about its build.
    // -------------------------------------------------------------------------
    //
    // 1.0 is the Foxy fork: another AID, another slot, another SPEND_PROOF.
    // 1.1 is the daily limit, the owner key and the clock (docs/FOXY-CARD-DAILY-LIMIT.md).
    // FORMAT is what a reader checks before it reads a slot: 3 for this design
    // (2 was the allowance draft and the first limit).
    static final byte VERSION_MAJOR = (byte) 0x01;
    // 1.6 is the same card made quicker to hold (see `slotHex`, GET_PIECES' two short forms, and several
    // pieces to one LOAD_PROOF): nothing it signs, refuses or stores for a piece is different.
    // 1.3 was a limit on one tap, a window of ten seconds that refused. 1.5 makes it the limit on one
    // payment, which asks no clock and is waited for (docs/FOXY-CARD-DAILY-LIMIT.md, section 6a).
    // 1.4 is one signature for a whole payment (NUT-11 SIG_ALL): format 4, in
    // which every piece's secret carries the flag and SPEND_PROOF is gone.
    static final byte VERSION_MINOR = (byte) 0x06;
    static final byte FORMAT        = (byte) 0x04;

    // -------------------------------------------------------------------------
    // APDU instruction bytes
    // -------------------------------------------------------------------------
    static final byte INS_GET_INFO         = (byte) 0x01;
    static final byte INS_GET_PUBKEY       = (byte) 0x10;
    static final byte INS_GET_BALANCE      = (byte) 0x11;
    static final byte INS_GET_PROOF_COUNT  = (byte) 0x12;
    static final byte INS_GET_PROOF        = (byte) 0x13;
    static final byte INS_GET_SLOT_STATUS  = (byte) 0x14;
    static final byte INS_AUTH             = (byte) 0x15;
    static final byte INS_GET_CARD         = (byte) 0x16;
    static final byte INS_GET_PIECES       = (byte) 0x17;
    static final byte INS_GET_LOG          = (byte) 0x18;
    // 0x20 was SPEND_PROOF: one piece, one signature over its secret alone. A
    // piece of format 4 says SIG_ALL in its secret, and a mint takes no such
    // signature for it. It answers 6D00 and stays unassigned.
    static final byte INS_SPEND_ALL_BEGIN   = (byte) 0x22;
    static final byte INS_SPEND_ALL_OUTPUTS = (byte) 0x23;
    static final byte INS_SPEND_ALL_SIGN    = (byte) 0x24;
    static final byte INS_SPEND_ALL_AGAIN   = (byte) 0x25;
    // 0x21 was SIGN_ARBITRARY. It answers 6D00 and must stay unassigned.
    static final byte INS_LOAD_PROOF       = (byte) 0x30;
    static final byte INS_CLEAR_SPENT      = (byte) 0x31;
    static final byte INS_SET_CARD         = (byte) 0x32;
    // 0x33 was once a limit on one PIN entry, which a terminal holding the PIN
    // walked round. It is back as the daily limit set by PIN, for an open card
    // only (no owner): the owner's form is 0x34.
    static final byte INS_SET_LIMIT        = (byte) 0x33;
    static final byte INS_SET_LIMIT_OWNER  = (byte) 0x34;
    static final byte INS_SET_TIME         = (byte) 0x35;
    static final byte INS_VERIFY_PIN       = (byte) 0x40;
    static final byte INS_SET_PIN          = (byte) 0x41;
    static final byte INS_CHANGE_PIN       = (byte) 0x42;
    static final byte INS_SET_OWNER        = (byte) 0x43;
    static final byte INS_GET_NONCE        = (byte) 0x44;
    static final byte INS_ALLOW_LOAD       = (byte) 0x45;
    static final byte INS_LOCK_CARD        = (byte) 0x50;

    // -------------------------------------------------------------------------
    // Proof slot layout constants (82 bytes per slot: upstream's 78 and a date)
    // -------------------------------------------------------------------------
    static final short PROOF_SIZE          = (short) 82;
    static final short PROOF_STATUS_OFFSET = (short) 0;
    static final short PROOF_KEYSET_OFFSET = (short) 1;
    static final short PROOF_AMOUNT_OFFSET = (short) 9;
    // The 32 bytes are the P2PK *nonce*, not the secret: a NUT-10 P2PK secret
    // is a JSON string of ~150 bytes and cannot fit. The reader rebuilds the
    // secret from this nonce plus GET_PUBKEY. See spec/NUT-XX.md.
    static final short PROOF_NONCE_OFFSET  = (short) 13;
    static final short PROOF_C_OFFSET      = (short) 45;
    // The piece's NUT-11 locktime, big-endian seconds, or 0 for none. With a
    // date the piece's secret also names the card's refund key, which may
    // spend it once the date has passed (spec section 6).
    static final short PROOF_DATE_OFFSET   = (short) 78;

    static final short PROOF_DATA_LEN      = (short) 81;  // PROOF_SIZE - 1 (no status byte on input)

    static final byte STATUS_EMPTY   = (byte) 0x00;
    static final byte STATUS_UNSPENT = (byte) 0x01;
    static final byte STATUS_SPENT   = (byte) 0x02;

    static final short MAX_PROOFS = (short) 64;

    // GET_PIECES' answer is at most this many bytes: under what a short APDU
    // carries (256), and room for three unspent entries (1 + 3 * 82 = 247).
    static final short PAGE_MAX = (short) 255;
    /** A place's text for hashing, kept beside it: its nonce in hex (64) and its C in hex (66). See `slotHex`. */
    static final short HEX_LEN = (short) 130;
    /** GET_PIECES, P2 = 1: what a place is worth and no more, for choosing: keyset (8), amount (4), date (4). */
    static final short BRIEF_LEN = (short) 16;
    /** The most pieces one LOAD_PROOF takes, and the most places GET_PIECES gives whole when asked for by name. */
    static final short BATCH_MOST = (short) 3;

    // -------------------------------------------------------------------------
    // The card record (persistent): which mint, which unit, who may take the
    // pieces back, the daily limit, the time key and the day.
    // -------------------------------------------------------------------------
    static final short CARD_SET_OFFSET     = (short) 0;   // 1 once SET_CARD has run
    static final short CARD_UNIT_OFFSET    = (short) 1;   // 0 = sat
    // The most the card signs for in one day, sats, big-endian. 0 is NO limit,
    // and is what a new card has. SET_CARD never writes it.
    static final short CARD_LIMIT_OFFSET   = (short) 2;
    static final short CARD_REFUND_OFFSET  = (short) 6;   // 33 bytes, zeros = none
    static final short CARD_MINTLEN_OFFSET = (short) 39;
    static final short CARD_MINT_OFFSET    = (short) 40;
    // 80, not 96: SET_CARD with the owner's proof carries a proof of up to 72
    // bytes and its length, 100 bytes of record and the mint, in one short
    // APDU of 255 data bytes.
    static final short CARD_MINT_MAX       = (short) 80;
    // The time signer's public key, uncompressed (04 || X || Y). Written by
    // SET_CARD. The card's own copy of what it checks a time against.
    static final short CARD_TIMEKEY_OFFSET = (short) 120;
    // The latest signed time the card has accepted, seconds, big-endian, 0
    // until it has been told one. SET_TIME writes it, and SET_CARD clears it
    // in exactly one case: a time key different from the one it holds.
    static final short CARD_NOW_OFFSET     = (short) 185;
    // When the current day began, and what has been signed for since.
    static final short CARD_WINDOW_OFFSET  = (short) 189;
    static final short CARD_SPENT_OFFSET   = (short) 193;
    // The most the card signs for in one payment without making the terminal
    // wait, sats, big-endian; 0 is NO limit. It is not counted against a clock
    // and nothing is remembered of it from one payment to the next: a payment
    // is judged by its own size, and every limit's worth past the first costs
    // WAIT_SIGNS signatures of the card's own work before it signs (6a). The
    // two fields after it held a ten-second window and its count, when this
    // was a limit a clock turned; they are kept as zeros, so the record and
    // GET_INFO are the lengths they were.
    static final short CARD_TAP_LIMIT_OFFSET  = (short) 197;
    static final short CARD_TAP_WINDOW_OFFSET = (short) 201;
    static final short CARD_TAP_SPENT_OFFSET  = (short) 205;
    static final short CARD_RECORD_LEN     = (short) 209;
    // SET_CARD's data: unit (1), refund key (33), time key (65), mint length (1), then the mint
    static final short SET_CARD_FIXED      = (short) 100;
    static final short SET_CARD_TIMEKEY_AT = (short) 34;
    static final short SET_CARD_MINTLEN_AT = (short) 99;
    static final short EC_POINT_LEN        = (short) 65;

    static final short AUTH_NONCE_LEN      = (short) 16;

    // One signature for a payment: no more pieces than this in it, and an
    // output is its amount (4, big-endian) and its blinded message (33).
    static final short ALL_MOST            = (short) 32;
    static final short ALL_OUTPUT_LEN      = (short) 37;

    // -------------------------------------------------------------------------
    // The log (persistent): the card's own account of what it has signed for
    // and what it has refused. A spend writes it, in the transaction that
    // burns the piece, and a spend refused for being over a limit writes it
    // before it is refused. Nothing else does: no command sets it, moves it
    // back or clears it, for the PIN or for the owner. The counts only go up,
    // and stop at the top of four bytes.
    //
    // A tap, here, is one time in a reader's field: from the card being
    // powered to its being taken away: it counts by the field, because that
    // is what a person did. A terminal that cuts the field to
    // begin again shows as more taps, and the totals count them all.
    // -------------------------------------------------------------------------
    static final short LOG_TAPS_OFFSET      = (short) 0;   // taps in which anything was signed for or refused, ever
    static final short LOG_SATS_OFFSET      = (short) 4;   // sats signed for, ever
    static final short LOG_REFUSED_OFFSET   = (short) 8;   // spends refused for being over a limit, ever
    static final short LOG_TAMPERS_OFFSET   = (short) 12;  // times a third spend was refused inside ten seconds of the clock, and times in the field in which the clock was moved twice (5.3)
    static final short LOG_RUN_AT_OFFSET    = (short) 16;  // the clock at the first refusal of the run in hand
    static final short LOG_RUN_OFFSET       = (short) 20;  // how many refusals are in that run (1 byte)
    static final short LOG_HEAD_LEN         = (short) 21;
    // the last eight taps, in a ring: the tap numbered n is at (n - 1) mod 8
    static final short LOG_ENTRIES          = (short) 8;
    static final short LOG_ENTRY_LEN        = (short) 16;
    static final short LOG_E_TIME           = (short) 0;   // the clock when the tap's first entry was made (4)
    static final short LOG_E_SATS           = (short) 4;   // sats signed for in it (4)
    static final short LOG_E_PIECES         = (short) 8;   // pieces signed (1, stops at 255)
    static final short LOG_E_REFUSED        = (short) 9;   // spends refused in it for being over a limit (1, stops at 255)
    static final short LOG_E_FLAGS          = (short) 10;  // bit 0: the third refusal of a run, or one after it, was in this tap; bit 1: a payment in it waited (6a)
    static final byte  LOG_FLAG_TAMPER      = (byte) 0x01;
    static final byte  LOG_FLAG_WAITED      = (byte) 0x02; // a payment in this tap was over the limit on one payment, and was waited for
    static final byte  LOG_FLAG_CLOCK       = (byte) 0x04; // the clock was moved on a second time in this tap, by more than CLOCK_JUMP (5.3)
    static final short LOG_E_LOADS          = (short) 11;  // pieces put on in it (1, stops at 255)
    static final short LOG_E_LOADED         = (short) 12;  // sats put on in it (4)
    static final short LOG_LEN              = (short) 149; // LOG_HEAD_LEN + LOG_ENTRIES * LOG_ENTRY_LEN

    /* ---- receipts -----------------------------------------------------------
     * For each payment the card signs, kept after the pieces are burned: the
     * clock (4), what the pieces were worth (4), SHA-256 of the message it
     * signed (32), and the first output's blinded message as it was given
     * (33; zeros for a payment with no outputs). The last RECEIPTS of them,
     * in a ring, after a count of every payment ever (4).
     *
     * The log says what left the card and when. A receipt says where it went,
     * as far as a card can: under SIG_ALL the message names every output of
     * the one swap the mint took the signature for, and an output is made
     * from its receiver's seed. Nobody can tell whose it is by looking; but
     * anyone who is later shown a wallet's seed can make that wallet's
     * outputs again and find this one among them, and the hash then pins the
     * whole swap, piece for piece and output for output. It is what a holder
     * has to show for a payment they did not mean to make. Read with the
     * owner's grant and nothing less: a terminal that has the PIN cannot read
     * it, and no command clears it. */
    static final short RECEIPTS             = (short) 16;
    static final short RECEIPT_LEN          = (short) 73;
    static final short RECEIPTS_HEAD        = (short) 4;
    static final short RECEIPTS_LEN         = (short) 1172; // RECEIPTS_HEAD + RECEIPTS * RECEIPT_LEN
    /** Seconds: the clock moved on by more than this for a second time in one tap is written down (5.3). */
    private static final byte[] CLOCK_JUMP = { (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x78 };
    // GET_LOG's answer: the four counts, then the taps the ring holds, newest first
    static final short LOG_ANSWER_HEAD      = (short) 16;
    // a run of this many refusals inside one tap's ten seconds is a terminal trying the limit, and is marked
    static final short TAMPER_RUN           = (short) 3;

    // The owner: a P-256 public key the card is given, a nonce of 16 it gives
    // for each proof, and a proof that is an ECDSA signature in DER form, 72
    // bytes at the most.
    static final short OWNER_NONCE_LEN     = (short) 16;
    static final short SIG_DER_MAX         = (short) 72;

    // -------------------------------------------------------------------------
    // Status words
    // -------------------------------------------------------------------------
    static final short SW_WRONG_PIN_REMAINING_2 = (short) 0x63C2;
    static final short SW_WRONG_PIN_REMAINING_1 = (short) 0x63C1;
    static final short SW_WRONG_PIN_REMAINING_0 = (short) 0x63C0;
    static final short SW_PIN_BLOCKED           = (short) 0x6983;
    static final short SW_PIN_NOT_SET           = (short) 0x6984;
    static final short SW_ALREADY_SPENT         = (short) 0x6985;
    static final short SW_SLOT_EMPTY            = (short) 0x6A88;
    static final short SW_NO_SPACE              = (short) 0x6A84;
    static final short SW_SLOT_OUT_OF_RANGE     = (short) 0x6A83;
    static final short SW_CRYPTO_ERROR          = (short) 0x6F00;
    static final short SW_CARD_LOCKED           = (short) 0x6985;
    // The fork's own, in a part of 6Axx ISO 7816-4 leaves unassigned
    static final short SW_NO_CARD_RECORD        = (short) 0x6A8C; // LOAD_PROOF before SET_CARD
    static final short SW_CARD_IN_USE           = (short) 0x6A8D; // SET_CARD with pieces unspent
    static final short SW_NO_REFUND_KEY         = (short) 0x6A8E; // a dated piece on a card with no refund key
    static final short SW_OVER_LIMIT            = (short) 0x6A8F; // the piece would take today past the daily limit
    static final short SW_NO_OWNER              = (short) 0x6A90; // a command that needs the owner, or a load, on a card with none
    static final short SW_OWNER_PROOF           = (short) 0x6A91; // no nonce given, or the proof is not the owner's, or the form is not open to an owned card
    static final short SW_NO_TIME               = (short) 0x6A92; // the card has never been told the time, and this needs one
    static final short SW_NOT_THE_TIME          = (short) 0x6A93; // SET_TIME whose signature is not the time key's
    static final short SW_PIECE_ON_CARD         = (short) 0x6A94; // LOAD_PROOF of a piece whose nonce is already in a slot, spent or not
    // 6A95 was "over the limit on one tap", when that was refused; it is now waited for (6a) and the word is unused
    static final short SW_TOO_MANY              = (short) 0x6A96; // more pieces than one signature can burn at once

    // LOCK_CARD confirmation byte
    static final byte LOCK_CONFIRM_BYTE = (byte) 0xDE;

    // PIN constraints
    static final short PIN_MIN_LEN  = (short) 4;
    static final short PIN_MAX_LEN  = (short) 8;
    static final byte  PIN_MAX_TRIES = (byte) 3;

    // -------------------------------------------------------------------------
    // secp256k1 curve parameters (JavaCard byte arrays)
    // Used by setSecp256k1Params() — hardware-compatible code.
    // -------------------------------------------------------------------------

    /** secp256k1 field prime p */
    private static final byte[] SECP256K1_P = {
        (byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,
        (byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,
        (byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,
        (byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFE,(byte)0xFF,(byte)0xFF,(byte)0xFC,(byte)0x2F
    };

    /** secp256k1 a = 0 */
    private static final byte[] SECP256K1_A = new byte[32];

    /** secp256k1 b = 7 */
    private static final byte[] SECP256K1_B = {
        0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,7
    };

    /** secp256k1 uncompressed generator G = 04 || Gx || Gy (65 bytes) */
    private static final byte[] SECP256K1_G = {
        (byte)0x04,
        // Gx
        (byte)0x79,(byte)0xBE,(byte)0x66,(byte)0x7E,(byte)0xF9,(byte)0xDC,(byte)0xBB,(byte)0xAC,
        (byte)0x55,(byte)0xA0,(byte)0x62,(byte)0x95,(byte)0xCE,(byte)0x87,(byte)0x0B,(byte)0x07,
        (byte)0x02,(byte)0x9B,(byte)0xFC,(byte)0xDB,(byte)0x2D,(byte)0xCE,(byte)0x28,(byte)0xD9,
        (byte)0x59,(byte)0xF2,(byte)0x81,(byte)0x5B,(byte)0x16,(byte)0xF8,(byte)0x17,(byte)0x98,
        // Gy
        (byte)0x48,(byte)0x3A,(byte)0xDA,(byte)0x77,(byte)0x26,(byte)0xA3,(byte)0xC4,(byte)0x65,
        (byte)0x5D,(byte)0xA4,(byte)0xFB,(byte)0xFC,(byte)0x0E,(byte)0x11,(byte)0x08,(byte)0xA8,
        (byte)0xFD,(byte)0x17,(byte)0xB4,(byte)0x48,(byte)0xA6,(byte)0x85,(byte)0x54,(byte)0x19,
        (byte)0x9C,(byte)0x47,(byte)0xD0,(byte)0x8F,(byte)0xFB,(byte)0x10,(byte)0xD4,(byte)0xB8
    };

    /** secp256k1 group order n */
    private static final byte[] SECP256K1_N = {
        (byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,
        (byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFE,
        (byte)0xBA,(byte)0xAE,(byte)0xDC,(byte)0xE6,(byte)0xAF,(byte)0x48,(byte)0xA0,(byte)0x3B,
        (byte)0xBF,(byte)0xD2,(byte)0x5E,(byte)0x8C,(byte)0xD0,(byte)0x36,(byte)0x41,(byte)0x41
    };

    // -------------------------------------------------------------------------
    // NIST P-256 (secp256r1) parameters, for the owner key and the time key.
    // They have nothing to do with the card's own secp256k1 key. Set on each
    // key object explicitly; no default curve is relied on.
    // -------------------------------------------------------------------------

    /** P-256 field prime p */
    private static final byte[] P256_P = {
        (byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x01,
        (byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,
        (byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,
        (byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF
    };

    /** P-256 a = p - 3 */
    private static final byte[] P256_A = {
        (byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x01,
        (byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,
        (byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,
        (byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFC
    };

    /** P-256 b */
    private static final byte[] P256_B = {
        (byte)0x5A,(byte)0xC6,(byte)0x35,(byte)0xD8,(byte)0xAA,(byte)0x3A,(byte)0x93,(byte)0xE7,
        (byte)0xB3,(byte)0xEB,(byte)0xBD,(byte)0x55,(byte)0x76,(byte)0x98,(byte)0x86,(byte)0xBC,
        (byte)0x65,(byte)0x1D,(byte)0x06,(byte)0xB0,(byte)0xCC,(byte)0x53,(byte)0xB0,(byte)0xF6,
        (byte)0x3B,(byte)0xCE,(byte)0x3C,(byte)0x3E,(byte)0x27,(byte)0xD2,(byte)0x60,(byte)0x4B
    };

    /** P-256 uncompressed generator G = 04 || Gx || Gy (65 bytes) */
    private static final byte[] P256_G = {
        (byte)0x04,
        (byte)0x6B,(byte)0x17,(byte)0xD1,(byte)0xF2,(byte)0xE1,(byte)0x2C,(byte)0x42,(byte)0x47,
        (byte)0xF8,(byte)0xBC,(byte)0xE6,(byte)0xE5,(byte)0x63,(byte)0xA4,(byte)0x40,(byte)0xF2,
        (byte)0x77,(byte)0x03,(byte)0x7D,(byte)0x81,(byte)0x2D,(byte)0xEB,(byte)0x33,(byte)0xA0,
        (byte)0xF4,(byte)0xA1,(byte)0x39,(byte)0x45,(byte)0xD8,(byte)0x98,(byte)0xC2,(byte)0x96,
        (byte)0x4F,(byte)0xE3,(byte)0x42,(byte)0xE2,(byte)0xFE,(byte)0x1A,(byte)0x7F,(byte)0x9B,
        (byte)0x8E,(byte)0xE7,(byte)0xEB,(byte)0x4A,(byte)0x7C,(byte)0x0F,(byte)0x9E,(byte)0x16,
        (byte)0x2B,(byte)0xCE,(byte)0x33,(byte)0x57,(byte)0x6B,(byte)0x31,(byte)0x5E,(byte)0xCE,
        (byte)0xCB,(byte)0xB6,(byte)0x40,(byte)0x68,(byte)0x37,(byte)0xBF,(byte)0x51,(byte)0xF5
    };

    /** P-256 group order n */
    private static final byte[] P256_N = {
        (byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,
        (byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,
        (byte)0xBC,(byte)0xE6,(byte)0xFA,(byte)0xAD,(byte)0xA7,(byte)0x17,(byte)0x9E,(byte)0x84,
        (byte)0xF3,(byte)0xB9,(byte)0xCA,(byte)0xC2,(byte)0xFC,(byte)0x63,(byte)0x25,(byte)0x51
    };

    // -------------------------------------------------------------------------
    // A piece's NUT-10 secret, as text. The card signs SHA-256 of exactly this
    // (NUT-11), so it is the wire format between the card and whoever made the
    // piece: no spaces, this key order, lowercase hex, the date in decimal.
    //
    //   ["P2PK",{"nonce":"<64 hex>","data":"<66 hex card key>","tags":[]}]
    //
    // and with a date:
    //
    //   ...,"tags":[["locktime","<date>"],["refund","<66 hex>"]]}]
    //
    // It is the text cashu-ts writes for a piece locked to one key, and for
    // one with a locktime and one refund key (OutputData.createP2PKData), to
    // the character: the wallet that loads a card makes its pieces with that
    // library, so the card builds what the library builds and nothing has to
    // be made specially for it. Foxy's tests hold the two together. It is not
    // upstream's text, which carried a sigflag tag the library does not write.
    // -------------------------------------------------------------------------
    private static final byte[] SECRET_1 = {   // ["P2PK",{"nonce":"
        '[','"','P','2','P','K','"',',','{','"','n','o','n','c','e','"',':','"' };
    private static final byte[] SECRET_2 = {   // ","data":"
        '"',',','"','d','a','t','a','"',':','"' };
    private static final byte[] SECRET_3 = {   // ","tags":[
        '"',',','"','t','a','g','s','"',':','[' };
    private static final byte[] SECRET_DATE = {   // ["locktime","
        '[','"','l','o','c','k','t','i','m','e','"',',','"' };
    private static final byte[] SECRET_REFUND = { // "],["refund","
        '"',']',',','[','"','r','e','f','u','n','d','"',',','"' };
    // Every piece's last tag: ["sigflag","SIG_ALL"]. After the refund key where
    // the piece has a date, and alone where it has none.
    private static final byte[] SECRET_END_DATED = {   // "],["sigflag","SIG_ALL"]]}]
        '"',']',',','[','"','s','i','g','f','l','a','g','"',',','"','S','I','G','_','A','L','L','"',']',']','}',']' };
    private static final byte[] SECRET_END = {         // ["sigflag","SIG_ALL"]]}]
        '[','"','s','i','g','f','l','a','g','"',',','"','S','I','G','_','A','L','L','"',']',']','}',']' };
    private static final byte[] HEX = {
        '0','1','2','3','4','5','6','7','8','9','a','b','c','d','e','f' };
    /** AUTH's tag: the message signed is SHA-256(SHA-256(tag) || SHA-256(tag) || ...). */
    private static final byte[] AUTH_TAG = {
        'F','o','x','y','C','a','r','d','/','a','u','t','h' };

    // The owner's proofs: an ECDSA signature (P-256, SHA-256, DER) by the owner
    // key over label || nonce || value. One label for each command, so a proof
    // made for one is of no use to another, and the value being set is inside
    // what is signed, so a proof made for one value is of no use for another.
    private static final byte[] LABEL_CHANGE_PIN = {   // FoxyCard/change-pin
        'F','o','x','y','C','a','r','d','/','c','h','a','n','g','e','-','p','i','n' };
    private static final byte[] LABEL_SET_LIMIT = {   // FoxyCard/set-limit
        'F','o','x','y','C','a','r','d','/','s','e','t','-','l','i','m','i','t' };
    private static final byte[] LABEL_SET_OWNER = {   // FoxyCard/set-owner
        'F','o','x','y','C','a','r','d','/','s','e','t','-','o','w','n','e','r' };
    private static final byte[] LABEL_SET_CARD = {   // FoxyCard/set-card
        'F','o','x','y','C','a','r','d','/','s','e','t','-','c','a','r','d' };
    private static final byte[] LABEL_LOAD = {   // FoxyCard/load
        'F','o','x','y','C','a','r','d','/','l','o','a','d' };
    private static final byte[] LABEL_LOCK = {   // FoxyCard/lock
        'F','o','x','y','C','a','r','d','/','l','o','c','k' };
    // The time signer's: not an owner's label. Signed by the time key over
    // label || the time (4 bytes, big-endian).
    private static final byte[] LABEL_TIME = {   // FoxyCard/time
        'F','o','x','y','C','a','r','d','/','t','i','m','e' };

    /** 86 400 seconds, big-endian: how long a day is. */
    private static final byte[] DAY_SECONDS = { (byte) 0x00, (byte) 0x01, (byte) 0x51, (byte) 0x80 };

    private static final byte[] ONE = { (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01 };

    /** What one limit's worth past the first costs a payment: this many signatures of work, about three seconds on the chip. */
    static final short WAIT_SIGNS = (short) 4;
    /** The most a payment is counted over its limit: past this it waits as long as this does (a quarter of an hour). */
    static final short WAIT_UNITS_MOST = (short) 255;
    /** 10 seconds, big-endian: how close together refusals are one run of them, in the card's own log (6b). */
    private static final byte[] TAP_SECONDS = { (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x0A };

    // -------------------------------------------------------------------------
    // Persistent state (EEPROM)
    // -------------------------------------------------------------------------

    /** Proof storage: MAX_PROOFS * PROOF_SIZE bytes */
    private byte[] proofStorage;
    /**
     * For each place, its nonce and its C as lowercase hex text, HEX_LEN bytes:
     * the two parts of a piece's secret and of a payment's message that differ
     * from piece to piece. Written when the piece is loaded, before its status
     * byte, and read only for a place that is UNSPENT.
     *
     * It is here for time and nothing else. A payment's message is every
     * piece's secret and C as text, and the card used to make that text for
     * each piece as it was named: a hundred and thirty bytes turned to hex a
     * byte at a time, in bytecode, most of a tenth of a second a piece on the
     * chip, while the card was being held to a till. Now that is done once,
     * when the piece goes on, and a payment hashes what is already written.
     */
    private byte[] slotHex;

    /** Card locked flag — once set to 1, write operations are disabled */
    private byte[] cardLocked;   // 1-byte array (persistent)

    /** PIN state: 0=unset, 1=set, 2=locked */
    private byte[] pinState;     // 1-byte array (persistent)

    /** The provisioning PIN (up to PIN_MAX_LEN bytes) */
    private OwnerPIN pin;

    /** The card record (CARD_RECORD_LEN bytes) */
    private byte[] cardRecord;

    /** SHA-256 of AUTH_TAG, worked out once at install */
    private byte[] authTagHash;

    /**
     * The owner's public key (P-256), and whether one has been given
     * (persistent). It is never read out: GET_INFO says only whether there is one.
     */
    private ECPublicKey ownerKey;
    private byte[] ownerSet;

    /**
     * That the card has signed for a payment since it was last tapped
     * (persistent, so it outlives the card leaving the field): the tap after
     * a payment is let put pieces on with no PIN, for the change. A till
     * writes change in a second tap, the card having been let go while the
     * mint was asked, and the holder should not have to type the PIN twice
     * for one payment. Set by SPEND_PROOF, taken by the next SELECT (into
     * changeGrant, for that tap only) and cleared then, whatever that tap
     * does with it. It opens one thing: putting pieces on (LOAD_PROOF, and
     * CLEAR_SPENT to make room), and only to the next reader, which is the
     * one that had the PIN a moment before. See the spec's 8.2.
     */
    private byte[] changeDue;

    /**
     * The time key, as the verifier takes it. The record holds the key's
     * bytes, which are the truth: this object is set from them before each use.
     */
    private ECPublicKey timeKey;

    /** The one ECDSA verifier (SHA-256, DER signatures), for the owner's proofs and the time. */
    private Signature ecdsa;

    // -------------------------------------------------------------------------
    // Card keypair (persistent, generated once on install)
    // -------------------------------------------------------------------------

    private KeyPair     cardKeyPair;
    private ECPrivateKey cardPrivKey;
    private ECPublicKey  cardPubKey;

    // -------------------------------------------------------------------------
    // Schnorr engine (ENG-182)
    //
    // SchnorrHW is the only signer. It uses JavaCard-native crypto
    // (ALG_EC_SVDP_DH_PLAIN_XY + byte-array modular arithmetic) and therefore
    // requires JavaCard 3.0.5 or later — that constant does not exist in 3.0.4.
    //
    // A BigInteger-based simulation used to live here behind a HARDWARE flag.
    // It could never be converted to a .cap (the JavaCard runtime has no
    // java.math, java.security, java.util or long), so it has been removed.
    // -------------------------------------------------------------------------
    private SchnorrHW schnorrHW;

    // -------------------------------------------------------------------------
    // Transient state (RAM, cleared on deselect)
    // -------------------------------------------------------------------------

    /**
     * Set to 0x01 after a successful VERIFY_PIN. Cleared on deselect, and by
     * any failed PIN check (see failPinCheck).
     */
    private byte[] pinVerifiedFlag;

    /**
     * The nonce the card last gave (GET_NONCE), and whether it can still be
     * used. One try for each nonce, right or wrong; gone with the tap.
     */
    private byte[] ownerNonce;
    private byte[] nonceLive;

    /**
     * Whether the owner has allowed loading in this tap (ALLOW_LOAD): LOAD_PROOF
     * and CLEAR_SPENT then need no verified PIN. Gone with the tap. Nothing else
     * reads it: SPEND_PROOF never does.
     */
    private byte[] loadGrant;

    /**
     * Whether this tap is the one after a payment (changeDue, taken at
     * SELECT): LOAD_PROOF and CLEAR_SPENT then need no verified PIN, as under
     * loadGrant. Gone with the tap. SPEND_PROOF never reads it.
     */
    private byte[] changeGrant;

    // Scratch for building a secret's hash and AUTH's (D10: allocated once).
    //   0..65   hex text of the value being hashed
    //   66..97  the 32-byte message
    //   98..107 a date's decimal digits
    //   108..111 a copy of a date, divided away; the end of a window, added up
    //   112..115 what the day would have signed for, with this piece
    //   116..119 what the tap would have signed for, with this piece
    private static final short X_HEX   = (short) 0;
    private static final short X_MSG   = (short) 66;
    private static final short X_DEC   = (short) 98;
    private static final short X_NUM   = (short) 108;
    private static final short X_SUM   = (short) 112;
    private static final short X_TAP   = (short) 116;
    // an output's text for the hash, in one piece: its amount in decimal, right-aligned in ten bytes, then its B_ in hex (66)
    private static final short X_OUT   = (short) 120;
    private static final short X_LEN   = (short) 196;
    private byte[] scratch;
    /** The log (LOG_*). Permanent. */
    private byte[] cardLog;
    private byte[] cardReceipts;
    /** The first output of the payment in hand, for its receipt (RAM). */
    private byte[] allOut;
    /**
     * What the card knows of the time it has been told in this time in the
     * field (RAM, gone with the power): [0] whether it has been told, [1]
     * whether the clock has since been moved on past CLOCK_JUMP from where
     * that first telling left it, [2..5] where that was.
     */
    private byte[] timeTold;
    /** Whether this time in the field has an entry in the log yet. Gone with the power, not with a SELECT. */
    private byte[] tapOpen;
    private MessageDigest sha;
    /** The hash of the message a payment's one signature is over, kept from SPEND_ALL_BEGIN to SPEND_ALL_SIGN. */
    private MessageDigest shaAll;
    /** The places being paid with, in the order they were named. Gone with a SELECT. */
    private byte[] allSlots;
    /** [0] whether a payment is begun and not yet signed for; [1] how many places it names. Gone with a SELECT. */
    private byte[] allState;
    /** What those pieces are worth together. Gone with a SELECT. */
    private byte[] allSum;
    /**
     * The last signature given (64), and whether there has been one (1).
     * Permanent, and written in the transaction that burns the pieces it is
     * for: a card taken away as its answer was on the air has burned them, and
     * the terminal that asked has nothing. It asks again (SPEND_ALL_AGAIN).
     */
    private byte[] lastSig;
    private RandomData rng;

    // -------------------------------------------------------------------------
    // Install / init
    // -------------------------------------------------------------------------

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new CashuApplet().register();
    }

    /**
     * A tap begins. The one thing that crosses from the last tap into this
     * one is that the card paid since it was last loaded: this tap may then
     * put pieces on with no PIN (changeGrant, transient, for this tap). The
     * note (changeDue, persistent) is NOT cleared here — only a load that
     * uses the grant clears it (processLoadProof). So a tap that reads the
     * card and does not write, a tap cut short, and a fresh session the phone
     * opens for the write itself all leave the note standing, and the change
     * still goes on with no PIN at the tap that writes it. The window closes
     * the moment the change lands, and a later stranger's tap gets nothing.
     */
    public boolean select() {
        changeGrant[0] = changeDue[0];
        return true;
    }

    private CashuApplet() {
        proofStorage    = new byte[(short)(MAX_PROOFS * PROOF_SIZE)];
        slotHex         = new byte[(short)(MAX_PROOFS * HEX_LEN)];
        cardLocked      = new byte[1];
        pinState        = new byte[1];
        pin             = new OwnerPIN(PIN_MAX_TRIES, (byte) PIN_MAX_LEN);
        cardRecord      = new byte[CARD_RECORD_LEN];
        authTagHash     = new byte[32];
        ownerSet        = new byte[1];
        changeDue       = new byte[1];
        ownerKey        = newP256Key();
        timeKey         = newP256Key();
        ecdsa           = Signature.getInstance(Signature.ALG_ECDSA_SHA_256, false);
        pinVerifiedFlag = JCSystem.makeTransientByteArray((short) 1, JCSystem.CLEAR_ON_DESELECT);
        ownerNonce      = JCSystem.makeTransientByteArray(OWNER_NONCE_LEN, JCSystem.CLEAR_ON_DESELECT);
        nonceLive       = JCSystem.makeTransientByteArray((short) 1, JCSystem.CLEAR_ON_DESELECT);
        loadGrant       = JCSystem.makeTransientByteArray((short) 1, JCSystem.CLEAR_ON_DESELECT);
        changeGrant     = JCSystem.makeTransientByteArray((short) 1, JCSystem.CLEAR_ON_DESELECT);
        scratch         = JCSystem.makeTransientByteArray(X_LEN, JCSystem.CLEAR_ON_DESELECT);
        cardLog         = new byte[LOG_LEN];
        cardReceipts    = new byte[RECEIPTS_LEN];
        allOut          = JCSystem.makeTransientByteArray((short) 33, JCSystem.CLEAR_ON_DESELECT);
        timeTold        = JCSystem.makeTransientByteArray((short) 6, JCSystem.CLEAR_ON_RESET);
        tapOpen         = JCSystem.makeTransientByteArray((short) 1, JCSystem.CLEAR_ON_RESET);
        sha             = MessageDigest.getInstance(MessageDigest.ALG_SHA_256, false);
        shaAll          = MessageDigest.getInstance(MessageDigest.ALG_SHA_256, false);
        allSlots        = JCSystem.makeTransientByteArray(ALL_MOST, JCSystem.CLEAR_ON_DESELECT);
        // [0] a payment is begun, [1] its places, [2..3] the signatures of work still to be done before it is signed, [4] it waited,
        // [5] its first output is kept (`allOut`)
        allState        = JCSystem.makeTransientByteArray((short) 6, JCSystem.CLEAR_ON_DESELECT);
        allSum          = JCSystem.makeTransientByteArray((short) 4, JCSystem.CLEAR_ON_DESELECT);
        lastSig         = new byte[(short) 65];
        rng             = RandomData.getInstance(RandomData.ALG_SECURE_RANDOM);
        sha.reset();
        sha.doFinal(AUTH_TAG, (short) 0, (short) AUTH_TAG.length, authTagHash, (short) 0);
        initCardKeypair();

        schnorrHW = new SchnorrHW(SECP256K1_G, SECP256K1_P,
                                  SECP256K1_A, SECP256K1_B, SECP256K1_N);
        schnorrHW.init();
    }

    /**
     * Initialises the secp256k1 card keypair.
     *
     * Sets standard secp256k1 curve parameters on the key objects before
     * generating a random key pair.
     *
     * Hardware notes:
     * - JCOP4 SmartMX3 (JavaCard 3.0.5): supports custom EC-FP curves
     * - jCardSim 3.x: supported, but its generator is seeded with
     *   SecureRandomNullProvider, so every simulator produces the SAME keypair
     * - JavaCard 3.0.4 chips are NOT supported by this applet at all — see the
     *   SchnorrHW note above
     */
    private void initCardKeypair() {
        cardKeyPair  = new KeyPair(KeyPair.ALG_EC_FP, KeyBuilder.LENGTH_EC_FP_256);
        cardPrivKey  = (ECPrivateKey) cardKeyPair.getPrivate();
        cardPubKey   = (ECPublicKey)  cardKeyPair.getPublic();
        setSecp256k1Params(cardPubKey, cardPrivKey);
        cardKeyPair.genKeyPair();
    }

    /**
     * A public key on NIST P-256, with the curve's parameters set explicitly
     * (field, a, b, generator, order, cofactor 1). For the owner key and the
     * time key, which verify ECDSA signatures and have nothing to do with the
     * card's own secp256k1 key. Its point (W) is set when one is given.
     */
    private static ECPublicKey newP256Key() {
        ECPublicKey key = (ECPublicKey) KeyBuilder.buildKey(KeyBuilder.TYPE_EC_FP_PUBLIC, KeyBuilder.LENGTH_EC_FP_256, false);
        key.setFieldFP(P256_P, (short) 0, (short) 32);
        key.setA(P256_A, (short) 0, (short) 32);
        key.setB(P256_B, (short) 0, (short) 32);
        key.setG(P256_G, (short) 0, (short) 65);
        key.setR(P256_N, (short) 0, (short) 32);
        key.setK((short) 1);
        return key;
    }

    /**
     * Sets secp256k1 curve parameters on a JavaCard EC key pair.
     *
     * This method uses only the standard JavaCard ECKey API and is
     * hardware-compatible (JCOP4 and any JavaCard 3.0.5+ part with custom EC-FP
     * curve support; jCardSim). Feitian's JavaCard 3.0.4 parts are not in that
     * set — see the note on initCardKeypair above.
     *
     * @param pub  EC public key to configure
     * @param priv EC private key to configure
     */
    private void setSecp256k1Params(ECPublicKey pub, ECPrivateKey priv) {
        pub.setFieldFP(SECP256K1_P, (short) 0, (short) 32);
        pub.setA(SECP256K1_A, (short) 0, (short) 32);
        pub.setB(SECP256K1_B, (short) 0, (short) 32);
        pub.setG(SECP256K1_G, (short) 0, (short) 65);
        pub.setR(SECP256K1_N, (short) 0, (short) 32);
        pub.setK((short) 1);

        priv.setFieldFP(SECP256K1_P, (short) 0, (short) 32);
        priv.setA(SECP256K1_A, (short) 0, (short) 32);
        priv.setB(SECP256K1_B, (short) 0, (short) 32);
        priv.setG(SECP256K1_G, (short) 0, (short) 65);
        priv.setR(SECP256K1_N, (short) 0, (short) 32);
        priv.setK((short) 1);
    }

    // -------------------------------------------------------------------------
    // APDU dispatch
    // -------------------------------------------------------------------------

    @Override
    public void process(APDU apdu) {
        byte[] buf = apdu.getBuffer();

        if (selectingApplet()) {
            buf[0] = VERSION_MAJOR;
            buf[1] = VERSION_MINOR;
            apdu.setOutgoingAndSend((short) 0, (short) 2);
            return;
        }

        if (buf[ISO7816.OFFSET_CLA] != (byte) 0xB0) {
            ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);
        }

        /* A payment begun (SPEND_ALL_BEGIN) is given up by anything that is not
         * its next step: nothing may come between the pieces being named and
         * their being burned and signed for, that could change either. */
        if (buf[ISO7816.OFFSET_INS] != INS_SPEND_ALL_OUTPUTS && buf[ISO7816.OFFSET_INS] != INS_SPEND_ALL_SIGN) {
            allState[0] = (byte) 0;
        }

        switch (buf[ISO7816.OFFSET_INS]) {
            case INS_GET_INFO:         processGetInfo(apdu);        break;
            case INS_GET_PUBKEY:       processGetPubkey(apdu);      break;
            case INS_GET_BALANCE:      processGetBalance(apdu);     break;
            case INS_GET_PROOF_COUNT:  processGetProofCount(apdu);  break;
            case INS_GET_PROOF:        processGetProof(apdu);       break;
            case INS_GET_SLOT_STATUS:  processGetSlotStatus(apdu);  break;
            case INS_AUTH:             processAuth(apdu);           break;
            case INS_GET_CARD:         processGetCard(apdu);        break;
            case INS_GET_PIECES:       processGetPieces(apdu);      break;
            case INS_GET_LOG:          processGetLog(apdu);         break;
            case INS_SPEND_ALL_BEGIN:   processSpendAllBegin(apdu);   break;
            case INS_SPEND_ALL_OUTPUTS: processSpendAllOutputs(apdu); break;
            case INS_SPEND_ALL_SIGN:    processSpendAllSign(apdu);    break;
            case INS_SPEND_ALL_AGAIN:   processSpendAllAgain(apdu);   break;
            case INS_LOAD_PROOF:       processLoadProof(apdu);      break;
            case INS_CLEAR_SPENT:      processClearSpent(apdu);     break;
            case INS_SET_CARD:         processSetCard(apdu);        break;
            case INS_SET_LIMIT:        processSetLimit(apdu);       break;
            case INS_SET_LIMIT_OWNER:  processSetLimitOwner(apdu);  break;
            case INS_SET_TIME:         processSetTime(apdu);        break;
            case INS_VERIFY_PIN:       processVerifyPin(apdu);      break;
            case INS_SET_PIN:          processSetPin(apdu);         break;
            case INS_CHANGE_PIN:       processChangePin(apdu);      break;
            case INS_SET_OWNER:        processSetOwner(apdu);       break;
            case INS_GET_NONCE:        processGetNonce(apdu);       break;
            case INS_ALLOW_LOAD:       processAllowLoad(apdu);      break;
            case INS_LOCK_CARD:        processLockCard(apdu);       break;
            default:
                ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
    }

    // -------------------------------------------------------------------------
    // Category 0x0x / 0x1x — Read commands
    // -------------------------------------------------------------------------

    private void processGetInfo(APDU apdu) {
        byte[] buf = apdu.getBuffer();
        // read before the answer is written over the command
        byte p1 = buf[ISO7816.OFFSET_P1];
        short unspent = 0, spent = 0, empty = 0;
        for (short i = 0; i < MAX_PROOFS; i++) {
            byte status = proofStorage[(short)(i * PROOF_SIZE + PROOF_STATUS_OFFSET)];
            if      (status == STATUS_UNSPENT) unspent++;
            else if (status == STATUS_SPENT)   spent++;
            else                               empty++;
        }
        buf[0] = VERSION_MAJOR;
        buf[1] = VERSION_MINOR;
        buf[2] = (byte) MAX_PROOFS;
        buf[3] = (byte) unspent;
        buf[4] = (byte) spent;
        buf[5] = (byte) empty;
        // Capabilities flags:
        //   bit0 = secp256k1 native key generation (set — ENG-181 complete)
        //   bit1 = BIP-340 Schnorr signing (set — ENG-181 complete)
        //   bit2 = PIN supported (always set)
        // secp256k1 + Schnorr + PIN + the limit on one payment is waited for, not refused
        // + GET_PIECES has its two short forms and LOAD_PROOF takes several pieces (bit 4)
        buf[6] = (byte) 0x1F;
        buf[7] = pinState[0];
        // The fork's: the first eight bytes are upstream's, so a reader that
        // knows only those still reads them right.
        buf[8]  = FORMAT;
        buf[9]  = pin.getTriesRemaining();
        buf[10] = cardLocked[0];
        buf[11] = cardRecord[CARD_SET_OFFSET];
        Util.arrayCopyNonAtomic(cardRecord, CARD_LIMIT_OFFSET, buf, (short) 12, (short) 4);
        // appended: whether the card has an owner. Nothing before it moved.
        buf[16] = ownerSet[0];
        // and the day: the card's clock, when its window began, what it has signed for since
        Util.arrayCopyNonAtomic(cardRecord, CARD_NOW_OFFSET, buf, (short) 17, (short) 4);
        Util.arrayCopyNonAtomic(cardRecord, CARD_WINDOW_OFFSET, buf, (short) 21, (short) 4);
        Util.arrayCopyNonAtomic(cardRecord, CARD_SPENT_OFFSET, buf, (short) 25, (short) 4);
        // and whether this tap, being the one after a payment, may put pieces on with no PIN
        buf[29] = changeGrant[0];
        /* P1 = 1 asks for the limit on one payment as well: the limit, and
         * eight bytes that were a window and its count and are zeros. Asked for,
         * and not simply appended, so that a reader that knows the thirty
         * bytes and checks for them still gets thirty. */
        if (p1 == (byte) 1) {
            /* The limit on one payment is the holder's and is said to the
             * holder only: with the owner's grant given in this tap, and to
             * nobody else, not even under the PIN, which a till has. A
             * terminal that knew it would ask for just under it, again and
             * again, and never be made to wait. To anyone else these four
             * bytes are zeros, which is also what a card with no such limit
             * says: a terminal cannot tell the two apart by asking. (What it
             * can do is find out by trying, a wait at a time: see 6a.) */
            if (loadGrant[0] == (byte) 1) {
                Util.arrayCopyNonAtomic(cardRecord, CARD_TAP_LIMIT_OFFSET, buf, (short) 30, (short) 4);
            } else {
                Util.arrayFillNonAtomic(buf, (short) 30, (short) 4, (byte) 0);
            }
            Util.arrayCopyNonAtomic(cardRecord, CARD_TAP_WINDOW_OFFSET, buf, (short) 34, (short) 4);
            Util.arrayCopyNonAtomic(cardRecord, CARD_TAP_SPENT_OFFSET, buf, (short) 38, (short) 4);
            apdu.setOutgoingAndSend((short) 0, (short) 42);
            return;
        }
        apdu.setOutgoingAndSend((short) 0, (short) 30);
    }

    private void processGetPubkey(APDU apdu) {
        byte[] buf = apdu.getBuffer();
        short len = toCompressed(buf, cardPubKey.getW(buf, (short) 0));
        apdu.setOutgoingAndSend((short) 0, len);
    }

    /**
     * Rewrite an EC public key to the spec's 33-byte compressed form, in place.
     *
     * ECPublicKey.getW() returns the uncompressed point (04 || X || Y, 65 bytes)
     * on real silicon but 33 bytes under jCardSim, so this branch is otherwise
     * never taken in CI — see the direct test. A part already returning 33 bytes
     * is passed through untouched. Anything else (a bare X||Y, a 65-byte blob
     * without the 0x04 marker) is refused with SW_CRYPTO_ERROR rather than
     * handed to the host: SchnorrHW.sign() takes the same stance for the same
     * getW() output, and a mint cannot parse it anyway.
     */
    static short toCompressed(byte[] buf, short len) {
        if (len == (short) 65 && buf[0] == (byte) 0x04) {
            // Prefix replaces the 0x04 marker in place; X already sits at
            // buf[1..32], so only the first byte changes. The prefix encodes the
            // parity of Y's least-significant byte.
            buf[0] = ((buf[64] & 0x01) == 0) ? (byte) 0x02 : (byte) 0x03;
            return (short) 33;
        }
        if (len == (short) 33 && (buf[0] == (byte) 0x02 || buf[0] == (byte) 0x03)) {
            return len;
        }
        ISOException.throwIt(SW_CRYPTO_ERROR);
        return (short) 0; // unreachable
    }

    private void processGetBalance(APDU apdu) {
        byte[] buf = apdu.getBuffer();
        // Accumulate the uint32 total directly in the outgoing buffer.
        // JavaCard has no long, so the sum is done byte-wise with carry.
        buf[0] = 0; buf[1] = 0; buf[2] = 0; buf[3] = 0;
        for (short i = 0; i < MAX_PROOFS; i++) {
            short base = (short)(i * PROOF_SIZE);
            if (proofStorage[(short)(base + PROOF_STATUS_OFFSET)] == STATUS_UNSPENT) {
                addUint32(buf, (short) 0, proofStorage,
                          (short)(base + PROOF_AMOUNT_OFFSET));
            }
        }
        apdu.setOutgoingAndSend((short) 0, (short) 4);
    }

    private void processGetProofCount(APDU apdu) {
        byte[] buf = apdu.getBuffer();
        short count = 0;
        for (short i = 0; i < MAX_PROOFS; i++) {
            if (proofStorage[(short)(i * PROOF_SIZE + PROOF_STATUS_OFFSET)] != STATUS_EMPTY) count++;
        }
        buf[0] = (byte) count;
        apdu.setOutgoingAndSend((short) 0, (short) 1);
    }

    private void processGetProof(APDU apdu) {
        byte[] buf = apdu.getBuffer();
        short idx = (short)(buf[ISO7816.OFFSET_P1] & 0xFF);
        if (idx >= MAX_PROOFS) ISOException.throwIt(SW_SLOT_OUT_OF_RANGE);

        short base = (short)(idx * PROOF_SIZE);
        if (proofStorage[(short)(base + PROOF_STATUS_OFFSET)] == STATUS_EMPTY) {
            ISOException.throwIt(SW_SLOT_EMPTY);
        }
        Util.arrayCopy(proofStorage, base, buf, (short) 0, PROOF_SIZE);
        apdu.setOutgoingAndSend((short) 0, PROOF_SIZE);
    }

    private void processGetSlotStatus(APDU apdu) {
        byte[] buf = apdu.getBuffer();
        for (short i = 0; i < MAX_PROOFS; i++) {
            buf[i] = proofStorage[(short)(i * PROOF_SIZE + PROOF_STATUS_OFFSET)];
        }
        apdu.setOutgoingAndSend((short) 0, MAX_PROOFS);
    }

    /**
     * GET_PIECES: what GET_SLOT_STATUS and a GET_PROOF for each slot say, in as
     * few answers as a short APDU allows. No PIN: it says what they say.
     *
     * P1 is the first slot to report. The answer is
     *
     *   next (1)                  the first slot this answer does not cover;
     *                             64 means there is nothing more to ask for
     *   then, for each slot from P1 up to next that is not empty, in order:
     *     tag (1)                 (state << 6) | slot index; state 1 is unspent, 2 is spent
     *     the piece (81)          only for an unspent slot: what GET_PROOF gives
     *                             after its status byte (keyset, amount, nonce, C, date)
     *
     * A slot in that range with no entry is empty. A spent slot is listed by its
     * tag alone: its bytes stay on the card until CLEAR_SPENT frees the place, and
     * nothing that reads the card wants them. The phone asks again with P1 = next
     * until next is 64.
     *
     * An answer is at most PAGE_MAX bytes, which is under the 256 a short APDU
     * carries, so three unspent pieces (247 bytes) make a page and the card does
     * not need extended length, which is not something every reader and phone
     * will carry. At least one entry always fits, so every answer moves on.
     * The slots are sent from where they sit, with no copy of the page made, so
     * the APDU buffer's size is not something this relies on.
     */
    private void processGetPieces(APDU apdu) {
        byte[] buf = apdu.getBuffer();
        /* P2 = 2: the places named in the data, whole. A till that has chosen
         * its pieces from the brief listing asks for those and no others. */
        if (buf[ISO7816.OFFSET_P2] == (byte) 2) { processGetSome(apdu); return; }
        // a form this card does not have is refused, and not answered with another
        if (buf[ISO7816.OFFSET_P2] != (byte) 0 && buf[ISO7816.OFFSET_P2] != (byte) 1) ISOException.throwIt(ISO7816.SW_INCORRECT_P1P2);
        /* P2 = 1: the brief listing. The same pages and the same tags, and for
         * an unspent place sixteen bytes in place of eighty-one: its keyset,
         * its amount and its date, which is all that choosing pieces needs.
         * Fourteen places to a page where the whole form has three. */
        boolean brief = buf[ISO7816.OFFSET_P2] == (byte) 1;
        short from = (short)(buf[ISO7816.OFFSET_P1] & 0xFF);
        if (from >= MAX_PROOFS) ISOException.throwIt(SW_SLOT_OUT_OF_RANGE);

        // what fits: slots from `from` while the next entry still leaves the answer within the page
        short length = 1;
        short next = from;
        short whole = brief ? (short)(BRIEF_LEN + 1) : PROOF_SIZE;
        while (next < MAX_PROOFS) {
            byte status = proofStorage[(short)(next * PROOF_SIZE + PROOF_STATUS_OFFSET)];
            short cost = (status == STATUS_UNSPENT) ? whole : (status == STATUS_SPENT) ? (short) 1 : (short) 0;
            if ((short)(length + cost) > PAGE_MAX) break;
            length += cost;
            next++;
        }

        apdu.setOutgoing();
        apdu.setOutgoingLength(length);
        buf[0] = (byte) next;
        apdu.sendBytes((short) 0, (short) 1);
        for (short i = from; i < next; i++) {
            short base = (short)(i * PROOF_SIZE);
            byte status = proofStorage[(short)(base + PROOF_STATUS_OFFSET)];
            if (status != STATUS_UNSPENT && status != STATUS_SPENT) continue;
            buf[0] = (byte)((status << 6) | i);
            apdu.sendBytes((short) 0, (short) 1);
            if (status == STATUS_UNSPENT && brief) {
                // the keyset and the amount lie together in the slot; the date is at its end
                apdu.sendBytesLong(proofStorage, (short)(base + PROOF_KEYSET_OFFSET), (short) 12);
                apdu.sendBytesLong(proofStorage, (short)(base + PROOF_DATE_OFFSET), (short) 4);
            } else if (status == STATUS_UNSPENT) {
                apdu.sendBytesLong(proofStorage, (short)(base + PROOF_KEYSET_OFFSET), PROOF_DATA_LEN);
            }
        }
    }

    /**
     * GET_PIECES with P2 = 2: one to BATCH_MOST places, a byte each, answered
     * with each one's slot as GET_PROOF gives it (status, then its 81 bytes),
     * in the order asked. An empty or spent place is answered as it is, so
     * the terminal sees for itself that it is not what the listing said.
     */
    private void processGetSome(APDU apdu) {
        short n = apdu.setIncomingAndReceive();
        if (n < 1 || n > BATCH_MOST) ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        byte[] buf = apdu.getBuffer();
        // the places are kept aside: the answer is written over the buffer they came in
        Util.arrayCopyNonAtomic(buf, ISO7816.OFFSET_CDATA, scratch, X_NUM, n);
        for (short i = 0; i < n; i++) {
            if ((short)(scratch[(short)(X_NUM + i)] & 0xFF) >= MAX_PROOFS) ISOException.throwIt(SW_SLOT_OUT_OF_RANGE);
        }
        apdu.setOutgoing();
        apdu.setOutgoingLength((short)(n * PROOF_SIZE));
        for (short i = 0; i < n; i++) {
            apdu.sendBytesLong(proofStorage, (short)((short)(scratch[(short)(X_NUM + i)] & 0xFF) * PROOF_SIZE), PROOF_SIZE);
        }
    }

    // -------------------------------------------------------------------------
    // Category 0x2x — Spend commands (PIN-gated when a PIN is set or blocked, D13)
    // -------------------------------------------------------------------------

    /*
     * A payment is one signature, however many pieces it is made of (NUT-11
     * SIG_ALL). The message the mint checks it against is every input's secret
     * and its C, in order, and then every output's amount and blinded message:
     *
     *   secret_0 || C_0 || ... || secret_n || C_n || amount_0 || B_0 || ... || amount_m || B_m
     *
     * as text (C and B_ in hex, an amount in decimal), hashed with SHA-256.
     *
     * The card builds the first half itself, from the places it is told to pay
     * with, and burns exactly those: a terminal that could hand it a hash to
     * sign would name one small piece to burn and get a signature good for
     * every piece on the card. The second half is the terminal's, and the card
     * cannot check it: it is where the terminal wants the money to go. It is
     * hashed as it comes, so the signature is good for those outputs and no
     * others.
     *
     * Three commands, in one tap and with nothing between them:
     *
     *   SPEND_ALL_BEGIN    the places, in the order the swap will name them
     *   SPEND_ALL_OUTPUTS  the outputs, thirty-seven bytes each; as many
     *                      commands as they need, or none
     *   SPEND_ALL_SIGN     the signature; the pieces are burned as it is given
     */

    /**
     * SPEND_ALL_BEGIN: one byte for each place to pay with, in order.
     *
     * The PIN, as any spend. Every place named once, holding an unspent piece,
     * and all of one date (pieces with different dates have different lock
     * conditions, and a mint takes no one signature for those: `6A80`). The
     * day's limit is held to what the pieces are worth together, before
     * anything is hashed: over it, `6A8F`, written down in the log before it
     * is refused. What the payment will wait for being over the limit on one
     * payment is worked out from the same sum (`waitsFor`), and paid at SIGN.
     * Answers what the pieces are worth (4, big-endian).
     */
    private void processSpendAllBegin(APDU apdu) {
        // D13: gate FIRST — a wrong or missing PIN throws before anything is looked at.
        // A LOCKED card still spends (lock disables writes, not the bearer's ability to pay).
        requirePinIfSet();
        short n = apdu.setIncomingAndReceive();
        byte[] buf = apdu.getBuffer();
        if (n < (short) 1) ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        if (n > ALL_MOST) ISOException.throwIt(SW_TOO_MANY);
        // kept apart from the APDU buffer, which the hashing below writes over
        Util.arrayCopyNonAtomic(buf, ISO7816.OFFSET_CDATA, allSlots, (short) 0, n);
        Util.arrayFillNonAtomic(allSum, (short) 0, (short) 4, (byte) 0);
        short first = (short)((short)(allSlots[0] & 0xFF) * PROOF_SIZE);
        short carry = 0;
        for (short i = 0; i < n; i++) {
            short idx = (short)(allSlots[i] & 0xFF);
            if (idx >= MAX_PROOFS) ISOException.throwIt(SW_SLOT_OUT_OF_RANGE);
            short base = (short)(idx * PROOF_SIZE);
            byte status = proofStorage[(short)(base + PROOF_STATUS_OFFSET)];
            if (status == STATUS_EMPTY)  ISOException.throwIt(SW_SLOT_EMPTY);
            if (status == STATUS_SPENT)  ISOException.throwIt(SW_ALREADY_SPENT);
            for (short j = 0; j < i; j++) {
                if (allSlots[j] == allSlots[i]) ISOException.throwIt(ISO7816.SW_WRONG_DATA);
            }
            if (!sameBytes(proofStorage, (short)(base + PROOF_DATE_OFFSET), proofStorage, (short)(first + PROOF_DATE_OFFSET), (short) 4)) {
                ISOException.throwIt(ISO7816.SW_WRONG_DATA);
            }
            if (addUint32Carry(allSum, (short) 0, proofStorage, (short)(base + PROOF_AMOUNT_OFFSET)) != 0) carry = 1;
        }
        // a sum that wraps is past any limit, and past what four bytes can say: not a payment
        if (carry != 0) Util.arrayFillNonAtomic(allSum, (short) 0, (short) 4, (byte) 0xFF);
        requireUnderLimits(carry);
        // what this payment costs in time, worked out now and paid at SIGN before anything is burned
        short waits = waitsFor(carry);
        Util.setShort(allState, (short) 2, waits);
        allState[4] = (byte)(waits > 0 ? 1 : 0);
        allState[5] = (byte) 0;

        /* The message's half that is the card's to build. Every piece of a
         * payment has the same key and the same date, so everything in a
         * secret after its nonce is the same text for all of them: built once
         * here (`secretTail`, into the APDU buffer, which is free now that the
         * places are in `allSlots`), and each piece is then four spans handed
         * to the hash, two of them its own hex as it was written at loading. */
        short tail = secretTail(buf, first);
        shaAll.reset();
        for (short i = 0; i < n; i++) {
            secretInto(shaAll, (short)(allSlots[i] & 0xFF), buf, tail);
        }
        allState[1] = (byte) n;
        allState[0] = (byte) 1;
        Util.arrayCopyNonAtomic(allSum, (short) 0, buf, (short) 0, (short) 4);
        apdu.setOutgoingAndSend((short) 0, (short) 4);
    }

    /**
     * SPEND_ALL_OUTPUTS: the swap's outputs, in order, thirty-seven bytes
     * each: the amount (4, big-endian) and the blinded message (33). Hashed as
     * the mint will read them, the amount in decimal and the point in hex. As
     * many of these commands as the outputs need. `6985` with no payment begun.
     */
    private void processSpendAllOutputs(APDU apdu) {
        if (allState[0] != (byte) 1) ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
        short len = apdu.setIncomingAndReceive();
        if (len < ALL_OUTPUT_LEN || (short)(len % ALL_OUTPUT_LEN) != (short) 0) {
            allState[0] = (byte) 0;
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }
        byte[] buf = apdu.getBuffer();
        // the first output of the payment, for its receipt
        if (allState[5] != (byte) 1) {
            Util.arrayCopyNonAtomic(buf, (short)(ISO7816.OFFSET_CDATA + 4), allOut, (short) 0, (short) 33);
            allState[5] = (byte) 1;
        }
        short end = (short)(ISO7816.OFFSET_CDATA + len);
        for (short at = ISO7816.OFFSET_CDATA; at < end; at += ALL_OUTPUT_LEN) {
            // its amount in decimal ending where its B_ in hex begins: one span to the hash
            short digits = decimalBefore(buf, at, (short)(X_OUT + 10));
            hexInto(buf, (short)(at + 4), (short) 33, (short)(X_OUT + 10));
            shaAll.update(scratch, (short)(X_OUT + 10 - digits), (short)(digits + 66));
        }
    }

    /**
     * SPEND_ALL_SIGN: the one signature, 64 bytes, and every piece named at
     * SPEND_ALL_BEGIN burned as it is given.
     *
     * Signed first, into the APDU buffer, which is RAM and leaves the card only
     * with the answer; then burned; then answered. A card taken away while it
     * signs (most of a second) has burned nothing and sent nothing, and no
     * signature leaves the card for pieces that are not burned, because the
     * answer is sent only after the commit. (Burned first, a card pulled away
     * while signing had spent the pieces and given nothing for them.)
     *
     * One transaction for all of it: every piece's place, what the day and the
     * tap have signed for, and the card's own log. A card pulled away in it has
     * done all of it or none. A set too large for one transaction on this card
     * burns nothing and is `6A96`: the terminal names fewer.
     */
    private void processSpendAllSign(APDU apdu) {
        if (allState[0] != (byte) 1) ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
        requirePinIfSet();
        byte[] buf = apdu.getBuffer();

        /* The wait, where the payment is over the limit on one payment (6a).
         * The card has no clock that runs and cannot sleep, so its wait is
         * work: one signature, over bytes of its own choosing, thrown away.
         * That is the one thing here whose time is the chip's and no
         * terminal's. One to a command, so that no command is longer than a
         * signature (a phone gives up on a card that is silent for long), and
         * the answer is "not yet", two bytes, in place of the signature. Nothing is burned and nothing counted until they
         * are done: a card lifted in the wait has lost nothing, and whatever
         * else is sent drops the payment and the wait with it. */
        short waits = Util.getShort(allState, (short) 2);
        if (waits > 0) {
            rng.generateData(scratch, X_MSG, (short) 32);
            schnorrHW.sign(cardPrivKey, cardPubKey, scratch, X_MSG, buf, (short) 0);
            Util.arrayFillNonAtomic(buf, (short) 0, (short) 64, (byte) 0);
            waits--;
            Util.setShort(allState, (short) 2, waits);
            /* "Not yet", and not how many are still to come: the count would
             * say how many limits the payment is over, and so what the limit
             * is, to a terminal that had only to ask for a large payment and
             * give it up. Two bytes, 00 01, every time. */
            Util.setShort(buf, (short) 0, (short) 1);
            apdu.setOutgoingAndSend((short) 0, (short) 2);
            return;
        }

        // one signature for one beginning
        allState[0] = (byte) 0;
        short n = (short)(allState[1] & 0xFF);

        /* The day's limit again, as it stands now: what the day would have
         * signed for is left in scratch (X_SUM) for the commit. */
        requireUnderLimits((short) 0);
        boolean limited = !isZero(cardRecord, CARD_LIMIT_OFFSET, (short) 4);
        boolean newDay = limited && dayIsOver();

        shaAll.doFinal(buf, (short) 0, (short) 0, scratch, X_MSG);
        short sigLen = schnorrHW.sign(cardPrivKey, cardPubKey, scratch, X_MSG, buf, (short) 0);

        try {
            JCSystem.beginTransaction();
            if (limited) {
                if (newDay) Util.arrayCopy(cardRecord, CARD_NOW_OFFSET, cardRecord, CARD_WINDOW_OFFSET, (short) 4);
                Util.arrayCopy(scratch, X_SUM, cardRecord, CARD_SPENT_OFFSET, (short) 4);
            }
            for (short i = 0; i < n; i++) {
                short base = (short)((short)(allSlots[i] & 0xFF) * PROOF_SIZE);
                proofStorage[(short)(base + PROOF_STATUS_OFFSET)] = STATUS_SPENT;
            }
            // and the next tap may put the change on with no PIN (see select)
            changeDue[0] = (byte) 1;
            // and the card's own account of it, with the burn: no piece is burned that the log does not have
            short entry = logEntry();
            addUint32Stop(cardLog, (short)(entry + LOG_E_SATS), allSum, (short) 0);
            short pieces = (short)((short)(cardLog[(short)(entry + LOG_E_PIECES)] & 0xFF) + n);
            cardLog[(short)(entry + LOG_E_PIECES)] = (byte)(pieces > (short) 255 ? (short) 255 : pieces);
            addUint32Stop(cardLog, LOG_SATS_OFFSET, allSum, (short) 0);
            // a payment that was over the limit on one payment says so in the card's account of the tap
            if (allState[4] == (byte) 1) cardLog[(short)(entry + LOG_E_FLAGS)] |= LOG_FLAG_WAITED;
            // and the signature itself, for the terminal whose answer is lost on the air (SPEND_ALL_AGAIN)
            Util.arrayCopy(buf, (short) 0, lastSig, (short) 1, (short) 64);
            lastSig[0] = (byte) 1;
            JCSystem.commitTransaction();
        } catch (TransactionException e) {
            if (JCSystem.getTransactionDepth() != (byte) 0) JCSystem.abortTransaction();
            ISOException.throwIt(SW_TOO_MANY);
        }
        tapOpen[0] = (byte) 1;

        /* The receipt, after the burn and by itself: what it says is true
         * only of a payment that was made, and it is kept out of the burn's
         * own transaction, which has to hold thirty-two places. A card pulled
         * away between the two has the payment in its log and no receipt. */
        short slot = (short)((cardReceipts[3] & 0x0F) * RECEIPT_LEN + RECEIPTS_HEAD);
        JCSystem.beginTransaction();
        Util.arrayCopy(cardRecord, CARD_NOW_OFFSET, cardReceipts, slot, (short) 4);
        Util.arrayCopy(allSum, (short) 0, cardReceipts, (short)(slot + 4), (short) 4);
        Util.arrayCopy(scratch, X_MSG, cardReceipts, (short)(slot + 8), (short) 32);
        if (allState[5] == (byte) 1) {
            Util.arrayCopy(allOut, (short) 0, cardReceipts, (short)(slot + 40), (short) 33);
        } else {
            Util.arrayFillNonAtomic(cardReceipts, (short)(slot + 40), (short) 33, (byte) 0);
        }
        addUint32Stop(cardReceipts, (short) 0, ONE, (short) 0);
        JCSystem.commitTransaction();

        apdu.setOutgoingAndSend((short) 0, sigLen);
    }

    /**
     * SPEND_ALL_AGAIN: the last signature this card gave, 64 bytes, again.
     *
     * The pieces are burned as the signature is given, and the answer is the
     * one thing that can still be lost: a card taken away in those few
     * milliseconds has spent the payment's pieces, all of them, and the
     * terminal has no signature to spend them with. This is the way back. It
     * gives nothing away: the signature is good only for the pieces it burned
     * and the outputs it was made for, which are the asking terminal's own,
     * and a terminal that did not make that payment can do nothing with it.
     * The PIN, as a spend. `6A88` on a card that has never signed.
     */
    private void processSpendAllAgain(APDU apdu) {
        requirePinIfSet();
        if (lastSig[0] != (byte) 1) ISOException.throwIt(SW_SLOT_EMPTY);
        byte[] buf = apdu.getBuffer();
        Util.arrayCopyNonAtomic(lastSig, (short) 1, buf, (short) 0, (short) 64);
        apdu.setOutgoingAndSend((short) 0, (short) 64);
    }

    /**
     * The day's limit, held to what the pieces named at SPEND_ALL_BEGIN are
     * worth together (`allSum`; `carry` where that sum wrapped). Leaves in
     * scratch what the day (X_SUM) would have signed for with them.
     *
     * A terminal that has the PIN picks the places, and nothing on the card
     * can tell its request from the holder's, so what bounds it is a number in
     * permanent memory, counted against a window that only time can end. The
     * pieces are charged at their whole worth, not at the price of the
     * payment: change that a terminal writes back is not something the card
     * can check. A limit of 0 is no limit: nothing is checked, and nothing
     * counted. Over it, the spend is written down and refused.
     */
    private void requireUnderLimits(short carry) {
        boolean limited = !isZero(cardRecord, CARD_LIMIT_OFFSET, (short) 4);
        // never been told the time: a card that cannot know the day does not spend under a daily limit
        if (limited && isZero(cardRecord, CARD_NOW_OFFSET, (short) 4)) ISOException.throwIt(SW_NO_TIME);
        if (limited) {
            if (dayIsOver()) {
                Util.arrayFillNonAtomic(scratch, X_SUM, (short) 4, (byte) 0);
            } else {
                Util.arrayCopyNonAtomic(cardRecord, CARD_SPENT_OFFSET, scratch, X_SUM, (short) 4);
            }
            short over = addUint32Carry(scratch, X_SUM, allSum, (short) 0);
            if (carry != 0 || over != 0 || cmpUint32(scratch, X_SUM, cardRecord, CARD_LIMIT_OFFSET) > 0) {
                refuseOverLimit(SW_OVER_LIMIT);
            }
        }
    }

    /**
     * What the payment begun costs in time: the signatures of work to be done
     * at SPEND_ALL_SIGN before it is signed (6a).
     *
     * With a limit on one payment of L and pieces worth S together, the first
     * L is free and every L after it, whole or in part, is WAIT_SIGNS
     * signatures: (ceil(S / L) - 1) * WAIT_SIGNS. Nothing is remembered from
     * one payment to the next and no clock is asked, so there is nothing a
     * terminal can replay or reset to make it less: a payment of ten limits
     * waits for nine, today and at any other time. What a terminal can do is
     * take the money a limit at a time, each a signature of its own, which is
     * the rate this limit holds it to. No limit, or a payment within it: 0.
     */
    private short waitsFor(short carry) {
        if (isZero(cardRecord, CARD_TAP_LIMIT_OFFSET, (short) 4)) return (short) 0;
        if (carry != 0) return (short)(WAIT_UNITS_MOST * WAIT_SIGNS);
        Util.arrayCopyNonAtomic(allSum, (short) 0, scratch, X_TAP, (short) 4);
        short units = 0;
        // what is left after each limit's worth is taken off: while more than a limit is left, another has to be waited for
        while (units < WAIT_UNITS_MOST && cmpUint32(scratch, X_TAP, cardRecord, CARD_TAP_LIMIT_OFFSET) > 0) {
            subUint32(scratch, X_TAP, cardRecord, CARD_TAP_LIMIT_OFFSET);
            units++;
        }
        return (short)(units * WAIT_SIGNS);
    }

    /** a -= b, four bytes each, big-endian; a is not less than b. */
    private static void subUint32(byte[] a, short aOff, byte[] b, short bOff) {
        short borrow = 0;
        for (short i = 3; i >= 0; i--) {
            short d = (short)((short)(a[(short)(aOff + i)] & 0xFF) - (short)(b[(short)(bOff + i)] & 0xFF) - borrow);
            if (d < 0) { d += (short) 256; borrow = 1; } else { borrow = 0; }
            a[(short)(aOff + i)] = (byte) d;
        }
    }

    /**
     * What comes after the nonce in the NUT-10 secret of every piece of a
     * payment, as text, into `buf` from 0; answers its length (216 at most):
     *
     *   ","data":"<card key>","tags":[["sigflag","SIG_ALL"]]}]
     *   ","data":"<card key>","tags":[["locktime","<date>"],["refund","<refund key>"],["sigflag","SIG_ALL"]]}]
     *
     * `base` is one of the payment's slots: they are all of one date
     * (SPEND_ALL_BEGIN has seen to it), and the key is the card's.
     */
    private short secretTail(byte[] buf, short base) {
        // the key first, since exporting it uses the buffer: its hex waits in scratch while the text is begun
        short len = toCompressed(buf, cardPubKey.getW(buf, (short) 0));
        toHex(buf, (short) 0, len);
        short at = Util.arrayCopyNonAtomic(SECRET_2, (short) 0, buf, (short) 0, (short) SECRET_2.length);
        at = Util.arrayCopyNonAtomic(scratch, X_HEX, buf, at, (short) 66);
        at = Util.arrayCopyNonAtomic(SECRET_3, (short) 0, buf, at, (short) SECRET_3.length);
        if (isZero(proofStorage, (short)(base + PROOF_DATE_OFFSET), (short) 4)) {
            return Util.arrayCopyNonAtomic(SECRET_END, (short) 0, buf, at, (short) SECRET_END.length);
        }
        at = Util.arrayCopyNonAtomic(SECRET_DATE, (short) 0, buf, at, (short) SECRET_DATE.length);
        short digits = toDecimal(proofStorage, (short)(base + PROOF_DATE_OFFSET));
        at = Util.arrayCopyNonAtomic(scratch, (short)(X_DEC + 10 - digits), buf, at, digits);
        at = Util.arrayCopyNonAtomic(SECRET_REFUND, (short) 0, buf, at, (short) SECRET_REFUND.length);
        toHex(cardRecord, CARD_REFUND_OFFSET, (short) 33);
        at = Util.arrayCopyNonAtomic(scratch, X_HEX, buf, at, (short) 66);
        return Util.arrayCopyNonAtomic(SECRET_END_DATED, (short) 0, buf, at, (short) SECRET_END_DATED.length);
    }

    /**
     * The piece in place `idx` into `md`, as a payment's message has it: its
     * NUT-10 secret as text and then its C in hex, not finished, so that more
     * can follow. `buf` holds the `tail` bytes `secretTail` wrote.
     */
    private void secretInto(MessageDigest md, short idx, byte[] buf, short tail) {
        short hex = (short)(idx * HEX_LEN);
        md.update(SECRET_1, (short) 0, (short) SECRET_1.length);
        md.update(slotHex, hex, (short) 64);
        md.update(buf, (short) 0, tail);
        md.update(slotHex, (short)(hex + 64), (short) 66);
    }

    /**
     * AUTH: that this is the card whose key GET_PUBKEY gives.
     *
     * The reader sends 16 random bytes. The card answers 16 of its own and a
     * BIP-340 signature over
     *
     *   SHA-256( SHA-256("FoxyCard/auth") || SHA-256("FoxyCard/auth")
     *            || reader's 16 || card's 16 || the card's key, compressed )
     *
     * No PIN: it spends nothing and says only what GET_PUBKEY already has. It
     * is not SIGN_ARBITRARY by another name. That signed the reader's 32 bytes
     * as they came, which could be a piece's own message; here the reader's
     * bytes are hashed under a tag with the card's, and a spend's message is
     * the hash of a secret's text, which no such hash can equal.
     */
    private void processAuth(APDU apdu) {
        short len = apdu.setIncomingAndReceive();
        if (len != AUTH_NONCE_LEN) ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        byte[] buf = apdu.getBuffer();

        sha.reset();
        sha.update(authTagHash, (short) 0, (short) 32);
        sha.update(authTagHash, (short) 0, (short) 32);
        sha.update(buf, ISO7816.OFFSET_CDATA, AUTH_NONCE_LEN);
        // the card's own, kept in scratch while the buffer is used for the key
        rng.generateData(scratch, X_HEX, AUTH_NONCE_LEN);
        sha.update(scratch, X_HEX, AUTH_NONCE_LEN);
        short keyLen = toCompressed(buf, cardPubKey.getW(buf, (short) 0));
        sha.doFinal(buf, (short) 0, keyLen, scratch, X_MSG);

        Util.arrayCopyNonAtomic(scratch, X_HEX, buf, (short) 0, AUTH_NONCE_LEN);
        short sigLen = schnorrHW.sign(cardPrivKey, cardPubKey, scratch, X_MSG, buf, AUTH_NONCE_LEN);
        apdu.setOutgoingAndSend((short) 0, (short)(AUTH_NONCE_LEN + sigLen));
    }

    /** GET_CARD: format, whether the record is set, unit, limit, refund key, time key, mint. */
    private void processGetCard(APDU apdu) {
        byte[] buf = apdu.getBuffer();
        short mintLen = (short)(cardRecord[CARD_MINTLEN_OFFSET] & 0xFF);
        buf[0] = FORMAT;
        buf[1] = cardRecord[CARD_SET_OFFSET];
        buf[2] = cardRecord[CARD_UNIT_OFFSET];
        Util.arrayCopyNonAtomic(cardRecord, CARD_LIMIT_OFFSET, buf, (short) 3, (short) 4);
        Util.arrayCopyNonAtomic(cardRecord, CARD_REFUND_OFFSET, buf, (short) 7, (short) 33);
        Util.arrayCopyNonAtomic(cardRecord, CARD_TIMEKEY_OFFSET, buf, (short) 40, EC_POINT_LEN);
        buf[105] = (byte) mintLen;
        Util.arrayCopyNonAtomic(cardRecord, CARD_MINT_OFFSET, buf, (short) 106, mintLen);
        apdu.setOutgoingAndSend((short) 0, (short)(106 + mintLen));
    }

    // -------------------------------------------------------------------------
    // Category 0x3x — Write commands (PIN required if set)
    // -------------------------------------------------------------------------

    private void processLoadProof(APDU apdu) {
        requireNotLocked();
        // Nothing goes onto a card with no PIN: upstream's did, and then any
        // reader in range could spend it. The PIN is typed, or the owner has
        // allowed loading in this tap (ALLOW_LOAD).
        requireLoadAuthority();
        // Nor onto a card with no owner: nobody could change its PIN or its
        // limit, and a terminal that had the PIN could never be bounded or
        // corrected.
        if (ownerSet[0] != (byte) 1) ISOException.throwIt(SW_NO_OWNER);
        if (cardRecord[CARD_SET_OFFSET] != (byte) 1) ISOException.throwIt(SW_NO_CARD_RECORD);
        // Nor onto a card that has never been told the time: so the earliest
        // `now` a funded card can hold is its own loading, and (it only moves
        // forward) no older time can ever get in after it.
        if (isZero(cardRecord, CARD_NOW_OFFSET, (short) 4)) ISOException.throwIt(SW_NO_TIME);

        // a full card says so whatever it is sent
        if (emptySlot() < 0) ISOException.throwIt(SW_NO_SPACE);

        /* One piece, or up to BATCH_MOST of them end to end: a card is loaded
         * with thirty at a time, and a command each was most of what loading
         * took. They are taken in order, each exactly as one alone would be.
         * The first that cannot be stored stops it: alone, or first, it is
         * refused with its own word, as it always was; after others, the
         * answer is the ones that did go on, and the terminal sends the rest
         * again to hear why. The answer is the place of each piece stored. */
        short dataLen = apdu.setIncomingAndReceive();
        if (dataLen < PROOF_DATA_LEN || dataLen > (short)(BATCH_MOST * PROOF_DATA_LEN)
            || (short)(dataLen % PROOF_DATA_LEN) != (short) 0) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }
        byte[] buf = apdu.getBuffer();
        short n = (short)(dataLen / PROOF_DATA_LEN);
        short done = 0;
        for (short k = 0; k < n; k++) {
            short sw = loadOne(buf, (short)(ISO7816.OFFSET_CDATA + (short)(k * PROOF_DATA_LEN)), (short)(X_NUM + k));
            if (sw != (short) 0) {
                if (k == (short) 0) ISOException.throwIt(sw);
                break;
            }
            done++;
        }
        /* And the card's own account of it (6b): what was put on in this tap,
         * once for the command. After the pieces, which are on the card
         * whether or not this is written. */
        if (done > 0) {
            Util.arrayFillNonAtomic(scratch, X_SUM, (short) 4, (byte) 0);
            for (short k = 0; k < done; k++) {
                addUint32Stop(scratch, X_SUM, buf, (short)(ISO7816.OFFSET_CDATA + (short)(k * PROOF_DATA_LEN) + PROOF_AMOUNT_OFFSET - 1));
            }
            JCSystem.beginTransaction();
            short entry = logEntry();
            addUint32Stop(cardLog, (short)(entry + LOG_E_LOADED), scratch, X_SUM);
            short loads = (short)((short)(cardLog[(short)(entry + LOG_E_LOADS)] & 0xFF) + done);
            cardLog[(short)(entry + LOG_E_LOADS)] = (byte)(loads > (short) 255 ? (short) 255 : loads);
            JCSystem.commitTransaction();
            tapOpen[0] = (byte) 1;
        }
        Util.arrayCopyNonAtomic(scratch, X_NUM, buf, (short) 0, done);
        apdu.setOutgoingAndSend((short) 0, done);
    }

    /** The first empty place, or -1. */
    private short emptySlot() {
        for (short i = 0; i < MAX_PROOFS; i++) {
            if (proofStorage[(short)(i * PROOF_SIZE + PROOF_STATUS_OFFSET)] == STATUS_EMPTY) return i;
        }
        return (short) -1;
    }

    /**
     * One piece, its 81 bytes at `in` in `buf`, into the first empty place.
     * Answers 0 and leaves the place's number in scratch[`out`], or answers
     * the status word it is refused with, having written nothing.
     */
    private short loadOne(byte[] buf, short in, short out) {
        short slot = emptySlot();
        if (slot < 0) return SW_NO_SPACE;
        /* A piece with a date names the card's refund key in its secret, so a
         * card with none cannot hold one: the card could not build the secret
         * the mint signed, and its signature would be worth nothing. */
        if (!isZero(buf, (short)(in + PROOF_DATE_OFFSET - 1), (short) 4)
            && cardRecord[CARD_REFUND_OFFSET] == (byte) 0) {
            return SW_NO_REFUND_KEY;
        }
        // not a point, or worth nothing: not a piece
        byte c0 = buf[(short)(in + PROOF_C_OFFSET - 1)];
        if ((c0 != (byte) 0x02 && c0 != (byte) 0x03)
            || isZero(buf, (short)(in + PROOF_AMOUNT_OFFSET - 1), (short) 4)) {
            return ISO7816.SW_WRONG_DATA;
        }
        /* Not a piece that is here already. What the card signs is the piece's
         * secret, which is its nonce (with the card's key and the date) and
         * not its amount: the amount is only what the day is charged. So a
         * second copy of a piece, written with a smaller amount, would be
         * signed for the real piece's secret at the price of the small one,
         * and the day's limit would count for nothing. A nonce that is in any
         * slot, spent or not, is refused, before anything is written. A slot
         * that CLEAR_SPENT has freed holds nothing, so that piece may be
         * loaded again then, if its signature was lost. (One of the pieces
         * before it in the same command is in a slot by now, and is seen.) */
        for (short i = 0; i < MAX_PROOFS; i++) {
            short at = (short)(i * PROOF_SIZE);
            if (proofStorage[(short)(at + PROOF_STATUS_OFFSET)] != STATUS_EMPTY
                && Util.arrayCompare(proofStorage, (short)(at + PROOF_NONCE_OFFSET),
                                     buf, (short)(in + PROOF_NONCE_OFFSET - 1), (short) 32) == 0) {
                return SW_PIECE_ON_CARD;
            }
        }
        short base = (short)(slot * PROOF_SIZE);
        short hex = (short)(slot * HEX_LEN);
        // its nonce and its C as the text a payment hashes (`slotHex`): before the data, and long before the status byte
        toHex(buf, (short)(in + PROOF_NONCE_OFFSET - 1), (short) 32);
        Util.arrayCopyNonAtomic(scratch, X_HEX, slotHex, hex, (short) 64);
        toHex(buf, (short)(in + PROOF_C_OFFSET - 1), (short) 33);
        Util.arrayCopyNonAtomic(scratch, X_HEX, slotHex, (short)(hex + 64), (short) 66);
        // The status byte is the slot's commit, written last (D14, ENG-620).
        // Util.arrayCopy into persistent memory is atomic, and so is a single
        // byte write, but the pair is not: with the status first, a card
        // pulled between them left a slot marked UNSPENT over whatever it held
        // before. Data first, a tear leaves the slot EMPTY with the new bytes
        // in it, which no read looks at and the next LOAD_PROOF overwrites.
        Util.arrayCopy(buf, in, proofStorage, (short)(base + PROOF_KEYSET_OFFSET), PROOF_DATA_LEN);
        proofStorage[(short)(base + PROOF_STATUS_OFFSET)] = STATUS_UNSPENT;

        // A load that the change grant alone allowed (no verified PIN, no
        // owner grant) is the change going on: the note is spent now, not at
        // SELECT, so a glance or a cut-short tap before this never lost it.
        // The rest of the change in this same tap still has changeGrant
        // (transient) and goes on; the next fresh tap sees the note gone.
        if (pinVerifiedFlag[0] != (byte) 1 && loadGrant[0] != (byte) 1) {
            changeDue[0] = (byte) 0;
        }
        scratch[out] = (byte) slot;
        return (short) 0;
    }

    private void processClearSpent(APDU apdu) {
        requireNotLocked();
        // the PIN, or the owner's grant for this tap, or the tap after a payment
        if (pinState[0] != (byte) 0 && pinVerifiedFlag[0] != (byte) 1 && loadGrant[0] != (byte) 1 && changeGrant[0] != (byte) 1) {
            ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED);
        }

        byte[] buf = apdu.getBuffer();
        short freed = 0;
        for (short i = 0; i < MAX_PROOFS; i++) {
            short base = (short)(i * PROOF_SIZE);
            if (proofStorage[(short)(base + PROOF_STATUS_OFFSET)] == STATUS_SPENT) {
                // Data first, status last (D14, ENG-620). The fill is not
                // atomic, so a tear mid-fill leaves a SPENT slot with some of
                // its bytes zeroed: never counted or signed for, and finished
                // by the next CLEAR_SPENT. An EMPTY slot never holds a spent
                // proof's bytes for a torn LOAD_PROOF to resurrect.
                Util.arrayFillNonAtomic(proofStorage, (short)(base + PROOF_KEYSET_OFFSET), PROOF_DATA_LEN, (byte) 0);
                proofStorage[(short)(base + PROOF_STATUS_OFFSET)] = STATUS_EMPTY;
                freed++;
            }
        }
        buf[0] = (byte) freed;
        apdu.setOutgoingAndSend((short) 0, (short) 1);
    }

    /**
     * SET_CARD: unit (1), refund key (33, zeros for none), time key (65, 04 ||
     * X || Y), mint length (1), mint. On a card with an owner the owner's proof
     * comes first: its length (1) and the proof (DER).
     *
     * Only with nothing unspent on the card. The refund key is part of every
     * dated piece's secret and the mint is where every piece is, so changing
     * either under pieces already loaded would leave them unspendable or
     * unfindable; and the time key decides what the card believes the day is.
     *
     * Who may: on a card with NO owner, a verified PIN; on a card WITH an
     * owner, the owner's proof over everything sent, whether or not the card
     * is empty (a terminal that holds the PIN can make a card empty, and must
     * not be able to set its own record then). The proof replaces the PIN: the
     * owner's phone does not know it.
     *
     * A time key different from the one the card holds clears the card's clock
     * (`now`) and the window with it, and nothing else does: it is the way out
     * of a signer's fault. The limit stays. One transaction: the record is
     * whole or as it was.
     */
    private void processSetCard(APDU apdu) {
        requireNotLocked();
        boolean owned = ownerSet[0] == (byte) 1;
        if (!owned) requirePinSetAndVerified();
        short dataLen = apdu.setIncomingAndReceive();
        byte[] buf = apdu.getBuffer();
        short at = ISO7816.OFFSET_CDATA;
        short len = dataLen;
        if (owned) {
            at = requireOwnerProof(LABEL_SET_CARD, buf, dataLen);
            len = (short)(ISO7816.OFFSET_CDATA + dataLen - at);
        }
        requireNothingUnspent();
        if (len < (short)(SET_CARD_FIXED + 1)) ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        short mintLen = (short)(buf[(short)(at + SET_CARD_MINTLEN_AT)] & 0xFF);
        if (mintLen < 1 || mintLen > CARD_MINT_MAX || len != (short)(SET_CARD_FIXED + mintLen)) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }
        // a refund key is a compressed point, or 33 zeros for none
        byte r0 = buf[(short)(at + 1)];
        if (r0 == (byte) 0) {
            if (!isZero(buf, (short)(at + 1), (short) 33)) ISOException.throwIt(ISO7816.SW_WRONG_DATA);
        } else if (r0 != (byte) 0x02 && r0 != (byte) 0x03) {
            ISOException.throwIt(ISO7816.SW_WRONG_DATA);
        }
        // the time key is an uncompressed point, and one the verifier takes
        short keyAt = (short)(at + SET_CARD_TIMEKEY_AT);
        if (buf[keyAt] != (byte) 0x04) ISOException.throwIt(ISO7816.SW_WRONG_DATA);
        try {
            timeKey.setW(buf, keyAt, EC_POINT_LEN);
        } catch (CryptoException e) {
            ISOException.throwIt(ISO7816.SW_WRONG_DATA);
        }
        boolean newKey = !sameBytes(cardRecord, CARD_TIMEKEY_OFFSET, buf, keyAt, EC_POINT_LEN);
        JCSystem.beginTransaction();
        cardRecord[CARD_UNIT_OFFSET] = buf[at];
        Util.arrayCopy(buf, (short)(at + 1), cardRecord, CARD_REFUND_OFFSET, (short) 33);
        cardRecord[CARD_MINTLEN_OFFSET] = (byte) mintLen;
        Util.arrayCopy(buf, (short)(at + SET_CARD_FIXED), cardRecord, CARD_MINT_OFFSET, mintLen);
        Util.arrayCopy(buf, keyAt, cardRecord, CARD_TIMEKEY_OFFSET, EC_POINT_LEN);
        if (newKey) {
            // the clock is the old key's signer's to have set; the window is in its units
            Util.arrayFillNonAtomic(cardRecord, CARD_NOW_OFFSET, (short) 4, (byte) 0);
            Util.arrayFillNonAtomic(cardRecord, CARD_WINDOW_OFFSET, (short) 4, (byte) 0);
            Util.arrayFillNonAtomic(cardRecord, CARD_SPENT_OFFSET, (short) 4, (byte) 0);
            Util.arrayFillNonAtomic(cardRecord, CARD_TAP_WINDOW_OFFSET, (short) 4, (byte) 0);
            Util.arrayFillNonAtomic(cardRecord, CARD_TAP_SPENT_OFFSET, (short) 4, (byte) 0);
        }
        cardRecord[CARD_SET_OFFSET] = (byte) 1;
        JCSystem.commitTransaction();
    }

    /**
     * SET_LIMIT, the PIN form (0x33): the limit (4, big-endian).
     *
     * For an OPEN card only: one with no owner and nothing unspent, and a
     * verified PIN. On a card with an owner the PIN cannot set the limit, empty
     * or not (a terminal holding it would otherwise remove the day's bound
     * whenever the card was empty); the owner's form is 0x34.
     */
    private void processSetLimit(APDU apdu) {
        requireNotLocked();
        requirePinSetAndVerified();
        if (ownerSet[0] == (byte) 1) ISOException.throwIt(SW_OWNER_PROOF);
        requireNothingUnspent();
        short dataLen = apdu.setIncomingAndReceive();
        if (dataLen != (short) 4 && dataLen != (short) 8) ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        writeLimit(apdu.getBuffer(), ISO7816.OFFSET_CDATA, dataLen);
    }

    /**
     * SET_LIMIT, the owner's form (0x34): the proof's length (1), the proof
     * (DER), then the limit (4, big-endian).
     *
     * No PIN, and any funds: the owner's phone does not know the PIN, and sets
     * or removes the limit on a funded card. A proof for one number is no proof
     * for another, and a proof once used is no proof.
     */
    private void processSetLimitOwner(APDU apdu) {
        requireNotLocked();
        if (ownerSet[0] != (byte) 1) ISOException.throwIt(SW_NO_OWNER);
        short dataLen = apdu.setIncomingAndReceive();
        byte[] buf = apdu.getBuffer();
        short at = requireOwnerProof(LABEL_SET_LIMIT, buf, dataLen);
        short len = (short)(ISO7816.OFFSET_CDATA + dataLen - at);
        if (len != (short) 4 && len != (short) 8) ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        writeLimit(buf, at, len);
    }

    /**
     * The limit written, and a window begun at `now` with nothing spent in it.
     * A limit needs a time to start from (`6A92`); a limit of 0 (none) does not.
     * One transaction: the limit and its window are all new or all old.
     */
    /*
     * `len` is 4 or 8. Four bytes are the day's limit, as they always were,
     * and the limit on one payment is left as it is. Eight are both: the
     * day's, then that one. In that form a day's limit whose number is not
     * changed keeps its window and what was signed for in it, so that setting
     * the other does not begin the day again; a day's number that changes
     * begins its window at `now` with nothing spent, as the four-byte form
     * always does. The limit on one payment has no window to begin.
     */
    private void writeLimit(byte[] src, short at, short len) {
        boolean both = len == (short) 8;
        // the day's limit is counted against the clock, and needs one; the limit on one payment asks no clock
        if (!isZero(src, at, (short) 4) && isZero(cardRecord, CARD_NOW_OFFSET, (short) 4)) {
            ISOException.throwIt(SW_NO_TIME);
        }
        boolean dayChanged = !both || !sameBytes(src, at, cardRecord, CARD_LIMIT_OFFSET, (short) 4);
        boolean tapChanged = both && !sameBytes(src, (short)(at + 4), cardRecord, CARD_TAP_LIMIT_OFFSET, (short) 4);
        JCSystem.beginTransaction();
        if (dayChanged) {
            Util.arrayCopy(src, at, cardRecord, CARD_LIMIT_OFFSET, (short) 4);
            Util.arrayCopy(cardRecord, CARD_NOW_OFFSET, cardRecord, CARD_WINDOW_OFFSET, (short) 4);
            Util.arrayFillNonAtomic(cardRecord, CARD_SPENT_OFFSET, (short) 4, (byte) 0);
        }
        if (tapChanged) {
            Util.arrayCopy(src, (short)(at + 4), cardRecord, CARD_TAP_LIMIT_OFFSET, (short) 4);
        }
        JCSystem.commitTransaction();
    }

    /**
     * SET_TIME: a time (4, big-endian seconds), the signature's length (1), and
     * the signature (DER) by the time key over "FoxyCard/time" || the time.
     *
     * No PIN, no owner, and no state of the card refuses it: a blocked or locked
     * card still takes the time. A time can only move the clock forward. An old
     * or repeated one changes nothing and is answered `9000`, so a terminal
     * that sends the time it has is never in the wrong; a signature that is not
     * the time key's changes nothing and is `6A93`. Answers the card's `now`.
     *
     * There is no nonce and no freshness: a stale time cannot help anybody, and
     * a fresh one is the truth, so a signed time is a public broadcast.
     */
    private void processSetTime(APDU apdu) {
        short dataLen = apdu.setIncomingAndReceive();
        byte[] buf = apdu.getBuffer();
        short at = ISO7816.OFFSET_CDATA;
        if (dataLen < (short) 6) ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        short sigLen = (short)(buf[(short)(at + 4)] & 0xFF);
        if (sigLen < 1 || sigLen > SIG_DER_MAX || dataLen != (short)(5 + sigLen)) ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        // the time key lives in the record, so a card with no record has none to check against
        if (cardRecord[CARD_SET_OFFSET] != (byte) 1) ISOException.throwIt(SW_NO_CARD_RECORD);
        boolean good = false;
        try {
            timeKey.setW(cardRecord, CARD_TIMEKEY_OFFSET, EC_POINT_LEN);
            ecdsa.init(timeKey, Signature.MODE_VERIFY);
            ecdsa.update(LABEL_TIME, (short) 0, (short) LABEL_TIME.length);
            good = ecdsa.verify(buf, at, (short) 4, buf, (short)(at + 5), sigLen);
        } catch (CryptoException e) {
            good = false;
        }
        if (!good) ISOException.throwIt(SW_NOT_THE_TIME);
        /* The card cannot know the time, only that it is not being told an
         * earlier one. What it can see is being told twice in one time in the
         * field, times far apart: no phone's clock moves two minutes in the
         * one tap, and a terminal walking the clock forward to turn the day
         * does exactly that. It is not refused (the card has no way to say
         * which of the two was the lie) and it is written down (6b). A
         * terminal that cuts the field between the two is not seen here; the
         * holder's phone, which knows the time, sees a clock that is ahead
         * of it.
         *
         * Judged against where the FIRST telling of this time in the field
         * left the clock, and not against the clock as it stands: a terminal
         * that walked it on a minute at a time would never be two minutes
         * from where it stood. And it begins no entry in the log: SET_TIME
         * needs no PIN, and an entry for every mark would let anybody near
         * the card push its eight taps out of the ring with marks. The count
         * of marked things goes up by one, once for this time in the field;
         * the tap's entry is marked if it has one, and if it gets one later
         * (`logEntry`). */
        boolean jumped = false;
        if (timeTold[0] == (byte) 1 && timeTold[1] != (byte) 1 && !isZero(timeTold, (short) 2, (short) 4)) {
            Util.arrayCopyNonAtomic(timeTold, (short) 2, scratch, X_NUM, (short) 4);
            jumped = addUint32Carry(scratch, X_NUM, CLOCK_JUMP, (short) 0) == 0 && cmpUint32(buf, at, scratch, X_NUM) > 0;
        }
        // only forward; one four-byte copy, so the clock is the old time or the new
        if (cmpUint32(buf, at, cardRecord, CARD_NOW_OFFSET) > 0) {
            Util.arrayCopy(buf, at, cardRecord, CARD_NOW_OFFSET, (short) 4);
        }
        if (timeTold[0] != (byte) 1) {
            timeTold[0] = (byte) 1;
            Util.arrayCopyNonAtomic(cardRecord, CARD_NOW_OFFSET, timeTold, (short) 2, (short) 4);
        }
        if (jumped) {
            timeTold[1] = (byte) 1;
            JCSystem.beginTransaction();
            addUint32Stop(cardLog, LOG_TAMPERS_OFFSET, ONE, (short) 0);
            if (tapOpen[0] == (byte) 1) {
                short entry = logEntry();
                cardLog[(short)(entry + LOG_E_FLAGS)] |= LOG_FLAG_CLOCK;
            }
            JCSystem.commitTransaction();
        }
        Util.arrayCopyNonAtomic(cardRecord, CARD_NOW_OFFSET, buf, (short) 0, (short) 4);
        apdu.setOutgoingAndSend((short) 0, (short) 4);
    }

    // -------------------------------------------------------------------------
    // Category 0x4x — Authentication
    // -------------------------------------------------------------------------

    private void processVerifyPin(APDU apdu) {
        if (pinState[0] == (byte) 0) ISOException.throwIt(SW_PIN_NOT_SET);
        if (pin.getTriesRemaining() == 0) ISOException.throwIt(SW_PIN_BLOCKED);

        short pinLen = apdu.setIncomingAndReceive();
        if (pinLen < PIN_MIN_LEN || pinLen > PIN_MAX_LEN) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }

        byte[] buf = apdu.getBuffer();
        boolean ok = pin.check(buf, ISO7816.OFFSET_CDATA, (byte) pinLen);
        if (!ok) failPinCheck();
        // Nothing about spending is touched here: the PIN can be presented as
        // often as its holder, or a terminal holding it, likes.
        pinVerifiedFlag[0] = (byte) 1;
    }

    /**
     * A PIN check failed. End this session's authentication, then report the
     * tries left as 63CX, or, when the last try just went, move the card to
     * the blocked state and report 6983.
     *
     * The session ends first because that is what OwnerPIN.check does to its
     * own validated flag: a failed check resets it before anything else. The
     * applet gates on pinVerifiedFlag rather than on pin.isValidated(), so it
     * has to do the same by hand. Without it a session that verified and then
     * failed stayed verified, which let a card report itself blocked (GET_INFO
     * byte 7 = 2, VERIFY_PIN 6983) while it still signed and spent for that
     * session. The write is to a CLEAR_ON_DESELECT transient, so it allocates
     * nothing and touches no EEPROM (D10).
     *
     * The blocked transition lives here and nowhere else so that every
     * command that checks the PIN (VERIFY_PIN, CHANGE_PIN) blocks the card
     * the same way. Before this helper CHANGE_PIN exhausted the OwnerPIN
     * without ever setting pinState, leaving GET_INFO reporting "set" on a
     * card that could no longer verify (ENG-615). With the session ending
     * here, CHANGE_PIN can no longer be the exhausting try at all: it needs a
     * verified session, and the successful VERIFY_PIN that opens one resets
     * the counter. Sharing the helper keeps the two paths from drifting apart
     * again if that ever changes.
     */
    private void failPinCheck() {
        pinVerifiedFlag[0] = (byte) 0;
        byte remaining = pin.getTriesRemaining();
        if (remaining == 0) {
            pinState[0] = (byte) 2;
            ISOException.throwIt(SW_PIN_BLOCKED);
        }
        short sw = (short)(0x63C0 | (remaining & 0x0F));
        ISOException.throwIt(sw);
    }

    /**
     * SET_PIN: the PIN (4 to 8 bytes). Sets or replaces it, and unblocks.
     *
     * For an OPEN card only: one with no owner and nothing unspent. No PIN
     * and no proof is needed, because there is nothing on the card, and
     * nobody whose card it is. On a card with an owner it is refused whether
     * the card is empty or not: a terminal that holds the PIN can empty a card
     * whose balance fits within the day's limit, and must not then be able to
     * set a PIN of its own. The owner uses CHANGE_PIN.
     */
    private void processSetPin(APDU apdu) {
        requireNotLocked();
        if (ownerSet[0] == (byte) 1) ISOException.throwIt(SW_OWNER_PROOF);
        requireNothingUnspent();

        short pinLen = apdu.setIncomingAndReceive();
        if (pinLen < PIN_MIN_LEN || pinLen > PIN_MAX_LEN) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }
        byte[] buf = apdu.getBuffer();
        pin.update(buf, ISO7816.OFFSET_CDATA, (byte) pinLen);
        pinState[0] = (byte) 1;
        // a new PIN has not been typed yet: any session that had verified the old one is over
        pinVerifiedFlag[0] = (byte) 0;
    }

    /**
     * CHANGE_PIN: the owner's proof's length (1), the proof (DER), then the new
     * PIN. No old PIN, no verified session, any PIN state, any funds.
     *
     * The owner's phone does not know the PIN, and the cards that most need it
     * changed are the blocked and the forgotten, where there is no old PIN to
     * give. A terminal that is handed the PIN has the old one and a verified
     * session, so neither could keep it from changing the PIN and locking the
     * holder out; the proof, over the new PIN, can. It sets the PIN, resets the
     * tries, unblocks a blocked card and ends any verified session. A wrong
     * proof costs no tries and changes nothing.
     */
    private void processChangePin(APDU apdu) {
        requireNotLocked();
        if (ownerSet[0] != (byte) 1) ISOException.throwIt(SW_NO_OWNER);
        short dataLen = apdu.setIncomingAndReceive();
        byte[] buf = apdu.getBuffer();
        short at = requireOwnerProof(LABEL_CHANGE_PIN, buf, dataLen);
        short newLen = (short)(ISO7816.OFFSET_CDATA + dataLen - at);
        if (newLen < PIN_MIN_LEN || newLen > PIN_MAX_LEN) ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        pin.update(buf, at, (byte) newLen);
        pinState[0] = (byte) 1;
        pinVerifiedFlag[0] = (byte) 0;
    }

    /**
     * SET_OWNER: the owner's public key (65 bytes, 04 || X || Y). On a card
     * that already has an owner, the old owner's proof's length (1) and the
     * proof come first.
     *
     * Only with nothing unspent. On a card with NO owner no proof is needed:
     * it is open, and has nothing to protect. On a card WITH one the old
     * owner's proof is needed, empty or not, because the only thing that could
     * authorise a new owner is the old one, and a card whose terminal can empty
     * it must not be taken over by emptying it. The key is kept and never read
     * out: nothing here answers with it. One transaction: the key and the flag
     * that it is there.
     */
    private void processSetOwner(APDU apdu) {
        requireNotLocked();
        boolean owned = ownerSet[0] == (byte) 1;
        short dataLen = apdu.setIncomingAndReceive();
        byte[] buf = apdu.getBuffer();
        short at = ISO7816.OFFSET_CDATA;
        short len = dataLen;
        if (owned) {
            at = requireOwnerProof(LABEL_SET_OWNER, buf, dataLen);
            len = (short)(ISO7816.OFFSET_CDATA + dataLen - at);
        }
        requireNothingUnspent();
        if (len != EC_POINT_LEN) ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        if (buf[at] != (byte) 0x04) ISOException.throwIt(ISO7816.SW_WRONG_DATA);
        JCSystem.beginTransaction();
        try {
            ownerKey.setW(buf, at, EC_POINT_LEN);
        } catch (CryptoException e) {
            JCSystem.abortTransaction();
            ISOException.throwIt(ISO7816.SW_WRONG_DATA);
        }
        ownerSet[0] = (byte) 1;
        JCSystem.commitTransaction();
    }

    /**
     * GET_NONCE: 16 fresh random bytes, for one owner's proof.
     *
     * No PIN: it changes nothing but the nonce, which a proof must name, and
     * asking again replaces it. A card with no owner has no use for one and
     * says so.
     */
    private void processGetNonce(APDU apdu) {
        if (ownerSet[0] != (byte) 1) ISOException.throwIt(SW_NO_OWNER);
        byte[] buf = apdu.getBuffer();
        rng.generateData(ownerNonce, (short) 0, OWNER_NONCE_LEN);
        nonceLive[0] = (byte) 1;
        Util.arrayCopyNonAtomic(ownerNonce, (short) 0, buf, (short) 0, OWNER_NONCE_LEN);
        apdu.setOutgoingAndSend((short) 0, OWNER_NONCE_LEN);
    }

    /**
     * ALLOW_LOAD: the owner's proof's length (1) and the proof, over
     * "FoxyCard/load" and no value.
     *
     * For the rest of this tap, and only this tap (the grant is in transient
     * memory), LOAD_PROOF and CLEAR_SPENT need no verified PIN. The owner's
     * phone adds funds without knowing it. It lets nothing else through: not
     * SPEND_PROOF, and nothing the PIN opens. A till still writes change back
     * under the verified PIN.
     */
    private void processAllowLoad(APDU apdu) {
        requireNotLocked();
        if (ownerSet[0] != (byte) 1) ISOException.throwIt(SW_NO_OWNER);
        short dataLen = apdu.setIncomingAndReceive();
        byte[] buf = apdu.getBuffer();
        short at = requireOwnerProof(LABEL_LOAD, buf, dataLen);
        if ((short)(ISO7816.OFFSET_CDATA + dataLen) != at) ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        loadGrant[0] = (byte) 1;
    }

    /**
     * Refuses unless the data is the owner's proof for this command: its
     * length (1), then an ECDSA signature (P-256, SHA-256, DER) by the owner
     * key over
     *
     *   label || the nonce just given || everything that follows the proof
     *
     * Answers where that value begins in the buffer, so a command can read
     * what the proof covered and nothing else, and check its length.
     *
     * The nonce is spent by being tried: a wrong proof does not leave it for
     * another go, and a proof made for an earlier nonce, or presented with none
     * given in this tap, fails. `6A91` for all of those and for a proof that is
     * missing or is not made of a length and that many bytes. `6A90` on a card
     * with no owner. Nothing here touches the PIN's tries: a wrong proof is not
     * a wrong PIN.
     */
    private short requireOwnerProof(byte[] label, byte[] buf, short dataLen) {
        if (ownerSet[0] != (byte) 1) ISOException.throwIt(SW_NO_OWNER);
        boolean live = nonceLive[0] == (byte) 1;
        nonceLive[0] = (byte) 0;
        if (!live || dataLen < (short) 1) ISOException.throwIt(SW_OWNER_PROOF);
        short proofLen = (short)(buf[ISO7816.OFFSET_CDATA] & 0xFF);
        if (proofLen < 1 || proofLen > SIG_DER_MAX || dataLen < (short)(1 + proofLen)) ISOException.throwIt(SW_OWNER_PROOF);
        short proofAt = (short)(ISO7816.OFFSET_CDATA + 1);
        short valueAt = (short)(proofAt + proofLen);
        short valueLen = (short)(dataLen - 1 - proofLen);
        boolean good = false;
        try {
            ecdsa.init(ownerKey, Signature.MODE_VERIFY);
            ecdsa.update(label, (short) 0, (short) label.length);
            ecdsa.update(ownerNonce, (short) 0, OWNER_NONCE_LEN);
            good = ecdsa.verify(buf, valueAt, valueLen, buf, proofAt, proofLen);
        } catch (CryptoException e) {
            good = false;
        }
        if (!good) ISOException.throwIt(SW_OWNER_PROOF);
        return valueAt;
    }

    // -------------------------------------------------------------------------
    // Category 0x5x — Admin
    // -------------------------------------------------------------------------

    /**
     * LOCK_CARD: the owner's proof's length (1) and the proof, P2 = the
     * confirming byte.
     *
     * For good, and so for the owner alone: the PIN must be set and verified,
     * and the owner's proof is needed as well, under a label of its own. It
     * was once the PIN alone, or nothing on a card that had none, and any
     * reader could lock a card, or a terminal that had been given the PIN.
     * A card with no PIN, or no owner, cannot be locked.
     */
    private void processLockCard(APDU apdu) {
        requirePinSetAndVerified();
        byte[] buf = apdu.getBuffer();
        if (buf[ISO7816.OFFSET_P2] != LOCK_CONFIRM_BYTE) {
            ISOException.throwIt(ISO7816.SW_WRONG_P1P2);
        }
        if (cardLocked[0] == (byte) 1) ISOException.throwIt(SW_CARD_LOCKED);
        short dataLen = apdu.setIncomingAndReceive();
        short at = requireOwnerProof(LABEL_LOCK, buf, dataLen);
        if ((short)(ISO7816.OFFSET_CDATA + dataLen) != at) ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        cardLocked[0] = (byte) 1;
    }

    // -------------------------------------------------------------------------
    // Guard helpers
    // -------------------------------------------------------------------------

    private void requireNotLocked() {
        if (cardLocked[0] == (byte) 1) ISOException.throwIt(ISO7816.SW_COMMAND_NOT_ALLOWED);
    }

    /**
     * D13 gate. Fires whenever a PIN exists and this session has not verified
     * it — and "exists" includes the blocked state (pinState 2), from which
     * VERIFY_PIN can never succeed. The earlier form checked `pinState == 1`,
     * so three wrong guesses turned the card into a no-PIN bearer card:
     * every gated command opened up the moment the PIN was blocked (ENG-615).
     */
    private void requirePinIfSet() {
        if (pinState[0] != (byte) 0 && pinVerifiedFlag[0] != (byte) 1) {
            ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED);
        }
    }

    /**
     * For what writes value onto the card: a PIN must exist and be verified.
     * requirePinIfSet lets a card with no PIN through, which is right for a
     * spend on an empty card and wrong for loading one.
     */
    private void requirePinSetAndVerified() {
        if (pinState[0] != (byte) 1 || pinVerifiedFlag[0] != (byte) 1) {
            ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED);
        }
    }

    /**
     * For what puts a piece on the card: a PIN that is set, and either typed in
     * this tap, or waived by the owner for this tap (ALLOW_LOAD), or this tap
     * being the one after a payment (changeGrant: the change comes back with
     * no PIN). A blocked PIN waives nothing: the owner unblocks the card first.
     */
    private void requireLoadAuthority() {
        if (pinState[0] != (byte) 1
            || (pinVerifiedFlag[0] != (byte) 1 && loadGrant[0] != (byte) 1 && changeGrant[0] != (byte) 1)) {
            ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED);
        }
    }

    /** `6A8D` if any slot holds an unspent piece: what is set only on an empty card is not set under one. */
    private void requireNothingUnspent() {
        for (short i = 0; i < MAX_PROOFS; i++) {
            if (proofStorage[(short)(i * PROOF_SIZE + PROOF_STATUS_OFFSET)] == STATUS_UNSPENT) {
                ISOException.throwIt(SW_CARD_IN_USE);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Utility
    // -------------------------------------------------------------------------

    /**
     * Big-endian 32-bit add: acc[accOff..accOff+3] += src[srcOff..srcOff+3].
     *
     * JavaCard has no long and int is optional, so the addition is performed
     * byte-wise with an explicit carry.
     *
     * Overflow past 2^32-1 wraps silently and is NOT detected. It cannot occur
     * for any realistic denomination set (32 slots of sane uint32 amounts stay
     * far below 2^32-1), but the wrap is real behaviour, not an impossibility —
     * see testGetBalanceWrapsPast2Pow32, which pins it.
     */
    private static void addUint32(byte[] acc, short accOff,
                                  byte[] src, short srcOff) {
        short carry = 0;
        for (short i = 3; i >= 0; i--) {
            short sum = (short) ((short)(acc[(short)(accOff + i)] & 0xFF)
                               + (short)(src[(short)(srcOff + i)] & 0xFF)
                               + carry);
            acc[(short)(accOff + i)] = (byte) (sum & 0xFF);
            carry = (short) ((sum >> 8) & 0xFF);
        }
    }
    /** Compare two big-endian uint32s: negative, zero or positive as a is below, equal to or above b. */
    private static short cmpUint32(byte[] a, short aOff, byte[] b, short bOff) {
        for (short i = 0; i < 4; i++) {
            short x = (short)(a[(short)(aOff + i)] & 0xFF);
            short y = (short)(b[(short)(bOff + i)] & 0xFF);
            if (x != y) return (short)(x - y);
        }
        return (short) 0;
    }

    /**
     * Big-endian 32-bit add that says whether it wrapped: acc[accOff..+3] +=
     * src[srcOff..+3]; answers the carry out of the top byte, 0 or 1. A sum
     * that carries is past what four bytes hold, and so past any limit.
     */
    private static short addUint32Carry(byte[] acc, short accOff, byte[] src, short srcOff) {
        short carry = 0;
        for (short i = 3; i >= 0; i--) {
            short sum = (short) ((short)(acc[(short)(accOff + i)] & 0xFF)
                               + (short)(src[(short)(srcOff + i)] & 0xFF)
                               + carry);
            acc[(short)(accOff + i)] = (byte) (sum & 0xFF);
            carry = (short) ((sum >> 8) & 0xFF);
        }
        return carry;
    }

    // ---- the log ---------------------------------------------------------------

    /**
     * Where this tap's entry is in the log, begun if nothing in this time in
     * the field has been written down yet: the count of taps goes up by one,
     * and the place the ring gives it is cleared and given the clock. Called
     * inside a transaction; the caller marks the tap open once it has
     * committed (`tapOpen`, which is RAM and would not be undone with it).
     */
    private short logEntry() {
        if (tapOpen[0] != (byte) 1) addUint32Stop(cardLog, LOG_TAPS_OFFSET, ONE, (short) 0);
        // the tap numbered n is at (n - 1) mod 8: eight divides 256, so the last byte says it
        short at = (short)(LOG_HEAD_LEN + (short)(((short)((cardLog[(short)(LOG_TAPS_OFFSET + 3)] & 0xFF) - 1) & 0x07) * LOG_ENTRY_LEN));
        if (tapOpen[0] != (byte) 1) {
            Util.arrayFillNonAtomic(cardLog, at, LOG_ENTRY_LEN, (byte) 0);
            Util.arrayCopy(cardRecord, CARD_NOW_OFFSET, cardLog, (short)(at + LOG_E_TIME), (short) 4);
            // a clock moved twice in this time in the field, before the tap had an entry to say so
            if (timeTold[1] == (byte) 1) cardLog[(short)(at + LOG_E_FLAGS)] = LOG_FLAG_CLOCK;
        }
        return at;
    }

    /**
     * A spend that is over a limit: written down, and then refused with `sw`.
     *
     * A terminal that keeps to the limits never causes one: what the card has
     * left of its day and of its tap is in GET_INFO, to be read before
     * anything is asked for. So a refusal is a terminal that asked for more
     * than it was allowed, and a run of three inside one tap's ten seconds of
     * the card's clock is one trying the limit again and again: the tap is
     * marked, and the count of such runs goes up. (Ten seconds of the clock,
     * and not three in a row at any distance: a terminal that does not know
     * of a limit and is refused once at each of three visits is not that.)
     */
    private void refuseOverLimit(short sw) {
        JCSystem.beginTransaction();
        short entry = logEntry();
        addUint32Stop(cardLog, LOG_REFUSED_OFFSET, ONE, (short) 0);
        if (cardLog[(short)(entry + LOG_E_REFUSED)] != (byte) 0xFF) cardLog[(short)(entry + LOG_E_REFUSED)]++;
        /* A new run: the first refusal there has been, or ten seconds on from
         * the last run's first, or a clock that is now behind that (a new time
         * key sets the clock back to nothing, and a run begun by the old one
         * would otherwise never end). */
        if (cardLog[LOG_RUN_OFFSET] == (byte) 0
            || cmpUint32(cardRecord, CARD_NOW_OFFSET, cardLog, LOG_RUN_AT_OFFSET) < 0
            || windowIsOver(cardLog, LOG_RUN_AT_OFFSET, TAP_SECONDS)) {
            Util.arrayCopy(cardRecord, CARD_NOW_OFFSET, cardLog, LOG_RUN_AT_OFFSET, (short) 4);
            cardLog[LOG_RUN_OFFSET] = (byte) 1;
        } else if (cardLog[LOG_RUN_OFFSET] != (byte) 0xFF) {
            cardLog[LOG_RUN_OFFSET]++;
        }
        short run = (short)(cardLog[LOG_RUN_OFFSET] & 0xFF);
        if (run == TAMPER_RUN) addUint32Stop(cardLog, LOG_TAMPERS_OFFSET, ONE, (short) 0);
        if (run >= TAMPER_RUN) cardLog[(short)(entry + LOG_E_FLAGS)] |= LOG_FLAG_TAMPER;
        JCSystem.commitTransaction();
        tapOpen[0] = (byte) 1;
        ISOException.throwIt(sw);
    }

    /**
     * GET_LOG: the card's own account of its taps. Sixteen bytes of counts
     * (taps, sats signed for, spends refused for being over a limit, runs of
     * three such refusals: four bytes each, big-endian), then the taps the
     * ring holds, newest first, twelve bytes each: the clock when it began
     * (4), sats signed for in it (4), pieces signed (1), spends refused (1),
     * flags (1; bit 0, a run of three refusals reached or gone past in it),
     * and a byte of nothing.
     *
     * For whoever the card is open to: the PIN verified in this tap, or the
     * owner's grant (ALLOW_LOAD). It says when the card was used and for how
     * much, which is more than a stranger's reader should be told. A card
     * with no PIN yet has nothing in it, and answers anyone.
     */
    private void processGetLog(APDU apdu) {
        byte[] buf = apdu.getBuffer();
        if (buf[ISO7816.OFFSET_P1] == (byte) 1) { processGetReceipts(apdu); return; }
        if (buf[ISO7816.OFFSET_P1] != (byte) 0) ISOException.throwIt(ISO7816.SW_INCORRECT_P1P2);
        if (pinState[0] != (byte) 0 && pinVerifiedFlag[0] != (byte) 1 && loadGrant[0] != (byte) 1) {
            ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED);
        }
        Util.arrayCopyNonAtomic(cardLog, (short) 0, buf, (short) 0, LOG_ANSWER_HEAD);
        short held = LOG_ENTRIES;
        if (isZero(cardLog, LOG_TAPS_OFFSET, (short) 3) && (short)(cardLog[(short)(LOG_TAPS_OFFSET + 3)] & 0xFF) < LOG_ENTRIES) {
            held = (short)(cardLog[(short)(LOG_TAPS_OFFSET + 3)] & 0xFF);
        }
        short newest = (short)(((short)((cardLog[(short)(LOG_TAPS_OFFSET + 3)] & 0xFF) - 1)) & 0x07);
        short out = LOG_ANSWER_HEAD;
        for (short k = 0; k < held; k++) {
            short at = (short)(LOG_HEAD_LEN + (short)(((short)(newest - k) & 0x07) * LOG_ENTRY_LEN));
            Util.arrayCopyNonAtomic(cardLog, at, buf, out, LOG_ENTRY_LEN);
            out += LOG_ENTRY_LEN;
        }
        apdu.setOutgoingAndSend((short) 0, out);
    }

    /**
     * GET_LOG with P1 = 1: the receipts. The count of every payment the card
     * has signed (4), then up to three receipts of RECEIPT_LEN, newest first,
     * beginning P2 back from the newest (P2 = 0 is the newest itself; one at
     * or past what the ring holds answers the count alone).
     *
     * By the owner's grant, given in this tap, and nothing else: not the PIN,
     * which a till has. (A card with no PIN yet has signed nothing.)
     */
    private void processGetReceipts(APDU apdu) {
        if (pinState[0] != (byte) 0 && loadGrant[0] != (byte) 1) {
            ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED);
        }
        byte[] buf = apdu.getBuffer();
        short back = (short)(buf[ISO7816.OFFSET_P2] & 0xFF);
        short held = RECEIPTS;
        if (isZero(cardReceipts, (short) 0, (short) 3) && (short)(cardReceipts[3] & 0xFF) < RECEIPTS) {
            held = (short)(cardReceipts[3] & 0xFF);
        }
        Util.arrayCopyNonAtomic(cardReceipts, (short) 0, buf, (short) 0, RECEIPTS_HEAD);
        short out = RECEIPTS_HEAD;
        // the payment numbered n is at (n - 1) mod 16: sixteen divides 256, so the last byte of the count says it
        short newest = (short)(((short)(cardReceipts[3] & 0xFF) - 1) & 0x0F);
        for (short k = back; k < held && k < (short)(back + 3); k++) {
            short at = (short)(RECEIPTS_HEAD + (short)(((short)(newest - k) & 0x0F) * RECEIPT_LEN));
            Util.arrayCopyNonAtomic(cardReceipts, at, buf, out, RECEIPT_LEN);
            out += RECEIPT_LEN;
        }
        apdu.setOutgoingAndSend((short) 0, out);
    }

    /**
     * Whether the window has run its day: `now` is at least 86 400 seconds past
     * the window's start. A window whose end is past what four bytes hold never
     * ends. Works in scratch[X_NUM], which nothing live is using here.
     */
    private boolean dayIsOver() {
        return windowIsOver(cardRecord, CARD_WINDOW_OFFSET, DAY_SECONDS);
    }

    /** Whether the window that began at `from[windowAt]` and lasts `seconds` (4, big-endian) has ended by the card's clock. */
    private boolean windowIsOver(byte[] from, short windowAt, byte[] seconds) {
        Util.arrayCopyNonAtomic(from, windowAt, scratch, X_NUM, (short) 4);
        if (addUint32Carry(scratch, X_NUM, seconds, (short) 0) != 0) return false;
        return cmpUint32(cardRecord, CARD_NOW_OFFSET, scratch, X_NUM) >= 0;
    }

    /** `acc` += `src`, four bytes each, big-endian; a sum past the top of four bytes stops there. */
    private static void addUint32Stop(byte[] acc, short accOff, byte[] src, short srcOff) {
        if (addUint32Carry(acc, accOff, src, srcOff) != 0) {
            acc[accOff] = (byte) 0xFF; acc[(short)(accOff + 1)] = (byte) 0xFF;
            acc[(short)(accOff + 2)] = (byte) 0xFF; acc[(short)(accOff + 3)] = (byte) 0xFF;
        }
    }

    /** Whether two ranges are the same, looking at every byte whatever it finds. */
    private static boolean sameBytes(byte[] a, short aOff, byte[] b, short bOff, short len) {
        byte diff = 0;
        for (short i = 0; i < len; i++) diff |= (byte)(a[(short)(aOff + i)] ^ b[(short)(bOff + i)]);
        return diff == (byte) 0;
    }

    private static boolean isZero(byte[] a, short off, short len) {
        byte any = 0;
        for (short i = 0; i < len; i++) any |= a[(short)(off + i)];
        return any == (byte) 0;
    }

    /** `len` bytes, 33 at most, as lowercase hex text into scratch[X_HEX]. */
    private void toHex(byte[] src, short off, short len) {
        hexInto(src, off, len, X_HEX);
    }

    /** `len` bytes as lowercase hex text into scratch from `at`. The arrays and the two ends are in locals: this loop is most of what the card does in bytecode. */
    private void hexInto(byte[] src, short off, short len, short at) {
        byte[] to = scratch;
        byte[] hex = HEX;
        short end = (short)(off + len);
        while (off < end) {
            short v = (short)(src[off++] & 0xFF);
            to[at++] = hex[(short)(v >> 4)];
            to[at++] = hex[(short)(v & 0x0F)];
        }
    }

    /**
     * A big-endian uint32 as decimal text in scratch, its last digit just
     * before `end`; answers how many digits. An amount under 32,768, which
     * nearly every output of a swap is, is divided as a short; a larger one
     * the long way (`toDecimal`).
     */
    private short decimalBefore(byte[] src, short off, short end) {
        if (src[off] == (byte) 0 && src[(short)(off + 1)] == (byte) 0 && src[(short)(off + 2)] >= (byte) 0) {
            short v = Util.getShort(src, (short)(off + 2));
            short at = end;
            do {
                scratch[--at] = (byte)('0' + (short)(v % 10));
                v = (short)(v / 10);
            } while (v != (short) 0);
            return (short)(end - at);
        }
        short digits = toDecimal(src, off);
        Util.arrayCopyNonAtomic(scratch, (short)(X_DEC + 10 - digits), scratch, (short)(end - digits), digits);
        return digits;
    }

    /**
     * A big-endian uint32 as decimal text, right-aligned in the ten bytes at
     * scratch[X_DEC], with no leading zeros. Answers how many digits.
     *
     * JavaCard has no long and int is optional, so it is long division by
     * ten, a byte at a time: the running value never passes 9*256+255.
     */
    private short toDecimal(byte[] src, short off) {
        Util.arrayCopyNonAtomic(src, off, scratch, X_NUM, (short) 4);
        short digits = 0;
        do {
            short rem = 0;
            for (short i = 0; i < 4; i++) {
                short cur = (short)((short)(rem << 8) | (short)(scratch[(short)(X_NUM + i)] & 0xFF));
                scratch[(short)(X_NUM + i)] = (byte)(cur / 10);
                rem = (short)(cur % 10);
            }
            digits++;
            scratch[(short)(X_DEC + 10 - digits)] = (byte)('0' + rem);
        } while (!isZero(scratch, X_NUM, (short) 4));
        return digits;
    }
}
