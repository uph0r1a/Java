package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.model.Category;
import com.coffeeshop.coffeeshopmanagement.model.Employee;
import com.coffeeshop.coffeeshopmanagement.model.Order;
import com.coffeeshop.coffeeshopmanagement.model.OrderItem;
import com.coffeeshop.coffeeshopmanagement.model.OrderStatus;
import com.coffeeshop.coffeeshopmanagement.model.PaymentMethod;
import com.coffeeshop.coffeeshopmanagement.model.Product;
import com.coffeeshop.coffeeshopmanagement.model.Role;
import com.coffeeshop.coffeeshopmanagement.model.ShiftClose;
import com.coffeeshop.coffeeshopmanagement.model.User;
import com.coffeeshop.coffeeshopmanagement.service.StaffService;
import com.coffeeshop.coffeeshopmanagement.util.AppClock;
import com.coffeeshop.coffeeshopmanagement.util.Session;
import com.coffeeshop.coffeeshopmanagement.util.TestSession;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * TODO 4.6: closing a shift. Every test works on a day of its own far in the future (the clock is fixed there), because shifts are
 * a chain of windows over the whole shop: a close made today by another test would otherwise be in the way.
 */
public class ShiftDAOTest {

    private static final AtomicInteger NEXT_DAY = new AtomicInteger(0);

    private final ShiftDAO shifts = new ShiftDAO();
    private final OrderDAO orderDAO = new OrderDAO();
    private final ProductDAO productDAO = new ProductDAO();
    private final CategoryDAO categoryDAO = new CategoryDAO();
    private final StaffService staff = new StaffService();

    private LocalDate savedToday;
    private LocalDateTime dayStart;   // 08:00 of this test's day: the shift starts here
    private Product product;
    private Employee an;
    private Employee binh;

    @BeforeClass
    public static void setUpDatabase() {
        TestSession.admin();
        TestDatabaseSupport.ensureReady();
    }

    @Before
    public void setUp() {
        TestSession.admin();
        savedToday = AppClock.today();
        dayStart = LocalDate.of(2200, 1, 1).plusDays(NEXT_DAY.incrementAndGet() * 3L).atTime(8, 0);
        AppClock.fix(dayStart.plusHours(4)); // "now" is 12:00 of this test's day

        Category category = new Category();
        category.setName("test-shift-category-" + UUID.randomUUID());
        category.setActive(true);
        categoryDAO.insert(category);
        product = new Product();
        product.setName("test-shift-product-" + UUID.randomUUID());
        product.setCategoryId(category.getId());
        product.setPrice(new BigDecimal("10000"));
        product.setStock(100000);
        product.setActive(true);
        productDAO.insert(product);
        an = person("An");
        binh = person("Binh");
    }

    @After
    public void restoreClock() {
        AppClock.pinDate(savedToday);
        TestSession.admin();
    }

    private Employee person(String name) {
        Employee e = new Employee();
        e.setFullName(name + " " + UUID.randomUUID().toString().substring(0, 6));
        e.setPhone("09" + String.format(java.util.Locale.ROOT, "%08d", new java.util.Random().nextInt(100_000_000)));
        return staff.save(e);
    }

    private static void signInAs(Employee e) {
        User u = new User();
        u.setId(1);
        u.setUsername("test-admin");
        u.setRole(Role.ADMIN);
        Session.start(u, e);
    }

    /** A paid order of {@code total} taken by {@code taker} at {@code paidAt}. */
    private void sale(Employee taker, String total, PaymentMethod method, LocalDateTime paidAt, OrderStatus status) {
        Order order = new Order();
        order.setOrderDate(paidAt);
        order.setEmployeeId(taker.getId());
        order.setStatus(status);
        order.setSubtotal(new BigDecimal(total));
        order.setDiscount(BigDecimal.ZERO);
        order.setTotal(new BigDecimal(total));
        order.setPaymentMethod(method);
        order.setPaidAt(paidAt);
        OrderItem line = new OrderItem();
        line.setProductId(product.getId());
        line.setProductName(product.getName());
        line.setQuantity(1);
        line.setUnitPrice(new BigDecimal(total));
        line.setLineTotal(new BigDecimal(total));
        orderDAO.insert(order, List.of(line));
    }

    @Test
    public void theWindowIsFromTheStartUpToButNotIncludingTheEnd() {
        sale(an, "100000", PaymentMethod.CASH, dayStart, OrderStatus.PAID);                 // exactly at the start: in
        sale(an, "50000", PaymentMethod.TRANSFER, dayStart.plusHours(1), OrderStatus.PAID);
        sale(an, "9000", PaymentMethod.CASH, dayStart.minusSeconds(1), OrderStatus.PAID);    // before: out
        sale(an, "7000", PaymentMethod.CASH, dayStart.plusHours(4), OrderStatus.PAID);       // exactly at the end: out
        sale(an, "3000", PaymentMethod.CASH, dayStart.plusHours(2), OrderStatus.CANCELLED);  // refunded: not a sale

        ShiftDAO.Summary s = shifts.summarize(dayStart, dayStart.plusHours(4));
        assertEquals(2, s.paidOrders());
        assertEquals(0, new BigDecimal("150000").compareTo(s.revenue()));
        assertEquals("only the cash sale is cash", 0, new BigDecimal("100000").compareTo(s.cashSales()));
    }

    @Test
    public void salesAreSplitByWhoTookTheMoney() {
        sale(an, "100000", PaymentMethod.CASH, dayStart.plusHours(1), OrderStatus.PAID);
        sale(binh, "30000", PaymentMethod.CASH, dayStart.plusHours(2), OrderStatus.PAID);
        sale(binh, "20000", PaymentMethod.MOMO, dayStart.plusHours(3), OrderStatus.PAID);

        ShiftDAO.Summary s = shifts.summarize(dayStart, dayStart.plusHours(4));
        ShiftDAO.EmployeeShare binhShare = s.byEmployee().stream()
                .filter(x -> x.employeeId() != null && x.employeeId() == binh.getId()).findFirst().orElseThrow();
        assertEquals(2, binhShare.orders());
        assertEquals(0, new BigDecimal("50000").compareTo(binhShare.revenue()));
        assertEquals(0, new BigDecimal("30000").compareTo(binhShare.cash()));
        assertEquals("the biggest share comes first", an.getId(), (int) s.byEmployee().get(0).employeeId());
    }

    @Test
    public void closingKeepsTheNumbersAndTheDifference() {
        signInAs(an);
        sale(an, "100000", PaymentMethod.CASH, dayStart.plusHours(1), OrderStatus.PAID);
        sale(an, "50000", PaymentMethod.TRANSFER, dayStart.plusHours(2), OrderStatus.PAID);

        ShiftClose closed = shifts.close(dayStart, new BigDecimal("500000"), new BigDecimal("590000"), "  két thiếu  ");
        assertEquals(2, closed.paidOrders());
        assertEquals(0, new BigDecimal("150000").compareTo(closed.revenue()));
        assertEquals(0, new BigDecimal("100000").compareTo(closed.cashSales()));
        assertEquals(0, new BigDecimal("600000").compareTo(closed.expectedCash()));
        assertEquals(0, new BigDecimal("-10000").compareTo(closed.difference()));
        assertEquals("két thiếu", closed.note());
        assertEquals(Integer.valueOf(an.getId()), closed.closedBy());
        assertEquals(dayStart.plusHours(4), closed.periodEnd());

        TestSession.admin();
        ShiftClose stored = shifts.findRecent(500).stream().filter(c -> c.id() == closed.id()).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("590000").compareTo(stored.countedCash()));
        assertEquals(dayStart, stored.periodStart());
        assertEquals(an.getFullName(), stored.closedByName());
    }

    @Test
    public void theNextShiftStartsWhereTheLastEndedAndStartsWithWhatWasCounted() {
        shifts.close(dayStart, new BigDecimal("100000"), new BigDecimal("250000"), null);
        assertEquals(dayStart.plusHours(4), shifts.lastCloseEnd().orElseThrow());
        assertEquals(dayStart.plusHours(4), shifts.currentShiftStart());
        assertEquals(0, new BigDecimal("250000").compareTo(shifts.lastCountedCash().orElseThrow()));
    }

    @Test
    public void aSaleBelongsToExactlyOneClosedShift() {
        sale(an, "10000", PaymentMethod.CASH, dayStart.plusHours(3), OrderStatus.PAID);
        ShiftClose first = shifts.close(dayStart, BigDecimal.ZERO, new BigDecimal("10000"), null);
        assertEquals(1, first.paidOrders());

        AppClock.fix(dayStart.plusHours(8)); // later the same day
        sale(an, "20000", PaymentMethod.CASH, dayStart.plusHours(6), OrderStatus.PAID);
        ShiftClose second = shifts.close(shifts.currentShiftStart(), BigDecimal.ZERO, new BigDecimal("20000"), null);
        assertEquals("the earlier sale is not counted again", 1, second.paidOrders());
        assertEquals(0, new BigDecimal("20000").compareTo(second.cashSales()));
        assertEquals(first.periodEnd(), second.periodStart());
    }

    @Test
    public void closingAShiftThatWasAlreadyClosedIsRefused() {
        shifts.close(dayStart, BigDecimal.ZERO, BigDecimal.ZERO, null);
        try {
            shifts.close(dayStart, BigDecimal.ZERO, BigDecimal.ZERO, null);
            fail("the window is gone");
        } catch (DataAccessException expected) {
            assertEquals(com.coffeeshop.coffeeshopmanagement.util.I18n.t("shift.val.stale"), expected.getMessage());
        }
    }

    @Test
    public void negativeOrMissingAmountsAreRefused() {
        try {
            shifts.close(dayStart, new BigDecimal("-1"), BigDecimal.ZERO, null);
            fail("negative");
        } catch (DataAccessException expected) {
            // fine
        }
        try {
            shifts.close(dayStart, BigDecimal.ZERO, null, null);
            fail("missing");
        } catch (DataAccessException expected) {
            // fine
        }
    }

    @Test
    public void nobodySignedInCannotCloseAndOnlyAdministratorsReadTheHistory() {
        TestSession.clear();
        try {
            shifts.close(dayStart, BigDecimal.ZERO, BigDecimal.ZERO, null);
            fail("not signed in");
        } catch (RuntimeException expected) {
            // fine
        }
        User cashier = new User();
        cashier.setId(2);
        cashier.setUsername("cashier");
        cashier.setRole(Role.EMPLOYEE);
        Session.start(cashier, an);
        ShiftClose closed = shifts.close(dayStart, BigDecimal.ZERO, BigDecimal.ZERO, null); // a cashier may close
        assertTrue(closed.id() > 0);
        try {
            shifts.findRecent(10);
            fail("history is for administrators");
        } catch (RuntimeException expected) {
            // fine
        }
    }

    @Test
    public void theReportCountsSalesPerEmployeeForDays() {
        sale(an, "40000", PaymentMethod.CASH, dayStart.plusHours(1), OrderStatus.PAID);
        sale(binh, "25000", PaymentMethod.TRANSFER, dayStart.plusHours(2), OrderStatus.PAID);
        sale(binh, "5000", PaymentMethod.CASH, dayStart.plusHours(3), OrderStatus.CANCELLED);

        List<ShiftDAO.EmployeeShare> rows = new ReportDAO().employeeSales(dayStart.toLocalDate(), dayStart.toLocalDate());
        assertEquals(2, rows.size());
        ShiftDAO.EmployeeShare binhRow = rows.stream().filter(x -> x.employeeId() == binh.getId()).findFirst().orElseThrow();
        assertEquals(1, binhRow.orders());
        assertEquals(0, new BigDecimal("25000").compareTo(binhRow.revenue()));
        assertEquals(0, BigDecimal.ZERO.compareTo(binhRow.cash()));
    }

    // ---- TODO 4.38: cash taken out of the drawer is taken off the cash the drawer should hold

    private void signInAsCashier() {
        User cashier = new User();
        cashier.setId(2);
        cashier.setUsername("cashier");
        cashier.setRole(Role.EMPLOYEE);
        Session.start(cashier, an);
    }

    @Test
    public void cashTakenOutOfTheDrawerIsTakenOffTheExpectedCash() {
        ExpenseDAO expenses = new ExpenseDAO();
        sale(an, "500000", PaymentMethod.CASH, dayStart.plusHours(1), OrderStatus.PAID);
        AppClock.fix(dayStart.plusHours(2));
        TestSession.admin();
        expenses.add(dayStart.toLocalDate(), new BigDecimal("900000"), "RENT", "paid by bank, not from the drawer", false);
        signInAsCashier();
        expenses.add(dayStart.toLocalDate(), new BigDecimal("120000"), "SUPPLIES", "cups", true);
        expenses.add(dayStart.toLocalDate(), new BigDecimal("30000"), "OTHER", "ice", true);
        AppClock.fix(dayStart.plusHours(4));

        assertEquals(0, new BigDecimal("150000").compareTo(shifts.cashOut(dayStart, dayStart.plusHours(4))));
        ShiftClose closed = shifts.close(dayStart, new BigDecimal("100000"), new BigDecimal("450000"), null);

        assertEquals("only what came out of the drawer counts", 0, new BigDecimal("150000").compareTo(closed.cashOut()));
        assertEquals("100000 + 500000 - 150000", 0, new BigDecimal("450000").compareTo(closed.expectedCash()));
        assertEquals(0, BigDecimal.ZERO.compareTo(closed.difference()));
        TestSession.admin();
        ShiftClose stored = shifts.findRecent(500).stream().filter(c -> c.id() == closed.id()).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("150000").compareTo(stored.cashOut()));
        assertEquals(0, new BigDecimal("450000").compareTo(stored.expectedCash()));
    }

    @Test
    public void aShiftWithNoCashOutIsAsBefore() {
        sale(an, "100000", PaymentMethod.CASH, dayStart.plusHours(1), OrderStatus.PAID);
        ShiftClose closed = shifts.close(dayStart, new BigDecimal("50000"), new BigDecimal("150000"), null);
        assertEquals(0, BigDecimal.ZERO.compareTo(closed.cashOut()));
        assertEquals(0, new BigDecimal("150000").compareTo(closed.expectedCash()));
    }

    @Test
    public void aCashOutBelongsToTheShiftDuringWhichItWasWrittenDown() {
        ExpenseDAO expenses = new ExpenseDAO();
        ShiftClose first = shifts.close(dayStart, BigDecimal.ZERO, BigDecimal.ZERO, null); // closed at 12:00
        assertEquals(0, BigDecimal.ZERO.compareTo(first.cashOut()));

        AppClock.fix(dayStart.plusHours(5));
        signInAsCashier();
        expenses.add(dayStart.toLocalDate(), new BigDecimal("20000"), "OTHER", null, true); // written after the first close
        AppClock.fix(dayStart.plusHours(8));
        ShiftClose second = shifts.close(shifts.currentShiftStart(), BigDecimal.ZERO, BigDecimal.ZERO, null);

        assertEquals("the later shift has it", 0, new BigDecimal("20000").compareTo(second.cashOut()));
        TestSession.admin();
        ShiftClose firstAgain = shifts.findRecent(500).stream().filter(c -> c.id() == first.id()).findFirst().orElseThrow();
        assertEquals("the closed shift keeps its own figure", 0, BigDecimal.ZERO.compareTo(firstAgain.cashOut()));
    }
}
