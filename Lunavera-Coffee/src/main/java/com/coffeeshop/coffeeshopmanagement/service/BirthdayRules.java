package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.model.Customer;
import com.coffeeshop.coffeeshopmanagement.util.I18n;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.Month;

/**
 * Customer birthdays (TODO 7, rule D22: my choice, the owner has not answered). The till shows a badge on the day and suggests a
 * discount, but gives none by itself: the cashier presses the usual percentage button, so the cashier's limit and the written reason
 * ({@link DiscountPolicy}) still apply and every discount is still on the audit trail. The date is optional and personal data: it is
 * removed when the customer is made anonymous.
 *
 * <p><b>Once a day (TODO 3.66, rule D25: my choice, the owner has not answered).</b> The badge used to come back on every order of the
 * day, so a customer could be given the 10% again and again. Now the payment box has a "Birthday 10%" button for a customer whose
 * birthday it is and who has not had it today. Paying with it writes today's date into {@code customers.birthday_used_on} in the
 * payment's own transaction (a second till cannot give it twice: the write only succeeds if the day is not already there), the badge
 * then says it was given, and the button is gone. The plain percentage buttons still work for any other promotion and are not tracked.
 */
public final class BirthdayRules {

    /** The percentage the badge suggests: a setting of the owner (TODO 4.49), 10 until it is changed. */
    public static int suggestedPercent() {
        return ShopRules.current().birthdayPercent();
    }

    /** Nobody older than this is believed (a typing mistake such as 1026 is refused). */
    public static final int MAX_AGE_YEARS = 120;

    private BirthdayRules() {
    }

    /** The stored text (yyyy-MM-dd, or a longer date-time text) as a date; null for nothing or something unreadable. */
    public static LocalDate parse(String text) {
        if (text == null || text.isBlank()) return null;
        String day = text.trim();
        if (day.length() > 10) day = day.substring(0, 10);
        try {
            return LocalDate.parse(day);
        } catch (DateTimeException e) {
            return null;
        }
    }

    /** Null when {@code birthday} may be saved ({@code null} means "not given" and is fine), otherwise a language key's text. */
    public static String problem(LocalDate birthday, LocalDate today) {
        if (birthday == null) return null;
        if (birthday.isAfter(today)) return I18n.t("birthday.val.future");
        if (birthday.isBefore(today.minusYears(MAX_AGE_YEARS))) return I18n.t("birthday.val.old");
        return null;
    }

    /** True when {@code today} is the customer's birthday; 29 February is celebrated on 28 February in a year with no 29th. */
    public static boolean isBirthday(LocalDate birthday, LocalDate today) {
        if (birthday == null || today == null) return false;
        if (birthday.getMonth() == Month.FEBRUARY && birthday.getDayOfMonth() == 29 && !today.isLeapYear()) {
            return today.getMonth() == Month.FEBRUARY && today.getDayOfMonth() == 28;
        }
        return birthday.getMonth() == today.getMonth() && birthday.getDayOfMonth() == today.getDayOfMonth();
    }

    /** True when the discount was already given to this customer on {@code today}. */
    public static boolean usedToday(Customer customer, LocalDate today) {
        return customer != null && today != null && today.equals(customer.getBirthdayUsedOn());
    }

    /** True when the till should offer the birthday discount now: their birthday, not anonymous, and not given yet today. */
    public static boolean isOffered(Customer customer, LocalDate today) {
        return customer != null && !customer.isAnonymised() && isBirthday(customer.getBirthday(), today) && !usedToday(customer, today);
    }

    /**
     * What the till adds after the customer's name on their birthday: how to give the discount, or that it was already given today.
     * Empty on any other day, for no date, and for an anonymous customer.
     */
    public static String badge(Customer customer, LocalDate today) {
        if (customer == null || customer.isAnonymised() || !isBirthday(customer.getBirthday(), today)) return "";
        return "  ·  " + (usedToday(customer, today) ? I18n.t("birthday.badge.used") : I18n.t("birthday.badge", suggestedPercent()));
    }

    /** What the birthday button takes off a bill of {@code subtotal}: {@link #suggestedPercent()} percent, whole dong, as the other buttons do. */
    public static java.math.BigDecimal discountFor(java.math.BigDecimal subtotal) {
        return VoucherRules.percentOf(subtotal, suggestedPercent());
    }

    /**
     * What is wrong with giving the birthday discount, as a sentence for the cashier; null when it may be given. Checked in the checkout
     * service (the discount must be exactly what the button fills in: a typed or changed amount is not the birthday discount) and again,
     * against the database row, inside the payment's transaction.
     *
     * @param customerId the customer the order is for, null for none
     * @param discount   the typed discount of the order (the part that is not a voucher or points)
     */
    public static String problemToUse(Integer customerId, java.math.BigDecimal subtotal, java.math.BigDecimal discount) {
        if (customerId == null) return I18n.t("birthday.use.nocustomer");
        if (subtotal == null || discount == null || discountFor(subtotal).compareTo(discount) != 0) {
            return I18n.t("birthday.use.discount", suggestedPercent());
        }
        return null;
    }
}
