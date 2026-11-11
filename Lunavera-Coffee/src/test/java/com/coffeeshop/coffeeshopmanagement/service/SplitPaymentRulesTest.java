package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.model.PaymentLine;
import com.coffeeshop.coffeeshopmanagement.model.PaymentMethod;
import com.coffeeshop.coffeeshopmanagement.service.SplitPaymentRules.Card;
import com.coffeeshop.coffeeshopmanagement.service.SplitPaymentRules.Input;
import com.coffeeshop.coffeeshopmanagement.service.SplitPaymentRules.Problem;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * TODO 6.5: the arithmetic that {@code SplitPaymentPanel} used to hold next to its text fields (the owner's rules D4 and D23). No JavaFX and no
 * database. The first group names each rule once; the last two are random: any input either is refused, or its lines add up to the bill.
 */
public class SplitPaymentRulesTest {

    private static BigDecimal d(long v) {
        return BigDecimal.valueOf(v);
    }

    private static Map<PaymentMethod, String> typed(Object... pairs) {
        Map<PaymentMethod, String> m = new EnumMap<>(PaymentMethod.class);
        for (int i = 0; i < pairs.length; i += 2) m.put((PaymentMethod) pairs[i], (String) pairs[i + 1]);
        return m;
    }

    private static Input in(long total, PaymentMethod main, boolean on, Map<PaymentMethod, String> texts, Card... cards) {
        return new Input(d(total), main, on, texts, List.of(cards));
    }

    private static Card card(String amount, Long balance, String code) {
        return new Card(amount, balance, code);
    }

    private static Card emptyCard() {
        return card("", null, null);
    }

    // ---- with no split and no card the box is not used

    @Test
    public void anUntickedBoxWithNoCardIsNotASplit() {
        Input box = in(100_000, PaymentMethod.TRANSFER, false, typed(PaymentMethod.CASH, "30000"), emptyCard());
        assertTrue(SplitPaymentRules.valid(box));
        assertEquals(Problem.NONE, SplitPaymentRules.problem(box));
        assertTrue(SplitPaymentRules.lines(box).isEmpty());
        assertEquals(d(100_000), SplitPaymentRules.due(box));
        assertFalse(SplitPaymentRules.showsRest(box));
    }

    // ---- the split box

    @Test
    public void aTickedSplitGivesTheTypedAmountsAndTheMainMethodTheRest() {
        Input box = in(100_000, PaymentMethod.TRANSFER, true, typed(PaymentMethod.CASH, "30000", PaymentMethod.CARD, "20000"), emptyCard());
        assertEquals(Problem.NONE, SplitPaymentRules.problem(box));
        assertEquals(d(50_000), SplitPaymentRules.due(box));
        List<PaymentLine> lines = SplitPaymentRules.lines(box);
        assertEquals(3, lines.size());
        assertEquals(PaymentMethod.CASH, lines.get(0).method());
        assertEquals(d(30_000), lines.get(0).amount());
        assertEquals(PaymentMethod.CARD, lines.get(1).method());
        assertEquals(PaymentMethod.TRANSFER, lines.get(2).method());
        assertEquals(d(50_000), lines.get(2).amount());
        assertTrue(SplitPaymentRules.showsRest(box));
    }

    @Test
    public void theMainMethodsOwnFieldIsNotAnAmountToBePaid() {
        Input box = in(100_000, PaymentMethod.TRANSFER, true, typed(PaymentMethod.TRANSFER, "99000", PaymentMethod.CASH, "10000"), emptyCard());
        assertEquals(d(10_000), SplitPaymentRules.typed(box).values().stream().findFirst().orElseThrow());
        assertEquals(1, SplitPaymentRules.typed(box).size());
        assertEquals(d(90_000), SplitPaymentRules.due(box));
    }

    @Test
    public void aTickedSplitWithNoAmountOrNoRestLeftIsRefused() {
        assertEquals(Problem.SPLIT, SplitPaymentRules.problem(in(100_000, PaymentMethod.TRANSFER, true, typed(), emptyCard())));
        assertEquals(Problem.SPLIT, SplitPaymentRules.problem(in(100_000, PaymentMethod.TRANSFER, true, typed(PaymentMethod.CASH, "0"), emptyCard())));
        assertEquals("the main method must keep something", Problem.SPLIT,
                SplitPaymentRules.problem(in(100_000, PaymentMethod.TRANSFER, true, typed(PaymentMethod.CASH, "100000"), emptyCard())));
        assertEquals(Problem.SPLIT, SplitPaymentRules.problem(in(100_000, PaymentMethod.TRANSFER, true, typed(PaymentMethod.CASH, "150000"), emptyCard())));
        assertEquals(Problem.NONE, SplitPaymentRules.problem(in(100_000, PaymentMethod.TRANSFER, true, typed(PaymentMethod.CASH, "99999"), emptyCard())));
    }

    @Test
    public void anAmountThatIsNotANumberIsRefusedAndTheWholeTotalIsDue() {
        Input box = in(100_000, PaymentMethod.TRANSFER, true, typed(PaymentMethod.CASH, "abc"), emptyCard());
        assertEquals(Problem.SPLIT, SplitPaymentRules.problem(box));
        assertEquals(d(100_000), SplitPaymentRules.due(box));
        assertTrue(SplitPaymentRules.lines(box).isEmpty());
    }

    @Test
    public void theGiftCardAndFreeDrinkAreNeverMethodsToType() {
        assertFalse(SplitPaymentRules.otherMethods().contains(PaymentMethod.GIFT_CARD));
        assertFalse(SplitPaymentRules.otherMethods().contains(PaymentMethod.COMPLIMENTARY));
        assertTrue(SplitPaymentRules.otherMethods().contains(PaymentMethod.CASH));
    }

    // ---- gift cards

    @Test
    public void aCardComesOffFirstAndTheMainMethodGetsTheRest() {
        Input box = in(100_000, PaymentMethod.CASH, false, typed(), card("40000", 50_000L, "GC1"));
        assertEquals(Problem.NONE, SplitPaymentRules.problem(box));
        assertEquals(d(60_000), SplitPaymentRules.due(box));
        List<PaymentLine> lines = SplitPaymentRules.lines(box);
        assertEquals(2, lines.size());
        assertEquals(PaymentMethod.GIFT_CARD, lines.get(0).method());
        assertEquals("GC1", lines.get(0).reference());
        assertEquals(PaymentMethod.CASH, lines.get(1).method());
        assertEquals(d(60_000), lines.get(1).amount());
        assertTrue(SplitPaymentRules.showsRest(box));
    }

    @Test
    public void cardsThatPayTheWholeBillLeaveNoMainLineAndMakeItAGiftCardPayment() {
        Input box = in(100_000, PaymentMethod.CASH, false, typed(), card("60000", 60_000L, "A"), card("40000", 90_000L, "B"));
        assertEquals(Problem.NONE, SplitPaymentRules.problem(box));
        assertEquals(d(0), SplitPaymentRules.due(box));
        assertEquals(2, SplitPaymentRules.lines(box).size());
        assertEquals(PaymentMethod.GIFT_CARD, SplitPaymentRules.effectiveMethod(box, PaymentMethod.CASH));
        Input part = in(100_000, PaymentMethod.CASH, false, typed(), card("50000", 60_000L, "A"));
        assertEquals("a card that pays a part keeps the chosen method", PaymentMethod.CASH, SplitPaymentRules.effectiveMethod(part, PaymentMethod.CASH));
    }

    @Test
    public void aCardThatWasNotCheckedHoldsTooLittleIsUsedTwiceOrPaysMoreThanTheBillIsRefused() {
        assertEquals(Problem.GIFT, SplitPaymentRules.problem(in(100_000, PaymentMethod.CASH, false, typed(), card("10000", null, null))));
        assertEquals(Problem.GIFT, SplitPaymentRules.problem(in(100_000, PaymentMethod.CASH, false, typed(), card("10001", 10_000L, "A"))));
        assertEquals(Problem.NONE, SplitPaymentRules.problem(in(100_000, PaymentMethod.CASH, false, typed(), card("10000", 10_000L, "A"))));
        assertEquals("the same card twice", Problem.GIFT,
                SplitPaymentRules.problem(in(100_000, PaymentMethod.CASH, false, typed(), card("10000", 50_000L, "A"), card("10000", 50_000L, "A"))));
        assertEquals("cards together over the bill", Problem.GIFT,
                SplitPaymentRules.problem(in(100_000, PaymentMethod.CASH, false, typed(), card("60000", 90_000L, "A"), card("50000", 90_000L, "B"))));
    }

    @Test
    public void aCardWithNothingTypedIsLeftOutAndAnAmountThatIsNotANumberIsASplitProblem() {
        Input box = in(100_000, PaymentMethod.CASH, false, typed(), card("", null, null), card("20000", 30_000L, "A"));
        assertEquals(Problem.NONE, SplitPaymentRules.problem(box));
        assertEquals(1, SplitPaymentRules.lines(box).stream().filter(l -> l.method() == PaymentMethod.GIFT_CARD).count());
        assertNull(card("x1", null, null).value());
        assertNull(card("-5", null, null).value());
        assertEquals(d(0), card("  ", null, null).value());
        Input bad = in(100_000, PaymentMethod.CASH, false, typed(), card("abc", null, null));
        assertEquals("a card amount that is not a number is not a gift-card fault", Problem.SPLIT, SplitPaymentRules.problem(bad));
    }

    @Test
    public void aCardAndASplitTogetherMayUseTheWholeBillExactly() {
        Input box = in(100_000, PaymentMethod.TRANSFER, true, typed(PaymentMethod.CASH, "60000"), card("40000", 40_000L, "A"));
        assertEquals(Problem.NONE, SplitPaymentRules.problem(box));
        assertEquals(d(0), SplitPaymentRules.due(box));
        assertEquals(2, SplitPaymentRules.lines(box).size()); // the card and the cash; nothing is left for the transfer
        Input over = in(100_000, PaymentMethod.TRANSFER, true, typed(PaymentMethod.CASH, "60001"), card("40000", 40_000L, "A"));
        assertEquals(Problem.SPLIT, SplitPaymentRules.problem(over));
    }

    // ---- the amount offered when a card is found

    @Test
    public void theOfferIsTheMostTheCardCanPayOfWhatTheOtherCardsLeave() {
        assertEquals(Long.valueOf(40_000), SplitPaymentRules.offerFor(d(100_000), 40_000, d(0), List.of()));
        assertEquals(Long.valueOf(100_000), SplitPaymentRules.offerFor(d(100_000), 500_000, d(0), List.of()));
        assertEquals(Long.valueOf(30_000), SplitPaymentRules.offerFor(d(100_000), 500_000, d(0), List.of(d(70_000))));
        assertEquals("other cards already cover it", Long.valueOf(0), SplitPaymentRules.offerFor(d(100_000), 500_000, d(0), List.of(d(100_000), d(50_000))));
        assertEquals("a typed amount is not overwritten", null, SplitPaymentRules.offerFor(d(100_000), 500_000, d(5_000), List.of()));
        assertEquals("a card that holds nothing offers nothing", null, SplitPaymentRules.offerFor(d(100_000), 0, d(0), List.of()));
        assertEquals("an amount that is not a number is left alone", null, SplitPaymentRules.offerFor(d(100_000), 500_000, null, List.of()));
        assertEquals("another card that is not a number counts as nothing", Long.valueOf(100_000),
                SplitPaymentRules.offerFor(d(100_000), 500_000, d(0), java.util.Arrays.asList((BigDecimal) null)));
    }

    // ---- random inputs

    private static Input randomInput(java.util.Random random) {
        long total = 1000L * RandomCases.between(random, 1, 400);
        PaymentMethod[] methods = {PaymentMethod.CASH, PaymentMethod.CARD, PaymentMethod.TRANSFER};
        PaymentMethod main = methods[random.nextInt(methods.length)];
        boolean on = random.nextBoolean();
        Map<PaymentMethod, String> texts = new EnumMap<>(PaymentMethod.class);
        for (PaymentMethod method : SplitPaymentRules.otherMethods()) {
            int kind = random.nextInt(6);
            if (kind == 0) texts.put(method, "");
            else if (kind == 1) texts.put(method, "abc");
            else texts.put(method, String.valueOf(1000L * RandomCases.between(random, 0, 450)));
        }
        List<Card> cards = new ArrayList<>();
        int count = RandomCases.between(random, 0, 3);
        for (int i = 0; i < count; i++) {
            int kind = random.nextInt(6);
            String amount = kind == 0 ? "" : kind == 1 ? "x" : String.valueOf(1000L * RandomCases.between(random, 0, 450));
            Long balance = random.nextInt(5) == 0 ? null : 1000L * RandomCases.between(random, 0, 450);
            String code = balance == null ? null : "GC" + RandomCases.between(random, 1, 3); // codes repeat now and then
            cards.add(card(amount, balance, code));
        }
        return new Input(d(total), main, on, texts, cards);
    }

    @Test
    public void anyInputIsEitherRefusedOrItsLinesAddUpToTheBill() {
        RandomCases.run("SplitPaymentRules", 4000, random -> {
            Input box = randomInput(random);
            Problem problem = SplitPaymentRules.problem(box);
            assertEquals("valid and problem agree", SplitPaymentRules.valid(box), problem == Problem.NONE);
            List<PaymentLine> lines = SplitPaymentRules.lines(box);
            BigDecimal due = SplitPaymentRules.due(box);
            if (problem != Problem.NONE) {
                assertTrue("no lines for a refused box", lines.isEmpty());
                assertEquals("the whole total is due while the box is refused", box.total(), due);
                assertFalse(SplitPaymentRules.showsRest(box));
                return;
            }
            assertTrue("what is due is never negative", due.signum() >= 0);
            assertTrue("what is due is never more than the bill", due.compareTo(box.total()) <= 0);
            if (lines.isEmpty()) {
                assertEquals("an unused box leaves the whole bill to the main method", box.total(), due);
                return;
            }
            BigDecimal sum = BigDecimal.ZERO;
            Set<String> codes = new HashSet<>();
            for (PaymentLine line : lines) {
                assertTrue("every line is positive", line.amount().signum() > 0);
                sum = sum.add(line.amount());
                if (line.method() == PaymentMethod.GIFT_CARD) assertTrue("no card twice", codes.add(line.reference()));
                else assertNull("only a card line has a reference", line.reference());
            }
            assertEquals("the lines add up to the bill", box.total(), sum);
            BigDecimal mainLine = lines.stream().filter(l -> l.method() == box.main()).map(PaymentLine::amount).findFirst().orElse(BigDecimal.ZERO);
            assertEquals("the main method receives what is due", due, mainLine);
        });
    }

    @Test
    public void aCardIsNeverTakenForMoreThanItHoldsWhateverIsTyped() {
        RandomCases.run("SplitPaymentRules cards", 4000, random -> {
            Input box = randomInput(random);
            if (SplitPaymentRules.problem(box) != Problem.NONE) return;
            for (PaymentLine line : SplitPaymentRules.lines(box)) {
                if (line.method() != PaymentMethod.GIFT_CARD) continue;
                Card source = box.cards().stream().filter(c -> line.reference().equals(c.code()) && line.amount().compareTo(c.value()) == 0).findFirst().orElse(null);
                assertNotNull("a card line comes from a card typed for that amount", source);
                assertTrue("never more than the card holds", line.amount().compareTo(BigDecimal.valueOf(source.balance())) <= 0);
            }
        });
    }
}
