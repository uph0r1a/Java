package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.model.Role;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

/** TODO 6.78: tests that came from a mutation run on {@link DiscountPolicy}. Factory shop rules, no database. */
public class DiscountPolicyBoundaryTest {

    @Before
    public void factoryRules() {
        ShopRules.resetForTests();
    }

    @After
    public void restore() {
        ShopRules.resetForTests();
    }

    @Test
    public void noSubtotalANegativeSubtotalAndAZeroSubtotalAllowNoDiscountForAnyRole() {
        for (Role role : new Role[]{Role.ADMIN, Role.EMPLOYEE, null}) {
            assertEquals(role + " / null", 0, DiscountPolicy.maxDiscount(role, null).signum());
            assertEquals(role + " / negative", 0, DiscountPolicy.maxDiscount(role, new BigDecimal("-100")).signum());
            assertEquals(role + " / zero", 0, DiscountPolicy.maxDiscount(role, BigDecimal.ZERO).signum());
        }
    }

    @Test
    public void theReasonAndTheColumnItIsSavedInHaveTheirOwnSizes() throws IOException {
        // read through reflection: a static final int is copied into this class when it is compiled, so a plain read would
        // keep seeing the old number whatever the class says now
        int reason = constant("REASON_MAX_LENGTH");
        int column = constant("COLUMN_MAX_LENGTH");
        assertEquals(200, reason);
        assertEquals(255, column);
        // the 255 is the width of the column, so it must stay what the table definition says
        String schema = Files.readString(Path.of("src", "main", "java", "com", "coffeeshop", "coffeeshopmanagement", "config", "SqliteSchema.java"),
                StandardCharsets.UTF_8) + Files.readString(Path.of("src", "main", "java", "com", "coffeeshop", "coffeeshopmanagement", "config", "MariaDbSchema.java"),
                StandardCharsets.UTF_8);
        assertTrue("discount_reason is declared with a width of " + column,
                schema.contains("discount_reason VARCHAR(" + column + ")"));
    }

    private static int constant(String name) throws IOException {
        try {
            return DiscountPolicy.class.getField(name).getInt(null);
        } catch (ReflectiveOperationException e) {
            throw new IOException(e);
        }
    }
}
