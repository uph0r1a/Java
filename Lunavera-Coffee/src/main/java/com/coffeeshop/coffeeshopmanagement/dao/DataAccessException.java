package com.coffeeshop.coffeeshopmanagement.dao;

import java.sql.SQLException;
import java.util.Locale;

/**
 * Wraps low-level SQLException in an unchecked exception so controllers can catch one
 * meaningful type and show the user a friendly alert, instead of a raw SQL stack trace.
 *
 * <p>When the cause is a problem the person at the till can act on - the database server cannot be reached, the login for
 * it is wrong, or the database is busy - the message starts with a plain Vietnamese sentence saying so, followed by the
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
                    return "Sai tài khoản hoặc mật khẩu của cơ sở dữ liệu. Hãy kiểm tra db.user và db.password trong data/database.properties.";
                }
                if (state.startsWith("08") || text.contains("communications link failure") || text.contains("connection refused")
                        || text.contains("could not connect") || text.contains("connection is closed")
                        || text.contains("socket fail") || text.contains("connection reset")) {
                    return "Không kết nối được máy chủ cơ sở dữ liệu (MariaDB/MySQL). Hãy kiểm tra máy chủ đã chạy chưa và mạng còn nối không, rồi thử lại.";
                }
                if (sql.getErrorCode() == 1205 || sql.getErrorCode() == 1213 || text.contains("database is locked")
                        || text.contains("sqlite_busy") || text.contains("lock wait timeout") || text.contains("deadlock")) {
                    return "Cơ sở dữ liệu đang bận (có máy khác đang ghi). Hãy thử lại sau giây lát.";
                }
            }
        }
        return null;
    }

    private static String withHint(String message, Throwable cause) {
        String hint = hintFor(cause);
        return hint == null ? message : hint + " (" + message + ")";
    }
}
