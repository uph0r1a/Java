package com.coffeeshop.coffeeshopmanagement.util;

import org.junit.Test;

import java.awt.Font;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.Assert.*;

/**
 * TODO 1.6: the Georgia that ships with Windows lacks many Vietnamese letters, so the app bundles one font.
 * These tests keep that true: the files are there and really contain every Vietnamese letter, and no style
 * sheet goes back to naming a machine-dependent font.
 */
public class FontBundleTest {

    /** Every Vietnamese letter with every tone mark, both cases (the ones Georgia on Windows drew as squares). */
    private static final String VIETNAMESE =
            "aàáảãạ ăằắẳẵặ âầấẩẫậ eèéẻẽẹ êềếểễệ iìíỉĩị oòóỏõọ ôồốổỗộ ơờớởỡợ uùúủũụ ưừứửữự yỳýỷỹỵ đ"
                    + "AÀÁẢÃẠ ĂẰẮẲẴẶ ÂẦẤẨẪẬ EÈÉẺẼẸ ÊỀẾỂỄỆ IÌÍỈĨỊ OÒÓỎÕỌ ÔỒỐỔỖỘ ƠỜỚỞỠỢ UÙÚỦŨỤ ƯỪỨỬỮỰ YỲÝỶỸỴ Đ";

    @Test
    public void everyBundledFontFileExistsAndHasEveryVietnameseLetter() throws Exception {
        assertFalse(AppStyle.FONT_FILES.isEmpty());
        for (String file : AppStyle.FONT_FILES) {
            try (InputStream in = AppStyle.class.getResourceAsStream(file)) {
                assertNotNull("missing font file " + file, in);
                Font font = Font.createFont(Font.TRUETYPE_FONT, in);
                assertEquals(AppStyle.FONT_FAMILY, font.getFamily());
                for (char c : VIETNAMESE.toCharArray()) {
                    if (c == ' ') continue;
                    assertTrue(file + " has no glyph for " + c + " (U+" + Integer.toHexString(c) + ")", font.canDisplay(c));
                }
            }
        }
    }

    @Test
    public void theFontLicenceIsShippedWithTheFont() throws Exception {
        try (InputStream in = AppStyle.class.getResourceAsStream("/fonts/OFL.txt")) {
            assertNotNull("the SIL Open Font License must be bundled with the font", in);
            assertTrue(new String(in.readAllBytes(), StandardCharsets.UTF_8).contains("SIL Open Font License"));
        }
    }

    @Test
    public void theSharedStyleSheetSetsTheFont() throws Exception {
        try (InputStream in = AppStyle.class.getResourceAsStream(AppStyle.STYLESHEET)) {
            assertNotNull(in);
            String css = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(css.contains(".root"));
            assertTrue(css.contains("-fx-font-family: \"" + AppStyle.FONT_FAMILY + "\""));
        }
    }

    @Test
    public void noOtherStyleSheetNamesAFont() throws Exception {
        Path css = Path.of("src", "main", "resources", "css");
        assertTrue("tests run from the project folder", Files.isDirectory(css));
        try (Stream<Path> files = Files.list(css)) {
            List<Path> sheets = files.filter(p -> p.toString().endsWith(".css")).toList();
            assertTrue(sheets.size() >= 8);
            for (Path sheet : sheets) {
                if (sheet.getFileName().toString().equals("app.css")) continue;
                for (String line : Files.readAllLines(sheet, StandardCharsets.UTF_8)) {
                    String l = line.trim();
                    boolean setsFont = l.startsWith("-fx-font-family") || l.startsWith("-fx-tick-label-font-family");
                    assertFalse(sheet.getFileName() + " sets its own font: " + l + " (only app.css may; the family differs per machine)",
                            setsFont && !l.contains(AppStyle.FONT_FAMILY));
                    assertFalse(sheet.getFileName() + " names a machine-dependent font: " + l,
                            l.contains("Georgia") || l.contains("Segoe") || l.contains("Serif"));
                    assertFalse(sheet.getFileName() + " uses a font weight no bundled file has: " + l,
                            l.matches("-fx-font-weight:\\s*[1-9]00;"));
                }
            }
        }
    }
}
