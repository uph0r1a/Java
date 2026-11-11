package com.coffeeshop.coffeeshopmanagement.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Automatic promotions (TODO 4.55): a price that changes by itself at a time of day, for a product or a whole category, or for a
 * combination, so the cashier does not have to remember "giờ vàng" and type a discount. This class is only the arithmetic: it takes
 * the lines of a bill, the promotions the shop has and the moment, and says what each line gets. No database, no screen, no clock
 * of its own, so every rule below is tested without either.
 *
 * <p>Four kinds ({@link Kind}): a <b>percentage</b> off, a <b>fixed price</b> per piece, <b>buy X get Y free</b> of the same product,
 * and a <b>combo</b> (a set of products for one price). A promotion is for products (listed one by one) or categories, applies on
 * the days and hours it names between its start and end date, and can be switched off.
 *
 * <p>The rules that keep the sums checkable:
 * <ul>
 *   <li><b>One promotion per line, no stacking.</b> A line gets the one promotion that saves the customer most; on a tie the one with
 *       the lower id. A line that is part of a combo gets the combo and nothing else.</li>
 *   <li>The saving of a line is never more than the line's price, and a fixed price never raises a price.</li>
 *   <li>Whole dong, always rounded down for a percentage (the customer never gets a fraction of a dong the shop did not agree to).</li>
 *   <li>A buy-X-get-Y counts the pieces of one product over all its lines (two lines of the same drink with different notes are one
 *       count); the free pieces are given to the lines in the order they are on the bill.</li>
 *   <li>A combo is won only when it saves more than the lines it uses would save on their own (those lines then keep only the combo); as many whole sets as the bill holds are
 *       taken, and the pieces left over are priced as usual.</li>
 *   <li>Gift cards are not order lines (selling one is no sale of goods, {@code GiftCardRules}), so nothing here can touch them;
 *       a line with no product (one whose product was deleted) gets no promotion.</li>
 * </ul>
 * What the saving is worth on the payment (the cashier's own discount, a voucher and points are worked out on top, the cashier's
 * limit on the price before promotions) is {@code CheckoutService}'s business.
 */
public final class AutoPromotionRules {

    public enum Kind {
        /** {@link Promotion#percent()} off the price of the matched pieces. */
        PERCENT,
        /** The matched pieces cost {@link Promotion#fixedPrice()} each (when that is less than the price). */
        FIXED_PRICE,
        /** For every {@code buy + free} pieces of one product, {@code free} of them cost nothing. */
        BUY_GET,
        /** The listed products, one set after another, together cost {@link Promotion#fixedPrice()}. */
        COMBO
    }

    /**
     * One thing a promotion is about: a product or a category (exactly one of the two is set). For a {@link Kind#COMBO} each target is
     * a product with the number of pieces one set needs; for the other kinds {@code quantity} is 1 and any matching line counts.
     */
    public record Target(Integer productId, Integer categoryId, int quantity) {
        public Target {
            if ((productId == null) == (categoryId == null)) {
                throw new IllegalArgumentException("A target is a product or a category, not both and not neither");
            }
            if (quantity < 1) {
                throw new IllegalArgumentException("A target needs at least one piece");
            }
        }

        public static Target product(int id) {
            return new Target(id, null, 1);
        }

        public static Target product(int id, int quantity) {
            return new Target(id, null, quantity);
        }

        public static Target category(int id) {
            return new Target(null, id, 1);
        }

        boolean matches(Line line) {
            return productId != null ? productId.equals(line.productId()) : categoryId.equals(line.categoryId());
        }
    }

    /**
     * A promotion as the shop set it up. {@code days} empty means every day; {@code from} and {@code to} both null mean all day,
     * otherwise {@code from} (included) is before {@code to} (not included); {@code validFrom} and {@code validTo} (both included)
     * may each be null for no limit.
     */
    public record Promotion(int id, String name, Kind kind, boolean active, int percent, long fixedPrice, int buy, int free,
                            Set<DayOfWeek> days, LocalTime from, LocalTime to, LocalDate validFrom, LocalDate validTo,
                            List<Target> targets) {
        public Promotion {
            days = days == null ? Set.of() : Set.copyOf(days);
            targets = targets == null ? List.of() : List.copyOf(targets);
        }

        /** Is the promotion on at this moment (switched on, inside its dates, on one of its days, inside its hours)? */
        public boolean isOn(LocalDateTime at) {
            if (!active || at == null) {
                return false;
            }
            LocalDate day = at.toLocalDate();
            if ((validFrom != null && day.isBefore(validFrom)) || (validTo != null && day.isAfter(validTo))) {
                return false;
            }
            if (!days.isEmpty() && !days.contains(at.getDayOfWeek())) {
                return false;
            }
            if (from != null && to != null) {
                LocalTime time = at.toLocalTime();
                return !time.isBefore(from) && time.isBefore(to);
            }
            return true;
        }
    }

    /** A line of the bill, as the rules need it: what it is, what one piece costs and how many there are. Prices in whole dong. */
    public record Line(Integer productId, Integer categoryId, long unitPrice, int quantity) {
        long price() {
            return unitPrice * quantity;
        }
    }

    /** What one line got: the promotion's name (null when none) and the saving in whole dong (0 when none). */
    public record Given(String promotionName, long saving) {
        static final Given NONE = new Given(null, 0);
    }

    /** What the whole bill got: one {@link Given} per input line, in the same order, and their sum. */
    public record Applied(List<Given> lines, long total) {
        public boolean isNone() {
            return total == 0;
        }
    }

    private AutoPromotionRules() {
    }

    /** The result for a bill that has nothing to give. */
    public static Applied none(int lines) {
        List<Given> nothing = new ArrayList<>();
        for (int i = 0; i < lines; i++) {
            nothing.add(Given.NONE);
        }
        return new Applied(nothing, 0);
    }

    /** Checks a promotion before it is saved; returns the first problem as a short code, or null when it is usable. */
    public static String problemWith(Promotion promotion) {
        if (promotion.name() == null || promotion.name().isBlank()) return "name";
        if (promotion.kind() == null) return "kind";
        if (promotion.targets().isEmpty()) return "targets";
        if (promotion.from() != null || promotion.to() != null) {
            if (promotion.from() == null || promotion.to() == null || !promotion.from().isBefore(promotion.to())) return "hours";
        }
        if (promotion.validFrom() != null && promotion.validTo() != null && promotion.validTo().isBefore(promotion.validFrom())) return "dates";
        switch (promotion.kind()) {
            case PERCENT -> {
                if (promotion.percent() < 1 || promotion.percent() > 100) return "percent";
            }
            case FIXED_PRICE -> {
                if (promotion.fixedPrice() < 0) return "price";
            }
            case BUY_GET -> {
                if (promotion.buy() < 1 || promotion.free() < 1) return "buyget";
            }
            case COMBO -> {
                if (promotion.fixedPrice() < 0) return "price";
                if (promotion.targets().stream().mapToInt(Target::quantity).sum() < 2
                        || promotion.targets().stream().anyMatch(t -> t.productId() == null)) return "combo";
            }
        }
        return null;
    }

    /**
     * What each line of the bill gets at {@code at}. Only promotions that are on at that moment count; a line with no product, no
     * price or no pieces gets nothing.
     */
    public static Applied apply(List<Line> lines, List<Promotion> promotions, LocalDateTime at) {
        if (lines == null || lines.isEmpty()) {
            return none(0);
        }
        List<Promotion> on = new ArrayList<>();
        for (Promotion promotion : promotions == null ? List.<Promotion>of() : promotions) {
            if (promotion.isOn(at) && problemWith(promotion) == null) {
                on.add(promotion);
            }
        }
        on.sort(Comparator.comparingInt(Promotion::id));
        long[] saving = new long[lines.size()];
        Promotion[] won = new Promotion[lines.size()];
        // 1. the best single-product promotion of each line
        for (int i = 0; i < lines.size(); i++) {
            if (!usable(lines.get(i))) continue;
            for (Promotion promotion : on) {
                if (promotion.kind() == Kind.COMBO) continue;
                long worth = singleSaving(promotion, lines, i);
                if (worth > saving[i]) { // strictly more: on a tie the lower id (earlier in the list) stays
                    saving[i] = worth;
                    won[i] = promotion;
                }
            }
        }
        // 2. combos, best set first; a combo takes the lines it needs only when it saves more than those lines saved alone
        List<Promotion> combos = new ArrayList<>();
        for (Promotion promotion : on) {
            if (promotion.kind() == Kind.COMBO) combos.add(promotion);
        }
        boolean[] inCombo = new boolean[lines.size()];
        int[] piecesLeft = new int[lines.size()];
        for (int i = 0; i < lines.size(); i++) piecesLeft[i] = usable(lines.get(i)) ? lines.get(i).quantity() : 0;
        while (true) {
            ComboPlan best = null;
            for (Promotion combo : combos) {
                ComboPlan plan = planCombo(combo, lines, piecesLeft, inCombo, saving);
                if (plan != null && (best == null || plan.gain > best.gain)) best = plan;
            }
            if (best == null) break;
            for (Map.Entry<Integer, Integer> use : best.piecesFromLine.entrySet()) {
                int i = use.getKey();
                if (!inCombo[i]) { // the line leaves the single promotion it had: one promotion per line
                    inCombo[i] = true;
                    saving[i] = 0;
                    won[i] = best.promotion;
                }
                piecesLeft[i] -= use.getValue();
                saving[i] += best.savingPerLine.get(i);
            }
        }
        List<Given> given = new ArrayList<>();
        long total = 0;
        for (int i = 0; i < lines.size(); i++) {
            long capped = Math.min(saving[i], lines.get(i).price());
            if (won[i] == null || capped <= 0) {
                given.add(Given.NONE);
            } else {
                given.add(new Given(won[i].name(), capped));
                total += capped;
            }
        }
        return new Applied(given, total);
    }

    private static boolean usable(Line line) {
        return line.productId() != null && line.unitPrice() > 0 && line.quantity() > 0;
    }

    /** What one promotion (not a combo) saves on line {@code i}; 0 when it does not apply. */
    private static long singleSaving(Promotion promotion, List<Line> lines, int i) {
        Line line = lines.get(i);
        if (promotion.targets().stream().noneMatch(t -> t.matches(line))) return 0;
        return switch (promotion.kind()) {
            case PERCENT -> line.price() * promotion.percent() / 100; // rounded down
            case FIXED_PRICE -> promotion.fixedPrice() < line.unitPrice() ? (line.unitPrice() - promotion.fixedPrice()) * line.quantity() : 0;
            case BUY_GET -> buyGetSaving(promotion, lines, i);
            case COMBO -> 0;
        };
    }

    /**
     * Buy X get Y free: the pieces of this line's product over every line are counted together; every {@code buy + free} pieces give
     * {@code free} free ones, which go to the lines of that product in bill order. This returns what falls to line {@code i}.
     */
    private static long buyGetSaving(Promotion promotion, List<Line> lines, int i) {
        Line line = lines.get(i);
        int group = promotion.buy() + promotion.free();
        int total = 0;
        for (Line other : lines) {
            if (usable(other) && other.productId().equals(line.productId())) total += other.quantity();
        }
        int freePieces = total / group * promotion.free();
        long saved = 0;
        for (int k = 0; k < lines.size() && freePieces > 0; k++) {
            Line other = lines.get(k);
            if (!usable(other) || !other.productId().equals(line.productId())) continue;
            int take = Math.min(freePieces, other.quantity());
            if (k == i) {
                saved = other.unitPrice() * take;
                break;
            }
            freePieces -= take;
        }
        return saved;
    }

    /** One round of a combo: which pieces of which lines, and what it gains over what those lines would save alone. */
    private static final class ComboPlan {
        Promotion promotion;
        final Map<Integer, Integer> piecesFromLine = new HashMap<>();
        final Map<Integer, Long> savingPerLine = new HashMap<>();
        long gain;
    }

    /** Can one more set of {@code combo} be made from the pieces still free, and does it beat the lines' single promotions? */
    private static ComboPlan planCombo(Promotion combo, List<Line> lines, int[] piecesLeft, boolean[] inCombo, long[] single) {
        ComboPlan plan = new ComboPlan();
        plan.promotion = combo;
        long listPrice = 0;
        long aloneWorth = 0;
        java.util.Set<Integer> leaving = new java.util.HashSet<>();
        for (Target target : combo.targets()) {
            int needed = target.quantity();
            for (int i = 0; i < lines.size() && needed > 0; i++) {
                Line line = lines.get(i);
                if (!usable(line) || !target.matches(line)) continue;
                int available = piecesLeft[i] - plan.piecesFromLine.getOrDefault(i, 0);
                int take = Math.min(needed, available);
                if (take <= 0) continue;
                plan.piecesFromLine.merge(i, take, Integer::sum);
                listPrice += line.unitPrice() * take;
                if (!inCombo[i] && leaving.add(i)) { // the line gives up its own promotion for all its pieces: count it once
                    aloneWorth += single[i];
                }
                needed -= take;
            }
            if (needed > 0) return null; // this set cannot be completed
        }
        long saving = listPrice - combo.fixedPrice();
        if (saving <= 0 || saving <= aloneWorth) return null;
        // the saving of the set is shared over its lines by the price of their pieces (the last line takes the remainder)
        long shared = 0;
        List<Integer> order = new ArrayList<>(plan.piecesFromLine.keySet());
        order.sort(Comparator.naturalOrder());
        for (int k = 0; k < order.size(); k++) {
            int i = order.get(k);
            long part = k == order.size() - 1 ? saving - shared
                    : saving * (lines.get(i).unitPrice() * plan.piecesFromLine.get(i)) / listPrice;
            plan.savingPerLine.put(i, part);
            shared += part;
        }
        plan.gain = saving - aloneWorth;
        return plan;
    }

    // ---- how the days of the week are kept in the database

    /** The days as digits, ISO numbering (1 = Monday ... 7 = Sunday), in order: {@code "67"} is the weekend; empty means every day. */
    public static String encodeDays(Set<DayOfWeek> days) {
        StringBuilder text = new StringBuilder();
        for (DayOfWeek day : DayOfWeek.values()) {
            if (days != null && days.contains(day)) {
                text.append(day.getValue());
            }
        }
        return text.toString();
    }

    /** The days kept as digits; anything that is not 1-7 is ignored, so a damaged value means fewer days, never an error. */
    public static Set<DayOfWeek> decodeDays(String text) {
        java.util.EnumSet<DayOfWeek> days = java.util.EnumSet.noneOf(DayOfWeek.class);
        if (text != null) {
            for (char c : text.toCharArray()) {
                if (c >= '1' && c <= '7') {
                    days.add(DayOfWeek.of(c - '0'));
                }
            }
        }
        return days;
    }
}
