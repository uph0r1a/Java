package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.model.LineChoice;
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
        return lineFor(productId, LineChoice.NONE);
    }

    /**
     * The line of this product with no note and exactly this size and these extras, if any (TODO 4.47): a Large is never topped up by
     * tapping a Small, and a drink with an extra shot is its own line.
     */
    public Optional<OrderItem> lineFor(int productId, LineChoice choice) {
        String key = LineOptions.key(choice);
        return lines.stream()
                .filter(item -> item.getProductId() != null && item.getProductId() == productId)
                .filter(item -> item.getNote() == null)
                .filter(item -> java.util.Objects.equals(LineOptions.key(item), key))
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
        return add(product, LineChoice.NONE);
    }

    /**
     * One more piece of the product with a size and extras (TODO 4.47): on the line that has the same ones, or on a new line priced at
     * the product's price plus what the choice adds (never below zero), with the names and cost of the choice copied onto it.
     */
    public Result add(Product product, LineChoice choice) {
        Optional<OrderItem> existing = lineFor(product.getId(), choice);
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
        BigDecimal unitPrice = product.getPrice().add(choice.priceDelta()).max(BigDecimal.ZERO);
        item.setUnitPrice(unitPrice);
        item.setLineTotal(unitPrice);
        LineOptions.apply(item, choice);
        lines.add(item);
        return new Result(Outcome.ADDED, product.getStock());
    }

    /**
     * Up to {@code quantity} pieces of the product, with {@code note} (null or empty: none), at the product's current price (TODO 4.56,
     * "the same as last time"). They go on the line of the same product that carries exactly this note, or on a new one. Never more
     * than the stock leaves (all lines of the product together) and never past the per-line ceiling.
     *
     * @return how many pieces were added: all of them, or fewer, or none when the stock or the ceiling leaves no room
     */
    public int addMany(Product product, int quantity, String note) {
        String cleaned = OrderNote.clean(note);
        Optional<OrderItem> existing = lines.stream()
                .filter(item -> item.getProductId() != null && item.getProductId() == product.getId())
                .filter(item -> Objects.equals(item.getNote(), cleaned))
                .findFirst();
        int onLine = existing.map(OrderItem::getQuantity).orElse(0);
        int room = Math.min(product.getStock() - quantityOf(product.getId()), Limits.MAX_LINE_QUANTITY - onLine);
        int added = Math.max(0, Math.min(quantity, room));
        if (added == 0) {
            return 0;
        }
        if (existing.isPresent()) {
            setQuantity(existing.get(), onLine + added);
            return added;
        }
        OrderItem item = new OrderItem();
        item.setProductId(product.getId());
        item.setProductName(product.getName());
        item.setNote(cleaned);
        item.setUnitPrice(product.getPrice());
        item.setQuantity(added);
        item.setLineTotal(product.getPrice().multiply(java.math.BigDecimal.valueOf(added)));
        lines.add(item);
        return added;
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
                            && java.util.Objects.equals(other.getNote(), note) && LineOptions.same(other, line))
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

    /** What a change of one line's quantity should do (TODO 6.5: it was the middle of {@code OrderController.changeQuantity}). */
    public enum QuantityMove {
        /** The same number: nothing happens. */
        UNCHANGED,
        /** The new number is over the ceiling of one line (TODO 3.59): say so. */
        LINE_LIMIT,
        /** The stock cannot cover the new number (TODO 4.9): say so. */
        NOT_ENOUGH_STOCK,
        /** The line would go to 0: ask before removing it (TODO 5.5). */
        ASK_REMOVE,
        /** Go ahead and set it. */
        SET
    }

    /**
     * "+", "-" and a typed number (TODO 5.27) all come through here, so no way of changing a quantity can skip the limit, the stock warning
     * or the "remove the line?" question. The checks run in this order; the stock is asked about only when the number goes up and the
     * ceiling allows it.
     *
     * @param overStock answers whether the stock cannot cover the new number (it reads the stock, so it is asked last)
     */
    public static QuantityMove decideQuantity(int current, int wanted, java.util.function.BooleanSupplier overStock) {
        if (wanted == current) return QuantityMove.UNCHANGED;
        if (wanted > current) {
            if (exceedsLineLimit(wanted)) return QuantityMove.LINE_LIMIT;
            if (overStock.getAsBoolean()) return QuantityMove.NOT_ENOUGH_STOCK;
        }
        if (wanted < current && wanted <= 0) return QuantityMove.ASK_REMOVE;
        return QuantityMove.SET;
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
