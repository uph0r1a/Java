package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.config.SeedNames;
import com.coffeeshop.coffeeshopmanagement.service.ProductOptionService;
import com.coffeeshop.coffeeshopmanagement.service.ProductService;
import com.coffeeshop.coffeeshopmanagement.model.Product;
import com.coffeeshop.coffeeshopmanagement.model.ProductOption;
import com.coffeeshop.coffeeshopmanagement.util.AlertUtil;
import com.coffeeshop.coffeeshopmanagement.util.Async;
import com.coffeeshop.coffeeshopmanagement.util.CurrencyUtil;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The sizes-and-extras tab of {@link IngredientDialog} (TODO 4.47): pick a product, list its sizes (S / M / L with the price they add and
 * how much of the recipe they use) and its extras (an extra shot, a topping), save. "Copy to the category" gives the same list to every
 * other product of that category, so a menu of twenty drinks is set up once. Saving an empty list removes the options.
 */
final class OptionsPane {

    private final ProductOptionService dao = new ProductOptionService();
    private final ComboBox<Product> products = new ComboBox<>();
    private final TableView<ProductOption> rows = new TableView<>();
    private final VBox root = new VBox(10);

    OptionsPane() {
        products.setPrefWidth(360);
        products.setConverter(new StringConverter<>() {
            @Override
            public String toString(Product p) {
                return p == null ? "" : SeedNames.product(p.getName());
            }

            @Override
            public Product fromString(String s) {
                return null;
            }
        });
        products.setOnAction(e -> showOptions());
        rows.getColumns().add(column("option.col.kind", 80, o -> o.isSize() ? I18n.t("option.kind.size") : I18n.t("option.kind.extra")));
        rows.getColumns().add(column("option.col.name", 160, ProductOption::name));
        rows.getColumns().add(column("option.col.price", 100, o -> OptionPickerDialog.deltaText(o.priceDelta()).strip()));
        rows.getColumns().add(column("option.col.cost", 90, o -> CurrencyUtil.format(o.costDelta())));
        rows.getColumns().add(column("option.col.percent", 80, o -> o.isSize() ? o.recipePercent() + "%" : ""));
        rows.getColumns().add(column("option.col.flags", 110, o -> (o.isDefault() ? I18n.t("option.flag.default") + " " : "")
                + (o.active() ? "" : I18n.t("option.flag.off"))));
        rows.setPlaceholder(new Label(I18n.t("option.empty")));
        rows.setPrefHeight(240);
        Button add = new Button(I18n.t("option.add"));
        add.setOnAction(e -> addOrEdit(null));
        Button edit = new Button(I18n.t("option.edit"));
        edit.setOnAction(e -> addOrEdit(rows.getSelectionModel().getSelectedItem()));
        Button remove = new Button(I18n.t("option.remove"));
        remove.setOnAction(e -> {
            ProductOption chosen = rows.getSelectionModel().getSelectedItem();
            if (chosen != null) rows.getItems().remove(chosen);
        });
        Button save = new Button(I18n.t("option.save"));
        save.setOnAction(e -> save(null));
        Button copy = new Button(I18n.t("option.copy"));
        copy.setOnAction(e -> copyToCategory());
        Label hint = new Label(I18n.t("option.hint"));
        hint.setWrapText(true);
        root.getChildren().addAll(hint, products, rows, new HBox(8, add, edit, remove, save, copy));
        root.setPadding(new Insets(12));
    }

    VBox node() {
        return root;
    }

    /** Reads the products again (the tab is opened). */
    void reload() {
        Product kept = products.getValue();
        Async.run(() -> new ProductService().findAll(),
                list -> {
                    products.setItems(FXCollections.observableArrayList(list));
                    if (kept != null) products.getItems().stream().filter(p -> p.getId() == kept.getId()).findFirst().ifPresent(products::setValue);
                    showOptions();
                },
                error -> AlertUtil.error(I18n.t("ingredient.title"), String.valueOf(error.getMessage())), products);
    }

    private static TableColumn<ProductOption, String> column(String key, double width, java.util.function.Function<ProductOption, String> value) {
        TableColumn<ProductOption, String> column = new TableColumn<>(I18n.t(key));
        column.setCellValueFactory(c -> new SimpleStringProperty(value.apply(c.getValue())));
        column.setPrefWidth(width);
        return column;
    }

    /** Shows the sizes and extras of the chosen product, read in the background; an answer for a product no longer chosen is dropped. */
    private void showOptions() {
        Product product = products.getValue();
        if (product == null) {
            rows.setItems(FXCollections.observableArrayList());
            return;
        }
        Async.run(() -> dao.findByProduct(product.getId()),
                saved -> {
                    Product now = products.getValue();
                    if (now != null && now.getId() == product.getId()) rows.setItems(FXCollections.observableArrayList(saved));
                },
                error -> AlertUtil.error(I18n.t("ingredient.title"), String.valueOf(error.getMessage())), rows);
    }

    private void addOrEdit(ProductOption existing) {
        Product product = products.getValue();
        if (product == null) {
            AlertUtil.info(I18n.t("ingredient.title"), I18n.t("recipe.pick"));
            return;
        }
        Optional<ProductOption> typed = OptionEditDialog.show(product.getId(), existing);
        if (typed.isEmpty()) return;
        ProductOption option = typed.get();
        if (existing == null) {
            rows.getItems().add(option);
        } else {
            rows.getItems().set(rows.getItems().indexOf(existing), option);
        }
    }

    /**
     * Saves the rows shown and, when that worked, runs {@code afterwards} (may be null). The question is asked here, on the screen
     * thread; the save and the read of what was stored run in the background, and the whole pane is held until they end.
     */
    private void save(Runnable afterwards) {
        Product product = products.getValue();
        if (product == null) {
            AlertUtil.info(I18n.t("ingredient.title"), I18n.t("recipe.pick"));
            return;
        }
        if (rows.getItems().isEmpty() && !AlertUtil.confirm(I18n.t("option.save"), I18n.t("option.remove.ask"))) return;
        List<ProductOption> toSave = new ArrayList<>(rows.getItems());
        Async.run(() -> {
                    dao.save(product.getId(), toSave);
                    return dao.findByProduct(product.getId()); // as saved: names cleaned, one default size
                },
                saved -> {
                    Product now = products.getValue();
                    if (now != null && now.getId() == product.getId()) rows.setItems(FXCollections.observableArrayList(saved));
                    AlertUtil.info(I18n.t("ingredient.title"), I18n.t("option.saved"));
                    if (afterwards != null) afterwards.run();
                },
                error -> AlertUtil.error(I18n.t("ingredient.title"), String.valueOf(error.getMessage())), root);
    }

    private void copyToCategory() {
        Product product = products.getValue();
        if (product == null) {
            AlertUtil.info(I18n.t("ingredient.title"), I18n.t("recipe.pick"));
            return;
        }
        if (!AlertUtil.confirm(I18n.t("option.copy"), I18n.t("option.copy.ask", SeedNames.product(product.getName())))) return;
        save(() -> Async.run(() -> dao.copyToCategory(product.getId()),
                changed -> AlertUtil.info(I18n.t("ingredient.title"), I18n.t("option.copied", changed)),
                error -> AlertUtil.error(I18n.t("ingredient.title"), String.valueOf(error.getMessage())), root));
    }
}
