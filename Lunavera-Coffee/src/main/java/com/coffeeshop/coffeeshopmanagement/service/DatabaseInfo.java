package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig;

/**
 * What a screen may ask about the database it runs on (TODO 6.21, 6.82): which kind it is, where it is, the address of its web tool and
 * whether a password is the factory one. Four screens used to import {@code DatabaseConfig} for these; now none of them does, and
 * a test keeps it that way. Nothing here opens a connection.
 */
public final class DatabaseInfo {

    private DatabaseInfo() {
    }

    /** True when the shop runs on the local SQLite file, false on a MariaDB/MySQL server. */
    public static boolean isSqlite() {
        return DatabaseConfig.isSqlite();
    }

    /** A line saying where the data lives (the folder, or the server), for the login screen. */
    public static String locationSummary() {
        return DatabaseConfig.locationSummary();
    }

    /** The address of phpMyAdmin for a server database, or null when there is none to offer. */
    public static String phpMyAdminUrl() {
        return DatabaseConfig.phpMyAdminUrl();
    }

    /** True when {@code password} is the one the first administrator account is created with. */
    public static boolean isFactoryAdminPassword(String password) {
        return DatabaseConfig.DEFAULT_ADMIN_PASSWORD.equals(password);
    }
}
