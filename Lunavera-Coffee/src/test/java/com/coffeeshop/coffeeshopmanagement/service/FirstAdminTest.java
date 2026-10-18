package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.dao.DataAccessException;
import com.coffeeshop.coffeeshopmanagement.dao.TestDatabaseSupport;
import com.coffeeshop.coffeeshopmanagement.model.Employee;
import com.coffeeshop.coffeeshopmanagement.model.Role;
import org.junit.BeforeClass;
import org.junit.Test;

import java.time.LocalDate;

import static org.junit.Assert.*;

/** TODO 2A.5: no built-in administrator; the first one is created on the first start, and only then. */
public class FirstAdminTest {

    @BeforeClass
    public static void setUpDatabase() {
        TestDatabaseSupport.ensureReady();
    }

    @Test
    public void theFirstAdminCannotBeCreatedOnceAnAccountExists() {
        // The shared test database already holds the seeded test administrator.
        Employee owner = new Employee();
        owner.setFullName("Chủ quán");
        owner.setHireDate(LocalDate.now());
        try {
            new AccountService().createFirstAdmin(new AccountService.Form(
                    "owner" + System.nanoTime() % 100000, "Strong-pass-9", Role.ADMIN, true, owner));
            fail("setup must be refused when accounts exist");
        } catch (DataAccessException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("thiết lập lần đầu"));
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void theFirstAccountMustBeAnAdministrator() {
        new AccountService().createFirstAdmin(new AccountService.Form("someone9", "Strong-pass-9", Role.EMPLOYEE, true, new Employee()));
    }
}
