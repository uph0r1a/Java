package com.coffeeshop.coffeeshopmanagement.model;

import java.time.LocalDateTime;

/** A table booked for a time (TODO 4.43, rule D11): who, how many, which table, when, and what became of it. */
public record Reservation(int id, int tableNumber, String customerName, String phone, LocalDateTime reservedFor, int people, String note,
                          Status status, String createdBy, LocalDateTime createdAt, LocalDateTime closedAt) {

    /** BOOKED is waiting; the other three are final: the guests were seated, it was cancelled, or they never came. */
    public enum Status { BOOKED, SEATED, CANCELLED, NO_SHOW }

    public boolean isBooked() {
        return status == Status.BOOKED;
    }
}
