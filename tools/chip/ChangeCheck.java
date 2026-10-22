import javax.smartcardio.Card;
import javax.smartcardio.CardChannel;
import javax.smartcardio.CardTerminal;
import javax.smartcardio.CommandAPDU;
import javax.smartcardio.TerminalFactory;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Arrays;

/**
 * Software 1.21 and 1.22 on the chip: a PIN first, a card set up and loaded with no owner (a holder with no phone) and its history
 * read as a guest, the owner's claim, the card's own change unblinded on the card (LOAD_CHANGE) and the tap after a payment that
 * loads that change and nothing else, and an opening a till bound to a key of its own, mended under the PIN or given up (1.22). What jCardSim cannot say is how long the card's own work takes, and the three figures this
 * run is for are printed: how long the guest's held read of the history takes from its first asking to the log, how long a claim
 * takes from its first asking to the owner set, and how long one LOAD_CHANGE takes (two RSA operations, two key agreements and
 * three generic mappings, and the hex of four points).
 *
 *   select (01 16 or later); GET_INFO;
 *   on a card with no PIN: LOAD_PROOF, SPEND_ALL_BEGIN and SET_OWNER refused with 6A97;
 *   a holder with no phone (1.22): SET_PIN; SET_CARD and LOAD_PROOF without the PIN 6982; VERIFY_PIN; SET_CARD with no refund key;
 *   SET_LIMIT's PIN form with eight bytes; a piece loaded with no owner; the log asked for again while the card says 6A98, ten
 *   askings (timed); the receipts at once; SPEND_ALL_BEGIN 6985 in that tap; a new tap: the piece paid, and the log and the
 *   receipts 6985 in it; a new tap: an asking, another command, and the count begun again; CLEAR_SPENT under the PIN;
 *   a new tap, a used card taken up again: SET_PIN 6982 (a stranger's), VERIFY_PIN, SET_PIN 6982 (not held for), the log held for, SET_PIN,
 *   VERIFY_PIN, SET_CARD, then SET_OWNER asked again while the card says "not yet" (timed);
 *   a new tap and the PIN; a piece loaded; a payment of it that makes two change outputs for the card (each timed); GET_CHANGE;
 *   a new tap with no PIN: GET_INFO byte 29 is 0, LOAD_PROOF and CLEAR_SPENT are 6982; LOAD_CHANGE with a proof that does not
 *   hold is 6A80; the two openings' change by LOAD_CHANGE, with no PIN (each timed), the piece's C checked here against the mint's
 *   own signature on the opening's secret; GET_DLEQ for it reads 00;
 *   1.22, an opening bound by a till to a key of its own: on the card with no owner, a piece, a payment whose change is bound to
 *   the till's key; the let-go (RESET_CARD, P1 = 1) 6985 in that tap and 6982 with the PIN and no hold, as LOAD_CHANGE under
 *   another key is; the history held for;
 *   SET_PIN 6A8D with change owed; the let-go with a number not P2 6B00, then under the PIN and the hold; the log's bit 4;
 *   and, on the card with an owner after its change is loaded, a payment whose first change is bound to the till's key:
 *   LOAD_CHANGE of the mint's real answer 6985 in the paying tap, 6A80 with no PIN and 6982 under the PIN alone; under the PIN
 *   and the owner's grant a spoiled proof 6A80 and
 *   the real answer taken (timed), its C the mint's; RESET_CARD 6A8D with the second opening owed; its let-go by the PIN alone
 *   and by another key 6A91, for another number 6B00, then by the owner's proof; the log's bit 4;
 *   then the change paid off, the places freed and the card reset.
 *
 * On a card of software 1.23 (01 17) a second run follows, blank card to blank card, of dollars and sats on one card
 * (docs/FOXY-CARD-KEYSETS.md 10.7), with a keyset made up for each unit as the run makes up its mint's key:
 *
 *   GET_INFO P1 = 3, 51 bytes, format 4; the PIN, a record in sats, the owner's claim; SET_LIMIT of sixteen bytes ($1.00 on one
 *   payment, $2.00 with no PIN, 1,000 sats to the dollar), read back by the owner's grant, and GET_CARD's 29 bytes still zeros;
 *   a new tap and the PIN: a plain load of sats (entered 40), a load labelled dollars 6A9D, then with the grant taken (C1);
 *   format 5; the table lists both; a payment of a sats piece and a dollar piece 6A9A;
 *   a new tap with no PIN: 2,000 sats paid with 1,500 of change (200 cents, the whole allowance; the SIGN timed), the change back
 *   by LOAD_CHANGE; a new tap with no PIN, 10 sats more: 6A94;
 *   1,500 sats under the PIN: 150 cents, a limit and a half, ten waits counted (timed);
 *   a dollar payment with change: the dollar entry kept while the opening is owed, through a CLEAR_SPENT; the change back;
 *   SET_LIMIT with no rate: a sats payment 6A99, a dollar payment signs; the dollars paid off and CLEAR_SPENT: the dollar entry let
 *   go, format 4; a drawer of 120 pieces: GET_BALANCE P1 = 1 and the reclaim over it timed, paid in one payment;
 *   SET_CARD again on the empty card (the table empty, the last unit gone); RESET_CARD; blank.
 *
 * Each command's time is printed beside the transactions it holds: SIGN's (the payment and its receipt), LOAD_PROOF's log,
 * SET_LIMIT's (the rate with the figures), SET_CARD's (the reclaim before it).
 *
 * This run is its own mint: it makes up a key k, signs the card's blinded messages with it (C_ = k*B_) and proves it (NUT-12), so
 * the change it writes is real to this run and to nobody else; the card cannot tell a mint's key from another, which is what the
 * run relies on. It makes up an owner key as well and uses it once. Nothing is kept.
 *
 * It refuses to begin on a card that is not blank (an owner, a record or any piece), so it cannot disturb a card in use, and it ends
 * with the card blank. A run that stops part way leaves test pieces on the card: install the software again.
 *
 *   sh tools/chip/changecheck.sh                 the card in the reader, by PC/SC
 *   sh tools/chip/changecheck.sh 127.0.0.1:47470 a card behind a bridge or the simulator's server (tools/chip/run.sh, tools/cardsim/run.sh)
 *
 * Needs GlobalPlatformPro's jar (gp.jar) for its PC/SC binding, found through GP_JAR or next to this file. Exits 0 if every check
 * held and 1 if one did not, 2 if the card is not blank, not 1.22 or later, or cannot be reached. It sends nothing a phone does
 * not send.
 */
public final class ChangeCheck {
    private static final String AID = "F0464F58594341524401";
    private static final byte[] KEYSET = hex("0059534ce0bfa19a");
    private static final byte[] PIN = { '1', '2', '3', '4' };

    private static final BigInteger P = new BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFFC2F", 16);
    private static final BigInteger N = new BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141", 16);
    private static final BigInteger[] G = {
        new BigInteger("79BE667EF9DCBBAC55A06295CE870B07029BFCDB2DCE28D959F2815B16F81798", 16),
        new BigInteger("483ADA7726A3C4655DA4FBFC0E1108A8FD17B448A68554199C47D08FFB10D4B8", 16) };

    /** One command and its answer, status word last. */
    interface Link {
        byte[] send(byte[] command) throws Exception;
        /** The card taken out of the field and put back: a new tap, nothing of the last one kept. */
        void retap() throws Exception;
        void close() throws Exception;
    }

    private static Link link;
    private static int checks = 0, failed = 0;

    public static void main(String[] args) throws Exception {
        try {
            link = args.length > 0 ? bridge(args[0]) : reader();
        } catch (Exception e) {
            System.out.println("cannot reach the card: " + e.getMessage());
            System.exit(2);
        }
        int code = 0;
        try {
            code = run();
        } finally {
            link.close();
        }
        System.exit(code);
    }

    private static int run() throws Exception {
        byte[] selected = select();
        if (sw(selected) != 0x9000 || selected.length != 4 || selected[0] != 1 || (selected[1] & 0xFF) < 0x16) {
            System.out.println("the card's applet is not software 1.22 or later: " + toHex(selected));
            return 2;
        }
        byte[] info = data(send(0xB0, 0x01, 0, 0, null, 0), 0x9000);
        if (info == null || info.length != 30 || info[16] != 0 || info[11] != 0 || info[3] != 0 || info[4] != 0) {
            System.out.println("the card is not blank (an owner, a record or a piece is on it): nothing was done. Make it blank first.");
            return 2;
        }
        System.out.println("the card is blank, on software 1." + (info[1] & 0xFF) + ". Checking a PIN first, a card with no owner, the claim and the card's own change.");

        // a PIN first (a card a run left with a PIN cannot show it; SET_PIN replaces that PIN)
        if (info[7] == 0) {
            say("LOAD_PROOF on a card with no PIN is 6A97", sw(send(0xB0, 0x30, 0, 0, piece(8, 1), 0)) == 0x6A97);
            say("SPEND_ALL_BEGIN on it is 6A97", sw(send(0xB0, 0x22, 0, 0, new byte[] { 0 }, 4)) == 0x6A97);
            say("SET_OWNER on it is 6A97", sw(send(0xB0, 0x43, 0, 0, uncompressed(ownerKey()), -1)) == 0x6A97);
        } else {
            System.out.println("      the card has a PIN from an earlier run: the refusals of a card with no PIN are not tried");
        }

        // a holder with no phone (1.22): the phone that first sends the card money sets it up under the holder's PIN and loads it
        say("SET_PIN, the holder's", sw(send(0xB0, 0x41, 0, 0, PIN, -1)) == 0x9000);
        say("SET_CARD with the PIN set and not given is 6982", sw(send(0xB0, 0x32, 0, 0, record(), -1)) == 0x6982);
        say("LOAD_PROOF with the PIN set and not given is 6982", sw(send(0xB0, 0x30, 0, 0, piece(8, 21), 0)) == 0x6982);
        say("VERIFY_PIN", sw(send(0xB0, 0x40, 0, 0, PIN, -1)) == 0x9000);
        say("SET_CARD under the PIN, no refund key: a cash card", sw(send(0xB0, 0x32, 0, 0, record(), -1)) == 0x9000);
        // 1.23 takes the PIN form in twelve bytes on a sats record, the rate after the two figures (100 sats to the dollar: a sat is a cent)
        boolean units = (selected[1] & 0xFF) >= 0x17;
        say("SET_LIMIT's PIN form, " + (units ? "twelve bytes: the limit on one payment, the no-PIN allowance and the rate" : "eight bytes: the limit on one payment and the no-PIN allowance"),
            sw(send(0xB0, 0x33, 0, 0, units ? cat(u32(1000), u32(0), u32(100)) : cat(u32(1000), u32(0)), -1)) == 0x9000);
        if (units) say("1.22's eight bytes on a sats record are 6700 (1.23)", sw(send(0xB0, 0x33, 0, 0, cat(u32(1000), u32(0)), -1)) == 0x6700);
        say("a piece of 8 goes on under the PIN, with no owner", Arrays.equals(data(send(0xB0, 0x30, 0, 0, piece(8, 21), 0), 0x9000), new byte[] { 0 }));
        byte[] owned = data(send(0xB0, 0x01, 0, 0, null, 0), 0x9000);
        say("and the card has no owner", owned != null && owned[16] == 0 && owned[3] == 1);
        say("SET_LIMIT's PIN form with a piece on the card is 6A8D", sw(send(0xB0, 0x33, 0, 0, u32(0), -1)) == 0x6A8D);
        long held = System.nanoTime();
        int holding = 0;
        byte[] log = null;
        for (int asked = 1; asked <= 10; asked++) {
            byte[] r = send(0xB0, 0x18, 0, 0, null, 0);
            if (sw(r) == 0x6A98 && r.length == 2) { holding++; continue; }
            log = r;
            break;
        }
        long heldMs = (System.nanoTime() - held) / 1000000;
        say("the guest's log: nine 'keep holding' (6A98), and the log at the tenth asking", holding == 9 && log != null && sw(log) == 0x9000 && log.length == 2 + 16 + 20);
        System.out.println("      THE GUEST'S HOLD took " + heldMs + " ms from the first asking to the log (" + (holding > 0 ? heldMs / (holding + 1) : 0) + " ms an asking)");
        byte[] receipts = send(0xB0, 0x18, 1, 0, null, 0);
        say("the receipts at once, in the tap that held for the log", sw(receipts) == 0x9000 && receipts.length == 2 + 4);
        say("no payment in the tap that read the history: SPEND_ALL_BEGIN is 6985", sw(send(0xB0, 0x22, 0, 0, new byte[] { 0 }, 4)) == 0x6985);
        retapWithPin();
        say("the piece is paid, from a card with no owner", sw(send(0xB0, 0x22, 0, 0, new byte[] { 0 }, 4)) == 0x9000 && sign() != null);
        say("the log in the tap that paid is 6985", sw(send(0xB0, 0x18, 0, 0, null, 0)) == 0x6985);
        say("and the receipts", sw(send(0xB0, 0x18, 1, 0, null, 0)) == 0x6985);
        retapWithPin();
        say("the log: a first asking, 6A98", sw(send(0xB0, 0x18, 0, 0, null, 0)) == 0x6A98);
        say("GET_BALANCE between: any reader's, no hold", sw(send(0xB0, 0x11, 0, 0, null, 4)) == 0x9000);
        int again = 0;
        for (int asked = 1; asked <= 9; asked++) if (sw(send(0xB0, 0x18, 0, 0, null, 0)) == 0x6A98) again++;
        say("the count began again: nine more askings are each 6A98", again == 9);
        say("and the tenth answers the log", sw(send(0xB0, 0x18, 0, 0, null, 0)) == 0x9000);
        say("CLEAR_SPENT under the PIN, with no owner, frees the place", Arrays.equals(data(send(0xB0, 0x31, 0, 0, null, 1), 0x9000), new byte[] { 1 }));

        // an opening of change a till bound to a key of its own, given up on a card with no owner under the PIN and a hold (1.22)
        BigInteger till = new BigInteger(1, sha256("a till's own key".getBytes(StandardCharsets.US_ASCII))).mod(N);
        retapWithPin();
        say("a piece of 8 goes on under the PIN", Arrays.equals(data(send(0xB0, 0x30, 0, 0, piece(8, 22), 0), 0x9000), new byte[] { 0 }));
        say("SPEND_ALL_BEGIN of it", sw(send(0xB0, 0x22, 0, 0, new byte[] { 0 }, 4)) == 0x9000);
        say("change of 3, under a key the till made up", sw(send(0xB0, 0x26, 0, 0, cat(u32(3), compressed(mul(till, G))), 33)) == 0x9000);
        say("the payment is signed", sign() != null);
        say("RESET_CARD's let-go form in the tap that paid is 6985", sw(send(0xB0, 0x51, 1, 0, new byte[] { 0 }, -1)) == 0x6985);
        retapWithPin();
        say("the let-go under the PIN, no hold: 6982", sw(send(0xB0, 0x51, 1, 0, new byte[] { 0 }, -1)) == 0x6982);
        byte[] owedHere = data(send(0xB0, 0x19, 0, 0, null, 0), 0x9000);
        BigInteger mintHere = new BigInteger(1, sha256("a mint made up for one run".getBytes(StandardCharsets.US_ASCII))).mod(N);
        byte[] answerHere = mintAnswer(mintHere, Arrays.copyOfRange(owedHere, 1, 84), BigInteger.valueOf(17));
        say("LOAD_CHANGE under another key, the PIN and no hold: 6982", sw(send(0xB0, 0x53, 0, 0, answerHere, 83)) == 0x6982);
        int holds = 0;
        byte[] logged = null;
        for (int asked = 1; asked <= 10; asked++) {
            byte[] r = send(0xB0, 0x18, 0, 0, null, 0);
            if (sw(r) == 0x6A98 && r.length == 2) { holds++; continue; }
            logged = r;
            break;
        }
        say("the history held for", holds == 9 && logged != null && sw(logged) == 0x9000);
        say("SET_PIN with change owed is 6A8D", sw(send(0xB0, 0x41, 0, 0, PIN, -1)) == 0x6A8D);
        say("the let-go of opening 0 with another number in the data is 6B00", sw(send(0xB0, 0x51, 1, 0, new byte[] { 1 }, -1)) == 0x6B00);
        say("the let-go of opening 0, under the PIN and the hold", sw(send(0xB0, 0x51, 1, 0, new byte[] { 0 }, -1)) == 0x9000);
        byte[] none = data(send(0xB0, 0x19, 0, 0, null, 0), 0x9000);
        say("no opening is left", none != null && none.length == 1 && none[0] == 0);
        byte[] flagged = data(send(0xB0, 0x18, 0, 0, null, 0), 0x9000);
        say("the log's newest tap has bit 4 of its flags: an opening let go", flagged != null && flagged.length >= 36 && (flagged[26] & 0x10) != 0);
        say("CLEAR_SPENT frees the place", Arrays.equals(data(send(0xB0, 0x31, 0, 0, null, 1), 0x9000), new byte[] { 1 }));

        // a new tap, set up as a phone sets a card up
        link.retap();
        say("selected again in a new tap", sw(select()) == 0x9000);
        say("SET_PIN on a card that is set up and has been used, without the PIN given: 6982 (a stranger's PIN)", sw(send(0xB0, 0x41, 0, 0, PIN, -1)) == 0x6982);
        say("VERIFY_PIN, the holder's", sw(send(0xB0, 0x40, 0, 0, PIN, -1)) == 0x9000);
        say("SET_PIN under the PIN, the history not held for: 6982", sw(send(0xB0, 0x41, 0, 0, PIN, -1)) == 0x6982);
        int holdingAgain = 0;
        byte[] logAgain = null;
        for (int asked = 1; asked <= 10; asked++) {
            byte[] r = send(0xB0, 0x18, 0, 0, null, 0);
            if (sw(r) == 0x6A98 && r.length == 2) { holdingAgain++; continue; }
            logAgain = r;
            break;
        }
        say("the history held for, as a guest", holdingAgain == 9 && logAgain != null && sw(logAgain) == 0x9000);
        say("SET_PIN under the PIN and the hold, on a card with no owner", sw(send(0xB0, 0x41, 0, 0, PIN, -1)) == 0x9000);
        say("SET_OWNER with the PIN set and not given is 6982", sw(send(0xB0, 0x43, 0, 0, uncompressed(ownerKey()), -1)) == 0x6982);
        say("VERIFY_PIN", sw(send(0xB0, 0x40, 0, 0, PIN, -1)) == 0x9000);
        say("SET_CARD", sw(send(0xB0, 0x32, 0, 0, record(), -1)) == 0x9000);
        KeyPair owner = ownerKey();
        byte[] ownerPub = uncompressed(owner);
        long began = System.nanoTime();
        int notYet = 0;
        byte[] claimed = null;
        for (int asked = 1; asked <= 6; asked++) {
            byte[] r = send(0xB0, 0x43, 0, 0, ownerPub, -1);
            if (sw(r) == 0x6A98 && r.length == 2) { notYet++; continue; }
            claimed = r;
            break;
        }
        long claimMs = (System.nanoTime() - began) / 1000000;
        say("five 'keep holding' (6A98), and the claim at the sixth asking", notYet == 5 && claimed != null && sw(claimed) == 0x9000 && claimed.length == 2);
        System.out.println("      THE CLAIM took " + claimMs + " ms from the first asking to the owner set (" + (notYet > 0 ? claimMs / (notYet + 1) : 0) + " ms an asking)");
        say("the card has an owner", data(send(0xB0, 0x01, 0, 0, null, 0), 0x9000)[16] == 1);
        say("no payment in the tap that claimed it: SPEND_ALL_BEGIN is 6985", sw(send(0xB0, 0x22, 0, 0, new byte[] { 0 }, 4)) == 0x6985);

        // a new tap: a piece, and a payment of it that makes change for the card
        retapWithPin();
        byte[] key = data(send(0xB0, 0x10, 0, 0, null, 0), 0x9000);
        say("a piece of 100 goes on under the PIN", Arrays.equals(data(send(0xB0, 0x30, 0, 0, piece(100, 2), 0), 0x9000), new byte[] { 0 }));
        say("SPEND_ALL_BEGIN of it", sw(send(0xB0, 0x22, 0, 0, new byte[] { 0 }, 4)) == 0x9000);
        BigInteger k = new BigInteger(1, sha256("a mint made up for one run".getBytes(StandardCharsets.US_ASCII))).mod(N);
        byte[] mintKey = compressed(mul(k, G));
        long[] changeMs = new long[2];
        byte[][] blinded = new byte[2][];
        long[] amounts = { 30, 20 };
        for (int i = 0; i < 2; i++) {
            began = System.nanoTime();
            blinded[i] = data(send(0xB0, 0x26, 0, 0, cat(u32(amounts[i]), mintKey), 33), 0x9000);
            changeMs[i] = (System.nanoTime() - began) / 1000000;
        }
        say("two change outputs for the card, 33 bytes each", blinded[0] != null && blinded[0].length == 33 && blinded[1] != null && blinded[1].length == 33);
        System.out.println("      SPEND_ALL_CHANGE took " + changeMs[0] + " ms and " + changeMs[1] + " ms");
        byte[] signed = sign();
        say("the payment is signed", signed != null && signed.length == 64);
        byte[] page = data(send(0xB0, 0x19, 0, 0, null, 0), 0x9000);
        say("GET_CHANGE: two records of 83 bytes", page != null && page.length == 167 && page[0] == 2);
        byte[][] rec = { Arrays.copyOfRange(page, 1, 84), Arrays.copyOfRange(page, 84, 167) };
        for (int i = 0; i < 2; i++) {
            say("record " + i + ": its number, amount, keyset and the blinded message the card answered",
                rec[i][0] == i && be32(rec[i], 1) == amounts[i] && Arrays.equals(Arrays.copyOfRange(rec[i], 5, 13), KEYSET)
                && Arrays.equals(Arrays.copyOfRange(rec[i], 17, 50), blinded[i]) && Arrays.equals(Arrays.copyOfRange(rec[i], 50, 83), mintKey));
        }

        // the next tap, no PIN: the change and nothing else
        link.retap();
        say("selected in the tap after the payment", sw(select()) == 0x9000);
        say("GET_INFO's byte 29 says change is owed, to anyone", data(send(0xB0, 0x01, 0, 0, null, 0), 0x9000)[29] == 1);
        say("LOAD_PROOF with no PIN is 6982", sw(send(0xB0, 0x30, 0, 0, piece(8, 3), 0)) == 0x6982);
        say("CLEAR_SPENT with no PIN is 6982", sw(send(0xB0, 0x31, 0, 0, null, 1)) == 0x6982);
        BigInteger forger = new BigInteger(1, sha256("a key the card was not told".getBytes(StandardCharsets.US_ASCII))).mod(N);
        say("LOAD_CHANGE under another key, with that key's own good proof, is 6A80", sw(send(0xB0, 0x53, 0, 0, mintAnswer(forger, rec[0], BigInteger.valueOf(5)), 1)) == 0x6A80);
        byte[] bad = mintAnswer(k, rec[0], BigInteger.valueOf(7));
        bad[193] ^= 1;
        began = System.nanoTime();
        int refused = sw(send(0xB0, 0x53, 0, 0, bad, 1));
        long refusedMs = (System.nanoTime() - began) / 1000000;
        say("LOAD_CHANGE with a proof that does not hold is 6A80", refused == 0x6A80);
        long[] loadMs = new long[2];
        for (int i = 0; i < 2; i++) {
            byte[] answer = mintAnswer(k, rec[i], BigInteger.valueOf(11 + i));
            began = System.nanoTime();
            byte[] r = send(0xB0, 0x53, i, 0, answer, 83);
            loadMs[i] = (System.nanoTime() - began) / 1000000;
            byte[] answered = data(r, 0x9000);
            say("LOAD_CHANGE of opening " + i + " with no PIN answers the place and the piece (83 bytes)", answered != null && answered.length == 83);
            if (answered == null || answered.length != 83) continue;
            byte[] place = { answered[0] };
            byte[] slot = data(send(0xB0, 0x13, place[0], 0, null, 0), 0x9000);
            byte[] nonce = Arrays.copyOfRange(slot, 13, 45);
            BigInteger[] y = hashToCurve(secret(nonce, key));
            say("its C is the mint's signature on the opening's secret, the card's factor taken off",
                Arrays.equals(Arrays.copyOfRange(slot, 45, 78), compressed(mul(k, y))) && be32(slot, 9) == amounts[i]);
            byte[] dleq = data(send(0xB0, 0x1A, place[0], 0xFF, null, 0), 0x9000);
            say("and it keeps the mint's proof with it (GET_DLEQ 01 and 96 bytes)", dleq != null && dleq.length == 97 && dleq[0] == 1);
        }
        System.out.println("      LOAD_CHANGE took " + loadMs[0] + " ms and " + loadMs[1] + " ms (refused for its proof: " + refusedMs + " ms)");
        byte[] left = data(send(0xB0, 0x19, 0, 0, null, 0), 0x9000);
        say("no opening is left", left != null && left.length == 1 && left[0] == 0);

        // an opening a till bound to its own key (1.22): mended under the PIN with the mint's real answer, and another given up by the owner
        retapWithPin();
        say("SPEND_ALL_BEGIN of the 30", sw(send(0xB0, 0x22, 0, 0, new byte[] { 1 }, 4)) == 0x9000);
        byte[] poisoned = data(send(0xB0, 0x26, 0, 0, cat(u32(10), compressed(mul(till, G))), 33), 0x9000);
        say("change of 10 under the till's own key", poisoned != null && poisoned.length == 33);
        say("change of 5 under the mint's", sw(send(0xB0, 0x26, 0, 0, cat(u32(5), mintKey), 33)) == 0x9000);
        say("the payment is signed", sign() != null);
        byte[] owed = data(send(0xB0, 0x19, 0, 0, null, 0), 0x9000);
        say("GET_CHANGE: two openings, the first bound to the till's key", owed != null && owed.length == 167 && owed[0] == 2
            && Arrays.equals(Arrays.copyOfRange(owed, 51, 84), compressed(mul(till, G))));
        byte[][] stuck = { Arrays.copyOfRange(owed, 1, 84), Arrays.copyOfRange(owed, 84, 167) };
        byte[] real = mintAnswer(k, stuck[0], BigInteger.valueOf(13));
        say("LOAD_CHANGE of the mint's real answer in the tap that paid is 6985", sw(send(0xB0, 0x53, stuck[0][0], 0, real, 83)) == 0x6985);
        link.retap();
        say("selected in a stranger's tap", sw(select()) == 0x9000);
        say("LOAD_CHANGE of the mint's real answer with no PIN is 6A80: not the bound key", sw(send(0xB0, 0x53, stuck[0][0], 0, real, 83)) == 0x6A80);
        retapWithPin();
        say("LOAD_CHANGE of the mint's real answer under the PIN alone is 6982: the owner's grant is wanted as well", sw(send(0xB0, 0x53, stuck[0][0], 0, real, 83)) == 0x6982);
        say("the owner's grant for this tap", sw(send(0xB0, 0x45, 0, 0, ownerData(owner, "FoxyCard/load", new byte[0]), -1)) == 0x9000);
        byte[] spoiled = real.clone();
        spoiled[193] ^= 1;
        say("LOAD_CHANGE under the PIN and the grant, the real key and a proof that does not hold, is 6A80", sw(send(0xB0, 0x53, stuck[0][0], 0, spoiled, 83)) == 0x6A80);
        began = System.nanoTime();
        byte[] mended = data(send(0xB0, 0x53, stuck[0][0], 0, real, 83), 0x9000);
        long mendMs = (System.nanoTime() - began) / 1000000;
        say("LOAD_CHANGE under the PIN and the grant, the mint's answer under its real key: the opening mended", mended != null && mended.length == 83);
        System.out.println("      LOAD_CHANGE under another key took " + mendMs + " ms");
        int mendedAt = mended == null ? 3 : mended[0];
        byte[] mendedSlot = data(send(0xB0, 0x13, mendedAt, 0, null, 0), 0x9000);
        say("its C is the mint's signature on the opening's secret",
            mendedSlot != null && Arrays.equals(Arrays.copyOfRange(mendedSlot, 45, 78), compressed(mul(k, hashToCurve(secret(Arrays.copyOfRange(mendedSlot, 13, 45), key))))));
        say("RESET_CARD with the other opening owed is 6A8D", sw(send(0xB0, 0x51, 0, 0xAD, ownerData(owner, "FoxyCard/reset", new byte[0]), -1)) == 0x6A8D);
        byte[] num = { stuck[1][0] };
        say("the let-go by the PIN alone, on a card with an owner, is 6A91", sw(send(0xB0, 0x51, 1, num[0], num, -1)) == 0x6A91);
        say("the let-go by another key's proof is 6A91", sw(send(0xB0, 0x51, 1, num[0], ownerData(ownerKey(), "FoxyCard/reset", num), -1)) == 0x6A91);
        say("the let-go of another number than the proof's is 6B00", sw(send(0xB0, 0x51, 1, stuck[0][0], ownerData(owner, "FoxyCard/reset", num), -1)) == 0x6B00);
        say("the let-go by the owner's proof over the number", sw(send(0xB0, 0x51, 1, num[0], ownerData(owner, "FoxyCard/reset", num), -1)) == 0x9000);
        byte[] gone = data(send(0xB0, 0x19, 0, 0, null, 0), 0x9000);
        say("no opening is left", gone != null && gone.length == 1 && gone[0] == 0);
        byte[] flags = data(send(0xB0, 0x18, 0, 0, null, 0), 0x9000);
        say("the log's newest tap has bit 4 of its flags", flags != null && flags.length >= 36 && (flags[26] & 0x10) != 0);

        // the change pays; the card is emptied and reset
        retapWithPin();
        say("the owner's grant for this tap", sw(send(0xB0, 0x45, 0, 0, ownerData(owner, "FoxyCard/load", new byte[0]), -1)) == 0x9000);
        say("the two change pieces left are paid", sw(send(0xB0, 0x22, 0, 0, new byte[] { 2, (byte) mendedAt }, 4)) == 0x9000 && sign() != null);
        say("CLEAR_SPENT frees the four places", Arrays.equals(data(send(0xB0, 0x31, 0, 0, null, 1), 0x9000), new byte[] { 4 }));
        say("RESET_CARD by the owner's proof", sw(send(0xB0, 0x51, 0, 0xAD, ownerData(owner, "FoxyCard/reset", new byte[0]), -1)) == 0x9000);
        byte[] blank = data(send(0xB0, 0x01, 0, 0, null, 0), 0x9000);
        say("the card is blank again: no owner, no record, nothing on it, no PIN",
            blank != null && blank.length == 30 && blank[16] == 0 && blank[11] == 0 && blank[3] == 0 && blank[4] == 0 && blank[7] == 0);

        if ((selected[1] & 0xFF) >= 0x17) units123();

        System.out.println(checks + " checks, " + failed + " failed.");
        return failed == 0 ? 0 : 1;
    }

    // ---- 1.23: dollars and sats on one card ------------------------------------------------------------------------------

    private static final byte[] SATS = hex("00aa000000000001"), USD = hex("00bb000000000001");

    /** A piece of `keyset` worth `amount`, its nonce its own (the seed in its first byte, 0x80 and up: none of the run before's). */
    private static byte[] piece(byte[] keyset, long amount, int seed) {
        byte[] p = piece(amount, 0);
        System.arraycopy(keyset, 0, p, 0, 8);
        for (int i = 0; i < 32; i++) p[12 + i] = (byte) (0x80 + seed + i);
        p[12] = (byte) seed;
        p[13] = (byte) 0xA5;
        return p;
    }

    private static byte[] labelled(int unit, byte[]... pieces) {
        byte[] out = { (byte) unit };
        for (byte[] p : pieces) out = cat(out, p);
        return out;
    }

    /** SIGN, again while the card says "not yet": the signature (or null) and, in `waited[0]`, how many times it said so. */
    private static byte[] sign(int[] waited) throws Exception {
        waited[0] = 0;
        for (int i = 0; i < 1100; i++) {
            byte[] r = send(0xB0, 0x24, 0, 0, null, 64);
            if (sw(r) != 0x9000) return null;
            if (r.length != 4) return Arrays.copyOf(r, r.length - 2);
            waited[0]++;
        }
        return null;
    }

    /** The table as GET_CARD P1 = 2 says it: the count and the entries. */
    private static byte[] table() throws Exception { return data(send(0xB0, 0x16, 2, 0, null, 0), 0x9000); }

    private static int units123Checks;

    private static void units123() throws Exception {
        int before = checks;
        System.out.println("software 1.23: dollars and sats on one card.");
        link.retap();
        byte[] selected = select();
        say("SELECT answers 01 17", sw(selected) == 0x9000 && selected.length == 4 && selected[1] == 0x17);
        byte[] three = data(send(0xB0, 0x01, 3, 0, null, 0), 0x9000);
        say("GET_INFO P1 = 3: 51 bytes, format 4", three != null && three.length == 51 && three[8] == 4);
        say("SET_PIN", sw(send(0xB0, 0x41, 0, 0, PIN, -1)) == 0x9000);
        say("VERIFY_PIN", sw(send(0xB0, 0x40, 0, 0, PIN, -1)) == 0x9000);
        byte[] unitTwo = record();
        unitTwo[0] = 2;
        say("SET_CARD of a unit the card does not know is 6A80", sw(send(0xB0, 0x32, 0, 0, unitTwo, -1)) == 0x6A80);
        long began = System.nanoTime();
        say("SET_CARD in sats", sw(send(0xB0, 0x32, 0, 0, record(), -1)) == 0x9000);
        System.out.println("      SET_CARD took " + (System.nanoTime() - began) / 1000000 + " ms (the reclaim and the last unit before its transaction)");
        three = data(send(0xB0, 0x01, 3, 0, null, 0), 0x9000);
        say("the book is there: P1 = 3 byte 43 is 01", three != null && three[43] == 1);
        KeyPair owner = ownerKey();
        byte[] ownerPub = uncompressed(owner);
        int notYet = 0;
        for (int asked = 1; asked <= 6; asked++) {
            byte[] r = send(0xB0, 0x43, 0, 0, ownerPub, -1);
            if (sw(r) == 0x6A98) { notYet++; continue; }
            say("the owner's claim at the sixth asking", sw(r) == 0x9000 && notYet == 5);
            break;
        }
        byte[] ceiling = cat(u32(0), u32(100), u32(200), u32(1000));
        began = System.nanoTime();
        say("SET_LIMIT of sixteen bytes: $1.00 on one payment, $2.00 with no PIN, 1,000 sats to the dollar", sw(send(0xB0, 0x34, 0, 0, ownerData(owner, "FoxyCard/set-limit", ceiling), -1)) == 0x9000);
        System.out.println("      SET_LIMIT took " + (System.nanoTime() - began) / 1000000 + " ms (the figures and the rate in one transaction)");
        say("1.22's twelve bytes on a sats record are 6700", sw(send(0xB0, 0x34, 0, 0, ownerData(owner, "FoxyCard/set-limit", cat(u32(0), u32(100), u32(200))), -1)) == 0x6700);
        say("the owner's grant", sw(send(0xB0, 0x45, 0, 0, ownerData(owner, "FoxyCard/load", new byte[0]), -1)) == 0x9000);
        three = data(send(0xB0, 0x01, 3, 0, null, 0), 0x9000);
        say("GET_INFO P1 = 3 with the grant reads the rate back", three != null && be32(three, 44) == 1000 && be32(three, 30) == 100 && be32(three, 34) == 200);
        byte[] card = data(send(0xB0, 0x16, 0, 0, null, 0), 0x9000);
        say("GET_CARD answers the rate's bytes as zeros", card != null && Arrays.equals(Arrays.copyOfRange(card, 76, 105), new byte[29]));

        // a new tap and the PIN: sats plain, dollars labelled, the table
        retapWithPin();
        began = System.nanoTime();
        byte[] put = data(send(0xB0, 0x30, 0, 0, cat(piece(SATS, 2000, 1), piece(SATS, 1500, 2), piece(SATS, 10, 3)), 0), 0x9000);
        System.out.println("      LOAD_PROOF of three new pieces, a new keyset among them, took " + (System.nanoTime() - began) / 1000000 + " ms");
        say("three pieces of sats loaded plain: places 0, 1 and 2", Arrays.equals(put, new byte[] { 0, 1, 2 }));
        say("the table: the sats keyset, entered 40", Arrays.equals(table(), cat(new byte[] { 1 }, SATS, new byte[] { 0x40 })));
        say("a load labelled dollars under the PIN alone is 6A9D", sw(send(0xB0, 0x30, 0, 1, labelled(1, piece(USD, 300, 4)), 0)) == 0x6A9D);
        say("the owner's grant", sw(send(0xB0, 0x45, 0, 0, ownerData(owner, "FoxyCard/load", new byte[0]), -1)) == 0x9000);
        put = data(send(0xB0, 0x30, 0, 1, labelled(1, piece(USD, 300, 4), piece(USD, 50, 5)), 0), 0x9000);
        say("with the grant: two dollar pieces, $3.00 and $0.50, places 3 and 4", Arrays.equals(put, new byte[] { 3, 4 }));
        say("the sats keyset labelled dollars is 6A9B", sw(send(0xB0, 0x30, 0, 1, labelled(1, piece(SATS, 5, 6)), 0)) == 0x6A9B);
        say("the table lists both: 40 and C1", Arrays.equals(table(), cat(new byte[] { 2 }, SATS, new byte[] { 0x40 }, USD, new byte[] { (byte) 0xC1 })));
        say("format 5", data(send(0xB0, 0x01, 0, 0, null, 0), 0x9000)[8] == 5);
        began = System.nanoTime();
        byte[] each = data(send(0xB0, 0x11, 1, 0, null, 0), 0x9000);
        System.out.println("      GET_BALANCE P1 = 1 took " + (System.nanoTime() - began) / 1000000 + " ms");
        say("GET_BALANCE P1 = 1: 3,510 sats in three, $3.50 in two", Arrays.equals(each, cat(new byte[] { 2, 0, 3 }, u32(3510), new byte[] { 1, 2 }, u32(350))));
        say("GET_BALANCE: the record's unit alone", Arrays.equals(data(send(0xB0, 0x11, 0, 0, null, 4), 0x9000), u32(3510)));
        say("a payment of a sats piece and a dollar piece is 6A9A", sw(send(0xB0, 0x22, 0, 0, new byte[] { 0, 3 }, 4)) == 0x6A9A);

        // a new tap with no PIN: the allowance, in cents, of sats
        BigInteger k = new BigInteger(1, sha256("a mint made up for one run".getBytes(StandardCharsets.US_ASCII))).mod(N);
        byte[] mintKey = compressed(mul(k, G));
        link.retap();
        say("selected, no PIN", sw(select()) == 0x9000);
        say("2,000 sats begun", sw(send(0xB0, 0x22, 0, 0, new byte[] { 0 }, 4)) == 0x9000);
        say("1,500 of it the card's own change", sw(send(0xB0, 0x26, 0, 0, cat(u32(1500), mintKey), 33)) == 0x9000);
        int[] waited = new int[1];
        began = System.nanoTime();
        byte[] signed = sign(waited);
        long signMs = (System.nanoTime() - began) / 1000000;
        say("signed with no PIN: 200 cents of pieces, the whole allowance; 50 cents leave the card, no wait", signed != null && signed.length == 64 && waited[0] == 0);
        System.out.println("      SPEND_ALL_SIGN (the first and the last in one: two conversions to cents, the payment's and the receipt's transactions) took " + signMs + " ms");
        say("the allowance's count: 200 cents", be32(data(send(0xB0, 0x01, 1, 0, null, 0), 0x9000), 38) == 200);
        byte[] owed = data(send(0xB0, 0x19, 0, 0, null, 0), 0x9000);
        say("the change back by LOAD_CHANGE, with no PIN", owed != null && owed[0] == 1 && data(send(0xB0, 0x53, owed[1], 0, mintAnswer(k, Arrays.copyOfRange(owed, 1, 84), BigInteger.valueOf(31)), 83), 0x9000) != null);
        link.retap();
        say("selected, no PIN", sw(select()) == 0x9000);
        say("10 sats more with no PIN: past the allowance by a cent, 6A94",
            sw(send(0xB0, 0x22, 0, 0, new byte[] { 2 }, 4)) == 0x9000 && sw(send(0xB0, 0x24, 0, 0, null, 64)) == 0x6A94);

        // under the PIN: the wait in cents
        retapWithPin();
        say("1,500 sats begun", sw(send(0xB0, 0x22, 0, 0, new byte[] { 1 }, 4)) == 0x9000);
        began = System.nanoTime();
        signed = sign(waited);
        long waitMs = (System.nanoTime() - began) / 1000000;
        say("150 cents, a limit and a half: ten waits counted to the asking, then the signature", signed != null && waited[0] == 10);
        System.out.println("      the ten waits and the signature took " + waitMs + " ms");

        // a dollar payment with change: the entry stays while the opening is owed
        retapWithPin();
        say("$3.50 begun", sw(send(0xB0, 0x22, 0, 0, new byte[] { 3, 4 }, 4)) == 0x9000);
        say("$1.00 of it the card's own change", sw(send(0xB0, 0x26, 0, 0, cat(u32(100), mintKey), 33)) == 0x9000);
        signed = sign(waited);
        say("signed: $2.50 leave the card, three limits' worth: fourteen waits (one change output buys none back)", signed != null && waited[0] == 14);
        say("CLEAR_SPENT", sw(send(0xB0, 0x31, 0, 0, null, 1)) == 0x9000);
        byte[] t = table();
        say("the dollar entry stays: the opening owed names its keyset", t != null && t[0] == 2);
        owed = data(send(0xB0, 0x19, 0, 0, null, 0), 0x9000);
        byte[] back = owed == null || owed[0] != 1 ? null : data(send(0xB0, 0x53, owed[1], 0, mintAnswer(k, Arrays.copyOfRange(owed, 1, 84), BigInteger.valueOf(37)), 83), 0x9000);
        say("the dollar change back by LOAD_CHANGE: $1.00", back != null && be32(back, 2 + 8) == 100 && Arrays.equals(Arrays.copyOfRange(back, 2, 10), USD));
        int dollarsAt = back == null ? 0 : back[0];

        // no rate: a sats payment is refused, a dollar one signs
        byte[] noRate = cat(u32(0), u32(100), u32(200), u32(0));
        say("SET_LIMIT with no rate", sw(send(0xB0, 0x34, 0, 0, ownerData(owner, "FoxyCard/set-limit", noRate), -1)) == 0x9000);
        int satsAt = -1;
        for (int place = 0; place < 128 && satsAt < 0; place++) {
            byte[] s = data(send(0xB0, 0x13, place, 0, null, 0), 0x9000);
            if (s != null && s[0] == 1 && Arrays.equals(Arrays.copyOfRange(s, 1, 9), SATS)) satsAt = place;
        }
        say("a sats payment with a limit and no rate: 6A99", sw(send(0xB0, 0x22, 0, 0, new byte[] { (byte) satsAt }, 4)) == 0x9000 && sw(send(0xB0, 0x24, 0, 0, null, 64)) == 0x6A99);
        say("and given up: 6985", sw(send(0xB0, 0x24, 0, 0, null, 64)) == 0x6985);
        say("a dollar payment asks for no rate", sw(send(0xB0, 0x22, 0, 0, new byte[] { (byte) dollarsAt }, 4)) == 0x9000 && sign(waited) != null);
        say("CLEAR_SPENT: the dollars paid off, the entry let go", sw(send(0xB0, 0x31, 0, 0, null, 1)) == 0x9000);
        say("the table holds the sats keyset alone, and the format is 4",
            Arrays.equals(table(), cat(new byte[] { 1 }, SATS, new byte[] { 0x40 })) && data(send(0xB0, 0x01, 0, 0, null, 0), 0x9000)[8] == 4);

        // a drawer of 120 pieces: the reclaim and GET_BALANCE over it
        say("no ceiling, the rate kept", sw(send(0xB0, 0x34, 0, 0, ownerData(owner, "FoxyCard/set-limit", cat(u32(0), u32(0), u32(0), u32(1000))), -1)) == 0x9000);
        retapWithPin();
        boolean loaded = true;
        for (int i = 0; i < 40 && loaded; i++) {
            loaded = sw(send(0xB0, 0x30, 0, 0, cat(piece(SATS, 1, 10 + 3 * i), piece(SATS, 1, 11 + 3 * i), piece(SATS, 1, 12 + 3 * i)), 0)) == 0x9000;
        }
        say("120 pieces of a sat loaded", loaded);
        began = System.nanoTime();
        each = data(send(0xB0, 0x11, 1, 0, null, 0), 0x9000);
        long balanceMs = (System.nanoTime() - began) / 1000000;
        say("GET_BALANCE P1 = 1 over them: 122 pieces of sats, the two before them among them", each != null && each[0] == 1 && each[1] == 0 && (each[2] & 0xFF) == 122);
        began = System.nanoTime();
        say("CLEAR_SPENT over a full drawer: the reclaim's pass", sw(send(0xB0, 0x31, 0, 0, null, 1)) == 0x9000);
        long reclaimMs = (System.nanoTime() - began) / 1000000;
        System.out.println("      over 122 unspent places: GET_BALANCE P1 = 1 took " + balanceMs + " ms, CLEAR_SPENT and its reclaim " + reclaimMs + " ms");
        byte[] everyone = new byte[122];
        int n = 0;
        for (int place = 0; place < 128; place++) {
            byte[] s = data(send(0xB0, 0x13, place, 0, null, 0), 0x9000);
            if (s != null && s[0] == 1) everyone[n++] = (byte) place;
        }
        say("all 122 paid in one payment", n == 122 && sw(send(0xB0, 0x22, 0, 0, everyone, 4)) == 0x9000 && sign(waited) != null);
        say("CLEAR_SPENT frees them, and the table", sw(send(0xB0, 0x31, 0, 0, null, 1)) == 0x9000 && Arrays.equals(table(), new byte[] { 0 }));

        // SET_CARD on the empty card, RESET_CARD, blank
        byte[] rec = record();
        began = System.nanoTime();
        say("SET_CARD again by the owner's proof, on the empty card", sw(send(0xB0, 0x32, 0, 0, ownerData(owner, "FoxyCard/set-card", rec), -1)) == 0x9000);
        System.out.println("      SET_CARD with a book took " + (System.nanoTime() - began) / 1000000 + " ms");
        three = data(send(0xB0, 0x01, 3, 0, null, 0), 0x9000);
        say("the table empty, the last payment's unit gone", three != null && three[48] == 0 && three[50] == 0);
        say("RESET_CARD by the owner's proof", sw(send(0xB0, 0x51, 0, 0xAD, ownerData(owner, "FoxyCard/reset", new byte[0]), -1)) == 0x9000);
        byte[] blank = data(send(0xB0, 0x01, 0, 0, null, 0), 0x9000);
        say("the card is blank again", blank != null && blank.length == 30 && blank[16] == 0 && blank[11] == 0 && blank[3] == 0 && blank[4] == 0 && blank[7] == 0);
        units123Checks = checks - before;
        System.out.println("      " + units123Checks + " checks in the run of 1.23");
    }

    // ---- the mint of this run, and the card's secret --------------------------------------------------------------------

    /** LOAD_CHANGE's data for a GET_CHANGE record: C_ = k*B_ (65), K = k*G (65), and NUT-12's proof made with the nonce p (e, s). */
    private static byte[] mintAnswer(BigInteger k, byte[] record, BigInteger p) throws Exception {
        BigInteger[] b = decompress(Arrays.copyOfRange(record, 17, 50));
        BigInteger[] c = mul(k, b), key = mul(k, G);
        StringBuilder text = new StringBuilder();
        for (BigInteger[] pt : new BigInteger[][] { mul(p, G), mul(p, b), key, c }) text.append(toHex(point65(pt)).toLowerCase());
        byte[] e = sha256(text.toString().getBytes(StandardCharsets.US_ASCII));
        BigInteger s = p.add(new BigInteger(1, e).multiply(k)).mod(N);
        return cat(point65(c), point65(key), e, bytes32(s));
    }

    /** A piece's secret as the card builds it for a piece with no date: ["P2PK",{"nonce":"..","data":"<card key>","tags":[["sigflag","SIG_ALL"]]}]. */
    private static byte[] secret(byte[] nonce, byte[] cardKey) {
        return ("[\"P2PK\",{\"nonce\":\"" + toHex(nonce).toLowerCase() + "\",\"data\":\"" + toHex(cardKey).toLowerCase()
            + "\",\"tags\":[[\"sigflag\",\"SIG_ALL\"]]}]").getBytes(StandardCharsets.UTF_8);
    }

    /** NUT-00: the point a secret hashes to, with the even y. */
    private static BigInteger[] hashToCurve(byte[] secret) throws Exception {
        byte[] msg = sha256(cat("Secp256k1_HashToCurve_Cashu_".getBytes(StandardCharsets.US_ASCII), secret));
        for (long counter = 0; ; counter++) {
            byte[] x = sha256(cat(msg, new byte[] { (byte) counter, (byte) (counter >> 8), (byte) (counter >> 16), (byte) (counter >> 24) }));
            BigInteger[] pt = lift(new BigInteger(1, x), false);
            if (pt != null) return pt;
        }
    }

    private static BigInteger[] lift(BigInteger x, boolean odd) {
        if (x.compareTo(P) >= 0) return null;
        BigInteger v = x.modPow(BigInteger.valueOf(3), P).add(BigInteger.valueOf(7)).mod(P);
        BigInteger y = v.modPow(P.add(BigInteger.ONE).shiftRight(2), P);
        if (!y.multiply(y).mod(P).equals(v)) return null;
        if (y.testBit(0) != odd) y = P.subtract(y);
        return new BigInteger[] { x, y };
    }

    private static BigInteger[] decompress(byte[] b33) { return lift(new BigInteger(1, Arrays.copyOfRange(b33, 1, 33)), b33[0] == 3); }

    private static BigInteger[] add(BigInteger[] a, BigInteger[] b) {
        if (a == null) return b;
        if (b == null) return a;
        BigInteger l;
        if (a[0].equals(b[0])) {
            if (!a[1].equals(b[1])) return null;
            l = BigInteger.valueOf(3).multiply(a[0].pow(2)).multiply(a[1].shiftLeft(1).modInverse(P)).mod(P);
        } else {
            l = b[1].subtract(a[1]).multiply(b[0].subtract(a[0]).modInverse(P)).mod(P);
        }
        BigInteger x = l.pow(2).subtract(a[0]).subtract(b[0]).mod(P);
        return new BigInteger[] { x, l.multiply(a[0].subtract(x)).subtract(a[1]).mod(P) };
    }

    private static BigInteger[] mul(BigInteger k, BigInteger[] pt) {
        BigInteger[] r = null, q = pt;
        for (k = k.mod(N); k.signum() > 0; k = k.shiftRight(1)) {
            if (k.testBit(0)) r = add(r, q);
            q = add(q, q);
        }
        return r;
    }

    private static byte[] point65(BigInteger[] pt) { return cat(new byte[] { 4 }, bytes32(pt[0]), bytes32(pt[1])); }

    private static byte[] compressed(BigInteger[] pt) { return cat(new byte[] { (byte) (pt[1].testBit(0) ? 3 : 2) }, bytes32(pt[0])); }

    private static byte[] bytes32(BigInteger n) {
        byte[] b = n.toByteArray(), out = new byte[32];
        int len = Math.min(b.length, 32);
        System.arraycopy(b, b.length - len, out, 32 - len, len);
        return out;
    }

    private static byte[] sha256(byte[] in) throws Exception { return MessageDigest.getInstance("SHA-256").digest(in); }

    // ---- what a phone sends -----------------------------------------------------------------------------------------------

    private static byte[] select() throws Exception { return send(0x00, 0xA4, 0x04, 0x00, hex(AID), 0); }

    private static void retapWithPin() throws Exception {
        link.retap();
        say("selected again in a new tap", sw(select()) == 0x9000);
        say("VERIFY_PIN in it", sw(send(0xB0, 0x40, 0, 0, PIN, -1)) == 0x9000);
    }

    /** SPEND_ALL_SIGN, again for as long as the card says "not yet" (9000 and two bytes). The signature, or null. */
    private static byte[] sign() throws Exception {
        for (int i = 0; i < 1100; i++) {
            byte[] r = send(0xB0, 0x24, 0, 0, null, 64);
            if (sw(r) != 0x9000) return null;
            if (r.length != 4) return Arrays.copyOf(r, r.length - 2);
        }
        return null;
    }

    /** The owner's proof for a command: its length, a signature (ECDSA, P-256, SHA-256, DER) over label, the nonce just given and the value, then the value. */
    private static byte[] ownerData(KeyPair owner, String label, byte[] value) throws Exception {
        byte[] nonce = data(send(0xB0, 0x44, 0, 0, null, 16), 0x9000);
        Signature s = Signature.getInstance("SHA256withECDSA");
        s.initSign(owner.getPrivate());
        s.update(label.getBytes(StandardCharsets.US_ASCII));
        s.update(nonce);
        s.update(value);
        byte[] der = s.sign();
        return cat(new byte[] { (byte) der.length }, der, value);
    }

    private static KeyPair ownerKey() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
        g.initialize(new ECGenParameterSpec("secp256r1"));
        return g.generateKeyPair();
    }

    private static byte[] uncompressed(KeyPair pair) {
        java.security.spec.ECPoint w = ((ECPublicKey) pair.getPublic()).getW();
        return cat(new byte[] { 4 }, bytes32(w.getAffineX()), bytes32(w.getAffineY()));
    }

    /** SET_CARD's data: unit, no refund key, the 65 bytes the card does not read, the mint's length and the mint. */
    private static byte[] record() {
        byte[] mint = "https://mint.example.com/Bitcoin".getBytes(StandardCharsets.US_ASCII);
        return cat(new byte[] { 0 }, new byte[33], new byte[65], new byte[] { (byte) mint.length }, mint);
    }

    /** A piece as LOAD_PROOF takes it: keyset (8), amount (4), nonce (32), C (33: a point's first byte and the seed after it), date (4, none). */
    private static byte[] piece(long amount, int seed) {
        byte[] p = new byte[81];
        System.arraycopy(KEYSET, 0, p, 0, 8);
        System.arraycopy(u32(amount), 0, p, 8, 4);
        for (int i = 0; i < 32; i++) p[12 + i] = (byte) (0x60 + seed + i);
        p[44] = 0x02;
        for (int i = 0; i < 32; i++) p[45 + i] = (byte) (seed + 7);
        return p;
    }

    private static byte[] u32(long v) { return new byte[] { (byte) (v >> 24), (byte) (v >> 16), (byte) (v >> 8), (byte) v }; }

    private static long be32(byte[] b, int at) {
        return ((long) (b[at] & 0xFF) << 24) | ((b[at + 1] & 0xFF) << 16) | ((b[at + 2] & 0xFF) << 8) | (b[at + 3] & 0xFF);
    }

    // ---- the talk ---------------------------------------------------------------------------------------------------------

    /** One command: CLA, INS, P1, P2, the data (null for none) and Le (-1 for none, 0 for 256). The answer, status word last. */
    private static byte[] send(int cla, int ins, int p1, int p2, byte[] data, int le) throws Exception {
        int lc = data == null ? 0 : data.length;
        byte[] c = new byte[4 + (lc > 0 ? 1 + lc : 0) + (le >= 0 ? 1 : 0)];
        c[0] = (byte) cla; c[1] = (byte) ins; c[2] = (byte) p1; c[3] = (byte) p2;
        int at = 4;
        if (lc > 0) { c[at++] = (byte) lc; System.arraycopy(data, 0, c, at, lc); at += lc; }
        if (le >= 0) c[at] = (byte) le;
        return link.send(c);
    }

    private static int sw(byte[] answer) {
        return answer.length < 2 ? -1 : ((answer[answer.length - 2] & 0xFF) << 8) | (answer[answer.length - 1] & 0xFF);
    }

    /** What an answer carries, if it was that status word; otherwise null. */
    private static byte[] data(byte[] answer, int wanted) {
        return sw(answer) == wanted ? Arrays.copyOf(answer, answer.length - 2) : null;
    }

    private static void say(String what, boolean held) {
        checks++;
        if (!held) failed++;
        System.out.println((held ? "  ok    " : "  FAIL  ") + what);
    }

    private static Link reader() throws Exception {
        TerminalFactory factory = TerminalFactory.getInstance("PC/SC", null, new jnasmartcardio.Smartcardio());
        for (CardTerminal t : factory.terminals().list()) {
            if (!t.isCardPresent()) continue;
            final Card card = t.connect("*");
            return new Link() {
                Card now = card;
                CardChannel to = card.getBasicChannel();
                public byte[] send(byte[] command) throws Exception { return to.transmit(new CommandAPDU(command)).getBytes(); }
                public void retap() throws Exception { now.disconnect(true); now = t.connect("*"); to = now.getBasicChannel(); }
                public void close() throws Exception { now.disconnect(true); }
            };
        }
        throw new IllegalStateException("no card in any reader");
    }

    /** A card behind the lines of tools/chip/ChipBridge.java or the simulator's server (tap, apdu HEX, end). */
    private static Link bridge(String hostAndPort) throws Exception {
        int colon = hostAndPort.lastIndexOf(':');
        final Socket socket = new Socket(hostAndPort.substring(0, colon), Integer.parseInt(hostAndPort.substring(colon + 1)));
        final BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
        final OutputStream out = socket.getOutputStream();
        if (!"ok".equals(talk(in, out, "tap"))) throw new IllegalStateException("the card does not answer a tap");
        return new Link() {
            public byte[] send(byte[] command) throws Exception {
                String a = talk(in, out, "apdu " + toHex(command));
                if (a.equals("gone") || a.equals("none") || a.equals("what")) throw new IllegalStateException("the card went (" + a + ")");
                return hex(a);
            }
            public void retap() throws Exception {
                talk(in, out, "end");
                if (!"ok".equals(talk(in, out, "tap"))) throw new IllegalStateException("the card does not answer a new tap");
            }
            public void close() throws Exception { talk(in, out, "end"); socket.close(); }
        };
    }

    private static String talk(BufferedReader in, OutputStream out, String line) throws Exception {
        out.write((line + "\n").getBytes(StandardCharsets.US_ASCII));
        out.flush();
        String a = in.readLine();
        if (a == null) throw new IllegalStateException("the other end closed");
        return a.trim();
    }

    private static byte[] cat(byte[]... parts) {
        int n = 0;
        for (byte[] p : parts) n += p.length;
        byte[] out = new byte[n];
        int at = 0;
        for (byte[] p : parts) { System.arraycopy(p, 0, out, at, p.length); at += p.length; }
        return out;
    }

    private static byte[] hex(String s) {
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) out[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
        return out;
    }

    private static String toHex(byte[] b) {
        StringBuilder out = new StringBuilder();
        for (byte x : b) out.append(String.format("%02X", x & 0xFF));
        return out.toString();
    }
}
