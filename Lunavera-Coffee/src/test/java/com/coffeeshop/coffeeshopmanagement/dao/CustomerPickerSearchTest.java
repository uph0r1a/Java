package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.model.Customer;
import com.coffeeshop.coffeeshopmanagement.service.CustomerService;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * TODO 3.52: the POS "attach a customer" box asks the database for at most 20 matches instead of loading every customer. Every
 * test makes its own customers under a marker nobody else uses, because the test database is shared by the whole run.
 */
public class CustomerPickerSearchTest {

    private final CustomerDAO customerDAO = new CustomerDAO();
    private final CustomerService service = new CustomerService(customerDAO);

    @BeforeClass
    public static void setUpDatabase() {
        TestDatabaseSupport.ensureReady();
    }

    private static String marker() {
        return "q" + UUID.randomUUID().toString().replace("-", "");
    }

    private static String uniquePhone() {
        String digits = UUID.randomUUID().toString().replaceAll("\\D", "");
        return "0" + (digits + "000000000").substring(0, 9);
    }

    private Customer add(String name, String phone, String email) {
        Customer customer = new Customer();
        customer.setFullName(name);
        customer.setPhone(phone);
        customer.setEmail(email);
        return customerDAO.insert(customer);
    }

    private static boolean contains(List<Customer> list, Customer wanted) {
        return list.stream().anyMatch(c -> c.getId() == wanted.getId());
    }

    @Test
    public void aNameIsFoundWithoutAccentsAndWithoutCapitals() {
        String marker = marker();
        Customer lan = add("Nguyễn Văn " + marker, null, null);
        assertTrue(contains(customerDAO.search("nguyen van " + marker, 20), lan));
        assertTrue(contains(customerDAO.search("NGUYỄN " + marker.toUpperCase(java.util.Locale.ROOT), 20), lan));
    }

    @Test
    public void aPhoneIsFoundAsTypedOrInItsInternationalForm() {
        String phone = uniquePhone();
        Customer customer = add("Phone " + marker(), phone, null);
        assertTrue(contains(customerDAO.search(phone, 20), customer));
        assertTrue(contains(customerDAO.search(phone.substring(3), 20), customer));            // the middle and end of the number
        assertTrue(contains(customerDAO.search("+84 " + phone.substring(1), 20), customer));   // as a tourist or a form would write it
    }

    @Test
    public void anEmailIsFound() {
        String marker = marker();
        Customer customer = add("Mail " + marker, null, marker + "@example.com");
        assertTrue(contains(customerDAO.search(marker + "@exa", 20), customer));
    }

    @Test
    public void aTypedPercentOrUnderscoreMatchesItselfAndNotEverybody() {
        String marker = marker();
        add("Plain " + marker + "x", null, null);
        assertTrue("a % typed by the person is not a wildcard", customerDAO.search(marker + "%", 20).isEmpty());
        assertTrue("an _ typed by the person is not a wildcard", customerDAO.search(marker + "_", 20).isEmpty());
    }

    @Test
    public void nothingMatchesAnEmptyList() {
        assertTrue(customerDAO.search("zzz" + marker(), 20).isEmpty());
    }

    @Test
    public void theLimitIsRespectedAndTheServiceSaysThereAreMore() {
        String marker = marker();
        for (int i = 0; i < CustomerService.PICKER_LIMIT + 5; i++) {
            add(marker + " customer " + i, null, null);
        }
        assertEquals(7, customerDAO.search(marker, 7).size());
        CustomerService.Matches matches = service.search(marker);
        assertEquals(CustomerService.PICKER_LIMIT, matches.shown().size());
        assertTrue(matches.more());
    }

    @Test
    public void whenEveryMatchFitsTheServiceSaysThereAreNoMore() {
        String marker = marker();
        for (int i = 0; i < 3; i++) {
            add(marker + " few " + i, null, null);
        }
        CustomerService.Matches matches = service.search(marker);
        assertEquals(3, matches.shown().size());
        assertFalse(matches.more());
    }

    @Test
    public void exactlyTheLimitIsNotMoreThanTheLimit() {
        String marker = marker();
        for (int i = 0; i < CustomerService.PICKER_LIMIT; i++) {
            add(marker + " edge " + i, null, null);
        }
        CustomerService.Matches matches = service.search(marker);
        assertEquals(CustomerService.PICKER_LIMIT, matches.shown().size());
        assertFalse(matches.more());
    }

    @Test
    public void aBlankSearchGivesTheFirstCustomersByNameAndNeverMoreThanTheLimit() {
        add("Blank " + marker(), null, null);
        CustomerService.Matches matches = service.search("   ");
        assertFalse(matches.shown().isEmpty());
        assertTrue(matches.shown().size() <= CustomerService.PICKER_LIMIT);
        List<Customer> sorted = new java.util.ArrayList<>(matches.shown());
        // by name, ties by id (the database decides what "by name" means, so only the ids of equal names are compared here)
        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i - 1).getFullName().equals(sorted.get(i).getFullName())) {
                assertTrue(sorted.get(i - 1).getId() < sorted.get(i).getId());
            }
        }
    }
}
