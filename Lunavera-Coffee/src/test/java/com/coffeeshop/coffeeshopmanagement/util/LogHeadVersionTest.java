package com.coffeeshop.coffeeshopmanagement.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** TODO 5.41: every log file, the ones that start after a rotation too, begins with the program version. */
public class LogHeadVersionTest {

    @Test
    public void theHeadOfALogFileIsTheVersionLine() {
        String head = new AppLogging.VersionHeadFormatter().getHead(null);
        assertEquals(AppVersion.logLine() + System.lineSeparator(), head);
        assertTrue(head.startsWith("Lunavera Coffee "));
    }
}
