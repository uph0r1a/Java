package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.model.Customer;
import com.coffeeshop.coffeeshopmanagement.service.CustomerService;
import com.coffeeshop.coffeeshopmanagement.util.AlertUtil;
import com.coffeeshop.coffeeshopmanagement.util.Async;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.TextLengthLimiter;

import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * "Gắn khách hàng" at the till (TODO 3.52): a search box over a short list, instead of a drop-down holding every customer. The
 * search runs in the database ({@link CustomerService#search}), on a background thread, after a short pause in typing, and shows at
 * most {@link CustomerService#PICKER_LIMIT} customers; the line under the list says when there are more and asks for more letters.
 * The phone box of the sales screen stays the quick way for a customer whose number is known.
 */
public final class CustomerPickerDialog {

    private static final Logger LOGGER = Logger.getLogger(CustomerPickerDialog.class.getName());
    private static final double SEARCH_DELAY_MS = 250;

    private CustomerPickerDialog() {}

    /** Shows the box. {@code current} (may be null) is selected when it is in the first list. Empty when the person cancelled. */
    public static Optional<Customer> choose(Customer current) {
        return choose(current, new CustomerService());
    }

    static Optional<Customer> choose(Customer current, CustomerService service) {
        return choose(current, service, I18n.t("pos.attach.title"), I18n.t("pos.attach.content"));
    }

    /** The same box with its own title and sentence, for choosing the customer to keep on the customer screen (TODO 4.8). */
    public static Optional<Customer> choose(String title, String intro) {
        return choose(null, new CustomerService(), title, intro);
    }

    private static Optional<Customer> choose(Customer current, CustomerService service, String title, String introText) {
        Dialog<Customer> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText(null);
        AlertUtil.configure(dialog);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Label intro = new Label(introText);
        intro.setWrapText(true);
        intro.setMaxWidth(420);
        TextField search = new TextField();
        search.setPromptText(I18n.t("pos.attach.search.prompt"));
        TextLengthLimiter.limit(search, TextLengthLimiter.NAME_MAX);
        ListView<Customer> list = new ListView<>();
        list.setPrefSize(420, 260);
        Label emptyText = new Label();
        list.setPlaceholder(emptyText);
        Label footer = new Label();
        footer.setWrapText(true);
        footer.setMaxWidth(420);
        footer.setVisible(false);
        footer.setManaged(false);
        VBox content = new VBox(10, intro, search, list, footer);
        content.setPadding(new Insets(10));
        dialog.getDialogPane().setContent(content);

        Button ok = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        ok.disableProperty().bind(list.getSelectionModel().selectedItemProperty().isNull());
        dialog.setResultConverter(type -> type == ButtonType.OK ? list.getSelectionModel().getSelectedItem() : null);

        int[] generation = {0};      // answers arrive in any order: only the newest question counts
        boolean[] pending = {false}; // true from a key press until its answer is shown, so Enter never picks from a stale list
        boolean[] firstAnswer = {true};
        Runnable ask = () -> {
            int mine = ++generation[0];
            String typed = search.getText() == null ? "" : search.getText();
            Async.run(
                    () -> service.search(typed),
                    matches -> {
                        if (mine != generation[0]) return;
                        pending[0] = false;
                        Customer keep = firstAnswer[0] ? current : list.getSelectionModel().getSelectedItem();
                        firstAnswer[0] = false;
                        list.getItems().setAll(matches.shown());
                        if (keep != null) {
                            for (Customer customer : list.getItems()) {
                                if (customer.getId() == keep.getId()) {
                                    list.getSelectionModel().select(customer);
                                    break;
                                }
                            }
                        }
                        emptyText.setText(typed.isBlank() ? I18n.t("pos.nocustomers.message") : I18n.t("pos.attach.none"));
                        footer.setText(matches.more() ? I18n.t("pos.attach.more", CustomerService.PICKER_LIMIT) : "");
                        footer.setVisible(matches.more());
                        footer.setManaged(matches.more());
                    },
                    error -> {
                        if (mine != generation[0]) return;
                        pending[0] = false;
                        LOGGER.log(Level.WARNING, "The customer search of the attach box failed", error);
                        list.getItems().clear();
                        emptyText.setText(I18n.t("pos.attach.failed"));
                    });
        };

        PauseTransition pause = new PauseTransition(Duration.millis(SEARCH_DELAY_MS));
        pause.setOnFinished(event -> ask.run());
        search.textProperty().addListener((observable, before, after) -> {
            pending[0] = true;
            pause.playFromStart();
        });
        search.setOnAction(event -> { // Enter: attach the selected customer, or the first one of the list
            if (pending[0] || list.getItems().isEmpty()) return;
            if (list.getSelectionModel().isEmpty()) list.getSelectionModel().selectFirst();
            ok.fire();
        });
        list.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && list.getSelectionModel().getSelectedItem() != null) ok.fire();
        });
        dialog.setOnShown(event -> search.requestFocus());

        ask.run();
        return dialog.showAndWait();
    }
}
