package com.coffeeshop.coffeeshopmanagement.util;

import com.coffeeshop.coffeeshopmanagement.dao.OrderDAO;
import com.coffeeshop.coffeeshopmanagement.dao.TestDatabaseSupport;
import com.coffeeshop.coffeeshopmanagement.model.Role;
import com.coffeeshop.coffeeshopmanagement.model.User;
import com.coffeeshop.coffeeshopmanagement.service.AccountService;
import com.coffeeshop.coffeeshopmanagement.service.BackupService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.nio.file.Files;

import static org.junit.Assert.fail;

/** TODO 2A.3: admin-only actions refuse a cashier (or nobody) even when no screen checked first. */
public class PermissionsTest {

    @Before
    public void signInAsACashier() {
        TestDatabaseSupport.ensureReady();
        User cashier = new User();
        cashier.setId(2);
        cashier.setUsername("cashier");
        cashier.setRole(Role.EMPLOYEE);
        Session.start(cashier, null);
    }

    @After
    public void signOut() {
        Session.clear();
    }

    private interface Action { void run() throws Exception; }

    private static void refused(Action action) {
        try {
            action.run();
            fail("a cashier must be refused");
        } catch (Permissions.PermissionDeniedException expected) {
            // good
        } catch (Exception other) {
            fail("expected a permission refusal, got " + other);
        }
    }

    @Test
    public void aCashierCannotManageAccounts() {
        refused(() -> new AccountService().setLocked(new User(), true));
        refused(() -> new AccountService().update(new User(), null, null));
        refused(() -> new AccountService().create(null));
    }

    @Test
    public void aCashierCannotRefundOrRestore() {
        refused(() -> new OrderDAO().cancelPaidOrder(1));
        refused(() -> BackupService.scheduleRestore(Files.createTempFile("x", ".zip")));
    }

    @Test
    public void nobodySignedInIsRefusedToo() {
        Session.clear();
        refused(() -> new OrderDAO().cancelPaidOrder(1));
    }
}
