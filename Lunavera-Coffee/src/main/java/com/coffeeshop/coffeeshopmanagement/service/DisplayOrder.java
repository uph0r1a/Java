package com.coffeeshop.coffeeshopmanagement.service;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/**
 * The order of the categories, and of the products of a category, on the sales screen (TODO 5.31). The shop moves one item up or
 * down with an arrow; this works out the new order of <em>all</em> items so that only the two that swap change place, and the
 * rest of the list (the products of other categories, which share the same numbering) stays exactly where it was.
 */
public final class DisplayOrder {

    private DisplayOrder() {
    }

    /**
     * The ids of {@code items} (given in their current order) after the item with {@code movedId} has traded places with its
     * neighbour inside the same group, one step up ({@code delta < 0}) or down ({@code delta > 0}). The neighbour is the next item,
     * in that direction, with the same {@code group} key; items of other groups are skipped over but not moved. When there is no
     * such neighbour (already first or last of its group) or the id is not in the list, the order is returned unchanged.
     */
    public static <T> List<Integer> moved(List<T> items, ToIntFunction<T> id, Function<T, Object> group, int movedId, int delta) {
        List<Integer> ids = new ArrayList<>();
        for (T item : items) {
            ids.add(id.applyAsInt(item));
        }
        int from = ids.indexOf(movedId);
        if (from < 0 || delta == 0) {
            return ids;
        }
        Object wanted = group.apply(items.get(from));
        int step = delta < 0 ? -1 : 1;
        for (int i = from + step; i >= 0 && i < items.size(); i += step) {
            if (java.util.Objects.equals(group.apply(items.get(i)), wanted)) {
                int other = ids.get(i);
                ids.set(i, movedId);
                ids.set(from, other);
                return ids;
            }
        }
        return ids;
    }
}
