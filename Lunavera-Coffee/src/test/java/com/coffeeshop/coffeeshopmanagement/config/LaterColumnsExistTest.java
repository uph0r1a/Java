package com.coffeeshop.coffeeshopmanagement.config;

import com.coffeeshop.coffeeshopmanagement.dao.TestDatabaseSupport;
import org.junit.BeforeClass;
import org.junit.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.Assert.assertEquals;

/** Every column of SqliteSchema.LATER_COLUMNS is really there after start-up, on SQLite and on MariaDB alike. */
public class LaterColumnsExistTest {

    @BeforeClass
    public static void setUpDatabase() {
        TestDatabaseSupport.ensureReady();
    }

    @Test
    public void everyLaterColumnCanBeSelected() throws Exception {
        try (Connection connection = DatabaseConfig.getConnection(); Statement st = connection.createStatement()) {
            for (String[] column : SqliteSchema.LATER_COLUMNS) {
                try (ResultSet rs = st.executeQuery("SELECT " + column[1] + " FROM " + column[0] + " WHERE 1 = 0")) {
                    rs.getMetaData(); // the statement is valid: the column exists
                }
            }
        }
    }

    @Test
    public void theColumnsOfThisChangeAreAmongThem() {
        java.util.Set<String> names = new java.util.HashSet<>();
        for (String[] column : SqliteSchema.LATER_COLUMNS) names.add(column[0] + "." + column[1]);
        for (String wanted : new String[]{"categories.sort_order", "products.sort_order", "products.reorder_level",
                "orders.points_earned", "orders.cash_received", "orders.change_given", "orders.transfer_ref",
                "products.code", "order_items.note", "employees.anonymised_at", "orders.points_redeemed", "orders.points_value",
                "customers.anonymised_at", "shift_closes.cash_out", "orders.cancelled_at", "users.pin_hash", "users.pin_failures"}) {
            assertEquals(wanted, true, names.contains(wanted));
        }
    }
}
