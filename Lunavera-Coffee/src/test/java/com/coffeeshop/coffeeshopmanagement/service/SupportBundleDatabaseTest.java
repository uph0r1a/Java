package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig;
import com.coffeeshop.coffeeshopmanagement.dao.CustomerDAO;
import com.coffeeshop.coffeeshopmanagement.dao.TestDatabaseSupport;
import com.coffeeshop.coffeeshopmanagement.model.Customer;
import com.coffeeshop.coffeeshopmanagement.util.DataPaths;
import com.coffeeshop.coffeeshopmanagement.util.Permissions.PermissionDeniedException;
import com.coffeeshop.coffeeshopmanagement.util.TestSession;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.Assert.*;

/**
 * TODO 5.42 on the real test database: a database with a customer named "Nguyễn Văn A" and the phone 0912345678 gives a zip in which
 * neither string appears, while the row count of the customers table does. Also: only an administrator can make it, the log lines
 * are filtered, the security log is left out, and a second save over the same name replaces the first.
 */
public class SupportBundleDatabaseTest {

    private static final String NAME = "Nguy\u1ec5n V\u0103n A";
    private static final String PHONE = "0912345678";

    @BeforeClass
    public static void setUpDatabase() {
        TestDatabaseSupport.ensureReady();
    }

    @After
    public void signOut() {
        TestSession.clear();
    }

    private static Map<String, String> read(Path zip) throws IOException {
        Map<String, String> entries = new TreeMap<>();
        try (ZipFile file = new ZipFile(zip.toFile())) {
            for (var e = file.entries(); e.hasMoreElements(); ) {
                ZipEntry entry = e.nextElement();
                entries.put(entry.getName(), new String(file.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return entries;
    }

    @Test
    public void aCustomersNameAndPhoneNeverReachTheZipButTheRowCountDoes() throws Exception {
        TestSession.admin();
        Customer customer = new Customer();
        customer.setFullName(NAME);
        customer.setPhone(PHONE);
        customer.setLoyaltyPoints(0);
        new CustomerDAO().insert(customer);
        long customers;
        try (Connection connection = DatabaseConfig.getConnection()) {
            customers = SupportBundle.rowCountsOfEveryTable(connection).get("customers");
        }
        Path dir = Files.createTempDirectory("supportzip");
        Path zip = SupportBundle.create(dir.resolve("support.zip"));
        Map<String, String> entries = read(zip);
        assertTrue(entries.containsKey("support-info.txt"));
        assertTrue(entries.containsKey("last-crash.txt"));
        for (Map.Entry<String, String> e : entries.entrySet()) {
            assertFalse(e.getKey() + " must not hold the name", e.getValue().contains(NAME) || e.getValue().contains("Nguyen Van A"));
            assertFalse(e.getKey() + " must not hold the phone", e.getValue().contains(PHONE));
        }
        assertTrue(entries.get("support-info.txt"), entries.get("support-info.txt").contains("customers: " + customers));
        assertTrue("every table is counted, not a short list", entries.get("support-info.txt").contains("order_items: "));
        assertFalse("no path of the data folder", entries.get("support-info.txt").contains(DataPaths.dataDir().toString()));
    }

    @Test
    public void everyTableOfTheDatabaseIsCounted() throws Exception {
        try (Connection connection = DatabaseConfig.getConnection()) {
            Map<String, Long> counts = SupportBundle.rowCountsOfEveryTable(connection);
            for (String table : new String[]{"orders", "order_items", "customers", "products", "users", "audit_log", "settings"}) {
                assertTrue(table + " is counted", counts.containsKey(table));
            }
            for (String name : counts.keySet()) assertFalse(name, name.toLowerCase().startsWith("sqlite_"));
        }
    }

    @Test(expected = PermissionDeniedException.class)
    public void aCashierCannotMakeIt() throws Exception {
        TestSession.clear();
        SupportBundle.create(Files.createTempDirectory("supportzip").resolve("no.zip"));
    }

    @Test
    public void theLogLinesAreFilteredAndTheSecurityLogIsLeftOut() throws Exception {
        TestSession.admin();
        Path logs = DataPaths.logsDir();
        Files.createDirectories(logs);
        Path appLog = logs.resolve("app-0-0.log");
        Path securityLog = logs.resolve("security-2026-11.log");
        Files.writeString(appLog, "Lunavera Coffee 1.0 | Java 21 | Windows\nNov 07, 2026 3:12:00 PM com.x.A b\nINFO: called " + PHONE + " and a@b.vn\n");
        Files.writeString(securityLog, "SECURITY: test-admin changed the shop settings\n");
        try {
            Path zip = SupportBundle.create(Files.createTempDirectory("supportzip").resolve("support.zip"));
            Map<String, String> entries = read(zip);
            assertTrue(entries.keySet().toString(), entries.containsKey("logs/app-0-0.log"));
            String log = entries.get("logs/app-0-0.log");
            assertTrue(log, log.contains("[number]") && log.contains("[email]"));
            assertFalse(log, log.contains(PHONE) || log.contains("a@b.vn"));
            for (String name : entries.keySet()) assertFalse(name + " is not the security log", name.contains("security"));
        } finally {
            Files.deleteIfExists(appLog);
            Files.deleteIfExists(securityLog);
        }
    }

    @Test
    public void aSecondSaveReplacesTheFirstAndLeavesNoTempFileBehind() throws Exception {
        TestSession.admin();
        Path dir = Files.createTempDirectory("supportzip");
        Path target = dir.resolve("support.zip");
        SupportBundle.create(target);
        SupportBundle.create(target);
        try (var files = Files.list(dir)) {
            assertEquals("only the zip is there", 1, files.count());
        }
        assertTrue(Files.size(target) > 0);
    }

    @Test
    public void theSuggestedNameIsAZipWithADate() {
        String name = SupportBundle.suggestedName();
        assertTrue(name, name.matches("lunavera-support-\\d{8}-\\d{4}\\.zip"));
    }
}
