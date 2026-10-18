package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.dao.TableDAO;
import com.coffeeshop.coffeeshopmanagement.model.DiningTable;
import com.coffeeshop.coffeeshopmanagement.service.DashboardStatsService;
import com.coffeeshop.coffeeshopmanagement.service.DashboardStatsService.DashboardStats;
import com.coffeeshop.coffeeshopmanagement.service.BackupService;
import com.coffeeshop.coffeeshopmanagement.util.AlertUtil;
import com.coffeeshop.coffeeshopmanagement.util.Async;
import com.coffeeshop.coffeeshopmanagement.util.DashboardWidgets;
import com.coffeeshop.coffeeshopmanagement.util.CurrencyUtil;
import com.coffeeshop.coffeeshopmanagement.util.Session;
import com.coffeeshop.coffeeshopmanagement.util.SceneNavigator;
import javafx.fxml.FXML;
import javafx.scene.chart.XYChart;
import javafx.scene.chart.LineChart;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Backs the administrator's home screen (admin-trangchu.fxml): today's figures, the 7-day revenue chart, the table map,
 * the warnings, and the data buttons (backup, restore, reports, transfer QR, phpMyAdmin). Split out of the old
 * {@code AdminController} (TODO 6.4); the navigation shared with the other admin screens is in {@link AdminScreen}.
 */
public class AdminDashboardController extends AdminScreen {

    private final DashboardStatsService dashboardStatsService = new DashboardStatsService();
    private final TableDAO tableDAO = new TableDAO();

    // ---------------------------------------------------------------- Dashboard (admin-trangchu.fxml)
    @FXML private Label dashboardProductCountLabel;
    @FXML private Label dashboardLowStockNoteLabel;
    @FXML private Label todayOrderLabel;
    @FXML private Label dashboardGreetingLabel;
    @FXML private Label dashboardAccountNameLabel;
    @FXML private Button backupButton;
    @FXML private Button restoreButton;
    @FXML private javafx.scene.layout.HBox phpMyAdminBox;
    @FXML private Label todayRevenueLabel;
    @FXML private Label todayCustomerLabel;
    @FXML private LineChart<String, Number> revenueChart;
    @FXML private Label paidOrderLabel;
    @FXML private Label openOrderLabel;
    @FXML private Label staleOrderLabel;
    @FXML private Label cancelledOrderLabel;
    @FXML private GridPane homeTableGrid;
    @FXML private VBox todayOrdersContainer;

    /**
     * Restoring replaces the open database, so it cannot happen while the app runs: the chosen backup is checked
     * and parked, and applied at the next start (after saving the current data as before-restore-*.zip).
     */
    /** Pending-restore notice with a cancel button, shown above the stale-order line on the dashboard (TODO 2.41). */
    private javafx.scene.layout.VBox pendingRestoreBanner;
    private Label pendingRestoreText;

    @FXML
    private void initialize() {
        setupPendingRestoreBanner();
        showMenuReviewNoticeOnce();
        loadHomeTableGrid();
        if (todayOrderLabel != null || todayRevenueLabel != null) {
            loadDashboardStats();
            // The numbers follow the till without leaving the screen (TODO 5.3).
            com.coffeeshop.coffeeshopmanagement.util.AutoRefresh.every(
                    todayOrderLabel != null ? todayOrderLabel : todayRevenueLabel,
                    javafx.util.Duration.seconds(60), () -> loadDashboardStats(true));
        }
        if (dashboardGreetingLabel != null) {
            dashboardGreetingLabel.setText("Chào mừng, " + Session.getDisplayName() + "!");
        }
        if (dashboardAccountNameLabel != null) {
            dashboardAccountNameLabel.setText(Session.getDisplayName());
        }
        if (phpMyAdminBox != null) {
            // The button only makes sense when the data lives in a MariaDB/MySQL server.
            boolean show = !com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig.isSqlite() && Session.isAdmin();
            phpMyAdminBox.setVisible(show);
            phpMyAdminBox.setManaged(show);
        }
        if (dashboardGreetingLabel != null && Session.isAdmin()) {
            // Start-up warning (2A.17): was the transfer QR picture swapped since an admin accepted it?
            Async.run(() -> com.coffeeshop.coffeeshopmanagement.util.TransferQrGuard.dataPicture()
                            .map(com.coffeeshop.coffeeshopmanagement.util.TransferQrGuard::inspect).orElse(null),
                    state -> {
                        if (state == com.coffeeshop.coffeeshopmanagement.util.TransferQrGuard.State.CHANGED) {
                            AlertUtil.warning("Ảnh QR chuyển khoản đã bị đổi",
                                    "Ảnh QR chuyển khoản khác với ảnh đã được xác nhận. Nếu bạn không đổi nó, có thể có người "
                                            + "đã thay bằng QR của tài khoản khác.\n\nBấm \"Mã QR chuyển khoản\" để đặt lại ảnh đúng.");
                        }
                    },
                    error -> { });
        }
    }

    @FXML
    public void handleBackupData() {
        if (!requireAdmin()) return;
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Sao lưu dữ liệu");
        chooser.setInitialFileName("lunavera-backup-" +
                java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")) + ".zip");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Bản sao lưu Lunavera (zip)", "*.zip"));
        // Start outside the app's data folder: a backup stored next to the live data is not a real backup.
        // Then the folder used last time (TODO 5.17), if it still exists.
        java.io.File startFolder = com.coffeeshop.coffeeshopmanagement.util.RememberedFolder.startFor("backup",
                new java.io.File(System.getProperty("user.home")));
        if (startFolder != null) {
            chooser.setInitialDirectory(startFolder);
        }
        java.io.File file = chooser.showSaveDialog(backupButton.getScene().getWindow());
        if (file == null) return;
        com.coffeeshop.coffeeshopmanagement.util.RememberedFolder.remember("backup", file);
        backupButton.setDisable(true);
        Async.run(
                () -> BackupService.createBackup(file.toPath()).toFile(),
                saved -> {
                    backupButton.setDisable(false);
                    String warning = com.coffeeshop.coffeeshopmanagement.util.DataPaths.isInsideDataDir(saved.toPath())
                            ? "\n\nLưu ý: bản sao này nằm ngay trong thư mục dữ liệu của ứng dụng. Nếu thư mục đó bị xóa hoặc hỏng thì bản sao cũng mất. Hãy chép nó sang ổ USB hoặc máy khác."
                            : "";
                    AlertUtil.info("Sao lưu thành công", "Đã lưu bản sao (cơ sở dữ liệu và ảnh sản phẩm) vào:\n" + saved.getAbsolutePath()
                            + "\nDung lượng: " + com.coffeeshop.coffeeshopmanagement.util.FileSizeText.format(saved.length()) +
                            "\n\nĐể khôi phục: dùng nút \"Khôi phục dữ liệu\" và chọn tệp này." + warning +
                            "\n\nBản sao này chứa tên, số điện thoại khách hàng và mật khẩu (đã mã hóa) của nhân viên và KHÔNG được mã hóa: "
                            + "chỉ giữ ở nơi an toàn, không gửi qua mạng hay để ổ USB không có người trông." +
                            "\n\nỨng dụng cũng tự sao lưu mỗi ngày vào thư mục backups (giữ "
                            + BackupService.KEEP_AUTOMATIC + " bản gần nhất).");
                },
                error -> {
                    backupButton.setDisable(false);
                    AlertUtil.error("Không thể sao lưu", error.getMessage());
                }
        );
    }

    private void setupPendingRestoreBanner() {
        if (staleOrderLabel == null || !(staleOrderLabel.getParent() instanceof javafx.scene.layout.VBox box)) return;
        pendingRestoreText = new Label();
        pendingRestoreText.setWrapText(true);
        pendingRestoreText.getStyleClass().add("stale-order-warning");
        javafx.scene.layout.HBox.setHgrow(pendingRestoreText, javafx.scene.layout.Priority.ALWAYS);
        Button cancel = new Button("Hủy khôi phục");
        cancel.setOnAction(e -> {
            if (AlertUtil.confirm("Hủy khôi phục", "Hủy việc khôi phục đang chờ? Dữ liệu hiện tại sẽ được giữ nguyên.")) {
                BackupService.cancelPendingRestore();
                refreshPendingRestoreBanner();
            }
        });
        pendingRestoreBanner = new javafx.scene.layout.VBox(6, pendingRestoreText, cancel);
        box.getChildren().add(Math.max(0, box.getChildren().indexOf(staleOrderLabel)), pendingRestoreBanner);
        refreshPendingRestoreBanner();
    }

    /** One-time reminder after the starter menu was added: its prices and stock are placeholders (TODO 2.20). */
    private void showMenuReviewNoticeOnce() {
        if (staleOrderLabel == null || !Session.isAdmin()) return; // only on the admin home screen
        Async.run(
                () -> com.coffeeshop.coffeeshopmanagement.config.AppFlags.has(
                        com.coffeeshop.coffeeshopmanagement.config.AppFlags.REVIEW_MENU_NOTICE),
                pending -> {
                    if (!pending) return;
                    AlertUtil.info("Kiểm tra thực đơn mẫu",
                            "Ứng dụng đã thêm sẵn một thực đơn mẫu để bạn thử. Giá bán và tồn kho (mỗi món 20) chỉ là số tạm, "
                                    + "chưa phải số của quán.\n\nHãy vào Quản lý sản phẩm để sửa giá, tồn kho hoặc xóa món không bán "
                                    + "trước khi bán thật.");
                    Async.run(() -> {
                        com.coffeeshop.coffeeshopmanagement.config.AppFlags.clear(
                                com.coffeeshop.coffeeshopmanagement.config.AppFlags.REVIEW_MENU_NOTICE);
                        return Boolean.TRUE;
                    }, done -> { }, error -> { });
                },
                error -> { });
    }

    private void refreshPendingRestoreBanner() {
        if (pendingRestoreBanner == null) return;
        java.util.Optional<java.time.LocalDateTime> since = BackupService.pendingSince();
        pendingRestoreBanner.setVisible(since.isPresent());
        pendingRestoreBanner.setManaged(since.isPresent());
        since.ifPresent(t -> pendingRestoreText.setText("⚠ Đang chờ khôi phục dữ liệu từ bản sao lưu (chuẩn bị lúc "
                + t.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy"))
                + "). Lần mở ứng dụng tới, mọi dữ liệu hiện tại sẽ được thay bằng bản sao lưu đó."));
    }

    @FXML
    public void handleRestoreData() {
        if (!requireAdmin()) return;
        if (!com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig.isSqlite()) {
            AlertUtil.info("Khôi phục dữ liệu", BackupService.MARIADB_RESTORE_HELP);
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Chọn bản sao lưu để khôi phục");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Bản sao lưu (zip hoặc db)", "*.zip", "*.db"));
        java.io.File start = com.coffeeshop.coffeeshopmanagement.util.DataPaths.backupsDir().toFile();
        if (start.isDirectory()) {
            chooser.setInitialDirectory(start);
        }
        java.io.File file = chooser.showOpenDialog(backupButton.getScene().getWindow());
        if (file == null) return;
        // Check the file and show what it holds before anything is asked or parked (TODO 5.22).
        Async.run(
                () -> BackupService.inspect(file.toPath()),
                summary -> {
                    if (!AlertUtil.confirm("Khôi phục dữ liệu",
                            "Tệp: " + file.getName() + "\n\n" + summary.describe()
                                    + "\n\nKhôi phục sẽ thay TOÀN BỘ dữ liệu hiện tại (sản phẩm, đơn hàng, khách hàng, tài khoản, ảnh) "
                                    + "bằng dữ liệu trong bản sao lưu. Dữ liệu hiện tại được lưu tự động vào thư mục backups "
                                    + "(before-restore-...) trước khi thay.\n\nLưu ý: tài khoản và mật khẩu cũng quay về như lúc sao lưu: "
                                    + "nhân viên đã bị xóa hoặc khóa từ đó có thể đăng nhập lại, và mật khẩu cũ (kể cả của quản trị viên) có hiệu lực lại. "
                                    + "Sau khi khôi phục hãy xem lại danh sách tài khoản.\n\nTiếp tục?")) {
                        return;
                    }
                    Async.run(
                            () -> { BackupService.scheduleRestore(file.toPath()); return file; },
                            picked -> {
                                refreshPendingRestoreBanner();
                                if (AlertUtil.confirm("Sẵn sàng khôi phục",
                                        "Ứng dụng cần đóng lại để khôi phục. Hãy mở lại ứng dụng sau đó.\n\nĐóng ứng dụng ngay bây giờ?")) {
                                    javafx.application.Platform.exit();
                                } else {
                                    AlertUtil.info("Khôi phục đang chờ",
                                            "Việc khôi phục sẽ được thực hiện vào lần mở ứng dụng tiếp theo. Dữ liệu bạn nhập từ giờ đến lúc đó "
                                                    + "sẽ không còn sau khi khôi phục. Bạn có thể hủy ngay trên trang chủ.");
                                }
                            },
                            error -> AlertUtil.error("Không thể khôi phục", error.getMessage()));
                },
                error -> AlertUtil.error("Không thể khôi phục", error.getMessage())
        );
    }

    @FXML
    public void handleShowReports() {
        ReportWindow.show();
    }

    /** Admin only (TODO 2A.17 / 4.26): pick the bank-transfer QR picture and type the account holder and bank. */
    @FXML
    public void handleSetTransferQr() {
        if (!requireAdmin()) return;
        javafx.scene.control.TextArea info = new javafx.scene.control.TextArea();
        info.setPromptText("Tên chủ tài khoản\nNgân hàng - số tài khoản");
        info.setPrefRowCount(3);
        var current = com.coffeeshop.coffeeshopmanagement.util.TransferQrGuard.dataPicture();
        current.ifPresent(p -> info.setText(com.coffeeshop.coffeeshopmanagement.util.TransferQrGuard.accountInfo(p)));
        javafx.scene.control.Dialog<javafx.scene.control.ButtonType> dialog = new javafx.scene.control.Dialog<>();
        dialog.setTitle("Mã QR chuyển khoản");
        dialog.setHeaderText("Nhập tên chủ tài khoản và ngân hàng (thu ngân sẽ so sánh với ứng dụng ngân hàng của khách), "
                + "rồi chọn ảnh QR (PNG hoặc JPG, tối đa 2 MB).");
        dialog.getDialogPane().setContent(info);
        javafx.scene.control.ButtonType choose = new javafx.scene.control.ButtonType("Chọn ảnh...",
                javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(choose, javafx.scene.control.ButtonType.CANCEL);
        if (dialog.showAndWait().orElse(javafx.scene.control.ButtonType.CANCEL) != choose) return;
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Chọn ảnh QR chuyển khoản");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Ảnh PNG/JPG", "*.png", "*.jpg", "*.jpeg"));
        java.io.File file = chooser.showOpenDialog(null);
        if (file == null) return;
        String text = info.getText();
        java.nio.file.Path source = file.toPath();
        Async.run(() -> com.coffeeshop.coffeeshopmanagement.util.TransferQrGuard.replace(
                        com.coffeeshop.coffeeshopmanagement.util.DataPaths.dataDir(), source, text),
                stored -> AlertUtil.info("Đã lưu", "Đã đặt ảnh QR chuyển khoản mới. Hãy quét thử bằng điện thoại và kiểm tra "
                        + "tên chủ tài khoản trước khi dùng."),
                error -> AlertUtil.warning("Không đặt được ảnh QR", String.valueOf(error.getMessage())));
    }

    /** Opens phpMyAdmin in the browser. Editing there skips the app's checks, so it asks first (and is admin-only). */
    @FXML
    public void handleOpenPhpMyAdmin() {
        if (!requireAdmin()) return;
        boolean ok = AlertUtil.confirm("Mở phpMyAdmin",
                "phpMyAdmin sửa dữ liệu trực tiếp, không qua các kiểm tra của ứng dụng (tồn kho, điểm tích lũy, tổng tiền đơn...). "
                        + "Nên sao lưu trước khi sửa và đừng sửa đơn đã thanh toán.\n\nMở phpMyAdmin bây giờ?");
        if (!ok) return;
        String url = com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig.phpMyAdminUrl();
        javafx.application.HostServices services = com.coffeeshop.coffeeshopmanagement.LunaveraApplication.hostServices();
        try {
            if (services == null) {
                throw new IllegalStateException("no host services");
            }
            services.showDocument(url);
        } catch (RuntimeException e) {
            AlertUtil.warning("Không mở được trình duyệt", "Hãy tự mở địa chỉ này trong trình duyệt:\n" + url);
        }
    }

    // =================================================================== Dashboard

    private void loadDashboardStats() {
        loadDashboardStats(false);
    }

    /** {@code quiet}: the once-a-minute refresh must not pop an error box every minute while the database is busy. */
    private void loadDashboardStats(boolean quiet) {
        // loadStats() makes ~10 sequential DB round-trips - fine on their own, but enough to
        // cause a visible freeze if run on the FX thread during screen load. Load in the
        // background and populate labels once the result comes back.
        Async.run(
                dashboardStatsService::loadStats,
                this::applyDashboardStats,
                error -> {
                    if (!quiet) AlertUtil.error("Lỗi tải dữ liệu", "Không thể tải số liệu thống kê: " + error.getMessage());
                }
        );
    }

    private void applyDashboardStats(DashboardStats stats) {
        if (todayOrderLabel != null) todayOrderLabel.setText(String.valueOf(stats.todayOrders()));
        if (todayRevenueLabel != null) todayRevenueLabel.setText(CurrencyUtil.format(stats.todayRevenue()));
        if (todayCustomerLabel != null) todayCustomerLabel.setText(String.valueOf(stats.todayCustomers()));
        if (paidOrderLabel != null) paidOrderLabel.setText(String.valueOf(stats.paidOrders()));
        if (openOrderLabel != null) openOrderLabel.setText(String.valueOf(stats.openOrders()));
        if (staleOrderLabel != null) {
            String warning = DashboardStatsService.combinedWarning(stats.staleOpenOrders(), stats.heldTablesWithoutOrder(),
                    stats.sharedCustomerPhones());
            staleOrderLabel.setText(warning == null ? "" : warning);
            staleOrderLabel.setVisible(warning != null);
            staleOrderLabel.setManaged(warning != null);
        }
        if (cancelledOrderLabel != null) cancelledOrderLabel.setText(String.valueOf(stats.cancelledOrders()));

        if (revenueChart != null) {
            XYChart.Series<String, Number> series = new XYChart.Series<>();
            series.setName("Doanh thu");
            DateTimeFormatter dayLabel = DateTimeFormatter.ofPattern("dd/MM");
            for (Map.Entry<java.time.LocalDate, BigDecimal> entry : stats.revenueLast7Days().entrySet()) {
                series.getData().add(new XYChart.Data<>(entry.getKey().format(dayLabel), entry.getValue()));
            }
            revenueChart.getData().setAll(series);
        }
        // The dashboard cards show real data: product count, low stock and recent orders.
        if (dashboardProductCountLabel != null) {
            dashboardProductCountLabel.setText(String.valueOf(stats.totalProducts()));
        }
        if (dashboardLowStockNoteLabel != null) {
            dashboardLowStockNoteLabel.setText(stats.lowStockCount() == 0
                    ? "Không có sản phẩm sắp hết hàng"
                    : "Cần nhập thêm: " + stats.lowStockCount() + " sản phẩm");
        }
        if (todayOrdersContainer != null) {
            DashboardWidgets.fillTodayOrders(todayOrdersContainer, stats.todayOrdersList());
        }
        loadHomeTableGrid();
    }

    private void loadHomeTableGrid() {
        if (homeTableGrid == null) return;
        Async.run(
                tableDAO::findAll,
                tables -> DashboardWidgets.fillTableGrid(homeTableGrid, tables, this::handleHomeTableClick),
                error -> AlertUtil.error("Lỗi", "Không thể tải sơ đồ bàn: " + error.getMessage())
        );
    }

    private void handleHomeTableClick(DiningTable table) {
        if (homeTableGrid == null || homeTableGrid.getScene() == null) return;
        javafx.stage.Stage stage = (javafx.stage.Stage) homeTableGrid.getScene().getWindow();
        OrderController controller = SceneNavigator.switchSceneAndGetController(stage, "/fxml/quanlydonhang.fxml");
        if (controller != null) {
            controller.selectTable(table.getTableNumber());
        }
    }
}
