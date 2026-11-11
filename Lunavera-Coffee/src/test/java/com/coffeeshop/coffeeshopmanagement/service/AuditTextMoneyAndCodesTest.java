package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.model.AuditAction;
import com.coffeeshop.coffeeshopmanagement.model.AuditEntry;
import com.coffeeshop.coffeeshopmanagement.model.AuditTarget;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.time.LocalDateTime;

import static org.junit.Assert.*;

/**
 * TODO 2A.44: how the audit lines for expenses, vouchers, the transfer QR and the shop's settings read on the administrator screen,
 * in English and in Vietnamese. Exact English words are checked on purpose: comparing with {@code I18n.t} of the same key would
 * also pass for a key nobody wrote.
 */
public class AuditTextMoneyAndCodesTest {

    private String language;

    @Before
    public void useEnglish() {
        language = I18n.current();
        I18n.setLanguage(I18n.EN);
    }

    @After
    public void restore() {
        I18n.setLanguage(language);
    }

    private static AuditEntry entry(AuditAction action, String targetType, Integer targetId, String label, String oldValue,
                                    String newValue, String detail) {
        return new AuditEntry(1, LocalDateTime.of(2026, 11, 7, 9, 0), 1, null, "An", action.name(), targetType, targetId, label,
                oldValue, newValue, detail);
    }

    @Test
    public void aDeletedExpenseKeepsItsFiguresOnScreen() {
        AuditEntry e = entry(AuditAction.EXPENSE_DELETED, AuditTarget.EXPENSE, 17, "SUPPLIES", "45000", null, "2026-11-05|0|milk for the week");
        assertEquals("Expense deleted", AuditText.action(e));
        assertEquals("Expense SUPPLIES", AuditText.target(e));
        assertEquals(com.coffeeshop.coffeeshopmanagement.util.CurrencyUtil.format(new java.math.BigDecimal("45000")), AuditText.change(e));
        assertEquals("Spent on 05/11/2026 \u2014 milk for the week", AuditText.detail(e));
    }

    @Test
    public void anExpenseFromTheDrawerSaysSo() {
        AuditEntry e = entry(AuditAction.EXPENSE_RECORDED, AuditTarget.EXPENSE, 18, "OTHER", null, "20000", "2026-11-07|1|");
        assertEquals("Spent on 07/11/2026, taken from the drawer", AuditText.detail(e));
        assertEquals(com.coffeeshop.coffeeshopmanagement.util.CurrencyUtil.format(new java.math.BigDecimal("20000")), AuditText.change(e));
    }

    @Test
    public void aNoteWithABarInItIsShownWhole() {
        AuditEntry e = entry(AuditAction.EXPENSE_DELETED, AuditTarget.EXPENSE, 19, "OTHER", "1000", null, "2026-11-07|0|a | b | c");
        assertEquals("Spent on 07/11/2026 \u2014 a | b | c", AuditText.detail(e));
    }

    @Test
    public void aNoteThatIsNotInTheStoredShapeIsShownAsItIs() {
        AuditEntry e = entry(AuditAction.EXPENSE_RECORDED, AuditTarget.EXPENSE, 20, "OTHER", null, "1000", "typed by an older version");
        assertEquals("typed by an older version", AuditText.detail(e));
    }

    @Test
    public void aNewVoucherReadsAsItsValueAndLimits() {
        AuditEntry percent = entry(AuditAction.VOUCHER_CREATED, AuditTarget.VOUCHER, 4, "WELCOME10", null, "PERCENT:10", "50000|2026-11-01|2026-11-30|100|1");
        assertEquals("Voucher WELCOME10", AuditText.target(percent));
        assertEquals("10%", AuditText.change(percent));
        String detail = AuditText.detail(percent);
        assertTrue(detail, detail.contains("from 01/11/2026"));
        assertTrue(detail, detail.contains("until 30/11/2026"));
        assertTrue(detail, detail.contains("100 uses at most"));
        assertTrue(detail, detail.contains("one use per customer"));
        assertTrue(detail, detail.startsWith("bill of at least "));
        AuditEntry amount = entry(AuditAction.VOUCHER_CREATED, AuditTarget.VOUCHER, 5, "TET", null, "AMOUNT:20000", "0|||" + "|0");
        assertEquals(com.coffeeshop.coffeeshopmanagement.util.CurrencyUtil.format(new java.math.BigDecimal("20000")), AuditText.change(amount));
        assertEquals("a voucher with no limits has nothing to add", "", AuditText.detail(amount));
    }

    @Test
    public void switchingAVoucherReadsOnAndOff() {
        AuditEntry e = entry(AuditAction.VOUCHER_SWITCHED, AuditTarget.VOUCHER, 4, "WELCOME10", "ON", "OFF", null);
        assertEquals("Voucher code switched on or off", AuditText.action(e));
        assertEquals("On \u2192 Off", AuditText.change(e));
    }

    @Test
    public void theTransferQrChangeReadsAsWords() {
        AuditEntry picture = entry(AuditAction.TRANSFER_QR_CHANGED, AuditTarget.SETTING, null, "transfer-qr", "Nguyen A VCB", "Evil Co", "PICTURE|0123456789ab");
        assertEquals("Setting Bank-transfer QR", AuditText.target(picture));
        assertEquals("Nguyen A VCB \u2192 Evil Co", AuditText.change(picture));
        assertEquals("Picture chosen again (0123456789ab)", AuditText.detail(picture));
        AuditEntry accepted = entry(AuditAction.TRANSFER_QR_CHANGED, AuditTarget.SETTING, null, "transfer-qr", null, null, "ACCEPTED|0123456789ab");
        assertEquals("Picture in the folder confirmed as right (0123456789ab)", AuditText.detail(accepted));
        assertEquals("", AuditText.change(accepted));
        AuditEntry info = entry(AuditAction.TRANSFER_QR_CHANGED, AuditTarget.SETTING, null, "transfer-qr", "Old text", null, "INFO|");
        assertEquals("Old text \u2192 (empty)", AuditText.change(info));
        assertEquals("Account holder and bank text changed", AuditText.detail(info));
    }

    @Test
    public void aShopSettingIsNamedInWordsAndARuleKeepsItsName() {
        AuditEntry level = entry(AuditAction.SETTINGS_CHANGED, AuditTarget.SETTING, null, "lowStockLevel", "10", "15", null);
        assertEquals("Setting Low-stock level", AuditText.target(level));
        assertEquals("10 \u2192 15", AuditText.change(level));
        AuditEntry cleared = entry(AuditAction.SETTINGS_CHANGED, AuditTarget.SETTING, null, "shopPhone", "0912345678", null, null);
        assertEquals("0912345678 \u2192 (empty)", AuditText.change(cleared));
        AuditEntry rule = entry(AuditAction.RULE_CHANGED, AuditTarget.SETTING, null, "staffDiscountPercent", "10", "20", null);
        assertEquals("Setting staffDiscountPercent", AuditText.target(rule));
    }

    @Test
    public void everyNewLineReadsInVietnameseWithNoMissingText() {
        I18n.setLanguage(I18n.VI);
        AuditEntry[] all = {
                entry(AuditAction.EXPENSE_DELETED, AuditTarget.EXPENSE, 17, "SUPPLIES", "45000", null, "2026-11-05|1|sữa"),
                entry(AuditAction.EXPENSE_RECORDED, AuditTarget.EXPENSE, 17, "SUPPLIES", null, "45000", "2026-11-05|0|"),
                entry(AuditAction.VOUCHER_CREATED, AuditTarget.VOUCHER, 4, "TET", null, "PERCENT:10", "50000|2026-11-01|2026-11-30|100|1"),
                entry(AuditAction.VOUCHER_SWITCHED, AuditTarget.VOUCHER, 4, "TET", "ON", "OFF", null),
                entry(AuditAction.TRANSFER_QR_CHANGED, AuditTarget.SETTING, null, "transfer-qr", "a", null, "PICTURE|0123456789ab"),
                entry(AuditAction.TRANSFER_QR_CHANGED, AuditTarget.SETTING, null, "transfer-qr", null, null, "INFO|0123456789ab"),
                entry(AuditAction.TRANSFER_QR_CHANGED, AuditTarget.SETTING, null, "transfer-qr", null, null, "ACCEPTED|0123456789ab"),
                entry(AuditAction.SETTINGS_CHANGED, AuditTarget.SETTING, null, "dayStart", "00:00", "04:00", null),
                entry(AuditAction.SETTINGS_CHANGED, AuditTarget.SETTING, null, "shopTaxCode", "0123456789", null, null),
        };
        for (AuditEntry e : all) {
            for (String text : new String[]{AuditText.action(e), AuditText.target(e), AuditText.change(e), AuditText.detail(e)}) {
                assertFalse("missing text in " + e.action() + ": " + text, text.contains("!audit.") || text.startsWith("!"));
            }
        }
        assertEquals("Xóa một khoản chi", AuditText.action(all[0]));
        assertEquals("Chi ngày 05/11/2026, lấy từ két \u2014 sữa", AuditText.detail(all[0]));
        assertEquals("Bật \u2192 Tắt", AuditText.change(all[3]));
    }
}
