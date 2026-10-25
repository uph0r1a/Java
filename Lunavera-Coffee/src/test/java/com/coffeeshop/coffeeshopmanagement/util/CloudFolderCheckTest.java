package com.coffeeshop.coffeeshopmanagement.util;

import org.junit.Test;

import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** TODO 3.44: a data folder inside a cloud-sync folder is noticed. */
public class CloudFolderCheckTest {

    @Test
    public void theThreeSyncFoldersAreRecognisedAnywhereInThePath() {
        assertEquals("OneDrive", CloudFolderCheck.syncServiceOf(Path.of("/Users/an/OneDrive/Desktop/Lunavera/data")));
        assertEquals("OneDrive", CloudFolderCheck.syncServiceOf(Path.of("/Users/an/OneDrive - Quan Ca Phe/Documents/data")));
        assertEquals("OneDrive", CloudFolderCheck.syncServiceOf(Path.of("/Users/an/onedrive/data")));
        assertEquals("Dropbox", CloudFolderCheck.syncServiceOf(Path.of("/Users/an/Dropbox/Lunavera/data")));
        assertEquals("Dropbox", CloudFolderCheck.syncServiceOf(Path.of("/Users/an/Dropbox (Personal)/data")));
        assertEquals("Google Drive", CloudFolderCheck.syncServiceOf(Path.of("/Users/an/Google Drive/Lunavera/data")));
        assertEquals("Google Drive", CloudFolderCheck.syncServiceOf(Path.of("/Users/an/GoogleDrive/data")));
    }

    @Test
    public void anOrdinaryFolderIsNotFlagged() {
        assertNull(CloudFolderCheck.syncServiceOf(Path.of("/Users/an/Lunavera/data")));
        assertNull(CloudFolderCheck.syncServiceOf(Path.of("C:/Lunavera/data")));
        assertNull(CloudFolderCheck.syncServiceOf(Path.of("/home/an/projects/one-drive-notes/data")));
        assertNull(CloudFolderCheck.syncServiceOf(Path.of("/home/an/dropbox-backup-scripts/data")));
        assertNull(CloudFolderCheck.syncServiceOf(null));
        assertNull(CloudFolderCheck.problem(Path.of("/Users/an/Lunavera/data")));
    }

    @Test
    public void theWarningNamesTheFolderAndTheService() {
        String text = CloudFolderCheck.problem(Path.of("/Users/an/OneDrive/Lunavera/data"));
        assertTrue(text, text.contains("OneDrive"));
        assertTrue(text, text.contains("Lunavera"));
    }
}
