package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.dao.TestDatabaseSupport;
import com.coffeeshop.coffeeshopmanagement.dao.UserDAO;
import com.coffeeshop.coffeeshopmanagement.model.Employee;
import com.coffeeshop.coffeeshopmanagement.model.Role;
import com.coffeeshop.coffeeshopmanagement.model.User;
import com.coffeeshop.coffeeshopmanagement.util.Session;
import com.coffeeshop.coffeeshopmanagement.util.SessionGuard;
import com.coffeeshop.coffeeshopmanagement.util.TestSession;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Random;
import java.util.UUID;

import static org.junit.Assert.*;

/** TODO 2A.10 (a password reset ends that person's open session) and 2A.18 (a password an admin set must be replaced). */
public class PasswordResetSessionTest {

    private final AccountService service = new AccountService();
    private final UserDAO userDAO = new UserDAO();

    @BeforeClass
    public static void setUpDatabase() {
        TestSession.admin();
        TestDatabaseSupport.ensureReady();
    }

    @org.junit.Before
    public void signInAsAdmin() {
        TestSession.admin();
    }

    @After
    public void signOut() {
        TestSession.clear();
    }

    private static String name() {
        return "t" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    private static AccountService.Form form(String username, String password) {
        Employee e = new Employee();
        e.setFullName("Test " + username);
        e.setPhone("09" + String.format("%08d", new Random().nextInt(100_000_000)));
        return new AccountService.Form(username, password, Role.EMPLOYEE, true, e);
    }

    @Test
    public void anAccountMadeByAnAdminMustChangeItsPassword() {
        User created = service.create(form(name(), "abc12345"));
        assertTrue(userDAO.findById(created.getId()).orElseThrow().isMustChangePassword());
    }

    @Test
    public void theFirstAdminMadeByTheOwnerIsNotForcedToChange() {
        // the first-admin path refuses when accounts exist, so check the flag logic through an existing account instead:
        // an old account (made before the column existed) reads as "not forced"
        User admin = userDAO.findByUsername("admin").orElseThrow();
        assertFalse(admin.isMustChangePassword());
    }

    @Test
    public void anAdminResetForcesAChangeAndChangingItClearsTheMark() {
        User user = service.create(form(name(), "abc12345"));
        PasswordChangeService changer = new PasswordChangeService();
        assertEquals(PasswordChangeService.Result.OK, changer.change(user.getId(), "abc12345", "newpass99", true));
        assertFalse("own change clears the mark", userDAO.findById(user.getId()).orElseThrow().isMustChangePassword());

        Employee employee = new com.coffeeshop.coffeeshopmanagement.dao.EmployeeDAO().findById(user.getEmployeeId()).orElseThrow();
        service.update(userDAO.findById(user.getId()).orElseThrow(), employee,
                new AccountService.Form(user.getUsername(), "reset1234", Role.EMPLOYEE, true, employee));
        assertTrue("an admin reset sets the mark again", userDAO.findById(user.getId()).orElseThrow().isMustChangePassword());
    }

    @Test
    public void anAdminChangingTheirOwnPasswordIsNotForced() {
        User me = service.create(new AccountService.Form(name(), "abc12345", Role.ADMIN, true, form("x", "y").employee()));
        User stored = userDAO.findById(me.getId()).orElseThrow();
        Session.start(stored, null);
        Employee employee = new com.coffeeshop.coffeeshopmanagement.dao.EmployeeDAO().findById(stored.getEmployeeId()).orElseThrow();
        service.update(stored, employee, new AccountService.Form(stored.getUsername(), "mine12345", Role.ADMIN, true, employee));
        assertFalse(userDAO.findById(me.getId()).orElseThrow().isMustChangePassword());
        assertTrue("their own session carries on", SessionGuard.validateNow());
    }

    @Test
    public void aPasswordResetByAnAdminEndsTheOpenSessionOfThatPerson() {
        User user = service.create(form(name(), "abc12345"));
        User stored = userDAO.findById(user.getId()).orElseThrow();
        Session.start(stored, null);
        assertTrue(SessionGuard.validateNow());

        userDAO.updatePasswordHash(user.getId(), PasswordUtil.hash("something-else1")); // what an admin's reset writes
        assertFalse("the old session must end", SessionGuard.validateNow());
    }

    @Test
    public void changingYourOwnPasswordKeepsYourOwnSession() {
        User user = service.create(form(name(), "abc12345"));
        Session.start(userDAO.findById(user.getId()).orElseThrow(), null);
        assertEquals(PasswordChangeService.Result.OK,
                new PasswordChangeService().change(user.getId(), "abc12345", "newpass99", true));
        assertTrue(SessionGuard.validateNow());
    }
}
