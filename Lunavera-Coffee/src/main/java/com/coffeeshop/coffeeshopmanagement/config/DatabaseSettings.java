package com.coffeeshop.coffeeshopmanagement.config;

import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.DataPaths;

import java.io.IOException;
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
 * db.ssl=false               # true = encrypt the connection (TLS), checking the server's certificate (TODO 2A.26)
 * db.sslMode=                # optional, instead of db.ssl: disable | trust | verify-ca | verify-full
 * db.sslServerCert=          # optional: the server's certificate or CA file, for a self-signed or private-CA server
 * db.snapshots=true         # false = this till makes no hourly SQLite copy (only one till of a shop makes them anyway, TODO 3.51)
 * db.socketTimeoutMs=30000   # how long to wait for the server to answer before giving up; 0 = wait forever
 * </pre>
 *
 * <p>A server on another computer should always be reached with TLS: without it customer names, phone numbers, password hashes
 * and the database password cross the shop network in clear text. {@link #insecureRemoteWarning()} says so at start-up.
 */
public record DatabaseSettings(SqlDialect dialect, String host, int port, String database, String user,
                               String password, String importSqlite, String mysqldump, String phpMyAdminUrl,
                               SslMode sslMode, String sslServerCert, int socketTimeoutMillis) {

    /** How the connection to a MariaDB/MySQL server is protected (the values of the connector's {@code sslMode}). */
    public enum SslMode {
        /** No encryption. Fine for a server on this computer, never for another one. */
        DISABLE("disable"),
        /** Encrypted, but the server is not checked: stops listening in, not a fake server. */
        TRUST("trust"),
        /** Encrypted, and the certificate must be signed by a CA we trust (or be the file in {@code db.sslServerCert}). */
        VERIFY_CA("verify-ca"),
        /** As {@link #VERIFY_CA}, and the certificate must also be for the host name used. */
        VERIFY_FULL("verify-full");

        private final String value;

        SslMode(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }

        static SslMode fromSetting(String text) {
            for (SslMode mode : values()) {
                if (mode.value.equalsIgnoreCase(text.trim())) {
                    return mode;
                }
            }
            throw new IllegalArgumentException(I18n.t("db.sslmode.invalid", text));
        }
    }

    /** How long a call waits for the server's answer by default; a stuck server no longer freezes the till for ever (TODO 2A.26). */
    public static final int DEFAULT_SOCKET_TIMEOUT_MILLIS = 30_000;
    static final int MAX_SOCKET_TIMEOUT_MILLIS = 3_600_000;

    public static final String FILE_NAME = "database.properties";
    private static final Pattern SAFE_NAME = Pattern.compile("[A-Za-z0-9_$]{1,64}");
    private static final Pattern SAFE_HOST = Pattern.compile("[A-Za-z0-9._:-]{1,255}");

    /** The settings of a connection without TLS and with the default read timeout (what every caller had before TODO 2A.26). */
    public DatabaseSettings(SqlDialect dialect, String host, int port, String database, String user, String password,
                            String importSqlite, String mysqldump, String phpMyAdminUrl) {
        this(dialect, host, port, database, user, password, importSqlite, mysqldump, phpMyAdminUrl,
                SslMode.DISABLE, null, DEFAULT_SOCKET_TIMEOUT_MILLIS);
    }

    /** True when {@code data/database.properties} exists (used to give a better start-up message when it does not). */
    public static boolean fileExists() {
        return Files.isRegularFile(DataPaths.dataDir().resolve(FILE_NAME));
    }

    /** Reads {@code data/database.properties} (if there is one) plus the {@code lunavera.*} system properties. */
    public static DatabaseSettings load() {
        Properties file = new Properties();
        Path path = DataPaths.dataDir().resolve(FILE_NAME);
        if (Files.isRegularFile(path)) {
            try {
                file = parseLiteral(Files.readString(path, StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new IllegalStateException(I18n.t("db.settings.unreadable", path, e.getMessage()), e);
            }
        }
        return fromProperties(file, System::getProperty);
    }

    /**
     * Reads the text of {@code database.properties} <em>literally</em> (TODO 2.82): one setting per line, the key before the
     * first {@code =} and the value exactly as written after it. {@code Properties.load} treats a backslash as an escape
     * (so a generated password {@code ab\cd} became {@code abcd}, {@code x\u0041y} became {@code xAy}), drops spaces at the
     * start of a value, and lets a backslash at the end of a line swallow the next setting (a SQLite shop then lost its
     * {@code db.type} and was sent to MariaDB). Here a backslash is an ordinary character. Blank lines and lines that start
     * with {@code #} or {@code !} are comments; a byte-order mark at the start (Notepad adds one) is ignored; a line without
     * {@code =} is ignored.
     */
    static Properties parseLiteral(String text) {
        Properties properties = new Properties();
        if (text.startsWith("\uFEFF")) {
            text = text.substring(1);
        }
        for (String line : text.split("\r\n|\r|\n", -1)) {
            String trimmed = line.stripLeading();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("!")) {
                continue;
            }
            int equals = trimmed.indexOf('=');
            if (equals <= 0) {
                continue;
            }
            String key = trimmed.substring(0, equals).strip();
            if (!key.isEmpty()) {
                properties.setProperty(key, trimmed.substring(equals + 1));
            }
        }
        return properties;
    }

    /** Builds the settings from file properties; {@code overrides} supplies the system properties (a function so tests can fake it). */
    public static DatabaseSettings fromProperties(Properties file, Function<String, String> overrides) {
        SqlDialect dialect = SqlDialect.fromSetting(read(file, overrides, "db.type", "mariadb"));
        String host = read(file, overrides, "db.host", "127.0.0.1");
        String database = read(file, overrides, "db.name", "lunavera");
        int port = parsePort(read(file, overrides, "db.port", "3306"));
        if (dialect == SqlDialect.MARIADB) {
            if (!SAFE_HOST.matcher(host).matches()) {
                throw new IllegalArgumentException(I18n.t("db.host.invalid", host));
            }
            if (!SAFE_NAME.matcher(database).matches()) {
                throw new IllegalArgumentException(I18n.t("db.name.invalid", database));
            }
        }
        SslMode sslMode = SslMode.DISABLE;
        String sslServerCert = null;
        int socketTimeout = DEFAULT_SOCKET_TIMEOUT_MILLIS;
        if (dialect == SqlDialect.MARIADB) { // these are never used by SQLite, so a leftover odd value must not stop it
            sslMode = parseSslMode(read(file, overrides, "db.ssl", "false"), read(file, overrides, "db.sslMode", ""));
            sslServerCert = parseServerCert(read(file, overrides, "db.sslServerCert", ""), sslMode);
            socketTimeout = parseSocketTimeout(read(file, overrides, "db.socketTimeoutMs", String.valueOf(DEFAULT_SOCKET_TIMEOUT_MILLIS)));
        }
        return new DatabaseSettings(dialect, host, port, database,
                read(file, overrides, "db.user", "lunavera"),
                readPassword(file, overrides),
                emptyToNull(read(file, overrides, "db.import.sqlite", "")),
                read(file, overrides, "db.mysqldump", "mysqldump"),
                webAddress(read(file, overrides, "db.phpmyadmin", "http://localhost/phpmyadmin/")),
                sslMode, sslServerCert, socketTimeout);
    }

    /**
     * {@code db.snapshots} (default true): may this till make the hourly SQLite copy of a MariaDB database (TODO 3.51)? The tills
     * of one shop elect one of themselves, so the setting is only for switching the copies off on a till for good.
     */
    public static boolean snapshotsEnabled() {
        return snapshotsEnabled(loadFileOnly(), System::getProperty);
    }

    static boolean snapshotsEnabled(Properties file, Function<String, String> overrides) {
        return parseBoolean("db.snapshots", read(file, overrides, "db.snapshots", "true"));
    }

    private static Properties loadFileOnly() {
        Path path = DataPaths.dataDir().resolve(FILE_NAME);
        try {
            return Files.isRegularFile(path) ? parseLiteral(Files.readString(path, StandardCharsets.UTF_8)) : new Properties();
        } catch (IOException e) {
            return new Properties();
        }
    }

    /** {@code db.sslMode} wins over {@code db.ssl}; {@code db.ssl=true} alone means the strictest mode, verify-full. */
    static SslMode parseSslMode(String ssl, String mode) {
        boolean wantsTls = parseBoolean("db.ssl", ssl);
        if (!mode.isBlank()) {
            SslMode explicit = SslMode.fromSetting(mode);
            if (wantsTls && explicit == SslMode.DISABLE) {
                throw new IllegalArgumentException(I18n.t("db.ssl.conflict"));
            }
            return explicit;
        }
        return wantsTls ? SslMode.VERIFY_FULL : SslMode.DISABLE;
    }

    private static boolean parseBoolean(String key, String text) {
        switch (text.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "true", "yes", "on", "1" -> {
                return true;
            }
            case "false", "no", "off", "0" -> {
                return false;
            }
            default -> throw new IllegalArgumentException(I18n.t("db.bool.invalid", key, text));
        }
    }

    /** The certificate file for the verifying modes; a relative path is read from the data folder. Null when none is given. */
    private static String parseServerCert(String text, SslMode mode) {
        if (text.isBlank() || (mode != SslMode.VERIFY_CA && mode != SslMode.VERIFY_FULL)) {
            return null;
        }
        Path path = Path.of(text);
        if (!path.isAbsolute()) {
            path = DataPaths.dataDir().resolve(path);
        }
        path = path.toAbsolutePath().normalize();
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException(I18n.t("db.sslcert.missing", path));
        }
        return path.toString();
    }

    private static int parseSocketTimeout(String text) {
        try {
            int millis = Integer.parseInt(text.trim());
            if (millis == 0 || (millis >= 1000 && millis <= MAX_SOCKET_TIMEOUT_MILLIS)) {
                return millis;
            }
        } catch (NumberFormatException ignored) {
            // falls through to the message
        }
        throw new IllegalArgumentException(I18n.t("db.sockettimeout.invalid", text, MAX_SOCKET_TIMEOUT_MILLIS));
    }

    /**
     * The password is used exactly as written (TODO 2.82): a space at either end is part of it, and nothing is trimmed. Only a
     * value that is nothing but spaces counts as "no password" (XAMPP's {@code root} has none, and a stray space after the
     * {@code =} must not turn that into a password of one space).
     */
    private static String readPassword(Properties file, Function<String, String> overrides) {
        String override = overrides.apply("lunavera.db.password");
        if (override != null && !override.isBlank()) {
            return override;
        }
        String value = file.getProperty("db.password");
        return value == null || value.isBlank() ? "" : value;
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
            throw new IllegalArgumentException(I18n.t("db.port.invalid", text));
        }
    }

    /** Only http:// and https:// addresses: the button must never be able to open a file or another kind of link. */
    private static String webAddress(String text) {
        String lower = text.toLowerCase(java.util.Locale.ROOT);
        if (!(lower.startsWith("http://") || lower.startsWith("https://")) || text.length() > 300 || text.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException(I18n.t("db.phpmyadmin.invalid", text));
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
        StringBuilder url = new StringBuilder("jdbc:mariadb://").append(hostForUrl()).append(':').append(port).append('/')
                .append(database).append("?connectTimeout=5000&socketTimeout=").append(socketTimeoutMillis)
                .append("&sslMode=").append(sslMode.value());
        if (sslServerCert != null) {
            url.append("&serverSslCert=").append(encodeForUrl(sslServerCert));
        }
        return url.toString();
    }

    /** An IPv6 address needs square brackets inside a JDBC URL ({@code [::1]}); a name or an IPv4 address does not. */
    String hostForUrl() {
        return host.indexOf(':') >= 0 ? "[" + host + "]" : host;
    }

    /** A file path inside a URL: forward slashes (Windows accepts them) and the few characters that would end the value escaped. */
    static String encodeForUrl(String path) {
        StringBuilder out = new StringBuilder();
        for (char c : path.replace('\\', '/').toCharArray()) {
            switch (c) {
                case ' ' -> out.append("%20");
                case '%' -> out.append("%25");
                case '&' -> out.append("%26");
                case '=' -> out.append("%3D");
                case '#' -> out.append("%23");
                case '?' -> out.append("%3F");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    /** True for a server on this computer (localhost, 127.x.x.x, ::1): nothing leaves the machine, so TLS is not needed. */
    public boolean isLoopbackHost() {
        String h = host.toLowerCase(java.util.Locale.ROOT);
        return h.equals("localhost") || h.startsWith("127.") || h.equals("::1") || h.equals("0:0:0:0:0:0:0:1");
    }

    /** True when the connection to the database is encrypted. */
    public boolean usesTls() {
        return isMariaDb() && sslMode != SslMode.DISABLE;
    }

    /**
     * The sentence for the log when the database is on another computer and nothing encrypts the connection; null when there is
     * nothing to warn about (SQLite, a server on this computer, or TLS in use).
     */
    public String insecureRemoteWarning() {
        if (!isMariaDb() || isLoopbackHost() || usesTls()) {
            return null;
        }
        return "The connection to the database server " + host + ":" + port + " is NOT encrypted: customer names, phone numbers, "
                + "password hashes and the database password cross the network in clear text. Set db.ssl=true in "
                + FILE_NAME + " (see docs/DATABASE.md section 7).";
    }

    /** A few words about how the connection is protected, for the line on the login screen; null when there is nothing to add. */
    public String connectionSecurityNote() {
        if (!isMariaDb()) {
            return null;
        }
        if (sslMode == SslMode.TRUST) {
            return I18n.t("db.security.tls.unverified");
        }
        if (usesTls()) {
            return I18n.t("db.security.tls");
        }
        return isLoopbackHost() ? null : I18n.t("db.security.none");
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
