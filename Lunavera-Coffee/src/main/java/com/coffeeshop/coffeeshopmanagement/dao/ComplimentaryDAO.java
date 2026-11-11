package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig;
import com.coffeeshop.coffeeshopmanagement.config.SqlStatus;
import com.coffeeshop.coffeeshopmanagement.model.AuditAction;
import com.coffeeshop.coffeeshopmanagement.model.AuditTarget;
import com.coffeeshop.coffeeshopmanagement.model.PaymentMethod;
import com.coffeeshop.coffeeshopmanagement.model.Role;
import com.coffeeshop.coffeeshopmanagement.service.AuditRules;
import com.coffeeshop.coffeeshopmanagement.service.ComplimentaryRules;
import com.coffeeshop.coffeeshopmanagement.util.AppClock;
import com.coffeeshop.coffeeshopmanagement.util.BusinessDay;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.Money;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Free drinks for the staff, the owner and tastings (TODO 4.61, rule D26; the rules are in {@link ComplimentaryRules}).
 *
 * <p><b>Writing.</b> {@link #record} is the step of the payment's own transaction that turns a bill that was just saved at no charge into a
 * free bill: it checks the person's limit for the day AGAIN, against the database and inside the transaction (the check on the screen is only
 * a courtesy), stores the kind, the person, the menu price and the note on the order, and writes the audit line. Any problem rolls the whole
 * payment back (nothing is saved, the stock is not taken). A refunded or cancelled free bill is not paid any more, so it does not count
 * against the limit.
 *
 * <p><b>Reading.</b> {@link #totals} and {@link #byPerson} read the sales ledger ({@code SalesLedger}) like every other figure of the report,
 * so a free bill counts on the day it was given and a cancelled one nets out. The cost of a drink is the cost recorded on its line when it
 * was given ({@code order_items.unit_cost}); cups with no known cost are counted apart so the figure never claims more than it knows.
 */
public final class ComplimentaryDAO {

    /** What a period gave away: bills, cups, the cost of those cups, what they would have cost at the menu price, and the cups whose cost is not known. */
    public record Totals(int bills, int cups, BigDecimal cost, BigDecimal value, int cupsWithoutCost) {
        public static final Totals NONE = new Totals(0, 0, BigDecimal.ZERO, BigDecimal.ZERO, 0);

        public boolean isNone() {
            return bills == 0 && cups == 0 && cost.signum() == 0 && value.signum() == 0;
        }
    }

    /** One kind and person of the period: {@code employeeId} and {@code name} are null for the owner and for tastings. */
    public record PersonLine(ComplimentaryRules.Kind kind, Integer employeeId, String name, int bills, int cups, BigDecimal cost, BigDecimal value) {
    }

    public ComplimentaryDAO() {
    }

    // ------------------------------------------------------------------ writing

    /**
     * Makes the bill {@code orderId}, just saved paid at no charge on {@code connection}, a free bill. Call it inside the payment's
     * transaction, after the order and its lines are written and before the commit; the caller commits.
     *
     * @param role      who is signed in (an administrator has no daily limit)
     * @param cups      the cups on the bill
     * @param listValue what the lines would have cost at the menu price
     * @throws DataAccessException when the request is not allowed (the person is not an active member of staff, or the limit is used up);
     *                             the transaction is rolled back first
     */
    public static void record(Connection connection, int orderId, ComplimentaryRules.Give give, Role role, int cups, BigDecimal listValue)
            throws SQLException {
        String person = null;
        if (give != null && give.kind() == ComplimentaryRules.Kind.STAFF && give.forEmployeeId() != null) {
            person = activeStaffName(connection, give.forEmployeeId());
            if (person == null) refuse(connection, I18n.t("complimentary.problem.persongone"));
        }
        final int limit = ComplimentaryRules.cashierCupsPerDay();
        final int given = give == null || give.kind() == null ? 0 : cupsGivenToday(connection, give, orderId);
        ComplimentaryRules.Problem problem = ComplimentaryRules.check(role, give, cups, given, limit);
        if (problem != ComplimentaryRules.Problem.OK) {
            refuse(connection, ComplimentaryRules.message(problem, person != null ? person : I18n.t("complimentary.who.owner"), given, cups, limit));
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE orders SET complimentary_kind = ?, complimentary_for = ?, complimentary_value = ?, complimentary_note = ? "
                        + "WHERE id = ? AND payment_method = '" + PaymentMethod.COMPLIMENTARY.name() + "' AND status = " + SqlStatus.PAID)) {
            statement.setString(1, give.kind().name());
            if (give.forEmployeeId() == null) statement.setNull(2, java.sql.Types.INTEGER); else statement.setInt(2, give.forEmployeeId());
            statement.setLong(3, Money.toDong(listValue == null ? BigDecimal.ZERO : listValue));
            statement.setString(4, give.note());
            statement.setInt(5, orderId);
            if (statement.executeUpdate() != 1) {
                refuse(connection, I18n.t("order.notopen", orderId));
            }
        }
        // Who got it: a drink for a member of staff is filed under that person (so making them anonymous scrubs their name here too, TODO 4.29);
        // the owner's and a tasting are filed under the order. The detail is "kind|order|cups|note", read back by AuditText.
        String detail = give.kind().name() + "|" + orderId + "|" + cups + "|" + (give.note() == null ? "" : give.note());
        String value = AuditRules.money(listValue == null ? BigDecimal.ZERO : listValue);
        if (person != null) {
            AuditDAO.write(connection, AuditAction.COMPLIMENTARY_GIVEN, AuditTarget.STAFF, give.forEmployeeId(), person, null, value, detail);
        } else {
            AuditDAO.write(connection, AuditAction.COMPLIMENTARY_GIVEN, AuditTarget.ORDER, orderId, "#" + orderId, null, value, detail);
        }
    }

    /** Rolls the payment back and refuses with {@code message} (every screen already shows a {@link DataAccessException}'s message). */
    private static void refuse(Connection connection, String message) throws SQLException {
        connection.rollback();
        throw new DataAccessException(message);
    }

    /** The name of an active, not anonymised member of staff, or null when there is none with this id. */
    private static String activeStaffName(Connection connection, int employeeId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT full_name, active, anonymised_at FROM employees WHERE id = ?")) {
            statement.setInt(1, employeeId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) return null;
                boolean active = rs.getInt("active") != 0;
                rs.getString("anonymised_at");
                boolean anonymised = !rs.wasNull();
                return active && !anonymised ? rs.getString("full_name") : null;
            }
        }
    }

    /**
     * The free cups already given today (the shop's day, TODO 4.50) to the same person as {@code give}: that member of staff, or the owner,
     * not counting bill {@code exceptOrderId} (the one being saved). Only paid bills count: a cancelled one gives the cups back. On MariaDB
     * the rows are locked until the transaction ends, so two tills cannot both take the last cup.
     */
    static int cupsGivenToday(Connection connection, ComplimentaryRules.Give give, int exceptOrderId) throws SQLException {
        boolean staff = give.kind() == ComplimentaryRules.Kind.STAFF;
        if (give.kind() == ComplimentaryRules.Kind.TASTING) return 0; // a tasting has no person to count against (only an administrator gives one)
        LocalDate today = BusinessDay.dayOf(AppClock.now());
        String sql = "SELECT COALESCE(SUM(oi.quantity), 0) FROM orders o JOIN order_items oi ON oi.order_id = o.id WHERE o.payment_method = '"
                + PaymentMethod.COMPLIMENTARY.name() + "' AND o.status = " + SqlStatus.PAID + " AND o.id <> ? AND o.complimentary_kind = ?"
                + (staff ? " AND o.complimentary_for = ?" : "") + " AND " + EffectiveDay.between("o.")
                + (DatabaseConfig.isSqlite() ? "" : " FOR UPDATE");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            statement.setInt(index++, exceptOrderId);
            statement.setString(index++, give.kind().name());
            if (staff) statement.setInt(index++, give.forEmployeeId());
            EffectiveDay.bind(statement, index, today, today);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    /** The free cups already given today to a member of staff ({@code employeeId}) or to the owner (null), for the window's hint. Not locked. */
    public int cupsGivenToday(ComplimentaryRules.Kind kind, Integer employeeId) {
        try (Connection connection = DatabaseConfig.getConnection()) {
            return cupsGivenToday(connection, new ComplimentaryRules.Give(kind, employeeId, null), 0);
        } catch (SQLException e) {
            throw new DataAccessException("Failed to count today's free drinks", e);
        }
    }

    // ------------------------------------------------------------------ reading

    /** The ledger rows of free bills (a sale or a whole refund of one; never a refunded line, which a free bill cannot have). */
    private static final String FREE = "o.payment_method = '" + PaymentMethod.COMPLIMENTARY.name() + "' AND o." + SalesLedger.PART + " = 0";

    /** The bills and their menu value of a period, from the ledger (eight {@code ?}, {@link SalesLedger#bindDays}). */
    static String billsSql() {
        return "SELECT COALESCE(SUM(o." + SalesLedger.COUNT + "), 0), COALESCE(SUM(COALESCE(o.complimentary_value, 0) * o." + SalesLedger.COUNT + "), 0) FROM "
                + SalesLedger.forDays() + " WHERE " + FREE;
    }

    /** The cups, their cost and the cups with no known cost of a period (eight {@code ?}). */
    static String cupsSql() {
        return "SELECT COALESCE(SUM(oi.quantity * o." + SalesLedger.SIGN + "), 0), COALESCE(SUM(oi.quantity * COALESCE(oi.unit_cost, 0) * o." + SalesLedger.SIGN + "), 0), "
                + "COALESCE(SUM(CASE WHEN oi.unit_cost IS NULL THEN oi.quantity * o." + SalesLedger.SIGN + " ELSE 0 END), 0) FROM "
                + SalesLedger.forDays() + " JOIN order_items oi ON oi.order_id = o.id WHERE " + FREE;
    }

    /** What the days {@code from} to {@code to} (both included) gave away; {@link Totals#NONE} when nothing. */
    public Totals totals(LocalDate from, LocalDate to) {
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement billStatement = connection.prepareStatement(billsSql());
             PreparedStatement cupStatement = connection.prepareStatement(cupsSql())) {
            SalesLedger.bindDays(billStatement, 1, from, to);
            SalesLedger.bindDays(cupStatement, 1, from, to);
            int billCount;
            long value;
            try (ResultSet rs = billStatement.executeQuery()) {
                rs.next();
                billCount = rs.getInt(1);
                value = rs.getLong(2);
            }
            try (ResultSet rs = cupStatement.executeQuery()) {
                rs.next();
                return new Totals(billCount, rs.getInt(1), Money.fromDong(rs.getLong(2)), Money.fromDong(value), rs.getInt(3));
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load the free drinks", e);
        }
    }

    private static final String KEY = "o.complimentary_kind, o.complimentary_for";

    /** Bills and menu value per kind and person (eight {@code ?}). */
    static String peopleBillsSql() {
        return "SELECT o.complimentary_kind, o.complimentary_for, MAX(e.full_name), COALESCE(SUM(o." + SalesLedger.COUNT + "), 0), "
                + "COALESCE(SUM(COALESCE(o.complimentary_value, 0) * o." + SalesLedger.COUNT + "), 0) FROM " + SalesLedger.forDays()
                + " LEFT JOIN employees e ON e.id = o.complimentary_for WHERE " + FREE + " GROUP BY " + KEY;
    }

    /** Cups and cost per kind and person (eight {@code ?}). */
    static String peopleCupsSql() {
        return "SELECT o.complimentary_kind, o.complimentary_for, COALESCE(SUM(oi.quantity * o." + SalesLedger.SIGN + "), 0), "
                + "COALESCE(SUM(oi.quantity * COALESCE(oi.unit_cost, 0) * o." + SalesLedger.SIGN + "), 0) FROM " + SalesLedger.forDays()
                + " JOIN order_items oi ON oi.order_id = o.id WHERE " + FREE + " GROUP BY " + KEY;
    }

    /** The same period per kind and person, the biggest cost first, then by name; lines with nothing left (a bill and its cancelling) are left out. */
    public List<PersonLine> byPerson(LocalDate from, LocalDate to) {
        Map<String, long[]> numbers = new LinkedHashMap<>(); // bills, value, cups, cost
        Map<String, Object[]> who = new LinkedHashMap<>();   // kind, employee id, name
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement billStatement = connection.prepareStatement(peopleBillsSql());
             PreparedStatement cupStatement = connection.prepareStatement(peopleCupsSql())) {
            SalesLedger.bindDays(billStatement, 1, from, to);
            SalesLedger.bindDays(cupStatement, 1, from, to);
            try (ResultSet rs = billStatement.executeQuery()) {
                while (rs.next()) {
                    String kind = rs.getString(1);
                    int person = rs.getInt(2);
                    Integer employeeId = rs.wasNull() ? null : person;
                    String k = kind + "|" + employeeId;
                    who.put(k, new Object[]{kind, employeeId, rs.getString(3)});
                    numbers.computeIfAbsent(k, x -> new long[4]);
                    numbers.get(k)[0] = rs.getLong(4);
                    numbers.get(k)[1] = rs.getLong(5);
                }
            }
            try (ResultSet rs = cupStatement.executeQuery()) {
                while (rs.next()) {
                    String kind = rs.getString(1);
                    int person = rs.getInt(2);
                    Integer employeeId = rs.wasNull() ? null : person;
                    String k = kind + "|" + employeeId;
                    who.putIfAbsent(k, new Object[]{kind, employeeId, null});
                    numbers.computeIfAbsent(k, x -> new long[4]);
                    numbers.get(k)[2] = rs.getLong(3);
                    numbers.get(k)[3] = rs.getLong(4);
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load the free drinks per person", e);
        }
        List<PersonLine> result = new ArrayList<>();
        for (Map.Entry<String, long[]> entry : numbers.entrySet()) {
            long[] n = entry.getValue();
            Object[] w = who.get(entry.getKey());
            ComplimentaryRules.Kind kind = ComplimentaryRules.Kind.fromDb((String) w[0]);
            if (kind == null || (n[0] == 0 && n[2] == 0 && n[1] == 0 && n[3] == 0)) continue;
            result.add(new PersonLine(kind, (Integer) w[1], (String) w[2], (int) n[0], (int) n[2], Money.fromDong(n[3]), Money.fromDong(n[1])));
        }
        result.sort((a, b) -> {
            int byCost = b.cost().compareTo(a.cost());
            return byCost != 0 ? byCost : Objects.toString(a.name(), "").compareToIgnoreCase(Objects.toString(b.name(), ""));
        });
        return result;
    }
}
