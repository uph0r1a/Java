package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.model.Order;
import com.coffeeshop.coffeeshopmanagement.util.AlertUtil;
import com.coffeeshop.coffeeshopmanagement.util.DataPaths;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.Session;
import com.coffeeshop.coffeeshopmanagement.util.TransferQr;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

import java.util.Optional;

/**
 * The blocks of the payment box that belong to one method (moved out of {@link OrderController}): the bank transfer (QR picture,
 * amount, order code, "money arrived" tick) and the e-wallet. The amount shown is the label given: the whole total, or what is
 * left after a cash part (TODO 4.32).
 */
final class PaymentBoxes {

    private PaymentBoxes() {
    }

    /**
     * The block shown for "Chuyển khoản": the amount to transfer, the shop's QR picture (or a
     * clear note when the picture has not been added - never a made-up code), the order code to
     * use as the transfer note, and the "money received" tick box.
     */
    static VBox transfer(Label dueLabel, CheckBox transferReceivedBox, TextField transferRefField, Order activeOpenOrder) {
        Label title = new Label(I18n.t("pos.qr.amount"));
        Label amount = new Label();
        amount.textProperty().bind(dueLabel.textProperty());
        amount.setStyle("-fx-font-size: 22px; -fx-font-weight: bold;");

        javafx.scene.Node picture;
        Optional<String> imageUrl = TransferQr.findImageUrl();
        javafx.scene.image.Image qrImage = imageUrl.map(u -> new javafx.scene.image.Image(u, 240, 240, true, true)).orElse(null);
        if (qrImage != null && !qrImage.isError()) {
            javafx.scene.image.ImageView view = new javafx.scene.image.ImageView(qrImage);
            view.setFitWidth(240);
            view.setFitHeight(240);
            view.setPreserveRatio(true);
            picture = view;
        } else {
            Label missing = new Label(I18n.t(qrImage != null ? "pos.qr.broken" : "pos.qr.none")
                    + I18n.t("pos.qr.hint", DataPaths.dataDir()));
            missing.setWrapText(true);
            missing.setMaxWidth(320);
            picture = missing;
        }

        VBox box = new VBox(8, title, amount, picture);
        // Who the money goes to, and a warning when the picture is not the one an administrator accepted (TODO 2A.17).
        java.util.Optional<java.nio.file.Path> pictureFile = com.coffeeshop.coffeeshopmanagement.util.TransferQrGuard.dataPicture();
        if (pictureFile.isPresent() && qrImage != null && !qrImage.isError()) {
            var state = com.coffeeshop.coffeeshopmanagement.util.TransferQrGuard.inspect(pictureFile.get());
            String holder = com.coffeeshop.coffeeshopmanagement.util.TransferQrGuard.accountInfo(pictureFile.get());
            if (!holder.isEmpty()) {
                Label who = new Label(I18n.t("pos.qr.holder", holder));
                who.setWrapText(true);
                who.setMaxWidth(320);
                box.getChildren().add(who);
            }
            if (state.needsAttention()) {
                boolean unreadable = state == com.coffeeshop.coffeeshopmanagement.util.TransferQrGuard.State.UNVERIFIABLE;
                Label warn = new Label(I18n.t(unreadable ? "pos.qr.unreadable"
                        : state == com.coffeeshop.coffeeshopmanagement.util.TransferQrGuard.State.UNCONFIRMED
                                ? "pos.qr.unconfirmed" : "pos.qr.changed"));
                warn.setWrapText(true);
                warn.setMaxWidth(320);
                warn.setStyle("-fx-text-fill: #b33939; -fx-font-weight: bold;");
                box.getChildren().add(warn);
                if (Session.isAdmin() && !unreadable) {
                    Button accept = new Button(I18n.t("pos.qr.accept"));
                    accept.setOnAction(e -> {
                        try {
                            com.coffeeshop.coffeeshopmanagement.util.TransferQrGuard.accept(pictureFile.get());
                            box.getChildren().remove(warn);
                            accept.setVisible(false);
                            accept.setManaged(false);
                        } catch (java.io.IOException ex) {
                            AlertUtil.error(I18n.t("pos.qr.accept.fail.title"), I18n.t("pos.qr.accept.fail.message"));
                        }
                    });
                    box.getChildren().add(accept);
                }
            }
        }
        if (activeOpenOrder != null && activeOpenOrder.getId() > 0) {
            Label note = new Label(I18n.t("pos.qr.note", com.coffeeshop.coffeeshopmanagement.util.OrderNumber.transferNote(activeOpenOrder.getId())));
            note.setStyle("-fx-font-weight: bold;");
            box.getChildren().add(note);
        }
        box.getChildren().addAll(new Label(I18n.t("pos.pay.transfer.ref")), transferRefField, transferReceivedBox);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(6, 0, 0, 0));
        return box;
    }

    /**
     * The block shown for an e-wallet (TODO 4.34): the amount the customer pays in the wallet app, the wallet's transaction code
     * (optional, so a disputed payment can be found later) and the "money received" tick. The shop's bank QR picture is not shown:
     * it belongs to the bank account, not to the wallet.
     */
    static VBox wallet(Label dueLabel, CheckBox walletReceivedBox, TextField walletRefField) {
        Label title = new Label(I18n.t("pos.wallet.amount"));
        Label amount = new Label();
        amount.textProperty().bind(dueLabel.textProperty());
        amount.setStyle("-fx-font-size: 22px; -fx-font-weight: bold;");
        VBox box = new VBox(8, title, amount, new Label(I18n.t("pos.pay.wallet.ref")), walletRefField, walletReceivedBox);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(6, 0, 0, 0));
        return box;
    }
}
