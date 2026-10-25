package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.dao.UserDAO;
import com.coffeeshop.coffeeshopmanagement.model.AccountStatus;
import com.coffeeshop.coffeeshopmanagement.model.User;
import com.coffeeshop.coffeeshopmanagement.service.AuthenticationService.LoginResult;
import com.coffeeshop.coffeeshopmanagement.service.AuthenticationService.LoginStatus;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/** Uses an in-memory stand-in for the user table, so no database is needed. */
public class AuthenticationServiceTest {

    private User user;
    private AuthenticationService service;

    @Before
    public void setUp() {
        // the security log limits repeated lines for a minute; every test starts with a clean count (TODO 2A.25)
        com.coffeeshop.coffeeshopmanagement.util.SecurityLog.resetLimitsForTests();
        user = new User();
        user.setUsername("staff1");
        user.setPasswordHash(PasswordUtil.hash("Correct1"));
        user.setStatus(AccountStatus.ACTIVE);
        UserDAO fake = new UserDAO() {
            @Override
            public Optional<User> findByUsername(String username) {
                return "staff1".equals(username) ? Optional.of(user) : Optional.empty();
            }
        };
        service = new AuthenticationService(fake, new LoginThrottle());
    }

    @Test
    public void correctPasswordSucceeds() {
        LoginResult result = service.login("staff1", "Correct1");
        assertEquals(LoginStatus.SUCCESS, result.status());
        assertNotNull(result.user());
    }

    @Test
    public void unknownUserAndWrongPasswordGiveTheSameAnswer() {
        assertEquals(LoginStatus.INVALID_CREDENTIALS, service.login("nobody", "Whatever1").status());
        assertEquals(LoginStatus.INVALID_CREDENTIALS, service.login("staff1", "Wrong123").status());
    }

    @Test
    public void aLockedAccountIsOnlyReportedToSomeoneWithItsPassword() {
        user.setStatus(AccountStatus.LOCKED);
        assertEquals(LoginStatus.INVALID_CREDENTIALS, service.login("staff1", "Wrong123").status());
        LoginResult result = service.login("staff1", "Correct1");
        assertEquals(LoginStatus.INACTIVE, result.status());
        assertNull(result.user());
    }

    @Test
    public void repeatedWrongAttemptsMakeEvenTheCorrectPasswordWaitAMoment() {
        for (int i = 0; i < 3; i++) {
            assertEquals(LoginStatus.INVALID_CREDENTIALS, service.login("staff1", "Wrong123").status());
        }
        LoginResult blocked = service.login("staff1", "Correct1");
        assertEquals(LoginStatus.TOO_MANY_ATTEMPTS, blocked.status());
        assertEquals(1, blocked.retryAfterSeconds());
    }

    @Test
    public void guessingAnUnknownUsernameIsThrottledToo() {
        for (int i = 0; i < 3; i++) service.login("ghost", "Wrong123");
        assertEquals(LoginStatus.TOO_MANY_ATTEMPTS, service.login("ghost", "Wrong123").status());
    }

    @Test
    public void aLegacyCustomerAccountCanNeverSignIn() {
        user.setRole(com.coffeeshop.coffeeshopmanagement.model.Role.CUSTOMER);
        assertEquals(LoginStatus.INVALID_CREDENTIALS, service.login("staff1", "Correct1").status());
    }

    // ---- TODO 2A.28: one account, one delay, whatever is typed to reach it

    private AuthenticationService serviceThatAlsoFindsTheStaffPhone() {
        user.setId(7);
        UserDAO fake = new UserDAO() {
            @Override
            public Optional<User> findByUsername(String username) {
                boolean thePhone = "0901111222".equals(com.coffeeshop.coffeeshopmanagement.util.ValidationUtil.normalizePhone(username));
                return thePhone || "staff1".equals(username) ? Optional.of(user) : Optional.empty();
            }
        };
        return new AuthenticationService(fake, new LoginThrottle());
    }

    @Test
    public void wrongTriesWithDifferentSpellingsOfThePhoneShareOneDelay() {
        AuthenticationService svc = serviceThatAlsoFindsTheStaffPhone();
        assertEquals(LoginStatus.INVALID_CREDENTIALS, svc.login("0901111222", "Wrong123").status());
        assertEquals(LoginStatus.INVALID_CREDENTIALS, svc.login("+84 901 111 222", "Wrong123").status());
        assertEquals(LoginStatus.INVALID_CREDENTIALS, svc.login("84901111222", "Wrong123").status());
        assertEquals("a fourth spelling is not a fresh start", LoginStatus.TOO_MANY_ATTEMPTS,
                svc.login("0901.111.222", "Wrong123").status());
    }

    @Test
    public void wrongTriesWithThePhoneAlsoSlowTheUserNameOfTheSameAccount() {
        AuthenticationService svc = serviceThatAlsoFindsTheStaffPhone();
        for (int i = 0; i < 3; i++) svc.login("84901111222", "Wrong123");
        LoginResult blocked = svc.login("staff1", "Correct1");
        assertEquals(LoginStatus.TOO_MANY_ATTEMPTS, blocked.status());
        assertEquals(1, blocked.retryAfterSeconds());
    }

    @Test
    public void aCorrectLoginClearsTheDelayOfTheWholeAccount() {
        AuthenticationService svc = serviceThatAlsoFindsTheStaffPhone();
        svc.login("0901111222", "Wrong123");
        svc.login("staff1", "Wrong123");
        assertEquals(LoginStatus.SUCCESS, svc.login("staff1", "Correct1").status());
        assertEquals(LoginStatus.INVALID_CREDENTIALS, svc.login("84901111222", "Wrong123").status());
        assertEquals("still inside the free tries", LoginStatus.INVALID_CREDENTIALS, svc.login("0901111222", "Wrong123").status());
    }

    // ---- TODO 2A.39: what is typed into the user-name box never reaches the log in full

    private static final class Capture extends java.util.logging.Handler {
        final java.util.List<String> lines = new java.util.ArrayList<>();

        @Override public void publish(java.util.logging.LogRecord record) { lines.add(record.getMessage()); }
        @Override public void flush() { }
        @Override public void close() { }
    }

    private Capture captureSecurityLog() {
        java.util.logging.Logger logger = java.util.logging.Logger.getLogger("lunavera.security");
        logger.setLevel(java.util.logging.Level.ALL);
        Capture capture = new Capture();
        logger.addHandler(capture);
        return capture;
    }

    private void stopCapturing(Capture capture) {
        java.util.logging.Logger.getLogger("lunavera.security").removeHandler(capture);
    }

    @Test
    public void aPasswordTypedAsTheUserNameIsNotWrittenToTheLog() {
        Capture capture = captureSecurityLog();
        try {
            for (int i = 0; i < 4; i++) service.login("Quan-Cafe#2026", "nguyenvan"); // unknown name, then blocked
            String all = String.join("\n", capture.lines);
            assertFalse("full text in the log: " + all, all.contains("Quan-Cafe#2026"));
            assertFalse(all, all.contains("Quan-Cafe"));
            assertTrue("unknown-name line still says something useful: " + all, all.contains("no such user"));
            assertTrue("blocked line too: " + all, all.contains("login blocked"));
            assertTrue("the length is logged: " + all, all.contains("14 characters"));
        } finally {
            stopCapturing(capture);
        }
    }

    @Test
    public void theLogNamesTheRealAccountNotTheTypedPhone() {
        AuthenticationService svc = serviceThatAlsoFindsTheStaffPhone();
        Capture capture = captureSecurityLog();
        try {
            svc.login("+84 901 111 222", "Wrong123");
            String all = String.join("\n", capture.lines);
            assertFalse("a staff phone number must not be logged: " + all, all.contains("901 111 222"));
            assertTrue(all, all.contains("'staff1' (user id 7)"));
        } finally {
            stopCapturing(capture);
        }
    }

    /** TODO 2A.25: holding Enter on the login screen must not write a line per key press and push the history out of the log. */
    @Test
    public void aFloodOfWrongPasswordsLeavesAFewSecurityLogLinesNotOnePerAttempt() {
        List<LogRecord> lines = new ArrayList<>();
        Handler capture = new Handler() {
            @Override
            public void publish(LogRecord record) {
                lines.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        Logger security = Logger.getLogger("lunavera.security");
        com.coffeeshop.coffeeshopmanagement.util.SecurityLog.resetLimitsForTests();
        security.addHandler(capture);
        try {
            for (int i = 0; i < 300; i++) {
                service.login("staff1", "Wrong123");
            }
        } finally {
            security.removeHandler(capture);
        }
        long wrong = lines.stream().filter(r -> r.getMessage().contains("wrong password for 'staff1'")).count();
        long blocked = lines.stream().filter(r -> r.getMessage().contains("login blocked for the typed text")).count();
        assertEquals("one line for the wrong passwords", 1, wrong);
        assertEquals("one line for the blocked attempts", 1, blocked);
        assertTrue("and nothing else: " + lines.size(), lines.size() <= 3);
    }
}
