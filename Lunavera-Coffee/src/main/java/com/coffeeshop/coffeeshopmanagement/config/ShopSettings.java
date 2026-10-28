package com.coffeeshop.coffeeshopmanagement.config;

import com.coffeeshop.coffeeshopmanagement.service.Limits;
import com.coffeeshop.coffeeshopmanagement.service.StockLevel;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.TextNormalizer;

import java.util.Map;
import java.util.Optional;

/**
 * The name of the shop (TODO 4.15), kept in the database ({@code app_flags}) so every till of the shop shows the same one. It is
 * what the window title and the text of the transfer QR carry. Until the owner sets one it is {@link #DEFAULT_NAME}.
 *
 * <p>{@link #cachedName()} never touches the database (it is used while building text, also in tests); {@link #load()} reads it
 * once at start and {@link #setName} keeps it up to date.
 */
public final class ShopSettings {

    public static final String DEFAULT_NAME = "Lunavera Coffee";
    public static final int NAME_MAX = 60;

    /** The prefix of the flag that holds the name: {@code shopname:<name>} (no % or _, which the prefix lookup treats as wildcards). */
    public static final String PREFIX = "shopname:";

    /** Address and phone printed under the shop name on the receipt (TODO 4.7). The name column of {@code app_flags} holds 100 characters. */
    public static final String ADDRESS_PREFIX = "shopaddr:";
    public static final String PHONE_PREFIX = "shopphone:";
    public static final int ADDRESS_MAX = 80;
    public static final int PHONE_MAX = 20;

    /**
     * The shop's tax code (mã số thuế), optional, printed on both receipts when filled in (TODO 4.22). Digits with at most one
     * hyphen, 10 to 13 digits in all, so a 10-digit code, a 10-3 branch code and a 12-digit personal number all fit; WHICH number
     * the shop must print, and whether it must issue e-invoices, is for its accountant to say. 14 characters with the hyphen.
     */
    public static final String TAX_PREFIX = "shoptax:";
    public static final int TAX_CODE_MAX = 14;

    /** The shop-wide reorder level (TODO 4.7), kept as {@code lowstock:<number>}; see {@link StockLevel}. */
    public static final String LOW_STOCK_PREFIX = "lowstock:";

    private static volatile String cached = DEFAULT_NAME;
    private static volatile String cachedAddress = "";
    private static volatile String cachedPhone = "";
    private static volatile String cachedTaxCode = "";

    private ShopSettings() {}

    /** The name read by {@link #load()} or set by {@link #setName}; the default before that. Never reads the database. */
    public static String cachedName() {
        return cached;
    }

    /** The address printed on the receipt, or an empty text. Never reads the database. */
    public static String cachedAddress() {
        return cachedAddress;
    }

    /** The phone number printed on the receipt, or an empty text. Never reads the database. */
    public static String cachedPhone() {
        return cachedPhone;
    }

    /** The tax code printed on the receipt, or an empty text. Never reads the database. */
    public static String cachedTaxCode() {
        return cachedTaxCode;
    }

    /** The tax code as it is stored: spaces taken out, so "0312 345 678" and "0312345678" are one code. Null stays null. */
    public static String normalizeTaxCode(String text) {
        return text == null ? null : text.replaceAll("\\s+", "");
    }

    /** Why this tax code cannot be kept (in the language in use), or null. Blank is fine: the code is optional. */
    public static String taxCodeProblem(String text) {
        String code = normalizeTaxCode(text);
        if (code == null || code.isEmpty()) return null;
        if (code.length() <= TAX_CODE_MAX && code.matches("[0-9]+(-[0-9]+)?")) {
            long digits = code.chars().filter(Character::isDigit).count();
            if (digits >= 10 && digits <= 13) return null;
        }
        return I18n.t("settings.tax.invalid");
    }

    /** The receipt's lines under the shop name: the address and the phone, each only when the owner typed one. */
    public static java.util.List<String> receiptDetailLines() {
        java.util.List<String> lines = new java.util.ArrayList<>(2);
        if (!cachedAddress.isEmpty()) lines.add(cachedAddress);
        if (!cachedPhone.isEmpty()) lines.add(cachedPhone);
        return lines;
    }

    /** Reads the name, address, phone and the reorder level from the database. A failure keeps what is already known and is not an error for the caller. */
    public static String load() {
        try {
            Optional<String> stored = AppFlags.valueWithPrefix(PREFIX);
            cached = stored.filter(name -> !name.isBlank()).orElse(DEFAULT_NAME);
            cachedAddress = AppFlags.valueWithPrefix(ADDRESS_PREFIX).map(ShopSettings::clean).orElse("");
            cachedPhone = AppFlags.valueWithPrefix(PHONE_PREFIX).map(ShopSettings::clean).orElse("");
            cachedTaxCode = AppFlags.valueWithPrefix(TAX_PREFIX).map(ShopSettings::normalizeTaxCode).orElse("");
            Integer level = AppFlags.valueWithPrefix(LOW_STOCK_PREFIX).map(StockLevel::parse).orElse(null);
            StockLevel.setDefaultLevel(level == null ? StockLevel.DEFAULT : level);
        } catch (RuntimeException e) {
            // keep what we have: a shop name is not worth stopping the program for
        }
        return cached;
    }

    /**
     * Saves everything the settings screen holds (TODO 4.7) in ONE transaction, so a failure never leaves half of it saved: the
     * texts are cleaned like {@link #clean}, anything {@link #problemWith} refuses throws {@link IllegalArgumentException} and
     * nothing is written. The known values change only after the database accepted them.
     */
    public static void saveAll(String name, String address, String phone, String lowStockLevel) {
        saveAll(name, address, phone, cachedTaxCode, lowStockLevel); // the tax code is left as it is
    }

    /** Like the four-text form, with the tax code (TODO 4.22): blank clears it, anything else must pass {@link #taxCodeProblem}. */
    public static void saveAll(String name, String address, String phone, String taxCode, String lowStockLevel) {
        String cleanName = clean(name);
        String cleanAddress = clean(address);
        String cleanPhone = clean(phone);
        String cleanTax = taxCode == null ? "" : normalizeTaxCode(taxCode);
        String problem = problemWith(name, address, phone, taxCode, lowStockLevel);
        if (problem != null) {
            throw new IllegalArgumentException(problem);
        }
        int level = StockLevel.parse(lowStockLevel);
        Map<String, String> values = new java.util.LinkedHashMap<>();
        values.put(PREFIX, cleanName);
        values.put(ADDRESS_PREFIX, cleanAddress);
        values.put(PHONE_PREFIX, cleanPhone);
        values.put(TAX_PREFIX, cleanTax);
        values.put(LOW_STOCK_PREFIX, Integer.toString(level));
        AppFlags.replaceValues(values);
        cached = cleanName;
        cachedAddress = cleanAddress;
        cachedPhone = cleanPhone;
        cachedTaxCode = cleanTax;
        StockLevel.setDefaultLevel(level);
    }

    /** The first thing wrong with what was typed on the settings screen (in the language in use), or null. */
    public static String problemWith(String name, String address, String phone, String lowStockLevel) {
        return problemWith(name, address, phone, "", lowStockLevel);
    }

    /** The same with the tax code of TODO 4.22 (blank is fine). */
    public static String problemWith(String name, String address, String phone, String taxCode, String lowStockLevel) {
        String taxProblem = taxCodeProblem(taxCode);
        String problem = problem(clean(name));
        if (problem != null) return problem;
        if (clean(address).length() > ADDRESS_MAX) return I18n.t("settings.address.long", ADDRESS_MAX);
        String cleanPhone = clean(phone);
        if (cleanPhone.length() > PHONE_MAX || !cleanPhone.matches("[0-9 +().\\-]*")) {
            return I18n.t("settings.phone.invalid", PHONE_MAX);
        }
        if (taxProblem != null) return taxProblem;
        if (StockLevel.parse(lowStockLevel) == null) return I18n.t("settings.lowstock.invalid", Limits.MAX_STOCK);
        return null;
    }

    /** Saves the name for the whole shop. {@code typed} is cleaned like {@link #clean}; a name {@link #problem} refuses is not saved. */
    public static void setName(String typed) {
        String name = clean(typed);
        String problem = problem(name);
        if (problem != null) {
            throw new IllegalArgumentException(problem);
        }
        AppFlags.replaceValues(Map.of(PREFIX, name));
        cached = name;
    }

    /** The name as it is kept: accents in one form, spaces at the ends removed, runs of spaces made one. */
    public static String clean(String typed) {
        return typed == null ? "" : TextNormalizer.nfc(typed).trim().replaceAll("\\s+", " ");
    }

    /** Why this (cleaned) name cannot be used, in the language in use, or null. */
    public static String problem(String cleanName) {
        if (cleanName == null || cleanName.isEmpty()) return I18n.t("setup.name.blank");
        if (cleanName.length() > NAME_MAX) return I18n.t("setup.name.long", NAME_MAX);
        return null;
    }

    /** For tests: forget the name and use the default again. */
    public static void resetForTests() {
        cached = DEFAULT_NAME;
        cachedAddress = "";
        cachedPhone = "";
        cachedTaxCode = "";
        StockLevel.resetForTests();
    }
}
