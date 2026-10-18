package com.coffeeshop.coffeeshopmanagement.util;

import org.junit.Test;

import static org.junit.Assert.*;

public class ErrorTextTest {
    @Test
    public void driverTextAndPathsAreHidden() {
        assertEquals(ErrorText.GENERIC, ErrorText.forScreen(
                "[SQLITE_CANTOPEN] Unable to open the database file (unable to open database: C:\\Users\\shop\\data\\lunavera.db)"));
        assertEquals(ErrorText.GENERIC, ErrorText.forScreen("java.io.IOException: disk full"));
        assertEquals(ErrorText.GENERIC, ErrorText.forScreen("Cannot write /home/shop/data/backups/x.zip"));
        assertEquals(ErrorText.GENERIC, ErrorText.forScreen("NullPointerException"));
    }

    @Test
    public void englishDataLayerMessagesAreNotShownToStaff() {
        assertEquals(ErrorText.GENERIC, ErrorText.forScreen("Failed to update account status"));
        assertEquals(ErrorText.GENERIC, ErrorText.forScreen("Unable to load the menu"));
        assertEquals("a Vietnamese message mentioning a failure is kept", "Không thể lưu: tên đã tồn tại.",
                ErrorText.forScreen("Không thể lưu: tên đã tồn tại."));
    }

    @Test
    public void theAppsOwnVietnameseMessagesAreShownAsTheyAre() {
        String own = "Tên đăng nhập đã tồn tại.";
        assertEquals(own, ErrorText.forScreen(own));
        assertEquals("Đơn hàng #12 không đủ tồn kho.", ErrorText.forScreen("Đơn hàng #12 không đủ tồn kho."));
        assertNull(ErrorText.forScreen(null));
    }
}
