package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.util.I18n;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The pure rules for ingredients and recipes (TODO 4.41, rule D12): what may be typed, and how many servings a stock makes. */
public final class IngredientRules {

    public static final int NAME_MAX = 100;
    public static final int UNIT_MAX = 20;
    /** Generous, so a typing slip such as an extra zero is caught but a 50 kg sack in grams is accepted. */
    public static final long AMOUNT_MAX = 100_000_000L;

    private IngredientRules() {
    }

    public static String clean(String text) {
        return text == null ? "" : com.coffeeshop.coffeeshopmanagement.util.TextNormalizer.nfc(text).replaceAll("\\s+", " "); // TODO 3.71 (invisible characters) and 3.18 (composed accents)
    }

    /** What is wrong with the name and unit, as a sentence; null when they are fine. */
    public static String problem(String name, String unit) {
        String n = clean(name);
        if (n.isEmpty()) return I18n.t("ingredient.val.name");
        if (n.length() > NAME_MAX) return I18n.t("ingredient.val.namelong", NAME_MAX);
        String u = clean(unit);
        if (u.isEmpty()) return I18n.t("ingredient.val.unit");
        if (u.length() > UNIT_MAX) return I18n.t("ingredient.val.unitlong", UNIT_MAX);
        return null;
    }

    /** What is wrong with a stock or minimum amount; null when fine. */
    public static String amountProblem(long amount) {
        if (amount < 0) return I18n.t("ingredient.val.negative");
        if (amount > AMOUNT_MAX) return I18n.t("ingredient.val.toobig");
        return null;
    }

    /** What is wrong with the lines of a recipe (ingredient id, amount per serving); null when fine. */
    public static String recipeProblem(List<long[]> lines) {
        Set<Long> seen = new HashSet<>();
        for (long[] line : lines) {
            if (line[1] <= 0) return I18n.t("ingredient.val.perserving");
            if (line[1] > AMOUNT_MAX) return I18n.t("ingredient.val.toobig");
            if (!seen.add(line[0])) return I18n.t("ingredient.val.twice");
        }
        return null;
    }

    /** How many servings the stocks make: the smallest of stock / amount over the lines; null when there are no lines (no recipe). */
    public static Integer servings(List<long[]> stockAndAmount) {
        if (stockAndAmount.isEmpty()) return null;
        long fewest = Long.MAX_VALUE;
        for (long[] line : stockAndAmount) {
            fewest = Math.min(fewest, line[0] / line[1]);
        }
        return (int) Math.min(fewest, Integer.MAX_VALUE);
    }
}
