package com.coffeeshop.coffeeshopmanagement.util;

import com.coffeeshop.coffeeshopmanagement.dao.TimeClockDAO;
import com.coffeeshop.coffeeshopmanagement.model.TimeClockEntry;
import com.coffeeshop.coffeeshopmanagement.service.EarningsRules;
import com.coffeeshop.coffeeshopmanagement.service.ShopRules;
import com.coffeeshop.coffeeshopmanagement.service.TimeClockRules;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The staff hours as CSV lines for whoever does the pay (TODO 4.46): one line per person for a range of days, or one line per person per
 * day. The numbers are the ones the hours table of {@code TimeClockDialog} shows, worked out by the same {@link TimeClockRules} and
 * {@link EarningsRules}, so the file never disagrees with the screen; the days are the shop's days ({@link BusinessDay}, TODO 4.50). Hours
 * come twice, as {@code 7:30} and as {@code 7.50}: a spreadsheet may read the first as a time of day, the second is a plain number to add up.
 * Every name goes through {@link Csv#cell}, so a name that starts like a formula is neutralised (TODO 2A.1). The pay is the same estimate
 * the screen shows, not a payroll. No JavaFX and no database: the rows are passed in; {@link ExportCsv#write} writes the file.
 */
public final class StaffHoursCsv {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private StaffHoursCsv() {
    }

    /** One line per person: name, hours as h:mm and as a number, hourly rate and pay estimate (empty without a salary), and the state. */
    public static List<String> perPerson(List<TimeClockDAO.Hours> hours, Map<Integer, BigDecimal> salaries, ShopRules.Values rules) {
        List<String> lines = new ArrayList<>();
        lines.add(I18n.t("export.hours.header"));
        for (TimeClockDAO.Hours h : hours) {
            BigDecimal rate = EarningsRules.hourlyRate(salaries == null ? null : salaries.get(h.userId()), rules);
            BigDecimal pay = EarningsRules.estimate(h.minutes(), rate);
            lines.add(String.join(",",
                    Csv.cell(h.userName()),
                    TimeClockRules.hoursText(h.minutes()),
                    decimalHours(h.minutes()),
                    rate == null ? "" : rate.toPlainString(),
                    pay == null ? "" : pay.toPlainString(),
                    Csv.cell(h.forgotten() ? I18n.t("time.clock.state.forgotten") : h.clockedIn() ? I18n.t("time.clock.state.in") : "")));
        }
        return lines;
    }

    /**
     * One line per person per shop day in {@code [from, to]} (both included) on which the person has minutes, the earliest day first, then by
     * name. A stretch that crosses the end of a shop day is cut there, so each line is the time inside that day and the lines of a person add
     * up to the same total as {@link #perPerson} for the same range and the same {@code now}.
     */
    public static List<String> perDay(List<TimeClockEntry> entries, LocalDate from, LocalDate to, LocalDateTime now) {
        List<String> lines = new ArrayList<>();
        lines.add(I18n.t("export.hours.day.header"));
        if (from == null || to == null || to.isBefore(from)) return lines;
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            LocalDateTime start = BusinessDay.startOf(day);
            LocalDateTime end = BusinessDay.startOf(day.plusDays(1));
            Map<Integer, long[]> minutes = new LinkedHashMap<>();
            Map<Integer, String> names = new LinkedHashMap<>();
            for (TimeClockEntry e : entries) {
                long m = TimeClockRules.minutesWithin(e.clockIn(), e.clockOut(), start, end, now);
                if (m <= 0) continue;
                minutes.computeIfAbsent(e.userId(), k -> new long[1])[0] += m;
                names.putIfAbsent(e.userId(), e.userName());
            }
            List<Integer> people = new ArrayList<>(minutes.keySet());
            people.sort(Comparator.comparing((Integer id) -> String.valueOf(names.get(id)).toLowerCase(java.util.Locale.ROOT)).thenComparing(id -> id));
            for (Integer id : people) {
                long m = minutes.get(id)[0];
                lines.add(String.join(",", day.format(DAY), Csv.cell(names.get(id)), TimeClockRules.hoursText(m), decimalHours(m)));
            }
        }
        return lines;
    }

    /** 450 minutes as {@code 7.50}: two decimals, a point, no thousands separator. */
    static String decimalHours(long minutes) {
        return BigDecimal.valueOf(Math.max(0, minutes)).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP).toPlainString();
    }
}
