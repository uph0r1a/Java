package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig;
import com.coffeeshop.coffeeshopmanagement.model.Category;
import com.coffeeshop.coffeeshopmanagement.model.Customer;
import org.junit.BeforeClass;
import org.junit.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * TODO 6.36 / 6.40: a duplicate is recognised by the driver's error code, not by the wording of its message. The first
 * tests need no database; the last ones make the real driver (SQLite by default, MariaDB with
 * {@code -Dlunavera.test.db=mariadb}) refuse a duplicate and check that the code is what the helper expects.
 */
public class UniqueViolationTest {

    @BeforeClass
    public static void setUpDatabase() {
        TestDatabaseSupport.ensureReady();
    }

    // ---- the rule itself, no database ----

    @Test
    public void sqliteExtendedUniqueAndPrimaryKeyCodesCount() {
        assertTrue(UniqueViolation.isSqliteUnique(2067, "anything at all"));
        assertTrue(UniqueViolation.isSqliteUnique(1555, null));
    }

    @Test
    public void otherSqliteConstraintsDoNot() {
        assertFalse("NOT NULL (1299)", UniqueViolation.isSqliteUnique(1299, "UNIQUE appears in this text"));
        assertFalse("FOREIGN KEY (787)", UniqueViolation.isSqliteUnique(787, null));
        assertFalse("CHECK (275)", UniqueViolation.isSqliteUnique(275, null));
        assertFalse("not a constraint at all", UniqueViolation.isSqliteUnique(1, "UNIQUE constraint failed: x.y"));
    }

    @Test
    public void thePlainConstraintCodeFallsBackToTheMessageOnly() {
        assertTrue(UniqueViolation.isSqliteUnique(19, "[SQLITE_CONSTRAINT] UNIQUE constraint failed: customers.phone"));
        assertFalse(UniqueViolation.isSqliteUnique(19, "NOT NULL constraint failed: customers.full_name"));
        assertFalse(UniqueViolation.isSqliteUnique(19, null));
    }

    @Test
    public void mariaDbDuplicateEntryIsRecognisedByItsErrorCodeAlone() {
        assertTrue("wording does not matter", UniqueViolation.isUnique(new SQLException("une autre langue", "23000", 1062)));
        assertFalse("a message that says duplicate but has another code",
                UniqueViolation.isUnique(new SQLException("Duplicate entry 'x' for key 'k'", "23000", 1452)));
        assertFalse(UniqueViolation.isUnique(new SQLException("UNIQUE constraint failed: a.b")));
        assertFalse(UniqueViolation.isUnique(null));
    }

    // ---- the real driver ----

    private static void insertCategoryRaw(String name) throws SQLException {
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement st = c.prepareStatement("INSERT INTO categories (name, description, active) VALUES (?, '', 1)")) {
            st.setString(1, name);
            st.executeUpdate();
        }
    }

    @Test
    public void theRealDriverReportsADuplicateCategoryNameAsUnique() throws SQLException {
        String name = "uv-" + UUID.randomUUID();
        insertCategoryRaw(name);
        SQLException e = assertThrows(SQLException.class, () -> insertCategoryRaw(name));
        assertTrue("code " + e.getErrorCode() + ": " + e.getMessage(), UniqueViolation.isUnique(e));
    }

    @Test
    public void theRealDriverDoesNotCallAMissingValueUnique() {
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement st = c.prepareStatement("INSERT INTO categories (name, description, active) VALUES (NULL, '', 1)")) {
            st.executeUpdate();
            fail("a category without a name must be refused");
        } catch (SQLException e) {
            assertFalse("NOT NULL is not a duplicate: " + e.getMessage(), UniqueViolation.isUnique(e));
        }
    }

    @Test
    public void theDaosTurnADuplicateIntoTheirFriendlyMessage() {
        // The category DAO also checks the name before it inserts; the customer DAO relies on the database alone, so the
        // customer half is the one that goes through the code check.
        String name = "uv-" + UUID.randomUUID();
        CategoryDAO categories = new CategoryDAO();
        Category first = new Category();
        first.setName(name);
        first.setActive(true);
        categories.insert(first);
        Category second = new Category();
        second.setName(name);
        second.setActive(true);
        DataAccessException e = assertThrows(DataAccessException.class, () -> categories.insert(second));
        assertTrue(e.getMessage(), e.getMessage().contains("Đã tồn tại danh mục"));

        String phone = "09" + String.format("%08d", Math.abs(UUID.randomUUID().getMostSignificantBits() % 100_000_000L));
        CustomerDAO customers = new CustomerDAO();
        customers.insert(customer(phone));
        DataAccessException dup = assertThrows(DataAccessException.class, () -> customers.insert(customer(phone)));
        assertTrue(dup.getMessage(), dup.getMessage().contains("khách hàng khác"));
    }

    private static Customer customer(String phone) {
        Customer c = new Customer();
        c.setFullName("uv-" + UUID.randomUUID());
        c.setPhone(phone);
        return c;
    }
}
