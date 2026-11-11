package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.service.ProductService;
import com.coffeeshop.coffeeshopmanagement.service.CategoryService;
import com.coffeeshop.coffeeshopmanagement.service.PromotionService;
import com.coffeeshop.coffeeshopmanagement.model.Category;
import com.coffeeshop.coffeeshopmanagement.model.Product;
import com.coffeeshop.coffeeshopmanagement.service.AutoPromotionRules.Kind;
import com.coffeeshop.coffeeshopmanagement.service.AutoPromotionRules.Promotion;
import com.coffeeshop.coffeeshopmanagement.service.AutoPromotionRules.Target;
import com.coffeeshop.coffeeshopmanagement.service.AutoPromotionService;
import com.coffeeshop.coffeeshopmanagement.util.AlertUtil;
import com.coffeeshop.coffeeshopmanagement.util.Async;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.MoneyParser;
import com.coffeeshop.coffeeshopmanagement.util.Session;
import com.coffeeshop.coffeeshopmanagement.util.SessionGuard;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * "Khuyến mãi tự động" (TODO 4.55): the administrator's window for automatic promotions. Make one (a percentage off, a fixed price,
 * buy X get Y free, or a combo), say what it is for (products or whole categories), on which days, hours and dates, see every promotion
 * and switch one off or on, or delete it. The rules are in {@code AutoPromotionRules} and the storage in {@code PromotionDAO} (through {@link PromotionService}); the till
 * applies the promotions that are on, by itself, when a bill is paid. To change a promotion, switch it off and make a new one.
 */
public final class PromotionDialog {

    private PromotionDialog() {
    }

    /** One thing in the target picker: a category or a product. */
    private record Choice(Integer productId, Integer categoryId, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    public static void show() {
        if (!SessionGuard.validateNow()) {
            SessionGuard.forceLogout();
            return;
        }
        if (!Session.isAdmin()) {
            AlertUtil.warning(I18n.t("admin.only.title"), I18n.t("promo.adminonly"));
            return;
        }
        // TODO 6.14: the categories and products of the target picker are read off the screen thread; the window opens when they are here
        Async.run(() -> {
                    List<Choice> choices = new ArrayList<>();
                    java.util.Map<Integer, String> categoryNames = new java.util.HashMap<>();
                    java.util.Map<Integer, String> productNames = new java.util.HashMap<>();
                    for (Category category : new CategoryService().findAll()) {
                        categoryNames.put(category.getId(), category.getName());
                        choices.add(new Choice(null, category.getId(), I18n.t("promo.choice.category", category.getName())));
                    }
                    for (Product product : new ProductService().findAll()) {
                        productNames.put(product.getId(), product.getName());
                        choices.add(new Choice(product.getId(), null, I18n.t("promo.choice.product", product.getName())));
                    }
                    return new Loaded(choices, categoryNames, productNames);
                },
                loaded -> open(loaded.choices(), loaded.categoryNames(), loaded.productNames()),
                error -> AlertUtil.error(I18n.t("promo.title"), error.getMessage()));
    }

    /** What the window needs from the database before it can open. */
    private record Loaded(List<Choice> choices, java.util.Map<Integer, String> categoryNames, java.util.Map<Integer, String> productNames) {
    }

    private static void open(List<Choice> choices, java.util.Map<Integer, String> categoryNames, java.util.Map<Integer, String> productNames) {
        PromotionService dao = new PromotionService();
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(I18n.t("promo.title"));
        AlertUtil.configure(dialog);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.setResizable(true);

        TextField nameField = new TextField();
        nameField.setPromptText(I18n.t("promo.name.prompt"));
        ComboBox<Kind> kindBox = new ComboBox<>(FXCollections.observableArrayList(Kind.values()));
        kindBox.setValue(Kind.PERCENT);
        kindBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(Kind kind) {
                return kind == null ? "" : AutoPromotionService.kindName(kind);
            }

            @Override
            public Kind fromString(String text) {
                return null;
            }
        });
        TextField percentField = new TextField();
        percentField.setPromptText(I18n.t("promo.percent.prompt"));
        TextField priceField = new TextField();
        priceField.setPromptText(I18n.t("promo.price.prompt"));
        TextField buyField = new TextField();
        buyField.setPromptText(I18n.t("promo.buy.prompt"));
        TextField freeField = new TextField();
        freeField.setPromptText(I18n.t("promo.free.prompt"));
        Runnable showFieldsForKind = () -> {
            Kind kind = kindBox.getValue();
            percentField.setDisable(kind != Kind.PERCENT);
            priceField.setDisable(kind != Kind.FIXED_PRICE && kind != Kind.COMBO);
            buyField.setDisable(kind != Kind.BUY_GET);
            freeField.setDisable(kind != Kind.BUY_GET);
        };
        kindBox.valueProperty().addListener((o, was, now) -> showFieldsForKind.run());
        showFieldsForKind.run();

        ComboBox<Choice> targetBox = new ComboBox<>(FXCollections.observableArrayList(choices));
        targetBox.setPromptText(I18n.t("promo.target.prompt"));
        targetBox.setPrefWidth(320);
        TextField quantityField = new TextField("1");
        quantityField.setPrefColumnCount(3);
        quantityField.setPromptText(I18n.t("promo.quantity.prompt"));
        ListView<Target> targetList = new ListView<>();
        targetList.setPrefHeight(90);
        targetList.setPlaceholder(new Label(I18n.t("promo.targets.empty")));
        targetList.setCellFactory(list -> new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(Target target, boolean empty) {
                super.updateItem(target, empty);
                setText(empty || target == null ? null : describe(target, categoryNames, productNames));
            }
        });
        Button addTarget = new Button(I18n.t("promo.target.add"));
        Button removeTarget = new Button(I18n.t("promo.target.remove"));
        removeTarget.setDisable(true);
        targetList.getSelectionModel().selectedItemProperty().addListener((o, was, now) -> removeTarget.setDisable(now == null));
        Label status = new Label();
        status.setWrapText(true);
        addTarget.setOnAction(e -> {
            Choice picked = targetBox.getValue();
            Long quantity = parseLong(quantityField.getText());
            if (picked == null || quantity == null || quantity < 1 || quantity > 99) {
                status.setText(I18n.t("promo.val.target"));
                return;
            }
            // a category is for "any product of it": a piece count means nothing there, and a combo needs products
            int pieces = picked.productId() != null && kindBox.getValue() == Kind.COMBO ? quantity.intValue() : 1;
            Target target = picked.productId() != null ? Target.product(picked.productId(), pieces) : Target.category(picked.categoryId());
            if (!targetList.getItems().contains(target)) targetList.getItems().add(target);
            status.setText("");
        });
        removeTarget.setOnAction(e -> targetList.getItems().remove(targetList.getSelectionModel().getSelectedItem()));

        List<CheckBox> dayBoxes = new ArrayList<>();
        HBox days = new HBox(8);
        for (DayOfWeek day : DayOfWeek.values()) {
            CheckBox box = new CheckBox(AutoPromotionService.dayName(day));
            dayBoxes.add(box);
            days.getChildren().add(box);
        }
        TextField fromTime = new TextField();
        fromTime.setPromptText("14:00");
        fromTime.setPrefColumnCount(5);
        TextField toTime = new TextField();
        toTime.setPromptText("16:00");
        toTime.setPrefColumnCount(5);
        DatePicker fromDate = com.coffeeshop.coffeeshopmanagement.util.DateBoxes.picker();
        DatePicker toDate = com.coffeeshop.coffeeshopmanagement.util.DateBoxes.picker();
        Button addButton = new Button(I18n.t("promo.add"));
        addButton.setDefaultButton(true);
        Label hint = new Label(I18n.t("promo.hint"));
        hint.setWrapText(true);
        hint.setStyle("-fx-text-fill: #6d4c41;");

        TableView<Promotion> table = new TableView<>();
        table.setPrefHeight(220);
        table.setPlaceholder(new Label(I18n.t("promo.empty")));
        table.getColumns().add(column(I18n.t("promo.col.name"), 150, Promotion::name));
        table.getColumns().add(column(I18n.t("promo.col.worth"), 130, AutoPromotionService::worth));
        table.getColumns().add(column(I18n.t("promo.col.when"), 230, AutoPromotionService::when));
        table.getColumns().add(column(I18n.t("promo.col.targets"), 170, p -> p.targets().stream()
                .map(t -> describe(t, categoryNames, productNames)).collect(java.util.stream.Collectors.joining(", "))));
        table.getColumns().add(column(I18n.t("promo.col.state"), 70, p -> I18n.t(p.active() ? "promo.on" : "promo.off")));
        Button toggleButton = new Button(I18n.t("promo.toggle"));
        Button deleteButton = new Button(I18n.t("promo.delete"));
        toggleButton.setDisable(true);
        deleteButton.setDisable(true);
        table.getSelectionModel().selectedItemProperty().addListener((o, was, now) -> {
            toggleButton.setDisable(now == null);
            deleteButton.setDisable(now == null);
        });

        // TODO 6.14: the list is read off the screen thread
        Runnable reload = () -> {
            AutoPromotionService.forgetPreviewCache(); // the cart of this till shows a change at once
            Async.run(dao::findAll,
                    found -> table.getItems().setAll(found),
                    error -> AlertUtil.error(I18n.t("promo.title"), error.getMessage()));
        };
        reload.run();

        addButton.setOnAction(e -> {
            Kind kind = kindBox.getValue();
            Long percent = percentField.isDisabled() ? Long.valueOf(0) : parseLong(percentField.getText());
            Long price = priceField.isDisabled() ? Long.valueOf(0) : parseLong(priceField.getText());
            Long buy = buyField.isDisabled() ? Long.valueOf(0) : parseLong(buyField.getText());
            Long free = freeField.isDisabled() ? Long.valueOf(0) : parseLong(freeField.getText());
            LocalTime from = parseTime(fromTime.getText());
            LocalTime to = parseTime(toTime.getText());
            boolean timeTyped = !blank(fromTime.getText()) || !blank(toTime.getText());
            if (percent == null || price == null || buy == null || free == null || percent > 1000 || buy > 99 || free > 99) {
                status.setText(I18n.t("promo.val.number"));
                return;
            }
            if (timeTyped && ((!blank(fromTime.getText()) && from == null) || (!blank(toTime.getText()) && to == null))) {
                status.setText(I18n.t("promo.val.time"));
                return;
            }
            EnumSet<DayOfWeek> chosen = EnumSet.noneOf(DayOfWeek.class);
            for (int i = 0; i < dayBoxes.size(); i++) {
                if (dayBoxes.get(i).isSelected()) chosen.add(DayOfWeek.of(i + 1));
            }
            Promotion made = new Promotion(0, nameField.getText() == null ? "" : nameField.getText(), kind, true, percent.intValue(), price,
                    buy.intValue(), free.intValue(), chosen, from, to, fromDate.getValue(), toDate.getValue(), new ArrayList<>(targetList.getItems()));
            Async.run(() -> {
                        dao.save(made); // the DAO checks it again (name, hours, dates, numbers, targets) and says what is wrong in plain words
                        return null;
                    },
                    saved -> {
                        reload.run();
                        nameField.clear();
                        percentField.clear();
                        priceField.clear();
                        buyField.clear();
                        freeField.clear();
                        fromTime.clear();
                        toTime.clear();
                        fromDate.setValue(null);
                        toDate.setValue(null);
                        targetList.getItems().clear();
                        dayBoxes.forEach(box -> box.setSelected(false));
                        status.setText("");
                        nameField.requestFocus();
                    },
                    error -> status.setText(error.getMessage()),
                    addButton);
        });
        toggleButton.setOnAction(e -> {
            Promotion selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) return;
            final int id = selected.id();
            final boolean on = !selected.active();
            Async.run(() -> {
                        dao.setActive(id, on);
                        return null;
                    },
                    done -> reload.run(),
                    error -> AlertUtil.error(I18n.t("promo.title"), error.getMessage()),
                    toggleButton);
        });
        deleteButton.setOnAction(e -> {
            Promotion selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) return;
            if (!AlertUtil.confirm(I18n.t("promo.title"), I18n.t("promo.delete.confirm", selected.name()))) return;
            final int id = selected.id();
            Async.run(() -> {
                        dao.delete(id);
                        return null;
                    },
                    done -> reload.run(),
                    error -> AlertUtil.error(I18n.t("promo.title"), error.getMessage()),
                    deleteButton);
        });

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(8);
        int row = 0;
        form.addRow(row++, new Label(I18n.t("promo.label.name")), nameField);
        form.addRow(row++, new Label(I18n.t("promo.label.kind")), kindBox);
        form.addRow(row++, new Label(I18n.t("promo.label.percent")), percentField);
        form.addRow(row++, new Label(I18n.t("promo.label.price")), priceField);
        form.addRow(row++, new Label(I18n.t("promo.label.buy")), new HBox(8, buyField, new Label(I18n.t("promo.label.free")), freeField));
        form.addRow(row++, new Label(I18n.t("promo.label.target")), new VBox(4, new HBox(8, targetBox, new Label(I18n.t("promo.label.quantity")),
                quantityField, addTarget), targetList, removeTarget));
        form.addRow(row++, new Label(I18n.t("promo.label.days")), days);
        form.addRow(row++, new Label(I18n.t("promo.label.hours")), new HBox(8, fromTime, new Label("–"), toTime));
        form.addRow(row++, new Label(I18n.t("promo.label.from")), fromDate);
        form.addRow(row++, new Label(I18n.t("promo.label.to")), toDate);
        form.add(addButton, 1, row);
        GridPane.setHgrow(nameField, Priority.ALWAYS);

        VBox content = new VBox(12, hint, form, status, table, new HBox(8, toggleButton, deleteButton));
        content.setPadding(new Insets(16));
        content.setPrefWidth(820);
        javafx.scene.control.ScrollPane scroll = new javafx.scene.control.ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(620);
        dialog.getDialogPane().setContent(scroll);
        javafx.application.Platform.runLater(nameField::requestFocus);
        dialog.showAndWait();
    }

    private static String describe(Target target, java.util.Map<Integer, String> categories, java.util.Map<Integer, String> products) {
        if (target.productId() != null) {
            String name = products.getOrDefault(target.productId(), "#" + target.productId());
            return target.quantity() > 1 ? target.quantity() + " × " + name : name;
        }
        return I18n.t("promo.choice.category", categories.getOrDefault(target.categoryId(), "#" + target.categoryId()));
    }

    private static boolean blank(String text) {
        return text == null || text.isBlank();
    }

    /** "14:00" as a time; null for blank or anything else. */
    private static LocalTime parseTime(String text) {
        if (blank(text)) return null;
        try {
            return LocalTime.parse(text.trim().length() == 4 ? "0" + text.trim() : text.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** A whole number typed in the field; 0 for blank; null for anything that is not one. */
    private static Long parseLong(String text) {
        if (blank(text)) return 0L;
        BigDecimal dong = MoneyParser.parseOptionalDong(text);
        return dong == null ? null : dong.longValue();
    }

    private static TableColumn<Promotion, String> column(String title, double width, java.util.function.Function<Promotion, String> text) {
        TableColumn<Promotion, String> column = new TableColumn<>(title);
        column.setPrefWidth(width);
        column.setSortable(false);
        column.setCellValueFactory(data -> new SimpleStringProperty(text.apply(data.getValue())));
        return column;
    }
}
