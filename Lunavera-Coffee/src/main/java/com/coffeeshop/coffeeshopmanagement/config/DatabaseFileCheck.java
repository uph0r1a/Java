package com.coffeeshop.coffeeshopmanagement.config;

import com.coffeeshop.coffeeshopmanagement.util.I18n;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Tells a damaged SQLite file from a brand-new shop (TODO 3.26). {@code DatabaseConfig.initialize()} decides "first run"
 * by whether the {@code users} table exists, so a zero-byte {@code lunavera.db} (which an interrupted copy or restore can
 * leave) used to pass as a new install: the default account and the starter menu were created again and the old data was
 * gone without a word.
 *
 * <p>A zero-byte file is refused only when there is evidence the shop had data before: a backup next to it, or a picture
 * that is not one of the bundled starter pictures. A zero-byte file with neither (for example one left by a first start
 * that was interrupted) holds nothing to lose, so the app starts as a new shop, as before; refusing that would block a
 * new install for good.
 */
final class DatabaseFileCheck {

    private DatabaseFileCheck() {
    }

    /** Null when the file can be opened as usual; otherwise the message for the start-up dialog. */
    static String problem(Path dbFile) {
        if (dbFile == null) {
            return null;
        }
        try {
            if (!Files.isRegularFile(dbFile) || Files.size(dbFile) != 0) {
                return null; // missing (a new shop) or has content (the migrations decide the rest)
            }
        } catch (IOException e) {
            return null; // cannot tell; opening the file will report the real error
        }
        Path folder = dbFile.toAbsolutePath().getParent();
        if (folder == null) {
            return null;
        }
        boolean hasBackup = containsFile(folder.resolve("backups"), name -> name.endsWith(".zip") || name.endsWith(".db"));
        boolean hasPictures = containsFile(folder.resolve("images"), name -> !name.startsWith("seed_"));
        if (!hasBackup && !hasPictures) {
            return null;
        }
        return I18n.t("db.file.empty", dbFile.toAbsolutePath(), (hasPictures ? " " + I18n.t("db.file.orpictures") : ""), folder.resolve("backups"));
    }

    private static boolean containsFile(Path dir, java.util.function.Predicate<String> nameMatches) {
        if (!Files.isDirectory(dir)) {
            return false;
        }
        try (DirectoryStream<Path> files = Files.newDirectoryStream(dir)) {
            for (Path file : files) {
                if (Files.isRegularFile(file) && nameMatches.test(file.getFileName().toString().toLowerCase(java.util.Locale.ROOT))) {
                    return true;
                }
            }
        } catch (IOException e) {
            return false;
        }
        return false;
    }
}
