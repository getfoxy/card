package me.foxy.probe;

import javacard.framework.*;
import javacard.security.*;
import javacardx.crypto.Cipher;

/* A probe with one set of questions for a new chip: does it offer each primitive the card's applet asks for at install,
   and does each give the answer the applet relies on? The card's applet asks for all of them in its constructor, so on a
   chip that lacks one its INSTALL fails with 6F00, which is also how a load file of a size the chip will not take fails
   (docs/FOXY-CARD-HARDWARE.md). This probe tells the two apart before the card's applet is tried. It allocates nothing at
   install, so it installs wherever a Java Card 3.0.5 applet can; it asks for each primitive only when asked.
   Class B0:
     01  the primitives, 10 bytes, one for each: 00 given; 01..05 CryptoException's reason (03 NO_SUCH_ALGORITHM = the chip
         does not offer it); FE another card exception; FF not tried (an earlier one it needs was refused). In order:
         [0] SHA-256  [1] RandomData ALG_SECURE_RANDOM  [2] KeyAgreement ALG_EC_SVDP_DH_PLAIN_XY  [3] KeyAgreement
         ALG_EC_PACE_GM  [4] Cipher ALG_RSA_NOPAD  [5] Signature ALG_ECDSA_SHA_256  [6] an EC-FP private key of 256 bits
         with secp256k1 set by hand  [7] the public key the same  [8] a secp256k1 key pair made on the chip
         [9] a 512-bit RSA private key whose modulus is p*p (p secp256k1's field prime), exponent 3
     02  [2]G by the plain key agreement: its whole answer (65 bytes, 04 || x || y, is what the applet reads)
     03  [2]G + G by the generic mapping, which the applet uses as its point addition: its whole answer (65 bytes, 3G)
     04  (G.x)^3 mod p*p by RSA without padding: its whole answer (64 bytes)
     05  memory: persistent, clear-on-reset and clear-on-deselect bytes available (4 bytes each, big-endian), the
         transaction's commit capacity and what is unused of it (2 bytes each), the Java Card version (2 bytes), and the same
         three amounts by the older call, which stops at 32,767 (2 bytes each): 24 bytes
     06  an ECDSA signature over secp256k1 with the pair of [8], checked on the chip: 01 if it verifies
   A refusal in 02..06 comes back as 6F0r (CryptoException reason r) or 6FFF.
   The host checks the answers (tools/j3r452/J3r452.java, "caps"). Delete the probe afterwards: its package F0464F585943415053. */
public class CapsProbe extends Applet {
    private static final byte[] P = {
        (byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,
        (byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,
        (byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,
        (byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFE,(byte)0xFF,(byte)0xFF,(byte)0xFC,(byte)0x2F
    };
    private static final byte[] G = {
        (byte)0x04,(byte)0x79,(byte)0xBE,(byte)0x66,(byte)0x7E,(byte)0xF9,(byte)0xDC,(byte)0xBB,
        (byte)0xAC,(byte)0x55,(byte)0xA0,(byte)0x62,(byte)0x95,(byte)0xCE,(byte)0x87,(byte)0x0B,
        (byte)0x07,(byte)0x02,(byte)0x9B,(byte)0xFC,(byte)0xDB,(byte)0x2D,(byte)0xCE,(byte)0x28,
        (byte)0xD9,(byte)0x59,(byte)0xF2,(byte)0x81,(byte)0x5B,(byte)0x16,(byte)0xF8,(byte)0x17,
        (byte)0x98,(byte)0x48,(byte)0x3A,(byte)0xDA,(byte)0x77,(byte)0x26,(byte)0xA3,(byte)0xC4,
        (byte)0x65,(byte)0x5D,(byte)0xA4,(byte)0xFB,(byte)0xFC,(byte)0x0E,(byte)0x11,(byte)0x08,
        (byte)0xA8,(byte)0xFD,(byte)0x17,(byte)0xB4,(byte)0x48,(byte)0xA6,(byte)0x85,(byte)0x54,
        (byte)0x19,(byte)0x9C,(byte)0x47,(byte)0xD0,(byte)0x8F,(byte)0xFB,(byte)0x10,(byte)0xD4,
        (byte)0xB8
    };
    private static final byte[] N = {
        (byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,
        (byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFE,
        (byte)0xBA,(byte)0xAE,(byte)0xDC,(byte)0xE6,(byte)0xAF,(byte)0x48,(byte)0xA0,(byte)0x3B,
        (byte)0xBF,(byte)0xD2,(byte)0x5E,(byte)0x8C,(byte)0xD0,(byte)0x36,(byte)0x41,(byte)0x41
    };
    private static final byte[] PP = {
        (byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,
        (byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,
        (byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,
        (byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFD,(byte)0xFF,(byte)0xFF,(byte)0xF8,(byte)0x5E,
        (byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,
        (byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,
        (byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x01,
        (byte)0x00,(byte)0x00,(byte)0x07,(byte)0xA2,(byte)0x00,(byte)0x0E,(byte)0x90,(byte)0xA1
    };
    private static final byte[] B7 = {
        0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,7
    };
    private static final byte[] ZERO32 = new byte[32];
    private static final byte[] THREE = { 3 };

    private byte[] said;
    private MessageDigest sha;
    private RandomData rng;
    private KeyAgreement dh, gm;
    private Cipher rsa;
    private Signature ecdsa;
    private ECPrivateKey priv;
    private ECPublicKey pub;
    private RSAPrivateKey rsaKey;
    private short[] mem;

    private CapsProbe() {
        mem = JCSystem.makeTransientShortArray((short) 2, JCSystem.CLEAR_ON_RESET);
        register();
    }

    public static void install(byte[] b, short o, byte l) { new CapsProbe(); }

    private static void curve(ECKey k) {
        k.setFieldFP(P, (short) 0, (short) 32);
        k.setA(ZERO32, (short) 0, (short) 32);
        k.setB(B7, (short) 0, (short) 32);
        k.setG(G, (short) 0, (short) 65);
        k.setR(N, (short) 0, (short) 32);
        k.setK((short) 1);
    }

    private byte attempt(byte i) {
        try {
            switch (i) {
                case 0: sha = MessageDigest.getInstance(MessageDigest.ALG_SHA_256, false); break;
                case 1: rng = RandomData.getInstance(RandomData.ALG_SECURE_RANDOM); break;
                case 2: dh = KeyAgreement.getInstance(KeyAgreement.ALG_EC_SVDP_DH_PLAIN_XY, false); break;
                case 3: gm = KeyAgreement.getInstance(KeyAgreement.ALG_EC_PACE_GM, false); break;
                case 4: rsa = Cipher.getInstance(Cipher.ALG_RSA_NOPAD, false); break;
                case 5: ecdsa = Signature.getInstance(Signature.ALG_ECDSA_SHA_256, false); break;
                case 6:
                    priv = (ECPrivateKey) KeyBuilder.buildKey(KeyBuilder.TYPE_EC_FP_PRIVATE, KeyBuilder.LENGTH_EC_FP_256, false);
                    curve(priv);
                    break;
                case 7:
                    pub = (ECPublicKey) KeyBuilder.buildKey(KeyBuilder.TYPE_EC_FP_PUBLIC, KeyBuilder.LENGTH_EC_FP_256, false);
                    curve(pub);
                    break;
                case 8:
                    if (priv == null || pub == null) return (byte) 0xFF;
                    new KeyPair(pub, priv).genKeyPair();
                    break;
                default:
                    rsaKey = (RSAPrivateKey) KeyBuilder.buildKey(KeyBuilder.TYPE_RSA_PRIVATE, KeyBuilder.LENGTH_RSA_512, false);
                    rsaKey.setModulus(PP, (short) 0, (short) 64);
                    rsaKey.setExponent(THREE, (short) 0, (short) 1);
                    break;
            }
            return 0;
        } catch (CryptoException e) {
            return (byte) e.getReason();
        } catch (CardRuntimeException e) {
            return (byte) 0xFE;
        } catch (RuntimeException e) {
            return (byte) 0xFE;
        }
    }

    private static void put32(byte[] buf, short at, short[] hiLo) {
        Util.setShort(buf, at, hiLo[0]);
        Util.setShort(buf, (short) (at + 2), hiLo[1]);
    }

    public void process(APDU apdu) {
        if (selectingApplet()) return;
        byte[] buf = apdu.getBuffer();
        if (buf[ISO7816.OFFSET_CLA] != (byte) 0xB0) ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);
        byte ins = buf[ISO7816.OFFSET_INS];
        short len;
        try {
            switch (ins) {
                case 0x01:
                    if (said == null) {
                        said = new byte[10];
                        for (byte i = 0; i < 10; i++) said[i] = attempt(i);
                    }
                    Util.arrayCopyNonAtomic(said, (short) 0, buf, (short) 0, (short) 10);
                    len = 10;
                    break;
                case 0x02:
                    if (dh == null || priv == null) ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
                    Util.arrayFillNonAtomic(buf, (short) 0, (short) 32, (byte) 0);
                    buf[31] = 2;
                    priv.setS(buf, (short) 0, (short) 32);
                    dh.init(priv);
                    len = dh.generateSecret(G, (short) 0, (short) 65, buf, (short) 0);
                    break;
                case 0x03:
                    if (gm == null || priv == null) ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
                    Util.arrayFillNonAtomic(buf, (short) 0, (short) 32, (byte) 0);
                    buf[31] = 2;
                    priv.setG(G, (short) 0, (short) 65);
                    priv.setS(buf, (short) 0, (short) 32);
                    gm.init(priv);
                    len = gm.generateSecret(G, (short) 0, (short) 65, buf, (short) 0);
                    break;
                case 0x04:
                    if (rsa == null || rsaKey == null) ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
                    Util.arrayFillNonAtomic(buf, (short) 0, (short) 32, (byte) 0);
                    Util.arrayCopyNonAtomic(G, (short) 1, buf, (short) 32, (short) 32);
                    rsa.init(rsaKey, Cipher.MODE_DECRYPT);
                    len = rsa.doFinal(buf, (short) 0, (short) 64, buf, (short) 0);
                    break;
                case 0x05:
                    JCSystem.getAvailableMemory(mem, (short) 0, JCSystem.MEMORY_TYPE_PERSISTENT);
                    put32(buf, (short) 0, mem);
                    JCSystem.getAvailableMemory(mem, (short) 0, JCSystem.MEMORY_TYPE_TRANSIENT_RESET);
                    put32(buf, (short) 4, mem);
                    JCSystem.getAvailableMemory(mem, (short) 0, JCSystem.MEMORY_TYPE_TRANSIENT_DESELECT);
                    put32(buf, (short) 8, mem);
                    Util.setShort(buf, (short) 12, JCSystem.getMaxCommitCapacity());
                    Util.setShort(buf, (short) 14, JCSystem.getUnusedCommitCapacity());
                    Util.setShort(buf, (short) 16, JCSystem.getVersion());
                    Util.setShort(buf, (short) 18, JCSystem.getAvailableMemory(JCSystem.MEMORY_TYPE_PERSISTENT));
                    Util.setShort(buf, (short) 20, JCSystem.getAvailableMemory(JCSystem.MEMORY_TYPE_TRANSIENT_RESET));
                    Util.setShort(buf, (short) 22, JCSystem.getAvailableMemory(JCSystem.MEMORY_TYPE_TRANSIENT_DESELECT));
                    len = 24;
                    break;
                case 0x06:
                    if (ecdsa == null || priv == null || pub == null || said == null || said[8] != 0) ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
                    new KeyPair(pub, priv).genKeyPair();
                    Util.arrayFillNonAtomic(buf, (short) 0, (short) 32, (byte) 0x5A);
                    ecdsa.init(priv, Signature.MODE_SIGN);
                    short sigLen = ecdsa.sign(buf, (short) 0, (short) 32, buf, (short) 32);
                    ecdsa.init(pub, Signature.MODE_VERIFY);
                    boolean good = ecdsa.verify(buf, (short) 0, (short) 32, buf, (short) 32, sigLen);
                    buf[0] = good ? (byte) 1 : (byte) 0;
                    len = 1;
                    break;
                default:
                    ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
                    return;
            }
        } catch (CryptoException e) {
            ISOException.throwIt((short) (0x6F00 | (e.getReason() & 0xFF)));
            return;
        }
        apdu.setOutgoingAndSend((short) 0, len);
    }
}
