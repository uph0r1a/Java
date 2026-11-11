package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.model.LineChoice;
import com.coffeeshop.coffeeshopmanagement.model.Product;
import com.coffeeshop.coffeeshopmanagement.model.ProductOption;
import com.coffeeshop.coffeeshopmanagement.service.LineOptions;
import com.coffeeshop.coffeeshopmanagement.service.OptionRules;
import com.coffeeshop.coffeeshopmanagement.util.AlertUtil;
import com.coffeeshop.coffeeshopmanagement.util.CurrencyUtil;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.config.SeedNames;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The box on the sales screen that opens when the cashier taps a product that has sizes or extras (TODO 4.47): one size to pick (the
 * usual one is already chosen) and any extras to tick, with the price of one piece shown as it changes. Nothing here touches the
 * database; the caller puts the answer on the cart line ({@code Cart.add(product, choice)}).
 */
final class OptionPickerDialog {

    private OptionPickerDialog() {
    }

    /** "+10.000 ₫" / "-5.000 ₫" for what an option adds to the price; empty when it adds nothing. */
    static String deltaText(BigDecimal delta) {
        if (delta.signum() == 0) return "";
        return (delta.signum() > 0 ? " +" : " -") + CurrencyUtil.format(delta.abs());
    }

    /** Asks for the size and extras of one piece; empty when the cashier cancelled. {@code options} are the switched-on options of the product. */
    static Optional<LineChoice> show(Product product, List<ProductOption> options) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(I18n.t("option.pick.title"));
        AlertUtil.configure(dialog);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        AlertUtil.setDefaultButton(dialog, ButtonType.OK);
        AlertUtil.setCancelButton(dialog, ButtonType.CANCEL);

        Label heading = new Label(I18n.t("option.pick.heading", SeedNames.product(product.getName())));
        heading.setWrapText(true);
        Label total = new Label();
        total.setStyle("-fx-font-weight: bold;");
        VBox content = new VBox(12, heading);
        content.setPadding(new Insets(16));
        content.setPrefWidth(420);

        ToggleGroup group = new ToggleGroup();
        List<RadioButton> sizeButtons = new ArrayList<>();
        List<ProductOption> sizes = OptionRules.sizesOf(options).stream().filter(ProductOption::active).toList();
        if (!sizes.isEmpty()) {
            FlowPane row = new FlowPane(12, 8);
            for (ProductOption size : sizes) {
                RadioButton button = new RadioButton(size.name() + deltaText(size.priceDelta()));
                button.setToggleGroup(group);
                button.setUserData(size);
                button.setSelected(size.isDefault());
                sizeButtons.add(button);
                row.getChildren().add(button);
            }
            if (group.getSelectedToggle() == null) sizeButtons.get(0).setSelected(true);
            content.getChildren().addAll(new Label(I18n.t("option.pick.size")), row);
        }

        List<CheckBox> extraBoxes = new ArrayList<>();
        List<ProductOption> extras = OptionRules.extrasOf(options).stream().filter(ProductOption::active).toList();
        if (!extras.isEmpty()) {
            FlowPane row = new FlowPane(12, 8);
            for (ProductOption extra : extras) {
                CheckBox box = new CheckBox(extra.name() + deltaText(extra.priceDelta()));
                box.setUserData(extra);
                extraBoxes.add(box);
                row.getChildren().add(box);
            }
            content.getChildren().addAll(new Label(I18n.t("option.pick.extras")), row);
        }
        content.getChildren().add(total);

        Runnable refresh = () -> total.setText(I18n.t("option.pick.total", CurrencyUtil.format(
                product.getPrice().add(currentChoice(group, extraBoxes).priceDelta()).max(BigDecimal.ZERO))));
        group.selectedToggleProperty().addListener((obs, was, now) -> refresh.run());
        for (CheckBox box : extraBoxes) box.selectedProperty().addListener((obs, was, now) -> refresh.run());
        refresh.run();
        dialog.getDialogPane().setContent(content);

        Optional<ButtonType> answer = dialog.showAndWait();
        if (answer.isPresent() && answer.get() == ButtonType.OK) return Optional.of(currentChoice(group, extraBoxes));
        return Optional.empty();
    }

    private static LineChoice currentChoice(ToggleGroup group, List<CheckBox> extraBoxes) {
        ProductOption size = group.getSelectedToggle() == null ? null : (ProductOption) group.getSelectedToggle().getUserData();
        List<ProductOption> ticked = new ArrayList<>();
        for (CheckBox box : extraBoxes) if (box.isSelected()) ticked.add((ProductOption) box.getUserData());
        return LineOptions.choice(size, ticked);
    }
}
