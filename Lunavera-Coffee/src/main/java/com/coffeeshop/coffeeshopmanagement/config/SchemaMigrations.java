package com.coffeeshop.coffeeshopmanagement.config;

import com.coffeeshop.coffeeshopmanagement.model.AccountStatus;
import com.coffeeshop.coffeeshopmanagement.model.OrderStatus;
import com.coffeeshop.coffeeshopmanagement.model.Role;
import com.coffeeshop.coffeeshopmanagement.model.TableStatus;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Schema changes after version 1 (money as whole dong, see {@link MoneyMigration}), one numbered step each:
 * <ul>
 *   <li><b>version 2</b> (TODO 2.6): indexes on the columns the screens and reports filter and join by. Before this the
 *       only indexes were the automatic ones for PRIMARY KEY and UNIQUE, so every history, report and "orders of this
 *       table / customer" query read the whole orders table.</li>
 * </ul>
 * Plus one rule that is re-checked at every start because it can fail on old data (TODO 2.7): at most one customer per
 * phone number, enforced by a unique index as soon as no duplicates exist.
 * Every statement is {@code IF NOT EXISTS}, so running a step twice, or on a database that already has the index, is harmless.
 */
public final class SchemaMigrations {

    private static final Logger LOGGER = Logger.getLogger(SchemaMigrations.class.getName());

    public static final int LATEST_VERSION = 2;
    static final String UNIQUE_PHONE_INDEX = "ux_customers_phone";

    /**
     * SQLite: version -> the step that brings a database from the previous version to that one (TODO 6.6). Version 1 is
     * {@link MoneyMigration}, so the first entry is 2. To change the tables: raise {@link #LATEST_VERSION}, add the step
     * here under the new number (and, for MariaDB, in {@link MariaDbMigrations#STEPS}), and add a test that starts from an
     * older database like {@code SchemaMigrationsTest.anOldDatabaseGetsTheIndexesOnUpgrade}.
     * {@code SchemaMigrationsTest.everySqliteVersionAfterOneHasAStep} fails if a version is raised without its step.
     */
    static final Map<Integer, MariaDbMigrations.Step> SQLITE_STEPS = new TreeMap<>();

    static {
        SQLITE_STEPS.put(2, SchemaMigrations::createPerformanceIndexes); // TODO 2.6
    }

    private SchemaMigrations() {
    }

    static void upgrade(Connection connection) throws SQLException {
        if (SqlDialect.of(connection) != SqlDialect.SQLITE) {
            ensureMariaEmployeePhoneKey(connection); // MariaDB/MySQL: MariaDbSchema creates everything else; this key came later
            ensureMariaDataRules(connection);
            return;
        }
        int version = userVersion(connection);
        if (version > LATEST_VERSION) {
            // A database made (or restored) by a newer build: older code must not open and change it (TODO 2.42).
            throw new IllegalStateException("Dữ liệu này thuộc về một phiên bản Lunavera mới hơn (v" + version
                    + ", phiên bản này chỉ hiểu đến v" + LATEST_VERSION + "). Hãy cập nhật ứng dụng.");
        }
        // The missing steps in order, the version written after each one, so a failure half way resumes from the right place.
        for (int step : MariaDbMigrations.plan(version, LATEST_VERSION, SQLITE_STEPS.keySet())) {
            LOGGER.info("Upgrading the SQLite database to version " + step + " ...");
            SQLITE_STEPS.get(step).apply(connection);
            DatabaseConfig.setUserVersion(connection, step);
        }
        ensureUniqueCustomerPhone(connection);
        ensureUniqueLoginNames(connection);
        ensureOneOpenOrderPerTable(connection);
        ensureDataRules(connection);
    }

    /**
     * One OPEN order per table as a database rule (TODO 3.22): a partial unique index. The code already refuses a second open
     * order for a table ({@code OrderDAO.rejectIfTableHasOpenOrder}); this makes the database refuse it too when two tills
     * save at the same moment. Like the other unique rules, an index that cannot be built because of existing duplicates is
     * skipped with a warning and tried again at the next start.
     */
    static void ensureOneOpenOrderPerTable(Connection connection) throws SQLException {
        buildUnique(connection, ONE_OPEN_ORDER_INDEX, "orders", "table_number",
                "status = " + SqlStatus.OPEN + " AND table_number IS NOT NULL",
                "SELECT table_number FROM orders WHERE status = " + SqlStatus.OPEN
                        + " AND table_number IS NOT NULL GROUP BY table_number HAVING COUNT(*) > 1");
    }

    static final String ONE_OPEN_ORDER_INDEX = "ux_orders_one_open_per_table";

    /**
     * One rule the database itself enforces (TODO 6.24). {@code violation} is the condition that must never be true for a row,
     * written with {@code {r}.} in front of each column: it becomes {@code NEW.} inside a trigger and nothing when the
     * existing rows are checked. Plain triggers, not {@code CHECK} constraints, because SQLite cannot add a {@code CHECK} to a
     * table that already exists without rebuilding it, and one mechanism then covers a new and an old database alike.
     */
    record DataRule(String name, String table, String violation, String message) {
    }

    /** The values the rest of the code understands, written from the enums so the two cannot drift apart. */
    private static String in(Enum<?>[] values) {
        return Stream.of(values).map(v -> "'" + v.name() + "'").collect(Collectors.joining(", ", "(", ")"));
    }

    static final List<DataRule> DATA_RULES = List.of(
            new DataRule("products_not_negative", "products",
                    "{r}.price < 0 OR {r}.stock < 0 OR ({r}.cost IS NOT NULL AND {r}.cost < 0)",
                    "Giá, giá vốn và tồn kho không được âm."),
            new DataRule("order_items_valid", "order_items",
                    "{r}.quantity <= 0 OR {r}.unit_price < 0 OR {r}.line_total < 0",
                    "Số lượng phải lớn hơn 0 và tiền không được âm."),
            new DataRule("orders_money_valid", "orders",
                    "{r}.subtotal < 0 OR {r}.discount < 0 OR {r}.total < 0 OR {r}.discount > {r}.subtotal",
                    "Tiền của đơn không hợp lệ: không âm, và giảm giá không lớn hơn tạm tính."),
            new DataRule("orders_status_known", "orders",
                    "{r}.status NOT IN " + in(OrderStatus.values()),
                    "Trạng thái đơn không hợp lệ."),
            new DataRule("users_role_status_known", "users",
                    "{r}.role NOT IN " + in(Role.values()) + " OR {r}.status NOT IN " + in(AccountStatus.values()),
                    "Vai trò hoặc trạng thái tài khoản không hợp lệ."),
            new DataRule("tables_status_known", "dining_tables",
                    "{r}.status NOT IN " + in(TableStatus.values()),
                    "Trạng thái bàn không hợp lệ."),
            new DataRule("customers_points_not_negative", "customers",
                    "{r}.loyalty_points < 0",
                    "Điểm tích lũy không được âm."));

    /** How many rows already in the table break {@code rule}. */
    private static long rowsBreaking(Connection connection, DataRule rule) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM " + rule.table() + " WHERE "
                     + rule.violation().replace("{r}.", ""))) {
            return rs.next() ? rs.getLong(1) : 0;
        }
    }

    /** The {@code ALTER TABLE} that gives MariaDB/MySQL the same rule as a {@code CHECK} constraint (TODO 6.24). */
    static String mariaCheckSql(DataRule rule) {
        return "ALTER TABLE " + rule.table() + " ADD CONSTRAINT rule_" + rule.name()
                + " CHECK (NOT (" + rule.violation().replace("{r}.", "") + "))";
    }

    /**
     * MariaDB 10.2+ / MySQL 8.0.16+: the same rules as {@link #DATA_RULES} as {@code CHECK} constraints, added to a database
     * made earlier (TODO 6.24). Safe at every start: a rule that is already there is left alone, and a rule is skipped, with a
     * warning that names only the rule and a row count, while rows already in the table break it. Each rule is tried on its
     * own and a failure (an older server that cannot add a {@code CHECK}, a lost connection) is logged, never thrown: these
     * rules are a second line of defence behind the checks in the code, so they must not stop the app from starting.
     * An older server that accepts the syntax but ignores it simply gets the statement again at the next start.
     */
    static void ensureMariaDataRules(Connection connection) {
        for (DataRule rule : DATA_RULES) {
            try {
                if (mariaConstraintExists(connection, rule.table(), "rule_" + rule.name())) {
                    continue;
                }
                long bad = rowsBreaking(connection, rule);
                if (bad > 0) {
                    LOGGER.warning(bad + " row(s) in " + rule.table() + " break the data rule " + rule.name()
                            + ", so the database cannot enforce it yet. List them with: SELECT * FROM " + rule.table()
                            + " WHERE " + rule.violation().replace("{r}.", ""));
                    continue;
                }
                try (Statement statement = connection.createStatement()) {
                    statement.execute(mariaCheckSql(rule));
                }
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "Data rule " + rule.name() + " was not added: " + e.getMessage());
            }
        }
    }

    private static boolean mariaConstraintExists(Connection connection, String table, String constraint) throws SQLException {
        try (java.sql.PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema = DATABASE() "
                        + "AND table_name = ? AND constraint_name = ? AND constraint_type = 'CHECK'")) {
            statement.setString(1, table);
            statement.setString(2, constraint);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * Creates the triggers for {@link #DATA_RULES} on SQLite. Safe at every start ({@code IF NOT EXISTS}). A rule is skipped,
     * with a warning that names only the rule and the number of rows, when rows already in the database break it (it is tried
     * again at the next start, after the owner has fixed them), and quietly when the table or a column it needs is not there
     * (a database older than the rule).
     */
    static void ensureDataRules(Connection connection) throws SQLException {
        for (DataRule rule : DATA_RULES) {
            long bad;
            try {
                bad = rowsBreaking(connection, rule);
            } catch (SQLException e) {
                LOGGER.log(Level.FINE, "Data rule " + rule.name() + " skipped: the table or a column is not there", e);
                continue;
            }
            if (bad > 0) {
                LOGGER.warning(bad + " row(s) in " + rule.table() + " break the data rule " + rule.name()
                        + ", so the database cannot enforce it yet. List them with: SELECT * FROM " + rule.table()
                        + " WHERE " + rule.violation().replace("{r}.", ""));
                continue;
            }
            String when = rule.violation().replace("{r}.", "NEW.");
            String raise = "BEGIN SELECT RAISE(ABORT, '" + rule.message().replace("'", "''") + "'); END";
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TRIGGER IF NOT EXISTS rule_" + rule.name() + "_insert BEFORE INSERT ON "
                        + rule.table() + " WHEN " + when + " " + raise);
                statement.execute("CREATE TRIGGER IF NOT EXISTS rule_" + rule.name() + "_update BEFORE UPDATE ON "
                        + rule.table() + " WHEN " + when + " " + raise);
            }
        }
    }

    /**
     * MariaDB/MySQL: one staff member per phone as a database rule (TODO 2.17). A new database already has the key; an older
     * one gets it here. Blank phones become NULL first (a plain unique key lets NULL repeat but not an empty text). If two staff
     * already share a phone the key cannot be built: it is skipped with a warning and the next start tries again.
     *
     * @return the phones that block the key (empty when the key exists or was just created)
     */
    static List<String> ensureMariaEmployeePhoneKey(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE employees SET phone = NULL WHERE phone IS NOT NULL AND TRIM(phone) = ''");
        }
        try (java.sql.PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'employees' "
                        + "AND index_name = 'ux_employees_phone'");
             ResultSet rs = statement.executeQuery()) {
            if (rs.next()) {
                return List.of();
            }
        }
        List<String> duplicates = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT phone FROM employees WHERE phone IS NOT NULL GROUP BY phone HAVING COUNT(*) > 1")) {
            while (rs.next()) duplicates.add(rs.getString(1));
        }
        if (!duplicates.isEmpty()) {
            // Count only: phone numbers do not go into the log (TODO 6.28). The query below lists them from the database.
            LOGGER.warning(duplicates.size() + " staff phone number(s) are shared by more than one staff member; the unique-phone rule "
                    + "cannot be enforced by the database until they are fixed. List them with: "
                    + "SELECT phone, COUNT(*) FROM employees GROUP BY phone HAVING COUNT(*) > 1");
            return duplicates;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE employees ADD UNIQUE KEY ux_employees_phone (phone)");
        }
        return List.of();
    }

    static void createPerformanceIndexes(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE INDEX IF NOT EXISTS idx_orders_order_date ON orders(order_date)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_orders_status ON orders(status)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_orders_table_number ON orders(table_number)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_orders_customer_id ON orders(customer_id)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_order_items_order_id ON order_items(order_id)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_products_category_id ON products(category_id)");
        }
    }

    /**
     * Makes "one customer per phone" a database rule. Blank phones are allowed to repeat (a partial index). If two
     * customers already share a phone the index cannot be built; this is logged as a count with the query that lists the phones, and the
     * rule stays code-only until they are merged (feature 4.8), then the next start builds the index.
     *
     * @return the phones that block the index (empty when the index exists or was just created)
     */
    static List<String> ensureUniqueCustomerPhone(Connection connection) throws SQLException {
        List<String> duplicates = duplicatePhones(connection);
        if (!duplicates.isEmpty()) {
            // Count only: phone numbers do not go into the log (TODO 6.28). The query below lists them from the database.
            LOGGER.warning(duplicates.size() + " customer phone number(s) are shared by more than one customer; the unique-phone rule "
                    + "cannot be enforced by the database until they are merged. List them with: "
                    + "SELECT phone, COUNT(*) FROM customers WHERE phone <> '' GROUP BY phone HAVING COUNT(*) > 1");
            return duplicates;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE UNIQUE INDEX IF NOT EXISTS " + UNIQUE_PHONE_INDEX
                    + " ON customers(phone) WHERE phone IS NOT NULL AND phone <> ''");
        }
        return List.of();
    }

    /**
     * One account per username whatever the capitals, and one staff member per phone (TODO 2.17). Like the customer
     * phone rule, an index that cannot be built because of existing duplicates is skipped with a warning.
     */
    static void ensureUniqueLoginNames(Connection connection) throws SQLException {
        buildUnique(connection, "ux_users_username_nocase", "users", "username COLLATE NOCASE", null,
                "SELECT lower(username) FROM users GROUP BY lower(username) HAVING COUNT(*) > 1");
        buildUnique(connection, "ux_employees_phone", "employees", "phone", "phone IS NOT NULL AND phone <> ''",
                "SELECT phone FROM employees WHERE phone IS NOT NULL AND phone <> '' GROUP BY phone HAVING COUNT(*) > 1");
    }

    private static void buildUnique(Connection connection, String index, String table, String columns, String where,
                                    String duplicatesSql) throws SQLException {
        List<String> duplicates = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(duplicatesSql)) {
            while (rs.next()) duplicates.add(rs.getString(1));
        }
        if (!duplicates.isEmpty()) {
            // Count only: the values are phone numbers or login names, which do not go into the log (TODO 6.28).
            LOGGER.warning(duplicates.size() + " duplicate value(s) in " + table + ": the unique rule "
                    + index + " cannot be enforced by the database until they are fixed. Ask the database for them with the "
                    + "GROUP BY ... HAVING COUNT(*) > 1 query on the column the rule covers (see docs/DATABASE.md).");
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE UNIQUE INDEX IF NOT EXISTS " + index + " ON " + table + "(" + columns + ")"
                    + (where != null ? " WHERE " + where : ""));
        }
    }

    static List<String> duplicatePhones(Connection connection) throws SQLException {
        List<String> phones = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT phone FROM customers WHERE phone IS NOT NULL AND phone <> '' "
                     + "GROUP BY phone HAVING COUNT(*) > 1 ORDER BY phone")) {
            while (rs.next()) phones.add(rs.getString(1));
        }
        return phones;
    }

    private static int userVersion(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("PRAGMA user_version")) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
