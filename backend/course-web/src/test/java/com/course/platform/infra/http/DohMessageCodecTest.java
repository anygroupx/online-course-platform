package com.course.platform.infra.http;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DohMessageCodecTest {

    /** Builds a response that mirrors what a public DoH provider returns for one A question. */
    private byte[] response(String questionName, int questionType, String... answers) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        short16(out, 0);
        short16(out, 0x8180);
        short16(out, 1);
        short16(out, answers.length);
        short16(out, 0);
        short16(out, 0);
        name(out, questionName);
        short16(out, questionType);
        short16(out, 1);
        for (String answer : answers) {
            name(out, questionName);
            short16(out, questionType);
            short16(out, 1);
            int32(out, 300);
            byte[] raw = literalBytes(answer, questionType);
            short16(out, raw.length);
            out.writeBytes(raw);
        }
        return out.toByteArray();
    }

    private byte[] literalBytes(String literal, int type) {
        try {
            return InetAddress.getByName(literal).getAddress();
        } catch (UnknownHostException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private void name(ByteArrayOutputStream out, String value) {
        for (String label : value.split("\\.")) {
            byte[] bytes = label.getBytes(StandardCharsets.US_ASCII);
            out.write(bytes.length);
            out.writeBytes(bytes);
        }
        out.write(0);
    }

    private void short16(ByteArrayOutputStream out, int value) {
        out.write((value >> 8) & 0xFF);
        out.write(value & 0xFF);
    }

    private void int32(ByteArrayOutputStream out, int value) {
        out.write((value >> 24) & 0xFF);
        out.write((value >> 16) & 0xFF);
        out.write((value >> 8) & 0xFF);
        out.write(value & 0xFF);
    }

    @Test
    void encodesSingleQuestionQueryWithEdns() throws Exception {
        byte[] query = DohMessageCodec.encodeQuery("api.example", DohMessageCodec.TYPE_A);

        assertEquals(0, ((query[0] & 0xFF) << 8) | (query[1] & 0xFF)); // ID 0 keeps responses cacheable.
        assertEquals(1, ((query[4] & 0xFF) << 8) | (query[5] & 0xFF)); // QDCOUNT
        assertEquals(1, ((query[10] & 0xFF) << 8) | (query[11] & 0xFF)); // ARCOUNT: EDNS0 OPT
        assertTrue(query.length > 12);
        assertEquals(1, DohMessageCodec.decodeAnswers(response("api.example", 1, "1.2.3.4"),
                "api.example", 1).size());
    }

    @Test
    void decodesIpv4AndIpv6Answers() throws Exception {
        List<InetAddress> v4 = DohMessageCodec.decodeAnswers(
                response("api.example", 1, "8.8.8.8", "8.8.4.4"), "api.example", 1);
        assertEquals(2, v4.size());
        assertEquals("8.8.8.8", v4.get(0).getHostAddress());
        assertEquals("8.8.4.4", v4.get(1).getHostAddress());

        List<InetAddress> v6 = DohMessageCodec.decodeAnswers(
                response("api.example", 28, "2001:4860:4860::8888"), "api.example", 28);
        assertEquals(1, v6.size());
        assertEquals(16, v6.get(0).getAddress().length);
    }

    @Test
    void rejectsMismatchedQuestionTypeAndName() {
        assertThrows(UnknownHostException.class, () -> DohMessageCodec.decodeAnswers(
                response("other.example", 1, "1.2.3.4"), "api.example", 1));
        assertThrows(UnknownHostException.class, () -> DohMessageCodec.decodeAnswers(
                response("api.example", 28, "2001:4860:4860::8888"), "api.example", 1));
    }

    @Test
    void rejectsNonNoerrorAndTruncatedResponses() {
        byte[] nxdomain = response("api.example", 1, "1.2.3.4");
        nxdomain[3] = (byte) 0x83; // RCODE=3 NXDOMAIN
        assertThrows(UnknownHostException.class,
                () -> DohMessageCodec.decodeAnswers(nxdomain, "api.example", 1));

        byte[] truncated = response("api.example", 1, "1.2.3.4");
        truncated[2] = (byte) 0x82; // TC=1
        assertThrows(UnknownHostException.class,
                () -> DohMessageCodec.decodeAnswers(truncated, "api.example", 1));

        byte[] notAResponse = response("api.example", 1, "1.2.3.4");
        notAResponse[2] = 0x01; // QR=0
        assertThrows(UnknownHostException.class,
                () -> DohMessageCodec.decodeAnswers(notAResponse, "api.example", 1));
    }

    @Test
    void rejectsCompressionPointerThatDoesNotMoveBackwards() {
        byte[] loop = response("api.example", 1, "1.2.3.4");
        // Point the question name at itself; a naive decoder would spin forever.
        loop[12] = (byte) 0xC0;
        loop[13] = 12;
        assertThrows(UnknownHostException.class,
                () -> DohMessageCodec.decodeAnswers(loop, "api.example", 1));
    }

    @Test
    void rejectsMalformedLengthsAndEmptyInput() {
        assertThrows(UnknownHostException.class,
                () -> DohMessageCodec.decodeAnswers(new byte[4], "api.example", 1));

        byte[] complete = response("api.example", 1, "1.2.3.4");
        // Truncate mid-answer so the declared RDLENGTH runs past the end of the buffer.
        byte[] truncatedMidAnswer = java.util.Arrays.copyOf(complete, complete.length - 3);
        assertThrows(UnknownHostException.class,
                () -> DohMessageCodec.decodeAnswers(truncatedMidAnswer, "api.example", 1));

        // An answer whose RDLENGTH disagrees with the record type must be rejected.
        byte[] wrongLength = response("api.example", 1, "1.2.3.4");
        wrongLength[wrongLength.length - 6] = 0;
        wrongLength[wrongLength.length - 5] = 16;
        assertThrows(UnknownHostException.class,
                () -> DohMessageCodec.decodeAnswers(wrongLength, "api.example", 1));
    }

    @Test
    void rejectsInvalidQueryNames() {
        assertThrows(IllegalArgumentException.class,
                () -> DohMessageCodec.encodeQuery("", DohMessageCodec.TYPE_A));
        assertThrows(IllegalArgumentException.class,
                () -> DohMessageCodec.encodeQuery("bad name.example", DohMessageCodec.TYPE_A));
        assertThrows(IllegalArgumentException.class,
                () -> DohMessageCodec.encodeQuery("api.example", 99));
    }
}
