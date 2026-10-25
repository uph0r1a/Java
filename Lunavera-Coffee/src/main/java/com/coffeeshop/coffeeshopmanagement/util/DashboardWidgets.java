package com.coffeeshop.coffeeshopmanagement.util;

import com.coffeeshop.coffeeshopmanagement.model.Order;
import com.coffeeshop.coffeeshopmanagement.model.Product;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Builds the rows for the two data-driven dashboard cards (low stock, recent orders) shared by
 * the admin and employee dashboards. Reuses the inventory-row/-name/-description/-danger/
 * -warning style classes both dashboards' stylesheets already define, so the look is unchanged
 * from the original static mockup - only the content is now real.
 */
public final class DashboardWidgets {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");

    private DashboardWidgets() {
    }

    public static void fillLowStock(VBox container, List<Product> products) {
        container.getChildren().clear();
        if (products.isEmpty()) {
            container.getChildren().add(styled(new Label(I18n.t("stock.allfine")), "inventory-description"));
            return;
        }
        for (Product product : products) {
            int stock = product.getStock();
            boolean critical = com.coffeeshop.coffeeshopmanagement.service.StockLevel.isCritical(stock, product.getReorderLevel());
            String badge = I18n.t(stock == 0 ? "stock.badge.out" : critical ? "stock.badge.soon" : "stock.badge.low");
            String badgeStyle = critical ? "inventory-danger" : "inventory-warning";
            container.getChildren().add(row(
                    com.coffeeshop.coffeeshopmanagement.config.SeedNames.product(product.getName()),
                    I18n.t("stock.left", stock, com.coffeeshop.coffeeshopmanagement.service.StockLevel.effective(product.getReorderLevel())),
                    badge, badgeStyle));
        }
    }

    private static final DateTimeFormatter ORDER_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    public static void fillTodayOrders(VBox container, List<Order> orders) {
        container.getChildren().clear();
        if (orders == null || orders.isEmpty()) {
            VBox emptyBox = new VBox(8);
            emptyBox.setAlignment(Pos.CENTER);
            emptyBox.setPadding(new javafx.geometry.Insets(30, 10, 30, 10));
            Label icon = new Label("📋");
            icon.setStyle("-fx-font-size: 26px; -fx-opacity: 0.6;");
            Label msg = styled(new Label(I18n.t("orders.today.empty")), "today-orders-empty");
            emptyBox.getChildren().addAll(icon, msg);
            container.getChildren().add(emptyBox);
            return;
        }

        for (Order order : orders) {
            VBox card = new VBox(5);
            card.getStyleClass().add("today-order-item");

            // Row 1: Order ID, Table info, Time
            HBox topRow = new HBox(8);
            topRow.setAlignment(Pos.CENTER_LEFT);
            Label idLabel = styled(new Label("#" + order.getId()), "today-order-id");
            String tableText = order.getTableNumber() != null ? I18n.t("orders.table", order.getTableNumber()) : I18n.t("orders.takeaway");
            Label tableLabel = styled(new Label("• " + tableText), "today-order-table");

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            LocalDateTime when = order.getPaidAt() != null ? order.getPaidAt() : order.getOrderDate();
            Label timeLabel = styled(new Label(when != null ? when.format(ORDER_TIME_FORMAT) : "-"), "today-order-time");
            topRow.getChildren().addAll(idLabel, tableLabel, spacer, timeLabel);

            // Row 2: Status badge and Total amount
            HBox bottomRow = new HBox(8);
            bottomRow.setAlignment(Pos.CENTER_LEFT);

            String statusText;
            String statusClass;
            switch (order.getStatus()) {
                case PAID -> {
                    statusText = I18n.t("orders.status.paid");
                    statusClass = "today-order-badge-paid";
                }
                case OPEN -> {
                    statusText = I18n.t("orders.status.open");
                    statusClass = "today-order-badge-open";
                }
                case CANCELLED -> {
                    statusText = I18n.t("orders.status.cancelled");
                    statusClass = "today-order-badge-cancelled";
                }
                default -> {
                    statusText = order.getStatus().name();
                    statusClass = "today-order-badge-open";
                }
            }
            Label statusBadge = styled(new Label(statusText), statusClass);

            Region bottomSpacer = new Region();
            HBox.setHgrow(bottomSpacer, Priority.ALWAYS);

            Label totalLabel = styled(new Label(CurrencyUtil.format(order.getTotal())), "today-order-total");
            bottomRow.getChildren().addAll(statusBadge, bottomSpacer, totalLabel);

            card.getChildren().addAll(topRow, bottomRow);
            container.getChildren().add(card);
        }
    }

    /** The line shown instead of an empty table map: what to do about it, different for the administrator and for staff. */
    public static javafx.scene.control.Label emptyTablesNote() {
        javafx.scene.control.Label note = new javafx.scene.control.Label(
                I18n.t(Session.isAdmin() ? "tables.empty.admin" : "tables.empty.staff"));
        note.setWrapText(true);
        note.getStyleClass().add("inventory-description");
        note.setPadding(new javafx.geometry.Insets(16));
        return note;
    }

    public static void fillTableGrid(javafx.scene.layout.GridPane grid, List<com.coffeeshop.coffeeshopmanagement.model.DiningTable> tables, java.util.function.Consumer<com.coffeeshop.coffeeshopmanagement.model.DiningTable> onTableClick) {
        grid.getChildren().clear();
        if (tables.isEmpty()) {
            grid.add(emptyTablesNote(), 0, 0, com.coffeeshop.coffeeshopmanagement.service.TableLayout.COLUMNS, 1);
            return;
        }
        int index = 0;
        for (com.coffeeshop.coffeeshopmanagement.model.DiningTable table : tables) {
            // the place comes from the position in the list, not from the number (TODO 3.48)
            com.coffeeshop.coffeeshopmanagement.service.TableLayout.Cell cell =
                    com.coffeeshop.coffeeshopmanagement.service.TableLayout.cellAt(index++);

            String statusText = I18n.t(table.isOccupied() ? "tables.status.occupied" : "tables.status.free"); // TODO 2.68: a free table is "Trống"
            javafx.scene.control.Button btn = new javafx.scene.control.Button(
                    "☕  " + table.displayName() + "\n" + statusText);
            btn.getStyleClass().add(table.isOccupied() ? "table-button-occupied" : "table-button-empty");
            btn.setMaxWidth(Double.MAX_VALUE);
            btn.setMaxHeight(Double.MAX_VALUE);
            btn.setMinHeight(78.0);
            javafx.scene.layout.GridPane.setHgrow(btn, Priority.ALWAYS);
            javafx.scene.layout.GridPane.setVgrow(btn, Priority.ALWAYS);

            if (onTableClick != null) {
                btn.setOnAction(e -> onTableClick.accept(table));
            }
            grid.add(btn, cell.column(), cell.row());
        }
    }

    private static HBox row(String title, String detail, String badge, String badgeStyle) {
        VBox text = new VBox(3,
                styled(new Label(title), "inventory-name"),
                styled(new Label(detail), "inventory-description"));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = new HBox(text, spacer, styled(new Label(badge), badgeStyle));
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("inventory-row");
        return row;
    }

    private static Label styled(Label label, String styleClass) {
        label.getStyleClass().add(styleClass);
        return label;
    }
}
