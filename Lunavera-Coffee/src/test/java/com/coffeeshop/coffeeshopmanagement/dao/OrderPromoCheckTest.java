package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.model.OrderItem;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/** TODO 4.55: what {@code OrderDAO} checks about the promotions on a bill before it writes a row. No database. */
public class OrderPromoCheckTest {

    private static OrderItem line(String lineTotal, String promoName, String promoValue) {
        OrderItem item = new OrderItem();
        item.setProductName("Latte");
        item.setQuantity(1);
        item.setUnitPrice(new BigDecimal(lineTotal));
        item.setLineTotal(new BigDecimal(lineTotal));
        item.setPromoName(promoName);
        item.setPromoValue(promoValue == null ? null : new BigDecimal(promoValue));
        return item;
    }

    private static BigDecimal promoOf(List<OrderItem> items, String discount, String voucher, String points, boolean paid) {
        return OrderDAO.promoOf(items, discount == null ? null : new BigDecimal(discount), voucher == null ? null : new BigDecimal(voucher),
                points == null ? null : new BigDecimal(points), paid);
    }

    private static void refused(List<OrderItem> items, String discount, String voucher, String points) {
        try {
            promoOf(items, discount, voucher, points, true);
            fail("should have been refused");
        } catch (DataAccessException expected) {
            // fine
        }
    }

    @Test
    public void theSumOfTheLinesSavingsIsWhatThePromotionsTookOff() {
        List<OrderItem> items = List.of(line("50000", "Giờ vàng", "5000"), line("40000", null, null), line("30000", "Combo", "3000"));
        assertEquals(0, new BigDecimal("8000").compareTo(promoOf(items, "8000", null, null, true)));
    }

    @Test
    public void aBillWithNoPromotionIsZeroAndNeedsNoDiscount() {
        assertEquals(0, BigDecimal.ZERO.compareTo(promoOf(List.of(line("50000", null, null)), "0", null, null, true)));
        assertEquals(0, BigDecimal.ZERO.compareTo(promoOf(List.of(line("50000", null, "0")), null, null, null, true)));
    }

    @Test
    public void aSaleThatIsNotBeingPaidCarriesNoPromotion() {
        assertEquals(0, BigDecimal.ZERO.compareTo(promoOf(List.of(line("50000", "Giờ vàng", "5000")), "0", null, null, false)));
    }

    @Test
    public void theVoucherAndThePointsFitInsideTheDiscountWithThePromotion() {
        List<OrderItem> items = List.of(line("50000", "Giờ vàng", "5000"));
        promoOf(items, "9000", "3000", "1000", true); // 5000 + 3000 + 1000 = 9000: exactly fits
        refused(items, "8999", "3000", "1000");
        refused(items, "5000", "3000", null);
    }

    @Test
    public void aSavingBiggerThanItsLineIsRefused() {
        refused(List.of(line("50000", "Giờ vàng", "50001")), "50001", null, null);
    }

    @Test
    public void aNegativeSavingIsRefused() {
        refused(List.of(line("50000", "Giờ vàng", "-1")), "0", null, null);
    }

    @Test
    public void aSavingWithoutAPromotionNameIsRefused() {
        refused(List.of(line("50000", null, "5000")), "5000", null, null);
        refused(List.of(line("50000", "  ", "5000")), "5000", null, null);
    }

    @Test
    public void aSavingWithNoDiscountSavedIsRefused() {
        refused(List.of(line("50000", "Giờ vàng", "5000")), null, null, null);
        refused(List.of(line("50000", "Giờ vàng", "5000")), "0", null, null);
    }
}
