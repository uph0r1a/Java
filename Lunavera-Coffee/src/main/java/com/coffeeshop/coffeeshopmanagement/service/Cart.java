package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.model.OrderItem;
import com.coffeeshop.coffeeshopmanagement.model.Product;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The rules of the cart on the sales screen, with no screen in them (TODO 6.5): adding a product (a second tap adds a piece to
 * the same line), changing a line's quantity (zero removes the line), never selling more than the stock, and the totals.
 * It works on the list it is given, so the screen can hand over its observable list and keep it as what the table shows.
 * Asking the person ("remove this line?"), showing a warning and saving the order stay in the controller.
 */
public final class Cart {

    public enum Outcome {
        /** A new line was added. */
        ADDED,
        /** The product was already in the cart: its line has one more piece. */
        INCREASED,
        /** One more piece would be more than the stock; nothing changed. */
        NOT_ENOUGH_STOCK
    }

    /** What {@link #add} did; {@code stock} is the product's stock, for the "only N left" message. */
    public record Result(Outcome outcome, int stock) {
    }

    private final List<OrderItem> lines;

    public Cart(List<OrderItem> lines) {
        this.lines = Objects.requireNonNull(lines);
    }

    /** The line already holding this product, if any. */
    public Optional<OrderItem> lineFor(int productId) {
        return lines.stream()
                .filter(item -> item.getProductId() != null && item.getProductId() == productId)
                .findFirst();
    }

    /** One more piece of the product: on its existing line, or on a new line at the product's current price. */
    public Result add(Product product) {
        Optional<OrderItem> existing = lineFor(product.getId());
        int newQuantity = existing.map(OrderItem::getQuantity).orElse(0) + 1;
        if (newQuantity > product.getStock()) {
            return new Result(Outcome.NOT_ENOUGH_STOCK, product.getStock());
        }
        if (existing.isPresent()) {
            setQuantity(existing.get(), newQuantity);
            return new Result(Outcome.INCREASED, product.getStock());
        }
        OrderItem item = new OrderItem();
        item.setProductId(product.getId());
        item.setProductName(product.getName());
        item.setQuantity(1);
        item.setUnitPrice(product.getPrice());
        item.setLineTotal(product.getPrice());
        lines.add(item);
        return new Result(Outcome.ADDED, product.getStock());
    }

    /** Sets a line's quantity and its total; a quantity of zero or less removes the line. */
    public void setQuantity(OrderItem line, int newQuantity) {
        if (newQuantity <= 0) {
            lines.remove(line);
        } else {
            line.setQuantity(newQuantity);
            line.setLineTotal(line.getUnitPrice().multiply(BigDecimal.valueOf(newQuantity)));
        }
    }

    /** Would {@code wantedQuantity} pieces be more than the stock? A product that is not known (null) has no limit here. */
    public static boolean exceedsStock(Product product, int wantedQuantity) {
        return product != null && wantedQuantity > product.getStock();
    }

    public BigDecimal subtotal() {
        return lines.stream().map(OrderItem::getLineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public int totalQuantity() {
        return lines.stream().mapToInt(OrderItem::getQuantity).sum();
    }
}
