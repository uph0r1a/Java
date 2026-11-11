package com.coffeeshop.coffeeshopmanagement.util;

import com.coffeeshop.coffeeshopmanagement.dao.GiftCardDAO.OpenBalance;

import java.util.List;

/** The words and CSV line for the money still on gift cards (TODO 4.45), shown in the report, the shift close and the gift-card window. */
public final class GiftCardText {

    private GiftCardText() {
    }

    /** "Gift cards still to be used: 1.200.000 đ on 4 cards"; empty when no card holds money. */
    public static String line(OpenBalance open) {
        if (open == null || open.cards() == 0) return "";
        return I18n.t("giftcard.open.line", CurrencyUtil.format(Money.fromDong(open.amount())), open.cards());
    }

    /** A blank line, then the cards that hold money and the amount (whole dong): what the shop still owes in drinks (the other blocks of the CSV, TODO 5.34). */
    public static List<String> csvLines(OpenBalance open) {
        long amount = open == null ? 0 : open.amount();
        return List.of("", I18n.t("report.csv.giftcards.header"),
                (open == null ? 0 : open.cards()) + "," + Money.fromDong(amount).toPlainString());
    }
}
