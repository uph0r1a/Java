package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.model.Employee;
import org.junit.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.Assert.*;

/** TODO 4.12: the rules for a staff record typed on the staff screen. No database, no JavaFX. */
public class StaffRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 27);

    private static Employee valid() {
        Employee e = new Employee();
        e.setFullName("Nguyễn Văn An");
        e.setPhone("0901 234 567");
        e.setEmail("an@example.com");
        e.setPosition("Pha chế");
        e.setAddress("12 Nguyễn Huệ, Quận 1");
        e.setSalary(new BigDecimal("8000000"));
        e.setHireDate(LocalDate.of(2026, 3, 1));
        return e;
    }

    @Test
    public void aCompleteRecordPasses() {
        assertNull(StaffRules.problem(valid(), TODAY));
    }

    @Test
    public void onlyANameIsRequired() {
        Employee e = new Employee();
        e.setFullName("An");
        assertNull(StaffRules.problem(e, TODAY));
    }

    @Test
    public void aBlankOrMissingNameIsRefused() {
        Employee e = valid();
        e.setFullName("   ");
        assertNotNull(StaffRules.problem(e, TODAY));
        e.setFullName(null);
        assertNotNull(StaffRules.problem(e, TODAY));
        assertNotNull(StaffRules.problem(null, TODAY));
    }

    @Test
    public void aTooLongNameIsRefused() {
        Employee e = valid();
        e.setFullName("a".repeat(101));
        assertNotNull(StaffRules.problem(e, TODAY));
        e.setFullName("a".repeat(100));
        assertNull(StaffRules.problem(e, TODAY));
    }

    @Test
    public void aPhoneMustLookLikeAPhoneButMayBeLeftOut() {
        Employee e = valid();
        e.setPhone("12345");
        assertNotNull(StaffRules.problem(e, TODAY));
        e.setPhone("+84 901 234 567");
        assertNull(StaffRules.problem(e, TODAY));
        e.setPhone("  ");
        assertNull(StaffRules.problem(e, TODAY));
        e.setPhone(null);
        assertNull(StaffRules.problem(e, TODAY));
    }

    @Test
    public void anEmailMustLookLikeAnEmailButMayBeLeftOut() {
        Employee e = valid();
        e.setEmail("not an email");
        assertNotNull(StaffRules.problem(e, TODAY));
        e.setEmail("");
        assertNull(StaffRules.problem(e, TODAY));
        e.setEmail(null);
        assertNull(StaffRules.problem(e, TODAY));
    }

    @Test
    public void positionAndAddressHaveALimit() {
        Employee e = valid();
        e.setPosition("p".repeat(61));
        assertNotNull(StaffRules.problem(e, TODAY));
        e.setPosition("p".repeat(60));
        assertNull(StaffRules.problem(e, TODAY));
        e.setAddress("a".repeat(201));
        assertNotNull(StaffRules.problem(e, TODAY));
        e.setAddress("a".repeat(200));
        assertNull(StaffRules.problem(e, TODAY));
    }

    @Test
    public void aNegativeSalaryIsRefusedAndZeroOrNoneIsFine() {
        Employee e = valid();
        e.setSalary(new BigDecimal("-1"));
        assertNotNull(StaffRules.problem(e, TODAY));
        e.setSalary(BigDecimal.ZERO);
        assertNull(StaffRules.problem(e, TODAY));
        e.setSalary(null);
        assertNull(StaffRules.problem(e, TODAY));
    }

    @Test
    public void aStartDateInTheFutureIsRefusedButTodayIsFine() {
        Employee e = valid();
        e.setHireDate(TODAY.plusDays(1));
        assertNotNull(StaffRules.problem(e, TODAY));
        e.setHireDate(TODAY);
        assertNull(StaffRules.problem(e, TODAY));
        e.setHireDate(null);
        assertNull(StaffRules.problem(e, TODAY));
    }

    @Test
    public void cleanTrimsAndTurnsBlankIntoNull() {
        assertNull(StaffRules.clean(null));
        assertNull(StaffRules.clean("   "));
        assertEquals("Pha chế", StaffRules.clean("  Pha chế "));
    }
}
