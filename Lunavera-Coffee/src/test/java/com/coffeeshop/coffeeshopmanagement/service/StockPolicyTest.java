package com.coffeeshop.coffeeshopmanagement.service;

import org.junit.Test;

import static org.junit.Assert.*;

public class StockPolicyTest {
    @Test
    public void aTypoSizedJumpIsQuestionedButNormalRestockingIsNot() {
        assertTrue(StockPolicy.looksTooBig(20, 2000));
        assertTrue(StockPolicy.looksTooBig(0, 100));
        assertFalse("a normal restock of a fast seller", StockPolicy.looksTooBig(20, 100));
        assertFalse(StockPolicy.looksTooBig(0, 99));
        assertFalse("lowering is never questioned", StockPolicy.looksTooBig(500, 5));
        assertFalse("500 to 700 is a big number but not a jump", StockPolicy.looksTooBig(500, 700));
    }
}
