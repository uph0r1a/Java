package com.coffeeshop.coffeeshopmanagement.controller;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

/**
 * TODO 4.12: the staff screen is reachable from every administrator sidebar. Reads sources as text (no JavaFX). The general
 * handler/field/style checks of {@code FxmlControllerCheckTest} already cover the new FXML file.
 */
public class StaffScreenFilesTest {

    private static final Path MAIN = Path.of("src", "main");
    private static final Path FXML = MAIN.resolve("resources").resolve("fxml");
    private static final Path CONTROLLERS = MAIN.resolve("java").resolve("com").resolve("coffeeshop")
            .resolve("coffeeshopmanagement").resolve("controller");

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    @Test
    public void everySidebarWithAnAccountsButtonAlsoHasAStaffButton() throws IOException {
        List<String> screens = List.of("admin-trangchu", "quanlydanhmuc", "quanlykhachhang", "quanlysanpham", "quanlydonhang", "quanlytaikhoan");
        for (String screen : screens) {
            String text = read(FXML.resolve(screen + ".fxml"));
            assertTrue(screen + " has no staff button", text.contains("onAction=\"#openStaffManagement\""));
            assertTrue(screen + " has no staff label", text.contains("text=\"%nav.staff\""));
        }
    }

    @Test
    public void theStaffScreenHasItsOwnActiveButtonAndTheOthersLinkBack() throws IOException {
        String text = read(FXML.resolve("quanlynhanvien.fxml"));
        assertTrue(text.contains("fx:controller=\"com.coffeeshop.coffeeshopmanagement.controller.StaffController\""));
        assertTrue("the staff entry is marked as the current screen",
                Pattern.compile("%nav\\.staff\"[^>]*menu-button-active", Pattern.DOTALL).matcher(text).find());
        for (String handler : List.of("#openDashboard", "#openProductManagement", "#openOrderManagement", "#openCustomerManagement",
                "#openCategoryManagement", "#openAccountManagement", "#logout")) {
            assertTrue("staff screen sidebar lacks " + handler, text.contains("onAction=\"" + handler + "\""));
        }
    }

    @Test
    public void everyControllerThatNavigatesToAccountsCanNavigateToStaffAndOnlyForAnAdministrator() throws IOException {
        for (String controller : List.of("AdminScreen", "CustomerController", "OrderController")) {
            String text = read(CONTROLLERS.resolve(controller + ".java"));
            int at = text.indexOf("void openStaffManagement(");
            assertTrue(controller + " has no openStaffManagement", at > 0);
            String body = text.substring(at, text.indexOf("@FXML", at));
            assertTrue(controller + " must check for an administrator", body.contains("Session.isAdmin()"));
            assertTrue(controller, body.contains("/fxml/quanlynhanvien.fxml"));
        }
    }

    @Test
    public void theStaffControllerIsAdministratorOnlyAndNeverTouchesPasswordsOrRoles() throws IOException {
        String text = read(CONTROLLERS.resolve("StaffController.java"));
        assertTrue(text.contains("public class StaffController extends AdminScreen"));
        assertTrue(text.contains("blockNonAdmin("));
        assertFalse(text.contains("PasswordUtil"));
        assertFalse(text.contains("PasswordField"));
        assertFalse(text.contains("userDAO"));
    }
}
