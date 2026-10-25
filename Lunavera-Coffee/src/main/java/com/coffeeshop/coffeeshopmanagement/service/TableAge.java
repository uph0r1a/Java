package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.util.I18n;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * How long a table has been occupied (TODO 5.11): the text for the table map and the point after which the table is
 * highlighted, because an order that has been open that long is more often a forgotten one (bug 2.11) than a customer
 * still sitting there.
 */
public final class TableAge {

    /** An order open for this long or longer is highlighted on the table map. */
    public static final Duration LONG_AFTER = Duration.ofHours(2);

    private TableAge() {}

    /** The time between {@code openedAt} and {@code now}; null when the start is unknown; never negative (a till clock that ran ahead). */
    public static Duration since(LocalDateTime openedAt, LocalDateTime now) {
        if (openedAt == null || now == null) return null;
        Duration age = Duration.between(openedAt, now);
        return age.isNegative() ? Duration.ZERO : age;
    }

    public static boolean isLong(Duration age) {
        return age != null && age.compareTo(LONG_AFTER) >= 0;
    }

    /** "just opened", "35 min", "2 h", "1 h 20 min" (in the language in use); empty for an unknown age. */
    public static String text(Duration age) {
        if (age == null) return "";
        long minutes = age.toMinutes();
        if (minutes < 1) return I18n.t("tables.age.now");
        if (minutes < 60) return I18n.t("tables.age.minutes", minutes);
        long hours = minutes / 60;
        long rest = minutes % 60;
        return rest == 0 ? I18n.t("tables.age.hours", hours) : I18n.t("tables.age.hoursminutes", hours, rest);
    }
}
