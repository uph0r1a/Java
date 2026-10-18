package com.coffeeshop.coffeeshopmanagement.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class FileSizeTextTest {

    @Test
    public void smallSizesAreShownInBytesAndKilobytes() {
        assertEquals("0 B", FileSizeText.format(0));
        assertEquals("1023 B", FileSizeText.format(1023));
        assertEquals("1 KB", FileSizeText.format(1024));
        assertEquals("850 KB", FileSizeText.format(850L * 1024));
    }

    @Test
    public void largerSizesUseOneDecimalWithAComma() {
        assertEquals("1,0 MB", FileSizeText.format(1024L * 1024));
        assertEquals("12,4 MB", FileSizeText.format((long) (12.4 * 1024 * 1024)));
        assertEquals("2,5 GB", FileSizeText.format((long) (2.5 * 1024 * 1024 * 1024)));
    }

    @Test
    public void justBelowAUnitDoesNotShowTheSmallerUnitTooBig() {
        assertEquals("1,0 MB", FileSizeText.format(1024L * 1024 - 1));
        assertEquals("1,0 GB", FileSizeText.format(1024L * 1024 * 1024 - 1));
    }

    @Test
    public void aNegativeSizeIsShownAsZero() {
        assertEquals("0 B", FileSizeText.format(-5));
    }
}
