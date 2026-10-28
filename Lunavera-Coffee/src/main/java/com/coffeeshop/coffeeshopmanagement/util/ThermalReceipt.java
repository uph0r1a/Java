package com.coffeeshop.coffeeshopmanagement.util;

import com.coffeeshop.coffeeshopmanagement.model.Order;
import com.coffeeshop.coffeeshopmanagement.model.OrderItem;
import com.coffeeshop.coffeeshopmanagement.model.OrderStatus;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * The receipt drawn for a narrow paper roll (58 mm or 80 mm), black on white, one column, built for exactly the
 * printable width the printer reports (TODO 4.3). The on-screen receipt is about 520 px wide; shrunk to a 58 mm roll
 * its letters would be unreadably small, so for narrow paper this layout is printed instead.
 *
 * <p>Fonts are set in code because the printed node is not part of a window, so the shared style sheet does not reach it.
 */
public final class ThermalReceipt {

    /** Paper whose printable width (in points, 1/72 inch) is below this gets the narrow layout (about 106 mm). */
    public static final double NARROW_BELOW_POINTS = 300;

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");

    private ThermalReceipt() {}

    public static boolean isNarrow(double printableWidthPoints) {
        return printableWidthPoints < NARROW_BELOW_POINTS;
    }

    /** Font size for a roll: about 8 pt on 58 mm paper, up to 10 pt on 80 mm. */
    static double fontSizeFor(double width) {
        return Math.max(7, Math.min(10, width / 19));
    }

    public static VBox build(Order order, List<OrderItem> items, String employeeName, String customerName,
                             Image qrImage, double width) {
        return build(order, items, employeeName, customerName, qrImage, width, null);
    }

    /** {@code pointsBalance} is the customer's total points when known (TODO 5.28); the line is left out when no customer is attached. */
    public static VBox build(Order order, List<OrderItem> items, String employeeName, String customerName,
                             Image qrImage, double width, Integer pointsBalance) {
        double size = fontSizeFor(width);
        VBox root = new VBox(2);
        root.setPrefWidth(width);
        root.setMinWidth(width);
        root.setMaxWidth(width);
        root.setPadding(new Insets(2, 0, 8, 0));
        root.setStyle("-fx-background-color: white;");

        root.getChildren().add(centered(com.coffeeshop.coffeeshopmanagement.config.ShopSettings.cachedName().toUpperCase(java.util.Locale.ROOT), size + 3, true, width));
        for (String detail : ReceiptText.shopDetailLines()) {
            root.getChildren().add(centered(detail, size, false, width)); // address, phone, tax code (TODO 4.7, 4.22)
        }
        root.getChildren().add(centered(ReceiptText.t("receipt.title"), size, true, width));
        if (order.getStatus() == OrderStatus.CANCELLED) {
            root.getChildren().add(centered(ReceiptText.t("receipt.cancelled"), size, true, width));
        }
        root.getChildren().add(rule(width));
        java.time.LocalDateTime shown = order.getPaidAt() != null ? order.getPaidAt() : order.getOrderDate();
        root.getChildren().add(line(ReceiptText.t("receipt.line.orderid", order.getId()), size, false, width));
        root.getChildren().add(line(ReceiptText.t("receipt.line.time", shown == null ? "-" : shown.format(TIME)), size, false, width));
        root.getChildren().add(line(ReceiptText.t("receipt.line.employee", employeeName == null ? "-" : employeeName), size, false, width));
        root.getChildren().add(line(ReceiptText.table(order.getTableNumber()), size, false, width));
        root.getChildren().add(line(ReceiptText.t("receipt.line.customer", customerName == null ? ReceiptText.t("receipt.guest") : customerName), size, false, width));
        Integer earned = com.coffeeshop.coffeeshopmanagement.service.LoyaltyPolicy.pointsEarnedBy(order);
        if (earned != null) {
            root.getChildren().add(line(ReceiptText.points(earned, pointsBalance), size, false, width));
        }
        String spentLine = ReceiptText.pointsSpent(order); // TODO 4.16
        if (spentLine != null) {
            root.getChildren().add(line(spentLine, size, false, width));
        }
        root.getChildren().add(rule(width));

        for (OrderItem item : items) {
            root.getChildren().add(line(ReceiptText.productName(item.getProductName()), size, true, width));
            String note = com.coffeeshop.coffeeshopmanagement.service.OrderNote.clean(item.getNote()); // TODO 4.9
            if (note != null) {
                root.getChildren().add(line("  - " + note, size - 1, false, width));
            }
            root.getChildren().add(pair(item.getQuantity() + " x " + CurrencyUtil.format(item.getUnitPrice()),
                    CurrencyUtil.format(item.getLineTotal()), size, false, width));
        }
        root.getChildren().add(rule(width));
        root.getChildren().add(pair(ReceiptText.t("receipt.subtotal"), CurrencyUtil.format(order.getSubtotal()), size, false, width));
        root.getChildren().add(pair(ReceiptText.t("receipt.discount"), CurrencyUtil.format(order.getDiscount()), size, false, width));
        if (order.getDiscountReason() != null && !order.getDiscountReason().isBlank()) {
            root.getChildren().add(line(ReceiptText.t("receipt.line.reason", order.getDiscountReason()), size - 1, false, width));
        }
        root.getChildren().add(pair(ReceiptText.t("receipt.total"), CurrencyUtil.format(order.getTotal()), size + 2, true, width));
        root.getChildren().add(line(ReceiptText.t("receipt.line.method", ReceiptText.paymentOf(order)), size, false, width));
        for (String extra : ReceiptText.paymentExtras(order)) { // cash received and change, or the transfer reference (TODO 3.17, 4.31)
            root.getChildren().add(line(extra, size, false, width));
        }
        root.getChildren().add(rule(width));

        if (qrImage != null) {
            ImageView view = new ImageView(qrImage);
            double side = Math.min(width * 0.62, 120);
            view.setFitWidth(side);
            view.setFitHeight(side);
            view.setSmooth(false);
            HBox box = new HBox(view);
            box.setAlignment(Pos.CENTER);
            box.setPadding(new Insets(4, 0, 2, 0));
            root.getChildren().add(box);
            root.getChildren().add(centered(ReceiptText.t("receipt.line.qr", order.getId()), size - 1, false, width));
        }
        root.getChildren().add(centered(ReceiptText.t("receipt.thanks"), size, false, width));
        return root;
    }

    /** Lays the node out so its height is known (it is printed without ever being shown in a window). */
    public static void layOut(VBox root) {
        if (root.getScene() == null) new Scene(root);
        root.applyCss();
        root.layout();
    }

    private static Text text(String value, double size, boolean bold, double width, TextAlignment alignment) {
        Text t = new Text(value);
        t.setFont(Font.font(AppStyle.FONT_FAMILY, bold ? FontWeight.BOLD : FontWeight.NORMAL, size));
        t.setFill(Color.BLACK);
        t.setWrappingWidth(width);
        t.setTextAlignment(alignment);
        return t;
    }

    private static Text line(String value, double size, boolean bold, double width) {
        return text(value, size, bold, width, TextAlignment.LEFT);
    }

    private static Text centered(String value, double size, boolean bold, double width) {
        return text(value, size, bold, width, TextAlignment.CENTER);
    }

    /** Left text and right-aligned amount on one row; the amount keeps its room, the left text wraps in the rest. */
    private static HBox pair(String left, String right, double size, boolean bold, double width) {
        Text r = text(right, size, bold, 0, TextAlignment.RIGHT);
        double rightWidth = r.getLayoutBounds().getWidth();
        Text l = text(left, size, bold, Math.max(20, width - rightWidth - 6), TextAlignment.LEFT);
        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        HBox row = new HBox(4, l, gap, r);
        row.setPrefWidth(width);
        row.setMaxWidth(width);
        row.setAlignment(Pos.TOP_LEFT);
        return row;
    }

    private static Region rule(double width) {
        Region r = new Region();
        r.setPrefSize(width, 1);
        r.setMinHeight(1);
        r.setMaxHeight(1);
        r.setStyle("-fx-background-color: black;");
        VBox.setMargin(r, new Insets(3, 0, 3, 0));
        return r;
    }
}
