package com.coffeeshop.coffeeshopmanagement.util;

import java.text.Collator;
import java.text.ParseException;
import java.text.RuleBasedCollator;
import java.util.Comparator;
import java.util.Locale;

/**
 * How names are put in order: the Vietnamese way (TODO 3.84). {@code Ă} and {@code Â} come right after {@code A}, {@code Đ} right
 * after {@code D}, {@code Ơ} after {@code O}, {@code Ư} after {@code U}, capitals and small letters count the same. One place for
 * the screens' tables ({@link PagedTable}) and for the database: SQLite compares text by code point and would put every accented
 * letter after {@code Z}, so {@code DatabaseConfig} gives each SQLite connection a collation that calls {@link #compare}, and
 * MariaDB's {@code utf8mb4_unicode_ci} does the same job on its own side.
 *
 * <p>Strength SECONDARY: letters and their marks decide, capitalisation does not (the same as {@code utf8mb4_unicode_ci}, so the two
 * databases give the same order for ordinary names). A {@link Collator} is not safe to change from several threads but its
 * {@code compare} is, and nothing here changes it after it is made.
 */
public final class VietnameseOrder {

    private static final Collator COLLATOR = makeCollator();

    private VietnameseOrder() {
    }

    /**
     * What the JDK's own Vietnamese collator adds to the root rules: the tones in their order (grave, hook, tilde, acute, dot below), đ after
     * d, and the breve, circumflex and horn (ă â ê ô ơ ư) after z. Written out here because {@code Collator.getInstance("vi")} lives in the
     * module {@code jdk.localedata}, which a jlink image does not contain unless it is asked for: the packaged program would quietly sort like
     * the root locale again, with Đ after Z. {@code VietnameseOrderRulesTest} compares this order with the JDK's wherever that module is present.
     */
    static final String VIETNAMESE_RULES = "&\u0300;\u0309;\u0303;\u0301;\u0323&D<\u0111,\u0110&Z<\u0306<\u0302<\u031B";

    private static Collator makeCollator() {
        Collator collator = vietnameseCollator();
        collator.setStrength(Collator.SECONDARY);
        return collator;
    }

    /** The root rules (in every JDK) plus {@link #VIETNAMESE_RULES}; the JDK's own Vietnamese collator only if the root one is not rule based. */
    static Collator vietnameseCollator() {
        Collator root = Collator.getInstance(Locale.ROOT);
        if (root instanceof RuleBasedCollator rules) {
            try {
                return new RuleBasedCollator(rules.getRules() + VIETNAMESE_RULES);
            } catch (ParseException e) {
                throw new IllegalStateException("The Vietnamese collation rules do not parse", e);
            }
        }
        return Collator.getInstance(Locale.forLanguageTag("vi"));
    }

    /** Orders two texts; an empty or missing text comes first, and two missing texts are equal. */
    public static int compare(String a, String b) {
        if (a == null || b == null) {
            return a == null ? (b == null ? 0 : -1) : 1;
        }
        return COLLATOR.compare(a, b);
    }

    /** The same order as a comparator, for lists that are sorted in Java. */
    public static Comparator<String> comparator() {
        return VietnameseOrder::compare;
    }
}
