package com.coffeeshop.coffeeshopmanagement.service;

/** Catches a typo in the stock box ("20" typed as "2000") before it is saved (TODO 5.13). */
public final class StockPolicy {

    /** A rise of at least this many units, and at least this many times the old amount, is asked about. */
    static final int BIG_RISE = 100;
    static final int BIG_FACTOR = 5;

    private StockPolicy() {}

    public static boolean looksTooBig(int oldStock, int newStock) {
        int rise = newStock - oldStock;
        return rise >= BIG_RISE && newStock >= (long) Math.max(1, oldStock) * BIG_FACTOR;
    }
}
