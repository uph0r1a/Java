package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.model.Customer;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import org.junit.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * TODO 6.78, mutation sweep 2026-11-16 (first batch): {@link TimeClockRules}, {@link ReceiptCost} and {@link BirthdayRules} are plain
 * rules, but their tests live in the database tests of {@code dao}, which the sweep leaves out. These tests need no database and
 * pin the edges: the hour a clock-in looks forgotten, a stretch of exactly 24 hours, the 200th character of a reason, the rounding
 * of an average cost, 29 February, and the 120th birthday.
 */
public class ClockCostBirthdayRulesTest {

    private static final LocalDateTime IN = LocalDateTime.of(2026, 11, 16, 8, 0);

    // ---- TimeClockRules

    @Test
    public void aClockInLooksForgottenFromTheLimitHourOnAndNeverWhenAnythingIsMissing() {
        int hours = TimeClockRules.forgottenAfterHours();
        assertTrue(TimeClockRules.looksForgotten(IN, IN.plusHours(hours)));
        assertFalse("a minute short of the limit", TimeClockRules.looksForgotten(IN, IN.plusHours(hours).minusMinutes(1)));
        assertFalse(TimeClockRules.looksForgotten(null, IN));
        assertFalse(TimeClockRules.looksForgotten(IN, null));
    }

    @Test
    public void minutesOfAClosedStretchAreCutByTheWindowOnly() {
        LocalDateTime out = IN.plusHours(9).plusMinutes(30);
        LocalDateTime dayStart = IN.toLocalDate().atStartOfDay();
        LocalDateTime dayEnd = dayStart.plusDays(1);
        assertEquals(570, TimeClockRules.minutesWithin(IN, out, dayStart, dayEnd, out));
        assertEquals("the window starts at 9:00", 510, TimeClockRules.minutesWithin(IN, out, IN.plusHours(1), dayEnd, out));
        assertEquals("the window ends at 12:00", 240, TimeClockRules.minutesWithin(IN, out, dayStart, IN.plusHours(4), out));
        assertEquals("a stretch longer than the forgotten limit is not cut when it is closed", 30 * 60,
                TimeClockRules.minutesWithin(IN, IN.plusHours(30), dayStart, dayStart.plusDays(5), IN.plusHours(31)));
    }

    @Test
    public void minutesOfAnOpenStretchEndAtNowButNeverLaterThanTheForgottenLimit() {
        LocalDateTime dayStart = IN.toLocalDate().atStartOfDay();
        assertEquals("open for three hours", 180, TimeClockRules.minutesWithin(IN, null, dayStart, dayStart.plusDays(5), IN.plusHours(3)));
        int hours = TimeClockRules.forgottenAfterHours();
        assertEquals("open for the limit plus five hours counts only the limit", hours * 60L,
                TimeClockRules.minutesWithin(IN, null, dayStart, dayStart.plusDays(5), IN.plusHours(hours + 5)));
        assertEquals("exactly the limit", hours * 60L, TimeClockRules.minutesWithin(IN, null, dayStart, dayStart.plusDays(5), IN.plusHours(hours)));
    }

    @Test
    public void minutesAreNeverNegativeAndMissingTimesCountForNothing() {
        LocalDateTime out = IN.plusHours(2);
        assertEquals("the window is after the stretch", 0, TimeClockRules.minutesWithin(IN, out, IN.plusHours(5), IN.plusHours(6), out));
        assertEquals("the window ends where the stretch starts", 0, TimeClockRules.minutesWithin(IN, out, IN.minusHours(3), IN, out));
        assertEquals(0, TimeClockRules.minutesWithin(null, out, IN, out, out));
        assertEquals(0, TimeClockRules.minutesWithin(IN, out, null, out, out));
        assertEquals(0, TimeClockRules.minutesWithin(IN, out, IN, null, out));
        assertEquals("open and no now", 0, TimeClockRules.minutesWithin(IN, null, IN, out, null));
    }

    @Test
    public void hoursAreWrittenAsHoursAndTwoDigitMinutesWithoutACutAtTwentyFour() {
        assertEquals("7:30", TimeClockRules.hoursText(450));
        assertEquals("25:00", TimeClockRules.hoursText(1500));
        assertEquals("0:05", TimeClockRules.hoursText(5));
        assertEquals("0:00", TimeClockRules.hoursText(0));
        assertEquals("0:00", TimeClockRules.hoursText(-5));
    }

    @Test
    public void aTypedTimeIsReadTrimmedAndWrittenBackTheSameWay() {
        assertEquals(LocalDateTime.of(2026, 10, 30, 17, 30), TimeClockRules.parseTyped("2026-10-30 17:30"));
        assertEquals(LocalDateTime.of(2026, 10, 30, 17, 30), TimeClockRules.parseTyped("  2026-10-30 17:30 "));
        assertNull(TimeClockRules.parseTyped("30/10/2026 17:30"));
        assertNull(TimeClockRules.parseTyped(""));
        assertNull(TimeClockRules.parseTyped(null));
        assertEquals("2026-10-30 07:05", TimeClockRules.typed(LocalDateTime.of(2026, 10, 30, 7, 5)));
        assertEquals("", TimeClockRules.typed(null));
    }

    @Test
    public void aStretchWrittenByHandIsCheckedInOrder() {
        LocalDateTime now = IN.plusDays(2);
        assertEquals("TIME", TimeClockRules.stretchProblem(null, IN, now));
        assertEquals("TIME", TimeClockRules.stretchProblem(IN, null, now));
        assertEquals("BEFORE", TimeClockRules.stretchProblem(IN, IN, now));
        assertEquals("BEFORE", TimeClockRules.stretchProblem(IN, IN.minusMinutes(1), now));
        assertEquals("FUTURE", TimeClockRules.stretchProblem(now.plusMinutes(1), now.plusHours(2), now));
        assertEquals("FUTURE", TimeClockRules.stretchProblem(IN, now.plusMinutes(1), now));
        assertNull("ending exactly now is fine", TimeClockRules.stretchProblem(IN.plusHours(30), now, now));
        assertNull("exactly 24 hours is fine", TimeClockRules.stretchProblem(IN, IN.plusHours(TimeClockRules.MAX_STRETCH_HOURS), now));
        assertEquals("LONG", TimeClockRules.stretchProblem(IN, IN.plusHours(TimeClockRules.MAX_STRETCH_HOURS).plusMinutes(1), now));
    }

    @Test
    public void aCorrectedEndIsCheckedInOrder() {
        LocalDateTime now = IN.plusDays(2);
        assertEquals("TIME", TimeClockRules.correctionProblem(IN, null, now));
        assertEquals("BEFORE", TimeClockRules.correctionProblem(IN, IN, now));
        assertEquals("BEFORE", TimeClockRules.correctionProblem(IN, IN.minusMinutes(1), now));
        assertEquals("FUTURE", TimeClockRules.correctionProblem(IN, now.plusMinutes(1), now));
        assertNull("exactly now", TimeClockRules.correctionProblem(IN.plusHours(30), now, now));
        assertNull("exactly 24 hours", TimeClockRules.correctionProblem(IN, IN.plusHours(TimeClockRules.MAX_STRETCH_HOURS), now));
        assertEquals("LONG", TimeClockRules.correctionProblem(IN, IN.plusHours(TimeClockRules.MAX_STRETCH_HOURS).plusMinutes(1), now));
    }

    @Test
    public void twoStretchesOverlapWhenTheyShareTimeAndNotWhenTheyOnlyTouch() {
        LocalDateTime nine = IN.plusHours(1);
        LocalDateTime noon = IN.plusHours(4);
        LocalDateTime two = IN.plusHours(6);
        assertTrue(TimeClockRules.overlaps(IN, noon, nine, two));
        assertTrue("one inside the other", TimeClockRules.overlaps(IN, two, nine, noon));
        assertFalse("ends touch", TimeClockRules.overlaps(IN, nine, nine, noon));
        assertFalse("ends touch the other way", TimeClockRules.overlaps(nine, noon, IN, nine));
        assertFalse("apart", TimeClockRules.overlaps(IN, nine, noon, two));
        assertTrue("an open stretch reaches every later one", TimeClockRules.overlaps(IN, null, noon, two));
        assertFalse("but not an earlier one", TimeClockRules.overlaps(noon, null, IN, nine));
        assertTrue("two open stretches", TimeClockRules.overlaps(IN, null, nine, null));
    }

    @Test
    public void aReasonIsRequiredAndMayBeTwoHundredCharactersLong() {
        assertEquals("REASON", TimeClockRules.reasonProblem(null));
        assertEquals("REASON", TimeClockRules.reasonProblem("   "));
        assertNull(TimeClockRules.reasonProblem("a".repeat(TimeClockRules.MAX_REASON_LENGTH)));
        assertNull("spaces around it do not count", TimeClockRules.reasonProblem("  " + "a".repeat(TimeClockRules.MAX_REASON_LENGTH) + "  "));
        assertEquals("LONG", TimeClockRules.reasonProblem("a".repeat(TimeClockRules.MAX_REASON_LENGTH + 1)));
    }

    // ---- ReceiptCost

    private static BigDecimal d(String s) {
        return new BigDecimal(s);
    }

    @Test
    public void aReceiptPriceDiffersOnlyWhenOneWasTypedAndItIsNotTheCostAlready() {
        assertFalse("nothing typed", ReceiptCost.differs(d("100"), null));
        assertTrue("the product has no cost yet", ReceiptCost.differs(null, d("100")));
        assertFalse("the same amount with a different scale", ReceiptCost.differs(d("100"), d("100.00")));
        assertTrue(ReceiptCost.differs(d("100"), d("101")));
    }

    @Test
    public void anAverageCostIsWeightedByStockAndRoundedHalfUpToWholeDong() {
        assertEquals(d("15000"), ReceiptCost.average(10, d("10000"), 10, d("20000")));
        assertEquals("(10000 + 20002) / 3 = 10000.67", d("10001"), ReceiptCost.average(1, d("10000"), 2, d("10001")));
        assertEquals("(1 * 10 + 1 * 11) / 2 = 10.5 rounds up", d("11"), ReceiptCost.average(1, d("10"), 1, d("11")));
        assertEquals("no stock: the new price", d("20000"), ReceiptCost.average(0, d("10000"), 5, d("20000")));
        assertEquals("negative stock counts for nothing", d("20000"), ReceiptCost.average(-3, d("10000"), 5, d("20000")));
        assertEquals("no old cost", d("20000"), ReceiptCost.average(10, null, 5, d("20000")));
        assertEquals("nothing arrived", d("20000"), ReceiptCost.average(10, d("10000"), 0, d("20000")));
        assertEquals("one in stock, one arrived", d("15000"), ReceiptCost.average(1, d("10000"), 1, d("20000")));
    }

    @Test
    public void theCostAfterADeliveryFollowsTheChosenMode() {
        BigDecimal old = d("10000");
        BigDecimal price = d("20000");
        assertEquals(old, ReceiptCost.resulting(ReceiptCost.Mode.KEEP, 10, old, 10, price));
        assertEquals(old, ReceiptCost.resulting(null, 10, old, 10, price));
        assertEquals("nothing typed", old, ReceiptCost.resulting(ReceiptCost.Mode.REPLACE, 10, old, 10, null));
        assertEquals("nothing arrived", old, ReceiptCost.resulting(ReceiptCost.Mode.REPLACE, 10, old, 0, price));
        assertEquals("one arrived is something", price, ReceiptCost.resulting(ReceiptCost.Mode.REPLACE, 10, old, 1, price));
        assertEquals(d("15000"), ReceiptCost.resulting(ReceiptCost.Mode.AVERAGE, 10, old, 10, price));
    }

    // ---- BirthdayRules

    @Test
    public void aStoredBirthdayIsReadFromADateOrADateTimeAndNeverFailsOnJunk() {
        LocalDate day = LocalDate.of(2000, 5, 17);
        assertEquals(day, BirthdayRules.parse("2000-05-17"));
        assertEquals(day, BirthdayRules.parse("2000-05-17 10:20:30"));
        assertEquals(day, BirthdayRules.parse("2000-05-17T10:20"));
        assertEquals(day, BirthdayRules.parse("  2000-05-17  "));
        assertNull(BirthdayRules.parse("2000-13-45"));
        assertNull(BirthdayRules.parse("17/05/2000"));
        assertNull(BirthdayRules.parse("   "));
        assertNull(BirthdayRules.parse(null));
    }

    @Test
    public void aBirthdayMayBeTodayAndMayBeTheDayOfAHundredAndTwentyYearsAgoButNotLater() {
        LocalDate today = LocalDate.of(2026, 11, 16);
        assertNull("not given", BirthdayRules.problem(null, today));
        assertNull(BirthdayRules.problem(today, today));
        assertEquals(I18n.t("birthday.val.future"), BirthdayRules.problem(today.plusDays(1), today));
        assertNull("exactly 120 years", BirthdayRules.problem(today.minusYears(BirthdayRules.MAX_AGE_YEARS), today));
        assertEquals(I18n.t("birthday.val.old"), BirthdayRules.problem(today.minusYears(BirthdayRules.MAX_AGE_YEARS).minusDays(1), today));
        assertNotEquals(I18n.t("birthday.val.future"), I18n.t("birthday.val.old"));
    }

    @Test
    public void aBirthdayIsOnTheSameMonthAndDayAndAnEmptyDateIsNever() {
        LocalDate birthday = LocalDate.of(2000, 5, 17);
        assertTrue(BirthdayRules.isBirthday(birthday, LocalDate.of(2026, 5, 17)));
        assertFalse("another day", BirthdayRules.isBirthday(birthday, LocalDate.of(2026, 5, 18)));
        assertFalse("another month", BirthdayRules.isBirthday(birthday, LocalDate.of(2026, 6, 17)));
        assertFalse(BirthdayRules.isBirthday(null, LocalDate.of(2026, 5, 17)));
        assertFalse(BirthdayRules.isBirthday(birthday, null));
    }

    @Test
    public void the29thOfFebruaryIsCelebratedOnThe28thInAYearWithoutIt() {
        LocalDate leapDay = LocalDate.of(2000, 2, 29);
        assertTrue("2026 has no 29 February", BirthdayRules.isBirthday(leapDay, LocalDate.of(2026, 2, 28)));
        assertFalse("and not on 1 March", BirthdayRules.isBirthday(leapDay, LocalDate.of(2026, 3, 1)));
        assertTrue("2028 has it", BirthdayRules.isBirthday(leapDay, LocalDate.of(2028, 2, 29)));
        assertFalse("and then the 28th is an ordinary day", BirthdayRules.isBirthday(leapDay, LocalDate.of(2028, 2, 28)));
        assertFalse("a birthday on the 28th is not moved", BirthdayRules.isBirthday(LocalDate.of(2000, 2, 28), LocalDate.of(2028, 2, 29)));
    }

    private static Customer customer(LocalDate birthday, LocalDate usedOn, boolean anonymised) {
        Customer customer = new Customer();
        customer.setBirthday(birthday);
        customer.setBirthdayUsedOn(usedOn);
        if (anonymised) customer.setAnonymisedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        return customer;
    }

    @Test
    public void theBirthdayDiscountIsOfferedOnceADayToACustomerWhoIsNotForgotten() {
        LocalDate today = LocalDate.of(2026, 5, 17);
        LocalDate birthday = LocalDate.of(2000, 5, 17);
        assertTrue(BirthdayRules.isOffered(customer(birthday, null, false), today));
        assertFalse("given today already", BirthdayRules.isOffered(customer(birthday, today, false), today));
        assertTrue("given yesterday", BirthdayRules.isOffered(customer(birthday, today.minusDays(1), false), today));
        assertFalse("anonymised", BirthdayRules.isOffered(customer(birthday, null, true), today));
        assertFalse("another day", BirthdayRules.isOffered(customer(birthday, null, false), today.plusDays(1)));
        assertFalse(BirthdayRules.isOffered(null, today));
        assertTrue(BirthdayRules.usedToday(customer(birthday, today, false), today));
        assertFalse(BirthdayRules.usedToday(customer(birthday, null, false), today));
        assertFalse(BirthdayRules.usedToday(null, today));
        assertFalse(BirthdayRules.usedToday(customer(birthday, today, false), null));
    }

    @Test
    public void theBadgeSaysHowToGiveTheDiscountOrThatItWasGivenAndIsEmptyOtherwise() {
        LocalDate today = LocalDate.of(2026, 5, 17);
        LocalDate birthday = LocalDate.of(2000, 5, 17);
        assertEquals("  ·  " + I18n.t("birthday.badge", BirthdayRules.suggestedPercent()), BirthdayRules.badge(customer(birthday, null, false), today));
        assertEquals("  ·  " + I18n.t("birthday.badge.used"), BirthdayRules.badge(customer(birthday, today, false), today));
        assertEquals("", BirthdayRules.badge(customer(birthday, null, false), today.plusDays(1)));
        assertEquals("", BirthdayRules.badge(customer(birthday, null, true), today));
        assertEquals("", BirthdayRules.badge(null, today));
    }

    @Test
    public void theBirthdayDiscountMustBeExactlyWhatTheButtonFillsIn() {
        BigDecimal subtotal = d("100000");
        BigDecimal exact = BirthdayRules.discountFor(subtotal);
        assertEquals(d("10000"), exact);
        assertNull(BirthdayRules.problemToUse(7, subtotal, exact));
        assertNull("the same amount with a different scale", BirthdayRules.problemToUse(7, subtotal, d("10000.00")));
        assertEquals(I18n.t("birthday.use.nocustomer"), BirthdayRules.problemToUse(null, subtotal, exact));
        String wrong = I18n.t("birthday.use.discount", BirthdayRules.suggestedPercent());
        assertEquals(wrong, BirthdayRules.problemToUse(7, subtotal, exact.add(BigDecimal.ONE)));
        assertEquals(wrong, BirthdayRules.problemToUse(7, null, exact));
        assertEquals(wrong, BirthdayRules.problemToUse(7, subtotal, null));
        assertNotEquals(wrong, I18n.t("birthday.use.nocustomer"));
    }
}
