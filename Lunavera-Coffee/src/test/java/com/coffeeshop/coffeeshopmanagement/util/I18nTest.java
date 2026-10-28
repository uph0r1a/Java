package com.coffeeshop.coffeeshopmanagement.util;

import org.junit.After;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.*;

/**
 * TODO 1.7: the safety nets of the language switch. No JavaFX and no display, so they run everywhere: both language files
 * have the same keys, no text is empty, placeholders agree, and every key a screen or the code asks for exists.
 */
public class I18nTest {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\d+}");
    private static final Pattern FXML_KEY = Pattern.compile("\"%([\\w.\\-]+)\"");
    private static final Pattern JAVA_KEY = Pattern.compile("I18n\\.t\\(\\s*\"([\\w.\\-]+)\"");

    /** Text that is the same in both languages on purpose (a language's own name, a currency amount). */
    private static final Set<String> SAME_ON_PURPOSE = Set.of("lang.en", "lang.vi", "money.zero", "pos.money.zero",
            "payment.momo", "payment.zalopay", "payment.vnpay", // brand names, written the same in every language (TODO 4.34)
            "staff.detail.email", "staff.label.email"); // the word "Email" (and its label) is the same in both languages (TODO 4.12)

    private final String languageAtStart = I18n.current();

    @After
    public void putTheLanguageBack() {
        I18n.setLanguage(languageAtStart);
    }

    @Test
    public void bothLanguageFilesHaveExactlyTheSameKeys() {
        Set<String> vi = I18n.keys(I18n.VI);
        Set<String> en = I18n.keys(I18n.EN);
        Set<String> onlyVi = new TreeSet<>(vi);
        onlyVi.removeAll(en);
        Set<String> onlyEn = new TreeSet<>(en);
        onlyEn.removeAll(vi);
        assertTrue("keys missing from messages_en.properties: " + onlyVi, onlyVi.isEmpty());
        assertTrue("keys missing from messages_vi.properties: " + onlyEn, onlyEn.isEmpty());
        assertFalse(vi.isEmpty());
    }

    @Test
    public void noTextIsEmpty() {
        for (String code : I18n.LANGUAGES) {
            for (String key : I18n.keys(code)) {
                assertFalse(code + ": " + key + " is empty", I18n.in(code, key).isBlank());
            }
        }
    }

    @Test
    public void everyKeyHasTheSamePlaceholdersInEveryLanguage() {
        for (String key : I18n.keys(I18n.VI)) {
            assertEquals("placeholders of " + key + " differ between languages",
                    placeholders(I18n.in(I18n.VI, key)), placeholders(I18n.in(I18n.EN, key)));
        }
    }

    @Test
    public void aTextIsNotJustLeftInTheOtherLanguage() {
        // Keys with the same text in both files are fine for names and brands, but each must be on purpose: this list is it.
        Set<String> same = new TreeSet<>();
        for (String key : I18n.keys(I18n.VI)) {
            if (I18n.in(I18n.VI, key).equals(I18n.in(I18n.EN, key))) same.add(key);
        }
        assertEquals("keys whose English text is identical to the Vietnamese one", SAME_ON_PURPOSE, same);
    }

    @Test
    public void everyKeyUsedInAnFxmlFileExists() throws IOException {
        List<String> missing = new ArrayList<>();
        for (Path fxml : files(Path.of("src", "main", "resources", "fxml"), ".fxml")) {
            for (String key : matches(FXML_KEY, read(fxml))) {
                if (!I18n.has(I18n.VI, key)) missing.add(fxml.getFileName() + ": %" + key);
            }
        }
        assertTrue("keys used in FXML but not in messages_vi.properties: " + missing, missing.isEmpty());
    }

    @Test
    public void everyKeyUsedInJavaCodeExists() throws IOException {
        List<String> missing = new ArrayList<>();
        for (Path java : files(Path.of("src", "main", "java"), ".java")) {
            for (String key : matches(JAVA_KEY, read(java))) {
                if (!I18n.has(I18n.VI, key)) missing.add(java.getFileName() + ": " + key);
            }
        }
        assertTrue("keys used in Java but not in messages_vi.properties: " + missing, missing.isEmpty());
    }

    @Test
    public void theTextFollowsTheLanguageInUse() {
        assertTrue(I18n.setLanguage(I18n.VI));
        assertEquals("ĐĂNG NHẬP", I18n.t("login.title"));
        assertTrue(I18n.setLanguage(I18n.EN));
        assertEquals("SIGN IN", I18n.t("login.title"));
        assertEquals(I18n.EN, I18n.current());
    }

    @Test
    public void anUnknownLanguageIsRefusedAndChangesNothing() {
        I18n.setLanguage(I18n.EN);
        assertFalse(I18n.setLanguage("fr"));
        assertFalse(I18n.setLanguage(null));
        assertFalse(I18n.setLanguage(""));
        assertFalse(I18n.setLanguage("EN"));
        assertEquals(I18n.EN, I18n.current());
    }

    @Test
    public void aMissingKeyShowsAsItsNameInsteadOfStoppingAScreen() {
        assertEquals("!no.such.key!", I18n.t("no.such.key"));
        assertEquals("!no.such.key!", I18n.in(I18n.EN, "no.such.key"));
        ResourceBundle bundle = I18n.bundle();
        assertEquals("!no.such.key!", bundle.getString("no.such.key"));
    }

    @Test
    public void placeholdersAreFilledAsPlainText() {
        I18n.setLanguage(I18n.VI);
        assertEquals("Đã nhập sai quá nhiều lần. Vui lòng thử lại sau 12345 giây.", I18n.t("login.throttled.message", 12345));
        I18n.setLanguage(I18n.EN);
        assertEquals("Too many wrong attempts. Please try again in 7 seconds.", I18n.t("login.throttled.message", 7));
    }

    @Test
    public void anArgumentThatLooksLikeAPlaceholderIsCopiedAsTyped() {
        I18n.setLanguage(I18n.EN);
        // "{1}" typed by the shop as a name must not be filled in a second time
        assertEquals("Table {1}", I18n.t("orders.table", "{1}"));
        assertEquals("Table 4", I18n.t("orders.table", 4));
        assertEquals("Welcome, {0}!".replace("{0}", "x"), I18n.t("dashboard.greeting", "x"));
        // a placeholder without an argument stays visible instead of failing
        assertEquals("Too many wrong attempts. Please try again in {0} seconds.", I18n.in(I18n.EN, "login.throttled.message", new Object[0]));
    }

    /**
     * {@code AlertUtil.error} passes its message through {@link ErrorText#forScreen}, which replaces anything that looks like
     * a raw technical error ("Could not ...", "Cannot ...", a file path, "...Exception") with the generic sentence. A translated
     * message that trips it would never be seen. These keys are only ever used as dialog <em>titles</em> (never filtered), so they
     * may look technical; any other key that does is a bug: rephrase it ("X could not be loaded").
     */
    private static final Set<String> ONLY_TITLES = Set.of("common.error.title", "dashboard.load.title",
            "pos.qr.accept.fail.title", "receipt.print.fail.title", "report.load.error", "common.delete.fail",
            "accounts.lock.fail.title");

    @Test
    public void noMessageLooksLikeATechnicalErrorToErrorText() {
        List<String> hidden = new ArrayList<>();
        for (String code : I18n.LANGUAGES) {
            for (String key : I18n.keys(code)) {
                if (!ONLY_TITLES.contains(key) && ErrorText.looksTechnical(I18n.in(code, key))) hidden.add(code + ":" + key);
            }
        }
        assertTrue("these texts would be replaced by the generic error text when shown in an error box: " + hidden, hidden.isEmpty());
    }

    @Test
    public void theBundleForFxmlFollowsTheCurrentLanguage() {
        I18n.setLanguage(I18n.EN);
        assertEquals("Password", I18n.bundle().getString("login.password.label"));
        I18n.setLanguage(I18n.VI);
        assertEquals("Mật khẩu", I18n.bundle().getString("login.password.label"));
    }

    @Test
    public void vietnameseFillsAGapInAnotherLanguage() {
        // The English file is complete (see the first test), so a gap is simulated by asking a language the app lacks.
        assertEquals(I18n.in(I18n.VI, "login.title"), I18n.in("xx", "login.title"));
    }

    @Test
    public void theLanguageFilesAreWrittenAsUtf8WithoutMojibake() throws IOException {
        for (String code : I18n.LANGUAGES) {
            String text = read(Path.of("src", "main", "resources", "i18n", "messages_" + code + ".properties"));
            assertFalse(code + " file has a replacement character", text.contains("\uFFFD"));
            // UTF-8 read as Latin-1 leaves a lead character ("Ã", "Ä", "Æ", "á") followed by a Latin-1 symbol or control character.
            // A plain "Ã" is a real Vietnamese capital letter ("ĐÃ HỦY") and must not trip this.
            assertFalse(code + " file looks double-encoded",
                    java.util.regex.Pattern.compile("[ÃÄÆ][\\u0080-\\u00BF]|á[\\u00BA\\u00BB]").matcher(text).find());
        }
        assertTrue(read(Path.of("src", "main", "resources", "i18n", "messages_vi.properties")).contains("Tiếng Việt"));
    }

    private static List<String> placeholders(String text) {
        List<String> found = new ArrayList<>();
        Matcher m = PLACEHOLDER.matcher(text);
        while (m.find()) found.add(m.group());
        java.util.Collections.sort(found);
        return found;
    }

    private static Set<String> matches(Pattern pattern, String text) {
        Set<String> found = new TreeSet<>();
        Matcher m = pattern.matcher(text);
        while (m.find()) found.add(m.group(1));
        return found;
    }

    private static List<Path> files(Path dir, String extension) throws IOException {
        try (Stream<Path> walk = Files.walk(dir)) {
            return walk.filter(p -> p.toString().endsWith(extension)).sorted().collect(Collectors.toList());
        }
    }

    private static String read(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8);
    }
}
