package com.coffeeshop.coffeeshopmanagement.util;

import com.coffeeshop.coffeeshopmanagement.dao.ReportDAO;

import java.util.ArrayList;
import java.util.List;

/**
 * The words about waste and breakage in the report window and its CSV (TODO 4.33). No screen, so the wording and the rule "say
 * nothing when there was none" can be tested.
 */
public final class WasteText {

    private WasteText() {
    }

    /**
     * The extra line under the summary: lines, pieces and cost, with a note when some lines had no cost price. Empty when nothing
     * was written off in the range, so a quiet period is not cluttered.
     */
    public static String summaryLine(ReportDAO.Waste waste) {
        if (waste == null || waste.lines() == 0) {
            return "";
        }
        String note = waste.linesWithoutCost() == 0 ? "" : "  " + I18n.t("report.wastenote", waste.linesWithoutCost());
        return I18n.t("report.waste", waste.lines(), waste.pieces(), CurrencyUtil.format(waste.cost()), note);
    }

    /** The rows added to the CSV (always written, also with 0, so the file has the same rows every time). */
    public static List<String> csvLines(ReportDAO.Waste waste) {
        ReportDAO.Waste w = waste == null ? new ReportDAO.Waste(0, 0, java.math.BigDecimal.ZERO, 0) : waste;
        List<String> lines = new ArrayList<>();
        lines.add(I18n.t("report.csv.waste.pieces") + "," + w.pieces());
        lines.add(I18n.t("report.csv.waste.cost") + "," + w.cost().toPlainString());
        lines.add(I18n.t("report.csv.waste.nocost") + "," + w.linesWithoutCost());
        return lines;
    }
}
