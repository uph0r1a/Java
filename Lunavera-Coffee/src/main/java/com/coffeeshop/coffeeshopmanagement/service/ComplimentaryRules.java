package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.model.OrderItem;
import com.coffeeshop.coffeeshopmanagement.model.Role;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.TextNormalizer;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Free drinks for the staff, for the owner and for tastings (TODO 4.61, rule D26). The item said "ask the owner what the rule is first";
 * the owner had not answered when this was built, so the rule below is a choice, kept in one place and in one setting so it is easy to change:
 * <ul>
 *   <li>A free drink is a way of CLOSING a bill with no money. It is not a discount (the discount reports and the cashier's 10 % limit do not
 *       see it) and it is not waste (the drink was made, given to somebody, and the stock was used for it). Nothing else can be on the bill:
 *       no discount, voucher, points, gift card, split or customer.</li>
 *   <li>There are three kinds: {@link Kind#STAFF} (a named member of the staff drinks it), {@link Kind#OWNER} (the owner does) and
 *       {@link Kind#TASTING} (a customer or a supplier tries it; the note says who).</li>
 *   <li>An <b>administrator</b> may give any kind, any number. A <b>cashier</b> may give {@link Kind#STAFF} and {@link Kind#OWNER} drinks, at most
 *       {@code ShopRules.freeDrinksPerDay} cups per person per shop day (2 until changed; 0 = cashiers may give none), counting the owner as one
 *       person. A tasting is for an administrator only: it has no person to count against.</li>
 *   <li>The bill is saved with its lines at 0 (so revenue, discounts and the average bill are untouched and the profit of the period falls by
 *       what the drinks cost), and the price they would have had is kept in {@code orders.complimentary_value}.</li>
 *   <li>Every free bill writes one audit line saying who got it, and the report has a line with how many cups were given and what they cost.</li>
 * </ul>
 * Nothing here touches a window or the database, so all of it is tested without either; {@code ComplimentaryDAO} applies the limit once more
 * inside the payment's own transaction, so two tills cannot both use the last free drink of a person.
 */
public final class ComplimentaryRules {

    /** The longest note kept; the column holds 200 characters. */
    public static final int NOTE_MAX = 200;

    /** What a free bill is for. The name is what {@code orders.complimentary_kind} stores. */
    public enum Kind {
        STAFF("complimentary.kind.staff"),
        OWNER("complimentary.kind.owner"),
        TASTING("complimentary.kind.tasting");

        private final String labelKey;

        Kind(String labelKey) {
            this.labelKey = labelKey;
        }

        /** The name in the language in use. */
        public String label() {
            return I18n.t(labelKey);
        }

        /** The name in a fixed language (the report CSV and the receipt follow the screen; a test may ask for either). */
        public String label(String language) {
            return I18n.in(language, labelKey);
        }

        /** The kind stored under this text, or null for a missing or unknown one (a row from a newer program). */
        public static Kind fromDb(String stored) {
            if (stored == null) return null;
            try {
                return Kind.valueOf(stored.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    /**
     * What the person at the till asked for: the kind, the member of staff who drinks it (the staff record's id; STAFF only, null otherwise) and
     * a note (cleaned; null when empty). The note is required for a tasting and optional otherwise.
     */
    public record Give(Kind kind, Integer forEmployeeId, String note) {
        public Give {
            note = noteToStore(note);
        }
    }

    /** Why a free bill is refused. {@link #OK} means it may be saved. */
    public enum Problem { OK, NO_KIND, NO_LINES, PERSON_NEEDED, PERSON_NOT_WANTED, TASTING_ADMIN_ONLY, NOTE_NEEDED, NOTE_TOO_LONG, CASHIERS_MAY_NOT, OVER_LIMIT }

    private ComplimentaryRules() {
    }

    /** The cashier's limit in force now: cups per person per shop day (a setting, {@link ShopRules}). */
    public static int cashierCupsPerDay() {
        return ShopRules.current().freeDrinksPerDay();
    }

    /** The note as it is saved: composed accents, one space between words, trimmed, cut to {@link #NOTE_MAX}; null when nothing is left. */
    public static String noteToStore(String typed) {
        if (typed == null) return null;
        String text = TextNormalizer.nfc(typed);
        if (text == null) return null;
        text = text.replaceAll("\\p{Cntrl}", " ").replaceAll("\\s+", " ").trim();
        if (text.isEmpty()) return null;
        return text.length() > NOTE_MAX ? text.substring(0, NOTE_MAX).trim() : text;
    }

    /** The number of cups on the bill (the sum of the quantities). */
    public static int cups(List<OrderItem> items) {
        int sum = 0;
        if (items != null) {
            for (OrderItem item : items) sum += Math.max(0, item.getQuantity());
        }
        return sum;
    }

    /** What the lines would have cost at the menu price, in dong: unit price x quantity of each line added up. */
    public static BigDecimal listValue(List<OrderItem> items) {
        BigDecimal sum = BigDecimal.ZERO;
        if (items != null) {
            for (OrderItem item : items) {
                if (item.getUnitPrice() != null && item.getQuantity() > 0) {
                    sum = sum.add(item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
                }
            }
        }
        return sum;
    }

    /**
     * Copies of the lines at no charge: the same product, quantity, size, extras and note, the price and the line total at 0, and no
     * promotion. The originals are left as they are (the till's cart is cleared after the bill is saved, but a failed save must find it intact).
     */
    public static List<OrderItem> atNoCharge(List<OrderItem> items) {
        List<OrderItem> free = new ArrayList<>();
        if (items == null) return free;
        for (OrderItem item : items) {
            OrderItem copy = new OrderItem();
            copy.setProductId(item.getProductId());
            copy.setProductName(item.getProductName());
            copy.setQuantity(item.getQuantity());
            copy.setUnitPrice(BigDecimal.ZERO);
            copy.setLineTotal(BigDecimal.ZERO);
            copy.setNote(item.getNote());
            copy.setSizeName(item.getSizeName());
            copy.setSizePercent(item.getSizePercent());
            copy.setExtras(item.getExtras());
            copy.setOptionCost(item.getOptionCost());
            free.add(copy);
        }
        return free;
    }

    /**
     * What is wrong with this request, or {@link Problem#OK}. Pure: the numbers it needs are passed in.
     *
     * @param role             who is signed in (null counts as a cashier, the stricter rule)
     * @param give             what was asked for
     * @param cupsInBill       the cups on this bill
     * @param cupsGivenToday   the free cups already given today to the same person (the same kind of person: that member of staff, or the owner)
     * @param cashierCupsPerDay the cashier's limit, in cups per person per day
     */
    public static Problem check(Role role, Give give, int cupsInBill, int cupsGivenToday, int cashierCupsPerDay) {
        if (give == null || give.kind() == null) return Problem.NO_KIND;
        if (cupsInBill <= 0) return Problem.NO_LINES;
        if (give.kind() == Kind.STAFF && give.forEmployeeId() == null) return Problem.PERSON_NEEDED;
        if (give.kind() != Kind.STAFF && give.forEmployeeId() != null) return Problem.PERSON_NOT_WANTED;
        if (give.kind() == Kind.TASTING && give.note() == null) return Problem.NOTE_NEEDED;
        if (give.note() != null && give.note().length() > NOTE_MAX) return Problem.NOTE_TOO_LONG;
        if (role == Role.ADMIN) return Problem.OK; // no limit for an administrator
        if (give.kind() == Kind.TASTING) return Problem.TASTING_ADMIN_ONLY;
        if (cashierCupsPerDay <= 0) return Problem.CASHIERS_MAY_NOT;
        if ((long) cupsGivenToday + cupsInBill > cashierCupsPerDay) return Problem.OVER_LIMIT;
        return Problem.OK;
    }

    /** How many more cups a cashier may still give to a person today (never negative). An administrator has no limit: see {@link #unlimited}. */
    public static int cupsLeft(int cupsGivenToday, int cashierCupsPerDay) {
        return Math.max(0, cashierCupsPerDay - Math.max(0, cupsGivenToday));
    }

    /** True when this role has no daily limit. */
    public static boolean unlimited(Role role) {
        return role == Role.ADMIN;
    }

    /**
     * The sentence for a refused request, in the language in use; {@code problem} must not be {@link Problem#OK}.
     *
     * @param who the person the limit is about, as it should read in the message ("Mai", "the owner"); only used for {@link Problem#OVER_LIMIT}
     */
    public static String message(Problem problem, String who, int cupsGivenToday, int cupsInBill, int cashierCupsPerDay) {
        return switch (problem) {
            case OK -> throw new IllegalArgumentException("OK has no problem to describe");
            case NO_KIND -> I18n.t("complimentary.problem.kind");
            case NO_LINES -> I18n.t("complimentary.problem.nolines");
            case PERSON_NEEDED -> I18n.t("complimentary.problem.person");
            case PERSON_NOT_WANTED -> I18n.t("complimentary.problem.personnotwanted");
            case TASTING_ADMIN_ONLY -> I18n.t("complimentary.problem.tasting");
            case NOTE_NEEDED -> I18n.t("complimentary.problem.note");
            case NOTE_TOO_LONG -> I18n.t("complimentary.problem.notelong", NOTE_MAX);
            case CASHIERS_MAY_NOT -> I18n.t("complimentary.problem.off");
            case OVER_LIMIT -> I18n.t("complimentary.problem.limit", who == null ? "-" : who, cashierCupsPerDay, cupsGivenToday, cupsInBill);
        };
    }
}
