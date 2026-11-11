package com.coffeeshop.coffeeshopmanagement.model;

import java.time.LocalDateTime;

/** One stretch of work of one person: from clock-in to clock-out ({@code clockOut} null while it is still open). */
public record TimeClockEntry(int id, int userId, String userName, LocalDateTime clockIn, LocalDateTime clockOut) {
    public boolean isOpen() {
        return clockOut == null;
    }
}
