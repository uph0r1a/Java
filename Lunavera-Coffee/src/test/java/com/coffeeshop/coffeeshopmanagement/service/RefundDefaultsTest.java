package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.dao.RefundDAO.RefundableLine;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import org.junit.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/** TODO 2.66 / 6.90 (d): the refund boxes suggest "do not put it back" and a reason for something made to order, and nothing else changes. */
public class RefundDefaultsTest {

    private static RefundableLine line(int id, boolean made) {
        return new RefundableLine(id, id, "Item " + id, 2, new BigDecimal("40000"), 0, made);
    }

    private static final List<RefundableLine> MIXED = List.of(line(1, true), line(2, false));

    @Test
    public void onlyTheLinesBeingRefundedDecideWhetherSomethingMadeIsAmongThem() {
        assertTrue(RefundDefaults.anyMade(MIXED, Map.of(1, 1)));
        assertFalse("only the packaged line is refunded", RefundDefaults.anyMade(MIXED, Map.of(2, 2)));
        assertFalse("zero pieces do not count", RefundDefaults.anyMade(MIXED, Map.of(1, 0)));
        assertFalse(RefundDefaults.anyMade(MIXED, Map.of()));
    }

    @Test
    public void somethingMadeIsNotPutBackAndGetsAReasonAnythingElseKeepsItsOldAnswer() {
        assertFalse(RefundDefaults.returnToStock(true));
        assertEquals(RefundDefaults.MADE_REASON_KEY, RefundDefaults.reasonKey(true));
        assertTrue(RefundDefaults.returnToStock(false));
        assertNull("no reason is made up for goods that come back whole", RefundDefaults.reasonKey(false));
    }

    @Test
    public void aWholeOrderIsMadeWhenAnyLineIs() {
        assertTrue(RefundDefaults.anyMade(MIXED));
        assertFalse(RefundDefaults.anyMade(List.of(line(2, false))));
        assertFalse(RefundDefaults.anyMade(List.of()));
    }

    @Test
    public void theReasonTextExistsInBothLanguagesAndIsLongEnoughToBeAccepted() {
        for (String language : I18n.LANGUAGES) {
            String text = I18n.t(RefundDefaults.MADE_REASON_KEY);
            assertFalse(language, text.equals(RefundDefaults.MADE_REASON_KEY));
            assertNull("accepted as a reason: " + text, com.coffeeshop.coffeeshopmanagement.service.AuditRules.reasonProblem(text));
        }
    }

    @Test
    public void bothRefundBoxesUseTheDefaultsAndTheCancelFlowReadsThemInTheBackground() throws IOException {
        String lines = Files.readString(Path.of("src/main/java/com/coffeeshop/coffeeshopmanagement/controller/RefundLinesDialog.java"));
        assertTrue(lines.contains("RefundDefaults.anyMade(state.lines(), requestOf(spinners))"));
        assertTrue("the cashier's own tick is not overwritten", lines.contains("if (!restockTouched[0])"));
        String flow = Files.readString(Path.of("src/main/java/com/coffeeshop/coffeeshopmanagement/controller/OrderCancelFlow.java"));
        assertTrue(flow.contains("Async.run(() -> new RefundService().suggestedReasonKey(orderId)"));
        assertTrue(flow.contains("RefundDialog.ask(orderId, total, suggestedReasonKey)"));
        String dialog = Files.readString(Path.of("src/main/java/com/coffeeshop/coffeeshopmanagement/controller/RefundDialog.java"));
        assertTrue("the whole-order answer still starts on do not return", dialog.contains("keep.setSelected(true)"));
    }
}
