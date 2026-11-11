package com.coffeeshop.coffeeshopmanagement.util;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.Assert.*;

/** TODO 6.83: the date facts of the rules are free of JavaFX, and the converter that needs JavaFX still agrees with them. */
public class DateLimitsTest {

    @Test
    public void theEarliestDateAndThePatternAreTheOnesTheScreensHaveAlwaysUsed() {
        assertEquals(LocalDate.of(2000, 1, 1), DateLimits.EARLIEST);
        assertEquals("dd/MM/yyyy", DateLimits.PATTERN);
    }

    @Test
    public void theRuleClassesDoNotNameTheJavaFxConverter() throws IOException {
        for (String rules : new String[]{"VoucherRules", "StaffRules"}) {
            String source = Files.readString(Path.of("src", "main", "java", "com", "coffeeshop", "coffeeshopmanagement", "service", rules + ".java"));
            assertFalse(rules + " must not use DateTextConverter (it needs JavaFX); use DateLimits", source.contains("DateTextConverter"));
            assertTrue(rules + " uses DateLimits", source.contains("DateLimits."));
        }
    }
}
