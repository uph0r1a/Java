package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.config.SchemaMigrations;
import com.coffeeshop.coffeeshopmanagement.util.AppVersion;
import org.junit.Test;

import java.time.LocalDateTime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** TODO 5.41: every backup says which program version and table layout wrote it. */
public class BackupInfoVersionTest {

    private static final LocalDateTime AT = LocalDateTime.of(2026, 11, 7, 9, 0);

    @Test
    public void theInfoNamesTheProgramAndTheTableLayout() {
        String text = BackupService.infoText("lunavera.db", true, AT);
        assertTrue(text, text.startsWith("Lunavera Coffee backup\ncreated: 2026-11-07T09:00\n"));
        assertTrue(text, text.contains("\nprogram: " + AppVersion.version() + "\n"));
        assertTrue(text, text.contains("\nschema: " + SchemaMigrations.LATEST_VERSION + "\n"));
        assertTrue(text, text.contains("database: SQLite (lunavera.db)"));
        assertFalse("pictures are in", text.contains("pictures: not included"));
    }

    @Test
    public void aBackupWithoutPicturesStillSaysSoAndNamesAMariaDbDump() {
        String text = BackupService.infoText("lunavera.sql", false, AT);
        assertTrue(text, text.contains("pictures: not included"));
        assertTrue(text, text.contains("MariaDB/MySQL dump (lunavera.sql)"));
        assertEquals("one entry, a few lines", 6, text.split("\n").length);
    }
}
