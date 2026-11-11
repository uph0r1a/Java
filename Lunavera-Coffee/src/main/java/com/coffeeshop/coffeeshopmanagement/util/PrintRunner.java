package com.coffeeshop.coffeeshopmanagement.util;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Runs one print job to its end and says how it went (TODO 2.83), without touching a screen, so it can run on a background thread
 * while the till stays usable (a network roll printer that is slow or off used to freeze the screen until it answered).
 *
 * <p>The three calls of a {@code PrinterJob} are passed in as a {@link Job}: the page is printed; when that worked the job is ended; when
 * it did not, or the driver threw, the job is cancelled so no entry is left in the printer's queue. A {@code RuntimeException} from the
 * driver never escapes: it becomes the short reason in the {@link Outcome}.
 */
public final class PrintRunner {

    private static final Logger LOGGER = Logger.getLogger(PrintRunner.class.getName());

    private PrintRunner() {
    }

    /** The three steps of a print job (a {@code javafx.print.PrinterJob}, in the program; a fake in the tests). */
    public interface Job {
        /** @return false when the printer refused the page; may throw a RuntimeException when the driver fails */
        boolean printPage();

        void endJob();

        void cancelJob();
    }

    /** @param printed true when the page was sent; @param problem a short reason fit for the screen when it was not (null when the printer only refused) */
    public record Outcome(boolean printed, String problem) {
    }

    /** Prints the page and ends the job, or cancels it; never throws. Call it off the screen thread. */
    public static Outcome run(Job job) {
        boolean printed;
        String problem = null;
        try {
            printed = job.printPage();
        } catch (RuntimeException e) {
            // A printer that went offline, a network printer that timed out.
            LOGGER.log(Level.WARNING, "The printer driver failed", e);
            printed = false;
            problem = ErrorText.forScreen(e.getMessage());
        }
        if (printed) {
            try {
                job.endJob();
                return new Outcome(true, null);
            } catch (RuntimeException e) {
                LOGGER.log(Level.WARNING, "The printer did not accept the end of the job", e);
                problem = ErrorText.forScreen(e.getMessage());
            }
        }
        try {
            job.cancelJob(); // a failed page must not leave the job (and the printer queue entry) open (TODO 2.14)
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, "Could not cancel the print job", e);
        }
        return new Outcome(false, problem);
    }
}
