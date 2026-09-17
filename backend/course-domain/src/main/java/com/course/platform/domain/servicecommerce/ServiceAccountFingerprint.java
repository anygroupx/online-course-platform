package com.course.platform.domain.servicecommerce;

import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

/** Private, salted identity binding; neither an account name nor a reusable credential. */
public record ServiceAccountFingerprint(String salt, String digest, String contextDigest) {
    private static final SecureRandom RANDOM = new SecureRandom();

    public ServiceAccountFingerprint {
        if (salt == null || !salt.matches("[a-f0-9]{32}")
                || digest == null || !digest.matches("[a-f0-9]{64}")
                || contextDigest != null && !contextDigest.matches("[a-f0-9]{64}"))
            throw new IllegalArgumentException("Invalid account binding");
    }

    public ServiceAccountFingerprint(String salt, String digest) {
        this(salt, digest, null);
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

    public static ServiceAccountFingerprint createScoped(String scope, String... values) {
        return create(scopedIdentity(scope, values));
    }

    public boolean matchesScoped(String scope, String... values) {
        return matches(scopedIdentity(scope, values));
    }

    public ServiceAccountFingerprint withContext(String scope, String... values) {
        return new ServiceAccountFingerprint(salt, digest, hash(salt, scopedIdentity(scope, values)));
    }

    public boolean matchesContext(String scope, String... values) {
        return contextDigest != null && MessageDigest.isEqual(HexFormat.of().parseHex(contextDigest),
                HexFormat.of().parseHex(hash(salt, scopedIdentity(scope, values))));
    }

    // Length-prefixed UTF-8 fields preserve password spaces and prevent tuple-boundary collisions.
    // Only the salted digest is persisted, never a school/account/password tuple.
    private static String scopedIdentity(String scope, String... values) {
        if (scope == null || !scope.matches("[a-z0-9:-]{1,32}")
                || values == null || values.length < 1 || values.length > 16)
            throw new IllegalArgumentException("Invalid identity scope");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String value : values) {
                if (value == null || value.isBlank() || value.length() > 2048)
                    throw new IllegalArgumentException("Invalid scoped identity");
                byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
                digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
                digest.update(bytes);
            }
            return scope + ":" + HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable");
        }
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
