package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.model.Category;
import com.coffeeshop.coffeeshopmanagement.model.Product;
import com.coffeeshop.coffeeshopmanagement.util.Csv;
import com.coffeeshop.coffeeshopmanagement.util.MoneyParser;
import com.coffeeshop.coffeeshopmanagement.util.TextLengthLimiter;
import com.coffeeshop.coffeeshopmanagement.util.TextNormalizer;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;

/**
 * Products as CSV, out and in (TODO 5.8). Export writes the columns below. Import only <em>adds</em> products: a row whose
 * name already exists in its category is skipped (never overwritten), and a row with any problem is skipped and reported by
 * line number, so one bad line does not lose the others. The file may use commas or semicolons (Excel in Vietnamese
 * settings saves with semicolons) and may carry the byte order mark. Pictures are not part of the file.
 */
public final class ProductCsv {

    public static final int MAX_ROWS = 5000;
    public static final String HEADER = "Tên sản phẩm,Danh mục,Giá bán (đ),Giá vốn (đ),Tồn kho,Mô tả,Đang bán";

    /** {@code products} are ready to insert; {@code problems} are plain sentences naming the line. */
    public record Result(List<Product> products, List<String> problems) {
    }

    private ProductCsv() {}

    public static List<String> export(List<Product> products) {
        List<String> lines = new ArrayList<>();
        lines.add(HEADER);
        for (Product p : products) {
            lines.add(String.join(",",
                    Csv.cell(p.getName()),
                    Csv.cell(p.getCategoryName()),
                    p.getPrice() == null ? "" : p.getPrice().toPlainString(),
                    p.getCost() == null ? "" : p.getCost().toPlainString(),
                    String.valueOf(p.getStock()),
                    Csv.cell(p.getDescription()),
                    p.isActive() ? "Có" : "Không"));
        }
        return lines;
    }

    /**
     * @param exists asks the database whether (name, category id) is taken already
     */
    public static Result parse(String text, List<Category> categories, BiPredicate<String, Integer> exists) {
        List<Product> products = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        List<List<String>> rows = split(text);
        if (rows.isEmpty()) {
            problems.add("File trống.");
            return new Result(products, problems);
        }
        List<String> header = rows.get(0);
        int name = column(header, "ten san pham", "ten", "name");
        int category = column(header, "danh muc", "category");
        int price = column(header, "gia ban", "gia", "price");
        int cost = column(header, "gia von", "cost");
        int stock = column(header, "ton kho", "so luong", "stock");
        int description = column(header, "mo ta", "description");
        int active = column(header, "dang ban", "active");
        if (name < 0 || price < 0 || category < 0) {
            problems.add("Dòng 1: thiếu cột bắt buộc. Cần có các cột \"Tên sản phẩm\", \"Danh mục\" và \"Giá bán\".");
            return new Result(products, problems);
        }
        if (rows.size() - 1 > MAX_ROWS) {
            problems.add("File có hơn " + MAX_ROWS + " dòng. Hãy chia nhỏ file.");
            return new Result(products, problems);
        }
        Set<String> seen = new HashSet<>();
        for (int i = 1; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            int line = i + 1;
            if (row.stream().allMatch(String::isBlank)) continue;
            String problem = null;
            Product p = new Product();
            String n = TextNormalizer.nfc(unprotect(cell(row, name)));
            if (n == null || n.isBlank()) problem = "thiếu tên sản phẩm";
            else if (n.length() > TextLengthLimiter.NAME_MAX) problem = "tên dài quá " + TextLengthLimiter.NAME_MAX + " ký tự";
            p.setName(n);
            Category found = null;
            if (problem == null) {
                String wanted = TextNormalizer.searchKey(cell(row, category));
                for (Category c : categories) {
                    if (TextNormalizer.searchKey(c.getName()).equals(wanted)) {
                        found = c;
                        break;
                    }
                }
                if (found == null) problem = "danh mục \"" + cell(row, category).trim() + "\" chưa có (hãy tạo danh mục trước)";
                else p.setCategoryId(found.getId());
            }
            if (problem == null) {
                try {
                    BigDecimal value = MoneyParser.parseDong(cell(row, price));
                    if (value == null || value.signum() <= 0) problem = "giá bán phải lớn hơn 0";
                    else p.setPrice(value);
                } catch (IllegalArgumentException e) {
                    problem = "giá bán không hợp lệ";
                }
            }
            if (problem == null && cost >= 0 && !cell(row, cost).isBlank()) {
                try {
                    p.setCost(MoneyParser.parseDong(cell(row, cost)));
                } catch (IllegalArgumentException e) {
                    problem = "giá vốn không hợp lệ";
                }
            }
            if (problem == null) {
                String s = stock < 0 ? "" : cell(row, stock).trim();
                if (s.isEmpty()) p.setStock(0);
                else {
                    try {
                        int v = Integer.parseInt(s.replace(".", "").replace(" ", ""));
                        if (v < 0 || v > 1_000_000) problem = "tồn kho phải từ 0 đến 1.000.000";
                        else p.setStock(v);
                    } catch (NumberFormatException e) {
                        problem = "tồn kho phải là số nguyên";
                    }
                }
            }
            if (problem == null) {
                String d = description < 0 ? "" : TextNormalizer.nfc(unprotect(cell(row, description)));
                if (d != null && d.length() > TextLengthLimiter.DESCRIPTION_MAX) problem = "mô tả dài quá " + TextLengthLimiter.DESCRIPTION_MAX + " ký tự";
                else p.setDescription(d == null || d.isEmpty() ? null : d);
            }
            if (problem == null) {
                String a = active < 0 ? "" : TextNormalizer.searchKey(cell(row, active));
                p.setActive(!(a.equals("khong") || a.equals("0") || a.equals("false") || a.equals("no")));
                String key = found.getId() + "|" + TextNormalizer.key(n);
                if (!seen.add(key)) problem = "trùng với một dòng phía trên trong file";
                else if (exists.test(n, found.getId())) problem = "\"" + n + "\" đã có trong danh mục " + found.getName();
            }
            if (problem != null) problems.add("Dòng " + line + ": " + problem + ".");
            else products.add(p);
        }
        return new Result(products, problems);
    }

    private static String cell(List<String> row, int index) {
        return index >= 0 && index < row.size() ? row.get(index) : "";
    }

    /** Our own export protects a cell starting with = + - @ with a leading apostrophe; take it off again. */
    private static String unprotect(String value) {
        return value != null && value.length() > 1 && value.charAt(0) == '\'' && "=+-@".indexOf(value.charAt(1)) >= 0
                ? value.substring(1) : value;
    }

    private static int column(List<String> header, String... names) {
        for (int i = 0; i < header.size(); i++) {
            String h = TextNormalizer.searchKey(header.get(i)).replaceAll("\\s*\\(.*\\)\\s*$", "").trim();
            for (String n : names) if (h.equals(n)) return i;
        }
        return -1;
    }

    /** Splits CSV text into rows of cells: quotes, doubled quotes, line breaks inside quotes, comma or semicolon. */
    static List<List<String>> split(String text) {
        List<List<String>> rows = new ArrayList<>();
        if (text == null) return rows;
        if (text.startsWith("﻿")) text = text.substring(1);
        int firstEnd = text.indexOf('\n');
        String first = firstEnd < 0 ? text : text.substring(0, firstEnd);
        char delimiter = count(first, ';') > count(first, ',') ? ';' : ',';
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        cell.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    cell.append(c);
                }
            } else if (c == '"' && cell.length() == 0) {
                quoted = true;
            } else if (c == delimiter) {
                row.add(cell.toString());
                cell.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                row.add(cell.toString());
                cell.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
            } else {
                cell.append(c);
            }
        }
        if (cell.length() > 0 || !row.isEmpty()) {
            row.add(cell.toString());
            rows.add(row);
        }
        return rows;
    }

    private static int count(String s, char c) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) == c) n++;
        return n;
    }
}
