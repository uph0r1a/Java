package com.coffeeshop.coffeeshopmanagement.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The file the security events go to (TODO 2A.25): {@code security-2026-10.log}, one file per month, and nothing else in it.
 *
 * <p>Why not the normal log: that one keeps only 3 files of 1 MB and every part of the app writes to it, so a burst of other
 * messages could push "account locked", "password reset" and "discount refused" out of it within hours. This file is only
 * written by {@link SecurityLog}, is rotated by the calendar and not by size, and old months are kept for two years.
 *
 * <ul>
 *   <li>A month's file that reaches {@code maxBytes} continues in {@code security-2026-10.1.log}, {@code .2.log} ... up to
 *       {@code MAX_PARTS}; after that lines of that month are dropped (the disk is never filled by a log).</li>
 *   <li>One line per record: {@code 2026-10-18 14:03:11 WARNING  message}; line breaks inside the message become spaces,
 *       so a typed text cannot forge a second line.</li>
 *   <li>A failure to write is reported once through the handler's error manager and never thrown at the caller.</li>
 * </ul>
 */
public final class SecurityLogFile extends Handler {

    static final int MAX_PARTS = 20;
    static final long DEFAULT_MAX_BYTES = 5_000_000;
    static final int DEFAULT_KEEP_MONTHS = 24;

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Pattern NAME = Pattern.compile("security-(\\d{4})-(\\d{2})(?:\\.(\\d+))?\\.log");

    private final Path dir;
    private final Clock clock;
    private final long maxBytes;
    private boolean reportedFailure;

    public SecurityLogFile(Path dir, Clock clock) {
        this(dir, clock, DEFAULT_MAX_BYTES, DEFAULT_KEEP_MONTHS);
    }

    SecurityLogFile(Path dir, Clock clock, long maxBytes, int keepMonths) {
        this.dir = dir;
        this.clock = clock;
        this.maxBytes = maxBytes;
        setLevel(java.util.logging.Level.ALL);
        try {
            Files.createDirectories(dir);
            deleteOldMonths(keepMonths);
        } catch (IOException e) {
            reportError("Cannot prepare the security log folder", e, java.util.logging.ErrorManager.OPEN_FAILURE);
        }
    }

    /** The file a line written now goes to, or {@code null} when this month already has {@code MAX_PARTS} full files. */
    Path currentFile() {
        YearMonth month = YearMonth.now(clock);
        String base = "security-" + month;
        for (int part = 0; part < MAX_PARTS; part++) {
            Path file = dir.resolve(part == 0 ? base + ".log" : base + "." + part + ".log");
            try {
                if (!Files.exists(file) || Files.size(file) < maxBytes) {
                    return file;
                }
            } catch (IOException e) {
                return file;
            }
        }
        return null;
    }

    @Override
    public synchronized void publish(LogRecord record) {
        if (record == null || !isLoggable(record)) {
            return;
        }
        Path file = currentFile();
        if (file == null) {
            return;
        }
        String message = record.getMessage() == null ? "" : record.getMessage().replaceAll("[\\r\\n]+", " ");
        String line = LocalDateTime.now(clock).format(STAMP) + " " + String.format(java.util.Locale.ROOT, "%-7s", record.getLevel().getName())
                + " " + message + System.lineSeparator();
        try {
            Files.writeString(file, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            if (!reportedFailure) {
                reportedFailure = true;
                reportError("Cannot write the security log " + file, e, java.util.logging.ErrorManager.WRITE_FAILURE);
            }
        }
    }

    @Override
    public void flush() {
        // every line is written and closed at once
    }

    @Override
    public void close() {
        // nothing is kept open
    }

    /** Deletes the files of months older than {@code keepMonths} (this month counts as month 0). */
    private void deleteOldMonths(int keepMonths) throws IOException {
        YearMonth oldest = YearMonth.now(clock).minusMonths(keepMonths);
        try (var files = Files.list(dir)) {
            for (Path file : (Iterable<Path>) files::iterator) {
                Matcher m = NAME.matcher(file.getFileName().toString());
                if (!m.matches()) {
                    continue;
                }
                try {
                    if (YearMonth.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))).isBefore(oldest)) {
                        Files.deleteIfExists(file);
                    }
                } catch (java.time.DateTimeException ignored) {
                    // a name like security-2026-13.log is not ours to judge: leave it
                }
            }
        }
    }
}
