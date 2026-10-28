package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.dao.DataAccessException;
import com.coffeeshop.coffeeshopmanagement.dao.ProductDAO;
import com.coffeeshop.coffeeshopmanagement.dao.StockLogDAO;
import com.coffeeshop.coffeeshopmanagement.model.Product;
import com.coffeeshop.coffeeshopmanagement.model.StockLogEntry;
import com.coffeeshop.coffeeshopmanagement.service.StockRules;
import com.coffeeshop.coffeeshopmanagement.util.AlertUtil;
import com.coffeeshop.coffeeshopmanagement.util.CurrencyUtil;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.MoneyParser;
import com.coffeeshop.coffeeshopmanagement.util.TextLengthLimiter;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * "Nhập kho" (TODO 4.5): goods come in. The top half is the receipt form (product, how many, optional cost per piece, supplier, note);
 * a switch above it turns the same form into "Hao hụt" (TODO 4.33): goods thrown away or broken (product, how many, a required
 * reason), which lowers the stock. The bottom half is the log of the newest lines, receipts, waste and hand-typed stock numbers
 * alike, with who and when. Administrators only:
 * the buttons that open it are on the product screen, and {@link StockLogDAO} refuses anyone else.
 */
public final class StockLogDialog {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");
    private static final int SHOWN_LINES = 300;

    private StockLogDialog() {
    }

    /**
     * @param products the products to choose from (the caller's list, newest numbers)
     * @param onChanged called after a receipt was saved, so the caller can reload its product list
     */
    public static void show(List<Product> products, Runnable onChanged) {
        StockLogDAO stockLogDAO = new StockLogDAO();
        ProductDAO productDAO = new ProductDAO();

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(I18n.t("stock.title"));
        AlertUtil.configure(dialog);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.setResizable(true);

        ComboBox<Product> productBox = new ComboBox<>(FXCollections.observableArrayList(products));
        productBox.setPromptText(I18n.t("stock.product.prompt"));
        productBox.setMaxWidth(Double.MAX_VALUE);
        productBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(Product p) {
                return p == null ? "" : com.coffeeshop.coffeeshopmanagement.config.SeedNames.product(p.getName())
                        + "  (" + I18n.t("stock.instock", p.getStock()) + ")";
            }

            @Override
            public Product fromString(String text) {
                return null;
            }
        });
        TextField quantityField = new TextField();
        quantityField.setPromptText(I18n.t("stock.quantity.prompt"));
        TextField costField = new TextField();
        costField.setPromptText(I18n.t("stock.cost.prompt"));
        TextField supplierField = new TextField();
        supplierField.setPromptText(I18n.t("stock.supplier.prompt"));
        TextLengthLimiter.limit(supplierField, StockRules.TEXT_MAX);
        TextField noteField = new TextField();
        noteField.setPromptText(I18n.t("stock.note.prompt"));
        TextLengthLimiter.limit(noteField, StockRules.TEXT_MAX);
        Label status = new Label();
        status.setWrapText(true);
        Button receiveButton = new Button(I18n.t("stock.receive"));
        receiveButton.setDefaultButton(true);

        // The switch between the two uses of the form (TODO 4.33). Receiving stays the first, selected one.
        ToggleGroup modeGroup = new ToggleGroup();
        RadioButton receiveMode = new RadioButton(I18n.t("stock.mode.receive"));
        receiveMode.setToggleGroup(modeGroup);
        receiveMode.setSelected(true);
        RadioButton wasteMode = new RadioButton(I18n.t("stock.mode.waste"));
        wasteMode.setToggleGroup(modeGroup);
        ComboBox<String> reasonBox = new ComboBox<>(FXCollections.observableArrayList(
                I18n.t("stock.waste.reason.expired"), I18n.t("stock.waste.reason.broken"),
                I18n.t("stock.waste.reason.spilled"), I18n.t("stock.waste.reason.given")));
        reasonBox.setEditable(true); // the shop's own words are allowed, the list only saves typing
        reasonBox.setPromptText(I18n.t("stock.waste.reason.prompt"));
        reasonBox.setMaxWidth(Double.MAX_VALUE);
        TextLengthLimiter.limit(reasonBox.getEditor(), StockRules.TEXT_MAX);

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(8);
        int row = 0;
        Label costLabel = new Label(I18n.t("stock.label.cost"));
        Label supplierLabel = new Label(I18n.t("stock.label.supplier"));
        Label noteLabel = new Label(I18n.t("stock.label.note"));
        Label reasonLabel = new Label(I18n.t("stock.waste.label.reason"));
        form.add(new HBox(18, receiveMode, wasteMode), 1, row++);
        form.addRow(row++, new Label(I18n.t("stock.label.product")), productBox);
        form.addRow(row++, new Label(I18n.t("stock.label.quantity")), quantityField);
        form.addRow(row++, costLabel, costField);
        form.addRow(row++, supplierLabel, supplierField);
        form.addRow(row++, noteLabel, noteField);
        form.addRow(row++, reasonLabel, reasonBox);
        form.add(receiveButton, 1, row++);
        GridPane.setHgrow(productBox, Priority.ALWAYS);
        // Cost, supplier and note belong to receiving; the reason belongs to waste. The hidden ones take no room.
        java.util.List<javafx.scene.Node> receiveOnly = java.util.List.of(costLabel, costField, supplierLabel, supplierField, noteLabel, noteField);
        java.util.List<javafx.scene.Node> wasteOnly = java.util.List.of(reasonLabel, reasonBox);
        Runnable applyMode = () -> {
            boolean waste = wasteMode.isSelected();
            for (javafx.scene.Node node : receiveOnly) {
                node.setVisible(!waste);
                node.setManaged(!waste);
            }
            for (javafx.scene.Node node : wasteOnly) {
                node.setVisible(waste);
                node.setManaged(waste);
            }
            quantityField.setPromptText(I18n.t(waste ? "stock.waste.quantity.prompt" : "stock.quantity.prompt"));
            receiveButton.setText(I18n.t(waste ? "stock.waste.record" : "stock.receive"));
            status.setText("");
            if (dialog.getDialogPane().getScene() != null && dialog.getDialogPane().getScene().getWindow() != null) {
                dialog.getDialogPane().getScene().getWindow().sizeToScene();
            }
        };
        modeGroup.selectedToggleProperty().addListener((obs, was, isNow) -> applyMode.run());
        applyMode.run();

        TableView<StockLogEntry> table = new TableView<>();
        table.setPrefHeight(300);
        table.setPlaceholder(new Label(I18n.t("stock.empty")));
        table.getColumns().add(column(I18n.t("stock.col.time"), 120, e -> TIME.format(e.loggedAt())));
        table.getColumns().add(column(I18n.t("stock.col.kind"), 90,
                e -> I18n.t(StockLogEntry.RECEIPT.equals(e.kind()) ? "stock.kind.receipt"
                        : StockLogEntry.WASTE.equals(e.kind()) ? "stock.kind.waste" : "stock.kind.adjust")));
        table.getColumns().add(column(I18n.t("stock.col.product"), 170,
                e -> com.coffeeshop.coffeeshopmanagement.config.SeedNames.product(e.productName())));
        table.getColumns().add(column(I18n.t("stock.col.quantity"), 70, e -> (e.quantity() > 0 ? "+" : "") + e.quantity()));
        table.getColumns().add(column(I18n.t("stock.col.stock"), 90, e -> e.stockBefore() + " \u2192 " + e.stockAfter()));
        table.getColumns().add(column(I18n.t("stock.col.cost"), 90, e -> e.unitCost() == null ? "" : CurrencyUtil.format(e.unitCost())));
        table.getColumns().add(column(I18n.t("stock.col.supplier"), 120, e -> e.supplier() == null ? "" : e.supplier()));
        table.getColumns().add(column(I18n.t("stock.col.by"), 110, e -> e.employeeName() == null ? "" : e.employeeName()));
        table.getColumns().add(column(I18n.t("stock.col.note"), 150, e -> e.note() == null ? "" : e.note()));
        Runnable reload = () -> {
            try {
                table.getItems().setAll(stockLogDAO.findRecent(null, SHOWN_LINES));
            } catch (DataAccessException ex) {
                AlertUtil.error(I18n.t("stock.title"), ex.getMessage());
            }
        };
        reload.run();

        // the choice list shows the stock in its text: after a save, refresh the product with the number the database holds now
        java.util.function.Function<Product, Product> refreshProduct = product -> {
            Product fresh = productDAO.findById(product.getId()).orElse(product);
            int at = productBox.getItems().indexOf(product);
            if (at >= 0) productBox.getItems().set(at, fresh);
            productBox.setValue(fresh);
            return fresh;
        };

        receiveButton.setOnAction(e -> {
            Product product = productBox.getValue();
            if (product == null) {
                status.setText(I18n.t("stock.val.product"));
                return;
            }
            int quantity = StockRules.parseQuantity(quantityField.getText());
            if (quantity < 0) {
                status.setText(I18n.t("stock.val.quantity"));
                return;
            }
            if (wasteMode.isSelected()) {
                String reason = reasonBox.getEditor().getText();
                if (StockRules.cleanText(reason) == null) {
                    status.setText(I18n.t("stock.waste.val.reason"));
                    return;
                }
                try {
                    int after = stockLogDAO.writeOff(product.getId(), quantity, reason);
                    String shownName = com.coffeeshop.coffeeshopmanagement.config.SeedNames.product(product.getName());
                    Product fresh = refreshProduct.apply(product);
                    status.setText(fresh.getCost() == null
                            ? I18n.t("stock.waste.saved.nocost", shownName, quantity, after)
                            : I18n.t("stock.waste.saved", shownName, quantity, after,
                                    CurrencyUtil.format(fresh.getCost().multiply(BigDecimal.valueOf(quantity)))));
                    quantityField.clear();
                    reasonBox.setValue(null);
                    reasonBox.getEditor().clear();
                    reload.run();
                    if (onChanged != null) onChanged.run();
                    quantityField.requestFocus();
                } catch (DataAccessException ex) {
                    status.setText(ex.getMessage());
                }
                return;
            }
            BigDecimal cost = null;
            if (costField.getText() != null && !costField.getText().isBlank()) {
                cost = MoneyParser.parseOptionalDong(costField.getText());
                if (cost == null) {
                    status.setText(I18n.t("stock.val.cost"));
                    return;
                }
            }
            try {
                int after = stockLogDAO.receive(product.getId(), quantity, cost, supplierField.getText(), noteField.getText());
                status.setText(I18n.t("stock.saved", com.coffeeshop.coffeeshopmanagement.config.SeedNames.product(product.getName()),
                        quantity, after));
                quantityField.clear();
                costField.clear();
                noteField.clear();
                refreshProduct.apply(product);
                reload.run();
                if (onChanged != null) onChanged.run();
                quantityField.requestFocus();
            } catch (DataAccessException ex) {
                status.setText(ex.getMessage());
            }
        });

        VBox content = new VBox(12, form, status, new Label(I18n.t("stock.log.heading")), table);
        content.setPadding(new Insets(16));
        content.setPrefWidth(980);
        dialog.getDialogPane().setContent(content);
        dialog.showAndWait();
    }

    private static TableColumn<StockLogEntry, String> column(String title, double width,
                                                               java.util.function.Function<StockLogEntry, String> text) {
        TableColumn<StockLogEntry, String> column = new TableColumn<>(title);
        column.setPrefWidth(width);
        column.setSortable(false);
        column.setCellValueFactory(data -> new SimpleStringProperty(text.apply(data.getValue())));
        return column;
    }
}
