package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.model.AuditAction;
import com.coffeeshop.coffeeshopmanagement.model.AuditEntry;
import com.coffeeshop.coffeeshopmanagement.model.AuditTarget;
import com.coffeeshop.coffeeshopmanagement.model.Expense;
import com.coffeeshop.coffeeshopmanagement.model.Role;
import com.coffeeshop.coffeeshopmanagement.model.User;
import com.coffeeshop.coffeeshopmanagement.model.Voucher;
import com.coffeeshop.coffeeshopmanagement.service.ExpenseRules;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * TODO 2A.44: money leaving the drawer and the codes that give discounts leave a line in the audit log, in the same transaction as
 * the change, and deleting an expense keeps its figures in that line. Each test marks its rows with a text of its own and looks for
 * that text, so the other tests' lines do not disturb it.
 */
public class ExpenseVoucherAuditTest {

    private static final AtomicInteger NEXT = new AtomicInteger(0);

    private final ExpenseDAO expenses = new ExpenseDAO();
    private final VoucherDAO vouchers = new VoucherDAO();
    private final AuditDAO audit = new AuditDAO();
    private LocalDate day;
    private String mark;

    @BeforeClass
    public static void setUpDatabase() {
        TestSession.admin();
        TestDatabaseSupport.ensureReady();
    }

    @Before
    public void setUp() {
        TestSession.admin();
        day = LocalDate.of(2002, 1, 1).plusDays(NEXT.incrementAndGet() * 3L);
        mark = "aud" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    @After
    public void signAdminBack() {
        TestSession.admin();
    }

    private List<AuditEntry> lines(AuditAction action, String text) {
        return audit.find(new AuditDAO.Filter(null, null, action, null, text), 50, 0);
    }

    private int countOf(AuditAction action) {
        return audit.count(new AuditDAO.Filter(null, null, action, null, null));
    }

    @Test
    public void anExpenseLeavesALineWithItsFigures() {
        Expense saved = expenses.add(day, new BigDecimal("45000"), ExpenseRules.SUPPLIES, mark, false);
        List<AuditEntry> found = lines(AuditAction.EXPENSE_RECORDED, mark);
        assertEquals(1, found.size());
        AuditEntry line = found.get(0);
        assertEquals(AuditTarget.EXPENSE, line.targetType());
        assertEquals(Integer.valueOf(saved.id()), line.targetId());
        assertEquals(ExpenseRules.SUPPLIES, line.targetLabel());
        assertNull(line.oldValue());
        assertEquals("45000", line.newValue());
        assertEquals(day + "|0|" + mark, line.detail());
    }

    @Test
    public void cashTakenFromTheDrawerSaysSoAndNamesTheCashier() {
        User cashier = new User();
        cashier.setId(2);
        cashier.setUsername("cashier");
        cashier.setRole(Role.EMPLOYEE);
        Session.start(cashier, null);
        expenses.add(day, new BigDecimal("20000"), ExpenseRules.OTHER, mark, true);
        TestSession.admin();
        AuditEntry line = lines(AuditAction.EXPENSE_RECORDED, mark).get(0);
        assertTrue("from the drawer, dated today: " + line.detail(), line.detail().matches("\\d{4}-\\d{2}-\\d{2}\\|1\\|" + mark));
        assertEquals(Integer.valueOf(2), line.actorUserId());
    }

    @Test
    public void deletingAnExpenseKeepsTheAmountCategoryDayAndNote() {
        Expense saved = expenses.add(day, new BigDecimal("123000"), ExpenseRules.EQUIPMENT, mark, false);
        assertTrue(expenses.delete(saved.id()));
        List<AuditEntry> found = lines(AuditAction.EXPENSE_DELETED, mark);
        assertEquals(1, found.size());
        AuditEntry line = found.get(0);
        assertEquals(Integer.valueOf(saved.id()), line.targetId());
        assertEquals(ExpenseRules.EQUIPMENT, line.targetLabel());
        assertEquals("123000", line.oldValue());
        assertNull(line.newValue());
        assertEquals(day + "|0|" + mark, line.detail());
        assertTrue("the row itself is gone", expenses.find(day, day, 10).stream().noneMatch(e -> e.id() == saved.id()));
    }

    @Test
    public void deletingAnExpenseThatIsNotThereLeavesNoLine() {
        int before = countOf(AuditAction.EXPENSE_DELETED);
        assertFalse(expenses.delete(2_000_000_000));
        assertEquals(before, countOf(AuditAction.EXPENSE_DELETED));
    }

    @Test
    public void aRefusedExpenseLeavesNoLine() {
        int before = countOf(AuditAction.EXPENSE_RECORDED);
        try {
            expenses.add(day, BigDecimal.ZERO, ExpenseRules.OTHER, mark, false);
            fail("an amount of 0 must be refused");
        } catch (RuntimeException expected) {
            assertNotNull(expected.getMessage());
        }
        assertEquals(before, countOf(AuditAction.EXPENSE_RECORDED));
        assertTrue(lines(AuditAction.EXPENSE_RECORDED, mark).isEmpty());
    }

    @Test
    public void aNewVoucherLeavesALineWithItsRules() {
        String code = mark.toUpperCase();
        Voucher made = vouchers.add(code, Voucher.PERCENT, 10L, new BigDecimal("50000"), day, day.plusDays(30), 100, true);
        List<AuditEntry> found = lines(AuditAction.VOUCHER_CREATED, code);
        assertEquals(1, found.size());
        AuditEntry line = found.get(0);
        assertEquals(AuditTarget.VOUCHER, line.targetType());
        assertEquals(Integer.valueOf(made.id()), line.targetId());
        assertEquals(code, line.targetLabel());
        assertEquals("PERCENT:10", line.newValue());
        assertEquals("50000|" + day + "|" + day.plusDays(30) + "|100|1", line.detail());
    }

    @Test
    public void aVoucherWithNoLimitsHasEmptyParts() {
        String code = mark.toUpperCase();
        vouchers.add(code, Voucher.AMOUNT, 20000L, null, null, null, null);
        AuditEntry line = lines(AuditAction.VOUCHER_CREATED, code).get(0);
        assertEquals("AMOUNT:20000", line.newValue());
        assertEquals("0|||" + "|0", line.detail());
    }

    @Test
    public void switchingAVoucherLeavesALineOnlyWhenItChanges() {
        String code = mark.toUpperCase();
        Voucher made = vouchers.add(code, Voucher.AMOUNT, 5000L, null, null, null, null);
        vouchers.setActive(made.id(), false);
        vouchers.setActive(made.id(), false); // already off: nothing changed
        vouchers.setActive(made.id(), true);
        List<AuditEntry> found = lines(AuditAction.VOUCHER_SWITCHED, code);
        assertEquals(2, found.size());
        AuditEntry on = found.get(0); // newest first
        AuditEntry off = found.get(1);
        assertEquals("OFF", on.oldValue());
        assertEquals("ON", on.newValue());
        assertEquals("ON", off.oldValue());
        assertEquals("OFF", off.newValue());
        assertEquals(code, off.targetLabel());
    }

    @Test
    public void switchingAVoucherThatIsNotThereLeavesNoLine() {
        int before = countOf(AuditAction.VOUCHER_SWITCHED);
        vouchers.setActive(2_000_000_000, false);
        assertEquals(before, countOf(AuditAction.VOUCHER_SWITCHED));
    }
}
