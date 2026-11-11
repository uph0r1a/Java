package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.config.SeedNames;
import com.coffeeshop.coffeeshopmanagement.model.OrderItem;
import com.coffeeshop.coffeeshopmanagement.model.Product;
import com.coffeeshop.coffeeshopmanagement.util.I18n;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stock that open orders hold (TODO 3.1). <b>The decision:</b> a line in an OPEN order reserves what it needs, and the reservation is
 * <i>derived</i>, never stored. What may still be sold to an order is the shelf less what the <i>other</i> open orders hold
 * ({@code ProductDAO.findAllForSale}); so a payment, a cancelled order or an emptied table frees its pieces by itself, and there is
 * no counter that could drift or be forgotten on one of the many ways an order ends.
 *
 * <p>Two places apply it. The sales screen refuses a piece the other tables hold, using the product list that is already net of
 * the holds ({@link Cart} needs no change: it compares the cart with {@code Product.getStock()}). The autosave of the order checks
 * again inside its own transaction ({@code OrderDAO.updateIfStillOpen} with the guard on), which is what stops two tills that add
 * the last piece at the same moment; {@link #shortages} is what it asks. Paying is unchanged: the real stock is taken, guarded, at
 * the payment.
 *
 * <p>Only a line that <b>grows</b> is checked: taking pieces out, or saving an order that was already over (the shelf was lowered
 * by a count), is never blocked, or a cashier could not even remove a line.
 */
public final class StockHolds {

    /**
     * A product the order may not take more of.
     *
     * @param wanted    pieces the order would hold, all its lines of the product together
     * @param available pieces that were free for this order (the shelf less the other orders' holds)
     * @param held      pieces the other open orders hold
     */
    public record Shortage(int productId, String name, int wanted, int available, int held) {
    }

    private StockHolds() {
    }

    /** Pieces per product over all lines (a product can be on two lines with different notes); lines with no product are left out. */
    static Map<Integer, Integer> totals(List<OrderItem> lines) {
        Map<Integer, Integer> totals = new LinkedHashMap<>();
        for (OrderItem line : lines) {
            if (line.getProductId() != null) totals.merge(line.getProductId(), line.getQuantity(), Integer::sum);
        }
        return totals;
    }

    /** The ids of the products that are in {@code lines}, in the order they first appear. */
    public static List<Integer> productIds(List<OrderItem> lines) {
        return new ArrayList<>(totals(lines).keySet());
    }

    /**
     * The products the order would hold more of than it did and than is free for it.
     *
     * @param saved     the lines as they are saved now
     * @param wanted    the lines to save
     * @param available the products as {@code ProductDAO.findAllForSale} reads them for this order (stock already net of the holds)
     * @return the shortages in the order the products first appear in {@code wanted}; empty when the save is fine
     */
    public static List<Shortage> shortages(List<OrderItem> saved, List<OrderItem> wanted, Map<Integer, Product> available) {
        Map<Integer, Integer> before = totals(saved);
        Map<Integer, String> names = new HashMap<>();
        for (OrderItem line : wanted) {
            if (line.getProductId() != null) names.putIfAbsent(line.getProductId(), line.getProductName());
        }
        List<Shortage> result = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : totals(wanted).entrySet()) {
            int id = entry.getKey();
            int quantity = entry.getValue();
            if (quantity <= before.getOrDefault(id, 0)) continue; // the same or fewer: never blocked
            Product product = available.get(id);
            if (product == null) continue; // gone or off: the payment check says so, it is not a matter of holds
            if (quantity > product.getStock()) {
                result.add(new Shortage(id, names.getOrDefault(id, product.getName()), quantity, Math.max(0, product.getStock()), product.getHeldElsewhere()));
            }
        }
        return result;
    }

    /** One sentence per shortage, for the box that tells the cashier why the last change was not kept. */
    public static String message(List<Shortage> shortages) {
        StringBuilder text = new StringBuilder();
        for (Shortage s : shortages) {
            if (text.length() > 0) text.append('\n');
            text.append(I18n.t("pos.hold.line", SeedNames.product(s.name()), s.wanted(), s.available(), s.held()));
        }
        return I18n.t("pos.hold.conflict", text.toString());
    }

    /**
     * The sentence for a product the cashier cannot add more of: "only N left" when the shelf is short, and when other open orders
     * hold part of it, "only N can still be sold to this order, M are in other open orders".
     */
    public static String refusal(Product product) {
        String name = SeedNames.product(product.getName());
        return product.getHeldElsewhere() > 0 ? I18n.t("pos.stock.short.held", name, product.getStock(), product.getHeldElsewhere())
                : I18n.t("pos.stock.short.message", name, product.getStock());
    }
}
