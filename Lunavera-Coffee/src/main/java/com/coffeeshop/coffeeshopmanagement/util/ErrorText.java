package com.coffeeshop.coffeeshopmanagement.util;

import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Error boxes must not show database-driver text, class names or file paths (TODO 2A.16): on a shared till that tells a
 * curious person where the data lives. The English "Failed to ..." messages of the data layer (TODO 5.15) are treated the same way:
 * the cashier sees one short Vietnamese sentence and the original goes to the log. A message that looks technical is replaced by a short Vietnamese one and the
 * original goes to the log; the app's own Vietnamese messages are shown as they are.
 */
public final class ErrorText {

    public static final String GENERIC = "Đã xảy ra lỗi. Hãy thử lại; nếu lỗi vẫn còn, báo người phụ trách. "
            + "Chi tiết đã được ghi vào nhật ký.";

    private static final Logger LOGGER = Logger.getLogger(ErrorText.class.getName());

    private static final Pattern TECHNICAL = Pattern.compile(
            "^\\s*(Failed|Unable|Cannot|Could not|Error)\\b|\\[SQLITE_|SQLException|org\\.sqlite|java\\.[a-z]+\\.|[A-Za-z]+Exception|\\bat [a-z]+\\.[a-z]+"
                    + "|[A-Za-z]:\\\\|\\\\Users\\\\|/home/|/Users/|/tmp/|/var/|/mnt/");

    private ErrorText() {}

    public static boolean looksTechnical(String message) {
        return message != null && TECHNICAL.matcher(message).find();
    }

    /** What to show: the message itself when it is plain, else the generic text (the original is logged). */
    public static String forScreen(String message) {
        if (!looksTechnical(message)) return message;
        LOGGER.log(Level.WARNING, "Technical error text hidden from the screen: {0}", message);
        return GENERIC;
    }
}
