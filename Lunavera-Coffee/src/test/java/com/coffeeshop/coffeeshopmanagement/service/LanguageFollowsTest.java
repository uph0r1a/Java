package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.model.DiningTable;
import com.coffeeshop.coffeeshopmanagement.model.PaymentMethod;
import com.coffeeshop.coffeeshopmanagement.model.Role;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.ReceiptText;
import org.junit.After;
import org.junit.Test;

import java.math.BigDecimal;

import static org.junit.Assert.*;

/**
 * TODO 1.7: texts that come from the model and the service layer (not from a screen) follow the language in use too:
 * the payment method names, the table names, the discount and table-policy messages the orders tab shows.
 */
public class LanguageFollowsTest {

    private final String languageAtStart = I18n.current();

    @After
    public void putTheLanguageBack() {
        I18n.setLanguage(languageAtStart);
    }

    @Test
    public void paymentMethodNamesFollowTheLanguage() {
        I18n.setLanguage(I18n.VI);
        assertEquals("Tiền mặt", PaymentMethod.CASH.label());
        assertEquals("Thẻ", PaymentMethod.CARD.label());
        assertEquals("Chuyển khoản", PaymentMethod.labelOf(PaymentMethod.TRANSFER));
        I18n.setLanguage(I18n.EN);
        assertEquals("Cash", PaymentMethod.CASH.label());
        assertEquals("Card", PaymentMethod.CARD.label());
        assertEquals("Bank transfer", PaymentMethod.labelOf(PaymentMethod.TRANSFER));
        assertEquals("-", PaymentMethod.labelOf(null));
    }

    @Test
    public void whatIsStoredForAPaymentMethodNeverDependsOnTheLanguage() {
        I18n.setLanguage(I18n.EN);
        assertEquals("CASH", PaymentMethod.CASH.name());
        assertEquals(PaymentMethod.TRANSFER, PaymentMethod.fromDb("transfer"));
    }

    private static DiningTable table(int number, String name) {
        DiningTable table = new DiningTable();
        table.setTableNumber(number);
        table.setName(name);
        return table;
    }

    @Test
    public void aStarterTableNameFollowsTheLanguageButATypedNameDoesNot() {
        I18n.setLanguage(I18n.EN);
        assertEquals("Table 3", table(3, "Bàn 3").displayName());
        assertEquals("Table 12", table(12, " Bàn 12 ").displayName());
        assertEquals("Sân vườn 1", table(3, "Sân vườn 1").displayName());
        assertEquals("Bàn 4", table(3, "Bàn 4").displayName()); // a name that is not its own number is the shop's own
        assertNull(table(3, null).displayName());
        I18n.setLanguage(I18n.VI);
        assertEquals("Bàn 3", table(3, "Bàn 3").displayName());
    }

    @Test
    public void discountMessagesFollowTheLanguage() {
        BigDecimal subtotal = new BigDecimal("100000");
        I18n.setLanguage(I18n.VI);
        assertEquals("Giảm giá không được lớn hơn tạm tính.",
                DiscountPolicy.messageFor(DiscountPolicy.Result.ABOVE_SUBTOTAL, Role.EMPLOYEE, subtotal));
        I18n.setLanguage(I18n.EN);
        assertEquals("The discount cannot be larger than the subtotal.",
                DiscountPolicy.messageFor(DiscountPolicy.Result.ABOVE_SUBTOTAL, Role.EMPLOYEE, subtotal));
        String limit = DiscountPolicy.messageFor(DiscountPolicy.Result.ABOVE_STAFF_LIMIT, Role.EMPLOYEE, subtotal);
        assertTrue(limit, limit.startsWith("Staff may give a discount of at most " + DiscountPolicy.STAFF_MAX_PERCENT + "%"));
        assertFalse(limit, limit.contains("{"));
        String reason = DiscountPolicy.messageFor(DiscountPolicy.Result.REASON_TOO_LONG, Role.ADMIN, subtotal);
        assertTrue(reason, reason.contains(String.valueOf(DiscountPolicy.REASON_MAX_LENGTH)));
    }

    @Test
    public void theTablePolicyMessageFollowsTheLanguage() {
        I18n.setLanguage(I18n.VI);
        assertTrue(TablePolicy.refusedMessage().startsWith("Đơn này do nhân viên khác mở."));
        I18n.setLanguage(I18n.EN);
        assertTrue(TablePolicy.refusedMessage().startsWith("This order was opened by another staff member."));
    }

    @Test
    public void aReceiptIsAlwaysEnglishWhateverTheLanguageInUse() {
        I18n.setLanguage(I18n.VI);
        assertEquals("PAYMENT RECEIPT", ReceiptText.t("receipt.title"));
        assertEquals("Cash", ReceiptText.paymentMethod(PaymentMethod.CASH));
        assertEquals("-", ReceiptText.paymentMethod(null));
        assertEquals("Table 3", ReceiptText.table(3));
        assertEquals("Takeaway", ReceiptText.table(null));
        assertEquals("Black coffee", ReceiptText.productName("Cafe đen"));
        assertEquals("Cà phê muối", ReceiptText.productName("Cà phê muối")); // a name the shop typed is shown as typed
        assertEquals("Order no.: #7", ReceiptText.t("receipt.line.orderid", 7));
        assertEquals("Receipt #12", ReceiptText.t("pos.invoice.title", 12));
        // the screens around it still follow the language in use
        assertEquals("Tiền mặt", PaymentMethod.CASH.label());
        I18n.setLanguage(I18n.EN);
        assertEquals("PAYMENT RECEIPT", ReceiptText.t("receipt.title"));
    }

    @Test
    public void theReceiptWindowTextComesFromTheEnglishBundleEvenInVietnamese() {
        I18n.setLanguage(I18n.VI);
        assertEquals("Print receipt", ReceiptText.bundle().getString("pos.print").replace("▣  ", ""));
        assertEquals("Thank you, see you again!", ReceiptText.bundle().getString("receipt.thanks"));
        assertEquals("Cảm ơn quý khách và hẹn gặp lại!", I18n.bundle().getString("receipt.thanks"));
    }
}
