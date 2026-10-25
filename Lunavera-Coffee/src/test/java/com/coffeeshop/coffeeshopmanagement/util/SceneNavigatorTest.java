package com.coffeeshop.coffeeshopmanagement.util;

import org.junit.After;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

/**
 * TODO 6.64: the two ways of switching screens used to be the same twenty lines twice. The "is this account still allowed to see
 * a screen" decision now lives in one place ({@link SceneNavigator#mayShow}); these tests pin it, and that
 * {@code switchScene} is only a call to {@code switchSceneAndGetController}.
 */
public class SceneNavigatorTest {

    @After
    public void signOut() {
        TestSession.clear();
    }

    @Test
    public void aLockedAccountIsRefusedAndLoggedOut() {
        TestSession.admin();
        boolean[] loggedOut = {false};
        assertFalse(SceneNavigator.mayShow("/fxml/quanlysanpham.fxml", () -> false, () -> loggedOut[0] = true));
        assertTrue("the session is ended", loggedOut[0]);
    }

    @Test
    public void aValidAccountSeesTheScreenWithoutALogout() {
        TestSession.admin();
        boolean[] loggedOut = {false};
        assertTrue(SceneNavigator.mayShow("/fxml/quanlysanpham.fxml", () -> true, () -> loggedOut[0] = true));
        assertFalse(loggedOut[0]);
    }

    @Test
    public void theLoginScreenAndAnEmptySessionAreNeverChecked() {
        TestSession.admin();
        assertTrue("the way out is always open", SceneNavigator.mayShow("/fxml/dangnhap.fxml", () -> {
            throw new AssertionError("the login screen must not ask the database");
        }, () -> fail("no logout needed")));
        TestSession.clear();
        assertTrue("nobody signed in: nothing to validate", SceneNavigator.mayShow("/fxml/quanlysanpham.fxml", () -> {
            throw new AssertionError("no account, no check");
        }, () -> fail("no logout needed")));
    }

    /** Keeps the duplicate from coming back: only one method may load a screen into a window. */
    @Test
    public void switchSceneOnlyCallsSwitchSceneAndGetController() throws IOException {
        String source = Files.readString(Path.of("src", "main", "java", "com", "coffeeshop", "coffeeshopmanagement", "util",
                "SceneNavigator.java"));
        int start = source.indexOf("public static void switchScene(Stage stage, String fxmlPath) {");
        assertTrue(start >= 0);
        String body = source.substring(start, source.indexOf("\n    }\n", start));
        assertTrue(body, body.contains("switchSceneAndGetController(stage, fxmlPath)"));
        assertFalse("no second copy of the loading code", body.contains("newLoader(") || body.contains("SessionGuard"));
        int loads = source.split("\\.load\\(\\)", -1).length - 1;
        assertEquals("screens are loaded into a window in one place plus the new-window helper", 2, loads);
    }
}
