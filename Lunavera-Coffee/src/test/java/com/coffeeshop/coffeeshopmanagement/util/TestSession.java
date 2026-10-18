package com.coffeeshop.coffeeshopmanagement.util;

import com.coffeeshop.coffeeshopmanagement.model.Role;
import com.coffeeshop.coffeeshopmanagement.model.User;

/** Tests of admin-only actions sign an administrator in first, as the screens do. */
public final class TestSession {
    private TestSession() {}

    public static void admin() {
        User u = new User();
        u.setId(1);
        u.setUsername("test-admin");
        u.setRole(Role.ADMIN);
        Session.start(u, null);
    }

    public static void clear() {
        Session.clear();
    }
}
