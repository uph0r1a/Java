package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * TODO 3.18: the one-time pass rewrites old text saved with separate accent marks, leaves a name that would repeat an existing one,
 * and changes nothing else. The pass is package-private in {@code config}, so it is reached by reflection; the flag that makes it
 * run once is not used (the shared test database has already run it).
 */
public class TextNormalizationDbTest {

    private static final String SEPARATE = "Tra\u0300 su\u031b\u0303a";
    private static final String COMPOSED = "Tr\u00e0 s\u1eefa";

    @BeforeClass
    public static void ready() {
        TestDatabaseSupport.ensureReady();
    }

    private static Object run(Connection connection) throws Exception {
        Class<?> type = Class.forName("com.coffeeshop.coffeeshopmanagement.config.TextNormalization");
        Method run = type.getDeclaredMethod("run", Connection.class);
        run.setAccessible(true);
        return run.invoke(null, connection);
    }

    private static String one(Connection c, String sql, String param) throws Exception {
        try (PreparedStatement st = c.prepareStatement(sql)) {
            st.setString(1, param);
            try (ResultSet rs = st.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    @Test
    public void oldSeparateAccentsBecomeComposedAndNothingElseChanges() throws Exception {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        try (Connection c = DatabaseConfig.getConnection()) {
            try (PreparedStatement st = c.prepareStatement("INSERT INTO categories (name, description) VALUES (?, ?)")) {
                st.setString(1, "Nhom " + tag + " " + SEPARATE);
                st.setString(2, "  mo ta  " + SEPARATE);
                st.executeUpdate();
            }
            run(c);
            assertEquals("Nhom " + tag + " " + COMPOSED, one(c, "SELECT name FROM categories WHERE name LIKE ?", "Nhom " + tag + "%"));
            assertEquals("spaces are not touched, only the accents", "  mo ta  " + COMPOSED,
                    one(c, "SELECT description FROM categories WHERE name LIKE ?", "Nhom " + tag + "%"));
        }
    }

    @Test
    public void aCategoryThatWouldRepeatAnExistingNameIsLeftAsItWas() throws Exception {
        // MariaDB's collation treats the composed and the separate spelling as the same name, so its unique rule refuses the set-up itself
        org.junit.Assume.assumeTrue("SQLite-only: on MariaDB the two spellings are one name and cannot both be inserted",
                DatabaseConfig.isSqlite());
        String tag = UUID.randomUUID().toString().substring(0, 8);
        String composed = "Nhom " + tag + " " + COMPOSED;
        String separate = "Nhom " + tag + " " + SEPARATE;
        try (Connection c = DatabaseConfig.getConnection()) {
            for (String name : new String[]{composed, separate}) {
                try (PreparedStatement st = c.prepareStatement("INSERT INTO categories (name) VALUES (?)")) {
                    st.setString(1, name);
                    st.executeUpdate();
                }
            }
            Object result = run(c); // must not fail on the unique rule
            assertEquals(separate, one(c, "SELECT name FROM categories WHERE name = ?", separate));
            assertTrue(result.toString().contains("categories.name#"));
        }
    }
}
