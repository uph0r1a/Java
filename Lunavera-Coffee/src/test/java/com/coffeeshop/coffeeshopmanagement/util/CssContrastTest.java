package com.coffeeshop.coffeeshopmanagement.util;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * TODO 5.43: text in the style sheets keeps enough contrast to be read in a bright shop. The WCAG formula is applied to every rule
 * that sets a text colour ({@code -fx-text-fill}, {@code -fx-fill}) as a plain colour: against the rule's own first background colour
 * when it has one, otherwise against white. 4.5 to 1 is needed, 3.0 to 1 for icons (selectors with "icon" or "avatar", and
 * {@code -fx-fill}) and for text of 18 px or more (14 px or more when bold). White text on a rule with no background of its own is
 * skipped: its background is set by another rule, and the test cannot see which. Colours made with a lookup or derive() are skipped too.
 * Reads the sheets as text, so it needs no JavaFX. Computed from the sources, not looked at on a screen in daylight.
 */
public class CssContrastTest {

    private static final Path CSS = Path.of("src", "main", "resources", "css");

    /** "file: selector" of a rule that is below the limit and is accepted for now. It may only get shorter; each needs a reason. */
    private static final Set<String> ACCEPTED = Set.of();

    private static String read(Path p) throws IOException {
        return Files.readString(p, StandardCharsets.UTF_8);
    }

    static int[] colour(String text) {
        String s = text.trim();
        if (s.equals("white")) return new int[]{255, 255, 255};
        if (s.equals("black")) return new int[]{0, 0, 0};
        Matcher m = Pattern.compile("#([0-9a-fA-F]{3})").matcher(s);
        if (m.matches()) {
            String h = m.group(1);
            return new int[]{Integer.parseInt("" + h.charAt(0) + h.charAt(0), 16), Integer.parseInt("" + h.charAt(1) + h.charAt(1), 16),
                    Integer.parseInt("" + h.charAt(2) + h.charAt(2), 16)};
        }
        m = Pattern.compile("#([0-9a-fA-F]{6})([0-9a-fA-F]{2})?").matcher(s);
        if (m.matches()) {
            if (m.group(2) != null && Integer.parseInt(m.group(2), 16) < 255) return null; // see-through: the colour behind shows
            String h = m.group(1);
            return new int[]{Integer.parseInt(h.substring(0, 2), 16), Integer.parseInt(h.substring(2, 4), 16), Integer.parseInt(h.substring(4, 6), 16)};
        }
        return null;
    }

    private static double luminance(int[] c) {
        double[] v = new double[3];
        for (int i = 0; i < 3; i++) {
            double x = c[i] / 255.0;
            v[i] = x <= 0.03928 ? x / 12.92 : Math.pow((x + 0.055) / 1.055, 2.4);
        }
        return 0.2126 * v[0] + 0.7152 * v[1] + 0.0722 * v[2];
    }

    static double ratio(int[] a, int[] b) {
        double la = luminance(a), lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    /** The first colour of a background list, or null when it is a gradient or anything that is not a plain colour. */
    private static int[] firstBackground(String value) {
        if (value == null) return null;
        int depth = 0, end = value.length();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth--;
            else if (c == ',' && depth == 0) { end = i; break; }
        }
        String first = value.substring(0, end).trim();
        if (first.startsWith("linear") || first.startsWith("radial")) return null;
        return colour(first);
    }

    private record Finding(String where, double ratio, double needed, String text) {}

    private static int measured;

    private static List<Finding> findings() throws IOException {
        List<Finding> bad = new ArrayList<>();
        measured = 0;
        List<Path> files;
        try (Stream<Path> walk = Files.walk(CSS)) {
            files = walk.filter(p -> p.toString().endsWith(".css")).sorted().toList();
        }
        for (Path file : files) {
            String text = read(file).replaceAll("(?s)/\\*.*?\\*/", "");
            Matcher rule = Pattern.compile("([^{}]+)\\{([^{}]*)\\}").matcher(text);
            while (rule.find()) {
                String selector = String.join(" ", rule.group(1).trim().split("\\s+"));
                java.util.Map<String, String> decl = new java.util.HashMap<>();
                for (String d : rule.group(2).split(";")) {
                    int colon = d.indexOf(':');
                    if (colon > 0) decl.put(d.substring(0, colon).trim(), d.substring(colon + 1).trim());
                }
                for (String key : new String[]{"-fx-text-fill", "-fx-fill"}) {
                    String value = decl.get(key);
                    if (value == null || value.isEmpty()) continue;
                    int[] fg = colour(value.split("\\s+")[0]);
                    if (fg == null) continue;
                    int[] own = firstBackground(decl.get("-fx-background-color"));
                    boolean white = fg[0] == 255 && fg[1] == 255 && fg[2] == 255;
                    if (own == null && white) continue;
                    int[] against = own != null ? own : new int[]{255, 255, 255};
                    Matcher size = Pattern.compile("([\\d.]+)px").matcher(decl.getOrDefault("-fx-font-size", ""));
                    double px = size.find() ? Double.parseDouble(size.group(1)) : 0;
                    String weight = decl.getOrDefault("-fx-font-weight", "") + decl.getOrDefault("-fx-font", "");
                    boolean bold = weight.contains("bold") || weight.contains("700");
                    boolean icon = key.equals("-fx-fill") || selector.contains("icon") || selector.contains("avatar");
                    double needed = icon || px >= 18 || (px >= 14 && bold) ? 3.0 : 4.5;
                    measured++;
                    double r = ratio(fg, against);
                    if (r < needed) {
                        bad.add(new Finding(file.getFileName() + ": " + selector, r, needed, key + " " + value));
                    }
                }
            }
        }
        return bad;
    }

    @Test
    public void everyTextColourHasEnoughContrastWithItsBackground() throws IOException {
        List<String> unexpected = new ArrayList<>();
        for (Finding f : findings()) {
            if (!ACCEPTED.contains(f.where())) {
                unexpected.add(String.format(java.util.Locale.ROOT, "%s  %s  gives %.2f, needs %.1f", f.where(), f.text(), f.ratio(), f.needed()));
            }
        }
        assertTrue("Text below the contrast limit (darken it, or lighten what it sits on):\n  " + String.join("\n  ", unexpected), unexpected.isEmpty());
    }

    @Test
    public void theAcceptedListOnlyHoldsRulesThatAreStillBelowTheLimit() throws IOException {
        Set<String> still = new java.util.TreeSet<>();
        for (Finding f : findings()) still.add(f.where());
        for (String accepted : ACCEPTED) {
            assertTrue(accepted + " is now fine: remove it from ACCEPTED", still.contains(accepted));
        }
    }

    @Test
    public void theTestReallySeesTheColours() throws IOException {
        findings();
        assertTrue("it measured " + measured + " colour pairs; fewer than expected means the sheets were not read", measured >= 150);
        // the formula, on known values: black on white is 21 to 1, and a mid grey on white is below the limit
        assertEquals(21.0, ratio(colour("black"), colour("white")), 0.001);
        assertTrue(ratio(colour("#a0a495"), colour("white")) < 3.0);
        assertTrue(ratio(colour("#73766B"), colour("white")) >= 4.5);
    }

    @Test
    public void shortColoursAndSeeThroughColoursAreRead() {
        assertEquals(255, colour("#fff")[0]);
        assertEquals(0x12, colour("#123456")[0]);
        assertEquals(null, colour("#12345680"));
        assertEquals(null, colour("-fx-accent"));
    }
}
