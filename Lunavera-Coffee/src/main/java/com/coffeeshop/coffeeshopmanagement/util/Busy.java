package com.coffeeshop.coffeeshopmanagement.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Keeps buttons (or a whole table) switched off while a background job runs, so a second quick click cannot start the same
 * action twice (TODO 2.75). Toolkit-free: {@link Async} adapts JavaFX nodes to {@link Switch}.
 */
public final class Busy {

    /** Something that can be disabled and asked whether it is. */
    public interface Switch {
        boolean isDisabled();
        void setDisabled(boolean disabled);
    }

    private Busy() {
    }

    /**
     * Disables every switch that is enabled now and returns what puts them back. The release restores only what this call
     * changed (something already disabled stays disabled) and does nothing the second time it is called.
     */
    public static Runnable hold(List<? extends Switch> switches) {
        List<Switch> changed = new ArrayList<>();
        for (Switch s : switches) {
            if (s != null && !s.isDisabled()) {
                s.setDisabled(true);
                changed.add(s);
            }
        }
        boolean[] released = {false};
        return () -> {
            if (released[0]) return;
            released[0] = true;
            for (Switch s : changed) s.setDisabled(false);
        };
    }
}
