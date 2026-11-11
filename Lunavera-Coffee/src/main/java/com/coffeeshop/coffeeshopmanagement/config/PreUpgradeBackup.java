package com.coffeeshop.coffeeshopmanagement.config;

import com.coffeeshop.coffeeshopmanagement.util.DataPaths;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A copy of the database taken automatically just before a numbered upgrade step changes an existing database (TODO 6.9), so a
 * new version never changes the shop's data without a way back. It is in addition to the checklist in {@code docs/RELEASE.md}
 * (a backup saved off the computer), not instead of it. A new, empty database has nothing to lose and gets none. A failure to
 * copy is logged and never stops the upgrade: a database left on old tables is worse than an upgrade without the extra copy.
 */
final class PreUpgradeBackup {

    private static final Logger LOGGER = Logger.getLogger(PreUpgradeBackup.class.getName());
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    static final String PREFIX = "pre-upgrade-";

    private PreUpgradeBackup() {
    }

    /** {@code pre-upgrade-v3-to-v4-20261106-105500.db} (SQLite) or {@code .sql} (MariaDB dump). */
    static String fileName(int from, int to, LocalDateTime at, boolean mariaDb) {
        return PREFIX + "v" + from + "-to-v" + to + "-" + STAMP.format(at) + (mariaDb ? ".sql" : ".db");
    }

    /** True when the database holds anything worth keeping: at least one account (a new file has none until the first start). */
    static boolean hasData(Connection connection) {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT 1 FROM users LIMIT 1")) {
            return rs.next();
        } catch (SQLException e) {
            return false; // no users table: nothing made yet
        }
    }

    /** Copies the database into the backups folder when it has data; returns the file, or null when none was made. */
    static Path takeIfNeeded(Connection connection, int from, int to) {
        if (!hasData(connection)) {
            return null;
        }
        try {
            Path dir = DataPaths.backupsDir();
            Files.createDirectories(dir);
            Path target = dir.resolve(fileName(from, to, com.coffeeshop.coffeeshopmanagement.util.AppClock.now(), !DatabaseConfig.isSqlite()));
            DatabaseConfig.backupTo(target);
            LOGGER.info("Backed up the database before upgrading it from version " + from + " to " + to + ": " + target);
            return target;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Could not make the backup before the upgrade; going on without it", e);
            return null;
        }
    }
}
