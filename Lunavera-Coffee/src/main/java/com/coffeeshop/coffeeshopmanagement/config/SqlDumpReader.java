package com.coffeeshop.coffeeshopmanagement.config;

import com.coffeeshop.coffeeshopmanagement.util.I18n;

import java.io.IOException;
import java.io.Reader;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the text file that {@code mysqldump} writes (the {@code lunavera.sql} inside a MariaDB backup) one statement at a time
 * (TODO 1.8). It is a reader, not a database: it only cuts the text into statements and says what kind each one is.
 *
 * <p>The in-app restore runs a file the app did not check before, so only the kinds of statement a dump of this app's tables
 * holds are accepted (see {@link Kind}); anything else (a {@code DROP DATABASE}, a {@code GRANT}, a {@code DELIMITER} line, a
 * trigger, an {@code INSERT ... SELECT}) stops the read with a plain message, and nothing has been run by then that could harm.
 * Comments are dropped, including the {@code /*!40101 ... *}{@code /} comments that carry session settings: the restore sets what
 * it needs itself.
 *
 * <p>The text is read as a stream, so a dump of hundreds of megabytes is never held in memory; only one statement is.
 */
final class SqlDumpReader {

    /** What a statement is. {@link #CREATE_TABLE} and {@link #INSERT} are run; the others are only recognised and skipped. */
    enum Kind {
        /** {@code CREATE TABLE `t` (...) ...}: run, so the scratch database gets the structure the backup had. */
        CREATE_TABLE,
        /** {@code INSERT INTO `t` [(columns)] VALUES (...),(...)}: run. */
        INSERT,
        /** {@code DROP TABLE IF EXISTS `t`}: skipped (the scratch database starts empty; a live table is never dropped). */
        DROP_TABLE,
        /** {@code LOCK TABLES}, {@code UNLOCK TABLES}, a plain {@code SET}, {@code START TRANSACTION} and {@code COMMIT} (newer {@code mariadb-dump} writes them around the data): skipped. */
        SESSION
    }

    /** One statement without its closing semicolon; {@code table} is empty for {@link Kind#SESSION}. */
    record Statement(Kind kind, String table, String sql) {
    }

    /** What is done with each statement; may throw (the reader passes it on). */
    @FunctionalInterface
    interface Sink {
        void accept(Statement statement) throws Exception;
    }

    /** The reader met something it does not accept or the text ends in the middle of a statement. */
    static final class UnsupportedDumpException extends IOException {
        UnsupportedDumpException(String message) {
            super(message);
        }
    }

    private static final Pattern IDENT = Pattern.compile("`([A-Za-z0-9_$]{1,64})`");
    private static final Pattern DROP = Pattern.compile("(?is)^DROP\\s+TABLE\\s+IF\\s+EXISTS\\s+" + IDENT.pattern() + "\\s*$");
    private static final Pattern CREATE = Pattern.compile("(?is)^CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?" + IDENT.pattern() + "\\s*\\(.*");
    private static final Pattern INSERT = Pattern.compile("(?is)^INSERT\\s+INTO\\s+" + IDENT.pattern() + "\\s*(?:\\([^)]*\\)\\s*)?VALUES\\s*\\(.*");
    private static final Pattern LOCK = Pattern.compile("(?is)^LOCK\\s+TABLES\\s+" + IDENT.pattern() + "\\s+WRITE\\s*$");
    private static final Pattern UNLOCK = Pattern.compile("(?is)^UNLOCK\\s+TABLES\\s*$");
    /** {@code mariadb-dump} 11 and later wraps the rows in a transaction; the reader runs only the tables and rows, so these are skipped like {@code SET}. */
    private static final Pattern TRANSACTION = Pattern.compile("(?is)^(?:START\\s+TRANSACTION|COMMIT)\\s*$");
    private static final Pattern PLAIN_SET = Pattern.compile("(?is)^SET\\s+[@A-Za-z_].*");

    private SqlDumpReader() {
    }

    /**
     * Cuts {@code reader} into statements and hands each to {@code sink}, in file order.
     *
     * @throws UnsupportedDumpException for a statement that is not one of the accepted kinds, or text that stops before a closing semicolon
     * @throws Exception                whatever {@code sink} throws
     */
    static void read(Reader reader, Sink sink) throws Exception {
        Source in = new Source(reader);
        StringBuilder text = new StringBuilder();
        int c;
        while ((c = in.read()) != -1) {
            char ch = (char) c;
            switch (ch) {
                case '\'', '"', '`' -> copyQuoted(in, text, ch);
                case '-' -> {
                    // "-- " (a space, tab or the end of the line after the two dashes) starts a comment; "a--b" does not
                    if (in.peek() == '-') {
                        in.read(); // the second dash
                        if (blankOrEndFollows(in)) {
                            skipLine(in);
                            text.append(' ');
                        } else {
                            text.append("--");
                        }
                    } else {
                        text.append(ch);
                    }
                }
                case '#' -> {
                    skipLine(in);
                    text.append(' ');
                }
                case '/' -> {
                    if (in.peek() == '*') {
                        in.read();
                        skipBlockComment(in);
                        text.append(' ');
                    } else {
                        text.append(ch);
                    }
                }
                case ';' -> {
                    emit(text.toString(), sink);
                    text.setLength(0);
                }
                default -> text.append(ch);
            }
        }
        if (!text.toString().isBlank()) {
            throw new UnsupportedDumpException(I18n.t("backup.restore.sql.cut"));
        }
    }

    /** Classifies one statement text (no semicolon); an empty text (only comments were there) is skipped. */
    static Statement classify(String raw) throws UnsupportedDumpException {
        String sql = raw.strip();
        if (sql.isEmpty()) {
            return null;
        }
        Matcher m = DROP.matcher(sql);
        if (m.matches()) {
            return new Statement(Kind.DROP_TABLE, m.group(1), sql);
        }
        m = CREATE.matcher(sql);
        if (m.matches()) {
            return new Statement(Kind.CREATE_TABLE, m.group(1), sql);
        }
        m = INSERT.matcher(sql);
        if (m.matches()) {
            return new Statement(Kind.INSERT, m.group(1), sql);
        }
        if (LOCK.matcher(sql).matches() || UNLOCK.matcher(sql).matches() || PLAIN_SET.matcher(sql).matches()
                || TRANSACTION.matcher(sql).matches()) {
            return new Statement(Kind.SESSION, "", sql);
        }
        throw new UnsupportedDumpException(I18n.t("backup.restore.sql.unsupported", firstWords(sql)));
    }

    private static void emit(String raw, Sink sink) throws Exception {
        Statement statement = classify(raw);
        if (statement != null) {
            sink.accept(statement);
        }
    }

    /** The first word or two of a statement, for the message: never the whole text (it can be a long list of customer rows). */
    static String firstWords(String sql) {
        String[] words = sql.strip().split("\\s+", 3);
        String shown = words.length >= 2 ? words[0] + " " + words[1] : words[0];
        shown = shown.toUpperCase(Locale.ROOT);
        return shown.length() > 30 ? shown.substring(0, 30) : shown;
    }

    // ---- the character level

    /** Copies a quoted text or identifier as it is, including its quotes: a semicolon inside it ends nothing. */
    private static void copyQuoted(Source in, StringBuilder out, char quote) throws IOException {
        out.append(quote);
        int c;
        while ((c = in.read()) != -1) {
            char ch = (char) c;
            out.append(ch);
            if (ch == '\\' && quote != '`') {
                int escaped = in.read(); // \' \\ \n \0 ...: the next character belongs to the text whatever it is
                if (escaped != -1) {
                    out.append((char) escaped);
                }
            } else if (ch == quote) {
                if (in.peek() == quote) {
                    out.append((char) in.read()); // a doubled quote is a quote inside the text
                } else {
                    return;
                }
            }
        }
        throw new UnsupportedDumpException(I18n.t("backup.restore.sql.cut"));
    }

    /** After "--": it is a comment only when a blank (or the end of the text) follows. */
    private static boolean blankOrEndFollows(Source in) throws IOException {
        int after = in.peek();
        return after == -1 || after == ' ' || after == '\t' || after == '\r' || after == '\n';
    }

    private static void skipLine(Source in) throws IOException {
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') {
                return;
            }
        }
    }

    private static void skipBlockComment(Source in) throws IOException {
        int c;
        while ((c = in.read()) != -1) {
            if (c == '*' && in.peek() == '/') {
                in.read();
                return;
            }
        }
        throw new UnsupportedDumpException(I18n.t("backup.restore.sql.cut"));
    }

    /** A reader with one character of look-ahead. */
    private static final class Source {
        private final Reader reader;
        private int ahead = -2; // -2: nothing looked at yet

        Source(Reader reader) {
            this.reader = reader;
        }

        int read() throws IOException {
            if (ahead != -2) {
                int c = ahead;
                ahead = -2;
                return c;
            }
            return reader.read();
        }

        int peek() throws IOException {
            if (ahead == -2) {
                ahead = reader.read();
            }
            return ahead;
        }
    }
}
