package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig;
import com.coffeeshop.coffeeshopmanagement.dao.TestDatabaseSupport;
import com.coffeeshop.coffeeshopmanagement.dao.UserDAO;
import com.coffeeshop.coffeeshopmanagement.model.AccountStatus;
import com.coffeeshop.coffeeshopmanagement.model.Role;
import com.coffeeshop.coffeeshopmanagement.model.User;
import com.coffeeshop.coffeeshopmanagement.service.PasswordChangeService.Result;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.*;

/** TODO 2.2: changing your own password, and refusing the default one. */
public class PasswordChangeServiceTest {

    private final UserDAO userDAO = new UserDAO();
    private final PasswordChangeService service = new PasswordChangeService(userDAO);

    @BeforeClass
    public static void setUpDatabase() {
        TestDatabaseSupport.ensureReady();
    }

    private User userWithPassword(String password) {
        User u = new User();
        u.setUsername("test-pw-" + UUID.randomUUID());
        u.setPasswordHash(PasswordUtil.hash(password));
        u.setRole(Role.EMPLOYEE);
        u.setStatus(AccountStatus.ACTIVE);
        return userDAO.insert(u);
    }

    private boolean passwordIs(User u, String password) {
        return PasswordUtil.matches(password, userDAO.findById(u.getId()).orElseThrow().getPasswordHash());
    }

    @Test
    public void changesThePasswordWhenTheCurrentOneIsRight() {
        User u = userWithPassword("Old-pass1");
        assertEquals(Result.OK, service.change(u.getId(), "Old-pass1", "New-pass2", true));
        assertTrue(passwordIs(u, "New-pass2"));
        assertFalse(passwordIs(u, "Old-pass1"));
    }

    @Test
    public void aWrongCurrentPasswordChangesNothing() {
        User u = userWithPassword("Old-pass1");
        assertEquals(Result.WRONG_CURRENT, service.change(u.getId(), "nope-123", "New-pass2", true));
        assertEquals(Result.WRONG_CURRENT, service.change(u.getId(), null, "New-pass2", true));
        assertTrue(passwordIs(u, "Old-pass1"));
    }

    @Test
    public void theForcedChangeAfterLoginDoesNotAskForTheCurrentPassword() {
        User u = userWithPassword(DatabaseConfig.DEFAULT_ADMIN_PASSWORD);
        assertEquals(Result.OK, service.change(u.getId(), null, "Real-pass9", false));
        assertTrue(passwordIs(u, "Real-pass9"));
    }

    @Test
    public void theDefaultPasswordCanNeverBeChosen() {
        User u = userWithPassword("Old-pass1");
        assertEquals(Result.IS_DEFAULT, service.change(u.getId(), "Old-pass1", DatabaseConfig.DEFAULT_ADMIN_PASSWORD, true));
        assertTrue(passwordIs(u, "Old-pass1"));
    }

    @Test
    public void weakPasswordsAreRefused() {
        User u = userWithPassword("Old-pass1");
        assertEquals(Result.WEAK, service.change(u.getId(), "Old-pass1", "ab1", true));
        assertEquals(Result.WEAK, service.change(u.getId(), "Old-pass1", "abc", true));
        assertEquals(Result.WEAK, service.change(u.getId(), "Old-pass1", "onlyletters", true));
        assertEquals(Result.WEAK, service.change(u.getId(), "Old-pass1", "1234567", true));
        assertTrue(passwordIs(u, "Old-pass1"));
    }

    @Test
    public void theSamePasswordAgainIsRefused() {
        User u = userWithPassword("Old-pass1");
        assertEquals(Result.SAME_AS_CURRENT, service.change(u.getId(), "Old-pass1", "Old-pass1", true));
    }

    @Test
    public void anUnknownUserIsReported() {
        assertEquals(Result.NO_SUCH_USER, service.change(-12345, "x", "New-pass2", true));
    }
}
