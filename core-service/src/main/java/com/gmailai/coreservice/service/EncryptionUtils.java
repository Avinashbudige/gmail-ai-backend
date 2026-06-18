package com.gmailai.coreservice.service;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class EncryptionUtils {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 16; // in bytes (128 bits)

    // Empty default — system MUST fail at startup if this is not set explicitly.
    // A missing key is caught by @PostConstruct below, not silently defaulted.
    @Value("${gmail.encryption-key:}")
    private String secretKey;

    /**
     * Validates the encryption key at application startup.
     * Fails fast rather than silently using a missing/weak key in production.
     */
    @PostConstruct
    public void validateEncryptionKey() {
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException(
                "[Security] 'gmail.encryption-key' is not configured. " +
                "Set the GMAIL_ENCRYPTION_KEY environment variable to a random 32-character secret. " +
                "Without this, OAuth refresh tokens cannot be encrypted and the service will not start."
            );
        }
        if (secretKey.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                "[Security] 'gmail.encryption-key' must be at least 32 bytes for AES-256. " +
                "Current key is too short (" + secretKey.length() + " chars). " +
                "Generate a secure key with: openssl rand -hex 16"
            );
        }
    }

    public String encrypt(String plainText) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "AES");
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);

            cipher.init(Cipher.ENCRYPT_MODE, keySpec, parameterSpec);
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            byte[] encrypted = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, encrypted, 0, iv.length);
            System.arraycopy(cipherText, 0, encrypted, iv.length, cipherText.length);

            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new RuntimeException("Failed to encrypt token", e);
        }
    }

    public String decrypt(String cipherText) {
        try {
            byte[] decoded = Base64.getDecoder().decode(cipherText);
            byte[] iv = new byte[GCM_IV_LENGTH];
            System.arraycopy(decoded, 0, iv, 0, iv.length);

            byte[] encryptedData = new byte[decoded.length - GCM_IV_LENGTH];
            System.arraycopy(decoded, iv.length, encryptedData, 0, encryptedData.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "AES");
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);

            cipher.init(Cipher.DECRYPT_MODE, keySpec, parameterSpec);
            byte[] decryptedText = cipher.doFinal(encryptedData);

            return new String(decryptedText, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to decrypt token", e);
        }
    }
}