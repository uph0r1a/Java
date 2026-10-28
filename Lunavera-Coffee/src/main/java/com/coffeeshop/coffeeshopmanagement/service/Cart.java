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
        NOT_ENOUGH_STOCK,
        /** One more piece would be more than {@link Limits#MAX_LINE_QUANTITY} on one line; nothing changed (TODO 3.59). */
        LINE_LIMIT
    }

    /** What {@link #add} did; {@code stock} is the product's stock, for the "only N left" message. */
    public record Result(Outcome outcome, int stock) {
    }

    private final List<OrderItem> lines;

    public Cart(List<OrderItem> lines) {
        this.lines = Objects.requireNonNull(lines);
    }

    /**
     * The line of this product that has no note, if any. A line with a note (TODO 4.9: "less sugar") is its own line and is never
     * topped up by tapping the product again: the second cup may be wanted with the usual sugar.
     */
    public Optional<OrderItem> lineFor(int productId) {
        return lines.stream()
                .filter(item -> item.getProductId() != null && item.getProductId() == productId)
                .filter(item -> item.getNote() == null)
                .findFirst();
    }

    /** All pieces of the product in the cart, over every line it has (lines with different notes count together against the stock). */
    public int quantityOf(int productId) {
        return lines.stream()
                .filter(item -> item.getProductId() != null && item.getProductId() == productId)
                .mapToInt(OrderItem::getQuantity)
                .sum();
    }

    /** One more piece of the product: on its plain line (no note), or on a new line at the product's current price. */
    public Result add(Product product) {
        Optional<OrderItem> existing = lineFor(product.getId());
        int newQuantity = existing.map(OrderItem::getQuantity).orElse(0) + 1;
        if (newQuantity > Limits.MAX_LINE_QUANTITY) {
            return new Result(Outcome.LINE_LIMIT, product.getStock());
        }
        if (quantityOf(product.getId()) + 1 > product.getStock()) {
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

    /**
     * Sets a line's quantity and its total; a quantity of zero or less removes the line, and one above
     * {@link Limits#MAX_LINE_QUANTITY} is held at it (the screens ask before they get here, TODO 3.59).
     */
    public void setQuantity(OrderItem line, int newQuantity) {
        if (newQuantity <= 0) {
            lines.remove(line);
        } else {
            int quantity = Math.min(newQuantity, Limits.MAX_LINE_QUANTITY);
            line.setQuantity(quantity);
            line.setLineTotal(line.getUnitPrice().multiply(BigDecimal.valueOf(quantity)));
        }
    }

    /** What {@link #setNote} did. */
    public enum NoteOutcome {
        /** The line now has this note (or already had it). */
        SET,
        /** Another line of the same product already had this note, so the two became one line with the pieces added. */
        MERGED
    }

    /**
     * Puts a note on a line, or takes it off (empty text). When another line of the same product already carries exactly this note
     * (or none, for an emptied note) the two lines are joined into one, so the same drink is never listed twice with the same words;
     * if joining would pass the per-line ceiling the lines stay apart.
     */
    public NoteOutcome setNote(OrderItem line, String typed) {
        String note = OrderNote.clean(typed);
        if (java.util.Objects.equals(note, line.getNote())) {
            return NoteOutcome.SET;
        }
        if (line.getProductId() != null) {
            Optional<OrderItem> same = lines.stream()
                    .filter(other -> other != line && java.util.Objects.equals(other.getProductId(), line.getProductId())
                            && java.util.Objects.equals(other.getNote(), note))
                    .findFirst();
            if (same.isPresent() && same.get().getQuantity() + line.getQuantity() <= Limits.MAX_LINE_QUANTITY) {
                int total = same.get().getQuantity() + line.getQuantity();
                lines.remove(line);
                setQuantity(same.get(), total);
                return NoteOutcome.MERGED;
            }
        }
        line.setNote(note);
        return NoteOutcome.SET;
    }

    /**
     * Would {@code wantedQuantity} pieces on {@code line} be more than the stock, counting the other lines of the same product too?
     * A product that is not known (null) has no limit here.
     */
    public boolean exceedsStock(Product product, OrderItem line, int wantedQuantity) {
        if (product == null) return false;
        int others = quantityOf(product.getId()) - (line != null && line.getProductId() != null && line.getProductId() == product.getId()
                ? line.getQuantity() : 0);
        return others + wantedQuantity > product.getStock();
    }

    /** Would {@code wantedQuantity} pieces on one line be more than the ceiling? */
    public static boolean exceedsLineLimit(int wantedQuantity) {
        return wantedQuantity > Limits.MAX_LINE_QUANTITY;
    }

    /** Would {@code wantedQuantity} pieces be more than the stock? A product that is not known (null) has no limit here. */
    public static boolean exceedsStock(Product product, int wantedQuantity) {
        return product != null && wantedQuantity > product.getStock();
    }

    public BigDecimal subtotal() {
        return lines.stream().map(OrderItem::getLineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** A long: many lines of the largest quantity would not fit an int (TODO 3.59). */
    public long totalQuantity() {
        return lines.stream().mapToLong(OrderItem::getQuantity).sum();
    }
}
