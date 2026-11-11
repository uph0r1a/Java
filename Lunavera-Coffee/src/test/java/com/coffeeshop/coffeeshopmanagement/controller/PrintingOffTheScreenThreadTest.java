package com.coffeeshop.coffeeshopmanagement.controller;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertTrue;

/**
 * TODO 2.83: nothing in a screen calls {@code printPage} itself: it goes through {@code PrintRunner}, which handles a failing driver
 * and the cancel, and {@code HoaDonController.handlePrint} hands it to {@code Async.run} for a page that is not in a window.
 * Since TODO 5.39 the one job wrapper that calls {@code printPage} is in {@code ReceiptPrinterJob} (it was in {@code HoaDonController}).
 * Reads the source files, like {@code ControllerSizeTest}.
 */
public class PrintingOffTheScreenThreadTest {

    private static final Path CONTROLLERS = Paths.get("src/main/java/com/coffeeshop/coffeeshopmanagement/controller");

    @Test
    public void onlyTheJobWrapperOfTheReceiptPrinterCallsPrintPage() throws IOException {
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.list(CONTROLLERS)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).collect(Collectors.toList())) {
                String name = file.getFileName().toString();
                String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
                int calls = text.split("\\.printPage\\(", -1).length - 1;
                int allowed = name.equals("ReceiptPrinterJob.java") ? 1 : 0;
                if (calls != allowed) offenders.add(name + " calls printPage " + calls + " times");
            }
        }
        assertTrue("a screen prints through PrintRunner: " + offenders, offenders.isEmpty());
    }

    @Test
    public void theReceiptIsPrintedOnABackgroundThreadWhenItIsNotInAWindow() throws IOException {
        String text = new String(Files.readAllBytes(CONTROLLERS.resolve("HoaDonController.java")), StandardCharsets.UTF_8);
        assertTrue(text.contains("PrintRunner.run(ReceiptPrinterJob.jobOf(job, page))"));
        int async = text.indexOf("Async.run(() -> PrintRunner.run(ReceiptPrinterJob.jobOf(job, page))");
        assertTrue("the print goes to Async.run", async > 0);
        assertTrue("the dialog stays on the screen thread, before it", text.indexOf("showPrintDialog") < async);
    }
}
