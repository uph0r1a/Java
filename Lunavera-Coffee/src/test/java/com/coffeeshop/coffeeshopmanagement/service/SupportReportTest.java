package com.coffeeshop.coffeeshopmanagement.service;

import org.junit.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * TODO 5.42: the text parts of the support file. A log full of phones, e-mails, hashes and passwords comes out with none of them, and
 * the ordinary lines a helper needs (times, class names, short numbers, messages) stay as they were. No database, no files.
 */
public class SupportReportTest {

    @Test
    public void aPhoneNumberIsRemovedInEveryWayItIsWritten() {
        for (String phone : new String[]{"0912345678", "+84912345678", "0912 345 678", "0912.345.678", "0912-345-678", "84912345678"}) {
            String line = SupportReport.scrub("Customer called from " + phone + " about an order");
            assertFalse(phone + " must be gone: " + line, line.contains(phone.replace("+", "")) || line.contains("345 678") || line.contains("345.678") || line.contains("345-678"));
            assertTrue(line, line.startsWith("Customer called from ") && line.endsWith(" about an order"));
        }
    }

    @Test
    public void anEmailAddressIsRemoved() {
        String line = SupportReport.scrub("sent to nguyen.van.a+shop@example.com today");
        assertFalse(line, line.contains("example.com") || line.contains("nguyen"));
        assertTrue(line, line.contains("[email]"));
    }

    @Test
    public void aValueAfterPasswordPinOrSecretIsRemoved() {
        assertEquals("login password=" + SupportReport.REMOVED + " failed", SupportReport.scrub("login password=Admin@123 failed"));
        assertEquals("PIN: " + SupportReport.REMOVED, SupportReport.scrub("PIN: 4821"));
        assertTrue(SupportReport.scrub("token = abcdef").endsWith(SupportReport.REMOVED));
        assertFalse(SupportReport.scrub("passphrase=correct horse").contains("correct"));
    }

    @Test
    public void aHashOrKeyIsRemoved() {
        String hash = "5f4dcc3b5aa765d61d8327deb882cf995f4dcc3b5aa765d61d8327deb882cf99";
        String line = SupportReport.scrub("stored " + hash + " for the account");
        assertFalse(line, line.contains("5f4dcc3b"));
        assertEquals("stored " + SupportReport.REMOVED + " for the account", line);
        String pbkdf = SupportReport.scrub("hash $pbkdf2-sha256$i=210000$c2FsdHNhbHRzYWx0$aGFzaGhhc2hoYXNoaGFzaGhhc2g");
        assertFalse(pbkdf, pbkdf.contains("pbkdf2"));
    }

    @Test
    public void theValueInADuplicateEntryErrorIsRemoved() {
        String line = SupportReport.scrub("SEVERE: Duplicate entry 'Nguyen Van A' for key 'phone'");
        assertFalse(line, line.contains("Nguyen") || line.contains("Van A"));
        assertEquals("SEVERE: Duplicate entry '" + SupportReport.REMOVED + "' for key 'phone'", line);
        String quote = SupportReport.scrub("Duplicate entry 'O'Brien' for key 'name'");
        assertFalse(quote, quote.contains("Brien"));
        assertFalse(SupportReport.scrub("Duplicate entry 'Le Thi B").contains("Le Thi"));
    }

    @Test
    public void aJdbcAddressWithItsHostAndUserIsRemoved() {
        String line = SupportReport.scrub("Cannot connect to jdbc:mariadb://shop-pc:3306/lunavera user=lan, retrying");
        assertFalse(line, line.contains("shop-pc") || line.contains("lunavera") || line.contains("lan,") || line.contains("user=lan"));
        assertTrue(line, line.contains("jdbc:" + SupportReport.REMOVED) && line.endsWith(", retrying"));
        assertFalse(SupportReport.scrub("url jdbc:mariadb://10.0.0.5/shop?user=lan&x=1").contains("10.0.0.5"));
    }

    @Test
    public void aLongExceptionNameIsKeptButALongRunWithADigitIsNot() {
        String name = "java.sql.SQLIntegrityConstraintViolationException: constraint";
        assertEquals(name, SupportReport.scrub(name));
        assertEquals("SQLIntegrityConstraintViolationException:", SupportReport.scrub("SQLIntegrityConstraintViolationException:"));
        assertEquals(SupportReport.REMOVED, SupportReport.scrub("abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz1"));
    }

    @Test
    public void ordinaryLogLinesAreKeptAsTheyWere() {
        for (String line : new String[]{
                "Nov 07, 2026 3:12:00 PM com.coffeeshop.coffeeshopmanagement.dao.OrderDAO payExistingOrder",
                "INFO: Order 124 paid, total 45.000 VND",
                "2026-11-07 14:55:01 WARNING: 3 customer phone number(s) are shared",
                "Lunavera Coffee 1.0-SNAPSHOT (built 2026-11-06) | Java 21.0.4 | Windows 11 10.0",
                "SEVERE: Unhandled error on thread JavaFX Application Thread in Lunavera Coffee 1.0",
                "\tat com.coffeeshop.coffeeshopmanagement.controller.OrderController.pay(OrderController.java:1240)",
                ""}) {
            assertEquals(line, SupportReport.scrub(line));
        }
        assertEquals("", SupportReport.scrub(null));
    }

    @Test
    public void theTailKeepsTheEndAndScrubsEveryLine() {
        List<String> lines = new ArrayList<>();
        for (int i = 1; i <= 10; i++) lines.add("line " + i + " 0912345678");
        String tail = SupportReport.scrubbedTail(lines, 3);
        assertEquals("line 8 [number]\nline 9 [number]\nline 10 [number]\n", tail);
        assertEquals(10, SupportReport.scrubbedTail(lines, 500).split("\n").length);
        assertEquals("", SupportReport.scrubbedTail(new ArrayList<>(), 5));
    }

    @Test
    public void theLastCrashIsTheNewestOneWithItsDateLineAndStackAndNothingAfterIt() {
        List<String> log = SupportReport.linesOf(String.join("\n",
                "Lunavera Coffee 1.0 | Java 21 | Windows 11",
                "Nov 06, 2026 9:00:00 AM com.x.CrashHandler report",
                "SEVERE: Unhandled error on thread OLD in Lunavera Coffee 1.0",
                "java.lang.IllegalStateException: old",
                "\tat com.x.Old.run(Old.java:1)",
                "Nov 07, 2026 3:12:00 PM com.x.CrashHandler report",
                "SEVERE: Unhandled error on thread JavaFX Application Thread in Lunavera Coffee 1.0",
                "java.lang.NullPointerException: customer 0912345678",
                "\tat com.x.OrderController.pay(OrderController.java:1240)",
                "\tat javafx.base/com.sun.Event.dispatch(Event.java:5)",
                "Nov 07, 2026 3:13:00 PM com.x.Other something",
                "INFO: not part of the crash", ""));
        String crash = SupportReport.lastCrash(log);
        assertNotNull(crash);
        assertTrue(crash, crash.startsWith("Nov 07, 2026 3:12:00 PM"));
        assertTrue(crash, crash.contains("NullPointerException") && crash.contains("OrderController.pay"));
        assertFalse("the older crash is not here", crash.contains("OLD"));
        assertFalse("the next record is not here", crash.contains("not part of the crash"));
        assertFalse("a phone in the message is removed", crash.contains("0912345678"));
        assertNull(SupportReport.lastCrash(SupportReport.linesOf("INFO: fine\nINFO: fine too")));
    }

    @Test
    public void aCrashBlockIsCapped() {
        List<String> log = new ArrayList<>();
        log.add("Nov 07, 2026 3:12:00 PM com.x.CrashHandler report");
        log.add("SEVERE: Unhandled error on thread T in Lunavera");
        for (int i = 0; i < 500; i++) log.add("\tat com.x.Deep.call(Deep.java:" + i + ")");
        assertTrue(SupportReport.lastCrash(log).split("\n").length <= SupportReport.CRASH_LINES);
    }

    @Test
    public void theSheetSaysWhatItIsAndWhatItLeavesOutAndHasNumbersOnly() {
        Map<String, Long> rows = new LinkedHashMap<>();
        rows.put("customers", 1L);
        rows.put("orders", 42L);
        String sheet = SupportReport.sheet(new SupportReport.Facts("Lunavera Coffee 1.0-SNAPSHOT | Java 21", "21.0.4", "Eclipse Adoptium",
                "Windows 11 10.0 (amd64)", "vi", "SQLite (a file in the data folder)", 1, rows, "", "2026-11-07 15:30", 2));
        assertTrue(sheet, sheet.contains("customers: 1") && sheet.contains("orders: 42"));
        assertTrue(sheet, sheet.contains("table layout version 1"));
        assertTrue(sheet, sheet.contains("SQLite") && sheet.contains("Windows 11"));
        assertTrue(sheet, sheet.contains("NOT in this file"));
        assertFalse(sheet, sheet.contains("Database problem"));
        String broken = SupportReport.sheet(new SupportReport.Facts("p", "j", "v", "s", "en", "MariaDB / MySQL (a server)", 0, new LinkedHashMap<>(),
                "could not be read (SQLException)", "2026-11-07 15:30", 0));
        assertTrue(broken, broken.contains("Database problem: could not be read (SQLException)") && broken.contains("none could be counted"));
        assertTrue(broken, broken.contains("table layout version not read"));
    }

    @Test
    public void onlyPlainTableNamesGoIntoAStatement() {
        assertTrue(SupportReport.safeTableName("order_items"));
        assertTrue(SupportReport.safeTableName("app_flags"));
        assertFalse(SupportReport.safeTableName("orders; DROP TABLE users"));
        assertFalse(SupportReport.safeTableName("a b"));
        assertFalse(SupportReport.safeTableName("`x`"));
        assertFalse(SupportReport.safeTableName(""));
        assertFalse(SupportReport.safeTableName(null));
        assertFalse(SupportReport.safeTableName("x".repeat(65)));
    }

    @Test
    public void textBecomesLinesWithoutAnEmptyLastOne() {
        assertEquals(List.of("a", "b"), SupportReport.linesOf("a\r\nb\n"));
        assertEquals(List.of("a", "", "b"), SupportReport.linesOf("a\n\nb"));
        assertTrue(SupportReport.linesOf(null).isEmpty());
        assertTrue(SupportReport.linesOf("").isEmpty());
    }
}
