package com.coffeeshop.coffeeshopmanagement.config;

import com.coffeeshop.coffeeshopmanagement.dao.TestDatabaseSupport;
import com.coffeeshop.coffeeshopmanagement.util.TextNormalizer;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * MariaDB only (the {@code test-mariadb} CI job), TODO 3.55: {@link TextNormalizer#collationKey} is a copy, written in Java, of what
 * the server's {@code utf8mb4_unicode_ci} decides about {@code categories.name}. This test keeps the copy honest: for every pair of
 * names below, the server refuses the second one exactly when the key says the two are equal. If a server version ever changes its
 * collation, this is the test that says so, before the SQLite import or the app's own duplicate check disagrees with the server.
 */
public class MariaDbCategoryNameRuleTest {

    private static final String PREFIX = "cn355-";

    @BeforeClass
    public static void setUpDatabase() {
        TestDatabaseSupport.ensureReady();
        Assume.assumeFalse("MariaDB-only test (this run uses SQLite)", DatabaseConfig.isSqlite());
    }

    private static List<String> words() {
        List<String> words = new ArrayList<>(List.of("Cà phê", "Ca phe", "CÀ PHÊ", "Cà  phê", "Cà phê ", " Cà phê", "Trà", "Tra", "TRÀ",
                "Đá", "Da", "đá", "Đỗ", "Do", "Bánh mì", "Banh mi", "Sữa", "Sua", "Ơ", "O", "Ư", "U", "Ă", "A", "Ê", "E"));
        for (String word : new ArrayList<>(words)) {
            words.add(Normalizer.normalize(word, Normalizer.Form.NFD)); // the same word with separate accent marks
        }
        return words;
    }

    /** True when the server accepted {@code name}; the row is left in the table for the caller to delete. */
    private static boolean accepted(Connection c, String name) throws SQLException {
        try (PreparedStatement st = c.prepareStatement("INSERT INTO categories(name, active) VALUES (?, 1)")) {
            st.setString(1, PREFIX + name);
            st.executeUpdate();
            return true;
        } catch (SQLException duplicate) {
            if (duplicate.getErrorCode() == 1062) return false;
            throw duplicate;
        }
    }

    @Test
    public void theServerRefusesTwoNamesExactlyWhenTheJavaKeyCallsThemEqual() throws Exception {
        List<String> words = words();
        try (Connection c = DatabaseConfig.getConnection(); Statement clean = c.createStatement()) {
            try {
                for (String first : words) {
                    for (String second : words) {
                        clean.executeUpdate("DELETE FROM categories WHERE name LIKE '" + PREFIX + "%'");
                        accepted(c, first);
                        boolean serverAcceptsSecond = accepted(c, second);
                        // the prefix is part of what the server compares, so compare keys of the same prefixed text
                        boolean keysEqual = TextNormalizer.collationKey(PREFIX + first).equals(TextNormalizer.collationKey(PREFIX + second));
                        assertEquals("'" + first + "' then '" + second + "'", !keysEqual, serverAcceptsSecond);
                    }
                }
            } finally {
                clean.executeUpdate("DELETE FROM categories WHERE name LIKE '" + PREFIX + "%'");
            }
        }
    }
}
