package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.util.OffScreenThread;
import com.coffeeshop.coffeeshopmanagement.service.ProductOptionService;
import com.coffeeshop.coffeeshopmanagement.model.LineChoice;
import com.coffeeshop.coffeeshopmanagement.model.OrderItem;
import com.coffeeshop.coffeeshopmanagement.model.Product;
import com.coffeeshop.coffeeshopmanagement.model.ProductOption;
import com.coffeeshop.coffeeshopmanagement.service.Cart;
import com.coffeeshop.coffeeshopmanagement.config.SeedNames;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * What the sales screen does to one cart line before and after it exists, kept out of {@code OrderController} (which may not grow):
 * asking for the size and extras when a product that has them is tapped (TODO 4.47) and asking for the note of a line (TODO 4.9).
 * It remembers the switched-on options of every product so a tap never waits for the database; {@link #reload} refreshes them
 * whenever the sales screen reloads its products.
 */
final class CartLineFlow {

    private static volatile Map<Integer, List<ProductOption>> options = Map.of();

    private CartLineFlow() {
    }

    /** Reads the options again; if that fails the last ones stay (a failing read must not stop the till selling). */
    @OffScreenThread
    static void reload() {
        try {
            options = new ProductOptionService().findAllActive();
        } catch (RuntimeException e) {
            // keep what we have
        }
    }

    /**
     * What to add for a tap on {@code product}: a plain drink when it has no options, else whatever the cashier picks in the box.
     * Empty means the box was cancelled and nothing is added.
     */
    static Optional<LineChoice> choose(Product product) {
        List<ProductOption> list = options.getOrDefault(product.getId(), List.of());
        return list.isEmpty() ? Optional.of(LineChoice.NONE) : OptionPickerDialog.show(product, list);
    }

    /** Asks for a new note for one cart line and puts it on the line; true when the cart changed and must be saved and drawn again. */
    static boolean editNote(Cart cart, OrderItem line) {
        Optional<String> typed = OrderNoteDialog.show(SeedNames.product(line.getProductName()), line.getNote());
        if (typed.isEmpty()) {
            return false;
        }
        cart.setNote(line, typed.get());
        return true;
    }
}
