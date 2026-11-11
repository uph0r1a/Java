package com.coffeeshop.coffeeshopmanagement.config;

import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.TextNormalizer;
import com.coffeeshop.coffeeshopmanagement.util.DataPaths;
import com.coffeeshop.coffeeshopmanagement.util.DbTime;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * One-time copy of the shop's existing SQLite database into an empty MariaDB/MySQL database.
 *
 * <p>Switched on by {@code db.import.sqlite=data/lunavera.db} in {@code data/database.properties}. It runs at start-up,
 * only while the MariaDB database has no real data: either completely empty, or holding nothing but what the app creates by
 * itself on a first start (see {@link State}); real data is never overwritten or duplicated. It keeps every id
 * (so orders still point at the right products and customers), converts the date text to DATETIME, and checks that every
 * table ended up with the same number of rows. It is all one transaction: if anything fails, nothing is kept and the
 * app stops with a message, so you never get half a shop. The SQLite file itself is only read, never changed: it is opened read-only
 * (so SQLite cannot replay or delete a leftover {@code -wal} / {@code -journal} file next to it), and before anything is copied its
 * version, its health and its foreign keys are checked ({@link #checkSource}, TODO 3.62).
 */
final class SqliteToMariaDbImporter {

    private static final Logger LOGGER = Logger.getLogger(SqliteToMariaDbImporter.class.getName());
    private static final int BATCH_SIZE = 500;

    /** One table to copy: its name, the columns in order, and which of them hold a date-time or a date. */
    record TableSpec(String name, List<String> columns, Set<String> dateTimeColumns, Set<String> dateColumns) {
    }

    /** In the order rows may be inserted (a table after the ones its foreign keys point to). */
    static final List<TableSpec> TABLES = List.of(
            spec("employees", List.of("id", "full_name", "phone", "email", "address", "position", "salary", "hire_date", "active",
                    "anonymised_at"),
                    Set.of("anonymised_at"), Set.of("hire_date")),
            spec("customers", List.of("id", "full_name", "phone", "email", "loyalty_points", "lifetime_points", "created_at", "anonymised_at", "birthday", "birthday_used_on"),
                    Set.of("created_at", "anonymised_at"), Set.of("birthday", "birthday_used_on")),
            spec("users", List.of("id", "username", "password_hash", "role", "status", "employee_id", "customer_id", "created_at", "must_change_password",
                    "pin_hash", "pin_failures"),
                    Set.of("created_at"), Set.of()),
            spec("categories", List.of("id", "name", "description", "active", "sort_order"), Set.of(), Set.of()),
            spec("products", List.of("id", "name", "category_id", "price", "cost", "stock", "description", "image_path", "active",
                    "sort_order", "reorder_level", "code"),
                    Set.of(), Set.of()),
            spec("orders", List.of("id", "order_date", "employee_id", "customer_id", "status", "subtotal", "discount", "total",
                    "payment_method", "paid_at", "table_number", "discount_reason", "paid_by", "points_earned", "cash_received", "change_given",
                    "transfer_ref", "points_redeemed", "points_value", "cancelled_by", "cancelled_at", "cancel_reason", "voucher_code", "voucher_value", "queue_no", "rev", "queue_day", "channel", "print_count", "promo_value",
                    "complimentary_kind", "complimentary_for", "complimentary_note", "complimentary_value"),
                    Set.of("order_date", "paid_at", "cancelled_at"), Set.of("queue_day")),
            spec("order_items", List.of("id", "order_id", "product_id", "product_name", "quantity", "unit_price", "line_total", "unit_cost",
                    "note", "size_name", "size_percent", "extras", "option_cost", "promo_name", "promo_value"),
                    Set.of(), Set.of()),
            spec("order_payments", List.of("id", "order_id", "method", "amount"), Set.of(), Set.of()),
            spec("refunds", List.of("id", "order_id", "order_item_id", "quantity", "line_amount", "amount", "method", "restocked", "reason",
                    "refunded_by", "refunded_by_name", "refunded_at"),
                    Set.of("refunded_at"), Set.of()),
            spec("stock_takes", List.of("order_item_id", "ingredient_id", "amount"), Set.of(), Set.of()), // TODO 2.96
            spec("dining_tables", List.of("id", "table_number", "name", "status", "capacity", "current_order_id"),
                    Set.of(), Set.of()),
            spec("stock_log", List.of("id", "logged_at", "kind", "product_id", "product_name", "quantity", "stock_before", "stock_after",
                    "unit_cost", "supplier", "note", "employee_id", "employee_name"),
                    Set.of("logged_at"), Set.of()),
            spec("expenses", List.of("id", "spent_on", "amount", "category", "note", "from_drawer", "recorded_by", "recorded_by_name",
                    "created_at"),
                    Set.of("created_at"), Set.of("spent_on")),
            spec("ingredients", List.of("id", "name", "unit", "stock", "min_stock", "created_at", "unit_cost"),
                    Set.of("created_at"), Set.of()),
            spec("recipes", List.of("product_id", "ingredient_id", "amount"), Set.of(), Set.of()),
            spec("product_options", List.of("id", "product_id", "kind", "name", "price_delta", "cost_delta", "recipe_percent", "is_default", "active"),
                    Set.of(), Set.of()),
            spec("time_clock", List.of("id", "user_id", "user_name", "clock_in", "clock_out"), Set.of("clock_in", "clock_out"), Set.of()),
            spec("gift_cards", List.of("id", "code", "initial_amount", "balance", "sold_by", "created_at", "sold_by_id"), Set.of("created_at"), Set.of()),
            spec("gift_card_moves", List.of("id", "card_id", "order_id", "amount", "moved_at"), Set.of("moved_at"), Set.of()),
            spec("reservations", List.of("id", "table_number", "customer_name", "phone", "reserved_for", "people", "note", "status",
                    "created_by", "created_at", "closed_at", "created_by_id", "customer_id"),
                    Set.of("reserved_for", "created_at", "closed_at"), Set.of()),
            spec("vouchers", List.of("id", "code", "kind", "value", "min_subtotal", "valid_from", "valid_to", "max_uses", "used_count",
                    "active", "created_at", "once_per_customer"),
                    Set.of("created_at"), Set.of("valid_from", "valid_to")),
            spec("promotions", List.of("id", "name", "kind", "percent", "fixed_price", "buy_qty", "free_qty", "days", "time_from", "time_to",
                    "valid_from", "valid_to", "active", "created_at"),
                    Set.of("created_at"), Set.of("valid_from", "valid_to")),
            spec("promotion_targets", List.of("id", "promotion_id", "product_id", "category_id", "quantity"), Set.of(), Set.of()),
            spec("shift_closes", List.of("id", "period_start", "period_end", "closed_by", "closed_by_name", "paid_orders", "revenue",
                    "cash_sales", "opening_cash", "expected_cash", "counted_cash", "difference", "note", "cash_out"),
                    Set.of("period_start", "period_end"), Set.of()),
            spec("audit_log", List.of("id", "logged_at", "actor_user_id", "actor_employee_id", "actor_name", "action", "target_type",
                    "target_id", "target_label", "old_value", "new_value", "detail"),
                    Set.of("logged_at"), Set.of()),
            spec("app_flags", List.of("name"), Set.of(), Set.of()),
            spec("settings", List.of("name", "value"), Set.of(), Set.of()));

    private static TableSpec spec(String name, List<String> columns, Set<String> dateTimes, Set<String> dates) {
        return new TableSpec(name, columns, dateTimes, dates);
    }

    private SqliteToMariaDbImporter() {
    }

    /** What the MariaDB database holds right now, as far as the import is concerned. */
    enum State {
        /** Nothing at all: safe to fill. */
        EMPTY,
        /** Only what the app creates by itself on a first start (the admin account, the starter menu, the tables): safe to replace. */
        ONLY_FIRST_RUN_DATA,
        /** Real data (a customer, an order, another account, an own product...): never touched by the import. */
        HAS_DATA
    }

    /** Row counts used to decide the {@link State}. */
    record Counts(int users, int adminUsers, int customers, int orders, int employees, int products) {
    }

    /**
     * Decides whether the import may go ahead. The first start of the app on a new database creates an {@code admin} account, the
     * starter menu and the dining tables, so a database that was started once is not literally empty; it still counts as "only first-run
     * data" while there is no customer, no order, no other account and nothing but the starter menu's own products and categories.
     */
    static State classify(Counts counts, Set<String> productNames, Set<String> categoryNames,
                          Set<String> starterProducts, Set<String> starterCategories) {
        if (counts.users() == 0 && counts.customers() == 0 && counts.orders() == 0 && counts.employees() == 0 && counts.products() == 0) {
            return State.EMPTY;
        }
        boolean onlyFirstRun = counts.orders() == 0 && counts.customers() == 0
                && counts.users() <= 1 && counts.users() == counts.adminUsers()
                && counts.employees() <= 1
                && starterProducts.containsAll(productNames) && starterCategories.containsAll(categoryNames);
        return onlyFirstRun ? State.ONLY_FIRST_RUN_DATA : State.HAS_DATA;
    }

    /** @return true if data was imported; false if nothing was asked for, the file is missing or not a Lunavera file, or MariaDB has real data. */
    static boolean importIfRequested(Connection maria, DatabaseSettings settings) throws SQLException {
        if (!settings.isMariaDb() || settings.importSqlite() == null) {
            return false;
        }
        Path file = locate(settings.importSqlite());
        if (file == null) {
            LOGGER.warning("db.import.sqlite points to \"" + settings.importSqlite() + "\", but that file does not exist; nothing imported.");
            return false;
        }
        State state = detectState(maria);
        if (state == State.HAS_DATA) {
            LOGGER.info("The MariaDB database already has real data, so the SQLite import (db.import.sqlite) was skipped. "
                    + "You can remove that line from database.properties.");
            return false;
        }
        LOGGER.info("Importing the SQLite database " + file + " into the "
                + (state == State.EMPTY ? "empty" : "new (first-run data only)") + " MariaDB database ...");
        try (Connection sqlite = openSourceReadOnly(file)) {
            if (!sqliteHasTable(sqlite, "users")) {
                LOGGER.warning("The file " + file + " is not a Lunavera database (it has no users table); nothing was imported or changed.");
                return false;
            }
            SourceCheck check = checkSource(sqlite);
            if (check.warning() != null) {
                LOGGER.warning("SQLite import, " + file + ": " + check.warning());
            }
            if (check.problem() != null) {
                // Stop, do not carry on with an empty shop: the owner would create a new administrator and believe the old data was imported.
                LOGGER.severe("SQLite import refused for " + file + ": " + check.problem());
                throw new IllegalStateException(I18n.t("db.import.failed", file, check.problem()));
            }
            return copyEverything(maria, sqlite, file, state == State.ONLY_FIRST_RUN_DATA);
        }
    }

    /** The old file opened so that SQLite can never write to it (no replay or removal of a leftover -wal / -journal, no new data). */
    static Connection openSourceReadOnly(Path file) throws SQLException {
        org.sqlite.SQLiteConfig config = new org.sqlite.SQLiteConfig();
        config.setReadOnly(true);
        return DriverManager.getConnection("jdbc:sqlite:" + file, config.toProperties());
    }

    /**
     * What looking at the old file found. A {@code problem} stops the import and is shown to the owner (so it is written in plain words,
     * never with SQLite's own text); a {@code warning} only goes to the log.
     */
    record SourceCheck(String problem, String warning) {
    }

    /** At most this many orphan rows are named in the message; the number of all of them is still given. */
    private static final int ORPHANS_SHOWN = 5;

    /**
     * Looks at the old file before one row is copied (TODO 3.62): a file from a <b>newer</b> version of the app is refused (its extra columns
     * would be dropped without a word); a file that fails {@code PRAGMA integrity_check} is refused; so is a file with rows that point at
     * something that is not there (the copy runs with foreign keys off, so such rows would only fail later, on the first screen that joins
     * them). Version 0 is a file from before the money migration: it is copied (whole dong, so rounding changes nothing for real data) but a
     * warning is logged. The version compared with is {@link SchemaMigrations#LATEST_VERSION}, as for a restored backup; the money
     * migration's {@code CURRENT_SCHEMA_VERSION} is only step 1 of it.
     */
    static SourceCheck checkSource(Connection sqlite) throws SQLException {
        int version = 0;
        try (Statement statement = sqlite.createStatement(); ResultSet rs = statement.executeQuery("PRAGMA user_version")) {
            if (rs.next()) {
                version = rs.getInt(1);
            }
        }
        if (version > SchemaMigrations.LATEST_VERSION) {
            return new SourceCheck(I18n.t("db.import.newer", version, SchemaMigrations.LATEST_VERSION), null);
        }
        try (Statement statement = sqlite.createStatement(); ResultSet rs = statement.executeQuery("PRAGMA integrity_check")) {
            String first = rs.next() ? rs.getString(1) : null;
            if (!"ok".equalsIgnoreCase(first)) {
                LOGGER.warning("integrity_check of the old SQLite file said: " + first); // the owner gets plain words, the log gets SQLite's
                return new SourceCheck(I18n.t("db.import.damaged"), null);
            }
        } catch (SQLException unreadable) {
            LOGGER.warning("The old SQLite file could not be read to the end: " + unreadable.getMessage());
            return new SourceCheck(I18n.t("db.import.damaged"), null);
        }
        int orphans = 0;
        List<String> examples = new ArrayList<>();
        try (Statement statement = sqlite.createStatement(); ResultSet rs = statement.executeQuery("PRAGMA foreign_key_check")) {
            while (rs.next()) {
                orphans++;
                if (examples.size() < ORPHANS_SHOWN) {
                    examples.add(rs.getString(1) + " #" + rs.getLong(2) + " -> " + rs.getString(3));
                }
            }
        }
        if (orphans > 0) {
            return new SourceCheck(I18n.t("db.import.orphans", orphans, String.join(", ", examples)), null);
        }
        String clash = clashingCategoryNames(sqlite);
        if (clash != null) {
            return new SourceCheck(clash, null);
        }
        String warning = version == 0
                ? "The file is from before the money migration (version 0): prices stored as decimals are rounded to whole dong. "
                + "If you can, open it once with the SQLite version of the app, which upgrades it, and import that file instead."
                : null;
        return new SourceCheck(null, warning);
    }

    private static boolean copyEverything(Connection maria, Connection sqlite, Path file, boolean replaceFirstRunData) throws SQLException {
        boolean autoCommit = maria.getAutoCommit();
        maria.setAutoCommit(false);
        try {
            setForeignKeyChecks(maria, false);
            if (replaceFirstRunData) {
                clearFirstRunData(maria); // inside the same transaction: a failure below puts the first-run data back
            }
            Map<String, Integer> copied = new LinkedHashMap<>();
            for (TableSpec table : TABLES) {
                if (sqliteHasTable(sqlite, table.name())) {
                    copied.put(table.name(), copyTable(sqlite, maria, table));
                }
            }
            for (Map.Entry<String, Integer> entry : copied.entrySet()) {
                int inMaria = count(maria, entry.getKey());
                if (inMaria != entry.getValue()) {
                    throw new SQLException(I18n.t("db.import.mismatch", entry.getKey(), entry.getValue(), inMaria));
                }
            }
            maria.commit();
            LOGGER.info("SQLite import finished (rows per table): " + copied);
            return true;
        } catch (SQLException | RuntimeException e) {
            try {
                maria.rollback();
            } catch (SQLException rollbackProblem) {
                e.addSuppressed(rollbackProblem);
            }
            throw new IllegalStateException(I18n.t("db.import.failed", file, describeFailure(e)), e);
        } finally {
            try {
                setForeignKeyChecks(maria, true);
            } finally {
                maria.setAutoCommit(autoCommit);
            }
        }
    }

    /** The reason to show for a failed import; a duplicate customer phone gets a plain explanation of what to fix. */
    static String describeFailure(Exception error) {
        if (error instanceof SQLException sql && sql.getErrorCode() == 1062 && String.valueOf(sql.getMessage()).contains("ux_customers_phone")) {
            String message = sql.getMessage();
            int from = message.indexOf("Duplicate entry '");
            String phone = "";
            if (from >= 0) {
                int start = from + "Duplicate entry '".length();
                int end = message.indexOf('\'', start);
                phone = end > start ? message.substring(start, end) : "";
            }
            return I18n.t("db.import.dupphone", (phone.isEmpty() ? "" : " (" + phone + ")"));
        }
        return String.valueOf(error.getMessage());
    }

    private static State detectState(Connection maria) throws SQLException {
        Counts counts = new Counts(count(maria, "users"), countWhere(maria, "users", "username = 'admin'"),
                count(maria, "customers"), count(maria, "orders"), count(maria, "employees"), count(maria, "products"));
        if (counts.orders() > 0 || counts.customers() > 0 || counts.users() > 1 || counts.employees() > 1) {
            return State.HAS_DATA; // no need to read any names
        }
        Set<String> starterProducts = new HashSet<>();
        Set<String> starterCategories = new HashSet<>();
        for (MenuSeedData.MenuItem item : MenuSeedData.ITEMS) {
            starterProducts.add(item.name());
            starterCategories.add(item.category());
        }
        return classify(counts, names(maria, "products"), names(maria, "categories"), starterProducts, starterCategories);
    }

    private static Set<String> names(Connection connection, String table) throws SQLException {
        Set<String> names = new HashSet<>();
        try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery("SELECT name FROM " + table)) {
            while (rs.next()) {
                names.add(rs.getString(1));
            }
        }
        return names;
    }

    private static int countWhere(Connection connection, String table, String where) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM " + table + " WHERE " + where)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    /** Removes what the app created on its first start so the old data can take its place; runs inside the import's transaction. */
    private static void clearFirstRunData(Connection maria) throws SQLException {
        try (Statement statement = maria.createStatement()) {
            for (String table : List.of("promotion_targets", "promotions", "gift_card_moves", "gift_cards", "time_clock", "product_options", "recipes", "ingredients", "refunds", "order_payments", "stock_takes", "order_items", "dining_tables", "orders", "products", "categories", "users", "customers",
                    "employees", "stock_log", "expenses", "vouchers", "reservations", "shift_closes", "audit_log", "app_flags", "settings")) {
                statement.executeUpdate("DELETE FROM " + table);
            }
        }
    }

    /** The configured path as given, else relative to the data folder; null if neither exists. */
    private static Path locate(String configured) {
        Path asGiven = Path.of(configured).toAbsolutePath().normalize();
        if (Files.isRegularFile(asGiven)) {
            return asGiven;
        }
        Path inDataFolder = DataPaths.dataDir().resolve(configured).normalize();
        return Files.isRegularFile(inDataFolder) ? inDataFolder : null;
    }

    private static int count(Connection connection, String table) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static void setForeignKeyChecks(Connection maria, boolean on) throws SQLException {
        try (Statement statement = maria.createStatement()) {
            statement.execute("SET FOREIGN_KEY_CHECKS = " + (on ? 1 : 0));
        }
    }

    /**
     * TODO 3.55: {@code categories.name} is {@code UNIQUE} on both databases, but MariaDB compares with {@code utf8mb4_unicode_ci}
     * (capitals and accents do not count) and SQLite compares the exact text, so a SQLite shop can hold "Trà" and "Tra" as two
     * categories that MariaDB would refuse as one. The copy would then stop on the second one with the server's own words, after
     * the earlier tables were already copied (and rolled back). Looking first lets the owner be told the two names. Compared with
     * {@link TextNormalizer#collationKey}, which is the server's own rule and nothing stricter, so a file MariaDB would accept is never
     * refused. Returns the plain-words problem, or null when no two names clash (or the file has no categories table).
     */
    static String clashingCategoryNames(Connection sqlite) throws SQLException {
        if (!sqliteHasTable(sqlite, "categories")) {
            return null;
        }
        Map<String, String> firstWithKey = new LinkedHashMap<>();
        try (Statement statement = sqlite.createStatement();
             ResultSet rs = statement.executeQuery("SELECT name FROM categories WHERE name IS NOT NULL ORDER BY id")) {
            while (rs.next()) {
                String name = rs.getString(1);
                String earlier = firstWithKey.putIfAbsent(TextNormalizer.collationKey(name), name);
                if (earlier != null) {
                    return I18n.t("db.import.dupcategory", earlier, name);
                }
            }
        }
        return null;
    }

    private static boolean sqliteHasTable(Connection sqlite, String name) throws SQLException {
        try (PreparedStatement statement = sqlite.prepareStatement(SqlDialect.SQLITE.tableExistsQuery())) {
            statement.setString(1, name);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static int copyTable(Connection sqlite, Connection maria, TableSpec table) throws SQLException {
        String insertSql = "INSERT INTO " + table.name() + " (" + String.join(", ", table.columns()) + ") VALUES ("
                + String.join(", ", java.util.Collections.nCopies(table.columns().size(), "?")) + ")";
        int copied = 0;
        try (Statement select = sqlite.createStatement();
             ResultSet rs = select.executeQuery("SELECT * FROM " + table.name());
             PreparedStatement insert = maria.prepareStatement(insertSql)) {
            Set<String> available = columnNames(rs.getMetaData());
            int pending = 0;
            while (rs.next()) {
                for (int i = 0; i < table.columns().size(); i++) {
                    String column = table.columns().get(i);
                    Object value = available.contains(column) ? rs.getObject(column) : null; // an older file may lack a newer column
                    insert.setObject(i + 1, convert(table, column, value));
                }
                insert.addBatch();
                copied++;
                if (++pending == BATCH_SIZE) {
                    insert.executeBatch();
                    pending = 0;
                }
            }
            if (pending > 0) {
                insert.executeBatch();
            }
        }
        return copied;
    }

    private static Set<String> columnNames(ResultSetMetaData meta) throws SQLException {
        Set<String> names = new HashSet<>();
        for (int i = 1; i <= meta.getColumnCount(); i++) {
            names.add(meta.getColumnName(i).toLowerCase(Locale.ROOT));
        }
        return names;
    }

    /** What to bind for one SQLite value: date text becomes DATETIME / DATE text, everything else is passed through. */
    static Object convert(TableSpec table, String column, Object value) {
        if (value == null) {
            return "must_change_password".equals(column) || "pin_failures".equals(column) || "sort_order".equals(column) ? Integer.valueOf(0) : null; // an older file has no such column
        }
        if (table.dateTimeColumns().contains(column)) {
            return DbTime.toSqlDateTime(value.toString());
        }
        if (("customers".equals(table.name()) || "employees".equals(table.name())) && "phone".equals(column) && value.toString().isBlank()) {
            return null; // MariaDB's unique phone keys let NULL repeat but not '' (SQLite's partial index allowed both)
        }
        if (table.dateColumns().contains(column)) {
            String text = value.toString().trim();
            return text.length() > 10 ? text.substring(0, 10) : text;
        }
        return value;
    }

    /** For tests: the table names in copy order. */
    static List<String> tableNames() {
        List<String> names = new ArrayList<>();
        for (TableSpec table : TABLES) {
            names.add(table.name());
        }
        return names;
    }
}
