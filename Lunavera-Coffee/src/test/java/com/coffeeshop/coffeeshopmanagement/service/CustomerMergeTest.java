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
        com.coffeeshop.coffeeshopmanagement.model.OrderItem line = new com.coffeeshop.coffeeshopmanagement.model.OrderItem();
        line.setProductName("test line"); // no product: a paid order needs a line, and this one touches no stock
        line.setQuantity(1);
        line.setUnitPrice(new BigDecimal("20000"));
        line.setLineTotal(new BigDecimal("20000"));
        return orderDAO.insert(o, List.of(line));
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
    public void theMergeIsOneLogLineWithoutThePhoneOrEmailAndThereIsNoMergeTable() throws Exception {
        Customer keep = customer(phone(), null, 0);
        String secretPhone = phone();
        Customer dup = customer(secretPhone, "secret@example.com", 0);
        order(dup, OrderStatus.PAID);

        service.merge(keep.getId(), dup.getId());

        var lines = new com.coffeeshop.coffeeshopmanagement.dao.AuditDAO().find(new com.coffeeshop.coffeeshopmanagement.dao.AuditDAO.Filter(
                null, null, com.coffeeshop.coffeeshopmanagement.model.AuditAction.CUSTOMER_MERGED, null, keep.getFullName()), 10, 0);
        assertEquals(1, lines.size());
        assertTrue(lines.get(0).oldValue().contains(dup.getFullName()));
        assertEquals("one order and the 2 points it earned", "1|2", lines.get(0).detail());
        for (String text : new String[]{lines.get(0).oldValue(), lines.get(0).detail(), lines.get(0).targetLabel()}) {
            assertFalse(text.contains(secretPhone));
            assertFalse(text.contains("secret@example.com"));
        }
        try (var c = com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig.getConnection(); var rs = c.getMetaData().getTables(null, null, "customer_merges", null)) {
            assertFalse("the table of merges is gone", rs.next());
        }
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
    public void anonymiseAlsoScrubsTheNamesOfDuplicatesFromTheMergeLine() {
        Customer keep = customer(phone(), null, 0);
        Customer dup = customer(phone(), null, 0);
        order(keep, OrderStatus.PAID);
        service.merge(keep.getId(), dup.getId());

        service.anonymise(keep.getId());

        var lines = new com.coffeeshop.coffeeshopmanagement.dao.AuditDAO().find(new com.coffeeshop.coffeeshopmanagement.dao.AuditDAO.Filter(
                null, null, com.coffeeshop.coffeeshopmanagement.model.AuditAction.CUSTOMER_MERGED, null, null), 200, 0).stream()
                .filter(e -> e.targetId() != null && e.targetId() == keep.getId()).toList();
        assertEquals(1, lines.size());
        assertNull(lines.get(0).oldValue());
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
    public void onlyAnAdministratorMayDoAnyOfIt() {
        Customer a = customer(phone(), null, 0);
        Customer b = customer(phone(), null, 0);
        TestSession.clear();
        assertThrows(RuntimeException.class, () -> service.merge(a.getId(), b.getId()));
        assertThrows(RuntimeException.class, () -> service.anonymise(a.getId()));
        TestSession.admin();
        assertTrue(customerDAO.findById(a.getId()).isPresent());
        assertTrue(customerDAO.findById(b.getId()).isPresent());
    }

    // ---- TODO 2.88: the bookings of a customer made anonymous

    private final com.coffeeshop.coffeeshopmanagement.dao.ReservationDAO reservations = new com.coffeeshop.coffeeshopmanagement.dao.ReservationDAO();
    private final java.util.List<Integer> madeBookings = new java.util.ArrayList<>();

    @After
    public void removeBookings() throws java.sql.SQLException {
        try (java.sql.Connection c = com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig.getConnection();
             java.sql.Statement st = c.createStatement()) {
            for (int id : madeBookings) st.executeUpdate("DELETE FROM reservations WHERE id = " + id);
        }
        madeBookings.clear();
    }

    private int book(String name, String phone, int minutesFromNow) {
        var made = reservations.add(1, name, phone, AppClock.now().plusDays(3).plusMinutes(minutesFromNow), 2, null);
        madeBookings.add(made.id());
        return made.id();
    }

    private String[] bookingRow(int id) throws java.sql.SQLException {
        try (java.sql.Connection c = com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig.getConnection();
             java.sql.PreparedStatement st = c.prepareStatement("SELECT customer_name, phone, customer_id FROM reservations WHERE id = ?")) {
            st.setInt(1, id);
            try (java.sql.ResultSet rs = st.executeQuery()) {
                rs.next();
                return new String[]{rs.getString(1), rs.getString(2), rs.getString(3)};
            }
        }
    }

    @Test
    public void aBookingMadeWithACustomersPhoneKnowsTheCustomer() throws Exception {
        String phone = phone();
        Customer c = customer(phone, null, 0);
        int known = book("Khách đặt bàn", phone, 0);
        int stranger = book("Người lạ", phone(), 300);
        assertEquals(String.valueOf(c.getId()), bookingRow(known)[2]);
        assertNull(bookingRow(stranger)[2]);
    }

    @Test
    public void anonymiseBlanksTheNameAndPhoneOfTheirBookings() throws Exception {
        String phone = phone();
        Customer c = customer(phone, null, 0);
        int byId = book("Chị Lan", phone, 0);
        int other = book("Anh Bình", phone(), 300);
        // an older booking: no customer id, the phone written another way (+84 ... with spaces)
        int older = book("Chị Lan (cũ)", phone, 600);
        try (java.sql.Connection conn = com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig.getConnection();
             java.sql.PreparedStatement st = conn.prepareStatement("UPDATE reservations SET customer_id = NULL, phone = ? WHERE id = ?")) {
            st.setString(1, "+84 " + phone.substring(1, 4) + " " + phone.substring(4));
            st.setInt(2, older);
            st.executeUpdate();
        }

        service.anonymise(c.getId());

        String former = com.coffeeshop.coffeeshopmanagement.util.I18n.t("customers.former.name", c.getId());
        for (int id : new int[]{byId, older}) {
            String[] row = bookingRow(id);
            assertEquals(former, row[0]);
            assertNull("no phone left", row[1]);
            assertEquals(String.valueOf(c.getId()), row[2]);
        }
        String[] untouched = bookingRow(other);
        assertEquals("Anh Bình", untouched[0]);
        assertNotNull(untouched[1]);
    }

    @Test
    public void aMergeMovesTheBookingsToTheKeptCustomer() throws Exception {
        String phone = phone();
        Customer keep = customer(phone(), null, 0);
        Customer gone = customer(phone, null, 0);
        int id = book("Khách", phone, 0);
        service.merge(keep.getId(), gone.getId());
        assertEquals(String.valueOf(keep.getId()), bookingRow(id)[2]);
    }
}
