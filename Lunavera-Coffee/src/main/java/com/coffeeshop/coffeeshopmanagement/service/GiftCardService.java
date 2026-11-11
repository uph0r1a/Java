package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.dao.GiftCardDAO;
import com.coffeeshop.coffeeshopmanagement.model.GiftCard;

import java.util.List;
import java.util.Optional;

/**
 * What the gift-card screens ask for (TODO 6.21): sell a card, look a code up, list the cards, what the shop still owes, void one.
 * {@link GiftCardRules} holds the rules; {@link GiftCardDAO} the permissions, the audit lines and the guard against guessing codes
 * ({@link GiftCardLookupGuard}, one per till). A guard stands in the DAO, so every way in is counted. {@code GiftCardDialog},
 * {@code GiftCardListDialog}, {@code SplitPaymentPanel} and {@code ShiftCloseDialog} hold no DAO of their own.
 */
public class GiftCardService {

    private final GiftCardDAO cards;

    public GiftCardService() {
        this(new GiftCardDAO());
    }

    GiftCardService(GiftCardDAO cards) {
        this.cards = cards;
    }

    public GiftCard sell(long amount) {
        return cards.sell(amount);
    }

    /** The card with this code, if there is one; a "wait" {@code DataAccessException} while the lookups on this till are paused. */
    public Optional<GiftCard> findByCode(String typed) {
        return cards.findByCode(typed);
    }

    public List<GiftCardDAO.Row> findAll(int limit) {
        return cards.findAll(limit);
    }

    public GiftCardDAO.OpenBalance openBalance() {
        return cards.openBalance();
    }

    public GiftCard voidCard(String typedCode) {
        return cards.voidCard(typedCode);
    }

    public List<com.coffeeshop.coffeeshopmanagement.model.GiftCardUse> usesForOrder(int orderId) {
        return cards.usesForOrder(orderId);
    }
}
