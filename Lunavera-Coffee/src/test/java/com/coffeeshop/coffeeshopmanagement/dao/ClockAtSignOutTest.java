package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.model.Role;
import com.coffeeshop.coffeeshopmanagement.model.TimeClockEntry;
import com.coffeeshop.coffeeshopmanagement.model.User;
import com.coffeeshop.coffeeshopmanagement.util.Session;
import com.coffeeshop.coffeeshopmanagement.util.TestSession;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Optional;
import java.util.Random;

import static org.junit.Assert.*;

/** TODO 5.38: clocking out for the person the idle timer has just signed out, and nobody else. Real SQLite. */
public class ClockAtSignOutTest {

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

    private static User newStaff() {
        User u = new User();
        u.setId(3_000_000 + new Random().nextInt(900_000));
        u.setUsername("clock-out-" + u.getId());
        u.setRole(Role.EMPLOYEE);
        return u;
    }

    @Test
    public void afterTheSessionEndedThePersonCanBeClockedOutByTheirId() {
        User staff = newStaff();
        Session.start(staff, null);
        TimeClockEntry in = dao.clockIn();
        Session.clear(); // what the idle sign-out does

        Optional<TimeClockEntry> open = dao.openFor(staff.getId());
        assertTrue(open.isPresent());
        assertEquals(in.id(), open.get().id());
        TimeClockEntry done = dao.clockOutFor(staff.getId());
        assertEquals(in.id(), done.id());
        assertNotNull(done.clockOut());
        assertTrue(dao.openFor(staff.getId()).isEmpty());
        TestSession.admin();
    }

    @Test
    public void aSignedInCashierCannotClockAColleagueOut() {
        User a = newStaff();
        User b = newStaff();
        Session.start(b, null);
        dao.clockIn();
        Session.start(a, null);
        try {
            dao.clockOutFor(b.getId());
            fail("a cashier clocked a colleague out");
        } catch (DataAccessException expected) {
            // refused
        }
        try {
            dao.openFor(b.getId());
            fail("a cashier read a colleague's open stretch");
        } catch (DataAccessException expected) {
            // refused
        }
        Session.start(b, null);
        assertTrue("the colleague is still clocked in", dao.openForMe().isPresent());
        dao.clockOut();
        TestSession.admin();
    }

    @Test
    public void clockingOutWhenNotClockedInIsRefusedAndOwnClockOutStillWorks() {
        User staff = newStaff();
        Session.start(staff, null);
        try {
            dao.clockOutFor(staff.getId());
            fail("nothing was open");
        } catch (DataAccessException expected) {
            // refused
        }
        dao.clockIn();
        assertNotNull(dao.clockOut().clockOut()); // the existing method behaves as before
        TestSession.admin();
    }
}
