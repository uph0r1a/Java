package com.coffeeshop.coffeeshopmanagement.util;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/** TODO 3.84: names are put in order the Vietnamese way. No database. */
public class VietnameseOrderTest {

    private static List<String> sorted(String... names) {
        List<String> list = new ArrayList<>(List.of(names));
        list.sort(VietnameseOrder.comparator());
        return list;
    }

    @Test
    public void aMarkedLetterSitsNextToItsBaseLetterNotAfterZ() {
        assertEquals(List.of("Ân", "Đặng", "Zung"), sorted("Zung", "Đặng", "Ân"));
        assertEquals(List.of("An", "Ân", "Ba", "Đặng", "Zung"), sorted("Zung", "Đặng", "Ân", "Ba", "An"));
    }

    @Test
    public void everyVietnameseLetterIsPlacedByItsBaseLetter() {
        assertEquals(List.of("An", "Ăn", "Ân", "Bà", "Dũng", "Đào", "Đặng", "E", "Ech", "Ê", "Ếch", "Ơn", "Ưng", "Zoe"),
                sorted("Zoe", "Ưng", "Ơn", "Ếch", "Ê", "Ech", "E", "Đặng", "Đào", "Dũng", "Bà", "Ân", "Ăn", "An"));
    }

    @Test
    public void capitalsAndSmallLettersCountTheSame() {
        assertEquals(0, VietnameseOrder.compare("ba", "Ba"));
        assertEquals(0, VietnameseOrder.compare("ĐẶNG", "đặng"));
        assertTrue(VietnameseOrder.compare("ba", "Bà") < 0);
        assertTrue("a capital does not sort before every small letter", VietnameseOrder.compare("Zoe", "ân") > 0);
    }

    @Test
    public void aMissingTextComesFirst() {
        assertTrue(VietnameseOrder.compare(null, "a") < 0);
        assertTrue(VietnameseOrder.compare("a", null) > 0);
        assertEquals(0, VietnameseOrder.compare(null, null));
        assertTrue(VietnameseOrder.compare("", "a") < 0);
        assertEquals(0, VietnameseOrder.compare("", ""));
    }

    @Test
    public void theOrderIsConsistent() {
        String[] names = {"An", "Ân", "Ăn", "Ba", "Bà", "Đặng", "Dũng", "Zung", "ân", "zung"};
        for (String a : names) {
            for (String b : names) {
                assertEquals(a + " / " + b, -Integer.signum(VietnameseOrder.compare(b, a)), Integer.signum(VietnameseOrder.compare(a, b)));
            }
        }
    }
}
