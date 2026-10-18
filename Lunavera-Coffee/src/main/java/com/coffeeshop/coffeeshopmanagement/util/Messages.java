package com.coffeeshop.coffeeshopmanagement.util;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.logging.Logger;

/**
 * The text the person reads, kept in one place instead of inside the screens (TODO 6.10): {@code messages_vi.properties}
 * next to the code. A wording fix is then one line in one file, and another language is one more file.
 *
 * <p>A missing key never breaks a screen: {@link #get} returns the key itself (visible, and obviously wrong) and logs it once.
 * {@code MessagesTest} reads the sources and fails the build if a {@code Messages.get("...")} call names a key that the file
 * does not have, so a typo is found when the tests run, not when somebody opens the dialog.
 *
 * <p>Moving text here is done one screen at a time. Screens not yet moved still hold their own text; both work together.
 */
public final class Messages {

    private static final Logger LOGGER = Logger.getLogger(Messages.class.getName());
    private static final String BASE_NAME = "messages";
    private static final Locale LOCALE = Locale.forLanguageTag("vi");

    private static volatile ResourceBundle bundle;

    private Messages() {
    }

    private static ResourceBundle bundle() {
        ResourceBundle b = bundle;
        if (b == null) {
            // Properties bundles are read as UTF-8 (Java 9 and later), so Vietnamese letters are written as they are.
            b = ResourceBundle.getBundle(BASE_NAME, LOCALE);
            bundle = b;
        }
        return b;
    }

    /** The text for {@code key}, or the key itself if there is none. */
    public static String get(String key) {
        try {
            return bundle().getString(key);
        } catch (MissingResourceException e) {
            LOGGER.warning("Missing text key: " + key);
            return key;
        }
    }

    /** The text for {@code key} with {@code {0}}, {@code {1}}... filled in. A quote in the text is written twice, as in {@link MessageFormat}. */
    public static String format(String key, Object... arguments) {
        return MessageFormat.format(get(key), arguments);
    }
}
