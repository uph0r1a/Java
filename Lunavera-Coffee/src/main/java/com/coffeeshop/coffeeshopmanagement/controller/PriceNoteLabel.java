package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.config.PriceNote;
import javafx.scene.control.Label;

/**
 * The line under the total on the sales screen (TODO 2.103): the owner's own text from Settings, and no space at all when there is none.
 * Used from {@code quanlydonhang.fxml}; it reads the setting when the screen is built.
 */
public final class PriceNoteLabel extends Label {

    public PriceNoteLabel() {
        String note = PriceNote.cached();
        setText(note);
        setVisible(!note.isEmpty());
        setManaged(!note.isEmpty());
    }
}
