package com.coffeeshop.coffeeshopmanagement.service;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.text.Normalizer;
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

    /**
     * A real hash, with today's iteration count, of a random password that was thrown away. Checked when a login names a user
     * that does not exist, so that case costs exactly as much as a wrong password for a real user, from the very first attempt
     * (TODO 2A.32: it used to be made on first use, which made the first unknown name twice as slow as the next ones).
     * {@code PasswordUtilTest} fails when {@link #ITERATIONS} is raised and this is not made again.
     */
    private static final String DUMMY_HASH = "600000:z484D+AOXBc0sN9UqXDeuw==:SB7pKIP0Qnaa+84BAbIaaCFb9F2pFFcdbUyWh2bbKpY=";
    /** Salt for the work that pads a check of an old hash up to today's cost; its value does not matter. */
    private static final byte[] PAD_SALT = "lunavera-pad-salt".getBytes(java.nio.charset.StandardCharsets.UTF_8);

    private PasswordUtil() {
    }

    /** The hash to check a password against when the user name is unknown (see {@link #DUMMY_HASH}). */
    public static String dummyHash() {
        return DUMMY_HASH;
    }

    public static String hash(String plainPassword) {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        byte[] hash = pbkdf2(nfc(plainPassword).toCharArray(), salt, ITERATIONS);
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
        return check(plainPassword, storedHash).ok();
    }

    /**
     * Like {@link #matches}, but a hash made with fewer iterations than today's (or a damaged one, which is refused at once) is
     * followed by the missing work, thrown away, so the answer takes as long as for a current hash (TODO 2A.32). Without it,
     * the quick answer for an account that has not logged in since the 600,000 upgrade (TODO 2.12) tells a stopwatch that the
     * name exists and which accounts are old. Use it for a login; {@link #matches} stays for everything else.
     */
    public static boolean matchesAtConstantCost(String plainPassword, String storedHash) {
        Check result = check(plainPassword, storedHash);
        int missing = paddingIterations(result.iterationsRun());
        if (missing > 0 && plainPassword != null) {
            pbkdf2(plainPassword.toCharArray(), PAD_SALT, missing);
        }
        return result.ok();
    }

    /** How many iterations still have to be run after {@code iterationsRun} to have spent today's cost (0 when already spent). */
    static int paddingIterations(int iterationsRun) {
        return iterationsRun < ITERATIONS ? ITERATIONS - Math.max(0, iterationsRun) : 0;
    }

    /** The answer, and how many PBKDF2 iterations were really run to get it (0 when the hash was refused without work). */
    record Check(boolean ok, int iterationsRun) {
    }

    static Check check(String plainPassword, String storedHash) {
        if (storedHash == null || plainPassword == null) {
            return new Check(false, 0);
        }
        String[] parts = storedHash.split(":");
        if (parts.length != 3) {
            return new Check(false, 0);
        }
        try {
            int iterations = Integer.parseInt(parts[0]);
            // A damaged or hand-edited count must not keep a login busy for minutes (TODO 2A.13).
            if (iterations < MIN_ACCEPTED_ITERATIONS || iterations > MAX_ACCEPTED_ITERATIONS) {
                java.util.logging.Logger.getLogger(PasswordUtil.class.getName())
                        .warning("A stored password hash has an unusable iteration count (" + iterations + "); login refused");
                return new Check(false, 0);
            }
            byte[] salt = Base64.getDecoder().decode(parts[1]);
            byte[] expected = Base64.getDecoder().decode(parts[2]);
            // The same letter can be one character or a base letter plus a mark depending on the keyboard; hashes are made from the
            // composed form (TODO 3.57), so both spellings of a password match.
            String composed = nfc(plainPassword);
            byte[] actual = pbkdf2(composed.toCharArray(), salt, iterations);
            // The JDK's own constant-time comparison (TODO 6.48), not a hand-written loop; different lengths are just "not equal".
            boolean ok = MessageDigest.isEqual(expected, actual);
            if (!ok && !composed.equals(plainPassword)) {
                // A hash made before this change from a password typed in the decomposed form must keep working.
                ok = MessageDigest.isEqual(expected, pbkdf2(plainPassword.toCharArray(), salt, iterations));
            }
            return new Check(ok, iterations);
        } catch (Exception e) {
            return new Check(false, 0);
        }
    }

    private static String nfc(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFC);
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
}
