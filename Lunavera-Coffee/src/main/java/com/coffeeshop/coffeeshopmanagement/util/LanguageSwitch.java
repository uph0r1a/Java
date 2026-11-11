package com.coffeeshop.coffeeshopmanagement.util;

import javafx.stage.Stage;

import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * The language switch inside the app (TODO 1.7), for every screen after sign-in; the login screen has its own animated pill
 * ({@code LoginController}) because nothing can be lost there.
 *
 * <p>Changing the language rebuilds the screen, because the text of a screen is filled in when it is loaded. So a screen that
 * holds work which is not saved ({@link ScreenWork}: the till with a cart or a typed customer) is asked first; if something
 * would be lost, the person confirms, and answering "no" changes nothing at all (the language is not even remembered). What can
 * be picked again (the selected table) is handed to the rebuilt screen. {@link #change(String, ScreenWork, Predicate, Supplier,
 * Predicate)} is the whole decision without JavaFX.
 */
public final class LanguageSwitch {

    private LanguageSwitch() {
    }

    /** Switches the window's screen to the language {@code code}. Returns true when the language was changed. */
    public static boolean change(Stage stage, String code) {
        if (stage == null) {
            return false;
        }
        boolean changed = change(code, SceneNavigator.workOf(stage), LanguageSwitch::confirmLoss,
                () -> SceneNavigator.reloadCurrent(stage), LanguageSetting::choose);
        if (changed) {
            // Windows opened from the screen (history, reports, audit log) were filled in the old language; the receipt is always English.
            SceneNavigator.closeLanguageBoundWindows(stage);
        }
        return changed;
    }

    /**
     * The decision: nothing happens for the language already in use or one the app lacks; a screen with work at risk must be
     * allowed by {@code mayLose} first; then the language is chosen (and remembered), the screen rebuilt by {@code reload}
     * (which returns the new controller) and, when the old one left a snapshot, the new one resumes from it.
     */
    static boolean change(String code, ScreenWork work, Predicate<List<String>> mayLose, Supplier<Object> reload,
                          Predicate<String> choose) {
        if (code == null || code.equals(I18n.current()) || !I18n.LANGUAGES.contains(code)) {
            return false;
        }
        Object snapshot = null;
        if (work != null) {
            List<String> lost = work.unsavedWork();
            if (!lost.isEmpty() && !mayLose.test(lost)) {
                return false;
            }
            snapshot = work.snapshot();
        }
        if (!choose.test(code)) {
            return false;
        }
        Object rebuilt = reload.get();
        if (snapshot != null && rebuilt instanceof ScreenWork next) {
            next.resume(snapshot);
        }
        return true;
    }

    /** The text of the question: what would be lost, one line each. The wording is in the language still in use. */
    static String lossMessage(List<String> lost) {
        StringBuilder text = new StringBuilder(I18n.t("lang.unsaved.intro"));
        for (String line : lost) {
            text.append("\n  \u2022 ").append(line);
        }
        return text.append("\n\n").append(I18n.t("lang.unsaved.question")).toString();
    }

    private static boolean confirmLoss(List<String> lost) {
        return AlertUtil.confirm(I18n.t("lang.unsaved.title"), lossMessage(lost));
    }
}
