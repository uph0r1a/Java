package com.coffeeshop.coffeeshopmanagement.util;

import com.coffeeshop.coffeeshopmanagement.config.AppFlags;
import com.coffeeshop.coffeeshopmanagement.dao.TestDatabaseSupport;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.*;

/** TODO 2A.34 on the real (SQLite test) database: the accepted record is kept in {@code app_flags}, not in the folder. */
public class TransferQrGuardDatabaseTest {

    @BeforeClass
    public static void setUpDatabase() {
        TestDatabaseSupport.ensureReady();
    }

    @After
    public void signOut() {
        TestSession.clear();
    }

    @Test
    public void theAcceptedPictureIsRememberedInTheDatabaseAndASwapIsCaught() throws Exception {
        TestSession.admin();
        Path dir = Files.createTempDirectory("qrdb");
        Path picture = dir.resolve("qr-transfer.png");
        Files.writeString(picture, "shop qr");
        TransferQrGuard.accept(picture);
        assertEquals(TransferQrGuard.State.OK, TransferQrGuard.inspect(picture));
        assertEquals(TransferQrGuard.fingerprint(picture), AppFlags.valueWithPrefix(TransferQrGuard.PICTURE_PREFIX).orElseThrow());
        Files.writeString(dir.resolve("qr-transfer.sha256"), "anything"); // a file beside the picture means nothing now
        Files.writeString(picture, "someone else's qr");
        assertEquals(TransferQrGuard.State.CHANGED, TransferQrGuard.inspect(picture));
        // Accepting again replaces the record; there is only ever one.
        TransferQrGuard.accept(picture);
        assertEquals(TransferQrGuard.State.OK, TransferQrGuard.inspect(picture));
        assertEquals(TransferQrGuard.fingerprint(picture), AppFlags.valueWithPrefix(TransferQrGuard.PICTURE_PREFIX).orElseThrow());
    }

    @Test
    public void aFlagValueIsReplacedNotAdded() {
        String prefix = "test.value-" + UUID.randomUUID() + "=";
        assertTrue(AppFlags.valueWithPrefix(prefix).isEmpty());
        AppFlags.replaceValues(Map.of(prefix, "first"));
        assertEquals("first", AppFlags.valueWithPrefix(prefix).orElseThrow());
        AppFlags.replaceValues(Map.of(prefix, "second"));
        assertEquals("second", AppFlags.valueWithPrefix(prefix).orElseThrow());
    }
}
