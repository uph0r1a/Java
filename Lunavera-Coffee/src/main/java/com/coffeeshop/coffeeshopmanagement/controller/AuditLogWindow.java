package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.service.AuditService;
import com.coffeeshop.coffeeshopmanagement.dao.AuditDAO;
import com.coffeeshop.coffeeshopmanagement.model.AuditAction;
import com.coffeeshop.coffeeshopmanagement.model.AuditEntry;
import com.coffeeshop.coffeeshopmanagement.service.AuditText;
import com.coffeeshop.coffeeshopmanagement.util.AlertUtil;
import com.coffeeshop.coffeeshopmanagement.util.AppClock;
import com.coffeeshop.coffeeshopmanagement.util.AppIcon;
import com.coffeeshop.coffeeshopmanagement.util.Async;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.PageBar;
import com.coffeeshop.coffeeshopmanagement.util.SceneNavigator;
import com.coffeeshop.coffeeshopmanagement.util.Session;
import com.coffeeshop.coffeeshopmanagement.util.TextLengthLimiter;
import com.coffeeshop.coffeeshopmanagement.util.StageSize;
import javafx.animation.PauseTransition;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.function.Function;

/**
 * The administrator's screen for the audit log (TODO 4.2): who did what and when, and what it was before and after, with a filter by
 * day, kind of action, person and a word (an order or product number, a name, a reason). Read-only: nothing here can change or delete a
 * line. Opened from the sidebar button that {@link SidebarExtras} adds for administrators; one window at a time.
 */
public final class AuditLogWindow {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static Stage instance;

    private final AuditService auditService = new AuditService();
    private final DatePicker fromPicker = com.coffeeshop.coffeeshopmanagement.util.DateBoxes.picker(AppClock.today().minusDays(30));
    private final DatePicker toPicker = com.coffeeshop.coffeeshopmanagement.util.DateBoxes.picker(AppClock.today());
    private final ComboBox<String> actionBox = new ComboBox<>();
    private final TextField actorField = new TextField();
    private final TextField textField = new TextField();
    private final TableView<AuditEntry> table = new TableView<>();
    private final Label countLabel = new Label();
    private final PageBar pageBar = new PageBar(50, 50, 100, 200).remember("audit.pageSize");
    private final PauseTransition typingPause = new PauseTransition(Duration.millis(300));
    private int requestCounter = 0;

    private AuditLogWindow() {
    }

    /** Opens the window (or brings the open one to the front). Administrators only. */
    public static void show() {
        if (!Session.isAdmin()) {
            AlertUtil.warning(I18n.t("admin.only.title"), I18n.t("audit.adminonly"));
            return;
        }
        if (instance != null && instance.isShowing()) {
            instance.toFront();
            return;
        }
        new AuditLogWindow().open();
    }

    private void open() {
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Label(I18n.t("audit.empty")));
        table.getColumns().add(column(I18n.t("audit.col.time"), 130, e -> TIME.format(e.loggedAt())));
        table.getColumns().add(column(I18n.t("audit.col.who"), 120, e -> e.actorName()));
        table.getColumns().add(column(I18n.t("audit.col.action"), 170, AuditText::action));
        table.getColumns().add(column(I18n.t("audit.col.target"), 170, AuditText::target));
        table.getColumns().add(column(I18n.t("audit.col.change"), 170, AuditText::change));
        table.getColumns().add(column(I18n.t("audit.col.detail"), 200, AuditText::detail));

        actionBox.getItems().add(I18n.t("audit.filter.allactions"));
        for (AuditAction action : AuditAction.values()) {
            actionBox.getItems().add(I18n.t(action.labelKey()));
        }
        actionBox.getSelectionModel().selectFirst();
        actionBox.setOnAction(e -> resetAndReload());
        actorField.setPromptText(I18n.t("audit.filter.who"));
        textField.setPromptText(I18n.t("audit.filter.text"));
        TextLengthLimiter.limit(actorField, TextLengthLimiter.SEARCH_MAX);
        TextLengthLimiter.limit(textField, TextLengthLimiter.SEARCH_MAX);
        typingPause.setOnFinished(e -> resetAndReload());
        actorField.textProperty().addListener((o, a, b) -> typingPause.playFromStart());
        textField.textProperty().addListener((o, a, b) -> typingPause.playFromStart());
        fromPicker.setOnAction(e -> resetAndReload());
        toPicker.setOnAction(e -> resetAndReload());
        Button clear = new Button(I18n.t("audit.filter.clear"));
        clear.setOnAction(e -> {
            fromPicker.setValue(null);
            toPicker.setValue(null);
            actionBox.getSelectionModel().selectFirst();
            actorField.clear();
            textField.clear();
            resetAndReload();
        });
        pageBar.setOnChange(this::reload);

        HBox filters = new HBox(8, new Label(I18n.t("audit.filter.from")), fromPicker, new Label(I18n.t("audit.filter.to")), toPicker,
                actionBox, actorField, textField, clear);
        filters.setAlignment(Pos.CENTER_LEFT);
        HBox bottom = new HBox(10, countLabel, new Region(), pageBar);
        bottom.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(bottom.getChildren().get(1), Priority.ALWAYS);
        VBox root = new VBox(10, filters, table, bottom);
        VBox.setVgrow(table, Priority.ALWAYS);
        root.setPadding(new Insets(14));

        Stage stage = SceneNavigator.newOwnedStage();
        instance = stage;
        stage.setTitle(I18n.t("audit.title"));
        AppIcon.apply(stage);
        stage.setScene(new Scene(root, 1100, 620));
        StageSize.remember(stage, "audit.window", 1100, 620);
        AlertUtil.closeOnEscape(stage.getScene());
        stage.show();
        reload();
    }

    private <T> TableColumn<T, String> column(String title, double width, Function<T, String> text) {
        TableColumn<T, String> column = new TableColumn<>(title);
        column.setPrefWidth(width);
        column.setSortable(false); // the log is always newest first; sorting a page would lie about the order
        column.setCellValueFactory(data -> new SimpleStringProperty(text.apply(data.getValue())));
        return column;
    }

    private void resetAndReload() {
        pageBar.setCurrentPage(1);
        reload();
    }

    /** The filter as it is on screen; the action is the one at the same position in the list, 0 meaning "every action". */
    private AuditDAO.Filter currentFilter() {
        int chosen = actionBox.getSelectionModel().getSelectedIndex();
        AuditAction action = chosen > 0 ? AuditAction.values()[chosen - 1] : null;
        return new AuditDAO.Filter(fromPicker.getValue(), toPicker.getValue(), action, actorField.getText(), textField.getText());
    }

    private void reload() {
        AuditDAO.Filter filter = currentFilter();
        if (filter.hasReversedDates()) {
            countLabel.setText(I18n.t("audit.dates.reversed"));
            table.getItems().clear();
            pageBar.setTotalItems(0);
            return;
        }
        int myRequest = ++requestCounter;
        int pageSize = pageBar.getPageSize();
        int page = pageBar.getCurrentPage();
        countLabel.setText(I18n.t("common.loading"));
        Async.run(
                () -> new Object[]{auditService.find(filter, pageSize, (int) Math.min(Integer.MAX_VALUE, (long) (page - 1) * pageSize)),
                        auditService.count(filter)},
                result -> {
                    if (myRequest != requestCounter) return; // a newer query was started: this answer is out of date
                    @SuppressWarnings("unchecked")
                    java.util.List<AuditEntry> rows = (java.util.List<AuditEntry>) result[0];
                    int total = (Integer) result[1];
                    table.getItems().setAll(rows);
                    countLabel.setText(I18n.t("audit.count", total));
                    pageBar.setTotalItems(total);
                },
                error -> {
                    if (myRequest != requestCounter) return;
                    countLabel.setText("");
                    AlertUtil.error(I18n.t("audit.title"), error.getMessage());
                });
    }
}
