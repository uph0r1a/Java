package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.util.AppClock;
import com.coffeeshop.coffeeshopmanagement.model.DiningTable;
import com.coffeeshop.coffeeshopmanagement.model.Order;
import com.coffeeshop.coffeeshopmanagement.model.OrderStatus;
import com.coffeeshop.coffeeshopmanagement.model.TableStatus;
import org.junit.BeforeClass;
import org.junit.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Table status is computed: a table is occupied when it has an OPEN order OR its stored status
 * is OCCUPIED (manual toggle). setEmpty() must also cancel any OPEN order on that table.
 * Each test uses its own table number (the database is shared across DAO tests).
 */
public class TableDAOTest {

    private final TableDAO tableDAO = new TableDAO();
    private final OrderDAO orderDAO = new OrderDAO();

    @BeforeClass
    public static void setUpDatabase() {
        TestDatabaseSupport.ensureReady();
    }

    /** The UI only ever lists tables, so the test looks one up the same way. */
    private DiningTable table(int number) {
        return tableDAO.findAll().stream().filter(t -> t.getTableNumber() == number).findFirst().orElse(null);
    }

    private Order openOrderOn(int tableNumber) {
        Order order = new Order();
        order.setOrderDate(AppClock.now());
        order.setTableNumber(tableNumber);
        order.setStatus(OrderStatus.OPEN);
        order.setSubtotal(BigDecimal.ZERO);
        order.setDiscount(BigDecimal.ZERO);
        order.setTotal(BigDecimal.ZERO);
        return orderDAO.saveOrUpdateOpenOrder(order, List.of());
    }

    @Test
    public void seedsTwelveTablesAndTheyStartEmpty() {
        List<DiningTable> tables = tableDAO.findAll();
        assertTrue(tables.size() >= 12);
        DiningTable first = table(12);
        assertNotNull(first);
        assertEquals(TableStatus.EMPTY, table(12).getStatus());
    }

    @Test
    public void manualOccupiedThenEmpty() {
        tableDAO.setOccupied(1, null);
        assertTrue(table(1).isOccupied());
        tableDAO.setEmpty(1);
        assertFalse(table(1).isOccupied());
    }

    @Test
    public void openOrderMakesTableOccupiedAndSetEmptyCancelsIt() {
        Order saved = openOrderOn(2);
        assertTrue(saved.getId() > 0);
        assertTrue(table(2).isOccupied());
        assertEquals(Integer.valueOf(saved.getId()), table(2).getCurrentOrderId());
        assertTrue(orderDAO.findOpenOrderByTable(2).isPresent());

        tableDAO.setEmpty(2);

        assertFalse(table(2).isOccupied());
        assertFalse(orderDAO.findOpenOrderByTable(2).isPresent());
        assertEquals(OrderStatus.CANCELLED, orderDAO.findById(saved.getId()).orElseThrow().getStatus());
    }

    @Test
    public void unknownTableReturnsNull() {
        assertNull(table(9999));
    }

    @Test
    public void setEmptyFreesTheTableAndCancelsItsOpenOrderTogether() {
        Order open = openOrderOn(3);
        assertTrue(table(3).isOccupied());

        tableDAO.setEmpty(3);

        assertFalse(table(3).isOccupied());
        assertEquals(OrderStatus.CANCELLED, orderDAO.findById(open.getId()).orElseThrow().getStatus());
    }

    // ---------------------------------------------------------------- TODO 2.53: one source of truth

    private static com.coffeeshop.coffeeshopmanagement.model.OrderItem line() {
        com.coffeeshop.coffeeshopmanagement.model.OrderItem item = new com.coffeeshop.coffeeshopmanagement.model.OrderItem();
        item.setProductName("Test");
        item.setQuantity(1);
        item.setUnitPrice(BigDecimal.valueOf(20_000));
        item.setLineTotal(BigDecimal.valueOf(20_000));
        return item;
    }

    @Test
    public void anOrderAloneMakesTheTableOccupiedAndLeavesNoHandMadeMark() {
        openOrderOn(10);
        assertTrue(table(10).isOccupied());
        assertFalse("the order is the reason, not a stored mark", tableDAO.holdsWithoutOrder().contains(10));
        tableDAO.setEmpty(10);
    }

    @Test
    public void aHandMadeMarkWithoutAnOrderIsListedSoItCanBeFlagged() {
        tableDAO.setOccupied(11, null);
        assertTrue(tableDAO.holdsWithoutOrder().contains(11));
        String text = com.coffeeshop.coffeeshopmanagement.service.DashboardStatsService.heldTablesWarning(List.of(11));
        assertTrue(text, text.contains("bàn 11"));
        assertNull(com.coffeeshop.coffeeshopmanagement.service.DashboardStatsService.heldTablesWarning(List.of()));
        tableDAO.setEmpty(11);
        assertFalse(tableDAO.holdsWithoutOrder().contains(11));
    }

    @Test
    public void payingAnOrderOnAMarkedTableClearsTheMarkInTheSameTransaction() {
        tableDAO.setOccupied(12, null);
        Order open = openOrderOn(12);
        orderDAO.payExistingOrder(open.getId(), null, com.coffeeshop.coffeeshopmanagement.model.PaymentMethod.CASH,
                BigDecimal.ZERO, BigDecimal.valueOf(20_000), List.of(line()));
        assertFalse(table(12).isOccupied());
        assertFalse(tableDAO.holdsWithoutOrder().contains(12));
    }

    @Test
    public void aSaleSavedStraightAsPaidClearsAMarkOnItsTable() {
        tableDAO.setOccupied(9, null);
        Order paid = new Order();
        paid.setOrderDate(AppClock.now());
        paid.setPaidAt(AppClock.now());
        paid.setTableNumber(9);
        paid.setStatus(OrderStatus.PAID);
        paid.setSubtotal(BigDecimal.valueOf(20_000));
        paid.setDiscount(BigDecimal.ZERO);
        paid.setTotal(BigDecimal.valueOf(20_000));
        orderDAO.insert(paid, List.of(line()));
        assertFalse(table(9).isOccupied());
    }
}
