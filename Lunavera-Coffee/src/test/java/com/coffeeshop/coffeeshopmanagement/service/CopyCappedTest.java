package com.coffeeshop.coffeeshopmanagement.service;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

/** TODO 2A.21: a restore never writes more than the limit. */
public class CopyCappedTest {
    @Test
    public void aStreamWithinTheLimitIsCopied() throws IOException {
        Path target = Files.createTempFile("capped", ".bin");
        long n = BackupService.copyCapped(new ByteArrayInputStream(new byte[1000]), target, 1000);
        assertEquals(1000, n);
        assertEquals(1000, Files.size(target));
    }

    @Test
    public void aStreamOverTheLimitIsStoppedAndTheFileRemoved() throws IOException {
        Path target = Files.createTempFile("capped", ".bin");
        try {
            BackupService.copyCapped(new ByteArrayInputStream(new byte[200_000]), target, 1000);
            fail("must refuse");
        } catch (IOException expected) {
            // good
        }
        assertFalse("no half-written file is left behind", Files.exists(target));
    }
}
