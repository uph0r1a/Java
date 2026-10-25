package com.coffeeshop.coffeeshopmanagement.util;

/** The one way an order number is written on screen and paper: "#12" (TODO 5.12). */
public final class OrderNumber {
    private OrderNumber() {}

    public static String format(int id) {
        return "#" + id;
    }

    /** The note a customer is asked to write on a bank transfer, and what the bank statement then shows: "DH000012" (TODO 3.56). */
    public static String transferNote(int id) {
        return "DH" + String.format(java.util.Locale.ROOT, "%06d", id);
    }

    /**
     * The order number behind what someone typed, or null when it is not one: {@code 12}, {@code #12}, {@code # 12},
     * {@code DH000012} (any capitals, spaces after the mark allowed) and the bare {@code 000012} of a transfer note. A number of up to nine
     * digits only; plain digits with a leading zero (other than exactly six, a transfer note) are left to the phone search.
     */
    public static Integer parseReference(String typed) {
        if (typed == null) return null;
        String s = typed.trim();
        boolean marked = false;
        if (s.startsWith("#")) {
            s = s.substring(1).trim();
            marked = true;
        } else if (s.length() > 2 && s.regionMatches(true, 0, "DH", 0, 2)) {
            s = s.substring(2).trim();
            marked = true;
        }
        if (!s.matches("[0-9]{1,9}")) return null;
        boolean leadingZero = s.startsWith("0");
        if (leadingZero && !marked && s.length() != 6) return null;
        int id = Integer.parseInt(s);
        return id > 0 ? id : null;
    }
}
