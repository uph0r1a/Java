package com.coffeeshop.coffeeshopmanagement.config;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** The scratch session of the MariaDB restore loads a dump that holds the value of a generated column, so it must not be strict. */
public class ScratchSqlModeTest {

    @Test
    public void theStrictModesAreLeftOutAndTheRestStays() {
        assertEquals("ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION,NO_AUTO_VALUE_ON_ZERO",
                MariaDbRestore.scratchSqlMode("STRICT_TRANS_TABLES,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION"));
        assertEquals("NO_AUTO_VALUE_ON_ZERO", MariaDbRestore.scratchSqlMode("STRICT_ALL_TABLES,STRICT_TRANS_TABLES"));
    }

    @Test
    public void anEmptyOrMissingModeStillGetsTheIdRule() {
        assertEquals("NO_AUTO_VALUE_ON_ZERO", MariaDbRestore.scratchSqlMode(""));
        assertEquals("NO_AUTO_VALUE_ON_ZERO", MariaDbRestore.scratchSqlMode(null));
    }

    @Test
    public void theIdRuleIsNeverWrittenTwice() {
        assertEquals("ANSI_QUOTES,NO_AUTO_VALUE_ON_ZERO", MariaDbRestore.scratchSqlMode("ANSI_QUOTES,NO_AUTO_VALUE_ON_ZERO"));
    }
}
