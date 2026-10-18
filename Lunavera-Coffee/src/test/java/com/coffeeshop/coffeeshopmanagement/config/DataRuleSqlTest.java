package com.coffeeshop.coffeeshopmanagement.config;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * TODO 6.24: the text of the data rules, without a database. The MariaDB statements cannot be run in the normal test run (they
 * need a server), so this at least fixes their shape; {@code DataRulesTest} runs the SQLite triggers for real.
 */
public class DataRuleSqlTest {

    @Test
    public void everyRuleHasAUniqueNameATableAConditionAndAMessage() {
        Set<String> names = new HashSet<>();
        for (SchemaMigrations.DataRule rule : SchemaMigrations.DATA_RULES) {
            assertTrue("duplicate rule name " + rule.name(), names.add(rule.name()));
            assertFalse(rule.table().isBlank());
            assertTrue(rule.violation(), rule.violation().contains("{r}."));
            assertFalse(rule.message().isBlank());
            assertFalse("a quote would break the trigger text: " + rule.message(), rule.message().contains("'"));
        }
        assertTrue(names.size() >= 7);
    }

    @Test
    public void theMariaDbStatementIsACheckOfTheOppositeOfTheViolation() {
        SchemaMigrations.DataRule rule = new SchemaMigrations.DataRule("demo", "products", "{r}.price < 0 OR {r}.stock < 0", "x");
        assertEquals("ALTER TABLE products ADD CONSTRAINT rule_demo CHECK (NOT (price < 0 OR stock < 0))",
                SchemaMigrations.mariaCheckSql(rule));
    }

    @Test
    public void noGeneratedMariaDbStatementKeepsATriggerPlaceholder() {
        for (SchemaMigrations.DataRule rule : SchemaMigrations.DATA_RULES) {
            String sql = SchemaMigrations.mariaCheckSql(rule);
            assertFalse(sql, sql.contains("{r}") || sql.contains("NEW."));
            assertTrue(sql, sql.startsWith("ALTER TABLE " + rule.table() + " ADD CONSTRAINT rule_" + rule.name() + " CHECK (NOT ("));
            assertEquals("the parentheses balance: " + sql, sql.chars().filter(c -> c == '(').count(), sql.chars().filter(c -> c == ')').count());
        }
    }

    @Test
    public void theColumnsTheRulesNameExistInBothSchemas() {
        String sqlite = String.join("\n", SqliteSchema.TABLES).toLowerCase();
        String maria = String.join("\n", MariaDbSchema.TABLES).toLowerCase();
        for (String column : new String[]{"price", "cost", "stock", "quantity", "unit_price", "line_total", "subtotal", "discount",
                "total", "loyalty_points", "role", "status"}) {
            assertTrue("SQLite has no column " + column, sqlite.contains(column));
            assertTrue("MariaDB has no column " + column, maria.contains(column));
        }
    }
}
