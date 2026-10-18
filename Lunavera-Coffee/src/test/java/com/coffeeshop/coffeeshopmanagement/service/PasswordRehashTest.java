package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.dao.UserDAO;
import com.coffeeshop.coffeeshopmanagement.model.AccountStatus;
import com.coffeeshop.coffeeshopmanagement.model.User;
import com.coffeeshop.coffeeshopmanagement.service.AuthenticationService.LoginStatus;
import org.junit.Before;
import org.junit.Test;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.util.Base64;
import java.util.Optional;

import static org.junit.Assert.*;

/** TODO 2.12: stronger hashing, and old hashes are upgraded the next time their owner logs in. */
public class PasswordRehashTest {

    private User user;
    private String savedHash;
    private int saves;
    private AuthenticationService service;

    /** A hash exactly like the ones the app wrote before: 120,000 iterations. */
    private static String oldStyleHash(String password) throws Exception {
        byte[] salt = new byte[16];
        new java.security.SecureRandom().nextBytes(salt);
        byte[] hash = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(new PBEKeySpec(password.toCharArray(), salt, 120_000, 256)).getEncoded();
        return "120000:" + Base64.getEncoder().encodeToString(salt) + ":" + Base64.getEncoder().encodeToString(hash);
    }

    @Before
    public void setUp() throws Exception {
        user = new User();
        user.setId(7);
        user.setUsername("staff1");
        user.setPasswordHash(oldStyleHash("Correct1"));
        user.setStatus(AccountStatus.ACTIVE);
        saves = 0;
        savedHash = null;
        UserDAO fake = new UserDAO() {
            @Override public Optional<User> findByUsername(String username) { return Optional.of(user); }
            @Override public void updatePasswordHash(int userId, String passwordHash) { saves++; savedHash = passwordHash; }
        };
        service = new AuthenticationService(fake, new LoginThrottle());
    }

    @Test
    public void newHashesUseAtLeast600000Iterations() {
        assertTrue(PasswordUtil.hash("Whatever1").startsWith("600000:"));
        assertFalse(PasswordUtil.needsRehash(PasswordUtil.hash("Whatever1")));
    }

    @Test
    public void oldHashesStillVerifyAndAreFlaggedForUpgrade() throws Exception {
        String old = oldStyleHash("Correct1");
        assertTrue(PasswordUtil.matches("Correct1", old));
        assertFalse(PasswordUtil.matches("Wrong123", old));
        assertTrue(PasswordUtil.needsRehash(old));
        assertFalse(PasswordUtil.needsRehash(null));
        assertFalse("garbage is not 'upgradable'", PasswordUtil.needsRehash("not-a-hash"));
    }

    @Test
    public void aSuccessfulLoginUpgradesAnOldHash() {
        assertEquals(LoginStatus.SUCCESS, service.login("staff1", "Correct1").status());
        assertEquals(1, saves);
        assertTrue(savedHash.startsWith("600000:"));
        assertTrue("the new hash still accepts the same password", PasswordUtil.matches("Correct1", savedHash));
        // and the next login finds nothing to upgrade
        assertEquals(LoginStatus.SUCCESS, service.login("staff1", "Correct1").status());
        assertEquals(1, saves);
    }

    @Test
    public void aWrongPasswordNeverTouchesTheStoredHash() {
        assertEquals(LoginStatus.INVALID_CREDENTIALS, service.login("staff1", "Wrong123").status());
        assertEquals(0, saves);
    }

    @Test
    public void aLockedAccountIsNotUpgraded() {
        user.setStatus(AccountStatus.LOCKED);
        assertEquals(LoginStatus.INACTIVE, service.login("staff1", "Correct1").status());
        assertEquals(0, saves);
    }

    @Test
    public void aFailureWhileSavingDoesNotStopTheLogin() {
        UserDAO failing = new UserDAO() {
            @Override public Optional<User> findByUsername(String username) { return Optional.of(user); }
            @Override public void updatePasswordHash(int userId, String passwordHash) { throw new RuntimeException("disk full"); }
        };
        assertEquals(LoginStatus.SUCCESS, new AuthenticationService(failing, new LoginThrottle()).login("staff1", "Correct1").status());
    }
}
