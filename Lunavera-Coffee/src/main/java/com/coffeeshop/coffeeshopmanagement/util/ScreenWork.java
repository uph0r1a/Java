package com.coffeeshop.coffeeshopmanagement.util;

import java.util.List;

/**
 * A screen that can hold work which is not saved yet (TODO 1.7). A controller that implements this tells
 * {@link LanguageSwitch} what a reload of its screen would lose, so the till asks first instead of wiping a cart or a typed
 * customer. {@link SceneNavigator} remembers the controller of the screen each window shows.
 *
 * <p>The screens that only list and search (products, accounts, ...) do not implement it: a reload costs them a filter,
 * never data. No JavaFX here, so the rules built on it can be unit-tested.
 */
public interface ScreenWork {

    /**
     * What would be lost if the screen were built again now, as short lines in the language in use (empty: nothing).
     * Runs on the screen thread; the implementation first lets pending saves finish, so only what really cannot be
     * kept is listed.
     */
    List<String> unsavedWork();

    /** What to pick again on the rebuilt screen (the selected table, for example); {@code null}: nothing to restore. */
    default Object snapshot() {
        return null;
    }

    /** Called on the NEW controller of the rebuilt screen with what {@link #snapshot()} returned (never with {@code null}). */
    default void resume(Object snapshot) {
    }
}
