package com.coffeeshop.coffeeshopmanagement.service;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/** TODO 4.18: what a table name and a seat count must look like. */
public class TableRulesTest {

    @Test
    public void aNormalNameIsAccepted() {
        assertNull(TableRules.nameProblem("Garden 1", List.of("Table 1", "Table 2")));
    }

    @Test
    public void aBlankNameIsRefused() {
        assertEquals("tablemgr.val.name.required", TableRules.nameProblem("   ", List.of()));
        assertEquals("tablemgr.val.name.required", TableRules.nameProblem(null, List.of()));
    }

    @Test
    public void aTooLongNameIsRefused() {
        assertNull(TableRules.nameProblem("x".repeat(TableRules.MAX_NAME), List.of()));
        assertEquals("tablemgr.val.name.long", TableRules.nameProblem("x".repeat(TableRules.MAX_NAME + 1), List.of()));
    }

    @Test
    public void aNameAnotherTableHasIsRefusedIgnoringCaseAndSpaces() {
        assertEquals("tablemgr.val.name.duplicate", TableRules.nameProblem("  table   1 ", List.of("Table 1")));
        assertEquals("tablemgr.val.name.duplicate", TableRules.nameProblem("SÂN VƯỜN", List.of("sân vườn")));
    }

    @Test
    public void capacityMustBeAWholeNumberFromOneToNinetyNine() {
        assertEquals(Integer.valueOf(4), TableRules.parseCapacity(" 4 "));
        assertEquals(Integer.valueOf(99), TableRules.parseCapacity("99"));
        assertNull(TableRules.parseCapacity("0"));
        assertNull(TableRules.parseCapacity("100"));
        assertNull(TableRules.parseCapacity("-2"));
        assertNull(TableRules.parseCapacity("2.5"));
        assertNull(TableRules.parseCapacity(""));
        assertNull(TableRules.parseCapacity(null));
        assertNull(TableRules.parseCapacity("4a"));
        assertNull(TableRules.parseCapacity("0004"));
        assertEquals("tablemgr.val.capacity", TableRules.capacityProblem("abc"));
        assertNull(TableRules.capacityProblem("12"));
    }

    @Test
    public void aNewNumberIsAboveEveryNumberATableOrAnOrderEverUsed() {
        assertEquals(13, TableRules.nextNumber(12, 12));
        assertEquals(1, TableRules.nextNumber(0, 0));
        // table 13 was removed but old orders still carry 13: it must not be given again
        assertEquals(14, TableRules.nextNumber(12, 13));
        assertEquals(8, TableRules.nextNumber(7, 3));
    }

    @Test
    public void aStarterTableAnswersToItsNameInBothLanguages() {
        java.util.List<String> names = TableRules.namesOf(5, "B\u00e0n 5");
        assertTrue(names.contains("B\u00e0n 5"));
        assertTrue(names.contains("Table 5"));
        assertNull(TableRules.nameProblem("Sân vườn", names));
        assertNotNull(TableRules.nameProblem("table 5", names));
        assertNotNull(TableRules.nameProblem("Ba\u0300n 5", names)); // the same word with the accent written as a separate mark
        // a table the shop named itself is only known by that name
        assertEquals(java.util.List.of("Sân vườn 1"), TableRules.namesOf(7, "Sân vườn 1"));
    }

    @Test
    public void typingNothingNewKeepsTheStoredNameAndTypingSomethingElseReplacesIt() {
        assertEquals("B\u00e0n 5", TableRules.nameToStore("Table 5", "B\u00e0n 5", "Table 5"));
        assertEquals("B\u00e0n 5", TableRules.nameToStore("  table 5 ", "B\u00e0n 5", "Table 5"));
        assertEquals("Sân vườn", TableRules.nameToStore("Sân vườn", "B\u00e0n 5", "Table 5"));
        assertEquals("Sân vườn 1", TableRules.nameToStore("Sân vườn 1", "Sân vườn 1", "Sân vườn 1"));
        assertEquals("", TableRules.nameToStore("   ", "B\u00e0n 5", "Table 5"));
    }
}
