package com.coffeeshop.coffeeshopmanagement.util;

/**
 * The language choice that survives a restart (TODO 1.7). It is kept per computer in {@code ui-settings.properties} in the
 * data folder, next to the window sizes, not in the shared database: each till can show the language its cashier reads
 * without changing the others. A missing or unknown value means Vietnamese.
 */
public final class LanguageSetting {

    static final String KEY = "language";

    private LanguageSetting() {
    }

    /** Applies the remembered language. Called once at start-up, before the first screen is loaded. */
    public static void restore() {
        try {
            I18n.setLanguage(UiSettings.get(KEY, I18n.DEFAULT));
        } catch (RuntimeException e) {
            // The data folder itself may be what is wrong (start-up problem screen): stay with Vietnamese rather than fail here.
            java.util.logging.Logger.getLogger(LanguageSetting.class.getName())
                    .log(java.util.logging.Level.WARNING, "Could not read the remembered language; using the default", e);
        }
    }

    /** Switches the language now and remembers it. Returns false for a language the app does not have. */
    public static boolean choose(String code) {
        if (!I18n.setLanguage(code)) {
            return false;
        }
        UiSettings.put(KEY, I18n.current());
        return true;
    }
}
