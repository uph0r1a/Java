package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.model.LineChoice;
import com.coffeeshop.coffeeshopmanagement.model.OrderItem;
import com.coffeeshop.coffeeshopmanagement.model.ProductOption;
import com.coffeeshop.coffeeshopmanagement.util.Money;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The size and extras on one order line (TODO 4.47), with no screen and no database: how they are written into the line's two text
 * columns, read back, compared and shown. The line keeps the NAMES and the PRICES as they were at the sale
 * ({@code order_items.size_name}, {@code size_percent}, {@code extras}, {@code option_cost}); the line's {@code unit_price} already
 * includes them, so totals, discounts, refunds and revenue need no change, and a later edit of an option moves no old order.
 *
 * <p>Extras are one text, {@code "Extra shot=10000;Whipped cream=5000"} (name, then the price in dong, joined by {@code ;}): the
 * report adds them up by name, and the receipt prints the names. The two separators cannot be typed into an option name
 * ({@link OptionRules}).
 */
public final class LineOptions {

    private static final char EXTRA_SEPARATOR = ';';
    private static final char PRICE_SEPARATOR = '=';

    /** The width of the {@code extras} column; {@link OptionRules#MAX_EXTRAS} extras of the longest name and price fit well inside it. */
    public static final int EXTRAS_COLUMN = 400;

    private LineOptions() {
    }

    /**
     * An option name as it is kept: runs of white space become one space, control characters and the two separators go, the ends are
     * trimmed. NOT cut to a length: {@link OptionRules#problem} refuses a name that is too long, so nothing is changed silently.
     */
    public static String cleanName(String typed) {
        if (typed == null) return "";
        StringBuilder out = new StringBuilder(typed.length());
        boolean pendingSpace = false;
        for (int i = 0; i < typed.length(); i++) {
            char c = typed.charAt(i);
            if (Character.isWhitespace(c) || Character.isSpaceChar(c)) {
                pendingSpace = out.length() > 0;
            } else if (!Character.isISOControl(c) && c != EXTRA_SEPARATOR && c != PRICE_SEPARATOR) {
                if (pendingSpace) out.append(' ');
                pendingSpace = false;
                out.append(c);
            }
        }
        return out.toString();
    }

    /** True when {@code typed} has a character that cannot be part of a name (the two separators), so the form can say so. */
    public static boolean hasReservedCharacter(String typed) {
        return typed != null && (typed.indexOf(EXTRA_SEPARATOR) >= 0 || typed.indexOf(PRICE_SEPARATOR) >= 0);
    }

    /**
     * What the cashier picked, as the line keeps it: the chosen size (null for none) and extras, with the price and cost they add to one
     * piece. Names and amounts are copied now, so the line does not change when the owner edits the option later.
     */
    public static LineChoice choice(ProductOption size, List<ProductOption> extras) {
        BigDecimal price = BigDecimal.ZERO;
        BigDecimal cost = BigDecimal.ZERO;
        if (size != null) {
            price = price.add(size.priceDelta());
            cost = cost.add(size.costDelta());
        }
        List<LineChoice.Extra> picked = new ArrayList<>();
        for (ProductOption extra : extras) {
            picked.add(new LineChoice.Extra(extra.name(), extra.priceDelta()));
            price = price.add(extra.priceDelta());
            cost = cost.add(extra.costDelta());
        }
        return new LineChoice(size == null ? null : size.name(), size == null ? null : size.recipePercent(), picked, price, cost);
    }

    /** The extras as the column keeps them; null for none, so a plain line has null and never an empty text. */
    public static String encodeExtras(List<LineChoice.Extra> extras) {
        if (extras == null || extras.isEmpty()) return null;
        StringBuilder out = new StringBuilder();
        for (LineChoice.Extra extra : extras) {
            if (out.length() > 0) out.append(EXTRA_SEPARATOR);
            out.append(cleanName(extra.name())).append(PRICE_SEPARATOR).append(Money.toDong(extra.price()));
        }
        return out.toString();
    }

    /** The extras of a column text, in order; a part that cannot be read (no name, no number) is skipped. */
    public static List<LineChoice.Extra> decodeExtras(String text) {
        List<LineChoice.Extra> extras = new ArrayList<>();
        if (text == null || text.isBlank()) return extras;
        for (String part : text.split(String.valueOf(EXTRA_SEPARATOR))) {
            int at = part.lastIndexOf(PRICE_SEPARATOR);
            if (at <= 0) continue;
            String name = part.substring(0, at).strip();
            if (name.isEmpty()) continue;
            try {
                extras.add(new LineChoice.Extra(name, BigDecimal.valueOf(Long.parseLong(part.substring(at + 1).strip()))));
            } catch (NumberFormatException e) {
                // a damaged part is left out; the rest of the line still shows
            }
        }
        return extras;
    }

    public static List<LineChoice.Extra> extrasOf(OrderItem item) {
        return decodeExtras(item.getExtras());
    }

    /** Puts a choice on a line: the names and the cost; the price is the caller's (the cart adds the difference to the unit price). */
    public static void apply(OrderItem item, LineChoice choice) {
        LineChoice picked = choice == null ? LineChoice.NONE : choice;
        item.setSizeName(picked.sizeName());
        item.setSizePercent(picked.sizeName() == null ? null : picked.sizePercent());
        item.setExtras(encodeExtras(picked.extras()));
        item.setOptionCost(picked.costDelta().signum() == 0 ? null : picked.costDelta());
    }

    /** The size and extras of a line as one text for comparing, null for a plain line. Two lines are the same line only if these match. */
    public static String key(OrderItem item) {
        return keyOf(item.getSizeName(), item.getExtras());
    }

    public static String key(LineChoice choice) {
        return keyOf(choice.sizeName(), encodeExtras(choice.extras()));
    }

    private static String keyOf(String size, String extras) {
        String s = size == null || size.isBlank() ? "" : size;
        String e = extras == null || extras.isBlank() ? "" : extras;
        return s.isEmpty() && e.isEmpty() ? null : s + "\u0001" + e;
    }

    public static boolean same(OrderItem a, OrderItem b) {
        return Objects.equals(key(a), key(b));
    }

    /** What the report shows for one extra: how many were sold (refunds taken off) and what they brought in. */
    public record ExtraTally(String name, int pieces, long revenue) {
    }

    /**
     * Adds up extras over many lines for the report (TODO 4.47). {@code piecesByExtras} maps the {@code extras} text of a group of
     * identical lines to the number of pieces sold on those lines (negative or zero after refunds); every extra on a line sells as many
     * pieces as the line has, at the price written on the line. Extras of the same name are added together whatever the price was; the
     * biggest revenue comes first, and one that has been refunded down to nothing is left out.
     */
    public static List<ExtraTally> tallyExtras(java.util.Map<String, Integer> piecesByExtras) {
        java.util.Map<String, long[]> totals = new java.util.LinkedHashMap<>();
        for (java.util.Map.Entry<String, Integer> line : piecesByExtras.entrySet()) {
            for (LineChoice.Extra extra : decodeExtras(line.getKey())) {
                long[] t = totals.computeIfAbsent(extra.name(), k -> new long[2]);
                t[0] += line.getValue();
                t[1] += Money.toDong(extra.price()) * line.getValue();
            }
        }
        List<ExtraTally> result = new ArrayList<>();
        for (java.util.Map.Entry<String, long[]> e : totals.entrySet()) {
            if (e.getValue()[0] != 0 || e.getValue()[1] != 0) result.add(new ExtraTally(e.getKey(), (int) e.getValue()[0], e.getValue()[1]));
        }
        result.sort(java.util.Comparator.comparingLong(ExtraTally::revenue).reversed());
        return result;
    }

    /** "L, +Extra shot, +Whipped cream" for the cart and the receipt; null for a plain line. */
    public static String describe(OrderItem item) {
        return describe(item.getSizeName(), extrasOf(item));
    }

    public static String describe(LineChoice choice) {
        return describe(choice.sizeName(), choice.extras());
    }

    private static String describe(String size, List<LineChoice.Extra> extras) {
        List<String> parts = new ArrayList<>();
        if (size != null && !size.isBlank()) parts.add(size);
        for (LineChoice.Extra extra : extras) parts.add("+" + extra.name());
        return parts.isEmpty() ? null : String.join(", ", parts);
    }

    /** The name with its size and extras from the two stored columns, for places that only have those: {@code Latte (L, +Extra shot)}. */
    public static String nameWithOptions(String name, String sizeName, String extras) {
        String options = describe(sizeName, decodeExtras(extras));
        return options == null ? name : name + " (" + options + ")";
    }

    /** The name with its size and note for one-line places such as the receipt window: {@code Latte (L, +Extra shot, less sugar)}. */
    public static String withOptions(String name, OrderItem item) {
        String options = describe(item);
        String note = OrderNote.clean(item.getNote());
        if (options == null && note == null) return name;
        return name + " (" + (options == null ? "" : options + (note == null ? "" : ", ")) + (note == null ? "" : note) + ")";
    }
}
