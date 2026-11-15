package com.coffeeshop.coffeeshopmanagement.controller;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

/**
 * TODO 4.61 reads the sources that cannot be run without a screen: the free-drink button and its window are wired, a free bill is never offered as a
 * way to pay or as a refund method, the report shows it, and both databases have its columns.
 */
public class ComplimentarySourceTest {

    private static String read(String first, String... more) throws IOException {
        return Files.readString(Path.of(first, more), StandardCharsets.UTF_8);
    }

    private static final String MAIN = "src/main/java/com/coffeeshop/coffeeshopmanagement/";

    @Test
    public void theTillHasAFreeDrinkButtonThatGoesThroughTheSameGuardsAsPaying() throws IOException {
        String fxml = read("src/main/resources/fxml/quanlydonhang.fxml");
        assertTrue(fxml.contains("onAction=\"#handleGiveAway\""));
        assertTrue(fxml.contains("%pos.giveaway"));
        String till = read(MAIN + "controller/OrderController.java");
        assertTrue(till.contains("public void handleGiveAway()"));
        assertTrue("both buttons share one guarded entry (a payment in flight, an empty cart, the stock check)",
                till.contains("beginPayment(this::openPaymentDialog)") && till.contains("beginPayment(this::openComplimentaryDialog)")
                        && till.contains("ComplimentaryDialog.ask("));
        assertTrue("a free drink is not part of a split bill", till.contains("splitPart != null"));
    }

    @Test
    public void aFreeBillIsNeverAWayToPayNorARefundMethod() throws IOException {
        assertTrue("the split box offers no free-drink method (the rule moved out of the panel in TODO 6.5)",
                read(MAIN + "service/SplitPaymentRules.java").contains("PaymentMethod.COMPLIMENTARY"));
        assertTrue(read(MAIN + "controller/RefundLinesDialog.java").contains("PaymentMethod.COMPLIMENTARY"));
        assertTrue(read(MAIN + "dao/RefundDAO.java").contains("complimentary.problem.norefund"));
    }

    @Test
    public void theReportAndItsCsvShowFreeDrinks() throws IOException {
        String report = read(MAIN + "controller/ReportWindow.java");
        assertTrue(report.contains("ComplimentaryText.summaryLine(lastSummary)"));
        // the CSV lines are built in util/ReportCsv since TODO 2.86; the window hands over its summary
        assertTrue(read(MAIN + "util/ReportCsv.java").contains("ComplimentaryText.csvLines(data.summary())"));
        assertTrue(report.contains("new ReportCsv.Data(lastFrom, lastTo, lastSummary,"));
        assertTrue("the summary carries the figures", read(MAIN + "dao/ReportDAO.java").contains("free.totals(from, to)"));
    }

    @Test
    public void bothDatabasesHaveTheFourColumns() throws IOException {
        String sqlite = read(MAIN + "config/SqliteSchema.java");
        String maria = read(MAIN + "config/MariaDbSchema.java");
        for (String column : new String[]{"complimentary_kind", "complimentary_for", "complimentary_value", "complimentary_note"}) {
            assertTrue("SQLite adds " + column + " to older files", sqlite.contains("\"" + column + "\""));
            assertTrue("a new MariaDB database has " + column, maria.contains(column + " "));
        }
    }

    @Test
    public void theLimitAndTheMarkingAreCheckedInsideThePaymentsTransaction() throws IOException {
        String checkout = read(MAIN + "service/CheckoutService.java");
        assertTrue(checkout.contains("ComplimentaryDAO.record(connection, orderId, give, actingRole, cups, listValue)"));
        String dao = read(MAIN + "dao/OrderDAO.java");
        assertTrue("run just before the commit", dao.contains("if (after != null) after.run(connection, orderId); // TODO 4.61\n            connection.commit();"));
        assertTrue("two tills cannot both take the last cup on MariaDB", read(MAIN + "dao/ComplimentaryDAO.java").contains("FOR UPDATE"));
    }
}
