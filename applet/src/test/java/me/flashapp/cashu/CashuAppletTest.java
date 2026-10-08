package me.flashapp.cashu;

import com.licel.jcardsim.base.SimulatorRuntime;
import com.licel.jcardsim.smartcardio.CardSimulator;
import com.licel.jcardsim.utils.AIDUtil;
import javacard.framework.AID;
import javacard.framework.Applet;
import org.junit.jupiter.api.*;

import javax.smartcardio.CommandAPDU;
import javax.smartcardio.ResponseAPDU;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * jCardSim tests for the Foxy fork of the applet (docs/FOXY-CARD-SPEC.md and
 * docs/FOXY-CARD-DAILY-LIMIT.md).
 *
 * Upstream's tests were of another wire format: a 78-byte slot, a SPEND_PROOF
 * that signed the reader's bytes, SIGN_ARBITRARY, and cards loaded with no PIN.
 * These are of this one. The helpers at the end (the BIP-340 verifier and its
 * arithmetic) are upstream's, and the Schnorr tests beside this file still use
 * them.
 *
 * The owner and the time signer are P-256 keys made here with the JDK's own
 * provider, and the proofs and times they sign are checked by the card's
 * verifier, so the card's ECDSA is held to an implementation that is not its
 * own.
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
    static final byte INS_GET_PIECES       = (byte) 0x17;
    static final byte INS_SPEND_PROOF      = (byte) 0x20;
    static final byte INS_SIGN_ARBITRARY   = (byte) 0x21;   // upstream's; gone
    static final byte INS_LOAD_PROOF       = (byte) 0x30;
    static final byte INS_CLEAR_SPENT      = (byte) 0x31;
    static final byte INS_SET_CARD         = (byte) 0x32;
    static final byte INS_SET_LIMIT        = (byte) 0x33;   // by PIN: an open card only
    static final byte INS_SET_LIMIT_OWNER  = (byte) 0x34;
    static final byte INS_SET_TIME         = (byte) 0x35;
    static final byte INS_VERIFY_PIN       = (byte) 0x40;
    static final byte INS_SET_PIN          = (byte) 0x41;
    static final byte INS_CHANGE_PIN       = (byte) 0x42;
    static final byte INS_SET_OWNER        = (byte) 0x43;
    static final byte INS_GET_NONCE        = (byte) 0x44;
    static final byte INS_ALLOW_LOAD       = (byte) 0x45;
    static final byte INS_LOCK_CARD        = (byte) 0x50;

    static final int SW_OK                  = 0x9000;
    static final int SW_WRONG_LENGTH        = 0x6700;
    static final int SW_SECURITY_NOT_SATIS  = 0x6982;
    static final int SW_PIN_BLOCKED         = 0x6983;
    static final int SW_PIN_NOT_SET         = 0x6984;
    static final int SW_CONDITIONS_NOT_SATIS = 0x6985;
    static final int SW_NOT_ALLOWED         = 0x6986;
    static final int SW_WRONG_DATA          = 0x6A80;
    static final int SW_SLOT_OUT_OF_RANGE   = 0x6A83;
    static final int SW_NO_SPACE            = 0x6A84;
    static final int SW_SLOT_EMPTY          = 0x6A88;
    static final int SW_NO_CARD_RECORD      = 0x6A8C;
    static final int SW_CARD_IN_USE         = 0x6A8D;
    static final int SW_NO_REFUND_KEY       = 0x6A8E;
    static final int SW_OVER_LIMIT          = 0x6A8F;
    static final int SW_NO_OWNER            = 0x6A90;
    static final int SW_OWNER_PROOF         = 0x6A91;
    static final int SW_NO_TIME             = 0x6A92;
    static final int SW_NOT_THE_TIME        = 0x6A93;
    static final int SW_PIECE_ON_CARD       = 0x6A94;
    static final int SW_INS_NOT_SUPPORTED   = 0x6D00;
    static final int SW_CLA_NOT_SUPPORTED   = 0x6E00;

    static final int MAX_PROOFS = 64;
    static final int SLOT = 82;
    static final int MINT_MAX = 80;

    static final byte[] TEST_PIN  = { 0x31, 0x32, 0x33, 0x34 };
    static final byte[] WRONG_PIN = { 0x39, 0x39, 0x39, 0x39 };
    static final byte[] NEW_PIN   = { 0x35, 0x36, 0x37, 0x38 };

    /** A time the signer told cards, in seconds: a day in 2027. */
    static final long T0 = 1_800_000_000L;
    static final long DAY = 86_400L;

    /**
     * A P-256 key pair, for an owner or a time signer: the JDK makes it and
     * signs with it (ECDSA, SHA-256, DER), and the card verifies.
     */
    static final class Key {
        final java.security.KeyPair pair;
        /** 04 || X || Y */
        final byte[] pub = new byte[65];

        Key() {
            try {
                java.security.KeyPairGenerator g = java.security.KeyPairGenerator.getInstance("EC");
                g.initialize(new java.security.spec.ECGenParameterSpec("secp256r1"));
                pair = g.generateKeyPair();
            } catch (java.security.GeneralSecurityException e) {
                throw new IllegalStateException(e);
            }
            java.security.spec.ECPoint w = ((java.security.interfaces.ECPublicKey) pair.getPublic()).getW();
            pub[0] = 0x04;
            put32(w.getAffineX(), 1);
            put32(w.getAffineY(), 33);
        }

        private void put32(java.math.BigInteger n, int at) {
            byte[] b = n.toByteArray();
            int len = Math.min(b.length, 32);
            System.arraycopy(b, b.length - len, pub, at + 32 - len, len);
        }

        byte[] sign(byte[] message) {
            try {
                java.security.Signature s = java.security.Signature.getInstance("SHA256withECDSA");
                s.initSign(pair.getPrivate());
                s.update(message);
                return s.sign();
            } catch (java.security.GeneralSecurityException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    /** The owner's key, another phone's, the time signer's and another signer's. */
    static final Key OWNER = new Key();
    static final Key OTHER_OWNER = new Key();
    static final Key SIGNER = new Key();
    static final Key OTHER_SIGNER = new Key();

    static final String KEYSET = "0059534ce0bfa19a";
    // two pieces the write-order tests load (SlotWriteOrderTest)
    static final byte[] PROOF_1 = buildProof("0059534ce0bfa19a", 1000, 1);
    static final byte[] PROOF_2 = buildProof("008288762774ace1", 500, 2);
    static final String MINT = "https://mint.example.com/Bitcoin";
    /** A refund key: the curve's generator, compressed. Any point will do. */
    static final byte[] REFUND = hexToBytes("0279be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798");
    static final byte[] NO_REFUND = new byte[33];

    static final String L_PIN = "FoxyCard/change-pin", L_LIMIT = "FoxyCard/set-limit", L_LOCK = "FoxyCard/lock",
        L_LOAD = "FoxyCard/load", L_OWNER = "FoxyCard/set-owner", L_CARD = "FoxyCard/set-card";
    static final String[] OWNER_LABELS = { L_PIN, L_LIMIT, L_LOCK, L_LOAD, L_OWNER, L_CARD };

    /** A runtime that hands the test its applet, so what a command cannot reach can be set or read directly. */
    static final class ExposedRuntime extends SimulatorRuntime {
        Applet appletAt(AID aid) {
            return getApplet(aid);
        }
    }

    private ExposedRuntime runtime;
    private CardSimulator simulator;

    @BeforeEach
    void setup() {
        simulator = freshCard();
    }

    // ---- what the tests do to a card -------------------------------------

    private int sw(CommandAPDU c) { return transmit(c).getSW(); }
    private int setPin(byte[] pin) { return sw(new CommandAPDU(CLA, INS_SET_PIN, 0, 0, pin)); }
    private int verify(byte[] pin) { return sw(new CommandAPDU(CLA, INS_VERIFY_PIN, 0, 0, pin)); }
    private ResponseAPDU nonce() { return transmit(new CommandAPDU(CLA, INS_GET_NONCE, 0, 0, 16)); }
    private byte[] nonceBytes() {
        ResponseAPDU r = nonce();
        assertEquals(SW_OK, r.getSW());
        assertEquals(16, r.getData().length);
        return r.getData();
    }

    static byte[] concat(byte[]... parts) {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        for (byte[] p : parts) out.write(p, 0, p.length);
        return out.toByteArray();
    }
    static byte[] u32(long v) { byte[] b = new byte[4]; putUint32(b, 0, v); return b; }

    /** The owner's proof for one command: an ECDSA signature by `key` over label || nonce || value. */
    static byte[] ownerProof(String label, Key key, byte[] nonce, byte[] value) {
        return key.sign(concat(label.getBytes(StandardCharsets.US_ASCII), nonce, value));
    }
    /** The time signer's signature over "FoxyCard/time" || the time. */
    static byte[] timeSignature(Key key, long t) {
        return key.sign(concat("FoxyCard/time".getBytes(StandardCharsets.US_ASCII), u32(t)));
    }
    /** An owner's command's data: the proof's length, the proof, then the value it was made over. */
    static byte[] ownerData(byte[] proof, byte[] value) {
        return concat(new byte[] { (byte) proof.length }, proof, value);
    }

    /** SET_CARD's data: unit, refund key, time key, mint length, mint. */
    static byte[] record(String mint, byte[] refund, byte[] timeKey) {
        byte[] m = mint.getBytes(StandardCharsets.US_ASCII);
        return concat(new byte[] { 0 }, refund, timeKey, new byte[] { (byte) m.length }, m);
    }

    private static CommandAPDU changePinCommand(byte[] proof, byte[] newPin) {
        return new CommandAPDU(CLA, INS_CHANGE_PIN, 0, 0, ownerData(proof, newPin));
    }
    private static CommandAPDU setLimitCommand(byte[] proof, long sats) {
        return new CommandAPDU(CLA, INS_SET_LIMIT_OWNER, 0, 0, ownerData(proof, u32(sats)));
    }
    private static CommandAPDU setLimitByPinCommand(long sats) {
        return new CommandAPDU(CLA, INS_SET_LIMIT, 0, 0, u32(sats));
    }
    private static CommandAPDU lockCommand(byte[] proof) {
        return new CommandAPDU(CLA, INS_LOCK_CARD, 0, 0xDE, ownerData(proof, new byte[0]));
    }
    private static CommandAPDU allowLoadCommand(byte[] proof) {
        return new CommandAPDU(CLA, INS_ALLOW_LOAD, 0, 0, ownerData(proof, new byte[0]));
    }
    private static CommandAPDU setOwnerOpenCommand(Key newOwner) {
        return new CommandAPDU(CLA, INS_SET_OWNER, 0, 0, newOwner.pub);
    }
    private static CommandAPDU setOwnerCommand(byte[] proof, Key newOwner) {
        return new CommandAPDU(CLA, INS_SET_OWNER, 0, 0, ownerData(proof, newOwner.pub));
    }
    private static CommandAPDU setCardOpenCommand(byte[] record) {
        return new CommandAPDU(CLA, INS_SET_CARD, 0, 0, record);
    }
    private static CommandAPDU setCardCommand(byte[] proof, byte[] record) {
        return new CommandAPDU(CLA, INS_SET_CARD, 0, 0, ownerData(proof, record));
    }
    private static CommandAPDU setTimeCommand(long t, byte[] signature) {
        return new CommandAPDU(CLA, INS_SET_TIME, 0, 0, concat(u32(t), new byte[] { (byte) signature.length }, signature), 4);
    }

    /** SET_CARD on an open card (no owner): the PIN form. */
    private int setCardOpen(String mint, byte[] refund, Key signer) {
        return sw(setCardOpenCommand(record(mint, refund, signer.pub)));
    }
    /** The owner's phone: a nonce, a proof by the owner's key over this record, SET_CARD. */
    private int ownerSetCard(String mint, byte[] refund, Key signer) {
        byte[] data = record(mint, refund, signer.pub);
        return sw(setCardCommand(ownerProof(L_CARD, OWNER, nonceBytes(), data), data));
    }
    /** SET_CARD as whichever form this card takes: the owner's when it has an owner, the PIN's when it has none. */
    private int setCard(String mint, byte[] refund) {
        return info()[16] == 1 ? ownerSetCard(mint, refund, SIGNER) : setCardOpen(mint, refund, SIGNER);
    }
    private int setOwner(Key owner) { return sw(setOwnerOpenCommand(owner)); }
    /** The owner's phone, as it does it: a nonce, a proof, the command. */
    private int changePin(byte[] newPin) {
        return sw(changePinCommand(ownerProof(L_PIN, OWNER, nonceBytes(), newPin), newPin));
    }
    private int setLimit(long sats) {
        return sw(setLimitCommand(ownerProof(L_LIMIT, OWNER, nonceBytes(), u32(sats)), sats));
    }
    private int allowLoad() {
        return sw(allowLoadCommand(ownerProof(L_LOAD, OWNER, nonceBytes(), new byte[0])));
    }
    private int lock() {
        return sw(lockCommand(ownerProof(L_LOCK, OWNER, nonceBytes(), new byte[0])));
    }
    /** The card is told the time, by the signer. */
    private int setTime(long t) { return sw(setTimeCommand(t, timeSignature(SIGNER, t))); }

    /** What the card says it will sign for in a day: GET_CARD's four bytes after the unit, as GET_INFO's say it too. */
    private long limit() {
        long viaCard = readUint32(transmit(new CommandAPDU(CLA, INS_GET_CARD, 0, 0, 256)).getData(), 3);
        assertEquals(viaCard, readUint32(info(), 12), "GET_INFO and GET_CARD say the same");
        return viaCard;
    }
    /** The card's clock, and the window and what it has signed for, from GET_INFO. */
    private long now() { return readUint32(info(), 17); }
    private long windowStart() { return readUint32(info(), 21); }
    private long spentToday() { return readUint32(info(), 25); }

    private ResponseAPDU load(byte[] proof) { return transmit(new CommandAPDU(CLA, INS_LOAD_PROOF, 0, 0, proof, 1)); }
    private ResponseAPDU spend(int slot) { return transmit(new CommandAPDU(CLA, INS_SPEND_PROOF, slot, 0, 64)); }
    private int clearSpent() { return sw(new CommandAPDU(CLA, INS_CLEAR_SPENT, 0, 0, 1)); }
    private byte[] info() { return transmit(new CommandAPDU(CLA, INS_GET_INFO, 0, 0, 256)).getData(); }
    private byte[] cardRecord() { return transmit(new CommandAPDU(CLA, INS_GET_CARD, 0, 0, 256)).getData(); }
    private byte[] cardKey() { return transmit(new CommandAPDU(CLA, INS_GET_PUBKEY, 0, 0, 256)).getData(); }
    private byte[] slot(int i) { return transmit(new CommandAPDU(CLA, INS_GET_PROOF, i, 0, 256)).getData(); }
    private long balance() { return readUint32(transmit(new CommandAPDU(CLA, INS_GET_BALANCE, 0, 0, 4)).getData(), 0); }
    private void reselect() {
        assertEquals(SW_OK, sw(new CommandAPDU(0x00, 0xA4, 0x04, 0x00, hexToBytes(AID_STR))));
    }

    /**
     * A card as its holder leaves it: set up by an open card's steps, as the
     * phone does them (PIN, record with the time key, and the owner last), the
     * PIN verified, and told the time. No limit.
     */
    private void ready() {
        readyOn(simulator, 0);
    }

    /** The same, with a daily limit set by the owner. */
    private void readyWithLimit(long limit) {
        readyOn(simulator, limit);
    }

    /**
     * A card in another test class's hands: set up as the phone sets one up
     * (PIN, verify, record, owner), told the time, and given a limit if one is
     * asked for. Leaves the PIN verified.
     */
    static void readyOn(CardSimulator sim, long limit) {
        assertEquals(SW_OK, sim.transmitCommand(new CommandAPDU(CLA, INS_SET_PIN, 0, 0, TEST_PIN)).getSW());
        assertEquals(SW_OK, sim.transmitCommand(new CommandAPDU(CLA, INS_VERIFY_PIN, 0, 0, TEST_PIN)).getSW());
        assertEquals(SW_OK, sim.transmitCommand(setCardOpenCommand(record(MINT, REFUND, SIGNER.pub))).getSW());
        assertEquals(SW_OK, sim.transmitCommand(setOwnerOpenCommand(OWNER)).getSW());
        assertEquals(SW_OK, sim.transmitCommand(setTimeCommand(T0, timeSignature(SIGNER, T0))).getSW());
        if (limit > 0) {
            byte[] n = sim.transmitCommand(new CommandAPDU(CLA, INS_GET_NONCE, 0, 0, 16)).getData();
            assertEquals(SW_OK, sim.transmitCommand(setLimitCommand(ownerProof(L_LIMIT, OWNER, n, u32(limit)), limit)).getSW());
        }
    }

    private boolean signedFor(byte[] sig, byte[] slotData, byte[] refund) throws Exception {
        byte[] nonce = Arrays.copyOfRange(slotData, 13, 45);
        long date = readUint32(slotData, 78);
        byte[] msg = sha256(secretText(nonce, cardKey(), date, refund).getBytes(StandardCharsets.UTF_8));
        return schnorrVerify(extractPubkeyX(cardKey()), msg, sig);
    }

    /** One of the applet's persistent arrays, live, for what no command can reach. */
    private byte[] field(String name) throws Exception {
        java.lang.reflect.Field f = CashuApplet.class.getDeclaredField(name);
        f.setAccessible(true);
        return (byte[]) f.get(runtime.appletAt(AIDUtil.create(AID_HEX)));
    }
    private void putRecord(int at, byte[] bytes) throws Exception {
        System.arraycopy(bytes, 0, field("cardRecord"), at, bytes.length);
    }

    // =========================================================================
    // What the card is
    // =========================================================================

    @Test
    @DisplayName("SELECT by the applet's whole name answers, as by its package's nine bytes")
    void testSelectByTheWholeName() {
        // what a phone sends: iOS chooses by the name in the app's Info.plist, and Foxy by the same ten bytes
        ResponseAPDU whole = transmit(new CommandAPDU(0x00, 0xA4, 0x04, 0x00, hexToBytes(AID_HEX), 256));
        assertEquals(SW_OK, whole.getSW());
        assertArrayEquals(new byte[] { 0x01, 0x02 }, whole.getData(), "the same answer either way: version 1.2");
        assertEquals(SW_OK, sw(new CommandAPDU(CLA, INS_GET_INFO, 0, 0, 256)), "and its instructions follow");
    }

    @Test
    @DisplayName("SELECT answers version 1.2, and not to upstream's AID")
    void testSelect() {
        ResponseAPDU resp = transmit(new CommandAPDU(0x00, 0xA4, 0x04, 0x00, hexToBytes(AID_STR)));
        assertEquals(SW_OK, resp.getSW());
        assertArrayEquals(new byte[] { 0x01, 0x02 }, resp.getData());
        assertNotEquals(SW_OK, sw(new CommandAPDU(0x00, 0xA4, 0x04, 0x00, hexToBytes(UPSTREAM_AID))),
            "an upstream reader must not find this applet under upstream's AID: the wire is not the same");
    }

    @Test
    @DisplayName("GET_INFO on a new card: 30 bytes: 64 empty slots, no PIN, three tries, format 3, no record, no limit, no owner, no time, no change due")
    void testInfoFresh() {
        byte[] d = info();
        assertEquals(30, d.length);
        assertEquals(1, d[0]); assertEquals(2, d[1]);
        assertEquals(MAX_PROOFS, d[2] & 0xFF);
        assertEquals(0, d[3]); assertEquals(0, d[4]);
        assertEquals(MAX_PROOFS, d[5] & 0xFF);
        assertEquals(0x07, d[6]);
        assertEquals(0, d[7], "no PIN");
        assertEquals(3, d[8], "format");
        assertEquals(3, d[9], "tries");
        assertEquals(0, d[10], "not locked");
        assertEquals(0, d[11], "no card record");
        assertEquals(0, readUint32(d, 12), "no limit: zero is none, and is what a new card has");
        assertEquals(0, d[16], "no owner");
        assertEquals(0, readUint32(d, 17), "the card has not been told the time");
        assertEquals(0, readUint32(d, 21), "no window");
        assertEquals(0, readUint32(d, 25), "and has signed for nothing today");
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
        assertEquals(SW_INS_NOT_SUPPORTED, sw(new CommandAPDU(CLA, 0x36, 0, 0, 256)), "nothing sits between SET_TIME and VERIFY_PIN");
        assertEquals(SW_INS_NOT_SUPPORTED, sw(new CommandAPDU(CLA, 0x46, 0, 0, 256)), "or after ALLOW_LOAD");
    }

    @Test
    @DisplayName("The P-256 constants the owner key and the time key are set up with are the curve's, to the byte")
    void testTheCurveIsP256() throws Exception {
        java.security.AlgorithmParameters params = java.security.AlgorithmParameters.getInstance("EC");
        params.init(new java.security.spec.ECGenParameterSpec("secp256r1"));
        java.security.spec.ECParameterSpec spec = params.getParameterSpec(java.security.spec.ECParameterSpec.class);
        java.security.spec.ECFieldFp field = (java.security.spec.ECFieldFp) spec.getCurve().getField();
        assertArrayEquals(unsigned32(field.getP()), constant("P256_P"), "the field prime");
        assertArrayEquals(unsigned32(spec.getCurve().getA()), constant("P256_A"), "a");
        assertArrayEquals(unsigned32(spec.getCurve().getB()), constant("P256_B"), "b");
        assertArrayEquals(unsigned32(spec.getOrder()), constant("P256_N"), "the order");
        byte[] g = new byte[65];
        g[0] = 0x04;
        System.arraycopy(unsigned32(spec.getGenerator().getAffineX()), 0, g, 1, 32);
        System.arraycopy(unsigned32(spec.getGenerator().getAffineY()), 0, g, 33, 32);
        assertArrayEquals(g, constant("P256_G"), "the generator, uncompressed");
        assertEquals(1, spec.getCofactor());
        // and the card's own key and these have nothing in common
        assertFalse(Arrays.equals(constant("P256_P"), constant("SECP256K1_P")));
    }

    private static byte[] unsigned32(java.math.BigInteger n) {
        byte[] b = n.toByteArray();
        byte[] out = new byte[32];
        int len = Math.min(b.length, 32);
        System.arraycopy(b, b.length - len, out, 32 - len, len);
        return out;
    }

    private static byte[] constant(String name) throws Exception {
        java.lang.reflect.Field f = CashuApplet.class.getDeclaredField(name);
        f.setAccessible(true);
        return (byte[]) f.get(null);
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
    @DisplayName("Nothing is loaded with the PIN set and not verified, onto a card with no owner, before it knows its mint, or before it has been told the time")
    void testLoadNeedsEverything() {
        assertEquals(SW_OK, setPin(TEST_PIN));
        assertEquals(SW_SECURITY_NOT_SATIS, load(buildProof(KEYSET, 16, 1)).getSW(), "the PIN, not verified");
        assertEquals(SW_OK, verify(TEST_PIN));
        assertEquals(SW_NO_OWNER, load(buildProof(KEYSET, 16, 1)).getSW(), "no owner");
        assertEquals(SW_OK, setCard(MINT, REFUND));
        assertEquals(SW_NO_OWNER, load(buildProof(KEYSET, 16, 1)).getSW(), "a record, and still no owner");
        assertEquals(SW_OK, setOwner(OWNER));
        assertEquals(SW_NO_TIME, load(buildProof(KEYSET, 16, 1)).getSW(), "an owner and a record, and no time");
        assertEquals(0, info()[3], "nothing was loaded by any of them");
        assertEquals(SW_OK, setTime(T0));
        assertEquals(SW_OK, load(buildProof(KEYSET, 16, 1)).getSW());
    }

    @Test
    @DisplayName("Nothing is loaded before the card knows its mint")
    void testLoadNeedsARecord() {
        assertEquals(SW_OK, setPin(TEST_PIN));
        assertEquals(SW_OK, verify(TEST_PIN));
        assertEquals(SW_OK, setOwner(OWNER));
        assertEquals(SW_NO_CARD_RECORD, load(buildProof(KEYSET, 16, 1)).getSW());
        assertEquals(SW_NO_CARD_RECORD, setTime(T0), "and with no record there is no time key to check a time against");
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
        assertEquals(SW_OK, setOwner(OWNER));
        assertEquals(SW_OK, setTime(T0));
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
    // Reading the whole card in a few answers (GET_PIECES)
    // =========================================================================

    private ResponseAPDU pieces(int from) { return transmit(new CommandAPDU(CLA, INS_GET_PIECES, from, 0, 256)); }

    /** One page as the phone reads it: the next slot to ask for, then the entries as { slot, state, the piece or null }. */
    private static final class Page {
        final int next, length;
        final java.util.List<Object[]> entries = new java.util.ArrayList<>();
        Page(byte[] d) {
            length = d.length;
            next = d[0] & 0xFF;
            int at = 1;
            while (at < d.length) {
                int tag = d[at++] & 0xFF;
                int state = tag >> 6, slot = tag & 0x3F;
                byte[] piece = null;
                if (state == 1) { piece = Arrays.copyOfRange(d, at, at + 81); at += 81; }
                entries.add(new Object[] { slot, state, piece });
            }
            assertEquals(d.length, at, "the answer is made of whole entries");
        }
    }

    @Test
    @DisplayName("GET_PIECES on an empty card is one byte: nothing more to ask for")
    void testPiecesEmpty() {
        ready();
        ResponseAPDU r = pieces(0);
        assertEquals(SW_OK, r.getSW());
        assertArrayEquals(new byte[] { 64 }, r.getData());
        assertEquals(SW_SLOT_OUT_OF_RANGE, pieces(MAX_PROOFS).getSW());
        assertEquals(SW_SLOT_OUT_OF_RANGE, pieces(255).getSW());
    }

    @Test
    @DisplayName("GET_PIECES gives each unspent piece as GET_PROOF does, in one answer for up to three, and asks for no PIN")
    void testPiecesOnePage() {
        ready();
        for (int i = 0; i < 3; i++) assertEquals(SW_OK, load(buildProof(KEYSET, 100 + i, i + 1, i == 1 ? 1900000000L : 0)).getSW());
        reselect();     // a new tap: the PIN is not verified, and nothing here asks for it
        ResponseAPDU r = pieces(0);
        assertEquals(SW_OK, r.getSW());
        Page page = new Page(r.getData());
        assertEquals(1 + 3 * 82, page.length);
        assertEquals(64, page.next);
        assertEquals(3, page.entries.size());
        for (int i = 0; i < 3; i++) {
            assertEquals(i, page.entries.get(i)[0]);
            assertEquals(1, page.entries.get(i)[1]);
            // the same bytes GET_PROOF gives, after its status byte
            assertArrayEquals(Arrays.copyOfRange(slot(i), 1, 82), (byte[]) page.entries.get(i)[2], "slot " + i);
        }
    }

    @Test
    @DisplayName("GET_PIECES lists a spent slot by its tag alone, leaves an empty one out, and starts where it is asked to")
    void testPiecesStates() {
        ready();
        for (int i = 0; i < 6; i++) assertEquals(SW_OK, load(buildProof(KEYSET, 10 + i, i + 1)).getSW());
        assertEquals(SW_OK, spend(1).getSW());
        assertEquals(SW_OK, spend(2).getSW());
        assertEquals(SW_OK, spend(4).getSW());
        Page all = new Page(pieces(0).getData());
        assertEquals(64, all.next);
        assertEquals(6, all.entries.size());
        int[] states = { 1, 2, 2, 1, 2, 1 };
        for (int i = 0; i < 6; i++) {
            assertEquals(i, all.entries.get(i)[0]);
            assertEquals(states[i], all.entries.get(i)[1], "slot " + i);
            assertEquals(states[i] == 1, all.entries.get(i)[2] != null);
        }
        assertEquals(1 + 3 * 82 + 3, all.length);
        // a page from the middle, and from an empty slot
        Page mid = new Page(pieces(3).getData());
        assertEquals(3, mid.entries.size());
        assertEquals(3, mid.entries.get(0)[0]);
        Page empty = new Page(pieces(20).getData());
        assertEquals(0, empty.entries.size());
        assertEquals(64, empty.next);
        // a freed place is not listed
        assertEquals(SW_OK, clearSpent());
        Page freed = new Page(pieces(0).getData());
        assertEquals(3, freed.entries.size());
        // and the same states GET_SLOT_STATUS gives
        byte[] status = transmit(new CommandAPDU(CLA, INS_GET_SLOT_STATUS, 0, 0, 256)).getData();
        for (Object[] e : freed.entries) assertEquals(status[(int) e[0]], (int) e[1]);
    }

    @Test
    @DisplayName("GET_PIECES on a full card: pages of three pieces, every one within a short APDU, every one moving on")
    void testPiecesFullCard() {
        ready();
        for (int i = 0; i < MAX_PROOFS; i++) assertEquals(SW_OK, load(buildProof(KEYSET, 1 + i, i + 1)).getSW());
        int from = 0, pages = 0, seen = 0;
        while (from < MAX_PROOFS) {
            ResponseAPDU r = pieces(from);
            assertEquals(SW_OK, r.getSW());
            assertTrue(r.getData().length <= 255, "within what a short APDU carries");
            Page page = new Page(r.getData());
            assertTrue(page.next > from, "every answer moves on");
            assertTrue(page.entries.size() <= 3);
            for (Object[] e : page.entries) {
                int i = (int) e[0];
                assertEquals(seen, i, "in order, none missed");
                assertArrayEquals(Arrays.copyOfRange(slot(i), 1, 82), (byte[]) e[2]);
                seen++;
            }
            from = page.next;
            pages++;
        }
        assertEquals(MAX_PROOFS, seen);
        assertEquals(22, pages, "sixty-four pieces are twenty-two answers, not sixty-four");
    }

    @Test
    @DisplayName("GET_PIECES fills a page to 255 bytes and not a byte over: three pieces and eight spent tags, then the next piece starts a page")
    void testPiecesPageEdge() {
        ready();
        for (int i = 0; i < 12; i++) assertEquals(SW_OK, load(buildProof(KEYSET, 5 + i, i + 1)).getSW());
        for (int i = 3; i <= 10; i++) assertEquals(SW_OK, spend(i).getSW());
        Page one = new Page(pieces(0).getData());
        assertEquals(255, one.length);
        assertEquals(11, one.next);
        assertEquals(11, one.entries.size());
        Page two = new Page(pieces(one.next).getData());
        assertEquals(64, two.next);
        assertEquals(1, two.entries.size());
        assertEquals(11, two.entries.get(0)[0]);
        // one spent tag more would be 256: with slot 11 spent too, its tag is left for the next page
        assertEquals(SW_OK, spend(11).getSW());
        Page nine = new Page(pieces(0).getData());
        assertEquals(255, nine.length);
        assertEquals(11, nine.next);
        Page rest = new Page(pieces(nine.next).getData());
        assertEquals(1, rest.entries.size());
        assertEquals(11, rest.entries.get(0)[0]);
        assertEquals(2, rest.entries.get(0)[1]);
    }

    @Test
    @DisplayName("GET_PIECES changes nothing: not the day, not the slots, not the PIN's state")
    void testPiecesReadOnly() {
        ready();
        for (int i = 0; i < 4; i++) assertEquals(SW_OK, load(buildProof(KEYSET, 20 + i, i + 1)).getSW());
        assertEquals(SW_OK, spend(0).getSW());
        byte[] infoBefore = info();
        byte[] statusBefore = transmit(new CommandAPDU(CLA, INS_GET_SLOT_STATUS, 0, 0, 256)).getData();
        byte[] first = pieces(0).getData();
        assertArrayEquals(first, pieces(0).getData(), "asking twice says the same");
        assertArrayEquals(infoBefore, info());
        assertArrayEquals(statusBefore, transmit(new CommandAPDU(CLA, INS_GET_SLOT_STATUS, 0, 0, 256)).getData());
        // a locked or blocked card still tells what it holds, as GET_PROOF does
        assertEquals(SW_OK, lock());
        assertEquals(SW_OK, pieces(0).getSW());
    }

    // =========================================================================
    // The card record
    // =========================================================================

    @Test
    @DisplayName("SET_CARD is read back by GET_CARD, whole, with the time key, and does not touch the limit")
    void testCardRecord() {
        ready();
        assertEquals(SW_OK, setLimit(5000));
        byte[] c = cardRecord();
        assertEquals(106 + MINT.length(), c.length);
        assertEquals(3, c[0]); assertEquals(1, c[1]); assertEquals(0, c[2]);
        assertEquals(5000, readUint32(c, 3));
        assertArrayEquals(REFUND, Arrays.copyOfRange(c, 7, 40));
        assertArrayEquals(SIGNER.pub, Arrays.copyOfRange(c, 40, 105), "the time key, uncompressed");
        assertEquals(MINT.length(), c[105] & 0xFF);
        assertEquals(MINT, new String(Arrays.copyOfRange(c, 106, c.length), StandardCharsets.US_ASCII));
        assertEquals(1, info()[11]);
        assertEquals(5000, readUint32(info(), 12));
        assertEquals(1, info()[16], "and it has an owner");
        // SET_CARD writes the record and not the limit, which is the owner's to set
        assertEquals(SW_OK, setCard("https://other.example.com", NO_REFUND));
        assertEquals(5000, limit(), "a new record leaves the limit as it was");
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
        assertEquals(SW_WRONG_LENGTH, setCard("x".repeat(MINT_MAX + 1), REFUND), "a mint too long to keep");
        byte[] lying = record("0123456789", REFUND, SIGNER.pub);
        lying[99] = 20;
        byte[] n = nonceBytes();
        assertEquals(SW_WRONG_LENGTH, sw(setCardCommand(ownerProof(L_CARD, OWNER, n, lying), lying)), "a length that is not the mint's");
        byte[] notAPoint = SIGNER.pub.clone(); notAPoint[0] = 0x02;
        byte[] data = record(MINT, REFUND, notAPoint);
        n = nonceBytes();
        assertEquals(SW_WRONG_DATA, sw(setCardCommand(ownerProof(L_CARD, OWNER, n, data), data)), "a time key that is not uncompressed");
        byte[] c = cardRecord();
        assertEquals(MINT, new String(Arrays.copyOfRange(c, 106, c.length), StandardCharsets.US_ASCII));
        assertArrayEquals(SIGNER.pub, Arrays.copyOfRange(c, 40, 105), "the time key is as it was");
        assertEquals(SW_OK, setCard("x".repeat(MINT_MAX), REFUND), "eighty is kept");
        // the longest a record and its proof can be: one short APDU of 255 data bytes
        byte[] longest = record("x".repeat(MINT_MAX), REFUND, SIGNER.pub);
        assertEquals(100 + MINT_MAX, longest.length);
        assertTrue(1 + 72 + longest.length <= 255, "a proof of 72 bytes and the longest record fit one command");
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
    @DisplayName("SPEND_PROOF signs the slot's own secret, as the wallet's library writes one")
    void testSpendSignsTheSlot() throws Exception {
        ready();
        assertEquals(SW_OK, load(buildProof(KEYSET, 16, 7)).getSW());
        byte[] before = slot(0);
        ResponseAPDU r = spend(0);
        assertEquals(SW_OK, r.getSW());
        assertEquals(64, r.getData().length);
        assertTrue(signedFor(r.getData(), before, REFUND), "a BIP-340 signature over SHA-256 of the piece's secret");
        // spelt out: a piece locked to one key, as cashu-ts writes it
        String plain = "[\"P2PK\",{\"nonce\":\"" + toHex(Arrays.copyOfRange(before, 13, 45)) + "\",\"data\":\""
            + toHex(cardKey()) + "\",\"tags\":[]}]";
        assertTrue(schnorrVerify(extractPubkeyX(cardKey()), sha256(plain.getBytes(StandardCharsets.UTF_8)), r.getData()));
        // and not upstream's, which the library does not write
        String upstream = plain.replace("\"tags\":[]", "\"tags\":[[\"sigflag\",\"SIG_INPUTS\"]]");
        assertFalse(schnorrVerify(extractPubkeyX(cardKey()), sha256(upstream.getBytes(StandardCharsets.UTF_8)), r.getData()));
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

    /** Every command that needs the PIN first, as (name, command): each is refused with 6982 until it has been verified. */
    private Object[][] gated() {
        return new Object[][] {
            { "SPEND_PROOF", new CommandAPDU(CLA, INS_SPEND_PROOF, 0, 0, 64) },
            { "LOAD_PROOF",  new CommandAPDU(CLA, INS_LOAD_PROOF, 0, 0, buildProof(KEYSET, 4, 50), 1) },
            { "CLEAR_SPENT", new CommandAPDU(CLA, INS_CLEAR_SPENT, 0, 0, 1) },
            { "SET_LIMIT (PIN form)", setLimitByPinCommand(1) },
            { "LOCK_CARD",   lockCommand(new byte[72]) },
        };
    }

    private void assertAllGatedRefuse(String when) {
        long before = balance();
        long limit = limit();
        byte[] status = transmit(new CommandAPDU(CLA, INS_GET_SLOT_STATUS, 0, 0, 256)).getData();
        for (Object[] g : gated()) {
            assertEquals(SW_SECURITY_NOT_SATIS, sw((CommandAPDU) g[1]), g[0] + " " + when);
        }
        assertEquals(before, balance(), "nothing was spent or added " + when);
        assertEquals(limit, limit(), "and the limit is as it was " + when);
        assertArrayEquals(status, transmit(new CommandAPDU(CLA, INS_GET_SLOT_STATUS, 0, 0, 256)).getData());
        assertEquals(0, info()[10], "and the card was not locked " + when);
    }

    @Test
    @DisplayName("Every gated command refuses: PIN set and not verified, after a wrong PIN, and once blocked, for good")
    void testGateInEveryState() {
        readyWithLimit(100000);
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
        assertEquals(SW_OWNER_PROOF, setPin(NEW_PIN), "and a blocked card cannot be given a new PIN by whoever is at the reader");
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
    @DisplayName("A right PIN gives the tries back; VERIFY_PIN before a PIN exists says so; an open card's PIN can be set again")
    void testTries() {
        assertEquals(SW_PIN_NOT_SET, verify(TEST_PIN));
        assertEquals(SW_OK, setPin(TEST_PIN));
        assertEquals(SW_OK, setPin(NEW_PIN), "a card with no owner has nobody's PIN to protect");
        assertEquals(SW_OK, setPin(TEST_PIN));
        assertEquals(0x63C2, verify(WRONG_PIN));
        assertEquals(2, info()[9]);
        assertEquals(SW_OK, verify(TEST_PIN));
        assertEquals(3, info()[9]);
        assertEquals(SW_WRONG_LENGTH, verify(new byte[] { 1, 2, 3 }));
        assertEquals(SW_WRONG_LENGTH, verify(new byte[9]));
        assertEquals(SW_WRONG_LENGTH, setPin(new byte[] { 1, 2, 3 }));
        assertEquals(SW_WRONG_LENGTH, setPin(new byte[9]));
    }

    @Test
    @DisplayName("SET_PIN on an open card replaces the PIN, unblocks it and ends the session")
    void testSetPinOnAnOpenCard() {
        assertEquals(SW_OK, setPin(TEST_PIN));
        assertEquals(SW_OK, verify(TEST_PIN));
        reselect();
        assertEquals(0x63C2, verify(WRONG_PIN));
        assertEquals(0x63C1, verify(WRONG_PIN));
        assertEquals(SW_PIN_BLOCKED, verify(WRONG_PIN));
        assertEquals(2, info()[7]);
        assertEquals(SW_OK, setPin(NEW_PIN), "an open card needs nobody's say to be given a PIN again");
        assertEquals(1, info()[7], "unblocked");
        assertEquals(3, info()[9]);
        assertEquals(SW_OK, verify(NEW_PIN));
        assertEquals(SW_OK, setPin(TEST_PIN));
        assertEquals(SW_SECURITY_NOT_SATIS, sw(setLimitByPinCommand(0)), "and the session that verified the old one is over");
    }

    // =========================================================================
    // The owner
    // =========================================================================

    @Test
    @DisplayName("GET_NONCE gives 16 new bytes each time and needs an owner; nothing else says the owner's key")
    void testNonce() {
        assertEquals(SW_NO_OWNER, nonce().getSW(), "a card with no owner has no use for one");
        assertEquals(SW_OK, setPin(TEST_PIN));
        assertEquals(SW_OK, verify(TEST_PIN));
        assertEquals(SW_OK, setCard(MINT, REFUND));
        assertEquals(SW_OK, setOwner(OWNER));
        byte[] a = nonceBytes(), b = nonceBytes();
        assertFalse(Arrays.equals(a, b), "new each time");
        // the key is in nothing the card says
        String said = toHex(info()) + toHex(cardRecord()) + toHex(cardKey()) + toHex(a) + toHex(b)
            + toHex(transmit(new CommandAPDU(CLA, INS_GET_SLOT_STATUS, 0, 0, 256)).getData());
        assertFalse(said.contains(toHex(OWNER.pub)), "the owner's public key is not read out");
        assertFalse(said.contains(toHex(OWNER.pub).substring(2)), "not without its prefix either");
    }

    @Test
    @DisplayName("The owner survives a new SELECT and a reset")
    void testOwnerSurvives() {
        ready();
        reselect();
        assertEquals(1, info()[16]);
        simulator.reset();
        reselect();
        assertEquals(1, info()[16]);
        assertEquals(SW_OK, setLimit(7), "and the card still knows its owner's key");
    }

    @Test
    @DisplayName("SET_OWNER takes 65 bytes beginning 04, on an open card and, with a proof, on an owned one")
    void testSetOwnerShape() {
        assertEquals(SW_WRONG_LENGTH, sw(new CommandAPDU(CLA, INS_SET_OWNER, 0, 0, new byte[32])));
        assertEquals(SW_WRONG_LENGTH, sw(new CommandAPDU(CLA, INS_SET_OWNER, 0, 0, new byte[64])));
        assertEquals(SW_WRONG_LENGTH, sw(new CommandAPDU(CLA, INS_SET_OWNER, 0, 0, new byte[66])));
        byte[] compressed = new byte[65]; compressed[0] = 0x02;
        assertEquals(SW_WRONG_DATA, sw(new CommandAPDU(CLA, INS_SET_OWNER, 0, 0, compressed)), "04 is the first byte of an uncompressed key");
        assertEquals(0, info()[16], "none of them was kept");
        assertEquals(SW_OK, setOwner(OWNER));
        assertEquals(1, info()[16]);
        // an owned card takes a new key only with a proof, and the same shapes
        byte[] bad = OTHER_OWNER.pub.clone(); bad[0] = 0x02;
        byte[] n = nonceBytes();
        assertEquals(SW_WRONG_DATA, sw(new CommandAPDU(CLA, INS_SET_OWNER, 0, 0, ownerData(ownerProof(L_OWNER, OWNER, n, bad), bad))));
        byte[] shortKey = Arrays.copyOf(OTHER_OWNER.pub, 64);
        n = nonceBytes();
        assertEquals(SW_WRONG_LENGTH, sw(new CommandAPDU(CLA, INS_SET_OWNER, 0, 0, ownerData(ownerProof(L_OWNER, OWNER, n, shortKey), shortKey))));
        assertEquals(SW_OK, setLimit(0), "the owner is still the first");
    }

    /**
     * An owner's command, as the tests below take it through every refusal and
     * one acceptance: its label, the value its proof covers, how to build it
     * from a proof, and how to see that it worked.
     */
    private interface OwnerCommand {
        String label();
        byte[] value();
        CommandAPDU build(byte[] proof);
        void assertDone(CashuAppletTest t);
        /** Whether the command also wants a verified PIN (LOCK_CARD does, as it always did). */
        default boolean wantsPin() { return false; }
    }

    private OwnerCommand[] ownerCommands() {
        return new OwnerCommand[] {
            new OwnerCommand() {
                public String label() { return L_PIN; }
                public byte[] value() { return NEW_PIN; }
                public CommandAPDU build(byte[] proof) { return changePinCommand(proof, NEW_PIN); }
                public void assertDone(CashuAppletTest t) { t.reselect(); assertEquals(SW_OK, t.verify(NEW_PIN)); }
                public String toString() { return "CHANGE_PIN"; }
            },
            new OwnerCommand() {
                public String label() { return L_LIMIT; }
                public byte[] value() { return u32(777); }
                public CommandAPDU build(byte[] proof) { return setLimitCommand(proof, 777); }
                public void assertDone(CashuAppletTest t) { assertEquals(777, t.limit()); }
                public String toString() { return "SET_LIMIT (owner's form)"; }
            },
            new OwnerCommand() {
                public String label() { return L_OWNER; }
                public byte[] value() { return OTHER_OWNER.pub; }
                public CommandAPDU build(byte[] proof) { return setOwnerCommand(proof, OTHER_OWNER); }
                public void assertDone(CashuAppletTest t) {
                    // the new owner can now be proved, and the old one cannot
                    assertEquals(SW_OK, sw2(t, setLimitCommand(ownerProof(L_LIMIT, OTHER_OWNER, t.nonceBytes(), u32(9)), 9)));
                    assertEquals(SW_OWNER_PROOF, sw2(t, setLimitCommand(ownerProof(L_LIMIT, OWNER, t.nonceBytes(), u32(10)), 10)));
                }
                public String toString() { return "SET_OWNER (replacing one)"; }
            },
            new OwnerCommand() {
                private final byte[] data = record("https://other.example.com", NO_REFUND, OTHER_SIGNER.pub);
                public String label() { return L_CARD; }
                public byte[] value() { return data; }
                public CommandAPDU build(byte[] proof) { return setCardCommand(proof, data); }
                public void assertDone(CashuAppletTest t) {
                    byte[] c = t.cardRecord();
                    assertEquals("https://other.example.com", new String(Arrays.copyOfRange(c, 106, c.length), StandardCharsets.US_ASCII));
                    assertArrayEquals(OTHER_SIGNER.pub, Arrays.copyOfRange(c, 40, 105));
                }
                public String toString() { return "SET_CARD"; }
            },
            new OwnerCommand() {
                public String label() { return L_LOAD; }
                public byte[] value() { return new byte[0]; }
                public CommandAPDU build(byte[] proof) { return allowLoadCommand(proof); }
                public void assertDone(CashuAppletTest t) { assertEquals(SW_OK, t.load(buildProof(KEYSET, 8, 70)).getSW(), "loaded with no PIN"); }
                public String toString() { return "ALLOW_LOAD"; }
            },
            new OwnerCommand() {
                public String label() { return L_LOCK; }
                public byte[] value() { return new byte[0]; }
                public CommandAPDU build(byte[] proof) { return lockCommand(proof); }
                public void assertDone(CashuAppletTest t) { assertEquals(1, t.info()[10], "locked"); }
                public boolean wantsPin() { return true; }
                public String toString() { return "LOCK_CARD"; }
            },
        };
    }

    private static int sw2(CashuAppletTest t, CommandAPDU c) { return t.sw(c); }

    @Test
    @DisplayName("Every owner's command is refused with the PIN alone, with a nonce and no proof, with another key's signature, with another command's label, with an earlier nonce's proof, with a proof for another value, and with a proof that has worked once; and is accepted with the right proof and no PIN")
    void testEveryOwnerCommandNeedsItsProof() {
        for (OwnerCommand c : ownerCommands()) {
            simulator = freshCard();
            ready();
            // the PIN is verified, the card is empty, nothing is on it: a terminal that holds the PIN has all it can have
            byte[] proof = ownerProof(c.label(), OWNER, new byte[16], c.value());
            long limitBefore = limit();
            byte[] recordBefore = cardRecord();
            String name = c.toString();

            assertEquals(SW_OWNER_PROOF, sw(c.build(proof)), name + ": the PIN alone, no nonce asked for");
            nonceBytes();
            assertEquals(SW_OWNER_PROOF, sw(c.build(new byte[] { 0x30, 0x06, 0x02, 0x01, 0x01, 0x02, 0x01, 0x01 })), name + ": a nonce, and a proof of nothing");
            byte[] n = nonceBytes();
            assertEquals(SW_OWNER_PROOF, sw(c.build(ownerProof(c.label(), OTHER_OWNER, n, c.value()))), name + ": another key's signature");
            for (String other : OWNER_LABELS) {
                if (other.equals(c.label())) continue;
                n = nonceBytes();
                assertEquals(SW_OWNER_PROOF, sw(c.build(ownerProof(other, OWNER, n, c.value()))), name + ": a proof under " + other);
            }
            n = nonceBytes();
            byte[] wrongValue = c.value().length == 0 ? new byte[] { 1 } : flip(c.value());
            assertEquals(SW_OWNER_PROOF, sw(c.build(ownerProof(c.label(), OWNER, n, wrongValue))), name + ": a proof for another value");
            byte[] older = nonceBytes();
            byte[] forOlder = ownerProof(c.label(), OWNER, older, c.value());
            nonceBytes();
            assertEquals(SW_OWNER_PROOF, sw(c.build(forOlder)), name + ": a proof for an earlier nonce");
            byte[] m = nonceBytes();
            byte[] right = ownerProof(c.label(), OWNER, m, c.value());
            assertEquals(SW_OWNER_PROOF, sw(c.build(ownerProof(c.label(), OTHER_OWNER, m, c.value()))), name + ": a wrong proof uses the nonce up");
            assertEquals(SW_OWNER_PROOF, sw(c.build(right)), name + ": and the right proof for it is no good after that");
            // nothing happened, and no PIN try was spent, and the session was not ended
            assertEquals(limitBefore, limit(), name);
            assertArrayEquals(recordBefore, cardRecord(), name);
            assertEquals(3, info()[9], name + ": a wrong proof costs no PIN tries");
            assertEquals(0, info()[10], name + ": not locked");
            assertEquals(1, info()[7], name + ": not blocked");
            assertEquals(SW_OK, verify(TEST_PIN), name + ": the session was not ended and the PIN is the old one");

            // and the right one, with no PIN verified, is taken
            reselect();
            if (c.wantsPin()) {
                assertEquals(SW_SECURITY_NOT_SATIS, sw(c.build(ownerProof(c.label(), OWNER, nonceBytes(), c.value()))), name + ": needs the PIN as well, as it always did");
                assertEquals(SW_OK, verify(TEST_PIN));
            }
            n = nonceBytes();
            byte[] good = ownerProof(c.label(), OWNER, n, c.value());
            assertEquals(SW_OK, sw(c.build(good)), name + ": the owner's proof" + (c.wantsPin() ? "" : ", and no PIN"));
            c.assertDone(this);
            // used: the same proof cannot be sent again
            assertNotEquals(SW_OK, sw(c.build(good)), name + ": a proof that has worked once");
        }
    }

    private static byte[] flip(byte[] b) {
        byte[] c = b.clone();
        c[c.length - 1] ^= 1;
        return c;
    }

    @Test
    @DisplayName("A wrong proof costs no PIN tries, even sent five times by a terminal with the PIN, and does not end its session")
    void testWrongProofsCostNothing() {
        ready();
        for (int i = 0; i < 5; i++) {
            byte[] n = nonceBytes();
            assertEquals(SW_OWNER_PROOF, sw(changePinCommand(ownerProof(L_PIN, OTHER_OWNER, n, NEW_PIN), NEW_PIN)), "a wrong proof, " + i);
        }
        assertEquals(3, info()[9], "the PIN's tries are as they were");
        assertEquals(1, info()[7], "and the card is not blocked");
        assertEquals(SW_OK, load(buildProof(KEYSET, 8, 1)).getSW(), "the session still stands");
        reselect();
        assertEquals(SW_OK, verify(TEST_PIN));
        assertEquals(0x63C2, verify(NEW_PIN), "the PIN never changed");
    }

    @Test
    @DisplayName("CHANGE_PIN: the old PIN stops working, the session ends, a PIN's length is the PIN's, and it works with no PIN and any funds")
    void testChangePin() {
        ready();
        assertEquals(SW_OK, load(buildProof(KEYSET, 16, 1)).getSW());
        assertEquals(SW_OK, changePin(NEW_PIN), "a funded card, the old PIN verified");
        assertEquals(SW_SECURITY_NOT_SATIS, spend(0).getSW(), "the session that verified the old PIN is over");
        assertEquals(0x63C2, verify(TEST_PIN));
        assertEquals(SW_OK, verify(NEW_PIN));
        reselect();
        assertEquals(SW_OK, changePin(TEST_PIN), "with no PIN verified in this tap");
        assertEquals(SW_OK, verify(TEST_PIN));
        byte[] n = nonceBytes();
        assertEquals(SW_WRONG_LENGTH, sw(changePinCommand(ownerProof(L_PIN, OWNER, n, new byte[] { 1, 2, 3 }), new byte[] { 1, 2, 3 })));
        n = nonceBytes();
        assertEquals(SW_WRONG_LENGTH, sw(changePinCommand(ownerProof(L_PIN, OWNER, n, new byte[9]), new byte[9])));
        assertEquals(SW_OK, verify(TEST_PIN), "the PIN is the one it was");
    }

    @Test
    @DisplayName("CHANGE_PIN unblocks a blocked card: with the owner's proof, no old PIN, and a funded card")
    void testChangePinUnblocks() {
        ready();
        assertEquals(SW_OK, load(buildProof(KEYSET, 16, 1)).getSW());
        reselect();
        assertEquals(0x63C2, verify(WRONG_PIN));
        assertEquals(0x63C1, verify(WRONG_PIN));
        assertEquals(SW_PIN_BLOCKED, verify(WRONG_PIN));
        assertEquals(2, info()[7]);
        assertEquals(SW_PIN_BLOCKED, verify(TEST_PIN));
        assertEquals(SW_SECURITY_NOT_SATIS, spend(0).getSW(), "blocked: nothing spends");
        reselect();
        assertEquals(SW_OK, changePin(NEW_PIN), "the owner's phone, which does not know the PIN");
        assertEquals(1, info()[7], "set, not blocked");
        assertEquals(3, info()[9], "the tries are back");
        assertEquals(SW_OK, verify(NEW_PIN));
        assertEquals(SW_OK, spend(0).getSW(), "and the card pays again");
        // and a card whose PIN somebody forgot is the same
        reselect();
        assertEquals(SW_OK, changePin(TEST_PIN));
        assertEquals(SW_OK, verify(TEST_PIN));
    }

    @Test
    @DisplayName("A card with no owner refuses everything that needs one with 6A90, and the PIN gate comes first for LOCK_CARD")
    void testNoOwnerRefusesWhatNeedsOne() {
        assertEquals(SW_OK, setPin(TEST_PIN));
        assertEquals(SW_OK, verify(TEST_PIN));
        assertEquals(SW_OK, setCard(MINT, REFUND));
        assertEquals(SW_NO_OWNER, nonce().getSW());
        assertEquals(SW_NO_OWNER, sw(changePinCommand(new byte[8], NEW_PIN)));
        assertEquals(SW_NO_OWNER, sw(setLimitCommand(new byte[8], 100)));
        assertEquals(SW_NO_OWNER, sw(allowLoadCommand(new byte[8])));
        assertEquals(SW_NO_OWNER, sw(lockCommand(new byte[8])));
        assertEquals(3, info()[9]);
        assertEquals(0, info()[10], "not locked");
        assertEquals(0, limit());
        assertEquals(SW_NO_OWNER, load(buildProof(KEYSET, 16, 1)).getSW(), "and no money can go on");
        assertEquals(0, balance());
    }

    // =========================================================================
    // An owned card is not open, empty or not (A1); no money without an owner (A2); the owner adds funds with no PIN (A3)
    // =========================================================================

    /**
     * The open commands, as an attacker with nothing but the PIN sends them:
     * each is refused on a card that has an owner, and nothing it would have
     * set is set.
     */
    private void assertTheOpenCommandsAreRefused(String when) {
        byte[] recordBefore = cardRecord();
        byte[] infoBefore = info();
        Key attacker = new Key();
        assertEquals(SW_OWNER_PROOF, setPin(NEW_PIN), "SET_PIN " + when);
        assertEquals(SW_OWNER_PROOF, setOwner(attacker), "SET_OWNER, the open form " + when);
        assertNotEquals(SW_OK, sw(new CommandAPDU(CLA, INS_SET_OWNER, 0, 0, ownerData(new byte[8], attacker.pub))), "SET_OWNER, with a proof of nothing " + when);
        assertEquals(SW_OWNER_PROOF, setCardOpen("https://attacker.example.com", NO_REFUND, attacker), "SET_CARD, the open form " + when);
        // the PIN's own gate comes first: refused for want of the PIN, or, with it, for want of the owner
        int pinForm = sw(setLimitByPinCommand(0));
        assertTrue(pinForm == SW_OWNER_PROOF || pinForm == SW_SECURITY_NOT_SATIS, "SET_LIMIT, the PIN form " + when + ": " + Integer.toHexString(pinForm));
        pinForm = sw(setLimitByPinCommand(4294967295L));
        assertTrue(pinForm == SW_OWNER_PROOF || pinForm == SW_SECURITY_NOT_SATIS, "and with a limit " + when + ": " + Integer.toHexString(pinForm));
        assertArrayEquals(recordBefore, cardRecord(), "the record is as it was " + when);
        assertArrayEquals(infoBefore, info(), "and so is everything the card says about itself " + when);
    }

    @Test
    @DisplayName("A1: a card with an owner refuses SET_PIN, SET_OWNER, SET_CARD and the PIN form of SET_LIMIT without the owner's proof, even when it is empty, funded, blocked or has the PIN verified")
    void testAnOwnedCardIsNotOpen() {
        ready();
        assertEquals(0, info()[3], "empty");
        assertTheOpenCommandsAreRefused("on an empty card, the PIN verified");
        reselect();
        assertTheOpenCommandsAreRefused("on an empty card, the PIN not verified");
        // SET_LIMIT's PIN form is gated by the PIN first; with it verified it is the owner it wants
        assertEquals(SW_OK, verify(TEST_PIN));
        assertEquals(SW_OK, load(buildProof(KEYSET, 16, 1)).getSW());
        assertTheOpenCommandsAreRefused("on a funded card");
        assertEquals(SW_OK, spend(0).getSW());
        assertEquals(SW_OK, clearSpent());
        assertTheOpenCommandsAreRefused("on a card a terminal has just emptied");
        reselect();
        verify(WRONG_PIN); verify(WRONG_PIN); verify(WRONG_PIN);
        assertEquals(2, info()[7]);
        assertTheOpenCommandsAreRefused("on a blocked card");
        simulator.reset();
        reselect();
        assertTheOpenCommandsAreRefused("after a reset");
        // and what the owner can do is as it was
        assertEquals(SW_OK, changePin(NEW_PIN));
        assertEquals(SW_OK, verify(NEW_PIN));
    }

    @Test
    @DisplayName("A1: a card with no owner accepts them while it is empty, and refuses them 6A8D with one unspent piece")
    void testAnOpenCardTakesThemWhileEmpty() throws Exception {
        assertEquals(SW_OK, setPin(TEST_PIN));
        assertEquals(SW_OK, verify(TEST_PIN));
        assertEquals(SW_OK, setCard(MINT, REFUND));
        assertEquals(SW_OK, setCard("https://other.example.com", NO_REFUND), "a record can be written again, with no proof");
        assertEquals(SW_NO_TIME, sw(setLimitByPinCommand(500)), "a limit needs a time to start from");
        assertEquals(SW_OK, sw(setLimitByPinCommand(0)), "none does not");
        assertEquals(SW_OK, setTime(T0));
        assertEquals(SW_OK, sw(setLimitByPinCommand(500)));
        assertEquals(500, limit());
        assertEquals(T0, windowStart());
        assertEquals(SW_OK, setPin(NEW_PIN), "and the PIN replaced");
        assertEquals(SW_OK, setOwner(OWNER), "and the owner given");
        // from there it is the owner's: the same four are refused
        assertEquals(SW_OWNER_PROOF, setPin(TEST_PIN));
        assertEquals(SW_OWNER_PROOF, setOwner(OTHER_OWNER));

        // one unspent piece: not on any command's account, since no command can put one on an open card,
        // but a card could hold one (a card that was loaded, then lost its owner some other way)
        simulator = freshCard();
        assertEquals(SW_OK, setPin(TEST_PIN));
        assertEquals(SW_OK, verify(TEST_PIN));
        assertEquals(SW_OK, setCard(MINT, REFUND));
        assertEquals(SW_OK, setTime(T0));
        byte[] storage = field("proofStorage");
        System.arraycopy(buildProof(KEYSET, 10, 3), 0, storage, 1, 81);
        storage[0] = 1;
        assertEquals(SW_CARD_IN_USE, setPin(NEW_PIN), "SET_PIN");
        assertEquals(SW_CARD_IN_USE, setOwner(OWNER), "SET_OWNER");
        assertEquals(SW_CARD_IN_USE, setCard("https://other.example.com", NO_REFUND), "SET_CARD");
        assertEquals(SW_CARD_IN_USE, sw(setLimitByPinCommand(0)), "SET_LIMIT, the PIN form");
        assertEquals(0, info()[16], "no owner was given");
        storage[0] = 2;   // spent, and not yet cleared: nothing unspent
        assertEquals(SW_OK, setPin(NEW_PIN), "a spent slot is not an unspent piece");
        assertEquals(SW_OK, verify(NEW_PIN));
        assertEquals(SW_OK, setOwner(OWNER));
    }

    /** A card whose terminal has emptied it, as far as the card can know. */
    @Test
    @DisplayName("A1: the takeover. A terminal with the PIN empties a card that fits within its limit, then tries to make the card its own: it fails at every step, in any order")
    void testTheTakeoverFails() {
        for (long limit : new long[] { 0, 100 }) {
            simulator = freshCard();
            readyWithLimit(limit);
            assertEquals(SW_OK, load(buildProof(KEYSET, 100, 1)).getSW(), "a piece within the day's limit");
            // the terminal: the PIN, a spend (what the day allows), and the used slot cleared: the card is empty
            reselect();
            assertEquals(SW_OK, verify(TEST_PIN));
            assertEquals(SW_OK, spend(0).getSW());
            assertEquals(SW_OK, clearSpent());
            assertEquals(0, info()[3], "the card is empty: it holds nothing a terminal could lose");
            byte[] recordBefore = cardRecord();
            byte[] infoBefore = info();

            // it now tries to take the card over, in each order: its own PIN, its own owner, its own record and time key, its own limit
            Key attacker = new Key();
            Key attackerSigner = new Key();
            assertEquals(SW_OWNER_PROOF, setPin(NEW_PIN), "its own PIN");
            assertEquals(SW_OWNER_PROOF, setOwner(attacker), "its own owner");
            assertEquals(SW_OWNER_PROOF, setCardOpen("https://attacker.example.com", NO_REFUND, attackerSigner), "its own record and time key");
            assertEquals(SW_OWNER_PROOF, sw(setLimitByPinCommand(0)), "its own limit");
            // and with a proof by its own key, which the card has no reason to take
            byte[] n = nonceBytes();
            assertEquals(SW_OWNER_PROOF, sw(setOwnerCommand(ownerProof(L_OWNER, attacker, n, attacker.pub), attacker)), "its own owner, signed by itself");
            byte[] data = record("https://attacker.example.com", NO_REFUND, attackerSigner.pub);
            n = nonceBytes();
            assertEquals(SW_OWNER_PROOF, sw(setCardCommand(ownerProof(L_CARD, attacker, n, data), data)), "its own record, signed by itself");
            n = nonceBytes();
            assertEquals(SW_OWNER_PROOF, sw(changePinCommand(ownerProof(L_PIN, attacker, n, NEW_PIN), NEW_PIN)), "its own PIN, signed by itself");
            n = nonceBytes();
            assertEquals(SW_OWNER_PROOF, sw(setLimitCommand(ownerProof(L_LIMIT, attacker, n, u32(0)), 0)), "its own limit, signed by itself");
            // a time signed by its own signer, to move the clock
            assertEquals(SW_NOT_THE_TIME, sw(setTimeCommand(T0 + 5 * DAY, timeSignature(attackerSigner, T0 + 5 * DAY))), "its own time");
            assertEquals(SW_NOT_THE_TIME, sw(setTimeCommand(T0 + 5 * DAY, timeSignature(OTHER_SIGNER, T0 + 5 * DAY))), "or another signer's");
            // the same after the tap, after a reset
            simulator.reset();
            reselect();
            assertEquals(SW_OK, verify(TEST_PIN));
            assertEquals(SW_OWNER_PROOF, setPin(NEW_PIN));
            assertEquals(SW_OWNER_PROOF, setOwner(attacker));
            assertEquals(SW_OWNER_PROOF, setCardOpen("https://attacker.example.com", NO_REFUND, attackerSigner));

            assertArrayEquals(recordBefore, cardRecord(), "the record, its time key and its limit are as they were");
            assertArrayEquals(Arrays.copyOf(infoBefore, 29), Arrays.copyOf(info(), 29), "and the card says what it said");
            assertEquals(1, info()[29], "but for the note that it paid, which the tap after a payment has (8.2)");
            // the card is still the owner's and the holder's: their PIN, their owner's proof
            reselect();
            assertEquals(SW_OK, verify(TEST_PIN), "the PIN is the holder's");
            assertEquals(SW_OK, setLimit(1234), "and the owner's proof still works");
            assertEquals(1234, limit());
            // all the terminal can do with an emptied card is put pieces on it, which the mint will refuse
            assertEquals(SW_OK, load(buildProof(KEYSET, 1, 9)).getSW());
        }
    }

    @Test
    @DisplayName("A2: a card with no owner cannot be loaded, whatever else is done to it; with an owner it can")
    void testNoLoadWithoutAnOwner() {
        assertEquals(SW_OK, setPin(TEST_PIN));
        assertEquals(SW_OK, verify(TEST_PIN));
        assertEquals(SW_OK, setCard(MINT, REFUND));
        assertEquals(SW_OK, setTime(T0));
        assertEquals(SW_OK, sw(setLimitByPinCommand(1_000_000)));
        assertEquals(SW_NO_OWNER, load(buildProof(KEYSET, 16, 1)).getSW(), "PIN verified, record, time, a limit: and still no owner");
        assertEquals(SW_NO_OWNER, load(buildProof(KEYSET, 16, 1, 1900000000L)).getSW());
        assertEquals(0, info()[3], "nothing is on the card");
        assertEquals(0, balance());
        assertEquals(SW_OK, setOwner(OWNER));
        assertEquals(SW_OK, load(buildProof(KEYSET, 16, 1)).getSW(), "with an owner it can");
    }

    @Test
    @DisplayName("A piece whose nonce is already on an unspent slot is refused, so a terminal with only the PIN cannot load a copy with a false amount, spend the copy for a small charge and hold a signature for the real piece")
    void testACopyOfAPieceOnTheCardIsRefused() throws Exception {
        readyWithLimit(250);
        // the owner's phone has put one real piece worth 1000 on the card, and the day's limit is 250
        byte[] real = buildProof(KEYSET, 1000, 7);
        assertEquals(SW_OK, load(real).getSW());
        assertEquals(SW_OVER_LIMIT, spend(0).getSW(), "honestly, the piece is over the day's limit and cannot be spent");
        byte[] infoBefore = info();
        byte[] slotsBefore = transmit(new CommandAPDU(CLA, INS_GET_SLOT_STATUS, 0, 0, 64)).getData();
        // the terminal reads slot 0 and writes the same piece back, stating an amount of 1: the amount is not in what the card signs
        byte[] slot0 = slot(0);
        byte[] copy = Arrays.copyOfRange(slot0, 1, 82);
        putUint32(copy, 8, 1);
        assertEquals(SW_PIECE_ON_CARD, load(copy).getSW(), "the copy is refused");
        assertEquals(SW_SLOT_EMPTY, spend(1).getSW(), "so there is no second slot to spend");
        assertEquals(SW_OVER_LIMIT, spend(0).getSW(), "and the real piece is still over the limit");
        // whatever else is stated about the same nonce, it is the same piece
        putUint32(copy, 8, 1000);
        assertEquals(SW_PIECE_ON_CARD, load(copy).getSW(), "the piece itself, loaded twice");
        byte[] dated = buildProof(KEYSET, 1, 7, 1900000000L);
        assertEquals(SW_PIECE_ON_CARD, load(dated).getSW(), "the same nonce with a date and another amount");
        byte[] otherKeyset = buildProof("0123456789abcdef", 1, 7);
        assertEquals(SW_PIECE_ON_CARD, load(otherKeyset).getSW(), "and of another keyset");
        assertArrayEquals(infoBefore, info(), "nothing was counted, burned or written");
        assertArrayEquals(slotsBefore, transmit(new CommandAPDU(CLA, INS_GET_SLOT_STATUS, 0, 0, 64)).getData());
        assertArrayEquals(slot0, slot(0), "and the real piece is as it was");
        assertEquals(0, spentToday());
        assertEquals(1000, balance());
    }

    @Test
    @DisplayName("A piece whose nonce is on a spent slot is refused as well, and loads again once CLEAR_SPENT has freed that slot")
    void testAPieceOnASpentSlotWaitsForClearSpent() throws Exception {
        readyWithLimit(250);
        byte[] piece = buildProof(KEYSET, 100, 7);
        assertEquals(SW_OK, load(piece).getSW());
        ResponseAPDU first = spend(0);
        assertEquals(SW_OK, first.getSW());
        byte[] spentSlot = slot(0);
        assertTrue(signedFor(first.getData(), spentSlot, REFUND));
        assertEquals(100, spentToday());
        // spent, the slot still holds the piece: a copy with another amount is not written beside it
        byte[] copy = buildProof(KEYSET, 1, 7);
        assertEquals(SW_PIECE_ON_CARD, load(copy).getSW(), "a copy of a spent piece");
        assertEquals(SW_PIECE_ON_CARD, load(piece).getSW(), "nor the piece itself");
        assertEquals(SW_SLOT_EMPTY, spend(1).getSW());
        assertEquals(100, spentToday());
        // freed, the slot holds nothing, and the piece may be loaded again: its signature may have been lost with the tap
        assertEquals(SW_OK, clearSpent());
        assertEquals(SW_SLOT_EMPTY, sw(new CommandAPDU(CLA, INS_GET_PROOF, 0, 0, 256)));
        ResponseAPDU again = load(piece);
        assertEquals(SW_OK, again.getSW(), "a re-load after CLEAR_SPENT is allowed");
        assertEquals(0, again.getData()[0], "into the freed slot");
        assertEquals(100, balance());
        ResponseAPDU second = spend(0);
        assertEquals(SW_OK, second.getSW(), "and it can be spent again, and is charged as the piece is worth");
        assertTrue(signedFor(second.getData(), slot(0), REFUND));
        assertEquals(200, spentToday());
        // and once it is back on the card, it is refused again
        assertEquals(SW_PIECE_ON_CARD, load(copy).getSW());
    }

    @Test
    @DisplayName("Only the whole nonce makes a piece the same: one that differs in its first byte or its last is another piece")
    void testNearlyTheSameNonceIsAnotherPiece() {
        ready();
        byte[] piece = buildProof(KEYSET, 16, 7);
        assertEquals(SW_OK, load(piece).getSW());
        byte[] first = piece.clone();
        first[12] ^= 1;
        byte[] last = piece.clone();
        last[43] ^= 1;
        assertEquals(SW_OK, load(first).getSW(), "the first byte of the nonce differs");
        assertEquals(SW_OK, load(last).getSW(), "the last byte differs");
        assertEquals(SW_PIECE_ON_CARD, load(first).getSW());
        assertEquals(SW_PIECE_ON_CARD, load(last).getSW());
        assertEquals(48, balance());
    }

    @Test
    @DisplayName("The owner's grant gets no piece past the refusal: a copy is refused without the PIN as with it")
    void testACopyIsRefusedUnderTheOwnersGrantToo() {
        readyWithLimit(1000);
        assertEquals(SW_OK, load(buildProof(KEYSET, 16, 7)).getSW());
        reselect();
        assertEquals(SW_OK, allowLoad());
        assertEquals(SW_PIECE_ON_CARD, load(buildProof(KEYSET, 1, 7)).getSW());
        assertEquals(SW_OK, load(buildProof(KEYSET, 1, 9)).getSW(), "another piece goes in under it as before");
    }

    @Test
    @DisplayName("The owner replaces the owner or the record only on an empty card, proof or no proof; the PIN, the limit and the grant it changes on a funded one")
    void testTheOwnerNeedsAnEmptyCardForTheOwnerAndTheRecord() {
        ready();
        assertEquals(SW_OK, load(buildProof(KEYSET, 16, 1)).getSW());
        byte[] n = nonceBytes();
        assertEquals(SW_CARD_IN_USE, sw(setOwnerCommand(ownerProof(L_OWNER, OWNER, n, OTHER_OWNER.pub), OTHER_OWNER)), "a new owner, with the old one's proof, over a piece");
        assertEquals(SW_OK, setLimit(0), "but the limit may be changed under it");
        assertEquals(SW_OK, changePin(NEW_PIN), "and the PIN");
        assertEquals(SW_OK, allowLoad(), "and loading allowed");
        assertEquals(SW_OK, setTime(T0 + 5));
        assertEquals(SW_OK, verify(NEW_PIN));
        assertEquals(SW_OK, spend(0).getSW());
        assertEquals(SW_OK, clearSpent());
        n = nonceBytes();
        assertEquals(SW_OK, sw(setOwnerCommand(ownerProof(L_OWNER, OWNER, n, OTHER_OWNER.pub), OTHER_OWNER)), "emptied, it may");
    }

    @Test
    @DisplayName("A3: the owner's grant lets LOAD_PROOF and CLEAR_SPENT through with no PIN, for the rest of that tap, and nothing else; a new SELECT ends it")
    void testAllowLoad() {
        readyWithLimit(100000);
        assertEquals(SW_OK, load(buildProof(KEYSET, 16, 1)).getSW());
        assertEquals(SW_OK, spend(0).getSW());
        reselect();
        reselect();     // the first tap after a payment may load (8.2); this is the one after that
        // no PIN in this tap
        assertEquals(SW_SECURITY_NOT_SATIS, load(buildProof(KEYSET, 8, 2)).getSW());
        assertEquals(SW_SECURITY_NOT_SATIS, clearSpent());
        assertEquals(SW_OK, allowLoad());
        assertEquals(SW_OK, load(buildProof(KEYSET, 8, 2)).getSW(), "a load, with no PIN");
        assertEquals(SW_OK, load(buildProof(KEYSET, 4, 3, 1900000000L)).getSW());
        assertEquals(SW_OK, clearSpent(), "and the used slots cleared");
        assertEquals(12, balance());
        // and nothing else the PIN opens
        assertEquals(SW_SECURITY_NOT_SATIS, spend(0).getSW(), "not a spend");
        assertEquals(SW_SECURITY_NOT_SATIS, spend(1).getSW());
        assertEquals(SW_OWNER_PROOF, setPin(NEW_PIN), "not the PIN");
        assertEquals(SW_OWNER_PROOF, setCardOpen("https://x.example.com", NO_REFUND, SIGNER), "not the record");
        assertEquals(SW_SECURITY_NOT_SATIS, sw(setLimitByPinCommand(1)), "not the limit by PIN");
        assertEquals(SW_SECURITY_NOT_SATIS, lock(), "not the lock");
        assertEquals(0, info()[10]);
        assertEquals(12, balance(), "nothing was spent");
        // the grant is for this tap
        reselect();
        assertEquals(SW_SECURITY_NOT_SATIS, load(buildProof(KEYSET, 8, 4)).getSW(), "a new SELECT ends it");
        assertEquals(SW_SECURITY_NOT_SATIS, clearSpent());
        assertEquals(SW_OK, allowLoad());
        assertEquals(SW_OK, load(buildProof(KEYSET, 8, 4)).getSW());
        simulator.reset();
        reselect();
        assertEquals(SW_SECURITY_NOT_SATIS, load(buildProof(KEYSET, 8, 5)).getSW(), "and so does a reset");
        // a wrong PIN in the same tap does not take it back, and does not give a spend
        assertEquals(SW_OK, allowLoad());
        assertEquals(0x63C2, verify(WRONG_PIN));
        assertEquals(SW_OK, load(buildProof(KEYSET, 8, 5)).getSW());
        assertEquals(SW_SECURITY_NOT_SATIS, spend(2).getSW());
    }

    @Test
    @DisplayName("8.2: the tap after a payment may put pieces on with no PIN, and free the burned places, and nothing else; the tap after that may not")
    void testChangeTapNeedsNoPin() {
        readyWithLimit(100000);
        assertEquals(SW_OK, load(buildProof(KEYSET, 16, 1)).getSW());
        assertEquals(SW_OK, load(buildProof(KEYSET, 8, 2)).getSW());
        assertEquals(0, info()[29], "nothing is due before a payment");
        assertEquals(SW_OK, spend(0).getSW());
        assertEquals(0, info()[29], "the note is for the next tap, not the one that paid");
        reselect();
        assertEquals(1, info()[29], "the tap after a payment says it may load");
        assertEquals(SW_OK, load(buildProof(KEYSET, 4, 3)).getSW(), "the change, with no PIN");
        assertEquals(SW_OK, clearSpent(), "and the place the payment burned is freed");
        assertEquals(12, balance());
        // and nothing else the PIN opens
        assertEquals(SW_SECURITY_NOT_SATIS, spend(1).getSW(), "not a spend");
        assertEquals(SW_OWNER_PROOF, setPin(NEW_PIN), "not the PIN");
        assertEquals(SW_OWNER_PROOF, setCardOpen("https://x.example.com", NO_REFUND, SIGNER), "not the record");
        assertEquals(SW_SECURITY_NOT_SATIS, sw(setLimitByPinCommand(1)), "not the limit by PIN");
        assertEquals(SW_SECURITY_NOT_SATIS, lock(), "not the lock");
        assertEquals(12, balance(), "nothing was spent");
        reselect();
        assertEquals(0, info()[29], "the tap after that has no grant");
        assertEquals(SW_SECURITY_NOT_SATIS, load(buildProof(KEYSET, 4, 4)).getSW(), "and loads nothing with no PIN");
        assertEquals(SW_SECURITY_NOT_SATIS, clearSpent());
    }

    @Test
    @DisplayName("8.2: the note outlives the card leaving the field, is used up by whichever tap comes next, even one that only reads, and a payment in the change tap makes a new one")
    void testChangeNoteIsForOneTap() {
        readyWithLimit(100000);
        assertEquals(SW_OK, load(buildProof(KEYSET, 16, 1)).getSW());
        assertEquals(SW_OK, load(buildProof(KEYSET, 8, 2)).getSW());
        assertEquals(SW_OK, load(buildProof(KEYSET, 4, 3)).getSW());
        assertEquals(SW_OK, spend(0).getSW());
        simulator.reset();      // the card left the field: the note is in permanent memory
        reselect();
        assertEquals(1, info()[29], "the next tap, after the card has been away, has the grant");
        reselect();             // and used it up by only reading
        assertEquals(0, info()[29]);
        assertEquals(SW_SECURITY_NOT_SATIS, load(buildProof(KEYSET, 4, 4)).getSW(), "a tap that only read the card used the note up");
        assertEquals(SW_OK, verify(TEST_PIN));
        assertEquals(SW_OK, spend(1).getSW(), "a payment, with the PIN");
        reselect();
        assertEquals(1, info()[29]);
        assertEquals(SW_OK, load(buildProof(KEYSET, 4, 4)).getSW(), "its change, with no PIN");
        assertEquals(SW_OK, verify(TEST_PIN));
        assertEquals(SW_OK, spend(2).getSW(), "a payment in the change tap, with the PIN");
        reselect();
        assertEquals(SW_OK, load(buildProof(KEYSET, 2, 5)).getSW(), "makes a note for the tap after it");
        reselect();
        assertEquals(SW_SECURITY_NOT_SATIS, load(buildProof(KEYSET, 2, 6)).getSW());
    }

    @Test
    @DisplayName("8.2: the note opens nothing on a locked card or under a blocked PIN")
    void testChangeNoteOpensNothingLockedOrBlocked() {
        ready();
        assertEquals(SW_OK, load(buildProof(KEYSET, 16, 1)).getSW());
        assertEquals(SW_OK, load(buildProof(KEYSET, 8, 2)).getSW());
        assertEquals(SW_OK, spend(0).getSW());
        reselect();
        assertEquals(1, info()[29]);
        assertEquals(0x63C2, verify(WRONG_PIN));
        assertEquals(0x63C1, verify(WRONG_PIN));
        assertEquals(SW_PIN_BLOCKED, verify(WRONG_PIN));
        assertEquals(SW_SECURITY_NOT_SATIS, load(buildProof(KEYSET, 4, 3)).getSW(), "a blocked PIN waives nothing");
        // unblocked by the owner, and paid from again, then locked in the paying tap
        byte[] n = nonceBytes();
        assertEquals(SW_OK, sw(changePinCommand(ownerProof(L_PIN, OWNER, n, TEST_PIN), TEST_PIN)));
        assertEquals(SW_OK, verify(TEST_PIN));
        assertEquals(SW_OK, spend(1).getSW());
        assertEquals(SW_OK, lock());
        reselect();
        assertEquals(1, info()[29], "the note is there");
        assertEquals(SW_NOT_ALLOWED, load(buildProof(KEYSET, 4, 3)).getSW(), "and a locked card takes no load for it");
        assertEquals(SW_NOT_ALLOWED, clearSpent());
    }

    @Test
    @DisplayName("A3: the grant needs the owner's proof, is refused on a card with no owner and on a locked card, and does not load onto a blocked PIN")
    void testAllowLoadIsTheOwners() {
        ready();
        reselect();
        assertEquals(SW_OWNER_PROOF, sw(allowLoadCommand(new byte[8])), "no nonce asked for");
        assertEquals(SW_SECURITY_NOT_SATIS, load(buildProof(KEYSET, 8, 1)).getSW(), "so no grant");
        byte[] n = nonceBytes();
        assertEquals(SW_OWNER_PROOF, sw(allowLoadCommand(ownerProof(L_PIN, OWNER, n, new byte[0]))), "a proof for another command");
        assertEquals(SW_SECURITY_NOT_SATIS, load(buildProof(KEYSET, 8, 1)).getSW());
        // a blocked PIN waives nothing: the owner unblocks it first
        verify(WRONG_PIN); verify(WRONG_PIN); verify(WRONG_PIN);
        assertEquals(2, info()[7]);
        assertEquals(SW_OK, allowLoad());
        assertEquals(SW_SECURITY_NOT_SATIS, load(buildProof(KEYSET, 8, 1)).getSW(), "a blocked card is unblocked before it is loaded");
        assertEquals(SW_OK, changePin(NEW_PIN));
        assertEquals(SW_OK, allowLoad());
        assertEquals(SW_OK, load(buildProof(KEYSET, 8, 1)).getSW());
        // locked
        assertEquals(SW_OK, verify(NEW_PIN));
        assertEquals(SW_OK, lock());
        reselect();
        assertEquals(SW_NOT_ALLOWED, sw(allowLoadCommand(ownerProof(L_LOAD, OWNER, nonceBytes(), new byte[0]))), "a locked card takes no writes");
    }

    @Test
    @DisplayName("A3: loading with the grant still needs a time and a record, as with the PIN")
    void testAllowLoadStillNeedsATime() throws Exception {
        ready();
        reselect();
        assertEquals(SW_OK, allowLoad());
        putRecord(CashuApplet.CARD_NOW_OFFSET, new byte[4]);   // the clock, as a new time key would have cleared it
        assertEquals(SW_NO_TIME, load(buildProof(KEYSET, 8, 1)).getSW());
        assertEquals(SW_OK, setTime(T0));
        assertEquals(SW_OK, load(buildProof(KEYSET, 8, 1)).getSW());
    }

    // =========================================================================
    // The time
    // =========================================================================

    @Test
    @DisplayName("SET_TIME: a signature by another key is 6A93 and changes nothing; an older or repeated time is 9000 and changes nothing; a newer one is taken; the answer is the card's clock")
    void testSetTime() {
        ready();
        assertEquals(T0, now(), "told at set-up");
        ResponseAPDU r = transmit(setTimeCommand(T0 + 10, timeSignature(OTHER_SIGNER, T0 + 10)));
        assertEquals(SW_NOT_THE_TIME, r.getSW(), "another signer's");
        assertEquals(0, r.getData().length, "and it says nothing of the clock");
        assertEquals(SW_NOT_THE_TIME, sw(setTimeCommand(T0 + 10, timeSignature(OWNER, T0 + 10))), "the owner is not the time signer");
        assertEquals(SW_NOT_THE_TIME, sw(setTimeCommand(T0 + 10, timeSignature(SIGNER, T0 + 11))), "a signature for another time");
        assertEquals(SW_NOT_THE_TIME, sw(setTimeCommand(T0 + 10, ownerProof("FoxyCard/set-limit", SIGNER, new byte[16], u32(T0 + 10)))), "or under another label");
        assertEquals(SW_NOT_THE_TIME, sw(setTimeCommand(T0 + 10, new byte[] { 0x30, 0x06, 0x02, 0x01, 0x01, 0x02, 0x01, 0x01 })), "or of nothing");
        assertEquals(T0, now(), "none of them moved it");

        ResponseAPDU older = transmit(setTimeCommand(T0 - 1000, timeSignature(SIGNER, T0 - 1000)));
        assertEquals(SW_OK, older.getSW(), "an old time is harmless");
        assertEquals(T0, readUint32(older.getData(), 0), "and is answered with the card's own clock");
        assertEquals(T0, now());
        ResponseAPDU same = transmit(setTimeCommand(T0, timeSignature(SIGNER, T0)));
        assertEquals(SW_OK, same.getSW());
        assertEquals(T0, readUint32(same.getData(), 0));
        ResponseAPDU newer = transmit(setTimeCommand(T0 + 3600, timeSignature(SIGNER, T0 + 3600)));
        assertEquals(SW_OK, newer.getSW());
        assertEquals(T0 + 3600, readUint32(newer.getData(), 0));
        assertEquals(T0 + 3600, now());
        // 2^32 - 1 is a time like any other, and the greatest
        assertEquals(SW_OK, setTime(4294967295L));
        assertEquals(4294967295L, now());
        assertEquals(SW_OK, setTime(T0 + 7200));
        assertEquals(4294967295L, now(), "and nothing is later than it: the clock never goes back");
    }

    @Test
    @DisplayName("SET_TIME needs no PIN, no owner and no state: a card that is blocked, locked or has nothing set up takes the time")
    void testSetTimeNeedsNothing() {
        ready();
        reselect();
        assertEquals(SW_OK, setTime(T0 + 1), "no PIN verified");
        verify(WRONG_PIN); verify(WRONG_PIN); verify(WRONG_PIN);
        assertEquals(2, info()[7]);
        assertEquals(SW_OK, setTime(T0 + 2), "a blocked card");
        assertEquals(SW_OK, changePin(NEW_PIN));
        assertEquals(SW_OK, verify(NEW_PIN));
        assertEquals(SW_OK, lock());
        assertEquals(1, info()[10]);
        assertEquals(SW_OK, setTime(T0 + 3), "a locked card");
        assertEquals(T0 + 3, now());
        // no signer with a clock: a card with no record has no time key to check against
        simulator = freshCard();
        assertEquals(SW_NO_CARD_RECORD, setTime(T0));
        assertEquals(SW_WRONG_LENGTH, sw(new CommandAPDU(CLA, INS_SET_TIME, 0, 0, new byte[4])));
        assertEquals(SW_WRONG_LENGTH, sw(new CommandAPDU(CLA, INS_SET_TIME, 0, 0, new byte[5])));
        ready();
        assertEquals(SW_WRONG_LENGTH, sw(new CommandAPDU(CLA, INS_SET_TIME, 0, 0, concat(u32(T0 + 9), new byte[] { 72 }, new byte[10]))), "a length that is not the signature's");
        assertEquals(SW_WRONG_LENGTH, sw(new CommandAPDU(CLA, INS_SET_TIME, 0, 0, concat(u32(T0 + 9), new byte[] { 0 }))), "no signature");
    }

    @Test
    @DisplayName("The clock survives SET_CARD with the same key, SET_OWNER, CHANGE_PIN, a reset, and an emptying and set-up again, so an old time offered to a card just set up again is ignored")
    void testNowOnlyMovesForward() {
        readyWithLimit(1000);
        assertEquals(SW_OK, setTime(T0 + 5 * DAY));
        assertEquals(SW_OK, load(buildProof(KEYSET, 10, 1)).getSW());
        assertEquals(SW_OK, spend(0).getSW());
        assertEquals(SW_OK, clearSpent());
        long before = now();
        long start = windowStart();
        assertEquals(T0 + 5 * DAY, before);
        assertEquals(before, start, "the day began at the clock");
        assertEquals(SW_OK, setCard("https://other.example.com", NO_REFUND), "SET_CARD with the time key it has");
        assertEquals(before, now());
        assertEquals(SW_OK, changePin(NEW_PIN));
        assertEquals(before, now());
        byte[] n = nonceBytes();
        assertEquals(SW_OK, sw(setOwnerCommand(ownerProof(L_OWNER, OWNER, n, OTHER_OWNER.pub), OTHER_OWNER)), "a new owner");
        assertEquals(before, now());
        simulator.reset();
        reselect();
        assertEquals(before, now(), "a reset");
        assertEquals(1000, limit(), "and the limit, and the window it began");
        assertEquals(start, windowStart());
        assertEquals(10, spentToday());
        // set up again, from the owner's side, and the card offered last week's time
        assertEquals(SW_OK, setTime(T0), "an old time: harmless");
        assertEquals(before, now());
        assertEquals(SW_OK, setTime(T0 - 7 * DAY));
        assertEquals(before, now(), "so last week's time offered to a card that has just been set up again is ignored");
    }

    @Test
    @DisplayName("A4: SET_CARD with a DIFFERENT time key, on an empty card, with the owner's proof, clears the clock and the window; the old signer's times are then refused and the new one's taken; the limit stays")
    void testAnotherTimeKeyClearsTheClock() {
        readyWithLimit(100);
        // one signer's fault: a time far in the future
        long farFuture = T0 + 3650 * DAY;
        assertEquals(SW_OK, setTime(farFuture));
        assertEquals(farFuture, now());
        assertEquals(SW_OK, load(buildProof(KEYSET, 100, 1)).getSW());
        assertEquals(SW_OK, spend(0).getSW(), "the day's limit");
        assertEquals(farFuture, windowStart(), "the window began at the clock, which is the fault");
        // the card's day is frozen: it is not a day later than the fault, whatever the real time is
        assertEquals(SW_OK, setTime(T0 + DAY + 5), "a good time, a day on");
        assertEquals(farFuture, now(), "is behind the fault, and ignored");
        assertEquals(SW_OK, load(buildProof(KEYSET, 100, 2)).getSW());
        assertEquals(SW_OVER_LIMIT, spend(1).getSW(), "and the window can not turn");

        // a funded card cannot be given another time key, with the proof or without
        assertEquals(SW_CARD_IN_USE, ownerSetCard(MINT, REFUND, OTHER_SIGNER), "with one unspent piece");
        assertEquals(farFuture, now());
        assertEquals(SW_OK, setLimit(0));
        assertEquals(SW_OK, spend(1).getSW());
        assertEquals(SW_OK, clearSpent());
        assertEquals(0, info()[3]);
        assertEquals(SW_OK, setLimit(100));
        assertEquals(farFuture, windowStart());

        // an empty card, the owner's proof: a different key
        assertEquals(SW_OWNER_PROOF, sw(setCardOpenCommand(record(MINT, REFUND, OTHER_SIGNER.pub))), "no proof");
        assertEquals(farFuture, now());
        byte[] data = record(MINT, REFUND, OTHER_SIGNER.pub);
        assertEquals(SW_OWNER_PROOF, sw(setCardCommand(ownerProof(L_CARD, OTHER_OWNER, nonceBytes(), data), data)), "another owner's proof");
        assertEquals(farFuture, now(), "nothing moved it");
        assertEquals(SW_OK, ownerSetCard(MINT, REFUND, OTHER_SIGNER));
        assertEquals(0, now(), "the clock is cleared");
        assertEquals(0, windowStart(), "and the window it was counted in");
        assertEquals(0, spentToday());
        assertEquals(100, limit(), "the limit stays");
        assertArrayEquals(OTHER_SIGNER.pub, Arrays.copyOfRange(cardRecord(), 40, 105));
        // the old signer's time is not the card's now, the new signer's is
        assertEquals(SW_NOT_THE_TIME, setTime(T0 + 2 * DAY), "the old signer");
        assertEquals(SW_OK, sw(setTimeCommand(T0 + 2 * DAY, timeSignature(OTHER_SIGNER, T0 + 2 * DAY))));
        assertEquals(T0 + 2 * DAY, now(), "back in time from the fault, and the card is as new");
        // loading needs the time again, and then the day is the real one
        assertEquals(SW_OK, load(buildProof(KEYSET, 100, 3)).getSW());
        assertEquals(SW_OK, spend(0).getSW(), "a spend under the limit, the window starting at the new time");
        assertEquals(T0 + 2 * DAY, windowStart());
        assertEquals(100, spentToday());
    }

    @Test
    @DisplayName("A4: the clock is cleared by nothing else: not the same key, not a refused SET_CARD, not a bad time key, not the PIN form")
    void testNothingElseClearsTheClock() {
        ready();
        assertEquals(SW_OK, setTime(T0 + 99));
        assertEquals(SW_OK, ownerSetCard(MINT, REFUND, SIGNER), "the same key");
        assertEquals(T0 + 99, now());
        byte[] bad = OTHER_SIGNER.pub.clone(); bad[0] = 0x03;
        byte[] data = record(MINT, REFUND, bad);
        assertEquals(SW_WRONG_DATA, sw(setCardCommand(ownerProof(L_CARD, OWNER, nonceBytes(), data), data)), "a key that is not uncompressed");
        assertEquals(T0 + 99, now());
        assertEquals(SW_OWNER_PROOF, sw(setCardOpenCommand(record(MINT, REFUND, OTHER_SIGNER.pub))), "the PIN form on an owned card");
        assertEquals(T0 + 99, now());
        byte[] wrong = ownerProof(L_CARD, OWNER, nonceBytes(), record(MINT, REFUND, OTHER_SIGNER.pub));
        assertEquals(SW_OWNER_PROOF, sw(setCardCommand(wrong, record(MINT, REFUND, new Key().pub))), "a proof for another record");
        assertEquals(T0 + 99, now());
        assertEquals(SW_OK, load(buildProof(KEYSET, 5, 1)).getSW());
        assertEquals(SW_CARD_IN_USE, ownerSetCard(MINT, REFUND, OTHER_SIGNER), "a funded card");
        assertEquals(T0 + 99, now());
    }

    // =========================================================================
    // The day
    // =========================================================================

    @Test
    @DisplayName("SET_LIMIT begins a window at the card's clock with nothing spent; it needs a time for a limit and none for no limit; a limit survives a new SELECT, a reset, VERIFY_PIN")
    void testSetLimitBeginsAWindow() {
        assertEquals(SW_OK, setPin(TEST_PIN));
        assertEquals(SW_OK, verify(TEST_PIN));
        assertEquals(SW_OK, setCard(MINT, REFUND));
        assertEquals(SW_OK, setOwner(OWNER));
        assertEquals(SW_NO_TIME, setLimit(500), "never told the time");
        assertEquals(0, limit());
        assertEquals(SW_OK, setLimit(0), "no limit needs none");
        assertEquals(SW_OK, setTime(T0));
        assertEquals(SW_OK, setLimit(500));
        assertEquals(500, limit());
        assertEquals(T0, windowStart());
        assertEquals(0, spentToday());
        assertEquals(SW_OK, setTime(T0 + 3600));
        assertEquals(SW_OK, setLimit(600), "set again: a new window, from the clock");
        assertEquals(T0 + 3600, windowStart());
        assertEquals(SW_OK, setLimit(0), "removed");
        assertEquals(0, limit());
        assertEquals(SW_OK, setLimit(4294967295L), "and any amount");
        assertEquals(4294967295L, limit());
        for (int i = 0; i < 4; i++) assertEquals(SW_OK, verify(TEST_PIN));
        reselect();
        simulator.reset();
        reselect();
        assertEquals(4294967295L, limit(), "a new SELECT and a reset leave it");
        assertEquals(T0 + 3600, windowStart());
    }

    @Test
    @DisplayName("The day: a terminal that sends the PIN between every spend takes one day's limit, however it is repeated, and a second only after a later signed time")
    void testTerminalTakesOneDay() {
        readyWithLimit(100);
        for (int i = 0; i < 4; i++) assertEquals(SW_OK, load(buildProof(KEYSET, 100, i + 1)).getSW());
        reselect();
        int taken = 0;
        for (int i = 0; i < 4; i++) {
            assertEquals(SW_OK, verify(TEST_PIN));
            if (spend(i).getSW() == SW_OK) taken += 100;
        }
        assertEquals(100, taken, "one piece of four, with the PIN presented four times");
        assertEquals(300, balance());
        assertEquals(100, spentToday());
        // everything else the PIN opens
        byte[] terminalProof = new byte[8];
        nonceBytes();
        assertEquals(SW_OWNER_PROOF, sw(setLimitCommand(terminalProof, 0)));
        assertEquals(SW_OWNER_PROOF, sw(changePinCommand(terminalProof, NEW_PIN)));
        assertEquals(SW_OWNER_PROOF, sw(lockCommand(terminalProof)));
        assertEquals(SW_OWNER_PROOF, sw(setLimitByPinCommand(0)));
        assertEquals(SW_OWNER_PROOF, sw(setLimitByPinCommand(4294967295L)));
        // and a new tap, a power cycle, the same time again, and the PIN again take nothing more
        for (int round = 0; round < 3; round++) {
            if (round == 1) simulator.reset();
            reselect();
            assertEquals(SW_OK, verify(TEST_PIN));
            assertEquals(SW_OK, setTime(T0 + 60 * round), "a minute on, and another");
            for (int i = 0; i < 4; i++) {
                assertEquals(SW_OK, verify(TEST_PIN));
                if (slot(i)[0] == 1) assertEquals(SW_OVER_LIMIT, spend(i).getSW());
            }
        }
        assertEquals(300, balance());
        assertEquals(100, spentToday());
        // a later signed time, a day on: a second
        reselect();
        assertEquals(SW_OK, verify(TEST_PIN));
        assertEquals(SW_OK, setTime(T0 + DAY));
        int second = 0;
        for (int i = 0; i < 4; i++) {
            assertEquals(SW_OK, verify(TEST_PIN));
            if (slot(i)[0] == 1 && spend(i).getSW() == SW_OK) second += 100;
        }
        assertEquals(100, second, "a second, and no more, after a later signed time");
        assertEquals(200, balance());
        assertEquals(T0 + DAY, windowStart());
    }

    @Test
    @DisplayName("The window turns at 86,400 seconds from its start and not at 86,399")
    void testTheWindowTurnsAtADay() {
        readyWithLimit(100);
        for (int i = 0; i < 3; i++) assertEquals(SW_OK, load(buildProof(KEYSET, 100, i + 1)).getSW());
        assertEquals(SW_OK, spend(0).getSW(), "the first, at the set-up clock");
        assertEquals(T0, windowStart());
        assertEquals(SW_OK, setTime(T0 + 86_399));
        assertEquals(SW_OVER_LIMIT, spend(1).getSW(), "86,399 seconds on: the same day");
        assertEquals(T0, windowStart(), "and the refusal wrote nothing");
        assertEquals(100, spentToday());
        assertEquals(SW_OK, setTime(T0 + 86_400));
        assertEquals(SW_OK, spend(1).getSW(), "86,400 seconds on: the next");
        assertEquals(T0 + 86_400, windowStart(), "begun at the clock");
        assertEquals(100, spentToday(), "with nothing carried over but this piece");
        assertEquals(SW_OVER_LIMIT, spend(2).getSW());
        // the next day is a day from the clock, and not from the old window
        assertEquals(SW_OK, setTime(T0 + 86_400 + 86_399));
        assertEquals(SW_OVER_LIMIT, spend(2).getSW());
        assertEquals(SW_OK, setTime(T0 + 2 * 86_400));
        assertEquals(SW_OK, spend(2).getSW());
        assertEquals(T0 + 2 * 86_400, windowStart());
    }

    @Test
    @DisplayName("A new SELECT, a reset, CLEAR_SPENT and a load leave today as it is, and spent today carries across taps")
    void testTodayIsNotReset() {
        readyWithLimit(300);
        for (int i = 0; i < 5; i++) assertEquals(SW_OK, load(buildProof(KEYSET, 100, i + 1)).getSW());
        assertEquals(SW_OK, spend(0).getSW());
        assertEquals(100, spentToday());
        reselect();
        assertEquals(100, spentToday(), "a new SELECT");
        assertEquals(SW_OK, verify(TEST_PIN));
        assertEquals(SW_OK, clearSpent());
        assertEquals(100, spentToday(), "CLEAR_SPENT");
        assertEquals(SW_OK, load(buildProof(KEYSET, 100, 9)).getSW());
        assertEquals(100, spentToday(), "a load, and change loaded is not a refill");
        simulator.reset();
        reselect();
        assertEquals(100, spentToday(), "a reset");
        assertEquals(T0, windowStart());
        assertEquals(SW_OK, verify(TEST_PIN));
        assertEquals(SW_OK, spend(1).getSW());
        reselect();
        assertEquals(SW_OK, verify(TEST_PIN));
        assertEquals(SW_OK, spend(2).getSW(), "the third of a limit of 300, in three taps");
        assertEquals(300, spentToday());
        reselect();
        assertEquals(SW_OK, verify(TEST_PIN));
        assertEquals(SW_OVER_LIMIT, spend(3).getSW(), "and the fourth is over");
        assertEquals(0x63C2, verify(WRONG_PIN));
        assertEquals(SW_OK, verify(TEST_PIN));
        assertEquals(300, spentToday(), "a wrong PIN and a right one change nothing");
    }

    @Test
    @DisplayName("Exactly the limit goes and one sat more does not, with nothing burned, nothing signed and nothing written")
    void testExactlyTheLimit() {
        readyWithLimit(1000);
        assertEquals(SW_OK, load(buildProof(KEYSET, 600, 1)).getSW());
        assertEquals(SW_OK, load(buildProof(KEYSET, 400, 2)).getSW());
        assertEquals(SW_OK, load(buildProof(KEYSET, 1, 3)).getSW());
        assertEquals(SW_OK, load(buildProof(KEYSET, 1001, 4)).getSW());
        byte[] before = slot(3);
        ResponseAPDU r = spend(3);
        assertEquals(SW_OVER_LIMIT, r.getSW(), "a piece over the whole limit by itself");
        assertEquals(0, r.getData().length, "no signature came with it");
        assertArrayEquals(before, slot(3), "the slot is as it was");
        assertEquals(SW_OK, spend(0).getSW());
        assertEquals(600, spentToday(), "counted by the whole piece, to the sat");
        assertEquals(SW_OK, spend(1).getSW(), "exactly what is left is within it");
        assertEquals(1000, spentToday());
        byte[] oneMore = slot(2);
        byte[] infoBefore = info();
        r = spend(2);
        assertEquals(SW_OVER_LIMIT, r.getSW(), "and one sat more is not");
        assertEquals(0, r.getData().length);
        assertArrayEquals(oneMore, slot(2), "not burned");
        assertArrayEquals(infoBefore, info(), "and nothing about the day was written");
        assertEquals(1002, balance());
    }

    @Test
    @DisplayName("A sum that wraps is over any limit, and the largest limit counts up without wrapping")
    void testASumThatWrapsIsOver() {
        readyWithLimit(4294967295L);
        assertEquals(SW_OK, load(buildProof(KEYSET, 4294967294L, 1)).getSW());
        assertEquals(SW_OK, load(buildProof(KEYSET, 5, 2)).getSW());
        assertEquals(SW_OK, load(buildProof(KEYSET, 1, 3)).getSW());
        assertEquals(SW_OK, spend(0).getSW());
        assertEquals(4294967294L, spentToday());
        assertEquals(SW_OVER_LIMIT, spend(1).getSW(), "4294967294 and 5 wrap to 3, which is under any limit, and must not be");
        assertEquals(4294967294L, spentToday());
        assertEquals(1, slot(1)[0], "nothing burned");
        assertEquals(SW_OK, spend(2).getSW(), "one more is exactly the largest number there is");
        assertEquals(4294967295L, spentToday());
    }

    @Test
    @DisplayName("A limit of 0 is no limit: it spends with no time, counts nothing, and never refuses for the day")
    void testNoLimitSpendsWithNoTime() throws Exception {
        ready();
        assertEquals(0, limit());
        for (int i = 0; i < 4; i++) assertEquals(SW_OK, load(buildProof(KEYSET, 1_000_000, i + 1)).getSW());
        // a card that has no clock: no command can leave a loaded card so, so the state is set directly
        putRecord(CashuApplet.CARD_NOW_OFFSET, new byte[4]);
        assertEquals(0, now());
        assertEquals(SW_OK, spend(0).getSW(), "spends with no time");
        assertEquals(SW_OK, spend(1).getSW());
        assertEquals(SW_OK, spend(2).getSW(), "and as much as it has");
        assertEquals(0, spentToday(), "nothing is counted when there is no limit");
        assertEquals(0, windowStart());
        assertEquals(0, now(), "and it does not ask for the time");
    }

    @Test
    @DisplayName("A limit with no time refuses 6A92 and burns nothing")
    void testALimitWithNoTimeRefuses() throws Exception {
        readyWithLimit(1000);
        assertEquals(SW_OK, load(buildProof(KEYSET, 10, 1)).getSW());
        putRecord(CashuApplet.CARD_NOW_OFFSET, new byte[4]);
        byte[] before = slot(0);
        ResponseAPDU r = spend(0);
        assertEquals(SW_NO_TIME, r.getSW());
        assertEquals(0, r.getData().length);
        assertArrayEquals(before, slot(0), "nothing burned");
        assertEquals(0, spentToday());
        assertEquals(SW_OK, setTime(T0 + 1));
        assertEquals(SW_OK, spend(0).getSW(), "told the time, it goes");
    }

    @Test
    @DisplayName("At the edge of a window a terminal can take up to two days' limit in a short span: pinned as what it is")
    void testTwoDaysAtABoundary() {
        readyWithLimit(100);
        for (int i = 0; i < 4; i++) assertEquals(SW_OK, load(buildProof(KEYSET, 100, i + 1)).getSW());
        assertEquals(SW_OK, setTime(T0 + 86_399));
        assertEquals(SW_OK, spend(0).getSW(), "the last second of the first day");
        assertEquals(SW_OVER_LIMIT, spend(1).getSW());
        assertEquals(SW_OK, setTime(T0 + 86_400));
        assertEquals(SW_OK, spend(1).getSW(), "and the first second of the next");
        assertEquals(SW_OVER_LIMIT, spend(2).getSW());
        assertEquals(200, 400 - balance(), "two days' limit, a second apart");
    }

    @Test
    @DisplayName("The window of a limit that was set a long time ago still turns; a limit set under a time far on turns a day after it")
    void testWindowFromSetLimit() {
        readyWithLimit(100);
        for (int i = 0; i < 2; i++) assertEquals(SW_OK, load(buildProof(KEYSET, 100, i + 1)).getSW());
        assertEquals(SW_OK, setTime(T0 + 400 * DAY));
        assertEquals(SW_OK, spend(0).getSW(), "a year on, the first spend begins a window at the clock");
        assertEquals(T0 + 400 * DAY, windowStart());
        assertEquals(SW_OK, setLimit(100), "set again: a window from the clock");
        assertEquals(0, spentToday());
        assertEquals(SW_OK, spend(1).getSW());
        assertEquals(100, spentToday());
    }

    // =========================================================================
    // What the source does, read from the source
    // =========================================================================

    private static String appletCode() throws Exception {
        String src = new String(java.nio.file.Files.readAllBytes(
            SchnorrHWMathTest.mainSourceDir().resolve("CashuApplet.java")), StandardCharsets.UTF_8);
        return SchnorrHWMathTest.stripCommentsAndCharLiterals(src);
    }

    /** The body of a method, from its declaration to the next one's. */
    private static String body(String code, String from, String to) {
        int start = code.indexOf(from);
        int end = code.indexOf(to, start + 1);
        assertTrue(start > 0 && end > start, "found " + from);
        return code.substring(start, end);
    }

    @Test
    @DisplayName("The spend marks the slot and counts the day in one transaction, refuses before anything is changed, and signs only after it commits")
    void testSpendIsOneTransaction() throws Exception {
        String code = appletCode();
        String body = body(code, "private void processSpendProof(", "private void secretHash(");
        int refuse = body.indexOf("SW_OVER_LIMIT");
        int refuseTime = body.indexOf("SW_NO_TIME");
        int begin = body.indexOf("beginTransaction");
        int window = body.indexOf("CARD_WINDOW_OFFSET");
        int charge = body.indexOf("CARD_SPENT_OFFSET", begin);
        int burn = body.lastIndexOf("STATUS_SPENT");
        int commit = body.indexOf("commitTransaction");
        int sign = body.indexOf("schnorrHW.sign");
        assertTrue(refuse > 0 && refuse < begin && refuseTime > 0 && refuseTime < begin, "the refusals come before anything is changed");
        assertTrue(begin > 0 && begin < window && window < charge && charge < burn && burn < commit && commit < sign,
            "the window, what the day has signed for and the slot change between begin and commit, and the signing is after");
        assertEquals(1, count(body, "Util.arrayCopy(cardRecord"), "a spend begins the window by copying the clock, in the transaction, and writes no other part of the record");
    }

    private static int count(String s, String what) {
        int n = 0;
        for (int at = s.indexOf(what); at >= 0; at = s.indexOf(what, at + 1)) n++;
        return n;
    }

    @Test
    @DisplayName("The grant is read only by loading and clearing, never by a spend; nothing in the applet resets what a spend has used; the clock is written only by SET_TIME and, once, by SET_CARD")
    void testWhatReadsWhat() throws Exception {
        String code = appletCode();
        // where the grant is read
        String spend = body(code, "private void processSpendProof(", "private void secretHash(");
        assertFalse(spend.contains("loadGrant"), "a spend does not look at the grant");
        String verify = body(code, "private void processVerifyPin(", "private void failPinCheck(");
        assertFalse(verify.contains("arrayFill"), "VERIFY_PIN fills nothing");
        assertFalse(verify.contains("cardRecord"), "and does not touch the record");
        assertFalse(verify.contains("scratch"));
        assertFalse(verify.contains("loadGrant"));
        int uses = count(code, "loadGrant[0]");
        // set once in ALLOW_LOAD, read in the load authority and in CLEAR_SPENT, and nowhere else
        assertEquals(3, uses, "loadGrant is set by ALLOW_LOAD and read by requireLoadAuthority and CLEAR_SPENT: " + uses);
        assertTrue(body(code, "private void processAllowLoad(", "private short requireOwnerProof(").contains("loadGrant[0] = (byte) 1"));
        assertTrue(body(code, "private void requireLoadAuthority(", "private void requireNothingUnspent(").contains("loadGrant[0]"));
        assertTrue(body(code, "private void processClearSpent(", "private void processSetCard(").contains("loadGrant[0]"));
        // the clock: written by SET_TIME, and cleared by SET_CARD only when the time key is new
        int at = 0, writes = 0;
        while ((at = code.indexOf("CARD_NOW_OFFSET", at)) >= 0) {
            int lineStart = code.lastIndexOf('\n', at) + 1;
            int lineEnd = code.indexOf('\n', at);
            String line = code.substring(lineStart, lineEnd);
            // a write has the clock as the destination: the second range of an arrayCopy, or the first of a fill
            if (line.matches(".*Util\\.arrayCopy(?:NonAtomic)?\\([^,]*,[^,]*,\\s*cardRecord,\\s*CARD_NOW_OFFSET.*")
                || line.contains("arrayFillNonAtomic(cardRecord, CARD_NOW_OFFSET")
                || line.contains("arrayFill(cardRecord, CARD_NOW_OFFSET")) writes++;
            at = lineEnd;
        }
        assertEquals(2, writes, "two places write the clock: " + writes);
        String setTime = body(code, "private void processSetTime(", "private void processVerifyPin(");
        assertTrue(setTime.contains("cmpUint32(buf, at, cardRecord, CARD_NOW_OFFSET) > 0"), "SET_TIME writes it only for a later time");
        String setCard = body(code, "private void processSetCard(", "private void processSetLimit(");
        int clear = setCard.indexOf("arrayFillNonAtomic(cardRecord, CARD_NOW_OFFSET");
        int guard = setCard.indexOf("if (newKey)");
        assertTrue(guard > 0 && clear > guard, "and SET_CARD clears it only inside `if (newKey)`");
    }

    @Test
    @DisplayName("The refusals that gate a command come first: the PIN, then the owner, then the shape; a limit is read, not set, by a spend")
    void testGatesComeFirst() throws Exception {
        String code = appletCode();
        String spend = body(code, "private void processSpendProof(", "private void secretHash(");
        assertTrue(spend.indexOf("requirePinIfSet()") >= 0 && spend.indexOf("requirePinIfSet()") < spend.indexOf("SLOT_OUT_OF_RANGE"), "the PIN gate is the first statement of a spend");
        String load = body(code, "private void processLoadProof(", "private void processClearSpent(");
        assertTrue(load.indexOf("requireLoadAuthority()") < load.indexOf("SW_NO_OWNER")
            && load.indexOf("SW_NO_OWNER") < load.indexOf("SW_NO_CARD_RECORD")
            && load.indexOf("SW_NO_CARD_RECORD") < load.indexOf("SW_NO_TIME"), "load: the PIN or the grant, an owner, a record, a time");
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
    // Vectors for the other side of the wire
    // =========================================================================

    /** The applet's folder in the build that is running: where target/ is. */
    private static java.nio.file.Path appletFolder() throws Exception {
        java.nio.file.Path target = SchnorrHWMathTest.mainSourceDir().toAbsolutePath().normalize();
        while (target != null && !target.endsWith("applet")) target = target.getParent();
        assertNotNull(target, "the applet folder");
        return target;
    }

    /**
     * What the card signed, written down for whoever makes the pieces: the
     * card's key, each piece as it was loaded, the secret text this test says
     * it is, and the signature the card gave for it. A wallet that builds the
     * same text from the same fields, and finds the signature good, agrees
     * with the card byte for byte. Written to target/secret-vectors.json;
     * spec/vectors/secret.json is one run of it, kept.
     */
    @Test
    @DisplayName("The card's signatures, as vectors for a wallet to check its secrets against")
    void testWriteVectors() throws Exception {
        ready();
        long[] dates = { 0L, 1L, 1700000000L, 1900000000L, 4294967295L };
        StringBuilder out = new StringBuilder();
        byte[] key = cardKey();
        out.append("{\n \"cardKey\": \"").append(toHex(key)).append("\",\n \"refundKey\": \"").append(toHex(REFUND))
           .append("\",\n \"mint\": \"").append(MINT).append("\",\n \"pieces\": [\n");
        for (int i = 0; i < dates.length; i++) {
            long amount = 1L << (i + 3);
            assertEquals(SW_OK, load(buildProof(KEYSET, amount, 0x40 + 7 * i, dates[i])).getSW());
            byte[] before = slot(i);
            ResponseAPDU r = spend(i);
            assertEquals(SW_OK, r.getSW());
            byte[] nonce = Arrays.copyOfRange(before, 13, 45);
            String secret = secretText(nonce, key, dates[i], REFUND);
            byte[] msg = sha256(secret.getBytes(StandardCharsets.UTF_8));
            assertTrue(schnorrVerify(extractPubkeyX(key), msg, r.getData()));
            out.append("  {\"keyset\": \"").append(KEYSET).append("\", \"amount\": ").append(amount)
               .append(", \"nonce\": \"").append(toHex(nonce))
               .append("\", \"C\": \"").append(toHex(Arrays.copyOfRange(before, 45, 78)))
               .append("\", \"date\": ").append(dates[i])
               .append(",\n   \"slot\": \"").append(toHex(before))
               .append("\",\n   \"secret\": ").append(jsonString(secret))
               .append(",\n   \"message\": \"").append(toHex(msg))
               .append("\",\n   \"signature\": \"").append(toHex(r.getData())).append("\"}")
               .append(i + 1 < dates.length ? ",\n" : "\n");
        }
        // AUTH, for the same reason
        byte[] reader = hexToBytes("a0a1a2a3a4a5a6a7a8a9aaabacadaeaf");
        byte[] auth = transmit(new CommandAPDU(CLA, INS_AUTH, 0, 0, reader, 80)).getData();
        byte[] cardNonce = Arrays.copyOfRange(auth, 0, 16);
        out.append(" ],\n \"auth\": {\"tag\": \"FoxyCard/auth\", \"readerNonce\": \"").append(toHex(reader))
           .append("\", \"cardNonce\": \"").append(toHex(cardNonce))
           .append("\", \"message\": \"").append(toHex(authMessage(reader, cardNonce, key)))
           .append("\", \"signature\": \"").append(toHex(Arrays.copyOfRange(auth, 16, 80))).append("\"}\n}\n");
        java.nio.file.Path target = SchnorrHWMathTest.mainSourceDir().toAbsolutePath().normalize();
        while (target != null && !target.endsWith("applet")) target = target.getParent();
        assertNotNull(target, "the applet folder");
        java.nio.file.Files.createDirectories(target.resolve("target"));
        java.nio.file.Files.write(target.resolve("target").resolve("secret-vectors.json"),
            out.toString().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Owner vectors, for whoever builds the other side of the owner's proof and
     * the time: the owner key, one proof for each label over a nonce and a value,
     * and a signed time with its key. ECDSA signatures are randomised, so each
     * is one the card has verified, and the other side verifies it again with
     * its own implementation. Written to target/owner-vectors.json;
     * spec/vectors/owner.json is one run of it, kept.
     */
    @Test
    @DisplayName("The owner's proofs and a signed time, as vectors for another implementation to verify")
    void testWriteOwnerVectors() throws Exception {
        StringBuilder out = new StringBuilder("{\n");
        out.append(" \"curve\": \"P-256 (secp256r1)\",\n \"signature\": \"ECDSA, SHA-256, DER\",\n");
        out.append(" \"ownerKey\": \"").append(toHex(OWNER.pub)).append("\",\n");
        out.append(" \"timeKey\": \"").append(toHex(SIGNER.pub)).append("\",\n \"proofs\": [\n");
        String[] labels = { L_PIN, L_LIMIT, L_LOAD, L_CARD, L_OWNER, L_LOCK };
        byte[][] values = { NEW_PIN, u32(5000), new byte[0], record(MINT, REFUND, SIGNER.pub), OTHER_OWNER.pub, new byte[0] };
        for (int i = 0; i < labels.length; i++) {
            simulator = freshCard();
            ready();
            byte[] n = nonceBytes();
            byte[] proof = ownerProof(labels[i], OWNER, n, values[i]);
            CommandAPDU[] commands = { changePinCommand(proof, NEW_PIN), setLimitCommand(proof, 5000), allowLoadCommand(proof),
                setCardCommand(proof, values[3]), setOwnerCommand(proof, OTHER_OWNER), lockCommand(proof) };
            assertEquals(SW_OK, sw(commands[i]), labels[i] + " is the owner's proof, to the card");
            out.append("  {\"label\": \"").append(labels[i]).append("\", \"nonce\": \"").append(toHex(n))
               .append("\", \"value\": \"").append(toHex(values[i])).append("\", \"signature\": \"").append(toHex(proof))
               .append("\"}").append(i + 1 < labels.length ? ",\n" : "\n");
        }
        simulator = freshCard();
        ready();
        out.append(" ],\n \"time\": {\"label\": \"FoxyCard/time\", \"time\": ").append(T0 + 100);
        byte[] ts = timeSignature(SIGNER, T0 + 100);
        assertEquals(SW_OK, sw(setTimeCommand(T0 + 100, ts)), "and the card verified the time");
        out.append(", \"signature\": \"").append(toHex(ts)).append("\"}\n}\n");
        java.nio.file.Path target = appletFolder();
        java.nio.file.Files.createDirectories(target.resolve("target"));
        java.nio.file.Files.write(target.resolve("target").resolve("owner-vectors.json"), out.toString().getBytes(StandardCharsets.UTF_8));
    }

    // ---- the transcript ------------------------------------------------------

    /** One command, written down with what it answered, and checked against what it was meant to answer. */
    private ResponseAPDU say(StringBuilder out, String name, String kind, int expected, CommandAPDU cmd) {
        ResponseAPDU r = transmit(cmd);
        assertEquals(expected, r.getSW(), name);
        out.append("  {\"name\": ").append(jsonString(name)).append(", \"kind\": \"").append(kind)
           .append("\", \"apdu\": \"").append(toHex(cmd.getBytes()))
           .append("\", \"sw\": \"").append(String.format("%04x", r.getSW()))
           .append("\", \"data\": \"").append(toHex(r.getData())).append("\"},\n");
        return r;
    }

    private byte[] sayNonce(StringBuilder out) {
        return say(out, "GET_NONCE", "nonce", SW_OK, new CommandAPDU(CLA, INS_GET_NONCE, 0, 0, 16)).getData();
    }

    /** A nonce, a proof by `key` over `value` under `label`, and the command built with it. */
    private ResponseAPDU owner(StringBuilder out, String name, int expected, String label, Key key, byte[] value,
                               java.util.function.Function<byte[], CommandAPDU> build) {
        byte[] n = sayNonce(out);
        return say(out, name, "exact", expected, build.apply(ownerProof(label, key, n, value)));
    }

    private ResponseAPDU time(StringBuilder out, String name, int expected, Key signer, long t) {
        return say(out, name, "exact", expected, setTimeCommand(t, timeSignature(signer, t)));
    }

    /**
     * A whole conversation with the card, written down: every command, what it
     * was sent and what came back. A model of the card (Foxy's tests have one,
     * so a wallet can be tested with no card and no Java) is held to this,
     * command for command. `kind` says how an answer may be compared: `exact`
     * to the byte; `key`, `sig` and `auth` hold a key or a signature, which is
     * another card's to differ in and the reader's to verify; `nonce` is 16
     * bytes the card made up, which the model is told, so that the owner's
     * proofs that follow (made from it here) are proofs to it as well. The
     * owner's key and the time signer's are in the commands that give them to
     * the card; the model verifies what is signed with them.
     * Written to target/transcript.json; spec/vectors/transcript.json is one
     * run of it, kept.
     */
    @Test
    @DisplayName("A conversation with the card, as a transcript for a model of it to be held to")
    void testWriteTranscript() throws Exception {
        StringBuilder out = new StringBuilder("[\n");
        byte[] badRefund = REFUND.clone(); badRefund[0] = 0x04;
        byte[] compressedKey = SIGNER.pub.clone(); compressedKey[0] = 0x02;
        byte[] lyingRecord = record("0123456789", REFUND, SIGNER.pub); lyingRecord[99] = 20;
        byte[] notPoint = buildProof(KEYSET, 16, 5); notPoint[44] = 0x04;
        CommandAPDU select = new CommandAPDU(0x00, 0xA4, 0x04, 0x00, hexToBytes(AID_STR));
        CommandAPDU info = new CommandAPDU(CLA, INS_GET_INFO, 0, 0, 256);
        CommandAPDU getCard = new CommandAPDU(CLA, INS_GET_CARD, 0, 0, 256);
        CommandAPDU verifyOk = new CommandAPDU(CLA, INS_VERIFY_PIN, 0, 0, TEST_PIN);
        CommandAPDU clear = new CommandAPDU(CLA, INS_CLEAR_SPENT, 0, 0, 1);
        byte[] oneKey = new byte[65]; oneKey[0] = 0x02;

        say(out, "select", "exact", SW_OK, select);
        say(out, "a new card says what it is", "exact", SW_OK, info);
        say(out, "its key", "key", SW_OK, new CommandAPDU(CLA, INS_GET_PUBKEY, 0, 0, 256));
        say(out, "its record, not yet written", "exact", SW_OK, getCard);
        say(out, "nothing loads with no PIN", "exact", SW_SECURITY_NOT_SATIS, new CommandAPDU(CLA, INS_LOAD_PROOF, 0, 0, buildProof(KEYSET, 16, 1), 1));
        say(out, "a card with no record has no time key to check a time against", "exact", SW_NO_CARD_RECORD, setTimeCommand(T0, timeSignature(SIGNER, T0)));
        say(out, "a card with no owner has no nonce to give", "exact", SW_NO_OWNER, new CommandAPDU(CLA, INS_GET_NONCE, 0, 0, 16));
        say(out, "nor an ALLOW_LOAD", "exact", SW_NO_OWNER, allowLoadCommand(new byte[8]));
        say(out, "nor a limit by an owner", "exact", SW_NO_OWNER, setLimitCommand(new byte[8], 100));
        say(out, "nor a CHANGE_PIN", "exact", SW_NO_OWNER, changePinCommand(new byte[8], NEW_PIN));
        say(out, "nor can it be locked, by a reader with no PIN", "exact", SW_SECURITY_NOT_SATIS, lockCommand(new byte[8]));
        say(out, "a PIN of the wrong length", "exact", SW_WRONG_LENGTH, new CommandAPDU(CLA, INS_SET_PIN, 0, 0, new byte[] { 1, 2, 3 }));
        say(out, "SET_PIN", "exact", SW_OK, new CommandAPDU(CLA, INS_SET_PIN, 0, 0, NEW_PIN));
        say(out, "SET_PIN again: an open card has nobody's PIN to protect", "exact", SW_OK, new CommandAPDU(CLA, INS_SET_PIN, 0, 0, TEST_PIN));
        say(out, "VERIFY_PIN with nothing wrong", "exact", SW_OK, verifyOk);
        say(out, "a wrong PIN", "exact", 0x63C2, new CommandAPDU(CLA, INS_VERIFY_PIN, 0, 0, WRONG_PIN));
        say(out, "the info after a wrong PIN", "exact", SW_OK, info);
        say(out, "the right PIN", "exact", SW_OK, verifyOk);
        say(out, "a record whose refund key is not a point", "exact", SW_WRONG_DATA, setCardOpenCommand(record(MINT, badRefund, SIGNER.pub)));
        say(out, "a record whose time key is not uncompressed", "exact", SW_WRONG_DATA, setCardOpenCommand(record(MINT, REFUND, compressedKey)));
        say(out, "a record whose length is not its mint's", "exact", SW_WRONG_LENGTH, setCardOpenCommand(lyingRecord));
        say(out, "SET_CARD, with no refund key", "exact", SW_OK, setCardOpenCommand(record(MINT, NO_REFUND, SIGNER.pub)));
        say(out, "SET_CARD again, with one, and the same time key", "exact", SW_OK, setCardOpenCommand(record(MINT, REFUND, SIGNER.pub)));
        say(out, "the record, read back: the time key, and no limit", "exact", SW_OK, getCard);
        say(out, "a limit by PIN needs a time to start from", "exact", SW_NO_TIME, setLimitByPinCommand(500));
        say(out, "no limit needs none", "exact", SW_OK, setLimitByPinCommand(0));
        say(out, "nothing loads onto a card with no owner", "exact", SW_NO_OWNER, new CommandAPDU(CLA, INS_LOAD_PROOF, 0, 0, buildProof(KEYSET, 16, 1), 1));
        say(out, "an owner key of the wrong length", "exact", SW_WRONG_LENGTH, new CommandAPDU(CLA, INS_SET_OWNER, 0, 0, new byte[64]));
        say(out, "an owner key that is not uncompressed", "exact", SW_WRONG_DATA, new CommandAPDU(CLA, INS_SET_OWNER, 0, 0, oneKey));
        say(out, "SET_OWNER", "exact", SW_OK, setOwnerOpenCommand(OWNER));
        say(out, "the info with an owner", "exact", SW_OK, info);
        say(out, "SET_OWNER again, with no proof", "exact", SW_OWNER_PROOF, setOwnerOpenCommand(OTHER_OWNER));
        say(out, "SET_PIN on a card with an owner", "exact", SW_OWNER_PROOF, new CommandAPDU(CLA, INS_SET_PIN, 0, 0, NEW_PIN));
        say(out, "SET_CARD on a card with an owner, with no proof", "exact", SW_OWNER_PROOF, setCardOpenCommand(record(MINT, REFUND, OTHER_SIGNER.pub)));
        say(out, "a limit by PIN on a card with an owner", "exact", SW_OWNER_PROOF, setLimitByPinCommand(0));
        say(out, "nothing loads before the card knows the time", "exact", SW_NO_TIME, new CommandAPDU(CLA, INS_LOAD_PROOF, 0, 0, buildProof(KEYSET, 16, 1), 1));
        time(out, "a time signed by another key", SW_NOT_THE_TIME, OTHER_SIGNER, T0);
        time(out, "the time", SW_OK, SIGNER, T0);
        say(out, "the info after the time", "exact", SW_OK, info);
        time(out, "an older time is taken for nothing", SW_OK, SIGNER, T0 - 5);
        time(out, "and the clock does not go back", SW_OK, SIGNER, T0);

        byte[] n = sayNonce(out);
        say(out, "a limit by the owner with another key's proof", "exact", SW_OWNER_PROOF, setLimitCommand(ownerProof(L_LIMIT, OTHER_OWNER, n, u32(1000)), 1000));
        owner(out, "a limit with a proof for another number", SW_OWNER_PROOF, L_LIMIT, OWNER, u32(1000), p -> setLimitCommand(p, 5000));
        owner(out, "a limit with CHANGE_PIN's proof", SW_OWNER_PROOF, L_PIN, OWNER, u32(1000), p -> setLimitCommand(p, 1000));
        byte[] limitNonce = sayNonce(out);
        byte[] limitProof = ownerProof(L_LIMIT, OWNER, limitNonce, u32(1000));
        say(out, "a limit of 1000 by the owner, with no PIN needed", "exact", SW_OK, setLimitCommand(limitProof, 1000));
        say(out, "the same proof again", "exact", SW_OWNER_PROOF, setLimitCommand(limitProof, 1000));
        say(out, "the info: the limit, and a window begun at the clock", "exact", SW_OK, info);
        say(out, "the record, read back", "exact", SW_OK, getCard);

        say(out, "a piece of 600 with a date", "exact", SW_OK, new CommandAPDU(CLA, INS_LOAD_PROOF, 0, 0, buildProof(KEYSET, 600, 1, 1900000000L), 1));
        say(out, "a piece of 400 with none", "exact", SW_OK, new CommandAPDU(CLA, INS_LOAD_PROOF, 0, 0, buildProof(KEYSET, 400, 2), 1));
        say(out, "a piece of 1", "exact", SW_OK, new CommandAPDU(CLA, INS_LOAD_PROOF, 0, 0, buildProof(KEYSET, 1, 3, 1900000000L), 1));
        say(out, "a piece of 2000", "exact", SW_OK, new CommandAPDU(CLA, INS_LOAD_PROOF, 0, 0, buildProof(KEYSET, 2000, 4), 1));
        say(out, "a piece whose C is not a point", "exact", SW_WRONG_DATA, new CommandAPDU(CLA, INS_LOAD_PROOF, 0, 0, notPoint, 1));
        say(out, "a piece worth nothing", "exact", SW_WRONG_DATA, new CommandAPDU(CLA, INS_LOAD_PROOF, 0, 0, buildProof(KEYSET, 0, 6), 1));
        say(out, "a piece that is on the card already is not written twice", "exact", SW_PIECE_ON_CARD, new CommandAPDU(CLA, INS_LOAD_PROOF, 0, 0, buildProof(KEYSET, 600, 1, 1900000000L), 1));
        say(out, "nor a copy of it that states another amount", "exact", SW_PIECE_ON_CARD, new CommandAPDU(CLA, INS_LOAD_PROOF, 0, 0, buildProof(KEYSET, 1, 1, 1900000000L), 1));
        say(out, "a piece of upstream's length", "exact", SW_WRONG_LENGTH, new CommandAPDU(CLA, INS_LOAD_PROOF, 0, 0, new byte[77], 1));
        owner(out, "the record cannot change under unspent pieces, owner's proof or not", SW_CARD_IN_USE, L_CARD, OWNER, record(MINT, REFUND, OTHER_SIGNER.pub),
              p -> setCardCommand(p, record(MINT, REFUND, OTHER_SIGNER.pub)));
        say(out, "loading did not give the day anything back", "exact", SW_OK, info);
        say(out, "every slot's state", "exact", SW_OK, new CommandAPDU(CLA, INS_GET_SLOT_STATUS, 0, 0, 256));
        say(out, "every piece and every slot's state: the first page", "exact", SW_OK, new CommandAPDU(CLA, INS_GET_PIECES, 0, 0, 256));
        say(out, "and the second, from where the first stopped", "exact", SW_OK, new CommandAPDU(CLA, INS_GET_PIECES, 3, 0, 256));
        say(out, "and from a slot there is not", "exact", SW_SLOT_OUT_OF_RANGE, new CommandAPDU(CLA, INS_GET_PIECES, 64, 0, 256));
        say(out, "slot 0", "exact", SW_OK, new CommandAPDU(CLA, INS_GET_PROOF, 0, 0, 256));
        say(out, "slot 1", "exact", SW_OK, new CommandAPDU(CLA, INS_GET_PROOF, 1, 0, 256));
        say(out, "an empty slot", "exact", SW_SLOT_EMPTY, new CommandAPDU(CLA, INS_GET_PROOF, 9, 0, 256));
        say(out, "a slot there is not", "exact", SW_SLOT_OUT_OF_RANGE, new CommandAPDU(CLA, INS_GET_PROOF, 64, 0, 256));
        say(out, "the balance", "exact", SW_OK, new CommandAPDU(CLA, INS_GET_BALANCE, 0, 0, 4));
        say(out, "how many slots are in use", "exact", SW_OK, new CommandAPDU(CLA, INS_GET_PROOF_COUNT, 0, 0, 1));
        say(out, "2000 is over the limit by itself", "exact", SW_OVER_LIMIT, new CommandAPDU(CLA, INS_SPEND_PROOF, 3, 0, 64));
        say(out, "spend slot 0", "sig", SW_OK, new CommandAPDU(CLA, INS_SPEND_PROOF, 0, 0, 64));
        say(out, "the day after it", "exact", SW_OK, info);
        say(out, "spend slot 1, to the limit exactly", "sig", SW_OK, new CommandAPDU(CLA, INS_SPEND_PROOF, 1, 0, 64));
        say(out, "one sat more is over it", "exact", SW_OVER_LIMIT, new CommandAPDU(CLA, INS_SPEND_PROOF, 2, 0, 64));
        say(out, "slot 0 a second time", "exact", SW_CONDITIONS_NOT_SATIS, new CommandAPDU(CLA, INS_SPEND_PROOF, 0, 0, 64));
        say(out, "a spent piece is not written again beside itself", "exact", SW_PIECE_ON_CARD, new CommandAPDU(CLA, INS_LOAD_PROOF, 0, 0, buildProof(KEYSET, 1, 1, 1900000000L), 1));
        say(out, "an empty slot spent", "exact", SW_SLOT_EMPTY, new CommandAPDU(CLA, INS_SPEND_PROOF, 9, 0, 64));
        say(out, "the PIN again", "exact", SW_OK, verifyOk);
        say(out, "it gives none of the day back", "exact", SW_OVER_LIMIT, new CommandAPDU(CLA, INS_SPEND_PROOF, 2, 0, 64));
        say(out, "a new tap", "exact", SW_OK, select);
        say(out, "the PIN in it", "exact", SW_OK, verifyOk);
        say(out, "nor does a new tap", "exact", SW_OVER_LIMIT, new CommandAPDU(CLA, INS_SPEND_PROOF, 2, 0, 64));
        time(out, "a time a second short of a day on", SW_OK, SIGNER, T0 + 86_399);
        say(out, "is the same day", "exact", SW_OVER_LIMIT, new CommandAPDU(CLA, INS_SPEND_PROOF, 2, 0, 64));
        say(out, "and the info is as it was", "exact", SW_OK, info);
        time(out, "a day on", SW_OK, SIGNER, T0 + 86_400);
        say(out, "the next day: slot 2, one sat", "sig", SW_OK, new CommandAPDU(CLA, INS_SPEND_PROOF, 2, 0, 64));
        say(out, "the window began at the clock, with one sat in it", "exact", SW_OK, info);
        say(out, "2000 is still over the limit by itself", "exact", SW_OVER_LIMIT, new CommandAPDU(CLA, INS_SPEND_PROOF, 3, 0, 64));
        owner(out, "the owner takes the limit off", SW_OK, L_LIMIT, OWNER, u32(0), p -> setLimitCommand(p, 0));
        say(out, "and 2000 goes", "sig", SW_OK, new CommandAPDU(CLA, INS_SPEND_PROOF, 3, 0, 64));
        say(out, "nothing is counted with no limit", "exact", SW_OK, info);
        owner(out, "the owner puts a limit of 50 back", SW_OK, L_LIMIT, OWNER, u32(50), p -> setLimitCommand(p, 50));
        say(out, "its window, begun at the clock", "exact", SW_OK, info);
        say(out, "spent places are listed by their tags alone", "exact", SW_OK, new CommandAPDU(CLA, INS_GET_PIECES, 0, 0, 256));
        say(out, "CLEAR_SPENT", "exact", SW_OK, clear);
        say(out, "every slot's state after", "exact", SW_OK, new CommandAPDU(CLA, INS_GET_SLOT_STATUS, 0, 0, 256));
        say(out, "and nothing is left to list", "exact", SW_OK, new CommandAPDU(CLA, INS_GET_PIECES, 0, 0, 256));
        say(out, "the freed slot is used next", "exact", SW_OK, new CommandAPDU(CLA, INS_LOAD_PROOF, 0, 0, buildProof(KEYSET, 8, 7), 1));
        say(out, "the piece of 8 is listed in the place that was freed", "exact", SW_OK, new CommandAPDU(CLA, INS_GET_PIECES, 0, 0, 256));
        say(out, "change loaded does not give the day anything back", "exact", SW_OK, info);
        say(out, "a piece whose place was freed may be loaded again", "exact", SW_OK, new CommandAPDU(CLA, INS_LOAD_PROOF, 0, 0, buildProof(KEYSET, 600, 1, 1900000000L), 1));
        say(out, "and is then on the card, and not written twice", "exact", SW_PIECE_ON_CARD, new CommandAPDU(CLA, INS_LOAD_PROOF, 0, 0, buildProof(KEYSET, 600, 1, 1900000000L), 1));

        // the tap after a payment may put pieces on with no PIN: the change (8.2)
        say(out, "a new tap, after payments", "exact", SW_OK, select);
        say(out, "the info: this tap may load with no PIN", "exact", SW_OK, info);
        say(out, "a piece of 4, with no PIN: change", "exact", SW_OK, new CommandAPDU(CLA, INS_LOAD_PROOF, 0, 0, buildProof(KEYSET, 4, 12), 1));
        say(out, "CLEAR_SPENT, with no PIN", "exact", SW_OK, clear);
        say(out, "but not a spend", "exact", SW_SECURITY_NOT_SATIS, new CommandAPDU(CLA, INS_SPEND_PROOF, 0, 0, 64));
        say(out, "nor a limit by PIN", "exact", SW_SECURITY_NOT_SATIS, setLimitByPinCommand(0));
        say(out, "a new tap: the note was for one tap", "exact", SW_OK, select);
        say(out, "which says so", "exact", SW_OK, info);
        say(out, "and loads nothing with no PIN", "exact", SW_SECURITY_NOT_SATIS, new CommandAPDU(CLA, INS_LOAD_PROOF, 0, 0, buildProof(KEYSET, 4, 13), 1));
        say(out, "the PIN", "exact", SW_OK, verifyOk);
        say(out, "a payment: the piece of 8", "sig", SW_OK, new CommandAPDU(CLA, INS_SPEND_PROOF, 0, 0, 64));
        say(out, "the tap it was paid in gets no grant of its own", "exact", SW_OK, info);
        say(out, "a tap that only reads the card", "exact", SW_OK, select);
        say(out, "has the grant", "exact", SW_OK, info);
        say(out, "and the tap after it", "exact", SW_OK, select);
        say(out, "has none", "exact", SW_OK, info);
        say(out, "nor loads", "exact", SW_SECURITY_NOT_SATIS, new CommandAPDU(CLA, INS_LOAD_PROOF, 0, 0, buildProof(KEYSET, 4, 13), 1));
        say(out, "the PIN, for the rest", "exact", SW_OK, verifyOk);

        say(out, "SIGN_ARBITRARY is not a command", "exact", SW_INS_NOT_SUPPORTED, new CommandAPDU(CLA, INS_SIGN_ARBITRARY, 0, 0, new byte[32], 64));
        say(out, "AUTH", "auth", SW_OK, new CommandAPDU(CLA, INS_AUTH, 0, 0, hexToBytes("a0a1a2a3a4a5a6a7a8a9aaabacadaeaf"), 80));
        say(out, "AUTH with 32 bytes", "exact", SW_WRONG_LENGTH, new CommandAPDU(CLA, INS_AUTH, 0, 0, new byte[32], 80));

        // the owner adds funds, with no PIN
        say(out, "a new tap", "exact", SW_OK, select);
        say(out, "a load, with no PIN", "exact", SW_SECURITY_NOT_SATIS, new CommandAPDU(CLA, INS_LOAD_PROOF, 0, 0, buildProof(KEYSET, 4, 8), 1));
        say(out, "CLEAR_SPENT, with no PIN", "exact", SW_SECURITY_NOT_SATIS, clear);
        say(out, "ALLOW_LOAD with no nonce asked for", "exact", SW_OWNER_PROOF, allowLoadCommand(new byte[8]));
        owner(out, "ALLOW_LOAD with another command's proof", SW_OWNER_PROOF, L_PIN, OWNER, new byte[0], p -> allowLoadCommand(p));
        owner(out, "ALLOW_LOAD with the owner's proof", SW_OK, L_LOAD, OWNER, new byte[0], p -> allowLoadCommand(p));
        say(out, "a load, with no PIN", "exact", SW_OK, new CommandAPDU(CLA, INS_LOAD_PROOF, 0, 0, buildProof(KEYSET, 4, 8), 1));
        say(out, "CLEAR_SPENT, with no PIN", "exact", SW_OK, clear);
        say(out, "but not a spend", "exact", SW_SECURITY_NOT_SATIS, new CommandAPDU(CLA, INS_SPEND_PROOF, 4, 0, 64));
        say(out, "nor a limit by PIN", "exact", SW_SECURITY_NOT_SATIS, setLimitByPinCommand(0));
        say(out, "nor the PIN", "exact", SW_OWNER_PROOF, new CommandAPDU(CLA, INS_SET_PIN, 0, 0, NEW_PIN));
        say(out, "a new tap", "exact", SW_OK, select);
        say(out, "the grant is gone with the tap", "exact", SW_SECURITY_NOT_SATIS, new CommandAPDU(CLA, INS_LOAD_PROOF, 0, 0, buildProof(KEYSET, 4, 9), 1));

        // CHANGE_PIN, by the owner
        say(out, "the PIN", "exact", SW_OK, verifyOk);
        say(out, "CHANGE_PIN with no nonce asked for", "exact", SW_OWNER_PROOF, changePinCommand(new byte[8], NEW_PIN));
        owner(out, "CHANGE_PIN with another key's proof", SW_OWNER_PROOF, L_PIN, OTHER_OWNER, NEW_PIN, p -> changePinCommand(p, NEW_PIN));
        say(out, "it cost no try", "exact", SW_OK, info);
        owner(out, "CHANGE_PIN with a proof for another PIN", SW_OWNER_PROOF, L_PIN, OWNER, WRONG_PIN, p -> changePinCommand(p, NEW_PIN));
        owner(out, "CHANGE_PIN with a PIN of the wrong length", SW_WRONG_LENGTH, L_PIN, OWNER, new byte[] { 1, 2, 3 }, p -> changePinCommand(p, new byte[] { 1, 2, 3 }));
        byte[] pinNonce = sayNonce(out);
        byte[] pinProof = ownerProof(L_PIN, OWNER, pinNonce, NEW_PIN);
        say(out, "CHANGE_PIN", "exact", SW_OK, changePinCommand(pinProof, NEW_PIN));
        say(out, "the same proof again", "exact", SW_OWNER_PROOF, changePinCommand(pinProof, TEST_PIN));
        say(out, "which ended the session", "exact", SW_SECURITY_NOT_SATIS, new CommandAPDU(CLA, INS_SPEND_PROOF, 4, 0, 64));
        say(out, "the old PIN", "exact", 0x63C2, verifyOk);
        say(out, "the new PIN", "exact", SW_OK, new CommandAPDU(CLA, INS_VERIFY_PIN, 0, 0, NEW_PIN));
        say(out, "a new tap", "exact", SW_OK, select);
        owner(out, "CHANGE_PIN back, with no PIN in the tap", SW_OK, L_PIN, OWNER, TEST_PIN, p -> changePinCommand(p, TEST_PIN));

        // blocked, and the owner unblocks it
        say(out, "another new tap", "exact", SW_OK, select);
        say(out, "wrong, two left", "exact", 0x63C2, new CommandAPDU(CLA, INS_VERIFY_PIN, 0, 0, WRONG_PIN));
        say(out, "wrong, one left", "exact", 0x63C1, new CommandAPDU(CLA, INS_VERIFY_PIN, 0, 0, WRONG_PIN));
        say(out, "wrong, blocked", "exact", SW_PIN_BLOCKED, new CommandAPDU(CLA, INS_VERIFY_PIN, 0, 0, WRONG_PIN));
        say(out, "a blocked card says so", "exact", SW_OK, info);
        say(out, "the right PIN opens nothing now", "exact", SW_PIN_BLOCKED, verifyOk);
        say(out, "a blocked card does not spend", "exact", SW_SECURITY_NOT_SATIS, new CommandAPDU(CLA, INS_SPEND_PROOF, 4, 0, 64));
        say(out, "nor load", "exact", SW_SECURITY_NOT_SATIS, new CommandAPDU(CLA, INS_LOAD_PROOF, 0, 0, buildProof(KEYSET, 16, 8), 1));
        say(out, "nor be given a PIN by whoever is at the reader", "exact", SW_OWNER_PROOF, new CommandAPDU(CLA, INS_SET_PIN, 0, 0, NEW_PIN));
        time(out, "but it takes the time", SW_OK, SIGNER, T0 + 86_400 + 60);
        owner(out, "the owner's CHANGE_PIN unblocks it, with no old PIN", SW_OK, L_PIN, OWNER, TEST_PIN, p -> changePinCommand(p, TEST_PIN));
        say(out, "unblocked, with the tries back", "exact", SW_OK, info);
        say(out, "the PIN", "exact", SW_OK, verifyOk);

        // the clock's way back: an empty card, a different time key
        owner(out, "the owner takes the limit off, to empty the card", SW_OK, L_LIMIT, OWNER, u32(0), p -> setLimitCommand(p, 0));
        for (int i = 0; i < MAX_PROOFS; i++) {
            byte[] st = slot(i);
            if (st.length == 0 || st[0] != 1) continue;
            say(out, "spend slot " + i, "sig", SW_OK, new CommandAPDU(CLA, INS_SPEND_PROOF, i, 0, 64));
        }
        say(out, "CLEAR_SPENT: the card is empty", "exact", SW_OK, clear);
        owner(out, "owner's proof, a different time key: the clock goes back to nothing", SW_OK, L_CARD, OWNER, record(MINT, REFUND, OTHER_SIGNER.pub),
              p -> setCardCommand(p, record(MINT, REFUND, OTHER_SIGNER.pub)));
        say(out, "the info: no clock, no window, and the limit as it was", "exact", SW_OK, info);
        say(out, "the record, with the new time key", "exact", SW_OK, getCard);
        time(out, "the old signer's time is not the time any more", SW_NOT_THE_TIME, SIGNER, T0 + 3 * DAY);
        time(out, "the new signer's, earlier than the old clock was, is", SW_OK, OTHER_SIGNER, T0 + 100);
        say(out, "the clock is the new one's", "exact", SW_OK, info);

        // the owner replaces itself, and locks
        say(out, "SET_OWNER with no proof", "exact", SW_OWNER_PROOF, setOwnerOpenCommand(OTHER_OWNER));
        say(out, "LOCK_CARD with no proof asked for", "exact", SW_OWNER_PROOF, lockCommand(new byte[8]));
        owner(out, "LOCK_CARD with CHANGE_PIN's proof", SW_OWNER_PROOF, L_PIN, OWNER, new byte[0], p -> lockCommand(p));
        byte[] lockNonce = sayNonce(out);
        say(out, "LOCK_CARD without its byte", "exact", 0x6B00, new CommandAPDU(CLA, INS_LOCK_CARD, 0, 0, ownerData(ownerProof(L_LOCK, OWNER, lockNonce, new byte[0]), new byte[0])));
        owner(out, "LOCK_CARD with the PIN and its own proof", SW_OK, L_LOCK, OWNER, new byte[0], p -> lockCommand(p));
        say(out, "a locked card says so", "exact", SW_OK, info);
        say(out, "and takes no more writes: a load", "exact", SW_NOT_ALLOWED, new CommandAPDU(CLA, INS_LOAD_PROOF, 0, 0, buildProof(KEYSET, 4, 11), 1));
        say(out, "a grant", "exact", SW_NOT_ALLOWED, allowLoadCommand(new byte[8]));
        say(out, "a limit", "exact", SW_NOT_ALLOWED, setLimitCommand(new byte[8], 5));
        say(out, "a PIN", "exact", SW_NOT_ALLOWED, changePinCommand(new byte[8], NEW_PIN));
        say(out, "an owner", "exact", SW_NOT_ALLOWED, setOwnerOpenCommand(OTHER_OWNER));
        say(out, "a record", "exact", SW_NOT_ALLOWED, setCardOpenCommand(record(MINT, REFUND, OTHER_SIGNER.pub)));
        say(out, "a CLEAR_SPENT", "exact", SW_NOT_ALLOWED, clear);
        time(out, "but the time", SW_OK, OTHER_SIGNER, T0 + 200);
        say(out, "and proves it is the card", "auth", SW_OK, new CommandAPDU(CLA, INS_AUTH, 0, 0, new byte[16], 80));

        String text = out.toString();
        text = text.substring(0, text.lastIndexOf(",\n")) + "\n]\n";
        java.nio.file.Path target = appletFolder();
        java.nio.file.Files.createDirectories(target.resolve("target"));
        java.nio.file.Files.write(target.resolve("target").resolve("transcript.json"), text.getBytes(StandardCharsets.UTF_8));
    }

    static String jsonString(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    // =========================================================================
    // A locked card
    // =========================================================================

    @Test
    @DisplayName("A locked card takes no writes, still takes the time, and still pays")
    void testLocked() throws Exception {
        ready();
        assertEquals(SW_OK, load(buildProof(KEYSET, 16, 1)).getSW());
        byte[] n = nonceBytes();
        assertEquals(0x6B00, sw(new CommandAPDU(CLA, INS_LOCK_CARD, 0, 0, ownerData(ownerProof(L_LOCK, OWNER, n, new byte[0]), new byte[0]))),
            "without its confirming byte it is not locked, whatever the proof");
        assertEquals(SW_OK, lock());
        assertEquals(1, info()[10]);
        assertEquals(SW_NOT_ALLOWED, load(buildProof(KEYSET, 16, 2)).getSW());
        assertEquals(SW_NOT_ALLOWED, setLimit(5));
        assertEquals(SW_NOT_ALLOWED, changePin(NEW_PIN));
        assertEquals(SW_NOT_ALLOWED, allowLoad());
        assertEquals(SW_NOT_ALLOWED, setPin(NEW_PIN));
        assertEquals(SW_NOT_ALLOWED, setOwner(OTHER_OWNER));
        assertEquals(SW_NOT_ALLOWED, sw(setLimitByPinCommand(5)));
        assertEquals(SW_NOT_ALLOWED, setCard(MINT, REFUND));
        assertEquals(SW_NOT_ALLOWED, sw(new CommandAPDU(CLA, INS_CLEAR_SPENT, 0, 0, 1)));
        assertEquals(SW_CONDITIONS_NOT_SATIS, lock(), "and it is locked once");
        assertEquals(SW_OK, setTime(T0 + 10), "the time is not a write to what is locked");
        byte[] before = slot(0);
        ResponseAPDU r = spend(0);
        assertEquals(SW_OK, r.getSW());
        assertTrue(signedFor(r.getData(), before, REFUND));
    }

    @Test
    @DisplayName("LOCK_CARD is refused on a fresh card: any reader could once lock it for good")
    void testLockRefusedOnAFreshCard() {
        assertEquals(SW_SECURITY_NOT_SATIS, sw(lockCommand(new byte[8])), "no PIN, no owner");
        assertEquals(SW_SECURITY_NOT_SATIS, sw(new CommandAPDU(CLA, INS_LOCK_CARD, 0, 0xDE)), "the shape it had, with nothing");
        assertEquals(0, info()[10]);
        assertEquals(SW_OK, setOwner(OWNER));
        assertEquals(SW_SECURITY_NOT_SATIS, lock(), "an owner and no PIN");
        assertEquals(0, info()[10]);
        simulator = freshCard();
        assertEquals(SW_OK, setPin(TEST_PIN));
        assertEquals(SW_OK, setOwner(OWNER));
        assertEquals(SW_SECURITY_NOT_SATIS, lock(), "an owner and a PIN that has not been given");
        assertEquals(0, info()[10]);
    }

    @Test
    @DisplayName("LOCK_CARD is refused with the PIN alone, with another command's proof, with a wrong or replayed one, and with no owner")
    void testLockNeedsThePinAndItsOwnProof() {
        assertEquals(SW_OK, setPin(TEST_PIN));
        assertEquals(SW_OK, verify(TEST_PIN));
        assertEquals(SW_NO_OWNER, sw(lockCommand(new byte[8])), "a PIN and no owner");
        assertEquals(0, info()[10]);

        simulator = freshCard();
        ready();
        assertEquals(SW_OWNER_PROOF, sw(lockCommand(new byte[8])), "the PIN alone, no nonce");
        nonceBytes();
        assertEquals(SW_OWNER_PROOF, sw(lockCommand(new byte[8])), "the PIN, a nonce, no proof");
        assertEquals(SW_OWNER_PROOF, sw(new CommandAPDU(CLA, INS_LOCK_CARD, 0, 0xDE)), "the shape it had, with nothing");
        byte[] n = nonceBytes();
        assertEquals(SW_OWNER_PROOF, sw(lockCommand(ownerProof(L_PIN, OWNER, n, new byte[0]))), "a CHANGE_PIN proof");
        n = nonceBytes();
        assertEquals(SW_OWNER_PROOF, sw(lockCommand(ownerProof(L_LIMIT, OWNER, n, new byte[0]))), "a SET_LIMIT proof");
        n = nonceBytes();
        assertEquals(SW_OWNER_PROOF, sw(lockCommand(ownerProof(L_LOAD, OWNER, n, new byte[0]))), "an ALLOW_LOAD proof");
        n = nonceBytes();
        assertEquals(SW_OWNER_PROOF, sw(lockCommand(ownerProof(L_LOCK, OTHER_OWNER, n, new byte[0]))), "another phone's key");
        byte[] n1 = nonceBytes();
        byte[] old = ownerProof(L_LOCK, OWNER, n1, new byte[0]);
        nonceBytes();
        assertEquals(SW_OWNER_PROOF, sw(lockCommand(old)), "a proof for an earlier nonce");
        assertEquals(0, info()[10], "not locked by any of them");
        assertEquals(3, info()[9], "and no try was spent");
        // after a wrong PIN there is no session, and so no lock
        assertEquals(0x63C2, verify(WRONG_PIN));
        assertEquals(SW_SECURITY_NOT_SATIS, lock());
        assertEquals(0, info()[10]);
        assertEquals(SW_OK, verify(TEST_PIN));
        assertEquals(SW_OK, lock(), "the PIN, and its own proof");
        assertEquals(1, info()[10]);
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
     * The runtime is kept, so what no command reaches can be read and set.
     */
    private CardSimulator freshCard() {
        runtime = new ExposedRuntime();
        CardSimulator sim = new CardSimulator(runtime);
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
                 + "\",\"tags\":[";
        if (date != 0) s += "[\"locktime\",\"" + date + "\"],[\"refund\",\"" + toHex(refundKey) + "\"]";
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
