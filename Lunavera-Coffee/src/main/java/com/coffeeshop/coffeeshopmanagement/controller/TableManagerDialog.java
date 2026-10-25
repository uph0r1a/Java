package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.dao.TableDAO;
import com.coffeeshop.coffeeshopmanagement.model.DiningTable;
import com.coffeeshop.coffeeshopmanagement.service.TableRules;
import com.coffeeshop.coffeeshopmanagement.util.AlertUtil;
import com.coffeeshop.coffeeshopmanagement.util.Async;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.SecurityLog;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.stream.Collectors;

/**
 * The owner's screen for the tables (TODO 4.18): add a table, rename it, change its seats, remove it. A table that has an open
 * order (or is marked "in use" by hand) cannot be removed; the database decides that in one statement ({@link TableDAO#remove}).
 * The table's NUMBER never changes, because every order keeps the number of its table.
 */
final class TableManagerDialog {

    private final TableDAO tableDAO = new TableDAO();
    private final TableView<DiningTable> view = new TableView<>();
    private final Runnable onChanged;

    private TableManagerDialog(Runnable onChanged) {
        this.onChanged = onChanged;
    }

    /** Opens the dialog; {@code onChanged} runs after the list was changed, so the table map behind it can reload. */
    static void show(Runnable onChanged) {
        new TableManagerDialog(onChanged).open();
    }

    private void open() {
        TableColumn<DiningTable, String> number = new TableColumn<>(I18n.t("tablemgr.col.number"));
        number.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getTableNumber())));
        number.setPrefWidth(70);
        TableColumn<DiningTable, String> name = new TableColumn<>(I18n.t("tablemgr.col.name"));
        name.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().displayName()));
        name.setPrefWidth(240);
        TableColumn<DiningTable, String> seats = new TableColumn<>(I18n.t("tablemgr.col.capacity"));
        seats.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getCapacity())));
        seats.setPrefWidth(90);
        TableColumn<DiningTable, String> status = new TableColumn<>(I18n.t("tablemgr.col.status"));
        status.setCellValueFactory(c -> new SimpleStringProperty(
                I18n.t(c.getValue().isOccupied() ? "tables.status.occupied" : "tables.status.free")));
        status.setPrefWidth(120);
        view.getColumns().add(number);
        view.getColumns().add(name);
        view.getColumns().add(seats);
        view.getColumns().add(status);
        view.setPlaceholder(new Label(I18n.t("tablemgr.empty")));
        view.setPrefSize(560, 360);

        Button add = new Button(I18n.t("tablemgr.add"));
        add.setOnAction(e -> edit(null));
        Button edit = new Button(I18n.t("tablemgr.edit"));
        edit.setOnAction(e -> edit(view.getSelectionModel().getSelectedItem()));
        Button remove = new Button(I18n.t("tablemgr.remove"));
        remove.setOnAction(e -> remove(view.getSelectionModel().getSelectedItem()));
        edit.disableProperty().bind(view.getSelectionModel().selectedItemProperty().isNull());
        remove.disableProperty().bind(view.getSelectionModel().selectedItemProperty().isNull());
        HBox buttons = new HBox(8, add, edit, remove);

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(I18n.t("tablemgr.title"));
        dialog.setHeaderText(I18n.t("tablemgr.intro"));
        dialog.getDialogPane().setContent(new VBox(10, buttons, view));
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        reload();
        dialog.showAndWait();
    }

    private void reload() {
        Async.run(tableDAO::findAll, tables -> view.setItems(FXCollections.observableArrayList(tables)),
                error -> AlertUtil.warning(I18n.t("tablemgr.title"), String.valueOf(error.getMessage())));
    }

    private void changed() {
        reload();
        if (onChanged != null) {
            onChanged.run();
        }
    }

    /** {@code existing} null = a new table. */
    private void edit(DiningTable existing) {
        // every name the other tables answer to: stored and shown, and "Bàn 5" = "Table 5" (the database checks again)
        List<String> others = view.getItems().stream()
                .filter(t -> existing == null || t.getTableNumber() != existing.getTableNumber())
                .flatMap(t -> TableRules.namesOf(t.getTableNumber(), t.getName()).stream())
                .collect(Collectors.toList());
        if (existing == null && view.getItems().size() >= TableRules.MAX_TABLES) {
            AlertUtil.warning(I18n.t("tablemgr.title"), I18n.t("tablemgr.val.max", TableRules.MAX_TABLES));
            return;
        }
        TextField nameField = new TextField(existing == null ? "" : existing.displayName());
        nameField.setPromptText(I18n.t("tablemgr.name.prompt"));
        TextField seatsField = new TextField(existing == null ? "4" : String.valueOf(existing.getCapacity()));
        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(10);
        form.setPadding(new Insets(10));
        form.add(new Label(I18n.t("tablemgr.col.name")), 0, 0);
        form.add(nameField, 1, 0);
        form.add(new Label(I18n.t("tablemgr.col.capacity")), 0, 1);
        form.add(seatsField, 1, 1);
        Label problem = new Label();
        problem.setWrapText(true);
        problem.setStyle("-fx-text-fill: #b3261e;");
        form.add(problem, 0, 2, 2, 1);

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(I18n.t(existing == null ? "tablemgr.add" : "tablemgr.edit"));
        dialog.getDialogPane().setContent(form);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.getDialogPane().lookupButton(ButtonType.OK).addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            // a blank name is allowed for a NEW table: the database gives it the starter name "Bàn N"
            String nameProblem = existing == null && nameField.getText().isBlank()
                    ? null : TableRules.nameProblem(nameField.getText(), others);
            String seatsProblem = TableRules.capacityProblem(seatsField.getText());
            String first = nameProblem != null ? nameProblem : seatsProblem;
            if (first != null) {
                problem.setText(I18n.t(first, TableRules.MAX_NAME, TableRules.MAX_CAPACITY));
                event.consume();
            }
        });
        if (dialog.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }
        // the box was filled with the name SHOWN; typing nothing new keeps the stored name (see TableRules.nameToStore)
        String name = existing == null ? nameField.getText().trim()
                : TableRules.nameToStore(nameField.getText(), existing.getName(), existing.displayName());
        int seats = TableRules.parseCapacity(seatsField.getText());
        if (existing == null) {
            Async.run(() -> tableDAO.add(name, seats), number -> {
                SecurityLog.event(SecurityLog.actor() + " added table " + number);
                changed();
            }, error -> AlertUtil.warning(I18n.t("tablemgr.title"), String.valueOf(error.getMessage())));
        } else {
            Async.run(() -> {
                tableDAO.update(existing.getTableNumber(), name, seats);
                return null;
            }, ignored -> {
                SecurityLog.event(SecurityLog.actor() + " edited table " + existing.getTableNumber());
                changed();
            }, error -> AlertUtil.warning(I18n.t("tablemgr.title"), String.valueOf(error.getMessage())));
        }
    }

    private void remove(DiningTable table) {
        if (table == null) {
            return;
        }
        if (view.getItems().size() <= 1) {
            AlertUtil.warning(I18n.t("tablemgr.title"), I18n.t("tablemgr.remove.last"));
            return;
        }
        if (table.isOccupied()) {
            AlertUtil.warning(I18n.t("tablemgr.title"), I18n.t("tablemgr.remove.inuse", table.displayName()));
            return;
        }
        if (!AlertUtil.confirm(I18n.t("tablemgr.remove.title"), I18n.t("tablemgr.remove.confirm", table.displayName()))) {
            return;
        }
        Async.run(() -> tableDAO.remove(table.getTableNumber()), removed -> {
            if (removed) {
                SecurityLog.event(SecurityLog.actor() + " removed table " + table.getTableNumber());
            } else {
                AlertUtil.warning(I18n.t("tablemgr.title"), I18n.t("tablemgr.remove.inuse", table.displayName()));
            }
            changed();
        }, error -> AlertUtil.warning(I18n.t("tablemgr.title"), String.valueOf(error.getMessage())));
    }
}
