package com.course.platform.domain.servicecommerce;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

/** Private, salted identity binding; neither an account name nor a reusable credential. */
public record ServiceAccountFingerprint(String salt, String digest) {
    private static final SecureRandom RANDOM = new SecureRandom();

    public ServiceAccountFingerprint {
        if (salt == null || !salt.matches("[a-f0-9]{32}")
                || digest == null || !digest.matches("[a-f0-9]{64}"))
            throw new IllegalArgumentException("Invalid account binding");
    }

    public static ServiceAccountFingerprint create(String account) {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        String salt = HexFormat.of().formatHex(bytes);
        return new ServiceAccountFingerprint(salt, hash(salt, account));
    }

    public boolean matches(String account) {
        return account != null && MessageDigest.isEqual(
                HexFormat.of().parseHex(digest), HexFormat.of().parseHex(hash(salt, account)));
    }

    private static String hash(String salt, String account) {
        if (account == null || account.isBlank() || account.length() > 100)
            throw new IllegalArgumentException("Invalid account binding");
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((salt + ":" + account).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable");
        }
    }

    @Override
    public String toString() { return "ServiceAccountFingerprint[REDACTED]"; }
}
