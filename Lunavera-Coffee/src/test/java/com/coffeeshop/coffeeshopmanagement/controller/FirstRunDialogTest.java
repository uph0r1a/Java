package com.coffeeshop.coffeeshopmanagement.controller;

import org.junit.Test;

import static org.junit.Assert.*;

public class FirstRunDialogTest {
    @Test
    public void goodInputHasNoProblem() {
        assertNull(FirstRunDialog.problemWith("Nguyễn Văn A", "chuquan", "Tra-sua-2026x", "Tra-sua-2026x"));
    }

    @Test
    public void eachMistakeIsReported() {
        assertNotNull(FirstRunDialog.problemWith("", "chuquan", "Tra-sua-2026x", "Tra-sua-2026x"));
        assertNotNull(FirstRunDialog.problemWith("A", "ab", "Tra-sua-2026x", "Tra-sua-2026x"));
        assertNotNull(FirstRunDialog.problemWith("A", "0901234567", "Tra-sua-2026x", "Tra-sua-2026x"));
        assertNotNull("too short / common", FirstRunDialog.problemWith("A", "chuquan", "12345678", "12345678"));
        assertNotNull("the old built-in password", FirstRunDialog.problemWith("A", "chuquan", "Admin@123", "Admin@123"));
        assertNotNull("contains the username", FirstRunDialog.problemWith("A", "chuquan", "chuquan-2026", "chuquan-2026"));
        assertNotNull("confirmation differs", FirstRunDialog.problemWith("A", "chuquan", "Tra-sua-2026x", "Tra-sua-2026y"));
    }
}
