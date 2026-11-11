package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.util.I18n;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * TODO 5.46: copying with one click is wired where the item says: a "Sao chép" button next to a gift card's code (when it is sold), the
 * transfer note and an error box, and a right-click menu on the order history, customer, product and gift card tables that does what
 * the window's buttons do. Reads the sources as text (no JavaFX): the menus are made in one class, the clipboard is touched in one
 * class, what is copied comes from {@code CopyText}, and none of it can reach a password, a PIN or the transfer account.
 */
public class CopyWiringSourceTest {

    private static final Path JAVA = Path.of("src", "main", "java", "com", "coffeeshop", "coffeeshopmanagement");

    private static String read(String relative) throws IOException {
        return Files.readString(JAVA.resolve(relative), StandardCharsets.UTF_8);
    }

    private static String method(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue("method not found: " + signature, start >= 0);
        int end = source.indexOf("\n    }\n", start);
        assertTrue(end > start);
        return source.substring(start, end);
    }

    // ---- one place for the clipboard and one for the menus

    @Test
    public void onlyClipboardTextTouchesTheSystemClipboard() throws IOException {
        try (Stream<Path> files = Files.walk(JAVA)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                String text = Files.readString(file, StandardCharsets.UTF_8);
                if (file.getFileName().toString().equals("ClipboardText.java")) continue;
                assertFalse(file + " uses the clipboard itself", text.contains("getSystemClipboard"));
            }
        }
    }

    @Test
    public void rightClickMenusAreMadeInCopyMenusOnly() throws IOException {
        try (Stream<Path> files = Files.walk(JAVA)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                String text = Files.readString(file, StandardCharsets.UTF_8);
                if (file.getFileName().toString().equals("CopyMenus.java")) continue;
                assertFalse(file + " builds a context menu itself", text.contains("new ContextMenu") || text.contains("setContextMenu("));
            }
        }
    }

    // ---- the four tables

    @Test
    public void theFourBigTablesHaveTheMenu() throws IOException {
        assertTrue(read("controller/OrderHistoryWindow.java").contains("CopyMenus.history(table, viewButton, cancelButton, refundLinesButton)"));
        assertTrue(read("controller/CustomerController.java").contains("CopyMenus.customers(customerTable, this::openEditDialog, history)"));
        assertTrue(read("controller/ProductController.java").contains("CopyMenus.products(productTable, this::openEditProductDialog, this::deleteProduct)"));
        assertTrue(read("controller/GiftCardListDialog.java").contains("CopyMenus.install(table,"));
    }

    @Test
    public void theMenuPressesTheButtonsOfTheWindowSoTheActionsAreTheSame() throws IOException {
        String menus = read("controller/CopyMenus.java");
        String item = method(menus, "static <T> Item<T> button(Button button)");
        assertTrue("the text follows the button", item.contains("textProperty().bind(button.textProperty())"));
        assertTrue("it is off when the button is off", item.contains("disableProperty().bind(button.disableProperty())"));
        assertTrue("and it presses the button", item.contains("button.fire()"));
        assertTrue("every button of the history window is in its menu", method(menus, "static void history(").contains("button(button)"));
        assertTrue("the gift card menu has the void button", read("controller/GiftCardListDialog.java").contains("CopyMenus.button(voidButton)"));
        assertTrue("the customer menu has the purchase history button", method(menus, "static void customers(").contains("button(history)"));
    }

    @Test
    public void aRightClickSelectsTheRowUnderItFirst() throws IOException {
        String menus = read("controller/CopyMenus.java");
        assertTrue(menus.contains("MouseButton.SECONDARY"));
        assertTrue(method(menus, "private static <T> void selectRowUnder(").contains("table.getSelectionModel().select(row.getIndex())"));
    }

    @Test
    public void theRowIsCopiedFromTheColumnsThatShowAValueAndNotTheButtonColumns() throws IOException {
        String row = method(read("controller/CopyMenus.java"), "private static <T> String rowText(");
        assertTrue(row.contains("getVisibleLeafColumns()"));
        assertTrue(row.contains("column.getCellValueFactory() == null"));
        assertTrue(row.contains("CopyText.row(cells)"));
    }

    @Test
    public void theMenuValuesComeFromCopyText() throws IOException {
        String menus = read("controller/CopyMenus.java");
        assertTrue(menus.contains("CopyText.orderNumber(s.order().getId())"));
        assertTrue(menus.contains("CopyText.transferNote(s.order().getId())"));
        assertTrue(menus.contains("CopyText.phone(c.getPhone())"));
        assertTrue(read("controller/GiftCardListDialog.java").contains("CopyText.giftCardCode(r.card().code())"));
    }

    @Test
    public void theHistoryListWritesTheOrderNumberThroughTheSameFunctionThatIsCopied() throws IOException {
        assertTrue("shown and copied are one text", read("controller/OrderHistoryWindow.java").contains("CopyText.orderNumber(s.order().getId())))"));
    }

    // ---- the buttons next to a value

    @Test
    public void theGiftCardCodeHasACopyButtonThatShowsOnlyAfterACardIsSold() throws IOException {
        String dialog = read("controller/GiftCardDialog.java");
        assertTrue(dialog.contains("CopyMenus.button(() -> CopyText.giftCardCode(lastCode[0]))"));
        int hide = dialog.indexOf("copyCode.setVisible(false);\n            copyCode.setManaged(false);");
        int show = dialog.indexOf("copyCode.setVisible(true)");
        assertTrue("hidden when a new sale starts", hide > dialog.indexOf("sell.setOnAction("));
        assertTrue("shown with the code of the card just sold", show > dialog.indexOf("lastCode[0] = card.code()") - 1 && show > hide);
        assertTrue("and it is in the dialog", dialog.contains("sold, copyCode,"));
    }

    @Test
    public void theTransferNoteHasACopyButtonThatCopiesOnlyTheNote() throws IOException {
        String box = read("controller/PaymentBoxes.java");
        assertTrue(box.contains("CopyMenus.button(() -> com.coffeeshop.coffeeshopmanagement.util.CopyText.transferNote(noteOrderId))"));
        assertTrue("the same order the note was made for", box.contains("int noteOrderId = activeOpenOrder.getId();"));
    }

    @Test
    public void anErrorBoxHasACopyButtonThatKeepsItOpenAndCopiesItsTitleAndText() throws IOException {
        String alerts = read("util/AlertUtil.java");
        assertTrue("only on an error box", alerts.contains("if (type == Alert.AlertType.ERROR) addCopyButton(alert, title, message);"));
        String add = method(alerts, "private static void addCopyButton(");
        assertTrue(add.contains("ClipboardText.copy(CopyText.error(title, message))"));
        assertTrue("the box stays open so the person can still read and press Close", add.contains("e.consume()"));
    }

    // ---- what can never be copied

    @Test
    public void theCopyCodeNeverTouchesAPasswordAPinOrTheTransferAccount() throws IOException {
        Pattern secret = Pattern.compile("(?i)password|passwd|\\bpin\\b|accountInfo|TransferQrGuard|dataPicture|qr-transfer|Session\\.|PasswordUtil");
        for (String file : new String[]{"util/CopyText.java", "util/ClipboardText.java", "controller/CopyMenus.java"}) {
            String code = read(file).replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\n]*", ""); // the comments say what is NOT read
            Matcher m = secret.matcher(code);
            assertFalse(file + " mentions " + (m.find() ? m.group() : ""), secret.matcher(code).find());
        }
    }

    // ---- the words

    @Test
    public void everyCopyKeyIsInBothLanguages() throws IOException {
        Pattern key = Pattern.compile("\"((?:copy|common)\\.[a-z.]+)\"");
        java.util.Set<String> keys = new java.util.TreeSet<>();
        for (String file : new String[]{"controller/CopyMenus.java", "controller/OrderHistoryWindow.java", "controller/CustomerController.java",
                "controller/ProductController.java", "controller/GiftCardListDialog.java"}) {
            Matcher m = key.matcher(read(file));
            while (m.find()) if (m.group(1).startsWith("copy.") || m.group(1).equals("common.edit") || m.group(1).equals("common.delete")) keys.add(m.group(1));
        }
        for (String k : new String[]{"copy.button", "copy.done", "copy.row", "copy.phone", "copy.name", "copy.code", "copy.order", "copy.note"}) keys.add(k);
        for (String k : keys) {
            for (String language : new String[]{I18n.EN, I18n.VI}) {
                String text = I18n.in(language, k);
                assertNotEquals("missing key " + k + " in " + language, k, text);
                assertFalse(k, text.isBlank());
            }
        }
        assertEquals("Sao chép", I18n.in(I18n.VI, "copy.button"));
        assertEquals("Copy", I18n.in(I18n.EN, "copy.button"));
    }
}
