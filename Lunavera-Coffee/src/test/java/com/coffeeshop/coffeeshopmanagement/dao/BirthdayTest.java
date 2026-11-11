package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.model.Customer;
import com.coffeeshop.coffeeshopmanagement.service.BirthdayRules;
import com.coffeeshop.coffeeshopmanagement.service.CustomerMergeService;
import com.coffeeshop.coffeeshopmanagement.util.TestSession;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.time.LocalDate;
import java.util.Random;

import static org.junit.Assert.*;

/** TODO 7, customer birthdays (rule D22): the date rules, saving the date, the audit line, and anonymising. */
public class BirthdayTest {

    private final CustomerDAO dao = new CustomerDAO();

    @BeforeClass
    public static void setUpDatabase() {
        TestSession.admin();
        TestDatabaseSupport.ensureReady();
    }

    @AfterClass
    public static void signOut() {
        TestSession.clear();
    }

    private Customer newCustomer(LocalDate birthday) {
        Customer c = new Customer();
        c.setFullName("test-birthday");
        c.setPhone("09" + (10_000_000 + new Random().nextInt(89_999_999)));
        c.setBirthday(birthday);
        return dao.insert(c);
    }

    @Test
    public void itIsTheBirthdayOnTheSameMonthAndDayWhateverTheYear() {
        LocalDate born = LocalDate.of(1990, 10, 30);
        assertTrue(BirthdayRules.isBirthday(born, LocalDate.of(2026, 10, 30)));
        assertFalse(BirthdayRules.isBirthday(born, LocalDate.of(2026, 10, 29)));
        assertFalse(BirthdayRules.isBirthday(null, LocalDate.of(2026, 10, 30)));
    }

    @Test
    public void aBirthdayOn29FebruaryIsCelebratedOn28FebruaryInAYearWithNo29th() {
        LocalDate born = LocalDate.of(1992, 2, 29);
        assertTrue(BirthdayRules.isBirthday(born, LocalDate.of(2027, 2, 28)));
        assertFalse(BirthdayRules.isBirthday(born, LocalDate.of(2027, 3, 1)));
        assertTrue(BirthdayRules.isBirthday(born, LocalDate.of(2028, 2, 29)));
        assertFalse(BirthdayRules.isBirthday(born, LocalDate.of(2028, 2, 28)));
    }

    @Test
    public void aFutureOrAbsurdlyOldDateIsRefusedAndNoDateIsFine() {
        LocalDate today = LocalDate.of(2026, 10, 30);
        assertNull(BirthdayRules.problem(null, today));
        assertNull(BirthdayRules.problem(LocalDate.of(1980, 1, 1), today));
        assertNotNull(BirthdayRules.problem(today.plusDays(1), today));
        assertNotNull(BirthdayRules.problem(LocalDate.of(1026, 1, 1), today));
    }

    @Test
    public void theBadgeShowsOnlyOnTheDayAndNeverForAnAnonymousCustomer() {
        Customer c = new Customer();
        c.setBirthday(LocalDate.of(2000, 5, 5));
        assertFalse(BirthdayRules.badge(c, LocalDate.of(2026, 5, 5)).isBlank());
        assertTrue(BirthdayRules.badge(c, LocalDate.of(2026, 5, 6)).isBlank());
        c.setAnonymisedAt(java.time.LocalDateTime.now());
        assertTrue(BirthdayRules.badge(c, LocalDate.of(2026, 5, 5)).isBlank());
        assertTrue(BirthdayRules.badge(null, LocalDate.of(2026, 5, 5)).isBlank());
    }

    @Test
    public void theDateIsSavedReadBackChangedAndRemoved() {
        Customer c = newCustomer(LocalDate.of(1995, 7, 7));
        assertEquals(LocalDate.of(1995, 7, 7), dao.findById(c.getId()).orElseThrow().getBirthday());
        c.setBirthday(LocalDate.of(1996, 8, 8));
        dao.update(c);
        assertEquals(LocalDate.of(1996, 8, 8), dao.findById(c.getId()).orElseThrow().getBirthday());
        c.setBirthday(null);
        dao.update(c);
        assertNull(dao.findById(c.getId()).orElseThrow().getBirthday());
    }

    @Test
    public void aChangeIsWrittenInTheAuditLogWithoutTheDate() {
        Customer c = newCustomer(null);
        c.setBirthday(LocalDate.of(1999, 9, 9));
        dao.update(c);
        var lines = new AuditDAO().find(new AuditDAO.Filter(null, null, com.coffeeshop.coffeeshopmanagement.model.AuditAction.BIRTHDAY_CHANGED, null, null), 200, 0);
        assertTrue(lines.stream().anyMatch(l -> ("#" + c.getId()).equals(l.targetLabel())));
        assertTrue(lines.stream().noneMatch(l -> String.valueOf(l.newValue()).contains("1999")));
    }

    @Test
    public void makingACustomerAnonymousRemovesTheDate() {
        Customer c = newCustomer(LocalDate.of(1988, 3, 3));
        new CustomerMergeService().anonymise(c.getId());
        Customer after = dao.findById(c.getId()).orElseThrow();
        assertTrue(after.isAnonymised());
        assertNull(after.getBirthday());
    }
}
