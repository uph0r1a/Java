package com.coffeeshop.coffeeshopmanagement.config;

import com.coffeeshop.coffeeshopmanagement.model.AccountStatus;
import com.coffeeshop.coffeeshopmanagement.model.OrderStatus;
import com.coffeeshop.coffeeshopmanagement.model.Reservation;
import com.coffeeshop.coffeeshopmanagement.model.Role;
import com.coffeeshop.coffeeshopmanagement.model.TableStatus;
import com.coffeeshop.coffeeshopmanagement.model.Voucher;
import com.coffeeshop.coffeeshopmanagement.util.I18n;

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
 *   <li><b>version 3</b> (TODO 3.36): an index on {@code orders.paid_at}. The date filters (history, reports, dashboard) are
 *       ranges on {@code paid_at} and {@code order_date} now, and the paid time had no index on SQLite. MariaDB's tables are
 *       created with it, so its step only covers a database made before that.</li>
 *   <li><b>version 4</b> (TODO 4.37 / 6.90): indexes on {@code refunds(order_id)} and {@code refunds(refunded_at)}. The refund
 *       reports and the "lines already refunded" look-up of one order read the refunds table by those two columns; MariaDB's table is
 *       created with both ({@code idx_refunds_order}, {@code idx_refunds_at}), SQLite's had none.</li>
 *   <li><b>version 5</b> (TODO 6.72): the rest of the plain indexes MariaDB's tables are made with and SQLite had none of, on {@code stock_log},
 *       {@code expenses}, {@code reservations}, {@code audit_log}, {@code shift_closes}, {@code gift_card_moves}, {@code time_clock} and
 *       {@code orders (status, order_date)}, and on SQLite {@code order_items (product_id)}; see
 *       {@link #INDEXES_SINCE_V5}. {@code MariaDbSchemaTest} now compares the index columns of the two, not only the table columns.</li>
 * </ul>
 * Plus one rule that is re-checked at every start because it can fail on old data (TODO 2.7): at most one customer per
 * phone number, enforced by a unique index as soon as no duplicates exist.
 * Every statement is {@code IF NOT EXISTS}, so running a step twice, or on a database that already has the index, is harmless.
 */
public final class SchemaMigrations {

    private static final Logger LOGGER = Logger.getLogger(SchemaMigrations.class.getName());

    public static final int LATEST_VERSION = 5;
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
        SQLITE_STEPS.put(3, SchemaMigrations::createPaidAtIndex);        // TODO 3.36
        SQLITE_STEPS.put(4, SchemaMigrations::createRefundIndexes);      // TODO 4.37 (6.90 a)
        SQLITE_STEPS.put(5, SchemaMigrations::createMirroredIndexes);    // TODO 6.72
    }

    private SchemaMigrations() {
    }

    /**
     * A numbered step that adds columns to tables that already exist (TODO 6.6). Use it, registered under the new version in
     * {@link #SQLITE_STEPS} and in {@link MariaDbMigrations#STEPS}, instead of adding a line to {@link SqliteSchema#LATER_COLUMNS},
     * which is frozen (see {@code LaterColumnsAreFrozenTest}). The column must ALSO be in the CREATE TABLE of both schema classes,
     * because a new MariaDB database is made at the latest version and runs no step. Safe to run twice: a column that is there is
     * left alone, so a new SQLite file (made from the older tables, then stepped up) and an old one end in the same place.
     * On SQLite it runs inside the step's transaction together with the version number.
     */
    static MariaDbMigrations.Step addColumns(String[]... columns) {
        return connection -> {
            for (String[] column : columns) {
                DatabaseConfig.addColumnIfMissing(connection, column[0], column[1], column[2]);
            }
        };
    }

    static void upgrade(Connection connection) throws SQLException {
        if (SqlDialect.of(connection) != SqlDialect.SQLITE) {
            ensureMariaEmployeePhoneKey(connection); // MariaDB/MySQL: MariaDbSchema creates everything else; this key came later
            ensureMariaLoginNameKey(connection);      // so did this one (TODO 1.9)
            ensureMariaProductCodeKey(connection);    // and the product code (TODO 4.42)
            ensureMariaOneAtATimeKeys(connection);    // and the three "one at a time" rules (TODO 2.98)
            ensureMariaDataRules(connection);
            return;
        }
        int version = userVersion(connection);
        if (version > LATEST_VERSION) {
            // A database made (or restored) by a newer build: older code must not open and change it (TODO 2.42).
            throw new IllegalStateException(I18n.t("db.newer.sqlite", version, LATEST_VERSION));
        }
        List<Integer> missing = MariaDbMigrations.plan(version, LATEST_VERSION, SQLITE_STEPS.keySet());
        if (!missing.isEmpty()) {
            PreUpgradeBackup.takeIfNeeded(connection, version, LATEST_VERSION); // TODO 6.9: a way back before any step changes the data
        }
        // The missing steps in order, the version written after each one, so a failure half way resumes from the right place.
        for (int step : missing) {
            LOGGER.info("Upgrading the SQLite database to version " + step + " ...");
            // The step and the version number together (TODO 3.37): killed in between, the file is at the old version with none of
            // the step applied, not at the old version with half of it.
            int target = step;
            DatabaseConfig.runAtomically(connection, () -> {
                SQLITE_STEPS.get(target).apply(connection);
                DatabaseConfig.setUserVersion(connection, target);
            });
        }
        ensureUniqueCustomerPhone(connection);
        ensureUniqueLoginNames(connection);
        ensureUniqueProductCode(connection);
        ensureOneOpenOrderPerTable(connection);
        ensureOneOpenClockIn(connection);       // TODO 2.98
        ensureUniqueQueueNumber(connection);    // TODO 2.98
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

    // ---- the "one at a time" rules (TODO 2.98) ----

    static final String ONE_OPEN_CLOCK_INDEX = "ux_time_clock_one_open";
    static final String QUEUE_NUMBER_INDEX = "ux_orders_queue_day_no";

    /** SQLite: at most one open clock-in per person, a partial unique index (TODO 2.98; built like the other rules, skipped while duplicates exist). */
    static void ensureOneOpenClockIn(Connection connection) throws SQLException {
        buildUnique(connection, ONE_OPEN_CLOCK_INDEX, "time_clock", "user_id", "clock_out IS NULL",
                "SELECT user_id FROM time_clock WHERE clock_out IS NULL GROUP BY user_id HAVING COUNT(*) > 1");
    }

    /**
     * Gives the take-away orders of before their shop day (the calendar day of the time they were opened: the day start of
     * TODO 4.50 did not exist then). Only rows that have a number and no day yet; the same statement on both databases.
     */
    static void backfillQueueDay(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE orders SET queue_day = DATE(order_date) WHERE queue_no IS NOT NULL AND queue_day IS NULL");
        }
    }

    private static final String DUPLICATE_QUEUE_NUMBERS = "SELECT queue_day FROM orders WHERE queue_no IS NOT NULL AND queue_day IS NOT NULL "
            + "GROUP BY queue_day, queue_no HAVING COUNT(*) > 1";

    /** SQLite: one take-away number once per shop day, a unique index on (day, number) (TODO 2.98). */
    static void ensureUniqueQueueNumber(Connection connection) throws SQLException {
        backfillQueueDay(connection);
        buildUnique(connection, QUEUE_NUMBER_INDEX, "orders", "queue_day, queue_no", "queue_no IS NOT NULL AND queue_day IS NOT NULL",
                DUPLICATE_QUEUE_NUMBERS);
    }

    /**
     * MariaDB/MySQL (TODO 2.98): the three rules SQLite has as partial unique indexes, which these servers do not have. Each is a
     * stored generated column that holds the value only while the rule applies and is NULL otherwise, with a unique key on it
     * (a unique key lets any number of NULLs repeat): {@code orders.open_table_key} (the table number while the order is OPEN),
     * {@code time_clock.open_user_key} (the person while the stretch is open), and for the take-away number the plain column
     * {@code orders.queue_day} with a unique key on (day, number). A new database is created with them ({@link MariaDbSchema});
     * an older one gets them here at the start. Like the other unique rules, a key that cannot be built because duplicates
     * already exist is skipped with a warning and tried again at the next start. Needs MariaDB 10.2.1+ or MySQL 5.7+ (stored
     * generated columns).
     */
    static void ensureMariaOneAtATimeKeys(Connection connection) throws SQLException {
        backfillQueueDay(connection);
        buildMariaKey(connection, "orders", "open_table_key", ONE_OPEN_ORDER_INDEX,
                "INT GENERATED ALWAYS AS (CASE WHEN status = " + SqlStatus.OPEN + " THEN table_number END) STORED",
                "(open_table_key)",
                "SELECT table_number FROM orders WHERE status = " + SqlStatus.OPEN
                        + " AND table_number IS NOT NULL GROUP BY table_number HAVING COUNT(*) > 1");
        buildMariaKey(connection, "time_clock", "open_user_key", ONE_OPEN_CLOCK_INDEX,
                "INT GENERATED ALWAYS AS (CASE WHEN clock_out IS NULL THEN user_id END) STORED",
                "(open_user_key)",
                "SELECT user_id FROM time_clock WHERE clock_out IS NULL GROUP BY user_id HAVING COUNT(*) > 1");
        buildMariaKey(connection, "orders", "queue_day", QUEUE_NUMBER_INDEX, null, "(queue_day, queue_no)", DUPLICATE_QUEUE_NUMBERS);
    }

    /** Adds {@code column} (when a definition is given and it is missing) and the unique key {@code index}, unless it exists or duplicates block it. */
    private static void buildMariaKey(Connection connection, String table, String column, String index, String columnDefinition,
                                      String keyColumns, String duplicatesSql) throws SQLException {
        if (mariaIndexExists(connection, table, index)) {
            return;
        }
        List<String> duplicates = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(duplicatesSql)) {
            while (rs.next()) duplicates.add(rs.getString(1));
        }
        if (!duplicates.isEmpty()) {
            LOGGER.warning(duplicates.size() + " duplicate value(s) in " + table + ": the unique rule " + index
                    + " cannot be enforced by the database until they are fixed. Ask the database for them with the GROUP BY ... HAVING "
                    + "COUNT(*) > 1 query on the column the rule covers (see docs/DATABASE.md).");
            return;
        }
        try (Statement statement = connection.createStatement()) {
            boolean addColumn = columnDefinition != null && !mariaColumnExists(connection, table, column);
            statement.execute("ALTER TABLE " + table + (addColumn ? " ADD COLUMN " + column + " " + columnDefinition + "," : "")
                    + " ADD UNIQUE KEY " + index + " " + keyColumns);
        } catch (SQLException e) {
            // A server that cannot do it (too old, no stored generated columns): the code still checks the rule, as before.
            LOGGER.log(Level.WARNING, "The unique rule " + index + " was not added: " + e.getMessage());
        }
    }

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
                    "Điểm tích lũy không được âm."),
            // The tables added after the rules above (TODO 6.87). The code checks each of these today; they are the second line of
            // defence for money when a second till or a hand edit in phpMyAdmin goes around the code. Each is the weakest form the
            // code already keeps, so no legitimate row is refused.
            new DataRule("gift_cards_valid", "gift_cards",
                    "{r}.initial_amount <= 0 OR {r}.balance < 0",
                    "Thẻ quà tặng: số tiền ban đầu phải lớn hơn 0 và số dư không được âm."),
            new DataRule("gift_card_moves_not_zero", "gift_card_moves",
                    "{r}.amount = 0",
                    "Một lần dùng hoặc hoàn tiền thẻ quà tặng không thể là 0 đồng."),
            new DataRule("time_clock_in_before_out", "time_clock",
                    "{r}.clock_out IS NOT NULL AND {r}.clock_out < {r}.clock_in",
                    "Giờ ra ca không được trước giờ vào ca."),
            new DataRule("vouchers_valid", "vouchers",
                    "{r}.value <= 0 OR {r}.min_subtotal < 0 OR {r}.used_count < 0"
                            + " OR {r}.kind NOT IN ('" + Voucher.PERCENT + "', '" + Voucher.AMOUNT + "')"
                            + " OR ({r}.kind = '" + Voucher.PERCENT + "' AND {r}.value > 100)"
                            + " OR ({r}.max_uses IS NOT NULL AND ({r}.max_uses <= 0 OR {r}.used_count > {r}.max_uses))",
                    "Mã giảm giá không hợp lệ: giá trị phải lớn hơn 0 (phần trăm tối đa 100) và số lượt dùng không vượt quá giới hạn."),
            new DataRule("expenses_amount_positive", "expenses",
                    "{r}.amount <= 0",
                    "Số tiền chi phải lớn hơn 0."),
            // A refund row may carry 0 pieces or 0 dong (the rest of a line paid partly by gift card, a line the discount made free),
            // so this rule only refuses what is negative.
            new DataRule("refunds_not_negative", "refunds",
                    "{r}.quantity < 0 OR {r}.line_amount < 0 OR {r}.amount < 0",
                    "Số lượng và số tiền hoàn không được âm."),
            new DataRule("order_payments_positive", "order_payments",
                    "{r}.amount <= 0",
                    "Số tiền của một cách thanh toán phải lớn hơn 0."),
            new DataRule("reservations_valid", "reservations",
                    "{r}.people <= 0 OR {r}.status NOT IN " + in(Reservation.Status.values()),
                    "Đặt bàn không hợp lệ: số người phải lớn hơn 0 và trạng thái phải được biết."));

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

    static final String MARIA_LOGIN_KEY_COLUMN = "username_key";
    static final String MARIA_LOGIN_KEY_INDEX = "ux_users_username_key";

    /**
     * MariaDB/MySQL: makes the database refuse two accounts whose names differ only in capitals (TODO 1.9). SQLite has
     * {@code ux_users_username_nocase}; on MariaDB {@code users.username} is an exact ({@code utf8mb4_bin}) column, so only the
     * app's checks stood in the way, and two tills creating {@code Mai} and {@code mai} at the same moment, or a hand edit in
     * phpMyAdmin, could still make a pair, after which one of the two could not log in reliably. A new database gets the
     * stored column {@code username_key} (the lower-case name) with a unique key from {@link MariaDbSchema}; an older one gets
     * both here. If the names already contain such a pair the key is NOT built (a warning with a query to list them is
     * logged and the next start tries again), exactly like the SQLite rule and the staff-phone key.
     *
     * @return the lower-case names that are taken more than once (empty when the key exists or was built)
     */
    static List<String> ensureMariaLoginNameKey(Connection connection) throws SQLException {
        if (!mariaColumnExists(connection, "users", MARIA_LOGIN_KEY_COLUMN)) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE users ADD COLUMN " + MARIA_LOGIN_KEY_COLUMN
                        + " VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin AS (LOWER(username)) STORED");
            }
        }
        if (mariaIndexExists(connection, "users", MARIA_LOGIN_KEY_INDEX)) {
            return List.of();
        }
        List<String> duplicates = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT " + MARIA_LOGIN_KEY_COLUMN + " FROM users GROUP BY " + MARIA_LOGIN_KEY_COLUMN + " HAVING COUNT(*) > 1")) {
            while (rs.next()) duplicates.add(rs.getString(1));
        }
        if (!duplicates.isEmpty()) {
            // Count only: login names do not go into the log (TODO 6.28). The query below lists them from the database.
            LOGGER.warning(duplicates.size() + " login name(s) exist more than once in different capitals; the unique rule "
                    + MARIA_LOGIN_KEY_INDEX + " cannot be enforced by the database until they are fixed. List them with: "
                    + "SELECT username FROM users WHERE username_key IN (SELECT username_key FROM users GROUP BY username_key "
                    + "HAVING COUNT(*) > 1)");
            return duplicates;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE users ADD UNIQUE KEY " + MARIA_LOGIN_KEY_INDEX + " (" + MARIA_LOGIN_KEY_COLUMN + ")");
        }
        return List.of();
    }

    private static boolean mariaColumnExists(Connection connection, String table, String column) throws SQLException {
        try (java.sql.PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?")) {
            statement.setString(1, table);
            statement.setString(2, column);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static boolean mariaIndexExists(Connection connection, String table, String index) throws SQLException {
        try (java.sql.PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = ? AND index_name = ?")) {
            statement.setString(1, table);
            statement.setString(2, index);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        }
    }

    static final String PRODUCT_CODE_INDEX = "ux_products_code";

    /**
     * One product per code (TODO 4.42), SQLite: a partial unique index, so any number of products may have no code. The code is
     * stored in capitals ({@code ProductCode.clean}), so exact comparison is enough on both databases. Blank codes become NULL
     * first. If two products already share a code (typed in by hand) the index is skipped with a warning and tried again at the
     * next start, like the other unique rules.
     */
    static void ensureUniqueProductCode(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE products SET code = NULL WHERE code IS NOT NULL AND TRIM(code) = ''");
        }
        buildUnique(connection, PRODUCT_CODE_INDEX, "products", "code", "code IS NOT NULL",
                "SELECT code FROM products WHERE code IS NOT NULL GROUP BY code HAVING COUNT(*) > 1");
    }

    /**
     * MariaDB/MySQL: the same rule as a unique key (a plain unique key lets NULL repeat). A new database already has it from
     * {@link MariaDbSchema}; an older one gets it here, unless two products already share a code (a warning, tried again at the
     * next start).
     *
     * @return the codes that block the key (empty when it exists or was just built)
     */
    static List<String> ensureMariaProductCodeKey(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE products SET code = NULL WHERE code IS NOT NULL AND TRIM(code) = ''");
        }
        if (mariaIndexExists(connection, "products", PRODUCT_CODE_INDEX)) {
            return List.of();
        }
        List<String> duplicates = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT code FROM products WHERE code IS NOT NULL GROUP BY code HAVING COUNT(*) > 1")) {
            while (rs.next()) duplicates.add(rs.getString(1));
        }
        if (!duplicates.isEmpty()) {
            LOGGER.warning(duplicates.size() + " product code(s) are used by more than one product; the unique rule "
                    + PRODUCT_CODE_INDEX + " cannot be enforced by the database until they are fixed. List them with: "
                    + "SELECT code, COUNT(*) FROM products WHERE code IS NOT NULL GROUP BY code HAVING COUNT(*) > 1");
            return duplicates;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE products ADD UNIQUE KEY " + PRODUCT_CODE_INDEX + " (code)");
        }
        return List.of();
    }

    static final String PAID_AT_INDEX = "idx_orders_paid_at";

    /** Version 3 on SQLite (TODO 3.36): the date filters look orders up by the time they were paid. */
    static void createPaidAtIndex(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE INDEX IF NOT EXISTS " + PAID_AT_INDEX + " ON orders(paid_at)");
        }
    }

    /** Version 3 on MariaDB: {@link MariaDbSchema} creates this index with the table; a database made before that gets it here. */
    static void ensureMariaPaidAtIndex(Connection connection) throws SQLException {
        if (!mariaIndexExists(connection, "orders", PAID_AT_INDEX)) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE INDEX " + PAID_AT_INDEX + " ON orders (paid_at)");
            }
        }
    }

    static final String REFUNDS_ORDER_INDEX = "idx_refunds_order";
    static final String REFUNDS_AT_INDEX = "idx_refunds_at";

    /** Version 4 on SQLite (TODO 4.37): the same two indexes on {@code refunds} that MariaDB's table is made with. */
    static void createRefundIndexes(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE INDEX IF NOT EXISTS " + REFUNDS_ORDER_INDEX + " ON refunds(order_id)");
            statement.execute("CREATE INDEX IF NOT EXISTS " + REFUNDS_AT_INDEX + " ON refunds(refunded_at)");
        }
    }

    /** Version 4 on MariaDB: {@link MariaDbSchema} creates both with the table; a database whose table lacks one gets it here. */
    static void ensureMariaRefundIndexes(Connection connection) throws SQLException {
        if (!mariaIndexExists(connection, "refunds", REFUNDS_ORDER_INDEX)) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE INDEX " + REFUNDS_ORDER_INDEX + " ON refunds (order_id)");
            }
        }
        if (!mariaIndexExists(connection, "refunds", REFUNDS_AT_INDEX)) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE INDEX " + REFUNDS_AT_INDEX + " ON refunds (refunded_at)");
            }
        }
    }

    /**
     * The plain indexes that SQLite got in versions 2 to 4, as {name, table, columns}. Listed here so a test can compare the whole set with
     * what {@link MariaDbSchema} creates (TODO 6.72); the statements that create them are above and in {@link #createPerformanceIndexes}.
     */
    static final String[][] INDEXES_BEFORE_V5 = {
            {"idx_orders_order_date", "orders", "order_date"}, {"idx_orders_status", "orders", "status"},
            {"idx_orders_table_number", "orders", "table_number"}, {"idx_orders_customer_id", "orders", "customer_id"},
            {"idx_order_items_order_id", "order_items", "order_id"}, {"idx_products_category_id", "products", "category_id"},
            {"idx_orders_paid_at", "orders", "paid_at"}, {"idx_refunds_order", "refunds", "order_id"},
            {"idx_refunds_at", "refunds", "refunded_at"}};

    /**
     * Version 5 (TODO 6.72): the plain indexes MariaDB's tables are made with that SQLite lacked, as {name, table, columns}, with the same names
     * as MariaDB. The reports, the audit and stock logs and the booking list read these tables by date, product, table or action, so on a shop
     * that has run for years each of those reads was a scan of the whole table.
     */
    static final String[][] INDEXES_SINCE_V5 = {
            {"idx_orders_status_date", "orders", "status, order_date"},
            {"idx_stock_log_logged_at", "stock_log", "logged_at"}, {"idx_stock_log_product", "stock_log", "product_id"},
            {"idx_expenses_spent_on", "expenses", "spent_on"}, {"idx_expenses_created_at", "expenses", "created_at"},
            {"idx_reservations_when", "reservations", "reserved_for"}, {"idx_reservations_table", "reservations", "table_number, status"},
            {"idx_audit_log_logged_at", "audit_log", "logged_at"}, {"idx_audit_log_action", "audit_log", "action"},
            {"idx_audit_log_target", "audit_log", "target_type, target_id"},
            {"idx_shift_closes_period_end", "shift_closes", "period_end"},
            {"idx_gift_card_moves_order", "gift_card_moves", "order_id"},
            {"idx_time_clock_user_out", "time_clock", "user_id, clock_out"}};

    /**
     * Version 5, SQLite only: MariaDB's foreign key on {@code order_items.product_id} already makes an index there, SQLite's does not. The
     * product reports (best sellers, margin, refunds by product) read {@code order_items} by product.
     */
    static final String[][] INDEXES_SQLITE_ONLY_SINCE_V5 = {
            {"idx_order_items_product_id", "order_items", "product_id"}};

    /** Version 5 on SQLite. */
    static void createMirroredIndexes(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            for (String[] index : concat(INDEXES_SINCE_V5, INDEXES_SQLITE_ONLY_SINCE_V5)) {
                statement.execute("CREATE INDEX IF NOT EXISTS " + index[0] + " ON " + index[1] + "(" + index[2] + ")");
            }
        }
    }

    private static String[][] concat(String[][] first, String[][] second) {
        String[][] all = java.util.Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, all, first.length, second.length);
        return all;
    }

    /** Version 5 on MariaDB: {@link MariaDbSchema} creates all of them with the tables; one that is missing is added here. */
    static void ensureMariaMirroredIndexes(Connection connection) throws SQLException {
        for (String[] index : INDEXES_SINCE_V5) {
            if (!mariaIndexExists(connection, index[1], index[0])) {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("CREATE INDEX " + index[0] + " ON " + index[1] + " (" + index[2] + ")");
                }
            }
        }
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
