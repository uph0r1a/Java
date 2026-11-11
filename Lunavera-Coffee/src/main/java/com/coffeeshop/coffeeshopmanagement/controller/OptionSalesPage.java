package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.dao.ReportDAO;
import com.coffeeshop.coffeeshopmanagement.util.CurrencyUtil;
import com.coffeeshop.coffeeshopmanagement.util.Csv;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;

/**
 * The "sizes and extras" tab of the report (TODO 4.47): how many pieces of each size and each extra were sold in the range and what they
 * brought in, refunds taken off. Kept out of {@code ReportWindow} (which is over its size ceiling): the window gives it the rows after
 * every load and asks it for its lines when it writes the CSV.
 */
final class OptionSalesPage {

    private final TableView<ReportDAO.OptionSales> table = new TableView<>();
    private final Tab tab;
    private List<ReportDAO.OptionSales> rows = List.of();

    OptionSalesPage() {
        table.getColumns().add(column("option.report.col.kind", 110, r -> kindText(r)));
        table.getColumns().add(column("option.report.col.name", 220, ReportDAO.OptionSales::name));
        table.getColumns().add(column("option.report.col.qty", 90, r -> String.valueOf(r.quantity())));
        table.getColumns().add(column("option.report.col.revenue", 140, r -> CurrencyUtil.format(r.revenue())));
        table.setPlaceholder(new Label(I18n.t("option.report.empty")));
        Label note = new Label(I18n.t("option.report.note"));
        note.setWrapText(true);
        note.setStyle("-fx-opacity: 0.7;");
        VBox page = new VBox(8, table, note);
        VBox.setVgrow(table, Priority.ALWAYS);
        page.setPadding(new Insets(8, 0, 0, 0));
        tab = new Tab(I18n.t("report.tab.options"), page);
        tab.setClosable(false);
    }

    Tab tab() {
        return tab;
    }

    /** Shows the rows of the range just loaded. */
    void show(List<ReportDAO.OptionSales> loaded) {
        rows = loaded == null ? List.of() : loaded;
        table.getItems().setAll(rows);
    }

    /** The rows for the CSV file: a blank line, the header, then one line per size or extra; nothing at all when there are none. */
    List<String> csvLines() {
        List<String> lines = new ArrayList<>();
        if (rows.isEmpty()) return lines;
        lines.add("");
        lines.add(I18n.t("report.csv.option.header"));
        for (ReportDAO.OptionSales r : rows) {
            lines.add(Csv.cell(r.name()) + "," + kindText(r) + "," + r.quantity() + "," + r.revenue().toPlainString());
        }
        return lines;
    }

    private static String kindText(ReportDAO.OptionSales r) {
        return r.kind() == com.coffeeshop.coffeeshopmanagement.model.OptionKind.SIZE ? I18n.t("option.kind.size") : I18n.t("option.kind.extra");
    }

    private static TableColumn<ReportDAO.OptionSales, String> column(String key, double width,
                                                                     java.util.function.Function<ReportDAO.OptionSales, String> value) {
        TableColumn<ReportDAO.OptionSales, String> column = new TableColumn<>(I18n.t(key));
        column.setCellValueFactory(c -> new SimpleStringProperty(value.apply(c.getValue())));
        column.setPrefWidth(width);
        return column;
    }
}
