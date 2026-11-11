package com.coffeeshop.coffeeshopmanagement.util;

import org.junit.After;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

/** TODO 5.40: the text of the offer, and the order of things in the start-up code (source test: no JavaFX is started). */
public class StartupLocalFileTest {

    private static final Path APP = Path.of("src", "main", "java", "com", "coffeeshop", "coffeeshopmanagement", "LunaveraApplication.java");

    @After
    public void backToVietnamese() {
        I18n.setLanguage(I18n.VI);
    }

    private static String read(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    @Test
    public void theOfferNamesTheButtonTheFileAndWhatToDoForAServer() {
        I18n.setLanguage(I18n.EN);
        String text = StartupProblem.localFileOffer("D:\\shop\\data\\database.properties", "D:\\shop\\data\\lunavera.db", false);
        assertTrue(text, text.contains("Use the local file on this computer"));
        assertTrue(text, text.contains("D:\\shop\\data\\database.properties"));
        assertTrue("says a new shop is made", text.contains("new, empty shop"));
        assertTrue("tells a server shop to close and fix the server", text.contains("press Close"));
        assertFalse("no placeholder is left", text.contains("{"));
    }

    @Test
    public void anExistingLocalFileIsSaidToBeOpenedWithItsData() {
        I18n.setLanguage(I18n.EN);
        String text = StartupProblem.localFileOffer("s", "D:\\shop\\data\\lunavera.db", true);
        assertTrue(text, text.contains("D:\\shop\\data\\lunavera.db"));
        assertTrue(text, text.contains("opened with all its data"));
        assertFalse(text.contains("new, empty shop"));
    }

    @Test
    public void theVietnameseOfferIsComplete() {
        I18n.setLanguage(I18n.VI);
        String text = StartupProblem.localFileOffer("s.properties", "d.db", false);
        assertTrue(text, text.contains("Dùng tệp dữ liệu trên máy này"));
        assertTrue(text, text.contains("s.properties"));
        assertFalse(text.contains("{"));
        assertFalse("every key exists", text.contains("!startup."));
    }

    @Test
    public void startAsksBeforeItShowsThePlainErrorAndAClosedBoxEndsTheProgram() throws IOException {
        String app = read(APP);
        int start = app.indexOf("public void start(");
        int offer = app.indexOf("LocalFileChoice.applies()", start);
        int plain = app.indexOf("AlertUtil.errorWithDetails(", start);
        assertTrue("the offer is checked in start()", offer > 0);
        assertTrue("before the plain error box", offer < plain);
        assertTrue("closing the offer ends the program, with no second box", app.contains("!offerLocalFile()) {\n            Platform.exit();"));
    }

    @Test
    public void choosingTheFileWritesItForgetsTheOldSettingsAndOpensTheDatabaseAgain() throws IOException {
        String app = read(APP);
        int method = app.indexOf("private boolean offerLocalFile()");
        String body = app.substring(method, app.indexOf("/** Asks on the FX thread", method));
        int write = body.indexOf("LocalFileChoice.write()");
        int reset = body.indexOf("DatabaseConfig.reset()");
        int open = body.indexOf("openDatabase()");
        int clear = body.indexOf("startupError = null");
        assertTrue(write > 0 && write < reset && reset < open && open < clear);
        assertEquals("the database is opened in one place only", 1, count(app, "DatabaseConfig.initialize()"));
        assertTrue("init() uses the same steps", app.substring(app.indexOf("public void init()"), method).contains("openDatabase();"));
    }

    @Test
    public void closeIsTheDefaultButtonSoAKeyPressNeverMakesANewShop() throws IOException {
        String alert = read(Path.of("src", "main", "java", "com", "coffeeshop", "coffeeshopmanagement", "util", "AlertUtil.java"));
        int method = alert.indexOf("public static boolean errorWithChoice(");
        String body = alert.substring(method, alert.indexOf("public static void warning(", method));
        assertTrue(body.contains("setDefaultButton(alert, close)"));
        assertTrue(body.contains("setCancelButton(alert, close)"));
    }

    @Test
    public void theWriterNeverReplacesAFile() throws IOException {
        String source = read(Path.of("src", "main", "java", "com", "coffeeshop", "coffeeshopmanagement", "config", "LocalFileChoice.java"));
        assertTrue(source.contains("StandardOpenOption.CREATE_NEW"));
    }

    private static int count(String text, String part) {
        int n = 0;
        for (int i = text.indexOf(part); i >= 0; i = text.indexOf(part, i + part.length())) n++;
        return n;
    }
}
