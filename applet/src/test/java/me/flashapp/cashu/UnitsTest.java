package me.flashapp.cashu;

import static me.flashapp.cashu.CashuAppletTest.AID_HEX;
import static me.flashapp.cashu.CashuAppletTest.AID_STR;
import static me.flashapp.cashu.CashuAppletTest.CLA;
import static me.flashapp.cashu.CashuAppletTest.HISTORY_ASKINGS;
import static me.flashapp.cashu.CashuAppletTest.INS_ALLOW_LOAD;
import static me.flashapp.cashu.CashuAppletTest.INS_CLEAR_SPENT;
import static me.flashapp.cashu.CashuAppletTest.INS_GET_BALANCE;
import static me.flashapp.cashu.CashuAppletTest.INS_GET_CARD;
import static me.flashapp.cashu.CashuAppletTest.INS_GET_INFO;
import static me.flashapp.cashu.CashuAppletTest.INS_GET_LOG;
import static me.flashapp.cashu.CashuAppletTest.INS_GET_NONCE;
import static me.flashapp.cashu.CashuAppletTest.INS_GET_PROOF;
import static me.flashapp.cashu.CashuAppletTest.INS_GET_PROOF_COUNT;
import static me.flashapp.cashu.CashuAppletTest.INS_LOAD_PROOF;
import static me.flashapp.cashu.CashuAppletTest.INS_RESET_CARD;
import static me.flashapp.cashu.CashuAppletTest.INS_SET_CARD;
import static me.flashapp.cashu.CashuAppletTest.INS_SET_LIMIT;
import static me.flashapp.cashu.CashuAppletTest.INS_SET_LIMIT_OWNER;
import static me.flashapp.cashu.CashuAppletTest.INS_SET_PIN;
import static me.flashapp.cashu.CashuAppletTest.INS_SPEND_ALL_BEGIN;
import static me.flashapp.cashu.CashuAppletTest.INS_SPEND_ALL_OUTPUTS;
import static me.flashapp.cashu.CashuAppletTest.INS_SPEND_ALL_SIGN;
import static me.flashapp.cashu.CashuAppletTest.INS_VERIFY_PIN;
import static me.flashapp.cashu.CashuAppletTest.L_CARD;
import static me.flashapp.cashu.CashuAppletTest.L_LIMIT;
import static me.flashapp.cashu.CashuAppletTest.L_LOAD;
import static me.flashapp.cashu.CashuAppletTest.L_RESET;
import static me.flashapp.cashu.CashuAppletTest.MINT;
import static me.flashapp.cashu.CashuAppletTest.OWNER;
import static me.flashapp.cashu.CashuAppletTest.REFUND;
import static me.flashapp.cashu.CashuAppletTest.RESET_CONFIRM;
import static me.flashapp.cashu.CashuAppletTest.SW_CARD_IN_USE;
import static me.flashapp.cashu.CashuAppletTest.SW_CONDITIONS_NOT_SATIS;
import static me.flashapp.cashu.CashuAppletTest.SW_KEEP_HOLDING;
import static me.flashapp.cashu.CashuAppletTest.SW_OK;
import static me.flashapp.cashu.CashuAppletTest.SW_OVER_LIMIT;
import static me.flashapp.cashu.CashuAppletTest.SW_OWNER_PROOF;
import static me.flashapp.cashu.CashuAppletTest.SW_PIN_NEEDED;
import static me.flashapp.cashu.CashuAppletTest.SW_SECURITY_NOT_SATIS;
import static me.flashapp.cashu.CashuAppletTest.SW_WRONG_DATA;
import static me.flashapp.cashu.CashuAppletTest.SW_WRONG_LENGTH;
import static me.flashapp.cashu.CashuAppletTest.T0;
import static me.flashapp.cashu.CashuAppletTest.TEST_PIN;
import static me.flashapp.cashu.CashuAppletTest.WAITS_MOST;
import static me.flashapp.cashu.CashuAppletTest.buildProof;
import static me.flashapp.cashu.CashuAppletTest.changeCommand;
import static me.flashapp.cashu.CashuAppletTest.claim;
import static me.flashapp.cashu.CashuAppletTest.concat;
import static me.flashapp.cashu.CashuAppletTest.dleqProof;
import static me.flashapp.cashu.CashuAppletTest.getDleqCommand;
import static me.flashapp.cashu.CashuAppletTest.hexToBytes;
import static me.flashapp.cashu.CashuAppletTest.loadChangeCommand;
import static me.flashapp.cashu.CashuAppletTest.mintAnswer;
import static me.flashapp.cashu.CashuAppletTest.mintKey;
import static me.flashapp.cashu.CashuAppletTest.ownerData;
import static me.flashapp.cashu.CashuAppletTest.ownerProof;
import static me.flashapp.cashu.CashuAppletTest.readUint32;
import static me.flashapp.cashu.CashuAppletTest.record;
import static me.flashapp.cashu.CashuAppletTest.setTimeCommand;
import static me.flashapp.cashu.CashuAppletTest.u32;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.licel.jcardsim.smartcardio.CardSimulator;
import com.licel.jcardsim.utils.AIDUtil;
import javacard.framework.AID;
import javax.smartcardio.CommandAPDU;
import javax.smartcardio.ResponseAPDU;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Dollars and sats on one card (software 1.23, docs/FOXY-CARD-KEYSETS.md): the table of keysets and their units, kept in the book
 * the first SET_CARD reserves; the labelled load and the table's rules at every load; one unit a payment; every limit in cents, a
 * payment in sats turned into cents at the rate the owner's phone writes with the limits; the log and the receipts saying each
 * payment's unit; and a card of one unit answering as 1.22 did, to the byte, but for the version.
 *
 * U1 to U34 are the design's (10.6), in its order, each under its own name; U35 and U36 came of the review of the build.
 */
class UnitsTest {

    /** Keysets the tests' mint is said to issue: two of sats and three of dollars, and more of either for a full table. */
    static final String SATS_A = "00aa000000000001", SATS_B = "00aa000000000002", SATS_C = "00aa000000000003";
    static final String USD_A = "00bb000000000001", USD_B = "00bb000000000002", USD_C = "00bb000000000003";
    static final int SATS = 0, USD = 1;
    static final int SW_NO_RATE = 0x6A99, SW_TWO_UNITS = 0x6A9A, SW_OTHER_UNIT = 0x6A9B, SW_TABLE_FULL = 0x6A9C, SW_NOT_THIS_TAP = 0x6A9D;
    static final int SW_WRONG_P1P2 = 0x6B00, SW_INCORRECT_P1P2 = 0x6A86;

    @BeforeEach
    void theTestFloor() { CashuAppletTest.useTheTestFloor(); }

    @AfterAll
    static void theBuiltFloor() { CashuAppletTest.useFloor(CashuAppletTest.BUILT_FLOOR); }

    // =========================================================================
    // A card, and what a test does to it
    // =========================================================================

    static final class Card {
        final CashuAppletTest.ExposedRuntime runtime = new CashuAppletTest.ExposedRuntime();
        final CardSimulator sim = new CardSimulator(runtime);
        final AID aid = AIDUtil.create(AID_HEX);
        private int seed = 1;

        Card() {
            sim.installApplet(aid, CashuApplet.class);
            select();
        }

        ResponseAPDU send(CommandAPDU c) { return sim.transmitCommand(c); }
        int sw(CommandAPDU c) { return send(c).getSW(); }
        void select() { assertEquals(SW_OK, sw(new CommandAPDU(0x00, 0xA4, 0x04, 0x00, hexToBytes(AID_STR)))); }
        /** Taken out of the field and put back: a new tap, nothing given in it. */
        void power() { sim.reset(); select(); }
        void pin() { assertEquals(SW_OK, sw(new CommandAPDU(CLA, INS_VERIFY_PIN, 0, 0, TEST_PIN))); }
        /** A new tap with the PIN given. */
        void tap() { power(); pin(); }

        /** Set up as a phone sets a card up, in `unit`: a PIN, the record, the owner (where `owner`), a clock; then a new tap with the PIN. */
        Card ready(int unit, boolean owner) {
            assertEquals(SW_OK, sw(new CommandAPDU(CLA, INS_SET_PIN, 0, 0, TEST_PIN)));
            pin();
            assertEquals(SW_OK, sw(new CommandAPDU(CLA, INS_SET_CARD, 0, 0, recordIn(unit))));
            if (owner) assertEquals(SW_OK, claim(sim::transmitCommand, OWNER).getSW());
            assertEquals(SW_OK, sw(setTimeCommand(T0)));
            tap();
            return this;
        }

        /** The owner's proof over `value` under `label`, after a nonce asked for in this tap. */
        byte[] proofFor(String label, byte[] value) {
            ResponseAPDU n = send(new CommandAPDU(CLA, INS_GET_NONCE, 0, 0, 16));
            assertEquals(SW_OK, n.getSW());
            return ownerProof(label, OWNER, n.getData(), value);
        }
        /** The owner's grant in this tap (ALLOW_LOAD): the holder's say on a card with an owner. */
        void grant() { assertEquals(SW_OK, sw(new CommandAPDU(CLA, INS_ALLOW_LOAD, 0, 0, ownerData(proofFor(L_LOAD, new byte[0]), new byte[0])))); }
        /** The history held for under the PIN on a card with no owner: the holder's say there. */
        void hold() {
            for (int asked = 1; asked < HISTORY_ASKINGS; asked++) assertEquals(SW_KEEP_HOLDING, sw(new CommandAPDU(CLA, INS_GET_LOG, 0, 0, 256)));
            assertEquals(SW_OK, sw(new CommandAPDU(CLA, INS_GET_LOG, 0, 0, 256)));
        }
        /** SET_LIMIT by the owner, sixteen bytes: the three figures in cents and the rate. */
        int limits(long day, long tap, long allowance, long rate) {
            byte[] v = concat(u32(day), u32(tap), u32(allowance), u32(rate));
            return sw(new CommandAPDU(CLA, INS_SET_LIMIT_OWNER, 0, 0, ownerData(proofFor(L_LIMIT, v), v)));
        }
        /** SET_LIMIT by the PIN (a card with no owner), twelve bytes: the limit on one payment, the allowance, the rate. */
        int pinLimits(long tap, long allowance, long rate) {
            return sw(new CommandAPDU(CLA, INS_SET_LIMIT, 0, 0, concat(u32(tap), u32(allowance), u32(rate))));
        }

        /** A piece of `keyset` worth `amount`, with a nonce of its own. */
        byte[] piece(String keyset, long amount) {
            byte[] p = buildProof(keyset, amount, seed & 0xFF);
            System.arraycopy(u32(seed++), 0, p, 12, 4);
            return p;
        }
        ResponseAPDU load(byte[]... pieces) { return send(new CommandAPDU(CLA, INS_LOAD_PROOF, 0, 0, concat(pieces), 256)); }
        ResponseAPDU loadAs(int unit, byte[]... pieces) { return send(new CommandAPDU(CLA, INS_LOAD_PROOF, 0, 1, concat(new byte[] { (byte) unit }, concat(pieces)), 256)); }
        ResponseAPDU loadAsWithProof(int unit, byte[] piece, byte[] proof) { return send(new CommandAPDU(CLA, INS_LOAD_PROOF, 1, 1, concat(new byte[] { (byte) unit }, piece, proof), 256)); }
        /** A piece put on and its place: loaded, under whatever the tap has, labelled `unit` (or plain, -1). */
        int put(int unit, String keyset, long amount) {
            ResponseAPDU r = unit < 0 ? load(piece(keyset, amount)) : loadAs(unit, piece(keyset, amount));
            assertEquals(SW_OK, r.getSW(), "the load of " + keyset);
            return r.getData()[0] & 0xFF;
        }

        ResponseAPDU begin(int... places) {
            byte[] list = new byte[places.length];
            for (int i = 0; i < places.length; i++) list[i] = (byte) places[i];
            return send(new CommandAPDU(CLA, INS_SPEND_ALL_BEGIN, 0, 0, list, 4));
        }
        /** How many "not yet" the last sign() was given. */
        int waits;
        ResponseAPDU sign() {
            waits = 0;
            for (int i = 0; i <= WAITS_MOST; i++) {
                ResponseAPDU r = send(new CommandAPDU(CLA, INS_SPEND_ALL_SIGN, 0, 0, 64));
                if (!(r.getSW() == SW_OK && r.getData().length == 2)) return r;
                waits++;
            }
            fail("a payment still waiting past the most");
            return null;
        }
        ResponseAPDU pay(int... places) {
            ResponseAPDU begun = begin(places);
            if (begun.getSW() != SW_OK) return begun;
            return sign();
        }
        /** A payment of those places, `change` of it kept as the card's own change: the signature, and the opening's blinded message in `changeOut[0]`. */
        ResponseAPDU payWithChange(long change, byte[][] changeOut, int... places) {
            ResponseAPDU begun = begin(places);
            if (begun.getSW() != SW_OK) return begun;
            ResponseAPDU c = send(changeCommand(change, mintKey()));
            assertEquals(SW_OK, c.getSW(), "the change asked for");
            changeOut[0] = c.getData();
            return sign();
        }

        byte[] info(int p1) { return send(new CommandAPDU(CLA, INS_GET_INFO, p1, 0, 256)).getData(); }
        byte[] getCard(int p1) { return send(new CommandAPDU(CLA, INS_GET_CARD, p1, 0, 256)).getData(); }
        byte[] balance(int p1) { return send(new CommandAPDU(CLA, INS_GET_BALANCE, p1, 0, 256)).getData(); }
        ResponseAPDU log(int p1) { return send(new CommandAPDU(CLA, INS_GET_LOG, p1, 0, 256)); }
        int count() { return send(new CommandAPDU(CLA, INS_GET_PROOF_COUNT, 0, 0, 1)).getData()[0] & 0xFF; }
        int clearSpent() { return sw(new CommandAPDU(CLA, INS_CLEAR_SPENT, 0, 0, 1)); }
        byte[] slot(int place) { return send(new CommandAPDU(CLA, INS_GET_PROOF, place, 0, 256)).getData(); }

        byte[] field(String name) throws Exception {
            java.lang.reflect.Field f = CashuApplet.class.getDeclaredField(name);
            f.setAccessible(true);
            return (byte[]) f.get(runtime.appletAt(aid));
        }
        void setField(String name, byte[] value) throws Exception {
            java.lang.reflect.Field f = CashuApplet.class.getDeclaredField(name);
            f.setAccessible(true);
            f.set(runtime.appletAt(aid), value);
        }
        byte[] book() throws Exception { return field("book"); }
        byte[] record() throws Exception { return field("cardRecord"); }
        /** The state byte of the entry whose keyset is `keyset`, or -1 where none is in use. */
        int stateOf(String keyset) throws Exception {
            byte[] b = book(), k = hexToBytes(keyset);
            for (int e = 0; e < 6; e++) {
                if ((b[e * 9 + 8] & 0x40) != 0 && Arrays.equals(Arrays.copyOfRange(b, e * 9, e * 9 + 8), k)) return b[e * 9 + 8] & 0xFF;
            }
            return -1;
        }
        /** Which entry holds `keyset`, or -1. */
        int entryOf(String keyset) throws Exception {
            byte[] b = book(), k = hexToBytes(keyset);
            for (int e = 0; e < 6; e++) {
                if ((b[e * 9 + 8] & 0x40) != 0 && Arrays.equals(Arrays.copyOfRange(b, e * 9, e * 9 + 8), k)) return e;
            }
            return -1;
        }
        int inUse() throws Exception {
            int n = 0;
            byte[] b = book();
            for (int e = 0; e < 6; e++) if ((b[e * 9 + 8] & 0x40) != 0) n++;
            return n;
        }
        long rate() throws Exception { return readUint32(record(), CashuApplet.CARD_RATE_OFFSET); }
        long spentToday() { return readUint32(info(0), 25); }
        long noPinSpent() { return readUint32(info(1), 38); }
        /** Every array of the card that lasts, copied. */
        Map<String, byte[]> snapshot() throws Exception {
            Map<String, byte[]> m = new HashMap<>();
            for (String n : new String[] { "proofStorage", "slotHex", "dleq", "book", "cardRecord", "cardLog", "cardReceipts", "openState", "changeOpen" }) {
                byte[] a = field(n);
                m.put(n, a == null ? null : a.clone());
            }
            return m;
        }
        void assertSame(Map<String, byte[]> before, String what) throws Exception {
            for (Map.Entry<String, byte[]> e : before.entrySet()) assertArrayEquals(e.getValue(), field(e.getKey()), what + ": " + e.getKey());
        }
    }

    /** SET_CARD's data in `unit`: the record a phone writes, its first byte the unit. */
    static byte[] recordIn(int unit) {
        byte[] r = record(MINT, REFUND);
        r[0] = (byte) unit;
        return r;
    }
    static Card owned(int unit) { return new Card().ready(unit, true); }
    static Card phoneless(int unit) { return new Card().ready(unit, false); }

    /** ceil(S x 100 / R), as the card works it out, and past four bytes FF FF FF FF. */
    static long cents(long sats, long rate) {
        BigInteger q = BigInteger.valueOf(sats).multiply(BigInteger.valueOf(100));
        BigInteger[] qr = q.divideAndRemainder(BigInteger.valueOf(rate));
        BigInteger c = qr[1].signum() == 0 ? qr[0] : qr[0].add(BigInteger.ONE);
        return c.bitLength() > 32 ? 0xFFFFFFFFL : c.longValue();
    }

    /** The applet's own conversion (`toCents`), run on its own scratch with a rate written into its record. */
    static long cardCents(Card c, long sats, long rate) throws Exception {
        byte[] rec = c.record(), scratch = c.field("scratch");
        System.arraycopy(u32(rate), 0, rec, CashuApplet.CARD_RATE_OFFSET, 4);
        System.arraycopy(u32(sats), 0, scratch, 0, 4);
        java.lang.reflect.Method m = CashuApplet.class.getDeclaredMethod("toCents", short.class);
        m.setAccessible(true);
        m.invoke(c.runtime.appletAt(c.aid), (short) 0);
        return readUint32(scratch, 0);
    }

    // =========================================================================
    // U1 to U14: the book and the table
    // =========================================================================

    @Test
    @DisplayName("U1 The book is not made at install and is made by the first SET_CARD: null after install, after a PIN and after a refused SET_CARD; 75 zero bytes after the first that goes through; the same array after every later one")
    void theBookIsNotMadeAtInstallAndIsMadeByTheFirstSetCard() throws Exception {
        Card c = new Card();
        assertNull(c.book(), "the chip will not have it in the constructor: it is not made at install");
        assertEquals(SW_OK, c.sw(new CommandAPDU(CLA, INS_SET_PIN, 0, 0, TEST_PIN)));
        c.pin();
        assertNull(c.book(), "a PIN makes nothing");
        assertEquals(SW_WRONG_LENGTH, c.sw(new CommandAPDU(CLA, INS_SET_CARD, 0, 0, new byte[10])), "a SET_CARD refused");
        assertNull(c.book(), "makes nothing");
        assertEquals(SW_WRONG_DATA, c.sw(new CommandAPDU(CLA, INS_SET_CARD, 0, 0, recordIn(2))), "nor one of a unit the card does not know");
        assertNull(c.book());
        assertEquals(SW_OK, c.sw(new CommandAPDU(CLA, INS_SET_CARD, 0, 0, recordIn(SATS))));
        byte[] book = c.book();
        assertNotNull(book, "the first SET_CARD that goes through makes it");
        assertEquals(75, book.length, "the table (54), the last unit (1), the other unit's count (4), the receipts' units (16)");
        assertArrayEquals(new byte[75], book, "all zeros: every entry free");
        assertEquals(SW_OK, c.sw(new CommandAPDU(CLA, INS_SET_CARD, 0, 0, recordIn(USD))));
        assertSame(book, c.book(), "a second SET_CARD makes no other");
        assertEquals(SW_OK, claim(c.sim::transmitCommand, OWNER).getSW());
        c.tap();
        byte[] rec = recordIn(SATS);
        assertEquals(SW_OK, c.sw(new CommandAPDU(CLA, INS_SET_CARD, 0, 0, ownerData(c.proofFor(L_CARD, rec), rec))));
        assertSame(book, c.book(), "nor the owner's");
    }

    @Test
    @DisplayName("U2 A card refused its book is a card of one unit: plain loads as the record's unit, a load labelled the other unit 6A9C, byte 8 is 4, P1 = 3 byte 43 and GET_CARD P1 = 2 are 00, and a rate is still taken and read back")
    void aCardRefusedItsBookIsAOneUnitCard() throws Exception {
        Card c = owned(SATS);
        // jCardSim cannot be made to refuse an allocation: the book is left null, as the catch around it leaves it on a chip that refuses the room
        c.setField("book", null);
        assertEquals(SW_OK, c.load(c.piece(SATS_A, 10)).getSW(), "a plain load: the record's unit");
        assertEquals(SW_OK, c.loadAs(SATS, c.piece(SATS_B, 20)).getSW(), "a load labelled the record's unit is a plain one");
        c.grant();
        Map<String, byte[]> before = c.snapshot();
        assertEquals(SW_TABLE_FULL, c.loadAs(USD, c.piece(USD_A, 5)).getSW(), "the other unit has no table to go in, even with the holder's say");
        c.assertSame(before, "and nothing is written");
        assertEquals(4, c.info(0)[8], "a card of one unit");
        byte[] three = c.info(3);
        assertEquals(51, three.length);
        assertEquals(0, three[43], "no book");
        assertEquals(0, three[48]);
        assertEquals(0, three[49]);
        assertEquals(0, three[50]);
        assertArrayEquals(new byte[] { 0 }, c.getCard(2), "and no table");
        assertEquals(4, c.getCard(0)[0]);
        assertArrayEquals(new byte[] { 1, 0, 2, 0, 0, 0, 30 }, c.balance(1), "one unit, sats: two pieces, 30");
        assertEquals(SW_OK, c.limits(0, 100, 0, 1000), "a rate is still taken: it lives in the record");
        c.grant();
        assertEquals(1000, readUint32(c.info(3), 44), "and read back");
        c.tap();
        assertEquals(SW_OK, c.pay(0).getSW(), "and a payment in sats is weighed with it: 10 sats, 1 cent");
        assertEquals(0, c.waits);
        assertNull(c.book(), "none of that made it");
    }

    @Test
    @DisplayName("U3 A plain load enters its keyset as the record's unit: 40 on a sats card and 41 on a dollars card under the PIN alone; C0 and C1 with the grant too; a second piece of the keyset writes no entry")
    void aPlainLoadEntersItsKeysetAsTheRecordsUnit() throws Exception {
        Card s = owned(SATS);
        s.put(-1, SATS_A, 10);
        assertEquals(0x40, s.stateOf(SATS_A), "sats, entered with no say");
        byte[] book = s.book().clone();
        s.put(-1, SATS_A, 20);
        assertArrayEquals(book, s.book(), "a second piece of it writes nothing in the table");
        s.grant();
        s.put(-1, SATS_B, 5);
        assertEquals(0xC0, s.stateOf(SATS_B), "with the grant: marked");
        Card d = owned(USD);
        d.put(-1, USD_A, 10);
        assertEquals(0x41, d.stateOf(USD_A), "dollars, on a dollars card");
        d.grant();
        d.put(-1, USD_B, 10);
        assertEquals(0xC1, d.stateOf(USD_B));
        assertArrayEquals(Arrays.copyOfRange(d.book(), 0, 8), hexToBytes(USD_A), "the keyset's eight bytes, as the piece holds them");
        // and on a card with no owner, the held tap is the say
        Card p = phoneless(SATS);
        p.put(-1, SATS_A, 10);
        assertEquals(0x40, p.stateOf(SATS_A));
        p.hold();
        p.put(-1, SATS_B, 10);
        assertEquals(0xC0, p.stateOf(SATS_B));
    }

    @Test
    @DisplayName("U4 The other unit needs the holder's say: a new keyset labelled the other unit is 6A9D under the PIN alone, nothing written; entered marked under the PIN and the grant; on a card with no owner, 6A9D under the PIN alone and entered marked in a held tap")
    void theOtherUnitNeedsTheHoldersSay() throws Exception {
        Card c = owned(SATS);
        c.put(-1, SATS_A, 10);
        Map<String, byte[]> before = c.snapshot();
        assertEquals(SW_NOT_THIS_TAP, c.loadAs(USD, c.piece(USD_A, 5)).getSW(), "the PIN alone: not this tap's to start");
        assertEquals(SW_NOT_THIS_TAP, c.loadAsWithProof(USD, c.piece(USD_A, 5), dleqProof(3)).getSW(), "with the mint's proof as well");
        c.assertSame(before, "nothing written");
        assertEquals(1, c.count());
        c.grant();
        c.put(USD, USD_A, 5);
        assertEquals(0xC1, c.stateOf(USD_A), "the PIN and the owner's grant: entered, marked");
        // a card with no owner
        Card p = phoneless(USD);
        p.put(-1, USD_A, 10);
        before = p.snapshot();
        assertEquals(SW_NOT_THIS_TAP, p.loadAs(SATS, p.piece(SATS_A, 5)).getSW(), "the PIN alone");
        p.assertSame(before, "nothing written");
        p.hold();
        p.put(SATS, SATS_A, 5);
        assertEquals(0xC0, p.stateOf(SATS_A), "in a held tap: entered, marked");
    }

    @Test
    @DisplayName("U5 A till may enter a new keyset of the last payment's unit: after a dollar payment on a sats card, the PIN alone enters a new dollar keyset, unmarked; after a sats payment it is 6A9D; after SET_CARD the last unit is gone")
    void aTillMayEnterANewKeysetOfTheLastPaymentsUnit() throws Exception {
        Card c = owned(SATS);
        c.grant();
        int usd = c.put(USD, USD_A, 50);
        int sats = c.put(-1, SATS_A, 70);
        c.tap();
        assertEquals(SW_OK, c.pay(usd).getSW(), "a dollar payment");
        assertEquals(0xA0, c.info(3)[50] & 0xFF, "the last payment's unit: the other");
        c.tap();
        c.put(USD, USD_B, 50);
        assertEquals(0x41, c.stateOf(USD_B), "a refund in the mint's new keyset, under the PIN alone: entered, unmarked");
        assertEquals(SW_OK, c.pay(sats).getSW(), "a sats payment");
        assertEquals(0x80, c.info(3)[50] & 0xFF, "the last payment's unit: the record's");
        c.tap();
        Map<String, byte[]> before = c.snapshot();
        assertEquals(SW_NOT_THIS_TAP, c.loadAs(USD, c.piece(USD_C, 5)).getSW(), "a new dollar keyset under the PIN alone, after a sats payment");
        c.assertSame(before, "nothing written");
        // emptied, and SET_CARD lets the last unit go
        int left = 0;
        for (int p = 0; p < 128; p++) if (c.slot(p).length == 82 && c.slot(p)[0] == 1) left = p;
        assertEquals(SW_OK, c.pay(left).getSW());
        assertEquals(SW_OK, c.clearSpent());
        byte[] rec = recordIn(SATS);
        assertEquals(SW_OK, c.sw(new CommandAPDU(CLA, INS_SET_CARD, 0, 0, ownerData(c.proofFor(L_CARD, rec), rec))));
        assertEquals(0, c.info(3)[50], "SET_CARD lets the last payment's unit go");
        assertEquals(SW_NOT_THIS_TAP, c.loadAs(USD, c.piece(USD_C, 5)).getSW(), "and with it the till's door to the other unit");
    }

    @Test
    @DisplayName("U6 A keyset is never entered as both units: a piece of an entered keyset labelled the other unit is 6A9B, plain or labelled, with the proof or without; nothing written, the count the same")
    void aKeysetIsNeverEnteredAsBothUnits() throws Exception {
        Card c = owned(SATS);
        c.grant();
        c.put(-1, SATS_A, 10);
        c.put(USD, USD_A, 10);
        Map<String, byte[]> before = c.snapshot();
        assertEquals(SW_OTHER_UNIT, c.loadAs(USD, c.piece(SATS_A, 5)).getSW(), "a sats keyset labelled dollars");
        assertEquals(SW_OTHER_UNIT, c.loadAsWithProof(USD, c.piece(SATS_A, 5), dleqProof(1)).getSW(), "and with the proof");
        assertEquals(SW_OTHER_UNIT, c.load(c.piece(USD_A, 5)).getSW(), "a dollar keyset loaded plain on a sats card: the record's unit, which it is not");
        assertEquals(SW_OTHER_UNIT, c.loadAs(SATS, c.piece(USD_A, 5)).getSW(), "or labelled sats");
        assertEquals(SW_OTHER_UNIT, c.send(CashuAppletTest.loadWithProofCommand(c.piece(USD_A, 5), dleqProof(2))).getSW(), "or plain with the proof");
        c.assertSame(before, "nothing written");
        assertEquals(2, c.count());
        // a later piece of a command is held to it too: the pieces before it stand
        ResponseAPDU two = c.loadAs(USD, c.piece(USD_A, 3), c.piece(SATS_A, 4));
        assertEquals(SW_OK, two.getSW());
        assertEquals(1, two.getData().length, "the first went on, the second was refused: the answer is the first's place");
    }

    @Test
    @DisplayName("U7 A bad label is refused before anything is written: unit 02 is 6A80; P2 = 2 is 6B00; 82, 163, 244 and (P1 = 1) 178 are taken, every other labelled length 6700")
    void aBadLabelIsRefusedBeforeAnythingIsWritten() throws Exception {
        Card c = owned(SATS);
        c.grant();
        Map<String, byte[]> before = c.snapshot();
        for (int unit : new int[] { 2, 3, 0x7F, 0x80, 0xFF }) {
            assertEquals(SW_WRONG_DATA, c.loadAs(unit, c.piece(SATS_A, 5)).getSW(), "unit " + unit);
        }
        for (int p2 : new int[] { 2, 3, 0x80, 0xFF }) {
            assertEquals(SW_WRONG_P1P2, c.sw(new CommandAPDU(CLA, INS_LOAD_PROOF, 0, p2, concat(new byte[] { 0 }, c.piece(SATS_A, 5)), 256)), "P2 = " + p2);
            assertEquals(SW_WRONG_P1P2, c.sw(new CommandAPDU(CLA, INS_LOAD_PROOF, 0, p2, new byte[7], 256)), "P2 = " + p2 + " comes before the length");
        }
        for (int n : new int[] { 1, 2, 81, 83, 162, 164, 243, 245 }) {
            assertEquals(SW_WRONG_LENGTH, c.sw(new CommandAPDU(CLA, INS_LOAD_PROOF, 0, 1, new byte[n], 256)), n + " bytes labelled");
        }
        for (int n : new int[] { 82, 177, 179 }) {
            assertEquals(SW_WRONG_LENGTH, c.sw(new CommandAPDU(CLA, INS_LOAD_PROOF, 1, 1, new byte[n], 256)), n + " bytes labelled with the proof");
        }
        c.assertSame(before, "none of them wrote anything");
        assertEquals(SW_OK, c.loadAs(SATS, c.piece(SATS_A, 1)).getSW(), "82");
        assertEquals(SW_OK, c.loadAs(SATS, c.piece(SATS_A, 1), c.piece(SATS_A, 1)).getSW(), "163");
        assertEquals(SW_OK, c.loadAs(SATS, c.piece(SATS_A, 1), c.piece(SATS_A, 1), c.piece(SATS_A, 1)).getSW(), "244");
        assertEquals(SW_OK, c.loadAsWithProof(SATS, c.piece(SATS_A, 1), dleqProof(5)).getSW(), "178");
        assertEquals(7, c.count());
    }

    @Test
    @DisplayName("U8 A labelled load reads its pieces one byte on: P1 = 1, P2 = 1, the proof read back is the one sent after the label; three labelled pieces each hold their own 81 bytes; the log's put on is the pieces' sum, not a sum read a byte off")
    void aLabelledLoadReadsItsPiecesOneByteOn() throws Exception {
        Card c = owned(USD);
        byte[] piece = c.piece(USD_A, 77);
        byte[] proof = dleqProof(11);
        ResponseAPDU r = c.loadAsWithProof(USD, piece, proof);
        assertArrayEquals(new byte[] { 0, 1 }, r.getData(), "place 0, its proof kept");
        assertArrayEquals(concat(new byte[] { 1 }, proof), c.send(getDleqCommand(0, 255)).getData(), "the proof, byte for byte");
        assertArrayEquals(concat(new byte[] { 1 }, piece), c.slot(0), "and the piece");
        byte[][] three = { c.piece(USD_A, 0x01020304L), c.piece(USD_A, 0x00000506L), c.piece(USD_A, 0x00000007L) };
        ResponseAPDU t = c.loadAs(USD, three);
        assertArrayEquals(new byte[] { 1, 2, 3 }, t.getData());
        for (int i = 0; i < 3; i++) assertArrayEquals(concat(new byte[] { 1 }, three[i]), c.slot(1 + i), "piece " + i + " whole, in its own place");
        c.grant();
        byte[] log = c.log(0).getData();
        assertEquals(4, log[16 + 11], "four pieces put on in the tap's entry");
        assertEquals(77 + 0x01020304L + 0x506 + 7, readUint32(log, 16 + 12), "and what they are worth, read where each piece's amount is");
    }

    @Test
    @DisplayName("U9 The table holds six and reclaims the dead first: a seventh keyset with every entry live is 6A9C; with one dead it goes in, in the dead one's place")
    void theTableHoldsSixAndReclaimsTheDeadFirst() throws Exception {
        Card c = owned(SATS);
        c.grant();
        String[] six = { SATS_A, SATS_B, SATS_C, USD_A, USD_B, USD_C };
        int[] places = new int[6];
        for (int i = 0; i < 6; i++) places[i] = c.put(i < 3 ? SATS : USD, six[i], 10 + i);
        assertEquals(6, c.inUse());
        Map<String, byte[]> before = c.snapshot();
        assertEquals(SW_TABLE_FULL, c.loadAs(SATS, c.piece("00aa000000000009", 1)).getSW(), "a seventh, every entry live");
        c.assertSame(before, "nothing written, not even by the reclaim, which found nothing dead");
        int dead = c.entryOf(SATS_B);
        assertEquals(SW_OK, c.pay(places[1]).getSW(), "SATS_B's one piece paid: its entry dead, and not yet let go");
        assertEquals(6, c.inUse());
        c.tap();
        c.grant();
        c.put(SATS, "00aa000000000009", 1);
        assertEquals(dead, c.entryOf("00aa000000000009"), "the seventh goes in, in the dead one's place");
        assertEquals(-1, c.entryOf(SATS_B));
    }

    @Test
    @DisplayName("U10 No more than two live entries without the mark: a third unmarked is 6A9C; marked ones still go in; with an unmarked one dead, a new unmarked keyset goes in after the reclaim; a load of a keyset already entered runs no reclaim")
    void noMoreThanTwoLiveEntriesWithoutTheMark() throws Exception {
        Card c = owned(SATS);
        int a = c.put(-1, SATS_A, 10);
        int b = c.put(-1, SATS_B, 10);
        Map<String, byte[]> before = c.snapshot();
        assertEquals(SW_TABLE_FULL, c.load(c.piece(SATS_C, 10)).getSW(), "a third without the mark, the table half empty");
        c.assertSame(before, "nothing written");
        c.grant();
        c.put(-1, SATS_C, 10);
        assertEquals(0xC0, c.stateOf(SATS_C), "a marked one still goes in");
        assertEquals(SW_OK, c.pay(b).getSW(), "SATS_B's piece paid: an unmarked entry dead");
        c.tap();
        c.put(-1, "00aa000000000007", 10);
        assertEquals(-1, c.entryOf(SATS_B), "the reclaim ran: the dead one let go");
        assertEquals(0x40, c.stateOf("00aa000000000007"), "and the new one went in, unmarked");
        // a load of a keyset already entered runs no reclaim: an entry made dead stays in use through it
        assertEquals(SW_OK, c.pay(a).getSW(), "SATS_A's piece paid: dead");
        c.tap();
        c.put(-1, SATS_C, 5);
        assertEquals(0x40, c.stateOf(SATS_A), "a load of an entered keyset looked at nothing but its entry");
        c.grant();
        c.put(SATS, "00aa00000000000a", 5);
        assertEquals(0x40, c.stateOf(SATS_A), "nor does a new marked keyset with entries free and the cap not in the way: the pass over the places is paid only where the cheap count says the table may be full");
    }

    @Test
    @DisplayName("U11 CLEAR_SPENT frees what no piece and no opening names: after a payment of every piece of a keyset its entry is free; one named by an owed opening is not; a drafted opening keeps it until the next BEGIN")
    void clearSpentFreesWhatNoPieceAndNoOpeningNames() throws Exception {
        Card c = owned(SATS);
        c.grant();
        int u = c.put(USD, USD_A, 30);
        int s = c.put(-1, SATS_A, 100);
        assertEquals(5, c.info(0)[8], "both units");
        c.tap();
        assertEquals(SW_OK, c.pay(u).getSW());
        assertEquals(0xC1, c.stateOf(USD_A), "dead, and not let go by the payment");
        assertEquals(SW_OK, c.clearSpent());
        assertEquals(-1, c.stateOf(USD_A), "CLEAR_SPENT freed it");
        assertEquals(4, c.info(0)[8], "and the card is of one unit again");
        // a payment of every piece of SATS_A with change: the opening owed names the keyset
        c.tap();
        byte[][] b = new byte[1][];
        assertEquals(SW_OK, c.payWithChange(40, b, s).getSW());
        assertEquals(SW_OK, c.clearSpent());
        assertEquals(0xC0, c.stateOf(SATS_A), "an opening owed keeps it");
        assertEquals(SW_OK, c.sw(loadChangeCommand(0, mintAnswer(b[0]))), "the change back");
        assertEquals(0xC0, c.stateOf(SATS_A), "and the piece keeps it now");
        // a drafted opening, its keyset's pieces all spent by hand (a state no command makes): it keeps the entry until the next BEGIN
        int back = 0;
        for (int p = 0; p < 128; p++) if (c.slot(p).length == 82 && c.slot(p)[0] == 1) back = p;
        c.tap();
        c.grant();
        int other = c.put(USD, USD_A, 9);
        assertEquals(SW_OK, c.begin(back).getSW());
        assertEquals(SW_OK, c.send(changeCommand(10, mintKey())).getSW(), "drafted");
        c.field("proofStorage")[back * 82] = 2;
        assertEquals(SW_OK, c.clearSpent());
        assertEquals(0xC0, c.stateOf(SATS_A), "the drafted opening keeps it");
        assertEquals(SW_OK, c.begin(other).getSW(), "the next BEGIN lets the draft go");
        assertEquals(SW_OK, c.clearSpent());
        assertEquals(-1, c.stateOf(SATS_A), "and the next CLEAR_SPENT frees the entry");
    }

    @Test
    @DisplayName("U12 A torn entry is freed and never serves: the state byte is written last; an entry with no piece, and a piece whose status byte is not written, are let go by the next reclaim; the format reads 4 again after it")
    void aTornEntryIsFreedAndNeverServes() throws Exception {
        String code = CashuAppletTestSource.code();
        String loadOne = CashuAppletTestSource.body(code, "private short loadOne(", "private void processClearSpent(");
        int nonce = loadOne.indexOf("if (slotWithNonce(buf");
        int keyset = loadOne.indexOf("Util.arrayCopyNonAtomic(buf, in, book, at, (short) 8);");
        int state = loadOne.indexOf("book[(short)(at + ENTRY_STATE)] = state;");
        int hex = loadOne.indexOf("Util.arrayCopyNonAtomic(scratch, X_HEX, slotHex");
        int data = loadOne.indexOf("Util.arrayCopy(buf, in, proofStorage");
        assertTrue(nonce < keyset && keyset < state && state < hex && hex < data, "after the last check, the keyset's eight bytes, then its state byte, then the place's text and data");
        Card c = owned(SATS);
        c.put(-1, SATS_A, 10);
        // torn after the entry's state byte, before the place: an entry of the other unit that nothing names
        byte[] book = c.book();
        System.arraycopy(hexToBytes(USD_A), 0, book, 9, 8);
        book[17] = (byte) 0xC1;
        assertEquals(5, c.info(0)[8], "an entry of the other unit in use: format 5 while it stands");
        // torn after the place's data, before its status byte: a place that is empty, its bytes there
        System.arraycopy(hexToBytes(USD_B), 0, book, 18, 8);
        book[26] = (byte) 0x41;
        byte[] ps = c.field("proofStorage");
        System.arraycopy(c.piece(USD_B, 5), 0, ps, 5 * 82 + 1, 81);
        assertEquals(0, ps[5 * 82]);
        assertArrayEquals(new byte[] { 1, 0, 1, 0, 0, 0, 10 }, c.balance(1), "neither is counted: the money there is the sats piece's");
        assertEquals(SW_OK, c.clearSpent());
        assertEquals(-1, c.stateOf(USD_A), "the entry with no piece let go");
        assertEquals(-1, c.stateOf(USD_B), "and the one whose piece never went on");
        assertEquals(0x40, c.stateOf(SATS_A), "the live one kept");
        assertEquals(4, c.info(0)[8], "and the format is 4 again");
        // the load that was torn, sent again: it enters its keyset afresh and the piece goes on
        c.grant();
        c.put(USD, USD_B, 5);
        assertEquals(0xC1, c.stateOf(USD_B));
    }

    @Test
    @DisplayName("U13 SET_CARD lets the table go and takes only units it knows: after it every entry is free and the last unit 00; a drafted opening let go first; the receipts' units and the other unit's count stay; unit 02 is 6A80, nothing written; the rate and every limit stay")
    void setCardLetsTheTableGoAndTakesOnlyUnitsItKnows() throws Exception {
        Card c = owned(SATS);
        assertEquals(SW_OK, c.limits(500, 100, 50, 1000));
        c.tap();
        c.grant();
        int u = c.put(USD, USD_A, 30);
        int s = c.put(-1, SATS_A, 100);
        assertEquals(SW_OK, c.pay(u).getSW());
        c.tap();
        assertEquals(SW_OK, c.pay(s).getSW());
        assertEquals(2, c.inUse(), "both entries dead, not let go");
        byte[] book = c.book();
        assertEquals(30, readUint32(book, 55), "the other unit's count");
        byte[] receipts = Arrays.copyOfRange(book, 59, 75);
        assertEquals(0xA0, receipts[0] & 0xFF);
        assertEquals(0x80, receipts[1] & 0xFF);
        // a drafted opening (set by hand: no command leaves one on a card with nothing unspent) naming a fresh keyset's entry
        System.arraycopy(hexToBytes(USD_B), 0, book, 18, 8);
        book[26] = (byte) 0xC1;
        byte[] open = c.field("changeOpen");
        System.arraycopy(hexToBytes(USD_B), 0, open, 4, 8);
        c.field("openState")[0] = 1;
        c.tap();
        byte[] rec = recordIn(SATS);
        Map<String, byte[]> before = c.snapshot();
        byte[] bad = recordIn(2);
        assertEquals(SW_WRONG_DATA, c.sw(new CommandAPDU(CLA, INS_SET_CARD, 0, 0, ownerData(c.proofFor(L_CARD, bad), bad))), "unit 02");
        c.assertSame(before, "nothing written");
        assertEquals(1, c.field("openState")[0], "the draft still there");
        assertEquals(SW_OK, c.sw(new CommandAPDU(CLA, INS_SET_CARD, 0, 0, ownerData(c.proofFor(L_CARD, rec), rec))));
        assertEquals(0, c.field("openState")[0], "the drafted opening let go");
        assertEquals(0, c.inUse(), "every entry free");
        assertEquals(0, book[54], "the last unit gone");
        assertEquals(30, readUint32(book, 55), "the other unit's count stays, with the log it describes");
        assertArrayEquals(receipts, Arrays.copyOfRange(book, 59, 75), "and the receipts' units");
        assertEquals(1000, c.rate(), "the rate stays");
        byte[] r = c.record();
        assertEquals(500, readUint32(r, CashuApplet.CARD_LIMIT_OFFSET));
        assertEquals(100, readUint32(r, CashuApplet.CARD_TAP_LIMIT_OFFSET));
        assertEquals(50, readUint32(r, CashuApplet.CARD_NOPIN_LIMIT_OFFSET), "and every limit");
        // a change of the record's unit, on the empty card: every limit means what it meant (cents)
        byte[] usd = recordIn(USD);
        assertEquals(SW_OK, c.sw(new CommandAPDU(CLA, INS_SET_CARD, 0, 0, ownerData(c.proofFor(L_CARD, usd), usd))));
        assertEquals(USD, c.getCard(0)[2]);
        assertEquals(500, readUint32(c.record(), CashuApplet.CARD_LIMIT_OFFSET));
    }

    @Test
    @DisplayName("U14 RESET_CARD zeros the book and the rate: the book all zeros, the same array, not made twice; the rate zeros with the record, the clock kept")
    void resetCardZerosTheBookAndTheRate() throws Exception {
        Card c = owned(SATS);
        assertEquals(SW_OK, c.limits(0, 100, 0, 1234));
        c.tap();
        c.grant();
        int u = c.put(USD, USD_A, 30);
        assertEquals(SW_OK, c.pay(u).getSW());
        c.tap();
        assertEquals(SW_OK, c.clearSpent());
        byte[] book = c.book();
        assertFalse(Arrays.equals(new byte[75], book), "something in it: the counts and the receipts' units");
        long now = readUint32(c.info(0), 17);
        assertEquals(SW_OK, c.sw(new CommandAPDU(CLA, INS_RESET_CARD, 0, RESET_CONFIRM, ownerData(c.proofFor(L_RESET, new byte[0]), new byte[0]))));
        assertSame(book, c.book(), "the same array");
        assertArrayEquals(new byte[75], book, "all zeros");
        assertEquals(0, c.rate(), "the rate gone with the record");
        assertEquals(now, readUint32(c.info(0), 17), "the clock kept");
        assertEquals(SW_OK, c.sw(new CommandAPDU(CLA, INS_SET_PIN, 0, 0, TEST_PIN)));
        c.pin();
        assertEquals(SW_OK, c.sw(new CommandAPDU(CLA, INS_SET_CARD, 0, 0, recordIn(USD))));
        assertSame(book, c.book(), "and not made again by the next SET_CARD");
    }

    // =========================================================================
    // U15 to U23: payments and the ceiling
    // =========================================================================

    @Test
    @DisplayName("U15 A payment of both units is 6A9A: both units in one BEGIN; two keysets of one unit go through and sign once; a place whose entry is zeroed by hand is 6A9A, first or not, and is never charged as the record's unit")
    void aPaymentOfBothUnitsIs6A9A() throws Exception {
        Card c = owned(SATS);
        c.grant();
        int s1 = c.put(-1, SATS_A, 10), s2 = c.put(-1, SATS_B, 20), u1 = c.put(USD, USD_A, 30);
        c.tap();
        Map<String, byte[]> before = c.snapshot();
        assertEquals(SW_TWO_UNITS, c.begin(s1, u1).getSW(), "sats then dollars");
        assertEquals(SW_TWO_UNITS, c.begin(u1, s1).getSW(), "dollars then sats");
        assertEquals(SW_TWO_UNITS, c.begin(s1, s2, u1).getSW(), "two keysets of sats and a dollar piece");
        assertEquals(0, c.field("allState")[0], "no payment begun");
        assertEquals(SW_CONDITIONS_NOT_SATIS, c.sign().getSW(), "nothing to sign");
        c.assertSame(before, "nothing written");
        ResponseAPDU two = c.pay(s1, s2);
        assertEquals(SW_OK, two.getSW(), "two keysets of one unit: one payment");
        assertEquals(64, two.getData().length, "signed once");
        // a place whose keyset is in no entry: no unit
        c.tap();
        c.grant();
        int s3 = c.put(-1, SATS_C, 5);
        c.book()[c.entryOf(SATS_C) * 9 + 8] = 0;
        c.tap();
        before = c.snapshot();
        assertEquals(SW_TWO_UNITS, c.begin(s3).getSW(), "first");
        assertEquals(SW_TWO_UNITS, c.begin(u1, s3).getSW(), "or not");
        assertArrayEquals(new byte[] { 1, 1, 1, 0, 0, 0, 30 }, c.balance(1), "and in no sum: the dollars alone are there");
        assertArrayEquals(u32(0), c.balance(0), "nor in the record's unit's: it is never taken for the record's");
        c.assertSame(before, "nothing written, nothing charged");
    }

    @Test
    @DisplayName("U16 The cents of a sats payment are rounded up: 1 sat at R = 1,000 is 1; 1,000 is 100; 1,001 is 101; at R = 1 and at R = 2^32 - 1; 2^32 - 1 sats at R = 100 is 2^32 - 1; at R = 99 it is past four bytes and taken as FF FF FF FF")
    void theCentsOfASatsPaymentAreRoundedUp() throws Exception {
        Card c = owned(SATS);
        long top = 0xFFFFFFFFL;
        long[][] cases = {
            { 1, 1000, 1 }, { 1000, 1000, 100 }, { 1001, 1000, 101 }, { 999, 1000, 100 }, { 0, 1000, 0 },
            { 1, 1, 100 }, { 42_949_672, 1, 4_294_967_200L }, { 42_949_673, 1, top }, { top, 1, top },
            { 1, top, 1 }, { top, top, 100 }, { 0, top, 0 },
            { top, 100, top }, { top, 99, top }, { 4_294_967_294L, 99, top },
            { 2000, 1000, 200 }, { 1500, 1000, 150 }, { 1031, 1000, 104 },
        };
        for (long[] k : cases) {
            assertEquals(k[2], cardCents(c, k[0], k[1]), k[0] + " sats at " + k[1] + " sats to the dollar");
            assertEquals(k[2], cents(k[0], k[1]), "and the rule says so: " + k[0] + " at " + k[1]);
        }
    }

    @Test
    @DisplayName("U17 The conversion agrees with BigInteger everywhere: thousands of random S and R, and every edge, against ceil(S x 100 / R) saturated at four bytes; never one cent under")
    void theConversionAgreesWithBigIntegerEverywhere() throws Exception {
        Card c = owned(SATS);
        Random rng = new Random(123);
        List<long[]> cases = new ArrayList<>();
        long[] edges = { 0, 1, 2, 99, 100, 101, 255, 256, 999, 1000, 1001, 65535, 65536, 0x7FFFFFFFL, 0x80000000L, 42_949_672L, 42_949_673L, 0xFFFFFFFEL, 0xFFFFFFFFL };
        for (long s : edges) for (long r : edges) if (r > 0) cases.add(new long[] { s, r });
        for (int i = 0; i < 5000; i++) {
            long s = rng.nextLong() & 0xFFFFFFFFL, r = 1 + ((rng.nextLong() & 0xFFFFFFFFL) % 0xFFFFFFFFL);
            if (i % 3 == 0) s >>>= rng.nextInt(32);
            if (i % 2 == 0) r >>>= rng.nextInt(31);
            cases.add(new long[] { s, Math.max(1, r) });
        }
        for (long[] k : cases) {
            long got = cardCents(c, k[0], k[1]);
            assertEquals(cents(k[0], k[1]), got, k[0] + " sats at " + k[1]);
            BigInteger worth = BigInteger.valueOf(k[0]).multiply(BigInteger.valueOf(100));
            assertTrue(BigInteger.valueOf(got).multiply(BigInteger.valueOf(k[1])).compareTo(worth) >= 0 || got == 0xFFFFFFFFL, "never a cent under: " + k[0] + " at " + k[1]);
        }
    }

    @Test
    @DisplayName("U18 A sats payment with a limit and no rate is 6A99: at the first SIGN, before the PIN gate; nothing signed, burned, counted or logged; the payment given up (6985 after); with no limit set it signs; a dollar payment never asks for a rate")
    void aSatsPaymentWithALimitAndNoRateIs6A99() throws Exception {
        for (int which = 0; which < 3; which++) {
            Card c = owned(SATS);
            long[] v = { 0, 0, 0 };
            v[which] = 1000;
            // the sats go on first: on a card with a limit and no rate they are refused at the load (U35); the owner's form
            // then sets the limit with no rate on the funded card, the one way such a card comes to hold sats
            c.tap();
            c.grant();
            int s = c.put(-1, SATS_A, 10);
            int u = c.put(USD, USD_A, 10);
            assertEquals(SW_OK, c.limits(v[0], v[1], v[2], 0), "a limit, and no rate");
            c.power();     // no PIN in this tap: the refusal comes before the PIN is asked
            Map<String, byte[]> before = c.snapshot();
            assertEquals(SW_OK, c.begin(s).getSW());
            assertEquals(SW_NO_RATE, c.sign().getSW(), "limit " + which + ": no rate, before the PIN gate");
            assertEquals(SW_CONDITIONS_NOT_SATIS, c.sign().getSW(), "the payment given up");
            c.assertSame(before, "nothing burned, counted or written in the log");
            c.pin();
            assertEquals(SW_NO_RATE, c.pay(s).getSW(), "nor with the PIN");
            ResponseAPDU d = c.pay(u);
            assertEquals(SW_OK, d.getSW(), "a dollar payment asks for no rate");
            assertEquals(64, d.getData().length);
        }
        Card c = owned(SATS);
        assertEquals(SW_OK, c.limits(0, 0, 0, 0), "no limit, no rate");
        c.tap();
        int s = c.put(-1, SATS_A, 10);
        assertEquals(SW_OK, c.pay(s).getSW(), "with no limit set it signs");
    }

    @Test
    @DisplayName("U19 The wait is worked out in cents: a sats payment worth the limit waits nothing, one worth a cent over the tolerance waits ten, each further limit's worth four; the L/32 tolerance in cents; a second payment in a tap slowed across units")
    void theWaitIsWorkedOutInCents() throws Exception {
        Card c = owned(SATS);
        assertEquals(SW_OK, c.limits(0, 100, 0, 1000), "a limit on one payment of $1.00, at 1,000 sats to the dollar");
        long[][] cases = {
            // sats, waits: 1,000 sats are 100 cents; the tolerance is 100 / 32 = 3 cents; 1,031 sats are 103.1, 104 cents
            { 1000, 0 }, { 1030, 0 }, { 1031, 10 }, { 2000, 10 }, { 2001, 14 }, { 3001, 18 }, { 10, 0 },
        };
        for (long[] k : cases) {
            c.tap();
            int p = c.put(-1, SATS_A, k[0]);
            c.power();
            c.pin();
            assertEquals(SW_OK, c.pay(p).getSW(), k[0] + " sats");
            assertEquals(k[1], c.waits, k[0] + " sats, " + cents(k[0], 1000) + " cents: the waits");
            assertEquals(CashuAppletTest.waitsRule(100, cents(k[0], 1000), 0), c.waits, "LIMIT 6a's rule, in cents");
        }
        // a dollar payment, then a sats one, in one tap: the second is slowed whichever its unit
        c.tap();
        c.grant();
        int u = c.put(USD, USD_A, 50);
        int s = c.put(-1, SATS_A, 500);
        c.tap();
        assertEquals(SW_OK, c.pay(u).getSW());
        assertEquals(0, c.waits);
        assertEquals(SW_OK, c.pay(s).getSW());
        assertEquals(10, c.waits, "a second payment in the tap, of the other unit: slowed as one over the limit");
    }

    @Test
    @DisplayName("U20 The allowance counts both units in cents: a sats payment and a dollar payment with no PIN add up in cents, as the whole of their pieces; 6A94 past it; VERIFY_PIN renews it")
    void theAllowanceCountsBothUnitsInCents() throws Exception {
        Card c = owned(SATS);
        assertEquals(SW_OK, c.limits(0, 0, 200, 1000), "an allowance of $2.00 with no PIN");
        c.tap();
        c.grant();
        int s = c.put(-1, SATS_A, 1000), u = c.put(USD, USD_A, 100), s2 = c.put(-1, SATS_A, 10);
        c.power();
        byte[][] b = new byte[1][];
        assertEquals(SW_OK, c.payWithChange(600, b, s).getSW(), "1,000 sats with no PIN, 600 of it change: the whole of the pieces counts, 100 cents");
        assertEquals(100, c.noPinSpent());
        c.power();
        assertEquals(SW_OK, c.pay(u).getSW(), "and $1.00");
        assertEquals(200, c.noPinSpent(), "$2.00 of sats and dollars together");
        c.power();
        assertEquals(SW_PIN_NEEDED, c.pay(s2).getSW(), "10 sats more is past it: 1 cent");
        assertEquals(200, c.noPinSpent());
        c.pin();
        assertEquals(0, c.noPinSpent(), "the PIN renews it");
        c.power();
        assertEquals(SW_OK, c.pay(s2).getSW(), "and the payment goes with no PIN in the next tap");
        assertEquals(1, c.noPinSpent());
    }

    @Test
    @DisplayName("U21 The day counts both units in cents: a daily limit set, sats and dollar payments counted in one spent-today, 6A8F over it, written in the log with the payment's unit; the window as 1.22's")
    void theDayCountsBothUnitsInCents() throws Exception {
        Card c = owned(SATS);
        assertEquals(SW_OK, c.limits(300, 0, 0, 1000), "$3.00 a day");
        assertEquals(T0, readUint32(c.info(0), 21), "a new daily limit begins its window at the clock");
        c.tap();
        c.grant();
        int s = c.put(-1, SATS_A, 1000), u = c.put(USD, USD_A, 150), s2 = c.put(-1, SATS_A, 600), u2 = c.put(USD, USD_A, 60);
        c.tap();
        c.grant();
        assertEquals(SW_OK, c.pay(s).getSW());
        assertEquals(100, c.spentToday(), "1,000 sats: 100 cents");
        assertEquals(SW_OK, c.pay(u).getSW());
        assertEquals(250, c.spentToday(), "and $1.50");
        long refused = readUint32(c.log(0).getData(), 8);
        assertEquals(SW_OVER_LIMIT, c.pay(s2).getSW(), "600 sats, 60 cents: past $3.00");
        byte[] log = c.log(0).getData();
        assertEquals(refused + 1, readUint32(log, 8), "written in the log");
        assertEquals(1, log[16 + 9], "in the newest entry");
        assertEquals(0, log[16 + 10] & 0x20, "of the record's unit: the payment was in sats");
        assertEquals(SW_OVER_LIMIT, c.pay(u2).getSW(), "60 cents of dollars");
        log = c.log(0).getData();
        assertEquals(0x20, log[16 + 10] & 0x20, "in an entry of the other unit");
        assertEquals(1, log[16 + 9]);
        assertEquals(250, c.spentToday(), "neither counted");
        assertEquals(SW_OK, c.sw(setTimeCommand(T0 + 86_400)), "a day on");
        c.tap();
        assertEquals(SW_OK, c.pay(s2).getSW(), "a new day");
        assertEquals(60, c.spentToday());
    }

    @Test
    @DisplayName("U22 SET_LIMIT takes the rate with the figures: sixteen bytes (34) and twelve (33); the proof binds the rate (a proof for one rate is none for another, and none for twelve bytes); a rate alone changed begins no window and keeps every count; seven writes at most")
    void setLimitTakesTheRateWithTheFigures() throws Exception {
        Card c = owned(SATS);
        assertEquals(SW_OK, c.limits(500, 100, 200, 1000));
        c.grant();
        byte[] three = c.info(3);
        assertEquals(500, readUint32(three, 12));
        assertEquals(100, readUint32(three, 30));
        assertEquals(200, readUint32(three, 34));
        assertEquals(1000, readUint32(three, 44), "the rate, read back by the owner");
        // the proof binds the rate
        byte[] v = concat(u32(500), u32(100), u32(200), u32(1000));
        byte[] w = concat(u32(500), u32(100), u32(200), u32(9999));
        assertEquals(SW_OWNER_PROOF, c.sw(new CommandAPDU(CLA, INS_SET_LIMIT_OWNER, 0, 0, ownerData(c.proofFor(L_LIMIT, v), w))), "a proof for one rate is none for another");
        assertEquals(SW_OWNER_PROOF, c.sw(new CommandAPDU(CLA, INS_SET_LIMIT_OWNER, 0, 0, ownerData(c.proofFor(L_LIMIT, v), Arrays.copyOf(v, 12)))), "and none for twelve bytes");
        assertEquals(1000, c.rate());
        // counts, then the rate alone
        c.tap();
        int s = c.put(-1, SATS_A, 500);
        c.power();
        assertEquals(SW_OK, c.pay(s).getSW());
        assertEquals(50, c.spentToday());
        assertEquals(50, c.noPinSpent());
        assertEquals(SW_OK, c.sw(setTimeCommand(T0 + 100)));
        assertEquals(SW_OK, c.limits(500, 100, 200, 2000), "the rate re-pegged, the figures as the card has them");
        assertEquals(2000, c.rate());
        assertEquals(50, c.spentToday(), "no window begun");
        assertEquals(T0, readUint32(c.info(0), 21));
        assertEquals(50, c.noPinSpent(), "and no count");
        // the PIN form
        Card p = phoneless(SATS);
        assertEquals(SW_OK, p.pinLimits(100, 50, 1500));
        assertEquals(1500, p.rate());
        assertEquals(100, readUint32(p.record(), CashuApplet.CARD_TAP_LIMIT_OFFSET));
        assertEquals(50, readUint32(p.record(), CashuApplet.CARD_NOPIN_LIMIT_OFFSET));
        // seven writes at most in writeLimit's transaction: the day (and its window and count), the limit on one payment, the allowance and its count, the rate
        String tx = CashuAppletTestSource.transaction(CashuAppletTestSource.body(CashuAppletTestSource.code(), "private void writeLimit(", "private void processSetHeader("));
        assertEquals(7, CashuAppletTestSource.writes(tx), "seven copies at most: " + tx);
        assertTrue(tx.contains("if (rateChanged) Util.arrayCopy(src, (short)(at + 12), cardRecord, CARD_RATE_OFFSET, (short) 4);"), "the rate, one copy of four, where it changed");
    }

    @Test
    @DisplayName("U23 The old lengths are taken only on a dollars record: 4, 8 and 12 bytes (34), 4 and 8 (33): taken on a dollars record as 1.22 takes them, the rate untouched; 6700 on a sats record and before the first SET_CARD, nothing written; every other length 6700")
    void theOldLengthsAreTakenOnlyOnADollarsRecord() throws Exception {
        Card d = owned(USD);
        assertEquals(SW_OK, d.limits(0, 0, 0, 777), "a rate first");
        byte[][] old = { u32(300), concat(u32(400), u32(50)), concat(u32(500), u32(60), u32(70)) };
        for (byte[] v : old) {
            assertEquals(SW_OK, d.sw(new CommandAPDU(CLA, INS_SET_LIMIT_OWNER, 0, 0, ownerData(d.proofFor(L_LIMIT, v), v))), v.length + " bytes on a dollars record");
        }
        byte[] r = d.record();
        assertEquals(500, readUint32(r, CashuApplet.CARD_LIMIT_OFFSET));
        assertEquals(60, readUint32(r, CashuApplet.CARD_TAP_LIMIT_OFFSET));
        assertEquals(70, readUint32(r, CashuApplet.CARD_NOPIN_LIMIT_OFFSET));
        assertEquals(777, d.rate(), "the rate as it was");
        for (int n : new int[] { 0, 1, 5, 11, 13, 15, 17, 20 }) {
            byte[] v = new byte[n];
            assertEquals(SW_WRONG_LENGTH, d.sw(new CommandAPDU(CLA, INS_SET_LIMIT_OWNER, 0, 0, ownerData(d.proofFor(L_LIMIT, v), v))), n + " bytes");
        }
        Card pd = phoneless(USD);
        assertEquals(SW_OK, pd.sw(new CommandAPDU(CLA, INS_SET_LIMIT, 0, 0, u32(250))), "four bytes by the PIN on a dollars record");
        assertEquals(SW_OK, pd.sw(new CommandAPDU(CLA, INS_SET_LIMIT, 0, 0, concat(u32(300), u32(40)))), "and eight");
        assertEquals(300, readUint32(pd.record(), CashuApplet.CARD_TAP_LIMIT_OFFSET));
        assertEquals(40, readUint32(pd.record(), CashuApplet.CARD_NOPIN_LIMIT_OFFSET));
        assertEquals(0, pd.rate());
        // a sats record
        Card s = owned(SATS);
        Map<String, byte[]> before = s.snapshot();
        for (byte[] v : old) {
            assertEquals(SW_WRONG_LENGTH, s.sw(new CommandAPDU(CLA, INS_SET_LIMIT_OWNER, 0, 0, ownerData(s.proofFor(L_LIMIT, v), v))), v.length + " bytes on a sats record");
        }
        s.assertSame(before, "nothing written");
        Card ps = phoneless(SATS);
        before = ps.snapshot();
        assertEquals(SW_WRONG_LENGTH, ps.sw(new CommandAPDU(CLA, INS_SET_LIMIT, 0, 0, u32(250))));
        assertEquals(SW_WRONG_LENGTH, ps.sw(new CommandAPDU(CLA, INS_SET_LIMIT, 0, 0, concat(u32(300), u32(40)))));
        for (int n : new int[] { 0, 1, 11, 13, 16 }) assertEquals(SW_WRONG_LENGTH, ps.sw(new CommandAPDU(CLA, INS_SET_LIMIT, 0, 0, new byte[n])), n + " bytes by the PIN");
        ps.assertSame(before, "nothing written");
        // before the first SET_CARD, whose unit byte is 00
        Card n = new Card();
        assertEquals(SW_OK, n.sw(new CommandAPDU(CLA, INS_SET_PIN, 0, 0, TEST_PIN)));
        n.pin();
        assertEquals(SW_WRONG_LENGTH, n.sw(new CommandAPDU(CLA, INS_SET_LIMIT, 0, 0, u32(250))), "before any record");
        assertEquals(SW_OK, n.sw(new CommandAPDU(CLA, INS_SET_LIMIT, 0, 0, concat(u32(250), u32(0), u32(1000)))), "the twelve bytes are taken there, the rate with them");
        assertEquals(1000, n.rate());
    }

    // =========================================================================
    // U24 to U30: what the card says
    // =========================================================================

    @Test
    @DisplayName("U24 The rate is the holder's to read: GET_INFO P1 = 3 bytes 44 to 47 with the grant, and on a card with no owner to the PIN in a held tap; zeros otherwise; GET_CARD P1 = 0's 29 bytes stay zeros with a rate set")
    void theRateIsTheHoldersToRead() throws Exception {
        Card c = owned(SATS);
        assertEquals(SW_OK, c.limits(0, 0, 0, 0x01020304L));
        c.tap();
        assertArrayEquals(new byte[4], Arrays.copyOfRange(c.info(3), 44, 48), "to the PIN: zeros");
        c.power();
        assertArrayEquals(new byte[4], Arrays.copyOfRange(c.info(3), 44, 48), "to nobody: zeros");
        c.grant();
        assertArrayEquals(u32(0x01020304L), Arrays.copyOfRange(c.info(3), 44, 48), "to the owner's grant");
        byte[] card = c.getCard(0);
        assertArrayEquals(new byte[29], Arrays.copyOfRange(card, 76, 105), "GET_CARD: the 29 bytes after the header's hash are zeros, the rate among them");
        assertArrayEquals(u32(0x01020304L), Arrays.copyOfRange(c.record(), 156, 160), "where the record keeps it");
        Card p = phoneless(SATS);
        assertEquals(SW_OK, p.pinLimits(0, 0, 4321));
        assertArrayEquals(new byte[4], Arrays.copyOfRange(p.info(3), 44, 48), "the PIN with no hold: zeros");
        p.hold();
        assertEquals(4321, readUint32(p.info(3), 44), "the PIN in a held tap");
    }

    @Test
    @DisplayName("U25 GET_INFO P1 = 3 says the book: every byte of the 51; P1 = 0, 1 and 2 as 1.22 but the version and byte 8")
    void getInfoThreeSaysTheBook() throws Exception {
        Card c = owned(SATS);
        assertEquals(SW_OK, c.limits(0, 100, 200, 1000));
        c.tap();
        c.put(-1, SATS_A, 50);
        c.put(-1, SATS_B, 50);
        c.grant();
        int u = c.put(USD, USD_A, 30);
        c.tap();
        assertEquals(SW_OK, c.pay(u).getSW());
        c.grant();
        byte[] two = c.info(2), three = c.info(3);
        assertEquals(43, two.length);
        assertEquals(51, three.length);
        assertArrayEquals(two, Arrays.copyOf(three, 43), "0 to 42: P1 = 2's");
        assertEquals(1, three[0]);
        assertEquals(0x17, three[1], "1.23");
        assertEquals(5, three[8], "a card of both units");
        assertEquals(1, three[43], "the book's format");
        assertEquals(1000, readUint32(three, 44), "the rate, to the grant");
        assertEquals(3, three[48], "three entries in use");
        assertEquals(2, three[49], "two without the mark");
        assertEquals((byte) 0xA0, three[50], "the last payment's unit: the other");
        assertArrayEquals(Arrays.copyOf(two, 42), c.info(1));
        assertArrayEquals(Arrays.copyOf(two, 30), c.info(0));
        assertEquals(30, c.info(4).length, "any other P1, the thirty");
        // a card of one unit says 4, and is 1.22's but for the version
        Card one = owned(SATS);
        one.put(-1, SATS_A, 10);
        byte[] zero = one.info(0);
        assertEquals(4, zero[8]);
        byte[] b3 = one.info(3);
        assertArrayEquals(new byte[] { 1, 0, 0, 0, 0, 1, 1, 0 }, Arrays.copyOfRange(b3, 43, 51), "a book, no rate to this reader, one entry, unmarked, no payment yet");
    }

    @Test
    @DisplayName("U26 The format is 4 for one unit and 5 for both: 4 on a new card and after loads of the record's unit, marked or not; 5 at the first entry of the other unit; 5 on a dollars card that holds sats; 4 again after the reclaim; GET_CARD byte 0 the same")
    void theFormatIsFourForOneUnitAndFiveForBoth() throws Exception {
        Card n = new Card();
        assertEquals(4, n.info(0)[8], "a new card");
        assertEquals(4, n.getCard(0)[0]);
        Card c = owned(SATS);
        c.put(-1, SATS_A, 10);
        c.grant();
        c.put(SATS, SATS_B, 10);
        assertEquals(4, c.info(0)[8], "loads of the record's unit, marked or not");
        int u = c.put(USD, USD_A, 10);
        assertEquals(5, c.info(0)[8], "the first entry of the other unit");
        assertEquals(5, c.getCard(0)[0]);
        assertEquals(5, c.info(1)[8]);
        assertEquals(5, c.info(2)[8]);
        c.tap();
        assertEquals(SW_OK, c.pay(u).getSW());
        assertEquals(5, c.info(0)[8], "paid, and not let go: still 5");
        assertEquals(SW_OK, c.clearSpent());
        assertEquals(4, c.info(0)[8], "4 again after the reclaim");
        assertEquals(4, c.getCard(0)[0]);
        Card d = owned(USD);
        d.put(-1, USD_A, 10);
        assertEquals(4, d.info(0)[8]);
        d.grant();
        d.put(SATS, SATS_A, 10);
        assertEquals(5, d.info(0)[8], "a dollars card that holds sats");
    }

    @Test
    @DisplayName("U27 GET_BALANCE P1 = 0 is the record's unit and P1 = 1 each unit: on one unit P1 = 0 is 1.22's; on both, the record's only; P1 = 1's layout; the wrap; a place of no entry in no sum")
    void getBalanceZeroIsTheRecordsUnitAndOneIsEachUnit() throws Exception {
        Card c = owned(SATS);
        assertArrayEquals(new byte[] { 0 }, c.balance(1), "nothing held: a count of 0");
        assertArrayEquals(u32(0), c.balance(0));
        c.put(-1, SATS_A, 100);
        c.put(-1, SATS_A, 23);
        assertArrayEquals(u32(123), c.balance(0), "one unit: every piece, as 1.22");
        assertArrayEquals(new byte[] { 1, 0, 2, 0, 0, 0, 123 }, c.balance(1));
        c.grant();
        c.put(USD, USD_A, 0x0102);
        assertArrayEquals(u32(123), c.balance(0), "both: the record's unit only");
        assertArrayEquals(concat(new byte[] { 2, 0, 2 }, u32(123), new byte[] { 1, 1 }, u32(0x0102)), c.balance(1), "sats first, then dollars");
        assertArrayEquals(u32(123), c.balance(2), "any other P1 is P1 = 0");
        Card d = owned(USD);
        d.put(-1, USD_A, 500);
        d.grant();
        d.put(SATS, SATS_A, 7);
        assertArrayEquals(u32(500), d.balance(0), "a dollars card: the dollars");
        assertArrayEquals(concat(new byte[] { 2, 0, 1 }, u32(7), new byte[] { 1, 1 }, u32(500)), d.balance(1), "and each, sats first");
        // the wrap, as P1 = 0 always had it
        Card w = owned(SATS);
        w.put(-1, SATS_A, 0xFFFFFFF0L);
        w.put(-1, SATS_A, 0x20);
        assertArrayEquals(u32(0x10), w.balance(0), "past 2^32 - 1 it wraps");
        assertArrayEquals(concat(new byte[] { 1, 0, 2 }, u32(0x10)), w.balance(1), "in P1 = 1 as well");
        // a place of no entry is in no sum
        c.book()[c.entryOf(USD_A) * 9 + 8] = 0;
        assertArrayEquals(new byte[] { 1, 0, 2, 0, 0, 0, 123 }, c.balance(1), "the dollar piece of no entry is counted nowhere");
    }

    @Test
    @DisplayName("U28 A long history keeps every piece in its unit: random loads under every authority, payments in both units with change, refunds, clears, SET_CARD, new rates, power-ups: after every step every unspent place's keyset is in an entry of its unit, and the counts add up, in cents and in each unit")
    void aLongHistoryKeepsEveryPieceInItsUnit() throws Exception {
        Random rng = new Random(123_23);
        String[] keysets = { SATS_A, SATS_B, SATS_C, USD_A, USD_B, USD_C };
        int[] unitOf = { SATS, SATS, SATS, USD, USD, USD };
        Card c = owned(SATS);
        int record = SATS;
        long[] signed = new long[2];     // what the log should say was signed for, by absolute unit, since the last SET_CARD
        long rate = 0;
        int steps = 0, loads = 0, pays = 0, changes = 0, refusals = 0, clears = 0, setCards = 0;
        List<byte[]> owed = new ArrayList<>();
        c.tap();
        for (int step = 0; step < 400; step++) {
            int op = rng.nextInt(100);
            if (op < 40) {
                // a load: which authority, which keyset, labelled or plain, or mislabelled now and then
                boolean say = rng.nextBoolean();
                if (say) c.grant();
                int k = rng.nextInt(keysets.length);
                // a wrong label only for a keyset the table has: a keyset it has not, labelled wrongly, would be the terminal's own money in the wrong unit, which the card cannot tell (threat 18)
                int label = rng.nextInt(10) == 0 && c.stateOf(keysets[k]) >= 0 ? 1 - unitOf[k] : unitOf[k];
                boolean plain = label == record && rng.nextBoolean();
                byte[] piece = c.piece(keysets[k], 1 + rng.nextInt(2000));
                Map<String, byte[]> before = c.snapshot();
                int state = c.stateOf(keysets[k]);
                ResponseAPDU r = plain ? c.load(piece) : c.loadAs(label, piece);
                if (r.getSW() == SW_OK) {
                    assertEquals(label, unitOf[k], "a load that went on is of its keyset's own unit");
                    loads++;
                } else {
                    refusals++;
                    int sw = r.getSW();
                    assertTrue(sw == SW_OTHER_UNIT || sw == SW_NOT_THIS_TAP || sw == SW_TABLE_FULL || sw == 0x6A84 || sw == 0x6A94, "a refusal of the table's, or a full card: " + Integer.toHexString(sw));
                    if (sw == SW_OTHER_UNIT) assertTrue(state >= 0 && (state & 1) != label, "6A9B only where the keyset is entered as the other unit");
                    if (label != unitOf[k]) assertTrue(sw != SW_OK);
                    if (sw != SW_TABLE_FULL) c.assertSame(before, "a refused load writes nothing");
                }
            } else if (op < 70) {
                // a payment of one unit's pieces, now and then with change
                List<Integer> mine = new ArrayList<>();
                int unit = rng.nextInt(2);
                long sum = 0;
                for (int p = 0; p < 128 && mine.size() < 4; p++) {
                    byte[] s = c.slot(p);
                    if (s.length != 82 || s[0] != 1) continue;
                    int k = Arrays.asList(keysets).indexOf(CashuAppletTest.toHex(Arrays.copyOfRange(s, 1, 9)));
                    if (unitOf[k] != unit || readUint32(s, 78) != 0) continue;
                    if (rng.nextInt(3) == 0) continue;
                    mine.add(p);
                    sum += readUint32(s, 9);
                }
                if (mine.isEmpty()) continue;
                int[] places = mine.stream().mapToInt(Integer::intValue).toArray();
                long change = sum > 2 && owed.size() < 8 && rng.nextBoolean() ? 1 + rng.nextInt((int) Math.min(sum - 1, 1000)) : 0;
                byte[][] b = new byte[1][];
                ResponseAPDU r = change > 0 ? c.payWithChange(change, b, places) : c.pay(places);
                if (r.getSW() == SW_OK) {
                    signed[unit] += sum - change;
                    pays++;
                    if (change > 0) { owed.add(b[0]); changes++; }
                    byte[] log = c.log(2).getData();
                    long recordCount = readUint32(log, 4), otherCount = readUint32(log, 16);
                    assertEquals(signed[record], recordCount, "the record's unit's count, after step " + step);
                    assertEquals(signed[1 - record], otherCount, "the other unit's, after step " + step);
                } else {
                    assertTrue(r.getSW() == 0x6A84 || r.getSW() == SW_NO_RATE, "a payment refused: " + Integer.toHexString(r.getSW()));
                }
            } else if (op < 78) {
                // the change written back, in a tap of its own
                if (!owed.isEmpty()) {
                    c.power();
                    ResponseAPDU g = c.send(new CommandAPDU(CLA, 0x19, 0, 0, 256));
                    if (g.getData()[0] > 0) {
                        int k = g.getData()[1];
                        byte[] blinded = Arrays.copyOfRange(g.getData(), 1 + 1 + 16, 1 + 1 + 16 + 33);
                        assertEquals(SW_OK, c.sw(loadChangeCommand(k, mintAnswer(blinded))), "the change back");
                        owed.removeIf(x -> Arrays.equals(x, blinded));
                    }
                    c.pin();
                }
            } else if (op < 86) {
                assertEquals(SW_OK, c.clearSpent());
                clears++;
            } else if (op < 92) {
                c.tap();
            } else if (op < 97) {
                rate = rng.nextInt(5) == 0 ? 0 : 1 + rng.nextInt(200_000);
                long tap = rng.nextBoolean() ? 0 : 1 + rng.nextInt(5000);
                assertEquals(SW_OK, c.limits(0, tap, 0, rate == 0 && tap > 0 ? 1 : rate));
                c.tap();
            } else {
                // SET_CARD on an empty card owed nothing: everything paid first
                c.tap();
                c.grant();
                List<Integer> all = new ArrayList<>();
                for (int p = 0; p < 128; p++) { byte[] s = c.slot(p); if (s.length == 82 && s[0] == 1) all.add(p); }
                boolean ok = true;
                for (int unit = 0; unit < 2 && ok; unit++) {
                    List<Integer> group = new ArrayList<>();
                    for (int p : all) {
                        int k = Arrays.asList(keysets).indexOf(CashuAppletTest.toHex(Arrays.copyOfRange(c.slot(p), 1, 9)));
                        if (unitOf[k] == unit) group.add(p);
                    }
                    if (group.isEmpty()) continue;
                    long sum = 0;
                    for (int p : group) sum += readUint32(c.slot(p), 9);
                    ResponseAPDU r = c.pay(group.stream().mapToInt(Integer::intValue).toArray());
                    if (r.getSW() != SW_OK) { ok = false; break; }
                    signed[unit] += sum;
                }
                c.tap();
                if (ok && owed.isEmpty() && c.clearSpent() == SW_OK) {
                    record = rng.nextInt(2);
                    byte[] rec = recordIn(record);
                    int sw = c.sw(new CommandAPDU(CLA, INS_SET_CARD, 0, 0, ownerData(c.proofFor(L_CARD, rec), rec)));
                    if (sw == SW_OK) {
                        setCards++;
                        assertEquals(0, c.inUse(), "SET_CARD on an empty card: every entry free");
                        // the counts are relative to the record's unit: what the log says is read from here on as deltas
                        byte[] log = c.log(2).getData();
                        signed[record] = readUint32(log, 4);
                        signed[1 - record] = readUint32(log, 16);
                    } else {
                        assertEquals(SW_CARD_IN_USE, sw);
                    }
                }
            }
            steps++;
            // the invariants, after every step
            long[] held = new long[2];
            int[] pieces = new int[2];
            for (int p = 0; p < 128; p++) {
                byte[] s = c.slot(p);
                if (s.length != 82 || s[0] != 1) continue;
                String ks = CashuAppletTest.toHex(Arrays.copyOfRange(s, 1, 9));
                int k = Arrays.asList(keysets).indexOf(ks);
                int state = c.stateOf(ks);
                assertTrue(state >= 0, "step " + step + ": the unspent place " + p + "'s keyset " + ks + " is in an entry");
                assertEquals(unitOf[k], state & 1, "step " + step + ": of its own unit");
                held[unitOf[k]] += readUint32(s, 9);
                pieces[unitOf[k]]++;
            }
            byte[] bal = c.balance(1);
            int at = 1;
            for (int u = 0; u < 2; u++) {
                if (pieces[u] == 0) continue;
                assertEquals(u, bal[at], "step " + step);
                assertEquals(pieces[u], bal[at + 1] & 0xFF, "step " + step + ": the pieces of unit " + u);
                assertEquals(held[u] & 0xFFFFFFFFL, readUint32(bal, at + 2), "step " + step + ": the sum of unit " + u);
                at += 6;
            }
            assertEquals(at, bal.length, "step " + step + ": the units held, and no other");
            assertEquals((pieces[0] > 0 ? 1 : 0) + (pieces[1] > 0 ? 1 : 0), bal[0]);
            assertEquals(held[record] & 0xFFFFFFFFL, readUint32(c.balance(0), 0), "step " + step + ": P1 = 0 is the record's unit's");
            int unmarked = 0;
            for (int e = 0; e < 6; e++) { int s = c.book()[e * 9 + 8] & 0xFF; if ((s & 0x40) != 0 && (s & 0x80) == 0) unmarked++; }
            assertTrue(unmarked <= 2, "step " + step + ": never more than two entries without the mark");
        }
        assertTrue(loads > 50 && pays > 30 && changes > 5 && refusals > 5 && clears > 5, "the history did what it was for: loads " + loads + ", payments " + pays + ", changes " + changes + ", refusals " + refusals + ", clears " + clears + ", set-ups " + setCards);
    }

    @Test
    @DisplayName("U29 The log says each entry's unit: bit 5; a tap of both units makes two entries; a card of one unit has bit 5 at 0; P1 = 2's counts; P1 = 3's receipts and units; a refusal in an entry of its unit; with no owner, P1 = 2 and 3 counted toward the held tap as 0 and 1, and 6A86 for P1 = 4 counting nothing")
    void theLogSaysEachEntrysUnit() throws Exception {
        Card c = owned(SATS);
        c.grant();
        int s = c.put(-1, SATS_A, 300), u = c.put(USD, USD_A, 40);
        byte[] log = c.log(0).getData();
        long taps = readUint32(log, 0);
        assertEquals(0x20, log[16 + 10] & 0x20, "the newest entry, the dollars put on: the other unit");
        assertEquals(40, readUint32(log, 16 + 12));
        assertEquals(0, log[16 + 20 + 10] & 0x20, "the one before it, in the same tap, the sats: the record's");
        assertEquals(300, readUint32(log, 16 + 20 + 12));
        c.tap();
        c.grant();
        assertEquals(SW_OK, c.pay(s).getSW());
        assertEquals(SW_OK, c.pay(u).getSW());
        log = c.log(0).getData();
        assertEquals(taps + 2, readUint32(log, 0), "a tap of both units: two entries");
        assertEquals(0x20, log[16 + 10] & 0x20);
        assertEquals(40, readUint32(log, 16 + 4), "the dollars signed for, in their own entry");
        assertEquals(0, log[16 + 20 + 10] & 0x20);
        assertEquals(300, readUint32(log, 16 + 20 + 4));
        assertEquals(300, readUint32(log, 4), "the head counts the record's unit");
        // P1 = 2: the counts with the other unit's after them
        byte[] two = c.log(2).getData();
        assertArrayEquals(Arrays.copyOf(log, 16), Arrays.copyOf(two, 16));
        assertEquals(40, readUint32(two, 16), "the other unit, ever");
        assertArrayEquals(Arrays.copyOfRange(log, 16, log.length), Arrays.copyOfRange(two, 20, two.length), "then the entries");
        assertTrue(two.length <= 180);
        // P1 = 3: the receipts, each with its unit
        byte[] one = c.log(1).getData(), three = c.log(3).getData();
        assertEquals(4 + 2 * 77, one.length);
        assertEquals(4 + 2 * 78, three.length);
        assertArrayEquals(Arrays.copyOf(one, 4), Arrays.copyOf(three, 4));
        assertArrayEquals(Arrays.copyOfRange(one, 4, 81), Arrays.copyOfRange(three, 4, 81), "the newest receipt as P1 = 1 has it");
        assertEquals((byte) 0xA0, three[81], "and its unit: the other");
        assertArrayEquals(Arrays.copyOfRange(one, 81, 158), Arrays.copyOfRange(three, 82, 159));
        assertEquals((byte) 0x80, three[159], "the one before: the record's");
        assertEquals(40, readUint32(three, 4 + 8), "a receipt's amount in its own unit");
        // a card of one unit: bit 5 never set
        Card one1 = owned(SATS);
        int p = one1.put(-1, SATS_A, 10);
        one1.tap();
        assertEquals(SW_OK, one1.pay(p).getSW());
        byte[] l1 = one1.log(0).getData();
        for (int e = 16; e < l1.length; e += 20) assertEquals(0, l1[e + 10] & 0x20, "a card of one unit has bit 5 at 0");
        // with no owner: P1 = 2 and 3 count toward the held tap as 0 and 1, and 4 counts nothing
        Card ph = phoneless(SATS);
        for (int asked = 1; asked < HISTORY_ASKINGS; asked++) {
            assertEquals(SW_KEEP_HOLDING, ph.log(asked % 2 == 0 ? 2 : 3).getSW(), "asking " + asked);
            if (asked == 4) {
                assertEquals(SW_INCORRECT_P1P2, ph.log(4).getSW(), "P1 = 4");
                assertEquals((byte)(0x80 | 4), ph.field("nonceLive")[1], "counting nothing and ending nothing");
            }
        }
        assertEquals(SW_OK, ph.log(2).getSW(), "the tenth answers");
        assertEquals(SW_OK, ph.log(3).getSW(), "and the history is open for the tap");
    }

    @Test
    @DisplayName("U30 GET_CARD P1 = 2 is the table: the count and every entry in use, in order; 55 bytes at the most; P1 = 1 answers as P1 = 0")
    void getCardTwoIsTheTable() throws Exception {
        Card c = owned(SATS);
        assertArrayEquals(new byte[] { 0 }, c.getCard(2), "an empty table");
        c.put(-1, SATS_A, 10);
        c.grant();
        String[] more = { USD_A, SATS_B, USD_B, SATS_C, USD_C };
        for (String k : more) c.put(k.startsWith("00bb") ? USD : SATS, k, 10);
        byte[] t = c.getCard(2);
        assertEquals(55, t.length, "six entries: the most");
        assertEquals(6, t[0]);
        assertArrayEquals(Arrays.copyOf(c.book(), 54), Arrays.copyOfRange(t, 1, 55), "each as kept, in the table's order");
        assertArrayEquals(concat(hexToBytes(SATS_A), new byte[] { 0x40 }), Arrays.copyOfRange(t, 1, 10));
        assertArrayEquals(concat(hexToBytes(USD_A), new byte[] { (byte) 0xC1 }), Arrays.copyOfRange(t, 10, 19));
        // a free entry is left out
        c.book()[9 + 8] = 0;
        byte[] five = c.getCard(2);
        assertEquals(1 + 5 * 9, five.length);
        assertEquals(5, five[0]);
        assertArrayEquals(Arrays.copyOfRange(c.book(), 18, 27), Arrays.copyOfRange(five, 10, 19), "the next in use after the first");
        assertArrayEquals(c.getCard(0), c.getCard(1), "P1 = 1 is P1 = 0");
        assertArrayEquals(c.getCard(0), c.getCard(0x80));
    }

    // =========================================================================
    // U31 to U34: read from the source, and the 1.22 transcript
    // =========================================================================

    @Test
    @DisplayName("U31 Only these methods name the book: SET_CARD, RESET_CARD, LOAD_PROOF and loadOne, the reclaim and the lookups beside it (the entry, a place's unit, the format, a free entry), SPEND_ALL_SIGN, and the readers of GET_INFO, GET_CARD and GET_LOG; no other")
    void onlyTheseMethodsNameTheBook() throws Exception {
        String code = CashuAppletTestSource.code();
        java.util.Set<String> want = new java.util.TreeSet<>(Arrays.asList(
            "processGetInfo", "processGetCard", "processSpendAllSign", "processLoadProof", "loadOne", "processSetCard", "processResetCard",
            "processGetLog", "processGetReceipts", "entryOf", "placeUnit", "cardFormat", "freeEntry", "reclaim"));
        java.util.Set<String> found = CashuAppletTestSource.methodsNaming(code, "book");
        assertEquals(want, found, "the methods that name the book");
        // and it is only ever assigned in SET_CARD, the guarded allocation and the null its catch leaves
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?<![\\w.])book\\s*=(?!=)").matcher(code);
        int n = 0;
        while (m.find()) n++;
        assertEquals(2, n, "book = new byte[BOOK_LEN], and book = null in its catch");
    }

    @Test
    @DisplayName("U32 The transactions grow by what the design says: SIGN's and the receipt's one byte more, writeLimit's one copy more, nothing in SET_CARD's")
    void theTransactionsGrowByWhatTheDesignSays() throws Exception {
        String code = CashuAppletTestSource.code();
        String sign = CashuAppletTestSource.body(code, "private void processSpendAllSign(", "private void finishBurn(");
        String[] txs = CashuAppletTestSource.transactions(sign);
        assertEquals(2, txs.length, "the payment's and the receipt's");
        assertTrue(txs[0].contains("if (book != null) book[BOOK_LAST] = unit == (byte) 0 ? UNIT_RECORD : UNIT_OTHER;"), "the payment's: the last unit, one byte");
        assertTrue(txs[0].contains("if (unit == (byte) 0) addUint32Stop(cardLog, LOG_SATS_OFFSET, allNet, (short) 0);") && txs[0].contains("else addUint32Stop(book, BOOK_OTHER, allNet, (short) 0);"),
            "the other unit's count in place of the head's, and not beside it");
        assertTrue(txs[0].contains("short entry = logEntry(unit);"), "the entry of the payment's unit, its flags byte written where it begins one");
        assertEquals(1, CashuAppletTestSource.count(txs[0], "book["), "and nothing else of the book");
        assertTrue(txs[1].contains("book[(short)(BOOK_UNITS + (short)(slot - RECEIPTS_HEAD) / RECEIPT_LEN)] ="), "the receipt's: its unit, one byte");
        assertEquals(1, CashuAppletTestSource.count(txs[1], "book["));
        String entry = CashuAppletTestSource.body(code, "private short logEntry(", "private short logAt(");
        assertTrue(entry.contains("if (unit == LOG_FLAG_OTHER) cardLog[(short)(at + LOG_E_FLAGS)] = LOG_FLAG_OTHER;"), "the new entry's flags byte, in the transaction that begins it");
        String limit = CashuAppletTestSource.body(code, "private void writeLimit(", "private void processSetHeader(");
        assertEquals(1, CashuAppletTestSource.count(CashuAppletTestSource.transaction(limit), "CARD_RATE_OFFSET"), "writeLimit: one copy more, the rate");
        String setCard = CashuAppletTestSource.body(code, "private void processSetCard(", "private void processSetLimit(");
        String tx = CashuAppletTestSource.transaction(setCard);
        assertFalse(tx.contains("book") || tx.contains("reclaim") || tx.contains("openState"), "SET_CARD's transaction gains nothing: the reclaim and the last unit are before it, outside it");
        assertTrue(setCard.indexOf("reclaim();") < setCard.indexOf("JCSystem.beginTransaction();"));
        String load = CashuAppletTestSource.body(code, "private short loadOne(", "private void processClearSpent(");
        assertFalse(load.contains("beginTransaction"), "the entry at a load: no transaction, its bytes before the place's, its state byte last");
        String reclaim = CashuAppletTestSource.body(code, "private void reclaim(", "private void centsOfPayment(");
        assertFalse(reclaim.contains("beginTransaction"), "the reclaim: a byte at a time, outside any transaction");
    }

    @Test
    @DisplayName("U33 A card of one unit answers the 1.22 transcript: spec/vectors/transcript-122.json replayed on a 1.23 card in sats, every answer equal but the version, and the old-length SET_LIMITs 6700 (U23, the figures then put by hand as 1.22 wrote them); on a card in dollars, those taken too")
    void aOneUnitCardAnswersTheTwentyTwoTranscript() throws Exception {
        java.nio.file.Path file = CashuAppletTestSource.repository().resolve("spec").resolve("vectors").resolve("transcript-122.json");
        String json = new String(java.nio.file.Files.readAllBytes(file), StandardCharsets.UTF_8);
        List<Map<String, String>> entries = CashuAppletTestSource.entries(json);
        assertEquals(1022, entries.size(), "the 1.22 recording, whole");
        for (int unit : new int[] { SATS, USD }) {
            int compared = replay(entries, unit);
            assertTrue(compared > 900, "every command replayed and its answer compared: " + compared);
        }
    }

    /** The 1.22 transcript replayed on a new card, as the recording's own test sets one up; answers how many answers were compared. */
    private int replay(List<Map<String, String>> entries, int unit) throws Exception {
        CashuAppletTest.useTheRealFloor();
        try {
            Card c = new Card();
            CashuAppletTestSource.ownPinKey(c.runtime.appletAt(c.aid));
            int compared = 0, refusedOld = 0;
            for (int i = 0; i < entries.size(); i++) {
                Map<String, String> e = entries.get(i);
                String name = i + " " + e.get("name") + (unit == USD ? " (dollars)" : " (sats)");
                if (e.get("kind").equals("reset")) { c.sim.reset(); continue; }
                byte[] apdu = hexToBytes(e.get("apdu"));
                int ins = apdu[1] & 0xFF, p1 = apdu[2] & 0xFF;
                int want = Integer.parseInt(e.get("sw"), 16);
                byte[] wantData = hexToBytes(e.get("data"));
                // a card in dollars: the open SET_CARDs written in dollars, and an owner's (whose proof is over its sats) put into dollars by hand after it
                if (unit == USD && ins == 0x32 && apdu.length > 5 && !owned(c)) apdu[5] = (byte) USD;
                ResponseAPDU r = c.send(new CommandAPDU(apdu));
                if (unit == USD && ins == 0x32 && r.getSW() == SW_OK) c.record()[CashuApplet.CARD_UNIT_OFFSET] = (byte) USD;
                boolean oldLimit = (ins == 0x34 || ins == 0x33) && want == SW_OK;
                if (oldLimit && unit == SATS) {
                    assertEquals(SW_WRONG_LENGTH, r.getSW(), name + ": 1.22's length, on a sats record (U23)");
                    writeAs122(c, ins, apdu);
                    refusedOld++;
                    compared++;
                    continue;
                }
                if (ins == 0x18 && p1 == 2) {
                    // 6A86 on 1.22; on 1.23 the log with the other unit's count after the four
                    assertEquals(SW_OK, r.getSW(), name);
                    byte[] zero = c.send(new CommandAPDU(CLA, INS_GET_LOG, 0, 0, 256)).getData();
                    assertArrayEquals(concat(Arrays.copyOf(zero, 16), new byte[4], Arrays.copyOfRange(zero, 16, zero.length)), r.getData(), name + ": P1 = 2 is P1 = 0's and the other unit's count (nothing)");
                    compared++;
                    continue;
                }
                assertEquals(want, r.getSW(), name);
                byte[] got = r.getData();
                if ((apdu[0] & 0xFF) == 0x00 && ins == 0xA4) {
                    assertArrayEquals(new byte[] { 1, 0x16 }, wantData, name);
                    assertArrayEquals(new byte[] { 1, 0x17 }, got, name + ": the version, 1.23");
                } else if (ins == 0x01 && got.length >= 30) {
                    assertEquals(0x16, wantData[1]);
                    assertEquals(0x17, got[1], name + ": the version");
                    got = got.clone();
                    got[1] = 0x16;
                    assertArrayEquals(wantData, got, name);
                } else if (ins == 0x16 && unit == USD && got.length > 2 && wantData.length == got.length) {
                    assertEquals(c.record()[CashuApplet.CARD_SET_OFFSET] == 1 ? USD : 0, got[2], name + ": the unit, dollars once a record is written");
                    got = got.clone();
                    got[2] = wantData[2];
                    assertArrayEquals(wantData, got, name);
                } else {
                    assertArrayEquals(wantData, got, name);
                }
                compared++;
            }
            if (unit == SATS) assertEquals(19, refusedOld, "the old-length SET_LIMITs that 1.22 took, sixteen of the owner's and three of the PIN's, every one refused on a sats record");
            return compared;
        } finally {
            CashuAppletTest.useTheTestFloor();
        }
    }

    /** Whether the card has an owner, read without a command (a command would end a held count or give up a payment the recording had in hand). */
    private static boolean owned(Card c) throws Exception { return c.field("ownerSet")[0] == 1; }

    /**
     * What 1.22's SET_LIMIT wrote, written by hand into the record of a card that has just refused the old length on a sats record,
     * with the tests' rate of 100 sats to the dollar: so that the replay goes on from the state the recording went on from. The
     * owner's form (34): the proof's length, the proof, the value; the PIN's (33): the value.
     */
    private static void writeAs122(Card c, int ins, byte[] apdu) throws Exception {
        byte[] rec = c.record();
        int lc = apdu[4] & 0xFF;
        byte[] data = Arrays.copyOfRange(apdu, 5, 5 + lc);
        System.arraycopy(u32(100), 0, rec, CashuApplet.CARD_RATE_OFFSET, 4);
        if (ins == 0x33) {
            System.arraycopy(data, 0, rec, CashuApplet.CARD_TAP_LIMIT_OFFSET, data.length);
            if (data.length == 8) System.arraycopy(new byte[4], 0, rec, CashuApplet.CARD_NOPIN_SPENT_OFFSET, 4);
            return;
        }
        byte[] v = Arrays.copyOfRange(data, 1 + (data[0] & 0xFF), data.length);
        boolean both = v.length >= 8, three = v.length == 12;
        boolean day = !both || !Arrays.equals(Arrays.copyOf(v, 4), Arrays.copyOfRange(rec, CashuApplet.CARD_LIMIT_OFFSET, CashuApplet.CARD_LIMIT_OFFSET + 4));
        boolean tap = both && !Arrays.equals(Arrays.copyOfRange(v, 4, 8), Arrays.copyOfRange(rec, CashuApplet.CARD_TAP_LIMIT_OFFSET, CashuApplet.CARD_TAP_LIMIT_OFFSET + 4));
        boolean allowance = three && !Arrays.equals(Arrays.copyOfRange(v, 8, 12), Arrays.copyOfRange(rec, CashuApplet.CARD_NOPIN_LIMIT_OFFSET, CashuApplet.CARD_NOPIN_LIMIT_OFFSET + 4));
        if (day) {
            System.arraycopy(v, 0, rec, CashuApplet.CARD_LIMIT_OFFSET, 4);
            System.arraycopy(rec, CashuApplet.CARD_NOW_OFFSET, rec, CashuApplet.CARD_WINDOW_OFFSET, 4);
            System.arraycopy(new byte[4], 0, rec, CashuApplet.CARD_SPENT_OFFSET, 4);
        }
        if (tap) System.arraycopy(v, 4, rec, CashuApplet.CARD_TAP_LIMIT_OFFSET, 4);
        if (allowance) {
            System.arraycopy(v, 8, rec, CashuApplet.CARD_NOPIN_LIMIT_OFFSET, 4);
            System.arraycopy(new byte[4], 0, rec, CashuApplet.CARD_NOPIN_SPENT_OFFSET, 4);
        }
    }

    @Test
    @DisplayName("U34 The cents are worked in scratch and ask no RAM: the conversion names scratch at offsets inside X_HEX only, and nothing else is written there between the conversion and its use; the RAM budget stays at 941 and 7")
    void theCentsAreWorkedInScratchAndAskNoRam() throws Exception {
        String code = CashuAppletTestSource.code();
        assertEquals(0, CashuAppletTestSource.constant(code, "X_CENTS"));
        assertEquals(8, CashuAppletTestSource.constant(code, "X_DIV"));
        assertTrue(CashuAppletTestSource.constant(code, "X_DIV") + 10 <= CashuAppletTestSource.constant(code, "X_MSG"), "the cents (8) and the division's room (10) inside X_HEX, before X_MSG");
        String cents = CashuAppletTestSource.body(code, "private void centsOfPayment(", "private void toCents(");
        String to = CashuAppletTestSource.body(code, "private void toCents(", "private static void addUint32Stop(");
        for (String body : new String[] { cents, to }) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("scratch\\s*,\\s*(\\(short\\)\\s*\\([^)]*\\)|[^,)]+)").matcher(body);
            while (m.find()) {
                String off = m.group(1).trim();
                assertTrue(off.startsWith("X_CENTS") || off.startsWith("X_DIV") || off.startsWith("(short)(X_CENTS") || off.startsWith("(short)(X_DIV") || off.equals("off"),
                    "scratch at " + off + ": inside X_HEX only");
            }
            assertFalse(body.contains("makeTransient") || body.contains("new byte"), "no RAM asked for, nothing allocated");
        }
        String sign = CashuAppletTestSource.body(code, "private void processSpendAllSign(", "private void finishBurn(");
        int conversion = sign.indexOf("if (waits <= 0) centsOfPayment();");
        int lastUse = sign.lastIndexOf("requireUnderLimits(scratch, (short)(X_CENTS + 4), (short) 0);");
        String between = sign.substring(conversion + 40, lastUse);
        assertFalse(between.contains("X_HEX") || between.contains("toHex(") || between.contains("secretTail(") || between.contains("hexInto("), "nothing that writes X_HEX runs between the conversion and its use");
        assertFalse(between.contains("scratch, (short) 0") || between.contains("X_DIV") || between.contains("scratch, X_HEX"), "and nothing else writes there");
        // and the budgets, as SchnorrHWMathTest pins them, are the ones it pins: 941 and 7, the constructors' 30,069
        assertEquals(941, SchnorrHWMathTest.RAM_ON_DESELECT_MOST);
        assertFalse(code.contains("JCSystem.makeTransientByteArray(BOOK_LEN") || CashuAppletTestSource.body(code, "private CashuApplet()", "private void initCardKeypair(").contains("book"), "the book is not RAM and not the constructor's");
    }
    @Test
    @DisplayName("U35 Sats onto a card with a limit and no rate are 6A99 at the load, nothing written: a card with no owner, whose rate can be written only while it is empty, never holds sats it cannot pay; dollars still go on, change still comes back, and with a rate the sats go on")
    void satsOntoACardWithALimitAndNoRateAreRefusedAtTheLoad() throws Exception {
        // a sats card with no owner: the PIN's twelve bytes, a limit on one payment and a rate of 0
        Card c = phoneless(SATS);
        assertEquals(SW_OK, c.pinLimits(100, 0, 0), "a limit with a rate of 0: taken, as the design has it");
        c.tap();
        c.hold();
        Map<String, byte[]> before = c.snapshot();
        assertEquals(SW_NO_RATE, c.load(c.piece(SATS_A, 500)).getSW(), "the sats are refused");
        assertEquals(SW_NO_RATE, c.sw(new CommandAPDU(CLA, INS_LOAD_PROOF, 0, 1, concat(new byte[] { 0 }, c.piece(SATS_A, 500)))), "labelled or not");
        c.assertSame(before, "nothing written: no piece, no entry, no log");
        assertEquals(SW_OK, c.pinLimits(100, 0, 1000), "the card is still empty, and the holder writes a rate");
        c.tap();
        int p = c.put(-1, SATS_A, 500);
        c.tap();
        c.pin();
        assertEquals(SW_OK, c.pay(p).getSW(), "with a rate the sats go on and are paid");

        // a dollars card with no owner set by 1.22's eight bytes: the rate stays 0; a held tap under the PIN alone
        Card d = phoneless(USD);
        assertEquals(SW_OK, d.sw(new CommandAPDU(CLA, INS_SET_LIMIT, 0, 0, concat(u32(500), u32(100)))), "1.22's eight bytes on a dollars record");
        d.tap();
        d.hold();
        assertEquals(SW_NO_RATE, d.sw(new CommandAPDU(CLA, INS_LOAD_PROOF, 0, 1, concat(new byte[] { 0 }, d.piece(SATS_A, 300)))), "the sats are refused");
        assertEquals(4, d.info(0)[8], "the card is still a card of one unit");
        int u = d.put(USD, USD_A, 100);
        d.tap();
        d.pin();
        assertEquals(SW_OK, d.pay(u).getSW(), "dollars go on and are paid");

        // with no limit set, sats go on with no rate, as they always did
        Card e = phoneless(SATS);
        e.tap();
        e.hold();
        assertTrue(e.put(-1, SATS_A, 10) >= 0);
    }

    @Test
    @DisplayName("U36 LOAD_CHANGE's log entry is of its opening's unit: dollar change on a sats card is logged in an entry of the other unit, as cents")
    void dollarChangeIsLoggedAsDollars() throws Exception {
        Card c = owned(SATS);
        c.tap();
        c.grant();
        int u = c.put(USD, USD_A, 100);
        c.put(-1, SATS_A, 100);
        c.tap();
        byte[][] out = new byte[1][];
        assertEquals(SW_OK, c.payWithChange(40, out, u).getSW(), "a dollar payment of $1.00, 40 cents of it change");
        c.power();
        assertEquals(SW_OK, c.sw(loadChangeCommand(0, mintAnswer(out[0]))), "the change back, with no PIN, in a tap of its own");
        c.pin();
        byte[] log = c.log(0).getData();
        assertEquals(40, readUint32(log, 16 + 12), "the newest entry: 40 put on");
        assertEquals(0x20, log[16 + 10] & 0x20, "and they are cents: the entry is of the other unit than the record's");
    }
}
