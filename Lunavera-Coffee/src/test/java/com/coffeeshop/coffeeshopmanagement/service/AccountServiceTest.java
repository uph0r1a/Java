package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig;
import com.coffeeshop.coffeeshopmanagement.dao.DataAccessException;
import com.coffeeshop.coffeeshopmanagement.dao.EmployeeDAO;
import com.coffeeshop.coffeeshopmanagement.dao.TestDatabaseSupport;
import com.coffeeshop.coffeeshopmanagement.dao.UserDAO;
import com.coffeeshop.coffeeshopmanagement.model.AccountStatus;
import com.coffeeshop.coffeeshopmanagement.model.Employee;
import com.coffeeshop.coffeeshopmanagement.model.Role;
import com.coffeeshop.coffeeshopmanagement.model.User;
import com.coffeeshop.coffeeshopmanagement.util.ValidationUtil;
import org.junit.BeforeClass;
import org.junit.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.Assert.*;

/** TODO 2.17, 2.31, 2.32, 2A.2: accounts are saved in one transaction, locking keeps both tables in step, login lookup is unambiguous. */
public class AccountServiceTest {

    private final AccountService service = new AccountService();
    private final UserDAO userDAO = new UserDAO();
    private final EmployeeDAO employeeDAO = new EmployeeDAO();

    @BeforeClass
    public static void setUpDatabase() {
        com.coffeeshop.coffeeshopmanagement.util.TestSession.admin();
        TestDatabaseSupport.ensureReady();
    }

    private static String name() {
        return "t" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    private static String phone() {
        return "09" + String.format("%08d", new Random().nextInt(100_000_000));
    }

    private static AccountService.Form form(String username, String phone, Role role, boolean active) {
        Employee e = new Employee();
        e.setFullName("Test " + username);
        e.setPhone(phone);
        return new AccountService.Form(username, "abc123", role, active, e);
    }

    private static int count(String sql) throws Exception {
        try (Connection c = DatabaseConfig.getConnection(); Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    @Test
    public void createMakesEmployeeAndAccountTogether() {
        String u = name();
        User user = service.create(form(u, phone(), Role.EMPLOYEE, true));
        assertTrue(user.getId() > 0);
        assertNotNull(user.getEmployeeId());
        assertEquals(AccountStatus.ACTIVE, userDAO.findById(user.getId()).orElseThrow().getStatus());
    }

    @Test
    public void duplicateUsernameLeavesNoOrphanEmployee() throws Exception {
        String u = name();
        service.create(form(u, phone(), Role.EMPLOYEE, true));
        int before = count("SELECT COUNT(*) FROM employees");
        try {
            service.create(form(u.toUpperCase(), phone(), Role.EMPLOYEE, true)); // same name, other capitals
            fail("duplicate username must be refused");
        } catch (DataAccessException expected) {
            assertTrue(expected.getMessage().contains("Tên đăng nhập"));
        }
        assertEquals("no employee row may be left behind", before, count("SELECT COUNT(*) FROM employees"));
    }

    @Test
    public void samePhoneInAnotherSpellingIsRefused() {
        String p = phone();
        service.create(form(name(), p, Role.EMPLOYEE, true));
        String plus84 = "+84" + p.substring(1);
        try {
            service.create(form(name(), plus84, Role.EMPLOYEE, true));
            fail("same phone must be refused");
        } catch (DataAccessException expected) {
            assertTrue(expected.getMessage().contains("điện thoại"));
        }
        assertTrue(employeeDAO.existsByPhone(plus84, 0));
    }

    @Test
    public void lockingChangesEmployeeActiveToo() {
        User user = service.create(form(name(), phone(), Role.EMPLOYEE, true));
        service.setLocked(user, true);
        assertEquals(AccountStatus.LOCKED, userDAO.findById(user.getId()).orElseThrow().getStatus());
        assertFalse(employeeDAO.findById(user.getEmployeeId()).orElseThrow().isActive());
        service.setLocked(user, false);
        assertTrue(employeeDAO.findById(user.getEmployeeId()).orElseThrow().isActive());
    }

    @Test
    public void editSavesRoleStatusAndEmployeeTogether() {
        User user = service.create(form(name(), phone(), Role.EMPLOYEE, true));
        Employee emp = employeeDAO.findById(user.getEmployeeId()).orElseThrow();
        Employee typed = new Employee();
        typed.setFullName("Đổi tên");
        typed.setPhone(emp.getPhone());
        typed.setPosition("Thu ngân");
        service.update(user, emp, new AccountService.Form(user.getUsername(), "", Role.ADMIN, false, typed));
        User after = userDAO.findById(user.getId()).orElseThrow();
        assertEquals(Role.ADMIN, after.getRole());
        assertEquals(AccountStatus.LOCKED, after.getStatus());
        Employee e2 = employeeDAO.findById(user.getEmployeeId()).orElseThrow();
        assertEquals("Đổi tên", e2.getFullName());
        assertEquals("Thu ngân", e2.getPosition());
        assertFalse(e2.isActive());
    }

    @Test
    public void lastActiveAdminCannotBeLockedOrDemoted() throws Exception {
        User only = service.create(form(name(), phone(), Role.ADMIN, true));
        List<Integer> others = new ArrayList<>();
        try (Connection c = DatabaseConfig.getConnection(); Statement s = c.createStatement()) {
            try (ResultSet rs = s.executeQuery("SELECT id FROM users WHERE role='ADMIN' AND status='ACTIVE' AND id <> " + only.getId())) {
                while (rs.next()) others.add(rs.getInt(1));
            }
            for (int id : others) s.executeUpdate("UPDATE users SET status='LOCKED' WHERE id=" + id);
        }
        try {
            try {
                service.setLocked(only, true);
                fail("locking the last admin must be refused");
            } catch (DataAccessException expected) {
                assertEquals(AccountService.LAST_ADMIN_MESSAGE, expected.getMessage());
            }
            Employee emp = employeeDAO.findById(only.getEmployeeId()).orElseThrow();
            try {
                service.update(only, emp, new AccountService.Form(only.getUsername(), "", Role.EMPLOYEE, true, emp));
                fail("demoting the last admin must be refused");
            } catch (DataAccessException expected) {
                // fine
            }
            User still = userDAO.findById(only.getId()).orElseThrow();
            assertEquals(Role.ADMIN, still.getRole());
            assertEquals(AccountStatus.ACTIVE, still.getStatus());
        } finally {
            try (Connection c = DatabaseConfig.getConnection(); Statement s = c.createStatement()) {
                for (int id : others) s.executeUpdate("UPDATE users SET status='ACTIVE' WHERE id=" + id);
            }
        }
    }

    @Test
    public void loginLookupIgnoresCapitalsAndPhoneSpelling() {
        String u = name();
        String p = phone();
        User user = service.create(form(u, p, Role.EMPLOYEE, true));
        assertEquals(user.getId(), userDAO.findByUsername(u.toUpperCase()).orElseThrow().getId());
        assertEquals(user.getId(), userDAO.findByUsername(p).orElseThrow().getId());
        assertEquals(user.getId(), userDAO.findByUsername("+84" + p.substring(1)).orElseThrow().getId());
    }

    @Test
    public void usernameThatLooksLikePhoneIsNotAllowed() {
        assertFalse(ValidationUtil.isValidUsername("0901234567"));
        assertFalse(ValidationUtil.isValidUsername("+84901234567"));
        assertTrue(ValidationUtil.isValidUsername("nam.nguyen"));
        assertTrue(ValidationUtil.isValidUsername("ab1234"));
    }

    @org.junit.AfterClass
    public static void signOut() {
        com.coffeeshop.coffeeshopmanagement.util.TestSession.clear();
    }
}
