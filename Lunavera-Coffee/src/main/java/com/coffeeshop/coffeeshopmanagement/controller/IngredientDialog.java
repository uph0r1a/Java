package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.util.TextLengthLimiter;
import com.coffeeshop.coffeeshopmanagement.service.IngredientRules;
import com.coffeeshop.coffeeshopmanagement.dao.IngredientDAO;
import com.coffeeshop.coffeeshopmanagement.service.IngredientService;
import com.coffeeshop.coffeeshopmanagement.model.Ingredient;
import com.coffeeshop.coffeeshopmanagement.service.IngredientCostRules;
import com.coffeeshop.coffeeshopmanagement.util.AlertUtil;
import com.coffeeshop.coffeeshopmanagement.util.Async;
import com.coffeeshop.coffeeshopmanagement.util.CurrencyUtil;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * The owner's screen for ingredients and recipes (TODO 4.41, rule D12): the list of ingredients (add, change, receive a delivery,
 * count, delete) and, on the second tab, the recipe of each product ({@link RecipePane}). Administrators only; the database
 * refuses the changes of anyone else.
 */
final class IngredientDialog {

    private final IngredientService dao = new IngredientService();
    private final TableView<Ingredient> view = new TableView<>();
    private final RecipePane recipes = new RecipePane(dao);
    private final OptionsPane options = new OptionsPane(); // TODO 4.47
    private final Runnable onChanged;

    private IngredientDialog(Runnable onChanged) {
        this.onChanged = onChanged;
    }

    /** Opens the dialog; {@code onChanged} runs after a change, so the dashboard behind it can refresh its low-stock card. */
    static void show(Runnable onChanged) {
        new IngredientDialog(onChanged).open();
    }

    private void open() {
        view.getColumns().add(column("ingredient.col.name", 220, i -> i.name()));
        view.getColumns().add(column("ingredient.col.unit", 80, i -> i.unit()));
        view.getColumns().add(column("ingredient.col.stock", 110, i -> String.valueOf(i.stock())));
        view.getColumns().add(column("ingredient.col.min", 110, i -> String.valueOf(i.minStock())));
        view.getColumns().add(column("ingredient.col.cost", 130, i -> costText(i.unitCost())));
        view.getColumns().add(column("ingredient.col.status", 120, i -> I18n.t(i.isLow() ? "ingredient.status.low" : "ingredient.status.ok")));
        view.setPlaceholder(new Label(I18n.t("ingredient.empty")));
        view.setPrefHeight(300);

        Button add = new Button(I18n.t("ingredient.add"));
        add.setOnAction(e -> edit(null));
        Button change = new Button(I18n.t("ingredient.edit"));
        change.setOnAction(e -> selected().ifPresent(this::edit));
        Button receive = new Button(I18n.t("ingredient.receive"));
        receive.setOnAction(e -> selected().ifPresent(this::receive));
        Button count = new Button(I18n.t("ingredient.count"));
        count.setOnAction(e -> selected().ifPresent(this::count));
        Button delete = new Button(I18n.t("ingredient.delete"));
        delete.setOnAction(e -> selected().ifPresent(this::delete));
        Button history = new Button(I18n.t("ingredient.history"));
        history.setOnAction(e -> selected().ifPresent(i -> StockHistoryDialog.showIngredient(i.id(), i.name())));
        HBox buttons = new HBox(8, add, change, receive, count, history, delete);
        Label hint = new Label(I18n.t("ingredient.hint"));
        hint.setWrapText(true);
        VBox listBox = new VBox(10, hint, view, buttons);
        listBox.setPadding(new Insets(12));

        Tab list = new Tab(I18n.t("ingredient.tab.list"), listBox);
        Tab recipe = new Tab(I18n.t("ingredient.tab.recipes"), recipes.node());
        Tab optionsTab = new Tab(I18n.t("ingredient.tab.options"), options.node());
        list.setClosable(false);
        recipe.setClosable(false);
        optionsTab.setClosable(false);
        TabPane tabs = new TabPane(list, recipe, optionsTab);
        tabs.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> {
            if (b == recipe) recipes.reload();
            if (b == optionsTab) options.reload();
        });

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle(I18n.t("ingredient.title"));
        AlertUtil.configure(dialog);
        dialog.getDialogPane().setContent(tabs);
        dialog.getDialogPane().setPrefWidth(720);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        reload();
        dialog.showAndWait();
    }

    /** The price of one unit as money, or "-" when the ingredient has none (TODO 4.51). */
    static String costText(Long unitCost) {
        return unitCost == null ? "-" : CurrencyUtil.format(BigDecimal.valueOf(unitCost));
    }

    private static TableColumn<Ingredient, String> column(String key, double width, java.util.function.Function<Ingredient, String> value) {
        TableColumn<Ingredient, String> column = new TableColumn<>(I18n.t(key));
        column.setCellValueFactory(c -> new SimpleStringProperty(value.apply(c.getValue())));
        column.setPrefWidth(width);
        return column;
    }

    private Optional<Ingredient> selected() {
        Ingredient chosen = view.getSelectionModel().getSelectedItem();
        if (chosen == null) AlertUtil.info(I18n.t("ingredient.title"), I18n.t("ingredient.pick"));
        return Optional.ofNullable(chosen);
    }

    /** Reads the list off the screen thread (TODO 6.14) and shows it when it is here. */
    private void reload() {
        Async.run(dao::findAll,
                found -> view.setItems(FXCollections.observableArrayList(found)),
                error -> AlertUtil.error(I18n.t("ingredient.title"), String.valueOf(error.getMessage())));
    }

    /**
     * Runs a change off the screen thread (TODO 6.14; the scan test {@code DatabaseOffTheScreenThreadTest} knows this name), then redraws, tells the dashboard and hands the result to {@code afterwards};
     * a refusal is shown in a box and nothing changes. The change must not touch the screen: read the boxes before calling this.
     */
    private <T> void inBackground(java.util.concurrent.Callable<T> change, java.util.function.Consumer<T> afterwards) {
        Async.run(change,
                result -> {
                    reload();
                    onChanged.run();
                    afterwards.accept(result);
                },
                error -> AlertUtil.error(I18n.t("ingredient.title"), String.valueOf(error.getMessage())));
    }

    private void inBackground(Runnable change) {
        inBackground(() -> {
            change.run();
            return null;
        }, ignored -> { });
    }

    /** The box for a new ingredient ({@code existing} null) or to change one (the stock is changed with receive / count). */
    private void edit(Ingredient existing) {
        TextField name = new TextField(existing == null ? "" : existing.name());
        TextField unit = new TextField(existing == null ? "" : existing.unit());
        unit.setPromptText(I18n.t("ingredient.unit.prompt"));
        TextField stock = new TextField("0");
        TextField min = new TextField(existing == null ? "0" : String.valueOf(existing.minStock()));
        TextField cost = new TextField(existing == null || existing.unitCost() == null ? "" : String.valueOf(existing.unitCost()));
        cost.setPromptText(I18n.t("ingredient.cost.prompt"));
        TextLengthLimiter.limit(name, IngredientRules.NAME_MAX); // TODO 5.50: stop typing at the limit the rules enforce
        TextLengthLimiter.limit(unit, IngredientRules.UNIT_MAX);
        TextLengthLimiter.limit(stock, TextLengthLimiter.NUMBER_MAX);
        TextLengthLimiter.limit(min, TextLengthLimiter.NUMBER_MAX);
        TextLengthLimiter.limit(cost, TextLengthLimiter.MONEY_MAX);
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.setPadding(new Insets(10));
        grid.addRow(0, new Label(I18n.t("ingredient.col.name")), name);
        grid.addRow(1, new Label(I18n.t("ingredient.col.unit")), unit);
        int row = 2;
        if (existing == null) grid.addRow(row++, new Label(I18n.t("ingredient.col.stock")), stock);
        grid.addRow(row++, new Label(I18n.t("ingredient.col.min")), min);
        grid.addRow(row, new Label(I18n.t("ingredient.col.cost")), cost);
        Dialog<ButtonType> box = new Dialog<>();
        box.setTitle(I18n.t(existing == null ? "ingredient.add" : "ingredient.edit"));
        AlertUtil.configure(box);
        box.getDialogPane().setContent(grid);
        box.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        AlertUtil.setCancelButton(box, ButtonType.CANCEL);
        if (box.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
        OptionalLong minValue = number(min.getText());
        OptionalLong stockValue = existing == null ? number(stock.getText()) : OptionalLong.of(0);
        if (minValue.isEmpty() || stockValue.isEmpty()) {
            AlertUtil.warning(I18n.t("ingredient.title"), I18n.t("ingredient.val.number"));
            return;
        }
        IngredientCostRules.Typed typedCost = IngredientCostRules.parseOptionalUnitCost(cost.getText());
        if (!typedCost.ok()) {
            AlertUtil.warning(I18n.t("ingredient.title"), I18n.t("ingredient.val.price"));
            return;
        }
        final String nameText = name.getText(); // read here: the change below runs off the screen thread
        final String unitText = unit.getText();
        inBackground(() -> {
            if (existing == null) dao.insert(nameText, unitText, stockValue.getAsLong(), minValue.getAsLong(), typedCost.cost());
            else dao.update(existing.id(), nameText, unitText, minValue.getAsLong(), typedCost.cost());
        });
    }

    /**
     * The delivery box (TODO 4.51): how many arrived, optionally what was paid for the whole lot (which sets the price of one unit as
     * the average of the shelf and the lot), and whether to write the amount down as a supplies expense too.
     */
    private void receive(Ingredient ingredient) {
        TextField quantity = new TextField();
        TextField paid = new TextField();
        paid.setPromptText(I18n.t("ingredient.receive.paid.prompt"));
        TextLengthLimiter.limit(quantity, TextLengthLimiter.NUMBER_MAX); // TODO 5.50
        TextLengthLimiter.limit(paid, TextLengthLimiter.MONEY_MAX);
        CheckBox expense = new CheckBox(I18n.t("ingredient.receive.expense"));
        expense.disableProperty().bind(paid.textProperty().isEmpty());
        Label preview = new Label();
        preview.setWrapText(true);
        Runnable refresh = () -> {
            OptionalLong q = number(quantity.getText());
            IngredientCostRules.Typed p = IngredientCostRules.parseOptionalPaid(paid.getText());
            if (q.isEmpty() || q.getAsLong() <= 0 || !p.ok() || p.cost() == null || IngredientCostRules.deliveryProblem(q.getAsLong(), p.cost()) != null) {
                preview.setText("");
                return;
            }
            long unitPrice = IngredientCostRules.unitCostFromPaid(p.cost(), q.getAsLong());
            long after = IngredientCostRules.afterDelivery(ingredient.stock(), ingredient.unitCost(), q.getAsLong(), unitPrice);
            preview.setText(I18n.t("ingredient.receive.preview", ingredient.unit(), costText(unitPrice), costText(after)));
        };
        quantity.textProperty().addListener((o, a, b) -> refresh.run());
        paid.textProperty().addListener((o, a, b) -> refresh.run());
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.setPadding(new Insets(10));
        Label ask = new Label(I18n.t("ingredient.receive.ask", ingredient.name(), ingredient.unit()));
        ask.setWrapText(true);
        grid.add(ask, 0, 0, 2, 1);
        grid.addRow(1, new Label(I18n.t("ingredient.receive.quantity", ingredient.unit())), quantity);
        grid.addRow(2, new Label(I18n.t("ingredient.receive.paid")), paid);
        grid.add(expense, 1, 3);
        grid.add(preview, 0, 4, 2, 1);
        Dialog<ButtonType> box = new Dialog<>();
        box.setTitle(I18n.t("ingredient.receive"));
        AlertUtil.configure(box);
        box.getDialogPane().setContent(grid);
        box.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        AlertUtil.setCancelButton(box, ButtonType.CANCEL);
        if (box.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
        OptionalLong arrived = number(quantity.getText());
        IngredientCostRules.Typed typedPaid = IngredientCostRules.parseOptionalPaid(paid.getText());
        if (arrived.isEmpty() || !typedPaid.ok()) {
            AlertUtil.warning(I18n.t("ingredient.receive"), I18n.t("ingredient.val.number"));
            return;
        }
        if (arrived.getAsLong() <= 0) { // a delivery cannot lower the stock (TODO 2.95)
            AlertUtil.warning(I18n.t("ingredient.receive"), I18n.t("ingredient.val.receive"));
            return;
        }
        boolean writeExpense = expense.isSelected() && typedPaid.cost() != null;
        inBackground(() -> dao.receive(ingredient.id(), arrived.getAsLong(), typedPaid.cost(), writeExpense, I18n.t("ingredient.reason.delivery")));
    }

    private void count(Ingredient ingredient) {
        ask(I18n.t("ingredient.count"), I18n.t("ingredient.count.ask", ingredient.name(), ingredient.unit(), ingredient.stock()),
                String.valueOf(ingredient.stock()))
                .ifPresent(counted -> {
                    // the counted number itself is saved (TODO 3.75), not a difference from the number this list showed
                    inBackground(() -> dao.setCounted(ingredient.id(), counted, I18n.t("ingredient.reason.count")),
                            done -> {
                                if (done != null && done.before() != ingredient.stock()) {
                                    AlertUtil.info(I18n.t("ingredient.count"), I18n.t("ingredient.count.moved.note", ingredient.name(),
                                            ingredient.stock() - done.before(), ingredient.unit(), counted));
                                }
                            });
                });
    }

    private void delete(Ingredient ingredient) {
        if (AlertUtil.confirm(I18n.t("ingredient.delete"), I18n.t("ingredient.delete.ask", ingredient.name()))) {
            inBackground(() -> dao.delete(ingredient.id()));
        }
    }

    /** One whole number typed in a small box; empty when cancelled or not a number. */
    private static Optional<Long> ask(String title, String question, String initial) {
        TextInputDialog box = new TextInputDialog(initial);
        box.setTitle(title);
        box.setHeaderText(null);
        box.setContentText(question);
        AlertUtil.configure(box);
        Optional<String> typed = box.showAndWait();
        if (typed.isEmpty()) return Optional.empty();
        OptionalLong value = number(typed.get());
        if (value.isEmpty()) {
            AlertUtil.warning(title, I18n.t("ingredient.val.number"));
            return Optional.empty();
        }
        return Optional.of(value.getAsLong());
    }

    /** A whole number as typed (a dot only in a group of three digits, no comma, TODO 2.95), or empty. */
    static OptionalLong number(String text) {
        return com.coffeeshop.coffeeshopmanagement.service.WholeNumber.parse(text);
    }
}
