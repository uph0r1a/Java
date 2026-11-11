package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.dao.ReportDAO.DiscountLine;
import com.coffeeshop.coffeeshopmanagement.service.DiscountReport;
import com.coffeeshop.coffeeshopmanagement.util.CurrencyUtil;
import com.coffeeshop.coffeeshopmanagement.util.DiscountText;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Function;

/** "Discounts given" (TODO 2.37): the discounts of the report's period, per person and reason, with the total. The query is {@code ReportDAO.discountsByCashier}. */
final class DiscountReportDialog {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private DiscountReportDialog() {
    }

    static void show(List<DiscountLine> lines, LocalDate from, LocalDate to) {
        TableView<DiscountLine> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.getColumns().add(column(I18n.t("report.col.employee"), 150, DiscountText::person));
        table.getColumns().add(column(I18n.t("report.discount.col.reason"), 260, DiscountText::reason));
        table.getColumns().add(column(I18n.t("report.col.orders"), 70, l -> String.valueOf(l.orders())));
        table.getColumns().add(column(I18n.t("report.discount.col.amount"), 130, l -> CurrencyUtil.format(l.amount())));
        table.setPlaceholder(new Label(I18n.t("report.discount.empty")));
        table.getItems().setAll(lines);
        Label total = new Label(I18n.t("report.discount.total", CurrencyUtil.format(DiscountReport.total(lines))));
        total.setStyle("-fx-font-weight: bold;");
        Label note = new Label(I18n.t("report.discount.note"));
        note.setWrapText(true);
        VBox root = new VBox(8, new Label(I18n.t("report.discount.title", from.format(DAY), to.format(DAY))), table, total, note);
        root.setPadding(new Insets(12));
        VBox.setVgrow(table, Priority.ALWAYS);
        Stage stage = com.coffeeshop.coffeeshopmanagement.util.SceneNavigator.newOwnedStage(); // owned by the main window (TODO 5.36)
        stage.setTitle(I18n.t("report.discount.window"));
        stage.setScene(new Scene(root, 640, 420));
        stage.show();
    }

    private static TableColumn<DiscountLine, String> column(String title, double width, Function<DiscountLine, String> text) {
        TableColumn<DiscountLine, String> column = new TableColumn<>(title);
        column.setPrefWidth(width);
        column.setCellValueFactory(cell -> new SimpleStringProperty(text.apply(cell.getValue())));
        return column;
    }
}
