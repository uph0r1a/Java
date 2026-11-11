package com.coffeeshop.coffeeshopmanagement.model;

/**
 * The names stored in {@code audit_log.target_label} for a changed setting (TODO 2A.44): the shop's details and the bank-transfer QR.
 * They are the same in every language; {@code AuditText} turns them into words. Kept here, with no database or window code, so the
 * text class and its tests need nothing else.
 */
public final class AuditSetting {
    public static final String SHOP_NAME = "shopName";
    public static final String SHOP_ADDRESS = "shopAddress";
    public static final String SHOP_PHONE = "shopPhone";
    public static final String SHOP_TAX_CODE = "shopTaxCode";
    public static final String LOW_STOCK_LEVEL = "lowStockLevel";
    public static final String DAY_START = "dayStart";
    public static final String TRANSFER_QR = "transfer-qr";

    private AuditSetting() {
    }
}
