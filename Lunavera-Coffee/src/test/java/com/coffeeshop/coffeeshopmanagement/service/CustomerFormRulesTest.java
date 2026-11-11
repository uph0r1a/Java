package com.coffeeshop.coffeeshopmanagement.service;

import org.junit.Test;

import static com.coffeeshop.coffeeshopmanagement.service.CustomerFormRules.Problem;
import static org.junit.Assert.*;

/** TODO 6.21: the customer form checks that need no database. */
public class CustomerFormRulesTest {

    @Test
    public void aBlankNameIsTheFirstProblem() {
        assertEquals(Problem.NAME, CustomerFormRules.fieldProblem("  ", "not a phone", "bad"));
        assertEquals(Problem.NAME, CustomerFormRules.fieldProblem(null, "", ""));
    }

    @Test
    public void thePhoneIsCheckedBeforeTheEmail() {
        assertEquals(Problem.PHONE, CustomerFormRules.fieldProblem("An", "12", "bad"));
        assertEquals(Problem.EMAIL, CustomerFormRules.fieldProblem("An", "0901234567", "bad"));
    }

    @Test
    public void emptyPhoneAndEmailAreAllowed() {
        assertNull(CustomerFormRules.fieldProblem("An", "", null));
        assertNull(CustomerFormRules.fieldProblem("An", "0901 234 567", "an@example.com"));
    }

    @Test
    public void everyProblemHasATitleAndExceptTheBirthdayAMessage() {
        for (Problem problem : Problem.values()) {
            assertNotNull(problem.name(), problem.titleKey());
            if (problem != Problem.BIRTHDAY) assertNotNull(problem.name(), problem.messageKey());
        }
        assertNull("the birthday text comes from BirthdayRules", Problem.BIRTHDAY.messageKey());
    }

    @Test
    public void aPhoneIsChangedOnlyForAnExistingCustomerAndOnlyWhenTheNumberDiffers() {
        assertFalse("a new customer has nothing to change", CustomerFormRules.phoneChanged(false, "0901234567", null));
        assertFalse("same number typed with spaces", CustomerFormRules.phoneChanged(true, "0901 234 567", "0901234567"));
        assertTrue(CustomerFormRules.phoneChanged(true, "0912345678", "0901234567"));
        assertTrue("clearing the phone is a change", CustomerFormRules.phoneChanged(true, "", "0901234567"));
        assertFalse("no phone before and none now", CustomerFormRules.phoneChanged(true, "  ", null));
    }
}
