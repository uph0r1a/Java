package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.dao.ReportDAO;
import com.coffeeshop.coffeeshopmanagement.dao.ReportDAO.DailySales;
import com.coffeeshop.coffeeshopmanagement.dao.ReportDAO.ProductSales;
import com.coffeeshop.coffeeshopmanagement.dao.ReportDAO.Summary;
import com.coffeeshop.coffeeshopmanagement.util.AlertUtil;
import com.coffeeshop.coffeeshopmanagement.util.Async;
import com.coffeeshop.coffeeshopmanagement.util.CurrencyUtil;
import com.coffeeshop.coffeeshopmanagement.util.ReportCsv;
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

import static com.coffeeshop.coffeeshopmanagement.util.ReportTexts.*;

/**
 * Admin-only sales report (opened from the admin dashboard): totals for a date range, best
 * sellers, and revenue per day, with CSV export. Non-modal window built in code for the same
 * reasons as OrderHistoryWindow (no sidebar needed; avoids Linux dialog-modality trouble).
 */
public final class ReportWindow {

    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final int TOP_PRODUCTS_LIMIT = 20;
    private static final int SHIFT_HISTORY_LIMIT = 100;

    private final com.coffeeshop.coffeeshopmanagement.service.ReportService reportService = new com.coffeeshop.coffeeshopmanagement.service.ReportService();
    private final DatePicker fromPicker = com.coffeeshop.coffeeshopmanagement.util.DateBoxes.picker(com.coffeeshop.coffeeshopmanagement.util.BusinessDay.today().withDayOfMonth(1));
    private final DatePicker toPicker = com.coffeeshop.coffeeshopmanagement.util.DateBoxes.picker(com.coffeeshop.coffeeshopmanagement.util.BusinessDay.today());
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
    private final OptionSalesPage optionPage = new OptionSalesPage(); // TODO 4.47

    private Summary lastSummary;
    private List<ProductSales> lastProducts = List.of();      // the best sellers shown on screen
    private List<ProductSales> lastAllProducts = List.of();   // every product that sold: what the CSV holds (TODO 5.34)
    private List<DailySales> lastDaily = List.of();
    private List<ReportDAO.PaymentSplit> lastSplit = List.of();
    private List<ReportDAO.ChannelSales> lastChannels = List.of(); // TODO 4.48
    private List<ReportDAO.DiscountLine> lastDiscounts = List.of(); // TODO 2.37
    private com.coffeeshop.coffeeshopmanagement.dao.GiftCardDAO.OpenBalance lastGiftCards; // TODO 4.45: what is still on cards, now (not of the period)
    private ReportDAO.Profit lastProfit;
    private ReportDAO.Waste lastWaste;
    private ReportDAO.Expenses lastExpenses;           // expenses of the range, for "Chi phí" and "Lợi nhuận ròng" (TODO 4.38)
    private SalesPatterns.Range lastPreviousRange;     // the period just before the one on screen (TODO 4.40)
    private Summary lastPreviousSummary;
    private List<SalesPatterns.Slot> lastHours = List.of();
    private List<SalesPatterns.WeekdaySlot> lastWeekdays = List.of();
    private List<ReportDAO.CategorySales> lastCategories = List.of();
    private List<com.coffeeshop.coffeeshopmanagement.dao.ShiftDAO.EmployeeShare> lastEmployees = List.of();
    private final com.coffeeshop.coffeeshopmanagement.service.ShiftService shiftService = new com.coffeeshop.coffeeshopmanagement.service.ShiftService();
    private final TableView<com.coffeeshop.coffeeshopmanagement.dao.ShiftDAO.EmployeeShare> employeeTable = new TableView<>();
    private final TableView<com.coffeeshop.coffeeshopmanagement.model.ShiftClose> shiftTable = new TableView<>();
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
        filters.getChildren().addAll(viewButton, spacer, discountButton(), RevenueBookExport.button(() -> lastFrom, () -> lastTo), exportButton);
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
        // Third tab (TODO 4.6): sales per employee for the range, and the closed shifts with the cash difference of each.
        employeeTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        shiftTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        employeeTable.getColumns().add(this.<com.coffeeshop.coffeeshopmanagement.dao.ShiftDAO.EmployeeShare>column(I18n.t("report.col.employee"), 160, x -> employeeName(x)));
        employeeTable.getColumns().add(this.<com.coffeeshop.coffeeshopmanagement.dao.ShiftDAO.EmployeeShare>column(I18n.t("report.col.orders"), 70, x -> String.valueOf(x.orders())));
        employeeTable.getColumns().add(this.<com.coffeeshop.coffeeshopmanagement.dao.ShiftDAO.EmployeeShare>column(I18n.t("report.col.revenue"), 120, x -> CurrencyUtil.format(x.revenue())));
        employeeTable.getColumns().add(this.<com.coffeeshop.coffeeshopmanagement.dao.ShiftDAO.EmployeeShare>column(I18n.t("report.col.cash"), 120, x -> CurrencyUtil.format(x.cash())));
        employeeTable.getColumns().add(this.<com.coffeeshop.coffeeshopmanagement.dao.ShiftDAO.EmployeeShare>column(I18n.t("report.col.avgorder"), 110,
                x -> CurrencyUtil.format(x.orders() == 0 ? BigDecimal.ZERO : x.revenue().divide(BigDecimal.valueOf(x.orders()), 0, java.math.RoundingMode.HALF_UP))));
        employeeTable.setPlaceholder(new Label(I18n.t("report.empty.employees")));
        java.time.format.DateTimeFormatter shiftTime = java.time.format.DateTimeFormatter.ofPattern("HH:mm dd/MM");
        shiftTable.getColumns().add(this.<com.coffeeshop.coffeeshopmanagement.model.ShiftClose>column(I18n.t("shift.col.period"), 170,
                c -> c.periodStart().format(shiftTime) + " \u2192 " + c.periodEnd().format(shiftTime)));
        shiftTable.getColumns().add(this.<com.coffeeshop.coffeeshopmanagement.model.ShiftClose>column(I18n.t("shift.col.by"), 110, c -> c.closedByName()));
        shiftTable.getColumns().add(this.<com.coffeeshop.coffeeshopmanagement.model.ShiftClose>column(I18n.t("shift.col.cashsales"), 110, c -> CurrencyUtil.format(c.cashSales())));
        shiftTable.getColumns().add(this.<com.coffeeshop.coffeeshopmanagement.model.ShiftClose>column(I18n.t("shift.col.cashout"), 110, c -> CurrencyUtil.format(c.cashOut())));
        shiftTable.getColumns().add(this.<com.coffeeshop.coffeeshopmanagement.model.ShiftClose>column(I18n.t("shift.col.expected"), 110, c -> CurrencyUtil.format(c.expectedCash())));
        shiftTable.getColumns().add(this.<com.coffeeshop.coffeeshopmanagement.model.ShiftClose>column(I18n.t("shift.col.counted"), 110, c -> CurrencyUtil.format(c.countedCash())));
        shiftTable.getColumns().add(this.<com.coffeeshop.coffeeshopmanagement.model.ShiftClose>column(I18n.t("shift.col.difference"), 100,
                c -> (c.difference().signum() > 0 ? "+" : "") + CurrencyUtil.format(c.difference())));
        shiftTable.getColumns().add(this.<com.coffeeshop.coffeeshopmanagement.model.ShiftClose>column(I18n.t("shift.col.note"), 140, c -> c.note() == null ? "" : c.note()));
        shiftTable.setPlaceholder(new Label(I18n.t("shift.empty")));
        VBox employeeBox = new VBox(6, new Label(I18n.t("report.employee.title")), employeeTable);
        VBox shiftBox = new VBox(6, new Label(I18n.t("shift.history.title")), shiftTable, ShiftSheetActions.listButtons(shiftTable)); // reprint / save the sheet (TODO 4.53)
        VBox.setVgrow(employeeTable, Priority.ALWAYS);
        VBox.setVgrow(shiftTable, Priority.ALWAYS);
        HBox staffTables = new HBox(14, employeeBox, shiftBox);
        HBox.setHgrow(employeeBox, Priority.ALWAYS); HBox.setHgrow(shiftBox, Priority.ALWAYS);
        VBox staffPage = new VBox(8, staffTables);
        VBox.setVgrow(staffTables, Priority.ALWAYS);
        staffPage.setPadding(new Insets(8, 0, 0, 0));

        Tab mainTab = new Tab(I18n.t("report.tab.main"), mainPage);
        Tab whenTab = new Tab(I18n.t("report.tab.when"), whenPage);
        Tab staffTab = new Tab(I18n.t("report.tab.staff"), staffPage);
        mainTab.setClosable(false);
        whenTab.setClosable(false);
        staffTab.setClosable(false);
        TabPane tabs = new TabPane(mainTab, whenTab, staffTab, optionPage.tab());
        VBox.setVgrow(tabs, Priority.ALWAYS);

        summaryLabel.setWrapText(true); summaryLabel.setMinHeight(66); // the bold summary is up to three lines (profit, expenses and net profit follow the sales) at the usual window width (TODO 1.7)
        paymentLabel.setWrapText(true); comparisonLabel.setWrapText(true);
        loadedLabel.setStyle("-fx-opacity: 0.7;");
        // When the owner set a day start (TODO 4.50) the report says which day an evening belongs to; with the calendar day there is nothing to say.
        Label dayStartLabel = new Label();
        dayStartLabel.setWrapText(true);
        if (com.coffeeshop.coffeeshopmanagement.util.BusinessDay.isCalendarDay()) {
            dayStartLabel.setManaged(false);
            dayStartLabel.setVisible(false);
        } else {
            dayStartLabel.setText(I18n.t("report.daystart.note",
                    com.coffeeshop.coffeeshopmanagement.util.BusinessDay.format(com.coffeeshop.coffeeshopmanagement.util.BusinessDay.startMinutes())));
        }
        VBox root = new VBox(12, filters, dayStartLabel, summaryLabel, comparisonLabel, loadedLabel, paymentLabel, tabs);
        root.setPadding(new Insets(16));

        Stage stage = com.coffeeshop.coffeeshopmanagement.util.SceneNavigator.newOwnedStage();
        instance = stage;
        stage.setTitle(I18n.t("report.title"));
        com.coffeeshop.coffeeshopmanagement.util.AppIcon.apply(stage);
        stage.setScene(new Scene(root, 1040, 600));
        com.coffeeshop.coffeeshopmanagement.util.StageSize.remember(stage, "report.window", 1040, 600);
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
                    List<SalesPatterns.PaidOrder> paid = reportService.paidOrders(from, to);
                    return new Object[]{reportService.summary(from, to), reportService.allProducts(from, to),
                            reportService.dailySales(from, to), reportService.paymentSplit(from, to), reportService.profit(from, to),
                            before, reportService.summary(before.from(), before.to()),
                            SalesPatterns.byHour(paid), SalesPatterns.byWeekday(paid, from, to), reportService.categorySales(from, to),
                            reportService.employeeSales(from, to), shiftService.findRecent(SHIFT_HISTORY_LIMIT),
                            reportService.waste(from, to), reportService.expenses(from, to), reportService.channelSales(from, to), reportService.optionSales(from, to), reportService.discountsByCashier(from, to), shiftService.giftCardsOpenBalance()};
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
                    lastEmployees = castList(result[10]);
                    lastWaste = (ReportDAO.Waste) result[12];
                    lastExpenses = (ReportDAO.Expenses) result[13];
                    lastChannels = castList(result[14]);
                    optionPage.show(castList(result[15]));
                    lastDiscounts = castList(result[16]);
                    lastGiftCards = (com.coffeeshop.coffeeshopmanagement.dao.GiftCardDAO.OpenBalance) result[17];
                    lastFrom = from;
                    lastTo = to;
                    productTable.getItems().setAll(lastProducts);
                    dailyTable.getItems().setAll(lastDaily);
                    summaryLabel.setText(I18n.t("report.summary", lastSummary.paidOrders(), CurrencyUtil.format(lastSummary.revenue()),
                            CurrencyUtil.format(lastSummary.averageOrderValue()), CurrencyUtil.format(lastSummary.discounts()),
                            lastSummary.cancelledOrders(), CurrencyUtil.format(lastProfit.grossProfit()), profitNote(lastProfit))
                            + (lastWaste.lines() == 0 ? "" : "\n" + com.coffeeshop.coffeeshopmanagement.util.WasteText.summaryLine(lastWaste))
                            + com.coffeeshop.coffeeshopmanagement.util.ComplimentaryText.summaryLine(lastSummary) // TODO 4.61
                            + "\n" + com.coffeeshop.coffeeshopmanagement.util.ExpenseText.summaryLines(lastProfit.grossProfit(), lastWaste, lastExpenses));
                    paymentLabel.setText(paymentText(lastSplit) + com.coffeeshop.coffeeshopmanagement.util.ChannelText.line(lastChannels)
                            + (lastGiftCards.cards() == 0 ? "" : "\n" + com.coffeeshop.coffeeshopmanagement.util.GiftCardText.line(lastGiftCards)));
                    comparisonLabel.setText(SalesPatternsText.comparison(lastPreviousRange, lastSummary, lastPreviousSummary));
                    hourTable.getItems().setAll(lastHours);
                    weekdayTable.getItems().setAll(lastWeekdays);
                    categoryTable.getItems().setAll(lastCategories);
                    employeeTable.getItems().setAll(lastEmployees);
                    shiftTable.getItems().setAll(this.<com.coffeeshop.coffeeshopmanagement.model.ShiftClose>castList(result[11]));
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

        List<String> lines = ReportCsv.lines(new ReportCsv.Data(lastFrom, lastTo, lastSummary, lastProfit, lastWaste, lastExpenses,
                lastPreviousRange, lastPreviousSummary, lastSplit, lastChannels, lastDiscounts, lastGiftCards, lastAllProducts, lastHours,
                lastWeekdays, lastCategories, optionPage.csvLines(), lastEmployees, shiftTable.getItems(), lastDaily));
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

    private Button discountButton() { // TODO 2.37: opens the discounts of the period on screen
        Button button = new Button(I18n.t("report.discount.button"));
        button.setOnAction(e -> {
            if (lastFrom != null) DiscountReportDialog.show(lastDiscounts, lastFrom, lastTo);
        });
        return button;
    }
}
