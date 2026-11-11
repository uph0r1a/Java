package com.coffeeshop.coffeeshopmanagement.util;

import com.coffeeshop.coffeeshopmanagement.service.LargeTextSheet;

/**
 * The text size the cashier reads at (TODO 5.44): "Vừa" (as designed) or "Lớn" (every size two pixels larger, see
 * {@link LargeTextSheet}). It is per computer, kept in {@code ui-settings.properties} next to the language, so a till at arm's length
 * can be larger than the office PC. Choosing it takes effect at once in every open window.
 */
public final class TextSize {

    static final String KEY = "textsize";
    static final String NORMAL = "normal";
    static final String LARGE = "large";

    private TextSize() {
    }

    /** True when this computer shows the large text. A file that cannot be read means the normal size. */
    public static boolean isLarge() {
        try {
            return LARGE.equals(UiSettings.get(KEY, NORMAL));
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * Remembers the choice; true when it changed. Applying it to the windows that are open is the screen's part
     * ({@code AppStyle.chooseTextSize} does both): this class names no JavaFX class, so it is tested without JavaFX.
     */
    public static boolean choose(boolean large) {
        if (large == isLarge()) return false;
        UiSettings.put(KEY, large ? LARGE : NORMAL);
        return true;
    }

    /**
     * {@code "-fx-font-size: 11px;"} for a label that is styled in code: the same size normally, the larger one while the large text
     * is on. A style written in code beats a style sheet, so the sheet cannot make these larger; they use this instead (and change
     * the next time their screen is opened).
     */
    public static String size(int px) {
        return "-fx-font-size: " + (isLarge() ? LargeTextSheet.scale(px) : px) + "px;";
    }
}
