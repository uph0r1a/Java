package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.model.OrderItem;
import com.coffeeshop.coffeeshopmanagement.service.AutoPromotionRules.Kind;
import com.coffeeshop.coffeeshopmanagement.service.AutoPromotionRules.Promotion;
import com.coffeeshop.coffeeshopmanagement.service.AutoPromotionRules.Target;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import org.junit.Before;
import org.junit.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** TODO 4.55: the bill-level part of automatic promotions (order lines in, names and amounts out). No database. */
public class AutoPromotionServiceTest {

    private static final LocalDateTime MON_15H = LocalDateTime.of(2026, 11, 2, 15, 0);
    private static final Map<Integer, Integer> CATEGORY_OF = Map.of(1, 10, 2, 10, 3, 11);

    private String languageBefore;

    @Before
    public void english() {
        languageBefore = I18n.current();
        I18n.setLanguage(I18n.EN);
    }

    /** Other tests expect the language they found: put it back, whatever order the test classes run in. */
    @org.junit.After
    public void putTheLanguageBack() {
        I18n.setLanguage(languageBefore);
    }

    private static OrderItem line(int product, String name, String price, int quantity) {
        OrderItem item = new OrderItem();
        item.setProductId(product);
        item.setProductName(name);
        item.setQuantity(quantity);
        item.setUnitPrice(new BigDecimal(price));
        item.setLineTotal(new BigDecimal(price).multiply(BigDecimal.valueOf(quantity)));
        return item;
    }

    private static Promotion happyHour() {
        return new Promotion(1, "Giờ vàng", Kind.PERCENT, true, 10, 0, 0, 0, Set.of(), LocalTime.of(14, 0), LocalTime.of(16, 0), null, null,
                List.of(Target.category(10)));
    }

    @Test
    public void theBillsLinesGetTheirNamesAndAmounts() {
        List<OrderItem> items = new ArrayList<>(List.of(line(1, "Latte", "50000", 2), line(3, "Cake", "40000", 1)));
        AutoPromotionService.Result result = AutoPromotionService.evaluate(items, List.of(happyHour()), CATEGORY_OF, MON_15H);
        assertEquals(0, new BigDecimal("10000").compareTo(result.value()));
        assertEquals(List.of("Giờ vàng"), result.names());
        result.writeOnto(items);
        assertEquals("Giờ vàng", items.get(0).getPromoName());
        assertEquals(0, new BigDecimal("10000").compareTo(items.get(0).getPromoValue()));
        assertNull("the cake is in another category", items.get(1).getPromoName());
        assertNull(items.get(1).getPromoValue());
    }

    @Test
    public void outsideTheHoursNothingIsGivenAndOldMarksAreCleared() {
        List<OrderItem> items = new ArrayList<>(List.of(line(1, "Latte", "50000", 1)));
        items.get(0).setPromoName("stale");
        items.get(0).setPromoValue(new BigDecimal("1"));
        AutoPromotionService.Result result = AutoPromotionService.evaluate(items, List.of(happyHour()), CATEGORY_OF,
                LocalDateTime.of(2026, 11, 2, 17, 0));
        assertTrue(result.isNone());
        assertTrue(result.names().isEmpty());
        result.writeOnto(items);
        assertNull(items.get(0).getPromoName());
        assertNull(items.get(0).getPromoValue());
    }

    @Test
    public void aLineWithoutAProductOrAPriceIsLeftAlone() {
        OrderItem gone = line(0, "Deleted product", "50000", 1);
        gone.setProductId(null);
        List<OrderItem> items = new ArrayList<>(List.of(gone));
        assertTrue(AutoPromotionService.evaluate(items, List.of(happyHour()), CATEGORY_OF, MON_15H).isNone());
    }

    @Test
    public void everyNameIsListedOnceInTheOrderItWasGiven() {
        Promotion drinks = happyHour();
        Promotion cake = new Promotion(2, "Bánh -20%", Kind.PERCENT, true, 20, 0, 0, 0, Set.of(), null, null, null, null, List.of(Target.category(11)));
        List<OrderItem> items = new ArrayList<>(List.of(line(3, "Cake", "40000", 1), line(1, "Latte", "50000", 1), line(2, "Tea", "30000", 1)));
        AutoPromotionService.Result result = AutoPromotionService.evaluate(items, List.of(drinks, cake), CATEGORY_OF, MON_15H);
        assertEquals(List.of("Bánh -20%", "Giờ vàng"), result.names());
        assertEquals(0, new BigDecimal(8_000 + 5_000 + 3_000).compareTo(result.value()));
    }

    @Test
    public void theReasonGetsThePromotionsNamesTheWayAVoucherAddsItsCode() {
        assertEquals("Promotion: Giờ vàng, Combo", AutoPromotionService.reasonWithPromotions(null, List.of("Giờ vàng", "Combo")));
        assertEquals("khách quen · Promotion: Giờ vàng", AutoPromotionService.reasonWithPromotions("khách quen", List.of("Giờ vàng")));
        assertEquals("khách quen", AutoPromotionService.reasonWithPromotions("khách quen", List.of()));
        assertNull(AutoPromotionService.reasonWithPromotions(null, List.of()));
    }

    @Test
    public void thePromotionIsToldInWords() {
        assertEquals("10% off", AutoPromotionService.worth(happyHour()));
        Promotion buyGet = new Promotion(2, "2+1", Kind.BUY_GET, true, 0, 0, 2, 1, Set.of(), null, null, null, null, List.of(Target.product(1)));
        assertEquals("Buy 2 get 1 free", AutoPromotionService.worth(buyGet));
        assertTrue(AutoPromotionService.worth(new Promotion(3, "C", Kind.COMBO, true, 0, 70_000, 0, 0, Set.of(), null, null, null, null,
                List.of(Target.product(1), Target.product(3)))).startsWith("Combo"));
        String when = AutoPromotionService.when(happyHour());
        assertTrue(when, when.startsWith("Every day") && when.contains("14:00–16:00"));
        Promotion weekend = new Promotion(4, "W", Kind.PERCENT, true, 5, 0, 0, 0, Set.of(DayOfWeek.SUNDAY, DayOfWeek.SATURDAY), null, null,
                null, null, List.of(Target.product(1)));
        assertTrue(AutoPromotionService.when(weekend), AutoPromotionService.when(weekend).startsWith("Sat, Sun, all day"));
    }

    @Test
    public void daysAreKeptAsDigitsAndComeBackTheSame() {
        assertEquals("", AutoPromotionRules.encodeDays(Set.of()));
        assertEquals("67", AutoPromotionRules.encodeDays(Set.of(DayOfWeek.SUNDAY, DayOfWeek.SATURDAY)));
        assertEquals(Set.of(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), AutoPromotionRules.decodeDays("15"));
        assertEquals(Set.of(), AutoPromotionRules.decodeDays(null));
        assertEquals("a damaged value means fewer days, never an error", Set.of(DayOfWeek.TUESDAY), AutoPromotionRules.decodeDays("x2z9"));
    }

    @Test
    public void everyTextTheLayerUsesExistsInBothLanguages() {
        for (String language : List.of(I18n.EN, I18n.VI)) {
            I18n.setLanguage(language);
            for (DayOfWeek day : DayOfWeek.values()) assertFalse(AutoPromotionService.dayName(day).startsWith("!"));
            for (Kind kind : Kind.values()) assertFalse(AutoPromotionService.kindName(kind).startsWith("!"));
            for (String code : List.of("name", "kind", "targets", "hours", "dates", "percent", "price", "buyget", "combo", "other")) {
                assertFalse(code + " in " + language, AutoPromotionService.problemText(code).startsWith("!"));
            }
        }
        I18n.setLanguage(languageBefore);
    }

    // ---- the moment a bill is priced at (the payment window's opening)

    @Test
    public void noClaimedMomentMeansNow() {
        assertEquals(MON_15H, AutoPromotionService.pricingInstant(null, MON_15H));
    }

    @Test
    public void aRecentMomentIsUsedAsClaimed() {
        assertEquals(MON_15H.minusMinutes(14), AutoPromotionService.pricingInstant(MON_15H.minusMinutes(14), MON_15H));
        assertEquals(MON_15H.minusMinutes(15), AutoPromotionService.pricingInstant(MON_15H.minusMinutes(15), MON_15H));
        assertEquals(MON_15H, AutoPromotionService.pricingInstant(MON_15H, MON_15H));
        assertEquals(MON_15H.plusMinutes(1), AutoPromotionService.pricingInstant(MON_15H.plusMinutes(1), MON_15H));
    }

    @Test
    public void aMomentThatIsTooOldOrInTheFutureIsRefused() {
        for (LocalDateTime claimed : List.of(MON_15H.minusMinutes(16), MON_15H.minusHours(3), MON_15H.plusMinutes(2), MON_15H.plusDays(1))) {
            try {
                AutoPromotionService.pricingInstant(claimed, MON_15H);
                org.junit.Assert.fail("should be refused: " + claimed);
            } catch (com.coffeeshop.coffeeshopmanagement.dao.DataAccessException expected) {
                assertEquals(I18n.t("order.promo.stale", 15), expected.getMessage());
            }
        }
    }

    @Test
    public void aHappyHourThatEndedWhileTheWindowWasOpenStillCountsAtTheMomentItWasOpened() {
        // the window opened at 15:59, the payment is confirmed at 16:03: priced at 15:59 the promotion is on, at 16:03 it is not
        List<OrderItem> items = new ArrayList<>(List.of(line(1, "Latte", "50000", 1)));
        LocalDateTime opened = LocalDateTime.of(2026, 11, 2, 15, 59);
        LocalDateTime confirmed = LocalDateTime.of(2026, 11, 2, 16, 3);
        LocalDateTime pricedAt = AutoPromotionService.pricingInstant(opened, confirmed);
        assertEquals(0, new BigDecimal("5000").compareTo(AutoPromotionService.evaluate(items, List.of(happyHour()), CATEGORY_OF, pricedAt).value()));
        assertTrue(AutoPromotionService.evaluate(items, List.of(happyHour()), CATEGORY_OF, confirmed).isNone());
    }

    @Test
    public void theTextsOfThePricingMomentAndTheCartExistInBothLanguages() {
        for (String language : List.of(I18n.EN, I18n.VI)) {
            I18n.setLanguage(language);
            assertFalse(I18n.t("order.promo.stale", 15).startsWith("!"));
            assertTrue(I18n.t("order.promo.stale", 15).contains("15"));
            assertFalse(I18n.t("pos.cart.promo", "A", "1", "2").startsWith("!"));
        }
        I18n.setLanguage(languageBefore);
    }
}
