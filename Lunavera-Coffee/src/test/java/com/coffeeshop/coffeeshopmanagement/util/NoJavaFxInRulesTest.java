package com.coffeeshop.coffeeshopmanagement.util;

import org.junit.Test;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.*;

/**
 * TODO 6.83: the rules, services, data access, models, configuration and the plain helpers compile and run without JavaFX. Only the
 * screens ({@code controller}), the two start-up classes and the helpers listed below may use it. The test compiles everything else
 * with a class path that holds the two libraries the code needs (the SQLite driver and slf4j) and nothing else, so a service that
 * starts to use a JavaFX class, or a helper that does, fails here and not on somebody's machine without JavaFX.
 *
 * <p>The list is the fence: a new helper that needs JavaFX is added to it on purpose; a rule that needs a screen helper is a design
 * error to fix, not a line to add.
 */
public class NoJavaFxInRulesTest {

    private static final Path ROOT = Path.of("src", "main", "java");

    /** The helpers in {@code util} that are about screens and so may use JavaFX. */
    static final Set<String> SCREEN_HELPERS = Set.of(
            "AlertPlayer", "AlertUtil", "AppIcon", "AppStyle", "Async", "AutoRefresh", "BusyIndicator", "CapsLockWarning", "ClipboardText", "ClockOffer", "CrashHandler",
            "DashboardWidgets", "DateBoxes", "DatePresets", "DateTextConverter", "DialogFit", "FitFix", "IdleLogout", "LanguageSwitch", "PageBar", "PagedTable", "PasswordReveal",
            "PictureCheck", "SceneNavigator", "ScreenFit", "SessionGuard", "ShiftSheetPage", "StageSize", "TextLengthLimiter", "ThermalReceipt",
            "Thumbnails", "TillLock");

    private static List<Path> allFiles() throws IOException {
        try (Stream<Path> files = Files.walk(ROOT)) {
            return files.filter(p -> p.toString().endsWith(".java")).sorted().collect(Collectors.toList());
        }
    }

    private static boolean mayUseJavaFx(Path file) {
        String name = file.getFileName().toString().replace(".java", "");
        String path = file.toString().replace('\\', '/');
        return path.contains("/controller/") || name.equals("Launcher") || name.equals("LunaveraApplication")
                || name.equals("module-info") || (path.contains("/util/") && SCREEN_HELPERS.contains(name));
    }

    private static Set<String> codeOf(Path file) throws IOException {
        String text = Files.readString(file, StandardCharsets.UTF_8)
                .replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\n]*", "");
        Set<String> lines = new TreeSet<>();
        for (String line : text.split("\n")) {
            if (line.contains("javafx")) lines.add(line.trim());
        }
        return lines;
    }

    @Test
    public void onlyTheScreenClassesUseJavaFx() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : allFiles()) {
            if (!mayUseJavaFx(file) && !codeOf(file).isEmpty()) offenders.add(file.getFileName() + ": " + codeOf(file).iterator().next());
        }
        assertTrue("these classes use JavaFX but are not screen classes: " + offenders, offenders.isEmpty());
    }

    @Test
    public void theListOfScreenHelpersHasNoOneThatDoesNotNeedIt() throws IOException {
        Set<String> real = new TreeSet<>();
        for (Path file : allFiles()) {
            if (file.toString().replace('\\', '/').contains("/util/") && !codeOf(file).isEmpty()) {
                real.add(file.getFileName().toString().replace(".java", ""));
            }
        }
        Set<String> stale = new TreeSet<>(SCREEN_HELPERS);
        stale.removeAll(real);
        assertTrue("on the list but no longer using JavaFX (take them off): " + stale, stale.isEmpty());
    }

    @Test
    public void everythingElseCompilesWithoutJavaFx() throws IOException, URISyntaxException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull("run the tests with a JDK", compiler);
        List<Path> files = new ArrayList<>();
        for (Path file : allFiles()) {
            if (!mayUseJavaFx(file)) files.add(file);
        }
        assertTrue(files.size() > 150);
        String classPath = Stream.of("org.sqlite.JDBC", "org.slf4j.Logger")
                .map(NoJavaFxInRulesTest::jarOf).collect(Collectors.joining(java.io.File.pathSeparator));
        Path out = Files.createTempDirectory("nofx");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager manager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            Boolean ok = compiler.getTask(null, manager, diagnostics,
                    List.of("-proc:none", "-nowarn", "-classpath", classPath, "-d", out.toString()),
                    null, manager.getJavaFileObjectsFromPaths(files)).call();
            List<String> errors = diagnostics.getDiagnostics().stream().filter(d -> d.getKind() == Diagnostic.Kind.ERROR)
                    .limit(8).map(d -> (d.getSource() == null ? "" : d.getSource().getName().replaceAll(".*/", "") + ":" + d.getLineNumber() + " ") + d.getMessage(null))
                    .collect(Collectors.toList());
            assertTrue("does not compile without JavaFX: " + errors, Boolean.TRUE.equals(ok) && errors.isEmpty());
        } finally {
            try (Stream<Path> tree = Files.walk(out)) {
                tree.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
    }

    private static String jarOf(String className) {
        try {
            return Path.of(Class.forName(className).getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        } catch (ReflectiveOperationException | URISyntaxException e) {
            throw new AssertionError("cannot find the library of " + className, e);
        }
    }
}
