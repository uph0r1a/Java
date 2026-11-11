package com.coffeeshop.coffeeshopmanagement.util;

import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

/** Puts one text on the system clipboard (TODO 5.46). Call it on the screen thread; the text to copy comes from {@link CopyText}. */
public final class ClipboardText {

    private ClipboardText() {
    }

    /** Copies {@code text}; a blank text copies nothing and returns false. */
    public static boolean copy(String text) {
        if (text == null || text.isBlank()) return false;
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        return Clipboard.getSystemClipboard().setContent(content);
    }
}
