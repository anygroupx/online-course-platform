package com.course.platform.infra.http;

import java.io.ByteArrayOutputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Minimal RFC 8484 DNS wire-format codec. It builds a single-question A/AAAA query and
 * extracts only the address records that answer it. Names are decoded with compression
 * pointers under an explicit jump budget, and addresses are rebuilt from raw bytes rather
 * than re-parsed as text, so no second resolver can be reached from untrusted input.
 */
final class DohMessageCodec {
    static final int TYPE_A = 1;
    static final int TYPE_AAAA = 28;

    private static final int CLASS_IN = 1;
    private static final int TYPE_OPT = 41;
    private static final int FLAG_QR = 0x8000;
    private static final int FLAG_TC = 0x0200;
    private static final int FLAG_RD = 0x0100;
    private static final int RCODE_MASK = 0x000F;
    private static final int EDNS0_PAYLOAD_SIZE = 4096;
    private static final int HEADER_BYTES = 12;
    private static final int ANSWER_FIXED_BYTES = 10;
    private static final int MAX_NAME_JUMPS = 8;
    private static final int MAX_LABEL_LENGTH = 63;
    private static final int MAX_NAME_LENGTH = 253;
    private static final Pattern ASCII_NAME = Pattern.compile("[A-Za-z0-9._-]+");

    private DohMessageCodec() {
    }

    static byte[] encodeQuery(String host, int type) {
        String name = requireQueryName(host);
        if (type != TYPE_A && type != TYPE_AAAA) {
            throw new IllegalArgumentException("Unsupported DNS query type");
        }
        ByteArrayOutputStream message = new ByteArrayOutputStream(64);
        writeShort(message, 0);        // ID 0 keeps responses cacheable (RFC 8484 section 4.1).
        writeShort(message, FLAG_RD);
        writeShort(message, 1);        // QDCOUNT
        writeShort(message, 0);        // ANCOUNT
        writeShort(message, 0);        // NSCOUNT
        writeShort(message, 1);        // ARCOUNT: one EDNS0 OPT record.
        for (String label : name.split("\\.", -1)) {
            byte[] bytes = label.getBytes(StandardCharsets.US_ASCII);
            if (bytes.length == 0 || bytes.length > MAX_LABEL_LENGTH) {
                throw new IllegalArgumentException("Invalid DNS query label");
            }
            message.write(bytes.length);
            message.writeBytes(bytes);
        }
        message.write(0);              // Root label terminates QNAME.
        writeShort(message, type);
        writeShort(message, CLASS_IN);
        message.write(0);              // OPT owner name is the root label.
        writeShort(message, TYPE_OPT);
        writeShort(message, EDNS0_PAYLOAD_SIZE);
        message.writeBytes(new byte[4]); // Extended RCODE, EDNS version and flags.
        writeShort(message, 0);        // Empty RDATA.
        return message.toByteArray();
    }

    static List<InetAddress> decodeAnswers(byte[] message, String host, int type) throws UnknownHostException {
        if (message == null || message.length < HEADER_BYTES) throw failed();
        int flags = readShort(message, 2);
        if ((flags & FLAG_QR) == 0) throw failed();               // Not a response.
        if ((flags & FLAG_TC) != 0) throw failed();               // Truncated: never trust a partial set.
        if ((flags & RCODE_MASK) != 0) throw failed();            // Only NOERROR carries usable answers.
        if (readShort(message, 4) != 1) throw failed();           // Exactly the one question we asked.

        int[] cursor = {HEADER_BYTES};
        String questionName = readName(message, cursor);
        if (cursor[0] + 4 > message.length) throw failed();
        int questionType = readShort(message, cursor[0]);
        cursor[0] += 4;                                           // TYPE + CLASS
        if (!host.equalsIgnoreCase(stripTrailingDot(questionName)) || questionType != type) throw failed();

        int answerCount = readShort(message, 6);
        List<InetAddress> addresses = new ArrayList<>();
        for (int index = 0; index < answerCount; index++) {
            readName(message, cursor);                            // Owner name; CNAME owners are skipped by type.
            if (cursor[0] + ANSWER_FIXED_BYTES > message.length) throw failed();
            int answerType = readShort(message, cursor[0]);
            cursor[0] += 2;                                       // TYPE
            cursor[0] += 2;                                       // CLASS
            cursor[0] += 4;                                       // TTL
            int length = readShort(message, cursor[0]);
            cursor[0] += 2;
            if (cursor[0] + length > message.length) throw failed();
            if (answerType == type) {
                addresses.add(literal(message, cursor[0], length, type));
            }
            cursor[0] += length;
        }
        return addresses;
    }

    private static InetAddress literal(byte[] message, int offset, int length, int type)
            throws UnknownHostException {
        int expected = type == TYPE_A ? 4 : 16;
        if (length != expected) throw failed();
        byte[] raw = new byte[expected];
        System.arraycopy(message, offset, raw, 0, expected);
        return InetAddress.getByAddress(raw);
    }

    private static String readName(byte[] message, int[] cursor) throws UnknownHostException {
        StringBuilder name = new StringBuilder();
        int position = cursor[0];
        int resume = -1;
        int jumps = 0;
        while (true) {
            if (position < 0 || position >= message.length) throw failed();
            int length = message[position] & 0xFF;
            if (length == 0) {
                position++;
                break;
            }
            if ((length & 0xC0) == 0xC0) {
                if (position + 1 >= message.length) throw failed();
                int pointer = ((length & 0x3F) << 8) | (message[position + 1] & 0xFF);
                if (resume < 0) resume = position + 2;
                // Pointers must strictly move backwards, so a self-reference cannot loop forever.
                if (++jumps > MAX_NAME_JUMPS || pointer >= position) throw failed();
                position = pointer;
                continue;
            }
            if ((length & 0xC0) != 0) throw failed();             // Reserved label encoding.
            position++;
            if (position + length > message.length) throw failed();
            if (name.length() + length + 1 > MAX_NAME_LENGTH) throw failed();
            if (!name.isEmpty()) name.append('.');
            name.append(new String(message, position, length, StandardCharsets.US_ASCII));
            position += length;
        }
        cursor[0] = resume >= 0 ? resume : position;
        return name.toString();
    }

    private static String requireQueryName(String host) {
        String name = stripTrailingDot(host == null ? "" : host.trim());
        if (name.isEmpty() || name.length() > MAX_NAME_LENGTH || !ASCII_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid DNS query name");
        }
        return name;
    }

    private static String stripTrailingDot(String value) {
        return value != null && value.endsWith(".") ? value.substring(0, value.length() - 1) : value;
    }

    private static int readShort(byte[] message, int offset) {
        return ((message[offset] & 0xFF) << 8) | (message[offset + 1] & 0xFF);
    }

    private static void writeShort(ByteArrayOutputStream message, int value) {
        message.write((value >> 8) & 0xFF);
        message.write(value & 0xFF);
    }

    private static UnknownHostException failed() {
        return new UnknownHostException("Trusted DNS response rejected");
    }

    static int maxAddressLength() {
        return MAX_ADDRESS_LENGTH;
    }

    private static final int MAX_ADDRESS_LENGTH = 16;
}
