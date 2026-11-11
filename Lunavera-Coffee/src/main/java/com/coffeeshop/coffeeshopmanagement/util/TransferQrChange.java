package com.coffeeshop.coffeeshopmanagement.util;

import java.io.IOException;
import java.nio.file.Path;

/**
 * One sitting of the "Transfer QR code" window (TODO 4.26): what is stored now, what the administrator has chosen so far, and what
 * saving would do. Nothing is stored until {@link #apply}: choosing a picture only checks it and lets the window show it next to the
 * holder and bank text. No JavaFX here, so the rules can be tested.
 */
public final class TransferQrChange {

    private final Path currentPicture;
    private final String currentText;
    private Path newPicture;

    /** @param currentPicture the picture stored now, or null when there is none yet; @param currentText its holder and bank text */
    public TransferQrChange(Path currentPicture, String currentText) {
        this.currentPicture = currentPicture;
        this.currentText = currentText == null ? "" : currentText;
    }

    /**
     * Takes a picture the administrator picked as the new one, if it can be used (a PNG or JPEG of a sensible size, TODO 2A.17).
     * Otherwise the earlier choice stays and the reason is returned, ready to show.
     *
     * @return null when the picture was taken, otherwise why it was not
     */
    public String choose(Path file) {
        String problem = TransferQrGuard.problemWith(file);
        if (problem != null) {
            return problem;
        }
        newPicture = file;
        return null;
    }

    /** The picture chosen in this sitting (not stored yet), or null. */
    public Path newPicture() {
        return newPicture;
    }

    public boolean hasNewPicture() {
        return newPicture != null;
    }

    /** The picture to show: the new one when there is one, otherwise the stored one, otherwise null. */
    public Path pictureToShow() {
        return newPicture != null ? newPicture : currentPicture;
    }

    /**
     * Whether Save may be pressed: a new picture was chosen, or the text was changed while a picture is stored (a text alone has
     * nothing to belong to when there is no picture). Comparing with the stored text the way it is stored, so a stray space is not a change.
     */
    public boolean canSave(String typedText) {
        if (newPicture != null) return true;
        return currentPicture != null && !TransferQrGuard.cleanInfo(typedText).equals(TransferQrGuard.cleanInfo(currentText));
    }

    /** True when saving would keep the stored picture and change the text only. */
    public boolean isTextOnly() {
        return newPicture == null;
    }

    /**
     * Stores what was chosen in {@code folder}, with the checks, the fingerprint and the log line that {@link TransferQrGuard} writes.
     * Administrator only. Call it off the screen thread.
     *
     * @throws IOException when the picture or the text cannot be stored, or nothing was chosen to store
     */
    public void apply(Path folder, String typedText) throws IOException {
        if (newPicture != null) {
            TransferQrGuard.replace(folder, newPicture, typedText);
        } else {
            TransferQrGuard.replaceInfo(folder, typedText);
        }
    }
}
