package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.model.OptionKind;
import com.coffeeshop.coffeeshopmanagement.model.ProductOption;
import com.coffeeshop.coffeeshopmanagement.service.LineOptions;
import com.coffeeshop.coffeeshopmanagement.service.OptionRules;
import com.coffeeshop.coffeeshopmanagement.util.AlertUtil;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.TextLengthLimiter;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * The box where the owner adds or changes one size or extra of a product (TODO 4.47): its kind, name, the price it adds, what it adds to
 * the cost, and for a size how much of the recipe it uses. It only builds the {@link ProductOption}; the list is checked and saved by
 * {@link OptionsPane} and {@code ProductOptionDAO}.
 */
final class OptionEditDialog {

    private OptionEditDialog() {
    }

    /** Shows the box for {@code existing} (null = a new option of {@code productId}); empty when cancelled or when what was typed is not usable. */
    static Optional<ProductOption> show(int productId, ProductOption existing) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(I18n.t(existing == null ? "option.add" : "option.edit"));
        AlertUtil.configure(dialog);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        AlertUtil.setDefaultButton(dialog, ButtonType.OK);
        AlertUtil.setCancelButton(dialog, ButtonType.CANCEL);

        ComboBox<OptionKind> kind = new ComboBox<>();
        kind.getItems().addAll(OptionKind.SIZE, OptionKind.EXTRA);
        kind.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(OptionKind k) {
                return k == null ? "" : k == OptionKind.SIZE ? I18n.t("option.kind.size") : I18n.t("option.kind.extra");
            }

            @Override
            public OptionKind fromString(String s) {
                return null;
            }
        });
        kind.setValue(existing == null ? OptionKind.SIZE : existing.kind());
        kind.setDisable(existing != null); // a size does not turn into an extra: remove it and add the other
        TextField name = new TextField(existing == null ? "" : existing.name());
        TextLengthLimiter.limit(name, OptionRules.NAME_MAX);
        TextField price = new TextField(existing == null ? "0" : existing.priceDelta().toBigInteger().toString());
        TextField cost = new TextField(existing == null ? "0" : existing.costDelta().toBigInteger().toString());
        TextField percent = new TextField(String.valueOf(existing == null ? ProductOption.NORMAL_PERCENT : existing.recipePercent()));
        CheckBox isDefault = new CheckBox(I18n.t("option.default"));
        isDefault.setSelected(existing != null && existing.isDefault());
        CheckBox active = new CheckBox(I18n.t("option.active"));
        active.setSelected(existing == null || existing.active());
        Label percentLabel = new Label(I18n.t("option.percent"));
        Runnable sizeOnly = () -> {
            boolean size = kind.getValue() == OptionKind.SIZE;
            for (javafx.scene.Node node : new javafx.scene.Node[]{percentLabel, percent, isDefault}) {
                node.setVisible(size);
                node.setManaged(size);
            }
        };
        kind.valueProperty().addListener((o, a, b) -> sizeOnly.run());
        sizeOnly.run();

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(16));
        grid.addRow(0, new Label(I18n.t("option.col.kind")), kind);
        grid.addRow(1, new Label(I18n.t("option.col.name")), name);
        grid.addRow(2, new Label(I18n.t("option.price.hint")), price);
        grid.addRow(3, new Label(I18n.t("option.col.cost")), cost);
        grid.addRow(4, percentLabel, percent);
        grid.add(isDefault, 1, 5);
        grid.add(active, 1, 6);
        dialog.getDialogPane().setContent(grid);

        Optional<ButtonType> answer = dialog.showAndWait();
        if (answer.isEmpty() || answer.get() != ButtonType.OK) return Optional.empty();
        if (LineOptions.hasReservedCharacter(name.getText())) {
            AlertUtil.warning(I18n.t("ingredient.title"), I18n.t("option.val.chars"));
            return Optional.empty();
        }
        OptionalLong priceValue = IngredientDialog.number(price.getText());
        OptionalLong costValue = IngredientDialog.number(cost.getText());
        OptionalLong percentValue = kind.getValue() == OptionKind.SIZE ? IngredientDialog.number(percent.getText()) : OptionalLong.of(ProductOption.NORMAL_PERCENT);
        if (priceValue.isEmpty() || costValue.isEmpty() || percentValue.isEmpty()) {
            AlertUtil.warning(I18n.t("ingredient.title"), I18n.t("ingredient.val.number"));
            return Optional.empty();
        }
        boolean size = kind.getValue() == OptionKind.SIZE;
        return Optional.of(new ProductOption(existing == null ? 0 : existing.id(), productId, kind.getValue(), LineOptions.cleanName(name.getText()),
                BigDecimal.valueOf(priceValue.getAsLong()), BigDecimal.valueOf(costValue.getAsLong()),
                (int) Math.min(percentValue.getAsLong(), Integer.MAX_VALUE), size && isDefault.isSelected(), active.isSelected()));
    }
}
