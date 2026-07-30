package com.course.platform.common.security;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 本地信封加密（AES-GCM）。生产应替换为 KMS/Vault。
 * 密文格式：ENC:v1:<base64(iv+ciphertext+tag)>
 */
public final class SecretCrypto {
    private static final String PREFIX = "ENC:v1:";
    private static final SecureRandom RANDOM = new SecureRandom();

    private SecretCrypto() {}

    public static boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    public static String encrypt(String plain, String masterKey) {
        if (plain == null || plain.isBlank() || isEncrypted(plain)) {
            return plain;
        }
        try {
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec(masterKey), new GCMParameterSpec(128, iv));
            byte[] cipherText = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + cipherText.length);
            buffer.put(iv);
            buffer.put(cipherText);
            return PREFIX + Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception e) {
            throw new IllegalStateException("encrypt failed", e);
        }
    }

    public static String decrypt(String value, String masterKey) {
        if (value == null || value.isBlank() || !isEncrypted(value)) {
            return value;
        }
        try {
            byte[] all = Base64.getDecoder().decode(value.substring(PREFIX.length()));
            byte[] iv = new byte[12];
            byte[] cipherText = new byte[all.length - 12];
            System.arraycopy(all, 0, iv, 0, 12);
            System.arraycopy(all, 12, cipherText, 0, cipherText.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec(masterKey), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("decrypt failed", e);
        }
    }

    private static SecretKeySpec keySpec(String masterKey) throws Exception {
        if (masterKey == null || masterKey.isBlank()) {
            throw new IllegalStateException("SECRET_ENCRYPT_KEY is required");
        }
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] key = digest.digest(masterKey.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(key, "AES");
    }
}
