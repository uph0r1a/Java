package com.coffeeshop.coffeeshopmanagement.util;

/** A file size for people: "850 KB", "12,4 MB" (Vietnamese decimal comma, no locale data needed). */
public final class FileSizeText {

    private FileSizeText() {
    }

    public static String format(long bytes) {
        if (bytes < 1024) return Math.max(bytes, 0) + " B";
        long kb = (bytes + 512) / 1024;
        if (kb < 1024) return kb + " KB";
        long mbTenths = (bytes * 10 + (1L << 19)) / (1L << 20);
        if (mbTenths < 10240) return (mbTenths / 10) + "," + (mbTenths % 10) + " MB";
        long gbTenths = (bytes * 10 + (1L << 29)) / (1L << 30);
        return (gbTenths / 10) + "," + (gbTenths % 10) + " GB";
    }
}
