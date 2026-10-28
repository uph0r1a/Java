package com.coffeeshop.coffeeshopmanagement.util;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Labeled;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.List;

/**
 * Locking the till (TODO 4.36). A locked till keeps everything it shows: the screen is not closed, so the cart and the open
 * order stay exactly as they were; every window's content is only hidden and disabled until the lock screen has been answered. The
 * person stays signed in (the {@link Session} is not cleared), which is why a PIN can be accepted while it is locked and never
 * otherwise (see {@code PinService}). The lock screen itself is {@code LockScreen}, handed in with {@link #setPresenter}, so this
 * class has no dependency on the screens.
 *
 * <p>The idle timeout locks instead of signing out when it can ({@link #lockForIdle}); a till that stays locked for another idle
 * period is signed out as before, so a forgotten till does not stay open for ever.
 */
public final class TillLock {

    private static volatile boolean locked;
    private static Runnable presenter;

    private TillLock() {
    }

    /** The lock screen: it blocks until the till is opened, or until the session ended under it. */
    public static void setPresenter(Runnable lockScreen) {
        presenter = lockScreen;
    }

    public static boolean isLocked() {
        return locked;
    }

    /** True when somebody is signed in, no lock is showing and there is a lock screen to show. */
    public static boolean canLock() {
        return presenter != null && Session.getCurrentUser() != null && !locked;
    }

    /** Locks the till and returns after it was opened (or the session ended). False when it could not be locked. */
    public static boolean lock() {
        if (!canLock()) return false;
        locked = true;
        List<Runnable> restore = new ArrayList<>();
        try {
            for (Window window : new ArrayList<>(Window.getWindows())) {
                if (!window.isShowing() || window.getScene() == null || window.getScene().getRoot() == null) continue;
                Parent root = window.getScene().getRoot();
                boolean wasVisible = root.isVisible();
                boolean wasDisabled = root.isDisable();
                root.setVisible(false);
                root.setDisable(true);
                restore.add(() -> {
                    root.setVisible(wasVisible);
                    root.setDisable(wasDisabled);
                });
            }
            presenter.run();
        } finally {
            locked = false;
            restore.forEach(Runnable::run);
        }
        return true;
    }

    /** What the idle timeout does: lock when it can. False means "sign out as before" (nothing to lock, or already locked). */
    public static boolean lockForIdle() {
        return lock();
    }

    /** After another person took over: the name shown in the sidebars of every window is the new one's. */
    public static void refreshNames() {
        String name = Session.getDisplayName();
        for (Window window : Window.getWindows()) {
            if (window.getScene() == null || window.getScene().getRoot() == null) continue;
            for (String style : List.of(".account-name", ".admin-name")) {
                for (Node node : window.getScene().getRoot().lookupAll(style)) {
                    if (node instanceof Labeled labeled) labeled.setText(name);
                }
            }
        }
    }
}
