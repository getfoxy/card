package me.flashapp.cashu;

import com.licel.jcardsim.smartcardio.CardSimulator;
import com.licel.jcardsim.utils.AIDUtil;

import javax.smartcardio.CommandAPDU;
import javax.smartcardio.ResponseAPDU;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * One card, on a port of this machine: the applet itself, run in the JavaCard
 * simulator the tests use, for a phone that is itself simulated.
 *
 * The iOS Simulator has no NFC. Foxy's simulator build reaches a card over
 * loopback instead (Foxy/Flashcard/CardLink.swift, in the app's repository),
 * and this is the card it reaches: the same class that is built into the CAP
 * file, answering the same commands. Run it with tools/cardsim/run.sh.
 *
 * Not a test: it has no assertions and surefire does not run it.
 *
 * The talk is lines of text, one answer for each line:
 *
 *   tap            the phone has found the card      ok | none
 *   apdu HEX       one command                       HEX (answer, status last) | gone
 *   end            the phone has let go              ok
 *   ctl off        take the card off the reader      ok
 *   ctl on         put it back                       ok
 *   ctl pull N     let N more commands through, then take it away
 *   ctl new        a card out of its packet in its place
 *   ctl show       what the card holds               pieces, places, balance
 *
 * A tap is a power-up: the card forgets that its PIN was checked, as a real
 * one does when it leaves the field. What it holds lasts as long as this
 * process does.
 *
 * It prints each command's instruction and status, and never its data: one
 * of the commands carries the card's PIN.
 */
public final class CardServer {
    static final String APPLET = "F0464F58594341524401";

    private CardSimulator card = fresh();
    private boolean present = true;
    private int pullAfter = -1;

    private static CardSimulator fresh() {
        CardSimulator sim = new CardSimulator();
        sim.installApplet(AIDUtil.create(APPLET), CashuApplet.class);
        return sim;
    }

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 47431;
        CardServer server = new CardServer();
        try (ServerSocket listening = new ServerSocket(port, 8, InetAddress.getLoopbackAddress())) {
            System.out.println("a card is on 127.0.0.1:" + port + " (ctl off | on | pull N | new | show)");
            while (true) {
                try (Socket phone = listening.accept()) {
                    server.serve(phone);
                } catch (Exception e) {
                    System.out.println("  the phone went: " + e.getMessage());
                }
            }
        }
    }

    private void serve(Socket phone) throws Exception {
        BufferedReader in = new BufferedReader(new InputStreamReader(phone.getInputStream(), StandardCharsets.US_ASCII));
        OutputStream out = phone.getOutputStream();
        String line;
        while ((line = in.readLine()) != null) {
            String answer = answer(line.trim());
            out.write((answer + "\n").getBytes(StandardCharsets.US_ASCII));
            out.flush();
        }
    }

    private String answer(String line) {
        if (line.equals("tap")) {
            if (!present) return "none";
            card.reset();
            System.out.println("tap");
            return "ok";
        }
        if (line.equals("end")) {
            System.out.println("end");
            return "ok";
        }
        if (line.startsWith("apdu ")) {
            if (!present) return "gone";
            if (pullAfter == 0) {
                pullAfter = -1;
                present = false;
                System.out.println("  pulled away");
                return "gone";
            }
            if (pullAfter > 0) pullAfter -= 1;
            byte[] command = bytes(line.substring(5).trim());
            if (command == null || command.length < 4) return "6700";
            ResponseAPDU said = card.transmitCommand(new CommandAPDU(command));
            System.out.println(String.format("  %02x %02x -> %04x%s", command[0] & 0xff, command[1] & 0xff, said.getSW(),
                    said.getData().length > 0 ? " (" + said.getData().length + " bytes)" : ""));
            return hex(said.getBytes());
        }
        if (line.startsWith("ctl ")) {
            String[] words = line.substring(4).trim().split("\\s+");
            switch (words[0]) {
                case "off": present = false; break;
                case "on": present = true; pullAfter = -1; break;
                case "pull": pullAfter = words.length > 1 ? Integer.parseInt(words[1]) : 0; present = true; break;
                case "new": card = fresh(); present = true; pullAfter = -1; break;
                case "show": return show();
                default: return "what";
            }
            System.out.println("ctl " + String.join(" ", words));
            return "ok";
        }
        return "what";
    }

    /** What the card says of itself with no PIN: GET_INFO and GET_BALANCE. */
    private String show() {
        card.reset();
        ResponseAPDU chosen = card.transmitCommand(new CommandAPDU(0x00, 0xA4, 0x04, 0x00, bytes(APPLET)));
        if (chosen.getSW() != 0x9000) return "not chosen " + Integer.toHexString(chosen.getSW());
        byte[] info = card.transmitCommand(new CommandAPDU(0xB0, 0x01, 0, 0, 256)).getData();
        byte[] bal = card.transmitCommand(new CommandAPDU(0xB0, 0x11, 0, 0, 4)).getData();
        long sats = 0;
        for (byte b : bal) sats = (sats << 8) | (b & 0xff);
        return "unspent " + (info[3] & 0xff) + ", spent " + (info[4] & 0xff) + ", empty " + (info[5] & 0xff)
                + ", pin " + (info[7] & 0xff) + ", balance " + sats;
    }

    private static byte[] bytes(String hex) {
        if (hex.length() % 2 != 0 || !hex.matches("[0-9a-fA-F]*")) return null;
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        return out;
    }

    private static String hex(byte[] data) {
        StringBuilder out = new StringBuilder();
        for (byte b : data) out.append(String.format("%02x", b & 0xff));
        return out.toString();
    }
}
