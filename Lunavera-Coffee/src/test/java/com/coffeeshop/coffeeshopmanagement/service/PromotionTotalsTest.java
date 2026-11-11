package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.model.Voucher;
import com.coffeeshop.coffeeshopmanagement.service.VoucherRules.Lookup;
import org.junit.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.Assert.*;

/**
 * TODO 6.5: the two things {@code PromotionPanel} used to work out itself: what is left to pay after the typed discount and the voucher
 * ({@link PaymentRules#remaining}), and what typing a voucher code does ({@link VoucherRules#lookup}). No JavaFX, no database.
 */
public class PromotionTotalsTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 8);

    private static BigDecimal d(long v) {
        return BigDecimal.valueOf(v);
    }

    private static Voucher percent(int pct) {
        return new Voucher(1, "SALE" + pct, Voucher.PERCENT, pct, null, null, null, null, 0, true);
    }

    private static Voucher amount(long dong) {
        return new Voucher(2, "OFF" + dong, Voucher.AMOUNT, dong, null, null, null, null, 0, true);
    }

    // ---- the typed discount

    @Test
    public void aDiscountThatIsNotANumberOrMoreThanTheBillCountsAsNone() {
        assertEquals(d(0), PaymentRules.usableDiscount(null, d(100_000)));
        assertEquals(d(0), PaymentRules.usableDiscount(d(100_001), d(100_000)));
        assertEquals(d(100_000), PaymentRules.usableDiscount(d(100_000), d(100_000)));
        assertEquals(d(30_000), PaymentRules.usableDiscount(d(30_000), d(100_000)));
        assertEquals(d(0), PaymentRules.usableDiscount(d(0), d(100_000)));
    }

    @Test
    public void theVoucherFollowsTheTypedDiscountAndNeverTakesTheBillBelowZero() {
        assertEquals(d(0), PaymentRules.voucherTakes(null, d(100_000), d(0)));
        assertEquals("10% of the price", d(10_000), PaymentRules.voucherTakes(percent(10), d(100_000), d(0)));
        assertEquals("what is left after a typed 95.000", d(5_000), PaymentRules.voucherTakes(percent(10), d(100_000), d(95_000)));
        assertEquals("a fixed amount is cut to what is left", d(20_000), PaymentRules.voucherTakes(amount(50_000), d(100_000), d(80_000)));
        assertEquals("an unusable typed discount counts as none", d(50_000), PaymentRules.voucherTakes(amount(50_000), d(100_000), d(150_000)));
        assertEquals(d(50_000), PaymentRules.voucherTakes(amount(50_000), d(100_000), null));
    }

    @Test
    public void remainingIsTheBillLessTheDiscountAndTheVoucher() {
        assertEquals(d(100_000), PaymentRules.remaining(d(100_000), d(0), null));
        assertEquals(d(70_000), PaymentRules.remaining(d(100_000), d(30_000), null));
        assertEquals(d(60_000), PaymentRules.remaining(d(100_000), d(30_000), amount(10_000)));
        assertEquals("nothing is left but nothing is negative", d(0), PaymentRules.remaining(d(100_000), d(60_000), amount(90_000)));
        assertEquals("a discount over the bill is ignored", d(100_000), PaymentRules.remaining(d(100_000), d(100_001), null));
        assertEquals(d(0), PaymentRules.remaining(d(0), d(0), amount(5_000)));
    }

    @Test
    public void whateverIsTypedTheRemainingStaysBetweenZeroAndTheBill() {
        RandomCases.run("PaymentRules.remaining", 6000, random -> {
            BigDecimal payable = d(1000L * RandomCases.between(random, 0, 300));
            BigDecimal typed = switch (random.nextInt(4)) {
                case 0 -> null;
                case 1 -> d(RandomCases.between(random, 0, payable.longValue() + 50_000));
                case 2 -> d(0);
                default -> d(RandomCases.between(random, 0, payable.longValue()));
            };
            Voucher voucher = switch (random.nextInt(3)) {
                case 0 -> null;
                case 1 -> percent(RandomCases.between(random, 1, 100));
                default -> amount(RandomCases.between(random, 1L, 400_000L));
            };
            BigDecimal usable = PaymentRules.usableDiscount(typed, payable);
            BigDecimal takes = PaymentRules.voucherTakes(voucher, payable, typed);
            BigDecimal remaining = PaymentRules.remaining(payable, typed, voucher);
            assertTrue("the voucher takes nothing negative", takes.signum() >= 0);
            assertTrue("discount and voucher together are never more than the bill: " + usable + " + " + takes + " > " + payable,
                    usable.add(takes).compareTo(payable) <= 0);
            assertTrue("never negative", remaining.signum() >= 0);
            assertTrue("never more than the bill", remaining.compareTo(payable) <= 0);
            assertEquals("the figures add up", payable, remaining.add(usable).add(takes));
        });
    }

    // ---- typing a voucher code

    @Test
    public void nothingTypedClearsTheVoucherAndTheLine() {
        for (String typed : new String[]{null, "", "   "}) {
            Lookup result = VoucherRules.lookup(typed, null, d(100_000), TODAY);
            assertNull(result.voucher());
            assertEquals("", result.status());
        }
    }

    @Test
    public void aGoodCodeIsAppliedAndAWrongOneIsRefusedWithItsSentence() {
        Voucher good = percent(10);
        Lookup ok = VoucherRules.lookup("SALE10", good, d(100_000), TODAY);
        assertSame(good, ok.voucher());
        assertTrue(ok.status(), ok.status().contains("SALE10"));

        Lookup unknown = VoucherRules.lookup("NOPE", null, d(100_000), TODAY);
        assertNull(unknown.voucher());
        assertEquals(VoucherRules.message(VoucherRules.Problem.NOT_FOUND, null), unknown.status());

        Voucher late = new Voucher(3, "OLD", Voucher.PERCENT, 10, null, null, TODAY.minusDays(1), null, 0, true);
        Lookup expired = VoucherRules.lookup("OLD", late, d(100_000), TODAY);
        assertNull(expired.voucher());
        assertEquals(VoucherRules.message(VoucherRules.Problem.EXPIRED, late), expired.status());

        Voucher min = new Voucher(4, "BIG", Voucher.AMOUNT, 20_000, d(200_000), null, null, null, 0, true);
        assertNull(VoucherRules.lookup("BIG", min, d(100_000), TODAY).voucher());
        assertSame(min, VoucherRules.lookup("BIG", min, d(200_000), TODAY).voucher());
    }

    @Test
    public void theCheckIsMadeOnTheBillAfterThePromotionsAndAOncePerCustomerCodeSaysSo() {
        Voucher min = new Voucher(4, "BIG", Voucher.AMOUNT, 20_000, d(200_000), null, null, null, 0, true);
        assertNull("the bill after promotions is the one that counts", VoucherRules.lookup("BIG", min, d(199_999), TODAY).voucher());
        Voucher once = new Voucher(5, "ONCE", Voucher.AMOUNT, 5_000, null, null, null, null, 0, true, true);
        Voucher plain = new Voucher(6, "PLAIN", Voucher.AMOUNT, 5_000, null, null, null, null, 0, true, false);
        String withNote = VoucherRules.lookup("ONCE", once, d(100_000), TODAY).status();
        String without = VoucherRules.lookup("PLAIN", plain, d(100_000), TODAY).status();
        assertTrue(withNote, withNote.length() > without.length());
    }
}
