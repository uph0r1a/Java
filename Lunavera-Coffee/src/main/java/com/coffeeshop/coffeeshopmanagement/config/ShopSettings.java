package com.coffeeshop.coffeeshopmanagement.config;

import com.coffeeshop.coffeeshopmanagement.service.Limits;
import com.coffeeshop.coffeeshopmanagement.service.ShopRules;
import com.coffeeshop.coffeeshopmanagement.service.StockLevel;
import com.coffeeshop.coffeeshopmanagement.util.BusinessDay;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.TextNormalizer;

import java.util.Map;
import java.util.Optional;

/**
 * The name of the shop (TODO 4.15), kept in the database ({@code settings} table since TODO 6.74; before that in {@code app_flags}) so every till of the shop shows the same one. It is
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

    /** Address and phone printed under the shop name on the receipt (TODO 4.7). The limit is what fits a receipt line; since TODO 6.74 it is no longer the 100-character name column of {@code app_flags}. */
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

    /**
     * The shop's own rules (TODO 4.49), one flag each: the cashier's discount limit and the loyalty points numbers; see
     * {@link ShopRules}. No % or _ in a prefix.
     */
    public static final String RULE_STAFF_PREFIX = "rulestaff:";
    public static final String RULE_EARN_PREFIX = "ruleearn:";
    public static final String RULE_SPEND_PREFIX = "rulespend:";
    public static final String RULE_BILL_PREFIX = "rulebill:";
    public static final String RULE_MIN_PREFIX = "rulemin:";
    public static final String RULE_SILVER_PREFIX = "rulesilver:";
    public static final String RULE_GOLD_PREFIX = "rulegold:";
    public static final String RULE_BIRTHDAY_PREFIX = "rulebirthday:";
    public static final String RULE_SLOT_PREFIX = "ruleslot:";
    public static final String RULE_IDLE_PREFIX = "ruleidle:";
    public static final String RULE_FORGOTTEN_PREFIX = "ruleforgot:";
    /**
     * When the shop's day starts (TODO 4.50), kept as {@code daystart:HH:mm}; none stored means 00:00, the calendar day. See
     * {@link BusinessDay}: this class only keeps the value in the database and hands it to that one.
     */
    public static final String DAY_START_PREFIX = "daystart:";
    public static final String RULE_SALARY_PREFIX = "rulesalary:";
    public static final String RULE_MONTHLY_HOURS_PREFIX = "rulehours:";
    /** Free cups a cashier may give one person in a day (TODO 4.61). */
    public static final String RULE_FREE_PREFIX = "rulefree:";

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
        return text == null ? null : TextNormalizer.plain(text).replaceAll("\\s+", ""); // TODO 3.71: no invisible characters in a tax code
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
            loadRules();
            PriceNote.load(); // TODO 2.103
            Integer dayStart = AppFlags.valueWithPrefix(DAY_START_PREFIX).map(BusinessDay::parse).orElse(0);
            BusinessDay.setStartMinutes(dayStart == null ? 0 : dayStart); // a stored value that is not a time reads as the calendar day
        } catch (RuntimeException e) {
            // keep what we have: a shop name is not worth stopping the program for
        }
        return cached;
    }

    /** Reads the shop's rules (TODO 4.49); a rule never saved, or a saved set that is not valid, stays at the factory value. */
    private static void loadRules() {
        ShopRules.Values d = ShopRules.DEFAULTS;
        String staff = AppFlags.valueWithPrefix(RULE_STAFF_PREFIX).orElse("");
        String earn = AppFlags.valueWithPrefix(RULE_EARN_PREFIX).orElse("");
        String spend = AppFlags.valueWithPrefix(RULE_SPEND_PREFIX).orElse("");
        String bill = AppFlags.valueWithPrefix(RULE_BILL_PREFIX).orElse("");
        String min = AppFlags.valueWithPrefix(RULE_MIN_PREFIX).orElse("");
        String silver = AppFlags.valueWithPrefix(RULE_SILVER_PREFIX).orElse("");
        String gold = AppFlags.valueWithPrefix(RULE_GOLD_PREFIX).orElse("");
        String birthday = AppFlags.valueWithPrefix(RULE_BIRTHDAY_PREFIX).orElse("");
        String slot = AppFlags.valueWithPrefix(RULE_SLOT_PREFIX).orElse("");
        String idle = AppFlags.valueWithPrefix(RULE_IDLE_PREFIX).orElse("");
        String forgotten = AppFlags.valueWithPrefix(RULE_FORGOTTEN_PREFIX).orElse("");
        String salary = AppFlags.valueWithPrefix(RULE_SALARY_PREFIX).orElse("");
        String monthlyHours = AppFlags.valueWithPrefix(RULE_MONTHLY_HOURS_PREFIX).orElse("");
        String free = AppFlags.valueWithPrefix(RULE_FREE_PREFIX).orElse("");
        ShopRules.Values loaded = ShopRules.parse(staff, earn, spend, bill, min, silver, gold, birthday, slot, idle, forgotten, salary, monthlyHours, free);
        ShopRules.set(loaded == null ? d : loaded);
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
        saveAll(name, address, phone, taxCode, lowStockLevel, (ShopRules.Values) null); // the rules and the day start are left as they are
    }

    /** Like the five-text form, and also the shop's rules (TODO 4.49) in the same transaction; the day start is left as it is. */
    public static void saveAll(String name, String address, String phone, String taxCode, String lowStockLevel, ShopRules.Values rules) {
        saveAll(name, address, phone, taxCode, lowStockLevel, rules, BusinessDay.format(BusinessDay.startMinutes()));
    }

    /** Like the five-text form, with when the shop's day starts (TODO 4.50), as {@code HH:mm} from 00:00 to 11:59; the rules are left as they are. */
    public static void saveAll(String name, String address, String phone, String taxCode, String lowStockLevel, String dayStart) {
        saveAll(name, address, phone, taxCode, lowStockLevel, (ShopRules.Values) null, dayStart);
    }

    /**
     * Like the five-text form, with the shop's rules (TODO 4.49) and when the shop's day starts (TODO 4.50) in the same transaction;
     * {@code rules} null leaves the rules alone, and a set {@link ShopRules#problem} refuses throws {@link IllegalArgumentException}
     * with nothing written.
     */
    public static void saveAll(String name, String address, String phone, String taxCode, String lowStockLevel, ShopRules.Values rules, String dayStart) {
        if (rules != null && ShopRules.problem(rules) != null) {
            throw new IllegalArgumentException(ShopRules.problem(rules));
        }
        String cleanName = clean(name);
        String cleanAddress = clean(address);
        String cleanPhone = clean(phone);
        String cleanTax = taxCode == null ? "" : normalizeTaxCode(taxCode);
        String problem = problemWith(name, address, phone, taxCode, lowStockLevel, dayStart);
        if (problem != null) {
            throw new IllegalArgumentException(problem);
        }
        int level = StockLevel.parse(lowStockLevel);
        int startMinutes = BusinessDay.parse(dayStart);
        Map<String, String> values = new java.util.LinkedHashMap<>();
        values.put(PREFIX, cleanName);
        values.put(ADDRESS_PREFIX, cleanAddress);
        values.put(PHONE_PREFIX, cleanPhone);
        values.put(TAX_PREFIX, cleanTax);
        values.put(LOW_STOCK_PREFIX, Integer.toString(level));
        values.put(DAY_START_PREFIX, BusinessDay.format(startMinutes));
        if (rules != null) {
            values.put(RULE_STAFF_PREFIX, Integer.toString(rules.staffDiscountPercent()));
            values.put(RULE_EARN_PREFIX, Long.toString(rules.earnDong()));
            values.put(RULE_SPEND_PREFIX, Long.toString(rules.spendDong()));
            values.put(RULE_BILL_PREFIX, Integer.toString(rules.maxPercentOfBill()));
            values.put(RULE_MIN_PREFIX, Integer.toString(rules.minPoints()));
            values.put(RULE_SILVER_PREFIX, Integer.toString(rules.silverFrom()));
            values.put(RULE_GOLD_PREFIX, Integer.toString(rules.goldFrom()));
            values.put(RULE_BIRTHDAY_PREFIX, Integer.toString(rules.birthdayPercent()));
            values.put(RULE_SLOT_PREFIX, Integer.toString(rules.slotMinutes()));
            values.put(RULE_IDLE_PREFIX, Integer.toString(rules.idleMinutes()));
            values.put(RULE_FORGOTTEN_PREFIX, Integer.toString(rules.forgottenHours()));
            values.put(RULE_SALARY_PREFIX, Integer.toString(rules.salaryHourly()));
            values.put(RULE_MONTHLY_HOURS_PREFIX, Integer.toString(rules.monthlyHours()));
            values.put(RULE_FREE_PREFIX, Integer.toString(rules.freeDrinksPerDay()));
        }
        // Each rule the owner changed gets its own audit line, saved in the same transaction (TODO 4.49).
        final java.util.List<ShopRules.Change> changed = rules == null ? java.util.List.of() : ShopRules.changes(ShopRules.current(), rules);
        // The shop's own details too, each one that differs from what is in use now (TODO 2A.44); the rules above are the other half.
        final java.util.List<String[]> details = detailChanges(cleanName, cleanAddress, cleanPhone, cleanTax, level, BusinessDay.format(startMinutes));
        AppFlags.replaceValues(values, connection -> {
            for (ShopRules.Change change : changed) {
                com.coffeeshop.coffeeshopmanagement.dao.AuditDAO.write(connection, com.coffeeshop.coffeeshopmanagement.model.AuditAction.RULE_CHANGED,
                        com.coffeeshop.coffeeshopmanagement.model.AuditTarget.SETTING, null, change.name(),
                        Long.toString(change.before()), Long.toString(change.after()), null);
            }
            for (String[] change : details) {
                com.coffeeshop.coffeeshopmanagement.dao.AuditDAO.write(connection, com.coffeeshop.coffeeshopmanagement.model.AuditAction.SETTINGS_CHANGED,
                        com.coffeeshop.coffeeshopmanagement.model.AuditTarget.SETTING, null, change[0], change[1], change[2], null);
            }
        });
        cached = cleanName;
        cachedAddress = cleanAddress;
        cachedPhone = cleanPhone;
        cachedTaxCode = cleanTax;
        StockLevel.setDefaultLevel(level);
        if (rules != null) ShopRules.set(rules);
        BusinessDay.setStartMinutes(startMinutes); // only after the database accepted it
    }

    /** Names of the settings in the audit line (TODO 2A.44), the same in every language: the screen turns them into words. */
    public static final String AUDIT_NAME = com.coffeeshop.coffeeshopmanagement.model.AuditSetting.SHOP_NAME;
    public static final String AUDIT_ADDRESS = com.coffeeshop.coffeeshopmanagement.model.AuditSetting.SHOP_ADDRESS;
    public static final String AUDIT_PHONE = com.coffeeshop.coffeeshopmanagement.model.AuditSetting.SHOP_PHONE;
    public static final String AUDIT_TAX_CODE = com.coffeeshop.coffeeshopmanagement.model.AuditSetting.SHOP_TAX_CODE;
    public static final String AUDIT_LOW_STOCK = com.coffeeshop.coffeeshopmanagement.model.AuditSetting.LOW_STOCK_LEVEL;
    public static final String AUDIT_DAY_START = com.coffeeshop.coffeeshopmanagement.model.AuditSetting.DAY_START;

    /**
     * What differs between the shop's details in use now and the ones about to be saved, as {@code {name, before, after}} (an empty
     * text where nothing was set). The texts are the cleaned ones that will be stored.
     */
    static java.util.List<String[]> detailChanges(String name, String address, String phone, String taxCode, int lowStockLevel, String dayStart) {
        java.util.List<String[]> list = new java.util.ArrayList<>();
        addDetail(list, AUDIT_NAME, cached, name);
        addDetail(list, AUDIT_ADDRESS, cachedAddress, address);
        addDetail(list, AUDIT_PHONE, cachedPhone, phone);
        addDetail(list, AUDIT_TAX_CODE, cachedTaxCode, taxCode);
        addDetail(list, AUDIT_LOW_STOCK, Integer.toString(StockLevel.defaultLevel()), Integer.toString(lowStockLevel));
        addDetail(list, AUDIT_DAY_START, BusinessDay.format(BusinessDay.startMinutes()), dayStart);
        return list;
    }

    private static void addDetail(java.util.List<String[]> list, String name, String before, String after) {
        String b = before == null ? "" : before;
        String a = after == null ? "" : after;
        if (!b.equals(a)) list.add(new String[]{name, b, a});
    }

    /** The first thing wrong with what was typed on the settings screen (in the language in use), or null. */
    public static String problemWith(String name, String address, String phone, String lowStockLevel) {
        return problemWith(name, address, phone, "", lowStockLevel);
    }

    /** The same with the tax code of TODO 4.22 (blank is fine). */
    public static String problemWith(String name, String address, String phone, String taxCode, String lowStockLevel) {
        return problemWith(name, address, phone, taxCode, lowStockLevel, BusinessDay.format(BusinessDay.startMinutes()));
    }

    /** The same with when the shop's day starts (TODO 4.50): {@code HH:mm} from 00:00 to 11:59. */
    public static String problemWith(String name, String address, String phone, String taxCode, String lowStockLevel, String dayStart) {
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
        if (BusinessDay.parse(dayStart) == null) return I18n.t("settings.daystart.invalid");
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
        BusinessDay.resetForTests();
        StockLevel.resetForTests();
        ShopRules.resetForTests();
    }
}
