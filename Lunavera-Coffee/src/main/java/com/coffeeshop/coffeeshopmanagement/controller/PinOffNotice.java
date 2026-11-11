package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.model.User;
import com.coffeeshop.coffeeshopmanagement.service.PinRules;
import com.coffeeshop.coffeeshopmanagement.service.PinService;
import com.coffeeshop.coffeeshopmanagement.util.AlertUtil;
import com.coffeeshop.coffeeshopmanagement.util.Async;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.Session;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * "Your PIN was switched off" (TODO 2A.46): the box a cashier sees the next time they sign in with their password, once, when
 * wrong PINs typed for their name on a till switched their PIN off while they were away. It only tells; setting a new PIN is the
 * ordinary PIN box. Called after a password sign-in and after a till locked by the same person was opened with the password
 * (the two ways a person with no PIN comes back). The database read runs off the screen thread; nothing is shown for a person who
 * has no such notice or cannot have a PIN (an administrator).
 *
 * <p>Kept out of the login screen's own code on purpose: that code must not know the PIN service (see {@code PinServiceTest}).
 */
public final class PinOffNotice {

    private static final Logger LOGGER = Logger.getLogger(PinOffNotice.class.getName());

    private PinOffNotice() {
    }

    /** Shows the notice to the signed-in person when there is one. Does nothing otherwise. */
    public static void afterSignIn() {
        User user = Session.getCurrentUser();
        if (user == null || !PinService.mayHavePin(user)) return;
        int userId = user.getId();
        Async.run(
                () -> new PinService().takeSwitchedOffNotice(userId),
                has -> {
                    if (!has) return;
                    AlertUtil.info(I18n.t("pin.off.notice.title"), I18n.t("pin.off.notice.text", PinRules.MAX_WRONG_IN_A_ROW));
                },
                error -> LOGGER.log(Level.WARNING, "Could not look for the switched-off PIN notice at sign-in; none shown", error));
    }
}
