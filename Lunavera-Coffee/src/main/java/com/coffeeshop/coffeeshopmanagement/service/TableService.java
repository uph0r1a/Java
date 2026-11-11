package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.dao.OrderDAO;
import com.coffeeshop.coffeeshopmanagement.dao.TableDAO;
import com.coffeeshop.coffeeshopmanagement.dao.TableMoveDAO;
import com.coffeeshop.coffeeshopmanagement.model.DiningTable;
import com.coffeeshop.coffeeshopmanagement.model.Order;
import com.coffeeshop.coffeeshopmanagement.util.Permissions;
import com.coffeeshop.coffeeshopmanagement.util.SecurityLog;
import com.coffeeshop.coffeeshopmanagement.util.Session;

import java.util.List;
import java.util.Optional;

/**
 * What the table screens ask for (TODO 6.21): the list, adding, changing and removing a table (the database refuses to remove one
 * that holds an order, in one statement), moving or merging the bill of one table into another ({@link TableMoveDAO}), and marking a
 * table in use or empty by hand. Emptying a table also cancels its unpaid order, so the rule of
 * {@link TablePolicy} (only an administrator or the cashier who opened the order) is checked here, against the order the database
 * holds right now, not against what a till screen remembers: an old screen cannot get past it. Meant to be called from a
 * background thread (TODO 6.14).
 */
public class TableService {

    private final TableDAO tableDAO;
    private final OrderDAO orderDAO;
    private final TableMoveDAO moves;

    public TableService() {
        this(new TableDAO(), new OrderDAO(), new TableMoveDAO());
    }

    TableService(TableDAO tableDAO, OrderDAO orderDAO) {
        this(tableDAO, orderDAO, new TableMoveDAO());
    }

    TableService(TableDAO tableDAO, OrderDAO orderDAO, TableMoveDAO moves) {
        this.tableDAO = tableDAO;
        this.orderDAO = orderDAO;
        this.moves = moves;
    }

    public List<DiningTable> findAll() {
        return tableDAO.findAll();
    }

    /** Adds a table; its number. */
    public int add(String name, int capacity) {
        return tableDAO.add(name, capacity);
    }

    public void update(int tableNumber, String name, int capacity) {
        tableDAO.update(tableNumber, name, capacity);
    }

    /** Removes a table; false when it holds an order or is marked in use. */
    public boolean remove(int tableNumber) {
        return tableDAO.remove(tableNumber);
    }

    /** Moves the open bill of table {@code from} to the empty table {@code to}. */
    public void transfer(int from, int to) {
        moves.transfer(from, to);
    }

    /** Adds the open bill of table {@code from} to the bill of table {@code into}. */
    public void merge(int into, int from) {
        moves.merge(into, from);
    }

    /**
     * Whether the signed-in person may empty a table whose unpaid order is {@code openOrder} (null: no order, anybody may). No
     * database access, so a screen can use it to refuse at once, before it asks "are you sure?".
     */
    public static boolean mayEmpty(Order openOrder) {
        if (openOrder == null) return true;
        return TablePolicy.canEmptyTableWithOpenOrder(Session.isAdmin(),
                Session.getCurrentEmployee() != null ? Session.getCurrentEmployee().getId() : null,
                openOrder.getEmployeeId());
    }

    /** Marks the table as in use with no order on it (a table held by hand). */
    public void markOccupied(int tableNumber) {
        tableDAO.setOccupied(tableNumber, null);
    }

    /**
     * Empties the table and cancels its unpaid order (both or neither).
     *
     * @throws Permissions.PermissionDeniedException if the table has an unpaid order that the signed-in person may not cancel
     */
    public void markEmpty(int tableNumber) {
        Optional<Order> open = orderDAO.findOpenOrderByTable(tableNumber);
        if (open.isPresent() && !mayEmpty(open.get())) {
            SecurityLog.warnLimited("table-empty:" + SecurityLog.actor() + ":" + tableNumber,
                    SecurityLog.actor() + " tried to empty table " + tableNumber + " that holds another cashier's open order");
            throw new Permissions.PermissionDeniedException(TablePolicy.refusedMessage());
        }
        tableDAO.setEmpty(tableNumber);
    }
}
