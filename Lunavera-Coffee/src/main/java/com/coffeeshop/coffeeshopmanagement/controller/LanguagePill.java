package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.LanguageSwitch;
import com.coffeeshop.coffeeshopmanagement.util.Session;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * The language switch inside the app (TODO 1.7): the same two-part pill as the login screen, under the logo of every sidebar.
 * It goes into the free room of the 150-pixel logo box, so the sidebar, which is already full of buttons, does not get
 * taller. Added once per screen by {@link SidebarExtras#addButtons}; {@link LanguageSwitch} does the switching and asks first
 * when the screen holds work that is not saved (the till).
 */
final class LanguagePill {

    static final String ID = "languagePill";
    /** Width of one language segment; the thumb under the chosen one moves by it. */
    static final double SEGMENT_WIDTH = 96.0;
    static final double HEIGHT = 26.0;

    private LanguagePill() {
    }

    /** Puts the pill under the logo of the sidebar in {@code root}; nothing happens on a screen without one (the login screen). */
    static void add(Parent root) {
        if (Session.getCurrentUser() == null) return;
        Node sidebar = root.lookup(".sidebar");
        if (!(sidebar instanceof Pane side) || side.lookup("#" + ID) != null) return;
        VBox logoBox = side.getChildren().stream()
                .filter(child -> child instanceof VBox box && box.getChildren().stream().anyMatch(c -> c instanceof ImageView))
                .map(child -> (VBox) child).findFirst().orElse(null);
        if (logoBox == null) return;

        Button vi = segment(I18n.VI);
        Button en = segment(I18n.EN);
        Region thumb = new Region();
        thumb.getStyleClass().add("sidebar-language-thumb");
        thumb.setMinSize(SEGMENT_WIDTH, HEIGHT);
        thumb.setPrefSize(SEGMENT_WIDTH, HEIGHT);
        thumb.setMaxSize(SEGMENT_WIDTH, HEIGHT);
        thumb.setMouseTransparent(true);
        boolean english = I18n.EN.equals(I18n.current());
        thumb.setTranslateX(english ? SEGMENT_WIDTH : 0);
        mark(vi, !english);
        mark(en, english);

        StackPane pill = new StackPane(thumb, new HBox(vi, en));
        pill.setId(ID);
        pill.getStyleClass().add("sidebar-language");
        pill.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        pill.setMaxSize(StackPane.USE_PREF_SIZE, StackPane.USE_PREF_SIZE);
        VBox.setMargin(pill, new Insets(6, 0, 0, 0));
        logoBox.getChildren().add(pill);
    }

    private static Button segment(String code) {
        Button button = new Button(I18n.in(code, "lang." + code));
        button.getStyleClass().add("sidebar-language-button");
        button.setFocusTraversable(false);
        button.setMinSize(SEGMENT_WIDTH, HEIGHT);
        button.setPrefSize(SEGMENT_WIDTH, HEIGHT);
        button.setMaxSize(SEGMENT_WIDTH, HEIGHT);
        button.setTooltip(new Tooltip(I18n.t("lang.tooltip")));
        button.setOnAction(e -> {
            if (button.getScene() != null && button.getScene().getWindow() instanceof Stage stage) {
                LanguageSwitch.change(stage, code);
            }
        });
        return button;
    }

    private static void mark(Button button, boolean active) {
        button.getStyleClass().remove("sidebar-language-active");
        if (active) button.getStyleClass().add("sidebar-language-active");
    }
}
