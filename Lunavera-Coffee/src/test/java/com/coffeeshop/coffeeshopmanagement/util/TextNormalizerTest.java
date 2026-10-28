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

    // ---- TODO 3.55: the same rule on SQLite and MariaDB ----

    @Test
    public void collationKeyIsMariaDbsRuleCaseAccentsAndTrailingSpacesDoNotCount() {
        assertEquals(TextNormalizer.collationKey("Trà"), TextNormalizer.collationKey("tra"));
        assertEquals(TextNormalizer.collationKey("Cà phê"), TextNormalizer.collationKey("CA PHE"));
        assertEquals("the accent written as letter + mark counts the same", TextNormalizer.collationKey("Cà phê"),
                TextNormalizer.collationKey("Ca\u0300 phe\u0302"));
        assertEquals("trailing spaces are ignored by the server's collation", TextNormalizer.collationKey("Cafe"),
                TextNormalizer.collationKey("cafe   "));
        assertEquals("", TextNormalizer.collationKey(null));
    }

    @Test
    public void collationKeyKeepsWhatTheServerKeepsApart() {
        assertNotEquals("d with a stroke is its own letter on MariaDB", TextNormalizer.collationKey("Đỗ"), TextNormalizer.collationKey("Do"));
        assertNotEquals("a space inside a name counts", TextNormalizer.collationKey("Cà  phê"), TextNormalizer.collationKey("Cà phê"));
        assertNotEquals("a space in front counts", TextNormalizer.collationKey(" cafe"), TextNormalizer.collationKey("cafe"));
        assertNotEquals(TextNormalizer.collationKey("Trà"), TextNormalizer.collationKey("Trà đá"));
    }

    @Test
    public void uniqueKeyIsTheAppsRuleAndALittleStricterThanTheServers() {
        assertEquals(TextNormalizer.uniqueKey("Trà"), TextNormalizer.uniqueKey("tra"));
        assertEquals("extra spaces never make another name (3.18)", TextNormalizer.uniqueKey("Cà  phê "), TextNormalizer.uniqueKey("ca phe"));
        assertNotEquals(TextNormalizer.uniqueKey("Đỗ"), TextNormalizer.uniqueKey("Do"));
        assertEquals("", TextNormalizer.uniqueKey(null));
    }

    @Test
    public void aCategoryThatDiffersOnlyInAccentsIsRefusedWithTheSamePlainMessageOnBothDatabases() {
        CategoryDAO dao = new CategoryDAO();
        String tag = " " + System.nanoTime();
        Category first = new Category();
        first.setName("Trà sữa" + tag);
        first.setActive(true);
        dao.insert(first);

        for (String clash : new String[] {"Tra sua" + tag, "TRA SỮA" + tag, "tra sua" + tag + "  "}) {
            Category other = new Category();
            other.setName(clash);
            other.setActive(true);
            DataAccessException refused = assertThrows("'" + clash + "' is the same name", DataAccessException.class, () -> dao.insert(other));
            assertEquals(I18n.t("category.exists"), refused.getMessage());
        }

        Category stroke = new Category(); // a different letter on both databases: allowed on both
        stroke.setName("Đá" + tag);
        stroke.setActive(true);
        dao.insert(stroke);
        Category plain = new Category();
        plain.setName("Da" + tag);
        plain.setActive(true);
        dao.insert(plain);

        // renaming a category so that it only differs from another one in accents is refused too
        Category renamed = new Category();
        renamed.setName("Cà phê" + tag);
        renamed.setActive(true);
        dao.insert(renamed);
        renamed.setName("TRA SUA" + tag);
        DataAccessException refused = assertThrows(DataAccessException.class, () -> dao.update(renamed));
        assertEquals(I18n.t("category.exists"), refused.getMessage());
    }
}

