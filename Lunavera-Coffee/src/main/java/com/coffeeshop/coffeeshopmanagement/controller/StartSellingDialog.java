package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.service.StartSellingRules;
import com.coffeeshop.coffeeshopmanagement.service.StartSellingService;
import com.coffeeshop.coffeeshopmanagement.util.AlertUtil;
import com.coffeeshop.coffeeshopmanagement.util.Async;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.TextLengthLimiter;
import javafx.scene.Node;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

import java.util.Map;

/**
 * "Start selling for real" (TODO 4.57), opened from the owner's Settings window. It shows what would be removed (counted when it
 * opens), asks the owner to type the shop's name, and only then lets the button work. The backup and the clearing are done by
 * {@link StartSellingService} off the screen thread; the backup comes first and cannot be skipped from here.
 */
final class StartSellingDialog {

    private StartSellingDialog() {
    }

    /** {@code onDone} runs on the screen thread after the data was cleared (the home page reloads its tables and numbers). */
    static void show(Runnable onDone) {
        Async.run(() -> StartSellingService.preview(true),
                counts -> open(counts, onDone),
                error -> AlertUtil.error(I18n.t("startselling.title"), I18n.t("startselling.fail")));
    }

    private static void open(Map<String, Integer> counts, Runnable onDone) {
        int orders = counts.getOrDefault("orders", 0);
        int customers = counts.getOrDefault(StartSellingRules.CUSTOMERS, 0);
        int others = counts.entrySet().stream().filter(e -> !e.getKey().equals("orders") && !e.getKey().equals(StartSellingRules.CUSTOMERS))
                .mapToInt(Map.Entry::getValue).sum();

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(I18n.t("startselling.title"));
        dialog.setHeaderText(I18n.t("startselling.header"));
        AlertUtil.configure(dialog);
        ButtonType start = new ButtonType(I18n.t("startselling.button"), ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(start, ButtonType.CANCEL);
        AlertUtil.setDefaultButton(dialog, ButtonType.CANCEL); // Enter must never start a wipe

        CheckBox withCustomers = new CheckBox(I18n.t("startselling.customers.check", customers));
        TextField typed = new TextField();
        TextLengthLimiter.limit(typed, 120);
        typed.setPromptText(StartSellingRules.expectedName());
        VBox box = new VBox(10,
                wrapped(I18n.t("startselling.removes", orders, others)),
                wrapped(I18n.t("startselling.keeps")),
                withCustomers,
                wrapped(I18n.t("startselling.backup")),
                wrapped(I18n.t("startselling.tills")),
                wrapped(I18n.t("startselling.type", StartSellingRules.expectedName())),
                typed);
        com.coffeeshop.coffeeshopmanagement.util.FitFix.wrapLabels(box);
        dialog.getDialogPane().setContent(box);

        Node startButton = dialog.getDialogPane().lookupButton(start);
        startButton.setDisable(true);
        if (startButton instanceof Button button) button.setDefaultButton(false); // an OK-type button is the default one unless told otherwise
        typed.textProperty().addListener((o, was, now) ->
                startButton.setDisable(!StartSellingRules.nameMatches(now, StartSellingRules.expectedName())));

        if (dialog.showAndWait().orElse(ButtonType.CANCEL) != start) return;
        String name = typed.getText();
        boolean alsoCustomers = withCustomers.isSelected();
        Async.run(() -> StartSellingService.start(name, alsoCustomers),
                result -> {
                    AlertUtil.info(I18n.t("startselling.title"),
                            I18n.t("startselling.done", result.backupName(), result.removed().getOrDefault("orders", 0)));
                    if (onDone != null) onDone.run();
                },
                error -> AlertUtil.error(I18n.t("startselling.title"), String.valueOf(error.getMessage())));
    }

    private static Label wrapped(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setMaxWidth(460);
        return label;
    }
}
