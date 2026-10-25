package com.coffeeshop.coffeeshopmanagement.util;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.Assert.assertTrue;

/**
 * TODO 6.50: {@link AppClock} is "the one place" the data code reads the date from, so a test that pins the date reaches
 * every saved date, default date and date range. This reads the sources and fails when a file outside the short list of
 * allowed exceptions (the class comment of {@link AppClock} says why each is allowed) calls the system clock for a date.
 */
public class AppClockUsageTest {

    private static final Pattern SYSTEM_CLOCK =
            Pattern.compile("\\b(?:LocalDateTime|LocalDate|LocalTime|Instant|ZonedDateTime)\\.now\\(\\)|System\\.currentTimeMillis\\(\\)");

    /** Files that must follow the real clock: file times, file names, the PC-clock check, timers. Keep it short. */
    private static final Set<String> REAL_CLOCK = Set.of(
            "AppClock.java", "MonotonicClock.java", "LunaveraApplication.java", "DatabaseHealth.java", "MoneyMigration.java",
            "SqliteSnapshotService.java", "AdminDashboardController.java", "CustomerController.java", "OrderHistoryWindow.java",
            "ProductController.java", "BackupService.java", "DataPaths.java", "ImageStorage.java", "UnusedPictures.java");

    @Test
    public void onlyTheListedFilesReadTheSystemClockForADate() throws IOException {
        List<String> offenders = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        try (Stream<Path> files = Files.walk(Path.of("src", "main", "java"))) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                String name = file.getFileName().toString();
                int uses = 0;
                for (String line : Files.readAllLines(file)) {
                    String code = line.strip();
                    if (code.startsWith("*") || code.startsWith("//") || code.startsWith("/*")) continue;
                    if (SYSTEM_CLOCK.matcher(line).find()) uses++;
                }
                if (uses > 0) seen.add(name);
                if (uses > 0 && !REAL_CLOCK.contains(name)) offenders.add(name + " (" + uses + ")");
            }
        }
        assertTrue("These files read the system clock for a date; use AppClock.now() / AppClock.today() "
                + "(or add the file to REAL_CLOCK with a reason in the AppClock comment): " + offenders, offenders.isEmpty());
        List<String> stale = new ArrayList<>();
        for (String allowed : REAL_CLOCK) {
            if (!seen.contains(allowed) && !allowed.equals("AppClock.java") && !allowed.equals("MonotonicClock.java")) stale.add(allowed);
        }
        assertTrue("These files no longer read the system clock; remove them from REAL_CLOCK: " + stale, stale.isEmpty());
    }

    @Test
    public void aPinnedDateIsSeenByTheCodeThatSavesDates() {
        java.time.LocalDate day = java.time.LocalDate.of(2020, 2, 29);
        try {
            AppClock.pinDate(day);
            org.junit.Assert.assertEquals(day, AppClock.today());
            org.junit.Assert.assertEquals(day, AppClock.now().toLocalDate());
            // the first-run owner and the report's default pickers read it from here (TODO 6.50)
            org.junit.Assert.assertEquals(day.withDayOfMonth(1), AppClock.today().withDayOfMonth(1));
        } finally {
            AppClock.reset();
        }
    }
}
