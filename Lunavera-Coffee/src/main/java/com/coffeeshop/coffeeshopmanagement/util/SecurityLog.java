package com.coffeeshop.coffeeshopmanagement.util;

import java.util.logging.Logger;

/**
 * One place for security-relevant events (TODO 2A.9): failed logins, lockouts, account changes, password changes.
 * Lines go to the normal log file with a "SECURITY" prefix and who did it. Never a password, never a hash.
 */
public final class SecurityLog {

    private static final Logger LOGGER = Logger.getLogger("lunavera.security");

    private SecurityLog() {}

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

    /** A typed user name is kept short and free of line breaks so it cannot forge extra log lines. */
    public static String clean(String typed) {
        if (typed == null) return "";
        String one = typed.replaceAll("[\\r\\n\\t]", " ").trim();
        return one.length() > 40 ? one.substring(0, 40) + "..." : one;
    }
}
