package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.config.SeedNames;
import com.coffeeshop.coffeeshopmanagement.model.OrderItem;
import com.coffeeshop.coffeeshopmanagement.model.Product;
import com.coffeeshop.coffeeshopmanagement.util.I18n;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * "The same as last time" (TODO 4.56): puts the lines of a customer's last paid order into the cart, with the notes they had
 * ("less sugar"), at <b>today's</b> prices, and says what could not be repeated. A product that is no longer on sale (deleted,
 * switched off, or in a hidden category) is skipped; one that is sold out is skipped; one with less in stock than last time is added
 * as far as the stock goes. What the other open orders hold is already off the stock the till sees ({@link StockHolds}), so a repeat
 * cannot take what another table has. No screen and no database: {@link Cart} does the adding.
 */
public final class RepeatOrder {

    /** Why a line could not be repeated whole. */
    public enum Reason {
        /** Not on sale any more. */
        NOT_SOLD,
        /** On sale but none left. */
        SOLD_OUT,
        /** Some left, fewer than last time, or the per-line ceiling was reached. */
        LESS
    }

    /**
     * A line that was not repeated whole.
     *
     * @param wanted pieces last time
     * @param added  pieces that went into the cart (0 when skipped altogether)
     */
    public record Left(String name, Reason reason, int wanted, int added) {
    }

    /** What was done: pieces put into the cart and the lines that fell short. */
    public record Result(int pieces, List<Left> left) {

        /** True when nothing at all could be repeated. */
        public boolean nothing() {
            return pieces == 0;
        }
    }

    private RepeatOrder() {
    }

    /**
     * Adds the lines of {@code last} to {@code cart}.
     *
     * @param last   the lines of the last paid order, less anything refunded
     * @param onSale the products the till may sell now, stock already net of other orders' holds ({@code SellableMenu.filter})
     */
    public static Result apply(Cart cart, List<OrderItem> last, List<Product> onSale) {
        Map<Integer, Product> byId = new HashMap<>();
        for (Product product : onSale) byId.put(product.getId(), product);
        int pieces = 0;
        List<Left> left = new ArrayList<>();
        for (OrderItem line : last) {
            Product product = line.getProductId() == null ? null : byId.get(line.getProductId());
            if (product == null) {
                left.add(new Left(line.getProductName(), Reason.NOT_SOLD, line.getQuantity(), 0));
                continue;
            }
            int added = cart.addMany(product, line.getQuantity(), line.getNote());
            pieces += added;
            if (added == line.getQuantity()) continue;
            left.add(new Left(product.getName(), added == 0 && product.getStock() - cart.quantityOf(product.getId()) <= 0 ? Reason.SOLD_OUT : Reason.LESS,
                    line.getQuantity(), added));
        }
        return new Result(pieces, List.copyOf(left));
    }

    /** One sentence per line that fell short, for the box shown after the repeat. */
    public static String notice(Result result) {
        StringBuilder text = new StringBuilder();
        for (Left item : result.left()) {
            if (text.length() > 0) text.append('\n');
            String name = SeedNames.product(item.name());
            text.append(switch (item.reason()) {
                case NOT_SOLD -> I18n.t("pos.repeat.left.notsold", name);
                case SOLD_OUT -> I18n.t("pos.repeat.left.soldout", name);
                case LESS -> I18n.t("pos.repeat.left.less", name, item.added(), item.wanted());
            });
        }
        return text.toString();
    }
}
