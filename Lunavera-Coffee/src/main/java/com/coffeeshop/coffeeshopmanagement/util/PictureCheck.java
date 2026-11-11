package com.coffeeshop.coffeeshopmanagement.util;

import java.io.File;

/** The check that a picked picture really decodes, which needs the JavaFX picture reader; the rest is in {@link ImageStorage} (TODO 6.83). */
public final class PictureCheck {

    private PictureCheck() {
    }

    /**
     * Looks at a picked file before it is copied: not empty, not larger than {@link ImageStorage#MAX_PICTURE_BYTES}, not larger in pixels than
     * {@link ImageStorage#MAX_PICTURE_PIXELS} (read from the file's header, nothing is decoded for this), and the content really decodes as a
     * picture (a renamed text file with a .png name is refused). Returns the problem in the language in use, or null when the
     * picture is fine.
     *
     * <p>Reading and decoding a picture takes time, so call this from a background thread ({@code Async}), not the screen thread.
     */
    public static String pictureProblem(File file) {
        String problem = ImageStorage.sizeProblem(file);
        if (problem != null) return problem;
        try {
            // A small requested size: the picture is only checked to decode, so there is no need to build it at full size.
            javafx.scene.image.Image image = new javafx.scene.image.Image(file.toURI().toString(), 64, 64, true, false);
            if (image.isError() || image.getWidth() <= 0 || image.getHeight() <= 0) {
                return I18n.t("image.invalid");
            }
        } catch (RuntimeException e) {
            return I18n.t("image.invalid");
        }
        return null;
    }
}
