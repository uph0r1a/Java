package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.service.AutoPromotionRules.Applied;
import com.coffeeshop.coffeeshopmanagement.service.AutoPromotionRules.Kind;
import com.coffeeshop.coffeeshopmanagement.service.AutoPromotionRules.Line;
import com.coffeeshop.coffeeshopmanagement.service.AutoPromotionRules.Promotion;
import com.coffeeshop.coffeeshopmanagement.service.AutoPromotionRules.Target;
import org.junit.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** TODO 4.55: the arithmetic of automatic promotions. No database, no screen. */
public class AutoPromotionRulesTest {

    // 2026-11-02 is a Monday.
    private static final LocalDateTime MON_15H = LocalDateTime.of(2026, 11, 2, 15, 0);
    private static final int LATTE = 1, TEA = 2, CAKE = 3, COFFEE_CAT = 10, CAKE_CAT = 11;

    private static Line latte(int qty) {
        return new Line(LATTE, COFFEE_CAT, 50_000, qty);
    }

    private static Line tea(int qty) {
        return new Line(TEA, COFFEE_CAT, 30_000, qty);
    }

    private static Line cake(int qty) {
        return new Line(CAKE, CAKE_CAT, 40_000, qty);
    }

    private static Promotion percent(int id, String name, int percent, Target... targets) {
        return new Promotion(id, name, Kind.PERCENT, true, percent, 0, 0, 0, Set.of(), null, null, null, null, List.of(targets));
    }

    private static Promotion fixed(int id, String name, long price, Target... targets) {
        return new Promotion(id, name, Kind.FIXED_PRICE, true, 0, price, 0, 0, Set.of(), null, null, null, null, List.of(targets));
    }

    private static Promotion buyGet(int id, String name, int buy, int free, Target... targets) {
        return new Promotion(id, name, Kind.BUY_GET, true, 0, 0, buy, free, Set.of(), null, null, null, null, List.of(targets));
    }

    private static Promotion combo(int id, String name, long price, Target... targets) {
        return new Promotion(id, name, Kind.COMBO, true, 0, price, 0, 0, Set.of(), null, null, null, null, List.of(targets));
    }

    private static Promotion with(Promotion p, boolean active, Set<DayOfWeek> days, LocalTime from, LocalTime to, LocalDate vf, LocalDate vt) {
        return new Promotion(p.id(), p.name(), p.kind(), active, p.percent(), p.fixedPrice(), p.buy(), p.free(), days, from, to, vf, vt, p.targets());
    }

    private static Applied apply(List<Line> lines, Promotion... promotions) {
        return AutoPromotionRules.apply(lines, List.of(promotions), MON_15H);
    }

    // ---- percentage and fixed price

    @Test
    public void aPercentageTakesThatMuchOffTheMatchedLine() {
        Applied a = apply(List.of(latte(2), cake(1)), percent(1, "Giờ vàng", 10, Target.category(COFFEE_CAT)));
        assertEquals("Giờ vàng", a.lines().get(0).promotionName());
        assertEquals(10_000, a.lines().get(0).saving());
        assertNull("the cake is not in the category", a.lines().get(1).promotionName());
        assertEquals(10_000, a.total());
    }

    @Test
    public void aPercentageIsRoundedDownToWholeDong() {
        // 33% of 50,000 x 1 = 16,500 exactly; of 30,001 it would be 9,900.33 -> 9,900
        Applied a = apply(List.of(new Line(TEA, COFFEE_CAT, 30_001, 1)), percent(1, "P", 33, Target.product(TEA)));
        assertEquals(9_900, a.total());
    }

    @Test
    public void aFixedPriceChargesThatPricePerPiece() {
        Applied a = apply(List.of(latte(3)), fixed(1, "Latte 39k", 39_000, Target.product(LATTE)));
        assertEquals(33_000, a.total()); // (50,000 - 39,000) x 3
    }

    @Test
    public void aFixedPriceNeverRaisesAPrice() {
        assertTrue(apply(List.of(tea(2)), fixed(1, "Dear", 45_000, Target.product(TEA))).isNone());
        assertTrue(apply(List.of(tea(2)), fixed(1, "Same", 30_000, Target.product(TEA))).isNone());
    }

    @Test
    public void aFixedPriceOfZeroMakesTheLineFree() {
        assertEquals(60_000, apply(List.of(tea(2)), fixed(1, "Free", 0, Target.product(TEA))).total());
    }

    // ---- buy X get Y

    @Test
    public void buyTwoGetOneGivesEveryThirdPieceFree() {
        assertEquals(50_000, apply(List.of(latte(3)), buyGet(1, "2+1", 2, 1, Target.product(LATTE))).total());
        assertEquals(50_000, apply(List.of(latte(5)), buyGet(1, "2+1", 2, 1, Target.product(LATTE))).total());
        assertEquals(100_000, apply(List.of(latte(6)), buyGet(1, "2+1", 2, 1, Target.product(LATTE))).total());
        assertTrue("two pieces are not enough", apply(List.of(latte(2)), buyGet(1, "2+1", 2, 1, Target.product(LATTE))).isNone());
    }

    @Test
    public void buyGetCountsThePiecesOfOneProductOverAllItsLines() {
        // two lines of the same drink (different notes): 1 + 2 pieces make one free; it falls on the first line
        List<Line> lines = List.of(latte(1), latte(2));
        Applied a = apply(lines, buyGet(1, "2+1", 2, 1, Target.product(LATTE)));
        assertEquals(50_000, a.total());
        assertEquals(50_000, a.lines().get(0).saving());
        assertEquals(0, a.lines().get(1).saving());
        assertNull(a.lines().get(1).promotionName());
    }

    @Test
    public void buyGetByCategoryCountsEachProductOnItsOwn() {
        // three pieces in total but 2 latte + 1 tea: neither product reaches three
        Applied a = apply(List.of(latte(2), tea(1)), buyGet(1, "2+1", 2, 1, Target.category(COFFEE_CAT)));
        assertTrue(a.isNone());
        Applied b = apply(List.of(latte(3), tea(3)), buyGet(1, "2+1", 2, 1, Target.category(COFFEE_CAT)));
        assertEquals(50_000 + 30_000, b.total());
    }

    @Test
    public void buyOneGetOneIsAGroupOfTwo() {
        assertEquals(50_000, apply(List.of(latte(2)), buyGet(1, "1+1", 1, 1, Target.product(LATTE))).total());
        assertEquals(50_000, apply(List.of(latte(3)), buyGet(1, "1+1", 1, 1, Target.product(LATTE))).total());
    }

    // ---- no stacking

    @Test
    public void aLineGetsOnlyThePromotionThatSavesMost() {
        Applied a = apply(List.of(latte(2)),
                percent(1, "10%", 10, Target.product(LATTE)),   // 10,000
                percent(2, "20%", 20, Target.product(LATTE)),   // 20,000
                fixed(3, "Fixed", 45_000, Target.product(LATTE))); // 10,000
        assertEquals("20%", a.lines().get(0).promotionName());
        assertEquals("never added up", 20_000, a.total());
    }

    @Test
    public void onATieTheLowerIdWins() {
        Applied a = apply(List.of(latte(1)), percent(7, "B", 10, Target.product(LATTE)), percent(3, "A", 10, Target.product(LATTE)));
        assertEquals("A", a.lines().get(0).promotionName());
    }

    @Test
    public void theSavingIsNeverMoreThanTheLine() {
        // a 100% promotion and a fixed price of zero both save exactly the line, never beyond
        Applied a = apply(List.of(tea(2)), percent(1, "Free", 100, Target.product(TEA)));
        assertEquals(60_000, a.total());
    }

    // ---- combos

    @Test
    public void aComboChargesOnePriceForTheSet() {
        Promotion set = combo(1, "Sáng", 70_000, Target.product(LATTE), Target.product(CAKE)); // 90,000 list
        Applied a = apply(List.of(latte(1), cake(1)), set);
        assertEquals(20_000, a.total());
        assertEquals("Sáng", a.lines().get(0).promotionName());
        assertEquals("Sáng", a.lines().get(1).promotionName());
        assertEquals("the two lines share the saving and add up to it", 20_000, a.lines().get(0).saving() + a.lines().get(1).saving());
    }

    @Test
    public void aComboTakesAsManyWholeSetsAsTheBillHolds() {
        Promotion set = combo(1, "Sáng", 70_000, Target.product(LATTE), Target.product(CAKE));
        Applied a = apply(List.of(latte(3), cake(2)), set); // two sets; one latte is left over
        assertEquals(40_000, a.total());
    }

    @Test
    public void aComboNeedsEveryPartOfTheSet() {
        Promotion set = combo(1, "Sáng", 70_000, Target.product(LATTE), Target.product(CAKE));
        assertTrue(apply(List.of(latte(2)), set).isNone());
        assertTrue(apply(List.of(cake(1)), set).isNone());
    }

    @Test
    public void aComboMayNeedMoreThanOnePieceOfAProduct() {
        Promotion two = combo(1, "2 latte", 80_000, Target.product(LATTE, 2)); // 100,000 list
        assertEquals(20_000, apply(List.of(latte(2)), two).total());
        assertEquals(40_000, apply(List.of(latte(4)), two).total());
        assertEquals(20_000, apply(List.of(latte(3)), two).total());
    }

    @Test
    public void aComboThatDoesNotSaveAnythingIsNotUsed() {
        Promotion dear = combo(1, "Dear", 90_000, Target.product(LATTE), Target.product(CAKE)); // equals list
        assertTrue(apply(List.of(latte(1), cake(1)), dear).isNone());
    }

    @Test
    public void aComboLosesToASingleOfferThatSavesMore() {
        Promotion set = combo(1, "Sáng", 80_000, Target.product(LATTE), Target.product(CAKE)); // saves 10,000
        Promotion half = percent(2, "Half latte", 50, Target.product(LATTE)); // saves 25,000 on one latte
        Applied a = apply(List.of(latte(1), cake(1)), set, half);
        assertEquals("Half latte", a.lines().get(0).promotionName());
        assertEquals(25_000, a.total());
        assertNull(a.lines().get(1).promotionName());
    }

    @Test
    public void aLineThatJoinsAComboKeepsOnlyTheComboNotItsOwnOfferOnItsOtherPieces() {
        // 3 lattes with 20% off (30,000) vs a combo of 1 latte + 1 cake saving 20,000: the combo would cost the customer 10,000, so it is not used
        Promotion set = combo(1, "Sáng", 70_000, Target.product(LATTE), Target.product(CAKE));
        Promotion twenty = percent(2, "20%", 20, Target.product(LATTE));
        Applied a = apply(List.of(latte(3), cake(1)), set, twenty);
        assertEquals("20%", a.lines().get(0).promotionName());
        assertEquals(30_000, a.total());
    }

    @Test
    public void aComboBeatsASmallerSingleOffer() {
        Promotion set = combo(1, "Sáng", 60_000, Target.product(LATTE), Target.product(CAKE)); // saves 30,000
        Promotion small = percent(2, "5%", 5, Target.product(LATTE)); // 2,500
        Applied a = apply(List.of(latte(1), cake(1)), set, small);
        assertEquals("Sáng", a.lines().get(0).promotionName());
        assertEquals(30_000, a.total());
    }

    // ---- when a promotion is on

    @Test
    public void theHoursStartIncludedAndEndExcluded() {
        Promotion happy = with(percent(1, "Giờ vàng", 10, Target.product(LATTE)), true, Set.of(), LocalTime.of(14, 0), LocalTime.of(16, 0), null, null);
        List<Line> lines = List.of(latte(1));
        assertEquals(5_000, AutoPromotionRules.apply(lines, List.of(happy), LocalDateTime.of(2026, 11, 2, 14, 0)).total());
        assertEquals(5_000, AutoPromotionRules.apply(lines, List.of(happy), LocalDateTime.of(2026, 11, 2, 15, 59)).total());
        assertTrue(AutoPromotionRules.apply(lines, List.of(happy), LocalDateTime.of(2026, 11, 2, 16, 0)).isNone());
        assertTrue(AutoPromotionRules.apply(lines, List.of(happy), LocalDateTime.of(2026, 11, 2, 13, 59)).isNone());
    }

    @Test
    public void onlyTheNamedDaysCount() {
        Promotion weekend = with(percent(1, "Cuối tuần", 10, Target.product(LATTE)), true, Set.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), null, null, null, null);
        List<Line> lines = List.of(latte(1));
        assertTrue("Monday", AutoPromotionRules.apply(lines, List.of(weekend), MON_15H).isNone());
        assertEquals("Saturday", 5_000, AutoPromotionRules.apply(lines, List.of(weekend), LocalDateTime.of(2026, 11, 7, 9, 0)).total());
    }

    @Test
    public void theStartAndEndDatesAreBothIncluded() {
        Promotion window = with(percent(1, "Tuần lễ", 10, Target.product(LATTE)), true, Set.of(), null, null,
                LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 8));
        List<Line> lines = List.of(latte(1));
        assertEquals(5_000, AutoPromotionRules.apply(lines, List.of(window), LocalDateTime.of(2026, 11, 2, 0, 0)).total());
        assertEquals(5_000, AutoPromotionRules.apply(lines, List.of(window), LocalDateTime.of(2026, 11, 8, 23, 59)).total());
        assertTrue(AutoPromotionRules.apply(lines, List.of(window), LocalDateTime.of(2026, 11, 1, 23, 59)).isNone());
        assertTrue(AutoPromotionRules.apply(lines, List.of(window), LocalDateTime.of(2026, 11, 9, 0, 0)).isNone());
    }

    @Test
    public void aSwitchedOffPromotionGivesNothing() {
        Promotion off = with(percent(1, "Off", 50, Target.product(LATTE)), false, Set.of(), null, null, null, null);
        assertTrue(apply(List.of(latte(1)), off).isNone());
    }

    // ---- what is never touched

    @Test
    public void aLineWithoutAProductGetsNothing() {
        Applied a = apply(List.of(new Line(null, null, 50_000, 2)), percent(1, "All", 50, Target.category(COFFEE_CAT)));
        assertTrue(a.isNone());
    }

    @Test
    public void anEmptyBillAndNoPromotionsGiveNothing() {
        assertTrue(AutoPromotionRules.apply(List.of(), List.of(percent(1, "P", 10, Target.product(LATTE))), MON_15H).isNone());
        assertTrue(AutoPromotionRules.apply(List.of(latte(1)), List.of(), MON_15H).isNone());
        assertTrue(AutoPromotionRules.apply(List.of(latte(1)), null, MON_15H).isNone());
        assertTrue(AutoPromotionRules.apply(null, null, MON_15H).isNone());
    }

    @Test
    public void everyInputLineGetsAnAnswerInTheSameOrder() {
        Applied a = apply(List.of(cake(1), latte(1), tea(1)), percent(1, "P", 10, Target.product(LATTE)));
        assertEquals(3, a.lines().size());
        assertNull(a.lines().get(0).promotionName());
        assertEquals("P", a.lines().get(1).promotionName());
        assertNull(a.lines().get(2).promotionName());
    }

    @Test
    public void theTotalIsAlwaysTheSumOfTheLines() {
        Applied a = apply(List.of(latte(4), tea(2), cake(3)),
                percent(1, "10%", 10, Target.category(COFFEE_CAT)),
                buyGet(2, "2+1", 2, 1, Target.product(CAKE)),
                combo(3, "Set", 60_000, Target.product(LATTE), Target.product(CAKE)));
        long sum = a.lines().stream().mapToLong(AutoPromotionRules.Given::saving).sum();
        assertEquals(sum, a.total());
        for (int i = 0; i < a.lines().size(); i++) {
            long price = new long[] {200_000, 60_000, 120_000}[i];
            assertTrue("line " + i + " keeps a price of at least zero", a.lines().get(i).saving() <= price);
        }
    }

    // ---- checking a promotion before it is saved

    @Test
    public void aPromotionIsCheckedBeforeItIsSaved() {
        assertNull(AutoPromotionRules.problemWith(percent(1, "OK", 10, Target.product(LATTE))));
        assertEquals("name", AutoPromotionRules.problemWith(percent(1, " ", 10, Target.product(LATTE))));
        assertEquals("targets", AutoPromotionRules.problemWith(percent(1, "X", 10)));
        assertEquals("percent", AutoPromotionRules.problemWith(percent(1, "X", 0, Target.product(LATTE))));
        assertEquals("percent", AutoPromotionRules.problemWith(percent(1, "X", 101, Target.product(LATTE))));
        assertEquals("price", AutoPromotionRules.problemWith(fixed(1, "X", -1, Target.product(LATTE))));
        assertEquals("buyget", AutoPromotionRules.problemWith(buyGet(1, "X", 0, 1, Target.product(LATTE))));
        assertEquals("buyget", AutoPromotionRules.problemWith(buyGet(1, "X", 2, 0, Target.product(LATTE))));
        assertEquals("combo", AutoPromotionRules.problemWith(combo(1, "X", 10_000, Target.product(LATTE))));
        assertEquals("combo", AutoPromotionRules.problemWith(combo(1, "X", 10_000, Target.category(COFFEE_CAT), Target.product(CAKE))));
        assertNull(AutoPromotionRules.problemWith(combo(1, "Two", 10_000, Target.product(LATTE, 2))));
        assertEquals("hours", AutoPromotionRules.problemWith(with(percent(1, "X", 10, Target.product(LATTE)), true, Set.of(),
                LocalTime.of(16, 0), LocalTime.of(14, 0), null, null)));
        assertEquals("hours", AutoPromotionRules.problemWith(with(percent(1, "X", 10, Target.product(LATTE)), true, Set.of(),
                LocalTime.of(14, 0), null, null, null)));
        assertEquals("dates", AutoPromotionRules.problemWith(with(percent(1, "X", 10, Target.product(LATTE)), true, Set.of(), null, null,
                LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 2))));
    }

    @Test
    public void aPromotionThatIsNotValidIsNeverApplied() {
        // 150% would be a refund; the engine ignores a promotion that fails the check instead of trusting the caller
        assertTrue(apply(List.of(latte(1)), percent(1, "Bad", 150, Target.product(LATTE))).isNone());
    }

    @Test
    public void aTargetIsAProductOrACategoryNotBothAndNotNeither() {
        try {
            new Target(1, 2, 1);
            org.junit.Assert.fail();
        } catch (IllegalArgumentException expected) {
            // fine
        }
        try {
            new Target(null, null, 1);
            org.junit.Assert.fail();
        } catch (IllegalArgumentException expected) {
            // fine
        }
    }
}
