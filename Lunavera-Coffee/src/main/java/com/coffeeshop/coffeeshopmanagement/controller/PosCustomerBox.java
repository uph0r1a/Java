package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.service.CustomerService;
import com.coffeeshop.coffeeshopmanagement.dao.OrderDAO;
import com.coffeeshop.coffeeshopmanagement.model.Customer;
import com.coffeeshop.coffeeshopmanagement.service.AttachedCustomerRule;
import com.coffeeshop.coffeeshopmanagement.service.BirthdayRules;
import com.coffeeshop.coffeeshopmanagement.service.OrderService;
import com.coffeeshop.coffeeshopmanagement.util.AlertUtil;
import com.coffeeshop.coffeeshopmanagement.util.AppClock;
import com.coffeeshop.coffeeshopmanagement.util.Async;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.TextLengthLimiter;
import com.coffeeshop.coffeeshopmanagement.util.ValidationUtil;
import javafx.animation.PauseTransition;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.Pane;
import javafx.util.Duration;

import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The customer part of the sales screen: the name and phone boxes, the line that says who the number belongs to, and (TODO 4.56) the
 * button "Gọi lại đơn gần nhất" that puts the lines of a known customer's last paid order into the cart. It was part of
 * {@code OrderController} (TODO 6.5 keeps that class from growing); the controller still owns which customer is attached to the
 * order, and this class reads and sets it through the two functions it is given.
 */
final class PosCustomerBox {

    private static final Logger LOGGER = Logger.getLogger(PosCustomerBox.class.getName());

    /** How long the phone box must be quiet before the number is looked up (TODO 5.29). */
    private static final double PHONE_LOOKUP_DELAY_MS = 250;

    private final PauseTransition phoneLookupDelay = new PauseTransition(Duration.millis(PHONE_LOOKUP_DELAY_MS));

    private final TextField nameField;
    private final TextField phoneField;
    private final Label infoLabel;
    private final CustomerService customers;
    private final OrderService orders;
    private final Supplier<Customer> attached;
    private final Consumer<Customer> attach;
    private final Runnable changed;
    private final Consumer<OrderDAO.LastOrder> repeat;
    private Button repeatButton;

    /**
     * @param attached the customer the order on screen is attached to, or null
     * @param attach   attaches a customer to the order on screen (null detaches)
     * @param changed  saves the order after the customer changed
     * @param repeat   receives the last paid order of the attached customer when the button is pressed
     */
    PosCustomerBox(TextField nameField, TextField phoneField, Label infoLabel, CustomerService customers, OrderService orders,
                   Supplier<Customer> attached, Consumer<Customer> attach, Runnable changed, Consumer<OrderDAO.LastOrder> repeat) {
        this.nameField = nameField;
        this.phoneField = phoneField;
        this.infoLabel = infoLabel;
        this.customers = customers;
        this.orders = orders;
        this.attached = attached;
        this.attach = attach;
        this.changed = changed;
        this.repeat = repeat;
        installRepeatButton();
        installInputs();
    }

    /** The button goes under the line that names the customer, and is only there while a known customer is attached. */
    private void installRepeatButton() {
        if (infoLabel == null || !(infoLabel.getParent() instanceof Pane box)) return;
        repeatButton = new Button(I18n.t("pos.repeat.button"));
        repeatButton.setVisible(false);
        repeatButton.setManaged(false);
        repeatButton.setOnAction(e -> pressRepeat());
        box.getChildren().add(box.getChildren().indexOf(infoLabel) + 1, repeatButton);
    }

    private void pressRepeat() {
        Customer customer = attached.get();
        if (customer == null) return;
        repeatButton.setDisable(true);
        Async.run(
                () -> orders.lastOrderOf(customer.getId()),
                last -> {
                    repeatButton.setDisable(false);
                    if (last.isEmpty() || last.get().items().isEmpty()) {
                        AlertUtil.info(I18n.t("pos.repeat.title"), I18n.t("pos.repeat.none", customer.getFullName()));
                    } else {
                        repeat.accept(last.get());
                    }
                },
                err -> {
                    repeatButton.setDisable(false);
                    LOGGER.log(Level.WARNING, "Loading the last order failed", err);
                    AlertUtil.error(I18n.t("pos.repeat.title"), I18n.t("pos.repeat.failed"));
                });
    }

    private void installInputs() {
        TextLengthLimiter.limit(nameField, TextLengthLimiter.NAME_MAX);
        TextLengthLimiter.limit(phoneField, 15);
        if (phoneField != null) {
            phoneField.textProperty().addListener((obs, oldVal, newVal) -> {
                String typed = newVal == null ? "" : newVal.trim();
                // Editing the phone away from the attached customer detaches them, so the order can never be paid under the
                // previous person's loyalty account. Also for a customer saved without a phone (TODO 2.70).
                if (attached.get() != null && !AttachedCustomerRule.stillMatches(attached.get(), nameField != null ? nameField.getText() : "", typed)) {
                    attach.accept(null);
                    changed.run();
                }
                // Look up only a complete, valid number and only once the typing has paused (TODO 5.29): "+84 901 234 567" typed
                // or pasted used to start up to seven lookups, each its own thread and, on a MariaDB server, its own round trip.
                phoneLookupDelay.stop();
                if (attached.get() == null) {
                    show(null, false);
                }
                if (ValidationUtil.isValidPhone(typed)) {
                    phoneLookupDelay.setOnFinished(e -> {
                        String current = phoneField.getText() == null ? "" : phoneField.getText().trim();
                        if (ValidationUtil.isValidPhone(current)) {
                            lookupByPhone(current);
                        }
                    });
                    phoneLookupDelay.playFromStart();
                }
            });
        }
        if (nameField != null) {
            nameField.textProperty().addListener((obs, oldVal, newVal) -> {
                // Editing the name away from the attached customer detaches them as well (TODO 2.70): the till never renames a
                // customer, so a different name means a different person. The phone, if it is a known number, finds them again.
                if (attached.get() != null && !AttachedCustomerRule.stillMatches(attached.get(), newVal, phoneField != null ? phoneField.getText() : "")) {
                    attach.accept(null);
                    changed.run();
                    String phone = phoneField != null && phoneField.getText() != null ? phoneField.getText().trim() : "";
                    if (ValidationUtil.isValidPhone(phone)) {
                        lookupByPhone(phone);
                    } else {
                        show(null, false);
                    }
                }
            });
            nameField.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
                if (!isNowFocused) {
                    customerInfoChanged();
                }
            });
        }
    }

    private void lookupByPhone(String phone) {
        Async.run(
                () -> customers.findByPhone(phone),
                optCust -> {
                    // Lookups finish in any order: ignore one for a number that is no longer typed.
                    if (phoneField == null || !phone.equals(phoneField.getText() == null ? "" : phoneField.getText().trim())) {
                        return;
                    }
                    if (optCust.isPresent()) {
                        attach.accept(optCust.get());
                        // The stored name is the source of truth: the POS never renames a customer.
                        if (nameField != null && !optCust.get().getFullName().equals(nameField.getText())) {
                            nameField.setText(optCust.get().getFullName());
                        }
                        show(optCust.get(), false);
                        changed.run();
                    } else {
                        show(null, ValidationUtil.isValidPhone(phone));
                    }
                },
                err -> LOGGER.log(Level.WARNING, "Customer lookup by phone failed", err)
        );
    }

    /** The line under the phone field: who the typed number belongs to, so the cashier sees the match; and the repeat button. */
    void show(Customer customer, boolean newCustomer) {
        if (repeatButton != null) {
            repeatButton.setVisible(customer != null);
            repeatButton.setManaged(customer != null);
        }
        if (infoLabel == null) return;
        String text = customer != null
                ? I18n.t("pos.customer.known", customer.getFullName(), customer.getLoyaltyPoints()) + BirthdayRules.badge(customer, AppClock.today())
                : newCustomer ? I18n.t("pos.customer.new") : "";
        infoLabel.setText(text);
        infoLabel.setVisible(!text.isEmpty());
        infoLabel.setManaged(!text.isEmpty());
    }

    private void customerInfoChanged() {
        String name = nameField != null && nameField.getText() != null ? nameField.getText().trim() : "";
        String phone = phoneField != null && phoneField.getText() != null ? phoneField.getText().trim() : "";
        if (name.isEmpty() && phone.isEmpty()) return;
        // Only LOOK for an existing customer here. A new one is created when the order is started or paid, so pressing
        // Tab after a mistyped number no longer leaves a permanent customer behind (TODO 3.29).
        if (ValidationUtil.isValidPhone(phone)) {
            lookupByPhone(phone);
        } else if (phone.isEmpty() && infoLabel != null) {
            // A name without a phone is a walk-in: say so, so staff know no points are given (TODO 3.9).
            infoLabel.setText(I18n.t("pos.customer.nophone"));
            infoLabel.setVisible(true);
            infoLabel.setManaged(true);
        }
    }
}
