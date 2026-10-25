package com.coffeeshop.coffeeshopmanagement.controller;

import org.junit.Test;

import static org.junit.Assert.*;

/** TODO 2A.4: what the "lock this backup" dialog accepts (the window itself needs JavaFX and is not tested here). */
public class BackupPassphraseDialogTest {
    @Test
    public void aGoodPassphraseHasNoProblem() {
        assertNull(BackupPassphraseDialog.problemWith("mật khẩu sao lưu", "mật khẩu sao lưu"));
        assertNull("exactly the minimum length", BackupPassphraseDialog.problemWith("12345678", "12345678"));
    }

    @Test
    public void eachMistakeIsReported() {
        assertNotNull("too short", BackupPassphraseDialog.problemWith("abc", "abc"));
        assertNotNull("one under the minimum", BackupPassphraseDialog.problemWith("1234567", "1234567"));
        assertNotNull("confirmation differs", BackupPassphraseDialog.problemWith("mật khẩu sao lưu", "mật khẩu sao lưu "));
        assertNotNull("nothing typed is handled by the dialog itself, never reaches here as a valid phrase",
                BackupPassphraseDialog.problemWith("", ""));
        assertNotNull(BackupPassphraseDialog.problemWith(null, null));
    }
}
