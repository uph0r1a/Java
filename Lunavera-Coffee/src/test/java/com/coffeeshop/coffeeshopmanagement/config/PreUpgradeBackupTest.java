package com.coffeeshop.coffeeshopmanagement.config;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** TODO 6.9: the copy taken before a numbered upgrade step. */
public class PreUpgradeBackupTest {

    @Test
    public void theFileNameSaysTheVersionsAndTheTime() {
        LocalDateTime at = LocalDateTime.of(2026, 11, 6, 10, 55, 0);
        assertEquals("pre-upgrade-v3-to-v4-20261106-105500.db", PreUpgradeBackup.fileName(3, 4, at, false));
        assertEquals("pre-upgrade-v3-to-v4-20261106-105500.sql", PreUpgradeBackup.fileName(3, 4, at, true));
    }

    @Test
    public void bothRunnersTakeTheCopyBeforeTheFirstStep() throws Exception {
        for (String name : new String[]{"SchemaMigrations", "MariaDbMigrations"}) {
            String source = Files.readString(Path.of("src/main/java/com/coffeeshop/coffeeshopmanagement/config/" + name + ".java"));
            int backup = source.indexOf("PreUpgradeBackup.takeIfNeeded");
            int firstStep = Math.max(source.indexOf("STEPS.get(target).apply"), source.indexOf("STEPS.get(version).apply"));
            assertTrue(name + " has no backup call", backup > 0);
            assertTrue(name + ": the backup must come before the steps run", backup < firstStep);
        }
    }
}
