package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.model.PaymentMethod;
import com.coffeeshop.coffeeshopmanagement.model.ShiftClose;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * TODO 4.53: the one-page sheet of a closed shift (the Z-report). {@link ShiftSheet} only turns a stored close and the detail of its
 * window into lines, so it is tested with no JavaFX, no printer and no database: the lines (what is on the paper), the CSV rows
 * (what is in a saved file) and the file name. The words are English here; one test checks that Vietnamese follows the language.
 */
public class ShiftSheetTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 4, 8, 0);
    private static final LocalDateTime END = LocalDateTime.of(2026, 11, 4, 12, 5);

    private String language;

    @Before
    public void useEnglish() {
        language = I18n.current();
        I18n.setLanguage(I18n.EN);
    }

    @After
    public void putBack() {
        I18n.setLanguage(language);
    }

    private static BigDecimal d(long dong) {
        return BigDecimal.valueOf(dong);
    }

    /** Opening 500,000 + cash sales 900,000 - cash out 150,000 = expected 1,250,000; counted 1,240,000, so 10,000 missing. */
    private static ShiftClose close(String by, String note, long counted) {
        return new ShiftClose(12, START, END, 3, by, 14, d(1_500_000), d(900_000), d(500_000), d(1_250_000), d(counted),
                d(counted - 1_250_000), note, d(150_000));
    }

    private static ShiftClose close() {
        return close("An", "két thiếu", 1_240_000);
    }

    private static ShiftSheet.Extras extras() {
        return new ShiftSheet.Extras(d(40_000), 2, d(25_000), d(13_000), d(200_000), List.of(
                new ShiftSheet.Method(PaymentMethod.CASH, 9, d(900_000)),
                new ShiftSheet.Method(PaymentMethod.TRANSFER, 5, d(600_000))));
    }

    /** The same detail with who gave the discounts: An 2 orders 10,000, Bình 1 order 3,000. */
    private static ShiftSheet.Extras extrasWithPeople() {
        return new ShiftSheet.Extras(d(40_000), 2, d(25_000), d(13_000), d(200_000), List.of(
                new ShiftSheet.Method(PaymentMethod.CASH, 9, d(900_000))),
                List.of(new ShiftSheet.DiscountShare("An", 2, d(10_000)), new ShiftSheet.DiscountShare("Bình, ca sáng", 1, d(3_000))));
    }

    private static List<ShiftSheet.Line> sheet(ShiftClose close, ShiftSheet.Extras extras, boolean copy) {
        return ShiftSheet.build(close, extras, "Lunavera Coffee", List.of("12 Test Street", " ", "Tel 0901 234 567"), I18n.EN, copy,
                LocalDateTime.of(2026, 11, 4, 12, 7));
    }

    private static ShiftSheet.Line pair(List<ShiftSheet.Line> lines, String label) {
        return lines.stream().filter(l -> l.kind() == ShiftSheet.Kind.PAIR && l.left().equals(label)).findFirst()
                .orElseThrow(() -> new AssertionError("no line \"" + label + "\" in " + lines));
    }

    private static boolean hasText(List<ShiftSheet.Line> lines, String text) {
        return lines.stream().anyMatch(l -> l.left().equals(text));
    }

    // ---- the head of the sheet

    @Test
    public void theHeadHasTheShopTheTitleAndTheShift() {
        List<ShiftSheet.Line> lines = sheet(close(), extras(), false);
        assertEquals(ShiftSheet.Kind.CENTER, lines.get(0).kind());
        assertEquals("the shop's name in capitals", "LUNAVERA COFFEE", lines.get(0).left());
        assertTrue(lines.get(0).bold());
        assertEquals("12 Test Street", lines.get(1).left());
        assertEquals("a blank detail line is left out", "Tel 0901 234 567", lines.get(2).left());
        assertTrue(hasText(lines, "SHIFT CLOSE SHEET"));
        assertTrue(hasText(lines, "Shift no. 12"));
        assertTrue(hasText(lines, "From: 08:00 04/11/2026"));
        assertTrue(hasText(lines, "To: 12:05 04/11/2026"));
        assertTrue(hasText(lines, "Closed by: An"));
    }

    @Test
    public void aSheetWithoutAShopNameStartsWithTheTitle() {
        List<ShiftSheet.Line> lines = ShiftSheet.build(close(), extras(), "  ", null, I18n.EN, false, null);
        assertEquals("SHIFT CLOSE SHEET", lines.get(0).left());
    }

    @Test
    public void aReprintSaysCopyAndTheFirstPrintDoesNot() {
        assertTrue(hasText(sheet(close(), extras(), true), "*** COPY - reprint ***"));
        assertFalse(hasText(sheet(close(), extras(), false), "*** COPY - reprint ***"));
    }

    @Test
    public void aMissingCashierNameShowsADash() {
        assertTrue(hasText(sheet(close(null, null, 1_250_000), extras(), false), "Closed by: -"));
        assertTrue(hasText(sheet(close("  ", null, 1_250_000), extras(), false), "Closed by: -"));
    }

    // ---- the money

    @Test
    public void theStoredFiguresOfTheCloseAreOnTheSheetExactly() {
        List<ShiftSheet.Line> lines = sheet(close(), extras(), false);
        assertEquals("14", pair(lines, "Paid orders").right());
        assertEquals("1.500.000đ", pair(lines, "Revenue (after refunds)").right());
        assertEquals("1500000", pair(lines, "Revenue (after refunds)").raw());
        assertEquals("500.000đ", pair(lines, "Cash at the start").right());
        assertEquals("900.000đ", pair(lines, "+ Cash taken (sales, gift cards)").right());
        assertEquals("1.250.000đ", pair(lines, "Cash expected").right());
        assertEquals("1.240.000đ", pair(lines, "Cash counted").right());
        assertTrue("expected and counted stand out", pair(lines, "Cash expected").bold() && pair(lines, "Cash counted").bold());
    }

    @Test
    public void cashTakenOutIsShownAsMinusAndNothingTakenOutStaysZero() {
        List<ShiftSheet.Line> lines = sheet(close(), extras(), false);
        assertEquals("-150.000đ", pair(lines, "- Cash taken out for expenses").right());
        assertEquals("-150000", pair(lines, "- Cash taken out for expenses").raw());

        ShiftClose none = new ShiftClose(13, START, END, null, "An", 0, d(0), d(0), d(0), d(0), d(0), d(0), null, null); // a close from before 4.38
        ShiftSheet.Line zero = pair(sheet(none, null, false), "- Cash taken out for expenses");
        assertEquals("0đ", zero.right());
        assertEquals("0", zero.raw());
    }

    @Test
    public void theDifferenceSaysWhichWayItWent() {
        ShiftSheet.Line missing = pair(sheet(close("An", null, 1_240_000), extras(), false), "Difference: money missing");
        assertEquals("-10.000đ", missing.right());
        assertEquals("-10000", missing.raw());

        ShiftSheet.Line over = pair(sheet(close("An", null, 1_260_000), extras(), false), "Difference: more than expected");
        assertEquals("a surplus carries a plus sign", "+10.000đ", over.right());

        ShiftSheet.Line fine = pair(sheet(close("An", null, 1_250_000), extras(), false), "Difference: it matches");
        assertEquals("0đ", fine.right());
        assertEquals("0", fine.raw());
    }

    @Test
    public void refundsAndDiscountsAreShownAsWhatWasTakenOff() {
        List<ShiftSheet.Line> lines = sheet(close(), extras(), false);
        ShiftSheet.Line refunds = pair(lines, "Refunds given (2), already taken off");
        assertEquals("-40.000đ", refunds.right());
        assertEquals("-40000", refunds.raw());
        assertEquals("-25.000đ", pair(lines, "Of which given back in cash").right());
        assertEquals("13.000đ", pair(lines, "Discounts given (already taken off)").right());
        assertEquals("200.000đ", pair(lines, "Gift cards sold for cash (not revenue)").right());
    }

    @Test
    public void theCashRefundLineIsLeftOutWhenNoRefundWentThroughTheDrawer() {
        ShiftSheet.Extras card = new ShiftSheet.Extras(d(80_000), 1, d(0), null, null, null);
        List<ShiftSheet.Line> lines = sheet(close(), card, false);
        assertEquals("-80.000đ", pair(lines, "Refunds given (1), already taken off").right());
        assertFalse(hasText(lines, "Of which given back in cash"));
    }

    @Test
    public void aShiftWithNoDetailStillHasEveryLineWithZeros() {
        for (ShiftSheet.Extras none : new ShiftSheet.Extras[]{null, ShiftSheet.Extras.none()}) {
            List<ShiftSheet.Line> lines = sheet(close(), none, false);
            assertEquals("0đ", pair(lines, "Refunds given (0), already taken off").right());
            assertEquals("0đ", pair(lines, "Discounts given (already taken off)").right());
            assertEquals("0đ", pair(lines, "Gift cards sold for cash (not revenue)").right());
            assertFalse("no method detail, no heading for it", hasText(lines, "By method of payment"));
        }
    }

    @Test
    public void theTakingsAreListedPerMethodOfPayment() {
        List<ShiftSheet.Line> lines = sheet(close(), extras(), false);
        assertTrue(hasText(lines, "By method of payment"));
        ShiftSheet.Line cash = pair(lines, "- " + PaymentMethod.CASH.label(I18n.EN) + " (9 orders)");
        assertEquals("900.000đ", cash.right());
        ShiftSheet.Line transfer = pair(lines, "- " + PaymentMethod.TRANSFER.label(I18n.EN) + " (5 orders)");
        assertEquals("600000", transfer.raw());
    }

    @Test
    public void aPaymentWithoutAKnownMethodIsCalledUnknown() {
        ShiftSheet.Extras odd = new ShiftSheet.Extras(null, 0, null, null, null, List.of(new ShiftSheet.Method(null, 1, d(5_000))));
        List<ShiftSheet.Line> lines = sheet(close(), odd, false);
        assertEquals("5.000đ", pair(lines, "- " + I18n.in(I18n.EN, "report.payment.unknown") + " (1 orders)").right());
    }

    @Test
    public void theDiscountsAreSplitPerCashierUnderTheTotal() {
        List<ShiftSheet.Line> lines = sheet(close(), extrasWithPeople(), false);
        int total = indexOf(lines, "Discounts given (already taken off)");
        assertEquals("13.000đ", ((ShiftSheet.Line) lines.get(total)).right());
        assertEquals("Discounts by cashier", lines.get(total + 1).left());
        assertEquals("- An (2 orders)", lines.get(total + 2).left());
        assertEquals("10.000đ", lines.get(total + 2).right());
        assertEquals("- Bình, ca sáng (1 orders)", lines.get(total + 3).left());
        assertEquals("3000", lines.get(total + 3).raw());
    }

    @Test
    public void noCashierBlockWhenNoDiscountWasGiven() {
        List<ShiftSheet.Line> lines = sheet(close(), extras(), false);
        assertFalse(hasText(lines, "Discounts by cashier"));
    }

    @Test
    public void aCashierWithoutANameIsCalledUnknown() {
        ShiftSheet.Extras odd = new ShiftSheet.Extras(null, 0, null, d(5_000), null, null,
                List.of(new ShiftSheet.DiscountShare(null, 1, d(5_000)), new ShiftSheet.DiscountShare("  ", 1, d(1_000))));
        List<ShiftSheet.Line> lines = sheet(close(), odd, false);
        assertEquals("5.000đ", pair(lines, "- Unknown (1 orders)").right());
        assertEquals(2, lines.stream().filter(l -> l.left().equals("- Unknown (1 orders)")).count());
    }

    @Test
    public void theCsvHasTheDiscountsPerCashierWithPlainNumbers() {
        List<String> rows = ShiftSheet.csvLines(sheet(close(), extrasWithPeople(), false));
        assertTrue(rows.toString(), rows.contains("An (2 orders),10000"));
        assertTrue("a name with a comma stays one cell", rows.contains("\"Bình, ca sáng (1 orders)\",3000"));
        assertTrue(rows.contains("Discounts by cashier"));
    }

    @Test
    public void theVietnameseSheetNamesTheCashiersToo() {
        List<ShiftSheet.Line> lines = ShiftSheet.build(close(), extrasWithPeople(), null, null, I18n.VI, false, null);
        assertTrue(hasText(lines, "Giảm giá theo thu ngân"));
        assertTrue(hasText(lines, "- An (2 đơn)"));
    }

    private static int indexOf(List<ShiftSheet.Line> lines, String label) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).left().equals(label)) return i;
        }
        throw new AssertionError("no line \"" + label + "\"");
    }

    // ---- the foot of the sheet

    @Test
    public void theNoteIsShownOnlyWhenThereIsOne() {
        assertTrue(hasText(sheet(close(), extras(), false), "Note: két thiếu"));
        assertFalse(sheet(close("An", null, 1_240_000), extras(), false).stream().anyMatch(l -> l.left().startsWith("Note:")));
        assertFalse(sheet(close("An", "   ", 1_240_000), extras(), false).stream().anyMatch(l -> l.left().startsWith("Note:")));
    }

    @Test
    public void thereAreTwoLinesToSignAndTheTimeOfPrintingIsLast() {
        List<ShiftSheet.Line> lines = sheet(close(), extras(), false);
        List<ShiftSheet.Line> signs = lines.stream().filter(l -> l.kind() == ShiftSheet.Kind.SIGNATURE).toList();
        assertEquals(2, signs.size());
        assertEquals("Cashier", signs.get(0).left());
        assertEquals("Owner / manager", signs.get(1).left());
        ShiftSheet.Line last = lines.get(lines.size() - 1);
        assertEquals(ShiftSheet.Kind.CENTER, last.kind());
        assertEquals("Printed 12:07 04/11/2026", last.left());

        List<ShiftSheet.Line> saved = ShiftSheet.build(close(), extras(), null, null, I18n.EN, false, null);
        assertNotEquals(ShiftSheet.Kind.CENTER, saved.get(saved.size() - 1).kind()); // no time of printing for a file
    }

    private static void assertNotEquals(Object unexpected, Object actual) {
        assertFalse("did not expect " + unexpected, unexpected.equals(actual));
    }

    @Test
    public void theWordsFollowTheLanguageGiven() {
        List<ShiftSheet.Line> lines = ShiftSheet.build(close(), extras(), "Lunavera", null, I18n.VI, false, null);
        assertTrue(hasText(lines, "PHIẾU CHỐT CA"));
        assertTrue(lines.stream().anyMatch(l -> l.left().equals("Chênh lệch: thiếu tiền")));
        assertFalse(hasText(lines, "SHIFT CLOSE SHEET"));
    }

    // ---- the saved file

    @Test
    public void theCsvCarriesPlainNumbersAndNoMoneySign() {
        List<String> rows = ShiftSheet.csvLines(sheet(close(), extras(), false));
        assertTrue(rows.toString(), rows.contains("Revenue (after refunds),1500000"));
        assertTrue(rows.toString(), rows.contains("Cash counted,1240000"));
        assertTrue(rows.toString(), rows.contains("Difference: money missing,-10000"));
        assertTrue("a label with a comma is quoted", rows.contains("\"Refunds given (2), already taken off\",-40000"));
        for (String row : rows) {
            assertFalse("no money sign in " + row, row.contains("đ"));
        }
    }

    @Test
    public void theCsvLeavesOutRulesGapsAndSignatureLines() {
        List<ShiftSheet.Line> sheet = sheet(close(), extras(), false);
        List<String> rows = ShiftSheet.csvLines(sheet);
        long wanted = sheet.stream().filter(l -> l.kind() == ShiftSheet.Kind.CENTER || l.kind() == ShiftSheet.Kind.LEFT
                || l.kind() == ShiftSheet.Kind.PAIR).count();
        assertEquals(wanted, rows.size());
        assertFalse(rows.contains("Cashier"));
        assertTrue(rows.contains("SHIFT CLOSE SHEET"));
        assertTrue(rows.contains("Note: két thiếu"));
    }

    @Test
    public void noRowOfTheCsvStartsWithAnApostropheThatExcelWouldShow() {
        // "- Cash (9 orders)" and "+ Cash taken (sales, gift cards)" look like a formula to Csv.cell, which then adds a '.
        for (String row : ShiftSheet.csvLines(sheet(close(), extras(), false))) {
            assertFalse("a row starts with an apostrophe: " + row, row.startsWith("'"));
        }
        assertTrue(ShiftSheet.csvLines(sheet(close(), extras(), false)).contains("Cash (9 orders),900000"));
    }

    @Test
    public void aTextThatCouldRunAsAFormulaIsDefusedInTheFile() {
        // the shop's name is typed by a person: "=1+1" must not become a formula when the file is opened in Excel
        List<String> rows = ShiftSheet.csvLines(ShiftSheet.build(close(), extras(), "=1+1", null, I18n.EN, false, null));
        assertEquals("'=1+1", rows.get(0));
    }

    // ---- the file name and the accountant's block

    @Test
    public void theFileNameHasTheShiftNumberAndTheTimeItEnded() {
        assertEquals("chot-ca-12-20261104-1205.csv", ShiftSheet.fileName(close()));
    }

    @Test
    public void theFileNameTakesTheExtensionOfTheFormat() {
        assertEquals("chot-ca-12-20261104-1205.pdf", ShiftSheet.fileName(close(), "pdf"));
        assertEquals("the plain name is still the CSV", "chot-ca-12-20261104-1205.csv", ShiftSheet.fileName(close()));
    }

    @Test
    public void theNameOrTheFilterDecidesWhetherASheetIsSavedAsPdf() {
        assertTrue(ShiftSheet.savesAsPdf("sheet.pdf", false));
        assertTrue("capitals do not matter", ShiftSheet.savesAsPdf("SHEET.PDF", false));
        assertFalse("a name that says csv wins over the filter", ShiftSheet.savesAsPdf("sheet.csv", true));
        assertTrue("no extension: the filter decides", ShiftSheet.savesAsPdf("sheet", true));
        assertFalse(ShiftSheet.savesAsPdf("sheet", false));
        assertFalse(ShiftSheet.savesAsPdf(null, false));
    }

    @Test
    public void theExtensionIsAddedOnlyWhenTheNameLacksIt() {
        assertEquals("sheet.pdf", ShiftSheet.withExtension("sheet", true));
        assertEquals("sheet.pdf", ShiftSheet.withExtension("sheet.pdf", true));
        assertEquals("SHEET.PDF", ShiftSheet.withExtension("SHEET.PDF", true));
        assertEquals("sheet.csv", ShiftSheet.withExtension("sheet", false));
        assertEquals("sheet.txt.csv", ShiftSheet.withExtension("sheet.txt", false));
    }

    @Test
    public void theReportBlockHasAHeaderAndOneRowPerShift() {
        assertTrue(ShiftSheet.csvBlock(null).isEmpty());
        assertTrue(ShiftSheet.csvBlock(List.of()).isEmpty());

        List<String> rows = ShiftSheet.csvBlock(List.of(close(), close("Bình, ca sáng", null, 1_250_000)));
        assertEquals("", rows.get(0));
        assertEquals(I18n.t("report.csv.shift.header"), rows.get(1));
        assertEquals(4, rows.size());
        assertEquals("12,04/11/2026 08:00,04/11/2026 12:05,An,14,1500000,900000,150000,1250000,1240000,-10000,két thiếu", rows.get(2));
        assertTrue("a name with a comma stays one cell", rows.get(3).contains("\"Bình, ca sáng\""));
        assertNotNull(rows.get(3));
    }
}
