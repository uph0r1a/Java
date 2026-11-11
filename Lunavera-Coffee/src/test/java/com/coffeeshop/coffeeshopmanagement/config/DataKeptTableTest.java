package com.coffeeshop.coffeeshopmanagement.config;

import com.coffeeshop.coffeeshopmanagement.service.BackupService;
import com.coffeeshop.coffeeshopmanagement.service.SecondBackupFolder;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.Assert.*;

/**
 * TODO 6.75: section 11 of {@code docs/DATABASE.md} ("How long each kind of data is kept") stays true. It reads the sources and the guide,
 * so it needs no database: every table the program ever deletes from is named in that section, and the numbers of the files that prune
 * themselves in the section are the ones in the code. A new {@code DELETE FROM} or a changed limit therefore fails here until the
 * table is brought up to date.
 */
public class DataKeptTableTest {

    private static final Path MAIN = Paths.get("src/main/java");
    private static final Pattern DELETE = Pattern.compile("DELETE FROM\\s+`?([a-z_]+)");

    private static String read(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    /** Section 11 of the guide, from its heading to the next one. */
    private static String section() throws IOException {
        String guide = read(Paths.get("docs/DATABASE.md"));
        int start = guide.indexOf("## 11. How long each kind of data is kept");
        assertTrue("section 11 of docs/DATABASE.md is missing", start >= 0);
        int end = guide.indexOf("\n## ", start + 5);
        return end < 0 ? guide.substring(start) : guide.substring(start, end);
    }

    private static boolean named(String text, String word) {
        return Pattern.compile("(?<![A-Za-z0-9_])" + Pattern.quote(word) + "(?![A-Za-z0-9_])").matcher(text).find();
    }

    private static int number(String file, String regex) throws IOException {
        Matcher m = Pattern.compile(regex).matcher(read(MAIN.resolve("com/coffeeshop/coffeeshopmanagement").resolve(file)));
        assertTrue(file + " no longer matches " + regex, m.find());
        return Integer.parseInt(m.group(1));
    }

    /** Every table named after a literal {@code DELETE FROM} in the main sources ({@code StartSellingService} and the restore build theirs from a list). */
    private static Set<String> deletedTables() throws IOException {
        Set<String> deleted = new TreeSet<>();
        try (Stream<Path> files = Files.walk(MAIN)) {
            for (Path file : (Iterable<Path>) files.filter(f -> f.toString().endsWith(".java"))::iterator) {
                Matcher m = DELETE.matcher(read(file));
                while (m.find()) deleted.add(m.group(1));
            }
        }
        return deleted;
    }

    @Test
    public void everyTableTheProgramDeletesFromIsNamedInTheTable() throws IOException {
        Set<String> deleted = deletedTables();
        assertTrue("the sources were not read: " + deleted, deleted.size() >= 10);
        String section = section();
        Set<String> missing = new TreeSet<>();
        for (String table : deleted) if (!named(section, table)) missing.add(table);
        assertTrue("tables the program deletes from but section 11 of docs/DATABASE.md does not name (say how long they are kept): "
                + missing, missing.isEmpty());
    }

    /** The tables the section calls \"for ever\" must really have no {@code DELETE} of their own. */
    @Test
    public void theTablesSaidToBeKeptForEverAreNeverDeletedFrom() throws IOException {
        Set<String> deleted = deletedTables();
        String section = section();
        for (String table : new String[]{"orders", "order_payments", "refunds", "audit_log", "stock_log", "stock_takes", "time_clock",
                "shift_closes", "reservations", "gift_cards", "gift_card_moves", "vouchers"}) {
            assertTrue(table + " is not named in section 11", named(section, table));
            assertFalse(table + " is deleted from by the program, so \"for ever\" in section 11 is no longer true", deleted.contains(table));
        }
    }

    @Test
    public void theNumbersInTheFileTableAreTheOnesInTheCode() throws IOException {
        String section = section();
        assertTrue("automatic backups", section.contains("newest " + BackupService.KEEP_AUTOMATIC + ","));
        assertTrue("before-restore and before-cleanup copies", section.contains("newest " + BackupService.KEEP_BEFORE_RESTORE + " of each"));
        assertTrue("snapshots", section.contains("newest " + SqliteSnapshotService.KEEP + ","));
        assertTrue("second backup folder", section.contains("newest " + SecondBackupFolder.KEEP + "."));
        assertTrue("every Nth automatic backup holds the pictures",
                section.contains("every " + BackupService.PICTURES_EVERY_NTH_AUTOMATIC + "th"));
        assertTrue("security log months", section.contains(number("util/SecurityLogFile.java", "DEFAULT_KEEP_MONTHS = (\\d+)") + " months"));
        assertTrue("booking reminders", section.contains(number("service/ReminderLog.java", "KEEP_DAYS = (\\d+)") + " days"));
        assertTrue("login attempts", section.contains(number("dao/LoginAttemptStore.java", "FORGET_AFTER_MILLIS = (\\d+) \\* 60_000L") + " minutes"));
        assertTrue("login attempts, how many are tracked", section.contains("at most " + number("dao/LoginAttemptStore.java", "MAX_TRACKED = (\\d+)")));
    }
}
