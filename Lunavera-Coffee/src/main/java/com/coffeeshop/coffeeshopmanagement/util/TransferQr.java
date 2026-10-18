package com.coffeeshop.coffeeshopmanagement.util;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Finds the shop's own bank-transfer QR picture, shown when a customer pays by "Chuyển khoản".
 *
 * <p>The picture is named {@code qr-transfer} ({@code .png}, {@code .jpg} or {@code .jpeg}, any
 * capitalisation) and is looked for in this order:
 * <ol>
 *   <li>the data folder ({@code data/qr-transfer.png}) - the shop can replace it without rebuilding the app;</li>
 *   <li>bundled with the program as {@code /images/qr-transfer.png} (or .jpg/.jpeg) - the fallback.</li>
 * </ol>
 * If neither exists nothing is returned: the screen then says the picture is missing, it never
 * shows a made-up QR (a wrong QR would send customers' money to the wrong account).
 */
public final class TransferQr {

    public static final String BASE_NAME = "qr-transfer";
    private static final List<String> EXTENSIONS = List.of("png", "jpg", "jpeg");

    private TransferQr() {
    }

    /** The picture as a URL JavaFX's {@code Image} can load, or empty when the shop has not added one. */
    public static Optional<String> findImageUrl() {
        Optional<Path> inDataFolder = findInFolder(DataPaths.dataDir());
        if (inDataFolder.isPresent()) {
            return Optional.of(inDataFolder.get().toUri().toString());
        }
        for (String extension : EXTENSIONS) {
            URL bundled = TransferQr.class.getResource("/images/" + BASE_NAME + "." + extension);
            if (bundled != null) {
                return Optional.of(bundled.toExternalForm());
            }
        }
        return Optional.empty();
    }

    /**
     * A file called {@code qr-transfer} with a PNG/JPG extension directly inside {@code folder}
     * (name and extension compared ignoring capitalisation, as Windows does; PNG is preferred
     * over JPG when both exist).
     */
    static Optional<Path> findInFolder(Path folder) {
        if (folder == null || !Files.isDirectory(folder)) {
            return Optional.empty();
        }
        try (Stream<Path> files = Files.list(folder)) {
            List<Path> matches = files.filter(Files::isRegularFile)
                    .filter(p -> extensionRank(p.getFileName().toString()) >= 0)
                    .sorted((a, b) -> Integer.compare(extensionRank(a.getFileName().toString()),
                            extensionRank(b.getFileName().toString())))
                    .toList();
            return matches.isEmpty() ? Optional.empty() : Optional.of(matches.get(0));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /** Every {@code qr-transfer.png|jpg|jpeg} directly inside {@code folder}. */
    static List<Path> allIn(Path folder) {
        if (folder == null || !Files.isDirectory(folder)) return List.of();
        try (Stream<Path> files = Files.list(folder)) {
            return files.filter(Files::isRegularFile)
                    .filter(p -> extensionRank(p.getFileName().toString()) >= 0).toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    /** 0 for png, 1 for jpg, 2 for jpeg when the name is exactly qr-transfer.<ext>; -1 otherwise. */
    private static int extensionRank(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot <= 0) {
            return -1;
        }
        String base = fileName.substring(0, dot);
        String extension = fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        return base.equalsIgnoreCase(BASE_NAME) ? EXTENSIONS.indexOf(extension) : -1;
    }
}
