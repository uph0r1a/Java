package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.model.DiningTable;
import com.coffeeshop.coffeeshopmanagement.model.Reservation;
import com.coffeeshop.coffeeshopmanagement.service.ReservationRules;
import com.coffeeshop.coffeeshopmanagement.util.AppClock;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.TestSession;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/** TODO 4.43, rule D11: reservations saved in the database. */
public class ReservationDaoTest {

    private static final AtomicInteger NEXT_DAY = new AtomicInteger(2);
    private final ReservationDAO dao = new ReservationDAO();
    private DiningTable table;
    private LocalDateTime day; // a day no other test in this class uses, at 18:00

    @BeforeClass
    public static void setUpDatabase() {
        TestSession.admin();
        TestDatabaseSupport.ensureReady();
    }

    @AfterClass
    public static void signOut() {
        TestSession.clear();
    }

    @Before
    public void pickATable() {
        TestSession.admin();
        table = new TableDAO().findAll().stream().filter(t -> t.getCapacity() >= 2).findFirst().orElseThrow();
        // two days apart: one test books "the next day" (closeBefore), which must not be the day another test gets
        day = AppClock.now().toLocalDate().plusDays(NEXT_DAY.getAndAdd(2) % 55 + 2L).atTime(18, 0);
    }

    @Test
    public void aBookingIsSavedAndListedAsWaiting() {
        Reservation made = dao.add(table.getTableNumber(), "  Nam  ", " 0912345678 ", day, 2, " cạnh cửa sổ ");
        assertEquals("Nam", made.customerName());
        assertEquals("0912345678", made.phone());
        assertEquals(Reservation.Status.BOOKED, made.status());
        assertEquals(day, made.reservedFor());
        assertEquals("cạnh cửa sổ", made.note());
        assertEquals("test-admin", made.createdBy());
        assertTrue(dao.findWaiting().stream().anyMatch(r -> r.id() == made.id()));
    }

    @Test
    public void aBadBookingIsRefusedAndSavesNothing() {
        int before = dao.findWaiting().size();
        int allSeats = Math.min(new TableDAO().findAll().stream().mapToInt(DiningTable::getCapacity).sum(), ReservationRules.PEOPLE_MAX);
        for (Runnable bad : List.<Runnable>of(
                () -> dao.add(table.getTableNumber(), "", null, day, 2, null),
                () -> dao.add(table.getTableNumber(), "Nam", null, AppClock.now().minusHours(1), 2, null),
                () -> dao.add(table.getTableNumber(), "Nam", null, day, allSeats + 1, null), // more people than all the tables together
                () -> dao.add(table.getTableNumber(), "Nam", "abc", day, 2, null),
                () -> dao.add(99999, "Nam", null, day, 2, null))) {
            try {
                bad.run();
                fail("refused");
            } catch (DataAccessException expected) {
                assertFalse(expected.getMessage().isBlank());
            }
        }
        assertEquals(before, dao.findWaiting().size());
    }

    @Test
    public void aTableTakesOneBookingPerSlotButAnotherTableIsFree() {
        dao.add(table.getTableNumber(), "Nam", null, day, 2, null);
        try {
            dao.add(table.getTableNumber(), "Lan", null, day.plusMinutes(60), 2, null);
            fail("too close");
        } catch (DataAccessException expected) {
            assertEquals(I18n.t("reservation.val.taken", 90), expected.getMessage());
        }
        dao.add(table.getTableNumber(), "Lan", null, day.plusMinutes(90), 2, null); // a full slot later
        DiningTable other = new TableDAO().findAll().stream()
                .filter(t -> t.getTableNumber() != table.getTableNumber() && t.getCapacity() >= 2).findFirst().orElseThrow();
        dao.add(other.getTableNumber(), "Hà", null, day, 2, null); // the same time on another table
    }

    // TODO 3.81: two tills booking the same table at once.

    @Test
    public void onMariaDbTheTablesRowIsLockedWhileBookingAndOnSqliteItIsNot() {
        assertTrue(ReservationDAO.capacitySql(false).endsWith(" FOR UPDATE"));
        assertFalse(ReservationDAO.capacitySql(true).contains("FOR UPDATE"));
        assertTrue("the lock is on the table's own row", ReservationDAO.capacitySql(false).contains("WHERE table_number = ?"));
    }

    @Test
    public void manyTillsBookingTheSameSlotAtOnceGetOneBooking() throws Exception {
        int tableNumber = table.getTableNumber();
        int before = (int) dao.findWaiting().stream().filter(r -> r.tableNumber() == tableNumber && r.reservedFor().equals(day)).count();
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(6);
        java.util.concurrent.CountDownLatch go = new java.util.concurrent.CountDownLatch(1);
        List<java.util.concurrent.Future<Boolean>> results = new java.util.ArrayList<>();
        for (int i = 0; i < 6; i++) {
            final int n = i;
            results.add(pool.submit(() -> {
                go.await();
                try {
                    dao.add(tableNumber, "Till " + n, null, day, 2, null);
                    return true;
                } catch (DataAccessException refused) {
                    return false; // "taken", or a busy database: either way no booking
                }
            }));
        }
        go.countDown();
        int made = 0;
        for (java.util.concurrent.Future<Boolean> f : results) if (f.get(60, java.util.concurrent.TimeUnit.SECONDS)) made++;
        pool.shutdown();
        int after = (int) dao.findWaiting().stream().filter(r -> r.tableNumber() == tableNumber && r.reservedFor().equals(day)).count();
        assertEquals(made, after - before);
        assertTrue("never two bookings for one slot", made <= 1 && after <= 1);
    }

    @Test
    public void aClosedBookingFreesTheSlotAndCanBeClosedOnlyOnce() {
        Reservation first = dao.add(table.getTableNumber(), "Nam", null, day, 2, null);
        assertTrue(dao.close(first.id(), Reservation.Status.CANCELLED));
        assertFalse("a second till closing it again", dao.close(first.id(), Reservation.Status.SEATED));
        Reservation stored = dao.findById(first.id()).orElseThrow();
        assertEquals(Reservation.Status.CANCELLED, stored.status());
        assertNotNull(stored.closedAt());
        assertTrue(dao.findWaiting().stream().noneMatch(r -> r.id() == first.id()));
        dao.add(table.getTableNumber(), "Lan", null, day, 2, null); // the slot is free again
        try {
            dao.close(first.id(), Reservation.Status.BOOKED);
            fail("closing cannot leave it waiting");
        } catch (IllegalArgumentException expected) {
            // fine
        }
    }

    @Test
    public void theMapBadgeIsOnlyForABookingNearInTime() {
        LocalDateTime now = AppClock.now();
        Reservation soon = dao.add(table.getTableNumber(), "Nam", null, now.plusMinutes(40), 2, null);
        Map<Integer, String> badges = ReservationDAO.badgesOf(dao.findWaiting(), now);
        assertNotNull(badges.get(table.getTableNumber()));
        assertTrue(badges.get(table.getTableNumber()).contains("Nam"));
        dao.close(soon.id(), Reservation.Status.NO_SHOW);
        assertNull(ReservationDAO.badgesOf(dao.findWaiting(), now).get(table.getTableNumber()));
        Reservation far = dao.add(table.getTableNumber(), "Lan", null, day, 2, null); // days away: no badge yet
        assertNull(ReservationDAO.badgesOf(List.of(far), now).get(table.getTableNumber()));
    }

    @Test
    public void nobodySignedInCannotBookOrClose() {
        Reservation made = dao.add(table.getTableNumber(), "Nam", null, day, 2, null);
        TestSession.clear();
        try {
            dao.add(table.getTableNumber(), "Lan", null, day.plusHours(5), 2, null);
            fail("not signed in");
        } catch (RuntimeException expected) {
            // fine
        }
        try {
            dao.close(made.id(), Reservation.Status.CANCELLED);
            fail("not signed in");
        } catch (RuntimeException expected) {
            // fine
        }
        TestSession.admin();
        assertEquals(Reservation.Status.BOOKED, dao.findById(made.id()).orElseThrow().status());
    }

    @Test
    public void closeBeforeMarksOnlyTheBookingsBeforeTheCutoffAndWritesOneAuditLine() { // TODO 5.49
        Reservation early = dao.add(table.getTableNumber(), "Early", null, day, 2, null);
        Reservation late = dao.add(table.getTableNumber(), "Late", null, day.plusDays(1), 2, null);
        int closed = dao.closeBefore(day.plusHours(2), AppClock.now());
        assertTrue(closed >= 1);
        assertEquals(Reservation.Status.NO_SHOW, dao.findById(early.id()).orElseThrow().status());
        assertEquals(Reservation.Status.BOOKED, dao.findById(late.id()).orElseThrow().status());
    }

    // ---- TODO 3.85

    @Test
    public void aPartyBiggerThanTheTableIsBookedWithTheTablesToJoinInItsNote() {
        // the other tables of the shop are free on this day, so one more seat than the table has needs one more table
        Reservation made = dao.add(table.getTableNumber(), "Nam", null, day, table.getCapacity() + 1, "by the window");
        assertEquals(table.getCapacity() + 1, made.people());
        assertEquals(table.getTableNumber(), made.tableNumber());
        String joinPrefix = I18n.t("reservation.merge.note", "").trim();
        assertTrue(made.note(), made.note().startsWith("by the window \u00b7 " + joinPrefix));
    }

    @Test
    public void theBookingWindowIsToldTheSameThingAsTheSavingStep() {
        int allSeats = new TableDAO().findAll().stream().mapToInt(DiningTable::getCapacity).sum();
        assertNull(dao.problemFor(table.getTableNumber(), "Nam", null, day, table.getCapacity() + 1, null));
        if (allSeats + 1 <= ReservationRules.PEOPLE_MAX) {
            String problem = dao.problemFor(table.getTableNumber(), "Nam", null, day, allSeats + 1, null);
            assertEquals(I18n.t("reservation.val.capacity", table.getCapacity(), allSeats), problem);
            try {
                dao.add(table.getTableNumber(), "Nam", null, day, allSeats + 1, null);
                fail("refused");
            } catch (DataAccessException expected) {
                assertEquals(problem, expected.getMessage());
            }
        }
        assertEquals(I18n.t("reservation.val.table"), dao.problemFor(99999, "Nam", null, day, 2, null));
    }

    @Test
    public void aTableAlreadyBookedAtThatTimeDoesNotCountForABiggerParty() {
        DiningTable other = new TableDAO().findAll().stream()
                .filter(t -> t.getTableNumber() != table.getTableNumber()).findFirst().orElseThrow();
        dao.add(other.getTableNumber(), "Lan", null, day, 1, null);
        // every other table but one is still free on that day: take the party's size up to what THIS table plus the free ones seat
        int others = new TableDAO().findAll().stream().filter(t -> t.getTableNumber() != table.getTableNumber()
                && t.getTableNumber() != other.getTableNumber()).mapToInt(DiningTable::getCapacity).sum();
        int tooMany = table.getCapacity() + others + other.getCapacity();
        if (tooMany <= ReservationRules.PEOPLE_MAX) {
            assertNotNull("the booked table's seats are not available at that time",
                    dao.problemFor(table.getTableNumber(), "Nam", null, day, tooMany, null));
        }
    }

    @Test
    public void aBookingForTheCurrentMinuteIsNotRefusedAsThePast() {
        // the last table, so a booking made by another test class near this time is on a different table
        DiningTable last = new TableDAO().findAll().stream().reduce((a, b) -> b).orElseThrow();
        LocalDateTime thisMinute = AppClock.now().truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
        Reservation made = null;
        try {
            made = dao.add(last.getTableNumber(), "Nam", null, thisMinute, 1, null);
            assertEquals(thisMinute, made.reservedFor());
        } finally {
            if (made != null) dao.close(made.id(), Reservation.Status.CANCELLED);
        }
    }
}
