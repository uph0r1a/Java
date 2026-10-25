package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.dao.ReportDAO;
import com.coffeeshop.coffeeshopmanagement.dao.ReportDAO.DailySales;
import com.coffeeshop.coffeeshopmanagement.dao.ReportDAO.ProductSales;
import com.coffeeshop.coffeeshopmanagement.dao.ReportDAO.Summary;
import com.coffeeshop.coffeeshopmanagement.util.AlertUtil;
import com.coffeeshop.coffeeshopmanagement.util.Async;
import com.coffeeshop.coffeeshopmanagement.util.CurrencyUtil;
import com.coffeeshop.coffeeshopmanagement.util.Session;
import com.coffeeshop.coffeeshopmanagement.service.SalesPatterns;
import com.coffeeshop.coffeeshopmanagement.service.SalesPatternsText;

import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.math.BigDecimal;
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
    private final DatePicker fromPicker = new DatePicker(com.coffeeshop.coffeeshopmanagement.util.AppClock.today().withDayOfMonth(1));
    private final DatePicker toPicker = new DatePicker(com.coffeeshop.coffeeshopmanagement.util.AppClock.today());
    private final Label summaryLabel = new Label(I18n.t("common.loading"));
    private final TableView<ProductSales> productTable = new TableView<>();
    private final TableView<DailySales> dailyTable = new TableView<>();
    private final Label loadedLabel = new Label("");
    private final Label paymentLabel = new Label("");
    private final Label comparisonLabel = new Label("");
    private final Label busiestLabel = new Label("");
    private final TableView<SalesPatterns.Slot> hourTable = new TableView<>();
    private final TableView<SalesPatterns.WeekdaySlot> weekdayTable = new TableView<>();
    private final TableView<ReportDAO.CategorySales> categoryTable = new TableView<>();
    private final Button exportButton = new Button(I18n.t("common.exportcsv"));

    private Summary lastSummary;
    private List<ProductSales> lastProducts = List.of();      // the best sellers shown on screen
    private List<ProductSales> lastAllProducts = List.of();   // every product that sold: what the CSV holds (TODO 5.34)
    private List<DailySales> lastDaily = List.of();
    private List<ReportDAO.PaymentSplit> lastSplit = List.of();
    private ReportDAO.Profit lastProfit;
    private SalesPatterns.Range lastPreviousRange;     // the period just before the one on screen (TODO 4.40)
    private Summary lastPreviousSummary;
    private List<SalesPatterns.Slot> lastHours = List.of();
    private List<SalesPatterns.WeekdaySlot> lastWeekdays = List.of();
    private List<ReportDAO.CategorySales> lastCategories = List.of();
    private LocalDate lastFrom;
    private LocalDate lastTo;
    private long loadedAtMillis = -1;   // MonotonicClock reading of the last successful load, -1 before the first
    private boolean loading;
    private int requestCounter;         // an older answer arriving after a newer request is dropped

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
            AlertUtil.warning(I18n.t("admin.only.title"), I18n.t("report.adminonly"));
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
        productTable.getColumns().add(this.<ProductSales>column(I18n.t("receipt.col.product"), 240, p -> com.coffeeshop.coffeeshopmanagement.config.SeedNames.product(p.productName())));
        productTable.getColumns().add(this.<ProductSales>column(I18n.t("report.col.qty"), 110, p -> String.valueOf(p.quantity())));
        productTable.getColumns().add(this.<ProductSales>column(I18n.t("report.col.revenue"), 130, p -> CurrencyUtil.format(p.revenue())));
        productTable.getColumns().add(this.<ProductSales>column(I18n.t("report.col.profit"), 130, p -> profitText(p.profit())));
        productTable.setPlaceholder(new Label(I18n.t("report.empty.products")));

        dailyTable.getColumns().add(this.<DailySales>column(I18n.t("chart.axis.date"), 120, d -> d.date().format(DAY_FORMAT)));
        dailyTable.getColumns().add(this.<DailySales>column(I18n.t("report.col.orders"), 80, d -> String.valueOf(d.orders())));
        dailyTable.getColumns().add(this.<DailySales>column(I18n.t("report.col.revenue"), 140, d -> CurrencyUtil.format(d.revenue())));
        dailyTable.getColumns().add(this.<DailySales>column(I18n.t("report.col.profit"), 130, d -> profitText(d.profit())));
        dailyTable.setPlaceholder(new Label(I18n.t("report.empty.daily")));

        Button viewButton = new Button(I18n.t("report.view"));
        viewButton.setOnAction(e -> reload());
        exportButton.setDisable(true);
        exportButton.setOnAction(e -> exportCsv());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox filters = new HBox(10, new Label(I18n.t("report.from")), fromPicker, new Label(I18n.t("report.to")), toPicker);
        filters.getChildren().addAll(com.coffeeshop.coffeeshopmanagement.util.DatePresets.buttons(fromPicker, toPicker, this::reload));
        filters.getChildren().addAll(viewButton, spacer, exportButton);
        filters.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        summaryLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        VBox productBox = new VBox(6, new Label(I18n.t("report.top", TOP_PRODUCTS_LIMIT)), productTable);
        VBox dailyBox = new VBox(6, new Label(I18n.t("report.daily.title")), dailyTable);
        VBox.setVgrow(productTable, Priority.ALWAYS);
        VBox.setVgrow(dailyTable, Priority.ALWAYS);
        HBox tables = new HBox(14, productBox, dailyBox);
        HBox.setHgrow(productBox, Priority.ALWAYS);
        HBox.setHgrow(dailyBox, Priority.ALWAYS);
        VBox.setVgrow(tables, Priority.ALWAYS);

        // Second tab (TODO 4.40): when the shop is busy, and which kind of product sells.
        hourTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        weekdayTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        categoryTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        hourTable.getColumns().add(this.<SalesPatterns.Slot>column(I18n.t("report.col.hour"), 120, h -> SalesPatternsText.hourLabel(h.key())));
        hourTable.getColumns().add(this.<SalesPatterns.Slot>column(I18n.t("report.col.orders"), 60, h -> String.valueOf(h.orders())));
        hourTable.getColumns().add(this.<SalesPatterns.Slot>column(I18n.t("report.col.revenue"), 110, h -> CurrencyUtil.format(h.revenue())));
        hourTable.getColumns().add(this.<SalesPatterns.Slot>column("", 150, h -> SalesPatternsText.bar(BigDecimal.valueOf(h.orders()), maxOrders(lastHours))));
        hourTable.setPlaceholder(new Label(I18n.t("report.empty.when")));
        weekdayTable.getColumns().add(this.<SalesPatterns.WeekdaySlot>column(I18n.t("report.col.weekday"), 100, d -> SalesPatternsText.weekdayName(d.day())));
        weekdayTable.getColumns().add(this.<SalesPatterns.WeekdaySlot>column(I18n.t("report.col.orders"), 60, d -> String.valueOf(d.orders())));
        weekdayTable.getColumns().add(this.<SalesPatterns.WeekdaySlot>column(I18n.t("report.col.revenue"), 110, d -> CurrencyUtil.format(d.revenue())));
        weekdayTable.getColumns().add(this.<SalesPatterns.WeekdaySlot>column(I18n.t("report.col.avg"), 110, d -> CurrencyUtil.format(d.average())));
        weekdayTable.getColumns().add(this.<SalesPatterns.WeekdaySlot>column("", 120, d -> SalesPatternsText.bar(d.average(), maxAverage(lastWeekdays))));
        weekdayTable.setPlaceholder(new Label(I18n.t("report.empty.when")));
        categoryTable.getColumns().add(this.<ReportDAO.CategorySales>column(I18n.t("report.col.category"), 140, c -> categoryName(c)));
        categoryTable.getColumns().add(this.<ReportDAO.CategorySales>column(I18n.t("report.col.qty"), 80, c -> String.valueOf(c.quantity())));
        categoryTable.getColumns().add(this.<ReportDAO.CategorySales>column(I18n.t("report.col.revenue"), 110, c -> CurrencyUtil.format(c.revenue())));
        categoryTable.getColumns().add(this.<ReportDAO.CategorySales>column("", 120, c -> SalesPatternsText.bar(c.revenue(), maxRevenue(lastCategories))));
        categoryTable.setPlaceholder(new Label(I18n.t("report.empty.when")));
        VBox hourBox = new VBox(6, new Label(I18n.t("report.hour.title")), hourTable);
        VBox weekdayBox = new VBox(6, new Label(I18n.t("report.weekday.title")), weekdayTable);
        VBox categoryBox = new VBox(6, new Label(I18n.t("report.category.title")), categoryTable);
        VBox.setVgrow(hourTable, Priority.ALWAYS);
        VBox.setVgrow(weekdayTable, Priority.ALWAYS);
        VBox.setVgrow(categoryTable, Priority.ALWAYS);
        HBox whenTables = new HBox(14, hourBox, weekdayBox, categoryBox);
        HBox.setHgrow(hourBox, Priority.ALWAYS);
        HBox.setHgrow(weekdayBox, Priority.ALWAYS);
        HBox.setHgrow(categoryBox, Priority.ALWAYS);
        busiestLabel.setWrapText(true);
        VBox whenPage = new VBox(8, busiestLabel, whenTables);
        VBox.setVgrow(whenTables, Priority.ALWAYS);
        whenPage.setPadding(new Insets(8, 0, 0, 0));
        VBox mainPage = new VBox(8, tables);
        VBox.setVgrow(tables, Priority.ALWAYS);
        mainPage.setPadding(new Insets(8, 0, 0, 0));
        Tab mainTab = new Tab(I18n.t("report.tab.main"), mainPage);
        Tab whenTab = new Tab(I18n.t("report.tab.when"), whenPage);
        mainTab.setClosable(false);
        whenTab.setClosable(false);
        TabPane tabs = new TabPane(mainTab, whenTab);
        VBox.setVgrow(tabs, Priority.ALWAYS);

        summaryLabel.setWrapText(true);
        paymentLabel.setWrapText(true);
        loadedLabel.setStyle("-fx-opacity: 0.7;");
        comparisonLabel.setWrapText(true);
        VBox root = new VBox(12, filters, summaryLabel, comparisonLabel, loadedLabel, paymentLabel, tabs);
        root.setPadding(new Insets(16));

        Stage stage = com.coffeeshop.coffeeshopmanagement.util.SceneNavigator.newOwnedStage();
        instance = stage;
        stage.setTitle(I18n.t("report.title"));
        com.coffeeshop.coffeeshopmanagement.util.AppIcon.apply(stage);
        stage.setScene(new Scene(root, 1040, 600));
        com.coffeeshop.coffeeshopmanagement.util.UiSettings.rememberSize(stage, "report.window", 1040, 600);
        AlertUtil.closeOnEscape(stage.getScene());
        // A window left open during the day goes stale; coming back to it reloads the range on screen (TODO 5.34).
        stage.focusedProperty().addListener((observable, was, focused) -> {
            if (focused && lastFrom != null && lastTo != null && com.coffeeshop.coffeeshopmanagement.util.ReportRefresh
                    .shouldRefreshOnFocus(loading, loadedAtMillis, com.coffeeshop.coffeeshopmanagement.util.MonotonicClock.millis())) {
                load(lastFrom, lastTo);
            }
        });
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
            AlertUtil.warning(I18n.t("common.missing.title"), I18n.t("report.dates.missing"));
            return;
        }
        if (from.isAfter(to)) {
            AlertUtil.warning(I18n.t("report.dates.invalid.title"), I18n.t("report.dates.invalid.message"));
            return;
        }
        load(from, to);
    }

    /** Reads the figures for the range and shows them (the button, a preset, and the reload when the window gets the focus back). */
    private void load(LocalDate from, LocalDate to) {
        exportButton.setDisable(true);
        summaryLabel.setText(I18n.t("common.loading"));
        loading = true;
        final int myRequest = ++requestCounter;
        Async.run(
                () -> {
                    SalesPatterns.Range before = SalesPatterns.previousRange(from, to);
                    List<SalesPatterns.PaidOrder> paid = reportDAO.paidOrders(from, to);
                    return new Object[]{reportDAO.summary(from, to), reportDAO.allProducts(from, to),
                            reportDAO.dailySales(from, to), reportDAO.paymentSplit(from, to), reportDAO.profit(from, to),
                            before, reportDAO.summary(before.from(), before.to()),
                            SalesPatterns.byHour(paid), SalesPatterns.byWeekday(paid, from, to), reportDAO.categorySales(from, to)};
                },
                result -> {
                    if (myRequest != requestCounter) return;
                    loading = false;
                    lastSummary = (Summary) result[0];
                    lastAllProducts = castList(result[1]);
                    lastProducts = com.coffeeshop.coffeeshopmanagement.util.ReportRefresh.top(lastAllProducts, TOP_PRODUCTS_LIMIT);
                    lastDaily = castList(result[2]);
                    lastSplit = castList(result[3]);
                    lastProfit = (ReportDAO.Profit) result[4];
                    lastPreviousRange = (SalesPatterns.Range) result[5];
                    lastPreviousSummary = (Summary) result[6];
                    lastHours = castList(result[7]);
                    lastWeekdays = castList(result[8]);
                    lastCategories = castList(result[9]);
                    lastFrom = from;
                    lastTo = to;
                    productTable.getItems().setAll(lastProducts);
                    dailyTable.getItems().setAll(lastDaily);
                    summaryLabel.setText(I18n.t("report.summary", lastSummary.paidOrders(), CurrencyUtil.format(lastSummary.revenue()),
                            CurrencyUtil.format(lastSummary.averageOrderValue()), CurrencyUtil.format(lastSummary.discounts()),
                            lastSummary.cancelledOrders(), CurrencyUtil.format(lastProfit.grossProfit()), profitNote(lastProfit)));
                    paymentLabel.setText(paymentText(lastSplit));
                    comparisonLabel.setText(SalesPatternsText.comparison(lastPreviousRange, lastSummary, lastPreviousSummary));
                    hourTable.getItems().setAll(lastHours);
                    weekdayTable.getItems().setAll(lastWeekdays);
                    categoryTable.getItems().setAll(lastCategories);
                    busiestLabel.setText(busiestText(lastHours, lastWeekdays));
                    loadedAtMillis = com.coffeeshop.coffeeshopmanagement.util.MonotonicClock.millis();
                    loadedLabel.setText(com.coffeeshop.coffeeshopmanagement.util.ReportRefresh
                            .loadedText(com.coffeeshop.coffeeshopmanagement.util.AppClock.now()));
                    exportButton.setDisable(false);
                },
                error -> {
                    if (myRequest != requestCounter) return;
                    loading = false;
                    summaryLabel.setText("");
                    loadedLabel.setText("");
                    AlertUtil.error(I18n.t("report.load.error"), error.getMessage());
                }
        );
    }

    private static BigDecimal maxOrders(List<SalesPatterns.Slot> slots) {
        return slots.stream().map(x -> BigDecimal.valueOf(x.orders())).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
    }

    private static BigDecimal maxAverage(List<SalesPatterns.WeekdaySlot> slots) {
        return slots.stream().map(SalesPatterns.WeekdaySlot::average).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
    }

    private static BigDecimal maxRevenue(List<ReportDAO.CategorySales> list) {
        return list.stream().map(ReportDAO.CategorySales::revenue).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
    }

    private static String categoryName(ReportDAO.CategorySales category) {
        return category.categoryName() == null ? I18n.t("report.category.none")
                : com.coffeeshop.coffeeshopmanagement.config.SeedNames.category(category.categoryName());
    }

    /** "Busiest hour: 10:00 - 11:00 (12 orders) • Best weekday: Saturday (about 1.200.000đ per day)". */
    static String busiestText(List<SalesPatterns.Slot> hours, List<SalesPatterns.WeekdaySlot> weekdays) {
        SalesPatterns.Slot hour = SalesPatterns.busiest(hours);
        SalesPatterns.WeekdaySlot day = SalesPatterns.bestWeekday(weekdays);
        if (hour == null || day == null) return I18n.t("report.busiest.none");
        return I18n.t("report.busiest.hour", SalesPatternsText.hourLabel(hour.key()), hour.orders()) + "   •   "
                + I18n.t("report.busiest.weekday", SalesPatternsText.weekdayName(day.day()), CurrencyUtil.format(day.average()));
    }

    /** "-" when no cost is known, so a missing cost is never shown as a profit of 0. */
    static String profitText(java.math.BigDecimal profit) {
        return profit == null ? "-" : CurrencyUtil.format(profit);
    }

    static String profitNote(ReportDAO.Profit profit) {
        return profit.linesWithoutCost() == 0 ? ""
                : "  " + I18n.t("report.profitnote", profit.linesWithoutCost());
    }

    static String paymentText(List<ReportDAO.PaymentSplit> split) {
        if (split.isEmpty()) return I18n.t("report.payment.none");
        StringBuilder text = new StringBuilder(I18n.t("report.payment.prefix") + " ");
        for (int i = 0; i < split.size(); i++) {
            ReportDAO.PaymentSplit part = split.get(i);
            if (i > 0) text.append("   •   ");
            text.append(part.method() == null ? I18n.t("report.payment.unknown") : part.method().label()).append(": ")
                    .append(CurrencyUtil.format(part.revenue())).append(" (").append(I18n.t("report.orders.count", part.orders())).append(")");
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
        chooser.setTitle(I18n.t("report.export.title"));
        chooser.setInitialFileName("bao-cao-" + lastFrom + "_" + lastTo + ".csv");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
        File file = chooser.showSaveDialog(exportButton.getScene().getWindow());
        if (file == null) return;

        List<String> lines = new ArrayList<>();
        lines.add(I18n.t("report.title") + "," + lastFrom.format(DAY_FORMAT) + "," + lastTo.format(DAY_FORMAT));
        lines.add(I18n.t("report.csv.paid") + "," + lastSummary.paidOrders());
        lines.add(I18n.t("report.csv.revenue") + "," + lastSummary.revenue().toPlainString());
        lines.add(I18n.t("report.csv.discount") + "," + lastSummary.discounts().toPlainString());
        lines.add(I18n.t("report.csv.cancelled") + "," + lastSummary.cancelledOrders());
        lines.add(I18n.t("report.csv.profit") + "," + lastProfit.grossProfit().toPlainString());
        lines.add(I18n.t("report.csv.nocost") + "," + lastProfit.linesWithoutCost());
        lines.add(I18n.t("report.csv.prev") + "," + lastPreviousRange.from().format(DAY_FORMAT) + "," + lastPreviousRange.to().format(DAY_FORMAT));
        lines.add(I18n.t("report.csv.prev.paid") + "," + lastPreviousSummary.paidOrders());
        lines.add(I18n.t("report.csv.prev.revenue") + "," + lastPreviousSummary.revenue().toPlainString());
        lines.add("");
        lines.add(I18n.t("report.csv.method.header"));
        for (ReportDAO.PaymentSplit part : lastSplit) {
            lines.add(csv(part.method() == null ? I18n.t("report.payment.unknown") : part.method().label()) + "," + part.orders() + "," + part.revenue().toPlainString());
        }
        lines.add("");
        lines.add(I18n.t("report.csv.product.header"));
        for (ProductSales p : lastAllProducts) { // every product, not only the top 20 on screen (TODO 5.34)
            lines.add(csv(p.productName()) + "," + p.quantity() + "," + p.revenue().toPlainString() + ","
                    + (p.profit() == null ? "" : p.profit().toPlainString()));
        }
        lines.add("");
        lines.add(I18n.t("report.csv.hour.header"));
        for (SalesPatterns.Slot h : lastHours) {
            lines.add(csv(SalesPatternsText.hourLabel(h.key())) + "," + h.orders() + "," + h.revenue().toPlainString());
        }
        lines.add("");
        lines.add(I18n.t("report.csv.weekday.header"));
        for (SalesPatterns.WeekdaySlot d : lastWeekdays) {
            lines.add(csv(SalesPatternsText.weekdayName(d.day())) + "," + d.orders() + "," + d.revenue().toPlainString() + ","
                    + d.daysInRange() + "," + d.average().toPlainString());
        }
        lines.add("");
        lines.add(I18n.t("report.csv.category.header"));
        for (ReportDAO.CategorySales c : lastCategories) {
            lines.add(csv(categoryName(c)) + "," + c.quantity() + "," + c.revenue().toPlainString());
        }
        lines.add("");
        lines.add(I18n.t("report.csv.daily.header"));
        for (DailySales d : lastDaily) {
            lines.add(d.date().format(DAY_FORMAT) + "," + d.orders() + "," + d.revenue().toPlainString() + ","
                    + (d.profit() == null ? "" : d.profit().toPlainString()));
        }
        // The file is written off the screen thread, with a spinner on the button meanwhile (TODO 5.16); the lines above were
        // built from what is on screen, so nothing changes under the writer.
        Runnable release = com.coffeeshop.coffeeshopmanagement.util.BusyIndicator.hold(exportButton, I18n.t("busy.export"));
        com.coffeeshop.coffeeshopmanagement.util.Async.run(
                () -> {
                    // UTF-8 with a BOM so Excel shows the Vietnamese text correctly; says "close it in Excel" when the file is open (TODO 5.30).
                    com.coffeeshop.coffeeshopmanagement.util.ExportCsv.write(file.toPath(), lines);
                    return file;
                },
                saved -> {
                    release.run();
                    AlertUtil.info(I18n.t("report.export.title"), I18n.t("report.export.done", saved.getAbsolutePath()));
                },
                error -> {
                    release.run();
                    AlertUtil.error(I18n.t("common.file.savefail"), error.getMessage());
                });
    }

    private static String csv(String value) {
        return com.coffeeshop.coffeeshopmanagement.util.Csv.cell(value);
    }
}
