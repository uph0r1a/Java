package com.coffeeshop.coffeeshopmanagement.util;

import java.nio.file.Path;
import java.time.Clock;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * One place for security-relevant events (TODO 2A.9): failed logins, lockouts, account changes, password changes.
 * Lines go to the normal log with a "SECURITY" prefix and who did it, and (since TODO 2A.25) also to their own file,
 * {@code data/logs/security-YYYY-MM.log}, which nothing else writes to and which is kept for two years. Never a password,
 * never a hash.
 *
 * <p>Events that can repeat without limit (a wrong password, a blocked login, a refused action) go through
 * {@link #warnLimited}: one line per kind per minute, with a count of what was left out.
 */
public final class SecurityLog {

    private static final Logger LOGGER = Logger.getLogger("lunavera.security");
    private static volatile LogRateLimiter limiter = LogRateLimiter.perMinute();
    private static SecurityLogFile fileHandler;

    private SecurityLog() {}

    /**
     * Starts writing the security events to {@code logsDir/security-YYYY-MM.log}. Safe to call more than once (the second call
     * does nothing). A failure is logged and the events still reach the normal log.
     */
    public static synchronized void install(Path logsDir) {
        if (fileHandler != null) {
            return;
        }
        try {
            SecurityLogFile handler = new SecurityLogFile(logsDir, Clock.systemDefaultZone());
            LOGGER.addHandler(handler);
            fileHandler = handler;
        } catch (RuntimeException e) {
            Logger.getLogger(SecurityLog.class.getName())
                    .log(Level.WARNING, "Could not set up the security log file; events go to the normal log only", e);
        }
    }

    /** Forgets what the rate limit has counted, so a test does not depend on what an earlier test wrote. Not for production code. */
    public static void resetLimitsForTests() {
        limiter = LogRateLimiter.perMinute();
    }

    /** "admin (id 1)" for the signed-in person, or "nobody signed in". */
    public static String actor() {
        var user = Session.getCurrentUser();
        return user == null ? "nobody signed in" : user.getUsername() + " (id " + user.getId() + ")";
    }

    public static void event(String text) {
        LOGGER.info("SECURITY: " + text);
    }

    public static void warn(String text) {
        LOGGER.warning("SECURITY: " + text);
    }

    /**
     * A warning that may repeat: written at most once a minute for the same {@code key} (and 30 a minute in all); the line that
     * follows a quiet spell says how many were left out. Use a key that names the kind of event and who it is about, for
     * example {@code "failed:" + name}.
     */
    public static void warnLimited(String key, String text) {
        String note = limiter.admit(key == null ? "" : key.toLowerCase(Locale.ROOT));
        if (note != null) {
            warn(text + note);
        }
    }

    /**
     * A text typed into the user-name box that is not (known to be) an account, shaped for the log (TODO 2A.39). People
     * sometimes type their password there, so the log never gets the whole text: only how long it was and, for a text of
     * five or more characters, its first two characters ({@code 'Qu...' (14 characters)}).
     */
    public static String redactTyped(String typed) {
        if (typed == null) return "(nothing) (0 characters)";
        String text = typed.trim();
        int length = text.codePointCount(0, text.length());
        if (length < 5) return "(" + length + " characters)";
        String head = text.substring(0, text.offsetByCodePoints(0, 2));
        return "'" + head.replaceAll("[\\p{Cntrl}\\p{Zl}\\p{Zp}]", " ") + "...' (" + length + " characters)";
    }

    /**
     * A typed user name is kept short and free of anything that starts a new line or acts on a terminal, so it cannot forge or
     * hide log lines (TODO 2A.41): every control character (including the vertical tab, form feed, ESC and U+0085) and every
     * Unicode line or paragraph separator (U+2028, U+2029) becomes a space.
     */
    public static String clean(String typed) {
        if (typed == null) return "";
        String one = typed.replaceAll("[\\p{Cntrl}\\p{Zl}\\p{Zp}\\x{80}-\\x{9F}]", " ").trim();
        return one.length() > 40 ? one.substring(0, 40) + "..." : one;
    }
}
