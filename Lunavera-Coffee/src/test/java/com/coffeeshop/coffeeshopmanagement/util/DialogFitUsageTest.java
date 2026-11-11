package com.coffeeshop.coffeeshopmanagement.util;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.Assert.assertTrue;

/**
 * TODO 2.99: a dialog that is built but never given to {@code AlertUtil.configure} or {@code DialogFit.install} would run off a short
 * screen again. Counts per file, so a new dialog in a file that already has one is caught too.
 */
public class DialogFitUsageTest {

    private static final Path JAVA = Path.of("src", "main", "java");
    private static final Pattern MADE = Pattern.compile("new (?:Dialog<|ChoiceDialog<|TextInputDialog\\()");
    private static final Pattern FITTED = Pattern.compile("AlertUtil\\.configured?\\(|DialogFit\\.(?:install|fitted)\\("); // configured(...) is configure() on a dialog made in one expression (TODO 2.89)

    @Test
    public void everyDialogIsFitted() throws IOException {
        List<String> missing = new ArrayList<>();
        try (Stream<Path> files = Files.walk(JAVA)) {
            for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
                String text = Files.readString(file);
                int made = count(MADE, text);
                int fitted = count(FITTED, text);
                if (made > fitted) missing.add(file.getFileName() + " builds " + made + " dialog(s) and fits " + fitted);
            }
        }
        assertTrue("dialogs that would not fit a short screen: " + missing, missing.isEmpty());
    }

    @Test
    public void theHelperItselfIsInstalledByConfigure() throws IOException {
        String alertUtil = Files.readString(JAVA.resolve("com/coffeeshop/coffeeshopmanagement/util/AlertUtil.java"));
        assertTrue(alertUtil.contains("DialogFit.install(dialog)"));
    }

    private static int count(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        int n = 0;
        while (matcher.find()) n++;
        return n;
    }
}
