package com.coffeeshop.coffeeshopmanagement.util;

import java.util.List;

/**
 * How receipts are printed on THIS computer (TODO 5.39): where they go (ask every time, the default printer, or one printer chosen by
 * name), the paper (as the printer says, or a 58 mm or 80 mm roll), what happens after a payment (open the receipt and wait for a click,
 * or print it at once) and how many copies. The choice is per computer, because a printer belongs to one till, so it is kept in
 * {@code ui-settings.properties} next to the language, the text size and the sounds.
 *
 * <p>Nothing here names a JavaFX class: what is decided (which printer, whether to fall back to the dialog, how wide a roll is) is tested
 * without JavaFX. {@code HoaDonController} asks the printers of the computer and does the printing.
 */
public final class ReceiptPrinting {

    static final String KEY_WHERE = "print.where";
    static final String KEY_PRINTER = "print.printer";
    static final String KEY_PAPER = "print.paper";
    static final String KEY_AFTER = "print.after";
    static final String KEY_COPIES = "print.copies";

    /** At most this many copies from the settings; more are made with the dialog. */
    public static final int MAX_COPIES = 5;
    /** A printer name longer than this is cut: no real name is. */
    public static final int MAX_NAME = 200;

    /** Where a receipt goes. */
    public enum Where { ASK, DEFAULT, NAMED }

    /** AfterPayment a payment: wait for a click on "Print" (how it always was) or print at once. */
    public enum AfterPayment { ASK, AUTO }

    /** The roll the receipt is laid out for. {@code AUTO} trusts the width the printer reports. */
    public enum Paper {
        AUTO(0), MM58(58), MM80(80);

        private final int millimetres;

        Paper(int millimetres) {
            this.millimetres = millimetres;
        }

        public int millimetres() {
            return millimetres;
        }

        /** The width in points (1/72 inch), or 0 for {@code AUTO}. */
        public double points() {
            return millimetres * 72.0 / 25.4;
        }
    }

    /**
     * What one computer chose. The constructor puts every field right, so a hand-edited file or a half-filled form can never give a state
     * that makes no sense: a named printer without a name is "ask"; an automatic print needs a printer that is not asked for each time;
     * copies stay between 1 and {@link #MAX_COPIES}.
     */
    public record Choice(Where where, String printer, Paper paper, AfterPayment after, int copies) {
        public Choice {
            printer = cleanName(printer);
            if (where == null) where = Where.ASK;
            if (where == Where.NAMED && printer.isEmpty()) where = Where.ASK;
            if (paper == null) paper = Paper.AUTO;
            if (after == null || where == Where.ASK) after = AfterPayment.ASK;
            copies = Math.max(1, Math.min(MAX_COPIES, copies));
        }

        /** True when a receipt is printed without the print dialog (the "Print with options" button is then shown). */
        public boolean direct() {
            return where != Where.ASK;
        }

        /** True when the receipt is printed as soon as it opens after a payment. */
        public boolean automatic() {
            return after == AfterPayment.AUTO;
        }
    }

    /** The behaviour before this setting existed: the dialog every time. */
    public static final Choice ASK_EVERY_TIME = new Choice(Where.ASK, "", Paper.AUTO, AfterPayment.ASK, 1);

    /** What to do for one print. */
    public enum Kind {
        /** Open the print dialog. */
        DIALOG,
        /** Print on the default printer of the computer, with no dialog. */
        DEFAULT_PRINTER,
        /** Print on the printer named in the settings, with no dialog. */
        NAMED_PRINTER,
        /** The printer asked for is not there: say so, then open the dialog. */
        DIALOG_PRINTER_MISSING
    }

    /** {@code printer} is the name to use for {@code DEFAULT_PRINTER} and {@code NAMED_PRINTER}, and the name that was not found for the missing case. */
    public record Target(Kind kind, String printer) {
    }

    private ReceiptPrinting() {
    }

    /**
     * Decides for one print. {@code installed} are the names of the printers the computer has; {@code defaultName} is the name of its
     * default printer or null. {@code forceDialog} is the "Print with options" button: the dialog opens whatever the settings say.
     * A name is matched as typed first, then without capitals or outer spaces (a driver update may change the case).
     */
    public static Target decide(Choice choice, List<String> installed, String defaultName, boolean forceDialog) {
        if (choice == null || forceDialog || choice.where() == Where.ASK) return new Target(Kind.DIALOG, "");
        List<String> names = installed == null ? List.of() : installed;
        if (choice.where() == Where.DEFAULT) {
            String name = defaultName == null ? "" : defaultName.strip();
            return name.isEmpty() ? new Target(Kind.DIALOG_PRINTER_MISSING, "") : new Target(Kind.DEFAULT_PRINTER, defaultName);
        }
        String wanted = choice.printer();
        for (String name : names) {
            if (name != null && name.equals(wanted)) return new Target(Kind.NAMED_PRINTER, name);
        }
        for (String name : names) {
            if (name != null && name.strip().equalsIgnoreCase(wanted.strip())) return new Target(Kind.NAMED_PRINTER, name);
        }
        return new Target(Kind.DIALOG_PRINTER_MISSING, wanted);
    }

    /** True when the receipt is laid out for a roll: always for 58 or 80 mm, and for "as the printer says" when its paper is narrow. */
    public static boolean useRoll(Paper paper, double printableWidthPoints, double narrowBelowPoints) {
        return (paper != null && paper != Paper.AUTO) || printableWidthPoints < narrowBelowPoints;
    }

    /**
     * The width the roll receipt is built for: what the printer reports for "as the printer says"; for 58 or 80 mm the width of that
     * roll, but never more than the printer can print (a 58 mm roll prints about 48 mm; a sheet printer prints more than the roll).
     * A printer that reports no printable width (0 or less, or not a number) does not make the receipt zero wide: the roll's own width
     * is the best guess then.
     */
    public static double rollWidth(Paper paper, double printableWidthPoints) {
        if (paper == null || paper == Paper.AUTO) return printableWidthPoints;
        if (!(printableWidthPoints > 0)) return paper.points();
        return Math.min(paper.points(), printableWidthPoints);
    }

    // ------------------------------------------------------------------------------------------------ the stored choice

    /** What this computer chose; the dialog every time when nothing was saved or the file cannot be read. */
    public static Choice load() {
        try {
            return new Choice(parseWhere(UiSettings.get(KEY_WHERE, "")), UiSettings.get(KEY_PRINTER, ""),
                    parsePaper(UiSettings.get(KEY_PAPER, "")), parseAfter(UiSettings.get(KEY_AFTER, "")),
                    parseCopies(UiSettings.get(KEY_COPIES, "1")));
        } catch (RuntimeException e) {
            return ASK_EVERY_TIME;
        }
    }

    /** Remembers the choice; true when it differs from what was kept. */
    public static boolean save(Choice choice) {
        Choice now = choice == null ? ASK_EVERY_TIME : choice;
        if (now.equals(load())) return false;
        UiSettings.put(KEY_WHERE, now.where().name());
        UiSettings.put(KEY_PRINTER, now.printer());
        UiSettings.put(KEY_PAPER, now.paper().name());
        UiSettings.put(KEY_AFTER, now.after().name());
        UiSettings.put(KEY_COPIES, Integer.toString(now.copies()));
        return true;
    }

    static Where parseWhere(String text) {
        return parse(Where.values(), text, Where.ASK);
    }

    static Paper parsePaper(String text) {
        return parse(Paper.values(), text, Paper.AUTO);
    }

    static AfterPayment parseAfter(String text) {
        return parse(AfterPayment.values(), text, AfterPayment.ASK);
    }

    static int parseCopies(String text) {
        try {
            return Integer.parseInt(text == null ? "" : text.strip());
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private static <E extends Enum<E>> E parse(E[] values, String text, E fallback) {
        if (text == null) return fallback;
        for (E value : values) {
            if (value.name().equalsIgnoreCase(text.strip())) return value;
        }
        return fallback;
    }

    /** A printer name without control characters or outer spaces, at most {@link #MAX_NAME} characters. */
    static String cleanName(String name) {
        if (name == null) return "";
        String one = name.replaceAll("[\\p{Cntrl}\\p{Zl}\\p{Zp}]", " ").strip();
        if (one.length() > MAX_NAME) one = one.substring(0, MAX_NAME).strip();
        return one;
    }
}
