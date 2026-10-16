package me.foxy.probe;

import javacard.framework.*;
import javacardx.biometry.*;

/* A probe with one question for the chip: can an applet of ours hold a fingerprint template?
   It loads beside the card's own applet and touches nothing of it. If the chip has no javacardx.biometry package at
   all, the LOAD itself is refused and this code never runs: that is the first answer. Otherwise, class B0:
     10      try to build a fingerprint template (BioBuilder.FINGERPRINT, 5 tries). Answers one byte on success:
             01 = made, and then its bio type and whether it says it is initialised (3 bytes: 01 type init).
             A refusal comes back as a status: 6Bxx = BioException reason xx (01 ILLEGAL_VALUE: the type is not
             supported; 02 INVALID_DATA; 03 NO_TEMPLATES_ENROLLED; 04 NO_TEMPLATE_INITIALIZED), 6Fxx = any other
             card exception's reason, 6F00 = something else.
     20      the same for the 1:N builder is not tried: FOXY needs 1:1 only.
   Delete the probe afterwards (run.sh does). The template it made goes with it. */
public class BioProbe extends Applet {
    private OwnerBioTemplate made;

    private BioProbe() { register(); }
    public static void install(byte[] b, short o, byte l) { new BioProbe(); }

    public void process(APDU apdu) {
        if (selectingApplet()) return;
        byte[] buf = apdu.getBuffer();
        if (buf[ISO7816.OFFSET_CLA] != (byte) 0xB0) ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);
        if (buf[ISO7816.OFFSET_INS] != (byte) 0x10) ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        try {
            if (made == null) made = BioBuilder.buildBioTemplate(BioBuilder.FINGERPRINT, (byte) 5);
        } catch (BioException e) {
            ISOException.throwIt((short) (0x6B00 | (e.getReason() & 0xFF)));
        } catch (CardRuntimeException e) {
            ISOException.throwIt((short) (0x6F00 | (e.getReason() & 0xFF)));
        } catch (RuntimeException e) {
            ISOException.throwIt((short) 0x6F00);
        }
        buf[0] = 1;
        buf[1] = made.getBioType();
        buf[2] = made.isInitialized() ? (byte) 1 : (byte) 0;
        apdu.setOutgoingAndSend((short) 0, (short) 3);
    }
}
