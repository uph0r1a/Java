package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.util.I18n;

import java.util.ArrayList;
import java.util.List;

/**
 * What a rebuild of the till screen would lose (TODO 1.7: the guard for the in-app language switch). No JavaFX, so it is
 * unit-tested.
 *
 * <p>The till saves an open order by itself after every change, so a cart of a started order is safe once the last save has
 * reached the database. What is NOT safe: a save that failed or is still running, a customer typed into the two boxes
 * and not yet saved with an order, and an order that is only being started. Everything else (the table, the order, the
 * lines) is read again from the database when the screen is rebuilt.
 */
public final class PosLeaveRules {

    private PosLeaveRules() {
    }

    /** The table or take-away order on screen, to select again after the rebuild. */
    public record Place(Integer table, boolean takeAway, Integer takeAwayOrderId) {
    }

    /**
     * @param savesDone      every autosave has finished and none has failed
     * @param starting       an order is being started right now (not saved yet)
     * @param orderSaved     the screen shows an order that exists in the database
     * @param cartLines      lines in the cart on screen
     * @param customerTyped  the name or phone boxes hold something that is not saved with the order
     */
    public record State(boolean savesDone, boolean starting, boolean orderSaved, int cartLines, boolean customerTyped) {
    }

    /** The lines to show the person, in the language in use; empty when a rebuild loses nothing. */
    public static List<String> lost(State state) {
        List<String> lines = new ArrayList<>();
        if (state.starting()) {
            lines.add(I18n.t("lang.lost.starting"));
        }
        if (state.cartLines() > 0 && !state.orderSaved()) {
            lines.add(I18n.t("lang.lost.cart", state.cartLines()));
        } else if (state.cartLines() > 0 && !state.savesDone()) {
            lines.add(I18n.t("lang.lost.lastsave"));
        }
        if (state.customerTyped()) {
            lines.add(I18n.t("lang.lost.customer"));
        }
        return lines;
    }

    /**
     * True when the name or phone boxes hold text that is not the saved customer: a name or phone typed for someone who is not
     * attached to the order. The "walk-in" text the screen fills in by itself does not count, nor does the saved customer's own text.
     */
    public static boolean customerTyped(String name, String phone, String savedName, String savedPhone, String walkInText) {
        String n = name == null ? "" : name.trim();
        String p = phone == null ? "" : phone.trim();
        boolean nameTyped = !n.isEmpty() && !n.equals(walkInText) && !n.equals(savedName == null ? null : savedName.trim());
        boolean phoneTyped = !p.isEmpty() && !p.equals(savedPhone == null ? null : savedPhone.trim());
        return nameTyped || phoneTyped;
    }
}
