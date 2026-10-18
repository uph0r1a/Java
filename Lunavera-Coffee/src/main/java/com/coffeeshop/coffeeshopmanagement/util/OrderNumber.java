package com.coffeeshop.coffeeshopmanagement.util;

/** The one way an order number is written on screen and paper: "#12" (TODO 5.12). */
public final class OrderNumber {
    private OrderNumber() {}

    public static String format(int id) {
        return "#" + id;
    }
}
