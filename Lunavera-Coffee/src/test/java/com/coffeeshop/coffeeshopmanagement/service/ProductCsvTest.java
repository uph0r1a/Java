package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.model.Category;
import com.coffeeshop.coffeeshopmanagement.model.Product;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.Assert.*;

public class ProductCsvTest {

    private final List<Category> categories = List.of(new Category(1, "Cà phê", null, true), new Category(2, "Trà", null, true));

    private ProductCsv.Result parse(String text) {
        return ProductCsv.parse(text, categories, (name, cat) -> name.equals("Đã có") && cat == 1);
    }

    @Test
    public void exportThenImportGivesTheSameProducts() {
        Product p = new Product();
        p.setName("Cà phê, sữa \"đá\"");
        p.setCategoryName("Cà phê");
        p.setPrice(BigDecimal.valueOf(29000));
        p.setCost(BigDecimal.valueOf(11000));
        p.setStock(7);
        p.setDescription("=bad");
        p.setActive(false);
        Product q = new Product();
        q.setName("=SUM(1)");
        q.setCategoryName("Trà");
        q.setPrice(BigDecimal.valueOf(39000));
        q.setActive(true);
        String text = "﻿" + String.join("\r\n", ProductCsv.export(List.of(p, q))) + "\r\n";
        ProductCsv.Result result = parse(text);
        assertEquals(List.of(), result.problems());
        assertEquals(2, result.products().size());
        Product back = result.products().get(0);
        assertEquals("Cà phê, sữa \"đá\"", back.getName());
        assertEquals(1, (int) back.getCategoryId());
        assertEquals(0, BigDecimal.valueOf(11000).compareTo(back.getCost()));
        assertEquals(7, back.getStock());
        assertEquals("=bad", back.getDescription());
        assertFalse(back.isActive());
        assertEquals("a formula-looking name survives the round trip", "=SUM(1)", result.products().get(1).getName());
        assertNull(result.products().get(1).getCost());
    }

    @Test
    public void semicolonFilesFromExcelAreUnderstood() {
        ProductCsv.Result r = parse("Tên sản phẩm;Danh mục;Giá bán\nBạc xỉu;ca phe;25.000\n");
        assertEquals(List.of(), r.problems());
        assertEquals(0, BigDecimal.valueOf(25000).compareTo(r.products().get(0).getPrice()));
        assertEquals("category names match without accents", 1, (int) r.products().get(0).getCategoryId());
    }

    @Test
    public void badLinesAreReportedByNumberAndTheGoodOnesStillCome() {
        String text = "Tên,Danh mục,Giá bán,Tồn kho\n"
                + "Tốt,Trà,30000,5\n"          // line 2: fine
                + ",Trà,30000,5\n"             // line 3: no name
                + "A,Nước ngọt,30000,5\n"      // line 4: unknown category
                + "B,Trà,0,5\n"               // line 5: price 0
                + "C,Trà,abc,5\n"             // line 6: price text
                + "D,Trà,30000,-1\n"          // line 7: negative stock
                + "Đã có,Cà phê,30000,5\n"    // line 8: exists
                + "Tốt,Trà,31000,5\n";        // line 9: twice in the file
        ProductCsv.Result r = parse(text);
        assertEquals(1, r.products().size());
        assertEquals(7, r.problems().size());
        assertTrue(r.problems().get(0).startsWith("Dòng 3:"));
        assertTrue(r.problems().get(6).startsWith("Dòng 9:"));
    }

    @Test
    public void aFileWithoutTheRequiredColumnsIsRefused() {
        ProductCsv.Result r = parse("Foo,Bar\n1,2\n");
        assertTrue(r.products().isEmpty());
        assertEquals(1, r.problems().size());
        assertTrue(parse("").problems().get(0).contains("trống"));
    }

    @Test
    public void quotedCellsMayHoldLineBreaks() {
        ProductCsv.Result r = parse("Tên,Danh mục,Giá bán,Mô tả\n\"Trà\nđào\",Trà,30000,\"ngon, mát\"\n");
        assertEquals(List.of(), r.problems());
        assertEquals("Trà\nđào", r.products().get(0).getName());
        assertEquals("ngon, mát", r.products().get(0).getDescription());
    }

    @Test
    public void theOnSaleColumnOnlyAcceptsAnExplicitWord() {
        // TODO 2.81
        String head = "Tên,Danh mục,Giá bán,Đang bán\n";
        ProductCsv.Result ok = parse(head + "A1,Trà,10000,có\nA2,Trà,10000,Không\nA3,Trà,10000,ngừng bán\nA4,Trà,10000,1\nA5,Trà,10000,\nA6,Trà,10000,x\n");
        assertEquals(List.of(), ok.problems());
        assertEquals(List.of(true, false, false, true, true, true),
                ok.products().stream().map(Product::isActive).toList());
        ProductCsv.Result bad = parse(head + "B1,Trà,10000,Hết\nB2,Trà,10000,tạm ngưng\nB3,Trà,10000,off\nB4,Trà,10000,n\n");
        assertEquals(0, bad.products().size());
        assertEquals(4, bad.problems().size());
        assertTrue(bad.problems().get(0), bad.problems().get(0).startsWith("Dòng 2:") && bad.problems().get(0).contains("Đang bán"));
    }

    @Test
    public void aCostAboveThePriceAndRaggedRowsAreImportedWithAWarning() {
        ProductCsv.Result r = parse("Tên,Danh mục,Giá bán,Giá vốn\nC1,Trà,45000,90000\nC2,Trà,45000,10000,extra,more\nC3,Trà,45000\n");
        assertEquals(List.of(), r.problems());
        assertEquals("all three rows are still importable", 3, r.products().size());
        assertEquals(r.warnings().toString(), 3, r.warnings().size());
        assertTrue(r.warnings().get(0).startsWith("Dòng 2:") && r.warnings().get(0).contains("giá vốn"));
        assertTrue(r.warnings().get(1).startsWith("Dòng 3:") && r.warnings().get(1).contains("6 ô"));
        assertTrue(r.warnings().get(2).startsWith("Dòng 4:") && r.warnings().get(2).contains("3 ô"));
    }

    /** TODO 3.46: a dot is a thousands separator only in a group of exactly three digits. */
    @Test
    public void stockWithThousandsSeparatorsIsAWholeNumber() {
        assertEquals(1000, ProductCsv.parseStock("1.000"));
        assertEquals(12_500, ProductCsv.parseStock("12.500"));
        assertEquals(1_000_000, ProductCsv.parseStock("1.000.000"));
        assertEquals(1000, ProductCsv.parseStock("1 000"));
        assertEquals(1000, ProductCsv.parseStock("1\u00A0000"));
        assertEquals(42, ProductCsv.parseStock(" 42 "));
        assertEquals(0, ProductCsv.parseStock("0"));
    }

    @Test
    public void stockWithAFractionOrOddGroupingIsRefusedNotRead() {
        for (String bad : new String[]{"1.5", "2.50", "0.5", "1.5000", "1,5", "1,000", "12.34.567", ".500", "1.", "abc", "1e3", "", "1 5"}) {
            try {
                ProductCsv.parseStock(bad);
                fail("should be refused: " + bad);
            } catch (NumberFormatException expected) {
                // the CSV line is reported with "tồn kho phải là số nguyên"
            }
        }
    }

    /** TODO 3.49: our own export starts with "sep=," for Excel; reading it back must skip that line. */
    @Test
    public void aFileWithTheSepLineIsReadLikeOneWithout() {
        Product p = new Product();
        p.setName("Trà đào");
        p.setCategoryName("Trà");
        p.setPrice(BigDecimal.valueOf(35000));
        p.setActive(true);
        String body = String.join("\r\n", ProductCsv.export(List.of(p))) + "\r\n";
        for (String prefix : new String[] {"\uFEFFsep=,\r\n", "sep=,\n", "\uFEFFSEP=,\r\n"}) {
            ProductCsv.Result result = parse(prefix + body);
            assertEquals(prefix, List.of(), result.problems());
            assertEquals(prefix, 1, result.products().size());
            assertEquals("Trà đào", result.products().get(0).getName());
        }
    }

    @Test
    public void aSepLineNamingTheSemicolonIsHonoured() {
        List<List<String>> rows = ProductCsv.split("sep=;\r\nTên;Giá\r\nTrà;35000\r\n");
        assertEquals(List.of(List.of("Tên", "Giá"), List.of("Trà", "35000")), rows);
    }

    /** TODO 3.59: an extra zero in a price or cost is a problem line, not a saved product. */
    @Test
    public void aPriceOrCostAboveTheCeilingIsAProblemLine() {
        ProductCsv.Result r = parse("Tên,Danh mục,Giá bán,Giá vốn,Tồn kho\n"
                + "Tốt,Trà,30000,10000,5\n"
                + "Giá sai,Trà,300000000000,10000,5\n"
                + "Vốn sai,Trà,30000,20000000000,5\n"
                + "Biên,Trà,100000000,1,5\n"
                + "Kho sai,Trà,30000,10000,2000000\n");
        assertEquals(2, r.products().size());
        assertEquals(3, r.problems().size());
        assertTrue(r.problems().get(0).contains("3"));
        assertTrue(r.problems().get(1).contains("4"));
        assertTrue(r.problems().get(2).contains("6"));
    }

    /** TODO 3.61: Excel's tab-separated saves are read, not refused as "missing columns". */
    @Test
    public void tabSeparatedFilesAreUnderstood() {
        ProductCsv.Result r = parse("Tên sản phẩm\tDanh mục\tGiá bán\tTồn kho\r\nBạc xỉu\tca phe\t25.000\t7\r\n");
        assertEquals(List.of(), r.problems());
        assertEquals(1, r.products().size());
        assertEquals(0, BigDecimal.valueOf(25000).compareTo(r.products().get(0).getPrice()));
        assertEquals(7, r.products().get(0).getStock());
    }

    @Test
    public void theSeparatorIsTheMostUsedOfCommaSemicolonAndTab() {
        assertEquals('\t', ProductCsv.delimiterOf("a\tb\tc"));
        assertEquals(',', ProductCsv.delimiterOf("a,b,c"));
        assertEquals(';', ProductCsv.delimiterOf("a;b;c"));
        assertEquals("a comma inside a tab file's header does not win", '\t', ProductCsv.delimiterOf("Tên, mô tả\tDanh mục\tGiá"));
        assertEquals(',', ProductCsv.delimiterOf("single"));
    }

    @Test
    public void unicodeTextFromExcelIsReadAsUtf16() {
        String text = "Tên sản phẩm\tDanh mục\tGiá bán\r\nBạc xỉu\tTrà\t25000\r\n";
        byte[] body = text.getBytes(java.nio.charset.StandardCharsets.UTF_16LE);
        byte[] withBom = new byte[body.length + 2];
        withBom[0] = (byte) 0xFF; withBom[1] = (byte) 0xFE;
        System.arraycopy(body, 0, withBom, 2, body.length);
        ProductCsv.Result r = parse(ProductCsv.decode(withBom));
        assertEquals(List.of(), r.problems());
        assertEquals("Bạc xỉu", r.products().get(0).getName());
        assertEquals("utf-8 still works", text, ProductCsv.decode(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Test
    public void theReorderLevelSurvivesExportAndImportAndEmptyMeansDefault() {
        Product p = new Product();
        p.setName("Tr\u00e0 \u0111\u00e0o");
        p.setCategoryName("Tr\u00e0");
        p.setPrice(BigDecimal.valueOf(39000));
        p.setReorderLevel(40);
        p.setActive(true);
        Product q = new Product();
        q.setName("Soda");
        q.setCategoryName("Tr\u00e0");
        q.setPrice(BigDecimal.valueOf(20000));
        q.setActive(true);
        ProductCsv.Result result = parse("\uFEFF" + String.join("\r\n", ProductCsv.export(List.of(p, q))) + "\r\n");
        assertEquals(List.of(), result.problems());
        assertEquals(Integer.valueOf(40), result.products().get(0).getReorderLevel());
        assertNull(result.products().get(1).getReorderLevel());
    }

    @Test
    public void aBadReorderLevelIsAProblemLineAndAFileWithoutTheColumnStillImports() {
        String bad = "T\u00ean,Danh m\u1ee5c,Gi\u00e1 b\u00e1n,M\u1ee9c b\u00e1o nh\u1eadp\nA,Tr\u00e0,10000,abc\n";
        assertEquals(1, parse(bad).problems().size());
        String without = "T\u00ean,Danh m\u1ee5c,Gi\u00e1 b\u00e1n\nA,Tr\u00e0,10000\n";
        ProductCsv.Result ok = parse(without);
        assertEquals(List.of(), ok.problems());
        assertNull(ok.products().get(0).getReorderLevel());
    }

    // ---------------------------------------------------------------- TODO 4.42: the barcode column

    private static final String HEAD = "T\u00ean,Danh m\u1ee5c,Gi\u00e1 b\u00e1n,M\u00e3 v\u1ea1ch\n";

    @Test
    public void theBarcodeIsExportedAndImportedInCapitals() {
        Product p = new Product();
        p.setName("Latte");
        p.setCategoryName("Cà phê");
        p.setPrice(BigDecimal.valueOf(45000));
        p.setActive(true);
        p.setCode("CF01");
        ProductCsv.Result back = parse("\uFEFF" + String.join("\r\n", ProductCsv.export(List.of(p))) + "\r\n");
        assertEquals(List.of(), back.problems());
        assertEquals("CF01", back.products().get(0).getCode());

        ProductCsv.Result typed = parse(HEAD + "A,Tr\u00e0,10000, cf-02 \n");
        assertEquals("CF-02", typed.products().get(0).getCode());
    }

    @Test
    public void aBlankBarcodeIsNoCodeAndAFileWithoutTheColumnStillImports() {
        ProductCsv.Result blank = parse(HEAD + "A,Tr\u00e0,10000,\n");
        assertEquals(List.of(), blank.problems());
        assertNull(blank.products().get(0).getCode());
        ProductCsv.Result without = parse("T\u00ean,Danh m\u1ee5c,Gi\u00e1 b\u00e1n\nA,Tr\u00e0,10000\n");
        assertNull(without.products().get(0).getCode());
    }

    @Test
    public void aBadOrRepeatedOrTakenBarcodeIsAProblemLine() {
        ProductCsv.Result r = ProductCsv.parse(HEAD
                + "A,Tr\u00e0,10000,8934\nB,Tr\u00e0,10000,8934\nC,Tr\u00e0,10000,bad code\nD,Tr\u00e0,10000,7777\n",
                categories, (name, cat) -> false, code -> code.equals("7777"));
        assertEquals(1, r.products().size());
        assertEquals("A", r.products().get(0).getName());
        assertEquals(3, r.problems().size());
    }
}

