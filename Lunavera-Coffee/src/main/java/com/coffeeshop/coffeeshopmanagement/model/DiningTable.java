package com.coffeeshop.coffeeshopmanagement.model;

import com.coffeeshop.coffeeshopmanagement.util.I18n;

public class DiningTable {
    private int id;
    private int tableNumber;
    private String name;
    private TableStatus status;
    private int capacity;
    private Integer currentOrderId;
    private java.time.LocalDateTime openedAt;

    public DiningTable() {
    }

    public DiningTable(int id, int tableNumber, String name, TableStatus status, int capacity, Integer currentOrderId) {
        this.id = id;
        this.tableNumber = tableNumber;
        this.name = name;
        this.status = status;
        this.capacity = capacity;
        this.currentOrderId = currentOrderId;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getTableNumber() {
        return tableNumber;
    }

    public void setTableNumber(int tableNumber) {
        this.tableNumber = tableNumber;
    }

    public String getName() {
        return name;
    }

    /**
     * The name to show on screen. The starter tables are stored as "Bàn 1", "Bàn 2"...; those follow the language in use
     * ("Table 1"). A name the shop typed itself ("Sân vườn 1") is shown as it is.
     */
    public String displayName() {
        if (name != null && name.trim().equals("Bàn " + tableNumber)) {
            return I18n.t("orders.table", tableNumber);
        }
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public TableStatus getStatus() {
        return status;
    }

    public void setStatus(TableStatus status) {
        this.status = status;
    }

    public boolean isOccupied() {
        return status == TableStatus.OCCUPIED;
    }

    public int getCapacity() {
        return capacity;
    }

    public Integer getCurrentOrderId() {
        return currentOrderId;
    }

    /** When the table's OPEN order was started (TODO 5.11); null for a free table or when the stored time cannot be read. */
    public java.time.LocalDateTime getOpenedAt() {
        return openedAt;
    }

    public void setOpenedAt(java.time.LocalDateTime openedAt) {
        this.openedAt = openedAt;
    }

}
