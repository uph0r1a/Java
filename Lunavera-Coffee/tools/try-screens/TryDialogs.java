package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.dao.*;
import com.coffeeshop.coffeeshopmanagement.model.*;
import com.coffeeshop.coffeeshopmanagement.util.TestSession;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.stage.Window;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * Tool, not part of the build (TODO 6.14, Session 258). Opens screens in a real JavaFX toolkit (on Linux under {@code xvfb-run}) and
 * records which thread opens each database connection, so a screen that still reads on the screen thread shows up as a number.
 * It must be compiled into the package {@code com.coffeeshop.coffeeshopmanagement.controller} against the project's classes and its
 * test classes (it uses {@code TestDatabaseSupport} and {@code TestSession}); see {@code try-one.sh} for the way it was run.
 * Run it with a throw-away data folder: it adds a voucher, an expense and an ingredient to the database it finds.
 */
public class TryDialogs {
    static final AtomicInteger fxConnects = new AtomicInteger(), otherConnects = new AtomicInteger();
    static volatile boolean counting = false;

    static class Spy implements Driver {
        final Driver real = new org.sqlite.JDBC();
        public Connection connect(String url, Properties p) throws SQLException {
            if (!acceptsURL(url)) return null;
            if (counting) {
                String n = Thread.currentThread().getName();
                if (n.contains("JavaFX Application Thread")) { fxConnects.incrementAndGet(); if (System.getProperty("trace") != null) new Throwable("DB call on FX thread").printStackTrace(System.out); }
                else otherConnects.incrementAndGet();
            }
            return real.connect(url, p);
        }
        public boolean acceptsURL(String u) { return u != null && u.startsWith("jdbc:sqlite"); }
        public DriverPropertyInfo[] getPropertyInfo(String u, Properties p) { return new DriverPropertyInfo[0]; }
        public int getMajorVersion() { return 3; } public int getMinorVersion() { return 0; }
        public boolean jdbcCompliant() { return false; } public java.util.logging.Logger getParentLogger() { return null; }
    }

    static void walk(Node n, List<String> out) {
        if (n instanceof TableView<?> t) out.add("Table(" + t.getItems().size() + ")");
        else if (n instanceof ListView<?> l) out.add("List(" + l.getItems().size() + ")");
        else if (n instanceof ComboBox<?> c) out.add("Combo(" + c.getItems().size() + ")");
        if (n instanceof Parent p) for (Node k : p.getChildrenUnmodifiable()) walk(k, out);
    }

    static String describe() throws Exception {
        CompletableFuture<String> f = new CompletableFuture<>();
        Platform.runLater(() -> {
            StringBuilder sb = new StringBuilder();
            for (Window w : Window.getWindows()) if (w.isShowing() && w.getScene() != null) {
                List<String> parts = new ArrayList<>(); walk(w.getScene().getRoot(), parts);
                sb.append(parts);
            }
            f.complete(sb.toString());
        });
        return f.get(5, TimeUnit.SECONDS);
    }

    static void closeAll() throws Exception {
        CompletableFuture<Void> f = new CompletableFuture<>();
        Platform.runLater(() -> { for (Window w : new ArrayList<>(Window.getWindows())) w.hide(); f.complete(null); });
        f.get(5, TimeUnit.SECONDS);
    }

    static void tryOne(String name, Runnable show) throws Exception {
        fxConnects.set(0); otherConnects.set(0);
        AtomicReference<Throwable> err = new AtomicReference<>();
        counting = true;
        Platform.runLater(() -> { try { show.run(); } catch (Throwable t) { err.set(t); } });
        String last = ""; long end = System.currentTimeMillis() + 6000;
        while (System.currentTimeMillis() < end) { Thread.sleep(300); last = describe(); }
        counting = false;
        closeAll(); Thread.sleep(300);
        System.out.printf("%-18s db connections on screen thread: %d, in background: %d, content after load: %s%s%n", name, fxConnects.get(), otherConnects.get(), last, err.get() != null ? "  THREW " + err.get() : "");
    }

    @SuppressWarnings("unchecked")
    static void optionsPane(Product first) {
        OptionsPane pane = new OptionsPane();
        javafx.stage.Stage stage = new javafx.stage.Stage();
        stage.setScene(new javafx.scene.Scene(pane.node(), 700, 500));
        stage.show();
        pane.reload();
        new Thread(() -> {
            try {
                Thread.sleep(1500); // list has arrived
                CompletableFuture<Void> f = new CompletableFuture<>();
                Platform.runLater(() -> { ComboBox<Product> c = (ComboBox<Product>) find(pane.node(), ComboBox.class);
                    c.getItems().stream().filter(p -> p.getId() == first.getId()).findFirst().ifPresent(c::setValue); pane.reload(); f.complete(null); });
                f.get(5, TimeUnit.SECONDS);
            } catch (Exception e) { System.out.println("harness: " + e); }
        }).start();
    }

    static Node find(Node n, Class<?> type) {
        if (type.isInstance(n)) return n;
        if (n instanceof Parent p) for (Node k : p.getChildrenUnmodifiable()) { Node r = find(k, type); if (r != null) return r; }
        return null;
    }

    public static void main(String[] a) throws Exception {
        DriverManager.getDrivers(); // load the real one
        for (Enumeration<Driver> e = DriverManager.getDrivers(); e.hasMoreElements();) DriverManager.deregisterDriver(e.nextElement());
        DriverManager.registerDriver(new Spy());
        com.coffeeshop.coffeeshopmanagement.dao.TestDatabaseSupport.ensureReady();
        TestSession.admin();
        // seed something to show
        new VoucherDAO().add("TRYME10", Voucher.PERCENT, 10L, null, null, null, null, false);
        new ExpenseDAO().add(LocalDate.now(), new BigDecimal("45000"), com.coffeeshop.coffeeshopmanagement.service.ExpenseRules.SUPPLIES, "try", false);
        new IngredientDAO().insert("Milk", "ml", 1000, 200, null);
        List<Product> products = new ProductDAO().findAll();
        CountDownLatch up = new CountDownLatch(1); Platform.startup(up::countDown); up.await();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> System.out.println("UNCAUGHT on " + t.getName() + ": " + e));
        String only = a.length > 0 ? a[0] : "";
        Map<String, Runnable> all = new LinkedHashMap<>();
        all.put("VoucherDialog", VoucherDialog::show);
        all.put("ExpenseDialog", ExpenseDialog::show);
        all.put("PromotionDialog", PromotionDialog::show);
        all.put("StockLogDialog", () -> StockLogDialog.show(products, () -> {}));
        all.put("IngredientDialog", () -> IngredientDialog.show(() -> {}));
        Product first = products.get(0);
        new ProductOptionDAO().save(first.getId(), List.of(
                new ProductOption(0, first.getId(), OptionKind.SIZE, "M", BigDecimal.ZERO, BigDecimal.ZERO, 100, true, true),
                new ProductOption(0, first.getId(), OptionKind.EXTRA, "Extra shot", new BigDecimal("8000"), BigDecimal.ZERO, 100, false, true)));
        all.put("OptionsPane", () -> optionsPane(first));
        for (var e : all.entrySet()) if (only.isEmpty() || only.equals(e.getKey())) tryOne(e.getKey(), e.getValue());
        Platform.exit(); System.exit(0);
    }
}
