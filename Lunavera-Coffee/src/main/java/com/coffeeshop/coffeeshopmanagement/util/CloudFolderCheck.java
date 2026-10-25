package com.coffeeshop.coffeeshopmanagement.util;

import java.nio.file.Path;
import java.util.Locale;

/**
 * Notices a data folder that sits inside a cloud-sync folder (TODO 3.44). On Windows the Desktop and Documents folders are
 * often redirected into OneDrive; the sync program then copies, locks or renames the database, its {@code -wal}/{@code -shm}
 * files and the backup zips while the app is running. The check only looks at the folder names of the path, so it needs no
 * file access and is the same on every system.
 */
public final class CloudFolderCheck {

    private CloudFolderCheck() {}

    /**
     * The name of the sync service whose folder holds {@code dir} ("OneDrive", "Dropbox" or "Google Drive"), or null when
     * the path is not inside such a folder. OneDrive folders may carry a suffix ({@code OneDrive - Company}); Google Drive's
     * is {@code Google Drive} or {@code GoogleDrive}.
     */
    public static String syncServiceOf(Path dir) {
        if (dir == null) return null;
        for (Path part : dir.toAbsolutePath().normalize()) {
            String name = part.toString().toLowerCase(Locale.ROOT).trim();
            if (name.equals("onedrive") || name.startsWith("onedrive -") || name.startsWith("onedrive-")) return "OneDrive";
            if (name.equals("dropbox") || name.startsWith("dropbox (") || name.startsWith("dropbox -")) return "Dropbox";
            if (name.equals("google drive") || name.equals("googledrive")) return "Google Drive";
        }
        return null;
    }

    /** The warning text for the start-up dialog, or null when the data folder is not in a sync folder. */
    public static String problem(Path dataDir) {
        String service = syncServiceOf(dataDir);
        return service == null ? null : I18n.t("app.cloud.message", dataDir.toAbsolutePath().normalize(), service);
    }
}
