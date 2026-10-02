package me.flashapp.cashu;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.licel.jcardsim.base.SimulatorRuntime;
import com.licel.jcardsim.smartcardio.CardSimulator;
import com.licel.jcardsim.utils.AIDUtil;
import javacard.framework.AID;
import javacard.framework.Applet;
import javax.smartcardio.CommandAPDU;
import javax.smartcardio.ResponseAPDU;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ENG-620: a proof slot survives the card leaving the field mid-write.
 *
 * A slot is status[1] ‖ keyset[8] ‖ amount[4] ‖ nonce[32] ‖ C[33], and every
 * reader trusts the status byte: GET_BALANCE sums the UNSPENT slots and
 * SPEND_PROOF signs for them. So the status byte is the slot's commit. It is
 * written as a single byte, which the JCRE makes atomic, and only after every
 * other write to the slot, so a tear leaves a slot in its old state and never
 * in a new state over old bytes. Written first, it once let a tear turn an
 * empty slot into a phantom UNSPENT proof, and a torn CLEAR_SPENT followed by
 * a torn LOAD_PROOF resurrect a spent proof's amount.
 *
 * jCardSim cannot tear a write, so this is checked two ways, as D10 is: the
 * order is scanned in the source, and the states a tear can now leave are set
 * in the slot storage directly and shown to be harmless to every command.
 */
class SlotWriteOrderTest {

    // ── the order, in the source ─────────────────────────────────────────────

    @Test
    @DisplayName("every slot writer commits the status byte last, and no bulk write covers it")
    void appletCommitsTheStatusByteLast() throws Exception {
        String src = new String(
            java.nio.file.Files.readAllBytes(
                SchnorrHWMathTest.mainSourceDir().resolve("CashuApplet.java")),
            java.nio.charset.StandardCharsets.UTF_8);

        List<String> violations = slotWriteViolations(src);
        assertTrue(violations.isEmpty(), String.join("\n", violations));

        // A scan that matched nothing would prove nothing: LOAD_PROOF,
        // CLEAR_SPENT and SPEND_PROOF each commit a status byte.
        long commits = slotWrites(src).stream().filter(w -> w.status).count();
        assertTrue(commits >= 3, "expected the three status commits, found " + commits);
    }

    @Test
    @DisplayName("the scan fails a status-first load, a whole-slot clear and a write after the commit")
    void theScanCanSayNo() {
        String statusFirst = "class A { void load() {"
            + " proofStorage[(short)(base + PROOF_STATUS_OFFSET)] = STATUS_UNSPENT;"
            + " Util.arrayCopy(buf, (short) 5, proofStorage, (short)(base + PROOF_KEYSET_OFFSET), PROOF_DATA_LEN);"
            + " } }";
        String wholeSlot = "class A { void clear() {"
            + " Util.arrayFillNonAtomic(proofStorage, base, PROOF_SIZE, (byte) 0);"
            + " } }";
        String afterCommit = "class A { void f() {"
            + " proofStorage[(short)(base + PROOF_STATUS_OFFSET)] = STATUS_SPENT;"
            + " proofStorage[(short)(base + 9)] = (byte) 0;"
            + " } }";
        // A status byte written without naming its offset is still a commit.
        String unnamedStatus = "class A { void clear() {"
            + " proofStorage[base] = STATUS_EMPTY;"
            + " Util.arrayFillNonAtomic(proofStorage, (short)(base + PROOF_KEYSET_OFFSET), PROOF_DATA_LEN, (byte) 0);"
            + " } }";
        String good = "class A {"
            + " void load() {"
            + "  Util.arrayCopy(buf, (short) 5, proofStorage, (short)(base + PROOF_KEYSET_OFFSET), PROOF_DATA_LEN);"
            + "  proofStorage[(short)(base + PROOF_STATUS_OFFSET)] = STATUS_UNSPENT;"
            + " }"
            + " void clear() {"
            + "  for (short i = 0; i < MAX_PROOFS; i++) {"
            + "   Util.arrayFillNonAtomic(proofStorage, (short)(base + PROOF_KEYSET_OFFSET), PROOF_DATA_LEN, (byte) 0);"
            + "   proofStorage[(short)(base + PROOF_STATUS_OFFSET)] = STATUS_EMPTY;"
            + "  }"
            + " }"
            + " boolean read() { return proofStorage[(short)(base + PROOF_STATUS_OFFSET)] == STATUS_UNSPENT; }"
            + " }";

        assertFalse(slotWriteViolations(statusFirst).isEmpty(), "status-first load passed");
        assertFalse(slotWriteViolations(wholeSlot).isEmpty(), "whole-slot fill passed");
        assertFalse(slotWriteViolations(afterCommit).isEmpty(), "write after the commit passed");
        assertFalse(slotWriteViolations(unnamedStatus).isEmpty(), "unnamed status commit passed");
        assertTrue(slotWriteViolations(good).isEmpty(), String.join("\n", slotWriteViolations(good)));
    }

    /** One write to proofStorage, in the comment-stripped source. */
    private static final class SlotWrite {
        final int at;
        final boolean status;
        final String text;

        SlotWrite(int at, boolean status, String text) {
            this.at = at;
            this.status = status;
            this.text = text;
        }
    }

    // proofStorage[...] = …, and the compound forms; not ==.
    private static final Pattern ELEMENT_WRITE = Pattern.compile(
        "proofStorage\\s*\\[([^\\]]*)\\]\\s*(?:[-+*/%&|^]|<<|>>>?)?=(?!=)([^;]*);");
    private static final Pattern BULK_WRITE = Pattern.compile(
        "Util\\.(arrayCopy|arrayCopyNonAtomic|arrayFill|arrayFillNonAtomic)\\s*\\(");
    // Any slot field offset but the status byte's.
    private static final Pattern DATA_OFFSET = Pattern.compile("PROOF_(?!STATUS_)[A-Z_]*OFFSET");

    private static List<SlotWrite> slotWrites(String rawSrc) {
        String src = SchnorrHWMathTest.stripCommentsAndCharLiterals(rawSrc);
        List<SlotWrite> writes = new ArrayList<>();
        Matcher element = ELEMENT_WRITE.matcher(src);
        while (element.find()) {
            boolean status = element.group(1).contains("PROOF_STATUS_OFFSET")
                || element.group(2).contains("STATUS_");
            writes.add(new SlotWrite(element.start(), status, element.group().trim()));
        }
        Matcher bulk = BULK_WRITE.matcher(src);
        while (bulk.find()) {
            List<String> args = arguments(src, bulk.end() - 1);
            boolean fill = bulk.group(1).startsWith("arrayFill");
            String destination = args.get(fill ? 0 : 2).trim();
            if (destination.equals("proofStorage")) {
                writes.add(new SlotWrite(bulk.start(), false, bulk.group() + String.join(",", args) + ")"));
            }
        }
        writes.sort((a, b) -> Integer.compare(a.at, b.at));
        return writes;
    }

    /**
     * Violations of the slot commit rule in a source file:
     * - a bulk write (arrayCopy/arrayFill) into proofStorage whose offset does
     *   not name a data field, and so can start at the status byte;
     * - any write to proofStorage after a status write in the same method.
     */
    static List<String> slotWriteViolations(String rawSrc) {
        String src = SchnorrHWMathTest.stripCommentsAndCharLiterals(rawSrc);
        int[] method = methodBodies(src);
        List<String> violations = new ArrayList<>();
        List<SlotWrite> writes = slotWrites(rawSrc);

        Matcher bulk = BULK_WRITE.matcher(src);
        while (bulk.find()) {
            List<String> args = arguments(src, bulk.end() - 1);
            boolean fill = bulk.group(1).startsWith("arrayFill");
            if (!args.get(fill ? 0 : 2).trim().equals("proofStorage")) continue;
            String offset = args.get(fill ? 1 : 3);
            if (!DATA_OFFSET.matcher(offset).find()) {
                violations.add("a bulk write into proofStorage can cover the status byte (offset '"
                    + offset.trim() + "'): write the data fields, then the status byte alone");
            }
        }

        for (int i = 0; i < writes.size(); i++) {
            SlotWrite commit = writes.get(i);
            if (!commit.status) continue;
            for (int j = i + 1; j < writes.size(); j++) {
                SlotWrite later = writes.get(j);
                if (method[later.at] != method[commit.at] || method[commit.at] < 0) break;
                if (!later.status) {
                    violations.add("'" + later.text + "' writes the slot after its status byte was"
                        + " committed ('" + commit.text + "'): a tear between them leaves the new"
                        + " status over old bytes");
                }
            }
        }
        return violations;
    }

    /** For each index, the open brace of the method body it sits in, or -1. */
    private static int[] methodBodies(String src) {
        int[] depth = SchnorrHWMathTest.braceDepths(src);
        int[] method = new int[src.length()];
        int current = -1;
        for (int i = 0; i < src.length(); i++) {
            if (src.charAt(i) == '{' && depth[i] == 2) current = i;
            method[i] = depth[i] >= 2 ? current : -1;
        }
        return method;
    }

    /** The top-level comma-separated arguments of the call whose '(' is at `open`. */
    private static List<String> arguments(String src, int open) {
        List<String> args = new ArrayList<>();
        int depth = 0;
        int start = open + 1;
        for (int i = open; i < src.length(); i++) {
            char c = src.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                if (--depth == 0) {
                    args.add(src.substring(start, i));
                    return args;
                }
            } else if (c == ',' && depth == 1) {
                args.add(src.substring(start, i));
                start = i + 1;
            }
        }
        throw new IllegalStateException("unbalanced call at " + open);
    }

    // ── the states a tear can now leave ──────────────────────────────────────

    /** A runtime that hands the test its applet, so a slot can be set to what a tear leaves. */
    private static final class ExposedRuntime extends SimulatorRuntime {
        Applet appletAt(AID aid) {
            return getApplet(aid);
        }
    }

    private static final byte CLA = CashuAppletTest.CLA;
    private CardSimulator sim;
    private byte[] storage;

    private void freshCard() throws Exception {
        ExposedRuntime runtime = new ExposedRuntime();
        sim = new CardSimulator(runtime);
        AID aid = AIDUtil.create(CashuAppletTest.AID_HEX);
        sim.installApplet(aid, CashuApplet.class);
        ResponseAPDU select = sim.transmitCommand(new CommandAPDU(
            0x00, 0xA4, 0x04, 0x00, CashuAppletTest.hexToBytes(CashuAppletTest.AID_STR)));
        assertEquals(CashuAppletTest.SW_OK, select.getSW());
        java.lang.reflect.Field field = CashuApplet.class.getDeclaredField("proofStorage");
        field.setAccessible(true);
        storage = (byte[]) field.get(runtime.appletAt(aid));
    }

    private ResponseAPDU send(CommandAPDU apdu) {
        return sim.transmitCommand(apdu);
    }

    private ResponseAPDU load(byte[] proof) {
        return send(new CommandAPDU(CLA, CashuAppletTest.INS_LOAD_PROOF, 0, 0, proof, 0, proof.length, 1));
    }

    private ResponseAPDU spend(int slot) {
        return send(new CommandAPDU(CLA, CashuAppletTest.INS_SPEND_PROOF, slot, 0, new byte[32], 64));
    }

    private ResponseAPDU clearSpent() {
        return send(new CommandAPDU(CLA, CashuAppletTest.INS_CLEAR_SPENT, 0, 0, 1));
    }

    private ResponseAPDU proofAt(int slot) {
        return send(new CommandAPDU(CLA, CashuAppletTest.INS_GET_PROOF, slot, 0, 78));
    }

    private long balance() {
        byte[] b = send(new CommandAPDU(CLA, CashuAppletTest.INS_GET_BALANCE, 0, 0, 4)).getData();
        return ((b[0] & 0xFFL) << 24) | ((b[1] & 0xFFL) << 16) | ((b[2] & 0xFFL) << 8) | (b[3] & 0xFFL);
    }

    private byte[] statuses() {
        return send(new CommandAPDU(CLA, CashuAppletTest.INS_GET_SLOT_STATUS, 0, 0,
            CashuAppletTest.MAX_PROOFS)).getData();
    }

    private int proofCount() {
        return send(new CommandAPDU(CLA, CashuAppletTest.INS_GET_PROOF_COUNT, 0, 0, 1)).getData()[0];
    }

    private byte[] slotBytes(int slot) {
        return Arrays.copyOfRange(storage, slot * 78, slot * 78 + 78);
    }

    @Test
    @DisplayName("a LOAD_PROOF torn before its commit leaves an empty slot: no read sees it, and the next load takes it whole")
    void aTornLoadLeavesTheSlotEmpty() throws Exception {
        freshCard();
        // What LOAD_PROOF leaves when the card goes between the copy and the
        // commit: the proof's 77 bytes are in, the status still says EMPTY.
        System.arraycopy(CashuAppletTest.PROOF_1, 0, storage, 1, 77);

        assertEquals(0, statuses()[0]);
        assertEquals(0, balance());
        assertEquals(0, proofCount());
        assertEquals(CashuAppletTest.SW_SLOT_EMPTY, proofAt(0).getSW());
        assertEquals(CashuAppletTest.SW_SLOT_EMPTY, spend(0).getSW());

        ResponseAPDU loaded = load(CashuAppletTest.PROOF_2);
        assertEquals(CashuAppletTest.SW_OK, loaded.getSW());
        assertEquals(0, loaded.getData()[0], "the torn slot is the first empty one and is reused");
        byte[] slot = proofAt(0).getData();
        assertEquals(1, slot[0]);
        assertArrayEquals(CashuAppletTest.PROOF_2, Arrays.copyOfRange(slot, 1, 78),
            "nothing of the torn load's bytes survives the next one");
        assertEquals(500, balance());
    }

    @Test
    @DisplayName("a CLEAR_SPENT torn mid-slot leaves it spent: never counted or signed for, still answered by GET_PROOF with fields zeroed, and the next CLEAR_SPENT finishes it")
    void aTornClearLeavesTheSlotSpent() throws Exception {
        freshCard();
        assertEquals(CashuAppletTest.SW_OK, load(CashuAppletTest.PROOF_1).getSW());
        assertEquals(CashuAppletTest.SW_OK, spend(0).getSW());
        // What CLEAR_SPENT leaves when the card goes mid-fill: part of the data
        // zeroed, the status still SPENT.
        Arrays.fill(storage, 1, 40, (byte) 0);

        assertEquals(2, statuses()[0]);
        assertEquals(0, balance());
        assertEquals(0x6985, spend(0).getSW(), "a spent slot is never signed for again");

        // Unlike the empty slot a torn LOAD_PROOF leaves, this one is still
        // read: GET_PROOF returns it as a spent slot whose fields are partly
        // zeroed, and its data is no longer a proof (spec/APDU.md, CLEAR_SPENT).
        // A reader has to check a spent slot's data before it trusts it;
        // cardctl dump skips one that fails.
        ResponseAPDU halfCleared = proofAt(0);
        assertEquals(CashuAppletTest.SW_OK, halfCleared.getSW());
        byte[] read = halfCleared.getData();
        assertEquals(78, read.length);
        assertEquals(2, read[0], "a half-cleared slot still reads as spent");
        assertArrayEquals(new byte[4], Arrays.copyOfRange(read, 9, 13),
            "the amount GET_PROOF reports is the zeroed one, not the proof's");

        ResponseAPDU cleared = clearSpent();
        assertEquals(CashuAppletTest.SW_OK, cleared.getSW());
        assertEquals(1, cleared.getData()[0]);
        assertArrayEquals(new byte[78], slotBytes(0));
    }

    @Test
    @DisplayName("CLEAR_SPENT empties a spent slot's bytes as well as its status, and leaves unspent slots alone")
    void clearSpentEmptiesTheWholeSlot() throws Exception {
        freshCard();
        assertEquals(CashuAppletTest.SW_OK, load(CashuAppletTest.PROOF_1).getSW());
        assertEquals(CashuAppletTest.SW_OK, load(CashuAppletTest.PROOF_2).getSW());
        byte[] unspent = slotBytes(1);
        assertEquals(CashuAppletTest.SW_OK, spend(0).getSW());

        ResponseAPDU cleared = clearSpent();
        assertEquals(1, cleared.getData()[0]);
        assertArrayEquals(new byte[78], slotBytes(0));
        assertArrayEquals(unspent, slotBytes(1));
        assertEquals(500, balance());
    }
}
