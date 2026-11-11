package com.coffeeshop.coffeeshopmanagement.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * "Cỡ chữ: Lớn" (TODO 5.44). The screens write their font sizes in pixels (62 of the 218 rules are 10 or 11 px), and JavaFX has no
 * "make everything bigger" switch, so the large size is a set of style sheets, one beside each sheet that sets a size:
 * {@code css/large/<name>.css} repeats every rule of {@code css/<name>.css} that sets a size with the size {@link #scale made larger}
 * and {@code !important} (a rule in a screen's own sheet or in the FXML would otherwise win over a sheet added to the scene).
 * {@code AppStyle} adds the companion of every sheet a window loads, and {@code large/base.css} (the size of what has no size of
 * its own), while the choice is "Lớn". One companion per sheet, not one for all, because two screens may give the same class
 * different sizes (".page-title" is 22, 24 or 25 px depending on the screen), and each screen must keep its own proportions.
 *
 * <p>The files are <b>generated</b> from the other sheets by {@link #generateAll}, never edited by hand: a test compares them with
 * what this class would write today, so a rule added to a screen's sheet next month cannot be left small. To regenerate after a
 * change: run {@code LargeTextSheet.main} from the project folder (see the message the test gives). Pure: no JavaFX.
 *
 * <p>Left out on purpose: {@code receipt.css} (the invoice window is a picture of the paper, which does not change size).
 */
public final class LargeTextSheet {

    /** The folder of the generated sheets, inside {@code css/}. */
    public static final String DIR_NAME = "large";
    /** The generated sheet with the base size: not a companion of any sheet, added to every window. */
    public static final String BASE_NAME = "base.css";
    /** Sheets whose sizes are not made larger. */
    public static final Set<String> SKIPPED = Set.of("receipt.css");
    /** The base size of every window while the large text is on: what has no size of its own (buttons, table cells, fields) follows it. */
    public static final int ROOT_PX = 15;

    private static final Pattern RULE = Pattern.compile("([^{}]+)\\{([^{}]*)\\}");
    private static final Pattern SIZE = Pattern.compile("-fx-font-size\\s*:\\s*([0-9]+)px");

    private LargeTextSheet() {
    }

    /**
     * The large size of a size in pixels: two more up to 18 px (the text people read, so 10 becomes 12 and the order of the sizes
     * stays the same), one more above that (headings and totals, which sit in boxes of a fixed size).
     */
    public static int scale(int px) {
        return px <= 18 ? px + 2 : px + 1;
    }

    private static final String WARNING = "/*\n"
            + " * GENERATED FILE - do not edit by hand (TODO 5.44, the large text).\n"
            + " * %s\n"
            + " * Added by AppStyle to a window while the large text is chosen. Regenerate with service/LargeTextSheet.main after a\n"
            + " * change to any style sheet; LargeTextSheetTest fails when a file here is out of date.\n"
            + " */\n";

    /** Every generated file for these sheets (file name to text): {@code base.css} and one companion per sheet that sets a size. */
    public static java.util.Map<String, String> generateAll(java.util.Map<String, String> sheets) {
        java.util.Map<String, String> files = new TreeMap<>();
        files.put(BASE_NAME, String.format(java.util.Locale.ROOT, WARNING, "The base size of every window.") + "\n.root {\n    -fx-font-size: " + ROOT_PX + "px !important;\n}\n");
        for (var sheet : sheets.entrySet()) {
            if (SKIPPED.contains(sheet.getKey())) continue;
            String text = companion(sheet.getKey(), sheet.getValue());
            if (text != null) files.put(sheet.getKey(), text);
        }
        return files;
    }

    /** The companion of one sheet: every rule that sets a size, with the large size and !important; null when the sheet sets none. */
    static String companion(String name, String css) {
        StringBuilder out = new StringBuilder(String.format(java.util.Locale.ROOT, WARNING, "The large sizes of " + name + ", rule by rule."));
        Set<String> written = new LinkedHashSet<>();
        for (String[] rule : rulesWithSize(css)) {
            int size = scale(Integer.parseInt(rule[1]));
            if (!written.add(rule[0] + "|" + size)) continue;
            out.append('\n').append(rule[0]).append(" {\n    -fx-font-size: ").append(size).append("px !important;\n}\n");
        }
        return written.isEmpty() ? null : out.toString();
    }

    /** Every rule of a sheet that sets a size in pixels: {selectors, size}. Selectors are one per line, spacing made the same. */
    static List<String[]> rulesWithSize(String css) {
        List<String[]> rules = new ArrayList<>();
        Matcher m = RULE.matcher(css.replaceAll("(?s)/\\*.*?\\*/", ""));
        while (m.find()) {
            Matcher size = SIZE.matcher(m.group(2));
            if (!size.find()) continue;
            List<String> selectors = new ArrayList<>();
            for (String s : m.group(1).split(",")) {
                String clean = s.trim().replaceAll("\\s+", " ");
                if (!clean.isEmpty()) selectors.add(clean);
            }
            if (selectors.isEmpty()) continue;
            rules.add(new String[]{String.join(",\n", selectors), size.group(1)});
        }
        return rules;
    }

    /** Reads every sheet of a folder (file name to text). */
    public static java.util.Map<String, String> read(Path cssDir) throws IOException {
        java.util.Map<String, String> sheets = new TreeMap<>();
        try (Stream<Path> files = Files.list(cssDir)) {
            for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".css"))::iterator) {
                sheets.put(file.getFileName().toString(), Files.readString(file, StandardCharsets.UTF_8));
            }
        }
        return sheets;
    }

    /** Rewrites {@code src/main/resources/css/large/}; run from the project folder (or pass the css folder as the argument). */
    public static void main(String[] args) throws IOException {
        Path dir = Paths.get(args.length > 0 ? args[0] : "src/main/resources/css");
        Path out = dir.resolve(DIR_NAME);
        Files.createDirectories(out);
        java.util.Map<String, String> files = generateAll(read(dir));
        try (Stream<Path> old = Files.list(out)) {
            for (Path file : (Iterable<Path>) old::iterator) {
                if (!files.containsKey(file.getFileName().toString())) Files.delete(file); // a sheet that was removed or no longer sets a size
            }
        }
        for (var file : files.entrySet()) Files.writeString(out.resolve(file.getKey()), file.getValue(), StandardCharsets.UTF_8);
        System.out.println("Wrote " + files.size() + " files to " + out);
    }
}
