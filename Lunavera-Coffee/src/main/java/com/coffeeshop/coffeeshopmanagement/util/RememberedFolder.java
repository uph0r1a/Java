package com.coffeeshop.coffeeshopmanagement.util;

import java.io.File;

/**
 * Remembers the folder a save/open dialog was last used in (TODO 5.17, 5.9), in the same small settings file as the
 * window sizes. A remembered folder that no longer exists is ignored.
 */
public final class RememberedFolder {

    private static final String PREFIX = "folder.";

    private RememberedFolder() {
    }

    /** The folder to start a dialog in: the remembered one for {@code key} if it still exists, else {@code fallback} if that exists, else null. */
    public static File startFor(String key, File fallback) {
        String saved = UiSettings.get(PREFIX + key, null);
        if (saved != null && !saved.isBlank()) {
            File folder = new File(saved);
            if (folder.isDirectory()) return folder;
        }
        return fallback != null && fallback.isDirectory() ? fallback : null;
    }

    /** Remembers the folder of the file (or the folder itself) the person just chose. Never throws. */
    public static void remember(String key, File chosen) {
        if (chosen == null) return;
        File folder = chosen.isDirectory() ? chosen : chosen.getAbsoluteFile().getParentFile();
        if (folder != null && folder.isDirectory()) {
            UiSettings.put(PREFIX + key, folder.getAbsolutePath());
        }
    }
}
