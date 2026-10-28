package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.dao.ProductDAO;
import com.coffeeshop.coffeeshopmanagement.model.OrderItem;
import com.coffeeshop.coffeeshopmanagement.model.Product;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Looks at the stock again just before the payment dialog opens (TODO 3.1, the small version). A cart never holds stock: two
 * tables can both hold the last cup, and the till's product list can be twenty seconds old. Without this the cashier collects
 * the money and only then is told that the payment failed. With it, the cashier hears which item ran out while nothing has been
 * charged yet.
 *
 * <p>It is a warning, not a guarantee: another till can still sell the item between this check and the click on "Pay", so the
 * payment keeps its own guarded {@code UPDATE ... stock >= ?} (in {@code OrderDAO}) and still fails cleanly in that case. The
 * same rule as at payment is used: the lines of one product are added up (a product can be on two lines with different notes),
 * and a product that is switched off or deleted counts as not available (TODO 3.34).
 */
public final class StockCheck {

    /** Why an item cannot be sold as the cart holds it. */
    public enum Kind {
        /** There are fewer in stock than the cart holds. */
        SHORT,
        /** The product was switched off or deleted since it was added. */
        UNAVAILABLE
    }

    /**
     * One product the cart cannot have.
     *
     * @param name      the product's current name (the line's name when the product is gone)
     * @param wanted    pieces in the cart, all lines of the product together
     * @param available pieces that can be sold now (0 when unavailable)
     */
    public record Shortage(int productId, String name, int wanted, int available, Kind kind) {
    }

    private StockCheck() {
    }

    /** Re-reads the products of the cart (one query) and compares; empty means the payment dialog can open. */
    public static List<Shortage> check(List<OrderItem> lines, ProductDAO productDAO) {
        Map<Integer, Product> fresh = new HashMap<>();
        for (Product product : productDAO.findByIds(productIds(lines))) {
            fresh.put(product.getId(), product);
        }
        return shortages(lines, fresh);
    }

    /**
     * What is wrong with the cart given the products as they are now, in the order the products first appear in the cart.
     * A line that is not tied to a product (no product id) is never a shortage.
     */
    public static List<Shortage> shortages(List<OrderItem> lines, Map<Integer, Product> fresh) {
        Map<Integer, Integer> wanted = new LinkedHashMap<>();
        Map<Integer, String> lineNames = new HashMap<>();
        for (OrderItem line : lines) {
            if (line.getProductId() == null) {
                continue;
            }
            wanted.merge(line.getProductId(), line.getQuantity(), Integer::sum);
            lineNames.putIfAbsent(line.getProductId(), line.getProductName());
        }
        List<Shortage> result = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : wanted.entrySet()) {
            int productId = entry.getKey();
            int quantity = entry.getValue();
            Product product = fresh.get(productId);
            if (product == null) {
                result.add(new Shortage(productId, lineNames.get(productId), quantity, 0, Kind.UNAVAILABLE));
            } else if (!product.isActive()) {
                result.add(new Shortage(productId, product.getName(), quantity, 0, Kind.UNAVAILABLE));
            } else if (product.getStock() < quantity) {
                result.add(new Shortage(productId, product.getName(), quantity, Math.max(0, product.getStock()), Kind.SHORT));
            }
        }
        return result;
    }

    private static List<Integer> productIds(List<OrderItem> lines) {
        List<Integer> ids = new ArrayList<>();
        for (OrderItem line : lines) {
            if (line.getProductId() != null && !ids.contains(line.getProductId())) {
                ids.add(line.getProductId());
            }
        }
        return ids;
    }
}
