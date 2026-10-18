package com.coffeeshop.coffeeshopmanagement.util;

import java.util.ArrayList;
import java.util.List;

/**
 * The page arithmetic of {@link PageBar}, with no JavaFX in it so it can be unit-tested (TODO 6.35): how many pages there
 * are, which page number is valid, and which page buttons are shown ("1 ... 4 5 6 ... 20").
 */
public final class PageMath {

    /** {@link #visiblePages} puts this where the bar shows "…". */
    public static final int GAP = 0;

    private PageMath() {
    }

    /** Pages needed for {@code totalItems} rows at {@code pageSize} per page; never less than 1, even with no rows. */
    public static int totalPages(int totalItems, int pageSize) {
        if (pageSize <= 0) pageSize = 1;
        return Math.max(1, (int) Math.ceil(Math.max(0, totalItems) / (double) pageSize));
    }

    /** A page number forced into 1..{@code totalPages}. */
    public static int clamp(int page, int totalPages) {
        return Math.max(1, Math.min(page, Math.max(1, totalPages)));
    }

    /** Whether the button for {@code page} is drawn: the first and last page, the current one and its neighbours, and four at either end. */
    public static boolean isShown(int page, int currentPage, int pages) {
        return page == 1 || page == pages || Math.abs(page - currentPage) <= 1
                || (currentPage <= 3 && page <= 4) || (currentPage >= pages - 2 && page >= pages - 3);
    }

    /** The page numbers drawn in order, with {@link #GAP} wherever pages are skipped. */
    public static List<Integer> visiblePages(int currentPage, int pages) {
        List<Integer> result = new ArrayList<>();
        int last = 0;
        for (int page = 1; page <= pages; page++) {
            if (!isShown(page, currentPage, pages)) continue;
            if (last != 0 && page - last > 1) result.add(GAP);
            result.add(page);
            last = page;
        }
        return result;
    }
}
