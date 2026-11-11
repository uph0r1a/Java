package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.model.Category;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.MoneyParser;
import com.coffeeshop.coffeeshopmanagement.util.ValidationUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * What the product box (add or edit) checks before anything is saved (TODO 6.21): the name, the category, the price, the cost, the
 * stock, the code and the reorder level. It used to sit inside the dialog, 120 lines between the text boxes; here it is pure (no
 * screen, no database) and tested. The checks that need the database (the same name in the category, a code another product has)
 * are in {@link ProductService#save}. A cost above the price and a stock number that jumped are not errors: they come back as
 * {@link Question}s the screen asks before saving.
 */
public final class ProductFormRules {

    private ProductFormRules() {
    }

    /** What was typed in the box, as text, and the category chosen. */
    public record Form(String name, Category category, String price, String cost, String stock, String code, String reorderLevel,
                       String description, String imagePath, boolean active) {
    }

    /** What the box holds once every check passed. */
    public record Values(String name, Category category, BigDecimal price, BigDecimal cost, int stock, Integer reorderLevel, String code,
                         String description, String imagePath, boolean active) {
    }

    /** Something wrong with what was typed: the title and the sentence of the box that tells the person. */
    public record Problem(String title, String message) {
    }

    /** Something the person must confirm before the product is saved. */
    public record Question(String title, String message) {
    }

    /** Either a {@code problem} (nothing to save) or the {@code values} with the {@code questions} to ask first. */
    public record Result(Problem problem, Values values, List<Question> questions) {
        static Result refused(String title, String message) {
            return new Result(new Problem(title, message), null, List.of());
        }
    }

    /**
     * Checks the box in the order the person meets the boxes. {@code oldStock} is the stock the product had when the box opened (0 for
     * a new one), to notice a number that looks like a slip of the finger ({@link StockPolicy#looksTooBig}).
     */
    public static Result check(Form form, int oldStock) {
        if (ValidationUtil.isBlank(form.name())) return Result.refused(I18n.t("common.missing.title"), I18n.t("products.val.name"));
        if (form.category() == null) return Result.refused(I18n.t("common.missing.title"), I18n.t("products.val.category"));
        BigDecimal price;
        try {
            price = MoneyParser.parseDong(form.price());
            if (price == null || price.signum() <= 0) throw new IllegalArgumentException();
        } catch (IllegalArgumentException ex) {
            return Result.refused(I18n.t("products.val.price.title"), I18n.t("products.val.price.message"));
        }
        if (Limits.priceTooBig(price)) { // a price with three extra zeros is an easy slip (TODO 3.59)
            return Result.refused(I18n.t("products.val.price.title"), I18n.t("products.val.price.max", Limits.maxPriceText()));
        }
        BigDecimal cost = null;
        List<Question> questions = new ArrayList<>();
        if (!ValidationUtil.isBlank(form.cost())) {
            try {
                cost = MoneyParser.parseDong(form.cost());
                if (cost.signum() < 0) throw new IllegalArgumentException("negative");
            } catch (IllegalArgumentException ex) {
                return Result.refused(I18n.t("products.val.cost.title"), I18n.t("products.val.cost.message"));
            }
            if (Limits.priceTooBig(cost)) {
                return Result.refused(I18n.t("products.val.cost.title"), I18n.t("products.val.price.max", Limits.maxPriceText()));
            }
            if (cost.compareTo(price) > 0) questions.add(new Question(I18n.t("products.val.loss.title"), I18n.t("products.val.loss.message")));
        }
        int stock;
        try {
            stock = Integer.parseInt(form.stock() == null ? "" : form.stock().trim());
            if (stock < 0) throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            return Result.refused(I18n.t("products.val.stock.title"), I18n.t("products.val.stock.message"));
        }
        if (stock > Limits.MAX_STOCK) {
            return Result.refused(I18n.t("products.val.stock.title"), I18n.t("products.val.stock.max", Limits.MAX_STOCK));
        }
        String codeProblem = ProductCode.problem(form.code());
        if (codeProblem != null) return Result.refused(I18n.t("products.val.code.title"), I18n.t(codeProblem, ProductCode.MAX_LENGTH));
        String reorderProblem = StockLevel.problem(form.reorderLevel());
        if (reorderProblem != null) return Result.refused(I18n.t("products.val.stock.title"), I18n.t(reorderProblem, Limits.MAX_STOCK));
        if (StockPolicy.looksTooBig(oldStock, stock)) {
            questions.add(new Question(I18n.t("products.stock.jump.title"), I18n.t("products.stock.jump.message", oldStock, stock, stock)));
        }
        return new Result(null, new Values(form.name().trim(), form.category(), price, cost, stock, StockLevel.parse(form.reorderLevel()),
                ProductCode.clean(form.code()), form.description(), form.imagePath(), form.active()), List.copyOf(questions));
    }
}
