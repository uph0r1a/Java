package com.coffeeshop.coffeeshopmanagement.util;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the end of the security log for the screen (TODO 4.44): the newest lines of this month's
 * {@code security-YYYY-MM.log} files (and the month before when this one is still short), newest first.
 *
 * <p>Only the last {@link #MAX_BYTES} of a file are read, so a big file cannot freeze the screen. A line that was cut by that
 * limit at the front, a last line that was only half written (no line end yet) and anything that does not look like a log
 * line are left out; nothing here ever throws because of the content of the file. Read-only.
 */
public final class SecurityLogReader {

    public static final int MAX_LINES = 50;
    public static final int MAX_BYTES = 64 * 1024;

    /** One line of the log: {@code 2026-10-18 14:03:11 WARNING  text}. */
    public record Entry(String time, String level, String text) { }

    private static final Pattern LINE = Pattern.compile("^(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}) (\\S+)\\s*(.*)$");
    private static final Pattern PART = Pattern.compile("security-(\\d{4}-\\d{2})(?:\\.(\\d+))?\\.log");

    private SecurityLogReader() {
    }

    /** The newest entries of this month (and, if needed, last month), newest first, at most {@code maxLines}. */
    public static List<Entry> recent(Path logsDir, YearMonth now, int maxLines, int maxBytes) {
        List<Entry> result = new ArrayList<>();
        for (YearMonth month : new YearMonth[]{now, now.minusMonths(1)}) {
            for (Path file : filesOfMonth(logsDir, month)) {
                if (result.size() >= maxLines) return result;
                for (String line : tailLines(file, maxBytes)) {
                    Matcher m = LINE.matcher(line);
                    if (m.matches()) {
                        result.add(new Entry(m.group(1), m.group(2), m.group(3)));
                        if (result.size() >= maxLines) return result;
                    }
                }
            }
        }
        return result;
    }

    /** The month's files, the newest part first: {@code security-2026-10.2.log}, {@code .1.log}, then {@code security-2026-10.log}. */
    static List<Path> filesOfMonth(Path logsDir, YearMonth month) {
        List<Path> found = new ArrayList<>();
        List<Integer> parts = new ArrayList<>();
        if (!Files.isDirectory(logsDir)) return found;
        try (var files = Files.list(logsDir)) {
            for (Path file : (Iterable<Path>) files::iterator) {
                Matcher m = PART.matcher(file.getFileName().toString());
                if (m.matches() && m.group(1).equals(month.toString()) && Files.isRegularFile(file)) {
                    found.add(file);
                    parts.add(m.group(2) == null ? 0 : parseOrZero(m.group(2)));
                }
            }
        } catch (IOException | RuntimeException e) {
            return new ArrayList<>();
        }
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < found.size(); i++) order.add(i);
        order.sort((a, b) -> Integer.compare(parts.get(b), parts.get(a)));
        List<Path> sorted = new ArrayList<>();
        for (int i : order) sorted.add(found.get(i));
        return sorted;
    }

    private static int parseOrZero(String number) {
        try {
            return Integer.parseInt(number);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * The complete lines among the last {@code maxBytes} of {@code file}, newest first. When the read starts in the middle of
     * the file the first, cut line is dropped; when the file does not end with a line end the last line is dropped as
     * half-written. An unreadable file gives an empty list.
     */
    static List<String> tailLines(Path file, int maxBytes) {
        try (RandomAccessFile in = new RandomAccessFile(file.toFile(), "r")) {
            long size = in.length();
            long start = Math.max(0, size - Math.max(1, maxBytes));
            byte[] bytes = new byte[(int) (size - start)];
            in.seek(start);
            in.readFully(bytes);
            int from = 0;
            if (start > 0) {
                while (from < bytes.length && bytes[from] != '\n') from++;
                from++; // past the line end; the cut line before it is dropped
            }
            int to = bytes.length;
            if (to > 0 && bytes[to - 1] != '\n') {
                while (to > from && bytes[to - 1] != '\n') to--; // the half-written last line
            }
            if (from >= to) return new ArrayList<>();
            String text = new String(bytes, from, to - from, StandardCharsets.UTF_8);
            List<String> lines = new ArrayList<>();
            for (String line : text.split("\\R")) {
                if (!line.isBlank()) lines.add(line);
            }
            Collections.reverse(lines);
            return lines;
        } catch (IOException | RuntimeException e) {
            return new ArrayList<>();
        }
    }
}
