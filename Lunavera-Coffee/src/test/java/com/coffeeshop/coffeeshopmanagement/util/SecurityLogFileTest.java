package com.coffeeshop.coffeeshopmanagement.util;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** TODO 2A.25: the security events get a file of their own, one per month, that nothing else writes to. */
public class SecurityLogFileTest {

    private static final ZoneId SHOP = ZoneId.of("Asia/Ho_Chi_Minh"); // UTC+7, no daylight saving

    /** A clock the test can move. */
    private static final class MovableClock extends Clock {
        private Instant instant;

        MovableClock(String utc) {
            this.instant = Instant.parse(utc);
        }

        void set(String utc) {
            this.instant = Instant.parse(utc);
        }

        @Override
        public ZoneId getZone() {
            return SHOP;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    private static Path tempDir() throws IOException {
        return Files.createTempDirectory("lunavera-seclog-test");
    }

    private static List<String> lines(Path file) throws IOException {
        return Files.readAllLines(file, StandardCharsets.UTF_8);
    }

    private static long countFiles(Path dir) throws IOException {
        try (var files = Files.list(dir)) {
            return files.count();
        }
    }

    @Test
    public void aRecordBecomesOneLineInTheFileOfItsMonth() throws IOException {
        Path dir = tempDir();
        SecurityLogFile handler = new SecurityLogFile(dir, new MovableClock("2026-10-18T07:03:11Z"));
        handler.publish(new LogRecord(Level.WARNING, "SECURITY: failed login: wrong password for 'admin'"));
        handler.publish(new LogRecord(Level.INFO, "SECURITY: login: admin (id 1, ADMIN)"));
        List<String> lines = lines(dir.resolve("security-2026-10.log"));
        assertEquals(2, lines.size());
        assertEquals("2026-10-18 14:03:11 WARNING SECURITY: failed login: wrong password for 'admin'", lines.get(0));
        assertEquals("2026-10-18 14:03:11 INFO    SECURITY: login: admin (id 1, ADMIN)", lines.get(1));
    }

    @Test
    public void lineBreaksInAMessageCannotForgeASecondLine() throws IOException {
        Path dir = tempDir();
        SecurityLogFile handler = new SecurityLogFile(dir, new MovableClock("2026-10-18T07:03:11Z"));
        handler.publish(new LogRecord(Level.WARNING, "first\r\n2026-10-18 14:03:12 INFO    SECURITY: login: admin\nthird"));
        List<String> lines = lines(dir.resolve("security-2026-10.log"));
        assertEquals("one record is one line", 1, lines.size());
        assertTrue(lines.get(0), lines.get(0).endsWith("first 2026-10-18 14:03:12 INFO    SECURITY: login: admin third"));
    }

    @Test
    public void aNewMonthStartsANewFileAndTheOldOneIsLeftAlone() throws IOException {
        Path dir = tempDir();
        MovableClock clock = new MovableClock("2026-10-31T16:59:59Z"); // 23:59:59 on 31 October in Vietnam
        SecurityLogFile handler = new SecurityLogFile(dir, clock);
        handler.publish(new LogRecord(Level.INFO, "late in October"));
        clock.set("2026-10-31T17:00:00Z"); // midnight: 1 November in Vietnam
        handler.publish(new LogRecord(Level.INFO, "early in November"));
        assertEquals(1, lines(dir.resolve("security-2026-10.log")).size());
        assertEquals(1, lines(dir.resolve("security-2026-11.log")).size());
    }

    @Test
    public void aFullFileContinuesInANumberedPart() throws IOException {
        Path dir = tempDir();
        SecurityLogFile handler = new SecurityLogFile(dir, new MovableClock("2026-10-18T07:00:00Z"), 100, 24);
        for (int i = 0; i < 6; i++) {
            handler.publish(new LogRecord(Level.INFO, "line number " + i + " padded to make the file fill up quickly"));
        }
        assertTrue(Files.exists(dir.resolve("security-2026-10.log")));
        assertTrue("the second file continues", Files.exists(dir.resolve("security-2026-10.1.log")));
        long total = 0;
        try (var files = Files.list(dir)) {
            for (Path f : (Iterable<Path>) files::iterator) total += lines(f).size();
        }
        assertEquals("no line is lost while there is room", 6, total);
    }

    @Test
    public void afterTheLastPartTheFolderStopsGrowing() throws IOException {
        Path dir = tempDir();
        SecurityLogFile handler = new SecurityLogFile(dir, new MovableClock("2026-10-18T07:00:00Z"), 1, 24);
        for (int i = 0; i < SecurityLogFile.MAX_PARTS + 15; i++) {
            handler.publish(new LogRecord(Level.INFO, "x" + i)); // every line fills a file of 1 byte
        }
        assertEquals(SecurityLogFile.MAX_PARTS, countFiles(dir));
    }

    @Test
    public void monthsOlderThanTheKeepTimeAreDeletedAtStart() throws IOException {
        Path dir = tempDir();
        for (String name : new String[]{"security-2020-01.log", "security-2024-09.log", "security-2024-09.3.log",
                "security-2024-10.log", "security-2026-10.log", "app-0-0.log", "security-2026-13.log", "security-notes.txt"}) {
            Files.writeString(dir.resolve(name), "x\n");
        }
        new SecurityLogFile(dir, new MovableClock("2026-10-18T07:00:00Z"), SecurityLogFile.DEFAULT_MAX_BYTES, 24);
        assertFalse("older than 24 months", Files.exists(dir.resolve("security-2020-01.log")));
        assertFalse(Files.exists(dir.resolve("security-2024-09.log")));
        assertFalse("its parts go with it", Files.exists(dir.resolve("security-2024-09.3.log")));
        assertTrue("exactly 24 months back is kept", Files.exists(dir.resolve("security-2024-10.log")));
        assertTrue(Files.exists(dir.resolve("security-2026-10.log")));
        assertTrue("not ours", Files.exists(dir.resolve("app-0-0.log")));
        assertTrue("a name that is not a month is left alone", Files.exists(dir.resolve("security-2026-13.log")));
        assertTrue(Files.exists(dir.resolve("security-notes.txt")));
    }

    @Test
    public void aFolderThatDoesNotExistYetIsCreated() throws IOException {
        Path dir = tempDir().resolve("a").resolve("b");
        SecurityLogFile handler = new SecurityLogFile(dir, Clock.fixed(Instant.parse("2026-01-02T03:04:05Z"), ZoneOffset.UTC));
        handler.publish(new LogRecord(Level.INFO, "hello"));
        assertEquals(1, lines(dir.resolve("security-2026-01.log")).size());
    }

    @Test
    public void aFolderThatCannotBeWrittenNeverThrowsAtTheCaller() throws IOException {
        Path blocker = Files.createTempFile("lunavera-seclog-blocker", ".txt"); // a FILE where the folder should be
        SecurityLogFile handler = new SecurityLogFile(blocker.resolve("logs"), Clock.systemUTC());
        handler.publish(new LogRecord(Level.INFO, "must not throw"));
        handler.publish(new LogRecord(Level.INFO, "twice"));
    }

    @Test
    public void aRecordBelowTheHandlerLevelIsIgnored() throws IOException {
        Path dir = tempDir();
        SecurityLogFile handler = new SecurityLogFile(dir, new MovableClock("2026-10-18T07:00:00Z"));
        handler.setLevel(Level.WARNING);
        handler.publish(new LogRecord(Level.INFO, "ignored"));
        assertFalse(Files.exists(dir.resolve("security-2026-10.log")));
        handler.publish(null); // never an error
    }
}
