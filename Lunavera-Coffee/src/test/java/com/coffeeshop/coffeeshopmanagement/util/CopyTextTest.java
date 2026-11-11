package com.coffeeshop.coffeeshopmanagement.util;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * TODO 5.46: the text a "Sao chép" button or a right-click menu copies is the text the screen shows, built in {@link CopyText}: a gift
 * card's code, a transfer note, an order number, a phone, an error box, a table row. Pure Java: no JavaFX, no clipboard.
 */
public class CopyTextTest {

    @Test
    public void theOrderNumberIsWrittenAsTheHistoryListWritesIt() {
        assertEquals("#124", CopyText.orderNumber(124));
        assertEquals("#7", CopyText.orderNumber(7));
    }

    @Test
    public void theTransferNoteIsTheOneThePaymentBoxShows() {
        assertEquals("DH000124", CopyText.transferNote(124));
        assertEquals("the same function the payment box uses", OrderNumber.transferNote(31), CopyText.transferNote(31));
    }

    @Test
    public void anOrderNotSavedYetHasNoTransferNoteToCopy() {
        assertEquals("", CopyText.transferNote(0));
        assertEquals("", CopyText.transferNote(-1));
    }

    @Test
    public void aGiftCardCodeIsCopiedAsShownWithoutTheSpacesAroundIt() {
        assertEquals("GC-7K3M-9PQ2", CopyText.giftCardCode("GC-7K3M-9PQ2"));
        assertEquals("GC-7K3M-9PQ2", CopyText.giftCardCode("  GC-7K3M-9PQ2 \n"));
        assertEquals("", CopyText.giftCardCode(null));
    }

    @Test
    public void aPhoneIsCopiedAsTheListShowsItAndADashIsNothing() {
        assertEquals("0912345678", CopyText.phone("0912345678"));
        assertEquals("0912 345 678", CopyText.phone(" 0912 345 678 "));
        assertEquals("the list shows a dash when there is no phone: nothing to copy", "", CopyText.phone("-"));
        assertEquals("", CopyText.phone(null));
        assertEquals("", CopyText.phone("   "));
    }

    @Test
    public void anErrorBoxIsCopiedAsItsTitleAndItsText() {
        assertEquals("Không lưu được\nTên đã có người dùng.", CopyText.error("Không lưu được", "Tên đã có người dùng."));
        assertEquals("only the text when there is no title", "Lỗi mạng", CopyText.error(null, " Lỗi mạng "));
        assertEquals("only the title when there is no text", "Lỗi", CopyText.error("Lỗi", ""));
        assertEquals("", CopyText.error(null, null));
    }

    @Test
    public void aRowIsItsShownCellsSeparatedByTabs() {
        assertEquals("1\tCà phê sữa\t25.000đ\tĐang bán", CopyText.row(List.of("1", "Cà phê sữa", "25.000đ", "Đang bán")));
    }

    @Test
    public void anEmptyCellKeepsItsPlaceSoTheOthersLineUpInASpreadsheet() {
        assertEquals("An\t\tan@example.com", CopyText.row(Arrays.asList("An", "", "an@example.com")));
        assertEquals("a missing cell is an empty one", "An\t\tx", CopyText.row(Arrays.asList("An", null, "x")));
    }

    @Test
    public void emptyCellsAtTheEndAreDroppedAndSoAreTabsAndLineBreaksInsideACell() {
        assertEquals("An\t0912", CopyText.row(Arrays.asList("An", "0912", "", "")));
        assertEquals("one cell cannot spill into the next or onto a new line", "a b c\td", CopyText.row(Arrays.asList("a\tb\nc", "d")));
        assertEquals("", CopyText.row(List.of()));
        assertEquals("", CopyText.row(Arrays.asList("", null)));
    }
}
