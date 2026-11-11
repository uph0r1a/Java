package com.coffeeshop.coffeeshopmanagement.controller;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.Assert.assertTrue;

/**
 * TODO 6.21: the money and cash screens ask a service, not a DAO. These screens used to build {@code ExpenseDAO}, {@code VoucherDAO},
 * {@code GiftCardDAO}, {@code EmployeeDAO}, {@code ComplimentaryDAO}, {@code ShiftDAO} and {@code ReportDAO} themselves; now
 * {@code ExpenseService}, {@code VoucherService}, {@code GiftCardService}, {@code ComplimentaryService} and {@code ShiftService} do. Reads the source
 * (the screens build JavaFX nodes and cannot be created in a test). Naming a type the DAO owns (a record such as
 * {@code ShiftDAO.Summary}) is allowed; making a DAO or calling one is not. Add a screen to the list when it has been moved.
 */
public class ScreensUseServicesTest {

    private static final Path CONTROLLERS = Path.of("src", "main", "java", "com", "coffeeshop", "coffeeshopmanagement", "controller");
    private static final Pattern DAO_MADE = Pattern.compile("new (?:com\\.coffeeshop\\.coffeeshopmanagement\\.dao\\.)?(?!DataAccess)\\w+DAO\\(");
    private static final Pattern DAO_CALLED = Pattern.compile("(?<!new )\\b\\w+DAO\\.\\w+\\(");

    private static final List<String> MOVED = List.of("ExpenseDialog", "VoucherDialog", "PromotionPanel", "GiftCardDialog",
            "GiftCardListDialog", "SplitPaymentPanel", "ComplimentaryDialog", "ShiftCloseDialog",
            // the floor and the clock (second batch)
            "TableManagerDialog", "TableMovePanel", "EmployeeController", "ReservationDialog", "ReservationPanel", "BookedTables",
            "TimeClockDialog", "TimeClockCorrectionDialog", "TimeClockExport",
            // stock and kitchen (third batch)
            "StockLogDialog", "StockHistoryDialog", "IngredientDialog", "RecipePane", "OptionsPane", "CartLineFlow");

    @Test
    public void theMovedScreensMakeAndCallNoDao() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (String name : MOVED) {
            String text = Files.readString(CONTROLLERS.resolve(name + ".java"), StandardCharsets.UTF_8);
            if (DAO_MADE.matcher(text).find() || DAO_CALLED.matcher(text).find()) offenders.add(name);
        }
        assertTrue("these screens make or call a DAO again; put the call in a service: " + offenders, offenders.isEmpty());
    }

    /** The last step of 6.21: no screen at all, and no helper that opens a window or a prompt, builds or calls a DAO. */
    @Test
    public void noScreenMakesOrCallsAnyDao() throws IOException {
        List<Path> files = new ArrayList<>();
        try (java.util.stream.Stream<Path> all = Files.list(CONTROLLERS)) {
            all.filter(p -> p.toString().endsWith(".java")).forEach(files::add);
        }
        Path util = CONTROLLERS.resolveSibling("util");
        files.add(util.resolve("ClockOffer.java"));
        files.add(util.resolve("DashboardWidgets.java"));
        assertTrue("no screens found", files.size() > 40);
        List<String> offenders = new ArrayList<>();
        for (Path file : files) {
            StringBuilder code = new StringBuilder();
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String t = line.strip();
                if (t.startsWith("*") || t.startsWith("/*") || t.startsWith("//") || t.startsWith("import ")) continue;
                int comment = line.indexOf("//");
                code.append(comment >= 0 ? line.substring(0, comment) : line).append('\n');
            }
            if (DAO_MADE.matcher(code).find() || DAO_CALLED.matcher(code).find()) offenders.add(file.getFileName().toString());
        }
        assertTrue("these screens make or call a DAO; put the call in a service: " + offenders, offenders.isEmpty());
    }

    @Test
    public void theMovedScreensAskTheirServices() throws IOException {
        String[][] expected = {
                {"ExpenseDialog", "new ExpenseService()"}, {"VoucherDialog", "new VoucherService()"}, {"PromotionPanel", "new VoucherService()"},
                {"GiftCardDialog", "new GiftCardService()"}, {"GiftCardListDialog", "new GiftCardService()"},
                {"SplitPaymentPanel", "new GiftCardService()"}, {"ComplimentaryDialog", "new ComplimentaryService()"},
                {"ShiftCloseDialog", "new ShiftService()"},
                {"TableManagerDialog", "new TableService()"}, {"TableMovePanel", "new TableService()"}, {"EmployeeController", "new TableService()"},
                {"ReservationDialog", "new ReservationService()"}, {"ReservationPanel", "new ReservationService()"},
                {"BookedTables", "ReservationService.badgesOf("}, {"TimeClockDialog", "new TimeClockService()"},
                {"TimeClockCorrectionDialog", "new TimeClockService()"}, {"TimeClockExport", "TimeClockService dao"},
                {"StockLogDialog", "new StockService()"}, {"StockHistoryDialog", "new StockService()"},
                {"IngredientDialog", "new IngredientService()"}, {"RecipePane", "IngredientService dao"},
                {"OptionsPane", "new ProductOptionService()"}, {"CartLineFlow", "new ProductOptionService()"},
                // the rest (fourth batch)
                {"PromotionDialog", "new PromotionService()"}, {"CustomerController", "new CustomerService()"},
                {"CategoryController", "categoryService."}, {"ProductController", "new CategoryService()"},
                {"AccountController", "accountService.findAllUsers()"}, {"LoginController", "new AccountService()"},
                {"AuditLogWindow", "new AuditService()"}, {"OrderHistoryWindow", "OrderHistoryService()"},
                {"OrderHistoryExport", "OrderHistoryService history"}, {"ReportWindow", "ReportService()"},
                {"RevenueBookExport", "new ReportService()"}, {"ShiftSheetActions", "new ShiftService()"},
                {"FirstSignInLeftovers", "new ReservationService()"}, {"SetupWizard", "new TableService()"},
                {"SettingsDialog", "new TableService()"}, {"AdminDashboardController", "new TableService()"},
                {"HoaDonController", "ReceiptPrintService()"}, {"RefundLinesDialog", "new RefundService()"},
                {"PosCustomerBox", "CustomerService customers"}, {"OrderController", "new CheckoutService()"}};
        for (String[] pair : expected) {
            String text = Files.readString(CONTROLLERS.resolve(pair[0] + ".java"), StandardCharsets.UTF_8);
            assertTrue(pair[0] + " does not use " + pair[1], text.contains(pair[1]));
        }
    }
}
