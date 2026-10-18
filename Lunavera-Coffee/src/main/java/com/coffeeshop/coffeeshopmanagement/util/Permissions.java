package com.coffeeshop.coffeeshopmanagement.util;

import com.coffeeshop.coffeeshopmanagement.dao.DataAccessException;

/**
 * Permission checks in the code that does the work, not only on the screens (TODO 2A.3): a handler added later that forgets
 * its own check still cannot change accounts, restore a backup or refund a sale for a cashier.
 */
public final class Permissions {

    /** Thrown when the signed-in person may not do this. It is a {@link DataAccessException}, so screens already show its message. */
    public static final class PermissionDeniedException extends DataAccessException {
        private static final long serialVersionUID = 1L;

        public PermissionDeniedException(String message) {
            super(message);
        }
    }

    private Permissions() {}

    public static void requireAdmin(String what) {
        if (!Session.isAdmin()) {
            SecurityLog.warn(SecurityLog.actor() + " tried to " + what + " without permission");
            throw new PermissionDeniedException("Chức năng này chỉ dành cho Quản trị viên.");
        }
    }
}
