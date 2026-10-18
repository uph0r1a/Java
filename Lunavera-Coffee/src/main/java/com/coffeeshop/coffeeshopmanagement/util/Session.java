package com.coffeeshop.coffeeshopmanagement.util;

import com.coffeeshop.coffeeshopmanagement.model.Employee;
import com.coffeeshop.coffeeshopmanagement.model.Role;
import com.coffeeshop.coffeeshopmanagement.model.User;

/**
 * Holds the currently logged-in account for the lifetime of the running application.
 * A single desktop process serves one signed-in user at a time, so a simple static holder
 * (rather than passing the user through every controller constructor) is the pragmatic
 * choice here; controllers read it after navigation instead of each other's fields.
 */
public final class Session {

    // volatile: SessionGuard's background check reads these off the FX thread.
    private static volatile User currentUser;
    private static volatile Employee currentEmployee; // populated for ADMIN/EMPLOYEE accounts

    /** The password hash the signed-in person logged in with; a different stored hash later means it was reset elsewhere (TODO 2A.10). */
    private static volatile String passwordBaseline;

    private Session() {
    }

    /** A new sign-in: remembers who and the password they signed in with. */
    public static void start(User user, Employee employee) {
        currentUser = user;
        currentEmployee = employee;
        passwordBaseline = user != null ? user.getPasswordHash() : null;
    }

    /** Refreshes the account details (role, status) from the database without touching the password baseline. */
    public static void refresh(User user, Employee employee) {
        currentUser = user;
        currentEmployee = employee;
    }

    /** The hash the person signed in with, or null when nobody is signed in. */
    public static String getPasswordBaseline() {
        return passwordBaseline;
    }

    /** The signed-in person changed their own password (or an admin changed their own): the new hash is the new baseline. */
    public static void passwordChangedBySelf(int userId, String newHash) {
        User user = currentUser;
        if (user != null && user.getId() == userId) {
            passwordBaseline = newHash;
        }
    }

    public static void clear() {
        currentUser = null;
        currentEmployee = null;
        passwordBaseline = null;
    }

    public static User getCurrentUser() {
        return currentUser;
    }

    public static Employee getCurrentEmployee() {
        return currentEmployee;
    }

    public static Role getCurrentRole() {
        return currentUser != null ? currentUser.getRole() : null;
    }

    public static boolean isAdmin() {
        return getCurrentRole() == Role.ADMIN;
    }

    public static String getDisplayName() {
        if (currentEmployee != null && currentEmployee.getFullName() != null) {
            return currentEmployee.getFullName();
        }
        return currentUser != null ? currentUser.getUsername() : "";
    }
}
