package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.model.Category;
import com.coffeeshop.coffeeshopmanagement.model.Product;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.TextLimits;
import org.junit.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * TODO 6.78, mutation sweep 2026-11-16 (second run, {@link ProductCsv}): the exact edges of the import. The tests so far used
 * clearly good and clearly bad files; a limit moved by one row, character or byte went unnoticed.
 */
public class ProductCsvEdgesTest {

    private static final List<Category> CATEGORIES = List.of(new Category(1, "Cà phê", "", true), new Category(2, "Bánh ngọt", "", true));
    private static final String HEADER = "Tên sản phẩm,Danh mục,Giá bán (đ),Giá vốn (đ),Tồn kho,Mô tả,Đang bán";

    private static ProductCsv.Result parse(String text) {
        return ProductCsv.parse(text, CATEGORIES, (name, category) -> false);
    }

    private static ProductCsv.Result parseRows(String... rows) {
        return parse(HEADER + "\n" + String.join("\n", rows) + "\n");
    }

    // ---- how a file is read

    @Test
    public void theSeparatorIsTheOneUsedMostAndATieKeepsTheCommaOrTheSemicolon() {
        assertEquals("tab only when it beats both", '\t', ProductCsv.delimiterOf("a\tb\tc,d"));
        assertEquals("a tab as often as the comma is not enough", ',', ProductCsv.delimiterOf("a\tb,c"));
        assertEquals("a tab as often as the semicolon is not enough, the semicolon beats the comma", ';', ProductCsv.delimiterOf("a\tb;c"));
        assertEquals(';', ProductCsv.delimiterOf("a;b;c,d"));
        assertEquals("a tie of semicolon and comma keeps the comma", ',', ProductCsv.delimiterOf("a;b,c"));
        assertEquals("nothing at all keeps the comma", ',', ProductCsv.delimiterOf("abc"));
    }

    @Test
    public void aFileIsReadAsUtf16OnlyWhenItStartsWithAUtf16ByteOrderMark() {
        assertEquals("A", ProductCsv.decode(new byte[]{(byte) 0xFF, (byte) 0xFE, 'A', 0}));
        assertEquals("A", ProductCsv.decode(new byte[]{(byte) 0xFE, (byte) 0xFF, 0, 'A'}));
        assertEquals("a mark and nothing else, little-endian", "", ProductCsv.decode(new byte[]{(byte) 0xFF, (byte) 0xFE}));
        assertEquals("a mark and nothing else, big-endian", "", ProductCsv.decode(new byte[]{(byte) 0xFE, (byte) 0xFF}));
        assertEquals("UTF-8 is the rest", "Cà phê", ProductCsv.decode("Cà phê".getBytes(StandardCharsets.UTF_8)));
        assertEquals("one byte can hold no mark", "\uFFFD", ProductCsv.decode(new byte[]{(byte) 0xFF}));
        assertEquals("one byte can hold no mark", "\uFFFD", ProductCsv.decode(new byte[]{(byte) 0xFE}));
        assertEquals("", ProductCsv.decode(new byte[0]));
        assertEquals("FF followed by another byte is not a mark", "\uFFFD\uFFFD", ProductCsv.decode(new byte[]{(byte) 0xFF, (byte) 0xFD}));
        assertEquals("FE followed by another byte is not a mark", "\uFFFD\uFFFD", ProductCsv.decode(new byte[]{(byte) 0xFE, (byte) 0xFD}));
    }

    @Test
    public void aByteOrderMarkAndASepLineAreSkippedAndTheDeclaredSeparatorIsUsed() {
        List<List<String>> rows = ProductCsv.split("\uFEFFsep=;\r\na;b,c;d\r\n1;2;3\r\n");
        assertEquals(2, rows.size());
        assertEquals(List.of("a", "b,c", "d"), rows.get(0));
        assertEquals(List.of("1", "2", "3"), rows.get(1));
        assertEquals("the word is read in any letter case", List.of("a", "b"), ProductCsv.split("SEP=;\na;b").get(0));
        assertEquals("a name of two characters declares nothing, the separator is guessed", List.of("a", "b"), ProductCsv.split("sep=ab\na,b").get(0));
        assertEquals("an empty declaration declares nothing", List.of("a", "b"), ProductCsv.split("sep=\na,b").get(0));
        assertEquals("a declared tab", List.of("a,b", "c"), ProductCsv.split("sep=\t\na,b\tc").get(0));
    }

    @Test
    public void quotesDoubledQuotesAndLineBreaksInsideQuotesAreKept() {
        List<List<String>> rows = ProductCsv.split("\"a\"\"b\",\"x\ny\",c\r\n1,2,3");
        assertEquals(List.of("a\"b", "x\ny", "c"), rows.get(0));
        assertEquals(List.of("1", "2", "3"), rows.get(1));
        assertEquals("a quote that is the last character of the text closes the cell", List.of("abc"), ProductCsv.split("\"abc\"").get(0));
        assertEquals("a quote inside a plain cell is just a character", List.of("ab\"c", "d"), ProductCsv.split("ab\"c,d").get(0));
        assertEquals("a lone CR ends a row", 2, ProductCsv.split("a\rb").size());
        assertEquals("CR LF ends one row, not two", 2, ProductCsv.split("a\r\nb").size());
        assertEquals("a last row with no end of line", List.of("b"), ProductCsv.split("a\nb").get(1));
        assertTrue(ProductCsv.split(null).isEmpty());
        assertTrue(ProductCsv.split("").isEmpty());
    }

    // ---- how a row is judged

    private static String rowWithName(int length) {
        return "n".repeat(length) + ",Cà phê,1000,,5,,Có";
    }

    @Test
    public void aNameMayHaveExactlyTheMaximumLength() {
        ProductCsv.Result ok = parseRows(rowWithName(TextLimits.NAME_MAX));
        assertEquals(1, ok.products().size());
        assertTrue(ok.problems().isEmpty());
        ProductCsv.Result tooLong = parseRows(rowWithName(TextLimits.NAME_MAX + 1));
        assertEquals(0, tooLong.products().size());
        assertEquals(I18n.t("csv.line", 2, I18n.t("csv.p.namelong", TextLimits.NAME_MAX)), tooLong.problems().get(0));
        assertEquals(I18n.t("csv.line", 2, I18n.t("csv.p.noname")), parseRows(",Cà phê,1000,,5,,Có").problems().get(0));
        assertEquals("a name of spaces is no name", I18n.t("csv.line", 2, I18n.t("csv.p.noname")), parseRows("   ,Cà phê,1000,,5,,Có").problems().get(0));
    }

    @Test
    public void aDescriptionMayHaveExactlyTheMaximumLengthAndAnEmptyOneIsNone() {
        ProductCsv.Result ok = parseRows("A,Cà phê,1000,,5," + "d".repeat(TextLimits.DESCRIPTION_MAX) + ",Có");
        assertEquals("d".repeat(TextLimits.DESCRIPTION_MAX), ok.products().get(0).getDescription());
        ProductCsv.Result tooLong = parseRows("A,Cà phê,1000,,5," + "d".repeat(TextLimits.DESCRIPTION_MAX + 1) + ",Có");
        assertEquals(I18n.t("csv.line", 2, I18n.t("csv.p.desclong", TextLimits.DESCRIPTION_MAX)), tooLong.problems().get(0));
        assertNull(parseRows("A,Cà phê,1000,,5,,Có").products().get(0).getDescription());
        assertNull("no description column at all", parse("Tên sản phẩm,Danh mục,Giá bán\nA,Cà phê,1000\n").products().get(0).getDescription());
    }

    @Test
    public void theStockMayBeZeroUpToTheMaximumAndAnEmptyCellIsZero() {
        assertEquals(0, parseRows("A,Cà phê,1000,,0,,Có").products().get(0).getStock());
        assertEquals(0, parseRows("A,Cà phê,1000,,,,Có").products().get(0).getStock());
        assertEquals(Limits.MAX_STOCK, parseRows("A,Cà phê,1000,,1000000,,Có").products().get(0).getStock());
        assertEquals(I18n.t("csv.line", 2, I18n.t("csv.p.stockrange")), parseRows("A,Cà phê,1000,,1000001,,Có").problems().get(0));
        assertEquals(I18n.t("csv.line", 2, I18n.t("csv.p.stockint")), parseRows("A,Cà phê,1000,,1.5,,Có").problems().get(0));
        assertEquals("no stock column at all", 0, parse("Tên sản phẩm,Danh mục,Giá bán\nA,Cà phê,1000\n").products().get(0).getStock());
    }

    @Test
    public void theOnSaleColumnTakesYesNoAndNothingElse() {
        for (String yes : new String[]{"Có", "co", "1", "true", "YES", "x", "đang bán", "bán"}) {
            assertTrue(yes, parseRows("A,Cà phê,1000,,5,," + yes).products().get(0).isActive());
        }
        for (String no : new String[]{"Không", "khong", "0", "false", "No", "ngừng bán", "ngưng"}) {
            assertFalse(no, parseRows("A,Cà phê,1000,,5,," + no).products().get(0).isActive());
        }
        assertTrue("an empty cell is on sale", parseRows("A,Cà phê,1000,,5,,").products().get(0).isActive());
        assertTrue("no such column is on sale", parse("Tên sản phẩm,Danh mục,Giá bán\nA,Cà phê,1000\n").products().get(0).isActive());
        assertEquals(I18n.t("csv.line", 2, I18n.t("csv.p.activeinvalid", "Hết")), parseRows("A,Cà phê,1000,,5,,Hết").problems().get(0));
    }

    @Test
    public void aCostAbovePriceGivesAWarningButTheRowIsStillAdded() {
        ProductCsv.Result equal = parseRows("A,Cà phê,1000,1000,5,,Có");
        assertEquals(1, equal.products().size());
        assertTrue("a cost equal to the price is not a warning", equal.warnings().isEmpty());
        assertTrue(parseRows("A,Cà phê,1000,999,5,,Có").warnings().isEmpty());
        ProductCsv.Result above = parseRows("A,Cà phê,1000,1001,5,,Có");
        assertEquals(1, above.products().size());
        assertEquals(List.of(I18n.t("csv.line", 2, I18n.t("csv.warn.cost", "1001", "1000"))), above.warnings());
        assertEquals("a blank cost cell is no cost", null, parseRows("A,Cà phê,1000,,5,,Có").products().get(0).getCost());
        assertEquals(new BigDecimal("700"), parseRows("A,Cà phê,1000,700,5,,Có").products().get(0).getCost());
        assertEquals(I18n.t("csv.line", 2, I18n.t("csv.p.costinvalid")), parseRows("A,Cà phê,1000,abc,5,,Có").problems().get(0));
        assertEquals(I18n.t("csv.line", 2, I18n.t("csv.p.pricemax", Limits.maxPriceText())), parseRows("A,Cà phê,1000,100000001,5,,Có").problems().get(0));
        assertEquals("the biggest cost", 1, parseRows("A,Cà phê,1000,100000000,5,,Có").products().size());
    }

    @Test
    public void aRowWithMoreOrFewerCellsThanTheHeaderIsAddedWithAWarning() {
        assertTrue("as many cells", parseRows("A,Cà phê,1000,,5,,Có").warnings().isEmpty());
        ProductCsv.Result more = parseRows("A,Cà phê,1000,,5,,Có,extra");
        assertEquals(1, more.products().size());
        assertEquals(List.of(I18n.t("csv.line", 2, I18n.t("csv.warn.cells.extra", 8, 7))), more.warnings());
        ProductCsv.Result fewer = parseRows("A,Cà phê,1000");
        assertEquals(1, fewer.products().size());
        assertEquals(List.of(I18n.t("csv.line", 2, I18n.t("csv.warn.cells.missing", 3, 7))), fewer.warnings());
    }

    @Test
    public void aLeadingApostropheProtectingAFormulaIsTakenOffAgainButNothingElse() {
        assertEquals("=SUM(A1)", parseRows("A,Cà phê,1000,,5,'=SUM(A1),Có").products().get(0).getDescription());
        assertEquals("+1", parseRows("A,Cà phê,1000,,5,'+1,Có").products().get(0).getDescription());
        assertEquals("-1", parseRows("A,Cà phê,1000,,5,'-1,Có").products().get(0).getDescription());
        assertEquals("@x", parseRows("A,Cà phê,1000,,5,'@x,Có").products().get(0).getDescription());
        assertEquals("an apostrophe before a letter stays", "'abc", parseRows("A,Cà phê,1000,,5,'abc,Có").products().get(0).getDescription());
        assertEquals("an apostrophe alone stays", "'", parseRows("A,Cà phê,1000,,5,',Có").products().get(0).getDescription());
        assertEquals("the name is unprotected too", "=Cà", parseRows("'=Cà,Cà phê,1000,,5,,Có").products().get(0).getName());
    }

    @Test
    public void aProblemNamesItsLineAndBlankRowsAreSkippedButCounted() {
        ProductCsv.Result result = parseRows("A,Cà phê,1000,,5,,Có", ",,,,,,", "B,Không có,1000,,5,,Có", "C,Cà phê,1000,,5,,Có");
        assertEquals(2, result.products().size());
        assertEquals("the blank row was line 3, the bad one line 4",
                List.of(I18n.t("csv.line", 4, I18n.t("csv.p.nocategory", "Không có"))), result.problems());
    }

    @Test
    public void theSameNameInTheSameCategoryIsAProblemAndInAnotherCategoryIsNot() {
        ProductCsv.Result result = parseRows("Latte,Cà phê,1000,,5,,Có", "LATTE,Cà phê,1000,,5,,Có", "Latte,Bánh ngọt,1000,,5,,Có");
        assertEquals(2, result.products().size());
        assertEquals(List.of(I18n.t("csv.line", 3, I18n.t("csv.p.duplicate"))), result.problems());
        ProductCsv.Result taken = ProductCsv.parse(HEADER + "\nLatte,Cà phê,1000,,5,,Có\n", CATEGORIES, (name, category) -> name.equals("Latte") && category == 1);
        assertEquals(I18n.t("csv.line", 2, I18n.t("csv.p.exists", "Latte", "Cà phê")), taken.problems().get(0));
    }

    @Test
    public void theHeaderNeedsANameAPriceAndACategoryColumn() {
        String no = I18n.t("csv.nocolumns");
        assertEquals(List.of(no), parse("Danh mục,Giá bán\nCà phê,1000\n").problems());
        assertEquals(List.of(no), parse("Tên sản phẩm,Giá bán\nA,1000\n").problems());
        assertEquals(List.of(no), parse("Tên sản phẩm,Danh mục\nA,Cà phê\n").problems());
        assertEquals(List.of(I18n.t("csv.empty")), parse("").problems());
        assertEquals(1, parse("Name,Category,Price\nA,Cà phê,1000\n").products().size());
    }

    @Test
    public void aFileMayHaveExactlyFiveThousandRows() {
        List<String> rows = new ArrayList<>();
        for (int i = 0; i < ProductCsv.MAX_ROWS; i++) rows.add("p" + i + ",Cà phê,1000,,1,,Có");
        ProductCsv.Result ok = parseRows(rows.toArray(new String[0]));
        assertEquals(ProductCsv.MAX_ROWS, ok.products().size());
        assertTrue(ok.problems().isEmpty());
        rows.add("one-more,Cà phê,1000,,1,,Có");
        ProductCsv.Result tooMany = parseRows(rows.toArray(new String[0]));
        assertEquals(0, tooMany.products().size());
        assertEquals(List.of(I18n.t("csv.toomany", ProductCsv.MAX_ROWS)), tooMany.problems());
    }

    @Test
    public void aProductCodeIsCheckedForTheFileAndTheDatabase() {
        String header = HEADER + ",Mức báo nhập,Mã vạch\n";
        ProductCsv.Result result = ProductCsv.parse(header
                        + "A,Cà phê,1000,,5,,Có,,ABC123\n"
                        + "B,Cà phê,1000,,5,,Có,,ABC123\n"
                        + "C,Cà phê,1000,,5,,Có,,TAKEN1\n"
                        + "D,Cà phê,1000,,5,,Có,3,\n",
                CATEGORIES, (name, category) -> false, code -> code.equals("TAKEN1"));
        assertEquals(2, result.products().size());
        assertEquals("ABC123", result.products().get(0).getCode());
        assertNull(result.products().get(1).getCode());
        assertEquals(Integer.valueOf(3), result.products().get(1).getReorderLevel());
        assertEquals(I18n.t("csv.line", 3, I18n.t("csv.p.codeduplicate", "ABC123")), result.problems().get(0));
        assertEquals(I18n.t("csv.line", 4, I18n.t("csv.p.codeexists", "TAKEN1")), result.problems().get(1));
        ProductCsv.Result badLevel = ProductCsv.parse(header + "A,Cà phê,1000,,5,,Có,abc,\n", CATEGORIES, (name, category) -> false);
        assertEquals(I18n.t("csv.line", 2, I18n.t("csv.p.reorderinvalid", Limits.MAX_STOCK)), badLevel.problems().get(0));
    }

    @Test
    public void everyColumnIsFoundEvenWhenItIsTheFirstOne() {
        // header name, value of the first test row, what the parsed product must say
        String[][] columns = {
                {"Tên sản phẩm", "Latte"}, {"Danh mục", "Bánh ngọt"}, {"Giá bán", "45000"}, {"Giá vốn", "20000"}, {"Tồn kho", "7"},
                {"Mô tả", "ít đá"}, {"Đang bán", "Không"}, {"Mức báo nhập", "3"}, {"Mã vạch", "XYZ789"},
        };
        for (int first = 0; first < columns.length; first++) {
            List<String> headers = new ArrayList<>();
            List<String> values = new ArrayList<>();
            headers.add(columns[first][0]);
            values.add(columns[first][1]);
            for (int i = 0; i < columns.length; i++) {
                if (i == first) continue;
                headers.add(columns[i][0]);
                values.add(columns[i][1]);
            }
            ProductCsv.Result result = ProductCsv.parse(String.join(",", headers) + "\n" + String.join(",", values) + "\n",
                    CATEGORIES, (name, category) -> false, code -> false);
            String where = columns[first][0] + " as the first column";
            assertTrue(where + ": " + result.problems(), result.problems().isEmpty());
            Product p = result.products().get(0);
            assertEquals(where, "Latte", p.getName());
            assertEquals(where, Integer.valueOf(2), p.getCategoryId());
            assertEquals(where, new BigDecimal("45000"), p.getPrice());
            assertEquals(where, new BigDecimal("20000"), p.getCost());
            assertEquals(where, 7, p.getStock());
            assertEquals(where, "ít đá", p.getDescription());
            assertFalse(where, p.isActive());
            assertEquals(where, Integer.valueOf(3), p.getReorderLevel());
            assertEquals(where, "XYZ789", p.getCode());
        }
    }

    @Test
    public void aWholeNumberOfItemsMustFitAnInt() {
        assertEquals(Integer.MAX_VALUE, ProductCsv.parseStock("2147483647"));
        assertEquals(Integer.MIN_VALUE, ProductCsv.parseStock("-2147483648"));
        for (String tooBig : new String[]{"2147483648", "-2147483649"}) {
            try {
                ProductCsv.parseStock(tooBig);
                org.junit.Assert.fail(tooBig + " does not fit an int");
            } catch (NumberFormatException expected) {
                // refused
            }
        }
    }
}
