package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.model.OptionKind;
import com.coffeeshop.coffeeshopmanagement.model.ProductOption;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/** TODO 4.47: what the owner may give a product: names, counts, prices and the one default size. */
public class OptionRulesTest {

    private static final BigDecimal PRICE = new BigDecimal("40000");

    private static ProductOption size(String name, long price, int percent, boolean isDefault, boolean active) {
        return new ProductOption(0, 1, OptionKind.SIZE, name, BigDecimal.valueOf(price), BigDecimal.ZERO, percent, isDefault, active);
    }

    private static ProductOption extra(String name, long price) {
        return new ProductOption(0, 1, OptionKind.EXTRA, name, BigDecimal.valueOf(price), BigDecimal.ZERO, 100, false, true);
    }

    private static String problem(ProductOption... options) {
        return OptionRules.problem(PRICE, OptionRules.normalise(List.of(options)));
    }

    @Test
    public void aSensibleListHasNoProblem() {
        assertNull(problem(size("S", -5000, 80, false, true), size("M", 0, 100, true, true), size("L", 10000, 130, false, true),
                extra("Extra shot", 10000), extra("Whipped cream", 5000)));
        assertNull("no options at all is fine", problem());
    }

    @Test
    public void exactlyOneSizeIsTheDefault() {
        List<ProductOption> none = OptionRules.normalise(List.of(size("S", 0, 100, false, true), size("M", 0, 100, false, true)));
        assertEquals(1, none.stream().filter(ProductOption::isDefault).count());
        assertTrue("the first size when none was asked for", none.get(0).isDefault());
        List<ProductOption> two = OptionRules.normalise(List.of(size("S", 0, 100, true, true), size("M", 0, 100, true, true)));
        assertEquals(1, two.stream().filter(ProductOption::isDefault).count());
        List<ProductOption> off = OptionRules.normalise(List.of(size("S", 0, 100, true, false), size("M", 0, 100, false, true)));
        assertFalse("a switched-off size is never the default", off.get(0).isDefault());
        assertTrue(off.get(1).isDefault());
    }

    @Test
    public void anExtraIsNeverADefaultAndAlwaysAtTheNormalRecipe() {
        ProductOption odd = new ProductOption(0, 1, OptionKind.EXTRA, "Shot", BigDecimal.TEN, BigDecimal.ZERO, 250, true, true);
        ProductOption fixed = OptionRules.normalise(List.of(odd)).get(0);
        assertFalse(fixed.isDefault());
        assertEquals(100, fixed.recipePercent());
    }

    @Test
    public void namesAreCleanedAndMustExistAndBeShortAndUnique() {
        assertEquals("Extra shot", OptionRules.normalise(List.of(extra("  Extra   shot ", 1))).get(0).name());
        assertNotNull(problem(extra("   ", 1)));
        assertNotNull(problem(extra("x".repeat(OptionRules.NAME_MAX + 1), 1)));
        assertNull(problem(extra("x".repeat(OptionRules.NAME_MAX), 1)));
        assertNotNull("same name in another case", problem(extra("Cream", 1), extra("cream", 1)));
        assertNull("a size and an extra may share a name", problem(size("Shot", 0, 100, true, true), extra("Shot", 1)));
    }

    @Test
    public void thereAreLimitsOnHowManyOfEachKind() {
        List<ProductOption> many = new ArrayList<>();
        for (int i = 0; i <= OptionRules.MAX_SIZES; i++) many.add(size("S" + i, 0, 100, false, true));
        assertNotNull(OptionRules.problem(PRICE, OptionRules.normalise(many)));
        many.clear();
        for (int i = 0; i <= OptionRules.MAX_EXTRAS; i++) many.add(extra("E" + i, 1));
        assertNotNull(OptionRules.problem(PRICE, OptionRules.normalise(many)));
        many.remove(0);
        assertNull(OptionRules.problem(PRICE, OptionRules.normalise(many)));
    }

    @Test
    public void pricesAndPercentsStayInRange() {
        assertNotNull("an extra cannot take money off", problem(extra("Coupon", -1)));
        assertNotNull("a size cannot make the product negative", problem(size("XS", -40001, 100, true, true)));
        assertNull(problem(size("XS", -40000, 100, true, true)));
        assertNotNull(problem(size("L", 0, OptionRules.MAX_PERCENT + 1, true, true)));
        assertNotNull(problem(size("L", 0, OptionRules.MIN_PERCENT - 1, true, true)));
        assertNotNull(OptionRules.problem(PRICE, OptionRules.normalise(List.of(extra("Gold", 2_000_000_000L)))));
        ProductOption negativeCost = new ProductOption(0, 1, OptionKind.EXTRA, "Shot", BigDecimal.TEN, BigDecimal.valueOf(-1), 100, false, true);
        assertNotNull(OptionRules.problem(PRICE, OptionRules.normalise(List.of(negativeCost))));
    }

    @Test
    public void whenThePriceIsNotKnownOnlyTheFiguresAreChecked() {
        assertNull(OptionRules.problem(null, OptionRules.normalise(List.of(size("XS", -1_000_000, 100, true, true)))));
    }
}
