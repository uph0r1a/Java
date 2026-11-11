package com.coffeeshop.coffeeshopmanagement.util;

import com.coffeeshop.coffeeshopmanagement.dao.TimeClockDAO;
import com.coffeeshop.coffeeshopmanagement.model.TimeClockEntry;
import com.coffeeshop.coffeeshopmanagement.service.EarningsRules;
import com.coffeeshop.coffeeshopmanagement.service.ShopRules;
import com.coffeeshop.coffeeshopmanagement.service.TimeClockRules;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * TODO 4.46: the staff hours as CSV: one line per person (the numbers the hours table shows, worked out by the same rules) and one line per
 * person per shop day (a stretch across the end of a day is cut there, and the days add up to the total). Names are neutralised like in every
 * other export. No database, no JavaFX.
 */
public class StaffHoursCsvTest {

    private String language;

    @Before
    public void english() {
        language = I18n.current();
        I18n.setLanguage("en");
        ShopRules.resetForTests();
        BusinessDay.resetForTests();
    }

    @After
    public void putBack() {
        I18n.setLanguage(language);
        ShopRules.resetForTests();
        BusinessDay.resetForTests();
    }

    private static TimeClockDAO.Hours hours(int id, String name, long minutes, boolean forgotten, boolean in) {
        return new TimeClockDAO.Hours(id, name, minutes, forgotten, in);
    }

    private static LocalDateTime at(int day, int hour, int minute) {
        return LocalDateTime.of(2026, 11, day, hour, minute);
    }

    private static TimeClockEntry entry(int id, int user, String name, LocalDateTime in, LocalDateTime out) {
        return new TimeClockEntry(id, user, name, in, out);
    }

    @Test
    public void theHoursPerPersonHaveTheHeaderAndOneLineEach() {
        List<String> lines = StaffHoursCsv.perPerson(List.of(hours(1, "Mai", 450, false, false), hours(2, "Linh", 90, false, false)), Map.of(),
                ShopRules.current());
        assertEquals(I18n.t("export.hours.header"), lines.get(0));
        assertEquals(3, lines.size());
        assertEquals("Mai,7:30,7.50,,,", lines.get(1));
        assertEquals("Linh,1:30,1.50,,,", lines.get(2));
    }

    @Test
    public void theRateAndThePayAreThoseTheScreenShows() {
        ShopRules.Values hourly = ShopRules.parse("10", "10000", "1000", "50", "20", "100", "500", "10", "90", "15", "16", "1", "208");
        assertNotNull(hourly);
        ShopRules.set(hourly); // the salary box holds an hourly rate
        List<String> lines = StaffHoursCsv.perPerson(List.of(hours(1, "Mai", 90, false, false)), Map.of(1, new BigDecimal("30000")), ShopRules.current());
        assertEquals("Mai,1:30,1.50,30000,45000,", lines.get(1));
        ShopRules.Values monthly = ShopRules.parse("10", "10000", "1000", "50", "20", "100", "500", "10", "90", "15", "16", "0", "208");
        ShopRules.set(monthly);
        BigDecimal rate = EarningsRules.hourlyRate(new BigDecimal("6240000"), ShopRules.current());
        BigDecimal pay = EarningsRules.estimate(450, rate);
        String line = StaffHoursCsv.perPerson(List.of(hours(1, "Mai", 450, false, false)), Map.of(1, new BigDecimal("6240000")), ShopRules.current()).get(1);
        assertEquals("Mai,7:30,7.50," + rate.toPlainString() + "," + pay.toPlainString() + ",", line);
        assertTrue("a rate and a pay, not blanks", rate.signum() > 0 && pay.signum() > 0);
    }

    @Test
    public void aPersonWithNoSalaryHasEmptyRateAndPayNotZero() {
        String line = StaffHoursCsv.perPerson(List.of(hours(7, "Nam", 60, false, false)), Map.of(1, BigDecimal.TEN), ShopRules.current()).get(1);
        assertEquals("Nam,1:00,1.00,,,", line);
        assertEquals("a missing map of salaries too", "Nam,1:00,1.00,,,",
                StaffHoursCsv.perPerson(List.of(hours(7, "Nam", 60, false, false)), null, ShopRules.current()).get(1));
    }

    @Test
    public void theStateIsSaidInWords() {
        List<String> lines = StaffHoursCsv.perPerson(List.of(hours(1, "A", 10, false, true), hours(2, "B", 10, true, true), hours(3, "C", 10, false, false)),
                Map.of(), ShopRules.current());
        assertTrue(lines.get(1), lines.get(1).endsWith("," + I18n.t("time.clock.state.in")));
        assertTrue(lines.get(2), lines.get(2).endsWith("," + Csv.cell(I18n.t("time.clock.state.forgotten"))));
        assertEquals("nothing to say for a finished day", "C,0:10,0.17,,,", lines.get(3));
    }

    @Test
    public void aNameThatLooksLikeAFormulaOrHasACommaIsSafe() {
        List<String> lines = StaffHoursCsv.perPerson(List.of(hours(1, "=HYPERLINK(\"http://x\")", 60, false, false), hours(2, "Tran, Mai", 60, false, false)),
                Map.of(), ShopRules.current());
        assertFalse("no cell starts with = (the formula is neutralised)", lines.get(1).startsWith("="));
        assertTrue(lines.get(1), lines.get(1).startsWith(Csv.cell("=HYPERLINK(\"http://x\")") + ","));
        assertTrue("a comma in a name is one cell", lines.get(2).startsWith("\"Tran, Mai\","));
        List<String> day = StaffHoursCsv.perDay(List.of(entry(1, 1, "=1+1", at(2, 8, 0), at(2, 9, 0))), LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 2), at(3, 0, 0));
        assertFalse(day.get(1).contains(",=1+1,"));
    }

    @Test
    public void decimalHoursAreTwoPlacesWithAPoint() {
        assertEquals("7.50", StaffHoursCsv.decimalHours(450));
        assertEquals("0.00", StaffHoursCsv.decimalHours(0));
        assertEquals("0.02", StaffHoursCsv.decimalHours(1));
        assertEquals("0.98", StaffHoursCsv.decimalHours(59));
        assertEquals("100.00", StaffHoursCsv.decimalHours(6000));
        assertEquals("never negative", "0.00", StaffHoursCsv.decimalHours(-5));
    }

    @Test
    public void perDayHasOneLinePerPersonPerDayWithTimeAndNothingForEmptyDays() {
        LocalDate from = LocalDate.of(2026, 11, 2), to = LocalDate.of(2026, 11, 5);
        List<TimeClockEntry> entries = List.of(
                entry(1, 1, "Mai", at(2, 8, 0), at(2, 12, 30)),
                entry(2, 2, "Linh", at(2, 9, 0), at(2, 10, 0)),
                entry(3, 1, "Mai", at(2, 13, 0), at(2, 14, 0)),
                entry(4, 2, "Linh", at(4, 8, 0), at(4, 16, 0)));
        List<String> lines = StaffHoursCsv.perDay(entries, from, to, at(6, 0, 0));
        assertEquals(I18n.t("export.hours.day.header"), lines.get(0));
        assertEquals(List.of("02/11/2026,Linh,1:00,1.00", "02/11/2026,Mai,5:30,5.50", "04/11/2026,Linh,8:00,8.00"), lines.subList(1, lines.size()));
    }

    @Test
    public void aStretchAcrossMidnightIsCutAtTheEndOfTheDayAndTheDaysAddUp() {
        LocalDate from = LocalDate.of(2026, 11, 2), to = LocalDate.of(2026, 11, 3);
        TimeClockEntry night = entry(1, 1, "Mai", at(2, 22, 0), at(3, 2, 30));
        List<String> lines = StaffHoursCsv.perDay(List.of(night), from, to, at(5, 0, 0));
        assertEquals(List.of("02/11/2026,Mai,2:00,2.00", "03/11/2026,Mai,2:30,2.50"), lines.subList(1, lines.size()));
        long total = TimeClockRules.minutesWithin(night.clockIn(), night.clockOut(), BusinessDay.startOf(from), BusinessDay.startOf(to.plusDays(1)), at(5, 0, 0));
        assertEquals("the two days add up to the person's total", 270, total);
    }

    @Test
    public void theShopsDayCanStartLaterThanMidnight() {
        BusinessDay.setStartMinutes(4 * 60); // the shop's day starts at 04:00 (TODO 4.50)
        LocalDate day = LocalDate.of(2026, 11, 2);
        TimeClockEntry late = entry(1, 1, "Mai", at(2, 22, 0), at(3, 2, 0)); // all inside the shop day that began on the 2nd
        List<String> lines = StaffHoursCsv.perDay(List.of(late), day, day.plusDays(1), at(5, 0, 0));
        assertEquals(List.of("02/11/2026,Mai,4:00,4.00"), lines.subList(1, lines.size()));
    }

    @Test
    public void anOpenStretchIsCountedUpToNowAndNeverBeyondTheForgottenLimit() {
        LocalDate day = LocalDate.of(2026, 11, 2);
        TimeClockEntry open = entry(1, 1, "Mai", at(2, 8, 0), null);
        List<String> lines = StaffHoursCsv.perDay(List.of(open), day, day, at(2, 10, 15));
        assertEquals(List.of("02/11/2026,Mai,2:15,2.25"), lines.subList(1, lines.size()));
        long perPerson = TimeClockRules.minutesWithin(open.clockIn(), null, BusinessDay.startOf(day), BusinessDay.startOf(day.plusDays(1)), at(2, 10, 15));
        assertEquals(135, perPerson);
    }

    @Test
    public void aRangeTheWrongWayRoundOrNoEntriesGivesTheHeaderOnly() {
        assertEquals(1, StaffHoursCsv.perDay(List.of(), LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 3), at(5, 0, 0)).size());
        assertEquals(1, StaffHoursCsv.perDay(List.of(entry(1, 1, "Mai", at(2, 8, 0), at(2, 9, 0))), LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 2), at(5, 0, 0)).size());
        assertEquals(1, StaffHoursCsv.perDay(List.of(), null, null, at(5, 0, 0)).size());
        assertEquals(1, StaffHoursCsv.perPerson(List.of(), Map.of(), ShopRules.current()).size());
    }

    @Test
    public void bothLanguagesHaveTheHeaders() {
        for (String language : new String[]{"en", "vi"}) {
            assertFalse(I18n.in(language, "export.hours.header").isBlank());
            assertFalse(I18n.in(language, "export.hours.day.header").isBlank());
            assertEquals("the same number of columns in both languages", 6, I18n.in(language, "export.hours.header").split(",").length);
            assertEquals(4, I18n.in(language, "export.hours.day.header").split(",").length);
        }
    }
}
