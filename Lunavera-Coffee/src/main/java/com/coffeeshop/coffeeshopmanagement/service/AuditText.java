package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.model.AuditAction;
import com.coffeeshop.coffeeshopmanagement.model.AuditEntry;
import com.coffeeshop.coffeeshopmanagement.model.AuditSetting;
import com.coffeeshop.coffeeshopmanagement.model.AuditTarget;
import com.coffeeshop.coffeeshopmanagement.util.CurrencyUtil;
import com.coffeeshop.coffeeshopmanagement.util.I18n;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Set;

/**
 * Turns an audit line into the words shown on the administrator screen (TODO 4.2), in the language of the screen. What is stored is
 * language-neutral (numbers as digits, roles and statuses as their names, a reason as typed), so an old line reads right after the
 * language is changed. No window here, so it can be tested.
 */
public final class AuditText {

    private static final Set<AuditAction> MONEY = EnumSet.of(AuditAction.DISCOUNT_GIVEN, AuditAction.PRODUCT_PRICE,
            AuditAction.PRODUCT_COST, AuditAction.ORDER_REFUNDED, AuditAction.ORDER_REFUNDED_NO_STOCK, AuditAction.ORDER_LINES_REFUNDED, AuditAction.SHIFT_CLOSED,
            AuditAction.TABLE_EMPTIED, AuditAction.TAKE_AWAY_CANCELLED, AuditAction.TABLES_MERGED, AuditAction.BILL_SPLIT, AuditAction.COMPLIMENTARY_GIVEN,
            AuditAction.EXPENSE_RECORDED, AuditAction.EXPENSE_DELETED);

    private AuditText() {
    }

    /** The name of the action, or the stored name itself for one this version does not know. */
    public static String action(AuditEntry entry) {
        return AuditAction.fromDb(entry.action()).map(a -> I18n.t(a.labelKey())).orElse(String.valueOf(entry.action()));
    }

    /** "Order #12", "Product: Latte", "Account: an": the kind of thing and its name (or its number when the name is gone). */
    public static String target(AuditEntry entry) {
        if (entry.targetType() == null) return "";
        String kind = switch (entry.targetType()) {
            case AuditTarget.ORDER -> I18n.t("audit.target.order");
            case AuditTarget.PRODUCT -> I18n.t("audit.target.product");
            case AuditTarget.ACCOUNT -> I18n.t("audit.target.account");
            case AuditTarget.STAFF -> I18n.t("audit.target.staff");
            case AuditTarget.CUSTOMER -> I18n.t("audit.target.customer");
            case AuditTarget.SHIFT -> I18n.t("audit.target.shift");
            case AuditTarget.BACKUP -> I18n.t("audit.target.backup");
            case AuditTarget.SHOP -> I18n.t("audit.target.shop");
            case AuditTarget.EXPENSE -> I18n.t("audit.target.expense");
            case AuditTarget.VOUCHER -> I18n.t("audit.target.voucher");
            case AuditTarget.SETTING -> I18n.t("audit.target.setting");
            default -> entry.targetType();
        };
        String name = entry.targetLabel() != null ? entry.targetLabel() : entry.targetId() != null ? "#" + entry.targetId() : "";
        if (AuditTarget.SETTING.equals(entry.targetType()) && entry.targetLabel() != null) {
            name = settingName(entry.targetLabel()); // the shop's details and the transfer QR in words; a rule keeps its name
        }
        return name.isEmpty() ? kind : kind + " " + name;
    }

    /** The before and after of the change: "45.000đ → 50.000đ", "Staff → Administrator", or just the new value. */
    public static String change(AuditEntry entry) {
        AuditAction action = AuditAction.fromDb(entry.action()).orElse(null);
        String before = value(action, entry.oldValue());
        String after = value(action, entry.newValue());
        if (before.isEmpty() && after.isEmpty()) return "";
        if (before.isEmpty()) return after;
        if (after.isEmpty()) {
            boolean clearable = action == AuditAction.SETTINGS_CHANGED || action == AuditAction.TRANSFER_QR_CHANGED;
            return clearable ? before + " \u2192 " + I18n.t("audit.value.none") : before; // a cleared text is stored as nothing
        }
        return before + " \u2192 " + after;
    }

    private static String value(AuditAction action, String stored) {
        if (stored == null || stored.isBlank()) return "";
        if (action != null && MONEY.contains(action) && stored.matches("-?[0-9]{1,15}")) {
            return CurrencyUtil.format(new BigDecimal(stored));
        }
        if (action == AuditAction.VOUCHER_CREATED) { // kind:value (TODO 2A.44)
            if (stored.matches("PERCENT:[0-9]{1,3}")) return stored.substring(8) + "%";
            if (stored.matches("AMOUNT:[0-9]{1,15}")) return CurrencyUtil.format(new BigDecimal(stored.substring(7)));
            return stored;
        }
        if (action == AuditAction.VOUCHER_SWITCHED) {
            switch (stored) {
                case "ON": return I18n.t("audit.value.on");
                case "OFF": return I18n.t("audit.value.off");
                default: return stored;
            }
        }
        if (action == AuditAction.ACCOUNT_CREATED || action == AuditAction.ACCOUNT_ROLE_CHANGED) {
            switch (stored) {
                case "ADMIN": return I18n.t("role.admin");
                case "EMPLOYEE": return I18n.t("role.employee");
                default: return stored;
            }
        }
        return stored;
    }

    /** The words for the name of a setting stored in an audit line (literal keys, so the language-file checks see them used). */
    private static String settingName(String stored) {
        return switch (stored) {
            case AuditSetting.SHOP_NAME -> I18n.t("audit.setting.shopName");
            case AuditSetting.SHOP_ADDRESS -> I18n.t("audit.setting.shopAddress");
            case AuditSetting.SHOP_PHONE -> I18n.t("audit.setting.shopPhone");
            case AuditSetting.SHOP_TAX_CODE -> I18n.t("audit.setting.shopTaxCode");
            case AuditSetting.LOW_STOCK_LEVEL -> I18n.t("audit.setting.lowStockLevel");
            case AuditSetting.DAY_START -> I18n.t("audit.setting.dayStart");
            case AuditSetting.TRANSFER_QR -> I18n.t("audit.setting.transfer-qr");
            default -> stored;
        };
    }

    /** A stored day (2026-11-07) as the shop writes days (07/11/2026); a text that is not a day is kept as it is. */
    private static String day(String stored) {
        try {
            return java.time.LocalDate.parse(stored).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        } catch (java.time.format.DateTimeParseException e) {
            return stored;
        }
    }

    /** The note: the reason typed, the table number, the cash difference... in words where the stored text is a code or a number. */
    public static String detail(AuditEntry entry) {
        String detail = entry.detail();
        if (detail == null || detail.isBlank()) return "";
        AuditAction action = AuditAction.fromDb(entry.action()).orElse(null);
        if (action == AuditAction.TABLE_EMPTIED && detail.matches("[0-9]{1,9}")) {
            return I18n.t("audit.detail.table", detail);
        }
        if (action == AuditAction.SHIFT_CLOSED && detail.matches("-?[0-9]{1,15}")) {
            return I18n.t("audit.detail.difference", CurrencyUtil.format(new BigDecimal(detail)));
        }
        if (action == AuditAction.CUSTOMER_MERGED && detail.matches("[0-9]{1,9}\\|[0-9]{1,9}")) {
            String[] parts = detail.split("\\|");
            return I18n.t("audit.detail.merge", parts[0], parts[1]);
        }
        if (action == AuditAction.SALES_STARTED && detail.matches("[0-9]{1,9}\\|[0-9]{1,9}\\|[01]")) {
            String[] parts = detail.split("\\|");
            return I18n.t(parts[2].equals("1") ? "audit.detail.sales.customers" : "audit.detail.sales", parts[0], parts[1]);
        }
        if (action == AuditAction.COMPLIMENTARY_GIVEN && detail.matches("[A-Z]{1,20}\\|[0-9]{1,9}\\|[0-9]{1,9}\\|.*")) { // kind|order|cups|note (TODO 4.61)
            String[] parts = detail.split("\\|", 4);
            com.coffeeshop.coffeeshopmanagement.service.ComplimentaryRules.Kind kind = com.coffeeshop.coffeeshopmanagement.service.ComplimentaryRules.Kind.fromDb(parts[0]);
            String text = I18n.t("audit.detail.free", kind == null ? parts[0] : kind.label(), parts[1], parts[2]);
            return parts[3].isBlank() ? text : text + " \u2014 " + parts[3];
        }
        if (action == AuditAction.PIN_SWITCHED_OFF && detail.matches("[0-9]{1,9}")) { // wrong PINs in a row (TODO 2A.46)
            return I18n.t("audit.detail.pin.off", detail);
        }
        if (action == AuditAction.GIFT_CARD_GUESSING && detail.matches("[0-9]{1,9}\\|[0-9]{1,9}")) { // unknown codes|seconds paused (TODO 2A.45)
            String[] parts = detail.split("\\|");
            return I18n.t("audit.detail.giftcard.guess", parts[0], parts[1]);
        }
        if (action == AuditAction.PIN_TILL_CLOSED && detail.matches("[0-9]{1,9}\\|[0-9]{1,9}")) { // wrong PINs|minutes closed
            String[] parts = detail.split("\\|");
            return I18n.t("audit.detail.pin.till", parts[0], parts[1]);
        }
        if ((action == AuditAction.EXPENSE_RECORDED || action == AuditAction.EXPENSE_DELETED)
                && detail.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}\\|[01]\\|(?s).*")) { // day|from the drawer|note (TODO 2A.44)
            String[] parts = detail.split("\\|", 3);
            String text = I18n.t(parts[1].equals("1") ? "audit.detail.expense.drawer" : "audit.detail.expense", day(parts[0]));
            return parts[2].isBlank() ? text : text + " \u2014 " + parts[2];
        }
        if (action == AuditAction.VOUCHER_CREATED && detail.matches("[0-9]{1,15}\\|[0-9-]{0,10}\\|[0-9-]{0,10}\\|[0-9]{0,9}\\|[01]")) { // min|from|to|max|once
            String[] parts = detail.split("\\|", -1);
            java.util.List<String> words = new java.util.ArrayList<>();
            if (!parts[0].equals("0")) words.add(I18n.t("audit.detail.voucher.min", CurrencyUtil.format(new BigDecimal(parts[0]))));
            if (!parts[1].isEmpty()) words.add(I18n.t("audit.detail.voucher.from", day(parts[1])));
            if (!parts[2].isEmpty()) words.add(I18n.t("audit.detail.voucher.to", day(parts[2])));
            if (!parts[3].isEmpty()) words.add(I18n.t("audit.detail.voucher.max", parts[3]));
            if (parts[4].equals("1")) words.add(I18n.t("audit.detail.voucher.once"));
            return String.join(", ", words);
        }
        if (action == AuditAction.TRANSFER_QR_CHANGED && detail.matches("(PICTURE|INFO|ACCEPTED)\\|[0-9a-f]{0,12}")) { // what|start of the picture's fingerprint
            String[] parts = detail.split("\\|", -1);
            String text = switch (parts[0]) {
                case "PICTURE" -> I18n.t("audit.detail.qr.picture");
                case "INFO" -> I18n.t("audit.detail.qr.info");
                default -> I18n.t("audit.detail.qr.accepted");
            };
            return parts[1].isEmpty() ? text : text + " (" + parts[1] + ")";
        }
        if (action == AuditAction.ACCOUNT_CREATED) {
            return "ACTIVE".equals(detail) ? I18n.t("accounts.status.active") : "LOCKED".equals(detail) ? I18n.t("accounts.status.locked") : detail;
        }
        return detail;
    }
}
