package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.dao.CustomerDAO;
import com.coffeeshop.coffeeshopmanagement.dao.DataAccessException;
import com.coffeeshop.coffeeshopmanagement.dao.OrderDAO;
import com.coffeeshop.coffeeshopmanagement.dao.TestDatabaseSupport;
import com.coffeeshop.coffeeshopmanagement.dao.UserDAO;
import com.coffeeshop.coffeeshopmanagement.model.AccountStatus;
import com.coffeeshop.coffeeshopmanagement.model.Customer;
import com.coffeeshop.coffeeshopmanagement.model.Order;
import com.coffeeshop.coffeeshopmanagement.model.OrderStatus;
import com.coffeeshop.coffeeshopmanagement.model.PaymentMethod;
import com.coffeeshop.coffeeshopmanagement.model.Role;
import com.coffeeshop.coffeeshopmanagement.model.User;
import com.coffeeshop.coffeeshopmanagement.util.AppClock;
import com.coffeeshop.coffeeshopmanagement.util.TestSession;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.Assert.*;

/** TODO 4.8 and 3.4 (rule D21): merging two rows of one customer, making a customer anonymous, deleting one nothing points at. */
public class CustomerMergeTest {

    private final CustomerMergeService service = new CustomerMergeService();
    private final CustomerDAO customerDAO = new CustomerDAO();
    private final OrderDAO orderDAO = new OrderDAO();
    private final UserDAO userDAO = new UserDAO();

    @BeforeClass
    public static void setUpDatabase() {
        TestDatabaseSupport.ensureReady();
    }

    @Before
    @After
    public void signInAsAdmin() {
        TestSession.admin();
    }

    private static String phone() {
        return "09" + String.format(java.util.Locale.ROOT, "%08d", new Random().nextInt(100_000_000));
    }

    private Customer customer(String phone, String email, int points) {
        Customer c = new Customer();
        c.setFullName("test-merge-" + UUID.randomUUID());
        c.setPhone(phone);
        c.setEmail(email);
        c.setLoyaltyPoints(points);
        return customerDAO.insert(c);
    }

    private Order order(Customer customer, OrderStatus status) {
        Order o = new Order();
        o.setOrderDate(AppClock.now());
        o.setCustomerId(customer.getId());
        o.setStatus(status);
        o.setSubtotal(new BigDecimal("20000"));
        o.setDiscount(BigDecimal.ZERO);
        o.setTotal(new BigDecimal("20000"));
        if (status == OrderStatus.PAID) {
            o.setPaymentMethod(PaymentMethod.CASH);
            o.setPaidAt(AppClock.now());
        }
        return orderDAO.insert(o, List.of());
    }

    private Customer reload(Customer c) {
        return customerDAO.findById(c.getId()).orElseThrow();
    }

    private void login(Customer c) {
        User u = new User();
        u.setUsername("cust" + UUID.randomUUID().toString().replace("-", "").substring(0, 10));
        u.setPasswordHash("x");
        u.setRole(Role.CUSTOMER);
        u.setStatus(AccountStatus.ACTIVE);
        u.setCustomerId(c.getId());
        userDAO.insert(u);
    }

    private static void assertRefused(Runnable action) {
        try {
            action.run();
            fail("expected the action to be refused");
        } catch (DataAccessException expected) {
            assertNotNull(expected.getMessage());
        }
    }

    @Test
    public void mergeMovesOrdersAndPointsAndDeletesTheDuplicate() {
        Customer keep = customer(phone(), null, 10);
        Customer dup = customer(phone(), "dup@example.com", 5);
        order(dup, OrderStatus.PAID);
        order(dup, OrderStatus.PAID);
        order(keep, OrderStatus.PAID);
        int keepPoints = reload(keep).getLoyaltyPoints();
        int dupPoints = reload(dup).getLoyaltyPoints();
        String dupPhone = dup.getPhone();

        CustomerMergeService.MergeResult result = service.merge(keep.getId(), dup.getId());

        assertEquals(2, result.ordersMoved());
        assertEquals(dupPoints, result.pointsMoved());
        assertTrue("the duplicate is gone", customerDAO.findById(dup.getId()).isEmpty());
        Customer kept = reload(keep);
        assertEquals(keepPoints + dupPoints, kept.getLoyaltyPoints());
        assertEquals("a missing e-mail is taken from the duplicate", "dup@example.com", kept.getEmail());
        assertEquals("the kept phone stays", keep.getPhone(), kept.getPhone());
        assertNotEquals(dupPhone, kept.getPhone());
        assertEquals(3, service.impact(keep.getId()).orders());
    }

    @Test
    public void aPhoneTheKeptCustomerLacksIsTakenFromTheDuplicate() {
        Customer keep = customer(null, null, 0);
        String dupPhone = phone();
        Customer dup = customer(dupPhone, null, 0);

        service.merge(keep.getId(), dup.getId());

        assertEquals(dupPhone, reload(keep).getPhone());
    }

    @Test
    public void theMergeIsWrittenDownWithoutThePhoneOrEmail() {
        Customer keep = customer(phone(), null, 0);
        Customer dup = customer(phone(), "secret@example.com", 0);
        order(dup, OrderStatus.PAID);

        service.merge(keep.getId(), dup.getId());

        List<CustomerMergeService.MergeNote> notes = service.notesFor(keep.getId());
        assertEquals(1, notes.size());
        assertEquals(dup.getId(), notes.get(0).removedId());
        assertEquals(dup.getFullName(), notes.get(0).removedName());
        assertEquals(1, notes.get(0).ordersMoved());
        assertNotNull(notes.get(0).mergedAt());
    }

    @Test
    public void aCustomerCannotBeMergedIntoItself() {
        Customer c = customer(phone(), null, 0);
        assertRefused(() -> service.merge(c.getId(), c.getId()));
        assertTrue(customerDAO.findById(c.getId()).isPresent());
    }

    @Test
    public void aDuplicateWithAnOpenOrderIsRefusedAndNothingChanges() {
        Customer keep = customer(phone(), null, 0);
        Customer dup = customer(phone(), null, 0);
        order(dup, OrderStatus.OPEN);

        assertRefused(() -> service.merge(keep.getId(), dup.getId()));

        assertTrue(customerDAO.findById(dup.getId()).isPresent());
        assertEquals(1, service.impact(dup.getId()).orders());
        assertEquals(0, service.impact(keep.getId()).orders());
    }

    @Test
    public void aDuplicateWithALoginIsRefused() {
        Customer keep = customer(phone(), null, 0);
        Customer dup = customer(phone(), null, 0);
        login(dup);

        assertRefused(() -> service.merge(keep.getId(), dup.getId()));

        assertTrue(customerDAO.findById(dup.getId()).isPresent());
    }

    @Test
    public void anAnonymousCustomerCannotBeMerged() {
        Customer keep = customer(phone(), null, 0);
        Customer dup = customer(phone(), null, 0);
        order(dup, OrderStatus.PAID);
        service.anonymise(dup.getId());

        assertRefused(() -> service.merge(keep.getId(), dup.getId()));
        assertRefused(() -> service.merge(dup.getId(), keep.getId()));
    }

    @Test
    public void aMissingCustomerIsRefused() {
        Customer keep = customer(phone(), null, 0);
        assertRefused(() -> service.merge(keep.getId(), 2_000_000_000));
    }

    @Test
    public void anonymiseRemovesEverythingPersonalAndKeepsTheOrders() {
        Customer c = customer(phone(), "me@example.com", 0);
        order(c, OrderStatus.PAID);
        order(c, OrderStatus.PAID);
        assertTrue(reload(c).getLoyaltyPoints() >= 0);

        service.anonymise(c.getId());

        Customer after = reload(c);
        assertTrue(after.isAnonymised());
        assertNull(after.getPhone());
        assertNull(after.getEmail());
        assertEquals(0, after.getLoyaltyPoints());
        assertFalse("no trace of the old name", after.getFullName().equals(c.getFullName()));
        assertTrue(after.getFullName().contains(String.valueOf(c.getId())));
        assertEquals("the orders still point at the row", 2, service.impact(c.getId()).orders());
        assertTrue(service.impact(c.getId()).anonymous());
    }

    @Test
    public void anonymiseLocksTheLoginAndFreesTheName() {
        Customer c = customer(phone(), null, 0);
        order(c, OrderStatus.PAID);
        login(c);

        service.anonymise(c.getId());

        List<User> mine = userDAO.findAll().stream().filter(u -> Integer.valueOf(c.getId()).equals(u.getCustomerId())).toList();
        assertEquals(1, mine.size());
        assertEquals(AccountStatus.LOCKED, mine.get(0).getStatus());
        assertTrue(mine.get(0).getUsername().startsWith("former-customer-" + c.getId()));
    }

    @Test
    public void anonymiseAlsoRemovesTheNamesOfDuplicatesMergedIntoIt() {
        Customer keep = customer(phone(), null, 0);
        Customer dup = customer(phone(), null, 0);
        order(keep, OrderStatus.PAID);
        service.merge(keep.getId(), dup.getId());

        service.anonymise(keep.getId());

        List<CustomerMergeService.MergeNote> notes = service.notesFor(keep.getId());
        assertEquals(1, notes.size());
        assertNotEquals(dup.getFullName(), notes.get(0).removedName());
    }

    @Test
    public void anonymiseIsRefusedTwiceAndWhileAnOrderIsOpen() {
        Customer c = customer(phone(), null, 0);
        order(c, OrderStatus.OPEN);
        assertRefused(() -> service.anonymise(c.getId()));
        assertFalse(reload(c).isAnonymised());

        Customer d = customer(phone(), null, 0);
        service.anonymise(d.getId());
        assertRefused(() -> service.anonymise(d.getId()));
    }

    @Test
    public void anAnonymousCustomerIsNotFoundByTheSearchAndCannotBeEdited() {
        Customer c = customer(phone(), null, 0);
        service.anonymise(c.getId());

        assertTrue(customerDAO.search("Former customer #" + c.getId(), 50).stream().noneMatch(x -> x.getId() == c.getId()));
        Customer edit = reload(c);
        edit.setFullName("Somebody Again");
        edit.setPhone(phone());
        customerDAO.update(edit);
        assertNotEquals("Somebody Again", reload(c).getFullName());
        assertNull(reload(c).getPhone());
    }

    @Test
    public void deleteRemovesACustomerNothingPointsAt() {
        Customer c = customer(phone(), null, 0);
        assertTrue(service.impact(c.getId()).deletable());

        assertTrue(service.delete(c.getId()));

        assertTrue(customerDAO.findById(c.getId()).isEmpty());
    }

    @Test
    public void deleteRefusesACustomerWithOrdersPointsOrALogin() {
        Customer withOrder = customer(phone(), null, 0);
        order(withOrder, OrderStatus.PAID);
        assertFalse(service.impact(withOrder.getId()).deletable());
        assertFalse(service.delete(withOrder.getId()));
        assertTrue(customerDAO.findById(withOrder.getId()).isPresent());

        Customer withPoints = customer(phone(), null, 7);
        assertFalse(service.delete(withPoints.getId()));

        Customer withLogin = customer(phone(), null, 0);
        login(withLogin);
        assertFalse(service.delete(withLogin.getId()));
    }

    @Test
    public void onlyAnAdministratorMayDoAnyOfIt() {
        Customer a = customer(phone(), null, 0);
        Customer b = customer(phone(), null, 0);
        TestSession.clear();
        assertThrows(RuntimeException.class, () -> service.merge(a.getId(), b.getId()));
        assertThrows(RuntimeException.class, () -> service.anonymise(a.getId()));
        assertThrows(RuntimeException.class, () -> service.delete(a.getId()));
        TestSession.admin();
        assertTrue(customerDAO.findById(a.getId()).isPresent());
        assertTrue(customerDAO.findById(b.getId()).isPresent());
    }
}
