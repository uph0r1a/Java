package com.coffeeshop.coffeeshopmanagement.dao;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.Assert.*;

/**
 * TODO 6.47: MySQL, and a MariaDB server whose {@code sql_mode} has {@code ONLY_FULL_GROUP_BY}, refuse a query that selects a
 * column it does not group by (error 1055), where SQLite and the default MariaDB mode answer. This test reads the sources as
 * text (no database) and keeps a list of every {@code GROUP BY} in the program, each READ once against that rule.
 *
 * <p>A new {@code GROUP BY} fails the test until someone checks it: every selected column must be in the GROUP BY (the same
 * expression, written the same) or inside {@code MAX/MIN/SUM/COUNT/AVG}; then raise the number for that file below and write
 * what was checked in the table. The MariaDB job of the CI runs with the mode ON ({@code .github/workflows/ci.yml}), so a
 * query that slips through is caught there on a real server.
 */
public class GroupByRulesTest {

    private static final Path SOURCES = Path.of("src", "main", "java");

    /**
     * Number of {@code GROUP BY} in each file (comment lines left out), as read on 2026-10-27:
     * <ul>
     * <li>ReportDAO 7: topProducts (the name is inside MAX since TODO 6.47; grouped by product), dailySales and its profit query
     *     (day expression selected and grouped identically), paymentSplit (payment_method), employeeSales (who: same expression;
     *     name in MAX), categorySales (c.id, c.name), unitsSold (oi.product_id).</li>
     * <li>ShiftDAO 1: the same person expression in SELECT and GROUP BY, name in MAX.</li>
     * <li>OrderDAO 1: revenueForLast7Days, the day expression selected and grouped identically.</li>
     * <li>CategoryDAO 1, CustomerDAO 1: the one grouped column is the one selected.</li>
     * <li>TableDAO 1: a sub-query that selects MAX(id) and the grouped table_number.</li>
     * <li>SchemaMigrations 13: duplicate finders that select only the grouped column (or lower() of it, written the same), plus
     *     two texts that only SHOW such a query to the owner.</li>
     * </ul>
     */
    private static final Map<String, Integer> REVIEWED = Map.of(
            "dao/ReportDAO.java", 7,
            "dao/ShiftDAO.java", 1,
            "dao/OrderDAO.java", 1,
            "dao/CategoryDAO.java", 1,
            "dao/CustomerDAO.java", 1,
            "dao/TableDAO.java", 1,
            "config/SchemaMigrations.java", 13);

    private static boolean isComment(String line) {
        String t = line.stripLeading();
        return t.startsWith("*") || t.startsWith("//") || t.startsWith("/*");
    }

    private static Map<String, Integer> groupByCounts() throws IOException {
        Map<String, Integer> counts = new TreeMap<>();
        Path root = SOURCES.resolve("com").resolve("coffeeshop").resolve("coffeeshopmanagement");
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
                int n = 0;
                for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    if (isComment(line)) continue;
                    int from = 0;
                    while ((from = line.indexOf("GROUP BY", from)) >= 0) {
                        n++;
                        from += "GROUP BY".length();
                    }
                }
                if (n > 0) counts.put(root.relativize(file).toString().replace('\\', '/'), n);
            }
        }
        return counts;
    }

    @Test
    public void everyGroupByInTheProgramHasBeenReadAgainstOnlyFullGroupBy() throws IOException {
        Map<String, Integer> found = groupByCounts();
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, Integer> e : found.entrySet()) {
            Integer reviewed = REVIEWED.get(e.getKey());
            if (reviewed == null) {
                problems.add(e.getKey() + " has " + e.getValue() + " new GROUP BY, never read against ONLY_FULL_GROUP_BY");
            } else if (e.getValue() > reviewed) {
                problems.add(e.getKey() + " has " + e.getValue() + " GROUP BY, " + reviewed + " were read");
            } else if (e.getValue() < reviewed) {
                problems.add(e.getKey() + " has only " + e.getValue() + " GROUP BY now: lower the number " + reviewed + " in this test");
            }
        }
        for (String file : REVIEWED.keySet()) {
            if (!found.containsKey(file)) problems.add(file + " has no GROUP BY any more: remove it from this test");
        }
        assertTrue("Check each new GROUP BY (every selected column in the GROUP BY or inside MAX/MIN/SUM/COUNT), then update REVIEWED: "
                + String.join("; ", problems), problems.isEmpty());
    }

    @Test
    public void theBestSellersReportTakesTheProductNameInsideMax() throws IOException {
        String source = Files.readString(SOURCES.resolve("com/coffeeshop/coffeeshopmanagement/dao/ReportDAO.java"), StandardCharsets.UTF_8);
        assertTrue("the name column of topProducts must be inside MAX (TODO 6.47)",
                source.contains("SELECT MAX(COALESCE(p.name, oi.product_name)) AS name"));
        assertFalse("a loose COALESCE(p.name, ...) in the select list is the error 1055 of ONLY_FULL_GROUP_BY",
                source.contains("SELECT COALESCE(p.name, oi.product_name)"));
    }

    @Test
    public void theMariaDbJobOfTheCiRunsWithTheModeOn() throws IOException {
        String ci = Files.readString(Path.of(".github", "workflows", "ci.yml"), StandardCharsets.UTF_8);
        int job = ci.indexOf("test-mariadb:");
        assertTrue(job >= 0);
        String mariaJob = ci.substring(job);
        int mode = mariaJob.indexOf("ONLY_FULL_GROUP_BY");
        int tests = mariaJob.indexOf("-Dlunavera.test.db=mariadb");
        assertTrue("the MariaDB job must switch ONLY_FULL_GROUP_BY on", mode >= 0);
        assertTrue("...and do it BEFORE the tests run", mode < tests);
    }
}
