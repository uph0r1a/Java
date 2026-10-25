package com.coffeeshop.coffeeshopmanagement.util;

import com.coffeeshop.coffeeshopmanagement.dao.CategoryDAO;
import com.coffeeshop.coffeeshopmanagement.dao.DataAccessException;
import com.coffeeshop.coffeeshopmanagement.dao.TestDatabaseSupport;
import com.coffeeshop.coffeeshopmanagement.model.Category;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

/** TODO 3.18: the same Vietnamese word typed two ways, or in two letter cases, is one name. */
public class TextNormalizerTest {

    private static final String COMPOSED = "Trà";          // "Trà" with one character for à
    private static final String SEPARATE = "Trà";         // "Trà" as a + separate accent mark

    @BeforeClass
    public static void setUpDatabase() {
        TestDatabaseSupport.ensureReady();
        TestSession.admin();
    }

    @AfterClass
    public static void signOut() {
        TestSession.clear();
    }

    @Test
    public void bothSpellingsBecomeTheSameText() {
        assertNotEquals("the raw strings differ", COMPOSED, SEPARATE);
        assertEquals(COMPOSED, TextNormalizer.nfc(SEPARATE));
        assertEquals(TextNormalizer.key(COMPOSED), TextNormalizer.key(SEPARATE));
    }

    @Test
    public void keyIgnoresCaseAndExtraSpacesIncludingVietnameseLetters() {
        assertEquals(TextNormalizer.key("Đào  Cam"), TextNormalizer.key(" đào cam "));
        assertNull(TextNormalizer.nfc(null));
        assertEquals("", TextNormalizer.key(null));
    }

    @Test
    public void aCategoryNamedTheSameWayIsRefused() {
        CategoryDAO dao = new CategoryDAO();
        String unique = "Đồ uống " + System.nanoTime();
        Category first = new Category();
        first.setName(unique);
        first.setActive(true);
        dao.insert(first);

        Category differentCase = new Category();
        differentCase.setName(unique.toLowerCase());
        differentCase.setActive(true);
        try {
            dao.insert(differentCase);
            fail("only the letter case differs");
        } catch (DataAccessException expected) {
            // good
        }

        Category otherEncoding = new Category();
        otherEncoding.setName(java.text.Normalizer.normalize(unique, java.text.Normalizer.Form.NFD));
        otherEncoding.setActive(true);
        try {
            dao.insert(otherEncoding);
            fail("only the accent encoding differs");
        } catch (DataAccessException expected) {
            // good
        }
        // renaming a category to its own name is fine
        dao.update(first);
    }
}
