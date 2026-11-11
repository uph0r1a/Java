package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.service.IngredientService;
import com.coffeeshop.coffeeshopmanagement.service.ProductService;
import com.coffeeshop.coffeeshopmanagement.service.StockService;
import com.coffeeshop.coffeeshopmanagement.model.Product;
import com.coffeeshop.coffeeshopmanagement.model.StockLogEntry;
import com.coffeeshop.coffeeshopmanagement.service.StockRules;
import com.coffeeshop.coffeeshopmanagement.util.AlertUtil;
import com.coffeeshop.coffeeshopmanagement.util.Async;
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
 * the buttons that open it are on the product screen, and {@link StockService} refuses anyone else.
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
        StockService stock = new StockService();
        ProductService productService = new ProductService();
        IngredientService ingredients = new IngredientService();

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
        Button historyButton = new Button(I18n.t("stock.history.button"));
        historyButton.setOnAction(e -> {
            Product chosen = productBox.getValue();
            if (chosen == null) {
                status.setText(I18n.t("stock.val.product"));
                return;
            }
            // TODO 6.14: whether the stock is counted by recipe is read off the screen thread; the history opens when the answer is here
            final int chosenId = chosen.getId();
            final String chosenName = com.coffeeshop.coffeeshopmanagement.config.SeedNames.product(chosen.getName());
            Async.run(() -> !ingredients.recipeOf(chosenId).isEmpty(),
                    byRecipe -> StockHistoryDialog.showProduct(chosenId, chosenName, byRecipe ? I18n.t("stock.history.recipe") : null),
                    error -> status.setText(error.getMessage()),
                    historyButton);
        });
        form.add(new HBox(10, receiveButton, historyButton), 1, row++);
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
        // TODO 6.14: the log is read off the screen thread
        Runnable reload = () -> Async.run(() -> stock.findRecent(null, SHOWN_LINES),
                found -> table.getItems().setAll(found),
                error -> AlertUtil.error(I18n.t("stock.title"), error.getMessage()));
        reload.run();

        // the choice list shows the stock in its text: after a save, refresh the product with the number the database holds now
        // (the product itself is read again in the background step that saves; this only puts it in the list, on the screen thread)
        java.util.function.BiFunction<Product, Product, Product> refreshProduct = (product, fresh) -> {
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
                final int productId = product.getId();
                Async.run(() -> new Written(stock.writeOff(productId, quantity, reason),
                                productService.findById(productId).orElse(product)),
                        written -> {
                            int after = written.after();
                            String shownName = com.coffeeshop.coffeeshopmanagement.config.SeedNames.product(product.getName());
                            Product fresh = refreshProduct.apply(product, written.fresh());
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
                        },
                        error -> status.setText(error.getMessage()),
                        receiveButton);
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
            final BigDecimal price = cost;
            final int productId = product.getId();
            final String supplier = supplierField.getText();
            final String note = noteField.getText();
            // two background steps with the question to the cashier between them, on the screen thread: read the product as it is now,
            // ask what to do with its cost, then save the receipt and read the product again
            Async.run(() -> productService.findById(productId).orElse(product),
                    current -> {
                        com.coffeeshop.coffeeshopmanagement.service.ReceiptCost.Mode costMode = askCostMode(current, quantity, price);
                        Async.run(() -> new Written(stock.receive(productId, quantity, price, supplier, note, costMode),
                                        productService.findById(productId).orElse(product)),
                                written -> {
                                    status.setText(I18n.t("stock.saved", com.coffeeshop.coffeeshopmanagement.config.SeedNames.product(product.getName()),
                                            quantity, written.after()));
                                    quantityField.clear();
                                    costField.clear();
                                    noteField.clear();
                                    refreshProduct.apply(product, written.fresh());
                                    reload.run();
                                    if (onChanged != null) onChanged.run();
                                    quantityField.requestFocus();
                                },
                                error -> status.setText(error.getMessage()),
                                receiveButton);
                    },
                    error -> status.setText(error.getMessage()),
                    receiveButton);
        });

        VBox content = new VBox(12, form, status, new Label(I18n.t("stock.log.heading")), table);
        content.setPadding(new Insets(16));
        content.setPrefWidth(980);
        dialog.getDialogPane().setContent(content);
        dialog.showAndWait();
    }

    /** What a saved line brings back to the screen: the stock after it, and the product as the database holds it now. */
    private record Written(int after, Product fresh) {
    }

    /**
     * When goods arrive at a price different from the product's cost (TODO 4.52), asks what to do with the cost: keep it (the default
     * and what closing the box does), set it to the new price, or use the average of the stock in hand and what arrived.
     */
    private static com.coffeeshop.coffeeshopmanagement.service.ReceiptCost.Mode askCostMode(Product product, int quantity, BigDecimal price) {
        var Mode = com.coffeeshop.coffeeshopmanagement.service.ReceiptCost.Mode.class;
        if (!com.coffeeshop.coffeeshopmanagement.service.ReceiptCost.differs(product.getCost(), price) || quantity <= 0) {
            return com.coffeeshop.coffeeshopmanagement.service.ReceiptCost.Mode.KEEP;
        }
        String name = com.coffeeshop.coffeeshopmanagement.config.SeedNames.product(product.getName());
        String old = product.getCost() == null ? I18n.t("stock.cost.none") : CurrencyUtil.format(product.getCost());
        BigDecimal average = com.coffeeshop.coffeeshopmanagement.service.ReceiptCost.average(product.getStock(), product.getCost(), quantity, price);
        javafx.scene.control.ButtonType keep = new javafx.scene.control.ButtonType(I18n.t("stock.cost.keep"), javafx.scene.control.ButtonBar.ButtonData.CANCEL_CLOSE);
        javafx.scene.control.ButtonType set = new javafx.scene.control.ButtonType(I18n.t("stock.cost.set", CurrencyUtil.format(price)), javafx.scene.control.ButtonBar.ButtonData.YES);
        javafx.scene.control.ButtonType avg = new javafx.scene.control.ButtonType(I18n.t("stock.cost.average", CurrencyUtil.format(average)), javafx.scene.control.ButtonBar.ButtonData.OTHER);
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.CONFIRMATION,
                I18n.t("stock.cost.ask", name, old, CurrencyUtil.format(price)), keep, set, avg);
        alert.setTitle(I18n.t("stock.title"));
        alert.setHeaderText(null);
        AlertUtil.configure(alert);
        AlertUtil.setDefaultButton(alert, keep); // Enter = keep: a habitual Enter must not change the cost
        AlertUtil.setCancelButton(alert, keep);
        javafx.scene.control.ButtonType chosen = alert.showAndWait().orElse(keep);
        return chosen == set ? com.coffeeshop.coffeeshopmanagement.service.ReceiptCost.Mode.REPLACE
                : chosen == avg ? com.coffeeshop.coffeeshopmanagement.service.ReceiptCost.Mode.AVERAGE
                : com.coffeeshop.coffeeshopmanagement.service.ReceiptCost.Mode.KEEP;
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
