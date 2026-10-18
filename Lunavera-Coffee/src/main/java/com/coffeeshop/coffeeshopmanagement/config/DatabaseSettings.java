package com.coffeeshop.coffeeshopmanagement.config;

import com.coffeeshop.coffeeshopmanagement.util.DataPaths;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Which database the app uses and how to reach it. Read once at start-up from
 * {@code data/database.properties} (outside git: it holds the database password), and every key can
 * be overridden by a {@code -Dlunavera.<key>=...} system property.
 *
 * <p><b>MariaDB/MySQL is the default database.</b> Without the file the app tries a server on this computer
 * (127.0.0.1:3306, database {@code lunavera}); if there is none it stops at start-up with a message that says what to do.
 * {@code db.type=sqlite} switches back to the local SQLite file. See {@code docs/DATABASE.md} and {@code database.properties.example}.
 *
 * <pre>
 * db.type=mariadb            # mariadb (default) | mysql | sqlite
 * db.host=127.0.0.1
 * db.port=3306
 * db.name=lunavera
 * db.user=lunavera
 * db.password=...
 * db.import.sqlite=data/lunavera.db   # optional: copy this SQLite file in once, if the new database is empty
 * db.mysqldump=mysqldump              # optional: full path of mysqldump, used by the backup button
 * db.phpmyadmin=http://localhost/phpmyadmin/   # optional: the page the "Mở phpMyAdmin" button opens
 * </pre>
 */
public record DatabaseSettings(SqlDialect dialect, String host, int port, String database, String user,
                               String password, String importSqlite, String mysqldump, String phpMyAdminUrl) {

    public static final String FILE_NAME = "database.properties";
    private static final Pattern SAFE_NAME = Pattern.compile("[A-Za-z0-9_$]{1,64}");
    private static final Pattern SAFE_HOST = Pattern.compile("[A-Za-z0-9._:-]{1,255}");

    /** True when {@code data/database.properties} exists (used to give a better start-up message when it does not). */
    public static boolean fileExists() {
        return Files.isRegularFile(DataPaths.dataDir().resolve(FILE_NAME));
    }

    /** Reads {@code data/database.properties} (if there is one) plus the {@code lunavera.*} system properties. */
    public static DatabaseSettings load() {
        Properties file = new Properties();
        Path path = DataPaths.dataDir().resolve(FILE_NAME);
        if (Files.isRegularFile(path)) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                file.load(reader);
            } catch (IOException e) {
                throw new IllegalStateException("Không đọc được tệp cấu hình cơ sở dữ liệu " + path + ": " + e.getMessage(), e);
            }
        }
        return fromProperties(file, System::getProperty);
    }

    /** Builds the settings from file properties; {@code overrides} supplies the system properties (a function so tests can fake it). */
    public static DatabaseSettings fromProperties(Properties file, Function<String, String> overrides) {
        SqlDialect dialect = SqlDialect.fromSetting(read(file, overrides, "db.type", "mariadb"));
        String host = read(file, overrides, "db.host", "127.0.0.1");
        String database = read(file, overrides, "db.name", "lunavera");
        int port = parsePort(read(file, overrides, "db.port", "3306"));
        if (dialect == SqlDialect.MARIADB) {
            if (!SAFE_HOST.matcher(host).matches()) {
                throw new IllegalArgumentException("db.host không hợp lệ: \"" + host + "\".");
            }
            if (!SAFE_NAME.matcher(database).matches()) {
                throw new IllegalArgumentException("db.name chỉ được gồm chữ, số và dấu gạch dưới: \"" + database + "\".");
            }
        }
        return new DatabaseSettings(dialect, host, port, database,
                read(file, overrides, "db.user", "lunavera"),
                read(file, overrides, "db.password", ""),
                emptyToNull(read(file, overrides, "db.import.sqlite", "")),
                read(file, overrides, "db.mysqldump", "mysqldump"),
                webAddress(read(file, overrides, "db.phpmyadmin", "http://localhost/phpmyadmin/")));
    }

    private static String read(Properties file, Function<String, String> overrides, String key, String fallback) {
        String override = overrides.apply("lunavera." + key);
        if (override != null && !override.isBlank()) {
            return override.trim();
        }
        String value = file.getProperty(key);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static int parsePort(String text) {
        try {
            int port = Integer.parseInt(text);
            if (port < 1 || port > 65535) {
                throw new NumberFormatException();
            }
            return port;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("db.port không hợp lệ: \"" + text + "\" (cần số từ 1 đến 65535).");
        }
    }

    /** Only http:// and https:// addresses: the button must never be able to open a file or another kind of link. */
    private static String webAddress(String text) {
        String lower = text.toLowerCase(java.util.Locale.ROOT);
        if (!(lower.startsWith("http://") || lower.startsWith("https://")) || text.length() > 300 || text.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("db.phpmyadmin phải là địa chỉ http:// hoặc https:// (không có dấu cách): \"" + text + "\".");
        }
        return text;
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    public boolean isMariaDb() {
        return dialect == SqlDialect.MARIADB;
    }

    /** The JDBC URL of the MariaDB/MySQL database (host and name were checked above, so nothing odd can be injected). */
    public String mariaDbUrl() {
        return "jdbc:mariadb://" + host + ":" + port + "/" + database + "?connectTimeout=5000";
    }

    /** Where we connect, for messages and logs - never includes the password. */
    public String describe() {
        return isMariaDb() ? "MariaDB/MySQL " + user + "@" + host + ":" + port + "/" + database : "SQLite";
    }

    @Override
    public String toString() {
        return "DatabaseSettings[" + describe() + "]";
    }
}
