package com.coffeeshop.coffeeshopmanagement.controller;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

/**
 * TODO 6.41: the receipt layout must not ship with believable demo values. If filling the receipt stops half-way (an
 * exception in {@code setInvoiceData}) the window would show them as if they were a real receipt. Reads the FXML as text, so
 * it needs no JavaFX.
 */
public class ReceiptLayoutTest {

    private static final Path RECEIPT = Path.of("src", "main", "resources", "fxml", "donthanhtoan.fxml");

    private static final Pattern TEXT_ATTRIBUTE = Pattern.compile("\\btext=\"([^\"]*)\"");

    /** An order code, a date or time, or an amount: what a real receipt would fill in. */
    private static final Pattern LOOKS_LIKE_DATA = Pattern.compile(
            "#?DH\\d+"                              // DH000124
                    + "|\\d{1,2}/\\d{1,2}/\\d{4}"   // 26/09/2026
                    + "|\\d{1,2}:\\d{2}"            // 17:25
                    + "|\\d{1,3}([.,]\\d{3})+\\s*(đ|₫|VND)?" // 150.000 đ
                    + "|\\d+\\s*(đ|₫|VND)");

    @Test
    public void noLabelCarriesAnOrderCodeADateOrAnAmount() throws IOException {
        String xml = Files.readString(RECEIPT, StandardCharsets.UTF_8);
        Matcher m = TEXT_ATTRIBUTE.matcher(xml);
        int labels = 0;
        while (m.find()) {
            labels++;
            String text = m.group(1);
            assertFalse("the receipt layout contains demo data: \"" + text + "\"", LOOKS_LIKE_DATA.matcher(text).find());
        }
        assertTrue("the check read no labels at all; is the path right?", labels > 10);
    }

    @Test
    public void theFilledInValueLabelsAreEmptyInTheLayout() throws IOException {
        // Labels with an fx:id are filled by HoaDonController; if one has text here, that text is what shows when filling fails.
        // A caption that only names a field ("Thanh toán lúc:") has an id ending in "CaptionLabel" and keeps its text.
        String xml = Files.readString(RECEIPT, StandardCharsets.UTF_8);
        Matcher m = Pattern.compile("<Label\\b[^>]*?fx:id=\"([^\"]+)\"[^>]*?>", Pattern.DOTALL).matcher(xml);
        while (m.find()) {
            Matcher t = TEXT_ATTRIBUTE.matcher(m.group());
            if (!m.group(1).endsWith("CaptionLabel") && t.find()) {
                assertTrue("label " + m.group(1) + " is filled in by code but has fixed text \"" + t.group(1) + "\"",
                        t.group(1).isBlank());
            }
        }
    }
}
