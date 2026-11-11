package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.model.Voucher;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * TODO 6.76: random vouchers on random bills. What a voucher takes off is never negative, never more than the bill still has to pay
 * (so the total can not go below 0), and a percentage is rounded down. See {@link RandomCases} to replay a failure.
 */
public class VoucherRulesPropertyTest {

    private static Voucher randomVoucher(Random random) {
        boolean percent = random.nextBoolean();
        long value = percent ? RandomCases.between(random, 1, 100) : RandomCases.between(random, 1, 3_000_000);
        return new Voucher(1, "CODE" + random.nextInt(100), percent ? Voucher.PERCENT : Voucher.AMOUNT, value, null, null, null, null, 0, true);
    }

    @Test
    public void aVoucherNeverTakesOffMoreThanIsLeftToPayAndNeverLessThanNothing() {
        RandomCases.run("VoucherRules.discountFor", 8000, random -> {
            Voucher voucher = randomVoucher(random);
            BigDecimal subtotal = BigDecimal.valueOf(RandomCases.between(random, -1000, 5_000_000));
            BigDecimal already = switch (random.nextInt(3)) {
                case 0 -> null;
                case 1 -> BigDecimal.valueOf(RandomCases.between(random, 0, 5_000_000));
                default -> subtotal.multiply(BigDecimal.valueOf(RandomCases.between(random, 0, 100))).divide(BigDecimal.valueOf(100), 0, java.math.RoundingMode.FLOOR);
            };
            BigDecimal off = VoucherRules.discountFor(voucher, subtotal, already);
            BigDecimal left = subtotal.subtract(already == null ? BigDecimal.ZERO : already);

            assertTrue("never negative: " + off, off.signum() >= 0);
            assertTrue("never more than what is left to pay: " + off + " > " + left, off.compareTo(left.max(BigDecimal.ZERO)) <= 0);
            assertTrue("the total after it is never below 0", left.subtract(off).signum() >= 0 || left.signum() < 0);
            assertEquals("whole dong", 0, off.remainder(BigDecimal.ONE).signum());
            if (left.signum() > 0) {
                BigDecimal wanted = Voucher.PERCENT.equals(voucher.kind()) ? VoucherRules.percentOf(subtotal, (int) voucher.value())
                        : BigDecimal.valueOf(voucher.value());
                assertEquals("it takes off what it is worth, or all that is left", wanted.min(left), off);
            } else {
                assertEquals("nothing left to pay: nothing taken off", 0, off.signum());
            }
        });
    }

    @Test
    public void aPercentageIsRoundedDownAndNeverMoreThanTheAmount() {
        RandomCases.run("VoucherRules.percentOf", 8000, random -> {
            BigDecimal amount = BigDecimal.valueOf(RandomCases.between(random, 0, 10_000_000));
            int percent = RandomCases.between(random, 0, 100);
            BigDecimal part = VoucherRules.percentOf(amount, percent);
            assertTrue("whole dong, not more than the amount", part.compareTo(amount) <= 0 && part.signum() >= 0);
            assertTrue("rounded down: " + part + " of " + percent + "% of " + amount,
                    part.multiply(BigDecimal.valueOf(100)).compareTo(amount.multiply(BigDecimal.valueOf(percent))) <= 0
                            && part.add(BigDecimal.ONE).multiply(BigDecimal.valueOf(100)).compareTo(amount.multiply(BigDecimal.valueOf(percent))) > 0);
        });
    }
}
