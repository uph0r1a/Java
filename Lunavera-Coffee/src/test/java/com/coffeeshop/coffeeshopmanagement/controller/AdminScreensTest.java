package com.coffeeshop.coffeeshopmanagement.controller;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

/**
 * TODO 6.4: the administrator screens each have their own controller and share one base class. Reads sources as text, so it
 * needs no JavaFX. {@code FxmlControllerCheckTest} already proves every handler and field of each FXML file exists in its
 * controller (including inherited ones); this fixes the shape of the split so it does not drift back into one big class.
 */
public class AdminScreensTest {

    private static final Path MAIN = Path.of("src", "main");
    private static final Path CONTROLLERS = MAIN.resolve("java").resolve("com").resolve("coffeeshop")
            .resolve("coffeeshopmanagement").resolve("controller");
    private static final Path FXML = MAIN.resolve("resources").resolve("fxml");

    private static final List<String> SCREENS = List.of("AdminDashboardController", "CategoryController", "AccountController", "ProductController");
    private static final List<String> NAVIGATION = List.of("openDashboard", "openProductManagement", "openOrderManagement",
            "openCustomerManagement", "openCategoryManagement", "openAccountManagement", "logout");

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private static String controllerOf(String fxmlFile) throws IOException {
        Matcher m = Pattern.compile("fx:controller=\"[\\w.]*\\.(\\w+)\"").matcher(read(FXML.resolve(fxmlFile)));
        assertTrue(fxmlFile + " has no fx:controller", m.find());
        return m.group(1);
    }

    @Test
    public void eachAdminScreenHasItsOwnController() throws IOException {
        assertEquals("AdminDashboardController", controllerOf("admin-trangchu.fxml"));
        assertEquals("CategoryController", controllerOf("quanlydanhmuc.fxml"));
        assertEquals("AccountController", controllerOf("quanlytaikhoan.fxml"));
        assertEquals("ProductController", controllerOf("quanlysanpham.fxml"));
    }

    @Test
    public void theOldSharedControllerIsGoneEverywhere() throws IOException {
        assertFalse(Files.exists(CONTROLLERS.resolve("AdminController.java")));
        try (var files = Files.walk(MAIN)) {
            for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".fxml") || p.toString().endsWith(".java"))::iterator) {
                String text = read(file);
                // a class name, not a mention in a comment: "controller.AdminController" or "AdminController." / "AdminController("
                assertFalse(file + " still uses AdminController", Pattern.compile("controller\\.AdminController\\b|\\bnew AdminController\\b|\\bAdminController\\.\\w+\\(|\\bAdminController [a-z]").matcher(text).find());
            }
        }
    }

    @Test
    public void everyScreenExtendsTheBaseAndDoesNotRepeatTheNavigation() throws IOException {
        for (String screen : SCREENS) {
            String text = read(CONTROLLERS.resolve(screen + ".java"));
            assertTrue(screen, text.contains("public class " + screen + " extends AdminScreen"));
            for (String handler : NAVIGATION) {
                assertFalse(screen + " declares " + handler + " again; it belongs in AdminScreen",
                        Pattern.compile("\\bvoid\\s+" + handler + "\\s*\\(").matcher(text).find());
            }
        }
    }

    @Test
    public void theBaseHoldsTheNavigationAsPublicFxmlHandlers() throws IOException {
        String base = read(CONTROLLERS.resolve("AdminScreen.java"));
        assertTrue(base.contains("public abstract class AdminScreen"));
        for (String handler : NAVIGATION) {
            assertTrue(handler, Pattern.compile("@FXML\\s+public void " + handler + "\\s*\\(").matcher(base).find());
        }
        assertTrue(base.contains("protected boolean requireAdmin()"));
        assertTrue(base.contains("protected static boolean blockNonAdmin("));
    }

    @Test
    public void aScreenOnlyDeclaresTheFxmlFieldsOfItsOwnFxmlFile() throws IOException {
        Pattern field = Pattern.compile("@FXML\\s+(?:private|protected|public)?\\s*[\\w<>\\[\\],.? ]+?\\s+(\\w+)\\s*;");
        String[][] pairs = {{"admin-trangchu.fxml", "AdminDashboardController"}, {"quanlydanhmuc.fxml", "CategoryController"},
                {"quanlytaikhoan.fxml", "AccountController"}, {"quanlysanpham.fxml", "ProductController"}};
        for (String[] pair : pairs) {
            String fxml = read(FXML.resolve(pair[0]));
            Matcher m = field.matcher(read(CONTROLLERS.resolve(pair[1] + ".java")));
            while (m.find()) {
                assertTrue(pair[1] + "." + m.group(1) + " is not an fx:id of " + pair[0], fxml.contains("fx:id=\"" + m.group(1) + "\""));
            }
        }
    }

    @Test
    public void noControllerIsAMonster() throws IOException {
        for (String screen : SCREENS) {
            long lines = read(CONTROLLERS.resolve(screen + ".java")).lines().count();
            assertTrue(screen + " has " + lines + " lines; split it before it grows past 900", lines <= 900);
        }
    }
}
