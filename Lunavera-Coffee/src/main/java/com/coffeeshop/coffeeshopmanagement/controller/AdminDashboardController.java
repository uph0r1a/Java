package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.util.I18n;
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
import java.util.ArrayList;
import java.util.List;
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
    @FXML private VBox databaseHealthBox;
    @FXML private Label databaseHealthLabel;
    @FXML private Label databaseHealthWarningLabel;
    @FXML private Label secondBackupLabel;
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
        showSetupWizardOrMenuNoticeOnce();
        loadDatabaseHealth();
        loadHomeTableGrid();
        if (todayOrderLabel != null || todayRevenueLabel != null) {
            loadDashboardStats();
            // The numbers follow the till without leaving the screen (TODO 5.3).
            com.coffeeshop.coffeeshopmanagement.util.AutoRefresh.every(
                    todayOrderLabel != null ? todayOrderLabel : todayRevenueLabel,
                    javafx.util.Duration.seconds(60), () -> loadDashboardStats(true));
        }
        if (dashboardGreetingLabel != null) {
            dashboardGreetingLabel.setText(I18n.t("dashboard.greeting", Session.getDisplayName()));
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
            // Start-up warning (2A.17, 2A.34): is the transfer QR picture one an administrator accepted?
            Async.run(() -> com.coffeeshop.coffeeshopmanagement.util.TransferQrGuard.dataPicture()
                            .map(com.coffeeshop.coffeeshopmanagement.util.TransferQrGuard::inspect).orElse(null),
                    state -> {
                        if (state == null || !state.needsAttention()) return;
                        switch (state) {
                            case CHANGED -> AlertUtil.warning(I18n.t("admin.qr.changed.title"),
                                    I18n.t("admin.qr.changed.message"));
                            case UNCONFIRMED -> AlertUtil.warning(I18n.t("admin.qr.unconfirmed.title"),
                                    I18n.t("admin.qr.unconfirmed.message"));
                            default -> AlertUtil.warning(I18n.t("admin.qr.unverifiable.title"),
                                    I18n.t("admin.qr.unverifiable.message"));
                        }
                    },
                    error -> { });
        }
    }

    /** The size / last backup / schema line (TODO 4.24), for an administrator only; read in the background. */
    private void loadDatabaseHealth() {
        if (databaseHealthBox == null || !Session.isAdmin()) return;
        Async.run(com.coffeeshop.coffeeshopmanagement.config.DatabaseHealth::read,
                health -> {
                    java.time.Instant now = java.time.Instant.now();
                    databaseHealthLabel.setText(com.coffeeshop.coffeeshopmanagement.config.DatabaseHealth.describe(health, now, java.time.ZoneId.systemDefault()));
                    String warning = com.coffeeshop.coffeeshopmanagement.config.DatabaseHealth.warning(health, now);
                    databaseHealthWarningLabel.setText(warning == null ? "" : warning);
                    databaseHealthWarningLabel.setVisible(warning != null);
                    databaseHealthWarningLabel.setManaged(warning != null);
                    showSecondBackupStatus();
                    databaseHealthBox.setVisible(true);
                    databaseHealthBox.setManaged(true);
                },
                error -> java.util.logging.Logger.getLogger(AdminDashboardController.class.getName()).log(java.util.logging.Level.INFO, "The database health line could not be read", error));
    }

    /** "Last copy outside the PC: N days ago", in the warning colour after 3 days or after a failed copy (TODO 4.35). */
    private void showSecondBackupStatus() {
        if (secondBackupLabel == null) return;
        com.coffeeshop.coffeeshopmanagement.service.SecondBackupFolder.Status status =
                com.coffeeshop.coffeeshopmanagement.service.SecondBackupFolder.status();
        java.time.Instant now = java.time.Instant.now();
        secondBackupLabel.setText(com.coffeeshop.coffeeshopmanagement.service.SecondBackupFolder.describe(status, now));
        boolean attention = com.coffeeshop.coffeeshopmanagement.service.SecondBackupFolder.needsAttention(status, now);
        secondBackupLabel.getStyleClass().setAll(attention ? "stale-order-warning" : "db-health-line");
    }

    /** Choose, change or switch off the second folder for the automatic backups (TODO 4.35); a setting of this till. */
    @FXML
    public void handleSecondBackupFolder() {
        if (!requireAdmin()) return;
        java.nio.file.Path current = com.coffeeshop.coffeeshopmanagement.service.SecondBackupFolder.configured();
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(I18n.t("backup2.title"));
        dialog.setHeaderText(I18n.t("backup2.intro"));
        Label where = new Label(current == null ? I18n.t("backup2.state.notset") : I18n.t("backup2.current", current));
        where.setWrapText(true);
        where.setPrefWidth(460);
        dialog.getDialogPane().setContent(where);
        ButtonType choose = new ButtonType(I18n.t("backup2.choose"), ButtonData.OK_DONE);
        ButtonType off = new ButtonType(I18n.t("backup2.off"), ButtonData.LEFT);
        dialog.getDialogPane().getButtonTypes().add(choose);
        if (current != null) dialog.getDialogPane().getButtonTypes().add(off);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        java.util.Optional<ButtonType> answer = dialog.showAndWait();
        if (answer.isEmpty()) return;
        if (answer.get() == off) {
            com.coffeeshop.coffeeshopmanagement.service.SecondBackupFolder.configure(null);
            showSecondBackupStatus();
        } else if (answer.get() == choose) {
            javafx.stage.DirectoryChooser chooser = new javafx.stage.DirectoryChooser();
            chooser.setTitle(I18n.t("backup2.choose.title"));
            java.io.File start = com.coffeeshop.coffeeshopmanagement.util.RememberedFolder.startFor("backup2",
                    current == null ? null : current.toFile());
            if (start != null) chooser.setInitialDirectory(start);
            java.io.File picked = chooser.showDialog(backupButton.getScene().getWindow());
            if (picked == null) return;
            String problem = com.coffeeshop.coffeeshopmanagement.service.SecondBackupFolder.problem(
                    picked.toPath(), com.coffeeshop.coffeeshopmanagement.util.DataPaths.dataDir());
            if (problem != null) {
                AlertUtil.warning(I18n.t("backup2.title"), I18n.t(problem, picked.getAbsolutePath()));
                return;
            }
            com.coffeeshop.coffeeshopmanagement.util.RememberedFolder.remember("backup2", picked);
            com.coffeeshop.coffeeshopmanagement.service.SecondBackupFolder.configure(picked.toPath());
            // copy the newest backup now, so the owner sees it work (and a failure) at once
            Async.run(com.coffeeshop.coffeeshopmanagement.service.SecondBackupFolder::catchUp,
                    copied -> showSecondBackupStatus(), error -> showSecondBackupStatus());
            showSecondBackupStatus();
        }
    }

    @FXML
    public void handleBackupData() {
        if (!requireAdmin()) return;
        // The dialogs and the copy live in BackupDialogs (TODO 5.17, 5.18), shared with the reminder at log-out and close.
        BackupDialogs.run(backupButton.getScene().getWindow(), this::setBackupBusy,
                this::loadDatabaseHealth, null); // the new backup is the "last backup" now
    }

    /** What puts the backup button back; null while no backup runs. */
    private Runnable releaseBackupBusy;

    /** The button shows a spinner and "Đang sao lưu...", and the pointer waits, while the copy is made (TODO 5.16). */
    private void setBackupBusy(boolean busy) {
        if (busy) {
            if (releaseBackupBusy == null) {
                releaseBackupBusy = com.coffeeshop.coffeeshopmanagement.util.BusyIndicator.hold(backupButton, I18n.t("busy.backup"));
            }
        } else if (releaseBackupBusy != null) {
            releaseBackupBusy.run();
            releaseBackupBusy = null;
        }
    }

    private void setupPendingRestoreBanner() {
        if (staleOrderLabel == null || !(staleOrderLabel.getParent() instanceof javafx.scene.layout.VBox box)) return;
        pendingRestoreText = new Label();
        pendingRestoreText.setWrapText(true);
        pendingRestoreText.getStyleClass().add("stale-order-warning");
        javafx.scene.layout.HBox.setHgrow(pendingRestoreText, javafx.scene.layout.Priority.ALWAYS);
        Button cancel = new Button(I18n.t("admin.restore.cancel.title"));
        cancel.setOnAction(e -> {
            if (AlertUtil.confirm(I18n.t("admin.restore.cancel.title"), I18n.t("admin.restore.cancel.message"))) {
                BackupService.cancelPendingRestore();
                refreshPendingRestoreBanner();
            }
        });
        pendingRestoreBanner = new javafx.scene.layout.VBox(6, pendingRestoreText, cancel);
        box.getChildren().add(Math.max(0, box.getChildren().indexOf(staleOrderLabel)), pendingRestoreBanner);
        refreshPendingRestoreBanner();
    }

    /**
     * On the first administrator login of a new shop the setup window comes first (shop name, tables, review of the starter menu,
     * TODO 4.15); in every other case the old one-time reminder about the starter menu is used (TODO 2.20).
     */
    private void showSetupWizardOrMenuNoticeOnce() {
        if (staleOrderLabel == null || !Session.isAdmin()) return; // only on the admin home screen
        Async.run(
                () -> {
                    if (!com.coffeeshop.coffeeshopmanagement.config.AppFlags.has(
                            com.coffeeshop.coffeeshopmanagement.config.AppFlags.SETUP_PENDING)) {
                        return null;
                    }
                    List<Integer> numbers = new ArrayList<>();
                    new com.coffeeshop.coffeeshopmanagement.dao.TableDAO().findAll().forEach(t -> numbers.add(t.getTableNumber()));
                    return numbers;
                },
                numbers -> {
                    javafx.stage.Window window = staleOrderLabel.getScene() == null ? null : staleOrderLabel.getScene().getWindow();
                    if (numbers == null || window == null) {
                        showMenuReviewNoticeOnce();
                        return;
                    }
                    SetupWizard.show(window, numbers, result -> {
                        if (!result.saved()) return;
                        if (window instanceof javafx.stage.Stage stage) {
                            stage.setTitle(com.coffeeshop.coffeeshopmanagement.config.ShopSettings.cachedName());
                            if (result.openProducts()) {
                                SceneNavigator.switchScene(stage, "/fxml/quanlysanpham.fxml");
                                return;
                            }
                        }
                        loadHomeTableGrid();
                    });
                },
                error -> showMenuReviewNoticeOnce());
    }

    /** One-time reminder after the starter menu was added: its prices and stock are placeholders (TODO 2.20). */
    private void showMenuReviewNoticeOnce() {
        if (staleOrderLabel == null || !Session.isAdmin()) return; // only on the admin home screen
        Async.run(
                () -> com.coffeeshop.coffeeshopmanagement.config.AppFlags.has(
                        com.coffeeshop.coffeeshopmanagement.config.AppFlags.REVIEW_MENU_NOTICE),
                pending -> {
                    if (!pending) return;
                    AlertUtil.info(I18n.t("admin.sample.title"),
                            I18n.t("admin.sample.message"));
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
        since.ifPresent(t -> pendingRestoreText.setText(I18n.t("admin.restore.pending", t.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy")))));
    }

    @FXML
    public void handleRestoreData() {
        if (!requireAdmin()) return;
        if (!com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig.isSqlite()) {
            AlertUtil.info(I18n.t("admin.restore.title"), BackupService.mariaDbRestoreHelp());
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle(I18n.t("admin.restore.choose"));
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter(I18n.t("admin.restore.filter"), "*.zip", "*" + BackupService.ENCRYPTED_EXTENSION, "*.db"));
        java.io.File start = com.coffeeshop.coffeeshopmanagement.util.DataPaths.backupsDir().toFile();
        if (start.isDirectory()) {
            chooser.setInitialDirectory(start);
        }
        java.io.File file = chooser.showOpenDialog(backupButton.getScene().getWindow());
        if (file == null) return;
        // A backup locked with a passphrase (TODO 2A.4) asks for it first; a plain one passes null.
        final char[] passphrase;
        if (BackupService.isEncryptedBackup(file.toPath())) {
            java.util.Optional<char[]> asked = BackupPassphraseDialog.askToOpen(file.getName());
            if (asked.isEmpty()) return;
            passphrase = asked.get();
        } else {
            passphrase = null;
        }
        // Check the file and show what it holds before anything is asked or parked (TODO 5.22).
        Async.run(
                () -> BackupService.inspect(file.toPath(), passphrase),
                summary -> {
                    if (!AlertUtil.confirm(I18n.t("admin.restore.title"),
                            I18n.t("admin.restore.confirm", file.getName(), summary.describe()))) {
                        return;
                    }
                    Async.run(
                            () -> {
                                try {
                                    BackupService.scheduleRestore(file.toPath(), passphrase);
                                    return file;
                                } finally {
                                    if (passphrase != null) java.util.Arrays.fill(passphrase, '\0');
                                }
                            },
                            picked -> {
                                refreshPendingRestoreBanner();
                                if (AlertUtil.confirm(I18n.t("admin.restore.ready.title"),
                                        I18n.t("admin.restore.ready.message"))) {
                                    javafx.application.Platform.exit();
                                } else {
                                    AlertUtil.info(I18n.t("admin.restore.waiting.title"),
                                            I18n.t("admin.restore.waiting.message"));
                                }
                            },
                            error -> AlertUtil.error(I18n.t("admin.restore.fail"), error.getMessage()),
                            restoreButton);
                },
                error -> AlertUtil.error(I18n.t("admin.restore.fail"), error.getMessage()),
                restoreButton // a second click must not start a second look at the file (TODO 2.75)
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
        info.setPromptText(I18n.t("admin.qr.holder.prompt"));
        info.setPrefRowCount(3);
        var current = com.coffeeshop.coffeeshopmanagement.util.TransferQrGuard.dataPicture();
        current.ifPresent(p -> info.setText(com.coffeeshop.coffeeshopmanagement.util.TransferQrGuard.accountInfo(p)));
        javafx.scene.control.Dialog<javafx.scene.control.ButtonType> dialog = new javafx.scene.control.Dialog<>();
        dialog.setTitle(I18n.t("admin.qr.title"));
        dialog.setHeaderText(I18n.t("admin.qr.intro"));
        dialog.getDialogPane().setContent(info);
        javafx.scene.control.ButtonType choose = new javafx.scene.control.ButtonType(I18n.t("common.pickimage"),
                javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        // A second way out (TODO 5.32): fix the holder or the bank without choosing the picture again.
        javafx.scene.control.ButtonType textOnly = new javafx.scene.control.ButtonType(I18n.t("admin.qr.textonly"),
                javafx.scene.control.ButtonBar.ButtonData.APPLY);
        dialog.getDialogPane().getButtonTypes().addAll(choose, textOnly, javafx.scene.control.ButtonType.CANCEL);
        javafx.scene.Node textOnlyButton = dialog.getDialogPane().lookupButton(textOnly);
        if (textOnlyButton != null) textOnlyButton.setDisable(current.isEmpty()); // no picture yet: nothing to keep
        java.nio.file.Path source;
        while (true) {
            javafx.scene.control.ButtonType answer = dialog.showAndWait().orElse(javafx.scene.control.ButtonType.CANCEL);
            if (answer == textOnly) {
                String typed = info.getText();
                Async.run(() -> {
                            com.coffeeshop.coffeeshopmanagement.util.TransferQrGuard.replaceInfo(
                                    com.coffeeshop.coffeeshopmanagement.util.DataPaths.dataDir(), typed);
                            return Boolean.TRUE;
                        },
                        saved -> AlertUtil.info(I18n.t("common.saved"), I18n.t("admin.qr.textonly.saved")),
                        error -> AlertUtil.warning(I18n.t("admin.qr.fail"), String.valueOf(error.getMessage())));
                return;
            }
            if (answer != choose) return;
            FileChooser chooser = new FileChooser();
            chooser.setTitle(I18n.t("admin.qr.choose"));
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(I18n.t("common.image.filter"), "*.png", "*.jpg", "*.jpeg"));
            java.io.File file = chooser.showOpenDialog(null);
            if (file != null) {
                source = file.toPath();
                break;
            }
            // The file chooser was cancelled: back to the dialog with the typed text still in it, not a lost text.
        }
        String text = info.getText();
        java.nio.file.Path chosen = source; // the loop above assigns it, so a lambda may not capture it directly
        Async.run(() -> com.coffeeshop.coffeeshopmanagement.util.TransferQrGuard.replace(
                        com.coffeeshop.coffeeshopmanagement.util.DataPaths.dataDir(), chosen, text),
                stored -> AlertUtil.info(I18n.t("common.saved"), I18n.t("admin.qr.saved")),
                error -> AlertUtil.warning(I18n.t("admin.qr.fail"), String.valueOf(error.getMessage())));
    }

    // =================================================================== Security log (TODO 4.44)

    /** A read-only list of the newest security events, so the owner need not open a text file in a folder. */
    @FXML
    public void handleShowSecurityLog() {
        if (!requireAdmin()) return;
        Async.run(() -> com.coffeeshop.coffeeshopmanagement.util.SecurityLogReader.recent(
                        com.coffeeshop.coffeeshopmanagement.util.DataPaths.logsDir(), java.time.YearMonth.now(),
                        com.coffeeshop.coffeeshopmanagement.util.SecurityLogReader.MAX_LINES,
                        com.coffeeshop.coffeeshopmanagement.util.SecurityLogReader.MAX_BYTES),
                this::showSecurityLog,
                error -> AlertUtil.warning(I18n.t("admin.seclog.title"), I18n.t("admin.seclog.fail", String.valueOf(error.getMessage()))));
    }

    private void showSecurityLog(java.util.List<com.coffeeshop.coffeeshopmanagement.util.SecurityLogReader.Entry> entries) {
        javafx.scene.control.TableView<com.coffeeshop.coffeeshopmanagement.util.SecurityLogReader.Entry> table =
                new javafx.scene.control.TableView<>(javafx.collections.FXCollections.observableArrayList(entries));
        javafx.scene.control.TableColumn<com.coffeeshop.coffeeshopmanagement.util.SecurityLogReader.Entry, String> time =
                new javafx.scene.control.TableColumn<>(I18n.t("admin.seclog.col.time"));
        time.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().time()));
        time.setPrefWidth(150);
        javafx.scene.control.TableColumn<com.coffeeshop.coffeeshopmanagement.util.SecurityLogReader.Entry, String> level =
                new javafx.scene.control.TableColumn<>(I18n.t("admin.seclog.col.level"));
        level.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().level()));
        level.setPrefWidth(90);
        javafx.scene.control.TableColumn<com.coffeeshop.coffeeshopmanagement.util.SecurityLogReader.Entry, String> text =
                new javafx.scene.control.TableColumn<>(I18n.t("admin.seclog.col.text"));
        text.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().text()));
        text.setPrefWidth(620);
        table.getColumns().add(time);
        table.getColumns().add(level);
        table.getColumns().add(text);
        table.setPlaceholder(new Label(I18n.t("admin.seclog.empty")));
        table.setPrefSize(900, 420);
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(I18n.t("admin.seclog.title"));
        dialog.setHeaderText(I18n.t("admin.seclog.intro", entries.size()));
        dialog.getDialogPane().setContent(table);
        ButtonType openFolder = new ButtonType(I18n.t("admin.seclog.openfolder"), ButtonData.LEFT);
        dialog.getDialogPane().getButtonTypes().addAll(openFolder, ButtonType.CLOSE);
        // The folder button must not close the list: its event is consumed after the folder is opened.
        dialog.getDialogPane().lookupButton(openFolder).addEventFilter(javafx.event.ActionEvent.ACTION, e -> {
            e.consume();
            openLogFolder();
        });
        dialog.showAndWait();
    }

    private void openLogFolder() {
        java.nio.file.Path folder = com.coffeeshop.coffeeshopmanagement.util.DataPaths.logsDir();
        try {
            java.nio.file.Files.createDirectories(folder);
            javafx.application.HostServices services = com.coffeeshop.coffeeshopmanagement.LunaveraApplication.hostServices();
            if (services == null) {
                throw new IllegalStateException("no host services");
            }
            services.showDocument(folder.toUri().toString());
        } catch (java.io.IOException | RuntimeException e) {
            AlertUtil.warning(I18n.t("admin.seclog.nofolder"), I18n.t("admin.seclog.openyourself", folder.toAbsolutePath()));
        }
    }

    // =================================================================== Unused pictures (TODO 4.27)

    /** The owner adds, renames and removes tables here (TODO 4.18); the map on the home page reloads afterwards. */
    @FXML
    public void handleManageTables() {
        if (!requireAdmin()) return;
        TableManagerDialog.show(this::loadHomeTableGrid);
    }

    /** The owner's settings window (TODO 4.7): shop name, address, phone, tables, low-stock level, bank details. */
    @FXML
    public void handleOpenSettings() {
        if (!requireAdmin()) return;
        SettingsDialog.show(this::loadHomeTableGrid, this::handleSetTransferQr, () -> {
            javafx.stage.Window window = staleOrderLabel.getScene() == null ? null : staleOrderLabel.getScene().getWindow();
            if (window instanceof javafx.stage.Stage stage) {
                stage.setTitle(com.coffeeshop.coffeeshopmanagement.config.ShopSettings.cachedName());
            }
            loadDashboardStats(); // the low-stock count and list follow the new level
        });
    }

    /** Lists the pictures no product uses, and deletes them after a confirmation and a backup. */
    @FXML
    public void handleCleanUnusedPictures() {
        if (!requireAdmin()) return;
        Async.run(com.coffeeshop.coffeeshopmanagement.service.UnusedPictures::scan,
                this::confirmPictureCleanup,
                error -> AlertUtil.warning(I18n.t("admin.pics.title"), I18n.t("admin.pics.fail", String.valueOf(error.getMessage()))));
    }

    private void confirmPictureCleanup(com.coffeeshop.coffeeshopmanagement.service.UnusedPictures.Report report) {
        if (report.isEmpty()) {
            AlertUtil.info(I18n.t("admin.pics.title"), I18n.t("admin.pics.none"));
            return;
        }
        StringBuilder list = new StringBuilder();
        int shown = 0;
        for (com.coffeeshop.coffeeshopmanagement.service.UnusedPictures.Picture picture : report.unused()) {
            if (shown++ >= 200) {
                list.append(I18n.t("admin.pics.more", report.unused().size() - 200));
                break;
            }
            list.append(picture.file().getFileName()).append("  (")
                    .append(com.coffeeshop.coffeeshopmanagement.util.FileSizeText.format(picture.bytes())).append(")\n");
        }
        TextArea files = new TextArea(list.toString());
        files.setEditable(false);
        files.setPrefRowCount(12);
        files.setPrefColumnCount(48);
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(I18n.t("admin.pics.title"));
        dialog.setHeaderText(I18n.t("admin.pics.header", report.unused().size(),
                com.coffeeshop.coffeeshopmanagement.util.FileSizeText.format(report.totalBytes())));
        VBox content = new VBox(8, new Label(I18n.t("admin.pics.warning")), files);
        dialog.getDialogPane().setContent(content);
        ButtonType delete = new ButtonType(I18n.t("admin.pics.delete"), ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(delete, ButtonType.CANCEL);
        if (dialog.showAndWait().orElse(ButtonType.CANCEL) != delete) return;
        // The backup comes first: if it fails nothing is deleted.
        Async.run(() -> {
                    com.coffeeshop.coffeeshopmanagement.service.BackupService.createBeforeCleanupBackup();
                    return com.coffeeshop.coffeeshopmanagement.service.UnusedPictures.deleteConfirmed(report);
                },
                result -> {
                    String message = I18n.t("admin.pics.done", result.deleted(),
                            com.coffeeshop.coffeeshopmanagement.util.FileSizeText.format(result.bytesFreed()));
                    if (result.failed() > 0) message += "\n" + I18n.t("admin.pics.done.failed", result.failed());
                    AlertUtil.info(I18n.t("admin.pics.title"), message);
                    loadDatabaseHealth();
                },
                error -> AlertUtil.warning(I18n.t("admin.pics.title"), I18n.t("admin.pics.fail", String.valueOf(error.getMessage()))));
    }

    /** Opens phpMyAdmin in the browser. Editing there skips the app's checks, so it asks first (and is admin-only). */
    @FXML
    public void handleOpenPhpMyAdmin() {
        if (!requireAdmin()) return;
        boolean ok = AlertUtil.confirm(I18n.t("admin.php.title"),
                I18n.t("admin.php.message"));
        if (!ok) return;
        String url = com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig.phpMyAdminUrl();
        javafx.application.HostServices services = com.coffeeshop.coffeeshopmanagement.LunaveraApplication.hostServices();
        try {
            if (services == null) {
                throw new IllegalStateException("no host services");
            }
            services.showDocument(url);
        } catch (RuntimeException e) {
            AlertUtil.warning(I18n.t("admin.php.nobrowser"), I18n.t("admin.php.openyourself", url));
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
                    if (!quiet) AlertUtil.error(I18n.t("dashboard.load.title"), I18n.t("dashboard.load.stats", error.getMessage()));
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
            series.setName(I18n.t("report.col.revenue"));
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
                    ? I18n.t("stats.lowstock.none")
                    : I18n.t("stats.lowstock.some", stats.lowStockCount()));
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
                error -> AlertUtil.error(I18n.t("common.error.title"), I18n.t("dashboard.load.tables", error.getMessage()))
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
