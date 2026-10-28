package com.coffeeshop.coffeeshopmanagement.service;

/**
 * The note on one order line: "ít đường", "không đá" (TODO 4.9). It is part of what is sold (the bar reads it), so it is kept with
 * the line on the order and printed on the receipt, and two lines of one product with different notes stay two lines.
 * No screen and no database here, so the rules are the same wherever a note is typed or read.
 */
public final class OrderNote {

    /** A note, not a letter: one short line the cashier can type while the customer is talking. */
    public static final int MAX_LENGTH = 100;

    private OrderNote() {
    }

    /**
     * The note as it is kept: line breaks, tabs and runs of spaces become one space, control characters go, the ends are trimmed,
     * and a note longer than {@link #MAX_LENGTH} is cut. Empty means "no note" and is null, so a line with no note is always
     * {@code null}, never an empty text that looks different to the database and to the code.
     */
    public static String clean(String typed) {
        if (typed == null) return null;
        StringBuilder out = new StringBuilder(typed.length());
        boolean pendingSpace = false;
        for (int i = 0; i < typed.length(); i++) {
            char c = typed.charAt(i);
            if (Character.isWhitespace(c) || Character.isSpaceChar(c)) {
                pendingSpace = out.length() > 0;
            } else if (!Character.isISOControl(c)) {
                if (pendingSpace) out.append(' ');
                pendingSpace = false;
                out.append(c);
            }
        }
        String note = out.length() > MAX_LENGTH ? out.substring(0, MAX_LENGTH).stripTrailing() : out.toString();
        return note.isEmpty() ? null : note;
    }

    /** True when {@code note} (as typed) is longer than a note may be, so the form can say so instead of cutting it silently. */
    public static boolean tooLong(String typed) {
        String single = typed == null ? "" : typed.strip();
        return single.length() > MAX_LENGTH;
    }

    /** The name with its note for a one-line place such as the receipt window: {@code Latte (less sugar)}; the bare name when there is none. */
    public static String withNote(String name, String note) {
        String clean = clean(note);
        return clean == null ? name : name + " (" + clean + ")";
    }

    /**
     * {@code typed} with {@code quick} added: the quick buttons of the note box add to what is there (a cashier taps "less sugar", then
     * "no ice"), with a comma between, and tapping one that is already in the note does nothing.
     */
    public static String append(String typed, String quick) {
        String current = clean(typed);
        String add = clean(quick);
        if (add == null) return current == null ? "" : current;
        if (current == null) return add;
        for (String part : current.split("\\s*,\\s*")) {
            if (part.equalsIgnoreCase(add)) return current;
        }
        return current + ", " + add;
    }
}
