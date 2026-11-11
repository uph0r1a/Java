package com.coffeeshop.coffeeshopmanagement.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * The staff guides in {@code src/main/resources/help/} (TODO 6.71): one page per audience ({@value #OWNER}, {@value #CASHIER}) in each
 * language, for example {@code owner_vi.md}. The window that shows them is {@code HelpDialog}.
 */
public final class HelpPages {

    public static final String OWNER = "owner";
    public static final String CASHIER = "cashier";

    private HelpPages() {
    }

    /** {@code /help/owner_en.md}; a language the guides do not have falls back to the default one. */
    public static String resource(String audience, String language) {
        String lang = language != null && I18n.LANGUAGES.contains(language) ? language : I18n.DEFAULT;
        return "/help/" + audience + "_" + lang + ".md";
    }

    /** The text of a guide in the language, or in the default language when that page is missing; {@code null} when neither is there. */
    public static String load(String audience, String language) {
        String text = read(resource(audience, language));
        return text != null ? text : read(resource(audience, I18n.DEFAULT));
    }

    private static String read(String resource) {
        try (InputStream in = HelpPages.class.getResourceAsStream(resource)) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }
}
