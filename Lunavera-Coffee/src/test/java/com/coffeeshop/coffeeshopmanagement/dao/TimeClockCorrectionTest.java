package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig;
import com.coffeeshop.coffeeshopmanagement.model.*;
import com.coffeeshop.coffeeshopmanagement.service.TimeClockRules;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.Permissions;
import com.coffeeshop.coffeeshopmanagement.util.Session;
import com.coffeeshop.coffeeshopmanagement.util.TestSession;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.Assert.*;

/** TODO 3.67: a finished stretch can be corrected, and a missed one added, by an administrator, with a reason and an audit line. */
public class TimeClockCorrectionTest {

    private final TimeClockDAO dao = new TimeClockDAO();
    private final int fakeUser = 3_000_000 + new Random().nextInt(900_000);
    private String language;

    @BeforeClass
    public static void setUpDatabase() {
        TestSession.admin();
        TestDatabaseSupport.ensureReady();
    }

    @Before
    public void admin() {
        language = I18n.current();
        I18n.setLanguage(I18n.EN);
        TestSession.admin();
    }

    @After
    public void putBack() {
        I18n.setLanguage(language);
        TestSession.admin();
    }

    private static final LocalDateTime D = LocalDateTime.of(2026, 3, 10, 0, 0);

    /** A finished stretch for the fake person, written straight into the table (a late clock-out as it was entered). */
    private int stretch(LocalDateTime in, LocalDateTime out) {
        return insert(fakeUser, "fake-" + fakeUser, in, out);
    }

    private static int insert(int userId, String name, LocalDateTime in, LocalDateTime out) {
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement st = c.prepareStatement("INSERT INTO time_clock (user_id, user_name, clock_in, clock_out) VALUES (?, ?, ?, ?)",
                     java.sql.Statement.RETURN_GENERATED_KEYS)) {
            st.setInt(1, userId);
            st.setString(2, name);
            st.setString(3, com.coffeeshop.coffeeshopmanagement.util.DbTime.write(in));
            st.setString(4, out == null ? null : com.coffeeshop.coffeeshopmanagement.util.DbTime.write(out));
            st.executeUpdate();
            try (ResultSet keys = st.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private TimeClockEntry read(int id) {
        return dao.findBetween(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)).stream().filter(e -> e.id() == id).findFirst().orElseThrow();
    }

    private int auditLines(String userName, String newValue) {
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement st = c.prepareStatement("SELECT COUNT(*) FROM audit_log WHERE action = 'CLOCK_CORRECTED' AND target_label = ? AND new_value = ?")) {
            st.setString(1, userName);
            st.setString(2, newValue);
            try (ResultSet rs = st.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private long minutesOfFakeUser() {
        return dao.hours(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)).stream().filter(h -> h.userId() == fakeUser).mapToLong(TimeClockDAO.Hours::minutes).sum();
    }

    // ------------------------------------------------------------------ the pure rules

    @Test
    public void theTypedFormIsReadAndWrittenTheSameWay() {
        assertEquals(LocalDateTime.of(2026, 10, 30, 17, 30), TimeClockRules.parseTyped(" 2026-10-30 17:30 "));
        assertNull(TimeClockRules.parseTyped("30/10/2026 17:30"));
        assertNull(TimeClockRules.parseTyped(null));
        assertEquals("2026-10-30 17:30", TimeClockRules.typed(LocalDateTime.of(2026, 10, 30, 17, 30)));
    }

    @Test
    public void aStretchMustHaveBothTimesEndAfterStartNotBeInTheFutureAndNotBeLongerThanADay() {
        LocalDateTime now = D.plusDays(10);
        assertNull(TimeClockRules.stretchProblem(D.withHour(8), D.withHour(17), now));
        assertNull("exactly 24 hours is allowed", TimeClockRules.stretchProblem(D, D.plusHours(24), now));
        assertEquals("TIME", TimeClockRules.stretchProblem(null, D, now));
        assertEquals("TIME", TimeClockRules.stretchProblem(D, null, now));
        assertEquals("BEFORE", TimeClockRules.stretchProblem(D.withHour(9), D.withHour(9), now));
        assertEquals("BEFORE", TimeClockRules.stretchProblem(D.withHour(9), D.withHour(8), now));
        assertEquals("FUTURE", TimeClockRules.stretchProblem(D.plusDays(9), D.plusDays(11), now));
        assertEquals("LONG", TimeClockRules.stretchProblem(D, D.plusHours(24).plusMinutes(1), now));
    }

    @Test
    public void stretchesOverlapOnlyWhenTheyShareTime() {
        assertTrue(TimeClockRules.overlaps(D.withHour(8), D.withHour(12), D.withHour(11), D.withHour(15)));
        assertTrue(TimeClockRules.overlaps(D.withHour(8), D.withHour(18), D.withHour(10), D.withHour(11)));
        assertFalse("touching is not overlapping", TimeClockRules.overlaps(D.withHour(8), D.withHour(12), D.withHour(12), D.withHour(15)));
        assertFalse(TimeClockRules.overlaps(D.withHour(8), D.withHour(12), D.withHour(13), D.withHour(15)));
        assertTrue("an open stretch has no end", TimeClockRules.overlaps(D.withHour(8), D.withHour(12), D.withHour(11), null));
        assertFalse(TimeClockRules.overlaps(D.withHour(8), D.withHour(12), D.withHour(12), null));
    }

    @Test
    public void aReasonIsRequiredAndNotTooLong() {
        assertEquals("REASON", TimeClockRules.reasonProblem(null));
        assertEquals("REASON", TimeClockRules.reasonProblem("   "));
        assertEquals("LONG", TimeClockRules.reasonProblem("x".repeat(TimeClockRules.MAX_REASON_LENGTH + 1)));
        assertNull(TimeClockRules.reasonProblem("forgot to clock out"));
    }

    // ------------------------------------------------------------------ correcting a finished stretch

    @Test
    public void aClockOutEnteredTwentyHoursLateIsCorrectedAndTheHoursFollow() {
        int id = stretch(D.withHour(8), D.plusDays(1).withHour(4)); // 20 hours: clocked out the next morning
        assertEquals(20 * 60, minutesOfFakeUser());
        dao.correct(id, D.withHour(8), D.withHour(17), "left at 17:00, clocked out next morning");
        TimeClockEntry fixed = read(id);
        assertEquals(D.withHour(17), fixed.clockOut());
        assertEquals("the hours report counts the corrected stretch", 9 * 60, minutesOfFakeUser());
        assertEquals("one audit line with the new times", 1, auditLines("fake-" + fakeUser, "2026-03-10 08:00 - 2026-03-10 17:00"));
    }

    @Test
    public void theAuditLineHoldsTheOldTimesTheNewTimesAndTheReason() throws Exception {
        int id = stretch(D.withHour(8), D.withHour(9));
        dao.correct(id, D.withHour(8), D.withHour(12), "clocked out by mistake after an hour");
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement st = c.prepareStatement("SELECT old_value, new_value, detail FROM audit_log WHERE action = 'CLOCK_CORRECTED' AND target_label = ? ORDER BY id DESC")) {
            st.setString(1, "fake-" + fakeUser);
            try (ResultSet rs = st.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("2026-03-10 08:00 - 2026-03-10 09:00", rs.getString(1));
                assertEquals("2026-03-10 08:00 - 2026-03-10 12:00", rs.getString(2));
                assertEquals("clocked out by mistake after an hour", rs.getString(3));
            }
        }
    }

    @Test
    public void theStartCanBeCorrectedToo() {
        int id = stretch(D.withHour(10), D.withHour(18));
        dao.correct(id, D.withHour(8), D.withHour(18), "arrived at 8");
        assertEquals(D.withHour(8), read(id).clockIn());
        assertEquals(10 * 60, minutesOfFakeUser());
    }

    @Test
    public void everyRefusalWritesNothing() {
        int id = stretch(D.withHour(8), D.withHour(12));
        int neighbour = stretch(D.withHour(13), D.withHour(17));
        String[][] bad = {
                {"2026-03-10 12:00", "2026-03-10 12:00", "x"},   // not after the start
                {"2026-03-10 08:00", "2026-03-10 07:00", "x"},   // before the start
                {"2026-03-10 08:00", "2027-01-01 08:00", "x"},   // in the future
                {"2026-03-10 08:00", "2026-03-11 09:00", "x"},   // over 24 hours
                {"2026-03-10 08:00", "2026-03-10 14:00", "x"},   // runs into the next stretch of the same person
                {"2026-03-10 08:00", "2026-03-10 13:00", ""},    // no reason
        };
        for (String[] b : bad) {
            try {
                dao.correct(id, TimeClockRules.parseTyped(b[0]), TimeClockRules.parseTyped(b[1]), b[2]);
                fail("must be refused: " + String.join(" | ", b));
            } catch (DataAccessException expected) {
                assertNotNull(expected.getMessage());
            }
        }
        assertEquals(D.withHour(12), read(id).clockOut());
        assertEquals(D.withHour(17), read(neighbour).clockOut());
        assertEquals(0, auditLines("fake-" + fakeUser, "2026-03-10 08:00 - 2026-03-10 14:00"));
    }

    @Test
    public void stretchesThatOnlyTouchAreFine() {
        int id = stretch(D.withHour(8), D.withHour(12));
        stretch(D.withHour(13), D.withHour(17));
        dao.correct(id, D.withHour(8), D.withHour(13), "until 13:00"); // ends exactly where the next one starts
        assertEquals(D.withHour(13), read(id).clockOut());
    }

    @Test
    public void somebodyElsesStretchAtTheSameTimeIsNotAnOverlap() {
        int id = stretch(D.withHour(8), D.withHour(12));
        insert(fakeUser + 1, "other-" + fakeUser, D.withHour(13), D.withHour(17));
        dao.correct(id, D.withHour(8), D.withHour(15), "same hours as a colleague"); // another person: fine
        assertEquals(D.withHour(15), read(id).clockOut());
    }

    @Test
    public void anUnchangedStretchAnOpenOneAndAMissingOneAreRefused() {
        int id = stretch(D.withHour(8), D.withHour(12));
        try {
            dao.correct(id, D.withHour(8), D.withHour(12), "same");
            fail("nothing changed");
        } catch (DataAccessException expected) {
            assertTrue(expected.getMessage().contains("not changed"));
        }
        int open = insert(fakeUser, "fake-" + fakeUser, D.withHour(20), null);
        try {
            dao.correct(open, D.withHour(20), D.withHour(22), "still open");
            fail("an open stretch is closed with closeForgotten");
        } catch (DataAccessException expected) {
            assertTrue(expected.getMessage().contains("finished"));
        }
        try {
            dao.correct(999_999_999, D.withHour(8), D.withHour(9), "no such");
            fail("no such stretch");
        } catch (DataAccessException expected) {
            // refused
        }
    }

    @Test
    public void onlyAnAdministratorCorrects() {
        int id = stretch(D.withHour(8), D.withHour(12));
        User cashier = new User();
        cashier.setId(5);
        cashier.setUsername("clock-cashier");
        cashier.setRole(Role.EMPLOYEE);
        Session.start(cashier, null);
        try {
            dao.correct(id, D.withHour(8), D.withHour(11), "me");
            fail("a cashier may not correct hours");
        } catch (Permissions.PermissionDeniedException expected) {
            // refused
        }
        try {
            dao.addMissing(5, D.withHour(8), D.withHour(11), "me");
            fail("a cashier may not add hours");
        } catch (Permissions.PermissionDeniedException expected) {
            // refused
        }
        TestSession.admin();
        assertEquals(D.withHour(12), read(id).clockOut());
    }

    // ------------------------------------------------------------------ adding a missed stretch

    private int newStaffAccount(Role role, AccountStatus status) {
        User user = new User();
        user.setUsername("clockfix-" + UUID.randomUUID().toString().substring(0, 8));
        user.setPasswordHash("x");
        user.setRole(role);
        user.setStatus(status);
        return new UserDAO().insert(user).getId();
    }

    @Test
    public void aMissedStretchIsAddedForAnActiveStaffAccountWithAnAuditLine() {
        int userId = newStaffAccount(Role.EMPLOYEE, AccountStatus.ACTIVE);
        TimeClockEntry added = dao.addMissing(userId, D.withHour(8), D.withHour(16), "forgot to clock in");
        assertEquals(userId, added.userId());
        assertFalse(added.isOpen());
        assertTrue(dao.findClosedBetween(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)).stream().anyMatch(e -> e.id() == added.id()));
        assertEquals(1, auditLines(added.userName(), "2026-03-10 08:00 - 2026-03-10 16:00"));
        assertTrue(dao.people().stream().anyMatch(p -> p.userId() == userId));
    }

    @Test
    public void aMissedStretchIsRefusedForACustomerALockedAccountOrNobody() {
        for (int userId : new int[]{newStaffAccount(Role.CUSTOMER, AccountStatus.ACTIVE), newStaffAccount(Role.EMPLOYEE, AccountStatus.LOCKED), 987_654_321}) {
            try {
                dao.addMissing(userId, D.withHour(8), D.withHour(16), "x");
                fail("not a staff account that can clock in: " + userId);
            } catch (DataAccessException expected) {
                assertTrue(expected.getMessage().contains("person"));
            }
        }
    }

    @Test
    public void aMissedStretchThatOverlapsOrIsMalformedIsRefused() {
        int userId = newStaffAccount(Role.EMPLOYEE, AccountStatus.ACTIVE);
        dao.addMissing(userId, D.withHour(8), D.withHour(12), "morning");
        for (LocalDateTime[] t : new LocalDateTime[][]{{D.withHour(11), D.withHour(15)}, {D.withHour(15), D.withHour(14)}}) {
            try {
                dao.addMissing(userId, t[0], t[1], "x");
                fail("must be refused");
            } catch (DataAccessException expected) {
                assertNotNull(expected.getMessage());
            }
        }
        dao.addMissing(userId, D.withHour(12), D.withHour(15), "afternoon, touching the morning");
    }

    @Test
    public void closedStretchesListedHereLeaveOutTheOpenOnes() {
        int closed = stretch(D.withHour(8), D.withHour(12));
        int open = insert(fakeUser, "fake-" + fakeUser, D.withHour(20), null);
        List<TimeClockEntry> list = dao.findClosedBetween(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));
        assertTrue(list.stream().anyMatch(e -> e.id() == closed));
        assertTrue(list.stream().noneMatch(e -> e.id() == open));
    }
}
