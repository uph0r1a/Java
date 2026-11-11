package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.dao.TimeClockDAO;
import com.coffeeshop.coffeeshopmanagement.model.TimeClockEntry;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * What the time-clock screens ask for (TODO 6.21): clock in and out, the hours of a range, the people still clocked in, closing a
 * forgotten clock-in, and an administrator's corrections. {@link TimeClockRules} holds the rules; {@link TimeClockDAO} the permission
 * (who may see hours, who may correct) and the audit lines in the same transaction. {@code TimeClockDialog},
 * {@code TimeClockCorrectionDialog} and {@code TimeClockExport} hold no DAO of their own. Meant to be called from a background
 * thread (TODO 6.14).
 */
public class TimeClockService {

    private final TimeClockDAO clock;

    public TimeClockService() {
        this(new TimeClockDAO());
    }

    TimeClockService(TimeClockDAO clock) {
        this.clock = clock;
    }

    public Optional<TimeClockEntry> openForMe() {
        return clock.openForMe();
    }

    public TimeClockEntry clockIn() {
        return clock.clockIn();
    }

    public TimeClockEntry clockOut() {
        return clock.clockOut();
    }

    public List<TimeClockEntry> findOpen() {
        return clock.findOpen();
    }

    public List<TimeClockEntry> findBetween(LocalDate from, LocalDate to) {
        return clock.findBetween(from, to);
    }

    public List<TimeClockDAO.Hours> hours(LocalDate from, LocalDate to) {
        return clock.hours(from, to);
    }

    public Map<Integer, BigDecimal> salaries() {
        return clock.salaries();
    }

    public void closeForgotten(int entryId, LocalDateTime at) {
        clock.closeForgotten(entryId, at);
    }

    public List<TimeClockEntry> findClosedBetween(LocalDate from, LocalDate to) {
        return clock.findClosedBetween(from, to);
    }

    public List<TimeClockDAO.Person> people() {
        return clock.people();
    }

    public void correct(int entryId, LocalDateTime clockIn, LocalDateTime clockOut, String reason) {
        clock.correct(entryId, clockIn, clockOut, reason);
    }

    public TimeClockEntry addMissing(int userId, LocalDateTime clockIn, LocalDateTime clockOut, String reason) {
        return clock.addMissing(userId, clockIn, clockOut, reason);
    }

    public Optional<TimeClockEntry> openFor(int userId) {
        return clock.openFor(userId);
    }

    public TimeClockEntry clockOutFor(int userId) {
        return clock.clockOutFor(userId);
    }
}
