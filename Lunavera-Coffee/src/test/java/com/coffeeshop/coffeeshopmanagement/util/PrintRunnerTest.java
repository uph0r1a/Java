package com.coffeeshop.coffeeshopmanagement.util;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/** TODO 2.83: a print job is run to its end, or cancelled, and a failing driver never escapes; the run is fit for a background thread. */
public class PrintRunnerTest {

    private String language;

    @Before
    public void useEnglish() {
        language = I18n.current();
        I18n.setLanguage(I18n.EN);
    }

    @After
    public void putBack() {
        I18n.setLanguage(language);
    }

    /** A job that records what was called and can be made to fail at any step. */
    private static final class FakeJob implements PrintRunner.Job {
        final List<String> calls = new ArrayList<>();
        boolean accepts = true;
        RuntimeException printFails;
        RuntimeException endFails;
        RuntimeException cancelFails;
        Thread ranOn;

        @Override public boolean printPage() {
            calls.add("print");
            ranOn = Thread.currentThread();
            if (printFails != null) throw printFails;
            return accepts;
        }

        @Override public void endJob() {
            calls.add("end");
            if (endFails != null) throw endFails;
        }

        @Override public void cancelJob() {
            calls.add("cancel");
            if (cancelFails != null) throw cancelFails;
        }
    }

    @Test
    public void aPageThatPrintedEndsTheJobAndIsNeverCancelled() {
        FakeJob job = new FakeJob();
        PrintRunner.Outcome outcome = PrintRunner.run(job);
        assertTrue(outcome.printed());
        assertNull(outcome.problem());
        assertEquals(List.of("print", "end"), job.calls);
    }

    @Test
    public void aPageThePrinterRefusedCancelsTheJobAndGivesNoDriverReason() {
        FakeJob job = new FakeJob();
        job.accepts = false;
        PrintRunner.Outcome outcome = PrintRunner.run(job);
        assertFalse(outcome.printed());
        assertNull(outcome.problem());
        assertEquals(List.of("print", "cancel"), job.calls);
    }

    @Test
    public void aDriverThatThrowsCancelsTheJobAndTheReasonIsReturnedNotThrown() {
        FakeJob job = new FakeJob();
        job.printFails = new IllegalStateException("Printer offline");
        PrintRunner.Outcome outcome = PrintRunner.run(job);
        assertFalse(outcome.printed());
        assertNotNull(outcome.problem());
        assertEquals("the reason is what ErrorText lets the screen show", ErrorText.forScreen("Printer offline"), outcome.problem());
        assertEquals(List.of("print", "cancel"), job.calls);
    }

    @Test
    public void aJobThatCannotBeEndedIsCancelledAndNotReportedAsSent() {
        FakeJob job = new FakeJob();
        job.endFails = new RuntimeException("spooler stopped");
        PrintRunner.Outcome outcome = PrintRunner.run(job);
        assertFalse(outcome.printed());
        assertEquals(List.of("print", "end", "cancel"), job.calls);
        assertEquals(ErrorText.forScreen("spooler stopped"), outcome.problem());
    }

    @Test
    public void aCancelThatFailsToo_StillGivesTheOutcome() {
        FakeJob job = new FakeJob();
        job.printFails = new RuntimeException("timed out");
        job.cancelFails = new RuntimeException("cannot cancel");
        PrintRunner.Outcome outcome = PrintRunner.run(job);
        assertFalse(outcome.printed());
        assertEquals(ErrorText.forScreen("timed out"), outcome.problem());
    }

    @Test
    public void aDriverMessageWithoutTextGivesNoReasonButStillCancels() {
        FakeJob job = new FakeJob();
        job.printFails = new NullPointerException();
        PrintRunner.Outcome outcome = PrintRunner.run(job);
        assertFalse(outcome.printed());
        assertEquals(List.of("print", "cancel"), job.calls);
    }

    @Test
    public void itRunsOnAnyThreadSoTheTillCanBeFreeWhileThePrinterAnswers() throws Exception {
        FakeJob job = new FakeJob();
        Thread caller = Thread.currentThread();
        CompletableFuture<PrintRunner.Outcome> later = CompletableFuture.supplyAsync(() -> PrintRunner.run(job));
        assertTrue(later.get(5, TimeUnit.SECONDS).printed());
        assertNotSame("it ran on another thread, not on the caller's", caller, job.ranOn);
    }
}
