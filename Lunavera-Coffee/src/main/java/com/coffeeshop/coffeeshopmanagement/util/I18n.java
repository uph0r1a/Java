package com.coffeeshop.coffeeshopmanagement.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The screen text in more than one language (TODO 1.7). The words live in {@code /i18n/messages_vi.properties} (the
 * default, and the fallback for anything missing) and {@code /i18n/messages_en.properties}, both saved as UTF-8.
 *
 * <ul>
 *   <li>Code asks for a text with {@link #t(String)} or {@link #t(String, Object...)} ({@code {0}}, {@code {1}} are
 *       replaced by the arguments as plain text: no number or date formatting, no quote-escaping rules).</li>
 *   <li>FXML files use {@code %key} and are loaded with {@link #bundle()} (see {@code SceneNavigator.newLoader}).</li>
 *   <li>A key that exists in no bundle shows as {@code !key!} instead of stopping the screen; the tests make sure that
 *       never ships (every key used in code or FXML is in both files).</li>
 *   <li>Not translated on purpose: data the shop typed, money ({@code 25.000đ}), dates, and the codes stored in the
 *       database ({@code ADMIN}, {@code PAID}); only their screen labels change.</li>
 * </ul>
 * No JavaFX here, so it can be unit-tested. The remembered choice is {@link LanguageSetting}.
 */
public final class I18n {

    public static final String VI = "vi";
    public static final String EN = "en";
    /** The language of the app until someone picks another, and the one used for any text missing from another language. */
    public static final String DEFAULT = VI;
    /** Every language the app has a bundle for, in the order a switch shows them. */
    public static final List<String> LANGUAGES = List.of(VI, EN);

    private static final String BUNDLE_PATH = "/i18n/messages_";
    private static final Map<String, ResourceBundle> LOADED = new ConcurrentHashMap<>();

    private static volatile String language = DEFAULT;

    private I18n() {
    }

    /** The language code in use now: {@code "vi"} or {@code "en"}. */
    public static String current() {
        return language;
    }

    /** Switches the language in use. Returns false (and changes nothing) for a code the app has no bundle for. */
    public static boolean setLanguage(String code) {
        if (code == null || !LANGUAGES.contains(code)) {
            return false;
        }
        language = code;
        return true;
    }

    /** The text for {@code key} in the current language, or in Vietnamese if that language lacks it, or {@code !key!}. */
    public static String t(String key) {
        return in(language, key);
    }

    /** Same, with {@code {0}}, {@code {1}}... replaced by the arguments ({@code String.valueOf}, so {@code 1000} stays {@code 1000}). */
    public static String t(String key, Object... args) {
        return in(language, key, args);
    }

    /** The text in a given language rather than the current one (for tests and for showing a language's own name). */
    public static String in(String code, String key, Object... args) {
        String text = lookup(code, key);
        if (text == null && !DEFAULT.equals(code)) {
            text = lookup(DEFAULT, key);
        }
        if (text == null) {
            return "!" + key + "!";
        }
        return args == null || args.length == 0 ? text : fill(text, args);
    }

    /**
     * Replaces {@code {0}}, {@code {1}}... in one pass over the text, so an argument that itself contains something like
     * {@code {1}} (a product the shop named that way) is copied as typed and never filled in a second time. A placeholder
     * with no matching argument is left as it is.
     */
    private static String fill(String text, Object[] args) {
        StringBuilder out = new StringBuilder(text.length() + 16);
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '{') {
                int close = text.indexOf('}', i + 1);
                if (close > i + 1) {
                    String index = text.substring(i + 1, close);
                    if (index.chars().allMatch(Character::isDigit)) {
                        int n = Integer.parseInt(index);
                        if (n < args.length) {
                            out.append(args[n]);
                            i = close + 1;
                            continue;
                        }
                    }
                }
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    /** True when the language's own bundle has the key (the fallback to Vietnamese does not count). */
    public static boolean has(String code, String key) {
        return lookup(code, key) != null;
    }

    /** All keys of a language's bundle. */
    public static java.util.Set<String> keys(String code) {
        return new java.util.TreeSet<>(Collections.list(load(code).getKeys()));
    }

    /**
     * What {@code FXMLLoader} resolves {@code %key} against: the current language, with Vietnamese filling any gap and
     * {@code !key!} for a key nobody has, so a typo cannot stop a screen from opening.
     */
    public static ResourceBundle bundle() {
        return new Merged(language);
    }

    /** Same, for a fixed language whatever language is in use (the receipt is always English). */
    public static ResourceBundle bundleFor(String code) {
        return new Merged(LANGUAGES.contains(code) ? code : DEFAULT);
    }

    private static String lookup(String code, String key) {
        if (!LANGUAGES.contains(code)) {
            return null; // no file for it: the caller falls back to Vietnamese
        }
        ResourceBundle bundle = load(code);
        return bundle.containsKey(key) ? bundle.getString(key) : null;
    }

    private static ResourceBundle load(String code) {
        return LOADED.computeIfAbsent(code, c -> {
            String path = BUNDLE_PATH + c + ".properties";
            try (InputStream in = I18n.class.getResourceAsStream(path)) {
                if (in == null) {
                    throw new IllegalStateException("Missing language file " + path);
                }
                try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    return new PropertyResourceBundle(reader);
                }
            } catch (IOException e) {
                throw new IllegalStateException("Cannot read language file " + path, e);
            }
        });
    }

    private static final class Merged extends ResourceBundle {
        private final String code;

        Merged(String code) {
            this.code = code;
        }

        @Override
        protected Object handleGetObject(String key) {
            return in(code, key);
        }

        @Override
        public Enumeration<String> getKeys() {
            return Collections.enumeration(keys(DEFAULT));
        }

        @Override
        public boolean containsKey(String key) {
            return true; // unknown keys come back as !key!, never as a MissingResourceException
        }
    }
}
