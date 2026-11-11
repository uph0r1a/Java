package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.dao.ReportDAO.DiscountLine;
import com.coffeeshop.coffeeshopmanagement.util.TextNormalizer;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** The rules of the "discounts given" report (TODO 2.37): joining reasons that are the same reason typed a little differently. */
public final class DiscountReport {

    private DiscountReport() {
    }

    /** What to show for a reason: cleaned and trimmed, empty when none (the screen says "no reason" for it). */
    public static String shownReason(String reason) {
        String text = TextNormalizer.nfc(reason);
        return text == null ? "" : text.replaceAll("\\s+", " ");
    }

    /**
     * Lines of one person whose reasons are equal apart from capitals, spaces and accent form become one line (the text of the first,
     * biggest, is shown); lines with no orders and no amount are left out. Order: biggest amount first, then the name.
     */
    public static List<DiscountLine> merge(List<DiscountLine> lines) {
        Map<String, DiscountLine> merged = new LinkedHashMap<>();
        for (DiscountLine line : lines) {
            String reason = shownReason(line.reason());
            String key = Objects.toString(line.employeeId(), "-") + "|" + TextNormalizer.key(reason);
            DiscountLine old = merged.get(key);
            merged.put(key, old == null
                    ? new DiscountLine(line.employeeId(), line.employeeName(), reason, line.orders(), line.amount())
                    : new DiscountLine(old.employeeId(), old.employeeName(), old.reason(), old.orders() + line.orders(), old.amount().add(line.amount())));
        }
        List<DiscountLine> result = new ArrayList<>();
        for (DiscountLine line : merged.values()) {
            if (line.orders() != 0 || line.amount().signum() != 0) result.add(line);
        }
        result.sort((a, b) -> {
            int byAmount = b.amount().compareTo(a.amount());
            return byAmount != 0 ? byAmount : Objects.toString(a.employeeName(), "").compareToIgnoreCase(Objects.toString(b.employeeName(), ""));
        });
        return result;
    }

    /** The total of the lines: the discounts of the period, equal to the summary's figure. */
    public static BigDecimal total(List<DiscountLine> lines) {
        BigDecimal sum = BigDecimal.ZERO;
        for (DiscountLine line : lines) sum = sum.add(line.amount());
        return sum;
    }
}
