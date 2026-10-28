package com.coffeeshop.coffeeshopmanagement.service;

import org.junit.Test;

import static org.junit.Assert.*;

/** TODO 4.9: the note on an order line. */
public class OrderNoteTest {

    @Test
    public void anEmptyNoteIsNull() {
        assertNull(OrderNote.clean(null));
        assertNull(OrderNote.clean("  \n\t "));
    }

    @Test
    public void whitespaceBecomesOneSpace() {
        assertEquals("ít đường, không đá", OrderNote.clean("  ít   đường,\n không\tđá "));
    }

    @Test
    public void controlCharactersGo() {
        assertEquals("abc", OrderNote.clean("a\u0007b\u0000c"));
    }

    @Test
    public void aLongNoteIsCutAndTrimmed() {
        String note = OrderNote.clean("x".repeat(OrderNote.MAX_LENGTH + 40));
        assertEquals(OrderNote.MAX_LENGTH, note.length());
        assertTrue(OrderNote.tooLong("x".repeat(OrderNote.MAX_LENGTH + 1)));
        assertFalse(OrderNote.tooLong("x".repeat(OrderNote.MAX_LENGTH)));
    }

    @Test
    public void withNoteShowsItInBrackets() {
        assertEquals("Latte (less sugar)", OrderNote.withNote("Latte", " less sugar "));
        assertEquals("Latte", OrderNote.withNote("Latte", "  "));
    }

    @Test
    public void quickButtonsAddWithACommaAndNeverTwice() {
        assertEquals("Less ice", OrderNote.append("", "Less ice"));
        assertEquals("Less ice, No sugar", OrderNote.append("Less ice", "No sugar"));
        assertEquals("Less ice, No sugar", OrderNote.append("Less ice, No sugar", "less ice"));
    }
}
