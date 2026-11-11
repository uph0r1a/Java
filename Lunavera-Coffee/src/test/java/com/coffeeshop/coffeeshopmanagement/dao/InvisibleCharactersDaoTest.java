package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.model.Category;
import com.coffeeshop.coffeeshopmanagement.model.Customer;
import com.coffeeshop.coffeeshopmanagement.util.TestSession;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.*;

/** TODO 3.71 in the database: names saved from pasted text are kept clean, so duplicates are seen and searches find them. */
public class InvisibleCharactersDaoTest {

    @BeforeClass
    public static void setUpDatabase() {
        TestSession.admin();
        TestDatabaseSupport.ensureReady();
    }

    private static String marker() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    @Test
    public void aCustomerNamedWithOddSpacesIsStoredCleanAndFoundByPlainLetters() {
        String m = marker();
        Customer c = new Customer();
        c.setFullName("Nguyễn\u00A0Văn\u200B Ân\u202F" + m);
        new CustomerDAO().insert(c);
        Customer stored = new CustomerDAO().findById(c.getId()).orElseThrow();
        assertEquals("one normal space, nothing invisible", "Nguyễn Văn Ân " + m, stored.getFullName());
        assertTrue(new CustomerDAO().search("nguyen van an " + m, 20).stream().anyMatch(x -> x.getId() == c.getId()));
    }

    @Test
    public void aCategoryWhoseNameDiffersOnlyByAnOddSpaceIsADuplicate() {
        String m = marker();
        CategoryDAO dao = new CategoryDAO();
        Category first = new Category();
        first.setName("Trà " + m + " đào");
        first.setActive(true);
        dao.insert(first);
        Category second = new Category();
        second.setName("Trà\u00A0" + m + "\u200B\u202Fđào");
        second.setActive(true);
        assertThrows(DataAccessException.class, () -> dao.insert(second));
    }

    @Test
    public void aCustomerPhoneAndEmailPastedWithInvisibleCharactersAreStoredClean() {
        String m = marker();
        Customer c = new Customer();
        c.setFullName("Khách " + m);
        c.setPhone("+84\u00A0(0)\u200E 9" + String.valueOf(System.nanoTime()).substring(0, 8));
        c.setEmail("\u200Bkhach" + m + "@example.com\u200E");
        new CustomerDAO().insert(c);
        Customer stored = new CustomerDAO().findById(c.getId()).orElseThrow();
        assertTrue(stored.getPhone(), stored.getPhone().matches("0\\d{9}"));
        assertEquals("khach" + m + "@example.com", stored.getEmail());
    }
}
