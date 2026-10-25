package com.coffeeshop.coffeeshopmanagement.service;

import org.junit.Test;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.util.Base64;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class PasswordUtilTest {

    @Test
    public void aHashWithAnAbsurdIterationCountIsRefusedQuickly() {
        long start = System.nanoTime();
        assertFalse(PasswordUtil.matches("x", "99999999:AAAAAAAAAAAAAAAAAAAAAA==:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="));
        assertFalse(PasswordUtil.matches("x", "0:AAAAAAAAAAAAAAAAAAAAAA==:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="));
        assertTrue("refused without doing the work", (System.nanoTime() - start) / 1_000_000 < 2_000);
    }

    @Test
    public void correctPasswordMatchesItsOwnHash() {
        String hash = PasswordUtil.hash("Correct1");
        assertTrue(PasswordUtil.matches("Correct1", hash));
    }

    @Test
    public void wrongPasswordDoesNotMatch() {
        String hash = PasswordUtil.hash("Correct1");
        assertFalse(PasswordUtil.matches("Wrong1234", hash));
    }

    @Test
    public void hashIsNeverThePlainPassword() {
        // The whole point of hashing: what's stored must not just be the password itself.
        String hash = PasswordUtil.hash("Correct1");
        assertNotEquals("Correct1", hash);
        assertFalse(hash.contains("Correct1"));
    }

    @Test
    public void sameInputProducesADifferentHashEachTime() {
        // A random salt per call - two users with the same password must not have the same
        // stored hash (that would leak "these two accounts share a password").
        assertNotEquals(PasswordUtil.hash("Correct1"), PasswordUtil.hash("Correct1"));
    }

    @Test
    public void malformedStoredHashFailsClosedRatherThanThrowing() {
        // A corrupted/truncated DB value must be treated as "doesn't match", never as an
        // exception that could be mishandled into an accidental login.
        assertFalse(PasswordUtil.matches("Correct1", "not-a-real-hash"));
        assertFalse(PasswordUtil.matches("Correct1", ""));
        assertFalse(PasswordUtil.matches("Correct1", null));
        assertFalse(PasswordUtil.matches(null, PasswordUtil.hash("Correct1")));
    }

    // ------------------------------------------------ TODO 2A.32: an unknown name and an old hash must cost what a current hash costs

    /** A stored hash in the real format, made with a given (small) iteration count: what an old install has. */
    private static String hashWith(String password, int iterations) throws Exception {
        byte[] salt = new byte[16];
        new java.security.SecureRandom().nextBytes(salt);
        byte[] hash = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(new PBEKeySpec(password.toCharArray(), salt, iterations, 256)).getEncoded();
        return iterations + ":" + Base64.getEncoder().encodeToString(salt) + ":" + Base64.getEncoder().encodeToString(hash);
    }

    @Test
    public void theDummyHashIsAValidHashWithTodaysCostThatNoTypedPasswordMatches() {
        String dummy = PasswordUtil.dummyHash();
        String[] parts = dummy.split(":");
        assertEquals(3, parts.length);
        assertEquals("raise DUMMY_HASH together with ITERATIONS", PasswordUtil.ITERATIONS, Integer.parseInt(parts[0]));
        assertFalse(PasswordUtil.needsRehash(dummy));
        assertEquals(16, Base64.getDecoder().decode(parts[1]).length);
        assertEquals(32, Base64.getDecoder().decode(parts[2]).length);
        assertFalse(PasswordUtil.matches("lunavera-not-a-real-password", dummy)); // the old throwaway password
        assertFalse(PasswordUtil.matches("", dummy));
    }

    @Test
    public void theDummyHashIsReadyAtOnceAndAlwaysTheSame() {
        long start = System.nanoTime();
        String first = PasswordUtil.dummyHash();
        String second = PasswordUtil.dummyHash();
        assertTrue("no work done to get it", (System.nanoTime() - start) / 1_000_000 < 50);
        assertEquals(first, second);
    }

    @Test
    public void whatIsMissingFromAnOldHashIsPaddedUpToTodaysCost() {
        assertEquals(PasswordUtil.ITERATIONS, PasswordUtil.paddingIterations(0));
        assertEquals(PasswordUtil.ITERATIONS, PasswordUtil.paddingIterations(-5));
        assertEquals(PasswordUtil.ITERATIONS - 120_000, PasswordUtil.paddingIterations(120_000));
        assertEquals(1, PasswordUtil.paddingIterations(PasswordUtil.ITERATIONS - 1));
        assertEquals(0, PasswordUtil.paddingIterations(PasswordUtil.ITERATIONS));
        assertEquals("a dearer hash cannot be made cheaper", 0, PasswordUtil.paddingIterations(PasswordUtil.MAX_ACCEPTED_ITERATIONS));
    }

    @Test
    public void checkReportsHowManyIterationsWereReallyRun() throws Exception {
        String old = hashWith("Correct1", 1_000);
        assertEquals(new PasswordUtil.Check(true, 1_000), PasswordUtil.check("Correct1", old));
        assertEquals(new PasswordUtil.Check(false, 1_000), PasswordUtil.check("Wrong123", old));
        assertEquals("a refused hash does no work", new PasswordUtil.Check(false, 0), PasswordUtil.check("x", "abc"));
        assertEquals(new PasswordUtil.Check(false, 0), PasswordUtil.check("x", null));
        assertEquals(new PasswordUtil.Check(false, 0), PasswordUtil.check(null, old));
        assertEquals(new PasswordUtil.Check(false, 0),
                PasswordUtil.check("x", "999:AAAAAAAAAAAAAAAAAAAAAA==:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="));
        assertEquals(new PasswordUtil.Check(false, 0), PasswordUtil.check("x", "1000:***:***"));
    }

    @Test
    public void theConstantCostCheckGivesTheSameAnswersAsThePlainOne() throws Exception {
        String old = hashWith("Correct1", 1_000);
        assertTrue(PasswordUtil.matchesAtConstantCost("Correct1", old));
        assertFalse(PasswordUtil.matchesAtConstantCost("Wrong123", old));
        assertFalse(PasswordUtil.matchesAtConstantCost("x", "abc"));
        assertFalse(PasswordUtil.matchesAtConstantCost("x", null));
        assertFalse("no password to pad with, and no error", PasswordUtil.matchesAtConstantCost(null, old));
        String current = PasswordUtil.hash("Correct1");
        assertTrue(PasswordUtil.matchesAtConstantCost("Correct1", current));
    }

    /** TODO 6.48: the comparison is the JDK's; a stored digest of another length is simply "not equal", not an error. */
    @Test
    public void aStoredDigestOfTheWrongLengthIsRefused() {
        String hash = PasswordUtil.hash("Correct1");
        String[] parts = hash.split(":");
        byte[] digest = Base64.getDecoder().decode(parts[2]);
        String shorter = parts[0] + ":" + parts[1] + ":" + Base64.getEncoder().encodeToString(java.util.Arrays.copyOf(digest, digest.length - 1));
        String longer = parts[0] + ":" + parts[1] + ":" + Base64.getEncoder().encodeToString(java.util.Arrays.copyOf(digest, digest.length + 1));
        assertTrue(PasswordUtil.matches("Correct1", hash));
        assertFalse(PasswordUtil.matches("Correct1", shorter));
        assertFalse(PasswordUtil.matches("Correct1", longer));
    }

    /** TODO 3.57: the composed and the decomposed spelling of an accented password are the same password. */
    @Test
    public void accentedPasswordMatchesInBothUnicodeForms() {
        String composed = java.text.Normalizer.normalize("mậtkhẩu1", java.text.Normalizer.Form.NFC);
        String decomposed = java.text.Normalizer.normalize("mậtkhẩu1", java.text.Normalizer.Form.NFD);
        assertNotEquals(composed, decomposed);
        String hash = PasswordUtil.hash(decomposed);
        assertTrue(PasswordUtil.matches(composed, hash));
        assertTrue(PasswordUtil.matches(decomposed, hash));
        assertTrue(PasswordUtil.matches(decomposed, PasswordUtil.hash(composed)));
        assertFalse(PasswordUtil.matches("matkhau1", hash));
    }

    /** A hash made before the change, from a password typed in the decomposed form, still opens. */
    @Test
    public void aHashOfTheRawDecomposedTextStillWorks() throws Exception {
        String decomposed = java.text.Normalizer.normalize("mậtkhẩu1", java.text.Normalizer.Form.NFD);
        byte[] salt = new byte[16];
        PBEKeySpec spec = new PBEKeySpec(decomposed.toCharArray(), salt, 1_000, 256);
        byte[] raw = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        String old = "1000:" + Base64.getEncoder().encodeToString(salt) + ":" + Base64.getEncoder().encodeToString(raw);
        assertTrue(PasswordUtil.matches(decomposed, old));
        assertFalse(PasswordUtil.matches("wrong", old));
    }
}
