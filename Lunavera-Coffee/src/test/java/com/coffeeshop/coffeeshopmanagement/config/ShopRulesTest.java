package com.coffeeshop.coffeeshopmanagement.config;

import com.coffeeshop.coffeeshopmanagement.dao.TestDatabaseSupport;
import com.coffeeshop.coffeeshopmanagement.model.Role;
import com.coffeeshop.coffeeshopmanagement.service.DiscountPolicy;
import com.coffeeshop.coffeeshopmanagement.service.LoyaltyPolicy;
import com.coffeeshop.coffeeshopmanagement.service.ShopRules;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.Assert.*;

/** TODO 4.49: the cashier's discount limit and the loyalty points numbers are settings: kept, read back, and applied by the policies. */
public class ShopRulesTest {

    private String language;

    @BeforeClass
    public static void setUpDatabase() {
        TestDatabaseSupport.ensureReady();
    }

    @Before
    public void useEnglish() {
        language = I18n.current();
        I18n.setLanguage(I18n.EN);
        ShopSettings.resetForTests();
    }

    @After
    public void putBack() {
        ShopSettings.saveAll("Lunavera Coffee", "", "", "", Integer.toString(com.coffeeshop.coffeeshopmanagement.service.StockLevel.DEFAULT), ShopRules.DEFAULTS);
        ShopSettings.resetForTests();
        I18n.setLanguage(language);
    }

    private static ShopRules.Values rules(int staff, long earn, long spend, int bill, int min, int silver, int gold) {
        return new ShopRules.Values(staff, earn, spend, bill, min, silver, gold);
    }

    // ------------------------------------------------------------------ no database

    @Test
    public void untilChangedTheRulesAreTheOnesTheProgramAlwaysHad() {
        assertEquals(10, DiscountPolicy.staffMaxPercent());
        assertEquals(0, new BigDecimal("1000").compareTo(LoyaltyPolicy.vndPerPointSpent()));
        assertEquals(50, LoyaltyPolicy.maxPercentOfBill());
        assertEquals(20, LoyaltyPolicy.minPoints());
        assertEquals(100, LoyaltyPolicy.silverFrom());
        assertEquals(500, LoyaltyPolicy.goldFrom());
        assertEquals(5, LoyaltyPolicy.pointsFor(new BigDecimal("55000")));
    }

    @Test
    public void aBlankBoxMeansTheOriginalValue() {
        assertEquals(ShopRules.DEFAULTS, ShopRules.parse("", " ", null, "", "", "", ""));
    }

    @Test
    public void numbersOutsideTheirRangeOrNotNumbersAreRefusedWithTheFieldNamed() {
        assertNotNull(ShopRules.problemWith("101", "10000", "1000", "50", "20", "100", "500"));
        assertNotNull(ShopRules.problemWith("10", "999", "1000", "50", "20", "100", "500"));
        assertNotNull(ShopRules.problemWith("10", "10000", "99", "50", "20", "100", "500"));
        assertNotNull(ShopRules.problemWith("10", "10000", "1000", "91", "20", "100", "500"));
        assertNotNull(ShopRules.problemWith("10", "10000", "1000", "50", "0", "100", "500"));
        assertNotNull(ShopRules.problemWith("10", "10000", "1000", "50", "20", "100", "abc"));
        assertNotNull(ShopRules.problemWith("-1", "10000", "1000", "50", "20", "100", "500"));
        assertNull(ShopRules.problemWith("0", "10.000", "1,000", "0", "20", "100", "500"));
        String message = ShopRules.problemWith("101", "10000", "1000", "50", "20", "100", "500");
        assertTrue(message, message.contains("Cashier discount limit") && message.contains("30")); // the limit was 100 until TODO 4.49 was finished
    }

    @Test
    public void goldMustStartAboveSilver() {
        assertNotNull(ShopRules.problemWith("10", "10000", "1000", "50", "20", "300", "300"));
        assertNotNull(ShopRules.problemWith("10", "10000", "1000", "50", "20", "300", "200"));
        assertNull(ShopRules.problemWith("10", "10000", "1000", "50", "20", "300", "301"));
    }

    @Test
    public void aSetThatIsNotValidIsNeverUsed() {
        ShopRules.set(rules(500, 10000, 1000, 50, 20, 100, 500));
        assertEquals(10, DiscountPolicy.staffMaxPercent());
    }

    @Test
    public void thePoliciesFollowTheRulesInForce() {
        ShopRules.set(rules(25, 5_000, 500, 30, 10, 200, 800));
        try {
            assertEquals(25, DiscountPolicy.staffMaxPercent());
            assertEquals(new BigDecimal("25000"), DiscountPolicy.maxDiscount(Role.EMPLOYEE, new BigDecimal("100000")));
            assertEquals(new BigDecimal("100000"), DiscountPolicy.maxDiscount(Role.ADMIN, new BigDecimal("100000")));
            assertEquals(DiscountPolicy.Result.OK, DiscountPolicy.check(Role.EMPLOYEE, new BigDecimal("100000"), new BigDecimal("25000"), "regular"));
            assertEquals(DiscountPolicy.Result.ABOVE_STAFF_LIMIT, DiscountPolicy.check(Role.EMPLOYEE, new BigDecimal("100000"), new BigDecimal("25001"), "regular"));
            assertEquals(11, LoyaltyPolicy.pointsFor(new BigDecimal("55000"))); // 1 point per 5.000đ
            // 30 % of 100.000 = 30.000đ at 500đ a point = 60 points; the balance caps it
            assertEquals(60, LoyaltyPolicy.maxRedeemable(1000, new BigDecimal("100000")));
            assertEquals(40, LoyaltyPolicy.maxRedeemable(40, new BigDecimal("100000")));
            assertEquals("below the least (10) nothing is spent", 0, LoyaltyPolicy.maxRedeemable(9, new BigDecimal("100000")));
            assertEquals(new BigDecimal("5000"), LoyaltyPolicy.redemptionOf(10).value());
            assertEquals(LoyaltyPolicy.Tier.NEW, LoyaltyPolicy.tierFor(199));
            assertEquals(LoyaltyPolicy.Tier.SILVER, LoyaltyPolicy.tierFor(200));
            assertEquals(LoyaltyPolicy.Tier.GOLD, LoyaltyPolicy.tierFor(800));
            assertNotNull(LoyaltyPolicy.redemptionProblem(5, 1000, new BigDecimal("100000")));
            assertTrue(LoyaltyPolicy.redemptionProblem(5, 1000, new BigDecimal("100000")).contains("10 points"));
        } finally {
            ShopRules.resetForTests();
        }
    }

    // ------------------------------------------------------------------ the database

    @Test
    public void theRulesAreSavedWithTheSettingsAndReadBackByAFreshStart() {
        ShopRules.Values mine = rules(20, 20_000, 2_000, 40, 5, 150, 600);
        ShopSettings.saveAll("Quán Mai", "", "", "", "10", mine);
        assertEquals(mine, ShopRules.current());
        ShopSettings.resetForTests(); // as if the program had just started
        assertEquals(ShopRules.DEFAULTS, ShopRules.current());
        ShopSettings.load();
        assertEquals(mine, ShopRules.current());
        assertEquals(20, DiscountPolicy.staffMaxPercent());
    }

    @Test
    public void savingTheOtherSettingsLeavesTheRulesAlone() {
        ShopRules.Values mine = rules(20, 20_000, 2_000, 40, 5, 150, 600);
        ShopSettings.saveAll("Quán Mai", "", "", "", "10", mine);
        ShopSettings.saveAll("Quán Mai 2", "", "", "", "10"); // the older form: no rules given
        ShopSettings.resetForTests();
        ShopSettings.load();
        assertEquals(mine, ShopRules.current());
    }

    @Test
    public void aRefusedSetWritesNothing() {
        ShopRules.Values mine = rules(20, 20_000, 2_000, 40, 5, 150, 600);
        ShopSettings.saveAll("Quán Mai", "", "", "", "10", mine);
        try {
            ShopSettings.saveAll("Other name", "", "", "", "10", rules(200, 20_000, 2_000, 40, 5, 150, 600));
            fail("a cashier limit of 200 % must be refused");
        } catch (IllegalArgumentException expected) {
            ShopSettings.resetForTests();
            ShopSettings.load();
            assertEquals(mine, ShopRules.current());
            assertEquals("Quán Mai", ShopSettings.cachedName());
        }
    }

    @Test
    public void aSavedSetThatWasEditedByHandToNonsenseFallsBackToTheOriginals() {
        AppFlags.replaceValues(Map.of(ShopSettings.RULE_STAFF_PREFIX, "999", ShopSettings.RULE_GOLD_PREFIX, "5"));
        ShopSettings.resetForTests();
        ShopSettings.load();
        assertEquals(ShopRules.DEFAULTS, ShopRules.current());
    }
}
