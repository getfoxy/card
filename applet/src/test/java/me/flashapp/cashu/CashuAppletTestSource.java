package me.flashapp.cashu;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Reading the applet's source for the tests that hold it to what the design says it writes (1.23, UnitsTest): its methods, their
 * transactions and the writes in them; and the two things a replay of a recorded transcript needs, the file and a card with the
 * PIN key the recording's card had.
 */
final class CashuAppletTestSource {
    private CashuAppletTestSource() { }

    /** CashuApplet.java with its comments and character literals taken out. */
    static String code() throws Exception {
        String src = new String(java.nio.file.Files.readAllBytes(SchnorrHWMathTest.mainSourceDir().resolve("CashuApplet.java")), StandardCharsets.UTF_8);
        return SchnorrHWMathTest.stripCommentsAndCharLiterals(src);
    }

    /** The source from one declaration to the next one named. */
    static String body(String code, String from, String to) {
        int start = code.indexOf(from);
        int end = code.indexOf(to, start + 1);
        assertTrue(start > 0 && end > start, "found " + from + " before " + to);
        return code.substring(start, end);
    }

    /** What lies between the first beginTransaction and the commit after it. */
    static String transaction(String body) {
        String[] all = transactions(body);
        assertTrue(all.length > 0, "a transaction in it");
        return all[0];
    }

    /** Every transaction in the body, in order: what lies between each beginTransaction and the commit after it. */
    static String[] transactions(String body) {
        List<String> out = new ArrayList<>();
        int at = 0;
        while (true) {
            int begin = body.indexOf("JCSystem.beginTransaction();", at);
            if (begin < 0) break;
            int commit = body.indexOf("JCSystem.commitTransaction();", begin);
            out.add(body.substring(begin, commit));
            at = commit + 1;
        }
        return out.toArray(new String[0]);
    }

    /**
     * The writes to permanent memory a transaction can make, as copies and fills of four or more and as single bytes, counted from
     * its text: each `Util.arrayCopy(` and each `arrayFill` into a permanent array, less any that only a constant set the other way
     * would run (ALLOWANCE_RENEWS_ON_PIN, which is true).
     */
    static int writes(String tx) {
        assertTrue(CashuApplet.ALLOWANCE_RENEWS_ON_PIN, "the allowance renews on the PIN");
        int n = 0;
        for (String line : tx.split("\n")) {
            if (line.contains("!ALLOWANCE_RENEWS_ON_PIN")) continue;
            n += count(line, "Util.arrayCopy(");
        }
        return n;
    }

    static int count(String s, String what) {
        int n = 0;
        for (int at = s.indexOf(what); at >= 0; at = s.indexOf(what, at + 1)) n++;
        return n;
    }

    /** A `static final short NAME = (short) n;` of the applet, as a number. */
    static int constant(String code, String name) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("static\\s+final\\s+short\\s+" + name + "\\s*=\\s*\\(short\\)\\s*(\\d+)\\s*;").matcher(code);
        assertTrue(m.find(), "the constant " + name);
        return Integer.parseInt(m.group(1));
    }

    /** The names of the methods whose bodies name `field` (as a word: not a constant that begins with it). */
    static Set<String> methodsNaming(String code, String field) {
        Set<String> out = new TreeSet<>();
        java.util.regex.Pattern header = java.util.regex.Pattern.compile("(?m)^\\s*(?:public|private|protected|static|final|\\s)*[\\w\\[\\]<>]+\\s+(\\w+)\\s*\\([^;{)]*\\)\\s*(?:throws[^{]*)?\\{");
        java.util.regex.Pattern word = java.util.regex.Pattern.compile("(?<![\\w.])" + field + "(?![\\w])");
        java.util.regex.Matcher m = header.matcher(code);
        while (m.find()) {
            int open = m.end() - 1, depth = 0, i = open;
            for (; i < code.length(); i++) {
                char c = code.charAt(i);
                if (c == '{') depth++;
                else if (c == '}' && --depth == 0) break;
            }
            if (word.matcher(code.substring(open, i)).find()) out.add(m.group(1));
        }
        return out;
    }

    /** The repository's root: the folder above the applet's. */
    static java.nio.file.Path repository() throws Exception {
        java.nio.file.Path p = SchnorrHWMathTest.mainSourceDir().toAbsolutePath().normalize();
        while (p != null && !p.endsWith("applet")) p = p.getParent();
        assertTrue(p != null, "the applet folder");
        return p.getParent();
    }

    /** A transcript's entries, each its fields as strings: the file is one object a line, of strings only, as the recording writes it. */
    static List<Map<String, String>> entries(String json) {
        List<Map<String, String>> out = new ArrayList<>();
        java.util.regex.Matcher obj = java.util.regex.Pattern.compile("\\{([^{}]*)\\}").matcher(json);
        java.util.regex.Pattern pair = java.util.regex.Pattern.compile("\"(\\w+)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
        while (obj.find()) {
            Map<String, String> m = new LinkedHashMap<>();
            java.util.regex.Matcher p = pair.matcher(obj.group(1));
            while (p.find()) m.put(p.group(1), p.group(2).replace("\\\"", "\"").replace("\\\\", "\\"));
            out.add(m);
        }
        return out;
    }

    /** The card's PIN key set to the value the transcript's recording gave it (CashuAppletTest.ownPinKey): a chip's PIN key is not its signing key. */
    static void ownPinKey(Object applet) throws Exception {
        java.lang.reflect.Field fp = CashuApplet.class.getDeclaredField("pinPrivKey");
        java.lang.reflect.Field fw = CashuApplet.class.getDeclaredField("pinPubKey");
        fp.setAccessible(true);
        fw.setAccessible(true);
        javacard.security.ECPrivateKey priv = (javacard.security.ECPrivateKey) fp.get(applet);
        javacard.security.ECPublicKey pub = (javacard.security.ECPublicKey) fw.get(applet);
        priv.setS(u32Of(CashuAppletTest.PIN_D), (short) 0, (short) 32);
        java.math.BigInteger[] w = CashuAppletTest.ecMulTest(CashuAppletTest.PIN_D, CashuAppletTest.SECP_GX, CashuAppletTest.SECP_GY);
        byte[] w65 = CashuAppletTest.concat(new byte[] { 4 }, u32Of(w[0]), u32Of(w[1]));
        pub.setW(w65, (short) 0, (short) 65);
    }

    private static byte[] u32Of(java.math.BigInteger n) {
        byte[] b = n.toByteArray();
        byte[] out = new byte[32];
        int len = Math.min(b.length, 32);
        System.arraycopy(b, b.length - len, out, 32 - len, len);
        return out;
    }
}
