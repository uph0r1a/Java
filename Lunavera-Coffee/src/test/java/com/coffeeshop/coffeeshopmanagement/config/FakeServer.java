package com.coffeeshop.coffeeshopmanagement.config;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A scripted stand-in for a MariaDB server, built from {@link Proxy} objects (TODO 1.8 tests). It answers the queries of
 * {@link MariaDbRestore} from rules, records every statement and every commit and rollback in {@link #log}, and can be told to
 * fail on a statement. It is no database: what it proves is the order of the steps, what is rolled back and what is refused;
 * {@code MariaDbRestoreServerTest} runs the same restore on a real server.
 */
final class FakeServer implements MariaDbRestore.Connections {

    /** Every statement, in order, as {@code "connection: sql"}, plus COMMIT, ROLLBACK and what the test's hooks add. */
    final List<String> log = new ArrayList<>();

    private record Rule(String after, String contains, String param, List<Object[]> rows) {
    }

    private record Failure(String contains, SQLException error) {
    }

    private final List<Rule> rules = new ArrayList<>();
    private final List<Failure> failures = new ArrayList<>();
    private final String database;
    private int connections;
    boolean commitFails;

    FakeServer(String database) {
        this.database = database;
    }

    /** Answer a query whose text contains {@code contains} (and whose first parameter is {@code param}, when given). First rule that fits wins. */
    FakeServer rule(String contains, String param, Object[]... rows) {
        rules.add(new Rule(null, contains, param, Arrays.asList(rows)));
        return this;
    }

    /** Like {@link #rule}, but only once a statement containing {@code afterLog} has been run (the server's answer changes during the swap). */
    FakeServer ruleAfter(String afterLog, String contains, String param, Object[]... rows) {
        rules.add(new Rule(afterLog, contains, param, Arrays.asList(rows)));
        return this;
    }

    FakeServer failOn(String contains, SQLException error) {
        failures.add(new Failure(contains, error));
        return this;
    }

    static Object[] row(Object... cells) {
        return cells;
    }

    boolean ran(String part) {
        return log.stream().anyMatch(l -> l.contains(part));
    }

    int indexOf(String part) {
        return indexOf(part, 0);
    }

    /** The first entry at or after {@code from} that contains {@code part}; -1 when there is none. */
    int indexOf(String part, int from) {
        for (int i = from; i < log.size(); i++) {
            if (log.get(i).contains(part)) return i;
        }
        return -1;
    }

    long count(String part) {
        return log.stream().filter(l -> l.contains(part)).count();
    }

    // ---- Connections

    @Override
    public Connection live() {
        return connection("live" + (++connections));
    }

    @Override
    public Connection scratch(String scratchDatabase) {
        return connection("scratch" + (++connections));
    }

    @Override
    public String liveDatabase() {
        return database;
    }

    private Connection connection(String tag) {
        InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
            case "createStatement" -> statement(tag);
            case "prepareStatement" -> prepared(tag, (String) args[0]);
            case "commit" -> {
                log.add("COMMIT");
                if (commitFails) throw new SQLException("commit failed", "08S01", 2013);
                yield null;
            }
            case "rollback" -> {
                log.add("ROLLBACK");
                yield null;
            }
            case "setAutoCommit", "close" -> null;
            case "getAutoCommit" -> true;
            case "toString" -> tag;
            case "hashCode" -> tag.hashCode();
            case "equals" -> proxy == args[0];
            default -> throw new UnsupportedOperationException("Connection." + method.getName());
        };
        return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {Connection.class}, handler);
    }

    private Statement statement(String tag) {
        InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
            case "execute", "executeUpdate" -> {
                run(tag, (String) args[0]);
                yield method.getName().equals("execute") ? (Object) false : (Object) 1;
            }
            case "executeQuery" -> results(run(tag, (String) args[0]), null);
            case "close" -> null;
            default -> throw new UnsupportedOperationException("Statement." + method.getName());
        };
        return (Statement) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {Statement.class}, handler);
    }

    private PreparedStatement prepared(String tag, String sql) {
        String[] first = new String[1];
        InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
            case "setInt" -> null;
            case "setString" -> {
                if ((Integer) args[0] == 1) first[0] = (String) args[1];
                yield null;
            }
            case "executeQuery" -> results(run(tag, sql), first[0]);
            case "close" -> null;
            default -> throw new UnsupportedOperationException("PreparedStatement." + method.getName());
        };
        return (PreparedStatement) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {PreparedStatement.class}, handler);
    }

    private String run(String tag, String sql) throws SQLException {
        log.add(tag + ": " + sql);
        for (Failure failure : failures) {
            if (sql.contains(failure.contains())) throw failure.error();
        }
        return sql;
    }

    private ResultSet results(String sql, String param) {
        List<Object[]> rows = List.of();
        for (Rule rule : rules) {
            if ((rule.after() == null || ran(rule.after())) && sql.contains(rule.contains())
                    && (rule.param() == null || rule.param().equals(param))) {
                rows = rule.rows();
                break;
            }
        }
        List<Object[]> data = rows;
        int[] at = {-1};
        InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
            case "next" -> ++at[0] < data.size();
            case "getString" -> {
                Object cell = data.get(at[0])[(Integer) args[0] - 1];
                yield cell == null ? null : String.valueOf(cell);
            }
            case "getLong" -> ((Number) data.get(at[0])[(Integer) args[0] - 1]).longValue();
            case "getInt" -> ((Number) data.get(at[0])[(Integer) args[0] - 1]).intValue();
            case "getObject" -> data.get(at[0])[(Integer) args[0] - 1];
            case "close" -> null;
            default -> throw new UnsupportedOperationException("ResultSet." + method.getName());
        };
        return (ResultSet) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {ResultSet.class}, handler);
    }
}
