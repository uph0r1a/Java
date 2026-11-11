package com.coffeeshop.coffeeshopmanagement.util;

import com.coffeeshop.coffeeshopmanagement.dao.ComplimentaryDAO;
import com.coffeeshop.coffeeshopmanagement.dao.ReportDAO;
import com.coffeeshop.coffeeshopmanagement.service.ComplimentaryRules;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The words about free drinks in the report window and its CSV (TODO 4.61). No screen, so the wording and the rule "say nothing when nothing was
 * given away" can be tested.
 */
public final class ComplimentaryText {

    private ComplimentaryText() {
    }

    /**
     * The extra line under the summary, starting with a line break so it can be appended: cups and bills, how many to whom, what they cost and what
     * they would have cost at the menu price, with a note when the cost of some cups is not known. Empty when nothing was given away.
     */
    public static String summaryLine(ReportDAO.Summary summary) {
        if (summary == null || summary.free() == null || summary.free().isNone()) return "";
        ComplimentaryDAO.Totals t = summary.free();
        Map<ComplimentaryRules.Kind, Integer> byKind = new EnumMap<>(ComplimentaryRules.Kind.class);
        for (ComplimentaryDAO.PersonLine line : summary.freePeople()) byKind.merge(line.kind(), line.cups(), Integer::sum);
        StringBuilder kinds = new StringBuilder();
        for (Map.Entry<ComplimentaryRules.Kind, Integer> e : byKind.entrySet()) {
            if (e.getValue() == 0) continue;
            if (kinds.length() > 0) kinds.append(", ");
            kinds.append(e.getKey().label()).append(' ').append(e.getValue());
        }
        String note = t.cupsWithoutCost() == 0 ? "" : "  " + I18n.t("report.free.nocost", t.cupsWithoutCost());
        return "\n" + I18n.t("report.free", t.cups(), t.bills(), kinds.length() == 0 ? "-" : kinds.toString(),
                CurrencyUtil.format(t.cost()), CurrencyUtil.format(t.value()), note);
    }

    /** The rows added to the CSV: always the three totals (also with 0, so the file has the same rows every time), then one row per kind and person. */
    public static List<String> csvLines(ReportDAO.Summary summary) {
        ComplimentaryDAO.Totals t = summary == null || summary.free() == null ? ComplimentaryDAO.Totals.NONE : summary.free();
        List<String> lines = new ArrayList<>();
        lines.add(I18n.t("report.csv.free.cups") + "," + t.cups());
        lines.add(I18n.t("report.csv.free.cost") + "," + t.cost().toPlainString());
        lines.add(I18n.t("report.csv.free.value") + "," + t.value().toPlainString());
        if (summary != null && !summary.freePeople().isEmpty()) {
            lines.add(I18n.t("report.csv.free.header"));
            for (ComplimentaryDAO.PersonLine p : summary.freePeople()) {
                lines.add(Csv.cell(p.kind().label()) + "," + Csv.cell(p.name() == null ? "" : p.name()) + "," + p.bills() + "," + p.cups() + ","
                        + p.cost().toPlainString() + "," + p.value().toPlainString());
            }
        }
        return lines;
    }
}
