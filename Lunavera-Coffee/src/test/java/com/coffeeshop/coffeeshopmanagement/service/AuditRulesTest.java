package com.coffeeshop.coffeeshopmanagement.service;

import org.junit.Test;

import java.math.BigDecimal;

import static org.junit.Assert.*;

/** TODO 4.2: how audit text is kept and what makes a refund reason acceptable. */
public class AuditRulesTest {

    @Test
    public void textBecomesOneShortLine() {
        assertEquals("khách trả lại", AuditRules.clip("  khách \n trả\tlại ", 200));
        assertNull(AuditRules.clip("   ", 200));
        assertNull(AuditRules.clip(null, 200));
        assertEquals("abc", AuditRules.clip("a\u0007b\u0000c", 200));
    }

    @Test
    public void aLongTextIsCutAtTheColumnSize() {
        String cut = AuditRules.clip("y".repeat(900), AuditRules.VALUE_MAX);
        assertEquals(AuditRules.VALUE_MAX, cut.length());
        assertEquals(AuditRules.REASON_MAX, AuditRules.clip("z".repeat(500), AuditRules.REASON_MAX).length());
    }

    @Test
    public void moneyIsWholeDongDigits() {
        assertEquals("45000", AuditRules.money(new BigDecimal("45000")));
        assertEquals("45001", AuditRules.money(new BigDecimal("45000.5")));
        assertEquals("0", AuditRules.money(BigDecimal.ZERO));
        assertNull(AuditRules.money(null));
    }

    @Test
    public void aRefundNeedsARealReason() {
        assertEquals("history.cancel.reason.required", AuditRules.reasonProblem(null));
        assertEquals("history.cancel.reason.required", AuditRules.reasonProblem("   "));
        assertEquals("history.cancel.reason.required", AuditRules.reasonProblem("ok"));
        assertNull(AuditRules.reasonProblem("sai"));
        assertNull(AuditRules.reasonProblem("  khách đổi ý  "));
    }
}
