package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.model.AuditAction;
import com.coffeeshop.coffeeshopmanagement.model.AuditEntry;
import com.coffeeshop.coffeeshopmanagement.model.AuditTarget;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import org.junit.Test;

import java.time.LocalDateTime;

import static org.junit.Assert.*;

/** TODO 4.2: what the administrator screen shows for a stored audit line. */
public class AuditTextTest {

    private static AuditEntry entry(AuditAction action, String targetType, Integer targetId, String label, String oldValue,
                                    String newValue, String detail) {
        return new AuditEntry(1, LocalDateTime.of(2026, 10, 28, 9, 0), 1, null, "An", action.name(), targetType, targetId, label,
                oldValue, newValue, detail);
    }

    /** TODO 4.57: the one line left after the test data was cleared reads as words, in both counts of customers. */
    @Test
    public void theLineOfStartingToSellReadsAsWords() {
        AuditEntry kept = entry(AuditAction.SALES_STARTED, AuditTarget.SHOP, null, "Lunavera Coffee", null, "before-opening-20261105-060000.zip", "12|47|0");
        assertEquals(I18n.t("audit.target.shop") + " Lunavera Coffee", AuditText.target(kept));
        assertEquals(I18n.t("audit.detail.sales", "12", "47"), AuditText.detail(kept));
        AuditEntry both = entry(AuditAction.SALES_STARTED, AuditTarget.SHOP, null, "Lunavera Coffee", null, "x.zip", "12|47|1");
        assertEquals(I18n.t("audit.detail.sales.customers", "12", "47"), AuditText.detail(both));
        assertNotEquals(AuditText.detail(kept), AuditText.detail(both));
        assertEquals("before-opening-20261105-060000.zip", AuditText.change(kept));
    }

    @Test
    public void thePinAuditLinesReadInWords() {
        AuditEntry off = entry(AuditAction.PIN_SWITCHED_OFF, AuditTarget.ACCOUNT, 7, "bob", null, null, "5");
        assertEquals(I18n.t("audit.detail.pin.off", "5"), AuditText.detail(off));
        assertEquals(I18n.t("audit.target.account") + " bob", AuditText.target(off));
        AuditEntry closed = entry(AuditAction.PIN_TILL_CLOSED, AuditTarget.ACCOUNT, 3, "ann", null, null, "10|15");
        assertEquals(I18n.t("audit.detail.pin.till", "10", "15"), AuditText.detail(closed));
        assertNotEquals("the detail is turned into words", "10|15", AuditText.detail(closed));
    }

    @Test
    public void theGiftCardGuessingLineReadsInWords() {
        AuditEntry e = entry(AuditAction.GIFT_CARD_GUESSING, AuditTarget.GIFT_CARD, null, null, null, null, "5|60");
        assertEquals(I18n.t("audit.detail.giftcard.guess", "5", "60"), AuditText.detail(e));
    }

    @Test
    public void everyActionHasAName() {
        for (AuditAction a : AuditAction.values()) {
            assertNotEquals("no text for " + a, a.labelKey(), I18n.t(a.labelKey()));
        }
    }

    @Test
    public void storedNamesRoundTripAndAnUnknownOneIsKept() {
        for (AuditAction a : AuditAction.values()) {
            assertEquals(a, AuditAction.fromDb(a.name()).orElseThrow());
        }
        assertTrue(AuditAction.fromDb("FROM_A_NEWER_VERSION").isEmpty());
        AuditEntry unknown = new AuditEntry(2, LocalDateTime.now(), null, null, "x", "FROM_A_NEWER_VERSION", null, null, null, null, null, null);
        assertEquals("FROM_A_NEWER_VERSION", AuditText.action(unknown));
    }

    @Test
    public void moneyIsShownWithTheCurrencySign() {
        AuditEntry e = entry(AuditAction.PRODUCT_PRICE, AuditTarget.PRODUCT, 3, "Latte", "45000", "50000", null);
        assertEquals("45.000đ \u2192 50.000đ", AuditText.change(e));
        assertEquals(I18n.t("audit.target.product") + " Latte", AuditText.target(e));
    }

    @Test
    public void aCostThatWasNotSetShowsOnlyTheNewValue() {
        AuditEntry e = entry(AuditAction.PRODUCT_COST, AuditTarget.PRODUCT, 3, "Latte", null, "12000", null);
        assertEquals("12.000đ", AuditText.change(e));
    }

    @Test
    public void plainNumbersStayPlain() {
        AuditEntry e = entry(AuditAction.PRODUCT_STOCK, AuditTarget.PRODUCT, 3, "Latte", "10", "7", null);
        assertEquals("10 \u2192 7", AuditText.change(e));
    }

    @Test
    public void rolesAndStatusesAreWords() {
        AuditEntry e = entry(AuditAction.ACCOUNT_ROLE_CHANGED, AuditTarget.ACCOUNT, 5, "an", "EMPLOYEE", "ADMIN", null);
        assertEquals(I18n.t("role.employee") + " \u2192 " + I18n.t("role.admin"), AuditText.change(e));
        AuditEntry created = entry(AuditAction.ACCOUNT_CREATED, AuditTarget.ACCOUNT, 5, "an", null, "ADMIN", "LOCKED");
        assertEquals(I18n.t("accounts.status.locked"), AuditText.detail(created));
    }

    @Test
    public void aTargetWithoutANameShowsItsNumber() {
        AuditEntry e = entry(AuditAction.TABLE_EMPTIED, AuditTarget.ORDER, 12, null, "90000", null, "5");
        assertEquals(I18n.t("audit.target.order") + " #12", AuditText.target(e));
        assertEquals(I18n.t("audit.detail.table", "5"), AuditText.detail(e));
        assertEquals("90.000đ", AuditText.change(e));
    }

    @Test
    public void shiftAndMergeDetailsAreWords() {
        AuditEntry shift = entry(AuditAction.SHIFT_CLOSED, AuditTarget.SHIFT, 7, "#7", "600000", "590000", "-10000");
        assertEquals("600.000đ \u2192 590.000đ", AuditText.change(shift));
        assertEquals(I18n.t("audit.detail.difference", "-10.000đ"), AuditText.detail(shift));
        AuditEntry merge = entry(AuditAction.CUSTOMER_MERGED, AuditTarget.CUSTOMER, 4, "Lan", "#9 Lan B", null, "3|20");
        assertEquals(I18n.t("audit.detail.merge", "3", "20"), AuditText.detail(merge));
    }

    @Test
    public void aReasonIsShownAsTyped() {
        AuditEntry e = entry(AuditAction.ORDER_REFUNDED, AuditTarget.ORDER, 8, "#8", "45000", null, "khách trả lại");
        assertEquals("khách trả lại", AuditText.detail(e));
    }

    @Test
    public void nothingToShowIsEmpty() {
        AuditEntry e = entry(AuditAction.PASSWORD_RESET, AuditTarget.ACCOUNT, 5, "an", null, null, null);
        assertEquals("", AuditText.change(e));
        assertEquals("", AuditText.detail(e));
    }
}
