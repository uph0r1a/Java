package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.dao.ComplimentaryDAO;
import com.coffeeshop.coffeeshopmanagement.dao.ReportDAO;
import com.coffeeshop.coffeeshopmanagement.model.AuditAction;
import com.coffeeshop.coffeeshopmanagement.model.AuditEntry;
import com.coffeeshop.coffeeshopmanagement.model.OrderItem;
import com.coffeeshop.coffeeshopmanagement.model.PaymentMethod;
import com.coffeeshop.coffeeshopmanagement.model.Role;
import com.coffeeshop.coffeeshopmanagement.service.ComplimentaryRules.Give;
import com.coffeeshop.coffeeshopmanagement.service.ComplimentaryRules.Kind;
import com.coffeeshop.coffeeshopmanagement.service.ComplimentaryRules.Problem;
import com.coffeeshop.coffeeshopmanagement.util.ComplimentaryText;
import org.junit.After;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.Assert.*;

/** TODO 4.61 (rule D26): who may give a free drink, how many, and how it reads. No database, no screen. */
public class ComplimentaryRulesTest {

    @After
    public void factoryRules() {
        ShopRules.resetForTests();
    }

    private static OrderItem line(int qty, long price) {
        OrderItem item = new OrderItem();
        item.setProductId(7);
        item.setProductName("Latte");
        item.setQuantity(qty);
        item.setUnitPrice(BigDecimal.valueOf(price));
        item.setLineTotal(BigDecimal.valueOf(price * qty));
        item.setSizeName("M");
        item.setNote("less ice");
        return item;
    }

    @Test
    public void aCashierMayGiveAStaffDrinkUpToTheDailyLimit() {
        Give mai = new Give(Kind.STAFF, 5, null);
        assertEquals(Problem.OK, ComplimentaryRules.check(Role.EMPLOYEE, mai, 1, 0, 2));
        assertEquals(Problem.OK, ComplimentaryRules.check(Role.EMPLOYEE, mai, 1, 1, 2));
        assertEquals("the third cup of the day", Problem.OVER_LIMIT, ComplimentaryRules.check(Role.EMPLOYEE, mai, 1, 2, 2));
        assertEquals("two cups on one bill after one given", Problem.OVER_LIMIT, ComplimentaryRules.check(Role.EMPLOYEE, mai, 2, 1, 2));
    }

    @Test
    public void theOwnerCountsAsOnePersonAndNeedsNoPersonChosen() {
        Give owner = new Give(Kind.OWNER, null, null);
        assertEquals(Problem.OK, ComplimentaryRules.check(Role.EMPLOYEE, owner, 2, 0, 2));
        assertEquals(Problem.OVER_LIMIT, ComplimentaryRules.check(Role.EMPLOYEE, owner, 1, 2, 2));
        assertEquals(Problem.PERSON_NOT_WANTED, ComplimentaryRules.check(Role.ADMIN, new Give(Kind.OWNER, 5, null), 1, 0, 2));
    }

    @Test
    public void aStaffDrinkNeedsAPerson() {
        assertEquals(Problem.PERSON_NEEDED, ComplimentaryRules.check(Role.ADMIN, new Give(Kind.STAFF, null, null), 1, 0, 2));
    }

    @Test
    public void aTastingIsForAnAdministratorAndNeedsANote() {
        assertEquals(Problem.NOTE_NEEDED, ComplimentaryRules.check(Role.ADMIN, new Give(Kind.TASTING, null, "   "), 1, 0, 2));
        assertEquals(Problem.OK, ComplimentaryRules.check(Role.ADMIN, new Give(Kind.TASTING, null, "milk supplier"), 3, 0, 2));
        assertEquals(Problem.TASTING_ADMIN_ONLY, ComplimentaryRules.check(Role.EMPLOYEE, new Give(Kind.TASTING, null, "milk supplier"), 1, 0, 2));
        assertEquals("nobody signed in is a cashier", Problem.TASTING_ADMIN_ONLY, ComplimentaryRules.check(null, new Give(Kind.TASTING, null, "x"), 1, 0, 2));
    }

    @Test
    public void anAdministratorHasNoDailyLimit() {
        assertEquals(Problem.OK, ComplimentaryRules.check(Role.ADMIN, new Give(Kind.STAFF, 5, null), 10, 40, 2));
        assertTrue(ComplimentaryRules.unlimited(Role.ADMIN));
        assertFalse(ComplimentaryRules.unlimited(Role.EMPLOYEE));
        assertFalse(ComplimentaryRules.unlimited(null));
    }

    @Test
    public void aLimitOfZeroSwitchesItOffForCashiersOnly() {
        Give mai = new Give(Kind.STAFF, 5, null);
        assertEquals(Problem.CASHIERS_MAY_NOT, ComplimentaryRules.check(Role.EMPLOYEE, mai, 1, 0, 0));
        assertEquals(Problem.OK, ComplimentaryRules.check(Role.ADMIN, mai, 1, 0, 0));
    }

    @Test
    public void anEmptyBillOrNoKindIsRefused() {
        assertEquals(Problem.NO_LINES, ComplimentaryRules.check(Role.ADMIN, new Give(Kind.OWNER, null, null), 0, 0, 2));
        assertEquals(Problem.NO_KIND, ComplimentaryRules.check(Role.ADMIN, null, 1, 0, 2));
        assertEquals(Problem.NO_KIND, ComplimentaryRules.check(Role.ADMIN, new Give(null, null, null), 1, 0, 2));
    }

    @Test
    public void cupsLeftNeverGoesBelowZero() {
        assertEquals(2, ComplimentaryRules.cupsLeft(0, 2));
        assertEquals(0, ComplimentaryRules.cupsLeft(5, 2));
        assertEquals(2, ComplimentaryRules.cupsLeft(-3, 2));
    }

    @Test
    public void theNoteIsCleanedAndCut() {
        assertNull(ComplimentaryRules.noteToStore(null));
        assertNull(ComplimentaryRules.noteToStore("  \t\n "));
        assertEquals("tasting with the supplier", ComplimentaryRules.noteToStore("  tasting \n with   the\tsupplier "));
        assertEquals(ComplimentaryRules.NOTE_MAX, ComplimentaryRules.noteToStore("x".repeat(500)).length());
        assertEquals("the Give record cleans it too", "a b", new Give(Kind.OWNER, null, " a   b ").note());
    }

    @Test
    public void linesAtNoChargeKeepTheDrinkButNotThePrice() {
        List<OrderItem> original = List.of(line(2, 45_000), line(1, 30_000));
        List<OrderItem> free = ComplimentaryRules.atNoCharge(original);
        assertEquals(2, free.size());
        for (int i = 0; i < free.size(); i++) {
            assertEquals(0, free.get(i).getUnitPrice().signum());
            assertEquals(0, free.get(i).getLineTotal().signum());
            assertEquals(original.get(i).getQuantity(), free.get(i).getQuantity());
            assertEquals(original.get(i).getProductId(), free.get(i).getProductId());
            assertEquals("M", free.get(i).getSizeName());
            assertEquals("less ice", free.get(i).getNote());
            assertNull(free.get(i).getPromoName());
        }
        assertEquals("the cart's own lines are not touched", 45_000, original.get(0).getUnitPrice().longValue());
        assertEquals(3, ComplimentaryRules.cups(original));
        assertEquals(120_000, ComplimentaryRules.listValue(original).longValue());
        assertEquals(0, ComplimentaryRules.cups(null));
        assertEquals(0, ComplimentaryRules.listValue(List.of()).signum());
    }

    @Test
    public void everyKindAndProblemHasAWordInBothLanguages() {
        for (Kind kind : Kind.values()) {
            assertFalse(kind.label("en").isBlank());
            assertFalse(kind.label("vi").isBlank());
            assertNotEquals("the two languages differ", kind.label("en"), kind.label("vi"));
            assertEquals(kind, Kind.fromDb(kind.name().toLowerCase()));
        }
        assertNull(Kind.fromDb("SOMETHING"));
        assertNull(Kind.fromDb(null));
        for (Problem problem : Problem.values()) {
            if (problem == Problem.OK) continue;
            String text = ComplimentaryRules.message(problem, "Mai", 2, 1, 2);
            assertFalse(problem + " has a sentence", text.isBlank() || text.startsWith("complimentary."));
        }
        try {
            ComplimentaryRules.message(Problem.OK, "Mai", 0, 1, 2);
            fail("OK has nothing to describe");
        } catch (IllegalArgumentException expected) {
            // fine
        }
    }

    @Test
    public void theMethodExistsAndIsNeverAWayToPay() {
        assertTrue(PaymentMethod.COMPLIMENTARY.isComplimentary());
        assertFalse(PaymentMethod.CASH.isComplimentary());
        assertFalse(PaymentMethod.COMPLIMENTARY.needsReceivedConfirmation());
        assertTrue("fits the 20-character column", PaymentMethod.COMPLIMENTARY.name().length() <= 20);
        assertEquals(PaymentMethod.COMPLIMENTARY, PaymentMethod.fromDb("complimentary"));
        assertFalse(PaymentMethod.COMPLIMENTARY.label().isBlank());
    }

    @Test
    public void theFreePaymentCarriesNothingElse() {
        CheckoutService.Payment payment = CheckoutService.Payment.free(new Give(Kind.OWNER, null, null));
        assertEquals(PaymentMethod.COMPLIMENTARY, payment.method());
        assertEquals(0, payment.discount().signum());
        assertEquals(0, payment.total().signum());
        assertEquals(0, payment.pointsRedeemed());
        assertNull(payment.voucherCode());
        assertTrue(payment.lines().isEmpty());
        assertFalse(payment.birthday());
        assertNotNull(payment.complimentary());
        assertNotNull("pricing the window does not lose it", payment.withPricedAt(java.time.LocalDateTime.now()).complimentary());
        assertNull("an ordinary payment has none", new CheckoutService.Payment(PaymentMethod.CASH, BigDecimal.ZERO, null, BigDecimal.TEN).complimentary());
    }

    @Test
    public void theCashiersLimitIsASettingWithItsOwnRange() {
        assertEquals("2 until the owner changes it", 2, ShopRules.DEFAULTS.freeDrinksPerDay());
        assertEquals(2, ComplimentaryRules.cashierCupsPerDay());
        ShopRules.Values five = ShopRules.parse("10", "10000", "1000", "50", "20", "100", "500", "10", "90", "15", "16", "0", "208", "5");
        assertNotNull(five);
        assertEquals(5, five.freeDrinksPerDay());
        ShopRules.set(five);
        assertEquals(5, ComplimentaryRules.cashierCupsPerDay());
        assertEquals("0 is allowed: none", 0, ShopRules.parse("10", "10000", "1000", "50", "20", "100", "500", "10", "90", "15", "16", "0", "208", "0").freeDrinksPerDay());
        assertNull("too many", ShopRules.parse("10", "10000", "1000", "50", "20", "100", "500", "10", "90", "15", "16", "0", "208", "21"));
        assertNull("not a number", ShopRules.parse("10", "10000", "1000", "50", "20", "100", "500", "10", "90", "15", "16", "0", "208", "many"));
        assertEquals("older forms without the box mean 2", 2, ShopRules.parse("10", "10000", "1000", "50", "20", "100", "500").freeDrinksPerDay());
        assertEquals("blank means the factory value", 2, ShopRules.parse("10", "10000", "1000", "50", "20", "100", "500", "10", "90", "15", "16", "0", "208", "").freeDrinksPerDay());
        boolean logged = ShopRules.changes(ShopRules.DEFAULTS, five).stream().anyMatch(c -> c.name().equals("freeDrinksPerDay") && c.before() == 2 && c.after() == 5);
        assertTrue("a change of the limit is audited like the other rules", logged);
    }

    @Test
    public void theAuditLineSaysWhoGotItAndWhy() {
        AuditEntry entry = new AuditEntry(1, java.time.LocalDateTime.now(), 1, 1, "an", AuditAction.COMPLIMENTARY_GIVEN.name(), "STAFF", 5, "Mai", null, "90000",
                "STAFF|123|2|after the late shift");
        String detail = AuditText.detail(entry);
        assertTrue(detail, detail.contains("123") && detail.contains("2") && detail.contains("after the late shift"));
        assertTrue(detail, detail.contains(Kind.STAFF.label()));
        assertTrue("the value reads as money", AuditText.change(entry).contains("90"));
        assertFalse(AuditText.action(entry).startsWith("audit.action."));
    }

    private static ReportDAO.Summary summary(ComplimentaryDAO.Totals totals, List<ComplimentaryDAO.PersonLine> people) {
        return new ReportDAO.Summary(10, BigDecimal.valueOf(900_000), BigDecimal.valueOf(10_000), 0, totals, people);
    }

    @Test
    public void theReportSaysNothingWhenNothingWasGivenAway() {
        assertEquals("", ComplimentaryText.summaryLine(null));
        assertEquals("", ComplimentaryText.summaryLine(new ReportDAO.Summary(3, BigDecimal.ONE, BigDecimal.ZERO, 0)));
        assertEquals("", ComplimentaryText.summaryLine(summary(ComplimentaryDAO.Totals.NONE, List.of())));
        List<String> csv = ComplimentaryText.csvLines(summary(ComplimentaryDAO.Totals.NONE, List.of()));
        assertEquals("the three totals are always written", 3, csv.size());
    }

    @Test
    public void theReportShowsCupsKindsCostAndValue() {
        ComplimentaryDAO.Totals totals = new ComplimentaryDAO.Totals(3, 5, BigDecimal.valueOf(40_000), BigDecimal.valueOf(190_000), 1);
        List<ComplimentaryDAO.PersonLine> people = List.of(
                new ComplimentaryDAO.PersonLine(Kind.STAFF, 5, "Mai, \"the late one\"", 2, 3, BigDecimal.valueOf(24_000), BigDecimal.valueOf(120_000)),
                new ComplimentaryDAO.PersonLine(Kind.OWNER, null, null, 1, 2, BigDecimal.valueOf(16_000), BigDecimal.valueOf(70_000)));
        String line = ComplimentaryText.summaryLine(summary(totals, people));
        assertTrue("starts with a line break so it can be appended", line.startsWith("\n"));
        assertTrue(line, line.contains("5") && line.contains("3") && line.contains("40"));
        assertTrue("the kinds are named", line.contains(Kind.STAFF.label()) && line.contains(Kind.OWNER.label()));
        assertFalse("no TASTING cups, so not named", line.contains(Kind.TASTING.label()));
        assertTrue("says how many cups have no cost", line.contains("1"));
        List<String> csv = ComplimentaryText.csvLines(summary(totals, people));
        assertEquals(3 + 1 + 2, csv.size());
        assertTrue("a name with a comma and quotes is one CSV cell", csv.get(4).contains("\"Mai, \"\"the late one\"\"\""));
    }

    @Test
    public void theAverageBillLeavesFreeBillsOut() {
        ComplimentaryDAO.Totals totals = new ComplimentaryDAO.Totals(2, 2, BigDecimal.valueOf(10_000), BigDecimal.valueOf(60_000), 0);
        assertEquals("900.000 over 8 sold bills, not 10", 112_500, summary(totals, List.of()).averageOrderValue().longValue());
        assertEquals(90_000, new ReportDAO.Summary(10, BigDecimal.valueOf(900_000), BigDecimal.ZERO, 0).averageOrderValue().longValue());
        assertEquals("only free bills: nothing to average", 0,
                new ReportDAO.Summary(2, BigDecimal.ZERO, BigDecimal.ZERO, 0, totals, List.of()).averageOrderValue().signum());
    }
}
