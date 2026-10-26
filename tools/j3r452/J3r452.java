import javax.smartcardio.Card;
import javax.smartcardio.CardChannel;
import javax.smartcardio.CardTerminal;
import javax.smartcardio.CommandAPDU;
import javax.smartcardio.TerminalFactory;
import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A new chip in the reader, looked at before anything is put on it, and the two probes run on it.
 *
 *   J3r452 [options] readers      the readers and the ATR of each card present (no command is sent)
 *   J3r452 [options] identify     read only: the card manager's data (CPLC, IIN, CIN, card data, key information, free memory,
 *                                 platform ID) and a SELECT of every applet this work cares about; nothing needs a key
 *   J3r452 [options] guard        identify, then the answer as an exit code: 0 only for a chip that may be installed on
 *   J3r452 [options] caps         the primitives probe (probe-caps), once it is on the chip; checks each answer here
 *   J3r452 [options] bio          the fingerprint probe (tools/probe-bio), once it is on the chip
 *   J3r452 [options] reset        power the card in the reader off and on (for a card that went mute: no command is sent)
 *   J3r452 [options] serve PORT   the card in the reader on 127.0.0.1:PORT (the lines of tools/chip/ChipBridge.java), after the
 *                                 chip's serial is checked, so that tools/chip's checks talk to this card and no other
 *
 * Options:
 *   --dry-run            print every command that would be sent, and send none (no reader is opened)
 *   --reader TEXT        the reader whose name holds TEXT; without it, exactly one reader may hold a card
 *   --bridge HOST:PORT   a card behind a bridge or the simulator's server instead of a reader (for testing these tools)
 *   --serial XXXXXXXX    the chip's serial (CPLC IC serial number) as identify printed it: guard and serve refuse another chip
 *   --ictype XXXX        guard: accept this CPLC IC type as the new chip's when its platform ID cannot be read
 *   --installed          guard: the card's applet must now answer (software 1.23, blank) instead of being absent
 *   --own-reinstall      guard: Foxy's software may be on the chip already, because this is the new chip (by --serial) and the
 *                        caller is about to delete Foxy's own AIDs and install again (install.sh --clean-own); card two never passes
 *   --log FILE           serve: each command's instruction, lengths, status and time (never its data)
 *
 * guard's exit codes: 0 may go on; 10 card two (money); 11 card one or three; 12 the card's applet answers (before an install),
 * or does not answer as 1.23 on a blank card (with --installed); 13 not NXP, or a JCOP 4 P71 (J3R180) and not a JCOP 4.5;
 * 14 no --serial, or another serial; 15 no card, or more than one reader holds a card; 16 the CPLC cannot be read;
 * 17 the platform ID cannot be read and no --ictype was given, or another IC type.
 *
 * Needs GlobalPlatformPro's jar on the class path for its PC/SC binding (jnasmartcardio).
 */
public final class J3r452 {
    // Foxy's AIDs (docs/HARDWARE_DEPLOYMENT.md)
    static final String CARD_APPLET = "F0464F58594341524401";
    static final String OPENER = "F0464F5859434152444D";
    static final String PACKAGE = "F0464F585943415244";
    static final String NDEF_TAG = "D2760000850101";
    static final String UPSTREAM = "D276000085010201";
    static final String CAPS_PROBE = "F0464F58594341505301";
    static final String BIO_PROBE = "F0464F585942494F01";
    // the three J3R180 cards (CPLC IC serial numbers); card two holds real money
    static final String CARD_ONE = "09334120", CARD_TWO = "09334220", CARD_THREE = "09301320";
    static final String JCOP4_P71_ICTYPE = "D321";

    interface Link {
        byte[] send(byte[] command) throws Exception;
        void retap() throws Exception;
        String where();
        byte[] atr();
        void close() throws Exception;
    }

    static boolean dry;
    static String readerText, bridgeAt, serial, icType, logFile;
    static boolean installed, ownReinstall;
    static Link link;

    public static void main(String[] a) throws Exception {
        List<String> rest = new ArrayList<>();
        for (int i = 0; i < a.length; i++) {
            switch (a[i]) {
                case "--dry-run": dry = true; break;
                case "--reader": readerText = a[++i]; break;
                case "--bridge": bridgeAt = a[++i]; break;
                case "--serial": serial = a[++i].toUpperCase(); break;
                case "--ictype": icType = a[++i].toUpperCase(); break;
                case "--installed": installed = true; break;
                case "--own-reinstall": ownReinstall = true; break;
                case "--log": logFile = a[++i]; break;
                default: rest.add(a[i]);
            }
        }
        if (rest.isEmpty()) { System.out.println("say what: readers, identify, guard, caps, bio, reset or serve PORT"); System.exit(64); }
        String what = rest.get(0);
        int code;
        switch (what) {
            case "readers": code = readers(); break;
            case "identify": code = identify(false); break;
            case "guard": code = identify(true); break;
            case "caps": code = caps(); break;
            case "bio": code = bio(); break;
            case "reset": code = reset(); break;
            case "serve": code = serve(rest.size() > 1 ? Integer.parseInt(rest.get(1)) : 47470); break;
            default: System.out.println("not a command: " + what); code = 64;
        }
        if (link != null) try { link.close(); } catch (Exception e) { /* gone */ }
        System.exit(code);
    }

    // ---- the readers -------------------------------------------------------------------------------------------------------

    static int readers() throws Exception {
        if (dry) { System.out.println("dry run: would list the PC/SC readers and, for each that holds a card, power it and read its ATR; no command is sent"); return 0; }
        TerminalFactory f = TerminalFactory.getInstance("PC/SC", null, new jnasmartcardio.Smartcardio());
        List<CardTerminal> all = f.terminals().list();
        if (all.isEmpty()) { System.out.println("no reader: is the reader plugged in?"); return 15; }
        int present = 0;
        for (CardTerminal t : all) {
            boolean in = t.isCardPresent();
            String line = "reader: " + t.getName() + (in ? "   card present" : "   empty");
            if (in) {
                present++;
                Card c = t.connect("*");
                line += "   ATR " + hex(c.getATR().getBytes()) + "   protocol " + c.getProtocol();
                c.disconnect(false);
            }
            System.out.println(line);
        }
        System.out.println(present + " card(s) present" + (present > 1 ? ": take out every card but the new chip (card two above all) before going on" : ""));
        return present == 1 ? 0 : 15;
    }

    static int reset() throws Exception {
        if (dry) { System.out.println("dry run: reset would connect to the card and disconnect with a reset (power off and on); no command is sent"); return 0; }
        link = open();
        link.retap();
        System.out.println("reset: the card in " + link.where() + " was powered off and on" + (link.atr().length > 0 ? " and answers ATR " + hex(link.atr()) : ""));
        return 0;
    }

    static Link open() throws Exception {
        if (bridgeAt != null) return bridge(bridgeAt);
        TerminalFactory f = TerminalFactory.getInstance("PC/SC", null, new jnasmartcardio.Smartcardio());
        List<CardTerminal> with = new ArrayList<>();
        for (CardTerminal t : f.terminals().list()) {
            if (readerText != null && !t.getName().contains(readerText)) continue;
            if (t.isCardPresent()) with.add(t);
        }
        if (with.size() != 1) {
            System.out.println(with.isEmpty() ? "STOP: no card in " + (readerText == null ? "any reader" : "a reader named like '" + readerText + "'")
                : "STOP: " + with.size() + " readers hold a card. Take out every card but the new chip (card two above all), or name the reader with --reader.");
            System.exit(15);
        }
        final CardTerminal t = with.get(0);
        final Card first = t.connect("*");
        return new Link() {
            Card now = first;
            CardChannel ch = first.getBasicChannel();
            public byte[] send(byte[] c) throws Exception { return ch.transmit(new CommandAPDU(c)).getBytes(); }
            public void retap() throws Exception { now.disconnect(true); now = t.connect("*"); ch = now.getBasicChannel(); }
            public String where() { return t.getName(); }
            public byte[] atr() { return now.getATR().getBytes(); }
            public void close() throws Exception { now.disconnect(true); }
        };
    }

    static Link bridge(String hostAndPort) throws Exception {
        int colon = hostAndPort.lastIndexOf(':');
        final Socket s = new Socket(hostAndPort.substring(0, colon), Integer.parseInt(hostAndPort.substring(colon + 1)));
        final BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.US_ASCII));
        final OutputStream out = s.getOutputStream();
        if (!"ok".equals(talk(in, out, "tap"))) throw new IllegalStateException("the card behind the bridge does not answer a tap");
        return new Link() {
            public byte[] send(byte[] c) throws Exception {
                String r = talk(in, out, "apdu " + hex(c));
                if (r.equals("gone") || r.equals("none") || r.equals("what")) throw new IllegalStateException("the card went (" + r + ")");
                return unhex(r);
            }
            public void retap() throws Exception { talk(in, out, "end"); talk(in, out, "tap"); }
            public String where() { return "bridge " + hostAndPort; }
            public byte[] atr() { return new byte[0]; }
            public void close() throws Exception { talk(in, out, "end"); s.close(); }
        };
    }

    static String talk(BufferedReader in, OutputStream out, String line) throws Exception {
        out.write((line + "\n").getBytes(StandardCharsets.US_ASCII));
        out.flush();
        String r = in.readLine();
        if (r == null) throw new IllegalStateException("the other end closed");
        return r.trim();
    }

    // ---- what is sent ------------------------------------------------------------------------------------------------------

    static final class Step {
        final String name, apdu;
        Step(String name, String apdu) { this.name = name; this.apdu = apdu; }
    }

    static byte[] lastAnswer;

    /** One step: printed always, sent unless this is a dry run. Answer with the status word last, or null in a dry run. */
    static byte[] run(Step s) throws Exception {
        if (dry) { System.out.println("  would send  " + spaced(s.apdu) + "   (" + s.name + ")"); return null; }
        byte[] r = link.send(unhex(s.apdu));
        lastAnswer = r;
        System.out.println("  " + pad(s.name, 46) + " > " + s.apdu + "  < " + hex(r));
        return r;
    }

    static Step select(String name, String aid) {
        return new Step("SELECT " + name, "00A40400" + String.format("%02X", aid.length() / 2) + aid + "00");
    }

    // ---- identify and guard ------------------------------------------------------------------------------------------------

    static int identify(boolean judge) throws Exception {
        if (dry) {
            System.out.println("dry run: identify" + (judge ? " and guard" : "") + " would open " + (bridgeAt != null ? "the bridge " + bridgeAt
                : readerText != null ? "the reader named like '" + readerText + "'" : "the one reader that holds a card (refusing if two do)") + ", read the ATR, and send:");
        } else {
            link = open();
            System.out.println("reader: " + link.where());
            System.out.println("ATR:    " + hex(link.atr()) + "   (the seller sets these bytes; they are recorded, not trusted: card one's says JCOPv241 and is a JCOP 4)");
        }
        System.out.println("the card manager (no key, nothing written):");
        byte[] isd = run(select("the card manager (ISD) by its JCOP AID", "A000000151000000"));
        if (!dry && sw(isd) != 0x9000) isd = run(new Step("SELECT the card manager, by an empty AID", "00A4040000"));
        byte[] cplc = run(new Step("GET DATA CPLC (9F7F)", "80CA9F7F00"));
        byte[] iin = run(new Step("GET DATA IIN (42)", "80CA004200"));
        byte[] cin = run(new Step("GET DATA CIN (45)", "80CA004500"));
        byte[] cardData = run(new Step("GET DATA card data (66)", "80CA006600"));
        byte[] keys = run(new Step("GET DATA key information (E0)", "80CA00E000"));
        byte[] mem = run(new Step("GET DATA free memory (FF21, NXP)", "80CAFF2100"));
        byte[] pid = run(new Step("GET DATA platform ID (DF20, JCOP 4.5)", "80CADF2000"));
        System.out.println("the applets this work cares about (a SELECT changes nothing):");
        String[][] aids = {
            { "the card's applet (Foxy)", CARD_APPLET }, { "the opener (Foxy)", OPENER }, { "the NDEF tag AID", NDEF_TAG },
            { "the upstream applet", UPSTREAM }, { "the primitives probe", CAPS_PROBE }, { "the fingerprint probe", BIO_PROBE },
            { "a seller's free-memory applet", "A0000008466D656D6F7279" }, { "a seller's FIDO2 applet", "A0000006472F0001" },
            { "a seller's OTP applet", "A00000052721010141504558" },
        };
        byte[][] said = new byte[aids.length][];
        for (int i = 0; i < aids.length; i++) said[i] = run(select(aids[i][0], aids[i][1]));
        byte[] info = null, balance = null;
        if (!dry && sw(said[0]) == 0x9000) {
            run(select("the card's applet again", CARD_APPLET));
            info = run(new Step("GET_INFO (read only)", "B001000000"));
            balance = run(new Step("GET_BALANCE (read only)", "B011000004"));
        } else if (dry) {
            System.out.println("  if the card's applet answers, only these two more, both read only:");
            run(new Step("GET_INFO", "B001000000"));
            run(new Step("GET_BALANCE", "B011000004"));
        }
        if (dry) {
            if (judge) System.out.println("then the verdict (exit codes in the header of J3r452.java). Nothing else is sent.");
            return 0;
        }

        System.out.println();
        System.out.println("what it says:");
        String[] c = cplc(data(cplc));
        String fab = c == null ? null : c[0], type = c == null ? null : c[1], ser = c == null ? null : c[6];
        if (c != null) {
            System.out.println("  CPLC: IC fabricator " + c[0] + (c[0].equals("4790") ? " (NXP)" : " (NOT NXP)") + ", IC type " + c[1]
                + (c[1].equals(JCOP4_P71_ICTYPE) ? " (the P71D321 of JCOP 4: a J3R180-class chip)" : "") + ", OS ID " + c[2] + ", OS release date " + c[3]
                + ", OS release level " + c[4] + ", fabrication date " + c[5] + ", IC serial " + c[6] + ", batch " + c[7]);
        } else {
            System.out.println("  CPLC: not read (" + swText(cplc) + ")");
        }
        System.out.println("  IIN: " + text(iin) + "   CIN: " + text(cin));
        System.out.println("  card data: " + cardData(data(cardData)));
        System.out.println("  key information: " + keyInfo(data(keys)));
        System.out.println("  free memory: " + memory(data(mem)));
        String platform = platformId(data(pid));
        System.out.println("  platform ID: " + (platform == null ? "not read (" + swText(pid) + ")" : platform
            + (platform.startsWith("J3R6") ? "   (JCOP 4.5 P71: the certified build is J3R6000373181200)" : platform.startsWith("J3R3") ? "   (JCOP 4 P71)" : "")));
        for (int i = 0; i < aids.length; i++) {
            System.out.println("  " + pad(aids[i][0], 32) + (sw(said[i]) == 0x9000 ? "ANSWERS " + hex(data(said[i])) : sw(said[i]) == 0x6A82 ? "absent (6A82)" : swText(said[i])));
        }
        boolean foxy = sw(said[0]) == 0x9000;
        if (foxy) {
            System.out.println("  the card's applet: version " + hex(data(said[0])) + ", GET_INFO " + hex(info) + ", GET_BALANCE " + hex(balance));
        }
        if (sw(said[2]) == 0x9000 && !foxy) {
            System.out.println("  NOTE: something else holds the NDEF tag AID D2760000850101 (a seller's NFC tag, most likely). Foxy's tag cannot be made under it");
            System.out.println("        until that is deleted; the card's applet and every chip check do not need the tag.");
        }
        if (!judge) return 0;

        System.out.println();
        if (CARD_TWO.equals(ser)) { System.out.println("STOP: this is card two (" + ser + "), which holds real money. Take it out. Nothing was changed."); return 10; }
        if (!installed && !ownReinstall && foxy) {
            System.out.println("STOP: Foxy's applet answers on this card (version " + hex(data(said[0])) + "). Never install over a card that answers. Only the two reads above were made.");
            return 12;
        }
        if (CARD_ONE.equals(ser) || CARD_THREE.equals(ser)) { System.out.println("STOP: this is " + (CARD_ONE.equals(ser) ? "card one" : "card three") + " (" + ser + "), a J3R180, not the new chip."); return 11; }
        if (c == null) { System.out.println("STOP: the CPLC could not be read, so the chip cannot be told from card two. Nothing was changed."); return 16; }
        if (!"4790".equals(fab) || JCOP4_P71_ICTYPE.equals(type)) {
            System.out.println("STOP: " + (!"4790".equals(fab) ? "not an NXP chip (fabricator " + fab + ")" : "IC type D321 is the JCOP 4 P71 of the J3R180, not a JCOP 4.5") + ".");
            return 13;
        }
        if (serial == null) { System.out.println("STOP: say which chip with --serial " + ser + " (the serial above), so that nothing runs on another card."); return 14; }
        if (!serial.equals(ser)) { System.out.println("STOP: the chip in the reader is " + ser + ", not " + serial + "."); return 14; }
        if (platform == null || !platform.startsWith("J3R6")) {
            if (icType == null) {
                System.out.println("STOP: the platform ID " + (platform == null ? "could not be read" : "is " + platform + ", not a JCOP 4.5's (J3R6...)")
                    + ". If the CPLC above is the chip you bought, say so with --ictype " + type + ".");
                return 17;
            }
            if (!icType.equals(type)) { System.out.println("STOP: the IC type is " + type + ", not " + icType + "."); return 17; }
        }
        if (installed) {
            byte[] v = data(said[0]);
            if (!foxy || v == null || v.length < 2 || v[0] != 0x01 || v[1] != 0x17) {
                System.out.println("STOP: the card's applet does not answer as software 1.23 (01 17): " + swText(said[0]) + (v == null ? "" : " " + hex(v)));
                return 12;
            }
            byte[] gi = data(info);
            boolean blank = gi != null && gi.length >= 30 && gi[16] == 0 && gi[11] == 0 && gi[3] == 0 && gi[4] == 0 && gi[7] == 0;
            byte[] bal = data(balance);
            boolean empty = bal != null && bal.length == 4 && bal[0] == 0 && bal[1] == 0 && bal[2] == 0 && bal[3] == 0;
            if (!blank || !empty) {
                System.out.println("STOP: the card is not blank (no owner, no record, no piece, no PIN and nothing on it is what the chip checks want). Reinstall it.");
                return 12;
            }
            System.out.println("GO: " + ser + " is the new chip, on software 1.23, blank and holding nothing.");
            return 0;
        }
        System.out.println("GO: " + ser + " is the new chip" + (platform != null && platform.startsWith("J3R6") ? " (JCOP 4.5 by its platform ID)" : " (IC type " + type + ", as you said)")
            + (foxy ? ", and Foxy's software on it (version " + hex(data(said[0])) + ") is to be deleted and installed again." : ", and Foxy's applet is not on it."));
        return 0;
    }

    static String[] cplc(byte[] d) {
        if (d == null) return null;
        if (d.length >= 3 && (d[0] & 0xFF) == 0x9F && (d[1] & 0xFF) == 0x7F) d = Arrays.copyOfRange(d, 3, d.length);
        if (d.length < 42) return null;
        String h = hex(d);
        return new String[] { h.substring(0, 4), h.substring(4, 8), h.substring(8, 12), h.substring(12, 16), h.substring(16, 20),
            h.substring(20, 24), h.substring(24, 32), h.substring(32, 36) };
    }

    static String cardData(byte[] d) {
        if (d == null) return "not read";
        String h = hex(d), out = "";
        String prefix = "2A864886FC6B";
        for (int at = h.indexOf(prefix); at >= 0; at = h.indexOf(prefix, at + 1)) {
            int lenAt = at - 2;
            if (lenAt < 0) continue;
            int len = Integer.parseInt(h.substring(lenAt, lenAt + 2), 16);
            String tail = h.substring(at + prefix.length(), Math.min(h.length(), at + len * 2));
            List<String> parts = new ArrayList<>();
            for (int i = 0; i + 2 <= tail.length(); i += 2) parts.add(String.valueOf(Integer.parseInt(tail.substring(i, i + 2), 16)));
            if (parts.isEmpty()) continue;
            switch (parts.get(0)) {
                case "2": out += "GlobalPlatform " + String.join(".", parts.subList(1, parts.size())) + "; "; break;
                case "4": out += (parts.size() > 2 ? "SCP0" + parts.get(1) + " i=" + String.format("%02X", Integer.parseInt(parts.get(2))) : "SCP " + parts) + "; "; break;
                default: out += "GP OID 1.2.840.114283." + String.join(".", parts) + "; ";
            }
        }
        return out.isEmpty() ? hex(d) : out + "(raw " + hex(d) + ")";
    }

    static String keyInfo(byte[] d) {
        if (d == null) return "not read";
        String out = "";
        for (int i = 0; i + 1 < d.length; i++) {
            if ((d[i] & 0xFF) == 0xC0 && i + 1 < d.length) {
                int n = d[i + 1] & 0xFF;
                if (i + 2 + n > d.length || n < 4) continue;
                int id = d[i + 2] & 0xFF, ver = d[i + 3] & 0xFF, typ = d[i + 4] & 0xFF;
                out += "key " + id + " version " + ver + " type " + String.format("%02X", typ) + (typ == 0x80 ? " (DES: SCP02)" : typ == 0x88 ? " (AES: SCP03)" : "") + "; ";
                i += 1 + n;
            }
        }
        return out.isEmpty() ? hex(d) : out;
    }

    static String memory(byte[] d) {
        if (d == null) return "not read";
        String h = hex(d);
        int p = h.indexOf("8204"), v = h.indexOf("8304");
        String out = "";
        if (p >= 0 && p + 12 <= h.length()) out += Long.parseLong(h.substring(p + 4, p + 12), 16) + " bytes persistent";
        if (v >= 0 && v + 12 <= h.length()) out += ", " + Long.parseLong(h.substring(v + 4, v + 12), 16) + " bytes volatile";
        return (out.isEmpty() ? "" : out + "   ") + "(raw " + h + "; a blank J3R180 shows about 164,176 and 2,486)";
    }

    static String platformId(byte[] d) {
        if (d == null) return null;
        if (d.length > 3 && (d[0] & 0xFF) == 0xDF && (d[1] & 0xFF) == 0x20) d = Arrays.copyOfRange(d, 3, d.length);
        StringBuilder s = new StringBuilder();
        for (byte b : d) s.append(b >= 0x20 && b < 0x7F ? (char) b : '.');
        return s.toString() + " (" + hex(d) + ")";
    }

    // ---- the primitives probe ----------------------------------------------------------------------------------------------

    static final BigInteger P = new BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFFC2F", 16);
    static final BigInteger GX = new BigInteger("79BE667EF9DCBBAC55A06295CE870B07029BFCDB2DCE28D959F2815B16F81798", 16);
    static final BigInteger GY = new BigInteger("483ADA7726A3C4655DA4FBFC0E1108A8FD17B448A68554199C47D08FFB10D4B8", 16);

    static BigInteger[] add(BigInteger[] a, BigInteger[] b) {
        BigInteger l;
        if (a[0].equals(b[0])) l = a[0].pow(2).multiply(BigInteger.valueOf(3)).multiply(a[1].shiftLeft(1).modInverse(P)).mod(P);
        else l = b[1].subtract(a[1]).multiply(b[0].subtract(a[0]).modInverse(P)).mod(P);
        BigInteger x = l.pow(2).subtract(a[0]).subtract(b[0]).mod(P);
        return new BigInteger[] { x, l.multiply(a[0].subtract(x)).subtract(a[1]).mod(P) };
    }

    static String point(BigInteger[] q) { return "04" + String.format("%064X", q[0]) + String.format("%064X", q[1]); }

    static final String[] PRIMITIVES = { "SHA-256", "RandomData ALG_SECURE_RANDOM", "KeyAgreement ALG_EC_SVDP_DH_PLAIN_XY (k*G, signing)",
        "KeyAgreement ALG_EC_PACE_GM (point addition, change)", "Cipher ALG_RSA_NOPAD (field arithmetic, change)", "Signature ALG_ECDSA_SHA_256 (the owner's proof)",
        "EC-FP private key, 256 bits, secp256k1 set by hand", "EC-FP public key, the same", "a secp256k1 key pair made on the chip", "RSA private key 512 bits over p*p, exponent 3" };

    static int caps() throws Exception {
        if (dry) System.out.println("dry run: caps would send, to the primitives probe (it must be on the chip already):");
        else { link = open(); System.out.println("reader: " + link.where()); }
        byte[] sel = run(select("the primitives probe", CAPS_PROBE));
        byte[] t = run(new Step("the primitives (01)", "B001000000"));
        byte[] two = run(new Step("[2]G by PLAIN_XY (02)", "B002000000"));
        byte[] three = run(new Step("[2]G + G by PACE_GM (03)", "B003000000"));
        byte[] cube = run(new Step("Gx^3 mod p*p by RSA_NOPAD (04)", "B004000000"));
        byte[] mem = run(new Step("memory, commit capacity, version (05)", "B005000000"));
        byte[] sig = run(new Step("ECDSA over secp256k1, checked on the chip (06)", "B006000000"));
        if (dry) return 0;
        System.out.println();
        if (sw(sel) != 0x9000) { System.out.println("STOP: the probe does not answer (" + swText(sel) + "): install it first."); return 22; }
        byte[] r = data(t);
        if (r == null || r.length != 10) { System.out.println("STOP: the primitives answer is " + swText(t)); return 22; }
        boolean core = true, change = true;
        for (int i = 0; i < 10; i++) {
            int v = r[i] & 0xFF;
            String what = v == 0 ? "given" : v == 3 ? "REFUSED: NO_SUCH_ALGORITHM (03)" : v == 0xFF ? "not tried (a key it needs was refused)" : v == 0xFE ? "REFUSED: another card exception" : "REFUSED: CryptoException reason " + v;
            System.out.println("  [" + i + "] " + pad(PRIMITIVES[i], 56) + what);
            if (v != 0) { if (i == 3 || i == 4 || i == 9) change = false; else core = false; }
        }
        BigInteger[] g = { GX, GY }, g2 = add(g, g), g3 = add(g2, g);
        String want2 = point(g2), want3 = point(g3);
        String gotTwo = hex(data(two)), gotThree = hex(data(three));
        boolean okTwo = want2.equals(gotTwo), okThree = want3.equals(gotThree);
        System.out.println("  [2]G by PLAIN_XY:       " + (okTwo ? "right, 65 bytes 04||x||y, as the signer reads it" : "WRONG: " + (data(two) == null ? swText(two) : gotTwo + " (want " + want2 + ")")));
        System.out.println("  [2]G + G by PACE_GM:    " + (okThree ? "right (3G): the chip's point addition works as on the J3R180" : "WRONG: " + (data(three) == null ? swText(three) : gotThree + " (want " + want3 + ")")));
        BigInteger pp = P.multiply(P), want = GX.modPow(BigInteger.valueOf(3), pp);
        String wantCube = String.format("%0128X", want), gotCube = hex(data(cube));
        boolean okCube = wantCube.equals(gotCube);
        System.out.println("  Gx^3 mod p*p by RSA:    " + (okCube ? "right, 64 bytes, leading zeros kept" : "WRONG: " + (data(cube) == null ? swText(cube) : gotCube + " (want " + wantCube + ")")));
        byte[] m = data(mem);
        if (m != null && m.length == 24) {
            System.out.println("  memory available to an applet: " + u32(m, 0) + " persistent, " + u32(m, 4) + " clear-on-reset, " + u32(m, 8) + " clear-on-deselect (the short call, capped at 32,767: "
                + u16(m, 18) + ", " + u16(m, 20) + ", " + u16(m, 22) + "); transaction commit capacity " + u16(m, 12) + " bytes (" + u16(m, 14) + " unused); JCSystem.getVersion "
                + String.format("%02X%02X", m[16] & 0xFF, m[17] & 0xFF) + " (0305 is Java Card 3.0.5)");
            System.out.println("  for comparison, the card's applet asks for 942 bytes clear-on-deselect and 6 clear-on-reset (the J3R180 refused 1,030), and its burn keeps every transaction small");
        } else System.out.println("  memory: " + swText(mem));
        boolean okSig = data(sig) != null && data(sig).length == 1 && data(sig)[0] == 1;
        System.out.println("  ECDSA over secp256k1:   " + (okSig ? "signs and verifies on the chip" : "NOT verified: " + swText(sig)));
        System.out.println();
        if (!core || !okTwo) {
            System.out.println("NO: the chip lacks what the card's applet needs to sign at all (k*G by PLAIN_XY or a hand-set secp256k1). Do not install the card's applet.");
            return 20;
        }
        if (!change || !okThree || !okCube) {
            System.out.println("NOT AS IS: the chip signs, but lacks the generic mapping or RSA over p*p that the card's own change uses. Software 1.23 asks for both in its");
            System.out.println("constructor, so its INSTALL would fail (6F00) whatever its size. Do not try sizes: a build without the card's own change is the owner's decision.");
            return 21;
        }
        System.out.println("YES: every primitive the card's applet asks for is given, and each answers as the applet reads it.");
        return okSig ? 0 : 22;
    }

    // ---- the fingerprint probe ---------------------------------------------------------------------------------------------

    static int bio() throws Exception {
        if (dry) System.out.println("dry run: bio would send, to the fingerprint probe (tools/probe-bio; it must be on the chip already):");
        else { link = open(); System.out.println("reader: " + link.where()); }
        byte[] sel = run(select("the fingerprint probe", BIO_PROBE));
        byte[] fp = run(new Step("build a FINGERPRINT template, 5 tries (10)", "B010000000"));
        String[] types = { "01", "02", "03", "04", "05", "06", "07", "08", "09", "0A", "0B", "0C", "0D", "0E", "0F", "10", "11", "12", "1F", "13", "7F" };
        byte[][] each = new byte[types.length][];
        for (int i = 0; i < types.length; i++) each[i] = run(new Step("bio type " + types[i] + " (11)", "B011" + types[i] + "0000"));
        if (dry) return 0;
        System.out.println();
        if (sw(sel) != 0x9000) { System.out.println("the probe does not answer (" + swText(sel) + "): was its load refused? A refused load means the chip has no javacardx.biometry."); return 31; }
        int s = sw(fp);
        if (s == 0x9000) { System.out.println("YES: an applet of ours made a fingerprint template (" + hex(data(fp)) + "): a match-on-card engine is on this chip."); return 0; }
        System.out.println(s == 0x6B03 ? "NO ENGINE: javacardx.biometry is there, and FINGERPRINT gets NO_SUCH_BIO_TEMPLATE (6B03), as on the J3R180."
            : "REFUSED: " + swText(fp) + " (6Bxx: BioException reason xx; 01 ILLEGAL_VALUE, 02 INVALID_DATA, 03 NO_SUCH_BIO_TEMPLATE, 04 NO_TEMPLATES_ENROLLED, 05 ILLEGAL_USE).");
        StringBuilder line = new StringBuilder("  every type: ");
        for (int i = 0; i < types.length; i++) line.append(types[i]).append("=").append(String.format("%04X", sw(each[i]))).append(" ");
        System.out.println(line);
        return 30;
    }

    // ---- the bridge --------------------------------------------------------------------------------------------------------

    static int serve(int port) throws Exception {
        if (serial == null) { System.out.println("STOP: serve needs --serial, the new chip's serial, so that the checks cannot reach another card."); return 14; }
        if (dry) { System.out.println("dry run: serve would check that the chip in the reader has serial " + serial + " (SELECT A000000151000000, GET DATA 9F7F), reset it, and pass commands from 127.0.0.1:" + port + " to it"); return 0; }
        link = open();
        run(select("the card manager", "A000000151000000"));
        String[] c = cplc(data(run(new Step("GET DATA CPLC", "80CA9F7F00"))));
        if (c == null || !serial.equals(c[6]) || CARD_TWO.equals(c[6])) {
            System.out.println("STOP: the chip in " + link.where() + " is " + (c == null ? "unreadable" : c[6]) + ", not " + serial + ". Nothing is served.");
            return 14;
        }
        link.retap();
        PrintWriter log = new PrintWriter(new FileWriter(logFile != null ? logFile : "j3r452-bridge.log", false), true);
        try (ServerSocket listening = new ServerSocket(port, 8, InetAddress.getLoopbackAddress())) {
            System.out.println("the chip " + serial + " in '" + link.where() + "' is on 127.0.0.1:" + port);
            while (true) {
                try (Socket peer = listening.accept()) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(peer.getInputStream(), StandardCharsets.US_ASCII));
                    OutputStream out = peer.getOutputStream();
                    String line;
                    while ((line = in.readLine()) != null) {
                        line = line.trim();
                        String answer;
                        if (line.equals("tap")) {
                            try { link.retap(); log.println("tap"); answer = "ok"; } catch (Exception e) { log.println("tap failed: " + e); answer = "none"; }
                        } else if (line.equals("end")) { log.println("end"); answer = "ok"; }
                        else if (line.startsWith("ctl ")) { log.println(line); answer = "ok"; }
                        else if (line.startsWith("apdu ")) {
                            byte[] cmd = unhex(line.substring(5).trim());
                            if (cmd == null || cmd.length < 4) answer = "6700";
                            else {
                                try {
                                    long t0 = System.nanoTime();
                                    byte[] r = link.send(cmd);
                                    long ms = (System.nanoTime() - t0) / 1000000;
                                    log.println(String.format("  %02x %02x p1 %02x p2 %02x in %3d -> %02x%02x out %3d  %5d ms", cmd[0] & 0xff, cmd[1] & 0xff, cmd[2] & 0xff, cmd[3] & 0xff,
                                        cmd.length > 5 ? cmd.length - 5 : 0, r[r.length - 2] & 0xff, r[r.length - 1] & 0xff, r.length - 2, ms));
                                    answer = hex(r).toLowerCase();
                                } catch (Exception e) { log.println(String.format("  %02x %02x FAILED: %s", cmd[0] & 0xff, cmd[1] & 0xff, e)); answer = "gone"; }
                            }
                        } else answer = "what";
                        out.write((answer + "\n").getBytes(StandardCharsets.US_ASCII));
                        out.flush();
                    }
                } catch (Exception e) { log.println("the other end went: " + e); }
            }
        }
    }

    // ---- small things ------------------------------------------------------------------------------------------------------

    static int sw(byte[] r) { return r == null || r.length < 2 ? -1 : ((r[r.length - 2] & 0xFF) << 8) | (r[r.length - 1] & 0xFF); }
    static byte[] data(byte[] r) { return sw(r) == 0x9000 ? Arrays.copyOf(r, r.length - 2) : null; }
    static String swText(byte[] r) { return r == null ? "no answer" : String.format("%04X", sw(r)); }
    static String text(byte[] r) { return data(r) == null ? swText(r) : hex(data(r)); }
    static long u32(byte[] b, int at) { return ((long) (b[at] & 0xFF) << 24) | ((b[at + 1] & 0xFF) << 16) | ((b[at + 2] & 0xFF) << 8) | (b[at + 3] & 0xFF); }
    static int u16(byte[] b, int at) { return ((b[at] & 0xFF) << 8) | (b[at + 1] & 0xFF); }
    static String pad(String s, int n) { StringBuilder b = new StringBuilder(s); while (b.length() < n) b.append(' '); return b.toString(); }
    static String spaced(String h) { StringBuilder b = new StringBuilder(); for (int i = 0; i < h.length(); i += 2) b.append(h, i, i + 2).append(i + 2 < h.length() ? " " : ""); return b.toString(); }

    static String hex(byte[] b) {
        if (b == null) return "";
        StringBuilder s = new StringBuilder();
        for (byte x : b) s.append(String.format("%02X", x & 0xFF));
        return s.toString();
    }

    static byte[] unhex(String s) {
        if (s.length() % 2 != 0 || !s.matches("[0-9a-fA-F]*")) return null;
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) out[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
        return out;
    }
}
