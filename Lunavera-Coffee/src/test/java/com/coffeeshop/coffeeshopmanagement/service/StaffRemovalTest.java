package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.dao.DataAccessException;
import com.coffeeshop.coffeeshopmanagement.dao.StockLogDAO;
import com.coffeeshop.coffeeshopmanagement.dao.ShiftDAO;
import com.coffeeshop.coffeeshopmanagement.dao.CategoryDAO;
import com.coffeeshop.coffeeshopmanagement.dao.ProductDAO;
import com.coffeeshop.coffeeshopmanagement.dao.TestDatabaseSupport;
import com.coffeeshop.coffeeshopmanagement.dao.UserDAO;
import com.coffeeshop.coffeeshopmanagement.model.AccountStatus;
import com.coffeeshop.coffeeshopmanagement.model.Category;
import com.coffeeshop.coffeeshopmanagement.model.Employee;
import com.coffeeshop.coffeeshopmanagement.model.Product;
import com.coffeeshop.coffeeshopmanagement.model.Role;
import com.coffeeshop.coffeeshopmanagement.model.StaffRow;
import com.coffeeshop.coffeeshopmanagement.model.User;
import com.coffeeshop.coffeeshopmanagement.util.Permissions;
import com.coffeeshop.coffeeshopmanagement.util.Session;
import com.coffeeshop.coffeeshopmanagement.util.TestSession;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Random;
import java.util.UUID;

import static org.junit.Assert.*;

/** TODO 4.29: deleting a person with no history, and making a former member of staff with history anonymous. */
public class StaffRemovalTest {

    private final StaffService service = new StaffService();
    private final AccountService accounts = new AccountService();
    private final UserDAO userDAO = new UserDAO();

    @BeforeClass
    public static void setUpDatabase() {
        TestDatabaseSupport.ensureReady();
    }

    @After
    public void signInAgain() {
        TestSession.admin();
    }

    private static String phone() {
        return "09" + String.format(java.util.Locale.ROOT, "%08d", new Random().nextInt(100_000_000));
    }

    private static Employee typed() {
        Employee e = new Employee();
        e.setFullName("Staff " + UUID.randomUUID().toString().substring(0, 8));
        e.setPhone(phone());
        e.setEmail("x" + UUID.randomUUID().toString().substring(0, 6) + "@example.com");
        e.setAddress("12 Hàng Bài");
        e.setPosition("Pha chế");
        e.setSalary(new BigDecimal("8000000"));
        e.setHireDate(LocalDate.of(2026, 3, 1));
        return e;
    }

    private StaffRow rowOf(int employeeId) {
        return service.list().stream().filter(r -> r.employee().getId() == employeeId).findFirst().orElseThrow();
    }

    private boolean listed(int employeeId) {
        return service.list().stream().anyMatch(r -> r.employee().getId() == employeeId);
    }

    private User accountFor(Employee employee, Role role) {
        String username = "t" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        return accounts.create(new AccountService.Form(username, "Abc12345x", role, true, employee));
    }

    /** Gives the person a line in the stock log by receiving goods as them. */
    private void receiveStockAs(User user, Employee employee) {
        Category category = new Category();
        category.setName("test-removal-category-" + UUID.randomUUID());
        category.setActive(true);
        TestSession.admin();
        new CategoryDAO().insert(category);
        Product p = new Product();
        p.setName("test-removal-product-" + UUID.randomUUID());
        p.setCategoryId(category.getId());
        p.setPrice(new BigDecimal("10000"));
        p.setStock(0);
        p.setActive(true);
        new ProductDAO().insert(p);
        Session.start(user, employee); // the person signs in (an administrator: only they may receive stock)
        new StockLogDAO().receive(p.getId(), 5, null, null, null);
        TestSession.admin();
    }

    @Test
    public void aPersonWithNoHistoryAndNoAccountIsDeleted() {
        TestSession.admin();
        Employee saved = service.save(typed());
        service.remove(rowOf(saved.getId()));
        assertFalse(listed(saved.getId()));
    }

    @Test
    public void aPersonWithNoHistoryIsDeletedTogetherWithTheLogin() {
        TestSession.admin();
        User user = accountFor(typed(), Role.EMPLOYEE);
        service.remove(rowOf(user.getEmployeeId()));
        assertFalse(listed(user.getEmployeeId()));
        assertTrue(userDAO.findById(user.getId()).isEmpty());
    }

    @Test
    public void aPersonInTheStockLogCannotBeDeleted() {
        TestSession.admin();
        User user = accountFor(typed(), Role.ADMIN);
        Employee employee = rowOf(user.getEmployeeId()).employee();
        receiveStockAs(user, employee);
        try {
            service.remove(rowOf(employee.getId()));
            fail("they appear in the stock log");
        } catch (DataAccessException expected) {
            assertEquals(com.coffeeshop.coffeeshopmanagement.util.I18n.t("staff.remove.history"), expected.getMessage());
        }
        assertTrue("nothing was deleted, the login is still there", userDAO.findById(user.getId()).isPresent());
        assertTrue(listed(employee.getId()));
        assertTrue(service.hasHistory(rowOf(employee.getId())));
    }

    @Test
    public void youCannotDeleteYourOwnAccount() {
        TestSession.admin();
        User user = accountFor(typed(), Role.ADMIN);
        Employee employee = rowOf(user.getEmployeeId()).employee();
        Session.start(user, employee);
        try {
            service.remove(rowOf(employee.getId()));
            fail("own account");
        } catch (DataAccessException expected) {
            // fine
        }
        assertTrue(userDAO.findById(user.getId()).isPresent());
    }

    @Test
    public void onlyAnAdministratorMayDeleteOrAnonymise() {
        TestSession.admin();
        Employee saved = service.save(typed());
        StaffRow row = rowOf(saved.getId());
        User cashier = new User();
        cashier.setId(77);
        cashier.setUsername("cashier");
        cashier.setRole(Role.EMPLOYEE);
        Session.start(cashier, null);
        try {
            service.remove(row);
            fail("not an administrator");
        } catch (Permissions.PermissionDeniedException expected) {
            // fine
        }
        try {
            service.makeAnonymous(row);
            fail("not an administrator");
        } catch (Permissions.PermissionDeniedException expected) {
            // fine
        }
    }

    @Test
    public void aPersonWhoIsStillWorkingCannotBeMadeAnonymous() {
        TestSession.admin();
        Employee saved = service.save(typed());
        try {
            service.makeAnonymous(rowOf(saved.getId()));
            fail("still working");
        } catch (DataAccessException expected) {
            assertEquals(com.coffeeshop.coffeeshopmanagement.util.I18n.t("staff.anon.working"), expected.getMessage());
        }
    }

    @Test
    public void aFormerMemberOfStaffLosesTheirPersonalDetailsButKeepsTheRecord() {
        TestSession.admin();
        User user = accountFor(typed(), Role.ADMIN);
        Employee employee = rowOf(user.getEmployeeId()).employee();
        String oldName = employee.getFullName();
        receiveStockAs(user, employee);
        // a second administrator must remain, so the first can be locked
        accountFor(typed(), Role.ADMIN);
        service.setWorking(rowOf(employee.getId()), false);

        service.makeAnonymous(rowOf(employee.getId()));

        StaffRow after = rowOf(employee.getId());
        Employee e = after.employee();
        assertTrue(e.isAnonymised());
        assertEquals(com.coffeeshop.coffeeshopmanagement.util.I18n.t("staff.former.name", employee.getId()), e.getFullName());
        assertNotEquals(oldName, e.getFullName());
        assertNull(e.getPhone());
        assertNull(e.getEmail());
        assertNull(e.getAddress());
        assertNull(e.getSalary());
        assertFalse(e.isActive());
        assertTrue("their account is still locked and renamed", after.hasAccount());
        assertEquals(AccountStatus.LOCKED, after.status());
        assertTrue(after.username().startsWith("former-"));

        var lines = new StockLogDAO().findRecent(null, 500).stream()
                .filter(l -> l.employeeId() != null && l.employeeId() == employee.getId()).toList();
        assertFalse(lines.isEmpty());
        for (var l : lines) {
            assertEquals("the name copied into the stock log is replaced too", e.getFullName(), l.employeeName());
        }
        assertTrue(new ShiftDAO().findRecent(500).stream().noneMatch(c -> oldName.equals(c.closedByName())));
    }

    @Test
    public void anAnonymousPersonCannotBeMadeAnonymousAgainEditedOrPutBackToWork() {
        TestSession.admin();
        User user = accountFor(typed(), Role.ADMIN);
        Employee employee = rowOf(user.getEmployeeId()).employee();
        receiveStockAs(user, employee);
        accountFor(typed(), Role.ADMIN);
        service.setWorking(rowOf(employee.getId()), false);
        service.makeAnonymous(rowOf(employee.getId()));

        StaffRow row = rowOf(employee.getId());
        try {
            service.makeAnonymous(row);
            fail("already anonymous");
        } catch (DataAccessException expected) {
            // fine
        }
        try {
            service.setWorking(row, true);
            fail("cannot come back");
        } catch (DataAccessException expected) {
            // fine
        }
        Employee edit = typed(); // complete and valid, so only the "anonymous" rule can refuse it
        edit.setId(employee.getId());
        try {
            service.save(edit);
            fail("nothing personal left to edit");
        } catch (DataAccessException expected) {
            // fine
        }
        User target = new User();
        target.setId(row.userId());
        target.setUsername(row.username());
        target.setRole(row.role());
        target.setStatus(row.status());
        target.setEmployeeId(employee.getId());
        try {
            accounts.setLocked(target, false);
            fail("the account stays locked");
        } catch (DataAccessException expected) {
            // fine
        }
    }
}
