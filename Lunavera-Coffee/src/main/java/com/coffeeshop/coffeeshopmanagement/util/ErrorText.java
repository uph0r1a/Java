package com.coffeeshop.coffeeshopmanagement.util;

import java.util.ArrayList;
import java.util.List;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Error boxes must not show database-driver text, class names or file paths (TODO 2A.16): on a shared till that tells a
 * curious person where the data lives. The English "Failed to ..." messages of the data layer (TODO 5.15) are treated the same way:
 * the cashier sees one short sentence in the language in use and the original goes to the log.
 *
 * <p>The filter is an allow-list (TODO 2A.38): only a text the app wrote itself is shown. In the language files the app's own
 * texts are known exactly, so a message is the app's own when it matches one of them, in Vietnamese or in English; a text with
 * Vietnamese letters also counts (messages built in code and not yet in the files). Everything else, including a blank or
 * missing message, becomes {@link #generic()}.
 */
public final class ErrorText {

    /** The sentence shown instead of a technical error text, in the language in use. */
    public static String generic() {
        return I18n.t("error.generic");
    }

    private static final Logger LOGGER = Logger.getLogger(ErrorText.class.getName());

    private static final Pattern TECHNICAL = Pattern.compile(
            "^\\s*(Failed|Unable|Cannot|Could not|Error)\\b|\\[SQLITE_|SQLException|org\\.sqlite|java\\.[a-z]+\\.|[A-Za-z]+Exception|\\bat [a-z]+\\.[a-z]+"
                    + "|[A-Za-z]:\\\\|\\\\Users\\\\|/home/|/Users/|/tmp/|/var/|/mnt/");

    /** Driver, operating-system and SQL texts that must not reach the screen even inside an otherwise Vietnamese message. */
    private static final Pattern DRIVER_TEXT = Pattern.compile(
            "(?i)access denied|access is denied|permission denied|no space left|disk full|database is locked|connection refused"
                    + "|communications link|duplicate entry|data too long|doesn't exist|does not exist|unknown (column|database|table)"
                    + "|being used by another process|syntax error|constraint failed|'[^']*'@'[^']*'|\\bjdbc\\b");

    /** Letters that only Vietnamese text has. */
    private static final Pattern VIETNAMESE = Pattern.compile(
            "[\u00e0\u00e1\u1ea3\u00e3\u1ea1\u0103\u1eb1\u1eaf\u1eb3\u1eb5\u1eb7\u00e2\u1ea7\u1ea5\u1ea9\u1eab\u1ead\u00e8\u00e9\u1ebb\u1ebd\u1eb9\u00ea\u1ec1\u1ebf\u1ec3\u1ec5\u1ec7\u00ec\u00ed\u1ec9\u0129\u1ecb\u00f2\u00f3\u1ecf\u00f5\u1ecd\u00f4\u1ed3\u1ed1\u1ed5\u1ed7\u1ed9\u01a1\u1edd\u1edb\u1edf\u1ee1\u1ee3\u00f9\u00fa\u1ee7\u0169\u1ee5\u01b0\u1eeb\u1ee9\u1eed\u1eef\u1ef1\u1ef3\u00fd\u1ef7\u1ef9\u1ef5\u0111]", Pattern.CASE_INSENSITIVE);

    /** One pattern per text of the language files (all languages), {n} placeholders matching any text; built once. */
    private static volatile List<Pattern> appTexts;

    private ErrorText() {}

    public static boolean looksTechnical(String message) {
        return message != null && (TECHNICAL.matcher(message).find() || DRIVER_TEXT.matcher(message).find());
    }

    private static List<Pattern> appTexts() {
        List<Pattern> local = appTexts;
        if (local == null) {
            local = new ArrayList<>();
            for (String language : I18n.LANGUAGES) {
                ResourceBundle bundle = I18n.bundleFor(language);
                for (String key : I18n.keys(language)) {
                    String template;
                    try {
                        template = bundle.getString(key);
                    } catch (MissingResourceException e) {
                        continue;
                    }
                    if (template.isBlank()) continue;
                    StringBuilder regex = new StringBuilder();
                    int last = 0;
                    java.util.regex.Matcher placeholder = Pattern.compile("\\{\\d+}").matcher(template);
                    while (placeholder.find()) {
                        regex.append(Pattern.quote(template.substring(last, placeholder.start()))).append(".*");
                        last = placeholder.end();
                    }
                    regex.append(Pattern.quote(template.substring(last)));
                    local.add(Pattern.compile(regex.toString(), Pattern.DOTALL));
                }
            }
            appTexts = local;
        }
        return local;
    }

    /** True for a text the app wrote itself and that has nothing technical in it. */
    static boolean isOwnMessage(String message) {
        if (message == null || message.isBlank() || looksTechnical(message)) return false;
        if (VIETNAMESE.matcher(message).find()) return true;
        String trimmed = message.trim();
        for (Pattern known : appTexts()) {
            if (known.matcher(trimmed).matches()) return true;
        }
        return false;
    }

    /**
     * What to show: only the app's own text. Anything else (driver text, an English system message, a blank or missing message)
     * becomes {@link #generic()} and the original goes to the log. A message of the app with a technical tail
     * ({@code "Không thể lưu: Access denied for user ..."} or {@code "hint (raw text)"}) shows its clean front part: the text
     * before " (" or before ": ".
     */
    public static String forScreen(String message) {
        if (isOwnMessage(message)) return message;
        if (message != null && !message.isBlank()) {
            LOGGER.log(Level.WARNING, "Text hidden from the screen: {0}", message);
            for (String separator : new String[] {" (", ": "}) {
                int cut = message.indexOf(separator);
                if (cut > 0) {
                    String front = message.substring(0, cut).trim();
                    if (isOwnMessage(front)) return front;
                }
            }
        }
        return generic();
    }
}
