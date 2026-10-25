package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.model.Customer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What a customer has bought (TODO 4.39): number of visits (paid orders), total spent and the last visit, worked out in Java from
 * the paid orders so SQLite and MariaDB give the same figures. Also the rules for "regular" and "not back for a while", and the
 * ranking of the best customers.
 */
public final class CustomerStats {

    /** A customer with this many paid orders or more is a regular ("Khách thân thiết"). */
    public static final int REGULAR_VISITS = 5;
    /** Not seen for this many days: worth a call or a voucher. */
    public static final int LAPSED_DAYS = 30;
    /** How many customers the "best customers" filter shows. */
    public static final int TOP_COUNT = 10;

    /** One paid order of a known customer. */
    public record Row(int customerId, LocalDateTime time, BigDecimal total) {
    }

    public record Figures(int visits, BigDecimal spent, LocalDateTime lastVisit) {
        public static final Figures NONE = new Figures(0, BigDecimal.ZERO, null);
    }

    public enum Filter { ALL, REGULARS, TOP, LAPSED }

    private CustomerStats() {
    }

    public static Map<Integer, Figures> aggregate(List<Row> rows) {
        Map<Integer, Figures> result = new HashMap<>();
        for (Row row : rows) {
            Figures old = result.getOrDefault(row.customerId(), Figures.NONE);
            BigDecimal total = row.total() == null ? BigDecimal.ZERO : row.total();
            LocalDateTime last = old.lastVisit();
            if (row.time() != null && (last == null || row.time().isAfter(last))) last = row.time();
            result.put(row.customerId(), new Figures(old.visits() + 1, old.spent().add(total), last));
        }
        return result;
    }

    public static Figures of(Map<Integer, Figures> all, int customerId) {
        return all.getOrDefault(customerId, Figures.NONE);
    }

    public static boolean isRegular(Figures f) {
        return f.visits() >= REGULAR_VISITS;
    }

    /** Has bought before, but not for {@link #LAPSED_DAYS} days or more. */
    public static boolean isLapsed(Figures f, LocalDate today) {
        return f.lastVisit() != null && daysSince(f, today) >= LAPSED_DAYS;
    }

    public static long daysSince(Figures f, LocalDate today) {
        return f.lastVisit() == null ? -1 : ChronoUnit.DAYS.between(f.lastVisit().toLocalDate(), today);
    }

    /** The {@link #TOP_COUNT} customers who spent most (only those who spent something), biggest first; ties by more visits. */
    public static List<Customer> top(List<Customer> customers, Map<Integer, Figures> all) {
        List<Customer> spenders = new ArrayList<>();
        for (Customer c : customers) {
            if (of(all, c.getId()).spent().signum() > 0) spenders.add(c);
        }
        spenders.sort(Comparator
                .comparing((Customer c) -> of(all, c.getId()).spent()).reversed()
                .thenComparing(c -> of(all, c.getId()).visits(), Comparator.reverseOrder())
                .thenComparingInt(Customer::getId));
        return spenders.size() > TOP_COUNT ? new ArrayList<>(spenders.subList(0, TOP_COUNT)) : spenders;
    }

    public static List<Customer> apply(Filter filter, List<Customer> customers, Map<Integer, Figures> all, LocalDate today) {
        switch (filter) {
            case REGULARS:
                return customers.stream().filter(c -> isRegular(of(all, c.getId()))).toList();
            case LAPSED:
                return customers.stream().filter(c -> isLapsed(of(all, c.getId()), today)).toList();
            case TOP:
                return top(customers, all);
            default:
                return customers;
        }
    }
}
