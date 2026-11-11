package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.model.AuditAction;
import com.coffeeshop.coffeeshopmanagement.model.AuditEntry;
import com.coffeeshop.coffeeshopmanagement.model.AuditTarget;
import com.coffeeshop.coffeeshopmanagement.model.Order;
import com.coffeeshop.coffeeshopmanagement.model.OrderItem;
import com.coffeeshop.coffeeshopmanagement.model.OrderStatus;
import com.coffeeshop.coffeeshopmanagement.model.PaymentMethod;
import com.coffeeshop.coffeeshopmanagement.util.AppClock;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.ReceiptText;
import com.coffeeshop.coffeeshopmanagement.util.TestSession;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.Assert.*;

/** TODO 3.72: a receipt printed again says so, and the print is counted and, from the second one, written to the audit log. */
public class ReceiptReprintTest {

    private final OrderDAO orderDAO = new OrderDAO();
    private final ReceiptPrintDAO prints = new ReceiptPrintDAO();
    private final AuditDAO audit = new AuditDAO();

    @BeforeClass
    public static void setUpDatabase() {
        TestDatabaseSupport.ensureReady();
    }

    @Before
    public void signIn() {
        TestSession.admin();
    }

    private Order paidOrder() {
        Order order = new Order();
        order.setOrderDate(AppClock.now());
        order.setStatus(OrderStatus.PAID);
        order.setSubtotal(new BigDecimal("30000"));
        order.setDiscount(BigDecimal.ZERO);
        order.setTotal(new BigDecimal("30000"));
        order.setPaymentMethod(PaymentMethod.CASH);
        order.setPaidAt(AppClock.now());
        OrderItem line = new OrderItem();
        line.setProductName("test line");
        line.setQuantity(1);
        line.setUnitPrice(new BigDecimal("30000"));
        line.setLineTotal(new BigDecimal("30000"));
        return orderDAO.insert(order, List.of(line));
    }

    private List<AuditEntry> reprintLines(int orderId) {
        return audit.find(new AuditDAO.Filter(null, null, AuditAction.RECEIPT_REPRINTED, null, String.valueOf(orderId)), 50, 0).stream()
                .filter(e -> AuditTarget.ORDER.equals(e.targetType()) && e.targetId() != null && e.targetId() == orderId).toList();
    }

    @Test
    public void theFirstPrintIsCountedAndHasNoMarkAndNoAuditLine() {
        Order order = paidOrder();
        assertEquals(0, orderDAO.findById(order.getId()).orElseThrow().getPrintCount());
        assertNull("never printed: the print that comes now is the original", ReceiptText.copyMark(order));

        assertEquals(1, prints.recordPrint(order.getId()));

        assertEquals(1, orderDAO.findById(order.getId()).orElseThrow().getPrintCount());
        assertTrue("the original print is not a reprint", reprintLines(order.getId()).isEmpty());
    }

    @Test
    public void theSecondPrintCarriesTheMarkAndIsWrittenToTheAuditLine() {
        Order order = paidOrder();
        prints.recordPrint(order.getId());

        Order reloaded = orderDAO.findById(order.getId()).orElseThrow();
        String mark = ReceiptText.copyMark(reloaded);
        assertNotNull("printed once already: the next one is a copy", mark);
        assertTrue(mark, mark.contains("2"));
        assertTrue(mark.toUpperCase().contains("COPY"));

        assertEquals(2, prints.recordPrint(order.getId()));
        assertEquals(3, prints.recordPrint(order.getId()));

        List<AuditEntry> lines = reprintLines(order.getId());
        assertEquals(2, lines.size());
        assertTrue(lines.stream().anyMatch(e -> "1".equals(e.oldValue()) && "2".equals(e.newValue())));
        assertTrue(lines.stream().anyMatch(e -> "2".equals(e.oldValue()) && "3".equals(e.newValue())));
        assertNotNull("says who", lines.get(0).actorName());
        assertTrue(ReceiptText.copyMark(orderDAO.findById(order.getId()).orElseThrow()).contains("4"));
    }

    @Test
    public void aMissingOrderCountsNothing() {
        assertEquals(0, prints.recordPrint(2_000_000_000));
    }

    @Test
    public void theMarkExistsInBothLanguagesAndTheActionHasAName() {
        Order order = new Order();
        order.setPrintCount(1);
        for (String language : new String[]{I18n.VI, I18n.EN}) {
            String text = I18n.in(language, "receipt.copy", "2");
            assertTrue(language + ": " + text, text.contains("2") && !text.startsWith("receipt."));
            assertFalse(I18n.in(language, AuditAction.RECEIPT_REPRINTED.labelKey()).startsWith("audit."));
        }
        assertEquals(2, order.nextCopyNumber());
        order.setPrintCount(0);
        assertEquals(0, order.nextCopyNumber());
    }
}
