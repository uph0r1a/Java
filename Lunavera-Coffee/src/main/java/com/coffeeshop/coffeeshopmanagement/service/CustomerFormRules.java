package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.util.ValidationUtil;

/**
 * The checks on the customer add/edit form that need no database (TODO 6.21): name, phone format, e-mail format, and whether a phone
 * was changed. Kept apart from {@link CustomerService#save} so they can be tested without a database or JavaFX.
 */
public final class CustomerFormRules {

    private CustomerFormRules() {}

    /** What is wrong with the form, with the message keys the screen shows (title key, message key; a null message key means the service gives the text). */
    public enum Problem {
        NAME("common.missing.title", "customers.val.name"),
        PHONE("customers.val.phone.title", "customers.val.phone.message"),
        EMAIL("customers.val.email.title", "customers.val.email.message"),
        DUPLICATE_PHONE("customers.val.dup.title", "customers.val.dup.message"),
        BIRTHDAY("common.missing.title", null),
        PHONE_ADMIN_ONLY("admin.only.title", "customers.phone.adminonly");

        private final String titleKey;
        private final String messageKey;

        Problem(String titleKey, String messageKey) {
            this.titleKey = titleKey;
            this.messageKey = messageKey;
        }

        public String titleKey() {
            return titleKey;
        }

        /** The message key, or null when {@link CustomerService.SaveResult#detail()} carries the text. */
        public String messageKey() {
            return messageKey;
        }
    }

    /** The first problem in name, phone format and e-mail format (in the order the form has always checked them), or null. */
    public static Problem fieldProblem(String name, String phone, String email) {
        if (ValidationUtil.isBlank(name)) return Problem.NAME;
        if (!ValidationUtil.isBlank(phone) && !ValidationUtil.isValidPhone(phone)) return Problem.PHONE;
        if (!ValidationUtil.isBlank(email) && !ValidationUtil.isValidEmail(email)) return Problem.EMAIL;
        return null;
    }

    /** The phone as it is compared and stored: empty when none was typed, otherwise normalised. */
    public static String comparablePhone(String phone) {
        return ValidationUtil.isBlank(phone) ? "" : ValidationUtil.normalizePhone(phone);
    }

    /** True when an existing customer's phone is being changed (a new customer has nothing to change). */
    public static boolean phoneChanged(boolean existingCustomer, String typedPhone, String storedPhone) {
        return existingCustomer && !comparablePhone(typedPhone).equals(comparablePhone(storedPhone));
    }
}
