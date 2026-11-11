package com.coffeeshop.coffeeshopmanagement.model;

import java.util.Optional;

/**
 * What an audit line (TODO 4.2) says happened. The name is what is stored in {@code audit_log.action}, so an entry can be renamed
 * on screen without touching old rows; the label key is a literal in each constant so the language-file checks see it is used.
 */
public enum AuditAction {
    DISCOUNT_GIVEN("audit.action.DISCOUNT_GIVEN"),
    ORDER_REFUNDED("audit.action.ORDER_REFUNDED"),
    ORDER_REFUNDED_NO_STOCK("audit.action.ORDER_REFUNDED_NO_STOCK"),
    ORDER_LINES_REFUNDED("audit.action.ORDER_LINES_REFUNDED"),
    TABLE_EMPTIED("audit.action.TABLE_EMPTIED"),
    TABLE_TRANSFERRED("audit.action.TABLE_TRANSFERRED"),
    TABLES_MERGED("audit.action.TABLES_MERGED"),
    BILL_SPLIT("audit.action.BILL_SPLIT"),
    TAKE_AWAY_CANCELLED("audit.action.TAKE_AWAY_CANCELLED"),
    RESERVATIONS_CLOSED("audit.action.RESERVATIONS_CLOSED"),
    PRODUCT_PRICE("audit.action.PRODUCT_PRICE"),
    PRODUCT_COST("audit.action.PRODUCT_COST"),
    PRODUCT_STOCK("audit.action.PRODUCT_STOCK"),
    INGREDIENT_SAVED("audit.action.INGREDIENT_SAVED"),
    INGREDIENT_STOCK("audit.action.INGREDIENT_STOCK"),
    INGREDIENT_DELETED("audit.action.INGREDIENT_DELETED"),
    RECIPE_CHANGED("audit.action.RECIPE_CHANGED"),
    OPTIONS_CHANGED("audit.action.OPTIONS_CHANGED"),
    ACCOUNT_CREATED("audit.action.ACCOUNT_CREATED"),
    ACCOUNT_ROLE_CHANGED("audit.action.ACCOUNT_ROLE_CHANGED"),
    ACCOUNT_LOCKED("audit.action.ACCOUNT_LOCKED"),
    ACCOUNT_UNLOCKED("audit.action.ACCOUNT_UNLOCKED"),
    PASSWORD_RESET("audit.action.PASSWORD_RESET"),
    PASSWORD_CHANGED("audit.action.PASSWORD_CHANGED"),
    STAFF_REMOVED("audit.action.STAFF_REMOVED"),
    STAFF_ANONYMISED("audit.action.STAFF_ANONYMISED"),
    CUSTOMER_MERGED("audit.action.CUSTOMER_MERGED"),
    CUSTOMER_ANONYMISED("audit.action.CUSTOMER_ANONYMISED"),
    SHIFT_CLOSED("audit.action.SHIFT_CLOSED"),
    CLOCK_CORRECTED("audit.action.CLOCK_CORRECTED"),
    GIFT_CARD_SOLD("audit.action.GIFT_CARD_SOLD"),
    GIFT_CARD_VOIDED("audit.action.GIFT_CARD_VOIDED"),
    BIRTHDAY_CHANGED("audit.action.BIRTHDAY_CHANGED"),
    BIRTHDAY_DISCOUNT_USED("audit.action.BIRTHDAY_DISCOUNT_USED"),
    BACKUP_MADE("audit.action.BACKUP_MADE"),
    RECEIPT_REPRINTED("audit.action.RECEIPT_REPRINTED"),
    RESTORE_APPLIED("audit.action.RESTORE_APPLIED"),
    RULE_CHANGED("audit.action.RULE_CHANGED"),
    BACKUP_RESTORED("audit.action.BACKUP_RESTORED"),
    SALES_STARTED("audit.action.SALES_STARTED"),
    COMPLIMENTARY_GIVEN("audit.action.COMPLIMENTARY_GIVEN"),
    PIN_SWITCHED_OFF("audit.action.PIN_SWITCHED_OFF"),
    PIN_TILL_CLOSED("audit.action.PIN_TILL_CLOSED"),
    GIFT_CARD_GUESSING("audit.action.GIFT_CARD_GUESSING"),
    EXPENSE_RECORDED("audit.action.EXPENSE_RECORDED"),
    EXPENSE_DELETED("audit.action.EXPENSE_DELETED"),
    VOUCHER_CREATED("audit.action.VOUCHER_CREATED"),
    VOUCHER_SWITCHED("audit.action.VOUCHER_SWITCHED"),
    TRANSFER_QR_CHANGED("audit.action.TRANSFER_QR_CHANGED"),
    SETTINGS_CHANGED("audit.action.SETTINGS_CHANGED");

    private final String labelKey;

    AuditAction(String labelKey) {
        this.labelKey = labelKey;
    }

    /** The language key of the name shown for this action. */
    public String labelKey() {
        return labelKey;
    }

    /** The action stored under this name, or empty for a name this version does not know (an entry from a newer program). */
    public static Optional<AuditAction> fromDb(String stored) {
        if (stored == null) return Optional.empty();
        for (AuditAction a : values()) {
            if (a.name().equals(stored)) return Optional.of(a);
        }
        return Optional.empty();
    }
}
