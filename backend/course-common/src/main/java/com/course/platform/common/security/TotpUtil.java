package com.course.platform.common.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Locale;

/**
 * RFC 6238 TOTP (HMAC-SHA1, 30s, 6 digits) + Base32 helpers.
 */
public final class TotpUtil {
    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final SecureRandom RANDOM = new SecureRandom();

    private TotpUtil() {}

    public static String generateSecret() {
        byte[] buf = new byte[20];
        RANDOM.nextBytes(buf);
        return encodeBase32(buf);
    }

    public static String generateCode(String base32Secret) {
        return generateCode(base32Secret, System.currentTimeMillis() / 1000L / 30L);
    }

    public static String generateCode(String base32Secret, long timeStep) {
        try {
            byte[] key = decodeBase32(base32Secret);
            byte[] data = ByteBuffer.allocate(8).putLong(timeStep).array();
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(data);
            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);
            int otp = binary % 1_000_000;
            return String.format(Locale.ROOT, "%06d", otp);
        } catch (Exception e) {
            throw new IllegalStateException("TOTP generation failed", e);
        }
    }

    public static boolean verify(String base32Secret, String code) {
        if (base32Secret == null || code == null || !code.matches("\\d{6}")) {
            return false;
        }
        long step = System.currentTimeMillis() / 1000L / 30L;
        for (long i = -1; i <= 1; i++) {
            if (generateCode(base32Secret, step + i).equals(code)) {
                return true;
            }
        }
        return false;
    }

    public static String otpAuthUrl(String issuer, String account, String secret) {
        String safeIssuer = urlEncode(issuer == null ? "CoursePlatform" : issuer);
        String safeAccount = urlEncode(account == null ? "user" : account);
        return "otpauth://totp/" + safeIssuer + ":" + safeAccount
                + "?secret=" + secret
                + "&issuer=" + safeIssuer
                + "&algorithm=SHA1&digits=6&period=30";
    }

    private static String urlEncode(String s) {
        return s.replace(" ", "%20");
    }

    public static String encodeBase32(byte[] data) {
        StringBuilder sb = new StringBuilder((data.length * 8 + 4) / 5);
        int buffer = 0;
        int bitsLeft = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                sb.append(BASE32.charAt((buffer >> (bitsLeft - 5)) & 0x1F));
                bitsLeft -= 5;
            }
        }
        if (bitsLeft > 0) {
            sb.append(BASE32.charAt((buffer << (5 - bitsLeft)) & 0x1F));
        }
        return sb.toString();
    }

    public static byte[] decodeBase32(String encoded) {
        String s = encoded.replace("=", "").replace(" ", "").toUpperCase(Locale.ROOT);
        int buffer = 0;
        int bitsLeft = 0;
        byte[] out = new byte[s.length() * 5 / 8];
        int index = 0;
        for (int i = 0; i < s.length(); i++) {
            int val = BASE32.indexOf(s.charAt(i));
            if (val < 0) {
                throw new IllegalArgumentException("Invalid base32");
            }
            buffer = (buffer << 5) | val;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                out[index++] = (byte) ((buffer >> (bitsLeft - 8)) & 0xFF);
                bitsLeft -= 8;
            }
        }
        if (index == out.length) {
            return out;
        }
        byte[] trimmed = new byte[index];
        System.arraycopy(out, 0, trimmed, 0, index);
        return trimmed;
    }
}
