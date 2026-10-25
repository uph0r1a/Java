package com.coffeeshop.coffeeshopmanagement.controller;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.*;

/**
 * TODO 6.30: keeps the "FXML versus controller" check as a test instead of a throw-away script. It reads the FXML and the
 * controller source as plain text, so it needs no JavaFX toolkit and no display (the CI machines have none).
 *
 * <p>A mistake in any of these shows up only when the screen is opened (FXMLLoader error or a silently dead button),
 * which is why a renamed method or a deleted file should fail {@code mvn test} instead:
 * <ul>
 *   <li>every {@code fx:controller} class has a source file;</li>
 *   <li>every {@code onAction="#name"} (and other {@code on...="#name"}) is a method of that controller;</li>
 *   <li>every {@code @../...} path (stylesheet, image, URL) points at a file in {@code src/main/resources};</li>
 *   <li>every {@code fx:id} reaches the code: an {@code @FXML} field, a {@code #id} rule in a style sheet or a
 *       {@code "#id"} lookup in Java. One that does is not loaded into anything (a leftover id);</li>
 *   <li>every {@code styleClass} used is defined (a {@code .class} rule) in a style sheet that screen loads or in
 *       {@code app.css}, which is on every window. A class defined nowhere does nothing (bug 2.29).</li>
 * </ul>
 * The last two have a short list of mistakes that were already there when the test was written
 * ({@link #KNOWN_DEAD_IDS}, {@link #KNOWN_UNDEFINED_CLASSES}): the test fails for a new one, and also when a listed one is
 * fixed but still listed, so the lists only ever get shorter.
 */
public class FxmlControllerCheckTest {

    private static final Path MAIN = Path.of("src", "main");
    private static final Path FXML_DIR = MAIN.resolve("resources").resolve("fxml");
    private static final Path CSS_DIR = MAIN.resolve("resources").resolve("css");
    private static final Path JAVA_DIR = MAIN.resolve("java");

    /** fx:id values that no code or style sheet reaches, as found by the first run (harmless: FXMLLoader ignores them). Format {@code file:id}. */
    static final Set<String> KNOWN_DEAD_IDS = new TreeSet<>(Arrays.asList(
            "admin-trangchu.fxml:phpMyAdminButton", "admin-trangchu.fxml:reportButton", "admin-trangchu.fxml:revenueXAxis",
            "admin-trangchu.fxml:revenueYAxis", "admin-trangchu.fxml:transferQrButton",
            "dangnhap.fxml:loginCard",
            "donthanhtoan.fxml:printButton",
            "quanlydanhmuc.fxml:QuanLyDanhMucRoot", "quanlydanhmuc.fxml:addCategoryButton",
            "quanlydonhang.fxml:accountMenuButton", "quanlydonhang.fxml:categoryMenuButton", "quanlydonhang.fxml:editOrderButton",
            "quanlydonhang.fxml:orderHistoryButton", "quanlydonhang.fxml:printOrderButton", "quanlydonhang.fxml:startOrderButton",
            "quanlydonhang.fxml:tableDetailButton", "quanlydonhang.fxml:toggleStatusButton",
            "quanlykhachhang.fxml:accountMenuButton", "quanlykhachhang.fxml:categoryMenuButton", "quanlykhachhang.fxml:quanLyKhachHangRoot",
            "quanlysanpham.fxml:QuanLySanPhamRoot", "quanlysanpham.fxml:addCategoryButton",
            "quanlytaikhoan.fxml:QuanLyTaiKhoanRoot", "quanlytaikhoan.fxml:closeDetailButton", "quanlytaikhoan.fxml:detailAvatar",
            "quanlytaikhoan.fxml:editAccountButton"));

    /** Style classes used in an FXML file but defined in none of the style sheets it loads (nor app.css). Format {@code file:class}. */
    static final Set<String> KNOWN_UNDEFINED_CLASSES = new TreeSet<>(Arrays.asList(
            "admin-trangchu.fxml:primary-button",
            "employee-trangchu.fxml:section-subtitle",
            "quanlykhachhang.fxml:primary-button", "quanlykhachhang.fxml:search-field"));

    private static final Pattern CONTROLLER = Pattern.compile("fx:controller=\"([^\"]+)\"");
    private static final Pattern HANDLER = Pattern.compile("\\bon[A-Z]\\w*=\"#(\\w+)\"");
    private static final Pattern RESOURCE_PATH = Pattern.compile("=\"@\\.\\./([\\w./-]+)\"");
    private static final Pattern SHEET = Pattern.compile("@\\.\\./css/([\\w.-]+\\.css)");
    private static final Pattern FX_ID = Pattern.compile("fx:id=\"(\\w+)\"");
    private static final Pattern STYLE_CLASS = Pattern.compile("styleClass=\"([^\"]*)\"");
    private static final Pattern CSS_CLASS = Pattern.compile("\\.([A-Za-z_][\\w-]*)");
    private static final Pattern FXML_FIELD = Pattern.compile(
            "@FXML\\s+(?:(?:private|protected|public)\\s+)?[\\w<>\\[\\],.? ]+?\\s+(\\w+)\\s*(?:;|=)");

    private static String read(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    private static List<Path> fxmlFiles() throws IOException {
        try (Stream<Path> files = Files.list(FXML_DIR)) {
            return files.filter(p -> p.toString().endsWith(".fxml")).sorted().collect(Collectors.toList());
        }
    }

    private static String allText(Path dir, String extension) throws IOException {
        try (Stream<Path> files = Files.walk(dir)) {
            StringBuilder all = new StringBuilder();
            for (Path file : files.filter(p -> p.toString().endsWith(extension)).sorted().collect(Collectors.toList())) {
                all.append(read(file)).append('\n');
            }
            return all.toString();
        }
    }

    private static Set<String> matches(Pattern pattern, String text) {
        Set<String> found = new TreeSet<>();
        Matcher m = pattern.matcher(text);
        while (m.find()) found.add(m.group(1));
        return found;
    }

    private static Path controllerSource(String fxmlText) {
        Matcher m = CONTROLLER.matcher(fxmlText);
        assertTrue("an FXML file has no fx:controller", m.find());
        return JAVA_DIR.resolve(m.group(1).replace('.', '/') + ".java");
    }

    private static final Pattern EXTENDS = Pattern.compile("\\bclass\\s+\\w+\\s+extends\\s+(\\w+)");

    /**
     * The source of the controller named in an FXML file <em>and of the classes it extends</em> in the same package (TODO 6.4:
     * the admin screens inherit their sidebar handlers from {@code AdminScreen}, and FXMLLoader finds an inherited
     * {@code @FXML} method or field just as it finds one declared in the controller itself).
     */
    private static String controllerText(String fxmlText) throws IOException {
        Path source = controllerSource(fxmlText);
        StringBuilder all = new StringBuilder(read(source));
        Path folder = source.getParent();
        Matcher parent = EXTENDS.matcher(all);
        while (parent.find()) {
            Path next = folder.resolve(parent.group(1) + ".java");
            if (!Files.isRegularFile(next)) break; // a JavaFX or JDK class: nothing to read
            String text = read(next);
            all.append('\n').append(text);
            parent = EXTENDS.matcher(text);
        }
        return all.toString();
    }

    @Test
    public void thereAreFxmlFilesToCheck() throws Exception {
        assertTrue("expected the nine FXML files, found " + fxmlFiles().size(), fxmlFiles().size() >= 9);
    }

    @Test
    public void everyControllerNamedInAnFxmlFileExists() throws Exception {
        List<String> problems = new ArrayList<>();
        for (Path fxml : fxmlFiles()) {
            Path source = controllerSource(read(fxml));
            if (!Files.isRegularFile(source)) problems.add(fxml.getFileName() + " names a controller with no source file: " + source);
        }
        assertTrue(problems.toString(), problems.isEmpty());
    }

    @Test
    public void everyHandlerNamedInAnFxmlFileIsAMethodOfItsController() throws Exception {
        List<String> problems = new ArrayList<>();
        for (Path fxml : fxmlFiles()) {
            String text = read(fxml);
            String controller = controllerText(text);
            for (String handler : matches(HANDLER, text)) {
                if (!Pattern.compile("\\b\\w+\\s+" + handler + "\\s*\\(").matcher(controller).find()) {
                    problems.add(fxml.getFileName() + ": #" + handler + " is not a method of the controller");
                }
            }
        }
        assertTrue(problems.toString(), problems.isEmpty());
    }

    @Test
    public void everyStylesheetAndPictureNamedInAnFxmlFileExists() throws Exception {
        List<String> problems = new ArrayList<>();
        for (Path fxml : fxmlFiles()) {
            for (String path : matches(RESOURCE_PATH, read(fxml))) {
                if (!Files.isRegularFile(MAIN.resolve("resources").resolve(path))) {
                    problems.add(fxml.getFileName() + ": @../" + path + " does not exist in src/main/resources");
                }
            }
        }
        assertTrue(problems.toString(), problems.isEmpty());
    }

    @Test
    public void everyFxIdReachesTheCodeOrAStyleSheetExceptTheKnownLeftovers() throws Exception {
        String css = allText(CSS_DIR, ".css");
        String java = allText(JAVA_DIR, ".java");
        Set<String> dead = new TreeSet<>();
        for (Path fxml : fxmlFiles()) {
            String text = read(fxml);
            Set<String> fields = matches(FXML_FIELD, controllerText(text));
            for (String id : matches(FX_ID, text)) {
                boolean reached = fields.contains(id) || css.contains("#" + id) || java.contains("\"#" + id + "\"");
                if (!reached) dead.add(fxml.getFileName() + ":" + id);
            }
        }
        assertNoNewAndNoFixed("fx:id with no field, rule or lookup", dead, KNOWN_DEAD_IDS);
    }

    @Test
    public void everyStyleClassUsedIsDefinedInASheetTheScreenLoadsExceptTheKnownOnes() throws Exception {
        Set<String> appClasses = classesIn(read(CSS_DIR.resolve("app.css")));
        assertFalse("app.css defines no classes", appClasses.isEmpty());
        Set<String> undefined = new TreeSet<>();
        for (Path fxml : fxmlFiles()) {
            String text = read(fxml);
            Set<String> defined = new TreeSet<>(appClasses);
            for (String sheet : matches(SHEET, text)) {
                // a missing sheet is reported by everyStylesheetAndPictureNamedInAnFxmlFileExists
                if (Files.isRegularFile(CSS_DIR.resolve(sheet))) defined.addAll(classesIn(read(CSS_DIR.resolve(sheet))));
            }
            Matcher m = STYLE_CLASS.matcher(text);
            while (m.find()) {
                for (String name : m.group(1).trim().split("[,\\s]+")) {
                    if (!name.isEmpty() && !defined.contains(name)) undefined.add(fxml.getFileName() + ":" + name);
                }
            }
        }
        assertNoNewAndNoFixed("style class defined in no sheet of its screen", undefined, KNOWN_UNDEFINED_CLASSES);
    }

    private static Set<String> classesIn(String css) {
        return matches(CSS_CLASS, css.replaceAll("(?s)/\\*.*?\\*/", ""));
    }

    private static void assertNoNewAndNoFixed(String what, Set<String> found, Set<String> known) {
        Set<String> added = new TreeSet<>(found);
        added.removeAll(known);
        assertTrue("New " + what + ": " + added, added.isEmpty());
        Set<String> fixed = new TreeSet<>(known);
        fixed.removeAll(found);
        assertTrue("Fixed (or renamed), so remove from the known list in this test: " + fixed, fixed.isEmpty());
    }
}
