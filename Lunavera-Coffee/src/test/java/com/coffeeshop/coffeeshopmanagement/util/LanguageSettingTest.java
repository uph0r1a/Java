package com.coffeeshop.coffeeshopmanagement.util;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.Assert.*;

/**
 * TODO 6.84: {@link LanguageSetting}, the language that survives a restart (TODO 1.7). Each test has its own data folder, so nothing of
 * the real {@code ui-settings.properties} is read or written, and the language in use is put back afterwards.
 */
public class LanguageSettingTest {

    private String realDataDir;
    private String languageAtStart;
    private Path sandbox;

    @Before
    public void ownDataFolder() throws IOException {
        realDataDir = System.getProperty(DataPaths.DATA_DIR_PROPERTY);
        languageAtStart = I18n.current();
        sandbox = Files.createTempDirectory("language-setting-");
        System.setProperty(DataPaths.DATA_DIR_PROPERTY, sandbox.resolve("data").toString());
        I18n.setLanguage(I18n.DEFAULT);
    }

    @After
    public void putEverythingBack() {
        if (realDataDir == null) System.clearProperty(DataPaths.DATA_DIR_PROPERTY);
        else System.setProperty(DataPaths.DATA_DIR_PROPERTY, realDataDir);
        I18n.setLanguage(languageAtStart);
    }

    private Path file() {
        return sandbox.resolve("data").resolve(UiSettings.FILE_NAME);
    }

    private String stored() throws IOException {
        Properties p = new Properties();
        try (var in = Files.newBufferedReader(file(), StandardCharsets.ISO_8859_1)) {
            p.load(in);
        }
        return p.getProperty(LanguageSetting.KEY);
    }

    @Test
    public void nothingRememberedMeansVietnamese() {
        LanguageSetting.restore();
        assertEquals(I18n.VI, I18n.current());
        assertFalse("restoring writes nothing", Files.exists(file()));
    }

    @Test
    public void aChosenLanguageIsSwitchedAtOnceAndRememberedInTheDataFolder() throws IOException {
        assertTrue(LanguageSetting.choose(I18n.EN));
        assertEquals(I18n.EN, I18n.current());
        assertTrue("saved next to the window sizes", Files.isRegularFile(file()));
        assertEquals(I18n.EN, stored());
    }

    @Test
    public void theRememberedLanguageIsAppliedAtTheNextStart() {
        LanguageSetting.choose(I18n.EN);
        I18n.setLanguage(I18n.VI); // a new run starts in Vietnamese
        LanguageSetting.restore();
        assertEquals(I18n.EN, I18n.current());
        assertNotEquals("the texts follow the language", I18n.in(I18n.VI, "order.item.nostock", "X"), I18n.t("order.item.nostock", "X"));
        assertEquals(I18n.in(I18n.EN, "order.item.nostock", "X"), I18n.t("order.item.nostock", "X"));
    }

    @Test
    public void aLanguageTheAppDoesNotHaveChangesNothingAndRemembersNothing() throws IOException {
        for (String bad : new String[]{"fr", "", "EN", " en", null}) {
            assertFalse("'" + bad + "'", LanguageSetting.choose(bad));
            assertEquals(I18n.VI, I18n.current());
        }
        assertFalse("nothing was written", Files.exists(file()));
        LanguageSetting.choose(I18n.EN);
        assertFalse(LanguageSetting.choose("fr"));
        assertEquals("the earlier choice stays", I18n.EN, I18n.current());
        assertEquals(I18n.EN, stored());
    }

    @Test
    public void anUnknownRememberedValueLeavesTheStartLanguageVietnamese() throws IOException {
        Files.createDirectories(file().getParent());
        Files.writeString(file(), LanguageSetting.KEY + "=klingon\n", StandardCharsets.ISO_8859_1);
        LanguageSetting.restore();
        assertEquals(I18n.VI, I18n.current());
    }

    @Test
    public void aDataFolderThatCannotBeReadDoesNotStopTheStart() throws IOException {
        // the "folder" is a file: the settings code copes by itself, restore() leaves Vietnamese
        Path notAFolder = Files.createTempFile(sandbox, "not-a-folder", ".txt");
        System.setProperty(DataPaths.DATA_DIR_PROPERTY, notAFolder.toString());
        LanguageSetting.restore();
        assertEquals(I18n.VI, I18n.current());
        assertTrue("choosing still switches the language for this run", LanguageSetting.choose(I18n.EN));
        assertEquals(I18n.EN, I18n.current());
    }

    @Test
    public void aDataFolderThatCannotEvenBeNamedDoesNotStopTheStartEither() {
        // a path with a NUL character is refused by the file system layer with an exception: the one case where restore() itself must catch
        System.setProperty(DataPaths.DATA_DIR_PROPERTY, "bad\u0000folder");
        LanguageSetting.restore(); // must come back, not throw
        assertEquals(I18n.VI, I18n.current());
    }

    @Test
    public void theChoiceIsPerComputerNotPerShop() {
        LanguageSetting.choose(I18n.EN);
        System.setProperty(DataPaths.DATA_DIR_PROPERTY, sandbox.resolve("other-till").toString());
        I18n.setLanguage(I18n.VI);
        LanguageSetting.restore();
        assertEquals("another data folder has its own choice", I18n.VI, I18n.current());
    }
}
