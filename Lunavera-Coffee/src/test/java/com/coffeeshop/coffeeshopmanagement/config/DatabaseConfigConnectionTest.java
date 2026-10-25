package com.coffeeshop.coffeeshopmanagement.config;

import org.junit.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/** TODO 2.71: a connection whose set-up fails is closed, not left open. */
public class DatabaseConfigConnectionTest {

    private static Connection fake(AtomicInteger closed, boolean failOnStatement, RuntimeException runtime) {
        InvocationHandler handler = (proxy, method, args) -> {
            switch (method.getName()) {
                case "close":
                    closed.incrementAndGet();
                    return null;
                case "createStatement":
                    if (runtime != null) throw runtime;
                    if (failOnStatement) throw new SQLException("database is locked");
                    return null;
                default:
                    return null;
            }
        };
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[] {Connection.class}, handler);
    }

    @Test
    public void aFailingPragmaClosesTheConnectionAndPassesTheErrorOn() {
        AtomicInteger closed = new AtomicInteger();
        try {
            DatabaseConfig.configureSqlite(fake(closed, true, null));
            fail("the SQLException must reach the caller");
        } catch (SQLException expected) {
            assertEquals("database is locked", expected.getMessage());
        }
        assertEquals("the connection was closed once", 1, closed.get());
    }

    @Test
    public void aRuntimeErrorDuringSetUpAlsoClosesTheConnection() throws Exception {
        AtomicInteger closed = new AtomicInteger();
        try {
            DatabaseConfig.configureSqlite(fake(closed, false, new IllegalStateException("boom")));
            fail("the exception must reach the caller");
        } catch (IllegalStateException expected) {
            assertEquals("boom", expected.getMessage());
        }
        assertEquals(1, closed.get());
    }
}
