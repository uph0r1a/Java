package com.coffeeshop.coffeeshopmanagement.util;

import com.coffeeshop.coffeeshopmanagement.dao.TestDatabaseSupport;
import com.coffeeshop.coffeeshopmanagement.dao.UserDAO;
import com.coffeeshop.coffeeshopmanagement.model.AccountStatus;
import com.coffeeshop.coffeeshopmanagement.model.Employee;
import com.coffeeshop.coffeeshopmanagement.model.Role;
import com.coffeeshop.coffeeshopmanagement.model.User;
import com.coffeeshop.coffeeshopmanagement.service.PasswordUtil;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.*;

/** SessionGuard against a real (throwaway) database: locked, deleted and re-roled accounts. */
public class SessionGuardTest {

    private final UserDAO dao = new UserDAO();

    @BeforeClass
    public static void setUpDatabase() {
        TestDatabaseSupport.ensureReady();
    }

    @After
    public void clearSession() {
        Session.clear();
    }

    private User newEmployeeUser() {
        User user = new User();
        user.setUsername("guardtest-" + UUID.randomUUID());
        user.setPasswordHash(PasswordUtil.hash("Test1234"));
        user.setRole(Role.EMPLOYEE);
        user.setStatus(AccountStatus.ACTIVE);
        return dao.insert(user);
    }

    @Test
    public void findByIdFindsTheSeededAdminAndNotAMissingUser() {
        assertTrue(dao.findById(987654).isEmpty());
        User admin = dao.findByUsername("admin").orElseThrow();
        assertTrue(dao.findById(admin.getId()).isPresent());
    }

    @Test
    public void noSessionIsInvalid() {
        Session.clear();
        assertFalse(SessionGuard.validateNow());
    }

    @Test
    public void anActiveAdminAndAnActiveEmployeeAreValid() {
        Session.start(dao.findByUsername("admin").orElseThrow(), null);
        assertTrue(SessionGuard.validateNow());

        Session.start(newEmployeeUser(), null);
        assertTrue(SessionGuard.validateNow());
        assertFalse(Session.isAdmin());
    }

    @Test
    public void promotionAndDemotionTakeEffectWithoutALogin() {
        User user = newEmployeeUser();
        Session.start(user, null);

        asAdmin(() -> dao.updateRole(user.getId(), Role.ADMIN));
        assertTrue(SessionGuard.validateNow());
        assertTrue("role refreshed to ADMIN without re-login", Session.isAdmin());

        asAdmin(() -> dao.updateRole(user.getId(), Role.EMPLOYEE));
        SessionGuard.validateNow();
        assertFalse("role refreshed back to EMPLOYEE", Session.isAdmin());
    }

    @Test
    public void aLockedAccountIsInvalidAndValidAgainWhenReactivated() {
        User user = newEmployeeUser();
        Session.start(user, null);

        asAdmin(() -> dao.updateStatus(user.getId(), AccountStatus.LOCKED));
        assertFalse(SessionGuard.validateNow());

        asAdmin(() -> dao.updateStatus(user.getId(), AccountStatus.ACTIVE));
        Session.start(user, null);
        assertTrue(SessionGuard.validateNow());
    }

    @Test
    public void anAccountThatNoLongerExistsIsInvalid() {
        User ghost = new User();
        ghost.setId(424242);
        ghost.setRole(Role.EMPLOYEE);
        ghost.setStatus(AccountStatus.ACTIVE);
        Session.start(ghost, null);
        assertFalse(SessionGuard.validateNow());
    }

    /** An account is changed by an administrator (TODO 2A.3); the session under test keeps the copy it had before. */
    private static void asAdmin(Runnable change) {
        User current = Session.getCurrentUser();
        Employee employee = Session.getCurrentEmployee();
        TestSession.admin();
        try {
            change.run();
        } finally {
            if (current == null) {
                Session.clear();
            } else {
                Session.start(current, employee);
            }
        }
    }
}
