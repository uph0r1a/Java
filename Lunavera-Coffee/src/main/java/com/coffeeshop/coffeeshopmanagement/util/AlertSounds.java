package com.coffeeshop.coffeeshopmanagement.util;

/**
 * The optional sounds for what a cashier must not miss (TODO 5.47): a low tone when a scanned code matches no product or a payment
 * is refused, a soft chime when a booking is due. The sounds are OFF until somebody switches them on in Settings, and the choice is per
 * computer (a speaker belongs to one till), kept in {@code ui-settings.properties} next to the language and the text size.
 *
 * <p>This class names no JavaFX class, so what it decides (which file belongs to which event, whether sounds are on) is tested without
 * JavaFX; {@link AlertPlayer} does the playing.
 */
public final class AlertSounds {

    /** The key in {@code ui-settings.properties}. */
    static final String KEY = "alertsounds";

    /** An event that makes a sound, and the bundled file ({@code src/main/resources/sounds/}) it plays. */
    public enum Cue {
        /** A scanned code that no product has, or that belongs to a product that is stopped. */
        CODE_NOT_FOUND("/sounds/low-tone.wav"),
        /** A payment that could not be saved. */
        PAYMENT_REFUSED("/sounds/low-tone.wav"),
        /** A booking that is due (shown once, TODO 4.43). */
        BOOKING_DUE("/sounds/chime.wav");

        private final String resource;

        Cue(String resource) {
            this.resource = resource;
        }

        /** The classpath name of the sound file, starting with a slash. */
        public String resource() {
            return resource;
        }
    }

    private AlertSounds() {
    }

    /** True when this computer plays the sounds. Off by default, and off when the settings file cannot be read. */
    public static boolean isOn() {
        try {
            return UiSettings.getBoolean(KEY, false);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Remembers the choice; true when it changed. */
    public static boolean choose(boolean on) {
        if (on == isOn()) return false;
        UiSettings.putBoolean(KEY, on);
        return true;
    }
}
