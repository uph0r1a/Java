package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.model.Role;
import com.coffeeshop.coffeeshopmanagement.model.TimeClockEntry;
import com.coffeeshop.coffeeshopmanagement.model.User;
import com.coffeeshop.coffeeshopmanagement.service.TimeClockRules;
import com.coffeeshop.coffeeshopmanagement.util.AppClock;
import com.coffeeshop.coffeeshopmanagement.util.Session;
import com.coffeeshop.coffeeshopmanagement.util.TestSession;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Random;

import static org.junit.Assert.*;

/** TODO 7, staff clock-in and hours: the rules, and the clock in and out on real SQLite. */
public class TimeClockTest {

    private final TimeClockDAO dao = new TimeClockDAO();

    @BeforeClass
    public static void setUpDatabase() {
        TestSession.admin();
        TestDatabaseSupport.ensureReady();
    }

    @AfterClass
    public static void signOut() {
        TestSession.clear();
    }

    /** A person nobody used before (the test database persists between runs). */
    private static void signInAsNewStaff() {
        User u = new User();
        u.setId(2_000_000 + new Random().nextInt(900_000));
        u.setUsername("clock-" + u.getId());
        u.setRole(Role.EMPLOYEE);
        Session.start(u, null);
    }

    private static void sleepSeconds() {
        try {
            Thread.sleep(1100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    public void minutesInsideAWindowAreCutAtItsEdges() {
        LocalDateTime d = LocalDateTime.of(2026, 3, 10, 0, 0);
        // 22:00 to 02:00 next day: 120 minutes on each day
        LocalDateTime in = d.withHour(22);
        LocalDateTime out = d.plusDays(1).withHour(2);
        assertEquals(120, TimeClockRules.minutesWithin(in, out, d, d.plusDays(1), null));
        assertEquals(120, TimeClockRules.minutesWithin(in, out, d.plusDays(1), d.plusDays(2), null));
        assertEquals(0, TimeClockRules.minutesWithin(in, out, d.plusDays(2), d.plusDays(3), null));
        assertEquals(0, TimeClockRules.minutesWithin(out, in, d, d.plusDays(3), null)); // never negative
    }

    @Test
    public void anOpenStretchCountsUpToNowButNotPastTheForgottenLimit() {
        LocalDateTime in = LocalDateTime.of(2026, 3, 10, 8, 0);
        LocalDateTime from = in.toLocalDate().atStartOfDay();
        LocalDateTime to = from.plusDays(5);
        assertEquals(90, TimeClockRules.minutesWithin(in, null, from, to, in.plusMinutes(90)));
        assertEquals(TimeClockRules.forgottenAfterHours() * 60L, TimeClockRules.minutesWithin(in, null, from, to, in.plusDays(3)));
        assertFalse(TimeClockRules.looksForgotten(in, in.plusHours(15)));
        assertTrue(TimeClockRules.looksForgotten(in, in.plusHours(16)));
    }

    @Test
    public void hoursAreWrittenAsHoursAndMinutes() {
        assertEquals("0:00", TimeClockRules.hoursText(0));
        assertEquals("7:30", TimeClockRules.hoursText(450));
        assertEquals("26:05", TimeClockRules.hoursText(26 * 60 + 5));
        assertEquals("0:00", TimeClockRules.hoursText(-5));
    }

    @Test
    public void clockInThenOutMakesOneFinishedStretch() {
        signInAsNewStaff();
        assertTrue(dao.openForMe().isEmpty());
        TimeClockEntry in = dao.clockIn();
        assertTrue(in.isOpen());
        assertTrue(dao.openForMe().isPresent());
        TimeClockEntry out = dao.clockOut();
        assertEquals(in.id(), out.id());
        assertFalse(out.isOpen());
        assertTrue(dao.openForMe().isEmpty());
    }

    @Test
    public void youCannotClockInTwiceOrOutWhenNotIn() {
        signInAsNewStaff();
        try {
            dao.clockOut();
            fail("not clocked in");
        } catch (DataAccessException expected) {
            // fine
        }
        dao.clockIn();
        try {
            dao.clockIn();
            fail("already in");
        } catch (DataAccessException expected) {
            // fine
        }
        dao.clockOut();
    }

    @Test
    public void onlyAnAdministratorReadsHoursOrClosesAStretch() {
        signInAsNewStaff();
        TimeClockEntry in = dao.clockIn();
        LocalDate today = AppClock.today();
        try {
            dao.hours(today, today);
            fail("staff must not read hours");
        } catch (RuntimeException expected) {
            // fine
        }
        try {
            dao.closeForgotten(in.id(), AppClock.now().minusMinutes(1));
            fail("staff must not close a stretch");
        } catch (RuntimeException expected) {
            // fine
        }
        dao.clockOut();
    }

    @Test
    public void anAdministratorSeesTheHoursAndCanCloseAForgottenStretch() {
        signInAsNewStaff();
        int userId = Session.getCurrentUser().getId();
        TimeClockEntry in = dao.clockIn();
        TestSession.admin();
        assertTrue(dao.findOpen().stream().anyMatch(e -> e.id() == in.id()));
        TimeClockDAO.Hours mine = dao.hours(AppClock.today(), AppClock.today()).stream().filter(h -> h.userId() == userId).findFirst().orElseThrow();
        assertTrue(mine.clockedIn());
        // a time before the clock-in, and one in the future, are refused
        try {
            dao.closeForgotten(in.id(), in.clockIn().minusMinutes(5));
            fail("before the clock-in");
        } catch (DataAccessException expected) {
            // fine
        }
        try {
            dao.closeForgotten(in.id(), AppClock.now().plusHours(2));
            fail("future");
        } catch (DataAccessException expected) {
            // fine
        }
        sleepSeconds(); // the end must be after the start and not in the future
        dao.closeForgotten(in.id(), AppClock.now().minusNanos(1));
        assertTrue(dao.findOpen().stream().noneMatch(e -> e.id() == in.id()));
        try {
            dao.closeForgotten(in.id(), AppClock.now());
            fail("already closed");
        } catch (DataAccessException expected) {
            // fine
        }
    }
}
