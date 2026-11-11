package com.coffeeshop.coffeeshopmanagement.util;

import com.coffeeshop.coffeeshopmanagement.service.AuditRules;
import com.coffeeshop.coffeeshopmanagement.service.OrderNote;
import com.coffeeshop.coffeeshopmanagement.service.PaymentDetails;
import com.coffeeshop.coffeeshopmanagement.service.StockRules;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** TODO 3.83: a text cut to its limit never ends in half an emoji. */
public class TextClipTest {

    private static final String EMOJI = "😀"; // one character, two chars

    private static String letters(int n) {
        return "a".repeat(n);
    }

    /** No unpaired surrogate anywhere in {@code text}. */
    private static boolean whole(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (i + 1 >= text.length() || !Character.isLowSurrogate(text.charAt(i + 1))) return false;
                i++;
            } else if (Character.isLowSurrogate(c)) {
                return false;
            }
        }
        return true;
    }

    @Test
    public void shortTextAndNullAreUntouched() {
        assertNull(TextClip.clip(null, 5));
        assertEquals("abc", TextClip.clip("abc", 5));
        assertEquals("abc", TextClip.clip("abc", 3));
        assertEquals("", TextClip.clip("abc", 0));
    }

    @Test
    public void aCutInsideAnEmojiDropsTheWholeEmoji() {
        String text = letters(4) + EMOJI + "b";
        assertEquals("the cut at 5 would split the emoji", letters(4), TextClip.clip(text, 5));
        assertEquals("a cut after the emoji keeps it", letters(4) + EMOJI, TextClip.clip(text, 6));
        assertEquals(letters(3), TextClip.clip(text, 3));
        assertTrue(whole(TextClip.clip(text, 5)));
    }

    @Test
    public void everyCutPointGivesWholeCharacters() {
        String text = "x" + EMOJI + "y" + EMOJI + EMOJI + "z";
        for (int max = 0; max <= text.length() + 1; max++) {
            String cut = TextClip.clip(text, max);
            assertTrue("max " + max, whole(cut));
            assertTrue(cut.length() <= max);
            assertTrue(text.startsWith(cut));
        }
    }

    @Test
    public void theOrderNoteIsNotCutThroughAnEmoji() {
        String typed = letters(OrderNote.MAX_LENGTH - 1) + EMOJI;
        String note = OrderNote.clean(typed);
        assertTrue(whole(note));
        assertEquals(letters(OrderNote.MAX_LENGTH - 1), note);
    }

    @Test
    public void theStockTextIsNotCutThroughAnEmoji() {
        String text = StockRules.cleanText(letters(StockRules.TEXT_MAX - 1) + EMOJI);
        assertTrue(whole(text));
        assertFalse(text.endsWith("?"));
    }

    @Test
    public void theAuditTextIsNotCutThroughAnEmoji() {
        String text = AuditRules.clip(letters(AuditRules.REASON_MAX - 1) + EMOJI, AuditRules.REASON_MAX);
        assertTrue(whole(text));
        assertEquals(AuditRules.REASON_MAX - 1, text.length());
    }

    @Test
    public void theTransferReferenceIsNotCutThroughAnEmoji() {
        String text = PaymentDetails.cleanReference(letters(PaymentDetails.REFERENCE_MAX - 1) + EMOJI);
        assertTrue(whole(text));
    }

    @Test
    public void anEmojiThatFitsIsKept() {
        String text = OrderNote.clean(letters(OrderNote.MAX_LENGTH - 2) + EMOJI);
        assertTrue(text.endsWith(EMOJI));
    }
}
