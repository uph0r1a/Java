package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.dao.ReportDAO;
import com.coffeeshop.coffeeshopmanagement.dao.ReportDAO.DailySales;
import com.coffeeshop.coffeeshopmanagement.dao.ReportDAO.ProductSales;
import com.coffeeshop.coffeeshopmanagement.dao.ReportDAO.Summary;
import com.coffeeshop.coffeeshopmanagement.util.AlertUtil;
import com.coffeeshop.coffeeshopmanagement.util.Async;
import com.coffeeshop.coffeeshopmanagement.util.CurrencyUtil;
import com.coffeeshop.coffeeshopmanagement.util.Session;

import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Admin-only sales report (opened from the admin dashboard): totals for a date range, best
 * sellers, and revenue per day, with CSV export. Non-modal window built in code for the same
 * reasons as OrderHistoryWindow (no sidebar needed; avoids Linux dialog-modality trouble).
 */
public final class ReportWindow {

    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final int TOP_PRODUCTS_LIMIT = 20;

    private final ReportDAO reportDAO = new ReportDAO();
    private final DatePicker fromPicker = new DatePicker(LocalDate.now().withDayOfMonth(1));
    private final DatePicker toPicker = new DatePicker(LocalDate.now());
    private final Label summaryLabel = new Label("Đang tải...");
    private final TableView<ProductSales> productTable = new TableView<>();
    private final TableView<DailySales> dailyTable = new TableView<>();
    private final Label paymentLabel = new Label("");
    private final Button exportButton = new Button("Xuất CSV");

    private Summary lastSummary;
    private List<ProductSales> lastProducts = List.of();
    private List<DailySales> lastDaily = List.of();
    private List<ReportDAO.PaymentSplit> lastSplit = List.of();
    private ReportDAO.Profit lastProfit;
    private LocalDate lastFrom;
    private LocalDate lastTo;

    private static javafx.stage.Stage instance;

    private ReportWindow() {
    }

    public static void show() {
        // Re-read the account first: a demoted or locked admin must not keep the report (TODO 2.43).
        if (!com.coffeeshop.coffeeshopmanagement.util.SessionGuard.validateNow()) {
            com.coffeeshop.coffeeshopmanagement.util.SessionGuard.forceLogout();
            return;
        }
        if (!Session.isAdmin()) {
            AlertUtil.warning("Không đủ quyền", "Chỉ quản trị viên mới xem được báo cáo doanh thu.");
            return;
        }
        if (instance != null && instance.isShowing()) {
            instance.toFront(); // one report window at a time (TODO 2.30)
            return;
        }
        ReportWindow window = new ReportWindow();
        window.open();
    }

    private void open() {
        productTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        dailyTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        productTable.getColumns().add(this.<ProductSales>column("Sản phẩm", 240, p -> p.productName()));
        productTable.getColumns().add(this.<ProductSales>column("Số lượng bán", 110, p -> String.valueOf(p.quantity())));
        productTable.getColumns().add(this.<ProductSales>column("Doanh thu", 130, p -> CurrencyUtil.format(p.revenue())));
        productTable.getColumns().add(this.<ProductSales>column("Lợi nhuận gộp", 130, p -> profitText(p.profit())));
        productTable.setPlaceholder(new Label("Chưa có sản phẩm nào được bán trong khoảng này"));

        dailyTable.getColumns().add(this.<DailySales>column("Ngày", 120, d -> d.date().format(DAY_FORMAT)));
        dailyTable.getColumns().add(this.<DailySales>column("Số đơn", 80, d -> String.valueOf(d.orders())));
        dailyTable.getColumns().add(this.<DailySales>column("Doanh thu", 140, d -> CurrencyUtil.format(d.revenue())));
        dailyTable.getColumns().add(this.<DailySales>column("Lợi nhuận gộp", 130, d -> profitText(d.profit())));
        dailyTable.setPlaceholder(new Label("Chưa có doanh thu trong khoảng này"));

        Button viewButton = new Button("Xem báo cáo");
        viewButton.setOnAction(e -> reload());
        exportButton.setDisable(true);
        exportButton.setOnAction(e -> exportCsv());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox filters = new HBox(10, new Label("Từ ngày:"), fromPicker, new Label("Đến ngày:"), toPicker);
        filters.getChildren().addAll(com.coffeeshop.coffeeshopmanagement.util.DatePresets.buttons(fromPicker, toPicker, this::reload));
        filters.getChildren().addAll(viewButton, spacer, exportButton);
        filters.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        summaryLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        VBox productBox = new VBox(6, new Label("Sản phẩm bán chạy (top " + TOP_PRODUCTS_LIMIT + ")"), productTable);
        VBox dailyBox = new VBox(6, new Label("Doanh thu theo ngày"), dailyTable);
        VBox.setVgrow(productTable, Priority.ALWAYS);
        VBox.setVgrow(dailyTable, Priority.ALWAYS);
        HBox tables = new HBox(14, productBox, dailyBox);
        HBox.setHgrow(productBox, Priority.ALWAYS);
        HBox.setHgrow(dailyBox, Priority.ALWAYS);
        VBox.setVgrow(tables, Priority.ALWAYS);

        summaryLabel.setWrapText(true);
        paymentLabel.setWrapText(true);
        VBox root = new VBox(12, filters, summaryLabel, paymentLabel, tables);
        root.setPadding(new Insets(16));

        Stage stage = new Stage();
        instance = stage;
        stage.setTitle("Báo cáo doanh thu");
        com.coffeeshop.coffeeshopmanagement.util.AppIcon.apply(stage);
        stage.setScene(new Scene(root, 1040, 600));
        com.coffeeshop.coffeeshopmanagement.util.UiSettings.rememberSize(stage, "report.window", 1040, 600);
        AlertUtil.closeOnEscape(stage.getScene());
        stage.show();

        reload();
    }

    private <T> TableColumn<T, String> column(String title, double width, Function<T, String> text) {
        TableColumn<T, String> column = new TableColumn<>(title);
        column.setPrefWidth(width);
        column.setCellValueFactory(data -> new SimpleStringProperty(text.apply(data.getValue())));
        return column;
    }

    private void reload() {
        LocalDate from = fromPicker.getValue();
        LocalDate to = toPicker.getValue();
        if (from == null || to == null) {
            AlertUtil.warning("Thiếu thông tin", "Vui lòng chọn đầy đủ khoảng ngày.");
            return;
        }
        if (from.isAfter(to)) {
            AlertUtil.warning("Khoảng ngày không hợp lệ", "\"Từ ngày\" phải trước hoặc bằng \"Đến ngày\".");
            return;
        }
        exportButton.setDisable(true);
        summaryLabel.setText("Đang tải...");
        Async.run(
                () -> new Object[]{reportDAO.summary(from, to), reportDAO.topProducts(from, to, TOP_PRODUCTS_LIMIT),
                        reportDAO.dailySales(from, to), reportDAO.paymentSplit(from, to), reportDAO.profit(from, to)},
                result -> {
                    lastSummary = (Summary) result[0];
                    lastProducts = castList(result[1]);
                    lastDaily = castList(result[2]);
                    lastSplit = castList(result[3]);
                    lastProfit = (ReportDAO.Profit) result[4];
                    lastFrom = from;
                    lastTo = to;
                    productTable.getItems().setAll(lastProducts);
                    dailyTable.getItems().setAll(lastDaily);
                    summaryLabel.setText("Đơn đã thanh toán: " + lastSummary.paidOrders()
                            + "   •   Doanh thu: " + CurrencyUtil.format(lastSummary.revenue())
                            + "   •   Giá trị TB/đơn: " + CurrencyUtil.format(lastSummary.averageOrderValue())
                            + "   •   Giảm giá: " + CurrencyUtil.format(lastSummary.discounts())
                            + "   •   Đơn đã hủy: " + lastSummary.cancelledOrders()
                            + "\nLợi nhuận gộp: " + CurrencyUtil.format(lastProfit.grossProfit()) + profitNote(lastProfit));
                    paymentLabel.setText(paymentText(lastSplit));
                    exportButton.setDisable(false);
                },
                error -> {
                    summaryLabel.setText("");
                    AlertUtil.error("Lỗi tải báo cáo", error.getMessage());
                }
        );
    }

    /** "-" when no cost is known, so a missing cost is never shown as a profit of 0. */
    static String profitText(java.math.BigDecimal profit) {
        return profit == null ? "-" : CurrencyUtil.format(profit);
    }

    static String profitNote(ReportDAO.Profit profit) {
        return profit.linesWithoutCost() == 0 ? ""
                : "  (chưa tính " + profit.linesWithoutCost() + " dòng bán chưa nhập giá vốn)";
    }

    static String paymentText(List<ReportDAO.PaymentSplit> split) {
        if (split.isEmpty()) return "Theo hình thức thanh toán: chưa có đơn nào";
        StringBuilder text = new StringBuilder("Theo hình thức thanh toán: ");
        for (int i = 0; i < split.size(); i++) {
            ReportDAO.PaymentSplit part = split.get(i);
            if (i > 0) text.append("   •   ");
            text.append(part.method() == null ? "Không rõ" : part.method().label()).append(": ")
                    .append(CurrencyUtil.format(part.revenue())).append(" (").append(part.orders()).append(" đơn)");
        }
        return text.toString();
    }

    @SuppressWarnings("unchecked")
    private static <T> List<T> castList(Object value) {
        return (List<T>) value;
    }

    /** Exports exactly what is on screen (the last successfully loaded range), not the pickers. */
    private void exportCsv() {
        if (lastSummary == null) return;
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Xuất báo cáo");
        chooser.setInitialFileName("bao-cao-" + lastFrom + "_" + lastTo + ".csv");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
        File file = chooser.showSaveDialog(exportButton.getScene().getWindow());
        if (file == null) return;

        List<String> lines = new ArrayList<>();
        lines.add("Báo cáo doanh thu," + lastFrom.format(DAY_FORMAT) + "," + lastTo.format(DAY_FORMAT));
        lines.add("Đơn đã thanh toán," + lastSummary.paidOrders());
        lines.add("Doanh thu (đ)," + lastSummary.revenue().toPlainString());
        lines.add("Giảm giá (đ)," + lastSummary.discounts().toPlainString());
        lines.add("Đơn đã hủy," + lastSummary.cancelledOrders());
        lines.add("Lợi nhuận gộp (đ)," + lastProfit.grossProfit().toPlainString());
        lines.add("Dòng bán chưa có giá vốn (không tính vào lợi nhuận)," + lastProfit.linesWithoutCost());
        lines.add("");
        lines.add("Hình thức thanh toán,Số đơn,Doanh thu (đ)");
        for (ReportDAO.PaymentSplit part : lastSplit) {
            lines.add(csv(part.method() == null ? "Không rõ" : part.method().label()) + "," + part.orders() + "," + part.revenue().toPlainString());
        }
        lines.add("");
        lines.add("Sản phẩm,Số lượng bán,Doanh thu (đ),Lợi nhuận gộp (đ)");
        for (ProductSales p : lastProducts) {
            lines.add(csv(p.productName()) + "," + p.quantity() + "," + p.revenue().toPlainString() + ","
                    + (p.profit() == null ? "" : p.profit().toPlainString()));
        }
        lines.add("");
        lines.add("Ngày,Số đơn,Doanh thu (đ),Lợi nhuận gộp (đ)");
        for (DailySales d : lastDaily) {
            lines.add(d.date().format(DAY_FORMAT) + "," + d.orders() + "," + d.revenue().toPlainString() + ","
                    + (d.profit() == null ? "" : d.profit().toPlainString()));
        }
        try {
            // UTF-8 with a BOM so Excel shows the Vietnamese text correctly instead of mojibake.
            String content = "\uFEFF" + String.join("\r\n", lines) + "\r\n";
            Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);
            AlertUtil.info("Xuất báo cáo", "Đã lưu báo cáo vào:\n" + file.getAbsolutePath());
        } catch (IOException e) {
            AlertUtil.error("Không thể lưu file", e.getMessage());
        }
    }

    private static String csv(String value) {
        return com.coffeeshop.coffeeshopmanagement.util.Csv.cell(value);
    }
}
