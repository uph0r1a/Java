package com.coffeeshop.coffeeshopmanagement.controller;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.Assert.assertTrue;

/**
 * TODO 5.50: a text box in a dialog stops the typing at the limit its rules enforce ({@code TextLengthLimiter.limit(box, ...)}), instead of
 * letting the person type a long text and be told "too long" on Save. Reads the sources, so it needs no JavaFX: every
 * {@code TextField name = new TextField(...)} in the checked dialogs of {@code controller/} must have a {@code limit(name,} call.
 */
public class DialogTextLimitsSourceTest {

    private static final Path CONTROLLERS = Path.of("src", "main", "java", "com", "coffeeshop", "coffeeshopmanagement", "controller");

    /**
     * The dialogs that are checked. Other dialogs have boxes without a limit too (26 found on 2026-11-06: ExpenseDialog, OptionEditDialog,
     * PasswordChangeDialog, PinSetupDialog, PromotionDialog, ReservationDialog, ShiftCloseDialog, StockLogDialog, TimeClockCorrectionDialog,
     * TransferQrDialog); add a dialog here when its boxes have been given their limits, until the list is every dialog.
     */
    private static final Set<String> CHECKED = Set.of("IngredientDialog.java", "VoucherDialog.java", "TableManagerDialog.java");

    /** Boxes that need no limit, with the reason (a box that is read-only, a one-off search the DAO bounds, ...). */
    private static final Set<String> EXEMPT = Set.of();

    private static final Pattern BOX = Pattern.compile("(?:TextField|TextArea|PasswordField)\\s+(\\w+)\\s*=\\s*new\\s+(?:TextField|TextArea|PasswordField)\\(");

    @Test
    public void everyTextBoxOfADialogHasALengthLimit() throws IOException {
        List<String> missing = new ArrayList<>();
        try (Stream<Path> files = Files.list(CONTROLLERS)) {
            for (Path file : (Iterable<Path>) files.filter(p -> CHECKED.contains(p.getFileName().toString())).sorted()::iterator) {
                String source = Files.readString(file, StandardCharsets.UTF_8);
                Matcher m = BOX.matcher(source);
                while (m.find()) {
                    String name = m.group(1);
                    String key = file.getFileName() + ":" + name;
                    if (EXEMPT.contains(key)) continue;
                    if (!Pattern.compile("limit\\(\\s*" + name + "\\s*,").matcher(source).find()) missing.add(key);
                }
            }
        }
        assertTrue("These text boxes can be typed past their limit (add TextLengthLimiter.limit(box, MAX) or list the box in EXEMPT with a reason): "
                + missing, missing.isEmpty());
    }
}
