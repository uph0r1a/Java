package com.coffeeshop.coffeeshopmanagement.util;

import com.coffeeshop.coffeeshopmanagement.dao.ReportDAO.ChannelSales;

import java.util.ArrayList;
import java.util.List;

/** The text of the by-channel figures of the report (TODO 4.48): the line under the payment line, and the block of the CSV. Kept out of {@code ReportWindow} (TODO 6.54). */
public final class ChannelText {

    private ChannelText() {
    }

    /** "\nBy channel: Dine-in: 1.200.000 đ (8 orders)   •   GrabFood: ..." ; empty when no order sold in the period. */
    public static String line(List<ChannelSales> channels) {
        if (channels.isEmpty()) return "";
        StringBuilder text = new StringBuilder("\n" + I18n.t("report.channel.prefix") + " ");
        for (int i = 0; i < channels.size(); i++) {
            ChannelSales part = channels.get(i);
            if (i > 0) text.append("   •   ");
            text.append(part.channel().label()).append(": ").append(CurrencyUtil.format(part.revenue()))
                    .append(" (").append(I18n.t("report.orders.count", part.orders())).append(")");
        }
        return text.toString();
    }

    /** A blank line, the header and one line per channel: name, orders, revenue in whole dong (the other blocks of the CSV, TODO 5.34). */
    public static List<String> csvLines(List<ChannelSales> channels) {
        List<String> lines = new ArrayList<>();
        lines.add("");
        lines.add(I18n.t("report.csv.channel.header"));
        for (ChannelSales part : channels) {
            lines.add(Csv.cell(part.channel().label()) + "," + part.orders() + "," + part.revenue().toPlainString());
        }
        return lines;
    }
}
