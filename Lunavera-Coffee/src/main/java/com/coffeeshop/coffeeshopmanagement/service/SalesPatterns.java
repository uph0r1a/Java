package com.coffeeshop.coffeeshopmanagement.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * The arithmetic behind the report's "when" and "compared with before" figures (TODO 4.40): sales by hour of the day, by
 * weekday, and the change against the period just before. Pure functions on lists of paid orders, so the rules are tested on a
 * fixed day without a database, and the same code serves SQLite and MariaDB (no date functions in SQL).
 */
public final class SalesPatterns {

    private SalesPatterns() {
    }

    /** One paid order: when the money came in (or when it was opened, for an order with no paid time) and its total. */
    public record PaidOrder(LocalDateTime time, BigDecimal total) {
    }

    /** {@code key}: the hour 0-23, or the ISO weekday 1 (Monday) to 7 (Sunday). */
    public record Slot(int key, int orders, BigDecimal revenue) {
    }

    /**
     * @param daysInRange how many days of this weekday the range contains
     * @param average     revenue per such day (revenue / daysInRange), so a Monday-heavy range does not look busier than it is
     */
    public record WeekdaySlot(DayOfWeek day, int orders, BigDecimal revenue, int daysInRange, BigDecimal average) {
    }

    /** The period just before: the same number of days, ending the day before {@code from}. */
    public record Range(LocalDate from, LocalDate to) {
    }

    /** {@code percent} is null when the earlier figure was 0 (a percentage of nothing is not a number). */
    public record Change(BigDecimal current, BigDecimal previous, Integer percent) {
        public int direction() {
            return current.compareTo(previous);
        }
    }

    /** Hours that had at least one paid order, in time order. */
    public static List<Slot> byHour(List<PaidOrder> orders) {
        int[] count = new int[24];
        BigDecimal[] sum = new BigDecimal[24];
        for (int i = 0; i < 24; i++) {
            sum[i] = BigDecimal.ZERO;
        }
        for (PaidOrder order : orders) {
            int hour = order.time().getHour();
            count[hour]++;
            sum[hour] = sum[hour].add(order.total());
        }
        List<Slot> result = new ArrayList<>();
        for (int hour = 0; hour < 24; hour++) {
            if (count[hour] > 0) {
                result.add(new Slot(hour, count[hour], sum[hour]));
            }
        }
        return result;
    }

    /** Monday to Sunday, only the weekdays the range contains (a one-day range gives one row). */
    public static List<WeekdaySlot> byWeekday(List<PaidOrder> orders, LocalDate from, LocalDate to) {
        int[] count = new int[8];
        BigDecimal[] sum = new BigDecimal[8];
        for (int i = 0; i < 8; i++) {
            sum[i] = BigDecimal.ZERO;
        }
        for (PaidOrder order : orders) {
            int day = order.time().getDayOfWeek().getValue();
            count[day]++;
            sum[day] = sum[day].add(order.total());
        }
        List<WeekdaySlot> result = new ArrayList<>();
        for (DayOfWeek day : DayOfWeek.values()) {
            int days = countWeekday(from, to, day);
            if (days == 0) {
                continue;
            }
            BigDecimal revenue = sum[day.getValue()];
            result.add(new WeekdaySlot(day, count[day.getValue()], revenue, days,
                    revenue.divide(BigDecimal.valueOf(days), 0, RoundingMode.HALF_UP)));
        }
        return result;
    }

    /** How many days of {@code day} lie in {@code from..to} (both included). */
    public static int countWeekday(LocalDate from, LocalDate to, DayOfWeek day) {
        if (to.isBefore(from)) {
            return 0;
        }
        long total = ChronoUnit.DAYS.between(from, to) + 1;
        long toFirst = Math.floorMod(day.getValue() - from.getDayOfWeek().getValue(), 7); // days from `from` to the first such day
        return toFirst >= total ? 0 : (int) ((total - 1 - toFirst) / 7 + 1);
    }

    /** The slot with the most orders (the larger revenue breaks a tie); null for an empty list. */
    public static Slot busiest(List<Slot> slots) {
        Slot best = null;
        for (Slot slot : slots) {
            if (best == null || slot.orders() > best.orders()
                    || (slot.orders() == best.orders() && slot.revenue().compareTo(best.revenue()) > 0)) {
                best = slot;
            }
        }
        return best;
    }

    /** The weekday with the highest AVERAGE revenue per day; null for an empty list. */
    public static WeekdaySlot bestWeekday(List<WeekdaySlot> slots) {
        WeekdaySlot best = null;
        for (WeekdaySlot slot : slots) {
            if (slot.orders() > 0 && (best == null || slot.average().compareTo(best.average()) > 0)) {
                best = slot;
            }
        }
        return best;
    }

    /** The period before {@code from..to}: as many days, ending the day before {@code from}. */
    public static Range previousRange(LocalDate from, LocalDate to) {
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        return new Range(from.minusDays(days), from.minusDays(1));
    }

    public static Change change(BigDecimal current, BigDecimal previous) {
        Integer percent = null;
        if (previous.signum() != 0) {
            percent = current.subtract(previous).multiply(BigDecimal.valueOf(100))
                    .divide(previous.abs(), 0, RoundingMode.HALF_UP).intValue();
        }
        return new Change(current, previous, percent);
    }

    /** Width of a text bar: {@code value} out of {@code max}, at most {@code width} blocks, at least one when there is any value. */
    public static int barLength(BigDecimal value, BigDecimal max, int width) {
        if (max.signum() <= 0 || value.signum() <= 0) {
            return 0;
        }
        int length = value.multiply(BigDecimal.valueOf(width)).divide(max, 0, RoundingMode.HALF_UP).intValue();
        return Math.max(1, Math.min(width, length));
    }
}
