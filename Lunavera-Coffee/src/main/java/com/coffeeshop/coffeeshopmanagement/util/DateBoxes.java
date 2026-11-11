package com.coffeeshop.coffeeshopmanagement.util;

import javafx.scene.control.DatePicker;

import java.time.LocalDate;

/** Makes the date boxes of the program (TODO 3.78): every {@code DatePicker} is created here so all of them share {@link DateTextConverter}. */
public final class DateBoxes {

    private DateBoxes() {
    }

    public static DatePicker picker() {
        return picker(null);
    }

    public static DatePicker picker(LocalDate value) {
        DatePicker box = new DatePicker();
        box.setConverter(new DateTextConverter());
        box.setPromptText(DateTextConverter.PATTERN);
        box.setValue(value);
        return box;
    }
}
