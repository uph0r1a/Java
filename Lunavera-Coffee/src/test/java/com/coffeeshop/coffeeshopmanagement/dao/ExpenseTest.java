package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.model.Expense;
import com.coffeeshop.coffeeshopmanagement.model.Role;
import com.coffeeshop.coffeeshopmanagement.model.User;
import com.coffeeshop.coffeeshopmanagement.service.ExpenseRules;
import com.coffeeshop.coffeeshopmanagement.util.AppClock;
import com.coffeeshop.coffeeshopmanagement.util.Session;
import com.coffeeshop.coffeeshopmanagement.util.TestSession;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * TODO 4.38: writing expenses down, who may, and the report's totals. Each test works on days of its own in the early 2000s (an
 * expense can be dated any day from 2000 up to today), so the range queries are not disturbed by other tests. The shift-close
 * side (cash taken out of the drawer) is in {@code ShiftDAOTest}, which owns the chain of shift windows.
 */
public class ExpenseTest {

    private static final AtomicInteger NEXT = new AtomicInteger(0);

    private final ExpenseDAO expenses = new ExpenseDAO();
    private LocalDate day;

    @BeforeClass
    public static void setUpDatabase() {
        TestSession.admin();
        TestDatabaseSupport.ensureReady();
    }

    @Before
    public void setUp() {
        TestSession.admin();
        day = LocalDate.of(2001, 1, 1).plusDays(NEXT.incrementAndGet() * 3L);
    }

    @After
    public void signAdminBack() {
        TestSession.admin();
    }

    private static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    private void signInAsCashier() {
        User cashier = new User();
        cashier.setId(2);
        cashier.setUsername("cashier");
        cashier.setRole(Role.EMPLOYEE);
        Session.start(cashier, null);
    }

    private static void assertRefused(Runnable action) {
        try {
            action.run();
            fail("expected the action to be refused");
        } catch (RuntimeException expected) {
            assertNotNull(expected.getMessage());
        }
    }

    @Test
    public void anAdministratorWritesAnExpenseAndFindsItAgainNewestFirst() {
        Expense rent = expenses.add(day, d("5000000"), ExpenseRules.RENT, "  October   rent ", false);
        Expense power = expenses.add(day.plusDays(1), d("800000"), ExpenseRules.UTILITIES, null, false);

        assertTrue(rent.id() > 0);
        assertEquals("October rent", rent.note());
        assertFalse(rent.fromDrawer());
        List<Expense> found = expenses.find(day, day.plusDays(1), 50);
        assertEquals(2, found.size());
        assertEquals("the later day comes first", power.id(), found.get(0).id());
        assertEquals(0, d("5000000").compareTo(found.get(1).amount()));
        assertEquals(day, found.get(1).spentOn());
        assertEquals(ExpenseRules.RENT, found.get(1).category());
        assertNotNull(found.get(1).createdAt());
    }

    @Test
    public void aMistakeCanBeDeletedByAnAdministrator() {
        Expense e = expenses.add(day, d("10000"), ExpenseRules.OTHER, null, false);
        assertTrue(expenses.delete(e.id()));
        assertTrue(expenses.find(day, day, 50).isEmpty());
        assertFalse("already gone", expenses.delete(e.id()));
    }

    @Test
    public void valuesThatAreNotAllowedAreRefusedAndNothingIsSaved() {
        assertRefused(() -> expenses.add(day, BigDecimal.ZERO, ExpenseRules.RENT, null, false));
        assertRefused(() -> expenses.add(day, d("-1"), ExpenseRules.RENT, null, false));
        assertRefused(() -> expenses.add(day, d("10.5"), ExpenseRules.RENT, null, false));
        assertRefused(() -> expenses.add(day, ExpenseRules.MAX_AMOUNT.add(BigDecimal.ONE), ExpenseRules.RENT, null, false));
        assertRefused(() -> expenses.add(day, d("1000"), "BEER", null, false));
        assertRefused(() -> expenses.add(AppClock.today().plusDays(1), d("1000"), ExpenseRules.RENT, null, false));
        assertRefused(() -> expenses.add(LocalDate.of(1999, 12, 31), d("1000"), ExpenseRules.RENT, null, false));
        assertTrue(expenses.find(day, day, 50).isEmpty());
    }

    @Test
    public void aCashierMayWriteDownDrawerCashAlwaysDatedTodayButNothingElse() {
        signInAsCashier();
        Expense out = expenses.add(day, d("50000"), ExpenseRules.SUPPLIES, "ly", true); // the day typed is ignored
        assertTrue(out.fromDrawer());
        assertEquals(AppClock.today(), out.spentOn());

        assertRefused(() -> expenses.add(day, d("50000"), ExpenseRules.RENT, null, false)); // not from the drawer: administrators only
        assertRefused(() -> expenses.delete(out.id()));
        assertRefused(() -> expenses.find(day, day, 10));
        TestSession.admin();
        assertTrue(expenses.delete(out.id()));
    }

    @Test
    public void nobodySignedInCannotWriteAnything() {
        TestSession.clear();
        assertRefused(() -> expenses.add(day, d("1000"), ExpenseRules.OTHER, null, true));
        assertRefused(() -> expenses.add(day, d("1000"), ExpenseRules.OTHER, null, false));
    }

    @Test
    public void theReportTotalsTheRangeByCategoryAndIncludesBothEndDays() {
        expenses.add(day, d("5000000"), ExpenseRules.RENT, null, false);
        expenses.add(day.plusDays(1), d("120000"), ExpenseRules.SUPPLIES, null, false);
        expenses.add(day.plusDays(1), d("30000"), ExpenseRules.SUPPLIES, null, false);
        expenses.add(day.plusDays(2), d("999"), ExpenseRules.OTHER, null, false); // outside the range below

        ReportDAO.Expenses report = new ReportDAO().expenses(day, day.plusDays(1));

        assertEquals(3, report.count());
        assertEquals(0, d("5150000").compareTo(report.total()));
        assertEquals(0, d("5000000").compareTo(report.byCategory().get(ExpenseRules.RENT)));
        assertEquals(0, d("150000").compareTo(report.byCategory().get(ExpenseRules.SUPPLIES)));
        assertNull(report.byCategory().get(ExpenseRules.OTHER));
        assertEquals("biggest category first", ExpenseRules.RENT, report.byCategory().keySet().iterator().next());
    }

    @Test
    public void aRangeWithNoExpenseIsZero() {
        ReportDAO.Expenses report = new ReportDAO().expenses(day, day);
        assertEquals(0, BigDecimal.ZERO.compareTo(report.total()));
        assertEquals(0, report.count());
        assertTrue(report.byCategory().isEmpty());
    }
}
