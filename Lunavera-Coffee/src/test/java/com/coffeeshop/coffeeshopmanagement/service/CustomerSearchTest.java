package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.model.Customer;
import org.junit.Test;

import java.time.LocalDateTime;

import static org.junit.Assert.*;

/** TODO 5.35: the customer search looks at the name, the phone and the e-mail. */
public class CustomerSearchTest {

    private static Customer customer(String name, String phone, String email) {
        return new Customer(1, name, phone, email, 0, LocalDateTime.now());
    }

    @Test
    public void findsByNameWithoutCaseOrAccents() {
        Customer c = customer("Nguy\u1ec5n V\u0103n An", "0901111222", null);
        assertTrue(CustomerSearch.matches(c, "nguyen van"));
        assertTrue(CustomerSearch.matches(c, "AN"));
        assertFalse(CustomerSearch.matches(c, "tran"));
    }

    @Test
    public void findsByEmail() {
        Customer c = customer("Binh", "0902222333", "Binh.Le@Example.com");
        assertTrue(CustomerSearch.matches(c, "binh.le@"));
        assertTrue(CustomerSearch.matches(c, "example.com"));
        assertFalse(CustomerSearch.matches(c, "gmail"));
    }

    @Test
    public void findsByPhoneAsTypedAndInCanonicalForm() {
        Customer c = customer("Chi", "0901111222", null);
        assertTrue(CustomerSearch.matches(c, "0901"));
        assertTrue(CustomerSearch.matches(c, "+84 901 111 222"));
        assertFalse(CustomerSearch.matches(c, "0999"));
    }

    @Test
    public void aBlankSearchMatchesEveryoneAndANullCustomerNobody() {
        assertTrue(CustomerSearch.matches(customer("A", null, null), "  "));
        assertTrue(CustomerSearch.matches(customer("A", null, null), null));
        assertFalse(CustomerSearch.matches(null, "a"));
    }

    @Test
    public void aCustomerWithoutPhoneOrEmailDoesNotBreakTheSearch() {
        assertFalse(CustomerSearch.matches(customer("Dung", null, null), "0901"));
    }
}
