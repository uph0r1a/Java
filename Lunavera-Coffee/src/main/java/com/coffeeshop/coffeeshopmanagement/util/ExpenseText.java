package com.coffeeshop.coffeeshopmanagement.util;

import com.coffeeshop.coffeeshopmanagement.dao.ReportDAO;
import com.coffeeshop.coffeeshopmanagement.service.ExpenseRules;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * The words about expenses and net profit in the report window and its CSV (TODO 4.38). No screen, so the wording and the sums can be
 * tested. Net profit is gross profit minus the cost of waste minus the expenses ({@link ExpenseRules#netProfit}).
 */
public final class ExpenseText {

    private ExpenseText() {
    }

    private static BigDecimal wasteCost(ReportDAO.Waste waste) {
        return waste == null ? BigDecimal.ZERO : waste.cost();
    }

    private static BigDecimal total(ReportDAO.Expenses expenses) {
        return expenses == null ? BigDecimal.ZERO : expenses.total();
    }

    /** The net profit of a range, the figure shown as "Lợi nhuận ròng". */
    public static BigDecimal net(BigDecimal grossProfit, ReportDAO.Waste waste, ReportDAO.Expenses expenses) {
        return ExpenseRules.netProfit(grossProfit, wasteCost(waste), total(expenses));
    }

    /**
     * The lines under the summary: expenses and net profit with how it is made up (always shown, so a period with no expense still
     * says that net profit is gross profit less waste), then the expenses by category when there were any.
     */
    public static String summaryLines(BigDecimal grossProfit, ReportDAO.Waste waste, ReportDAO.Expenses expenses) {
        BigDecimal spent = total(expenses);
        String text = I18n.t("report.net", CurrencyUtil.format(spent), CurrencyUtil.format(net(grossProfit, waste, expenses)),
                CurrencyUtil.format(grossProfit == null ? BigDecimal.ZERO : grossProfit), CurrencyUtil.format(wasteCost(waste)));
        if (expenses != null && !expenses.byCategory().isEmpty()) {
            List<String> parts = new ArrayList<>();
            expenses.byCategory().forEach((code, amount) -> parts.add(ExpenseRules.categoryName(code) + " " + CurrencyUtil.format(amount)));
            text += "\n" + I18n.t("report.expenses.bycategory", String.join("  •  ", parts));
        }
        return text;
    }

    /** The rows added to the CSV: always the same rows (every category, also with 0), so two files can be put side by side. */
    public static List<String> csvLines(BigDecimal grossProfit, ReportDAO.Waste waste, ReportDAO.Expenses expenses) {
        List<String> lines = new ArrayList<>();
        lines.add(I18n.t("report.csv.expenses") + "," + total(expenses).toPlainString());
        for (String code : ExpenseRules.CATEGORIES) {
            BigDecimal amount = expenses == null ? null : expenses.byCategory().get(code);
            lines.add(I18n.t("report.csv.expenses.cat", ExpenseRules.categoryName(code)) + ","
                    + (amount == null ? BigDecimal.ZERO : amount).toPlainString());
        }
        lines.add(I18n.t("report.csv.net") + "," + net(grossProfit, waste, expenses).toPlainString());
        return lines;
    }
}
