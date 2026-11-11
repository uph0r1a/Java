package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.util.AlertUtil;
import com.coffeeshop.coffeeshopmanagement.util.Async;
import com.coffeeshop.coffeeshopmanagement.util.DataPaths;
import com.coffeeshop.coffeeshopmanagement.util.FileSizeText;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.TransferQrChange;
import com.coffeeshop.coffeeshopmanagement.util.TransferQrGuard;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * The administrator's "Transfer QR code" window (TODO 4.26, 2A.17): the picture, and the account holder and bank, side by side.
 * Choosing a picture only shows it here, marked "not saved yet"; nothing is stored, and no line is written to the security log, until
 * Save is pressed. Save then does what {@link TransferQrGuard} does: checks the file, stores it, records its fingerprint and logs who
 * did it. With no new picture, the holder and bank text can be saved alone.
 */
final class TransferQrDialog {

    private static final double PREVIEW = 240;

    private TransferQrDialog() {
    }

    /** Opens the window; the caller has already checked that the person is an administrator. */
    static void show() {
        Optional<Path> current = TransferQrGuard.dataPicture();
        TransferQrChange change = new TransferQrChange(current.orElse(null), current.map(TransferQrGuard::accountInfo).orElse(""));

        Label caption = new Label();
        caption.setStyle("-fx-font-weight: bold;");
        ImageView view = new ImageView();
        view.setFitWidth(PREVIEW);
        view.setFitHeight(PREVIEW);
        view.setPreserveRatio(true);
        Label none = new Label(I18n.t("admin.qr.preview.none"));
        none.setWrapText(true);
        none.setMaxWidth(PREVIEW - 20);
        StackPane frame = new StackPane(view, none);
        frame.setPrefSize(PREVIEW, PREVIEW);
        frame.setMinSize(PREVIEW, PREVIEW);
        frame.setStyle("-fx-border-color: #bdbdbd; -fx-background-color: white;");
        Label fileLabel = new Label();
        fileLabel.setWrapText(true);
        fileLabel.setMaxWidth(PREVIEW);
        Label problem = new Label();
        problem.setWrapText(true);
        problem.setMaxWidth(PREVIEW);
        problem.setStyle("-fx-text-fill: #b71c1c;");
        VBox left = new VBox(8, caption, frame, fileLabel, problem);

        TextArea info = new TextArea(current.map(TransferQrGuard::accountInfo).orElse(""));
        info.setPromptText(I18n.t("admin.qr.holder.prompt"));
        info.setPrefRowCount(4);
        info.setPrefColumnCount(26);
        info.setWrapText(true);
        Label holderLabel = new Label(I18n.t("admin.qr.holder.label"));
        holderLabel.setWrapText(true);
        holderLabel.setMaxWidth(300);
        VBox right = new VBox(8, holderLabel, info);

        HBox content = new HBox(16, left, right);
        content.setAlignment(Pos.TOP_LEFT);
        content.setPadding(new Insets(4));

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(I18n.t("admin.qr.title"));
        dialog.setHeaderText(I18n.t("admin.qr.intro"));
        dialog.getDialogPane().setContent(content);
        // The header text is one long line: without a width it makes the window as wide as the text, wider than a small till screen.
        dialog.getDialogPane().setPrefWidth(640);
        dialog.getDialogPane().setMaxWidth(640);
        ButtonType choose = new ButtonType(I18n.t("common.pickimage"), ButtonData.LEFT);
        ButtonType save = new ButtonType(I18n.t("admin.qr.save"), ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(choose, save, ButtonType.CANCEL);
        AlertUtil.configure(dialog);
        Button chooseButton = (Button) dialog.getDialogPane().lookupButton(choose);
        Node saveButton = dialog.getDialogPane().lookupButton(save);

        Runnable refresh = () -> {
            Path shown = change.pictureToShow();
            boolean fresh = change.hasNewPicture();
            caption.setText(shown == null ? "" : I18n.t(fresh ? "admin.qr.preview.new" : "admin.qr.preview.current")); // with no picture the frame says so
            caption.setStyle("-fx-font-weight: bold;" + (fresh ? " -fx-text-fill: #e65100;" : ""));
            if (shown == null) {
                view.setImage(null);
                none.setVisible(true);
                fileLabel.setText("");
            } else {
                Image image = new Image(shown.toUri().toString(), 0, 0, true, true, false); // read now, so a bad file is seen here
                view.setImage(image.isError() ? null : image);
                none.setVisible(image.isError());
                try {
                    fileLabel.setText(I18n.t("admin.qr.preview.file", shown.getFileName(), FileSizeText.format(Files.size(shown))));
                } catch (java.io.IOException e) {
                    fileLabel.setText(String.valueOf(shown.getFileName()));
                }
            }
            chooseButton.setText(I18n.t(fresh || shown != null ? "admin.qr.chooseagain" : "common.pickimage"));
            saveButton.setDisable(!change.canSave(info.getText()));
        };
        info.textProperty().addListener((o, was, now) -> saveButton.setDisable(!change.canSave(now)));

        // "Choose picture" does not close the window: the picture is checked and shown, and the window stays for Save (or choose again).
        chooseButton.addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            FileChooser chooser = new FileChooser();
            chooser.setTitle(I18n.t("admin.qr.choose"));
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(I18n.t("common.image.filter"), "*.png", "*.jpg", "*.jpeg"));
            File file = chooser.showOpenDialog(dialog.getDialogPane().getScene() != null ? dialog.getDialogPane().getScene().getWindow() : null);
            if (file == null) return; // cancelled: whatever was chosen before stays
            String why = new Image(file.toURI().toString(), 0, 0, true, true, false).isError()
                    ? I18n.t("image.unreadable") : change.choose(file.toPath());
            problem.setText(why == null ? "" : why);
            if (why == null) refresh.run();
        });
        refresh.run();

        if (dialog.showAndWait().orElse(ButtonType.CANCEL) != save) return;
        String typed = info.getText();
        boolean textOnly = change.isTextOnly();
        Path folder = DataPaths.dataDir();
        Async.run(() -> {
                    change.apply(folder, typed);
                    return Boolean.TRUE;
                },
                done -> AlertUtil.info(I18n.t("common.saved"), I18n.t(textOnly ? "admin.qr.textonly.saved" : "admin.qr.saved")),
                error -> AlertUtil.warning(I18n.t("admin.qr.fail"), String.valueOf(error.getMessage())));
    }
}
