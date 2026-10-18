package com.coffeeshop.coffeeshopmanagement.config;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;

/**
 * Numbered, one-way upgrades for an existing MariaDB/MySQL database, driven by the {@code schema_version} table.
 *
 * <p>A <b>new</b> MariaDB database is created by {@link MariaDbSchema} already at {@link #LATEST_VERSION}, so it runs none
 * of these. When the tables change later: (1) change {@link MariaDbSchema} (and the SQLite side; a test compares the
 * columns), (2) raise {@link SchemaMigrations#LATEST_VERSION}, (3) add a step here under that new number that applies
 * the same change to a database created earlier (for example {@code DatabaseConfig.addColumnIfMissing}). The runner
 * applies the missing steps in order, writing the version after each one, so a failure half way resumes from the right place.
 */
final class MariaDbMigrations {

    private static final Logger LOGGER = Logger.getLogger(MariaDbMigrations.class.getName());

    /** One upgrade step: brings the database from the previous version to the one it is registered under. */
    @FunctionalInterface
    interface Step {
        void apply(Connection connection) throws SQLException;
    }

    static final int LATEST_VERSION = SchemaMigrations.LATEST_VERSION;

    /** Version -> step. Empty today: version 2 is the first MariaDB schema. */
    static final Map<Integer, Step> STEPS = new TreeMap<>();

    private MariaDbMigrations() {
    }

    /**
     * Which step numbers must run to go from {@code current} to {@code latest}, in order.
     *
     * @throws IllegalStateException if the database is newer than this app: running an older app on newer tables could
     *         damage them, so the app refuses to start and asks for the update
     */
    static List<Integer> plan(int current, int latest, Collection<Integer> stepVersions) {
        if (current > latest) {
            throw new IllegalStateException("Cơ sở dữ liệu thuộc phiên bản mới hơn (" + current + ") so với ứng dụng này ("
                    + latest + "). Hãy cập nhật ứng dụng lên phiên bản mới nhất rồi mở lại.");
        }
        List<Integer> toRun = new ArrayList<>();
        for (int version : new java.util.TreeSet<>(stepVersions)) {
            if (version > current && version <= latest) {
                toRun.add(version);
            }
        }
        return toRun;
    }

    /** Brings an existing MariaDB database up to {@link #LATEST_VERSION}; a no-op when it already is. */
    static void upgrade(Connection connection) throws SQLException {
        int current = DatabaseConfig.readSchemaVersion(connection);
        for (int version : plan(current, LATEST_VERSION, STEPS.keySet())) {
            LOGGER.info("Upgrading the MariaDB database to version " + version + " ...");
            STEPS.get(version).apply(connection);
            DatabaseConfig.writeSchemaVersion(connection, version);
        }
        if (current < LATEST_VERSION) {
            DatabaseConfig.writeSchemaVersion(connection, LATEST_VERSION); // also covers versions that need no step
        }
    }
}
