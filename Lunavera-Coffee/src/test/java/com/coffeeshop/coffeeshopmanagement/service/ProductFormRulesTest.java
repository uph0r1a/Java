package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.model.Category;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.math.BigDecimal;

import static org.junit.Assert.*;

/** TODO 6.21: what the product box checks, without the box. */
public class ProductFormRulesTest {

    private String language;
    private final Category category = category();

    private static Category category() {
        Category c = new Category();
        c.setId(7);
        c.setName("Coffee");
        return c;
    }

    @Before
    public void useEnglish() {
        language = I18n.current();
        I18n.setLanguage(I18n.EN);
    }

    @After
    public void putBack() {
        I18n.setLanguage(language);
    }

    private ProductFormRules.Form form(String name, Category cat, String price, String cost, String stock, String code, String reorder) {
        return new ProductFormRules.Form(name, cat, price, cost, stock, code, reorder, "", null, true);
    }

    private ProductFormRules.Form good() {
        return form("Latte", category, "45000", "", "10", "", "");
    }

    @Test
    public void aGoodBoxGivesTheValuesAndNoQuestion() {
        ProductFormRules.Result result = ProductFormRules.check(good(), 0);
        assertNull(result.problem());
        assertTrue(result.questions().isEmpty());
        assertEquals("Latte", result.values().name());
        assertEquals(0, new BigDecimal("45000").compareTo(result.values().price()));
        assertNull("no cost typed", result.values().cost());
        assertEquals(10, result.values().stock());
        assertTrue(result.values().active());
    }

    @Test
    public void theNameIsTrimmedAndMustNotBeBlank() {
        assertEquals("Latte", ProductFormRules.check(form("  Latte ", category, "45000", "", "1", "", ""), 0).values().name());
        assertNotNull(ProductFormRules.check(form("   ", category, "45000", "", "1", "", ""), 0).problem());
        assertNotNull(ProductFormRules.check(form(null, category, "45000", "", "1", "", ""), 0).problem());
    }

    @Test
    public void aCategoryIsRequired() {
        assertNotNull(ProductFormRules.check(form("Latte", null, "45000", "", "1", "", ""), 0).problem());
    }

    @Test
    public void thePriceMustBeAPositiveNumberAndNotAbsurd() {
        for (String bad : new String[]{"", "abc", "0", "-5"}) {
            assertNotNull("price '" + bad + "'", ProductFormRules.check(form("Latte", category, bad, "", "1", "", ""), 0).problem());
        }
        assertNotNull(ProductFormRules.check(form("Latte", category, "999999999999", "", "1", "", ""), 0).problem());
    }

    @Test
    public void aCostMayBeBlankButNotNegativeOrText() {
        assertNull(ProductFormRules.check(form("Latte", category, "45000", "20000", "1", "", ""), 0).problem());
        assertNotNull(ProductFormRules.check(form("Latte", category, "45000", "-1", "1", "", ""), 0).problem());
        assertNotNull(ProductFormRules.check(form("Latte", category, "45000", "abc", "1", "", ""), 0).problem());
    }

    @Test
    public void aCostAboveThePriceIsAQuestionNotAnError() {
        ProductFormRules.Result result = ProductFormRules.check(form("Latte", category, "20000", "30000", "1", "", ""), 0);
        assertNull(result.problem());
        assertEquals(1, result.questions().size());
    }

    @Test
    public void theStockMustBeAWholeNumberFromZeroAndWithinTheLimit() {
        for (String bad : new String[]{"", "x", "-1", "1.5"}) {
            assertNotNull("stock '" + bad + "'", ProductFormRules.check(form("Latte", category, "45000", "", bad, "", ""), 0).problem());
        }
        assertNull(ProductFormRules.check(form("Latte", category, "45000", "", "0", "", ""), 0).problem());
        assertNotNull(ProductFormRules.check(form("Latte", category, "45000", "", Integer.toString(Limits.MAX_STOCK + 1), "", ""), 0).problem());
    }

    @Test
    public void aStockThatJumpedIsAQuestion() {
        ProductFormRules.Result result = ProductFormRules.check(form("Latte", category, "45000", "", "5000", "", ""), 5);
        assertNull(result.problem());
        assertEquals(1, result.questions().size());
        assertTrue(result.questions().get(0).message().contains("5000"));
        assertTrue("a small change asks nothing", ProductFormRules.check(form("Latte", category, "45000", "", "6", "", ""), 5).questions().isEmpty());
    }

    @Test
    public void bothQuestionsComeBackInTheOrderTheBoxMeetsThem() {
        ProductFormRules.Result result = ProductFormRules.check(form("Latte", category, "20000", "30000", "5000", "", ""), 5);
        assertEquals(2, result.questions().size());
        assertNotEquals(result.questions().get(0).title(), result.questions().get(1).title());
    }

    @Test
    public void aTooLongCodeAndAnUnreadableReorderLevelAreRefused() {
        assertNotNull(ProductFormRules.check(form("Latte", category, "45000", "", "1", "x".repeat(ProductCode.MAX_LENGTH + 1), ""), 0).problem());
        assertNotNull(ProductFormRules.check(form("Latte", category, "45000", "", "1", "", "many"), 0).problem());
        assertEquals(Integer.valueOf(3), ProductFormRules.check(form("Latte", category, "45000", "", "1", "", "3"), 0).values().reorderLevel());
    }

    @Test
    public void theMessagesFollowTheLanguage() {
        String en = ProductFormRules.check(form("", category, "45000", "", "1", "", ""), 0).problem().message();
        I18n.setLanguage(I18n.VI);
        String vi = ProductFormRules.check(form("", category, "45000", "", "1", "", ""), 0).problem().message();
        assertNotEquals(en, vi);
    }
}
