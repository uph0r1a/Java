package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.service.ShiftOpenOrders.Open;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/** TODO 5.48: what the shift-close box says about tables and take-away orders that are still unpaid. */
public class ShiftOpenOrdersTest {

    private String language;

    @Before
    public void english() {
        language = I18n.current();
        I18n.setLanguage(I18n.EN);
    }

    @After
    public void restore() {
        I18n.setLanguage(language);
    }

    private static Open table(int order, int table, String total) {
        return new Open(order, table, null, new BigDecimal(total));
    }

    @Test
    public void nothingUnpaidSaysNothing() {
        assertEquals("", ShiftOpenOrders.text(List.of()));
        assertEquals("", ShiftOpenOrders.text(null));
    }

    @Test
    public void tablesAreNamedWithTheirAmountAndTheCashierIsToldTheyCanStillClose() {
        String text = ShiftOpenOrders.text(List.of(table(10, 3, "120000"), table(11, 5, "45000")));
        assertTrue(text, text.contains("2 table(s) still have an unpaid order"));
        assertTrue(text, text.contains("Table 3 (") && text.contains("Table 5 ("));
        assertTrue("the amount is shown", text.contains("120.000") || text.contains("120,000"));
        assertTrue(text, text.contains("You can still close the shift"));
        assertFalse("no take-away line when there is none", text.contains("take-away"));
    }

    @Test
    public void takeAwayOrdersAreSeparateAndUseTheirQueueNumberOrTheOrderNumber() {
        String text = ShiftOpenOrders.text(List.of(table(10, 3, "10000"),
                new Open(11, null, 7, new BigDecimal("30000")), new Open(12345, null, null, new BigDecimal("5000"))));
        assertTrue(text, text.contains("1 table(s)"));
        assertTrue(text, text.contains("2 take-away order(s) are not paid yet"));
        assertTrue(text, text.contains("take-away No. 7 ("));
        assertTrue("an order number is not written with a thousands separator", text.contains("order 12345 ("));
        assertTrue("tables come before take-away", text.indexOf("Table 3") < text.indexOf("take-away No. 7"));
    }

    @Test
    public void aLongListIsCutAndTheRestIsCounted() {
        List<Open> many = new ArrayList<>();
        for (int i = 1; i <= ShiftOpenOrders.MAX_SHOWN + 4; i++) many.add(table(i, i, "1000"));
        String text = ShiftOpenOrders.text(many);
        assertTrue(text, text.contains((ShiftOpenOrders.MAX_SHOWN + 4) + " table(s)"));
        assertTrue(text, text.contains("Table " + ShiftOpenOrders.MAX_SHOWN + " ("));
        assertFalse("the ninth is not named", text.contains("Table " + (ShiftOpenOrders.MAX_SHOWN + 1) + " ("));
        assertTrue(text, text.contains("and 4 more"));
    }

    @Test
    public void theVietnameseTextReadsInVietnamese() {
        I18n.setLanguage(I18n.VI);
        String text = ShiftOpenOrders.text(List.of(table(10, 3, "120000")));
        assertTrue(text, text.contains("Còn 1 bàn chưa thanh toán"));
        assertTrue(text, text.contains("Bàn 3 ("));
        assertTrue(text, text.contains("Vẫn có thể chốt ca"));
    }
}
