package com.coffeeshop.coffeeshopmanagement.config;

import com.coffeeshop.coffeeshopmanagement.config.TextNormalization.Change;
import com.coffeeshop.coffeeshopmanagement.config.TextNormalization.Plan;
import com.coffeeshop.coffeeshopmanagement.config.TextNormalization.Row;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** TODO 3.18: what the one-time pass over old text decides, with no database. */
public class TextNormalizationTest {

    /** "Trà" typed as T, r, a and a separate grave accent. */
    private static final String TRA_SEPARATE = "Tra\u0300";
    private static final String TRA_COMPOSED = "Tr\u00e0";

    @Test
    public void aSeparateAccentBecomesTheComposedLetter() {
        assertEquals(TRA_COMPOSED, TextNormalization.composed(TRA_SEPARATE));
        assertEquals("Cà phê", TextNormalization.composed("Ca\u0300 phe\u0302"));
        assertEquals("Đỗ", TextNormalization.composed("\u0110o\u0302\u0303"));
    }

    @Test
    public void onlyTheAccentEncodingChangesNothingElse() {
        assertEquals("  Trà   Sữa  ", TextNormalization.composed("  Tra\u0300   Su\u031b\u0303a  "));
        assertEquals("keeps  inner   spaces", TextNormalization.composed("keeps  inner   spaces"));
        assertNull(TextNormalization.composed(null));
    }

    @Test
    public void textThatIsAlreadyComposedIsLeftAlone() {
        Plan plan = TextNormalization.plan(List.of(new Row(1, TRA_COMPOSED), new Row(2, "Cà phê"), new Row(3, "plain ascii")), false);
        assertTrue(plan.changes().isEmpty());
        assertTrue(plan.collisions().isEmpty());
    }

    @Test
    public void everyDecomposedRowIsRewrittenInAColumnThatMayRepeat() {
        Plan plan = TextNormalization.plan(List.of(new Row(1, TRA_SEPARATE), new Row(2, TRA_COMPOSED), new Row(3, TRA_SEPARATE)), false);
        assertEquals(List.of(new Change(1, TRA_COMPOSED), new Change(3, TRA_COMPOSED)), plan.changes());
        assertTrue(plan.collisions().isEmpty());
    }

    @Test
    public void inAUniqueColumnARowThatWouldBecomeAnExistingNameIsLeftAlone() {
        Plan plan = TextNormalization.plan(List.of(new Row(1, TRA_COMPOSED), new Row(2, TRA_SEPARATE)), true);
        assertTrue("the composed name already belongs to row 1", plan.changes().isEmpty());
        assertEquals(List.of(2), plan.collisions());
    }

    @Test
    public void inAUniqueColumnTwoDecomposedRowsDoNotBothClaimTheSameName() {
        Plan plan = TextNormalization.plan(List.of(new Row(5, TRA_SEPARATE), new Row(9, TRA_SEPARATE)), true);
        // both are the same exact text, which a unique column could not hold; the first one to appear is rewritten, the other cannot exist
        assertEquals(List.of(new Change(5, TRA_COMPOSED)), plan.changes());
        assertEquals(List.of(9), plan.collisions());
    }

    @Test
    public void inAUniqueColumnADifferentNameIsStillRewritten() {
        Plan plan = TextNormalization.plan(List.of(new Row(1, "Cà phê"), new Row(2, TRA_SEPARATE)), true);
        assertEquals(List.of(new Change(2, TRA_COMPOSED)), plan.changes());
        assertTrue(plan.collisions().isEmpty());
    }

    @Test
    public void nullTextIsSkipped() {
        Plan plan = TextNormalization.plan(List.of(new Row(1, null), new Row(2, TRA_SEPARATE)), false);
        assertEquals(List.of(new Change(2, TRA_COMPOSED)), plan.changes());
    }

    @Test
    public void onlyTheUniqueNameColumnsAreGuarded() {
        assertTrue(TextNormalization.UNIQUE.containsAll(List.of("categories.name", "ingredients.name")));
        assertTrue("every guarded column is one that is rewritten", TextNormalization.COLUMNS.containsAll(TextNormalization.UNIQUE));
    }
}
