package com.coffeeshop.coffeeshopmanagement.util;

import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;

import java.time.DayOfWeek;
import java.time.LocalDate;

/** "Hôm nay / Tuần này / Tháng này" shortcuts for a pair of date pickers (TODO 5.2). Weeks start on Monday. */
public final class DatePresets {

    public enum Preset {
        TODAY("date.today"), THIS_WEEK("date.thisweek"), THIS_MONTH("date.thismonth");

        private final String labelKey;

        Preset(String labelKey) {
            this.labelKey = labelKey;
        }

        /** The button text, in the language in use. */
        public String label() {
            return I18n.t(labelKey);
        }

        /** {from, to} for this preset, both inclusive; "to" is always the given day. */
        public LocalDate[] range(LocalDate today) {
            return switch (this) {
                case TODAY -> new LocalDate[]{today, today};
                case THIS_WEEK -> new LocalDate[]{today.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)), today};
                case THIS_MONTH -> new LocalDate[]{today.withDayOfMonth(1), today};
            };
        }
    }

    private DatePresets() {}

    /** One button per preset; each sets both pickers and then runs {@code afterChange}. */
    public static Button[] buttons(DatePicker from, DatePicker to, Runnable afterChange) {
        Preset[] presets = Preset.values();
        Button[] buttons = new Button[presets.length];
        for (int i = 0; i < presets.length; i++) {
            Preset preset = presets[i];
            Button b = new Button(preset.label());
            b.setOnAction(e -> {
                LocalDate[] range = preset.range(AppClock.today());
                from.setValue(range[0]);
                to.setValue(range[1]);
                afterChange.run();
            });
            buttons[i] = b;
        }
        return buttons;
    }
}
