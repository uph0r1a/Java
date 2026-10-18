package com.coffeeshop.coffeeshopmanagement.dao;

import org.sqlite.SQLiteException;

import java.sql.SQLException;

/**
 * Tells "this value already exists" apart from every other database error by the driver's error <em>code</em>, not by
 * the wording of its message (TODO 6.36, 6.40). A driver upgrade or a different server language can change a message
 * without notice; the codes are part of SQLite's and MariaDB's documented interface.
 *
 * <ul>
 *   <li><b>MariaDB / MySQL:</b> vendor error 1062 ({@code ER_DUP_ENTRY}).</li>
 *   <li><b>SQLite:</b> the extended result code {@code SQLITE_CONSTRAINT_UNIQUE} (2067) or
 *       {@code SQLITE_CONSTRAINT_PRIMARYKEY} (1555), read from {@link SQLiteException#getResultCode()}.</li>
 * </ul>
 *
 * The caller still decides <em>which</em> rule was broken. That is safe in this schema because each table that uses
 * this class has exactly one unique rule besides its generated id: categories (name), customers (phone), employees
 * (phone). A table with two unique rules would need the index name too.
 */
public final class UniqueViolation {

    static final int MARIADB_DUPLICATE_ENTRY = 1062;
    static final int SQLITE_CONSTRAINT = 19;
    static final int SQLITE_CONSTRAINT_PRIMARYKEY = 1555;
    static final int SQLITE_CONSTRAINT_UNIQUE = 2067;

    private UniqueViolation() {
    }

    public static boolean isUnique(SQLException e) {
        if (e == null) {
            return false;
        }
        if (e instanceof SQLiteException sqlite) {
            return isSqliteUnique(sqlite.getResultCode() == null ? 0 : sqlite.getResultCode().code, e.getMessage());
        }
        return e.getErrorCode() == MARIADB_DUPLICATE_ENTRY;
    }

    /**
     * Split out so the rule can be tested without a database. The message is read only when the driver reports the plain
     * code 19 ("some constraint failed") with no detail, which an older driver or a build without extended result codes
     * does; SQLite has always worded a unique failure "UNIQUE constraint failed: table.column".
     */
    static boolean isSqliteUnique(int resultCode, String message) {
        if (resultCode == SQLITE_CONSTRAINT_UNIQUE || resultCode == SQLITE_CONSTRAINT_PRIMARYKEY) {
            return true;
        }
        return resultCode == SQLITE_CONSTRAINT && message != null && message.contains("UNIQUE constraint failed");
    }
}
