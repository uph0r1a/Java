package com.coffeeshop.coffeeshopmanagement.util;

import javafx.scene.image.Image;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Small pictures for table rows, kept in a cache. The product table used to read and shrink the picture file again every
 * time a row was drawn (scrolling, paging, sorting); now each file is decoded once, in the background (TODO 2.35).
 */
public final class Thumbnails {

    private static final int MAX_ENTRIES = 300;

    private static final Map<String, Image> CACHE = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Image> eldest) {
            return size() > MAX_ENTRIES;
        }
    };

    private Thumbnails() {
    }

    /** The picture scaled to fit a {@code size} x {@code size} square; same instance for the same file, size and modification time. */
    public static synchronized Image of(File file, int size) {
        String key = file.getAbsolutePath() + "|" + file.lastModified() + "|" + size;
        return CACHE.computeIfAbsent(key, k -> new Image(file.toURI().toString(), size, size, true, true, true));
    }

    static synchronized int cachedCount() {
        return CACHE.size();
    }

    static synchronized void clear() {
        CACHE.clear();
    }
}
