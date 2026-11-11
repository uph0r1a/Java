package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.config.SeedNames;
import com.coffeeshop.coffeeshopmanagement.dao.IngredientDAO;
import com.coffeeshop.coffeeshopmanagement.service.IngredientService;
import com.coffeeshop.coffeeshopmanagement.service.ProductService;
import com.coffeeshop.coffeeshopmanagement.model.Ingredient;
import com.coffeeshop.coffeeshopmanagement.model.Product;
import com.coffeeshop.coffeeshopmanagement.service.IngredientCostRules;
import com.coffeeshop.coffeeshopmanagement.service.IngredientRules;
import com.coffeeshop.coffeeshopmanagement.util.AlertUtil;
import com.coffeeshop.coffeeshopmanagement.util.Async;
import com.coffeeshop.coffeeshopmanagement.util.OffScreenThread;
import com.coffeeshop.coffeeshopmanagement.util.CurrencyUtil;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * The recipe tab of {@link IngredientDialog} (TODO 4.41): pick a product, list what one serving takes off which ingredient, save.
 * Saving an empty list removes the recipe, and the product counts cups again.
 */
final class RecipePane {

    private final IngredientService dao;
    private final ComboBox<Product> products = new ComboBox<>();
    private final TableView<IngredientDAO.RecipeLine> lines = new TableView<>();
    private final Label servings = new Label();
    private final Label cost = new Label();
    private final VBox root = new VBox(10);
    private List<Ingredient> stock = List.of(); // read with the recipe, in the background; the servings are worked out from it
    private boolean settingUp; // the list is being filled: the choice it makes is not a click

    RecipePane(IngredientService dao) {
        this.dao = dao;
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
        products.setOnAction(e -> {
            if (!settingUp) showRecipe();
        });
        lines.getColumns().add(column("ingredient.col.name", 240, l -> l.name()));
        lines.getColumns().add(column("recipe.col.amount", 120, l -> String.valueOf(l.amount())));
        lines.getColumns().add(column("ingredient.col.unit", 80, l -> l.unit()));
        lines.getColumns().add(column("recipe.col.cost", 120, l -> l.unitCost() == null ? "-" : money(l.amount() * l.unitCost())));
        lines.setPlaceholder(new Label(I18n.t("recipe.empty")));
        lines.setPrefHeight(240);
        Button add = new Button(I18n.t("recipe.add"));
        add.setOnAction(e -> addLine());
        Button remove = new Button(I18n.t("recipe.remove"));
        remove.setOnAction(e -> {
            IngredientDAO.RecipeLine chosen = lines.getSelectionModel().getSelectedItem();
            if (chosen != null) lines.getItems().remove(chosen);
            updateServings();
        });
        Button save = new Button(I18n.t("recipe.save"));
        save.setOnAction(e -> save());
        Button useCost = new Button(I18n.t("recipe.cost.use"));
        useCost.setOnAction(e -> useCostAsProductCost());
        cost.setWrapText(true);
        Label hint = new Label(I18n.t("recipe.hint"));
        hint.setWrapText(true);
        root.getChildren().addAll(hint, products, lines, servings, cost, new HBox(8, add, remove, save, useCost));
        root.setPadding(new Insets(12));
    }

    VBox node() {
        return root;
    }

    /** What the tab shows after a read: the products (or none), the recipe of the chosen product and the stock. */
    private record Shown(List<Product> products, List<IngredientDAO.RecipeLine> lines, List<Ingredient> stock) {
    }

    @OffScreenThread
    private Shown read(boolean withProducts, int productId) {
        List<Product> all = withProducts ? new ProductService().findAll() : null;
        boolean known = productId >= 0 && (all == null || all.stream().anyMatch(p -> p.getId() == productId));
        return new Shown(all, known ? dao.recipeOf(productId) : List.of(), dao.findAll());
    }

    /** Reads the products again (the tab is opened, or an ingredient changed). TODO 6.14: in the background. */
    void reload() {
        Product kept = products.getValue();
        int keptId = kept == null ? -1 : kept.getId();
        Async.run(() -> read(true, keptId), shown -> {
            settingUp = true;
            try {
                products.setItems(FXCollections.observableArrayList(shown.products()));
                if (kept != null) products.getItems().stream().filter(p -> p.getId() == kept.getId()).findFirst().ifPresent(products::setValue);
            } finally {
                settingUp = false;
            }
            show(shown);
        }, error -> AlertUtil.error(I18n.t("ingredient.title"), String.valueOf(error.getMessage())), root);
    }

    private void show(Shown shown) {
        stock = shown.stock();
        lines.setItems(FXCollections.observableArrayList(products.getValue() == null ? List.of() : shown.lines()));
        updateServings();
    }

    private static String money(long dong) {
        return CurrencyUtil.format(BigDecimal.valueOf(dong));
    }

    private static TableColumn<IngredientDAO.RecipeLine, String> column(String key, double width,
                                                                       java.util.function.Function<IngredientDAO.RecipeLine, String> value) {
        TableColumn<IngredientDAO.RecipeLine, String> column = new TableColumn<>(I18n.t(key));
        column.setCellValueFactory(c -> new SimpleStringProperty(value.apply(c.getValue())));
        column.setPrefWidth(width);
        return column;
    }

    private void showRecipe() {
        Product product = products.getValue();
        if (product == null) {
            lines.setItems(FXCollections.observableArrayList());
            updateServings();
            return;
        }
        int id = product.getId();
        Async.run(() -> read(false, id), shown -> {
            Product now = products.getValue();
            if (now != null && now.getId() == id) show(shown); // the person may have chosen another product meanwhile
        }, error -> AlertUtil.error(I18n.t("ingredient.title"), String.valueOf(error.getMessage())), root);
    }

    private void updateServings() {
        Product product = products.getValue();
        if (product == null) {
            servings.setText("");
            cost.setText("");
            return;
        }
        updateCost(product);
        List<long[]> stockAndAmount = new ArrayList<>();
        for (IngredientDAO.RecipeLine line : lines.getItems()) {
            stock.stream().filter(i -> i.id() == line.ingredientId()).findFirst()
                    .ifPresent(i -> stockAndAmount.add(new long[]{i.stock(), line.amount()}));
        }
        Integer made = IngredientRules.servings(stockAndAmount);
        servings.setText(made == null ? I18n.t("recipe.servings.none") : I18n.t("recipe.servings", made));
    }

    /** The cost of one serving at the ingredients' prices, next to the cost written on the product (TODO 4.51). */
    private void updateCost(Product product) {
        if (lines.getItems().isEmpty()) {
            cost.setText("");
            return;
        }
        IngredientCostRules.RecipeCost recipeCost = IngredientCostRules.recipeCost(
                lines.getItems().stream().map(IngredientDAO.RecipeLine::toCostLine).toList());
        String written = product.getCost() == null ? "-" : CurrencyUtil.format(product.getCost());
        cost.setText(recipeCost.complete()
                ? I18n.t("recipe.cost.total", money(recipeCost.total()), written)
                : I18n.t("recipe.cost.partial", money(recipeCost.total()), String.join(", ", recipeCost.missing()), written));
    }

    /** What the cost check found: a warning to show, or the question to ask before the cost is used. */
    private record CostCheck(String warning, String question) {
    }

    /** Whether the lines on screen are what is saved, line for line (the cost is worked out from what is saved), and the cost itself. */
    @OffScreenThread
    private CostCheck costCheck(Product product, java.util.Set<String> shown) {
        java.util.Set<String> saved = new java.util.HashSet<>();
        for (IngredientDAO.RecipeLine line : dao.recipeOf(product.getId())) saved.add(line.ingredientId() + ":" + line.amount());
        if (!saved.equals(shown)) return new CostCheck(I18n.t("recipe.cost.unsaved"), null);
        IngredientCostRules.RecipeCost recipeCost = dao.recipeCostOf(product.getId());
        if (recipeCost.lines() == 0) return new CostCheck(I18n.t("recipe.cost.norecipe"), null);
        if (!recipeCost.complete()) return new CostCheck(I18n.t("recipe.cost.missing", String.join(", ", recipeCost.missing())), null);
        Product current = new ProductService().findById(product.getId()).orElse(product);
        String old = current.getCost() == null ? "-" : CurrencyUtil.format(current.getCost());
        return new CostCheck(null, I18n.t("recipe.cost.use.ask", SeedNames.product(product.getName()), old, money(recipeCost.total())));
    }

    /** "Use as the product's cost": asks first, shows the old and the new cost, and refuses a recipe that is unsaved or not fully priced. */
    private void useCostAsProductCost() {
        Product product = products.getValue();
        if (product == null) {
            AlertUtil.info(I18n.t("ingredient.title"), I18n.t("recipe.pick"));
            return;
        }
        java.util.Set<String> shown = new java.util.HashSet<>();
        for (IngredientDAO.RecipeLine line : lines.getItems()) shown.add(line.ingredientId() + ":" + line.amount());
        Async.run(() -> costCheck(product, shown), check -> {
            if (check.warning() != null) {
                AlertUtil.warning(I18n.t("recipe.cost.use"), check.warning());
                return;
            }
            if (!AlertUtil.confirm(I18n.t("recipe.cost.use"), check.question())) return;
            Async.run(() -> {
                dao.useRecipeCostAsProductCost(product.getId());
                return Boolean.TRUE;
            }, done -> {
                AlertUtil.info(I18n.t("ingredient.title"), I18n.t("recipe.cost.used"));
                reload();
            }, error -> AlertUtil.error(I18n.t("ingredient.title"), String.valueOf(error.getMessage())), root);
        }, error -> AlertUtil.error(I18n.t("ingredient.title"), String.valueOf(error.getMessage())), root);
    }

    private void addLine() {
        if (products.getValue() == null) {
            AlertUtil.info(I18n.t("ingredient.title"), I18n.t("recipe.pick"));
            return;
        }
        List<Ingredient> free = stock.stream()
                .filter(i -> lines.getItems().stream().noneMatch(l -> l.ingredientId() == i.id())).toList();
        if (free.isEmpty()) {
            AlertUtil.info(I18n.t("ingredient.title"), I18n.t("recipe.noingredients"));
            return;
        }
        ChoiceDialog<Ingredient> choose = new ChoiceDialog<>(free.get(0), free);
        choose.setTitle(I18n.t("recipe.add"));
        choose.setHeaderText(null);
        choose.setContentText(I18n.t("recipe.choose"));
        AlertUtil.configure(choose);
        Optional<Ingredient> ingredient = choose.showAndWait();
        if (ingredient.isEmpty()) return;
        TextInputDialog amount = new TextInputDialog("1");
        amount.setTitle(I18n.t("recipe.add"));
        amount.setHeaderText(null);
        amount.setContentText(I18n.t("recipe.amount.ask", ingredient.get().name(), ingredient.get().unit()));
        AlertUtil.configure(amount);
        Optional<String> typed = amount.showAndWait();
        if (typed.isEmpty()) return;
        OptionalLong value = IngredientDialog.number(typed.get());
        if (value.isEmpty() || value.getAsLong() <= 0) {
            AlertUtil.warning(I18n.t("ingredient.title"), I18n.t("ingredient.val.perserving"));
            return;
        }
        lines.getItems().add(new IngredientDAO.RecipeLine(ingredient.get().id(), ingredient.get().name(), ingredient.get().unit(), value.getAsLong(), ingredient.get().unitCost()));
        updateServings();
    }

    private void save() {
        Product product = products.getValue();
        if (product == null) {
            AlertUtil.info(I18n.t("ingredient.title"), I18n.t("recipe.pick"));
            return;
        }
        List<long[]> toSave = new ArrayList<>();
        for (IngredientDAO.RecipeLine line : lines.getItems()) toSave.add(new long[]{line.ingredientId(), line.amount()});
        if (toSave.isEmpty() && !AlertUtil.confirm(I18n.t("recipe.save"), I18n.t("recipe.remove.ask"))) return;
        Async.run(() -> {
            dao.saveRecipe(product.getId(), toSave);
            return Boolean.TRUE;
        }, done -> AlertUtil.info(I18n.t("ingredient.title"), I18n.t("recipe.saved")),
                error -> AlertUtil.error(I18n.t("ingredient.title"), String.valueOf(error.getMessage())), root);
    }
}
