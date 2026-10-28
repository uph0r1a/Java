package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.dao.DataAccessException;
import com.coffeeshop.coffeeshopmanagement.dao.EmployeeDAO;
import com.coffeeshop.coffeeshopmanagement.dao.TestDatabaseSupport;
import com.coffeeshop.coffeeshopmanagement.dao.UserDAO;
import com.coffeeshop.coffeeshopmanagement.model.AccountStatus;
import com.coffeeshop.coffeeshopmanagement.model.Employee;
import com.coffeeshop.coffeeshopmanagement.model.Role;
import com.coffeeshop.coffeeshopmanagement.model.StaffRow;
import com.coffeeshop.coffeeshopmanagement.model.User;
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

/** TODO 4.12: the staff list, adding and editing a person, and "left / working again" with and without a login account. */
public class StaffServiceTest {

    private final StaffService service = new StaffService();
    private final AccountService accounts = new AccountService();
    private final EmployeeDAO employeeDAO = new EmployeeDAO();
    private final UserDAO userDAO = new UserDAO();

    @BeforeClass
    public static void setUpDatabase() {
        TestDatabaseSupport.ensureReady();
    }

    @After
    public void signInAsAdminAgain() {
        TestSession.admin();
    }

    private static String phone() {
        return "09" + String.format(java.util.Locale.ROOT, "%08d", new Random().nextInt(100_000_000));
    }

    private static Employee typed(String phone) {
        Employee e = new Employee();
        e.setFullName("Staff " + UUID.randomUUID().toString().substring(0, 8));
        e.setPhone(phone);
        e.setPosition("Pha chế");
        e.setSalary(new BigDecimal("8000000"));
        e.setHireDate(LocalDate.of(2026, 3, 1));
        return e;
    }

    private StaffRow rowOf(int employeeId) {
        return service.list().stream().filter(r -> r.employee().getId() == employeeId).findFirst().orElseThrow();
    }

    private static void signInAs(int userId, Role role) {
        User u = new User();
        u.setId(userId);
        u.setUsername("signed-in-" + userId);
        u.setRole(role);
        Session.start(u, null);
    }

    @Test
    public void aNewPersonIsSavedAsWorkingWithoutAnAccount() {
        TestSession.admin();
        Employee saved = service.save(typed(phone()));
        assertTrue(saved.getId() > 0);
        StaffRow row = rowOf(saved.getId());
        assertFalse(row.hasAccount());
        assertTrue(row.isWorking());
        assertEquals("Pha chế", row.employee().getPosition());
        assertEquals(0, new BigDecimal("8000000").compareTo(row.employee().getSalary()));
        assertEquals(LocalDate.of(2026, 3, 1), row.employee().getHireDate());
    }

    @Test
    public void aPersonWithAnAccountShowsTheAccount() {
        TestSession.admin();
        String username = "t" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        Employee e = typed(phone());
        User user = accounts.create(new AccountService.Form(username, "Abc12345x", Role.EMPLOYEE, true, e));
        StaffRow row = rowOf(user.getEmployeeId());
        assertTrue(row.hasAccount());
        assertEquals(username, row.username());
        assertEquals(Role.EMPLOYEE, row.role());
        assertEquals(AccountStatus.ACTIVE, row.status());
        assertTrue(row.isWorking());
    }

    @Test
    public void editingChangesTheDetailsAndKeepsTheWorkingFlag() {
        TestSession.admin();
        Employee saved = service.save(typed(phone()));
        service.setWorking(rowOf(saved.getId()), false);

        Employee change = new Employee();
        change.setId(saved.getId());
        change.setFullName("  Tên mới  ");
        change.setPhone(saved.getPhone());
        change.setEmail("moi@example.com");
        change.setPosition("   ");
        change.setSalary(new BigDecimal("9500000"));
        service.save(change);

        StaffRow row = rowOf(saved.getId());
        assertEquals("Tên mới", row.employee().getFullName());
        assertEquals("moi@example.com", row.employee().getEmail());
        assertNull("a blank position is stored as nothing", row.employee().getPosition());
        assertNull(row.employee().getHireDate());
        assertEquals(0, new BigDecimal("9500000").compareTo(row.employee().getSalary()));
        assertFalse("editing must not bring a person who left back to work", row.isWorking());
    }

    @Test
    public void editingAPersonWithAnAccountDoesNotTouchTheAccount() {
        TestSession.admin();
        String username = "t" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        User user = accounts.create(new AccountService.Form(username, "Abc12345x", Role.EMPLOYEE, true, typed(phone())));
        String hashBefore = userDAO.findById(user.getId()).orElseThrow().getPasswordHash();

        Employee change = employeeDAO.findById(user.getEmployeeId()).orElseThrow();
        change.setPosition("Thu ngân");
        service.save(change);

        User after = userDAO.findById(user.getId()).orElseThrow();
        assertEquals(hashBefore, after.getPasswordHash());
        assertEquals(Role.EMPLOYEE, after.getRole());
        assertEquals(AccountStatus.ACTIVE, after.getStatus());
        assertEquals("Thu ngân", rowOf(user.getEmployeeId()).employee().getPosition());
    }

    @Test
    public void thePhoneOfAnotherPersonIsRefusedInAnySpelling() {
        TestSession.admin();
        String p = phone();
        service.save(typed(p));
        DataAccessException added = assertThrows(DataAccessException.class, () -> service.save(typed("+84" + p.substring(1))));
        assertTrue(added.getMessage(), added.getMessage().contains("nhân viên khác"));

        Employee other = service.save(typed(phone()));
        other.setPhone(p);
        assertThrows(DataAccessException.class, () -> service.save(other));
    }

    @Test
    public void aPersonMayKeepTheirOwnPhoneWhenEdited() {
        TestSession.admin();
        Employee saved = service.save(typed(phone()));
        saved.setPosition("Phục vụ");
        service.save(saved); // same phone, same person: not a clash
        assertEquals("Phục vụ", rowOf(saved.getId()).employee().getPosition());
    }

    @Test
    public void aBrokenRuleSavesNothing() {
        TestSession.admin();
        int before = service.list().size();
        Employee bad = typed(phone());
        bad.setFullName("  ");
        assertThrows(DataAccessException.class, () -> service.save(bad));
        Employee futureStart = typed(phone());
        futureStart.setHireDate(LocalDate.now().plusYears(1));
        assertThrows(DataAccessException.class, () -> service.save(futureStart));
        assertEquals(before, service.list().size());
    }

    @Test
    public void editingARecordThatWasRemovedSaysSo() {
        TestSession.admin();
        Employee ghost = typed(phone());
        ghost.setId(2_000_000_000);
        DataAccessException e = assertThrows(DataAccessException.class, () -> service.save(ghost));
        assertTrue(e.getMessage(), e.getMessage().contains("không còn"));
    }

    @Test
    public void aPersonWithoutAnAccountCanLeaveAndComeBack() {
        TestSession.admin();
        Employee saved = service.save(typed(phone()));
        service.setWorking(rowOf(saved.getId()), false);
        assertFalse(rowOf(saved.getId()).isWorking());
        assertFalse(employeeDAO.findById(saved.getId()).orElseThrow().isActive());
        service.setWorking(rowOf(saved.getId()), true);
        assertTrue(rowOf(saved.getId()).isWorking());
    }

    @Test
    public void leavingLocksTheAccountAndComingBackUnlocksIt() {
        TestSession.admin();
        String username = "t" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        User user = accounts.create(new AccountService.Form(username, "Abc12345x", Role.EMPLOYEE, true, typed(phone())));
        signInAs(Integer.MAX_VALUE, Role.ADMIN); // somebody else: your own account cannot be locked

        service.setWorking(rowOf(user.getEmployeeId()), false);
        assertEquals(AccountStatus.LOCKED, userDAO.findById(user.getId()).orElseThrow().getStatus());
        assertFalse(employeeDAO.findById(user.getEmployeeId()).orElseThrow().isActive());
        assertFalse(rowOf(user.getEmployeeId()).isWorking());

        service.setWorking(rowOf(user.getEmployeeId()), true);
        assertEquals(AccountStatus.ACTIVE, userDAO.findById(user.getId()).orElseThrow().getStatus());
        assertTrue(employeeDAO.findById(user.getEmployeeId()).orElseThrow().isActive());
    }

    @Test
    public void youCannotMarkYourOwnRecordAsLeft() {
        TestSession.admin();
        String username = "t" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        User user = accounts.create(new AccountService.Form(username, "Abc12345x", Role.EMPLOYEE, true, typed(phone())));
        StaffRow row = rowOf(user.getEmployeeId());
        signInAs(user.getId(), Role.ADMIN); // the signed-in person is this one (the check is on the id)
        assertThrows(DataAccessException.class, () -> service.setWorking(row, false));
        assertEquals(AccountStatus.ACTIVE, userDAO.findById(user.getId()).orElseThrow().getStatus());
    }

    @Test
    public void anEmployeeCannotUseTheStaffService() {
        TestSession.admin();
        Employee saved = service.save(typed(phone()));
        StaffRow row = rowOf(saved.getId());
        signInAs(Integer.MAX_VALUE - 1, Role.EMPLOYEE);
        assertThrows(DataAccessException.class, service::list);
        assertThrows(DataAccessException.class, () -> service.save(typed(phone())));
        assertThrows(DataAccessException.class, () -> service.setWorking(row, false));
        TestSession.admin();
        assertTrue("nothing changed", rowOf(saved.getId()).isWorking());
    }
}
