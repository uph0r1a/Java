package com.coffeeshop.coffeeshopmanagement.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * The small rules behind the report window that need no screen (TODO 5.34): how much of the product list is shown, when a window
 * that was left open reloads itself, and the "updated at" text.
 */
public final class ReportRefresh {

    /** A window that gets the focus back reloads when its figures are at least this old; a quick alt-tab does not. */
    public static final long MIN_AGE_MILLIS = 30_000;

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");

    private ReportRefresh() {
    }

    /** The first {@code limit} entries (the best sellers on screen); the whole list when it is shorter. The list is not changed. */
    public static <T> List<T> top(List<T> all, int limit) {
        return all.size() <= limit ? all : List.copyOf(all.subList(0, Math.max(0, limit)));
    }

    /**
     * Whether a report window that just got the focus should reload.
     *
     * @param loading        a load is already running
     * @param loadedAtMillis {@link MonotonicClock#millis()} when the figures were loaded, or a negative number when nothing was loaded
     * @param nowMillis      {@link MonotonicClock#millis()} now
     */
    public static boolean shouldRefreshOnFocus(boolean loading, long loadedAtMillis, long nowMillis) {
        if (loading || loadedAtMillis < 0) return false;
        return nowMillis - loadedAtMillis >= MIN_AGE_MILLIS;
    }

    /** "Updated at 14:05" next to the figures, so a window left open all day shows how old they are. */
    public static String loadedText(LocalDateTime loadedAt) {
        return I18n.t("report.loadedat", loadedAt.format(CLOCK));
    }
}
