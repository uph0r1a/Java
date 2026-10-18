package com.coffeeshop.coffeeshopmanagement.config;

import org.junit.Test;

import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class MariaDbMigrationsTest {

    @Test
    public void aDatabaseAlreadyAtTheLatestVersionRunsNothing() {
        assertEquals(List.of(), MariaDbMigrations.plan(2, 2, Set.of()));
        assertEquals(List.of(), MariaDbMigrations.plan(3, 3, Set.of(2, 3)));
    }

    @Test
    public void missingStepsRunInOrderAndOnlyTheMissingOnes() {
        assertEquals(List.of(3, 4), MariaDbMigrations.plan(2, 4, Set.of(4, 3, 2)));
        assertEquals(List.of(4), MariaDbMigrations.plan(3, 4, Set.of(2, 3, 4)));
    }

    @Test
    public void aVersionWithoutAStepIsSkippedNotInvented() {
        assertEquals(List.of(4), MariaDbMigrations.plan(2, 4, Set.of(4)));
        assertEquals(List.of(), MariaDbMigrations.plan(1, 3, Set.of()));
    }

    @Test
    public void stepsBeyondTheLatestVersionAreIgnored() {
        assertEquals(List.of(3), MariaDbMigrations.plan(2, 3, Set.of(3, 9)));
    }

    @Test
    public void aDatabaseNewerThanTheAppIsRefusedWithAnUpdateMessage() {
        try {
            MariaDbMigrations.plan(5, 2, Set.of());
            fail("an older app must not touch newer tables");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("cập nhật ứng dụng"));
        }
    }

    @Test
    public void theLatestVersionIsTheSameNumberTheSqliteSideReaches() {
        assertEquals(SchemaMigrations.LATEST_VERSION, MariaDbMigrations.LATEST_VERSION);
        assertTrue("there is no step for a version that does not exist yet",
                MariaDbMigrations.STEPS.keySet().stream().allMatch(v -> v <= MariaDbMigrations.LATEST_VERSION));
    }
}
