package com.course.platform.common.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Token / API Key 哈希与随机串工具
 */
public final class TokenHashUtil {
    private static final SecureRandom RANDOM = new SecureRandom();

    private TokenHashUtil() {}

    public static String sha256(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public static String randomHex(int bytes) {
        byte[] buf = new byte[bytes];
        RANDOM.nextBytes(buf);
        return HexFormat.of().formatHex(buf);
    }

    public static String randomApiKey() {
        // oc_live_<32hex>
        return "oc_live_" + randomHex(16);
    }

    public static String apiKeyPrefix(String apiKey) {
        if (apiKey == null || apiKey.length() < 12) {
            return "oc_****";
        }
        return apiKey.substring(0, Math.min(12, apiKey.length())) + "****";
    }
}
