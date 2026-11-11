package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.model.Customer;
import com.coffeeshop.coffeeshopmanagement.model.DiningTable;
import com.coffeeshop.coffeeshopmanagement.model.Order;
import com.coffeeshop.coffeeshopmanagement.model.TableStatus;
import com.coffeeshop.coffeeshopmanagement.service.OrderScreenRules.IdText;
import com.coffeeshop.coffeeshopmanagement.service.OrderScreenRules.Seat;
import com.coffeeshop.coffeeshopmanagement.service.OrderScreenRules.Shown;
import org.junit.Test;

import static org.junit.Assert.*;

/** TODO 6.5: what the till's order screen decided inside {@code OrderController}, tested without JavaFX or a database. */
public class OrderScreenRulesTest {

    private static Order order(int id) {
        Order order = new Order();
        order.setId(id);
        return order;
    }

    private static Customer customer(String name, String phone) {
        Customer c = new Customer();
        c.setFullName(name);
        c.setPhone(phone);
        return c;
    }

    private static DiningTable table(TableStatus status) {
        DiningTable t = new DiningTable();
        t.setStatus(status);
        return t;
    }

    // ---- a late answer

    @Test
    public void anAnswerForAnotherTableIsStale() {
        assertFalse(OrderScreenRules.isStale(5, 5, null, null));
        assertTrue(OrderScreenRules.isStale(6, 5, null, null));
        assertTrue("the table was left for the take-away screen", OrderScreenRules.isStale(null, 5, 9, null));
        assertTrue("the take-away screen was left for a table", OrderScreenRules.isStale(5, null, null, null));
    }

    @Test
    public void aTakeAwayAnswerIsStaleWhenAnotherTakeAwayOrderIsOnScreen() {
        assertFalse(OrderScreenRules.isStale(null, null, 9, 9));
        assertFalse("a new take-away order has no number on either side", OrderScreenRules.isStale(null, null, null, null));
        assertTrue(OrderScreenRules.isStale(null, null, 10, 9));
        assertTrue(OrderScreenRules.isStale(null, null, 9, null));
        assertFalse("for a table the take-away number does not matter", OrderScreenRules.isStale(5, 5, 10, 9));
    }

    // ---- what the order box says

    @Test
    public void aSavedOrderShowsItsNumberItsCustomerAndTheCartTotal() {
        Shown shown = OrderScreenRules.shown(order(7), customer("Lan", "0901"), true, "Khách lẻ");
        assertTrue(shown.serving());
        assertEquals(IdText.NUMBER, shown.idText());
        assertEquals("Lan", shown.customerName());
        assertEquals("0901", shown.customerPhone());
        assertTrue(shown.showCustomerBox());
        assertTrue(shown.totalFromCart());
    }

    @Test
    public void aSavedOrderWithNoCustomerShowsTheWalkInText() {
        Shown shown = OrderScreenRules.shown(order(7), null, false, "Walk-in");
        assertTrue(shown.serving());
        assertEquals("Walk-in", shown.customerName());
        assertEquals("", shown.customerPhone());
        assertTrue(shown.showCustomerBox());
    }

    @Test
    public void aTakenTableWithNoOrderIsBeingServedButShowsNoNumberAndNoTotal() {
        Shown shown = OrderScreenRules.shown(null, null, true, "Walk-in");
        assertTrue(shown.serving());
        assertEquals(IdText.SERVING, shown.idText());
        assertEquals("Walk-in", shown.customerName());
        assertFalse(shown.showCustomerBox());
        assertFalse(shown.totalFromCart());
    }

    @Test
    public void aFreeTableShowsNothing() {
        Shown shown = OrderScreenRules.shown(null, null, false, "Walk-in");
        assertFalse(shown.serving());
        assertEquals(IdText.NONE, shown.idText());
        assertEquals("", shown.customerName());
        assertEquals("", shown.customerPhone());
        assertFalse(shown.showCustomerBox());
        assertFalse(shown.totalFromCart());
    }

    @Test
    public void aCustomerWithNoPhoneShowsAnEmptyPhone() {
        assertEquals("", OrderScreenRules.shown(order(1), customer("An", null), false, "x").customerPhone());
    }

    // ---- seating a booking

    @Test
    public void aBookingIsIgnoredWhileAPaymentOrAnOrderStartIsRunning() {
        assertEquals(Seat.IGNORE, OrderScreenRules.canSeat(true, false, table(TableStatus.EMPTY)));
        assertEquals(Seat.IGNORE, OrderScreenRules.canSeat(false, true, null));
    }

    @Test
    public void aBookingNeedsAFreeTableThatIsOnTheMap() {
        assertEquals(Seat.TABLE_BUSY, OrderScreenRules.canSeat(false, false, null));
        assertEquals(Seat.TABLE_BUSY, OrderScreenRules.canSeat(false, false, table(TableStatus.OCCUPIED)));
        assertEquals(Seat.OK, OrderScreenRules.canSeat(false, false, table(TableStatus.EMPTY)));
    }

    // ---- the autosave's two questions

    @Test
    public void onlyASavedOrderIsAutosavedAndOnlyASelectionIsReloaded() {
        assertFalse(OrderScreenRules.isSaved(null));
        assertFalse("a new order has no number yet", OrderScreenRules.isSaved(order(0)));
        assertTrue(OrderScreenRules.isSaved(order(3)));
        assertFalse(OrderScreenRules.somethingSelected(null, false));
        assertTrue(OrderScreenRules.somethingSelected(4, false));
        assertTrue(OrderScreenRules.somethingSelected(null, true));
    }
}
