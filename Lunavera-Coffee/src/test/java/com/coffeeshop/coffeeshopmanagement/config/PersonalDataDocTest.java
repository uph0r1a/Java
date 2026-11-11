package com.coffeeshop.coffeeshopmanagement.config;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.Assert.assertTrue;

/**
 * TODO 2A.4: {@code docs/PERSONAL_DATA.md} part 7 is the list an adviser reads of every column that holds a person's data. This keeps it
 * true. The shop database is read from its table definitions (the MariaDB one is the full one), so a column added later that looks
 * personal, and is not in the document, fails here until someone writes down what it holds. It also checks the one claim a reader would
 * rely on: a column that the document says is scrubbed when a person is made anonymous must be rewritten by an UPDATE in the code.
 * Source reading only: no database.
 */
public class PersonalDataDocTest {

    private static final Path MAIN = Paths.get("src/main/java/com/coffeeshop/coffeeshopmanagement");

    /** Column names that can hold a person's data. Columns ending in _id are links, not data, and are not listed. */
    private static final Pattern PERSONAL = Pattern.compile("(?i)full_name|phone|email|address|salary|birthday|username|name_key|_by_name|employee_name|user_name|"
            + "actor_name|customer_name|supplier|^note$|_note$|reason|detail|label|sold_by|created_by|paid_by|cancelled_by|refunded_by|recorded_by|"
            + "closed_by|complimentary_for|transfer_ref|old_value|new_value|password|pin_|hire_date|position");

    private static String read(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    /** table to columns, read from the MariaDB table definitions (each starts at "CREATE TABLE IF NOT EXISTS name" and ends at ENGINE). */
    private static Map<String, List<String>> schema() throws IOException {
        String source = read(MAIN.resolve("config/MariaDbSchema.java"));
        Map<String, List<String>> tables = new LinkedHashMap<>();
        String[] parts = source.split("CREATE TABLE IF NOT EXISTS ");
        Pattern column = Pattern.compile("(?m)^\\s*\"?\\s*([a-z_]+)\\s+(?:INT|BIGINT|TINYINT|SMALLINT|VARCHAR|CHAR|TEXT|DECIMAL|DATETIME|TIMESTAMP|DATE|TIME|BOOLEAN|LONGTEXT|MEDIUMTEXT)");
        for (int i = 1; i < parts.length; i++) {
            Matcher name = Pattern.compile("^(\\w+)").matcher(parts[i]);
            if (!name.find()) continue;
            String body = parts[i].contains("ENGINE") ? parts[i].substring(0, parts[i].indexOf("ENGINE")) : parts[i].substring(0, Math.min(3000, parts[i].length()));
            List<String> columns = new ArrayList<>();
            Matcher c = column.matcher(body);
            while (c.find()) columns.add(c.group(1));
            tables.put(name.group(1), columns);
        }
        return tables;
    }

    /** The rows of part 7: "table.column" to what the last column says. */
    private static Map<String, String> documented() throws IOException {
        String doc = read(Paths.get("docs/PERSONAL_DATA.md"));
        int part7 = doc.indexOf("## 7. Column by column");
        assertTrue("PERSONAL_DATA.md has part 7", part7 > 0);
        Map<String, String> rows = new LinkedHashMap<>();
        Matcher m = Pattern.compile("(?m)^\\| `([a-z_]+\\.[a-z_]+)` \\|.*\\| ([^|]+) \\|\\s*$").matcher(doc.substring(part7));
        while (m.find()) rows.put(m.group(1), m.group(2).trim());
        return rows;
    }

    @Test
    public void everyColumnThatLooksPersonalIsInTheDocument() throws IOException {
        Map<String, String> doc = documented();
        List<String> missing = new ArrayList<>();
        for (Map.Entry<String, List<String>> table : schema().entrySet()) {
            for (String column : table.getValue()) {
                if (column.endsWith("_id") || !PERSONAL.matcher(column).find()) continue;
                if (!doc.containsKey(table.getKey() + "." + column)) missing.add(table.getKey() + "." + column);
            }
        }
        assertTrue("these columns look like they hold a person's data and are not in part 7 of docs/PERSONAL_DATA.md (add a row saying whom "
                + "they are about and what happens when that person is made anonymous): " + missing, missing.isEmpty());
    }

    @Test
    public void everyColumnInTheDocumentExists() throws IOException {
        Map<String, List<String>> schema = schema();
        List<String> gone = new ArrayList<>();
        for (String key : documented().keySet()) {
            String[] parts = key.split("\\.");
            if (!schema.containsKey(parts[0]) || !schema.get(parts[0]).contains(parts[1])) gone.add(key);
        }
        assertTrue("part 7 names columns the database does not have: " + gone, gone.isEmpty());
        assertTrue("the schema was read", schema.size() > 20 && schema.containsKey("customers"));
    }

    @Test
    public void aColumnSaidToBeScrubbedHasAnUpdateThatRewritesItWhenAPersonIsMadeAnonymous() throws IOException {
        StringBuilder code = new StringBuilder();
        try (Stream<Path> files = Files.walk(MAIN)) {
            for (Path file : (Iterable<Path>) files.filter(f -> f.toString().endsWith(".java"))::iterator) code.append(read(file)).append('\n');
        }
        String all = code.toString();
        List<String> unproved = new ArrayList<>();
        for (Map.Entry<String, String> row : documented().entrySet()) {
            if (!row.getValue().startsWith("scrubbed")) continue;
            String[] parts = row.getKey().split("\\.");
            Matcher generated = Pattern.compile("generated from `([a-z_]+\\.[a-z_]+)`").matcher(row.getValue());
            if (generated.find()) { // the database fills it from another column: that column must be scrubbed and this one declared as generated
                String from = generated.group(1);
                String schemaSource = read(MAIN.resolve("config/MariaDbSchema.java"));
                boolean declared = Pattern.compile(parts[1] + "[^\\n]*\\bAS \\(").matcher(schemaSource).find();
                boolean sourceScrubbed = documented().getOrDefault(from, "").startsWith("scrubbed")
                        && Pattern.compile("UPDATE " + from.split("\\.")[0] + " SET [^;]{0,400}?\\b" + from.split("\\.")[1] + "\\b").matcher(all).find();
                if (!declared || !sourceScrubbed) unproved.add(row.getKey());
                continue;
            }
            // an UPDATE of that table whose SET part mentions the column, in one statement (a Java string, so up to the closing quote or +)
            Pattern update = Pattern.compile("UPDATE " + parts[0] + " SET [^;]{0," + 400 + "}?\\b" + parts[1] + "\\b");
            if (!update.matcher(all).find()) unproved.add(row.getKey());
        }
        assertTrue("part 7 says these columns are scrubbed when a person is made anonymous, but no UPDATE of them was found: " + unproved
                + " (write the UPDATE, or change the word in part 7 to kept and say why)", unproved.isEmpty());
    }

    @Test
    public void everyRowSaysWhatHappensAndPartThreeStillNamesTheBackupGap() throws IOException {
        for (Map.Entry<String, String> row : documented().entrySet()) {
            assertTrue(row.getKey() + " says scrubbed or kept", row.getValue().startsWith("scrubbed") || row.getValue().startsWith("kept"));
        }
        String doc = read(Paths.get("docs/PERSONAL_DATA.md"));
        assertTrue("backups made earlier keep the data as it was", doc.contains("keep the\n   data as it was") || doc.contains("keep the data as it was"));
        assertTrue(new TreeSet<>(documented().keySet()).size() > 40);
    }
}
