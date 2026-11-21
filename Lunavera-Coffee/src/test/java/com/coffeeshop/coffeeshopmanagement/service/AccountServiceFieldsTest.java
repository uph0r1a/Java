package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.dao.DataAccessException;
import com.coffeeshop.coffeeshopmanagement.dao.EmployeeDAO;
import com.coffeeshop.coffeeshopmanagement.dao.TestDatabaseSupport;
import com.coffeeshop.coffeeshopmanagement.model.Employee;
import com.coffeeshop.coffeeshopmanagement.model.Role;
import com.coffeeshop.coffeeshopmanagement.model.User;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.ValidationUtil;
import org.junit.BeforeClass;
import org.junit.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * TODO 6.78 (mutation sweep, profile pit-db): what {@link AccountService#update} writes to the employee row (every field the
 * dialog carries, not only the phone and the hire date), a phone another employee has, and the plain lookups.
 */
public class AccountServiceFieldsTest {

    private final AccountService service = new AccountService();
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
        return "09" + String.format(Locale.ROOT, "%08d", new Random().nextInt(100_000_000));
    }

    private static AccountService.Form form(String username, String phone, Role role, boolean active) {
        Employee e = new Employee();
        e.setFullName("Test " + username);
        e.setPhone(phone);
        return new AccountService.Form(username, "Abc12345x", role, active, e);
    }

    @Test
    public void updateWritesEveryFieldTheDialogCarries() {
        String u = name();
        User user = service.create(form(u, phone(), Role.EMPLOYEE, true));
        Employee employee = employeeDAO.findById(user.getEmployeeId()).orElseThrow();

        Employee typed = new Employee();
        typed.setFullName("Changed " + u);
        String newPhone = phone();
        typed.setPhone(newPhone);
        typed.setHireDate(LocalDate.of(2024, 3, 15));
        typed.setPosition("Barista");
        typed.setAddress("12 Le Loi, Hanoi");
        typed.setSalary(new BigDecimal("8500000"));
        service.update(user, employee, new AccountService.Form(u, "", Role.EMPLOYEE, true, typed));

        Employee saved = employeeDAO.findById(user.getEmployeeId()).orElseThrow();
        assertEquals("Changed " + u, saved.getFullName());
        assertEquals("a changed phone is saved (in its normal form)", ValidationUtil.normalizePhone(newPhone), saved.getPhone());
        assertEquals(LocalDate.of(2024, 3, 15), saved.getHireDate());
        assertEquals("Barista", saved.getPosition());
        assertEquals("12 Le Loi, Hanoi", saved.getAddress());
        assertNotNull(saved.getSalary());
        assertEquals(0, new BigDecimal("8500000").compareTo(saved.getSalary()));
    }

    @Test
    public void updateRefusesAPhoneAnotherEmployeeHas() {
        User first = service.create(form(name(), phone(), Role.EMPLOYEE, true));
        Employee firstEmployee = employeeDAO.findById(first.getEmployeeId()).orElseThrow();
        String u = name();
        User second = service.create(form(u, phone(), Role.EMPLOYEE, true));
        Employee secondEmployee = employeeDAO.findById(second.getEmployeeId()).orElseThrow();

        try {
            service.update(second, secondEmployee, form(u, firstEmployee.getPhone(), Role.EMPLOYEE, true));
            fail("the phone of the first employee must be refused");
        } catch (DataAccessException expected) {
            assertEquals(I18n.t("employee.phone.taken"), expected.getMessage());
        }
        assertNotEquals(firstEmployee.getPhone(), employeeDAO.findById(second.getEmployeeId()).orElseThrow().getPhone());
    }

    @Test
    public void theLookupsAnswerFromTheDatabase() {
        User admin = service.create(form(name(), phone(), Role.ADMIN, true));
        Employee employee = employeeDAO.findById(admin.getEmployeeId()).orElseThrow();

        assertTrue(service.findAllUsers().stream().anyMatch(x -> x.getId() == admin.getId()));
        assertTrue(service.findAllEmployees().stream().anyMatch(x -> x.getId() == employee.getId()));
        assertEquals(employee.getPhone(), service.findEmployee(employee.getId()).orElseThrow().getPhone());
        assertTrue(service.findEmployee(-5).isEmpty());
        assertTrue("an active administrator was just made", service.countActiveAdmins() >= 1);
    }

    @Test
    public void theLastAdministratorMessageIsAnAnswerNotAnEmptyString() {
        assertFalse(AccountService.lastAdminMessage().isBlank());
        assertEquals(I18n.t("account.lastadmin"), AccountService.lastAdminMessage());
    }
}
