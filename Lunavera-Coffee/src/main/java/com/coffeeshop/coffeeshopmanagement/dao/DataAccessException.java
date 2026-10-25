package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.util.I18n;

import java.sql.SQLException;
import java.util.Locale;

/**
 * Wraps low-level SQLException in an unchecked exception so controllers can catch one
 * meaningful type and show the user a friendly alert, instead of a raw SQL stack trace.
 *
 * <p>When the cause is a problem the person at the till can act on - the database server cannot be reached, the login for
 * it is wrong, the database is busy, or the disk is full - the message starts with a plain Vietnamese sentence saying so, followed by the
 * original technical message in brackets. Any other cause keeps the message exactly as given.
 */
public class DataAccessException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public DataAccessException(String message, Throwable cause) {
        super(withHint(message, cause), cause);
    }

    public DataAccessException(String message) {
        super(message);
    }

    /** A short Vietnamese explanation for a connection/login/busy failure found anywhere in the cause chain, else null. */
    static String hintFor(Throwable cause) {
        for (Throwable t = cause; t != null; t = t.getCause()) {
            String text = t.getMessage() == null ? "" : t.getMessage().toLowerCase(Locale.ROOT);
            if (t instanceof SQLException sql) {
                String state = sql.getSQLState() == null ? "" : sql.getSQLState();
                if (sql.getErrorCode() == 1045 || state.equals("28000") || text.contains("access denied")) {
                    return I18n.t("db.error.credentials");
                }
                if (state.startsWith("08") || text.contains("communications link failure") || text.contains("connection refused")
                        || text.contains("could not connect") || text.contains("connection is closed")
                        || text.contains("socket fail") || text.contains("connection reset")) {
                    return I18n.t("db.error.connect");
                }
                if (sql.getErrorCode() == 1205 || sql.getErrorCode() == 1213 || text.contains("database is locked")
                        || text.contains("sqlite_busy") || text.contains("lock wait timeout") || text.contains("deadlock")) {
                    return I18n.t("db.error.busy");
                }
                if (isDiskProblem(sql, text)) {
                    return diskFullHint();
                }
            }
        }
        return null;
    }

    /** TODO 2.78: shown when a write fails because the disk is full or cannot be written; nothing was saved. */
    static String diskFullHint() {
        return I18n.t("db.error.diskfull");
    }

    private static final int SQLITE_IOERR = 10;
    private static final int SQLITE_FULL = 13;

    /**
     * A write that failed because there is no room (TODO 2.78). SQLite: the result code {@code SQLITE_FULL} or any
     * {@code SQLITE_IOERR} (the extended codes keep these in their low byte); MariaDB/MySQL: error 1021 (disk full), 1114
     * (table full) or the engine's "Got error 28" (no space left); and the operating system's own words as a fallback.
     */
    private static boolean isDiskProblem(SQLException sql, String lowerCaseText) {
        if (sql instanceof org.sqlite.SQLiteException sqlite && sqlite.getResultCode() != null) {
            int primary = sqlite.getResultCode().code & 0xff;
            if (primary == SQLITE_FULL || primary == SQLITE_IOERR) {
                return true;
            }
        }
        int code = sql.getErrorCode();
        if (code == 1021 || code == 1114) {
            return true;
        }
        return lowerCaseText.contains("sqlite_full") || lowerCaseText.contains("sqlite_ioerr")
                || lowerCaseText.contains("database or disk is full") || lowerCaseText.contains("disk i/o error")
                || lowerCaseText.contains("no space left") || lowerCaseText.contains("not enough space on the disk")
                || lowerCaseText.contains("got error 28");
    }

    private static String withHint(String message, Throwable cause) {
        String hint = hintFor(cause);
        return hint == null ? message : hint + " (" + message + ")";
    }
}
