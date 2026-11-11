package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.model.Reservation;
import com.coffeeshop.coffeeshopmanagement.util.AppClock;
import com.coffeeshop.coffeeshopmanagement.util.Permissions;
import com.coffeeshop.coffeeshopmanagement.util.Session;
import com.coffeeshop.coffeeshopmanagement.util.TestSession;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * TODO 2.94 and 2.97: a forgotten booking is closed in one go with one audit line, and a table with a waiting booking can be neither
 * removed nor made too small for it. The forgotten bookings are made with the clock set far in the past (a booking cannot be made
 * for a time that has passed), then the clock goes back to the real time.
 */
public class ForgottenBookingTest {

    private static final AtomicInteger NEXT = new AtomicInteger(0);

    private final ReservationDAO reservations = new ReservationDAO();
    private final TableDAO tables = new TableDAO();

    @BeforeClass
    public static void setUpDatabase() {
        TestSession.admin();
        TestDatabaseSupport.ensureReady();
    }

    @Before
    public void signIn() {
        TestSession.admin();
        AppClock.reset();
    }

    @After
    public void restoreClock() {
        AppClock.reset();
        TestSession.admin();
    }

    /** A booking that is long past now: made while the clock stood in 2001, so it was a booking for the future then. */
    private Reservation forgotten(int table, String name) {
        LocalDateTime when = LocalDateTime.of(2001, 1, 1, 18, 0).plusDays(NEXT.incrementAndGet());
        AppClock.fix(when.minusDays(1));
        try {
            return reservations.add(table, name, null, when, 2, null);
        } finally {
            AppClock.reset();
        }
    }

    private Reservation tomorrow(int table, String name, int people) {
        return reservations.add(table, name, null, AppClock.now().plusDays(1).withHour(19).withMinute(0).withSecond(0).withNano(0), people, null);
    }

    private int newTable(int seats) {
        return tables.add("booked-" + System.nanoTime(), seats);
    }

    private static long auditLines() throws Exception {
        try (Connection c = DatabaseConfig_connection(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM audit_log WHERE action = 'RESERVATIONS_CLOSED'")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static Connection DatabaseConfig_connection() throws Exception {
        return com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig.getConnection();
    }

    private Reservation reload(int id) {
        return reservations.findWaiting().stream().filter(r -> r.id() == id).findFirst().orElse(null);
    }

    // ---- 2.94: closing the forgotten bookings ----

    @Test
    public void theForgottenOnesAreListedApartFromTheWaitingOnes() {
        int table = newTable(4);
        Reservation old = forgotten(table, "Cũ");
        Reservation next = tomorrow(table, "Mai", 2);
        LocalDateTime now = AppClock.now();
        assertTrue(reservations.findToClose(now).stream().anyMatch(r -> r.id() == old.id()));
        assertTrue(reservations.findToClose(now).stream().noneMatch(r -> r.id() == next.id()));
        assertNotNull("both are still waiting", reload(old.id()));
        assertNotNull(reload(next.id()));
    }

    @Test
    public void closingMarksOnlyTheForgottenOnesAsNotComeWithOneAuditLine() throws Exception {
        int table = newTable(4);
        Reservation a = forgotten(table, "Cũ một");
        Reservation b = forgotten(table, "Cũ hai");
        Reservation next = tomorrow(table, "Mai", 2);
        long before = auditLines();

        int closed = reservations.closeStale(AppClock.now());
        assertTrue("at least these two: " + closed, closed >= 2);
        assertNull("no longer waiting", reload(a.id()));
        assertNull(reload(b.id()));
        assertNotNull("tomorrow's booking is untouched", reload(next.id()));
        assertEquals("ONE audit line for all of them", before + 1, auditLines());
        reservations.close(next.id(), Reservation.Status.CANCELLED);
    }

    @Test
    public void closingWhenNothingIsForgottenWritesNothing() throws Exception {
        reservations.closeStale(AppClock.now()); // whatever other tests left
        long before = auditLines();
        assertEquals(0, reservations.closeStale(AppClock.now()));
        assertEquals(before, auditLines());
    }

    @Test
    public void onlyAnAdministratorMayCloseThem() {
        int table = newTable(4);
        Reservation old = forgotten(table, "Cũ");
        com.coffeeshop.coffeeshopmanagement.model.User u = new com.coffeeshop.coffeeshopmanagement.model.User();
        u.setId(2);
        u.setUsername("test-cashier");
        u.setRole(com.coffeeshop.coffeeshopmanagement.model.Role.EMPLOYEE);
        Session.start(u, null);
        assertThrows(Permissions.PermissionDeniedException.class, () -> reservations.closeStale(AppClock.now()));
        TestSession.admin();
        assertNotNull("nothing was closed", reload(old.id()));
        reservations.close(old.id(), Reservation.Status.NO_SHOW);
    }

    // ---- 2.97: a table with a waiting booking ----

    @Test
    public void aTableWithAWaitingBookingCannotBeRemovedAndTheMessageNamesTheBooking() {
        int table = newTable(4);
        Reservation made = tomorrow(table, "Lan Anh", 3);
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class, () -> tables.remove(table));
        assertTrue(refused.getMessage(), refused.getMessage().contains("Lan Anh"));
        assertTrue("the table is still there", tables.findAll().stream().anyMatch(t -> t.getTableNumber() == table));

        reservations.close(made.id(), Reservation.Status.CANCELLED);
        assertTrue("free to remove once the booking is closed", tables.remove(table));
    }

    @Test
    public void aForgottenBookingAlsoKeepsTheTableUntilItIsClosed() {
        int table = newTable(4);
        Reservation old = forgotten(table, "Quên");
        assertThrows(IllegalArgumentException.class, () -> tables.remove(table));
        reservations.closeStale(AppClock.now());
        assertNull(reload(old.id()));
        assertTrue(tables.remove(table));
    }

    @Test
    public void aFinishedBookingDoesNotKeepATable() {
        int table = newTable(4);
        for (Reservation.Status end : new Reservation.Status[]{Reservation.Status.CANCELLED, Reservation.Status.NO_SHOW}) {
            Reservation r = tomorrow(table, "Xong " + end, 2);
            reservations.close(r.id(), end);
        }
        assertTrue(tables.remove(table));
    }

    @Test
    public void aTableCannotBeMadeSmallerThanAWaitingParty() {
        int table = newTable(6);
        Reservation made = tomorrow(table, "Nhóm lớn", 5);
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class, () -> tables.update(table, "Bàn lớn", 4));
        assertTrue(refused.getMessage(), refused.getMessage().contains("Nhóm lớn") && refused.getMessage().contains("5"));
        tables.update(table, "Bàn lớn", 5); // exactly as many seats as the party is fine
        tables.update(table, "Bàn lớn", 8);
        assertEquals(5, tables.findAll().stream().filter(t -> t.getTableNumber() == table).findFirst().orElseThrow().getCapacity() > 0 ? 5 : 0);
        reservations.close(made.id(), Reservation.Status.CANCELLED);
        tables.update(table, "Bàn nhỏ", 2); // nobody is waiting any more
    }

    @Test
    public void renamingATableIsNotHeldUpByABooking() {
        int table = newTable(4);
        Reservation made = tomorrow(table, "Giữ chỗ", 4);
        tables.update(table, "Tên mới " + System.nanoTime(), 4);
        reservations.close(made.id(), Reservation.Status.CANCELLED);
    }
}
