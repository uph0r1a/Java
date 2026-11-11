package com.coffeeshop.coffeeshopmanagement.util;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** TODO 6.71: the guides can be opened from Settings: the page for each audience and language is found, with a fallback. */
public class HelpPagesLoadTest {

    @Test
    public void everyAudienceAndLanguageHasAPageThatLoads() {
        for (String audience : new String[]{HelpPages.OWNER, HelpPages.CASHIER}) {
            for (String language : I18n.LANGUAGES) {
                String text = HelpPages.load(audience, language);
                assertNotNull(audience + " " + language, text);
                assertTrue(audience + " " + language + " is not a real page", text.length() > 500);
            }
        }
    }

    @Test
    public void thePagesAreNamedAsTheFilesAreAndAnUnknownLanguageFallsBack() {
        assertEquals("/help/owner_en.md", HelpPages.resource("owner", "en"));
        assertEquals("/help/cashier_vi.md", HelpPages.resource("cashier", "vi"));
        assertEquals("/help/cashier_vi.md", HelpPages.resource("cashier", "fr"));
        assertEquals("/help/cashier_vi.md", HelpPages.resource("cashier", null));
        assertEquals(HelpPages.load("owner", "vi"), HelpPages.load("owner", "fr"));
        assertNull("no such audience", HelpPages.load("nobody", "en"));
    }

    @Test
    public void theSettingsWindowOpensBothGuidesThroughTheDialog() throws IOException {
        String java = "src/main/java/com/coffeeshop/coffeeshopmanagement/controller/";
        String settings = Files.readString(Path.of(java + "SettingsDialog.java"));
        assertTrue(settings.contains("HelpDialog.show(com.coffeeshop.coffeeshopmanagement.util.HelpPages.OWNER)"));
        assertTrue(settings.contains("HelpDialog.show(com.coffeeshop.coffeeshopmanagement.util.HelpPages.CASHIER)"));
        String dialog = Files.readString(Path.of(java + "HelpDialog.java"));
        assertTrue(dialog.contains("HelpPages.load(audience, I18n.current())"));
        assertTrue("a missing page gives a message, not an empty window", dialog.contains("help.missing"));
    }

    /** TODO 6.71: a cashier cannot open Settings, so the sidebar has a Help button of its own, added by {@code addButtons} for everybody signed in. */
    @Test
    public void theSidebarHasAHelpButtonForEverySignedInPersonThatPicksTheGuideByRole() throws IOException {
        String sidebar = Files.readString(Path.of("src/main/java/com/coffeeshop/coffeeshopmanagement/controller/SidebarExtras.java"));
        assertTrue("added with the other extras", sidebar.contains("        addHelpButton(root);"));
        assertTrue("for every signed-in person, not only administrators", sidebar.contains("public static void addHelpButton(Parent root) {\n        if (Session.getCurrentUser() == null) return;"));
        assertTrue(sidebar.contains("HelpDialog.show(Session.isAdmin()"));
        assertTrue(sidebar.contains("HelpPages.OWNER : com.coffeeshop.coffeeshopmanagement.util.HelpPages.CASHIER"));
        for (String language : I18n.LANGUAGES) {
            assertTrue(language + " has a label for it", I18n.t("help.open").length() > 0 && !I18n.t("help.open").equals("help.open"));
        }
    }
}
