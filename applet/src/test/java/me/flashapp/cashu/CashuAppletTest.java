package me.flashapp.cashu;

import com.licel.jcardsim.smartcardio.CardSimulator;
import com.licel.jcardsim.utils.AIDUtil;
import javacard.framework.AID;
import org.junit.jupiter.api.*;

import javax.smartcardio.CommandAPDU;
import javax.smartcardio.ResponseAPDU;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * jCardSim tests for the Foxy fork of the applet (docs/FOXY-CARD-SPEC.md).
 *
 * Upstream's tests were of another wire format: a 78-byte slot, a SPEND_PROOF
 * that signed the reader's bytes, SIGN_ARBITRARY, and cards loaded with no PIN.
 * These are of this one. The helpers at the end (the BIP-340 verifier and its
 * arithmetic) are upstream's, and the Schnorr tests beside this file still use
 * them.
 */
class CashuAppletTest {

    static final String AID_HEX = "F0464F58594341524401";  // the applet's, for AIDUtil
    static final String AID_STR = "F0464F585943415244";      // the package's: SELECT matches by prefix
    static final String UPSTREAM_AID = "D2760000850102";
    static final byte   CLA     = (byte) 0xB0;

    static final byte INS_GET_INFO         = (byte) 0x01;
    static final byte INS_GET_PUBKEY       = (byte) 0x10;
    static final byte INS_GET_BALANCE      = (byte) 0x11;
    static final byte INS_GET_PROOF_COUNT  = (byte) 0x12;
    static final byte INS_GET_PROOF        = (byte) 0x13;
    static final byte INS_GET_SLOT_STATUS  = (byte) 0x14;
    static final byte INS_AUTH             = (byte) 0x15;
    static final byte INS_GET_CARD         = (byte) 0x16;
    static final byte INS_SPEND_PROOF      = (byte) 0x20;
    static final byte INS_SIGN_ARBITRARY   = (byte) 0x21;   // upstream's; gone
    static final byte INS_LOAD_PROOF       = (byte) 0x30;
    static final byte INS_CLEAR_SPENT      = (byte) 0x31;
    static final byte INS_SET_CARD         = (byte) 0x32;
    static final byte INS_SET_LIMIT        = (byte) 0x33;
    static final byte INS_VERIFY_PIN       = (byte) 0x40;
    static final byte INS_SET_PIN          = (byte) 0x41;
    static final byte INS_CHANGE_PIN       = (byte) 0x42;
    static final byte INS_LOCK_CARD        = (byte) 0x50;

    static final int SW_OK                  = 0x9000;
    static final int SW_WRONG_LENGTH        = 0x6700;
    static final int SW_SECURITY_NOT_SATIS  = 0x6982;
    static final int SW_PIN_BLOCKED         = 0x6983;
    static final int SW_PIN_NOT_SET         = 0x6984;
    static final int SW_CONDITIONS_NOT_SATIS= 0x6985;
    static final int SW_NOT_ALLOWED         = 0x6986;
    static final int SW_WRONG_DATA          = 0x6A80;
    static final int SW_SLOT_OUT_OF_RANGE   = 0x6A83;
    static final int SW_NO_SPACE            = 0x6A84;
    static final int SW_SLOT_EMPTY          = 0x6A88;
    static final int SW_NO_CARD_RECORD      = 0x6A8C;
    static final int SW_CARD_IN_USE         = 0x6A8D;
    static final int SW_NO_REFUND_KEY       = 0x6A8E;
    static final int SW_OVER_LIMIT          = 0x6A8F;
    static final int SW_INS_NOT_SUPPORTED   = 0x6D00;
    static final int SW_CLA_NOT_SUPPORTED   = 0x6E00;

    static final int MAX_PROOFS = 64;
    static final int SLOT = 82;

    static final byte[] TEST_PIN  = { 0x31, 0x32, 0x33, 0x34 };
    static final byte[] WRONG_PIN = { 0x39, 0x39, 0x39, 0x39 };
    static final byte[] NEW_PIN   = { 0x35, 0x36, 0x37, 0x38 };

    static final String KEYSET = "0059534ce0bfa19a";
    // two pieces the write-order tests load (SlotWriteOrderTest)
    static final byte[] PROOF_1 = buildProof("0059534ce0bfa19a", 1000, 1);
    static final byte[] PROOF_2 = buildProof("008288762774ace1", 500, 2);
    static final String MINT = "https://mint.example.com/Bitcoin";
    /** A refund key: the curve's generator, compressed. Any point will do. */
    static final byte[] REFUND = hexToBytes("0279be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798");
    static final byte[] NO_REFUND = new byte[33];

    private CardSimulator simulator;

    @BeforeEach
    void setup() {
        simulator = freshCard();
    }

    // ---- what the tests do to a card -------------------------------------

    private int sw(CommandAPDU c) { return transmit(c).getSW(); }
    private int setPin(byte[] pin) { return sw(new CommandAPDU(CLA, INS_SET_PIN, 0, 0, pin)); }
    private int verify(byte[] pin) { return sw(new CommandAPDU(CLA, INS_VERIFY_PIN, 0, 0, pin)); }
    private int setCard(String mint, byte[] refund) {
        byte[] m = mint.getBytes(StandardCharsets.US_ASCII);
        byte[] d = new byte[35 + m.length];
        d[0] = 0;
        System.arraycopy(refund, 0, d, 1, 33);
        d[34] = (byte) m.length;
        System.arraycopy(m, 0, d, 35, m.length);
        return sw(new CommandAPDU(CLA, INS_SET_CARD, 0, 0, d));
    }
    private int setLimit(long sats) {
        byte[] d = new byte[4];
        putUint32(d, 0, sats);
        return sw(new CommandAPDU(CLA, INS_SET_LIMIT, 0, 0, d));
    }
    private ResponseAPDU load(byte[] proof) { return transmit(new CommandAPDU(CLA, INS_LOAD_PROOF, 0, 0, proof, 1)); }
    private ResponseAPDU spend(int slot) { return transmit(new CommandAPDU(CLA, INS_SPEND_PROOF, slot, 0, 64)); }
    private byte[] info() { return transmit(new CommandAPDU(CLA, INS_GET_INFO, 0, 0, 256)).getData(); }
    private byte[] cardKey() { return transmit(new CommandAPDU(CLA, INS_GET_PUBKEY, 0, 0, 256)).getData(); }
    private byte[] slot(int i) { return transmit(new CommandAPDU(CLA, INS_GET_PROOF, i, 0, 256)).getData(); }
    private long balance() { return readUint32(transmit(new CommandAPDU(CLA, INS_GET_BALANCE, 0, 0, 4)).getData(), 0); }
    private void reselect() {
        assertEquals(SW_OK, sw(new CommandAPDU(0x00, 0xA4, 0x04, 0x00, hexToBytes(AID_STR))));
    }

    /** A card as its holder leaves it: PIN set and verified, at a mint, with a refund key. */
    private void ready() {
        assertEquals(SW_OK, setPin(TEST_PIN));
        assertEquals(SW_OK, verify(TEST_PIN));
        assertEquals(SW_OK, setCard(MINT, REFUND));
    }

    private boolean signedFor(byte[] sig, byte[] slotData, byte[] refund) throws Exception {
        byte[] nonce = Arrays.copyOfRange(slotData, 13, 45);
        long date = readUint32(slotData, 78);
        byte[] msg = sha256(secretText(nonce, cardKey(), date, refund).getBytes(StandardCharsets.UTF_8));
        return schnorrVerify(extractPubkeyX(cardKey()), msg, sig);
    }

    // =========================================================================
    // What the card is
    // =========================================================================

    @Test
    @DisplayName("SELECT answers version 1.0, and not to upstream's AID")
    void testSelect() {
        ResponseAPDU resp = transmit(new CommandAPDU(0x00, 0xA4, 0x04, 0x00, hexToBytes(AID_STR)));
        assertEquals(SW_OK, resp.getSW());
        assertArrayEquals(new byte[] { 0x01, 0x00 }, resp.getData());
        assertNotEquals(SW_OK, sw(new CommandAPDU(0x00, 0xA4, 0x04, 0x00, hexToBytes(UPSTREAM_AID))),
            "an upstream reader must not find this applet under upstream's AID: the wire is not the same");
    }

    @Test
    @DisplayName("GET_INFO on a new card: 64 empty slots, no PIN, three tries, format 2, no record, no limit")
    void testInfoFresh() {
        byte[] d = info();
        assertEquals(16, d.length);
        assertEquals(1, d[0]); assertEquals(0, d[1]);
        assertEquals(MAX_PROOFS, d[2] & 0xFF);
        assertEquals(0, d[3]); assertEquals(0, d[4]);
        assertEquals(MAX_PROOFS, d[5] & 0xFF);
        assertEquals(0x07, d[6]);
        assertEquals(0, d[7], "no PIN");
        assertEquals(2, d[8], "format");
        assertEquals(3, d[9], "tries");
        assertEquals(0, d[10], "not locked");
        assertEquals(0, d[11], "no card record");
        assertEquals(0, readUint32(d, 12), "no limit");
    }

    @Test
    @DisplayName("GET_PUBKEY is a 33-byte compressed key")
    void testPubkey() {
        byte[] k = cardKey();
        assertEquals(33, k.length);
        assertTrue(k[0] == 0x02 || k[0] == 0x03);
    }

    @Test
    @DisplayName("A class other than B0, and an instruction nobody defined, are refused")
    void testUnknown() {
        assertEquals(SW_CLA_NOT_SUPPORTED, sw(new CommandAPDU(0x80, INS_GET_INFO, 0, 0, 256)));
        assertEquals(SW_INS_NOT_SUPPORTED, sw(new CommandAPDU(CLA, 0x7F, 0, 0, 256)));
    }

    // =========================================================================
    // SIGN_ARBITRARY is gone
    // =========================================================================

    @Test
    @DisplayName("SIGN_ARBITRARY does not exist: with no PIN, with the PIN, and with pieces on the card")
    void testNoSignArbitrary() {
        byte[] msg = new byte[32];
        assertEquals(SW_INS_NOT_SUPPORTED, sw(new CommandAPDU(CLA, INS_SIGN_ARBITRARY, 0, 0, msg, 64)));
        ready();
        assertEquals(SW_OK, load(buildProof(KEYSET, 16, 1)).getSW());
        assertEquals(SW_INS_NOT_SUPPORTED, sw(new CommandAPDU(CLA, INS_SIGN_ARBITRARY, 0, 0, msg, 64)));
        assertEquals(16, balance(), "and nothing was burned by asking");
    }

    // =========================================================================
    // Loading
    // =========================================================================

    @Test
    @DisplayName("Nothing is loaded onto a card with no PIN")
    void testNoLoadWithoutPin() {
        assertEquals(SW_SECURITY_NOT_SATIS, load(buildProof(KEYSET, 16, 1)).getSW());
        assertEquals(SW_SECURITY_NOT_SATIS, setCard(MINT, REFUND), "nor is its record written");
        assertEquals(0, info()[3], "no slot was taken");
    }

    @Test
    @DisplayName("Nothing is loaded with the PIN set and not verified, or before the card knows its mint")
    void testLoadNeedsVerifyAndRecord() {
        assertEquals(SW_OK, setPin(TEST_PIN));
        assertEquals(SW_SECURITY_NOT_SATIS, load(buildProof(KEYSET, 16, 1)).getSW());
        assertEquals(SW_OK, verify(TEST_PIN));
        assertEquals(SW_NO_CARD_RECORD, load(buildProof(KEYSET, 16, 1)).getSW());
        assertEquals(SW_OK, setCard(MINT, REFUND));
        assertEquals(SW_OK, load(buildProof(KEYSET, 16, 1)).getSW());
    }

    @Test
    @DisplayName("LOAD_PROOF takes 81 bytes, a C that is a point and an amount that is not nothing")
    void testLoadShape() {
        ready();
        assertEquals(SW_WRONG_LENGTH, load(new byte[77]).getSW(), "upstream's 77 bytes are not a piece here");
        assertEquals(SW_WRONG_LENGTH, load(new byte[82]).getSW());
        byte[] notPoint = buildProof(KEYSET, 16, 1);
        notPoint[44] = 0x04;
        assertEquals(SW_WRONG_DATA, load(notPoint).getSW());
        assertEquals(SW_WRONG_DATA, load(buildProof(KEYSET, 0, 1)).getSW());
        assertEquals(0, info()[3], "none of them took a slot");
        ResponseAPDU ok = load(buildProof(KEYSET, 16, 1, 1900000000L));
        assertEquals(SW_OK, ok.getSW());
        assertEquals(0, ok.getData()[0], "the first slot");
        byte[] s = slot(0);
        assertEquals(SLOT, s.length);
        assertEquals(1, s[0]);
        assertEquals(16, readUint32(s, 9));
        assertEquals(1900000000L, readUint32(s, 78), "the date is kept with the piece");
    }

    @Test
    @DisplayName("A dated piece needs a refund key on the card; an undated one does not")
    void testDatedNeedsRefundKey() {
        assertEquals(SW_OK, setPin(TEST_PIN));
        assertEquals(SW_OK, verify(TEST_PIN));
        assertEquals(SW_OK, setCard(MINT, NO_REFUND));
        assertEquals(SW_NO_REFUND_KEY, load(buildProof(KEYSET, 16, 1, 1900000000L)).getSW());
        assertEquals(SW_OK, load(buildProof(KEYSET, 16, 1, 0)).getSW());
    }

    @Test
    @DisplayName("Sixty-four pieces fit, and the sixty-fifth is refused")
    void testFull() {
        ready();
        for (int i = 0; i < MAX_PROOFS; i++) assertEquals(SW_OK, load(buildProof(KEYSET, 1, i)).getSW(), "slot " + i);
        assertEquals(SW_NO_SPACE, load(buildProof(KEYSET, 1, 99)).getSW());
        assertEquals(MAX_PROOFS, balance());
        assertEquals(MAX_PROOFS, transmit(new CommandAPDU(CLA, INS_GET_SLOT_STATUS, 0, 0, 256)).getData().length);
        assertEquals(SW_SLOT_OUT_OF_RANGE, sw(new CommandAPDU(CLA, INS_GET_PROOF, MAX_PROOFS, 0, 256)));
    }

    // =========================================================================
    // The card record
    // =========================================================================

    @Test
    @DisplayName("SET_CARD is read back by GET_CARD, whole")
    void testCardRecord() {
        ready();
        assertEquals(SW_OK, setLimit(5000));
        byte[] c = transmit(new CommandAPDU(CLA, INS_GET_CARD, 0, 0, 256)).getData();
        assertEquals(41 + MINT.length(), c.length);
        assertEquals(2, c[0]); assertEquals(1, c[1]); assertEquals(0, c[2]);
        assertEquals(5000, readUint32(c, 3));
        assertArrayEquals(REFUND, Arrays.copyOfRange(c, 7, 40));
        assertEquals(MINT.length(), c[40] & 0xFF);
        assertEquals(MINT, new String(Arrays.copyOfRange(c, 41, c.length), StandardCharsets.US_ASCII));
        assertEquals(1, info()[11]);
        assertEquals(5000, readUint32(info(), 12));
    }

    @Test
    @DisplayName("SET_CARD refuses a record that is not one, and leaves the old one")
    void testCardRecordShape() {
        ready();
        byte[] bad = REFUND.clone(); bad[0] = 0x04;
        assertEquals(SW_WRONG_DATA, setCard(MINT, bad), "a refund key that is not a compressed point");
        byte[] half = new byte[33]; half[5] = 1;
        assertEquals(SW_WRONG_DATA, setCard(MINT, half), "zeros with something in them");
        assertEquals(SW_WRONG_LENGTH, setCard("", REFUND), "no mint");
        assertEquals(SW_WRONG_LENGTH, setCard("x".repeat(97), REFUND), "a mint too long to keep");
        byte[] lying = new byte[35 + 10]; lying[1] = 0x02; lying[34] = 20;
        assertEquals(SW_WRONG_LENGTH, sw(new CommandAPDU(CLA, INS_SET_CARD, 0, 0, lying)), "a length that is not the mint's");
        byte[] c = transmit(new CommandAPDU(CLA, INS_GET_CARD, 0, 0, 256)).getData();
        assertEquals(MINT, new String(Arrays.copyOfRange(c, 41, c.length), StandardCharsets.US_ASCII));
        assertEquals(SW_OK, setCard("x".repeat(96), REFUND), "ninety-six is kept");
    }

    @Test
    @DisplayName("The mint and the refund key cannot change under pieces that are still unspent")
    void testCardRecordInUse() throws Exception {
        ready();
        assertEquals(SW_OK, load(buildProof(KEYSET, 16, 1, 1900000000L)).getSW());
        assertEquals(SW_CARD_IN_USE, setCard("https://other.example.com", NO_REFUND));
        // the piece is still signed for with the key it was made with
        ResponseAPDU r = spend(0);
        assertEquals(SW_OK, r.getSW());
        assertTrue(signedFor(r.getData(), slot(0), REFUND));
        assertEquals(SW_OK, setCard("https://other.example.com", NO_REFUND), "with nothing unspent it may");
    }

    // =========================================================================
    // Spending
    // =========================================================================

    @Test
    @DisplayName("SPEND_PROOF signs the slot's own secret, upstream's form when there is no date")
    void testSpendSignsTheSlot() throws Exception {
        ready();
        assertEquals(SW_OK, load(buildProof(KEYSET, 16, 7)).getSW());
        byte[] before = slot(0);
        ResponseAPDU r = spend(0);
        assertEquals(SW_OK, r.getSW());
        assertEquals(64, r.getData().length);
        assertTrue(signedFor(r.getData(), before, REFUND), "a BIP-340 signature over SHA-256 of the piece's secret");
        // upstream's reconstruction, spelt out: no locktime, no refund
        String upstream = "[\"P2PK\",{\"nonce\":\"" + toHex(Arrays.copyOfRange(before, 13, 45)) + "\",\"data\":\""
            + toHex(cardKey()) + "\",\"tags\":[[\"sigflag\",\"SIG_INPUTS\"]]}]";
        assertTrue(schnorrVerify(extractPubkeyX(cardKey()), sha256(upstream.getBytes(StandardCharsets.UTF_8)), r.getData()));
        assertEquals(2, slot(0)[0], "and the slot is spent");
        assertEquals(0, balance());
    }

    @Test
    @DisplayName("A dated piece's secret names its date in decimal and the card's refund key")
    void testSpendDated() throws Exception {
        ready();
        long[] dates = { 1L, 9L, 10L, 99L, 100L, 65535L, 65536L, 1700000000L, 1900000000L, 4294967295L };
        for (int i = 0; i < dates.length; i++) {
            assertEquals(SW_OK, load(buildProof(KEYSET, 16, 20 + i, dates[i])).getSW());
        }
        for (int i = 0; i < dates.length; i++) {
            byte[] before = slot(i);
            ResponseAPDU r = spend(i);
            assertEquals(SW_OK, r.getSW(), "date " + dates[i]);
            assertTrue(signedFor(r.getData(), before, REFUND), "date " + dates[i] + " as text in the secret");
            // and not the same piece with no date, or with another refund key
            byte[] undated = before.clone();
            putUint32(undated, 78, 0);
            assertFalse(signedFor(r.getData(), undated, REFUND));
            byte[] other = REFUND.clone(); other[0] = 0x03;
            assertFalse(signedFor(r.getData(), before, other));
        }
    }

    @Test
    @DisplayName("Nothing the reader sends is signed: 32 bytes offered to SPEND_PROOF change nothing")
    void testSpendTakesNoMessage() throws Exception {
        ready();
        assertEquals(SW_OK, load(buildProof(KEYSET, 16, 1)).getSW());
        assertEquals(SW_OK, load(buildProof(KEYSET, 32, 2)).getSW());
        byte[] a = slot(0), b = slot(1);
        // upstream's attack: have slot 0 burned for slot 1's message
        byte[] bMsg = sha256(secretText(Arrays.copyOfRange(b, 13, 45), cardKey(), 0, REFUND).getBytes(StandardCharsets.UTF_8));
        ResponseAPDU r = transmit(new CommandAPDU(CLA, INS_SPEND_PROOF, 0, 0, bMsg, 64));
        assertEquals(SW_OK, r.getSW());
        assertFalse(schnorrVerify(extractPubkeyX(cardKey()), bMsg, r.getData()), "not the message that was offered");
        assertTrue(signedFor(r.getData(), a, REFUND), "the slot's own");
        assertFalse(signedFor(r.getData(), b, REFUND), "and no other slot's");
        assertEquals(1, slot(1)[0], "slot 1 is untouched");
    }

    @Test
    @DisplayName("A slot is signed for once")
    void testSpendOnce() {
        ready();
        assertEquals(SW_OK, load(buildProof(KEYSET, 16, 1)).getSW());
        assertEquals(SW_OK, spend(0).getSW());
        assertEquals(SW_CONDITIONS_NOT_SATIS, spend(0).getSW());
        assertEquals(SW_SLOT_EMPTY, spend(1).getSW());
        assertEquals(SW_SLOT_OUT_OF_RANGE, spend(MAX_PROOFS).getSW());
        reselect();
        assertEquals(SW_OK, verify(TEST_PIN));
        assertEquals(SW_CONDITIONS_NOT_SATIS, spend(0).getSW(), "and not in a later tap either");
    }

    @Test
    @DisplayName("CLEAR_SPENT frees what was spent, and only that")
    void testClearSpent() {
        ready();
        for (int i = 0; i < 3; i++) assertEquals(SW_OK, load(buildProof(KEYSET, 8, i)).getSW());
        assertEquals(SW_OK, spend(1).getSW());
        ResponseAPDU r = transmit(new CommandAPDU(CLA, INS_CLEAR_SPENT, 0, 0, 1));
        assertEquals(SW_OK, r.getSW());
        assertEquals(1, r.getData()[0]);
        assertEquals(SW_SLOT_EMPTY, sw(new CommandAPDU(CLA, INS_GET_PROOF, 1, 0, 256)));
        assertEquals(16, balance());
        assertEquals(1, load(buildProof(KEYSET, 4, 9)).getData()[0], "the freed slot is the next one used");
    }

    // =========================================================================
    // The PIN
    // =========================================================================

    /** Every command that needs the PIN, as (name, command). */
    private Object[][] gated() {
        byte[] card = new byte[35 + 4]; card[1] = 0x02; card[34] = 4; card[35] = 'm';
        return new Object[][] {
            { "SPEND_PROOF", new CommandAPDU(CLA, INS_SPEND_PROOF, 0, 0, 64) },
            { "LOAD_PROOF",  new CommandAPDU(CLA, INS_LOAD_PROOF, 0, 0, buildProof(KEYSET, 4, 50), 1) },
            { "CLEAR_SPENT", new CommandAPDU(CLA, INS_CLEAR_SPENT, 0, 0, 1) },
            { "SET_CARD",    new CommandAPDU(CLA, INS_SET_CARD, 0, 0, card) },
            { "SET_LIMIT",   new CommandAPDU(CLA, INS_SET_LIMIT, 0, 0, new byte[4]) },
            { "CHANGE_PIN",  new CommandAPDU(CLA, INS_CHANGE_PIN, 0, 0, new byte[] { 4, 0x31, 0x32, 0x33, 0x34, 0x35, 0x36, 0x37, 0x38 }) },
            { "LOCK_CARD",   new CommandAPDU(CLA, INS_LOCK_CARD, 0, 0xDE) },
        };
    }

    private void assertAllGatedRefuse(String when) {
        long before = balance();
        byte[] status = transmit(new CommandAPDU(CLA, INS_GET_SLOT_STATUS, 0, 0, 256)).getData();
        for (Object[] g : gated()) {
            assertEquals(SW_SECURITY_NOT_SATIS, sw((CommandAPDU) g[1]), g[0] + " " + when);
        }
        assertEquals(before, balance(), "nothing was spent or added " + when);
        assertArrayEquals(status, transmit(new CommandAPDU(CLA, INS_GET_SLOT_STATUS, 0, 0, 256)).getData());
        assertEquals(0, info()[10], "and the card was not locked " + when);
    }

    @Test
    @DisplayName("Every gated command refuses: PIN set and not verified, after a wrong PIN, and once blocked, for good")
    void testGateInEveryState() {
        ready();
        assertEquals(SW_OK, load(buildProof(KEYSET, 16, 1)).getSW());
        reselect();
        assertAllGatedRefuse("with the PIN set and not verified");

        assertEquals(SW_OK, verify(TEST_PIN));
        assertEquals(0x63C2, verify(WRONG_PIN));
        assertAllGatedRefuse("after a wrong PIN ended the session");

        assertEquals(0x63C1, verify(WRONG_PIN));
        assertEquals(SW_PIN_BLOCKED, verify(WRONG_PIN));
        assertEquals(2, info()[7], "blocked");
        assertEquals(0, info()[9], "no tries left");
        assertAllGatedRefuse("once the PIN is blocked");
        assertEquals(SW_PIN_BLOCKED, verify(TEST_PIN), "the right PIN no longer opens it");
        assertAllGatedRefuse("after the right PIN was tried on a blocked card");
        reselect();
        assertAllGatedRefuse("in a later tap of a blocked card");
        assertEquals(SW_CONDITIONS_NOT_SATIS, setPin(NEW_PIN), "and a blocked card cannot be given a new PIN");
        assertEquals(16, balance(), "the piece is still there, for its refund key");
    }

    @Test
    @DisplayName("The PIN is for one tap")
    void testPinIsForOneTap() {
        ready();
        assertEquals(SW_OK, load(buildProof(KEYSET, 16, 1)).getSW());
        reselect();
        assertEquals(SW_SECURITY_NOT_SATIS, spend(0).getSW());
        assertEquals(1, slot(0)[0], "a refused spend burns nothing");
        assertEquals(SW_OK, verify(TEST_PIN));
        assertEquals(SW_OK, spend(0).getSW());
    }

    @Test
    @DisplayName("A right PIN gives the tries back; VERIFY_PIN before a PIN exists says so")
    void testTries() {
        assertEquals(SW_PIN_NOT_SET, verify(TEST_PIN));
        assertEquals(SW_OK, setPin(TEST_PIN));
        assertEquals(SW_CONDITIONS_NOT_SATIS, setPin(NEW_PIN), "a PIN is set once");
        assertEquals(0x63C2, verify(WRONG_PIN));
        assertEquals(2, info()[9]);
        assertEquals(SW_OK, verify(TEST_PIN));
        assertEquals(3, info()[9]);
        assertEquals(SW_WRONG_LENGTH, verify(new byte[] { 1, 2, 3 }));
        assertEquals(SW_WRONG_LENGTH, verify(new byte[9]));
    }

    @Test
    @DisplayName("CHANGE_PIN: the old one stops working, and a wrong old one costs a try and the session")
    void testChangePin() {
        ready();
        byte[] wrongOld = { 4, 0x39, 0x39, 0x39, 0x39, 0x35, 0x36, 0x37, 0x38 };
        assertEquals(0x63C2, sw(new CommandAPDU(CLA, INS_CHANGE_PIN, 0, 0, wrongOld)));
        assertEquals(SW_SECURITY_NOT_SATIS, setLimit(1), "the session ended with it");
        assertEquals(SW_OK, verify(TEST_PIN));
        byte[] good = { 4, 0x31, 0x32, 0x33, 0x34, 0x35, 0x36, 0x37, 0x38 };
        assertEquals(SW_OK, sw(new CommandAPDU(CLA, INS_CHANGE_PIN, 0, 0, good)));
        reselect();
        assertEquals(0x63C2, verify(TEST_PIN));
        assertEquals(SW_OK, verify(NEW_PIN));
    }

    // =========================================================================
    // The limit
    // =========================================================================

    @Test
    @DisplayName("One PIN entry spends up to the limit and no further; the PIN again starts it again")
    void testLimit() {
        ready();
        assertEquals(SW_OK, setLimit(1000));
        assertEquals(SW_OK, load(buildProof(KEYSET, 600, 1)).getSW());
        assertEquals(SW_OK, load(buildProof(KEYSET, 400, 2)).getSW());
        assertEquals(SW_OK, load(buildProof(KEYSET, 1, 3)).getSW());
        assertEquals(SW_OK, load(buildProof(KEYSET, 2000, 4)).getSW());
        assertEquals(SW_OK, verify(TEST_PIN));
        assertEquals(SW_OVER_LIMIT, spend(3).getSW(), "a piece over the limit by itself");
        assertEquals(SW_OK, spend(0).getSW());
        assertEquals(SW_OK, spend(1).getSW(), "exactly the limit is within it");
        assertEquals(SW_OVER_LIMIT, spend(2).getSW(), "one sat more is not");
        assertEquals(1, slot(2)[0], "and the refused piece is not burned");
        assertEquals(2001, balance());
        assertEquals(SW_OK, verify(TEST_PIN));
        assertEquals(SW_OK, spend(2).getSW(), "typed again, the PIN has its limit again");
        assertEquals(SW_OVER_LIMIT, spend(3).getSW());
    }

    @Test
    @DisplayName("A later tap has the limit to itself, no limit is no limit, and a sum past 2^32 is over any limit")
    void testLimitEdges() {
        ready();
        assertEquals(SW_OK, load(buildProof(KEYSET, 700, 1)).getSW());
        assertEquals(SW_OK, load(buildProof(KEYSET, 700, 2)).getSW());
        assertEquals(SW_OK, load(buildProof(KEYSET, 4294967295L, 3)).getSW());
        assertEquals(SW_OK, load(buildProof(KEYSET, 4294967295L, 4)).getSW());
        assertEquals(SW_OK, setLimit(1000));
        assertEquals(SW_OK, verify(TEST_PIN));
        assertEquals(SW_OK, spend(0).getSW());
        assertEquals(SW_OVER_LIMIT, spend(1).getSW());
        reselect();
        assertEquals(SW_OK, verify(TEST_PIN));
        assertEquals(SW_OK, spend(1).getSW());
        // the largest limit there is, and two pieces whose sum wraps
        assertEquals(SW_OK, setLimit(4294967295L));
        assertEquals(SW_OK, verify(TEST_PIN));
        assertEquals(SW_OK, spend(2).getSW());
        assertEquals(SW_OVER_LIMIT, spend(3).getSW(), "a sum that wraps round is not a small one");
        assertEquals(SW_OK, setLimit(0));
        assertEquals(SW_OK, spend(3).getSW(), "with no limit it goes");
        assertEquals(SW_WRONG_LENGTH, sw(new CommandAPDU(CLA, INS_SET_LIMIT, 0, 0, new byte[3])));
    }

    // =========================================================================
    // AUTH
    // =========================================================================

    private byte[] authMessage(byte[] reader, byte[] card, byte[] key) throws Exception {
        byte[] tag = sha256("FoxyCard/auth".getBytes(StandardCharsets.US_ASCII));
        java.security.MessageDigest d = java.security.MessageDigest.getInstance("SHA-256");
        d.update(tag); d.update(tag); d.update(reader); d.update(card); d.update(key);
        return d.digest();
    }

    @Test
    @DisplayName("AUTH signs the reader's nonce and the card's own under a tag, with no PIN, and never the same twice")
    void testAuth() throws Exception {
        byte[] reader = hexToBytes("000102030405060708090a0b0c0d0e0f");
        ResponseAPDU r = transmit(new CommandAPDU(CLA, INS_AUTH, 0, 0, reader, 80));
        assertEquals(SW_OK, r.getSW());
        assertEquals(80, r.getData().length);
        byte[] cardNonce = Arrays.copyOfRange(r.getData(), 0, 16);
        byte[] sig = Arrays.copyOfRange(r.getData(), 16, 80);
        byte[] key = cardKey();
        assertTrue(schnorrVerify(extractPubkeyX(key), authMessage(reader, cardNonce, key), sig));
        byte[] other = reader.clone(); other[0] ^= 1;
        assertFalse(schnorrVerify(extractPubkeyX(key), authMessage(other, cardNonce, key), sig), "another reader's nonce is not answered by it");
        ResponseAPDU again = transmit(new CommandAPDU(CLA, INS_AUTH, 0, 0, reader, 80));
        assertFalse(Arrays.equals(cardNonce, Arrays.copyOfRange(again.getData(), 0, 16)), "the card's nonce is new each time");
        assertEquals(SW_WRONG_LENGTH, sw(new CommandAPDU(CLA, INS_AUTH, 0, 0, new byte[32], 80)), "32 bytes are not taken: that was SIGN_ARBITRARY");
        assertEquals(SW_WRONG_LENGTH, sw(new CommandAPDU(CLA, INS_AUTH, 0, 0, new byte[15], 80)));
    }

    @Test
    @DisplayName("AUTH's signature is not a spend: it verifies for no piece on the card, and burns none")
    void testAuthIsNotASpend() throws Exception {
        ready();
        assertEquals(SW_OK, load(buildProof(KEYSET, 16, 1)).getSW());
        assertEquals(SW_OK, load(buildProof(KEYSET, 16, 2, 1900000000L)).getSW());
        reselect();
        ResponseAPDU r = transmit(new CommandAPDU(CLA, INS_AUTH, 0, 0, new byte[16], 80));
        assertEquals(SW_OK, r.getSW(), "it needs no PIN");
        byte[] sig = Arrays.copyOfRange(r.getData(), 16, 80);
        assertFalse(signedFor(sig, slot(0), REFUND));
        assertFalse(signedFor(sig, slot(1), REFUND));
        assertEquals(32, balance());
    }

    // =========================================================================
    // A locked card
    // =========================================================================

    @Test
    @DisplayName("A locked card takes no writes and still pays")
    void testLocked() throws Exception {
        ready();
        assertEquals(SW_OK, load(buildProof(KEYSET, 16, 1)).getSW());
        assertEquals(0x6B00, sw(new CommandAPDU(CLA, INS_LOCK_CARD, 0, 0)), "without its confirming byte it is not locked");
        assertEquals(SW_OK, sw(new CommandAPDU(CLA, INS_LOCK_CARD, 0, 0xDE)));
        assertEquals(1, info()[10]);
        assertEquals(SW_NOT_ALLOWED, load(buildProof(KEYSET, 16, 2)).getSW());
        assertEquals(SW_NOT_ALLOWED, setLimit(5));
        assertEquals(SW_NOT_ALLOWED, setCard(MINT, REFUND));
        assertEquals(SW_NOT_ALLOWED, sw(new CommandAPDU(CLA, INS_CLEAR_SPENT, 0, 0, 1)));
        byte[] before = slot(0);
        ResponseAPDU r = spend(0);
        assertEquals(SW_OK, r.getSW());
        assertTrue(signedFor(r.getData(), before, REFUND));
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private ResponseAPDU transmit(CommandAPDU apdu) {
        return simulator.transmitCommand(apdu);
    }

    /**
     * Install and SELECT a brand-new card. Each one runs genKeyPair() afresh, so
     * successive calls draw independent card keys (and independent P.y parities).
     */
    static CardSimulator freshCard() {
        CardSimulator sim = new CardSimulator();
        sim.installApplet(AIDUtil.create(AID_HEX), CashuApplet.class);
        ResponseAPDU resp = sim.transmitCommand(
            new CommandAPDU(0x00, 0xA4, 0x04, 0x00, hexToBytes(AID_STR)));
        assertEquals(SW_OK, resp.getSW(), "SELECT on a fresh card should succeed");
        return sim;
    }

    /** Parity of the public key's y-coordinate, from either EC point encoding. */
    static boolean pubkeyYIsOdd(byte[] pubBytes) {
        if (pubBytes.length == 65 && pubBytes[0] == 0x04) {
            return (pubBytes[64] & 1) == 1;          // uncompressed: LSB of Y
        } else if (pubBytes.length == 33) {
            return (pubBytes[0] & 1) == 1;           // compressed: 0x02 even / 0x03 odd
        }
        throw new IllegalArgumentException("Unexpected pubkey length: " + pubBytes.length);
    }

    /**
     * Build an 81-byte proof payload: keyset_id[8] + amount[4] + nonce[32] + C[33] + date[4].
     *
     * keysetIdHex is hex-decoded to 8 RAW bytes, never ASCII-encoded. The
     * 32-byte field is the P2PK nonce, not the secret string. `date` is the
     * piece's locktime, 0 for none.
     */
    static byte[] buildProof(String keysetIdHex, long amount, int seed, long date) {
        if (keysetIdHex.length() != 16) {
            throw new IllegalArgumentException(
                "keyset id must be 16 hex chars (8 raw bytes), got " + keysetIdHex.length()
                + ": " + keysetIdHex);
        }
        byte[] proof = new byte[81];
        System.arraycopy(hexToBytes(keysetIdHex), 0, proof, 0, 8);
        putUint32(proof, 8, amount);
        for (int i = 0; i < 32; i++) proof[12 + i] = (byte) (seed + i);
        proof[44] = 0x02;
        for (int i = 0; i < 32; i++) proof[45 + i] = (byte)(seed + 1);
        putUint32(proof, 77, date);
        return proof;
    }

    static byte[] buildProof(String keysetIdHex, long amount, int seed) {
        return buildProof(keysetIdHex, amount, seed, 0);
    }

    static void putUint32(byte[] buf, int off, long v) {
        buf[off]     = (byte)((v >> 24) & 0xFF);
        buf[off + 1] = (byte)((v >> 16) & 0xFF);
        buf[off + 2] = (byte)((v >> 8)  & 0xFF);
        buf[off + 3] = (byte)( v        & 0xFF);
    }

    static String toHex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte v : b) sb.append(String.format("%02x", v & 0xFF));
        return sb.toString();
    }

    static byte[] sha256(byte[] in) {
        try { return java.security.MessageDigest.getInstance("SHA-256").digest(in); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    /**
     * A piece's NUT-10 secret, as whoever makes the piece writes it and as the
     * card must rebuild it (CashuApplet's SECRET_* constants). Written out here
     * by hand, on purpose: this is the other side of the wire.
     */
    static String secretText(byte[] nonce, byte[] cardKey, long date, byte[] refundKey) {
        String s = "[\"P2PK\",{\"nonce\":\"" + toHex(nonce) + "\",\"data\":\"" + toHex(cardKey)
                 + "\",\"tags\":[[\"sigflag\",\"SIG_INPUTS\"]";
        if (date != 0) s += ",[\"locktime\",\"" + date + "\"],[\"refund\",\"" + toHex(refundKey) + "\"]";
        return s + "]}]";
    }

    static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] out = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            out[i / 2] = (byte) Integer.parseInt(hex.substring(i, i + 2), 16);
        }
        return out;
    }

    static long readUint32(byte[] buf, int offset) {
        return ((long)(buf[offset]     & 0xFF) << 24)
             | ((long)(buf[offset + 1] & 0xFF) << 16)
             | ((long)(buf[offset + 2] & 0xFF) << 8)
             |  (long)(buf[offset + 3] & 0xFF);
    }

    // =========================================================================
    // Schnorr / EC helpers (BigInteger, jCardSim/JVM only)
    // =========================================================================

    static final java.math.BigInteger SECP_P = new java.math.BigInteger(
        "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFFC2F", 16);
    static final java.math.BigInteger SECP_N = new java.math.BigInteger(
        "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141", 16);
    static final java.math.BigInteger SECP_GX = new java.math.BigInteger(
        "79BE667EF9DCBBAC55A06295CE870B07029BFCDB2DCE28D959F2815B16F81798", 16);
    static final java.math.BigInteger SECP_GY = new java.math.BigInteger(
        "483ADA7726A3C4655DA4FBFC0E1108A8FD17B448A68554199C47D08FFB10D4B8", 16);

    /**
     * BIP-340 Schnorr verify.
     * sig = R.x (32) || s (32)
     */
    static boolean schnorrVerify(byte[] pubX, byte[] msg, byte[] sig)
            throws java.security.NoSuchAlgorithmException {
        java.math.BigInteger p = SECP_P;
        java.math.BigInteger n = SECP_N;

        java.math.BigInteger r = new java.math.BigInteger(1,
            java.util.Arrays.copyOfRange(sig, 0, 32));
        java.math.BigInteger s = new java.math.BigInteger(1,
            java.util.Arrays.copyOfRange(sig, 32, 64));

        if (r.compareTo(p) >= 0) return false;
        if (s.compareTo(n) >= 0) return false;

        // P = lift_x(pubX) — even-y point
        java.math.BigInteger[] P = liftX(new java.math.BigInteger(1, pubX));
        if (P == null) return false;

        // e = tagged_hash("BIP0340/challenge", bytes(r) || bytes(P.x) || msg) mod n
        byte[] rBytes  = toBytes32Test(r);
        byte[] PxBytes = toBytes32Test(P[0]);
        byte[] challengeInput = new byte[96];
        System.arraycopy(rBytes,  0, challengeInput,  0, 32);
        System.arraycopy(PxBytes, 0, challengeInput, 32, 32);
        System.arraycopy(msg,     0, challengeInput, 64, 32);
        java.math.BigInteger e = new java.math.BigInteger(1,
            taggedHashTest("BIP0340/challenge", challengeInput)).mod(n);

        // R = s*G - e*P  (subtract = add negated point: -P = (P.x, p - P.y))
        java.math.BigInteger[] sG  = ecMulTest(s,  SECP_GX, SECP_GY);
        java.math.BigInteger[] eP  = ecMulTest(e,  P[0],    P[1]);
        if (sG == null || eP == null) return false;

        // Negate eP: (eP.x, p - eP.y)
        java.math.BigInteger[] negEP = { eP[0], p.subtract(eP[1]) };
        java.math.BigInteger[] R = ecAddTest(sG[0], sG[1], negEP[0], negEP[1]);
        if (R == null) return false;

        // R.y must be even, R.x must equal r
        if (R[1].testBit(0)) return false;
        return R[0].equals(r);
    }

    /** lift_x: find the even-y point on secp256k1 with the given x-coordinate. */
    static java.math.BigInteger[] liftX(java.math.BigInteger x) {
        java.math.BigInteger p = SECP_P;
        if (x.compareTo(p) >= 0) return null;
        java.math.BigInteger rhs = x.modPow(java.math.BigInteger.valueOf(3), p)
            .add(java.math.BigInteger.valueOf(7)).mod(p);
        java.math.BigInteger y = rhs.modPow(p.add(java.math.BigInteger.ONE)
            .divide(java.math.BigInteger.valueOf(4)), p);
        // Verify it's actually a square root
        if (!y.modPow(java.math.BigInteger.TWO, p).equals(rhs)) return null;
        // Choose even y
        if (y.testBit(0)) y = p.subtract(y);
        return new java.math.BigInteger[]{ x, y };
    }

    /** Extract 32-byte x-coordinate from a compressed (33) or uncompressed (65) public key. */
    static byte[] extractPubkeyX(byte[] pubBytes) {
        if (pubBytes.length == 65 && pubBytes[0] == 0x04) {
            return java.util.Arrays.copyOfRange(pubBytes, 1, 33);
        } else if (pubBytes.length == 33) {
            return java.util.Arrays.copyOfRange(pubBytes, 1, 33);
        }
        throw new IllegalArgumentException("Unexpected pubkey length: " + pubBytes.length);
    }

    /** Returns true if every byte in the array is 0x00. */
    static boolean isAllZeros(byte[] b) {
        for (byte v : b) if (v != 0) return false;
        return true;
    }

    /** Scalar multiplication: k * (x,y) using double-and-add. */
    static java.math.BigInteger[] ecMulTest(java.math.BigInteger k,
                                             java.math.BigInteger x,
                                             java.math.BigInteger y) {
        java.math.BigInteger[] R = null;
        java.math.BigInteger[] P = { x, y };
        k = k.mod(SECP_N);
        while (k.signum() > 0) {
            if (k.testBit(0)) {
                R = (R == null) ? new java.math.BigInteger[]{ P[0], P[1] }
                                : ecAddTest(R[0], R[1], P[0], P[1]);
            }
            P = ecAddTest(P[0], P[1], P[0], P[1]);
            k = k.shiftRight(1);
        }
        return R;
    }

    /** EC point addition / doubling on secp256k1. Returns null for point at infinity. */
    static java.math.BigInteger[] ecAddTest(java.math.BigInteger x1,
                                             java.math.BigInteger y1,
                                             java.math.BigInteger x2,
                                             java.math.BigInteger y2) {
        java.math.BigInteger p  = SECP_P;
        java.math.BigInteger p2 = p.subtract(java.math.BigInteger.TWO);
        java.math.BigInteger lambda;
        if (x1.equals(x2)) {
            if (!y1.equals(y2)) return null; // point at infinity
            // doubling
            java.math.BigInteger num = java.math.BigInteger.valueOf(3)
                .multiply(x1.modPow(java.math.BigInteger.TWO, p)).mod(p);
            java.math.BigInteger den = java.math.BigInteger.valueOf(2).multiply(y1).mod(p);
            lambda = num.multiply(den.modPow(p2, p)).mod(p);
        } else {
            java.math.BigInteger num = y2.subtract(y1).mod(p);
            java.math.BigInteger den = x2.subtract(x1).mod(p);
            lambda = num.multiply(den.modPow(p2, p)).mod(p);
        }
        java.math.BigInteger x3 = lambda.modPow(java.math.BigInteger.TWO, p)
            .subtract(x1).subtract(x2).mod(p);
        java.math.BigInteger y3 = lambda.multiply(x1.subtract(x3)).subtract(y1).mod(p);
        if (x3.signum() < 0) x3 = x3.add(p);
        if (y3.signum() < 0) y3 = y3.add(p);
        return new java.math.BigInteger[]{ x3, y3 };
    }

    /** BIP-340 tagged hash: SHA256(SHA256(tag) || SHA256(tag) || msg) */
    static byte[] taggedHashTest(String tag, byte[] msg)
            throws java.security.NoSuchAlgorithmException {
        java.security.MessageDigest sha =
            java.security.MessageDigest.getInstance("SHA-256");
        byte[] tagHash = sha.digest(
            tag.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        sha.reset();
        sha.update(tagHash);
        sha.update(tagHash);
        sha.update(msg);
        return sha.digest();
    }

    /** BigInteger → big-endian 32-byte array (zero-padded / sign-stripped). */
    static byte[] toBytes32Test(java.math.BigInteger n) {
        byte[] b = n.toByteArray();
        if (b.length == 32) return b;
        byte[] out = new byte[32];
        if (b.length > 32) {
            System.arraycopy(b, b.length - 32, out, 0, 32);
        } else {
            System.arraycopy(b, 0, out, 32 - b.length, b.length);
        }
        return out;
    }

    // Expose ISO7816 constants for tests
    static class ISO7816 {
        static final int SW_COMMAND_NOT_ALLOWED = 0x6986;

}
}
