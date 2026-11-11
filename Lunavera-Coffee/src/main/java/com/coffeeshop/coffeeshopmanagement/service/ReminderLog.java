package com.coffeeshop.coffeeshopmanagement.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Which booking reminders this PC has already shown, and on which day (TODO 2.94). A booking is reminded once per day, not once per
 * program start: the list is kept in a small file in the data folder, so closing and opening the program does not bring the same
 * reminders back. It belongs to this PC (every till reminds its own cashier). A file that cannot be read or written never stops the
 * reminders: the list then lives in memory only, as it did before.
 */
public final class ReminderLog {

    private static final Logger LOGGER = Logger.getLogger(ReminderLog.class.getName());
    /** Lines older than this many days are dropped when the file is written. */
    static final int KEEP_DAYS = 3;

    private final Map<Integer, LocalDate> shown = new HashMap<>();
    private final Path file;

    /** A log kept in memory only. */
    public ReminderLog() {
        this(null);
    }

    /** A log kept in {@code file} (read now; written after every change). */
    public ReminderLog(Path file) {
        this.file = file;
        if (file != null && Files.isRegularFile(file)) {
            try {
                List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                for (String line : lines) {
                    String[] parts = line.trim().split("\\s+");
                    if (parts.length != 2) continue;
                    try {
                        shown.put(Integer.parseInt(parts[0]), LocalDate.parse(parts[1]));
                    } catch (RuntimeException ignored) {
                        // a damaged line is skipped
                    }
                }
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Could not read the reminder list " + file, e);
            }
        }
    }

    /** True the first time it is asked for this booking on this day (and remembers it); false after that on the same day. */
    public synchronized boolean firstTimeToday(int reservationId, LocalDate today) {
        if (today.equals(shown.get(reservationId))) {
            return false;
        }
        shown.put(reservationId, today);
        shown.values().removeIf(day -> day.isBefore(today.minusDays(KEEP_DAYS)));
        save();
        return true;
    }

    private void save() {
        if (file == null) return;
        try {
            List<String> lines = shown.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(e -> e.getKey() + " " + e.getValue()).toList();
            Files.write(file, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Could not write the reminder list " + file, e);
        }
    }
}
