package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig;
import com.coffeeshop.coffeeshopmanagement.model.AuditAction;
import com.coffeeshop.coffeeshopmanagement.model.AuditEntry;
import com.coffeeshop.coffeeshopmanagement.model.AuditTarget;
import com.coffeeshop.coffeeshopmanagement.service.AuditRules;
import com.coffeeshop.coffeeshopmanagement.util.AppClock;
import com.coffeeshop.coffeeshopmanagement.util.Permissions;
import com.coffeeshop.coffeeshopmanagement.util.TestSession;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.sql.Connection;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * TODO 4.2: writing and reading the audit log. Every test marks its lines with a word of its own (the log is shared by the whole test
 * run) and reads them back through that word; the date tests fix the clock on a day of their own far in the future.
 */
public class AuditLogDaoTest {

    private static final AtomicInteger NEXT_DAY = new AtomicInteger(0);

    private final AuditDAO audit = new AuditDAO();
    private LocalDate savedToday;
    private String mark;

    @BeforeClass
    public static void setUpDatabase() {
        TestSession.admin();
        TestDatabaseSupport.ensureReady();
    }

    @Before
    public void setUp() {
        TestSession.admin();
        savedToday = AppClock.today();
        mark = "mark" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    @After
    public void restore() {
        AppClock.pinDate(savedToday);
        TestSession.admin();
    }

    private List<AuditEntry> mine() {
        return audit.find(new AuditDAO.Filter(null, null, null, null, mark), 100, 0);
    }

    private void line(AuditAction action, String label, String oldV, String newV, String detail) {
        AuditDAO.recordQuietly(action, AuditTarget.PRODUCT, 1, label, oldV, newV, detail);
    }

    @Test
    public void aLineKeepsWhoWhatAndBeforeAndAfter() {
        line(AuditAction.PRODUCT_PRICE, "Latte " + mark, "45000", "50000", "  to   fit \n the rule ");
        AuditEntry e = mine().get(0);
        assertEquals(AuditAction.PRODUCT_PRICE.name(), e.action());
        assertEquals(AuditTarget.PRODUCT, e.targetType());
        assertEquals(Integer.valueOf(1), e.targetId());
        assertEquals("Latte " + mark, e.targetLabel());
        assertEquals("45000", e.oldValue());
        assertEquals("50000", e.newValue());
        assertEquals("to fit the rule", e.detail());
        assertEquals(Integer.valueOf(1), e.actorUserId());
        assertEquals("test-admin", e.actorName());
        assertNotNull(e.loggedAt());
    }

    @Test
    public void nobodySignedInIsTheSystem() {
        TestSession.clear();
        line(AuditAction.RESTORE_APPLIED, "System " + mark, null, null, null);
        TestSession.admin();
        AuditEntry e = mine().get(0);
        assertEquals(AuditRules.SYSTEM_ACTOR, e.actorName());
        assertNull(e.actorUserId());
    }

    @Test
    public void textIsCutAtTheColumnSize() {
        line(AuditAction.PRODUCT_PRICE, "L " + mark, "y".repeat(900), null, null);
        assertEquals(AuditRules.VALUE_MAX, mine().get(0).oldValue().length());
    }

    @Test
    public void theNewestLineComesFirstAndPagingWorks() {
        for (int i = 1; i <= 5; i++) line(AuditAction.PRODUCT_STOCK, "P" + i + " " + mark, null, String.valueOf(i), null);
        List<AuditEntry> all = mine();
        assertEquals(5, all.size());
        assertEquals("5", all.get(0).newValue());
        assertEquals("1", all.get(4).newValue());
        AuditDAO.Filter f = new AuditDAO.Filter(null, null, null, null, mark);
        assertEquals(5, audit.count(f));
        List<AuditEntry> second = audit.find(f, 2, 2);
        assertEquals(2, second.size());
        assertEquals("3", second.get(0).newValue());
    }

    @Test
    public void filtersByActionActorAndText() {
        line(AuditAction.PRODUCT_PRICE, "A " + mark, "1", "2", null);
        line(AuditAction.PRODUCT_COST, "B " + mark, "1", "2", "reason " + mark);
        assertEquals(1, audit.find(new AuditDAO.Filter(null, null, AuditAction.PRODUCT_COST, null, mark), 10, 0).size());
        assertEquals(2, audit.find(new AuditDAO.Filter(null, null, null, "TEST-ADMIN", mark), 10, 0).size());
        assertEquals(0, audit.find(new AuditDAO.Filter(null, null, null, "nobody-called-this", mark), 10, 0).size());
        assertEquals("the text also searches the detail", 1,
                audit.find(new AuditDAO.Filter(null, null, null, null, "reason " + mark), 10, 0).size());
    }

    @Test
    public void aPercentSignInTheSearchIsLiteral() {
        line(AuditAction.PRODUCT_PRICE, "Sale 100% " + mark, null, null, null);
        line(AuditAction.PRODUCT_PRICE, "Sale 1000 " + mark, null, null, null);
        assertEquals(1, audit.find(new AuditDAO.Filter(null, null, null, null, "100% " + mark), 10, 0).size());
    }

    @Test
    public void aNumberFindsTheLinesAboutThatTarget() {
        AuditDAO.recordQuietly(AuditAction.ORDER_REFUNDED, AuditTarget.ORDER, 987654, null, "1", null, mark);
        assertEquals(1, audit.find(new AuditDAO.Filter(null, null, null, null, "987654"), 10, 0).size());
    }

    @Test
    public void theDayFilterIncludesBothEndDays() {
        LocalDate day = LocalDate.of(2300, 1, 1).plusDays(NEXT_DAY.incrementAndGet() * 5L);
        AppClock.fix(day.minusDays(1).atTime(23, 59, 59));
        line(AuditAction.PRODUCT_PRICE, "before " + mark, null, null, null);
        AppClock.fix(day.atTime(0, 0, 0));
        line(AuditAction.PRODUCT_PRICE, "first " + mark, null, null, null);
        AppClock.fix(day.plusDays(1).atTime(23, 59, 59));
        line(AuditAction.PRODUCT_PRICE, "last " + mark, null, null, null);
        AppClock.fix(day.plusDays(2).atTime(0, 0, 0));
        line(AuditAction.PRODUCT_PRICE, "after " + mark, null, null, null);
        List<AuditEntry> inside = audit.find(new AuditDAO.Filter(day, day.plusDays(1), null, null, mark), 10, 0);
        assertEquals(2, inside.size());
        assertEquals("last " + mark, inside.get(0).targetLabel());
        assertEquals("first " + mark, inside.get(1).targetLabel());
        assertTrue(new AuditDAO.Filter(day.plusDays(1), day, null, null, null).hasReversedDates());
    }

    @Test
    public void aLineWrittenInARolledBackTransactionIsNotThere() throws Exception {
        try (Connection c = DatabaseConfig.getConnection()) {
            c.setAutoCommit(false);
            AuditDAO.write(c, AuditAction.PRODUCT_PRICE, AuditTarget.PRODUCT, 1, "rolled " + mark, null, null, null);
            c.rollback();
        }
        assertEquals(0, mine().size());
    }

    @Test
    public void scrubbingRewritesTheNamesOfOnePerson() throws Exception {
        AuditDAO.recordQuietly(AuditAction.CUSTOMER_MERGED, AuditTarget.CUSTOMER, 424242, "Lan " + mark, "#9 Hoa", null, "2|5");
        try (Connection c = DatabaseConfig.getConnection()) {
            AuditDAO.scrubTarget(c, AuditTarget.CUSTOMER, 424242, "Former customer #424242", true);
        }
        AuditEntry e = audit.find(new AuditDAO.Filter(null, null, null, null, "424242"), 10, 0).get(0);
        assertEquals("Former customer #424242", e.targetLabel());
        assertNull(e.oldValue());
        assertNull(e.detail());
        assertEquals("what was done stays", AuditAction.CUSTOMER_MERGED.name(), e.action());
    }

    @Test
    public void onlyAnAdministratorMayRead() {
        TestSession.clear();
        try {
            audit.find(AuditDAO.Filter.NONE, 10, 0);
            fail("not signed in");
        } catch (Permissions.PermissionDeniedException expected) {
            // fine
        }
        try {
            audit.count(AuditDAO.Filter.NONE);
            fail("not signed in");
        } catch (Permissions.PermissionDeniedException expected) {
            // fine
        }
    }
}
