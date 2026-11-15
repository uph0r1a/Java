package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.model.OptionKind;
import com.coffeeshop.coffeeshopmanagement.model.ProductOption;
import com.coffeeshop.coffeeshopmanagement.util.I18n;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The pure rules for the sizes and extras the owner gives a product (TODO 4.47): what a name may be, how many there may be, and
 * that no price comes out below zero or above the ceiling of {@link Limits}. The same checks run in the owner's screen (to say what
 * is wrong) and in {@code ProductOptionDAO} (so nothing else can save a bad list).
 */
public final class OptionRules {

    /** A name the cashier taps and the receipt prints; short, so a row of buttons and a 58 mm receipt still fit. */
    public static final int NAME_MAX = 30;
    public static final int MAX_SIZES = 5;
    /** With {@link #NAME_MAX} and the longest price this still fits the {@code extras} column of a line ({@link LineOptions#EXTRAS_COLUMN}). */
    public static final int MAX_EXTRAS = 8;
    public static final int MIN_PERCENT = 10;
    public static final int MAX_PERCENT = 500;

    private OptionRules() {
    }

    /**
     * The list as it will be kept: every name cleaned ({@link LineOptions#cleanName}), a missing price or cost taken as 0, an extra
     * always at the normal recipe and never "default", and exactly one default size when there are sizes in use (the one asked for
     * if it is switched on, else the first one that is).
     */
    public static List<ProductOption> normalise(List<ProductOption> options) {
        List<ProductOption> out = new ArrayList<>();
        int chosenDefault = -1;
        for (int i = 0; i < options.size(); i++) {
            ProductOption o = options.get(i);
            if (o.isSize() && o.active() && o.isDefault() && chosenDefault < 0) chosenDefault = i;
        }
        for (int i = 0; chosenDefault < 0 && i < options.size(); i++) {
            if (options.get(i).isSize() && options.get(i).active()) chosenDefault = i;
        }
        for (int i = 0; i < options.size(); i++) {
            ProductOption o = options.get(i);
            BigDecimal price = o.priceDelta() == null ? BigDecimal.ZERO : o.priceDelta();
            BigDecimal cost = o.costDelta() == null ? BigDecimal.ZERO : o.costDelta();
            boolean size = o.isSize();
            out.add(new ProductOption(o.id(), o.productId(), o.kind(), LineOptions.cleanName(o.name()), price, cost,
                    size ? o.recipePercent() : ProductOption.NORMAL_PERCENT, size && i == chosenDefault, o.active()));
        }
        return out;
    }

    /**
     * What is wrong with a product's options, as a sentence for the owner; null when they are fine. {@code productPrice} is the
     * price the sizes are added to (null when it is not known, then only the figures themselves are checked); the list should be
     * {@link #normalise normalised} first.
     */
    public static String problem(BigDecimal productPrice, List<ProductOption> options) {
        int sizes = 0;
        int extras = 0;
        Set<String> seen = new HashSet<>();
        BigDecimal biggestSize = BigDecimal.ZERO;
        BigDecimal allExtras = BigDecimal.ZERO;
        for (ProductOption o : options) {
            String name = o.name() == null ? "" : o.name();
            if (name.isEmpty()) return I18n.t("option.val.name");
            if (name.length() > NAME_MAX) return I18n.t("option.val.namelong", NAME_MAX);
            if (!seen.add(o.kind() + "|" + name.toLowerCase(Locale.ROOT))) return I18n.t("option.val.twice", name);
            BigDecimal price = o.priceDelta();
            if (price.abs().compareTo(Limits.MAX_PRICE) > 0) return I18n.t("option.val.toobig", Limits.maxPriceText());
            if (o.costDelta().signum() < 0 || o.costDelta().compareTo(Limits.MAX_PRICE) > 0) return I18n.t("option.val.cost", name);
            if (o.isSize()) {
                sizes++;
                if (o.recipePercent() < MIN_PERCENT || o.recipePercent() > MAX_PERCENT) {
                    return I18n.t("option.val.percent", MIN_PERCENT, MAX_PERCENT);
                }
                if (productPrice != null && productPrice.add(price).signum() < 0) return I18n.t("option.val.belowzero", name);
                if (price.compareTo(biggestSize) > 0) biggestSize = price;
            } else {
                extras++;
                if (price.signum() < 0) return I18n.t("option.val.extraneg", name);
                allExtras = allExtras.add(price);
            }
        }
        if (sizes > MAX_SIZES) return I18n.t("option.val.toomanysizes", MAX_SIZES);
        if (extras > MAX_EXTRAS) return I18n.t("option.val.toomanyextras", MAX_EXTRAS);
        if (productPrice != null && productPrice.add(biggestSize).add(allExtras).compareTo(Limits.MAX_PRICE) > 0) {
            return I18n.t("option.val.toobig", Limits.maxPriceText());
        }
        return null;
    }

    public static List<ProductOption> sizesOf(List<ProductOption> options) {
        return options.stream().filter(o -> o.kind() == OptionKind.SIZE).toList();
    }

    public static List<ProductOption> extrasOf(List<ProductOption> options) {
        return options.stream().filter(o -> o.kind() == OptionKind.EXTRA).toList();
    }
}
