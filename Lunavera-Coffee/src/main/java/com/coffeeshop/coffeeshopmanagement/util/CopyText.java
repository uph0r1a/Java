package com.coffeeshop.coffeeshopmanagement.util;

import java.util.List;

/**
 * The text that a "Sao chép" button or a right-click menu puts on the clipboard (TODO 5.46), built in one place so that it is the text
 * the screen SHOWS, no more: a gift card's code, a transfer note, an order number as the history list writes it, a phone number, an
 * error box, one row of a table. Nothing here reads a password, a PIN, a transfer account or a setting, and a test pins that. Pure Java:
 * the clipboard itself is {@link ClipboardText}.
 */
public final class CopyText {

    private CopyText() {
    }

    /** The order number as the history list shows it: {@code #124}. */
    public static String orderNumber(int id) {
        return "#" + id;
    }

    /** The note the customer types in the bank app, as the payment box shows it: {@code DH000124}; nothing for an order not saved yet. */
    public static String transferNote(int id) {
        return id > 0 ? OrderNumber.transferNote(id) : "";
    }

    /** A gift card's code as shown when it is sold or listed. */
    public static String giftCardCode(String code) {
        return code == null ? "" : code.strip();
    }

    /** A phone number as the customer list shows it; nothing when the list shows a dash because there is none. */
    public static String phone(String shown) {
        if (shown == null) return "";
        String text = shown.strip();
        return text.equals("-") ? "" : text;
    }

    /** The text of an error box: its title and its message, as shown. */
    public static String error(String title, String message) {
        String head = title == null ? "" : title.strip();
        String body = message == null ? "" : message.strip();
        if (head.isEmpty()) return body;
        return body.isEmpty() ? head : head + "\n" + body;
    }

    /**
     * One row of a table as the columns show it, separated by tabs (so it pastes into a spreadsheet one cell to a column). A cell that
     * shows nothing stays an empty cell so the others keep their places; empty cells at the end are dropped.
     */
    public static String row(List<String> shownCells) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < shownCells.size(); i++) {
            if (i > 0) out.append('\t');
            String cell = shownCells.get(i);
            out.append(cell == null ? "" : cell.strip().replace('\t', ' ').replace('\n', ' ').replace('\r', ' '));
        }
        int end = out.length();
        while (end > 0 && out.charAt(end - 1) == '\t') end--;
        return out.substring(0, end);
    }
}
