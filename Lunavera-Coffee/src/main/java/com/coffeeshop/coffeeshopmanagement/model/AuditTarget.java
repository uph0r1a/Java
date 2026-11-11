package com.coffeeshop.coffeeshopmanagement.model;

/** The kinds of thing an audit line can be about (TODO 4.2), stored in {@code audit_log.target_type}. */
public final class AuditTarget {
    public static final String ORDER = "ORDER";
    public static final String PRODUCT = "PRODUCT";
    /** An ingredient (TODO 4.41); the id is the ingredient's id. */
    public static final String INGREDIENT = "INGREDIENT";
    /** A login account; the id is the user's id. */
    public static final String ACCOUNT = "ACCOUNT";
    /** A staff record; the id is the employee's id. */
    public static final String STAFF = "STAFF";
    public static final String CUSTOMER = "CUSTOMER";
    public static final String SHIFT = "SHIFT";
    /** A gift card (TODO 7); the id is the card's id and the label its code. */
    public static final String GIFT_CARD = "GIFT_CARD";
    public static final String BACKUP = "BACKUP";
    /** One of the shop's rules set in Settings (TODO 4.49); the label is the rule's name, the old and new value are the numbers. */
    public static final String SETTING = "SETTING";

    /** The shop itself (TODO 4.57, the test data was cleared before opening day); the label is the shop's name. */
    public static final String SHOP = "SHOP";

    /** An expense or cash taken out of the drawer (TODO 2A.44); the id is the expense's id and the label its category. */
    public static final String EXPENSE = "EXPENSE";

    /** A voucher code (TODO 2A.44); the id is the voucher's id and the label its code. */
    public static final String VOUCHER = "VOUCHER";

    private AuditTarget() {
    }
}
