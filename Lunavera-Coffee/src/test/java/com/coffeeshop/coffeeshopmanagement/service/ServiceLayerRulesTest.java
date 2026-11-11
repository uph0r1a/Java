package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.dao.*;
import com.coffeeshop.coffeeshopmanagement.model.*;
import com.coffeeshop.coffeeshopmanagement.util.AppClock;
import com.coffeeshop.coffeeshopmanagement.util.Permissions;
import com.coffeeshop.coffeeshopmanagement.util.Session;
import com.coffeeshop.coffeeshopmanagement.util.TestSession;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.coffeeshop.coffeeshopmanagement.service.CustomerFormRules.Problem;
import static org.junit.Assert.*;

/**
 * TODO 6.21: saving a customer, saving a category and emptying a table go through services that hold the rules; the screens only
 * ask and show the answer. Runs on the shared test database.
 */
public class ServiceLayerRulesTest {

    private final CustomerService customers = new CustomerService();
    private final CategoryService categories = new CategoryService();
    private final TableService tables = new TableService();
    private final CustomerDAO customerDAO = new CustomerDAO();
    private final TableDAO tableDAO = new TableDAO();
    private final OrderDAO orderDAO = new OrderDAO();

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 2);

    @BeforeClass
    public static void setUpDatabase() {
        TestDatabaseSupport.ensureReady();
    }

    @Before
    public void signInAsAdmin() {
        TestSession.admin();
    }

    @After
    public void signOut() {
        TestSession.clear();
    }

    private static void signInAsEmployee(Employee employee) {
        User user = new User();
        user.setId(2);
        user.setUsername("test-employee");
        user.setRole(Role.EMPLOYEE);
        Session.start(user, employee);
    }

    private static String uniquePhone() {
        String digits = String.valueOf(Math.abs(UUID.randomUUID().getMostSignificantBits())).replaceAll("\\D", "");
        return "09" + (digits + "00000000").substring(0, 8);
    }

    private Employee newEmployee() {
        Employee employee = new Employee();
        employee.setFullName("test-" + UUID.randomUUID());
        employee.setActive(true);
        return new EmployeeDAO().insert(employee);
    }

    // ---- customers ----

    @Test
    public void aNewCustomerIsSavedWithZeroPoints() {
        String phone = uniquePhone();
        CustomerService.SaveResult result = customers.save(null,
                new CustomerService.Form("test-" + UUID.randomUUID(), phone, "", null), TODAY);
        assertTrue(result.ok());
        assertEquals(0, result.customer().getLoyaltyPoints());
        assertTrue(customerDAO.findByPhone(phone).isPresent());
    }

    @Test
    public void aSecondCustomerWithTheSamePhoneIsRefusedAndNothingIsSaved() {
        String phone = uniquePhone();
        assertTrue(customers.save(null, new CustomerService.Form("test-a-" + UUID.randomUUID(), phone, null, null), TODAY).ok());
        CustomerService.SaveResult second = customers.save(null,
                new CustomerService.Form("test-b-" + UUID.randomUUID(), phone, null, null), TODAY);
        assertEquals(Problem.DUPLICATE_PHONE, second.problem());
        assertNull(second.customer());
    }

    @Test
    public void aFormProblemSavesNothing() {
        String phone = uniquePhone();
        CustomerService.SaveResult result = customers.save(null, new CustomerService.Form(" ", phone, null, null), TODAY);
        assertEquals(Problem.NAME, result.problem());
        assertFalse(customerDAO.findByPhone(phone).isPresent());
    }

    @Test
    public void aBirthdayInTheFutureIsRefusedWithTheTextFromBirthdayRules() {
        CustomerService.SaveResult result = customers.save(null,
                new CustomerService.Form("test-" + UUID.randomUUID(), uniquePhone(), null, TODAY.plusDays(1)), TODAY);
        assertEquals(Problem.BIRTHDAY, result.problem());
        assertEquals(BirthdayRules.problem(TODAY.plusDays(1), TODAY), result.detail());
    }

    @Test
    public void onlyAnAdministratorMayChangeAnExistingCustomersPhone() {
        String phone = uniquePhone();
        Customer saved = customers.save(null, new CustomerService.Form("test-" + UUID.randomUUID(), phone, null, null), TODAY).customer();
        signInAsEmployee(null);
        String newPhone = uniquePhone();
        CustomerService.SaveResult refused = customers.save(saved,
                new CustomerService.Form(saved.getFullName(), newPhone, null, null), TODAY);
        assertEquals(Problem.PHONE_ADMIN_ONLY, refused.problem());
        assertTrue("the old phone is still the customer's", customerDAO.findByPhone(phone).isPresent());

        CustomerService.SaveResult sameNumber = customers.save(saved,
                new CustomerService.Form("renamed-" + UUID.randomUUID(), phone, null, null), TODAY);
        assertTrue("a cashier may change everything but the phone", sameNumber.ok());

        TestSession.admin();
        assertTrue(customers.save(saved, new CustomerService.Form(saved.getFullName(), newPhone, null, null), TODAY).ok());
        assertTrue(customerDAO.findByPhone(newPhone).isPresent());
    }

    // ---- categories ----

    @Test
    public void aCategoryIsAddedThenChanged() {
        String name = "svc-cat-" + UUID.randomUUID();
        Category added = categories.save(null, "  " + name + " ", "desc", true);
        assertEquals(name, added.getName());
        Category changed = categories.save(added, name + "-2", "new desc", false);
        assertEquals(name + "-2", changed.getName());
        Category stored = new CategoryDAO().findAll().stream().filter(c -> c.getId() == added.getId()).findFirst().orElseThrow();
        assertEquals(name + "-2", stored.getName());
        assertFalse(stored.isActive());
    }

    @Test
    public void aBlankCategoryNameIsRefused() {
        assertThrows(DataAccessException.class, () -> categories.save(null, "   ", "", true));
    }

    @Test
    public void aFailedCategorySaveLeavesTheCategoryOnScreenUntouched() {
        String name = "svc-keep-" + UUID.randomUUID();
        Category first = categories.save(null, name, "", true);
        Category other = categories.save(null, "svc-other-" + UUID.randomUUID(), "", true);
        try {
            categories.save(other, name, "", true); // the name is taken: the database refuses
            fail("a duplicate category name must be refused");
        } catch (DataAccessException expected) {
            assertNotEquals("the object a table shows must not change when the save failed", first.getName(), other.getName());
        }
    }

    @Test
    public void anEmployeeCannotSaveACategory() {
        signInAsEmployee(null);
        assertThrows(Permissions.PermissionDeniedException.class, () -> categories.save(null, "svc-x-" + UUID.randomUUID(), "", true));
    }

    // ---- tables ----

    private int tableWithOpenOrderOf(Employee employee) {
        int number = tableDAO.add("svc-table-" + System.nanoTime(), 2);
        Order order = new Order();
        order.setOrderDate(AppClock.now());
        order.setEmployeeId(employee.getId());
        order.setTableNumber(number);
        order.setStatus(OrderStatus.OPEN);
        order.setSubtotal(BigDecimal.ZERO);
        order.setDiscount(BigDecimal.ZERO);
        order.setTotal(BigDecimal.ZERO);
        orderDAO.insert(order, List.of());
        return number;
    }

    @Test
    public void anotherCashierCannotEmptyATableThatHoldsSomeoneElsesOpenOrder() {
        Employee owner = newEmployee();
        Employee other = newEmployee();
        int number = tableWithOpenOrderOf(owner);
        signInAsEmployee(other);
        assertThrows(Permissions.PermissionDeniedException.class, () -> tables.markEmpty(number));
        assertTrue("the order was not cancelled", orderDAO.findOpenOrderByTable(number).isPresent());
    }

    @Test
    public void theCashierWhoOpenedTheOrderMayEmptyTheTable() {
        Employee owner = newEmployee();
        int number = tableWithOpenOrderOf(owner);
        signInAsEmployee(owner);
        tables.markEmpty(number);
        assertFalse(orderDAO.findOpenOrderByTable(number).isPresent());
    }

    @Test
    public void anAdministratorMayEmptyAnyTable() {
        int number = tableWithOpenOrderOf(newEmployee());
        tables.markEmpty(number);
        assertFalse(orderDAO.findOpenOrderByTable(number).isPresent());
    }

    @Test
    public void aTableWithNoOrderMayBeEmptiedByAnyCashierAndMarkedInUse() {
        int number = tableDAO.add("svc-free-" + System.nanoTime(), 2);
        signInAsEmployee(newEmployee());
        tables.markOccupied(number);
        assertTrue(tableDAO.findAll().stream().anyMatch(t -> t.getTableNumber() == number && t.isOccupied()));
        tables.markEmpty(number);
        assertTrue(tableDAO.findAll().stream().anyMatch(t -> t.getTableNumber() == number && !t.isOccupied()));
    }

    @Test
    public void theScreensQuickCheckAgreesWithThePolicy() {
        Order order = new Order();
        order.setEmployeeId(7);
        assertTrue("no order: anybody", TableService.mayEmpty(null));
        TestSession.admin();
        assertTrue(TableService.mayEmpty(order));
        signInAsEmployee(null);
        assertFalse(TableService.mayEmpty(order));
    }
}
