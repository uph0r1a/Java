package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.util.I18n;
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
 * line number, so one bad line does not lose the others. The file may use commas, semicolons or tabs (Excel in Vietnamese
 * settings saves with semicolons; "Text (Tab delimited)" and "Unicode Text" use tabs) and may carry the byte order mark. Pictures are not part of the file.
 */
public final class ProductCsv {

    public static final int MAX_ROWS = 5000;
    public static final String HEADER = "Tên sản phẩm,Danh mục,Giá bán (đ),Giá vốn (đ),Tồn kho,Mô tả,Đang bán"
            + ",M\u1ee9c b\u00e1o nh\u1eadp";

    /**
     * {@code products} are ready to insert; {@code problems} are plain sentences naming the line (those rows are skipped);
     * {@code warnings} name rows that are imported anyway but look wrong: a cost above the price, a row with more or fewer cells
     * than the header (TODO 2.81).
     */
    public record Result(List<Product> products, List<String> problems, List<String> warnings) {
        public Result(List<Product> products, List<String> problems) {
            this(products, problems, List.of());
        }
    }

    /** What the "Đang bán" column may say; anything else is a problem line instead of silently meaning "on sale" (TODO 2.81). */
    private static final Set<String> YES = Set.of("co", "1", "true", "yes", "x", "dang ban", "ban");
    private static final Set<String> NO = Set.of("khong", "0", "false", "no", "ngung ban", "ngung");

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
                    p.isActive() ? "Có" : "Không",
                    p.getReorderLevel() == null ? "" : String.valueOf(p.getReorderLevel())));
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
            problems.add(I18n.t("csv.empty"));
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
        int reorder = column(header, "muc bao nhap", "muc bao nhap hang", "reorder level", "reorder");
        if (name < 0 || price < 0 || category < 0) {
            problems.add(I18n.t("csv.nocolumns"));
            return new Result(products, problems);
        }
        if (rows.size() - 1 > MAX_ROWS) {
            problems.add(I18n.t("csv.toomany", MAX_ROWS));
            return new Result(products, problems);
        }
        Set<String> seen = new HashSet<>();
        List<String> warnings = new ArrayList<>();
        for (int i = 1; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            int line = i + 1;
            if (row.stream().allMatch(String::isBlank)) continue;
            String problem = null;
            Product p = new Product();
            String n = TextNormalizer.nfc(unprotect(cell(row, name)));
            if (n == null || n.isBlank()) problem = I18n.t("csv.p.noname");
            else if (n.length() > TextLengthLimiter.NAME_MAX) problem = I18n.t("csv.p.namelong", TextLengthLimiter.NAME_MAX);
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
                if (found == null) problem = I18n.t("csv.p.nocategory", cell(row, category).trim());
                else p.setCategoryId(found.getId());
            }
            if (problem == null) {
                try {
                    BigDecimal value = MoneyParser.parseDong(cell(row, price));
                    if (value == null || value.signum() <= 0) problem = I18n.t("csv.p.price0");
                    else if (Limits.priceTooBig(value)) problem = I18n.t("csv.p.pricemax", Limits.maxPriceText());
                    else p.setPrice(value);
                } catch (IllegalArgumentException e) {
                    problem = I18n.t("csv.p.priceinvalid");
                }
            }
            if (problem == null && cost >= 0 && !cell(row, cost).isBlank()) {
                try {
                    BigDecimal costValue = MoneyParser.parseDong(cell(row, cost));
                    if (Limits.priceTooBig(costValue)) problem = I18n.t("csv.p.pricemax", Limits.maxPriceText());
                    else p.setCost(costValue);
                } catch (IllegalArgumentException e) {
                    problem = I18n.t("csv.p.costinvalid");
                }
            }
            if (problem == null) {
                String s = stock < 0 ? "" : cell(row, stock).trim();
                if (s.isEmpty()) p.setStock(0);
                else {
                    try {
                        int v = parseStock(s);
                        if (v < 0 || v > Limits.MAX_STOCK) problem = I18n.t("csv.p.stockrange");
                        else p.setStock(v);
                    } catch (NumberFormatException e) {
                        problem = I18n.t("csv.p.stockint");
                    }
                }
            }
            if (problem == null && reorder >= 0 && !cell(row, reorder).isBlank()) {
                Integer level = StockLevel.parse(cell(row, reorder));
                if (level == null) problem = I18n.t("csv.p.reorderinvalid", Limits.MAX_STOCK);
                else p.setReorderLevel(level);
            }
            if (problem == null) {
                String d = description < 0 ? "" : TextNormalizer.nfc(unprotect(cell(row, description)));
                if (d != null && d.length() > TextLengthLimiter.DESCRIPTION_MAX) problem = I18n.t("csv.p.desclong", TextLengthLimiter.DESCRIPTION_MAX);
                else p.setDescription(d == null || d.isEmpty() ? null : d);
            }
            if (problem == null) {
                String a = active < 0 ? "" : TextNormalizer.searchKey(cell(row, active)).trim();
                if (a.isEmpty() || YES.contains(a)) p.setActive(true);          // an empty cell, or no such column: on sale, as before
                else if (NO.contains(a)) p.setActive(false);
                else problem = I18n.t("csv.p.activeinvalid", cell(row, active).trim());
            }
            if (problem == null) {
                String key = found.getId() + "|" + TextNormalizer.key(n);
                if (!seen.add(key)) problem = I18n.t("csv.p.duplicate");
                else if (exists.test(n, found.getId())) problem = I18n.t("csv.p.exists", n, found.getName());
            }
               if (problem != null) {
                   problems.add(I18n.t("csv.line", line, problem));
               } else {
                   products.add(p);
                   if (p.getCost() != null && p.getPrice() != null && p.getCost().compareTo(p.getPrice()) > 0) {
                       warnings.add(I18n.t("csv.line", line,
                               I18n.t("csv.warn.cost", p.getCost().toPlainString(), p.getPrice().toPlainString())));
                   }
                   if (row.size() != header.size()) {
                       warnings.add(I18n.t("csv.line", line, I18n.t(
                               row.size() > header.size() ? "csv.warn.cells.extra" : "csv.warn.cells.missing",
                               row.size(), header.size())));
                   }
               }
        }
        return new Result(products, problems, warnings);
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

    /**
     * The separator of a file whose first line is {@code first}: the one of comma, semicolon and tab that is used most; a comma
     * when none is (TODO 3.61: Excel's "Text (Tab delimited)" and "Unicode Text" use tabs, and were refused as having no columns).
     */
    static char delimiterOf(String first) {
        int commas = count(first, ','), semicolons = count(first, ';'), tabs = count(first, '\t');
        if (tabs > commas && tabs > semicolons) return '\t';
        return semicolons > commas ? ';' : ',';
    }

    /**
     * Turns the bytes of a picked file into text: UTF-16 when it starts with a UTF-16 byte-order mark (what Excel's "Unicode Text"
     * writes), UTF-8 otherwise (with or without its BOM, which {@link #parse} skips) (TODO 3.61).
     */
    public static String decode(byte[] bytes) {
        if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xFE) {
            return new String(bytes, 2, bytes.length - 2, java.nio.charset.StandardCharsets.UTF_16LE);
        }
        if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFE && (bytes[1] & 0xFF) == 0xFF) {
            return new String(bytes, 2, bytes.length - 2, java.nio.charset.StandardCharsets.UTF_16BE);
        }
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }

    /** Splits CSV text into rows of cells: quotes, doubled quotes, line breaks inside quotes, comma or semicolon. */
    static List<List<String>> split(String text) {
        List<List<String>> rows = new ArrayList<>();
        if (text == null) return rows;
        if (text.startsWith("﻿")) text = text.substring(1);
        // Our own export starts with "sep=," for Excel (TODO 3.49): skip that line and use the separator it names.
        char declared = 0;
        if (text.regionMatches(true, 0, "sep=", 0, 4)) {
            int end = 4;
            while (end < text.length() && text.charAt(end) != '\n' && text.charAt(end) != '\r') end++;
            if (end - 4 == 1) declared = text.charAt(4);
            while (end < text.length() && (text.charAt(end) == '\n' || text.charAt(end) == '\r')) end++;
            text = text.substring(end);
        }
        int firstEnd = text.indexOf('\n');
        String first = firstEnd < 0 ? text : text.substring(0, firstEnd);
        char delimiter = declared != 0 ? declared : delimiterOf(first);
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

    /**
     * A whole number of items (TODO 3.46). A dot is only a thousands separator when it sits in a group of exactly three digits
     * ({@code 1.000}, {@code 12.500.000}); so {@code 1.5}, {@code 2.50} or {@code 0.5}, which Excel writes for a fraction, are
     * refused instead of being read as 15, 250 and 5. A comma is never accepted. Spaces (also no-break ones) group digits too.
     * @throws NumberFormatException when the text is not a whole number
     */
    static int parseStock(String text) {
        String s = text.trim().replace('\u00A0', ' ').replace('\u202F', ' ');
        String digits = s.startsWith("-") ? s.substring(1) : s;
        boolean plain = digits.matches("[0-9]+");
        boolean dotted = digits.matches("[0-9]{1,3}(\\.[0-9]{3})+");
        boolean spaced = digits.matches("[0-9]{1,3}( [0-9]{3})+");
        if (!plain && !dotted && !spaced) throw new NumberFormatException(text);
        return Integer.parseInt((s.startsWith("-") ? "-" : "") + digits.replace(".", "").replace(" ", ""));
    }
}
