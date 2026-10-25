package com.coffeeshop.coffeeshopmanagement.util;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * TODO 6.62: on a PC whose regional settings use other digits (Arabic-Indic, Persian, Devanagari) a {@code String.format("%d")}
 * without a locale writes those digits. The bank-transfer note a customer must type is the one place the app itself did that
 * ("DH" followed by the digits of the order number), and the tests built phone numbers and file names the same way. These
 * tests run the number-writing code under an Arabic default locale and read the sources for any unqualified
 * {@code String.format}, so a new one fails the build on any PC and not only on one with those settings.
 */
public class LocaleDigitsTest {

    private Locale saved;

    @Before
    public void useArabicSaudiArabia() {
        saved = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("ar-SA"));
    }

    @After
    public void restore() {
        Locale.setDefault(saved);
    }

    @Test
    public void theTransferNoteKeepsPlainDigits() {
        assertEquals("DH000012", OrderNumber.transferNote(12));
        assertEquals("DH123456", OrderNumber.transferNote(123456));
        assertEquals("#12", OrderNumber.format(12));
        assertEquals(Integer.valueOf(12), OrderNumber.parseReference(OrderNumber.transferNote(12)));
    }

    @Test
    public void moneyAndDatesKeepPlainDigits() {
        assertEquals("1.234.567\u0111", CurrencyUtil.format(new java.math.BigDecimal("1234567")));
        String day = java.time.LocalDate.of(2026, 3, 9).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        assertEquals("09/03/2026", day);
    }

    @Test
    public void screenTextWithNumbersInItKeepsPlainDigits() {
        String text = I18n.t("history.export.done", 1234, "file.csv");
        assertTrue(text, text.contains("1234"));
    }

    /** The pattern is built from pieces so that this file does not match itself. */
    @Test
    public void noSourceFormatsNumbersThroughTheDefaultLocale() throws IOException {
        String call = "String" + ".format(";
        String print = ".print" + "f(";
        List<String> offenders = new ArrayList<>();
        for (String root : new String[] {"src/main/java", "src/test/java"}) {
            try (Stream<Path> files = Files.walk(Path.of(root))) {
                for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                    if (file.getFileName().toString().equals("LocaleDigitsTest.java")) continue;
                    List<String> lines = Files.readAllLines(file);
                    for (int i = 0; i < lines.size(); i++) {
                        String line = lines.get(i);
                        String code = line.strip();
                        if (code.startsWith("*") || code.startsWith("//") || code.startsWith("/*")) continue;
                        for (String needle : new String[] {call, print}) {
                            int at = line.indexOf(needle);
                            while (at >= 0) {
                                String after = line.substring(at + needle.length()).stripLeading();
                                if (!after.startsWith("Locale.") && !after.startsWith("java.util.Locale.")) {
                                    offenders.add(file + ":" + (i + 1));
                                }
                                at = line.indexOf(needle, at + 1);
                            }
                        }
                    }
                }
            }
        }
        assertTrue("These calls format through the PC's regional settings; pass Locale.ROOT first: " + offenders,
                offenders.isEmpty());
    }
}
