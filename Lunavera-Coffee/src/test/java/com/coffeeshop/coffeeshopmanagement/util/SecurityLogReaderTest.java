package com.coffeeshop.coffeeshopmanagement.util;

import com.coffeeshop.coffeeshopmanagement.util.SecurityLogReader.Entry;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.List;

import static org.junit.Assert.*;

/** TODO 4.44: reading the newest lines of the security log without reading a whole big file. */
public class SecurityLogReaderTest {

    private static final YearMonth OCT = YearMonth.of(2026, 10);

    private static String line(int n) {
        return String.format(java.util.Locale.ROOT, "2026-10-%02d 10:00:00 INFO    event number %d%n", n, n);
    }

    private static Path logs(String fileName, String content) throws Exception {
        Path dir = Files.createTempDirectory("seclog");
        Files.writeString(dir.resolve(fileName), content, StandardCharsets.UTF_8);
        return dir;
    }

    @Test
    public void aFileWithFewerLinesThanAskedGivesAllOfThemNewestFirst() throws Exception {
        Path dir = logs("security-2026-10.log", line(1) + line(2) + line(3));
        List<Entry> entries = SecurityLogReader.recent(dir, OCT, 50, SecurityLogReader.MAX_BYTES);
        assertEquals(3, entries.size());
        assertEquals("event number 3", entries.get(0).text());
        assertEquals("event number 1", entries.get(2).text());
        assertEquals("2026-10-03 10:00:00", entries.get(0).time());
        assertEquals("INFO", entries.get(0).level());
    }

    @Test
    public void aFileWithMoreLinesThanAskedGivesOnlyTheNewest() throws Exception {
        StringBuilder all = new StringBuilder();
        for (int i = 1; i <= 28; i++) all.append(line(i));
        Path dir = logs("security-2026-10.log", all.toString());
        List<Entry> entries = SecurityLogReader.recent(dir, OCT, 5, SecurityLogReader.MAX_BYTES);
        assertEquals(5, entries.size());
        assertEquals("event number 28", entries.get(0).text());
        assertEquals("event number 24", entries.get(4).text());
    }

    @Test
    public void aHalfWrittenLastLineIsLeftOut() throws Exception {
        Path dir = logs("security-2026-10.log", line(1) + line(2) + "2026-10-03 10:00:00 WARNING  half writ");
        List<Entry> entries = SecurityLogReader.recent(dir, OCT, 50, SecurityLogReader.MAX_BYTES);
        assertEquals(2, entries.size());
        assertEquals("event number 2", entries.get(0).text());
    }

    @Test
    public void onlyTheEndOfABigFileIsReadAndTheCutLineAtTheFrontIsDropped() throws Exception {
        StringBuilder all = new StringBuilder();
        for (int i = 1; i <= 28; i++) all.append(line(i));
        Path dir = logs("security-2026-10.log", all.toString());
        int small = line(1).length() * 3 + 10; // a little more than three lines: the front of the read starts inside a line
        List<Entry> entries = SecurityLogReader.recent(dir, OCT, 50, small);
        assertTrue("at most the lines that fit: " + entries.size(), entries.size() <= 3 && entries.size() >= 2);
        assertEquals("event number 28", entries.get(0).text());
        for (Entry e : entries) assertTrue(e.text().startsWith("event number "));
    }

    @Test
    public void linesThatAreNotLogLinesAreSkippedAndATextWithVietnameseLettersSurvives() throws Exception {
        Path dir = logs("security-2026-10.log", "garbage without a time\n" + "\n"
                + "2026-10-05 08:15:00 WARNING  Tài khoản bị khóa: nhanvien1\n");
        List<Entry> entries = SecurityLogReader.recent(dir, OCT, 50, SecurityLogReader.MAX_BYTES);
        assertEquals(1, entries.size());
        assertEquals("Tài khoản bị khóa: nhanvien1", entries.get(0).text());
        assertEquals("WARNING", entries.get(0).level());
    }

    @Test
    public void aMonthThatContinuesInNumberedFilesIsReadNewestPartFirst() throws Exception {
        Path dir = Files.createTempDirectory("seclog");
        Files.writeString(dir.resolve("security-2026-10.log"), line(1) + line(2), StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("security-2026-10.1.log"), line(3) + line(4), StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("security-2026-10.10.log"), line(9), StandardCharsets.UTF_8);
        List<Entry> entries = SecurityLogReader.recent(dir, OCT, 50, SecurityLogReader.MAX_BYTES);
        assertEquals(5, entries.size());
        assertEquals("event number 9", entries.get(0).text());
        assertEquals("event number 4", entries.get(1).text());
        assertEquals("event number 1", entries.get(4).text());
    }

    @Test
    public void aShortMonthIsFilledFromTheMonthBefore() throws Exception {
        Path dir = Files.createTempDirectory("seclog");
        Files.writeString(dir.resolve("security-2026-09.log"),
                "2026-09-30 23:00:00 INFO    last of september\n", StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("security-2026-10.log"), line(1), StandardCharsets.UTF_8);
        List<Entry> entries = SecurityLogReader.recent(dir, OCT, 50, SecurityLogReader.MAX_BYTES);
        assertEquals(2, entries.size());
        assertEquals("event number 1", entries.get(0).text());
        assertEquals("last of september", entries.get(1).text());
    }

    @Test
    public void aMissingFolderOrFileGivesAnEmptyListAndNeverAnException() throws Exception {
        Path none = Files.createTempDirectory("seclog").resolve("nope");
        assertTrue(SecurityLogReader.recent(none, OCT, 50, SecurityLogReader.MAX_BYTES).isEmpty());
        Path empty = logs("security-2026-10.log", "");
        assertTrue(SecurityLogReader.recent(empty, OCT, 50, SecurityLogReader.MAX_BYTES).isEmpty());
        Path other = logs("something-else.log", line(1));
        assertTrue(SecurityLogReader.recent(other, OCT, 50, SecurityLogReader.MAX_BYTES).isEmpty());
    }
}
