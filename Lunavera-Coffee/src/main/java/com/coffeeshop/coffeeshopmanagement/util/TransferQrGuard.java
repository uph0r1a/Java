package com.coffeeshop.coffeeshopmanagement.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * The bank-transfer QR is a plain picture in the data folder, so anyone who can write there could swap it for their own
 * account's QR and every transfer sale would pay them (TODO 2A.17). This guard remembers the picture's SHA-256 in
 * {@code qr-transfer.sha256} next to it and reports when the picture is no longer the one an administrator accepted,
 * and {@code qr-transfer.info} (account holder and bank, typed by the owner) is shown under the picture so the
 * cashier can compare it with what the customer's banking app shows.
 */
public final class TransferQrGuard {

    public enum State {
        /** The picture is the one that was accepted. */
        OK,
        /** First time this picture is seen: its fingerprint is now recorded. */
        FIRST_SEEN,
        /** The picture differs from the accepted one. */
        CHANGED
    }

    static final String HASH_FILE = TransferQr.BASE_NAME + ".sha256";
    static final String INFO_FILE = TransferQr.BASE_NAME + ".info";
    static final int MAX_INFO_CHARS = 300;

    private TransferQrGuard() {}

    /** Compares the picture with the remembered fingerprint; records it the very first time. */
    public static State inspect(Path picture) {
        Path hashFile = picture.resolveSibling(HASH_FILE);
        String current = fingerprint(picture);
        try {
            if (!Files.isRegularFile(hashFile)) {
                Files.writeString(hashFile, current, StandardCharsets.UTF_8);
                SecurityLog.event("transfer QR picture recorded (first use): " + picture.getFileName());
                return State.FIRST_SEEN;
            }
            String accepted = Files.readString(hashFile, StandardCharsets.UTF_8).trim();
            if (accepted.equalsIgnoreCase(current)) return State.OK;
            SecurityLog.warn("transfer QR picture CHANGED since it was accepted: " + picture.getFileName());
            return State.CHANGED;
        } catch (IOException e) {
            return State.OK; // cannot read or write the record: do not block the sale, the log has the cause
        }
    }

    /** An administrator confirms the current picture is right. */
    public static void accept(Path picture) throws IOException {
        Files.writeString(picture.resolveSibling(HASH_FILE), fingerprint(picture), StandardCharsets.UTF_8);
        SecurityLog.event(SecurityLog.actor() + " accepted the transfer QR picture " + picture.getFileName());
    }

    /** Account holder / bank text from {@code qr-transfer.info} (first lines, limited length), or empty. */
    public static String accountInfo(Path picture) {
        try {
            Path info = picture.resolveSibling(INFO_FILE);
            if (!Files.isRegularFile(info) || Files.size(info) > 10_000) return "";
            List<String> lines = Files.readAllLines(info, StandardCharsets.UTF_8);
            String text = String.join("\n", lines.stream().limit(4).toList()).trim();
            return text.length() > MAX_INFO_CHARS ? text.substring(0, MAX_INFO_CHARS) : text;
        } catch (IOException | RuntimeException e) {
            return "";
        }
    }

    /** Largest picture an administrator may set (a QR code is a few KB; this only stops a wrong file). */
    public static final long MAX_PICTURE_BYTES = 2L * 1024 * 1024;

    /** Why a picture cannot be used, or null when it is a PNG/JPEG of a sensible size. */
    public static String problemWith(Path source) {
        try {
            if (source == null || !Files.isRegularFile(source)) return "Không đọc được tệp ảnh.";
            long size = Files.size(source);
            if (size == 0) return "Tệp ảnh rỗng.";
            if (size > MAX_PICTURE_BYTES) return "Ảnh quá lớn (tối đa 2 MB).";
            byte[] head = new byte[8];
            try (var in = Files.newInputStream(source)) {
                if (in.readNBytes(head, 0, 8) < 8) return "Tệp không phải ảnh PNG hoặc JPG.";
            }
            boolean png = (head[0] & 0xFF) == 0x89 && head[1] == 'P' && head[2] == 'N' && head[3] == 'G';
            boolean jpg = (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF;
            return png || jpg ? null : "Tệp không phải ảnh PNG hoặc JPG.";
        } catch (IOException e) {
            return "Không đọc được tệp ảnh.";
        }
    }

    /** Where the file will be stored: {@code qr-transfer.png} or {@code qr-transfer.jpg} by the picture's real type. */
    private static String targetName(Path source) throws IOException {
        try (var in = Files.newInputStream(source)) {
            return in.read() == 0x89 ? TransferQr.BASE_NAME + ".png" : TransferQr.BASE_NAME + ".jpg";
        }
    }

    /**
     * Admin action (TODO 2A.17 / 4.26): checks the picture, stores it in {@code folder} as {@code qr-transfer.png|jpg}
     * (replacing any older one), saves the account holder and bank, records the fingerprint and logs who did it.
     * Admin only. Returns the stored picture.
     */
    public static Path replace(Path folder, Path source, String accountText) throws IOException {
        Permissions.requireAdmin("change the transfer QR picture");
        String problem = problemWith(source);
        if (problem != null) throw new IOException(problem);
        Files.createDirectories(folder);
        Path target = folder.resolve(targetName(source));
        Path partial = folder.resolve(target.getFileName() + ".part");
        Files.copy(source, partial, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        for (Path old : TransferQr.allIn(folder)) {
            Files.deleteIfExists(old);
        }
        Files.move(partial, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        String text = accountText == null ? "" : accountText.replace("\r", "").strip();
        if (text.length() > MAX_INFO_CHARS) text = text.substring(0, MAX_INFO_CHARS);
        Path info = folder.resolve(INFO_FILE);
        if (text.isEmpty()) Files.deleteIfExists(info);
        else Files.writeString(info, text, StandardCharsets.UTF_8);
        Files.writeString(folder.resolve(HASH_FILE), fingerprint(target), StandardCharsets.UTF_8);
        SecurityLog.event(SecurityLog.actor() + " SET the transfer QR picture (" + target.getFileName()
                + ", holder/bank: " + (text.isEmpty() ? "none" : text.replace('\n', ' ')) + ")");
        return target;
    }

    static String fingerprint(Path file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(Files.readAllBytes(file)));
        } catch (IOException | NoSuchAlgorithmException e) {
            return "unreadable";
        }
    }

    /** The picture file in the data folder, if the shop has put one there. */
    public static Optional<Path> dataPicture() {
        return TransferQr.findInFolder(DataPaths.dataDir());
    }
}
