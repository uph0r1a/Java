package com.coffeeshop.coffeeshopmanagement.util;

import com.coffeeshop.coffeeshopmanagement.service.IngredientRules;
import com.coffeeshop.coffeeshopmanagement.service.LoyaltyPolicy;
import com.coffeeshop.coffeeshopmanagement.service.OrderNote;
import com.coffeeshop.coffeeshopmanagement.service.ProductCode;
import com.coffeeshop.coffeeshopmanagement.service.StaffRules;
import com.coffeeshop.coffeeshopmanagement.service.StockRules;
import com.coffeeshop.coffeeshopmanagement.service.VoucherRules;
import org.junit.Test;

import java.math.BigDecimal;

import static org.junit.Assert.*;

/**
 * TODO 3.71: text copied from Zalo, a web page or the phone's contacts carries characters nobody can see. One shared list of them is
 * tried on every reader of typed text, so a new parser is easy to add to the list and an old one cannot be forgotten.
 */
public class InvisibleCharactersTest {

    /** Space separators (read as a space) and invisible format characters (dropped). */
    private static final String[] SPACES = {" ", " ", " ", " ", "　", " "};
    private static final String[] INVISIBLE = {"​", "‎", "‏", "‪", "‫", "‬", "‭", "‮", "⁦", "⁩", "﻿", "­", "‍", "‌"};

    @Test
    public void cleanTurnsSpacesIntoSpacesAndDropsTheInvisible() {
        for (String s : SPACES) assertEquals("a b", TextNormalizer.clean("a" + s + "b"));
        for (String i : INVISIBLE) {
            if (i.equals("‍") || i.equals("‌")) continue; // kept by clean, dropped by strip
            assertEquals("ab", TextNormalizer.clean("a" + i + "b"));
        }
        for (String i : INVISIBLE) assertEquals("ab", TextNormalizer.strip("a" + i + "b"));
        assertEquals("plain text stays the same object", "Cà phê", TextNormalizer.clean("Cà phê"));
        assertNull(TextNormalizer.clean(null));
        assertNull(TextNormalizer.strip(null));
    }

    @Test
    public void anEmojiSequenceKeepsItsJoinersInANameButNotInANumber() {
        String family = "👨‍👩‍👧";
        assertEquals(family, TextNormalizer.clean(family));
        assertNotEquals(family, TextNormalizer.strip(family));
    }

    @Test
    public void aNameWithANonBreakingSpaceIsTheSameNameAsOneWithANormalSpace() {
        assertEquals(TextNormalizer.key("Trà đào"), TextNormalizer.key("Trà đào"));
        assertEquals(TextNormalizer.uniqueKey("Trà đào"), TextNormalizer.uniqueKey("Trà đào​"));
        assertEquals(TextNormalizer.searchKey("tra dao"), TextNormalizer.searchKey("Trà đào"));
        assertEquals("a name saved with them has none", "Trà đào", TextNormalizer.nfc("‎Trà đào​ "));
    }

    @Test
    public void aPhoneWithAnyOfThemIsStillAPhone() {
        for (String s : concat(SPACES, INVISIBLE)) {
            String phone = "0901" + s + "111" + s + "222";
            assertTrue("valid with " + hex(s), ValidationUtil.isValidPhone(phone));
            assertEquals("0901111222", ValidationUtil.normalizePhone(phone));
            assertEquals("0901111222", ValidationUtil.normalizePhone(s + "0901111222" + s));
        }
    }

    @Test
    public void thePlus84FormWithAnOptionalZeroInBracketsIsRead() {
        assertEquals("0901111222", ValidationUtil.normalizePhone("+84 (0) 901 111 222"));
        assertEquals("0901111222", ValidationUtil.normalizePhone("+840901111222"));
        assertEquals("0901111222", ValidationUtil.normalizePhone("+84901111222"));
        assertEquals("0901111222", ValidationUtil.normalizePhone("84901111222"));
        assertTrue(ValidationUtil.isValidPhone("+84 (0) 901 111 222"));
        assertFalse("the zero is only optional after +84", ValidationUtil.isValidPhone("840901111222"));
        assertFalse(ValidationUtil.isValidPhone("+84 (0) 901 111 22"));
        assertFalse(ValidationUtil.isValidPhone("+8400901111222"));
    }

    @Test
    public void anEmailWithATrailingInvisibleCharacterIsValid() {
        for (String s : concat(SPACES, INVISIBLE)) assertTrue(hex(s), ValidationUtil.isValidEmail("an@example.com" + s));
        assertFalse(ValidationUtil.isValidEmail("an@exa​mple"));
    }

    @Test
    public void anAmountWithAnyOfThemIsRead() {
        for (String s : concat(SPACES, INVISIBLE)) {
            if (s.equals(" ")) continue; // a line separator inside an amount is not a thousands separator any more than a newline is
            assertEquals(hex(s), new BigDecimal("50000"), MoneyParser.parseDong("50" + s + "000"));
            assertEquals(hex(s), new BigDecimal("25000"), MoneyParser.parseDong(s + "25.000đ" + s));
        }
        assertEquals(new BigDecimal("50000"), MoneyParser.parseDong("50 000"));
        assertNull(MoneyParser.parseDong("​"));
        assertEquals(BigDecimal.ZERO, MoneyParser.parseOptionalDong("​"));
        assertEquals(new BigDecimal("50000"), MoneyParser.parseDong("50​k"));
    }

    @Test
    public void aVoucherCodeAndAPointsAmountPastedFromAMessageAreRead() {
        for (String s : concat(SPACES, INVISIBLE)) {
            assertEquals(hex(s), "SALE10", VoucherRules.normalise(s + "sale10" + s));
            assertEquals(hex(s), Integer.valueOf(120), LoyaltyPolicy.parsePoints(s + "120" + s));
        }
        assertNull("a code with one inside is still not a code", VoucherRules.normalise("SA​LE 10"));
    }

    @Test
    public void thePlainCleanersDoNotKeepThem() {
        assertEquals("Nam", StaffRules.clean("‎Nam "));
        assertNull(StaffRules.clean("​ "));
        assertEquals("Đường mía", IngredientRules.clean("Đường ​mía"));
        assertEquals("AB12", ProductCode.clean("ab​12 "));
        assertEquals("giao nhanh", OrderNote.clean("giao nhanh​"));
        assertEquals("giao nhanh", StockRules.cleanText("giao nhanh​"));
        assertNull(StockRules.cleanText("​ "));
    }

    private static String[] concat(String[] a, String[] b) {
        String[] out = new String[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    private static String hex(String s) {
        return "U+" + Integer.toHexString(s.codePointAt(0)).toUpperCase();
    }
}
