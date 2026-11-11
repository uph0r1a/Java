package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.service.TableService;
import com.coffeeshop.coffeeshopmanagement.model.DiningTable;
import com.coffeeshop.coffeeshopmanagement.model.OrderItem;
import com.coffeeshop.coffeeshopmanagement.service.SplitBillRules;
import com.coffeeshop.coffeeshopmanagement.service.TableLayout;
import com.coffeeshop.coffeeshopmanagement.util.AlertUtil;
import com.coffeeshop.coffeeshopmanagement.util.Async;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.config.SeedNames;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.layout.GridPane;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Moving, joining and splitting an order on the sales screen (TODO 4.10, rule D10), kept out of {@code OrderController} (TODO 6.54):
 * the "Chuyển / Gộp / Tách" button after the take-away ones and the boxes that ask what to do. The rules are in
 * {@link com.coffeeshop.coffeeshopmanagement.service.TableMoveRules} and {@link SplitBillRules}; the database work is in {@link TableService}.
 */
final class TableMovePanel {

    private enum Action { TRANSFER, MERGE, SPLIT }

    private TableMovePanel() {
    }

    static void addButton(GridPane grid, int index, Runnable onClick) {
        Button button = new Button("🔀  " + I18n.t("table.move.open"));
        TakeAwayPanel.style(button, "table-button-empty", false);
        button.setWrapText(true); // the words do not fit one line of a table tile in English or in Vietnamese (TODO 1.7)
        button.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        button.setOnAction(e -> onClick.run());
        TableLayout.Cell cell = TableLayout.cellAt(index);
        grid.add(button, cell.column(), cell.row());
    }

    /**
     * Asks what to do with the order of table {@code selected}. {@code onSplit} gets the pieces to pay first (line index to count);
     * {@code onMoved} is called with the table to show once a transfer or merge is saved.
     */
    static void open(Integer selected, boolean hasOrder, List<DiningTable> tables, List<OrderItem> cart,
                     Consumer<Map<Integer, Integer>> onSplit, Consumer<Integer> onMoved) {
        if (selected == null || !hasOrder) {
            AlertUtil.info(I18n.t("table.move.title"), I18n.t("table.move.pick"));
            return;
        }
        Optional<Action> action = askAction();
        if (action.isEmpty()) return;
        List<DiningTable> sorted = tables.stream().sorted(Comparator.comparingInt(DiningTable::getTableNumber)).toList();
        switch (action.get()) {
            case TRANSFER -> chooseTable("table.move.transfer", sorted.stream().filter(t -> !t.isOccupied() && t.getTableNumber() != selected).toList())
                    .filter(to -> BookedTables.confirm(to.getTableNumber(), to.displayName())) // a booking within the hour (TODO 3.74)
                    .ifPresent(to -> run(() -> new TableService().transfer(selected, to.getTableNumber()), () -> onMoved.accept(to.getTableNumber())));
            case MERGE -> chooseTable("table.move.merge", sorted.stream().filter(t -> t.isOccupied() && t.getTableNumber() != selected).toList())
                    .ifPresent(from -> {
                        if (AlertUtil.confirm(I18n.t("table.move.merge"), I18n.t("table.move.merge.confirm", from.displayName(), selectedName(sorted, selected)))) {
                            run(() -> new TableService().merge(selected, from.getTableNumber()), () -> onMoved.accept(selected));
                        }
                    });
            case SPLIT -> chooseSplit(cart).ifPresent(onSplit);
        }
    }

    private static String selectedName(List<DiningTable> tables, int number) {
        return tables.stream().filter(t -> t.getTableNumber() == number).findFirst().map(DiningTable::displayName).orElse(I18n.t("orders.table", number));
    }

    private static void run(Runnable work, Runnable then) {
        Async.run(() -> {
            work.run();
            return Boolean.TRUE;
        }, done -> then.run(), error -> AlertUtil.error(I18n.t("table.move.title"), String.valueOf(error.getMessage())));
    }

    private static Optional<Action> askAction() {
        ButtonType transfer = new ButtonType(I18n.t("table.move.transfer"));
        ButtonType merge = new ButtonType(I18n.t("table.move.merge"));
        ButtonType split = new ButtonType(I18n.t("table.move.split"));
        Dialog<Action> dialog = new Dialog<>();
        dialog.setTitle(I18n.t("table.move.title"));
        AlertUtil.configure(dialog);
        dialog.getDialogPane().setContentText(I18n.t("table.move.ask"));
        dialog.getDialogPane().getButtonTypes().addAll(transfer, merge, split, ButtonType.CANCEL);
        AlertUtil.setCancelButton(dialog, ButtonType.CANCEL);
        dialog.setResultConverter(b -> b == transfer ? Action.TRANSFER : b == merge ? Action.MERGE : b == split ? Action.SPLIT : null);
        return dialog.showAndWait();
    }

    private static Optional<DiningTable> chooseTable(String titleKey, List<DiningTable> options) {
        if (options.isEmpty()) {
            AlertUtil.info(I18n.t(titleKey), I18n.t("table.move.none"));
            return Optional.empty();
        }
        ChoiceDialog<DiningTable> dialog = new ChoiceDialog<>(options.get(0), options);
        dialog.setTitle(I18n.t(titleKey));
        dialog.setHeaderText(null);
        dialog.setContentText(I18n.t("table.move.choose"));
        AlertUtil.configure(dialog);
        return dialog.showAndWait();
    }

    /** One spinner per cart line: how many pieces go in the part paid now. */
    private static Optional<Map<Integer, Integer>> chooseSplit(List<OrderItem> cart) {
        Dialog<Map<Integer, Integer>> dialog = new Dialog<>();
        dialog.setTitle(I18n.t("table.move.split"));
        AlertUtil.configure(dialog);
        ButtonType ok = new ButtonType(I18n.t("table.move.split.go"), javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(ok, ButtonType.CANCEL);
        AlertUtil.setCancelButton(dialog, ButtonType.CANCEL);
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(6);
        grid.add(new Label(I18n.t("table.move.split.hint")), 0, 0, 2, 1);
        List<Spinner<Integer>> spinners = new java.util.ArrayList<>();
        for (int i = 0; i < cart.size(); i++) {
            OrderItem line = cart.get(i);
            Spinner<Integer> spinner = new Spinner<>(0, line.getQuantity(), 0);
            spinner.setPrefWidth(80);
            spinners.add(spinner);
            grid.add(new Label(SeedNames.product(line.getProductName()) + "  (×" + line.getQuantity() + ")"), 0, i + 1);
            grid.add(spinner, 1, i + 1);
        }
        dialog.getDialogPane().setContent(grid);
        dialog.setResultConverter(b -> {
            if (b != ok) return null;
            Map<Integer, Integer> take = new HashMap<>();
            for (int i = 0; i < spinners.size(); i++) {
                Integer value = spinners.get(i).getValue();
                if (value != null && value > 0) take.put(i, value);
            }
            return take;
        });
        Optional<Map<Integer, Integer>> result = dialog.showAndWait();
        if (result.isEmpty()) return Optional.empty();
        String problem = SplitBillRules.problem(cart, result.get());
        if (problem != null) {
            AlertUtil.warning(I18n.t("table.move.split"), problem);
            return Optional.empty();
        }
        return result;
    }
}
