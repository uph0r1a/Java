package com.coffeeshop.coffeeshopmanagement.tools;

import com.coffeeshop.coffeeshopmanagement.dao.TestDatabaseSupport;
import com.coffeeshop.coffeeshopmanagement.controller.*;
import com.coffeeshop.coffeeshopmanagement.model.*;
import com.coffeeshop.coffeeshopmanagement.util.*;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Labeled;
import javafx.scene.image.WritableImage;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.File;
import java.util.*;
import java.util.concurrent.CountDownLatch;

/** Measures, on real screens, every label, button, column header and cell whose text does not fit (JavaFX shows "..." when it is cut). */
/**
 * A tool, not a test (its name does not end in "Test", so Maven does not run it): it opens every screen and the main dialogs of the
 * program in English and in Vietnamese on a real window, and prints one line "CUT|language|screen|..." for every label, button, column
 * header or cell whose text JavaFX had to cut to "..." (TODO 1.7). Pictures of every screen are written to /tmp/shots. It needs a
 * display; on a server use a virtual one:
 * <pre>xvfb-run -a -s "-screen 0 1700x1100x24" java -Dprism.order=sw ... com.coffeeshop.coffeeshopmanagement.tools.ScreenFitCheck</pre>
 * (run with the test classpath; {@code -Dlunavera.noFitFix=true} measures without {@code FitFix}). Cells that show a product's or
 * a person's name are data, not interface text, and are expected to be cut when the name is long.
 */
public class ScreenFitCheck extends Application {
    static final List<String> OUT = Collections.synchronizedList(new ArrayList<>());
    static Stage main;

    public static void main(String[] args) { launch(args); }

    static void onFx(Runnable r) throws Exception {
        CountDownLatch l = new CountDownLatch(1);
        Platform.runLater(() -> { try { r.run(); } finally { l.countDown(); } });
        l.await();
    }

    @Override
    public void start(Stage stage) {
        main = stage;
        stage.setWidth(1600); stage.setHeight(1000);
        stage.show();
        Thread t = new Thread(() -> {
            try { run(); } catch (Throwable e) { e.printStackTrace(); }
            Platform.exit();
            System.exit(0);
        });
        t.setDaemon(true); t.start();
    }

    record Item(String name, Runnable open, boolean dialog) {}

    void run() throws Exception {
        TestDatabaseSupport.ensureReady();
        TestSession.admin();
        onFx(() -> AppStyle.install()); // the bundled font and the shared style sheet, as in the real app
        new File("/tmp/shots").mkdirs();
        String[] screens = {"admin-trangchu", "employee-trangchu", "quanlydonhang", "quanlysanpham", "quanlydanhmuc", "quanlykhachhang", "quanlynhanvien", "quanlytaikhoan", "dangnhap"};
        List<Item> items = new ArrayList<>();
        for (String s : screens) items.add(new Item(s, () -> SceneNavigator.switchScene(main, "/fxml/" + s + ".fxml"), false));
        items.add(new Item("OrderHistoryWindow", OrderHistoryWindow::show, true));
        items.add(new Item("ReportWindow", ReportWindow::show, true));
        items.add(new Item("AuditLogWindow", AuditLogWindow::show, true));
        items.add(new Item("ExpenseDialog", ExpenseDialog::show, true));
        items.add(new Item("ExpenseCashOut", () -> ExpenseDialog.showCashOut(() -> {}), true));
        items.add(new Item("GiftCardDialog", GiftCardDialog::show, true));
        items.add(new Item("VoucherDialog", VoucherDialog::show, true));
        items.add(new Item("ShiftCloseDialog", ShiftCloseDialog::show, true));
        items.add(new Item("TimeClockDialog", TimeClockDialog::show, true));
        items.add(new Item("TimeClockCorrection", () -> TimeClockCorrectionDialog.show(() -> {}), true));
        items.add(new Item("CategoryDialog", () -> CategoryDialog.show(null, () -> {}), true));
        items.add(new Item("OrderNoteDialog", () -> OrderNoteDialog.show("Latte", ""), true));
        items.add(new Item("PasswordChangeDialog", () -> PasswordChangeDialog.showVoluntary(Session.getCurrentUser()), true));
        items.add(new Item("SettingsDialog", () -> {
            try {
                var m = Class.forName("com.coffeeshop.coffeeshopmanagement.controller.SettingsDialog").getDeclaredMethod("show", Runnable.class, Runnable.class, Runnable.class);
                m.setAccessible(true);
                m.invoke(null, (Runnable) () -> {}, (Runnable) () -> {}, (Runnable) () -> {});
            } catch (Exception e) { throw new RuntimeException(e); }
        }, true));
        for (String audience : new String[]{"OWNER", "CASHIER"}) { // the guide windows (TODO 6.71), opened as the Help buttons open them
            items.add(new Item("HelpDialog" + audience, () -> {
                try {
                    var m = Class.forName("com.coffeeshop.coffeeshopmanagement.controller.HelpDialog").getDeclaredMethod("show", String.class);
                    m.setAccessible(true);
                    var f = Class.forName("com.coffeeshop.coffeeshopmanagement.util.HelpPages").getField(audience);
                    m.invoke(null, (String) f.get(null));
                } catch (Exception e) { throw new RuntimeException(e); }
            }, true));
        }
        for (String lang : new String[]{I18n.EN, I18n.VI}) {
            for (Item item : items) {
                onFx(() -> I18n.setLanguage(lang));
                try {
                    Platform.runLater(() -> { try { item.open().run(); } catch (Throwable e) { OUT.add("ERROR|" + lang + "|" + item.name() + "|" + e); } });
                    Thread.sleep(item.dialog() ? 1500 : 2500);
                    onFx(() -> collect(lang, item.name()));
                    onFx(() -> { for (Window w : new ArrayList<>(Window.getWindows())) if (w != main && w.isShowing()) w.hide(); });
                    Thread.sleep(300);
                } catch (Throwable e) { OUT.add("ERROR|" + lang + "|" + item.name() + "|" + e); }
            }
        }
        for (String l : OUT) System.out.println(l);
    }

    static boolean visible(Node n) {
        for (Node p = n; p != null; p = p.getParent()) if (!p.isVisible() || !p.isManaged() && p != n && false) return false;
        return true;
    }

    static void collect(String lang, String name) {
        int shot = 0;
        for (Window w : new ArrayList<>(Window.getWindows())) {
            if (!w.isShowing() || w.getScene() == null) continue;
            Parent root = w.getScene().getRoot();
            root.applyCss(); root.layout();
            walk(root, lang, name + (w == main ? "" : "@" + (w instanceof Stage s ? s.getTitle() : "popup")));
            try {
                WritableImage img = w.getScene().snapshot(null);
                String file = "/tmp/shots/" + lang + "_" + name + (w == main ? "" : "_win" + shot) + ".png";
                javax.imageio.ImageIO.write(toBuffered(img), "png", new File(file));
                shot++;
            } catch (Throwable e) { /* ignore snapshot failures */ }
        }
    }

    static void walk(Node n, String lang, String where) {
        if (n instanceof Labeled l && visible(l) && l.getWidth() > 0) {
            String full = l.getText();
            if (full != null && !full.isBlank()) {
                Node t = l.lookup(".text");
                if (t instanceof Text tx) {
                    String shown = tx.getText();
                    if (shown != null && !shown.equals(full) && (shown.endsWith("...") || shown.endsWith("\u2026")) && !full.endsWith("...")) {
                        String kind = l.getClass().getSimpleName();
                        String id = l.getId() != null ? "#" + l.getId() : l.getStyleClass().toString();
                        OUT.add("CUT|" + lang + "|" + where + "|" + kind + "|" + id + "|w=" + Math.round(l.getWidth()) + "|" + full.replace('\n', ' ') + "|shown=" + shown.replace('\n', ' '));
                    }
                }
            }
        }
        if (n instanceof Parent p) for (Node c : new ArrayList<>(p.getChildrenUnmodifiable())) walk(c, lang, where);
    }

    /** The picture as a java.awt image, so the PNG can be written without the javafx.swing module (which the program does not use). */
    private static java.awt.image.BufferedImage toBuffered(javafx.scene.image.Image img) {
        int w = (int) img.getWidth();
        int h = (int) img.getHeight();
        java.awt.image.BufferedImage out = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        javafx.scene.image.PixelReader reader = img.getPixelReader();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                out.setRGB(x, y, reader.getArgb(x, y));
            }
        }
        return out;
    }
}
