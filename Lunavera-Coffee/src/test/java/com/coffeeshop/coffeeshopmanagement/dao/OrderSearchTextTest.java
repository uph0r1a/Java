package com.coffeeshop.coffeeshopmanagement.dao;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** When the history search text means "order number N" (TODO 3.35). No database needed. */
public class OrderSearchTextTest {

    @Test
    public void plainDigitsAreAnOrderNumber() {
        assertTrue(OrderDAO.looksLikeOrderNumber("1"));
        assertTrue(OrderDAO.looksLikeOrderNumber("12"));
        assertTrue(OrderDAO.looksLikeOrderNumber("999999999"));
    }

    @Test
    public void textThatCannotBeAnOrderNumberIsNot() {
        assertFalse("a leading zero is more likely the start of a phone", OrderDAO.looksLikeOrderNumber("0901"));
        assertFalse(OrderDAO.looksLikeOrderNumber("0"));
        assertFalse("ten digits is no order number here", OrderDAO.looksLikeOrderNumber("1234567890"));
        assertFalse(OrderDAO.looksLikeOrderNumber("12a"));
        assertFalse(OrderDAO.looksLikeOrderNumber("1 2"));
        assertFalse(OrderDAO.looksLikeOrderNumber(""));
        assertFalse(OrderDAO.looksLikeOrderNumber(null));
    }
}
