package com.coffeeshop.coffeeshopmanagement.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TableStatusTest {
    @Test
    public void aFreeTableIsCalledEmptyNotInactive() {
        // TODO 2.68: one word, kept here and read by every screen
        assertEquals("Trống", TableStatus.EMPTY.getDisplayName());
        assertEquals("Đang sử dụng", TableStatus.OCCUPIED.getDisplayName());
    }
}
