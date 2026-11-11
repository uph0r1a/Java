package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.service.StockService;
import com.coffeeshop.coffeeshopmanagement.service.StockHistory;
import com.coffeeshop.coffeeshopmanagement.service.StockHistory.Move;
import com.coffeeshop.coffeeshopmanagement.util.AlertUtil;
import com.coffeeshop.coffeeshopmanagement.util.Async;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.format.DateTimeFormatter;
import java.util.function.Function;

/**
 * "Lịch sử kho" (TODO 4.59): everything that moved the stock of one product or one ingredient, newest first: goods received, counts,
 * waste, sales and refunds that put goods back, each with the stock it left. Read-only; administrators only (the DAO refuses anyone
 * else). The list is built by {@link StockService}.
 */
public final class StockHistoryDialog {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");

    private StockHistoryDialog() {
    }

    /** The history of a product's own stock number (cups). {@code hint} is shown above the list when not null. */
    public static void showProduct(int productId, String name, String hint) {
        Async.run(() -> new StockService().ofProduct(productId, StockService.HISTORY_LINES), page -> display(name, hint, page),
                error -> failed(name, error)); // TODO 6.14: read in the background, shown when it is here
    }

    /** The history of an ingredient's stock. */
    public static void showIngredient(int ingredientId, String name) {
        Async.run(() -> new StockService().ofIngredient(ingredientId, StockService.HISTORY_LINES), page -> display(name, null, page),
                error -> failed(name, error));
    }

    private static void failed(String name, Throwable error) {
        AlertUtil.error(I18n.t("stock.history.title", name), error.getMessage());
    }

    private static void display(String name, String hint, StockHistory.Page page) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(I18n.t("stock.history.title", name));
        AlertUtil.configure(dialog);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.setResizable(true);

        TableView<Move> table = new TableView<>(FXCollections.observableArrayList(page.moves()));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Label(I18n.t("stock.history.empty")));
        table.getColumns().add(column("stock.history.col.time", m -> m.at().format(TIME), Pos.CENTER_LEFT));
        table.getColumns().add(column("stock.history.col.kind", m -> kindText(m.kind()), Pos.CENTER_LEFT));
        table.getColumns().add(column("stock.history.col.change", m -> (m.quantity() > 0 ? "+" : "") + m.quantity(), Pos.CENTER_RIGHT));
        table.getColumns().add(column("stock.history.col.after", m -> String.valueOf(m.balance()), Pos.CENTER_RIGHT));
        table.getColumns().add(column("stock.history.col.detail", StockHistoryDialog::detail, Pos.CENTER_LEFT));
        table.getColumns().add(column("stock.history.col.by", m -> m.by() == null ? "" : m.by(), Pos.CENTER_LEFT));
        table.setPrefHeight(420);
        table.setPrefWidth(820);

        VBox content = new VBox(10);
        content.setPadding(new Insets(10));
        if (hint != null) {
            Label hintLabel = new Label(hint);
            hintLabel.setWrapText(true);
            content.getChildren().add(hintLabel);
        }
        Label summary = new Label(I18n.t("stock.history.summary", page.now(), page.before()));
        summary.setWrapText(true);
        content.getChildren().addAll(summary, table);
        if (page.cut()) {
            Label cut = new Label(I18n.t("stock.history.cut", page.moves().size()));
            cut.setWrapText(true);
            content.getChildren().add(cut);
        }
        VBox.setVgrow(table, Priority.ALWAYS);
        dialog.getDialogPane().setContent(content);
        dialog.showAndWait();
    }

    private static String kindText(StockHistory.Kind kind) {
        return switch (kind) {
            case RECEIPT -> I18n.t("stock.kind.receipt");
            case ADJUST -> I18n.t("stock.kind.adjust");
            case WASTE -> I18n.t("stock.kind.waste");
            case SALE -> I18n.t("stock.kind.sale");
            case REFUND -> I18n.t("stock.kind.refund");
            case CHANGE -> I18n.t("stock.kind.change");
        };
    }

    /** The order a line belongs to and the reason or supplier, in one cell. */
    private static String detail(Move move) {
        String order = move.orderId() == null ? null : I18n.t("stock.history.order", move.orderId());
        String note = move.note() == null || move.note().isBlank() ? null : move.note();
        if (order == null) return note == null ? "" : note;
        return note == null ? order : order + " - " + note;
    }

    private static TableColumn<Move, String> column(String titleKey, Function<Move, String> text, Pos alignment) {
        TableColumn<Move, String> column = new TableColumn<>(I18n.t(titleKey));
        column.setCellValueFactory(cell -> new SimpleStringProperty(text.apply(cell.getValue())));
        column.setStyle("-fx-alignment: " + alignment.name().replace('_', '-') + ";");
        column.setSortable(false);
        return column;
    }
}
