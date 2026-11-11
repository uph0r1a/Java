package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.util.BusinessDay;
import com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig;
import com.coffeeshop.coffeeshopmanagement.config.SqlStatus;
import com.coffeeshop.coffeeshopmanagement.model.AuditAction;
import com.coffeeshop.coffeeshopmanagement.model.AuditTarget;
import com.coffeeshop.coffeeshopmanagement.model.TimeClockEntry;
import com.coffeeshop.coffeeshopmanagement.service.TimeClockRules;
import com.coffeeshop.coffeeshopmanagement.util.AppClock;
import com.coffeeshop.coffeeshopmanagement.util.DbTime;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.Permissions;
import com.coffeeshop.coffeeshopmanagement.util.Session;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Staff clock-in and clock-out (TODO 7). Anybody signed in clocks themselves in and out (never somebody else); one open stretch per
 * person at a time, guaranteed inside one transaction. Only an administrator reads everybody's hours or closes a forgotten stretch,
 * and that correction is written in the audit log. The times are the till's clock ({@link AppClock}).
 */
public class TimeClockDAO {

    /** One person's total in a window. {@code forgotten} is true when an open stretch of theirs was cut at the forgotten limit. */
    public record Hours(int userId, String userName, long minutes, boolean forgotten, boolean clockedIn) {
    }

    private static final String COLUMNS = "id, user_id, user_name, clock_in, clock_out";

    /** The signed-in person's open stretch, if any. */
    public Optional<TimeClockEntry> openForMe() {
        int userId = me();
        try (Connection connection = DatabaseConfig.getConnection()) {
            return open(connection, userId);
        } catch (SQLException e) {
            throw new DataAccessException("Failed to read the time clock", e);
        }
    }

    /** Clocks the signed-in person in; refused when they already are. */
    public TimeClockEntry clockIn() {
        int userId = me();
        String name = Session.getDisplayName();
        try (Connection connection = DatabaseConfig.getConnection()) {
            connection.setAutoCommit(false);
            try {
                if (open(connection, userId).isPresent()) throw new DataAccessException(I18n.t("time.clock.already.in"));
                LocalDateTime now = AppClock.now();
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO time_clock (user_id, user_name, clock_in) VALUES (?, ?, ?)")) {
                    statement.setInt(1, userId);
                    statement.setString(2, name == null || name.isBlank() ? "?" : name);
                    statement.setString(3, DbTime.write(now));
                    statement.executeUpdate();
                }
                TimeClockEntry entry = open(connection, userId).orElseThrow();
                connection.commit();
                return entry;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            if (UniqueViolation.isUnique(e)) {
                // Two tills clocked the same person in at the same moment and the database's one-open-stretch rule caught it (TODO 2.98).
                throw new DataAccessException(I18n.t("time.clock.already.in"), e);
            }
            throw new DataAccessException("Failed to clock in", e);
        }
    }

    /** Clocks the signed-in person out and returns the finished stretch; refused when they are not clocked in. */
    public TimeClockEntry clockOut() {
        return clockOutUser(me());
    }

    /** The open stretch of {@code userId}: for the person the idle timer has just signed out, when nobody is signed in any more (TODO 5.38). */
    public Optional<TimeClockEntry> openFor(int userId) {
        mustBeMeOrNobody(userId);
        try (Connection connection = DatabaseConfig.getConnection()) {
            return open(connection, userId);
        } catch (SQLException e) {
            throw new DataAccessException("Failed to read the time clock", e);
        }
    }

    /**
     * Clocks {@code userId} out. Only for the person the idle timer has just signed out (TODO 5.38): the session is gone by then, so
     * {@link #clockOut()} cannot say who. Refused for somebody else while a different person is signed in, so a cashier still cannot
     * clock a colleague out (an administrator closes a forgotten stretch with {@link #closeForgotten}).
     */
    public TimeClockEntry clockOutFor(int userId) {
        mustBeMeOrNobody(userId);
        return clockOutUser(userId);
    }

    private static void mustBeMeOrNobody(int userId) {
        if (Session.getCurrentUser() != null && Session.getCurrentUser().getId() != userId) {
            throw new DataAccessException(I18n.t("admin.only.message"));
        }
    }

    private TimeClockEntry clockOutUser(int userId) {
        try (Connection connection = DatabaseConfig.getConnection()) {
            connection.setAutoCommit(false);
            try {
                TimeClockEntry entry = open(connection, userId).orElseThrow(() -> new DataAccessException(I18n.t("time.clock.not.in")));
                LocalDateTime now = AppClock.now();
                if (!now.isAfter(entry.clockIn())) now = entry.clockIn().plusSeconds(1); // a clock set back must not give a negative stretch
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE time_clock SET clock_out = ? WHERE id = ? AND clock_out IS NULL")) {
                    statement.setString(1, DbTime.write(now));
                    statement.setInt(2, entry.id());
                    if (statement.executeUpdate() != 1) throw new DataAccessException(I18n.t("time.clock.not.in"));
                }
                connection.commit();
                return new TimeClockEntry(entry.id(), entry.userId(), entry.userName(), entry.clockIn(), now);
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to clock out", e);
        }
    }

    /** Administrator: everybody still clocked in, longest first (a forgotten one shows at the top). */
    public List<TimeClockEntry> findOpen() {
        Permissions.requireAdmin("read the open time clock stretches");
        return query("SELECT " + COLUMNS + " FROM time_clock WHERE clock_out IS NULL ORDER BY clock_in", null, null);
    }

    /** Administrator: the stretches that touch {@code [from, to]} (both days included), newest first. */
    public List<TimeClockEntry> findBetween(LocalDate from, LocalDate to) {
        Permissions.requireAdmin("read the time clock");
        return query("SELECT " + COLUMNS + " FROM time_clock WHERE clock_in < ? AND (clock_out IS NULL OR clock_out > ?) ORDER BY clock_in DESC",
                BusinessDay.startOf(to.plusDays(1)), BusinessDay.startOf(from));
    }

    private List<TimeClockEntry> query(String sql, LocalDateTime before, LocalDateTime after) {
        List<TimeClockEntry> result = new ArrayList<>();
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (before != null) {
                statement.setString(1, DbTime.write(before));
                statement.setString(2, DbTime.write(after));
            }
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) result.add(read(rs));
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to read the time clock", e);
        }
        return result;
    }

    /** Administrator: hours per person in {@code [from, to]} (both days included), the most hours first. */
    public List<Hours> hours(LocalDate from, LocalDate to) {
        Permissions.requireAdmin("read the staff hours");
        LocalDateTime start = BusinessDay.startOf(from); // the shop's day (TODO 4.50)
        LocalDateTime end = BusinessDay.startOf(to.plusDays(1));
        LocalDateTime now = AppClock.now();
        Map<Integer, long[]> minutes = new LinkedHashMap<>(); // [minutes, forgotten, clockedIn]
        Map<Integer, String> names = new LinkedHashMap<>();
        for (TimeClockEntry e : findBetween(from, to)) {
            long[] row = minutes.computeIfAbsent(e.userId(), k -> new long[3]);
            names.putIfAbsent(e.userId(), e.userName());
            row[0] += TimeClockRules.minutesWithin(e.clockIn(), e.clockOut(), start, end, now);
            if (e.isOpen()) {
                row[2] = 1;
                if (TimeClockRules.looksForgotten(e.clockIn(), now)) row[1] = 1;
            }
        }
        List<Hours> result = new ArrayList<>();
        minutes.forEach((id, row) -> result.add(new Hours(id, names.get(id), row[0], row[1] == 1, row[2] == 1)));
        result.sort((a, b) -> Long.compare(b.minutes(), a.minutes()));
        return result;
    }

    /**
     * Administrator: the salary in the staff table of each account that has one (TODO 4.60), by user id, to put beside the hours as a pay
     * estimate ({@link com.coffeeshop.coffeeshopmanagement.service.EarningsRules}). Accounts without a staff record or without a salary are left out.
     */
    public Map<Integer, java.math.BigDecimal> salaries() {
        Permissions.requireAdmin("read the staff salaries");
        Map<Integer, java.math.BigDecimal> salaries = new LinkedHashMap<>();
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT u.id, e.salary FROM users u JOIN employees e ON e.id = u.employee_id WHERE e.salary IS NOT NULL");
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) salaries.put(rs.getInt(1), com.coffeeshop.coffeeshopmanagement.util.Money.fromDong(rs.getLong(2)));
        } catch (SQLException e) {
            throw new DataAccessException("Failed to read the staff salaries", e);
        }
        return salaries;
    }

    /** Administrator: ends a stretch that was never clocked out, at the time the person really left. One audit line. */
    public void closeForgotten(int entryId, LocalDateTime at) {
        Permissions.requireAdmin("close a forgotten time clock stretch");
        LocalDateTime now = AppClock.now();
        try (Connection connection = DatabaseConfig.getConnection()) {
            connection.setAutoCommit(false);
            try {
                TimeClockEntry entry = null;
                try (PreparedStatement statement = connection.prepareStatement("SELECT " + COLUMNS + " FROM time_clock WHERE id = ?")) {
                    statement.setInt(1, entryId);
                    try (ResultSet rs = statement.executeQuery()) {
                        if (rs.next()) entry = read(rs);
                    }
                }
                if (entry == null || !entry.isOpen()) throw new DataAccessException(I18n.t("time.clock.not.open"));
                String problem = TimeClockRules.correctionProblem(entry.clockIn(), at, now);
                if (problem != null) {
                    throw new DataAccessException("BEFORE".equals(problem) ? I18n.t("time.clock.val.before")
                            : "FUTURE".equals(problem) ? I18n.t("time.clock.val.future")
                            : "LONG".equals(problem) ? I18n.t("time.clock.val.long", TimeClockRules.MAX_STRETCH_HOURS) : I18n.t("time.clock.val.time"));
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE time_clock SET clock_out = ? WHERE id = ? AND clock_out IS NULL")) {
                    statement.setString(1, DbTime.write(at));
                    statement.setInt(2, entryId);
                    if (statement.executeUpdate() != 1) throw new DataAccessException(I18n.t("time.clock.not.open"));
                }
                AuditDAO.write(connection, AuditAction.CLOCK_CORRECTED, AuditTarget.ACCOUNT, entry.userId(), entry.userName(),
                        null, at.toString(), "in " + entry.clockIn());
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to close the time clock stretch", e);
        }
    }

    /** A person who can clock in, for the box that adds a missing stretch. */
    public record Person(int userId, String name) {
        @Override
        public String toString() {
            return name;
        }
    }

    /** Administrator: the stretches that are finished and touch {@code [from, to]}, newest first (what can be corrected). */
    public List<TimeClockEntry> findClosedBetween(LocalDate from, LocalDate to) {
        Permissions.requireAdmin("read the time clock");
        return findBetween(from, to).stream().filter(e -> !e.isOpen()).toList();
    }

    /** Administrator: the active accounts of staff (not customers), by name. */
    public List<Person> people() {
        Permissions.requireAdmin("list the staff accounts");
        List<Person> people = new ArrayList<>();
        String sql = "SELECT u.id, COALESCE(NULLIF(e.full_name, ''), u.username) FROM users u LEFT JOIN employees e ON e.id = u.employee_id "
                + "WHERE u.role <> 'CUSTOMER' AND u.status = " + SqlStatus.ACTIVE
                + " ORDER BY " + DatabaseConfig.dialect().vietnameseOrder("COALESCE(NULLIF(e.full_name, ''), u.username)");
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) people.add(new Person(rs.getInt(1), rs.getString(2)));
        } catch (SQLException e) {
            throw new DataAccessException("Failed to list the staff accounts", e);
        }
        return people;
    }

    /**
     * Administrator: changes the start and/or the end of a FINISHED stretch (TODO 3.67), for a clock-out that was entered late or by
     * mistake. The new times must make a stretch ({@link TimeClockRules#stretchProblem}), must not overlap another stretch of the
     * same person, and a reason is required. One audit line (CLOCK_CORRECTED) with the old and the new times and the reason, in
     * the same transaction. A stretch that is still open is closed with {@link #closeForgotten}.
     */
    public void correct(int entryId, LocalDateTime clockIn, LocalDateTime clockOut, String reason) {
        Permissions.requireAdmin("correct a time clock stretch");
        LocalDateTime now = AppClock.now();
        try (Connection connection = DatabaseConfig.getConnection()) {
            connection.setAutoCommit(false);
            try {
                TimeClockEntry entry = byId(connection, entryId);
                if (entry == null) throw new DataAccessException(I18n.t("time.clock.not.open"));
                if (entry.isOpen()) throw new DataAccessException(I18n.t("time.clock.val.notclosed"));
                if (entry.clockIn().equals(clockIn) && entry.clockOut().equals(clockOut)) throw new DataAccessException(I18n.t("time.clock.val.unchanged"));
                checkWrittenStretch(connection, entry.userId(), entryId, clockIn, clockOut, reason, now);
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE time_clock SET clock_in = ?, clock_out = ? WHERE id = ? AND clock_out IS NOT NULL")) {
                    statement.setString(1, DbTime.write(clockIn));
                    statement.setString(2, DbTime.write(clockOut));
                    statement.setInt(3, entryId);
                    if (statement.executeUpdate() != 1) throw new DataAccessException(I18n.t("time.clock.not.open"));
                }
                AuditDAO.write(connection, AuditAction.CLOCK_CORRECTED, AuditTarget.ACCOUNT, entry.userId(), entry.userName(),
                        TimeClockRules.typed(entry.clockIn()) + " - " + TimeClockRules.typed(entry.clockOut()),
                        TimeClockRules.typed(clockIn) + " - " + TimeClockRules.typed(clockOut), reason.trim());
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to correct the time clock stretch", e);
        }
    }

    /**
     * Administrator: adds a finished stretch that was never clocked (somebody forgot to clock in at all), with the same checks and
     * the same audit line as {@link #correct}. The person must be an active staff account.
     */
    public TimeClockEntry addMissing(int userId, LocalDateTime clockIn, LocalDateTime clockOut, String reason) {
        Permissions.requireAdmin("add a time clock stretch");
        LocalDateTime now = AppClock.now();
        try (Connection connection = DatabaseConfig.getConnection()) {
            connection.setAutoCommit(false);
            try {
                String name = null;
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT COALESCE(NULLIF(e.full_name, ''), u.username) FROM users u LEFT JOIN employees e ON e.id = u.employee_id "
                                + "WHERE u.id = ? AND u.role <> 'CUSTOMER' AND u.status = " + SqlStatus.ACTIVE)) {
                    statement.setInt(1, userId);
                    try (ResultSet rs = statement.executeQuery()) {
                        if (rs.next()) name = rs.getString(1);
                    }
                }
                if (name == null) throw new DataAccessException(I18n.t("time.clock.val.person"));
                checkWrittenStretch(connection, userId, 0, clockIn, clockOut, reason, now);
                int id;
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO time_clock (user_id, user_name, clock_in, clock_out) VALUES (?, ?, ?, ?)", java.sql.Statement.RETURN_GENERATED_KEYS)) {
                    statement.setInt(1, userId);
                    statement.setString(2, name);
                    statement.setString(3, DbTime.write(clockIn));
                    statement.setString(4, DbTime.write(clockOut));
                    statement.executeUpdate();
                    try (ResultSet keys = statement.getGeneratedKeys()) {
                        keys.next();
                        id = keys.getInt(1);
                    }
                }
                AuditDAO.write(connection, AuditAction.CLOCK_CORRECTED, AuditTarget.ACCOUNT, userId, name, null,
                        TimeClockRules.typed(clockIn) + " - " + TimeClockRules.typed(clockOut), reason.trim());
                connection.commit();
                return new TimeClockEntry(id, userId, name, clockIn, clockOut);
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to add the time clock stretch", e);
        }
    }

    /** The checks a stretch written by hand must pass; throws the sentence the screen shows. {@code exceptId} is the stretch being corrected (0: none). */
    private static void checkWrittenStretch(Connection connection, int userId, int exceptId, LocalDateTime clockIn, LocalDateTime clockOut,
                                            String reason, LocalDateTime now) throws SQLException {
        String problem = TimeClockRules.stretchProblem(clockIn, clockOut, now);
        if (problem != null) {
            throw new DataAccessException("BEFORE".equals(problem) ? I18n.t("time.clock.val.before.out")
                    : "FUTURE".equals(problem) ? I18n.t("time.clock.val.future")
                    : "LONG".equals(problem) ? I18n.t("time.clock.val.long", TimeClockRules.MAX_STRETCH_HOURS) : I18n.t("time.clock.val.time"));
        }
        String reasonProblem = TimeClockRules.reasonProblem(reason);
        if (reasonProblem != null) {
            throw new DataAccessException("LONG".equals(reasonProblem) ? I18n.t("time.clock.val.reasonlong", TimeClockRules.MAX_REASON_LENGTH)
                    : I18n.t("time.clock.val.reason"));
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT clock_in, clock_out FROM time_clock WHERE user_id = ? AND id <> ? AND clock_in < ? AND (clock_out IS NULL OR clock_out > ?)")) {
            statement.setInt(1, userId);
            statement.setInt(2, exceptId);
            statement.setString(3, DbTime.write(clockOut));
            statement.setString(4, DbTime.write(clockIn));
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    LocalDateTime otherOut = DbTime.parse(rs.getString(2));
                    throw new DataAccessException(I18n.t("time.clock.val.overlap", TimeClockRules.typed(DbTime.parse(rs.getString(1))),
                            otherOut == null ? "..." : TimeClockRules.typed(otherOut)));
                }
            }
        }
    }

    private static TimeClockEntry byId(Connection connection, int entryId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT " + COLUMNS + " FROM time_clock WHERE id = ?")) {
            statement.setInt(1, entryId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? read(rs) : null;
            }
        }
    }

    private static Optional<TimeClockEntry> open(Connection connection, int userId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT " + COLUMNS + " FROM time_clock WHERE user_id = ? AND clock_out IS NULL ORDER BY id DESC")) {
            statement.setInt(1, userId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(read(rs)) : Optional.empty();
            }
        }
    }

    private static TimeClockEntry read(ResultSet rs) throws SQLException {
        return new TimeClockEntry(rs.getInt(1), rs.getInt(2), rs.getString(3), DbTime.parse(rs.getString(4)), DbTime.parse(rs.getString(5)));
    }

    private static int me() {
        if (Session.getCurrentUser() == null) throw new DataAccessException(I18n.t("admin.only.message"));
        return Session.getCurrentUser().getId();
    }
}
