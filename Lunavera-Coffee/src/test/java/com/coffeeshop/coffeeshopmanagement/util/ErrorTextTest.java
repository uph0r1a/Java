package com.coffeeshop.coffeeshopmanagement.util;

import org.junit.Test;

import static org.junit.Assert.*;

public class ErrorTextTest {
    @Test
    public void driverTextAndPathsAreHidden() {
        assertEquals(ErrorText.generic(), ErrorText.forScreen(
                "[SQLITE_CANTOPEN] Unable to open the database file (unable to open database: C:\\Users\\shop\\data\\lunavera.db)"));
        assertEquals(ErrorText.generic(), ErrorText.forScreen("java.io.IOException: disk full"));
        assertEquals(ErrorText.generic(), ErrorText.forScreen("Cannot write /home/shop/data/backups/x.zip"));
        assertEquals(ErrorText.generic(), ErrorText.forScreen("NullPointerException"));
    }

    @Test
    public void englishDataLayerMessagesAreNotShownToStaff() {
        assertEquals(ErrorText.generic(), ErrorText.forScreen("Failed to update account status"));
        assertEquals(ErrorText.generic(), ErrorText.forScreen("Unable to load the menu"));
        assertEquals("a Vietnamese message mentioning a failure is kept", "Không thể lưu: tên đã tồn tại.",
                ErrorText.forScreen("Không thể lưu: tên đã tồn tại."));
    }

    @Test
    public void theAppsOwnVietnameseMessagesAreShownAsTheyAre() {
        String own = "Tên đăng nhập đã tồn tại.";
        assertEquals(own, ErrorText.forScreen(own));
        assertEquals("Đơn hàng #12 không đủ tồn kho.", ErrorText.forScreen("Đơn hàng #12 không đủ tồn kho."));
    }

    @Test
    public void everyTextThatIsNotTheAppsOwnIsHidden() {
        String[] seen = {
                "No space left on device", "Access is denied", "Permission denied", "database is locked",
                "Connection refused: connect", "Communications link failure",
                "Table 'lunavera.orders' doesn't exist", "Duplicate entry 'admin' for key 'username'",
                "Data too long for column 'name' at row 1", "Unknown column 'cost' in 'field list'",
                "Unknown database 'lunavera'", "The process cannot access the file because it is being used by another process",
                "Access denied for user 'lunavera'@'localhost' (using password: YES)", "disk full", "An unexpected thing happened",
                "", "   ", null};
        for (String text : seen) {
            assertEquals("hidden: " + text, ErrorText.generic(), ErrorText.forScreen(text));
        }
    }

    @Test
    public void aVietnameseMessageWithATechnicalTailShowsOnlyItsCleanFront() {
        assertEquals("Không thể tạo tài khoản",
                ErrorText.forScreen("Không thể tạo tài khoản: Access denied for user 'lunavera'@'localhost' (using password: YES)"));
        String hint = "Cơ sở dữ liệu đang bận (có máy khác đang ghi). Hãy thử lại sau giây lát.";
        assertEquals("a clean message with its own brackets stays whole", hint, ErrorText.forScreen(hint));
        assertEquals("Sai tài khoản hoặc mật khẩu của cơ sở dữ liệu",
                ErrorText.forScreen("Sai tài khoản hoặc mật khẩu của cơ sở dữ liệu (Không thể lưu: Access denied for user 'x'@'y')"));
    }

    @Test
    public void theAppsOwnEnglishTextsAreShownInEnglishAndDriverTextIsStillHidden() {
        // TODO 2A.38 with the language switch: with the English language on, the app's own messages have no Vietnamese
        // letters, so the filter must know them from the language files, not from their letters.
        String before = I18n.current();
        try {
            assertTrue(I18n.setLanguage("en"));
            for (String key : new String[] {"backup.crypto.wrong", "backup.locked.needpass", "account.rule.phone", "backup.noadmin"}) {
                String own = I18n.t(key);
                assertEquals(key, own, ErrorText.forScreen(own));
            }
            String withNumber = I18n.t("backup.crypto.short", 8);
            assertEquals(withNumber, ErrorText.forScreen(withNumber));
            assertEquals(ErrorText.generic(), ErrorText.forScreen("Access denied for user 'x'@'y'"));
            assertEquals(ErrorText.generic(), ErrorText.forScreen("An unexpected thing happened"));
            assertEquals("a clean English message keeps its front when a technical tail is added", I18n.t("backup.crypto.wrong"),
                    ErrorText.forScreen(I18n.t("backup.crypto.wrong") + ": Access denied for user 'x'@'y'"));
        } finally {
            I18n.setLanguage(before);
        }
    }

    @Test
    public void everyTextOfBothLanguageFilesThatIsAnErrorMessageIsRecognised() {
        // a Vietnamese message from the files is the app's own in both languages (the files are the allow-list)
        String before = I18n.current();
        try {
            for (String language : I18n.LANGUAGES) {
                assertTrue(I18n.setLanguage(language));
                String own = I18n.t("account.rule.username");
                assertEquals(language, own, ErrorText.forScreen(own));
            }
        } finally {
            I18n.setLanguage(before);
        }
    }
}
