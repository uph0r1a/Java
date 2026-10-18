package com.coffeeshop.coffeeshopmanagement.service;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

/**
 * Password hashing using PBKDF2WithHmacSHA256 (built into the JDK - no extra dependency
 * needed). Passwords are never stored or compared in plain text.
 *
 * Stored format: "<iterations>:<base64 salt>:<base64 hash>" so the iteration count can be
 * increased later without breaking verification of older stored hashes.
 */
public final class PasswordUtil {

    /**
     * 600,000 is the current OWASP recommendation for PBKDF2-HMAC-SHA256 (it was 120,000). Hashes made with fewer
     * iterations keep working and are re-hashed with this number the next time their owner logs in
     * ({@link #needsRehash}); the count is stored inside every hash.
     */
    static final int ITERATIONS = 600_000;
    private static final int KEY_LENGTH_BITS = 256;
    /** Older hashes (120,000 and below) must keep working; anything outside this range is treated as damaged. */
    static final int MIN_ACCEPTED_ITERATIONS = 1_000;
    static final int MAX_ACCEPTED_ITERATIONS = 5_000_000;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordUtil() {
    }

    public static String hash(String plainPassword) {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        byte[] hash = pbkdf2(plainPassword.toCharArray(), salt, ITERATIONS);
        return ITERATIONS + ":" + Base64.getEncoder().encodeToString(salt) + ":" +
                Base64.getEncoder().encodeToString(hash);
    }

    /** True when the stored hash was made with fewer iterations than today's (or is not in the expected format). */
    public static boolean needsRehash(String storedHash) {
        if (storedHash == null) return false;
        String[] parts = storedHash.split(":");
        if (parts.length != 3) return false;
        try {
            return Integer.parseInt(parts[0]) < ITERATIONS;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static boolean matches(String plainPassword, String storedHash) {
        if (storedHash == null || plainPassword == null) {
            return false;
        }
        String[] parts = storedHash.split(":");
        if (parts.length != 3) {
            return false;
        }
        try {
            int iterations = Integer.parseInt(parts[0]);
            // A damaged or hand-edited count must not keep a login busy for minutes (TODO 2A.13).
            if (iterations < MIN_ACCEPTED_ITERATIONS || iterations > MAX_ACCEPTED_ITERATIONS) {
                java.util.logging.Logger.getLogger(PasswordUtil.class.getName())
                        .warning("A stored password hash has an unusable iteration count (" + iterations + "); login refused");
                return false;
            }
            byte[] salt = Base64.getDecoder().decode(parts[1]);
            byte[] expected = Base64.getDecoder().decode(parts[2]);
            byte[] actual = pbkdf2(plainPassword.toCharArray(), salt, iterations);
            return constantTimeEquals(expected, actual);
        } catch (Exception e) {
            return false;
        }
    }

    private static byte[] pbkdf2(char[] password, byte[] salt, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, KEY_LENGTH_BITS);
            try {
                SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
                return factory.generateSecret(spec).getEncoded();
            } finally {
                spec.clearPassword(); // do not leave the password in memory longer than needed (TODO 2A.11)
                java.util.Arrays.fill(password, '\0');
            }
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Password hashing is not available on this JVM", e);
        }
    }

    private static boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a.length != b.length) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length; i++) {
            result |= a[i] ^ b[i];
        }
        return result == 0;
    }
}
