package com.coffeeshop.coffeeshopmanagement.dao;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.Assert.*;

/**
 * TODO 3.84: a list of names that the database sorts goes through {@code SqlDialect.vietnameseOrder}, never a plain
 * {@code ORDER BY name}: SQLite compares by code point and would put {@code Ân} and {@code Đặng} after {@code Zung}. Reads the DAO and
 * service sources as text (no database), so a new name list that forgets it fails here on any machine.
 */
public class VietnameseOrderSourceTest {

    private static final Path MAIN = Path.of("src", "main", "java", "com", "coffeeshop", "coffeeshopmanagement");

    /** An ORDER BY whose first key is a name column, written plainly (a name column is: full_name, name, username, or the alias shown). */
    private static final Pattern PLAIN_NAME_ORDER = Pattern.compile("ORDER BY\\s+(?:[a-z]\\w*\\.)?(full_name|name|username|shown)\\b");

    private static String read(String file) throws IOException {
        return Files.readString(MAIN.resolve(file), StandardCharsets.UTF_8);
    }

    private static int count(String text, String part) {
        int n = 0;
        for (int i = text.indexOf(part); i >= 0; i = text.indexOf(part, i + part.length())) n++;
        return n;
    }

    @Test
    public void noDaoOrServiceSortsANameColumnPlainly() throws IOException {
        List<String> problems = new ArrayList<>();
        for (String dir : new String[]{"dao", "service"}) {
            try (Stream<Path> files = Files.list(MAIN.resolve(dir))) {
                for (Path file : files.filter(p -> p.toString().endsWith(".java")).sorted().toList()) {
                    Matcher m = PLAIN_NAME_ORDER.matcher(Files.readString(file, StandardCharsets.UTF_8));
                    while (m.find()) problems.add(dir + "/" + file.getFileName() + ": " + m.group());
                }
            }
        }
        assertTrue("These sort names plainly; use DatabaseConfig.dialect().vietnameseOrder(column) (TODO 3.84):\n  "
                + String.join("\n  ", problems), problems.isEmpty());
    }

    @Test
    public void theListsOfNamesUseTheVietnameseOrder() throws IOException {
        assertEquals(1, count(read("dao/CustomerDAO.java"), "vietnameseOrder(\"full_name\")"));
        assertEquals("all ingredients, the low ones, the ingredients of a recipe", 3, count(read("dao/IngredientDAO.java"), "vietnameseOrder("));
        assertEquals("the low-stock products", 1, count(read("dao/ProductDAO.java"), "vietnameseOrder(\"p.name\")"));
        assertEquals("the staff list of the time sheet", 1, count(read("dao/TimeClockDAO.java"), "vietnameseOrder("));
        assertEquals("the staff who have a PIN", 1, count(read("dao/UserDAO.java"), "vietnameseOrder(\"shown\")"));
        String orders = read("dao/OrderDAO.java");
        assertEquals("the sortable name columns of the order history", 1, count(orders, "vietnameseOrder(column)"));
        assertEquals("the history no longer uses the ASCII-only case folding", 0, count(orders, "caseInsensitive("));
    }

    @Test
    public void everySqliteConnectionIsGivenTheCollationTheDialectNames() throws IOException {
        String config = read("config/DatabaseConfig.java");
        assertTrue(config.contains("VIETNAMESE_COLLATION = \"VN_ORDER\""));
        assertTrue("registered where the connection is made, next to the search function",
                config.indexOf("registerVietnameseCollation(connection);") > config.indexOf("registerSearchKeyFunction(connection);"));
        assertTrue(read("config/SqlDialect.java").contains("DatabaseConfig.VIETNAMESE_COLLATION"));
    }
}
