package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.util.AlertUtil;
import com.coffeeshop.coffeeshopmanagement.util.HelpPages;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.TextArea;

/**
 * Shows a staff guide (TODO 6.71) in the language of the screen, as plain text in a read-only box that wraps and scrolls. The guides
 * are Markdown but written to read well without being rendered. A page that cannot be read gives a short message instead of an empty window.
 */
final class HelpDialog {

    private HelpDialog() {
    }

    /** {@code audience} is {@link HelpPages#OWNER} or {@link HelpPages#CASHIER}. */
    static void show(String audience) {
        String text = HelpPages.load(audience, I18n.current());
        if (text == null) {
            AlertUtil.error(I18n.t("help.title"), I18n.t("help.missing"));
            return;
        }
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(I18n.t("help.title"));
        dialog.setHeaderText(null);
        AlertUtil.configure(dialog);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        TextArea area = new TextArea(text);
        area.setEditable(false);
        area.setWrapText(true);
        area.setPrefColumnCount(70);
        area.setPrefRowCount(26);
        dialog.getDialogPane().setContent(area);
        dialog.showAndWait();
    }
}
