package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.model.Customer;
import com.coffeeshop.coffeeshopmanagement.service.CheckoutService.Payment;
import com.coffeeshop.coffeeshopmanagement.model.PaymentMethod;
import org.junit.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.Assert.*;

/** TODO 3.66 (rule D25): the birthday discount is given once a day, by its own button; these are the rules, with no screen and no database. */
public class BirthdayUseRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 5, 5);

    private static Customer customer(LocalDate birthday, LocalDate usedOn) {
        Customer c = new Customer();
        c.setBirthday(birthday);
        c.setBirthdayUsedOn(usedOn);
        return c;
    }

    @Test
    public void theDiscountIsOfferedOnTheBirthdayUntilItIsGiven() {
        assertTrue(BirthdayRules.isOffered(customer(LocalDate.of(2000, 5, 5), null), TODAY));
        assertFalse("not given twice the same day", BirthdayRules.isOffered(customer(LocalDate.of(2000, 5, 5), TODAY), TODAY));
        assertTrue("given on an earlier birthday: offered again", BirthdayRules.isOffered(customer(LocalDate.of(2000, 5, 5), TODAY.minusYears(1)), TODAY));
        assertFalse("another day", BirthdayRules.isOffered(customer(LocalDate.of(2000, 5, 6), null), TODAY));
        assertFalse("no date", BirthdayRules.isOffered(customer(null, null), TODAY));
        assertFalse(BirthdayRules.isOffered(null, TODAY));
    }

    @Test
    public void anAnonymousCustomerIsNeverOffered() {
        Customer c = customer(LocalDate.of(2000, 5, 5), null);
        c.setAnonymisedAt(LocalDateTime.now());
        assertFalse(BirthdayRules.isOffered(c, TODAY));
    }

    @Test
    public void usedTodayMeansExactlyToday() {
        assertTrue(BirthdayRules.usedToday(customer(null, TODAY), TODAY));
        assertFalse(BirthdayRules.usedToday(customer(null, TODAY.minusDays(1)), TODAY));
        assertFalse(BirthdayRules.usedToday(customer(null, null), TODAY));
        assertFalse(BirthdayRules.usedToday(null, TODAY));
    }

    @Test
    public void theBadgeSaysHowToGiveItOrThatItWasGiven() {
        String offered = BirthdayRules.badge(customer(LocalDate.of(2000, 5, 5), null), TODAY);
        String used = BirthdayRules.badge(customer(LocalDate.of(2000, 5, 5), TODAY), TODAY);
        assertFalse(offered.isBlank());
        assertFalse(used.isBlank());
        assertNotEquals("the badge changes once it was given", offered, used);
        assertTrue("a day with no birthday has no badge", BirthdayRules.badge(customer(LocalDate.of(2000, 5, 6), null), TODAY).isBlank());
    }

    @Test
    public void theBirthdayDiscountIsTheSuggestedPercentOfTheBill() {
        assertEquals(0, new BigDecimal("8500").compareTo(BirthdayRules.discountFor(new BigDecimal("85000"))));
        assertEquals(0, new BigDecimal("4000").compareTo(BirthdayRules.discountFor(new BigDecimal("40000"))));
    }

    @Test
    public void thePaymentNeedsACustomerAndExactlyTheButtonsAmount() {
        BigDecimal subtotal = new BigDecimal("40000");
        assertNull(BirthdayRules.problemToUse(7, subtotal, new BigDecimal("4000")));
        assertNotNull("no customer on the bill", BirthdayRules.problemToUse(null, subtotal, new BigDecimal("4000")));
        assertNotNull("a typed amount is not the birthday discount", BirthdayRules.problemToUse(7, subtotal, new BigDecimal("4001")));
        assertNotNull("none", BirthdayRules.problemToUse(7, subtotal, BigDecimal.ZERO));
        assertNotNull(BirthdayRules.problemToUse(7, subtotal, null));
        assertNotNull(BirthdayRules.problemToUse(7, null, new BigDecimal("4000")));
    }

    @Test
    public void aPaymentIsNotABirthdayPaymentUnlessMarked() {
        Payment plain = new Payment(PaymentMethod.CASH, new BigDecimal("4000"), "10%", new BigDecimal("36000"), null, null);
        assertFalse(plain.birthday());
        Payment marked = plain.withBirthday();
        assertTrue(marked.birthday());
        assertEquals(plain.discount(), marked.discount());
        assertEquals(plain.total(), marked.total());
        assertEquals(plain.method(), marked.method());
        assertFalse("the original is unchanged", plain.birthday());
    }
}
