package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.util.DateTextConverter;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** TODO 3.78: a year-26 date is refused by the hire-date and voucher rules, and no date box is made without the shared converter. */
public class EarlyDateRulesTest {

    @Test
    public void aVoucherDateInTheYear26IsRefused() {
        LocalDate slip = LocalDate.of(26, 11, 1);
        assertNotNull(VoucherRules.newVoucherProblem("SALE10", "PERCENT", 10L, null, slip, null, null));
        assertNotNull(VoucherRules.newVoucherProblem("SALE10", "PERCENT", 10L, null, null, slip, null));
        assertNull(VoucherRules.newVoucherProblem("SALE10", "PERCENT", 10L, null, DateTextConverter.EARLIEST, null, null));
        assertNull(VoucherRules.newVoucherProblem("SALE10", "PERCENT", 10L, null, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 1), null));
    }

    @Test
    public void earliestIsTheYearTwoThousand() {
        assertEquals(2000, DateTextConverter.EARLIEST.getYear());
    }

    @Test
    public void everyDatePickerIsMadeByDateBoxes() throws IOException {
        Path root = Path.of("src/main/java");
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
                if (file.getFileName().toString().equals("DateBoxes.java")) continue;
                assertTrue(file + " makes a DatePicker itself; use DateBoxes.picker (TODO 3.78)",
                        !Files.readString(file).contains("new DatePicker(") && !Files.readString(file).contains("new javafx.scene.control.DatePicker("));
            }
        }
    }
}
