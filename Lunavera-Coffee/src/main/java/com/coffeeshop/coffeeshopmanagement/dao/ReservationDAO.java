package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig;
import com.coffeeshop.coffeeshopmanagement.model.Reservation;
import com.coffeeshop.coffeeshopmanagement.service.ReservationRules;
import com.coffeeshop.coffeeshopmanagement.service.StockRules;
import com.coffeeshop.coffeeshopmanagement.util.AppClock;
import com.coffeeshop.coffeeshopmanagement.util.DbTime;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.Permissions;
import com.coffeeshop.coffeeshopmanagement.util.Session;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Table reservations (TODO 4.43, rule D11; the rules are in {@link ReservationRules}). Anyone signed in may book, cancel, mark "not
 * come" and seat: it is the cashier's job on a busy evening, and nothing here touches money. A booking is never deleted, only closed
 * (SEATED, CANCELLED or NO_SHOW), so the day's list can still be read afterwards.
 */
public class ReservationDAO {

    private static final String COLUMNS = "id, table_number, customer_name, phone, reserved_for, people, note, status, created_by, created_at, closed_at";

    /** Books a table; refused with a sentence when the booking is not allowed or the table is already booked near that time. */
    public Reservation add(int tableNumber, String name, String phone, LocalDateTime when, int people, String note) {
        requireSignedIn();
        LocalDateTime now = AppClock.now();
        try (Connection connection = DatabaseConfig.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Integer capacity = capacityOf(connection, tableNumber); // locks the table's row on MariaDB: the check below and the insert are one step
                Map<Integer, Integer> others = capacity == null ? Map.of() : otherFreeTables(connection, tableNumber, when);
                String problem = ReservationRules.problem(capacity, name, phone, when, people, note, now, others);
                if (problem != null) throw new DataAccessException(problem);
                // A party bigger than the table: the tables to push together are written into the note, so they are not given away (TODO 3.85)
                List<Integer> extra = people > capacity ? ReservationRules.extraTables(people - capacity, others) : List.of();
                String savedNote = ReservationRules.noteWithMerge(note, extra);
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT reserved_for FROM reservations WHERE table_number = ? AND status = 'BOOKED'")) {
                    statement.setInt(1, tableNumber);
                    try (ResultSet rs = statement.executeQuery()) {
                        while (rs.next()) {
                            if (ReservationRules.tooClose(DbTime.parse(rs.getString(1)), when)) {
                                throw new DataAccessException(I18n.t("reservation.val.taken", ReservationRules.slotMinutes()));
                            }
                        }
                    }
                }
                int id;
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO reservations (table_number, customer_name, phone, reserved_for, people, note, status, created_by, created_at, customer_id, created_by_id) "
                                + "VALUES (?, ?, ?, ?, ?, ?, 'BOOKED', ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                    statement.setInt(1, tableNumber);
                    statement.setString(2, com.coffeeshop.coffeeshopmanagement.util.TextNormalizer.nfc(name)); // TODO 3.18
                    statement.setString(3, phone == null || phone.isBlank() ? null : phone.trim());
                    statement.setString(4, DbTime.write(when));
                    statement.setInt(5, people);
                    statement.setString(6, StockRules.cleanText(savedNote));
                    statement.setString(7, Session.getDisplayName() == null ? "" : Session.getDisplayName());
                    statement.setString(8, DbTime.write(now));
                    Integer known = knownCustomer(connection, phone); // TODO 2.88: so making them anonymous reaches this booking
                    if (known == null) statement.setNull(9, java.sql.Types.INTEGER); else statement.setInt(9, known);
                    if (Session.getCurrentEmployee() == null) statement.setNull(10, java.sql.Types.INTEGER);
                    else statement.setInt(10, Session.getCurrentEmployee().getId()); // for TODO 2.87
                    statement.executeUpdate();
                    try (ResultSet keys = statement.getGeneratedKeys()) {
                        keys.next();
                        id = keys.getInt(1);
                    }
                }
                connection.commit();
                return findById(id).orElseThrow();
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to save the reservation", e);
        }
    }

    /**
     * Closes a waiting booking as {@code status} in one guarded statement; false when it was not waiting any more (another till closed
     * it first), so two tills cannot both seat the same guests.
     */
    public boolean close(int id, Reservation.Status status) {
        requireSignedIn();
        if (status == Reservation.Status.BOOKED) throw new IllegalArgumentException("closing a booking cannot leave it waiting");
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE reservations SET status = ?, closed_at = ? WHERE id = ? AND status = 'BOOKED'")) {
            statement.setString(1, status.name());
            statement.setString(2, DbTime.write(AppClock.now()));
            statement.setInt(3, id);
            return statement.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to change the reservation", e);
        }
    }

    public java.util.Optional<Reservation> findById(int id) {
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT " + COLUMNS + " FROM reservations WHERE id = ?")) {
            statement.setInt(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? java.util.Optional.of(read(rs)) : java.util.Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load the reservation", e);
        }
    }

    /** The bookings still waiting, soonest first (also the late ones: they stay until staff close them). */
    public List<Reservation> findWaiting() {
        List<Reservation> result = new ArrayList<>();
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT " + COLUMNS + " FROM reservations WHERE status = 'BOOKED' ORDER BY reserved_for, id");
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) result.add(read(rs));
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load the reservations", e);
        }
        return result;
    }

    /** The waiting bookings that are more than {@link ReservationRules#STALE_HOURS} hours late: nobody closed them (TODO 2.94). */
    public List<Reservation> findToClose(LocalDateTime now) {
        return findWaiting().stream().filter(r -> ReservationRules.isStale(r, now)).toList();
    }

    /**
     * Marks every forgotten booking (see {@link #findToClose}) as "not come" in one go, with ONE audit line that says how many and who
     * (TODO 2.94, rule D11: nothing is cancelled silently). Administrator only. Returns how many were closed.
     */
    public int closeStale(LocalDateTime now) {
        return closeThese(findToClose(now), now);
    }

    /**
     * Marks the waiting bookings that were for a time before {@code cutoff} (the start of today) as "not come", with the same single
     * audit line (TODO 5.49: the list at the first sign-in). Administrator only. Returns how many were closed.
     */
    public int closeBefore(LocalDateTime cutoff, LocalDateTime now) {
        return closeThese(findWaiting().stream().filter(r -> r.reservedFor().isBefore(cutoff)).toList(), now);
    }

    private int closeThese(List<Reservation> stale, LocalDateTime now) {
        Permissions.requireAdmin("close old bookings");
        try (Connection connection = DatabaseConfig.getConnection()) {
            connection.setAutoCommit(false);
            try {
                int closed = 0;
                StringBuilder who = new StringBuilder();
                for (Reservation r : stale) {
                    try (PreparedStatement statement = connection.prepareStatement(
                            "UPDATE reservations SET status = 'NO_SHOW', closed_at = ? WHERE id = ? AND status = 'BOOKED'")) {
                        statement.setString(1, DbTime.write(now));
                        statement.setInt(2, r.id());
                        if (statement.executeUpdate() == 1) {
                            closed++;
                            if (who.length() > 0) who.append("; ");
                            who.append(r.customerName()).append(' ').append(r.reservedFor().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM HH:mm")));
                        }
                    }
                }
                if (closed > 0) {
                    com.coffeeshop.coffeeshopmanagement.dao.AuditDAO.write(connection, com.coffeeshop.coffeeshopmanagement.model.AuditAction.RESERVATIONS_CLOSED,
                            "reservation", null, closed + " booking(s)", "BOOKED", "NO_SHOW", who.toString());
                }
                connection.commit();
                return closed;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to close the old reservations", e);
        }
    }

    /** The line to show on each table's button, by table number (only tables that have a booking inside the badge window). */
    public Map<Integer, String> badgesByTable() {
        return badgesOf(findWaiting(), AppClock.now());
    }

    /** {@link #badgesByTable} for a list already loaded. */
    public static Map<Integer, String> badgesOf(List<Reservation> waiting, LocalDateTime now) {
        Map<Integer, List<Reservation>> byTable = new HashMap<>();
        for (Reservation r : waiting) byTable.computeIfAbsent(r.tableNumber(), k -> new ArrayList<>()).add(r);
        Map<Integer, String> badges = new HashMap<>();
        byTable.forEach((table, list) -> ReservationRules.forBadge(list, now).ifPresent(r -> badges.put(table, ReservationRules.badge(r, now))));
        return badges;
    }

    /** The live (not anonymous) customer with this phone, or null: a booking made with that phone is theirs (TODO 2.88). */
    static Integer knownCustomer(Connection connection, String phone) throws SQLException {
        if (phone == null || phone.isBlank()) return null;
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id FROM customers WHERE phone = ? AND anonymised_at IS NULL ORDER BY id LIMIT 1")) {
            statement.setString(1, com.coffeeshop.coffeeshopmanagement.util.ValidationUtil.normalizePhone(phone));
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getInt(1) : null;
            }
        }
    }

    /**
     * Takes a person's name and phone off every booking that is theirs (TODO 2.88), inside the caller's transaction: the ones that carry
     * the customer's id, and the older ones with no id whose phone is the customer's phone (written in any way: +84 or 0, spaces).
     * The name becomes {@code newName} and the phone is removed; the booking stays (its table, time, people and status are no personal
     * data) and gets the customer's id. The free-text note is left: it is the shop's note about the booking, not a field for a name.
     *
     * @param oldPhone the customer's phone before it was cleared, or null
     * @return how many bookings were changed
     */
    public static int anonymiseFor(Connection connection, int customerId, String oldPhone, String newName) throws SQLException {
        List<Integer> ids = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("SELECT id FROM reservations WHERE customer_id = ?")) {
            statement.setInt(1, customerId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) ids.add(rs.getInt(1));
            }
        }
        String wanted = com.coffeeshop.coffeeshopmanagement.util.ValidationUtil.normalizePhone(oldPhone);
        if (wanted != null && !wanted.isBlank()) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT id, phone FROM reservations WHERE customer_id IS NULL AND phone IS NOT NULL");
                 ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    if (wanted.equals(com.coffeeshop.coffeeshopmanagement.util.ValidationUtil.normalizePhone(rs.getString(2)))) ids.add(rs.getInt(1));
                }
            }
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE reservations SET customer_name = ?, phone = NULL, customer_id = ? WHERE id = ?")) {
            for (int id : ids) {
                statement.setString(1, newName);
                statement.setInt(2, customerId);
                statement.setInt(3, id);
                statement.addBatch();
            }
            if (!ids.isEmpty()) statement.executeBatch();
        }
        return ids.size();
    }

    /**
     * The table's seats, read with the table's row <b>locked</b> on MariaDB (TODO 3.81): the booking then reads the table's other
     * bookings and inserts its own while a second till that books the same table waits at this line, and finds the first booking
     * when it gets the lock. Without it both tills pass the "too close" check under MariaDB's default isolation. The lock is held
     * until the caller commits or rolls back. SQLite has one writer at a time and no {@code FOR UPDATE}.
     */
    /**
     * What is wrong with a booking, as the booking window shows it before saving (TODO 3.85): the same rules as {@link #add}, with the
     * other tables that are free at that time taken from the database, so a party bigger than the table is judged on the seats of the
     * whole shop and not on one table. Nothing is written or locked here; {@link #add} checks again inside its transaction.
     */
    public String problemFor(int tableNumber, String name, String phone, LocalDateTime when, int people, String note) {
        try (Connection connection = DatabaseConfig.getConnection()) {
            Integer capacity;
            try (PreparedStatement statement = connection.prepareStatement("SELECT capacity FROM dining_tables WHERE table_number = ?")) {
                statement.setInt(1, tableNumber);
                try (ResultSet rs = statement.executeQuery()) {
                    capacity = rs.next() ? rs.getInt(1) : null;
                }
            }
            Map<Integer, Integer> others = capacity == null || when == null ? Map.of() : otherFreeTables(connection, tableNumber, when);
            return ReservationRules.problem(capacity, name, phone, when, people, note, AppClock.now(), others);
        } catch (SQLException e) {
            throw new DataAccessException("Failed to check the reservation", e);
        }
    }

    /**
     * The other tables (number -> seats) that have no waiting booking within a slot of {@code when}: the ones that can be pushed
     * together with the booked table for a bigger party. Whether a table is taken by an open order at that time cannot be known for a
     * future booking, so only bookings count (TODO 3.85).
     */
    private static Map<Integer, Integer> otherFreeTables(Connection connection, int tableNumber, LocalDateTime when) throws SQLException {
        Map<Integer, Integer> free = new java.util.LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT table_number, capacity FROM dining_tables WHERE table_number <> ? ORDER BY table_number")) {
            statement.setInt(1, tableNumber);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) free.put(rs.getInt(1), rs.getInt(2));
            }
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT table_number, reserved_for FROM reservations WHERE status = 'BOOKED' AND table_number <> ?")) {
            statement.setInt(1, tableNumber);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    if (ReservationRules.tooClose(DbTime.parse(rs.getString(2)), when)) free.remove(rs.getInt(1));
                }
            }
        }
        return free;
    }

    private static Integer capacityOf(Connection connection, int tableNumber) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(capacitySql(DatabaseConfig.isSqlite()))) {
            statement.setInt(1, tableNumber);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getInt(1) : null;
            }
        }
    }

    /** The statement that reads a table's seats; it locks the row unless the database is SQLite. */
    static String capacitySql(boolean sqlite) {
        return "SELECT capacity FROM dining_tables WHERE table_number = ?" + (sqlite ? "" : " FOR UPDATE");
    }

    private static void requireSignedIn() {
        if (Session.getCurrentUser() == null) {
            Permissions.requireAdmin("handle a table reservation"); // nobody signed in: refused and logged like any other action
        }
    }

    private static Reservation read(ResultSet rs) throws SQLException {
        String closed = rs.getString("closed_at");
        return new Reservation(rs.getInt("id"), rs.getInt("table_number"), rs.getString("customer_name"), rs.getString("phone"),
                DbTime.parse(rs.getString("reserved_for")), rs.getInt("people"), rs.getString("note"),
                Reservation.Status.valueOf(rs.getString("status")), rs.getString("created_by"), DbTime.parse(rs.getString("created_at")),
                closed == null ? null : DbTime.parse(closed));
    }
}
