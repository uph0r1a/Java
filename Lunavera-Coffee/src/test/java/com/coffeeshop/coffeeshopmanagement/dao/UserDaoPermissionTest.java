package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.model.AccountStatus;
import com.coffeeshop.coffeeshopmanagement.model.Role;
import com.coffeeshop.coffeeshopmanagement.model.User;
import com.coffeeshop.coffeeshopmanagement.util.Permissions;
import com.coffeeshop.coffeeshopmanagement.util.Session;
import com.coffeeshop.coffeeshopmanagement.util.TestSession;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.*;

/** TODO 2A.3: changing the status or the role of an account is refused inside the data layer for anybody but an administrator. */
public class UserDaoPermissionTest {

    private final UserDAO users = new UserDAO();

    @BeforeClass
    public static void database() {
        TestDatabaseSupport.ensureReady();
    }

    @Before
    public void administrator() {
        TestSession.admin();
    }

    @After
    public void signOut() {
        TestSession.clear();
    }

    private User anAccount() {
        User user = new User();
        user.setUsername("perm" + UUID.randomUUID().toString().substring(0, 8));
        user.setPasswordHash("x");
        user.setRole(Role.EMPLOYEE);
        user.setStatus(AccountStatus.ACTIVE);
        return users.insert(user);
    }

    private static void refused(String what, Runnable action) {
        try {
            action.run();
            fail(what + " must be refused");
        } catch (Permissions.PermissionDeniedException expected) {
            // refused
        }
    }

    @Test
    public void aCashierAndNobodyCannotLockOrPromoteAnAccount() {
        User account = anAccount();
        User cashier = new User();
        cashier.setId(2);
        cashier.setUsername("test-cashier");
        cashier.setRole(Role.EMPLOYEE);
        Session.start(cashier, null);
        refused("lock", () -> users.updateStatus(account.getId(), AccountStatus.LOCKED));
        refused("promote", () -> users.updateRole(account.getId(), Role.ADMIN));
        TestSession.clear();
        refused("lock by nobody", () -> users.updateStatus(account.getId(), AccountStatus.LOCKED));
        refused("promote by nobody", () -> users.updateRole(account.getId(), Role.ADMIN));

        TestSession.admin();
        User now = users.findByUsername(account.getUsername()).orElseThrow();
        assertEquals("nothing was changed", Role.EMPLOYEE, now.getRole());
        assertEquals(AccountStatus.ACTIVE, now.getStatus());
    }

    @Test
    public void anAdministratorStillCan() {
        User account = anAccount();
        users.updateStatus(account.getId(), AccountStatus.LOCKED);
        users.updateRole(account.getId(), Role.ADMIN);
        User now = users.findByUsername(account.getUsername()).orElseThrow();
        assertEquals(Role.ADMIN, now.getRole());
        assertEquals(AccountStatus.LOCKED, now.getStatus());
    }
}
