package com.coffeeshop.coffeeshopmanagement.controller;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.Assert.*;

/**
 * TODO 6.54: the sidebar buttons and the sign-out are written once, in {@link ScreenNavigation}. Reads the sources as text (no
 * JavaFX, no database): a screen that copies them again, a sign-out that skips a question, or a till that stops asking about the open
 * order fails here.
 */
public class ScreenNavigationTest {

    private static final Path CONTROLLERS = Path.of("src", "main", "java", "com", "coffeeshop", "coffeeshopmanagement", "controller");

    private static final List<String> HANDLERS = List.of("openDashboard", "openProductManagement", "openOrderManagement",
            "openCustomerManagement", "openCategoryManagement", "openAccountManagement", "openStaffManagement", "logout");

    /** Every screen that has the sidebar: the four bases and what extends AdminScreen. */
    private static final List<String> SCREENS = List.of("CustomerController", "EmployeeController", "OrderController", "AdminScreen");

    private static String read(String name) throws IOException {
        return Files.readString(CONTROLLERS.resolve(name + ".java"), StandardCharsets.UTF_8);
    }

    @Test
    public void theHandlersAreDeclaredOnceInTheBaseAndNowhereElseInTheControllers() throws IOException {
        List<String> copies = new ArrayList<>();
        try (Stream<Path> files = Files.list(CONTROLLERS)) {
            for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
                String name = file.getFileName().toString().replace(".java", "");
                if (name.equals("ScreenNavigation")) continue;
                String text = Files.readString(file, StandardCharsets.UTF_8);
                for (String handler : HANDLERS) {
                    if (Pattern.compile("\\bvoid\\s+" + handler + "\\s*\\(\\s*ActionEvent").matcher(text).find()) copies.add(name + "." + handler);
                }
            }
        }
        assertTrue("these copy what ScreenNavigation has: " + copies, copies.isEmpty());
        String base = read("ScreenNavigation");
        for (String handler : HANDLERS) {
            assertTrue(handler, Pattern.compile("@FXML\\s+public void " + handler + "\\s*\\(ActionEvent event\\)").matcher(base).find());
        }
    }

    @Test
    public void theBaseIsPublicSoTheFxmlLoaderCanCallItsHandlersOnASubclass() throws IOException {
        assertTrue(read("ScreenNavigation").contains("public abstract class ScreenNavigation"));
    }

    @Test
    public void everyScreenWithASidebarExtendsIt() throws IOException {
        assertTrue(read("CustomerController").contains("public class CustomerController extends ScreenNavigation"));
        assertTrue(read("EmployeeController").contains("public class EmployeeController extends ScreenNavigation"));
        assertTrue(read("OrderController").contains("public class OrderController extends ScreenNavigation"));
        assertTrue(read("AdminScreen").contains("public abstract class AdminScreen extends ScreenNavigation"));
        assertEquals(4, SCREENS.size());
    }

    @Test
    public void everyWayOutOfTheScreenAsksConfirmLeavingFirst() throws IOException {
        String base = read("ScreenNavigation");
        // the two helpers every opener goes through, and the sign-out, each ask before they switch the screen or clear the session
        int open = base.indexOf("private void open(");
        int admin = base.indexOf("private void openAdminScreen(");
        assertTrue(base.substring(open, base.indexOf("}", open)).contains("confirmLeaving()"));
        assertTrue(base.substring(admin, base.indexOf("\n    }\n", admin)).contains("confirmLeaving()"));
        int logout = base.indexOf("public void logout(");
        String body = base.substring(logout, base.indexOf("\n    }\n", logout));
        assertTrue(body.indexOf("confirmLeaving()") > 0 && body.indexOf("confirmLeaving()") < body.indexOf("Session.clear()"));
        assertTrue(body.contains("ClockOffer.beforeSignOut(") && body.contains("BackupDialogs.offerBeforeLeaving"));
    }

    @Test
    public void theTillStillAsksAboutTheOpenOrderBeforeLeaving() throws IOException {
        String till = read("OrderController");
        int hook = till.indexOf("protected boolean confirmLeaving()");
        assertTrue("OrderController must override confirmLeaving", hook > 0);
        assertTrue(till.substring(hook, till.indexOf("\n    }\n", hook)).contains("confirmLeaveOrder()"));
        assertTrue("the default of the others is to leave at once",
                read("ScreenNavigation").contains("protected boolean confirmLeaving() {\n        return true;"));
    }

    @Test
    public void anAdministratorScreenIsOnlyOpenedByAnAdministrator() throws IOException {
        String base = read("ScreenNavigation");
        int at = base.indexOf("private void openAdminScreen(");
        String body = base.substring(at, base.indexOf("\n    }\n", at));
        assertTrue(body.contains("Session.isAdmin()"));
        for (String handler : List.of("openProductManagement", "openCategoryManagement", "openAccountManagement", "openStaffManagement")) {
            int h = base.indexOf("public void " + handler + "(");
            assertTrue(handler, base.substring(h, base.indexOf("\n    }\n", h)).contains("openAdminScreen("));
        }
    }
}
