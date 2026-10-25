package com.coffeeshop.coffeeshopmanagement.util;

import com.coffeeshop.coffeeshopmanagement.config.AppFlags;

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
 * account's QR and every transfer sale would pay them (TODO 2A.17). This guard compares the picture (and the account
 * holder / bank text in {@code qr-transfer.info}, shown under the picture so the cashier can compare it with what the
 * customer's banking app shows) with the fingerprints an administrator accepted.
 *
 * <p>The accepted fingerprints are kept <b>in the database</b> ({@link AppFlags}), not in a file beside the picture
 * (TODO 2A.34): deleting or rewriting a file in the data folder no longer resets the check. A picture with no accepted
 * record, or one that cannot be checked, is reported, not trusted; an administrator accepts it once. An older shop that
 * still has the previous {@code qr-transfer.sha256} file has it adopted once, when it matches the picture.
 */
public final class TransferQrGuard {

    public enum State {
        /** The picture and its account text are the ones that were accepted. */
        OK,
        /** The picture or the account text differs from what was accepted. */
        CHANGED,
        /** Nobody has accepted this picture yet (a new picture put in the folder by hand, or a restore without the record). */
        UNCONFIRMED,
        /** The accepted record could not be read (database error) or the picture could not be read, so nothing can be said. */
        UNVERIFIABLE;

        /** True for everything except {@link #OK}: the cashier must not trust the picture. */
        public boolean needsAttention() {
            return this != OK;
        }
    }

    /** Where the accepted fingerprints live. The real one is the database; tests use a plain object. */
    interface Store {
        Optional<String> picture();

        Optional<String> info();

        void save(String pictureFingerprint, String infoFingerprint);
    }

    /** Names of the two flags ({@code prefix + 64 hex digits}, under the 100-character limit of the flag column). */
    static final String PICTURE_PREFIX = "transferqr.pic=";
    static final String INFO_PREFIX = "transferqr.info=";

    private static final Store DATABASE = new Store() {
        @Override
        public Optional<String> picture() {
            return AppFlags.valueWithPrefix(PICTURE_PREFIX);
        }

        @Override
        public Optional<String> info() {
            return AppFlags.valueWithPrefix(INFO_PREFIX);
        }

        @Override
        public void save(String pictureFingerprint, String infoFingerprint) {
            AppFlags.replaceValues(java.util.Map.of(PICTURE_PREFIX, pictureFingerprint, INFO_PREFIX, infoFingerprint));
        }
    };

    /** The file older versions used for the accepted fingerprint; read once to adopt it, never written again. */
    static final String HASH_FILE = TransferQr.BASE_NAME + ".sha256";
    static final String INFO_FILE = TransferQr.BASE_NAME + ".info";
    static final int MAX_INFO_CHARS = 300;

    private TransferQrGuard() {}

    /** Compares the picture and its account text with what an administrator accepted. Never trusts what it cannot check. */
    public static State inspect(Path picture) {
        return inspect(picture, DATABASE);
    }

    static State inspect(Path picture, Store store) {
        String current = fingerprint(picture);
        if (UNREADABLE.equals(current)) {
            SecurityLog.warnLimited("qr-unreadable", "transfer QR picture cannot be read: " + picture.getFileName());
            return State.UNVERIFIABLE;
        }
        String currentInfo = infoFingerprint(picture);
        Optional<String> accepted;
        Optional<String> acceptedInfo;
        try {
            accepted = store.picture();
            acceptedInfo = store.info();
        } catch (RuntimeException e) {
            SecurityLog.warnLimited("qr-unreadable-record", "transfer QR record could not be read: " + e.getMessage());
            return State.UNVERIFIABLE;
        }
        if (accepted.isEmpty() || acceptedInfo.isEmpty()) {
            if (adoptLegacyRecord(picture, current, currentInfo, store)) return State.OK;
            // checked every time the payment dialog or the dashboard looks at it: once a minute is enough in the log (2A.25)
            SecurityLog.warnLimited("qr-unconfirmed",
                    "transfer QR picture has not been accepted by an administrator yet: " + picture.getFileName());
            return State.UNCONFIRMED;
        }
        if (!accepted.get().equalsIgnoreCase(current) || !acceptedInfo.get().equalsIgnoreCase(currentInfo)) {
            SecurityLog.warnLimited("qr-changed",
                    "transfer QR picture or account text CHANGED since it was accepted: " + picture.getFileName());
            return State.CHANGED;
        }
        return State.OK;
    }

    /**
     * A shop that used the previous version has {@code qr-transfer.sha256} beside the picture. When it still matches the
     * picture it is taken over once into the database (and the file removed), so an upgrade does not make every administrator
     * confirm a picture they already confirmed. Anything else stays unconfirmed.
     */
    private static boolean adoptLegacyRecord(Path picture, String current, String currentInfo, Store store) {
        Path legacy = picture.resolveSibling(HASH_FILE);
        try {
            if (!Files.isRegularFile(legacy)) return false;
            String old = Files.readString(legacy, StandardCharsets.UTF_8).trim();
            if (!old.equalsIgnoreCase(current)) return false;
            store.save(current, currentInfo);
            Files.deleteIfExists(legacy);
            SecurityLog.event("transfer QR record moved from " + HASH_FILE + " into the database: " + picture.getFileName());
            return true;
        } catch (IOException | RuntimeException e) {
            SecurityLog.warn("transfer QR old record could not be taken over: " + e.getMessage());
            return false;
        }
    }

    /** An administrator confirms the current picture and its account text are right. */
    public static void accept(Path picture) throws IOException {
        accept(picture, DATABASE);
    }

    static void accept(Path picture, Store store) throws IOException {
        Permissions.requireAdmin("accept the transfer QR picture");
        String current = fingerprint(picture);
        if (UNREADABLE.equals(current)) throw new IOException(I18n.t("qr.read.fail"));
        try {
            store.save(current, infoFingerprint(picture));
        } catch (RuntimeException e) {
            throw new IOException(I18n.t("pos.qr.accept.fail.message"), e);
        }
        Files.deleteIfExists(picture.resolveSibling(HASH_FILE));
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
            if (source == null || !Files.isRegularFile(source)) return I18n.t("image.unreadable");
            long size = Files.size(source);
            if (size == 0) return I18n.t("qr.file.empty");
            if (size > MAX_PICTURE_BYTES) return I18n.t("qr.file.toolarge");
            byte[] head = new byte[8];
            try (var in = Files.newInputStream(source)) {
                if (in.readNBytes(head, 0, 8) < 8) return I18n.t("qr.file.type");
            }
            boolean png = (head[0] & 0xFF) == 0x89 && head[1] == 'P' && head[2] == 'N' && head[3] == 'G';
            boolean jpg = (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF;
            return png || jpg ? null : I18n.t("qr.file.type");
        } catch (IOException e) {
            return I18n.t("image.unreadable");
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
        return replace(folder, source, accountText, DATABASE);
    }

    static Path replace(Path folder, Path source, String accountText, Store store) throws IOException {
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
        String text = writeInfo(folder, accountText);
        try {
            store.save(fingerprint(target), infoFingerprint(target));
        } catch (RuntimeException e) {
            throw new IOException(I18n.t("pos.qr.accept.fail.message"), e);
        }
        Files.deleteIfExists(folder.resolve(HASH_FILE));
        SecurityLog.event(SecurityLog.actor() + " SET the transfer QR picture (" + target.getFileName()
                + ", holder/bank: " + (text.isEmpty() ? "none" : text.replace('\n', ' ')) + ")");
        return target;
    }

    /** The account text as it is stored: no carriage returns, no outer blank space, at most {@link #MAX_INFO_CHARS} characters. */
    private static String cleanInfo(String accountText) {
        String text = accountText == null ? "" : accountText.replace("\r", "").strip();
        return text.length() > MAX_INFO_CHARS ? text.substring(0, MAX_INFO_CHARS) : text;
    }

    /** Writes (or, for an empty text, removes) {@code qr-transfer.info} in {@code folder} and returns the cleaned text. */
    private static String writeInfo(Path folder, String accountText) throws IOException {
        String text = cleanInfo(accountText);
        Path info = folder.resolve(INFO_FILE);
        if (text.isEmpty()) Files.deleteIfExists(info);
        else Files.writeString(info, text, StandardCharsets.UTF_8);
        return text;
    }

    /**
     * Admin action (TODO 5.32): changes only the account holder / bank text and keeps the picture, so a typo or a new bank
     * does not need the picture chosen again. It is allowed only while the picture in {@code folder} is the one an
     * administrator accepted: if the file was swapped by hand (or never accepted) this would otherwise quietly accept it, so
     * it is refused and the administrator must choose the picture again, which is the check the guard exists for.
     */
    public static void replaceInfo(Path folder, String accountText) throws IOException {
        replaceInfo(folder, accountText, DATABASE);
    }

    static void replaceInfo(Path folder, String accountText, Store store) throws IOException {
        Permissions.requireAdmin("change the transfer QR account text");
        Path picture = TransferQr.findInFolder(folder)
                .orElseThrow(() -> new IOException(I18n.t("admin.qr.textonly.nopicture")));
        String current = fingerprint(picture);
        if (UNREADABLE.equals(current)) throw new IOException(I18n.t("qr.read.fail"));
        Optional<String> accepted;
        try {
            accepted = store.picture();
        } catch (RuntimeException e) {
            throw new IOException(I18n.t("pos.qr.accept.fail.message"), e);
        }
        if (accepted.isEmpty() || !accepted.get().equalsIgnoreCase(current)) {
            throw new IOException(I18n.t("admin.qr.textonly.notaccepted"));
        }
        String text = writeInfo(folder, accountText);
        try {
            store.save(current, infoFingerprint(picture));
        } catch (RuntimeException e) {
            throw new IOException(I18n.t("pos.qr.accept.fail.message"), e);
        }
        Files.deleteIfExists(folder.resolve(HASH_FILE));
        SecurityLog.event(SecurityLog.actor() + " changed the transfer QR account text only (" + picture.getFileName()
                + ", holder/bank: " + (text.isEmpty() ? "none" : text.replace('\n', ' ')) + ")");
    }

    static final String UNREADABLE = "unreadable";

    static String fingerprint(Path file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(Files.readAllBytes(file)));
        } catch (IOException | NoSuchAlgorithmException e) {
            return UNREADABLE;
        }
    }

    /** SHA-256 of the account text exactly as it is shown (an empty text has one too), so a changed holder/bank line is noticed. */
    static String infoFingerprint(Path picture) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(accountInfo(picture).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            return UNREADABLE;
        }
    }

    /** The picture file in the data folder, if the shop has put one there. */
    public static Optional<Path> dataPicture() {
        return TransferQr.findInFolder(DataPaths.dataDir());
    }
}
