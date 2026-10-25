package com.coffeeshop.coffeeshopmanagement.util;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.*;

/**
 * TODO 1.7: new text must go through {@link I18n}, not be typed into a Java string or an FXML attribute in Vietnamese.
 * It counts, per file, the Java string literals and FXML attribute values that contain Vietnamese letters (comments are
 * ignored) and compares with {@code src/test/resources/i18n/untranslated-baseline.txt}, the count when the language
 * switch was started. A count that grows fails (new text skipped the system); a count that shrinks also fails until the
 * baseline is lowered, so the list only ever gets shorter and an empty list means the app is fully switchable.
 *
 * <p>After translating a screen: run {@code mvn test -Dtest=UntranslatedTextTest -Dlunavera.i18n.writeBaseline=true}
 * once to write the new numbers, and look at the change in git.
 */
public class UntranslatedTextTest {

    static final Path BASELINE = Path.of("src", "test", "resources", "i18n", "untranslated-baseline.txt");
    private static final Pattern VIETNAMESE = Pattern.compile(
            "[àáảãạăằắẳẵặâầấẩẫậèéẻẽẹêềếểễệìíỉĩịòóỏõọôồốổỗộơờớởỡợùúủũụưừứửữựỳýỷỹỵđ]"
                    // Vietnamese words that happen to have no accent mark, which the letters above cannot see
                    + "|\\b(?:Doanh thu|Hoa don|Thanh toan|Khach hang|San pham|Nhan vien|Ban hang|Tong tien)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern XML_COMMENT = Pattern.compile("(?s)<!--.*?-->");
    private static final Pattern ATTRIBUTE = Pattern.compile("=\"([^\"]*)\"");

    @Test
    public void noScreenGainsVietnameseTextThatSkipsTheLanguageSystem() throws IOException {
        Map<String, Integer> now = countAll();
        if (Boolean.getBoolean("lunavera.i18n.writeBaseline")) {
            writeBaseline(now);
        }
        Map<String, Integer> baseline = readBaseline();
        List<String> grew = new ArrayList<>();
        List<String> shrank = new ArrayList<>();
        Map<String, Integer> all = new TreeMap<>(baseline);
        now.forEach(all::putIfAbsent);
        for (String file : all.keySet()) {
            int was = baseline.getOrDefault(file, 0);
            int is = now.getOrDefault(file, 0);
            if (is > was) grew.add(file + " " + was + " -> " + is);
            if (is < was) shrank.add(file + " " + was + " -> " + is);
        }
        assertTrue("Vietnamese text typed into code or FXML instead of the language files (use I18n.t(\"key\") / %key): " + grew,
                grew.isEmpty());
        assertTrue("Less untranslated text than the baseline says: lower src/test/resources/i18n/untranslated-baseline.txt "
                + "(run with -Dlunavera.i18n.writeBaseline=true): " + shrank, shrank.isEmpty());
    }

    @Test
    public void theCounterSeesLiteralsAndSkipsCommentsAndCharacters() {
        String source = String.join("\n",
                "class A {",
                "  // \"Chú thích\" in a line comment",
                "  /* \"Ghi chú\" in a block comment */",
                "  /** \"Tài liệu\" in javadoc */",
                "  String a = \"Xin chào\";",
                "  String b = \"plain english\";",
                "  String c = \"đ\";",
                "  char d = '\"';",
                "  String e = \"a \\\" quote then Việt\";",
                "  String f = \"\"\"",
                "      text block Tiếng Việt",
                "      \"\"\";",
                "}");
        assertEquals(4, countJava(source));
    }

    @Test
    public void theFxmlCounterSkipsCommentsAndKeys() {
        String fxml = "<!-- Căn sát phải --><Label text=\"%login.title\"/><Label text=\"Đăng nhập\" prompt=\"Tên\"/>";
        assertEquals(2, countFxml(fxml));
    }

    static Map<String, Integer> countAll() throws IOException {
        Map<String, Integer> counts = new TreeMap<>();
        for (Path file : files(Path.of("src", "main", "java"), ".java")) {
            int n = countJava(read(file));
            if (n > 0) counts.put(unix(Path.of("src", "main").relativize(file)), n);
        }
        for (Path file : files(Path.of("src", "main", "resources", "fxml"), ".fxml")) {
            int n = countFxml(read(file));
            if (n > 0) counts.put(unix(Path.of("src", "main").relativize(file)), n);
        }
        return counts;
    }

    /** Java string literals (and text blocks) with a Vietnamese letter in them; comments and char literals are skipped. */
    static int countJava(String src) {
        int count = 0;
        int i = 0;
        int n = src.length();
        while (i < n) {
            char c = src.charAt(i);
            if (c == '/' && i + 1 < n && src.charAt(i + 1) == '/') {
                while (i < n && src.charAt(i) != '\n') i++;
            } else if (c == '/' && i + 1 < n && src.charAt(i + 1) == '*') {
                int end = src.indexOf("*/", i + 2);
                i = end < 0 ? n : end + 2;
            } else if (c == '"' && src.startsWith("\"\"\"", i)) {
                int end = src.indexOf("\"\"\"", i + 3);
                String literal = src.substring(i + 3, end < 0 ? n : end);
                if (VIETNAMESE.matcher(literal).find()) count++;
                i = end < 0 ? n : end + 3;
            } else if (c == '"') {
                int j = i + 1;
                StringBuilder literal = new StringBuilder();
                while (j < n && src.charAt(j) != '"' && src.charAt(j) != '\n') {
                    if (src.charAt(j) == '\\' && j + 1 < n) {
                        literal.append(src.charAt(j + 1));
                        j += 2;
                    } else {
                        literal.append(src.charAt(j));
                        j++;
                    }
                }
                if (VIETNAMESE.matcher(literal).find()) count++;
                i = j + 1;
            } else if (c == '\'') {
                int j = i + 1;
                while (j < n && src.charAt(j) != '\'' && src.charAt(j) != '\n') {
                    j += src.charAt(j) == '\\' ? 2 : 1;
                }
                i = j + 1;
            } else {
                i++;
            }
        }
        return count;
    }

    /** FXML attribute values with a Vietnamese letter in them (a {@code %key} has none); XML comments are skipped. */
    static int countFxml(String fxml) {
        String text = XML_COMMENT.matcher(fxml).replaceAll("");
        int count = 0;
        java.util.regex.Matcher m = ATTRIBUTE.matcher(text);
        while (m.find()) {
            if (VIETNAMESE.matcher(m.group(1)).find()) count++;
        }
        return count;
    }

    private static Map<String, Integer> readBaseline() throws IOException {
        Map<String, Integer> baseline = new TreeMap<>();
        if (!Files.isRegularFile(BASELINE)) return baseline;
        for (String line : Files.readAllLines(BASELINE, StandardCharsets.UTF_8)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
            int eq = trimmed.lastIndexOf('=');
            baseline.put(trimmed.substring(0, eq), Integer.parseInt(trimmed.substring(eq + 1)));
        }
        return baseline;
    }

    static void writeBaseline(Map<String, Integer> counts) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("# Vietnamese string literals / FXML attribute values per file that still skip the language system (TODO 1.7).");
        lines.add("# Written by UntranslatedTextTest with -Dlunavera.i18n.writeBaseline=true. Only ever lower these numbers.");
        lines.add("# What is left is on purpose (TODO 1.7): MenuSeedData and SeedNames (the starter menu as stored, and the table that maps its\n# Vietnamese names to English ones for display), ProductController (icon keywords matched");
        lines.add("# against product names), DatabaseConfig (seeded names and a SQL match on the old category), ProductCsv (the CSV header and");
        lines.add("# yes/no values are a file format), MoneyParser/CurrencyUtil/Product (the dong sign), TableStatus and DiningTable (stored");
        lines.add("# table names), CustomerService (the stored walk-in name), FirstRunDialog (the owner's stored position), ReceiptQr (ASCII QR text).");
        counts.forEach((file, n) -> lines.add(file + "=" + n));
        Files.createDirectories(BASELINE.getParent());
        Files.write(BASELINE, lines, StandardCharsets.UTF_8);
    }

    private static String unix(Path path) {
        return path.toString().replace('\\', '/');
    }

    private static List<Path> files(Path dir, String extension) throws IOException {
        try (Stream<Path> walk = Files.walk(dir)) {
            return walk.filter(p -> p.toString().endsWith(extension)).sorted().collect(Collectors.toList());
        }
    }

    private static String read(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8);
    }
}
