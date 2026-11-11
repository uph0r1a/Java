package com.coffeeshop.coffeeshopmanagement.util;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * TODO 2.89: every dialog the program opens is tied to the window that opened it and made modal the way {@code AlertUtil.configure}
 * does it (an owner, so it cannot open behind the window; soft modality on Linux, so the dialog does not trap the mouse). A dialog that
 * skips it is the same complaint coming back, so this reads the sources: each {@code new Dialog}, {@code new Alert},
 * {@code new TextInputDialog} and {@code new ChoiceDialog} in {@code src/main} must have {@code AlertUtil.configure(variable)} (or
 * {@code variable.initOwner(...)}, as the setup wizard does) before it is shown, for the same variable, in the same method.
 */
public class DialogConfigureSourceTest {

    private static final Path JAVA = Path.of("src", "main", "java");

    private static final Pattern CONSTRUCTION = Pattern.compile("\\bnew\\s+(Dialog|Alert|TextInputDialog|ChoiceDialog)\\b\\s*(?:<[^>]*>)?\\s*\\(");

    /** One construction found in a source: where, and the variable the dialog is kept in (null when it is not kept in one). */
    record Found(String file, int line, String kind, String variable, boolean configuredAtBirth, String methodRest) {
    }

    static List<Found> dialogsIn(String file, String source) {
        List<Found> found = new ArrayList<>();
        Matcher m = CONSTRUCTION.matcher(source);
        String[] lines = source.split("\n", -1);
        while (m.find()) {
            int line = (int) source.substring(0, m.start()).chars().filter(c -> c == '\n').count() + 1;
            String text = lines[line - 1].strip();
            if (text.startsWith("*") || text.startsWith("//") || text.startsWith("/*")) continue; // a comment that says "new Dialog"
            int start = Math.max(Math.max(source.lastIndexOf(';', m.start()), source.lastIndexOf('{', m.start())), source.lastIndexOf('}', m.start())) + 1;
            Matcher variable = Pattern.compile("(\\w+)\\s*=\\s*[^;=]*$").matcher(source.substring(start, m.start()));
            String name = variable.find() ? variable.group(1) : null;
            int end = source.indexOf("\n    }\n", m.start());
            // AlertUtil.configured(new Dialog<>()) configures it in the expression that makes it
            boolean atBirth = Pattern.compile("\\bconfigured\\(\\s*$").matcher(source.substring(Math.max(0, m.start() - 40), m.start())).find();
            found.add(new Found(file, line, m.group(1), name, atBirth, source.substring(m.start(), end < 0 ? source.length() : end)));
        }
        return found;
    }

    /** Null when the dialog is configured before it is shown, else what is wrong. */
    static String problem(Found d) {
        if (d.configuredAtBirth()) return null;
        if (d.variable() == null) return "is not kept in a variable, so nothing can configure it";
        String v = Pattern.quote(d.variable());
        Matcher configured = Pattern.compile("\\bconfigure\\(\\s*" + v + "\\s*\\)|\\b" + v + "\\.initOwner\\(").matcher(d.methodRest());
        if (!configured.find()) return "is never given AlertUtil.configure(" + d.variable() + ") or an owner";
        Matcher shown = Pattern.compile("\\b" + v + "\\.(showAndWait|show)\\(").matcher(d.methodRest());
        if (shown.find() && shown.start() < configured.start()) return "is shown before it is configured";
        return null;
    }

    @Test
    public void everyDialogOfTheProgramIsConfiguredBeforeItIsShown() throws IOException {
        List<String> offenders = new ArrayList<>();
        int seen = 0;
        try (Stream<Path> files = Files.walk(JAVA)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                for (Found d : dialogsIn(file.getFileName().toString(), Files.readString(file, StandardCharsets.UTF_8))) {
                    seen++;
                    String problem = problem(d);
                    if (problem != null) offenders.add(d.file() + ":" + d.line() + " new " + d.kind() + " " + problem);
                }
            }
        }
        assertTrue("the scan found the dialogs of the program (" + seen + ")", seen > 40);
        assertTrue("These dialogs can open behind their window or trap the mouse on Linux; call AlertUtil.configure(dialog) before showAndWait (TODO 2.89): "
                + offenders, offenders.isEmpty());
    }

    // ---- the check itself, on small sources: it must notice each way of getting this wrong

    private static String problemOf(String body) {
        List<Found> found = dialogsIn("X.java", "class X {\n    void f() {\n" + body + "\n    }\n}\n");
        assertEquals(1, found.size());
        return problem(found.get(0));
    }

    @Test
    public void aDialogThatIsConfiguredIsAccepted() {
        assertEquals(null, problemOf("Dialog<ButtonType> dialog = new Dialog<>();\ndialog.setTitle(\"x\");\nAlertUtil.configure(dialog);\ndialog.showAndWait();"));
        assertEquals(null, problemOf("Alert alert = new Alert(Alert.AlertType.ERROR);\nconfigure(alert);\nalert.showAndWait();"));
        assertEquals("an owner given by hand counts, as the setup wizard does it", null,
                problemOf("Dialog<String> d = new Dialog<>();\nd.initOwner(window);\nd.show();"));
    }

    @Test
    public void aDialogMadeInsideConfiguredIsAccepted() {
        assertEquals(null, problemOf("Dialog<ButtonType> dialog = AlertUtil.configured(new Dialog<>());\ndialog.showAndWait();"));
        assertEquals(null, problemOf("Dialog<ButtonType> dialog = com.coffeeshop.coffeeshopmanagement.util.AlertUtil.configured(new Dialog<>());\ndialog.showAndWait();"));
        assertEquals("only configured() counts: fitted() is just the screen fit",
                "is never given AlertUtil.configure(dialog) or an owner", problemOf("Dialog<ButtonType> dialog = DialogFit.fitted(new Dialog<>());\ndialog.showAndWait();").replace("is never given AlertUtil.configure(dialog) or an owner", "is never given AlertUtil.configure(dialog) or an owner"));
    }

    @Test
    public void aDialogThatSkipsConfigureIsFound() {
        assertTrue(problemOf("Dialog<ButtonType> dialog = new Dialog<>();\ndialog.showAndWait();").contains("never given"));
        assertTrue("a DialogFit alone is not an owner",
                problemOf("Dialog<ButtonType> dialog = DialogFit.fitted(new Dialog<>());\ndialog.showAndWait();").contains("never given"));
    }

    @Test
    public void configuringAnotherDialogDoesNotCountForThisOne() {
        assertTrue(problemOf("Dialog<ButtonType> first = new Dialog<>();\nAlertUtil.configure(second);\nfirst.showAndWait();").contains("never given"));
    }

    @Test
    public void aDialogShownBeforeItIsConfiguredIsFound() {
        assertTrue(problemOf("Dialog<ButtonType> dialog = new Dialog<>();\ndialog.showAndWait();\nAlertUtil.configure(dialog);").contains("shown before"));
    }

    @Test
    public void aDialogNobodyCanConfigureBecauseItIsNotKeptIsFound() {
        assertTrue(problemOf("new Alert(Alert.AlertType.INFORMATION).showAndWait();").contains("not kept in a variable"));
    }

    @Test
    public void aWordInACommentIsNotADialog() {
        assertEquals(0, dialogsIn("X.java", "class X {\n    /** makes a new Dialog<>() for you */\n    // new Alert(x)\n}\n").size());
    }
}
