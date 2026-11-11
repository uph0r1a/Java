package com.coffeeshop.coffeeshopmanagement.util;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Remembers when the person last touched the app and decides whether they have been away too long (TODO 2.9). No JavaFX
 * here, so it is unit-tested.
 *
 * <p>Time comes from {@link MonotonicClock}, not from the computer's clock (TODO 2A.37): setting the Windows clock forward
 * must not log the person out at once, and setting it back must not keep the till open for ever.
 */
public final class IdleTracker {

    public static final double DEFAULT_MINUTES = 15;
    /** The longest limit that is taken (a day); a longer one is as good as none (TODO 3.79). */
    public static final int MAX_MINUTES = 1440;

    private static final Logger LOGGER = Logger.getLogger(IdleTracker.class.getName());
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();
    /** Digits with an optional fraction: no sign, no exponent, no NaN or Infinity, no trailing d or f that Double.parseDouble would accept. */
    private static final Pattern PLAIN_NUMBER = Pattern.compile("[0-9]{1,6}([.,][0-9]{1,6})?");

    private final LongSupplier clockMillis;
    private volatile long lastActivity;

    public IdleTracker() {
        this(MonotonicClock::millis);
    }

    /** @param clockMillis a clock that only moves forward (a test passes its own) */
    public IdleTracker(LongSupplier clockMillis) {
        this.clockMillis = clockMillis;
        this.lastActivity = clockMillis.getAsLong();
    }

    public void touch() {
        lastActivity = clockMillis.getAsLong();
    }

    public Duration idleFor() {
        long idle = clockMillis.getAsLong() - lastActivity;
        return idle <= 0 ? Duration.ZERO : Duration.ofMillis(idle);
    }

    /**
     * What the timer does at each look (TODO 2.9): true when the person must be locked out now. Nobody signed in, or a limit of 0,
     * only restarts the clock (it starts when someone signs in; 0 means never); an idle person is reported once and the clock
     * restarts, so the next look does not report the same idleness again.
     */
    public boolean checkIdle(boolean signedIn, Duration limit) {
        if (!signedIn || limit == null || limit.isZero()) {
            touch();
            return false;
        }
        if (isIdle(limit)) {
            touch();
            return true;
        }
        return false;
    }

    /** True when the idle time has reached the limit. A zero or negative limit means "never log out". */
    public boolean isIdle(Duration limit) {
        if (limit == null || limit.isZero() || limit.isNegative()) return false;
        return idleFor().compareTo(limit) >= 0;
    }

    /**
     * The limit in force (TODO 4.49): {@code -Dlunavera.idleMinutes} when it is given (the developer's override, as before), else the
     * owner's setting {@code ruleMinutes} from Settings. Zero turns the automatic sign-out off, on purpose; a value that makes no
     * sense (below zero, above {@link #MAX_MINUTES}) is not taken as "off": the {@link #DEFAULT_MINUTES} apply and a warning says so
     * (TODO 3.79). Settings already refuses such a value, so this only matters for a hand-edited database.
     */
    public static Duration limit(String systemSetting, int ruleMinutes) {
        if (systemSetting != null && !systemSetting.isBlank()) return limitFromSetting(systemSetting);
        if (ruleMinutes == 0) {
            warnOnce("Idle auto-lock is switched off on purpose (the setting in Settings is 0): a till left signed in stays open.");
            return Duration.ZERO;
        }
        if (ruleMinutes < 0 || ruleMinutes > MAX_MINUTES) {
            warnOnce("The idle minutes in Settings (" + ruleMinutes + ") are not between 1 and " + MAX_MINUTES + " and not 0: using "
                    + (long) DEFAULT_MINUTES + " minutes.");
            return defaultLimit();
        }
        return Duration.ofMinutes(ruleMinutes);
    }

    /**
     * The limit from a setting such as "15" or "1.5" (minutes, a comma is read as a point). Only a plain number from 1 to
     * {@link #MAX_MINUTES} is taken, or exactly 0 to switch the sign-out off on purpose (logged as a WARNING, so it shows in the
     * support file). Anything else (TODO 3.79: {@code NaN}, {@code -5}, {@code Infinity}, {@code 1e9}, {@code abc}, {@code 0,5}, which
     * is half a minute) was either "off" or a limit of thousands of years, and a till that never locks went unnoticed; now it gives the
     * {@link #DEFAULT_MINUTES} and one WARNING that names the refused value. Missing or blank also gives the default, without a warning.
     */
    public static Duration limitFromSetting(String setting) {
        if (setting == null || setting.isBlank()) return defaultLimit();
        String text = setting.trim();
        if (!PLAIN_NUMBER.matcher(text).matches()) return refused(text);
        double minutes;
        try {
            minutes = Double.parseDouble(text.replace(',', '.'));
        } catch (NumberFormatException e) {
            return refused(text);
        }
        if (minutes == 0) {
            warnOnce("Idle auto-lock is switched off on purpose (" + "lunavera.idleMinutes=" + text + "): a till left signed in stays open.");
            return Duration.ZERO;
        }
        if (minutes < 1 || minutes > MAX_MINUTES) return refused(text);
        return Duration.ofMillis(Math.round(minutes * 60_000));
    }

    private static Duration refused(String text) {
        // The value is shown as typed but cut short: it can come from a shortcut a person edited by hand.
        String shown = text.length() > 40 ? text.substring(0, 40) + "..." : text;
        warnOnce("The idle minutes \"" + shown + "\" are not a number from 1 to " + MAX_MINUTES + " (or 0 to switch the auto-lock off): using "
                + (long) DEFAULT_MINUTES + " minutes.");
        return defaultLimit();
    }

    private static Duration defaultLimit() {
        return Duration.ofMillis(Math.round(DEFAULT_MINUTES * 60_000));
    }

    /** One WARNING per distinct text: the limit is read every few seconds and must not fill the log. */
    private static void warnOnce(String message) {
        if (WARNED.add(message)) LOGGER.warning(message);
    }

    /** For tests: lets the same warning be written again. */
    static void forgetWarnings() {
        WARNED.clear();
    }
}
