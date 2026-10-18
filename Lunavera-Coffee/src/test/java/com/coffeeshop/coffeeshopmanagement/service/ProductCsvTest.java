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
}
