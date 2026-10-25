package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.model.Customer;
import org.junit.Test;

import static org.junit.Assert.*;

/** TODO 2.70: the rule that decides when an attached customer is detached from the sale. No database, no screen. */
public class AttachedCustomerRuleTest {

    private static Customer customer(String name, String phone) {
        Customer c = new Customer();
        c.setFullName(name);
        c.setPhone(phone);
        return c;
    }

    @Test
    public void aCustomerWithAPhoneMatchesWhileBothBoxesStillSayTheSame() {
        Customer c = customer("Nguyễn Văn A", "0901234567");
        assertTrue(AttachedCustomerRule.stillMatches(c, "Nguyễn Văn A", "0901234567"));
        assertTrue("spaces around the boxes do not matter", AttachedCustomerRule.stillMatches(c, "  Nguyễn Văn A ", " 0901234567 "));
    }

    @Test
    public void thePhoneInAnotherSpellingStillMatches() {
        Customer c = customer("An", "0901234567");
        assertTrue(AttachedCustomerRule.stillMatches(c, "An", "0901 234 567"));
        assertTrue(AttachedCustomerRule.stillMatches(c, "An", "+84901234567"));
    }

    @Test
    public void aDifferentPhoneDetaches() {
        assertFalse(AttachedCustomerRule.stillMatches(customer("An", "0901234567"), "An", "0912345678"));
        assertFalse("a half-erased phone", AttachedCustomerRule.stillMatches(customer("An", "0901234567"), "An", "09012"));
        assertFalse("an erased phone", AttachedCustomerRule.stillMatches(customer("An", "0901234567"), "An", ""));
    }

    @Test
    public void aCustomerWithoutAPhoneIsDetachedAsSoonAsANumberIsTyped() {
        // The bug: such a customer stayed attached, the new number was never saved, and the sale was paid under the wrong person.
        assertFalse(AttachedCustomerRule.stillMatches(customer("Khách quen", null), "Khách quen", "0912345678"));
        assertFalse(AttachedCustomerRule.stillMatches(customer("Khách quen", ""), "Khách quen", "0912345678"));
        assertFalse(AttachedCustomerRule.stillMatches(customer("Khách quen", "   "), "Khách quen", "0"));
    }

    @Test
    public void aCustomerWithoutAPhoneMatchesAnEmptyPhoneBox() {
        assertTrue(AttachedCustomerRule.stillMatches(customer("Khách quen", null), "Khách quen", ""));
        assertTrue(AttachedCustomerRule.stillMatches(customer("Khách quen", null), "Khách quen", null));
        assertTrue(AttachedCustomerRule.stillMatches(customer("Khách quen", ""), "Khách quen", "  "));
    }

    @Test
    public void editingTheNameDetachesToo() {
        assertFalse(AttachedCustomerRule.stillMatches(customer("Nguyễn Văn A", "0901234567"), "Trần Thị B", "0901234567"));
        assertFalse(AttachedCustomerRule.stillMatches(customer("Khách quen", null), "Khách khác", ""));
        assertFalse("an emptied name", AttachedCustomerRule.stillMatches(customer("An", "0901234567"), "", "0901234567"));
    }

    @Test
    public void nobodyAttachedNeverMatches() {
        assertFalse(AttachedCustomerRule.stillMatches(null, "An", "0901234567"));
    }
}
