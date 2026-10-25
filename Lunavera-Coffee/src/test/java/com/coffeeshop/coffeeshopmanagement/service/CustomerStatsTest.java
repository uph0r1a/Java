package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.model.Customer;
import com.coffeeshop.coffeeshopmanagement.service.CustomerStats.Figures;
import com.coffeeshop.coffeeshopmanagement.service.CustomerStats.Row;
import org.junit.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class CustomerStatsTest {

    private static Row row(int id, String time, long total) {
        return new Row(id, time == null ? null : LocalDateTime.parse(time), BigDecimal.valueOf(total));
    }

    private static Customer customer(int id) {
        Customer c = new Customer();
        c.setId(id);
        c.setFullName("C" + id);
        return c;
    }

    @Test
    public void countsVisitsSumsMoneyAndKeepsTheLatestVisit() {
        Map<Integer, Figures> m = CustomerStats.aggregate(List.of(
                row(1, "2026-10-01T09:00", 30000), row(1, "2026-10-20T18:30", 45000), row(1, "2026-10-10T08:00", 5000),
                row(2, "2026-09-01T10:00", 70000)));
        Figures one = CustomerStats.of(m, 1);
        assertEquals(3, one.visits());
        assertEquals(0, new BigDecimal(80000).compareTo(one.spent()));
        assertEquals(LocalDateTime.parse("2026-10-20T18:30"), one.lastVisit());
        assertEquals(1, CustomerStats.of(m, 2).visits());
    }

    @Test
    public void aCustomerWithNoPaidOrderHasNothing() {
        Figures none = CustomerStats.of(Map.of(), 7);
        assertEquals(0, none.visits());
        assertEquals(0, BigDecimal.ZERO.compareTo(none.spent()));
        assertNull(none.lastVisit());
        assertFalse(CustomerStats.isRegular(none));
        assertFalse(CustomerStats.isLapsed(none, LocalDate.of(2026, 10, 25)));
    }

    @Test
    public void anUnreadableTimeStillCountsAsAVisitAndMoney() {
        Map<Integer, Figures> m = CustomerStats.aggregate(List.of(row(1, null, 10000), row(1, "2026-10-01T09:00", 20000)));
        assertEquals(2, CustomerStats.of(m, 1).visits());
        assertEquals(LocalDateTime.parse("2026-10-01T09:00"), CustomerStats.of(m, 1).lastVisit());
    }

    @Test
    public void regularFromFiveVisits() {
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < 4; i++) rows.add(row(1, "2026-10-0" + (i + 1) + "T09:00", 1000));
        assertFalse(CustomerStats.isRegular(CustomerStats.of(CustomerStats.aggregate(rows), 1)));
        rows.add(row(1, "2026-10-09T09:00", 1000));
        assertTrue(CustomerStats.isRegular(CustomerStats.of(CustomerStats.aggregate(rows), 1)));
    }

    @Test
    public void lapsedAfterThirtyDays() {
        LocalDate today = LocalDate.of(2026, 10, 25);
        Figures recent = CustomerStats.of(CustomerStats.aggregate(List.of(row(1, "2026-09-26T23:59", 1000))), 1); // 29 days
        Figures old = CustomerStats.of(CustomerStats.aggregate(List.of(row(1, "2026-09-25T00:00", 1000))), 1);    // 30 days
        assertFalse(CustomerStats.isLapsed(recent, today));
        assertTrue(CustomerStats.isLapsed(old, today));
        assertEquals(30, CustomerStats.daysSince(old, today));
    }

    @Test
    public void topIsBySpendingThenVisitsAndLeavesOutNonBuyers() {
        List<Customer> customers = new ArrayList<>();
        List<Row> rows = new ArrayList<>();
        for (int id = 1; id <= 14; id++) {
            customers.add(customer(id));
            if (id <= 12) rows.add(row(id, "2026-10-01T09:00", id * 1000L));
        }
        rows.add(row(3, "2026-10-02T09:00", 0)); // customer 3: 2 visits, 3000 in all
        rows.add(row(2, "2026-10-02T09:00", 1000)); // customer 2: 2 visits, 3000 in all -> ties with 3
        Map<Integer, Figures> m = CustomerStats.aggregate(rows);
        List<Customer> top = CustomerStats.top(customers, m);
        assertEquals(CustomerStats.TOP_COUNT, top.size());
        assertEquals(12, top.get(0).getId());
        assertFalse(top.stream().anyMatch(c -> c.getId() >= 13));
        // 3 and 2 tie on 3000 and on 2 visits: the lower id comes first, and both are in the cut-off
        assertEquals(List.of(12, 11, 10, 9, 8, 7, 6, 5, 4, 2), top.stream().map(Customer::getId).toList());
    }

    @Test
    public void filtersPickTheRightGroups() {
        LocalDate today = LocalDate.of(2026, 10, 25);
        List<Customer> customers = List.of(customer(1), customer(2), customer(3));
        List<Row> rows = new ArrayList<>();
        for (int i = 1; i <= 5; i++) rows.add(row(1, "2026-10-0" + i + "T09:00", 1000));
        rows.add(row(2, "2026-08-01T09:00", 9000));
        Map<Integer, Figures> m = CustomerStats.aggregate(rows);
        assertEquals(3, CustomerStats.apply(CustomerStats.Filter.ALL, customers, m, today).size());
        assertEquals(List.of(1), CustomerStats.apply(CustomerStats.Filter.REGULARS, customers, m, today).stream().map(Customer::getId).toList());
        assertEquals(List.of(2), CustomerStats.apply(CustomerStats.Filter.LAPSED, customers, m, today).stream().map(Customer::getId).toList());
        assertEquals(List.of(2, 1), CustomerStats.apply(CustomerStats.Filter.TOP, customers, m, today).stream().map(Customer::getId).toList());
    }

    @Test
    public void csvGetsThreeMoreColumns() {
        Customer c = customer(1);
        Map<Integer, Figures> m = CustomerStats.aggregate(List.of(row(1, "2026-10-01T09:05", 30000), row(1, "2026-10-02T10:00", 20000)));
        List<String> lines = com.coffeeshop.coffeeshopmanagement.util.ExportCsv.customers(List.of(c, customer(2)), m);
        assertEquals(3, lines.size());
        assertTrue(lines.get(1).endsWith(",2,50000,02/10/2026 10:00"));
        assertTrue(lines.get(2).endsWith(",0,0,"));
        assertEquals(lines.get(0).split(",").length, lines.get(1).split(",").length);
    }
}
