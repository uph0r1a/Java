package com.coffeeshop.coffeeshopmanagement.service;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** What the database file inside a backup zip is called, and what a MariaDB shop is told about restoring. */
public class BackupEntryNameTest {

    @Test
    public void sqliteBackupsKeepTheirOldEntryName() {
        assertEquals("lunavera.db", BackupService.databaseEntryName(true));
    }

    @Test
    public void mariaDbBackupsHoldAnSqlDumpPhpMyAdminCanImport() {
        assertEquals("lunavera.sql", BackupService.databaseEntryName(false));
    }

    @Test
    public void theMariaDbRestoreHelpPointsToPhpMyAdminAndTheImages() {
        String help = BackupService.MARIADB_RESTORE_HELP;
        assertTrue(help, help.contains("phpMyAdmin"));
        assertTrue(help, help.contains("lunavera.sql"));
        assertTrue(help, help.contains("images"));
    }
}
