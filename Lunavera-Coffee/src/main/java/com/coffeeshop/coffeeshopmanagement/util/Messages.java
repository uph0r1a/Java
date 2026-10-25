package com.coffeeshop.coffeeshopmanagement.util;

import java.util.logging.Logger;

/**
 * The text the person reads, kept in one place instead of inside the screens (TODO 6.10). It is a thin layer over {@link I18n}:
 * the text lives in {@code /i18n/messages_vi.properties} and {@code messages_en.properties}, so a wording fix is one line in each
 * file and the screens follow the language in use (TODO 1.7). Screens may call either {@code Messages.get(key)} or
 * {@code I18n.t(key)}; they read the same files.
 *
 * <p>A missing key never breaks a screen: {@link #get} returns the key itself (visible, and obviously wrong) and logs it.
 * {@code MessagesTest} reads the sources and fails the build if a Messages.get call names a key that the
 * language files do not have, and {@code I18nTest} does the same for I18n.t calls and {@code %key} in FXML.
 */
public final class Messages {

    private static final Logger LOGGER = Logger.getLogger(Messages.class.getName());

    /** Keys already reported; capped so a program that builds keys at random cannot grow it without end (TODO 6.57). */
    private static final java.util.Set<String> REPORTED = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final int MAX_REPORTED = 500;

    private Messages() {
    }

    /** One log line per missing key, not one per call: a label read on every refresh would fill the log file in a day. */
    private static void logMissingOnce(String key) {
        if (REPORTED.size() < MAX_REPORTED && REPORTED.add(key)) {
            LOGGER.warning("Missing text key: " + key);
        }
    }

    /** The text for {@code key} in the language in use, or the key itself if no language file has it. */
    public static String get(String key) {
        if (!I18n.has(I18n.DEFAULT, key)) {
            logMissingOnce(key);
            return key;
        }
        return I18n.t(key);
    }

    /** The text for {@code key} with {@code {0}}, {@code {1}}... filled in (plain text, no quote doubling needed). */
    public static String format(String key, Object... arguments) {
        if (!I18n.has(I18n.DEFAULT, key)) {
            logMissingOnce(key);
            return key;
        }
        return I18n.t(key, arguments);
    }
}
