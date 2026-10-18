package com.coffeeshop.coffeeshopmanagement.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads table and column names out of CREATE TABLE text, so tests can compare the SQLite and MariaDB definitions. */
final class SchemaColumns {

    private static final Pattern CREATE = Pattern.compile("CREATE TABLE IF NOT EXISTS (\\w+) \\(");
    private static final Pattern REFERENCES = Pattern.compile("REFERENCES (\\w+)\\(");
    private static final List<String> NOT_A_COLUMN = List.of("FOREIGN", "UNIQUE", "INDEX", "PRIMARY", "KEY", "CONSTRAINT", ")");

    private SchemaColumns() {
    }

    static String tableName(String createStatement) {
        Matcher m = CREATE.matcher(createStatement);
        if (!m.find()) {
            throw new IllegalArgumentException("not a CREATE TABLE IF NOT EXISTS statement: " + createStatement);
        }
        return m.group(1);
    }

    /** Column names in the order written, lower case. */
    static List<String> columns(String createStatement) {
        List<String> names = new ArrayList<>();
        boolean inBody = false;
        for (String raw : createStatement.split("\n")) {
            String line = raw.trim();
            if (!inBody) {
                inBody = CREATE.matcher(line).find();
                continue;
            }
            if (line.isEmpty()) continue;
            String first = line.split("[\\s(]+")[0].toUpperCase(Locale.ROOT);
            if (NOT_A_COLUMN.contains(first) || line.startsWith(")")) continue;
            names.add(line.split("\\s+")[0].toLowerCase(Locale.ROOT));
        }
        return names;
    }

    static Set<String> referencedTables(String createStatement) {
        Set<String> tables = new LinkedHashSet<>();
        Matcher m = REFERENCES.matcher(createStatement);
        while (m.find()) tables.add(m.group(1));
        return tables;
    }

    static Map<String, String> byTable(List<String> statements) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String statement : statements) result.put(tableName(statement), statement);
        return result;
    }
}
