package com.coffeeshop.coffeeshopmanagement.service;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/** TODO 4.15: the number of tables asked for in the first-time setup. */
public class SetupPlanTest {

    @Test
    public void theTableCountMustBeAWholeNumberFromOneToTheLimit() {
        assertEquals(Integer.valueOf(1), SetupPlan.parseTableCount("1"));
        assertEquals(Integer.valueOf(12), SetupPlan.parseTableCount(" 12 "));
        assertEquals(Integer.valueOf(TableRules.MAX_TABLES), SetupPlan.parseTableCount(String.valueOf(TableRules.MAX_TABLES)));
        assertNull(SetupPlan.parseTableCount("0"));
        assertNull(SetupPlan.parseTableCount(String.valueOf(TableRules.MAX_TABLES + 1)));
        assertNull(SetupPlan.parseTableCount("-3"));
        assertNull(SetupPlan.parseTableCount("2.5"));
        assertNull(SetupPlan.parseTableCount("abc"));
        assertNull(SetupPlan.parseTableCount(""));
        assertNull(SetupPlan.parseTableCount(null));
        assertNull("a number too long to be sensible is refused before it is parsed", SetupPlan.parseTableCount("99999999999"));
    }

    @Test
    public void moreTablesAreAddedAndNoneRemoved() {
        SetupPlan.TablePlan plan = SetupPlan.planTables(List.of(1, 2, 3), 5);
        assertEquals(2, plan.toAdd());
        assertTrue(plan.toRemove().isEmpty());
    }

    @Test
    public void theSameNumberChangesNothing() {
        SetupPlan.TablePlan plan = SetupPlan.planTables(List.of(1, 2, 3), 3);
        assertEquals(0, plan.toAdd());
        assertTrue(plan.toRemove().isEmpty());
    }

    @Test
    public void fewerTablesRemoveTheHighestNumbersFirst() {
        SetupPlan.TablePlan plan = SetupPlan.planTables(List.of(1, 2, 3, 4, 5, 6), 4);
        assertEquals(0, plan.toAdd());
        assertEquals(List.of(6, 5), plan.toRemove());
    }

    @Test
    public void theHighestAreFoundEvenWhenTheNumbersHaveGapsOrComeUnsorted() {
        SetupPlan.TablePlan plan = SetupPlan.planTables(List.of(7, 2, 13, 4), 2);
        assertEquals(List.of(13, 7), plan.toRemove());
    }
}
