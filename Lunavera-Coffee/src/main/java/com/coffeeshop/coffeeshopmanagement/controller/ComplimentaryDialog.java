package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.model.Employee;
import com.coffeeshop.coffeeshopmanagement.model.OrderItem;
import com.coffeeshop.coffeeshopmanagement.model.Role;
import com.coffeeshop.coffeeshopmanagement.service.CheckoutService;
import com.coffeeshop.coffeeshopmanagement.service.ComplimentaryRules;
import com.coffeeshop.coffeeshopmanagement.service.ComplimentaryService;
import com.coffeeshop.coffeeshopmanagement.service.ComplimentaryRules.Kind;
import com.coffeeshop.coffeeshopmanagement.util.AlertUtil;
import com.coffeeshop.coffeeshopmanagement.util.CurrencyUtil;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.Session;
import com.coffeeshop.coffeeshopmanagement.util.TextLengthLimiter;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.util.List;
import java.util.Optional;

/**
 * The window of the "free drink" button (TODO 4.61, rule D26): who the drinks of this bill are for (a member of staff, the owner, or a tasting)
 * and an optional note (required for a tasting). It answers with a {@link CheckoutService.Payment#free} payment that the till saves like any other;
 * what is allowed is decided by {@link ComplimentaryRules}, which this window only asks, and the database checks the person's limit again when the
 * bill is saved. Enter does not confirm: giving something away is not undone by a stray key.
 */
final class ComplimentaryDialog {

    private ComplimentaryDialog() {
    }

    /**
     * @param staffList the people a free drink can be given to, read by the caller in the background (TODO 6.14): the window is built on the
     *              screen thread and must not wait for the database; an empty list makes the confirm step say to choose a person
     * @return the payment to save, or empty when the person changed their mind
     */
    static Optional<CheckoutService.Payment> ask(List<OrderItem> items, Role role, List<Employee> staffList) {
        final int cups = ComplimentaryRules.cups(items);
        final int limit = ComplimentaryRules.cashierCupsPerDay();
        final boolean admin = ComplimentaryRules.unlimited(role);

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(I18n.t("complimentary.title"));
        AlertUtil.configure(dialog);
        ButtonType confirm = new ButtonType(I18n.t("complimentary.confirm"), ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(confirm, ButtonType.CANCEL);
        AlertUtil.setCancelButton(dialog, ButtonType.CANCEL);

        ToggleGroup group = new ToggleGroup();
        RadioButton staff = radio(Kind.STAFF, group);
        RadioButton owner = radio(Kind.OWNER, group);
        RadioButton tasting = radio(Kind.TASTING, group);
        staff.setSelected(true);
        tasting.setDisable(!admin); // a tasting has no person to count against: administrators only

        ComboBox<Employee> person = new ComboBox<>();
        person.getItems().addAll(staffList); // an empty list: the confirm step then says to choose a person, and the payment itself refuses an unknown one
        person.setConverter(new StringConverter<>() {
            @Override
            public String toString(Employee e) {
                return e == null ? "" : e.getFullName();
            }

            @Override
            public Employee fromString(String text) {
                return null;
            }
        });
        Employee me = Session.getCurrentEmployee();
        if (me != null) person.getItems().stream().filter(e -> e.getId() == me.getId()).findFirst().ifPresent(person::setValue);
        person.setPromptText(I18n.t("complimentary.person.prompt"));
        person.setMaxWidth(Double.MAX_VALUE);
        person.visibleProperty().bind(staff.selectedProperty());
        person.managedProperty().bind(staff.selectedProperty());

        TextField note = new TextField();
        note.setPromptText(I18n.t("complimentary.note.prompt"));
        TextLengthLimiter.limit(note, ComplimentaryRules.NOTE_MAX);

        Label value = new Label(I18n.t("complimentary.bill", cups, CurrencyUtil.format(ComplimentaryRules.listValue(items))));
        value.setWrapText(true);
        Label hint = new Label();
        hint.setWrapText(true);
        hint.setStyle(com.coffeeshop.coffeeshopmanagement.util.TextSize.size(11) + " -fx-text-fill: #5b7e45;");
        Label problem = new Label();
        problem.setStyle("-fx-text-fill: #c62828;");
        problem.setWrapText(true);

        Runnable refreshHint = () -> hint.setText(admin ? I18n.t("complimentary.hint.admin") : hintFor(owner.isSelected(), person.getValue(), cups, limit));
        group.selectedToggleProperty().addListener((o, was, now) -> refreshHint.run());
        person.valueProperty().addListener((o, was, now) -> refreshHint.run());
        refreshHint.run();

        VBox content = new VBox(10, new Label(I18n.t("complimentary.intro")), value, staff, person, owner, tasting,
                new Label(I18n.t("complimentary.note.label")), note, hint, problem);
        content.setPadding(new Insets(16));
        content.setPrefWidth(460);
        dialog.getDialogPane().setContent(content);

        final ComplimentaryRules.Give[] chosen = {null};
        dialog.getDialogPane().lookupButton(confirm).addEventFilter(ActionEvent.ACTION, event -> {
            Kind kind = staff.isSelected() ? Kind.STAFF : owner.isSelected() ? Kind.OWNER : Kind.TASTING;
            Employee who = kind == Kind.STAFF ? person.getValue() : null;
            ComplimentaryRules.Give give = new ComplimentaryRules.Give(kind, who == null ? null : who.getId(), note.getText());
            int given = 0;
            try {
                given = kind == Kind.TASTING ? 0 : new ComplimentaryService().cupsGivenToday(kind, give.forEmployeeId());
            } catch (RuntimeException failed) {
                // the count is a courtesy here; the payment checks it again against the database
            }
            ComplimentaryRules.Problem why = ComplimentaryRules.check(role, give, cups, given, limit);
            if (why != ComplimentaryRules.Problem.OK) {
                problem.setText(ComplimentaryRules.message(why, who != null ? who.getFullName() : I18n.t("complimentary.who.owner"), given, cups, limit));
                event.consume();
                return;
            }
            chosen[0] = give;
        });
        Optional<ButtonType> answer = dialog.showAndWait();
        return answer.isPresent() && answer.get() == confirm && chosen[0] != null
                ? Optional.of(CheckoutService.Payment.free(chosen[0])) : Optional.empty();
    }

    private static RadioButton radio(Kind kind, ToggleGroup group) {
        RadioButton button = new RadioButton(kind.label());
        button.setToggleGroup(group);
        return button;
    }

    /** "Today: 1 of 2 cups already given to Mai; this bill has 1": what a cashier has left, so the refusal is no surprise. */
    private static String hintFor(boolean owner, Employee who, int cups, int limit) {
        if (!owner && who == null) return "";
        try {
            int given = new ComplimentaryService().cupsGivenToday(owner ? Kind.OWNER : Kind.STAFF, owner ? null : who.getId());
            return I18n.t("complimentary.hint", owner ? I18n.t("complimentary.who.owner") : who.getFullName(), given, limit, cups);
        } catch (RuntimeException failed) {
            return "";
        }
    }
}
