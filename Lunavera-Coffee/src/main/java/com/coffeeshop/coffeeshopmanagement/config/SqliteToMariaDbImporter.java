package com.coffeeshop.coffeeshopmanagement.config;

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
 * app stops with a message, so you never get half a shop. The SQLite file itself is only read, never changed.
 */
final class SqliteToMariaDbImporter {

    private static final Logger LOGGER = Logger.getLogger(SqliteToMariaDbImporter.class.getName());
    private static final int BATCH_SIZE = 500;

    /** One table to copy: its name, the columns in order, and which of them hold a date-time or a date. */
    record TableSpec(String name, List<String> columns, Set<String> dateTimeColumns, Set<String> dateColumns) {
    }

    /** In the order rows may be inserted (a table after the ones its foreign keys point to). */
    static final List<TableSpec> TABLES = List.of(
            spec("employees", List.of("id", "full_name", "phone", "email", "address", "position", "salary", "hire_date", "active"),
                    Set.of(), Set.of("hire_date")),
            spec("customers", List.of("id", "full_name", "phone", "email", "loyalty_points", "created_at"),
                    Set.of("created_at"), Set.of()),
            spec("users", List.of("id", "username", "password_hash", "role", "status", "employee_id", "customer_id", "created_at", "must_change_password"),
                    Set.of("created_at"), Set.of()),
            spec("categories", List.of("id", "name", "description", "active"), Set.of(), Set.of()),
            spec("products", List.of("id", "name", "category_id", "price", "cost", "stock", "description", "image_path", "active"),
                    Set.of(), Set.of()),
            spec("orders", List.of("id", "order_date", "employee_id", "customer_id", "status", "subtotal", "discount", "total",
                    "payment_method", "paid_at", "table_number", "discount_reason", "paid_by"),
                    Set.of("order_date", "paid_at"), Set.of()),
            spec("order_items", List.of("id", "order_id", "product_id", "product_name", "quantity", "unit_price", "line_total"),
                    Set.of(), Set.of()),
            spec("dining_tables", List.of("id", "table_number", "name", "status", "capacity", "current_order_id"),
                    Set.of(), Set.of()),
            spec("app_flags", List.of("name"), Set.of(), Set.of()));

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
        try (Connection sqlite = DriverManager.getConnection("jdbc:sqlite:" + file)) {
            if (!sqliteHasTable(sqlite, "users")) {
                LOGGER.warning("The file " + file + " is not a Lunavera database (it has no users table); nothing was imported or changed.");
                return false;
            }
            return copyEverything(maria, sqlite, file, state == State.ONLY_FIRST_RUN_DATA);
        }
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
                    throw new SQLException("Bảng " + entry.getKey() + ": đã chép " + entry.getValue()
                            + " dòng nhưng MariaDB đếm được " + inMaria + ".");
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
            throw new IllegalStateException("Không nhập được dữ liệu từ tệp SQLite " + file
                    + " vào MariaDB. Mọi thay đổi đã được hoàn tác (MariaDB giữ nguyên như trước, tệp SQLite không bị sửa). Lý do: "
                    + describeFailure(e), e);
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
            return "hai khách hàng trong tệp SQLite có cùng số điện thoại" + (phone.isEmpty() ? "" : " (" + phone + ")")
                    + ", mà MariaDB chỉ cho phép mỗi số điện thoại một khách. Hãy sửa hoặc gộp hai khách đó trong Quản lý khách hàng "
                    + "(chạy bản SQLite) rồi thử lại.";
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
            for (String table : List.of("order_items", "dining_tables", "orders", "products", "categories", "users", "customers",
                    "employees", "app_flags")) {
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
            return "must_change_password".equals(column) ? Integer.valueOf(0) : null; // an older file has no such column
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
