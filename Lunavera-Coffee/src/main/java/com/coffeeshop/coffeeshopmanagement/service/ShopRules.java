package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.util.I18n;

/**
 * The numbers of the shop's own rules that the owner may change in Settings (TODO 4.49): how much discount a cashier may give, the
 * loyalty points rule (what earns a point, what a point is worth, how much of a bill points may pay, the least that may be spent,
 * and where silver and gold start), the birthday discount that is suggested, the minutes one booking holds a table, the minutes of
 * no activity before a till signs out, the hours after which an open clock-in counts as forgotten, and how the salary of the staff
 * table is to be read for the pay estimate (a monthly figure or an hourly rate, and the hours in a month, TODO 4.60), and how many free
 * drinks a cashier may give one person in a day (TODO 4.61). Until the owner changes one the
 * value is the one the program always had ({@link #DEFAULTS}).
 *
 * <p>This class only holds and checks the numbers; {@code ShopSettings} reads and saves them in the database and
 * {@link DiscountPolicy} / {@link LoyaltyPolicy} apply them. A new value counts for the next payment: an order already paid keeps
 * the points and the discount it was saved with (the value of spent points is saved with the order, TODO 4.16).
 */
public final class ShopRules {

    /**
     * @param staffDiscountPercent most a cashier (not an administrator) may discount, in percent of the subtotal (0 = none)
     * @param earnDong             one point is earned for each full amount of this many dong of the final total
     * @param spendDong            what one point takes off a bill when it is spent, in dong
     * @param maxPercentOfBill     the share of a bill (after discount) that points may pay, in percent
     * @param minPoints            the least number of points that may be spent on one order
     * @param silverFrom           points from which a customer is silver
     * @param goldFrom             points from which a customer is gold
     * @param birthdayPercent      the discount suggested on a customer's birthday, in percent
     * @param slotMinutes          the minutes a booking holds its table: two bookings of one table closer than this clash
     * @param idleMinutes          minutes without activity before the till locks and then signs out (0 = never)
     * @param forgottenHours       hours after which a clock-in that was never closed counts as forgotten
     * @param salaryHourly         0: the salary in the staff table is a monthly figure, 1: it is an hourly rate (TODO 4.60)
     * @param monthlyHours         hours in a month's work, to turn a monthly salary into an hourly rate (TODO 4.60)
     * @param freeDrinksPerDay     most cups of free drink (staff, owner) a cashier may give one person in a shop day; 0 = none (TODO 4.61, rule D26)
     */
    public record Values(int staffDiscountPercent, long earnDong, long spendDong, int maxPercentOfBill, int minPoints,
                         int silverFrom, int goldFrom, int birthdayPercent, int slotMinutes, int idleMinutes, int forgottenHours,
                         int salaryHourly, int monthlyHours, int freeDrinksPerDay) {

        /** The thirteen numbers before the free drinks, with that one at what a new shop starts with (2 cups per person per day). */
        public Values(int staffDiscountPercent, long earnDong, long spendDong, int maxPercentOfBill, int minPoints, int silverFrom, int goldFrom,
                      int birthdayPercent, int slotMinutes, int idleMinutes, int forgottenHours, int salaryHourly, int monthlyHours) {
            this(staffDiscountPercent, earnDong, spendDong, maxPercentOfBill, minPoints, silverFrom, goldFrom, birthdayPercent, slotMinutes,
                    idleMinutes, forgottenHours, salaryHourly, monthlyHours, 2);
        }

        /** The eleven numbers before the pay estimate, with its two at what a new shop starts with (monthly salary, 208 hours). */
        public Values(int staffDiscountPercent, long earnDong, long spendDong, int maxPercentOfBill, int minPoints, int silverFrom, int goldFrom,
                      int birthdayPercent, int slotMinutes, int idleMinutes, int forgottenHours) {
            this(staffDiscountPercent, earnDong, spendDong, maxPercentOfBill, minPoints, silverFrom, goldFrom, birthdayPercent, slotMinutes,
                    idleMinutes, forgottenHours, 0, 208);
        }

        /** The seven loyalty and discount numbers, with the other six at what the program always had. */
        public Values(int staffDiscountPercent, long earnDong, long spendDong, int maxPercentOfBill, int minPoints, int silverFrom, int goldFrom) {
            this(staffDiscountPercent, earnDong, spendDong, maxPercentOfBill, minPoints, silverFrom, goldFrom, 10, 90, 15, 16, 0, 208);
        }
    }

    /** What the program did before this was a setting: 10 %, 1 point per 10.000đ, 1 point = 1.000đ, 50 % of the bill, 20 points, 100 / 500, birthday 10 %, 90 minutes, 15 minutes idle, 16 hours. */
    public static final Values DEFAULTS = new Values(10, 10_000, 1_000, 50, 20, 100, 500);

    // Ranges: wide enough for any shop, narrow enough that a slip of the finger (an extra zero) is caught.
    /** A cashier may never give more than this (TODO 4.49 asked for 0 to 30 %); an administrator is not limited by it. */
    public static final int STAFF_MAX = 30;
    public static final long EARN_MIN = 1_000, EARN_MAX = 10_000_000;
    public static final long SPEND_MIN = 100, SPEND_MAX = 100_000;
    public static final int BILL_MAX = 90; // a sale is never paid for entirely with points
    public static final int MIN_POINTS_MAX = 10_000;
    public static final int TIER_MAX = 1_000_000;
    public static final int BIRTHDAY_MIN = 1, BIRTHDAY_MAX = 30;
    public static final int SLOT_MIN = 15, SLOT_MAX = 240;
    public static final int IDLE_MAX = 480; // 0 is allowed: no automatic sign-out
    public static final int FORGOTTEN_MIN = 4, FORGOTTEN_MAX = 24;
    public static final int MONTHLY_HOURS_MIN = 40, MONTHLY_HOURS_MAX = 400;
    /** Free cups per person per day for a cashier (TODO 4.61): 0 means none; no shop needs more than 20 a day for one person. */
    public static final int FREE_MAX = 20;

    /** The label keys of the boxes on the settings screen, in the order of {@link #parse}. */
    private static final String[] LABELS = {"settings.rules.staff.label", "settings.rules.earn.label", "settings.rules.spend.label",
            "settings.rules.bill.label", "settings.rules.min.label", "settings.rules.silver.label", "settings.rules.gold.label",
            "settings.rules.birthday.label", "settings.rules.slot.label", "settings.rules.idle.label", "settings.rules.forgotten.label",
            "settings.rules.salary.label", "settings.rules.hours.label", "settings.rules.free.label"};
    private static final long[] MINS = {0, EARN_MIN, SPEND_MIN, 0, 1, 1, 1, BIRTHDAY_MIN, SLOT_MIN, 0, FORGOTTEN_MIN, 0, MONTHLY_HOURS_MIN, 0};
    private static final long[] MAXS = {STAFF_MAX, EARN_MAX, SPEND_MAX, BILL_MAX, MIN_POINTS_MAX, TIER_MAX, TIER_MAX, BIRTHDAY_MAX, SLOT_MAX,
            IDLE_MAX, FORGOTTEN_MAX, 1, MONTHLY_HOURS_MAX, FREE_MAX};

    private static volatile Values current = DEFAULTS;

    private ShopRules() {
    }

    /** The rules in force now. Never reads the database. */
    public static Values current() {
        return current;
    }

    /** Called by {@code ShopSettings} when the rules are loaded or saved; a set of numbers {@link #problem} refuses is ignored. */
    public static void set(Values values) {
        if (values != null && problem(values) == null) current = values;
    }

    /** For tests: the factory rules again. */
    public static void resetForTests() {
        current = DEFAULTS;
    }

    /**
     * The numbers typed on the settings screen as rules, or null when {@link #problemWith} would refuse them. The boxes are, in order:
     * cashier discount, earn, spend, bill share, least points, silver, gold, birthday, booking slot, idle minutes, forgotten hours. A blank
     * box (or one that is missing: the older form with seven) means "the factory value", so the owner can clear a box to go back.
     */
    public static Values parse(String... boxes) {
        return problemWith(boxes) != null ? null : valuesOf(boxes);
    }

    /** The first thing wrong with what was typed (in the language in use), or null. The boxes after the eleventh are optional (TODO 4.60, 4.61). */
    public static String problemWith(String... boxes) {
        Values d = DEFAULTS;
        long[] defaults = {d.staffDiscountPercent(), d.earnDong(), d.spendDong(), d.maxPercentOfBill(), d.minPoints(), d.silverFrom(),
                d.goldFrom(), d.birthdayPercent(), d.slotMinutes(), d.idleMinutes(), d.forgottenHours(), d.salaryHourly(), d.monthlyHours(), d.freeDrinksPerDay()};
        Long[] numbers = new Long[LABELS.length];
        for (int i = 0; i < LABELS.length; i++) {
            numbers[i] = number(i < boxes.length ? boxes[i] : null, defaults[i]);
            if (numbers[i] == null || numbers[i] < MINS[i] || numbers[i] > MAXS[i]) {
                return I18n.t("settings.rules.invalid", I18n.t(LABELS[i]), MINS[i], MAXS[i]);
            }
        }
        if (numbers[6] <= numbers[5]) return I18n.t("settings.rules.tiers");
        return null;
    }

    /** The same check on numbers already parsed (used by {@link #set}); null when fine. */
    public static String problem(Values v) {
        return problemWith(Integer.toString(v.staffDiscountPercent()), Long.toString(v.earnDong()), Long.toString(v.spendDong()),
                Integer.toString(v.maxPercentOfBill()), Integer.toString(v.minPoints()), Integer.toString(v.silverFrom()),
                Integer.toString(v.goldFrom()), Integer.toString(v.birthdayPercent()), Integer.toString(v.slotMinutes()),
                Integer.toString(v.idleMinutes()), Integer.toString(v.forgottenHours()), Integer.toString(v.salaryHourly()),
                Integer.toString(v.monthlyHours()), Integer.toString(v.freeDrinksPerDay()));
    }

    private static Values valuesOf(String... boxes) {
        Values d = DEFAULTS;
        return new Values(at(boxes, 0, d.staffDiscountPercent()).intValue(), at(boxes, 1, d.earnDong()), at(boxes, 2, d.spendDong()),
                at(boxes, 3, d.maxPercentOfBill()).intValue(), at(boxes, 4, d.minPoints()).intValue(), at(boxes, 5, d.silverFrom()).intValue(),
                at(boxes, 6, d.goldFrom()).intValue(), at(boxes, 7, d.birthdayPercent()).intValue(), at(boxes, 8, d.slotMinutes()).intValue(),
                at(boxes, 9, d.idleMinutes()).intValue(), at(boxes, 10, d.forgottenHours()).intValue(), at(boxes, 11, d.salaryHourly()).intValue(),
                at(boxes, 12, d.monthlyHours()).intValue(), at(boxes, 13, d.freeDrinksPerDay()).intValue());
    }

    private static Long at(String[] boxes, int index, long blank) {
        return number(index < boxes.length ? boxes[index] : null, blank);
    }

    /** One rule that differs between two sets, for the audit log: a stable name and the two numbers. */
    public record Change(String name, long before, long after) {
    }

    /** What an owner changed in one save, rule by rule (empty when nothing changed). */
    public static java.util.List<Change> changes(Values before, Values after) {
        java.util.List<Change> list = new java.util.ArrayList<>();
        if (before == null || after == null) return list;
        add(list, "staffDiscountPercent", before.staffDiscountPercent(), after.staffDiscountPercent());
        add(list, "earnDong", before.earnDong(), after.earnDong());
        add(list, "spendDong", before.spendDong(), after.spendDong());
        add(list, "maxPercentOfBill", before.maxPercentOfBill(), after.maxPercentOfBill());
        add(list, "minPoints", before.minPoints(), after.minPoints());
        add(list, "silverFrom", before.silverFrom(), after.silverFrom());
        add(list, "goldFrom", before.goldFrom(), after.goldFrom());
        add(list, "birthdayPercent", before.birthdayPercent(), after.birthdayPercent());
        add(list, "slotMinutes", before.slotMinutes(), after.slotMinutes());
        add(list, "idleMinutes", before.idleMinutes(), after.idleMinutes());
        add(list, "forgottenHours", before.forgottenHours(), after.forgottenHours());
        add(list, "salaryHourly", before.salaryHourly(), after.salaryHourly());
        add(list, "monthlyHours", before.monthlyHours(), after.monthlyHours());
        add(list, "freeDrinksPerDay", before.freeDrinksPerDay(), after.freeDrinksPerDay());
        return list;
    }

    private static void add(java.util.List<Change> list, String name, long before, long after) {
        if (before != after) list.add(new Change(name, before, after));
    }

    /** A whole number as typed (digits, spaces and thousands dots or commas allowed); blank gives {@code blank}; anything else null. */
    private static Long number(String text, long blank) {
        if (text == null || text.isBlank()) return blank;
        String t = text.trim().replace(" ", "").replace(".", "").replace(",", "");
        if (!t.matches("[0-9]{1,9}")) return null;
        return Long.parseLong(t);
    }
}
