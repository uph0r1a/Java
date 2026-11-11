package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.model.Customer;
import com.coffeeshop.coffeeshopmanagement.service.CustomerStats.Figures;
import org.junit.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class CustomerStatsTest {

    private static Figures fig(int visits, long spent, String last) {
        return CustomerStats.figures(visits, BigDecimal.valueOf(spent), BigDecimal.ZERO, last == null ? null : LocalDateTime.parse(last));
    }

    private static Customer customer(int id) {
        Customer c = new Customer();
        c.setId(id);
        c.setFullName("C" + id);
        return c;
    }

    @Test
    public void theFiguresAreWhatTheDatabaseSummedAndTheLatestVisit() {
        Map<Integer, Figures> m = Map.of(1, fig(3, 80000, "2026-10-20T18:30"), 2, fig(1, 70000, "2026-09-01T10:00"));
        Figures one = CustomerStats.of(m, 1);
        assertEquals(3, one.visits());
        assertEquals(0, new BigDecimal(80000).compareTo(one.spent()));
        assertEquals(LocalDateTime.parse("2026-10-20T18:30"), one.lastVisit());
        assertEquals(1, CustomerStats.of(m, 2).visits());
    }

    @Test
    public void lineRefundsAreTakenOffTheTotalSpent() {
        Figures f = CustomerStats.figures(3, new BigDecimal(100000), new BigDecimal(15000), LocalDateTime.parse("2026-10-20T18:30"));
        assertEquals("the visits still count: the orders stand", 3, f.visits());
        assertEquals(0, new BigDecimal(85000).compareTo(f.spent()));
    }

    @Test
    public void aFullyRefundedCustomerSpentNothingButStillCameIn() {
        Figures f = CustomerStats.figures(1, new BigDecimal(40000), new BigDecimal(40000), null);
        assertEquals(1, f.visits());
        assertEquals(0, BigDecimal.ZERO.compareTo(f.spent()));
    }

    @Test
    public void theTotalIsNeverNegativeWhateverTheData() {
        assertEquals(0, BigDecimal.ZERO.compareTo(CustomerStats.figures(1, new BigDecimal(1000), new BigDecimal(5000), null).spent()));
        assertEquals(0, BigDecimal.ZERO.compareTo(CustomerStats.figures(0, null, null, null).spent()));
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
    public void aVisitWithNoReadableTimeStillCountsAsAVisitAndMoney() {
        Figures f = fig(2, 30000, null);
        assertEquals(2, f.visits());
        assertEquals(0, new BigDecimal(30000).compareTo(f.spent()));
        assertNull(f.lastVisit());
    }

    @Test
    public void regularFromFiveVisits() {
        assertFalse(CustomerStats.isRegular(fig(4, 4000, "2026-10-04T09:00")));
        assertTrue(CustomerStats.isRegular(fig(5, 5000, "2026-10-09T09:00")));
    }

    @Test
    public void lapsedAfterThirtyDays() {
        LocalDate today = LocalDate.of(2026, 10, 25);
        Figures recent = fig(1, 1000, "2026-09-26T23:59"); // 29 days
        Figures old = fig(1, 1000, "2026-09-25T00:00");    // 30 days
        assertFalse(CustomerStats.isLapsed(recent, today));
        assertTrue(CustomerStats.isLapsed(old, today));
        assertEquals(30, CustomerStats.daysSince(old, today));
    }

    @Test
    public void topIsBySpendingThenVisitsAndLeavesOutNonBuyers() {
        List<Customer> customers = new ArrayList<>();
        Map<Integer, Figures> m = new java.util.HashMap<>();
        for (int id = 1; id <= 14; id++) {
            customers.add(customer(id));
            if (id <= 12) m.put(id, fig(1, id * 1000L, "2026-10-01T09:00"));
        }
        m.put(3, fig(2, 3000, "2026-10-02T09:00")); // customer 3: 2 visits, 3000 in all
        m.put(2, fig(2, 3000, "2026-10-02T09:00")); // customer 2: 2 visits, 3000 in all -> ties with 3
        List<Customer> top = CustomerStats.top(customers, m);
        assertEquals(CustomerStats.TOP_COUNT, top.size());
        assertEquals(12, top.get(0).getId());
        assertFalse(top.stream().anyMatch(c -> c.getId() >= 13));
        // 3 and 2 tie on 3000 and on 2 visits: the lower id comes first, and both are in the cut-off
        assertEquals(List.of(12, 11, 10, 9, 8, 7, 6, 5, 4, 2), top.stream().map(Customer::getId).toList());
    }

    @Test
    public void aCustomerWhoseEverythingWasRefundedIsNotAmongTheBest() {
        List<Customer> customers = List.of(customer(1), customer(2));
        Map<Integer, Figures> m = Map.of(1, CustomerStats.figures(2, new BigDecimal(90000), new BigDecimal(90000), null),
                2, fig(1, 1000, "2026-10-01T09:00"));
        assertEquals(List.of(2), CustomerStats.top(customers, m).stream().map(Customer::getId).toList());
    }

    @Test
    public void filtersPickTheRightGroups() {
        LocalDate today = LocalDate.of(2026, 10, 25);
        List<Customer> customers = List.of(customer(1), customer(2), customer(3));
        Map<Integer, Figures> m = Map.of(1, fig(5, 5000, "2026-10-05T09:00"), 2, fig(1, 9000, "2026-08-01T09:00"));
        assertEquals(3, CustomerStats.apply(CustomerStats.Filter.ALL, customers, m, today).size());
        assertEquals(List.of(1), CustomerStats.apply(CustomerStats.Filter.REGULARS, customers, m, today).stream().map(Customer::getId).toList());
        assertEquals(List.of(2), CustomerStats.apply(CustomerStats.Filter.LAPSED, customers, m, today).stream().map(Customer::getId).toList());
        assertEquals(List.of(2, 1), CustomerStats.apply(CustomerStats.Filter.TOP, customers, m, today).stream().map(Customer::getId).toList());
    }

    @Test
    public void csvGetsThreeMoreColumns() {
        Customer c = customer(1);
        Map<Integer, Figures> m = Map.of(1, fig(2, 50000, "2026-10-02T10:00"));
        List<String> lines = com.coffeeshop.coffeeshopmanagement.util.ExportCsv.customers(List.of(c, customer(2)), m);
        assertEquals(3, lines.size());
        assertTrue(lines.get(1).endsWith(",2,50000,02/10/2026 10:00"));
        assertTrue(lines.get(2).endsWith(",0,0,"));
        assertEquals(lines.get(0).split(",").length, lines.get(1).split(",").length);
    }
}
